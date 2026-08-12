package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.service.dreams.DreamService;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Optional screensaver (Daydream) host. Uses the same {@link PonySceneController}
 * as the live wallpaper so scene, prefs, power, and thermal policy stay in one place.
 *
 * <p>When the dream runs is decided entirely by the system (typically after screen
 * timeout while charging or docked — see Display → Screen saver → When to start).
 * This service only controls content and interactive exit.
 *
 * <p>Interactive so hold-to-drag works. Starts dimmed for dock/idle power use.
 * A real tap/swipe that is not a completed long-press drag brightens the screen
 * if dimmed (dream continues); the same gesture exits only when already bright
 * via {@link #requestUserUnlock()}. Back always exits after the start grace window
 * the same way. That path gently wakes the dream and starts
 * {@link UnlockRequestActivity} so a secure keyguard can show the unlock method
 * (PIN / pattern / biometrics) without an extra lock-screen swipe.
 *
 * <p>Enter and exit use a black content overlay (fade-in / fade-out) so the herd
 * does not hard-cut against the lock screen, and so any OEM window wipe only
 * animates a solid black buffer instead of stretching live sprites.
 *
 * <p>After {@link #MAX_IDLE_MS} with no touch interaction, the dream calls
 * {@link #finish()} so the system can turn the screen off (and run AOD if
 * configured). Touch input resets that timer. Thermal hard-stop also uses
 * {@link #finish()} — neither should prompt for unlock.
 */
public class PonyDreamService extends DreamService implements PonySceneController.FrameSurface {

    /**
     * Ignore dismiss gestures for a short time after the dream becomes visible.
     * Some devices deliver a synthetic CANCEL/UP when the window attaches; finishing
     * on those would make the screensaver appear not to start after idle timeout.
     */
    private static final long DISMISS_GRACE_MS = 750;

    /**
     * After a tap brightens a dim dream, re-dim if there is no further interaction.
     * Keeps dock/idle power behaviour without requiring a second tap to exit first.
     */
    private static final long RE_DIM_IDLE_MS = 30_000;

    /**
     * Exit the dream after this long with no user touch so the display can sleep
     * instead of animating overnight (e.g. docked / charging).
     */
    private static final long MAX_IDLE_MS = 10 * 60_000L;

    /** Black overlay fade when the dream becomes visible. */
    private static final long FADE_IN_MS = 350;
    /**
     * Brief hold at full black so the first scene frame can paint under the
     * overlay before it dissolves.
     */
    private static final long FADE_IN_DELAY_MS = 50;
    /** Black overlay fade before {@link #wakeUp()} / {@link #finish()}. */
    private static final long FADE_OUT_MS = 250;
    /** Treat overlay as already solid black above this alpha. */
    private static final float FADE_OPAQUE_EPSILON = 0.99f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reDimRunnable = new Runnable() {
        @Override
        public void run() {
            if (dreaming && !exiting && isScreenBright()) {
                setScreenBright(false);
            }
        }
    };
    private final Runnable maxIdleRunnable = new Runnable() {
        @Override
        public void run() {
            if (dreaming && !exiting) {
                beginGracefulExit(new Runnable() {
                    @Override
                    public void run() {
                        if (dreaming) {
                            finish();
                        }
                    }
                });
            }
        }
    };
    private PonySceneController controller;
    private FrameLayout rootLayout;
    private SurfaceView surfaceView;
    /** Full-screen black veil for content enter/exit transitions. */
    private View fadeOverlay;
    private boolean dreaming = false;
    private boolean surfaceReady = false;
    /** True while a content fade-out is in progress (or completed) for this exit. */
    private boolean exiting = false;
    /** True after the enter fade for this dream session has been started. */
    private boolean fadeInStarted = false;
    /** Uptime millis when {@link #onDreamingStarted} last ran; 0 if not dreaming. */
    private long dreamingStartedAtMs = 0;
    /** True after a real {@link MotionEvent#ACTION_DOWN} we own for the current gesture. */
    private boolean gestureDown = false;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);

        setInteractive(true);
        setFullscreen(true);
        // Keep the default dimmed screen for dock/idle power use.
        setScreenBright(false);

        rootLayout = new FrameLayout(this);
        surfaceView = new SurfaceView(this);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surfaceReady = true;
                updateActive();
                maybeStartFadeIn();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                surfaceReady = true;
                if (controller != null) {
                    controller.onSurfaceSizeChanged();
                }
                updateActive();
                maybeStartFadeIn();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surfaceReady = false;
                if (controller != null) {
                    // Freeze first so any late size/policy work does not unload bitmaps.
                    controller.setFrozen(true);
                    controller.setActive(false);
                }
            }
        });

        fadeOverlay = new View(this);
        fadeOverlay.setBackgroundColor(Color.BLACK);
        fadeOverlay.setAlpha(1f);
        // Overlay sits above the surface for the whole dream; it owns touch so
        // events still reach the herd while alpha is 0.
        fadeOverlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (exiting) {
                    // Swallow input during fade-out so a second tap cannot re-enter unlock.
                    return true;
                }
                if (controller != null) {
                    controller.onTouchEvent(event);
                }
                int action = event.getActionMasked();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        gestureDown = true;
                        // Defer re-dim until the finger is up so a long drag cannot dim mid-gesture.
                        cancelReDim();
                        // Real contact counts as activity; ignore orphan UP/CANCEL for idle.
                        noteUserActivity();
                        break;
                    case MotionEvent.ACTION_UP:
                        // Only act on a complete user gesture we saw from DOWN.
                        // Never exit on orphan UP or on CANCEL (window transitions).
                        if (gestureDown && canDismissFromTouch()) {
                            if (controller == null || !controller.didDragThisGesture()) {
                                gestureDown = false;
                                if (isScreenBright()) {
                                    requestUserUnlock();
                                } else {
                                    // Dimmed: brighten and keep dreaming (clock-style wake).
                                    setScreenBright(true);
                                    scheduleReDim();
                                }
                                return true;
                            }
                        }
                        // Drag ended (or grace/incomplete gesture): stay bright if already so.
                        if (isScreenBright()) {
                            scheduleReDim();
                        }
                        gestureDown = false;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        // Surface/window may cancel without a user intent to exit.
                        if (isScreenBright()) {
                            scheduleReDim();
                        }
                        gestureDown = false;
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        // Primary pointer only ends the gesture for dismiss purposes.
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        FrameLayout.LayoutParams matchParent = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rootLayout.addView(surfaceView, matchParent);
        rootLayout.addView(fadeOverlay, matchParent);
        setContentView(rootLayout);
        surfaceView.requestFocus();

        controller = new PonySceneController(this, handler, this);
        controller.start();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreaming = true;
        exiting = false;
        fadeInStarted = false;
        dreamingStartedAtMs = SystemClock.uptimeMillis();
        gestureDown = false;
        // Always start dimmed; a prior bright session must not stick across dreams.
        setScreenBright(false);
        cancelReDim();
        scheduleMaxIdle();
        if (controller != null) {
            controller.setFrozen(false);
        }
        // Cover the scene until the first frames are ready, then dissolve.
        if (fadeOverlay != null) {
            fadeOverlay.animate().cancel();
            fadeOverlay.setAlpha(1f);
        }
        updateActive();
        maybeStartFadeIn();
        if (surfaceView != null) {
            surfaceView.requestFocus();
        }
    }

    @Override
    public void onDreamingStopped() {
        dreaming = false;
        dreamingStartedAtMs = 0;
        gestureDown = false;
        fadeInStarted = false;
        cancelReDim();
        cancelMaxIdle();
        cancelOverlayAnimations();
        if (fadeOverlay != null) {
            fadeOverlay.setAlpha(1f);
        }
        if (controller != null) {
            // Hold the last buffer; do not recycle under any residual window anim.
            controller.setFrozen(true);
            controller.setActive(false);
        }
        exiting = false;
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreaming = false;
        exiting = false;
        fadeInStarted = false;
        dreamingStartedAtMs = 0;
        gestureDown = false;
        surfaceReady = false;
        cancelReDim();
        cancelMaxIdle();
        cancelOverlayAnimations();
        if (controller != null) {
            controller.stop();
            controller = null;
        }
        fadeOverlay = null;
        surfaceView = null;
        rootLayout = null;
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (exiting) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            int code = event.getKeyCode();
            // HOME is often not delivered to apps; BACK/ESCAPE still help on docks.
            // Back exits regardless of dim/bright — only taps use the two-step wake.
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (canDismissFromTouch()) {
                    requestUserUnlock();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * User intends to leave the dream and unlock. Fades content to black, then
     * starts the keyguard-dismiss trampoline (API 26+) and {@link #wakeUp()} so
     * the dream ends gently and the device stays awake. Idle / thermal paths
     * keep using {@link #finish()} so they do not prompt for credentials.
     */
    private void requestUserUnlock() {
        beginGracefulExit(new Runnable() {
            @Override
            public void run() {
                if (!dreaming) return;
                UnlockRequestActivity.launch(PonyDreamService.this);
                wakeUp();
            }
        });
    }

    /**
     * Content-side exit: freeze the herd, fade the black overlay to opaque, then
     * run {@code afterFade} ({@link #wakeUp()} or {@link #finish()}). Idempotent
     * for the lifetime of one exit so double-taps cannot stack unlock requests.
     */
    private void beginGracefulExit(final Runnable afterFade) {
        if (exiting) {
            return;
        }
        exiting = true;
        cancelReDim();
        cancelMaxIdle();
        cancelOverlayAnimations();
        if (controller != null) {
            controller.setFrozen(true);
        }
        if (fadeOverlay == null) {
            if (afterFade != null) afterFade.run();
            return;
        }
        float alpha = fadeOverlay.getAlpha();
        if (alpha >= FADE_OPAQUE_EPSILON) {
            fadeOverlay.setAlpha(1f);
            if (afterFade != null) afterFade.run();
            return;
        }
        fadeOverlay.animate()
                .alpha(1f)
                .setDuration(FADE_OUT_MS)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (afterFade != null) afterFade.run();
                    }
                })
                .start();
    }

    /**
     * Dissolves the black overlay once the dream is active and the surface is
     * ready. Started at most once per dream session so surface size churn does
     * not restart the enter animation.
     */
    private void maybeStartFadeIn() {
        if (!dreaming || !surfaceReady || exiting || fadeInStarted || fadeOverlay == null) {
            return;
        }
        fadeInStarted = true;
        cancelOverlayAnimations();
        fadeOverlay.setAlpha(1f);
        fadeOverlay.animate()
                .alpha(0f)
                .setStartDelay(FADE_IN_DELAY_MS)
                .setDuration(FADE_IN_MS)
                .start();
    }

    private void cancelOverlayAnimations() {
        if (fadeOverlay != null) {
            fadeOverlay.animate().cancel();
        }
    }

    /**
     * Whether a user-driven dismiss / brighten is allowed. Blocks the brief window
     * after the dream starts so attach-time input noise cannot call
     * {@link #requestUserUnlock()} / flip brightness.
     */
    private boolean canDismissFromTouch() {
        if (!dreaming || exiting || dreamingStartedAtMs == 0) return false;
        return SystemClock.uptimeMillis() - dreamingStartedAtMs >= DISMISS_GRACE_MS;
    }

    private void scheduleReDim() {
        handler.removeCallbacks(reDimRunnable);
        handler.postDelayed(reDimRunnable, RE_DIM_IDLE_MS);
    }

    private void cancelReDim() {
        handler.removeCallbacks(reDimRunnable);
    }

    /**
     * User touched the dream; restart the max-idle countdown so interaction
     * keeps the screensaver running.
     */
    private void noteUserActivity() {
        if (!dreaming || exiting) return;
        scheduleMaxIdle();
    }

    private void scheduleMaxIdle() {
        handler.removeCallbacks(maxIdleRunnable);
        handler.postDelayed(maxIdleRunnable, MAX_IDLE_MS);
    }

    private void cancelMaxIdle() {
        handler.removeCallbacks(maxIdleRunnable);
    }

    private void updateActive() {
        if (controller == null) return;
        // Keep the controller "active" only while living and not mid-exit freeze
        // beyond what setFrozen already handles; surface still needed for first paint.
        boolean wantActive = dreaming && surfaceReady && !exiting;
        if (wantActive) {
            controller.setActive(true);
        } else if (!dreaming || !surfaceReady) {
            controller.setActive(false);
        }
        // When exiting, leave active as-is under freeze so we do not clear frozen
        // via setActive(false) until dreaming stops / surface dies.
    }

    // --- FrameSurface ---

    @Override
    public SurfaceHolder getSurfaceHolder() {
        return surfaceView != null ? surfaceView.getHolder() : null;
    }

    @Override
    public boolean isDrawingEnabled() {
        // Allow the last pre-exit frames; freeze stops the loop separately.
        return dreaming && surfaceReady && !exiting;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public float getBackgroundXOffset() {
        return 0.5f;
    }

    @Override
    public float getBackgroundYOffset() {
        return 0.5f;
    }

    @Override
    public boolean shouldShowClock() {
        return getDreamPreferences().getBoolean(
                PonySceneController.PREF_DREAM_SHOW_CLOCK, false);
    }

    @Override
    public boolean shouldShowClockDate() {
        SharedPreferences prefs = getDreamPreferences();
        return prefs.getBoolean(PonySceneController.PREF_DREAM_SHOW_CLOCK, false)
                && prefs.getBoolean(PonySceneController.PREF_DREAM_SHOW_DATE, true);
    }

    /**
     * Thermal emergency (SEVERE+): fade out then end the dream so the system can
     * turn the display off instead of holding a frozen screensaver on a hot device.
     */
    @Override
    public void onThermalHardStop() {
        if (!dreaming) return;
        beginGracefulExit(new Runnable() {
            @Override
            public void run() {
                if (dreaming) {
                    finish();
                }
            }
        });
    }

    private SharedPreferences getDreamPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }
}
