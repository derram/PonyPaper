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
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import java.io.File;

/**
 * Host-agnostic scene controller: frame loop, power policy, background, and
 * pony herd. Used by both the live wallpaper engine and the dream (screensaver).
 */
public class PonySceneController implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Preference key for {@link #DEFAULT_TARGET_FPS} and allowed list values. */
    static final String PREF_TARGET_FPS = "pref_target_fps";
    /** When true (default), system Battery Saver caps FPS, pony count, and image backgrounds. */
    static final String PREF_RESPECT_BATTERY_SAVER = "pref_respect_battery_saver";
    /** When true, use {@link #DEFAULT_TARGET_FPS} while the device is on battery power. */
    static final String PREF_BATTERY_DEFAULT_FPS = "pref_battery_default_fps";
    /** When true, use {@link #DEFAULT_NUM_PONIES} while the device is on battery power. */
    static final String PREF_BATTERY_DEFAULT_PONIES = "pref_battery_default_ponies";
    /** When true, replace image backgrounds with a solid colour while on battery. */
    static final String PREF_BATTERY_DISABLE_BACKGROUND = "pref_battery_disable_background";
    /** Preference key for the user's preferred number of on-screen ponies. */
    static final String PREF_NUM_PONIES = "pref_num_ponies";
    /** When true, the dream draws a large digital clock over the scene. */
    static final String PREF_DREAM_SHOW_CLOCK = "pref_dream_show_clock";
    /** When true (and the clock is shown), draw the date under the time. */
    static final String PREF_DREAM_SHOW_DATE = "pref_dream_show_date";
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

    /**
     * Provides the surface and host-specific drawing state for the controller.
     */
    public interface FrameSurface {
        SurfaceHolder getSurfaceHolder();
        boolean isDrawingEnabled();
        Context getContext();
        float getBackgroundXOffset();
        float getBackgroundYOffset();

        /**
         * Whether to draw the Everyday Clock–style digital overlay. Wallpaper
         * hosts return false; the dream returns the screen-saver preference.
         */
        boolean shouldShowClock();

        /**
         * When {@link #shouldShowClock()} is true, whether to draw the date line
         * under the time.
         */
        boolean shouldShowClockDate();
    }

    private final Context appContext;
    private final Handler handler;
    private final FrameSurface surface;

    private Ponies ponies = null;
    private Bitmap background = null;
    private boolean drunkMode = false;
    private final Paint paint = new Paint();
    private final DreamClock dreamClock = new DreamClock();
    private int backgroundColour = 0;
    private int drunkElapsedMs = 0;
    /** Delay between draw callbacks; derived from {@link #PREF_TARGET_FPS}. */
    private int framePeriodMs = 1000 / DEFAULT_TARGET_FPS;
    /** Last known system Battery Saver state. */
    private boolean powerSaveMode = false;
    /** True when the device is not plugged in (running on battery). */
    private boolean onBattery = true;

    private boolean active = false;
    private boolean started = false;
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
            applyPowerPolicyState(isSystemPowerSaveMode(), onBattery);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (!Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;
            applyPowerPolicyState(powerSaveMode, isOnBattery(intent));
        }
    };

    public PonySceneController(Context context, Handler handler, FrameSurface surface) {
        this.appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.handler = handler;
        this.surface = surface;
    }

    /**
     * Registers preference and power listeners. Safe to call once per controller lifetime.
     */
    public void start() {
        if (started) return;
        started = true;
        getPreferences().registerOnSharedPreferenceChangeListener(this);
        powerSaveMode = isSystemPowerSaveMode();
        onBattery = isOnBattery(null);
        applyTargetFps(getPreferences());
        registerPowerReceivers();
    }

    /**
     * Unregisters listeners, stops the frame loop, and releases scene resources.
     */
    public void stop() {
        if (!started) return;
        started = false;
        active = false;
        try {
            appContext.unregisterReceiver(powerSaveReceiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered or never registered.
        }
        try {
            appContext.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered or never registered.
        }
        getPreferences().unregisterOnSharedPreferenceChangeListener(this);
        handler.removeCallbacks(drawFrameCallback);
        ponies = null;
        background = null;
    }

    /**
     * Starts or stops the draw loop. Wallpaper maps visibility here; dream maps
     * dreaming + surface-ready.
     */
    public void setActive(boolean active) {
        if (!active) {
            this.active = false;
            handler.removeCallbacks(drawFrameCallback);
            return;
        }
        // Sync in case Battery Saver or plug state changed while inactive.
        applyPowerPolicyState(isSystemPowerSaveMode(), isOnBattery(null));
        this.active = true;
        lastFrameUptimeMs = 0;
        drawFrame();
    }

    /**
     * Call when the host surface size changes. Resets pony positions and
     * redraws if the scene is active.
     */
    public void onSurfaceSizeChanged() {
        if (ponies != null) ponies.reset();
        if (drunkMode) {
            drunkElapsedMs = 0;
            backgroundColour = 0xff333333;
            paint.setAlpha(0xff);
        }
        lastFrameUptimeMs = 0;
        if (active) {
            drawFrame();
        }
    }

    public void onTouchEvent(MotionEvent event) {
        if (ponies != null) ponies.onTouchEvent(event);
    }

    /**
     * Whether the current gesture long-press-dragged a pony. Dream hosts use this
     * to dismiss on tap/swipe while leaving an active drag alone.
     */
    public boolean didDragThisGesture() {
        return ponies != null && ponies.didDragThisGesture();
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    private boolean isSystemPowerSaveMode() {
        PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }

    /**
     * Whether the device is running on battery (not AC/USB/wireless charging).
     * Pass a sticky {@link Intent#ACTION_BATTERY_CHANGED} intent when available;
     * otherwise a sticky broadcast is queried.
     */
    private boolean isOnBattery(Intent batteryStatus) {
        Intent status = batteryStatus;
        if (status == null) {
            status = appContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
        if (status == null) return true; // Assume battery if unknown.
        int plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        return plugged == 0;
    }

    private boolean shouldApplyBatterySaverLimits(SharedPreferences prefs) {
        return powerSaveMode && prefs.getBoolean(PREF_RESPECT_BATTERY_SAVER, true);
    }

    private boolean shouldUseDefaultFpsOnBattery(SharedPreferences prefs) {
        return onBattery && prefs.getBoolean(PREF_BATTERY_DEFAULT_FPS, false);
    }

    private boolean shouldUseDefaultPoniesOnBattery(SharedPreferences prefs) {
        return onBattery && prefs.getBoolean(PREF_BATTERY_DEFAULT_PONIES, false);
    }

    private boolean shouldDisableBackgroundImage(SharedPreferences prefs) {
        if (shouldApplyBatterySaverLimits(prefs)) return true;
        return onBattery && prefs.getBoolean(PREF_BATTERY_DISABLE_BACKGROUND, false);
    }

    private int getEffectivePonyCount(SharedPreferences prefs) {
        int count = prefs.getInt(PREF_NUM_PONIES, DEFAULT_NUM_PONIES);
        if (count < 1) count = DEFAULT_NUM_PONIES;
        if (shouldApplyBatterySaverLimits(prefs)) {
            count = Math.min(count, BATTERY_SAVER_MAX_PONIES);
        }
        if (shouldUseDefaultPoniesOnBattery(prefs)) {
            count = Math.min(count, DEFAULT_NUM_PONIES);
        }
        return count;
    }

    private void registerPowerReceivers() {
        IntentFilter powerSaveFilter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(powerSaveReceiver, powerSaveFilter, Context.RECEIVER_NOT_EXPORTED);
            appContext.registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(powerSaveReceiver, powerSaveFilter);
            appContext.registerReceiver(batteryReceiver, batteryFilter);
        }
    }

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
        if (shouldUseDefaultFpsOnBattery(prefs)) {
            fps = Math.min(fps, DEFAULT_TARGET_FPS);
        }
        framePeriodMs = Math.max(1, 1000 / fps);
    }

    private int powerPolicySignature(SharedPreferences prefs) {
        int sig = 0;
        if (shouldApplyBatterySaverLimits(prefs)) sig |= 1;
        if (shouldUseDefaultFpsOnBattery(prefs)) sig |= 2;
        if (shouldUseDefaultPoniesOnBattery(prefs)) sig |= 4;
        if (shouldDisableBackgroundImage(prefs)) sig |= 8;
        return sig;
    }

    private void applyPowerPolicyState(boolean newPowerSaveMode, boolean newOnBattery) {
        if (powerSaveMode == newPowerSaveMode && onBattery == newOnBattery) return;
        SharedPreferences prefs = getPreferences();
        int wasSig = powerPolicySignature(prefs);
        int wasEffective = getEffectivePonyCount(prefs);

        powerSaveMode = newPowerSaveMode;
        onBattery = newOnBattery;

        int nowSig = powerPolicySignature(prefs);
        int nowEffective = getEffectivePonyCount(prefs);

        applyTargetFps(prefs);

        // Rebuild when herd size or image-background policy changes.
        if (ponies == null || wasEffective != nowEffective || wasSig != nowSig) {
            ponies = null;
        }

        handler.removeCallbacks(drawFrameCallback);
        if (active) {
            lastFrameUptimeMs = 0;
            drawFrame();
        }
    }

    private void reapplyPowerProfilePrefs(SharedPreferences prefs) {
        applyTargetFps(prefs);
        ponies = null;
        handler.removeCallbacks(drawFrameCallback);
        if (active) {
            lastFrameUptimeMs = 0;
            drawFrame();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (PREF_TARGET_FPS.equals(key)) {
            applyTargetFps(prefs);
            handler.removeCallbacks(drawFrameCallback);
            if (active) {
                lastFrameUptimeMs = 0;
                drawFrame();
            }
            return;
        }
        if (PREF_RESPECT_BATTERY_SAVER.equals(key)
                || PREF_BATTERY_DEFAULT_FPS.equals(key)
                || PREF_BATTERY_DEFAULT_PONIES.equals(key)
                || PREF_BATTERY_DISABLE_BACKGROUND.equals(key)) {
            reapplyPowerProfilePrefs(prefs);
            return;
        }
        ponies = null;
    }

    private void drawFrame() {
        final SurfaceHolder holder = surface.getSurfaceHolder();
        if (holder == null) {
            return;
        }
        // Prefer host flag; also respect surface-reported drawing enable.
        if (!active && !surface.isDrawingEnabled()) {
            return;
        }

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
                    ponies = new Ponies(surface.getContext(), prefs, getEffectivePonyCount(prefs));

                    background = null;
                    drunkMode = prefs.getBoolean("pref_drunk_mode", false);
                    drunkElapsedMs = 0;
                    backgroundColour = 0xff333333;
                    paint.setAlpha(0xff);
                    // Under Battery Saver / on-battery profile, keep a solid colour
                    // instead of decoding and blitting a full-screen image each frame.
                    if (prefs.getBoolean("pref_background", false)
                            && !shouldDisableBackgroundImage(prefs)) {
                        File filesDir = surface.getContext().getExternalFilesDir(null);
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
                    drunkElapsedMs += (int) deltaMs;
                    if (drunkElapsedMs >= DRUNK_FADE_MS) {
                        backgroundColour = 0x33333333;
                        paint.setAlpha(0x33);
                    }
                }
                float xOffset = surface.getBackgroundXOffset();
                float yOffset = surface.getBackgroundYOffset();
                if (background != null) {
                    Rect srcRect = new Rect(0, 0, background.getWidth(), background.getHeight());
                    Rect cb = c.getClipBounds();
                    float scale = Math.max((float) cb.height() / (float) srcRect.height(),
                            (float) cb.width() / (float) srcRect.width());
                    RectF dstRect = new RectF((cb.width() - srcRect.width() * scale) * xOffset,
                            (cb.height() - srcRect.height() * scale) * yOffset,
                            (cb.width() - srcRect.width() * scale) * xOffset + srcRect.width() * scale,
                            (cb.height() - srcRect.height() * scale) * yOffset + srcRect.height() * scale);
                    c.drawBitmap(background, srcRect, dstRect, paint);
                } else {
                    c.drawColor(backgroundColour);
                }
                ponies.drawAndUpdate(c, deltaMs);
                // Clock on top so digits stay readable over ponies / backgrounds.
                if (surface.shouldShowClock()) {
                    dreamClock.draw(c, surface.getContext(), surface.shouldShowClockDate());
                }
            }
        } finally {
            if (c != null) holder.unlockCanvasAndPost(c);
        }

        // Reschedule the next redraw at the effective target rate.
        handler.removeCallbacks(drawFrameCallback);
        if (active && surface.isDrawingEnabled()) {
            handler.postDelayed(drawFrameCallback, framePeriodMs);
        }
    }
}
