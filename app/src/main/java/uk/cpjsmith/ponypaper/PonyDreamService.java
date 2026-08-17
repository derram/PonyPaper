package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.service.dreams.DreamService;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Switch;

/**
 * Optional screensaver (Daydream) host. Uses the same {@link PonySceneController}
 * as the live wallpaper so scene, prefs, power, and thermal policy stay in one place.
 *
 * <p>When the dream runs is decided entirely by the system (typically after screen
 * timeout while charging or docked — see Display → Screen saver → When to start).
 * This service only controls content and interactive exit.
 *
 * <p>Interactive so hold-to-drag works. Starts dimmed for dock/idle power use.
 * First contact on a dim scene brightens immediately (no gear yet). A confirmed
 * single tap that is not a completed long-press drag shows or hides session
 * chrome (a gear). Double-tap on the scene, Back / Escape after the start grace
 * window, or the Unlock row on the session sheet gently wake the dream and
 * start {@link UnlockRequestActivity} so a secure keyguard can show the unlock
 * method (PIN / pattern / biometrics) without an extra lock-screen swipe.
 * Chrome and the sheet do not unlock on tap — that would fight settings use.
 *
 * <p>Session chrome (keep-screen-on and disable-auto-dim) lives only for this
 * dream and is not written to preferences. Keep-screen-on skips the idle
 * {@link #finish()}; disable-auto-dim skips the 30s re-dim after wake.
 * Brightness stays with the host device. Thermal hard-stop still ends the dream.
 *
 * <p>Enter and exit use a black content overlay (fade-in / fade-out) so the herd
 * does not hard-cut against the lock screen, and so any OEM window wipe only
 * animates a solid black buffer instead of stretching live sprites.
 *
 * <p>After {@link #MAX_IDLE_MS} with no touch interaction, the dream calls
 * {@link #finish()} so the system can turn the screen off (and run AOD if
 * configured), unless the user opted to keep the screen on for this session.
 * Touch input resets that timer. Thermal hard-stop also uses
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

    /** Hide the gear when the session sheet is not open. */
    private static final long CHROME_AUTO_HIDE_MS = 5_000;
    private static final long CHROME_FADE_MS = 180;

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
            if (dreaming && !exiting && !keepScreenOn && !disableAutoDim && isScreenBright()) {
                sessionAwake = false;
                hideChrome(false);
                applyDisplayPolicy();
            }
        }
    };
    private final Runnable maxIdleRunnable = new Runnable() {
        @Override
        public void run() {
            if (dreaming && !exiting && !keepScreenOn) {
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
    private final Runnable chromeAutoHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (dreaming && !exiting && chromeVisible && !sheetExpanded) {
                hideChrome(true);
            }
        }
    };
    private PonySceneController controller;
    private FrameLayout rootLayout;
    private SurfaceView surfaceView;
    /** Full-screen black veil for content enter/exit transitions. */
    private View fadeOverlay;
    private GestureDetector sceneGestureDetector;
    private View chromeRoot;
    private ImageButton gearButton;
    private View sessionSheet;
    private Switch keepScreenOnSwitch;
    private Switch disableAutoDimSwitch;
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
    /**
     * Pony drag completed on the previous gesture. The controller clears
     * {@link PonySceneController#didDragThisGesture()} on the next DOWN, but
     * {@link GestureDetector.OnDoubleTapListener#onDoubleTap} also fires on that
     * DOWN, so the prior gesture must be remembered.
     */
    private boolean lastCompletedGestureWasDrag = false;

    /** User woke the display this session (or keep-on is holding it). */
    private boolean sessionAwake = false;
    /** Session opt-in: skip the 10-minute idle {@link #finish()} (and the 30s re-dim). */
    private boolean keepScreenOn = false;
    /** Session opt-in: skip the 30s re-dim after wake. The 10-minute idle still applies. */
    private boolean disableAutoDim = false;
    private boolean chromeVisible = false;
    private boolean sheetExpanded = false;
    /** Do not run switch listeners while syncing widgets from session state. */
    private boolean updatingChromeUi = false;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        PonySize.ensureDefault(this);
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
        sceneGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (controller != null && controller.didDragThisGesture()) {
                    return true;
                }
                if (canDismissFromTouch()) {
                    onSceneTap();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (lastCompletedGestureWasDrag) {
                    return true;
                }
                if (canDismissFromTouch()) {
                    requestUserUnlock();
                }
                return true;
            }
        });
        // Long-press on empty space should still count as a tap; pony grab is
        // handled by the scene controller, not GestureDetector's long-press.
        sceneGestureDetector.setIsLongpressEnabled(false);
        // Overlay sits above the surface for the whole dream; it owns touch so
        // events still reach the herd while alpha is 0. Session chrome is stacked
        // above this and receives gear / sheet hits first.
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
                        maybeWakeFromContact();
                        break;
                    case MotionEvent.ACTION_UP:
                        lastCompletedGestureWasDrag = controller != null
                                && controller.didDragThisGesture();
                        // Drag ended (or grace/incomplete gesture): stay bright if already so.
                        maybeScheduleReDim();
                        gestureDown = false;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        lastCompletedGestureWasDrag = controller != null
                                && controller.didDragThisGesture();
                        // Surface/window may cancel without a user intent to exit.
                        maybeScheduleReDim();
                        gestureDown = false;
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        // Primary pointer only ends the gesture for dismiss purposes.
                        break;
                    default:
                        break;
                }
                if (sceneGestureDetector != null) {
                    sceneGestureDetector.onTouchEvent(event);
                }
                return true;
            }
        });

        FrameLayout.LayoutParams matchParent = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rootLayout.addView(surfaceView, matchParent);
        rootLayout.addView(fadeOverlay, matchParent);
        inflateSessionChrome(matchParent);
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
        lastCompletedGestureWasDrag = false;
        resetSessionDisplayState();
        // Always start dimmed; a prior bright session must not stick across dreams.
        applyDisplayPolicy();
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
        lastCompletedGestureWasDrag = false;
        fadeInStarted = false;
        resetSessionDisplayState();
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
        lastCompletedGestureWasDrag = false;
        surfaceReady = false;
        sceneGestureDetector = null;
        resetSessionDisplayState();
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
        chromeRoot = null;
        gearButton = null;
        sessionSheet = null;
        keepScreenOnSwitch = null;
        disableAutoDimSwitch = null;
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
            // Back exits regardless of dim/bright — a single tap does not unlock.
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
        hideChrome(false);
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
        cancelChromeAnimations();
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

    /**
     * First contact on a dim scene: brighten immediately so a docked display
     * does not wait for {@link GestureDetector.OnDoubleTapListener#onSingleTapConfirmed}.
     * Chrome waits for that confirmed single tap so a double-tap unlock does
     * not flash the gear.
     */
    private void maybeWakeFromContact() {
        if (!canDismissFromTouch() || isScreenBright()) return;
        sessionAwake = true;
        applyDisplayPolicy();
    }

    /**
     * Confirmed non-drag single tap on the scene (not on chrome). Wakes a dim
     * dream if contact did not already, then toggles the gear; never unlocks.
     */
    private void onSceneTap() {
        if (!isScreenBright()) {
            sessionAwake = true;
            applyDisplayPolicy();
            showChrome(false);
            return;
        }
        if (chromeVisible) {
            hideChrome(true);
        } else {
            showChrome(false);
        }
    }

    private void scheduleReDim() {
        handler.removeCallbacks(reDimRunnable);
        handler.postDelayed(reDimRunnable, RE_DIM_IDLE_MS);
    }

    private void cancelReDim() {
        handler.removeCallbacks(reDimRunnable);
    }

    private void maybeScheduleReDim() {
        if (!dreaming || exiting || keepScreenOn || disableAutoDim || !isScreenBright()) return;
        scheduleReDim();
    }

    /**
     * User touched the dream; restart the max-idle countdown so interaction
     * keeps the screensaver running.
     */
    private void noteUserActivity() {
        if (!dreaming || exiting) return;
        if (!keepScreenOn) {
            scheduleMaxIdle();
        }
    }

    /** Chrome controls count as activity and must not lose a race with re-dim. */
    private void noteChromeActivity() {
        cancelReDim();
        noteUserActivity();
    }

    private void scheduleMaxIdle() {
        handler.removeCallbacks(maxIdleRunnable);
        handler.postDelayed(maxIdleRunnable, MAX_IDLE_MS);
    }

    private void cancelMaxIdle() {
        handler.removeCallbacks(maxIdleRunnable);
    }

    /**
     * Apply dim/bright and which idle timers may run.
     * Does not itself restart the 10-minute countdown.
     */
    private void applyDisplayPolicy() {
        boolean wantBright = dreaming && !exiting && (keepScreenOn || sessionAwake);
        setScreenBright(wantBright);
        if (keepScreenOn) {
            cancelReDim();
            cancelMaxIdle();
        } else if (disableAutoDim || !wantBright) {
            cancelReDim();
        }
    }

    private void resetSessionDisplayState() {
        sessionAwake = false;
        keepScreenOn = false;
        disableAutoDim = false;
        hideChrome(false);
    }

    private void inflateSessionChrome(FrameLayout.LayoutParams matchParent) {
        chromeRoot = LayoutInflater.from(this).inflate(R.layout.dream_session_chrome, rootLayout, false);
        rootLayout.addView(chromeRoot, matchParent);
        gearButton = chromeRoot.findViewById(R.id.dream_gear);
        sessionSheet = chromeRoot.findViewById(R.id.dream_session_sheet);
        keepScreenOnSwitch = chromeRoot.findViewById(R.id.dream_keep_screen_on);
        disableAutoDimSwitch = chromeRoot.findViewById(R.id.dream_disable_auto_dim);
        View unlockRow = chromeRoot.findViewById(R.id.dream_unlock);
        if (gearButton != null) {
            gearButton.setAlpha(0f);
            gearButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onGearClicked();
                }
            });
        }
        if (chromeRoot != null) {
            chromeRoot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (sheetExpanded) {
                        hideChrome(true);
                    }
                }
            });
            // setOnClickListener makes the root clickable; empty taps must reach
            // the fade overlay unless the sheet is open.
            chromeRoot.setClickable(false);
        }
        if (keepScreenOnSwitch != null) {
            keepScreenOnSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (updatingChromeUi) return;
                    keepScreenOn = isChecked;
                    if (keepScreenOn) {
                        sessionAwake = true;
                    }
                    applyDisplayPolicy();
                    if (!keepScreenOn) {
                        scheduleMaxIdle();
                        maybeScheduleReDim();
                    }
                    noteChromeActivity();
                }
            });
        }
        if (disableAutoDimSwitch != null) {
            disableAutoDimSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (updatingChromeUi) return;
                    disableAutoDim = isChecked;
                    if (disableAutoDim) {
                        sessionAwake = true;
                    }
                    applyDisplayPolicy();
                    if (!disableAutoDim) {
                        maybeScheduleReDim();
                    }
                    noteChromeActivity();
                }
            });
        }
        if (unlockRow != null) {
            unlockRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestUserUnlock();
                }
            });
        }
    }

    private void onGearClicked() {
        if (exiting || !dreaming) return;
        noteChromeActivity();
        if (!chromeVisible) {
            sessionAwake = true;
            applyDisplayPolicy();
            showChrome(true);
            return;
        }
        if (sheetExpanded) {
            collapseSheet();
            scheduleChromeAutoHide();
        } else {
            expandSheet();
        }
    }

    private void showChrome(boolean expandSheet) {
        if (!dreaming || exiting || chromeRoot == null || gearButton == null) return;
        chromeVisible = true;
        noteChromeActivity();
        cancelChromeAutoHide();
        gearButton.animate().cancel();
        gearButton.setVisibility(View.VISIBLE);
        gearButton.animate()
                .alpha(1f)
                .setDuration(CHROME_FADE_MS)
                .start();
        if (expandSheet) {
            expandSheet();
        } else {
            collapseSheet();
            scheduleChromeAutoHide();
        }
    }

    private void hideChrome(boolean animate) {
        cancelChromeAutoHide();
        chromeVisible = false;
        sheetExpanded = false;
        if (chromeRoot != null) {
            chromeRoot.setClickable(false);
        }
        if (sessionSheet != null) {
            sessionSheet.animate().cancel();
            sessionSheet.setVisibility(View.GONE);
            sessionSheet.setAlpha(1f);
        }
        if (dreaming && !exiting) {
            maybeScheduleReDim();
        }
        if (gearButton == null) return;
        gearButton.animate().cancel();
        if (!animate || gearButton.getVisibility() != View.VISIBLE) {
            gearButton.setAlpha(0f);
            gearButton.setVisibility(View.INVISIBLE);
            return;
        }
        gearButton.animate()
                .alpha(0f)
                .setDuration(CHROME_FADE_MS)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (gearButton != null && !chromeVisible) {
                            gearButton.setVisibility(View.INVISIBLE);
                        }
                    }
                })
                .start();
    }

    private void expandSheet() {
        if (sessionSheet == null || chromeRoot == null) return;
        sheetExpanded = true;
        chromeRoot.setClickable(true);
        cancelChromeAutoHide();
        syncChromeWidgets();
        sessionSheet.animate().cancel();
        sessionSheet.setAlpha(0f);
        sessionSheet.setVisibility(View.VISIBLE);
        sessionSheet.animate()
                .alpha(1f)
                .setDuration(CHROME_FADE_MS)
                .start();
    }

    private void collapseSheet() {
        sheetExpanded = false;
        if (chromeRoot != null) {
            chromeRoot.setClickable(false);
        }
        if (sessionSheet == null) return;
        sessionSheet.animate().cancel();
        sessionSheet.setVisibility(View.GONE);
        sessionSheet.setAlpha(1f);
    }

    private void syncChromeWidgets() {
        updatingChromeUi = true;
        try {
            if (keepScreenOnSwitch != null) {
                keepScreenOnSwitch.setChecked(keepScreenOn);
            }
            if (disableAutoDimSwitch != null) {
                disableAutoDimSwitch.setChecked(disableAutoDim);
            }
        } finally {
            updatingChromeUi = false;
        }
    }

    private void scheduleChromeAutoHide() {
        handler.removeCallbacks(chromeAutoHideRunnable);
        if (!dreaming || exiting || !chromeVisible || sheetExpanded) return;
        handler.postDelayed(chromeAutoHideRunnable, CHROME_AUTO_HIDE_MS);
    }

    private void cancelChromeAutoHide() {
        handler.removeCallbacks(chromeAutoHideRunnable);
    }

    private void cancelChromeAnimations() {
        cancelChromeAutoHide();
        if (gearButton != null) {
            gearButton.animate().cancel();
        }
        if (sessionSheet != null) {
            sessionSheet.animate().cancel();
        }
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
     * Runs even when the user asked this session to keep the screen on.
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
