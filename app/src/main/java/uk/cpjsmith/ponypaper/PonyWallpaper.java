package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.view.Display;
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
        private boolean surfaceReady = false;
        private float xOffset = 0.5f;
        private float yOffset = 0.5f;

        private PonyEngine() {
            // Live wallpaper engines do not receive touch events unless enabled.
            setTouchEventsEnabled(true);
            controller = new PonySceneController(PonyWallpaper.this, handler, this);
            controller.start();
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            // lockHardwareCanvas expects an 8888 buffer. RGB_565 was a software-fill
            // bandwidth win; keep software lockCanvas as the runtime fallback only.
            surfaceHolder.setFormat(PixelFormat.RGBA_8888);
            // isPreview() NPEs in the Engine constructor (wrapper not attached yet).
            controller.setPreviewEngine(isPreview());
        }

        @Override
        public SurfaceHolder getSurfaceHolder() {
            return super.getSurfaceHolder();
        }

        @Override
        public boolean isDrawingEnabled() {
            return isVisible && surfaceReady;
        }

        @Override
        public Context getContext() {
            return PonyWallpaper.this;
        }

        @Override
        public Display getHostDisplay() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Context displayContext = getDisplayContext();
                if (displayContext != null) {
                    Display display = TargetFps.displayFor(displayContext);
                    if (display != null) return display;
                }
            }
            return TargetFps.displayFor(PonyWallpaper.this);
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
        public boolean shouldHintSurfaceFrameRate() {
            // DEFAULT compatibility only (see SurfaceFrameRateSupport). Cleared on hide.
            return true;
        }

        @Override
        public boolean shouldLockHardwareCanvas() {
            // Prefer GPU composition; PonySceneController falls back to lockCanvas.
            return true;
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
            updateActive();
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep,
                int xPixelOffset, int yPixelOffset) {
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            controller.onOffsetsChanged();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceReady = true;
            // New BLAST buffer: allow another hardware-canvas attempt after a prior fallback.
            controller.allowHardwareCanvasRetry();
            updateActive();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceReady = true;
            controller.allowHardwareCanvasRetry();
            controller.onSurfaceSizeChanged();
            updateActive();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            surfaceReady = false;
            // Drop setFrameRate before the holder dies so the next show does not stall.
            controller.setActive(false);
        }

        private void updateActive() {
            controller.setActive(isVisible && surfaceReady);
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
        TargetFps.ensureDefault(this);
        PonySceneController.ensureIdleTimeoutDefault(this);
        PrefDefaults.apply(this);
        return new PonyEngine();
    }
}
