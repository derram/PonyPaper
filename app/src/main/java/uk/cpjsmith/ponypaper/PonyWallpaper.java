package uk.cpjsmith.ponypaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import java.io.File;

public class PonyWallpaper extends WallpaperService {
    
    /** Preference key for {@link #DEFAULT_TARGET_FPS} and allowed list values. */
    static final String PREF_TARGET_FPS = "pref_target_fps";
    /** When true (default), system Battery Saver caps FPS, pony count, and image backgrounds. */
    static final String PREF_RESPECT_BATTERY_SAVER = "pref_respect_battery_saver";
    /** Preference key for the user's preferred number of on-screen ponies. */
    static final String PREF_NUM_PONIES = "pref_num_ponies";
    /** Battery-friendly default; motion uses delta time so speed stays consistent. */
    static final int DEFAULT_TARGET_FPS = 30;
    /** Default pony count when the preference is missing. */
    static final int DEFAULT_NUM_PONIES = 4;
    /** Hard ceiling so a corrupt preference cannot schedule a tight spin loop. */
    private static final int MAX_TARGET_FPS = 120;
    private static final int MIN_TARGET_FPS = 1;
    /** Cap one-frame jumps after pause so motion does not teleport. */
    private static final long MAX_DELTA_MS = 100;
    /** Original Berry Punch fade took ~3 frames at 25 FPS. */
    private static final int DRUNK_FADE_MS = 120;
    /** Maximum FPS while system Battery Saver is active. */
    private static final int BATTERY_SAVER_MAX_FPS = 25;
    /** Maximum on-screen ponies while system Battery Saver is active. */
    private static final int BATTERY_SAVER_MAX_PONIES = 3;
    
    private class PonyEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        
        private Ponies ponies = null;
        private Bitmap background = null;
        private float xOffset = 0.5f;
        private float yOffset = 0.5f;
        private boolean drunkMode = false;
        private Paint paint = null;
        private int backgroundColour = 0;
        private int drunkElapsedMs = 0;
        /** Delay between draw callbacks; derived from {@link #PREF_TARGET_FPS}. */
        private int framePeriodMs = 1000 / DEFAULT_TARGET_FPS;
        /** Last known system Battery Saver state (see {@link PowerManager#isPowerSaveMode()}). */
        private boolean powerSaveMode = false;
        
        private boolean isVisible = false;
        private long lastFrameUptimeMs = 0;
        private final Runnable drawFrameCallback = new Runnable() {
            public void run() {
                drawFrame();
            }
        };
        
        private final BroadcastReceiver powerSaveReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                if (!PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) return;
                applyPowerSaveMode(isSystemPowerSaveMode());
            }
        };
        
        private PonyEngine() {
            // Live wallpaper engines do not receive touch events unless enabled.
            setTouchEventsEnabled(true);
            getPreferences().registerOnSharedPreferenceChangeListener(this);
            paint = new Paint();
            powerSaveMode = isSystemPowerSaveMode();
            applyTargetFps(getPreferences());
            registerPowerSaveReceiver();
        }
        
        private SharedPreferences getPreferences() {
            return PreferenceManager.getDefaultSharedPreferences(PonyWallpaper.this);
        }
        
        private boolean isSystemPowerSaveMode() {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isPowerSaveMode();
        }
        
        /**
         * Whether Battery Saver limits should be applied right now. Requires both
         * system power-save mode and the user preference to respect it.
         */
        private boolean shouldApplyBatterySaverLimits(SharedPreferences prefs) {
            return powerSaveMode && prefs.getBoolean(PREF_RESPECT_BATTERY_SAVER, true);
        }
        
        /**
         * Effective on-screen pony count: user preference, optionally capped under
         * Battery Saver.
         */
        private int getEffectivePonyCount(SharedPreferences prefs) {
            int count = prefs.getInt(PREF_NUM_PONIES, DEFAULT_NUM_PONIES);
            if (count < 1) count = DEFAULT_NUM_PONIES;
            if (shouldApplyBatterySaverLimits(prefs)) {
                count = Math.min(count, BATTERY_SAVER_MAX_PONIES);
            }
            return count;
        }
        
        private void registerPowerSaveReceiver() {
            IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(powerSaveReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(powerSaveReceiver, filter);
            }
        }
        
        /**
         * Reads the target FPS preference and updates {@link #framePeriodMs}.
         * Motion is delta-time based, so changing FPS only affects smoothness and battery.
         * Under Battery Saver the rate is capped at {@link #BATTERY_SAVER_MAX_FPS}.
         */
        private void applyTargetFps(SharedPreferences prefs) {
            int fps = DEFAULT_TARGET_FPS;
            try {
                fps = Integer.parseInt(prefs.getString(PREF_TARGET_FPS,
                        Integer.toString(DEFAULT_TARGET_FPS)));
            } catch (NumberFormatException e) {
                fps = DEFAULT_TARGET_FPS;
            }
            if (fps < MIN_TARGET_FPS) fps = DEFAULT_TARGET_FPS;
            if (fps > MAX_TARGET_FPS) fps = MAX_TARGET_FPS;
            if (shouldApplyBatterySaverLimits(prefs)) {
                fps = Math.min(fps, BATTERY_SAVER_MAX_FPS);
            }
            framePeriodMs = Math.max(1, 1000 / fps);
        }
        
        /**
         * Applies a change in system Battery Saver state: cap FPS, drop image
         * backgrounds for a solid colour, and rebuild the pony set if the
         * effective count would change.
         */
        private void applyPowerSaveMode(boolean enabled) {
            if (powerSaveMode == enabled) return;
            SharedPreferences prefs = getPreferences();
            boolean wasApplying = shouldApplyBatterySaverLimits(prefs);
            powerSaveMode = enabled;
            boolean nowApplying = shouldApplyBatterySaverLimits(prefs);
            
            applyTargetFps(prefs);
            
            int effective = getEffectivePonyCount(prefs);
            // Rebuild when the herd size changes, or when image-background policy
            // toggles (solid colour under saver; restore the bitmap when leaving).
            if (ponies == null || ponies.getActiveCount() != effective
                    || wasApplying != nowApplying) {
                ponies = null;
            }
            
            handler.removeCallbacks(drawFrameCallback);
            if (isVisible) {
                lastFrameUptimeMs = 0;
                drawFrame();
            }
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (PREF_TARGET_FPS.equals(key)) {
                applyTargetFps(prefs);
                // Reschedule immediately at the new rate without rebuilding ponies.
                handler.removeCallbacks(drawFrameCallback);
                if (isVisible) {
                    lastFrameUptimeMs = 0;
                    drawFrame();
                }
                return;
            }
            if (PREF_RESPECT_BATTERY_SAVER.equals(key)) {
                // Re-evaluate caps and background policy without waiting for a
                // system broadcast. Always rebuild so image vs solid colour matches.
                applyTargetFps(prefs);
                ponies = null;
                handler.removeCallbacks(drawFrameCallback);
                if (isVisible) {
                    lastFrameUptimeMs = 0;
                    drawFrame();
                }
                return;
            }
            ponies = null;
        }
        
        @Override
        public void onDestroy() {
            try {
                unregisterReceiver(powerSaveReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered or never registered.
            }
            getPreferences().unregisterOnSharedPreferenceChangeListener(this);
            handler.removeCallbacks(drawFrameCallback);
            ponies = null;
            background = null;
            super.onDestroy();
        }
        
        @Override
        public void onVisibilityChanged(boolean visible) {
            isVisible = visible;
            if (visible) {
                // Sync in case Battery Saver changed while we were not drawing.
                applyPowerSaveMode(isSystemPowerSaveMode());
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
            long deltaMs = framePeriodMs;
            if (lastFrameUptimeMs != 0) {
                deltaMs = now - lastFrameUptimeMs;
                if (deltaMs < 0) deltaMs = framePeriodMs;
                if (deltaMs > MAX_DELTA_MS) deltaMs = MAX_DELTA_MS;
            }
            lastFrameUptimeMs = now;
            
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                // Surface may be lost or not yet ready; never touch a null/zero canvas.
                if (c != null && c.getWidth() > 0 && c.getHeight() > 0) {
                    if (ponies == null) {
                        SharedPreferences prefs = getPreferences();
                        applyTargetFps(prefs);
                        ponies = new Ponies(PonyWallpaper.this, prefs, getEffectivePonyCount(prefs));
                        
                        background = null;
                        drunkMode = prefs.getBoolean("pref_drunk_mode", false);
                        drunkElapsedMs = 0;
                        backgroundColour = 0xff333333;
                        paint.setAlpha(0xff);
                        // Under Battery Saver, keep a solid colour instead of decoding
                        // and blitting a full-screen image each frame.
                        if (prefs.getBoolean("pref_background", false)
                                && !shouldApplyBatterySaverLimits(prefs)) {
                            File filesDir = getExternalFilesDir(null);
                            if (filesDir != null) {
                                File bgFile = new File(filesDir, "background");
                                if (bgFile.exists()) {
                                    BitmapFactory.Options bfo = new BitmapFactory.Options();
                                    bfo.inScaled = false;
                                    bfo.inJustDecodeBounds = true;
                                    BitmapFactory.decodeFile(bgFile.toString(), bfo);
                                    int h = bfo.outHeight, w = bfo.outWidth;
                                    int canvasH = c.getHeight();
                                    int canvasW = c.getWidth();
                                    int scale = 1;
                                    if (h > 0 && w > 0) {
                                        scale = Math.min(h / canvasH, w / canvasW);
                                        if (scale < 1) scale = 1;
                                    }
                                    scale *= prefs.getInt("pref_pixelation", 1);
                                    if (scale < 1) scale = 1;
                                    bfo.inJustDecodeBounds = false;
                                    bfo.inSampleSize = scale;
                                    background = BitmapFactory.decodeFile(bgFile.toString(), bfo);
                                }
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
            
            // Reschedule the next redraw at the effective target rate.
            handler.removeCallbacks(drawFrameCallback);
            if (isVisible) handler.postDelayed(drawFrameCallback, framePeriodMs);
        }
        
    }
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    @Override
    public Engine onCreateEngine() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        return new PonyEngine();
    }
    
}
