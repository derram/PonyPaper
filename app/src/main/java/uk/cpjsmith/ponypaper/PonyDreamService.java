package uk.cpjsmith.ponypaper;

import android.content.Context;
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

/**
 * Optional screensaver (Daydream) host. Uses the same {@link PonySceneController}
 * as the live wallpaper so scene, prefs, and power policy stay in one place.
 *
 * <p>When the dream runs is decided entirely by the system (typically after screen
 * timeout while charging or docked — see Display → Screen saver → When to start).
 * This service only controls content and interactive exit.
 *
 * <p>Interactive so hold-to-drag works. Starts dimmed for dock/idle power use.
 * A real tap/swipe that is not a completed long-press drag brightens the screen
 * if dimmed (dream continues); the same gesture exits only when already bright
 * ({@link #finish()}). Back always exits after the start grace window.
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reDimRunnable = new Runnable() {
        @Override
        public void run() {
            if (dreaming && isScreenBright()) {
                setScreenBright(false);
            }
        }
    };
    private PonySceneController controller;
    private SurfaceView surfaceView;
    private boolean dreaming = false;
    private boolean surfaceReady = false;
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

        surfaceView = new SurfaceView(this);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surfaceReady = true;
                updateActive();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                surfaceReady = true;
                if (controller != null) {
                    controller.onSurfaceSizeChanged();
                }
                updateActive();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surfaceReady = false;
                if (controller != null) {
                    controller.setActive(false);
                }
            }
        });
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (controller != null) {
                    controller.onTouchEvent(event);
                }
                int action = event.getActionMasked();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        gestureDown = true;
                        // Defer re-dim until the finger is up so a long drag cannot dim mid-gesture.
                        cancelReDim();
                        break;
                    case MotionEvent.ACTION_UP:
                        // Only act on a complete user gesture we saw from DOWN.
                        // Never finish on orphan UP or on CANCEL (window transitions).
                        if (gestureDown && canDismissFromTouch()) {
                            if (controller == null || !controller.didDragThisGesture()) {
                                gestureDown = false;
                                if (isScreenBright()) {
                                    finish();
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
        setContentView(surfaceView);
        surfaceView.requestFocus();

        controller = new PonySceneController(this, handler, this);
        controller.start();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreaming = true;
        dreamingStartedAtMs = SystemClock.uptimeMillis();
        gestureDown = false;
        // Always start dimmed; a prior bright session must not stick across dreams.
        setScreenBright(false);
        cancelReDim();
        updateActive();
        if (surfaceView != null) {
            surfaceView.requestFocus();
        }
    }

    @Override
    public void onDreamingStopped() {
        dreaming = false;
        dreamingStartedAtMs = 0;
        gestureDown = false;
        cancelReDim();
        if (controller != null) {
            controller.setActive(false);
        }
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreaming = false;
        dreamingStartedAtMs = 0;
        gestureDown = false;
        surfaceReady = false;
        cancelReDim();
        if (controller != null) {
            controller.stop();
            controller = null;
        }
        surfaceView = null;
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            int code = event.getKeyCode();
            // HOME is often not delivered to apps; BACK/ESCAPE still help on docks.
            // Back exits regardless of dim/bright — only taps use the two-step wake.
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (canDismissFromTouch()) {
                    finish();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Whether a user-driven dismiss / brighten is allowed. Blocks the brief window
     * after the dream starts so attach-time input noise cannot call {@link #finish()}
     * or flip brightness.
     */
    private boolean canDismissFromTouch() {
        if (!dreaming || dreamingStartedAtMs == 0) return false;
        return SystemClock.uptimeMillis() - dreamingStartedAtMs >= DISMISS_GRACE_MS;
    }

    private void scheduleReDim() {
        handler.removeCallbacks(reDimRunnable);
        handler.postDelayed(reDimRunnable, RE_DIM_IDLE_MS);
    }

    private void cancelReDim() {
        handler.removeCallbacks(reDimRunnable);
    }

    private void updateActive() {
        if (controller == null) return;
        controller.setActive(dreaming && surfaceReady);
    }

    // --- FrameSurface ---

    @Override
    public SurfaceHolder getSurfaceHolder() {
        return surfaceView != null ? surfaceView.getHolder() : null;
    }

    @Override
    public boolean isDrawingEnabled() {
        return dreaming && surfaceReady;
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
}
