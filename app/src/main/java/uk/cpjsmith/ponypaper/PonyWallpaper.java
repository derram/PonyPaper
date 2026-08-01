package uk.cpjsmith.ponypaper;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import java.io.File;

public class PonyWallpaper extends WallpaperService {
    
    /**
     * Target redraw rate. 60 FPS is the modern baseline for smooth animation
     * without chasing high-refresh panels (90/120) that cost battery for little
     * gain on a soft-pixel live wallpaper.
     */
    static final int TARGET_FPS = 60;
    private static final int FRAME_PERIOD_MS = 1000 / TARGET_FPS;
    /** Cap one-frame jumps after pause so motion does not teleport. */
    private static final long MAX_DELTA_MS = 100;
    /** Original Berry Punch fade took ~3 frames at 25 FPS. */
    private static final int DRUNK_FADE_MS = 120;
    
    private class PonyEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        
        private Ponies ponies = null;
        private Bitmap background = null;
        private float xOffset = 0.5f;
        private float yOffset = 0.5f;
        private boolean drunkMode = false;
        private Paint paint = null;
        private int backgroundColour = 0;
        private int drunkElapsedMs = 0;
        
        private boolean isVisible = false;
        private long lastFrameUptimeMs = 0;
        private final Runnable drawFrameCallback = new Runnable() {
            public void run() {
                drawFrame();
            }
        };
        
        private PonyEngine() {
            // Live wallpaper engines do not receive touch events unless enabled.
            setTouchEventsEnabled(true);
            getPreferences().registerOnSharedPreferenceChangeListener(this);
            paint = new Paint();
        }
        
        private SharedPreferences getPreferences() {
            return PreferenceManager.getDefaultSharedPreferences(PonyWallpaper.this);
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            ponies = null;
        }
        
        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(drawFrameCallback);
        }
        
        @Override
        public void onVisibilityChanged(boolean visible) {
            isVisible = visible;
            if (visible) {
                lastFrameUptimeMs = 0;
                drawFrame();
            } else {
                handler.removeCallbacks(drawFrameCallback);
            }
        }
        
        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }
        
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            if (ponies != null) ponies.reset();
            if (drunkMode) {
                drunkElapsedMs = 0;
                backgroundColour = 0xff333333;
                paint.setAlpha(0xff);
            }
            lastFrameUptimeMs = 0;
            drawFrame();
        }
        
        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            isVisible = false;
            handler.removeCallbacks(drawFrameCallback);
        }
        
        @Override
        public void onTouchEvent(MotionEvent event) {
            if (ponies != null) ponies.onTouchEvent(event);
        }
        
        private void drawFrame() {
            final SurfaceHolder holder = getSurfaceHolder();
            final long now = SystemClock.uptimeMillis();
            long deltaMs = FRAME_PERIOD_MS;
            if (lastFrameUptimeMs != 0) {
                deltaMs = now - lastFrameUptimeMs;
                if (deltaMs < 0) deltaMs = FRAME_PERIOD_MS;
                if (deltaMs > MAX_DELTA_MS) deltaMs = MAX_DELTA_MS;
            }
            lastFrameUptimeMs = now;
            
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (ponies == null) {
                    SharedPreferences prefs = getPreferences();
                    ponies = new Ponies(PonyWallpaper.this, prefs);
                    
                    background = null;
                    drunkMode = prefs.getBoolean("pref_drunk_mode", false);
                    drunkElapsedMs = 0;
                    backgroundColour = 0xff333333;
                    paint.setAlpha(0xff);
                    if (prefs.getBoolean("pref_background", false)) {
                        File bgFile = new File(getExternalFilesDir(null), "background");
                        if (bgFile.exists()) {
                            BitmapFactory.Options bfo = new BitmapFactory.Options();
                            bfo.inScaled = false;
                            bfo.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(bgFile.toString(), bfo);
                            int h = bfo.outHeight, w = bfo.outWidth;
                            int scale = Math.min(h / c.getHeight(), w / c.getWidth());
                            scale *= prefs.getInt("pref_pixelation", 1);
                            bfo.inJustDecodeBounds = false;
                            bfo.inSampleSize = scale;
                            background = BitmapFactory.decodeFile(bgFile.toString(), bfo);
                        }
                    }
                }
                if (drunkMode && paint.getAlpha() == 0xff) {
                    drunkElapsedMs += (int)deltaMs;
                    if (drunkElapsedMs >= DRUNK_FADE_MS) {
                        backgroundColour = 0x33333333;
                        paint.setAlpha(0x33);
                    }
                }
                if (c != null) {
                    if (background != null) {
                        Rect srcRect = new Rect(0, 0, background.getWidth(), background.getHeight());
                        Rect cb = c.getClipBounds();
                        float scale = Math.max((float)cb.height() / (float)srcRect.height(),
                                               (float)cb.width() / (float)srcRect.width());
                        RectF dstRect = new RectF((cb.width() - srcRect.width() * scale) * xOffset,
                                                  (cb.height() - srcRect.height() * scale) * yOffset,
                                                  (cb.width() - srcRect.width() * scale) * xOffset + srcRect.width() * scale,
                                                  (cb.height() - srcRect.height() * scale) * yOffset + srcRect.height() * scale);
                        c.drawBitmap(background, srcRect, dstRect, paint);
                    } else {
                        c.drawColor(backgroundColour);
                    }
                    ponies.drawAndUpdate(c, deltaMs);
                }
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
            
            // Reschedule the next redraw
            handler.removeCallbacks(drawFrameCallback);
            if (isVisible) handler.postDelayed(drawFrameCallback, FRAME_PERIOD_MS);
        }
        
    }
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    @Override
    public Engine onCreateEngine() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        return new PonyEngine();
    }
    
}
