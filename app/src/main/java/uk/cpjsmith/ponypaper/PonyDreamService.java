package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.service.dreams.DreamService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/**
 * Optional screensaver (Daydream) host. Uses the same {@link PonySceneController}
 * as the live wallpaper so scene, prefs, and power policy stay in one place.
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
                return true;
            }
        });
        setContentView(surfaceView);

        controller = new PonySceneController(this, handler, this);
        controller.start();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreaming = true;
        updateActive();
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
