package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/**
 * Live wallpaper host. Rendering, prefs, power, and thermal policy live in
 * {@link PonySceneController}; this class only maps engine lifecycle.
 * Thermal emergency freezes animation in the controller (no host action needed).
 */
public class PonyWallpaper extends WallpaperService {

    private class PonyEngine extends Engine implements PonySceneController.FrameSurface {

        private final PonySceneController controller;
        private boolean isVisible = false;
        private float xOffset = 0.5f;
        private float yOffset = 0.5f;

        private PonyEngine() {
            // Live wallpaper engines do not receive touch events unless enabled.
            setTouchEventsEnabled(true);
            controller = new PonySceneController(PonyWallpaper.this, handler, this);
            controller.start();
        }

        @Override
        public SurfaceHolder getSurfaceHolder() {
            return super.getSurfaceHolder();
        }

        @Override
        public boolean isDrawingEnabled() {
            return isVisible;
        }

        @Override
        public Context getContext() {
            return PonyWallpaper.this;
        }

        @Override
        public float getBackgroundXOffset() {
            return xOffset;
        }

        @Override
        public float getBackgroundYOffset() {
            return yOffset;
        }

        @Override
        public boolean shouldShowClock() {
            return false;
        }

        @Override
        public boolean shouldShowClockDate() {
            return false;
        }

        @Override
        public void onDestroy() {
            controller.stop();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            isVisible = visible;
            controller.setActive(visible);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep,
                int xPixelOffset, int yPixelOffset) {
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            controller.onSurfaceSizeChanged();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            isVisible = false;
            controller.setActive(false);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            controller.onTouchEvent(event);
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public Engine onCreateEngine() {
        PonySize.ensureDefault(this);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        return new PonyEngine();
    }
}
