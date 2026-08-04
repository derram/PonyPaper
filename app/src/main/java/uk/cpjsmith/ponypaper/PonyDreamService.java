package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
 * <p>Interactive so hold-to-drag works. Exit with Back, or any tap/swipe that is
 * not a completed long-press drag ({@link #finish()}).
 */
public class PonyDreamService extends DreamService implements PonySceneController.FrameSurface {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PonySceneController controller;
    private SurfaceView surfaceView;
    private boolean dreaming = false;
    private boolean surfaceReady = false;

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
                // Tap, swipe, or long-press on empty space: leave the dream.
                // After a pony drag, keep running so the user can keep watching.
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (controller == null || !controller.didDragThisGesture()) {
                        finish();
                    }
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
        updateActive();
        if (surfaceView != null) {
            surfaceView.requestFocus();
        }
    }

    @Override
    public void onDreamingStopped() {
        dreaming = false;
        if (controller != null) {
            controller.setActive(false);
        }
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreaming = false;
        surfaceReady = false;
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
            if (code == KeyEvent.KEYCODE_BACK
                    || code == KeyEvent.KEYCODE_ESCAPE
                    || code == KeyEvent.KEYCODE_HOME) {
                finish();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
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
