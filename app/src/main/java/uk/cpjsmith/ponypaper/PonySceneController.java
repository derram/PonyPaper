package uk.cpjsmith.ponypaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.io.File;

/**
 * Host-agnostic scene controller: frame loop, power policy, thermal policy,
 * background, and pony herd. Used by both the live wallpaper engine and the
 * dream (screensaver).
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
    /** Packed ARGB for the solid fill behind ponies when there is no image background. */
    static final String PREF_BACKGROUND_COLOUR = "pref_background_colour";
    /** When true, decode and draw the shared background image (subject to power policy). */
    static final String PREF_BACKGROUND = "pref_background";
    /** Historical hardcoded fill; also the preference default. */
    static final int DEFAULT_BACKGROUND_COLOUR = 0xff333333;
    /** Preference key for the user's preferred number of on-screen ponies. */
    static final String PREF_NUM_PONIES = "pref_num_ponies";
    /**
     * When true, the dream uses {@link #PREF_DREAM_NUM_PONIES},
     * {@link #PREF_DREAM_TARGET_FPS}, and {@link #PREF_DREAM_BACKGROUND}
     * instead of the live-wallpaper display prefs. Off (default) inherits.
     */
    static final String PREF_DREAM_CUSTOM_DISPLAY = "pref_dream_custom_display";
    /** Dream pony count while {@link #PREF_DREAM_CUSTOM_DISPLAY} is on. */
    static final String PREF_DREAM_NUM_PONIES = "pref_dream_num_ponies";
    /** Dream target FPS while {@link #PREF_DREAM_CUSTOM_DISPLAY} is on. */
    static final String PREF_DREAM_TARGET_FPS = "pref_dream_target_fps";
    /** Dream background-image enable while {@link #PREF_DREAM_CUSTOM_DISPLAY} is on. */
    static final String PREF_DREAM_BACKGROUND = "pref_dream_background";
    /** When true, the dream draws a large digital clock over the scene. */
    static final String PREF_DREAM_SHOW_CLOCK = "pref_dream_show_clock";
    /** When true (and the clock is shown), draw the date under the time. */
    static final String PREF_DREAM_SHOW_DATE = "pref_dream_show_date";
    /**
     * Dream idle timeout in minutes as a string ({@code "0"} = never).
     * Missing keys use {@link #defaultIdleTimeoutMinutes()}; invalid values
     * fall back the same way.
     */
    static final String PREF_DREAM_IDLE_TIMEOUT = "pref_dream_idle_timeout";
    /**
     * First-run idle timeout on devices that already sleep after dream
     * {@code finish()}. Other OEMs stay at never until lock is granted.
     */
    static final int DEFAULT_DREAM_IDLE_MINUTES = 10;
    /** Preference value for {@link #PREF_DREAM_IDLE_TIMEOUT} meaning never. */
    static final String DREAM_IDLE_TIMEOUT_NEVER = "0";
    /** Default target; motion uses delta time so speed stays consistent. */
    static final int DEFAULT_TARGET_FPS = 60;
    /** Default pony count when the preference is missing. */
    static final int DEFAULT_NUM_PONIES = 4;
    /** Hard ceiling so a corrupt preference cannot schedule a tight spin loop. */
    private static final int MAX_TARGET_FPS = 120;
    private static final int MIN_TARGET_FPS = 1;
    /** Cap one-frame jumps after pause so motion does not teleport. */
    private static final long MAX_DELTA_MS = 100;
    /** Original Berry Punch fade took ~3 frames at 25 FPS. */
    private static final int DRUNK_FADE_MS = 120;
    /** Fill/sprite alpha after the Berry Punch fade. */
    private static final int DRUNK_FILL_ALPHA = 0x33;
    /**
     * Cap while every on-screen pony is idle. Sprite timings are centiseconds,
     * so 15 FPS still catches frame changes; moving ponies keep {@link #targetFps}.
     */
    private static final int IDLE_MAX_FPS = 15;
    /** Maximum FPS while system Battery Saver is active. */
    private static final int BATTERY_SAVER_MAX_FPS = 25;
    /** Maximum on-screen ponies while system Battery Saver is active. */
    private static final int BATTERY_SAVER_MAX_PONIES = 3;

    /**
     * Thermal status codes matching {@link PowerManager} (API 29+). Inlined so
     * pre-Q devices can use the same scale for the battery-temperature fallback.
     */
    private static final int THERMAL_NONE = 0;
    private static final int THERMAL_LIGHT = 1;
    private static final int THERMAL_MODERATE = 2;
    private static final int THERMAL_SEVERE = 3;
    private static final int THERMAL_CRITICAL = 4;
    /**
     * Maximum FPS while effective thermal status is MODERATE or SEVERE.
     * Lower than Battery Saver so the image background can stay up.
     */
    private static final int THERMAL_MODERATE_MAX_FPS = 15;
    /** Maximum on-screen ponies while effective thermal status is MODERATE or SEVERE. */
    private static final int THERMAL_MODERATE_MAX_PONIES = 2;
    /**
     * Battery {@link BatteryManager#EXTRA_TEMPERATURE} (tenths of °C) treated as
     * MODERATE when the Thermal API is unavailable or cooler than the pack.
     */
    private static final int BATTERY_TEMP_MODERATE_TENTHS = 430; // 43.0 °C
    /** Battery temperature (tenths of °C) treated as SEVERE (soft throttle). */
    private static final int BATTERY_TEMP_SEVERE_TENTHS = 460; // 46.0 °C
    /** Battery temperature (tenths of °C) treated as CRITICAL / emergency freeze. */
    private static final int BATTERY_TEMP_CRITICAL_TENTHS = 500; // 50.0 °C
    /** Sentinel: battery temperature not yet read. */
    private static final int BATTERY_TEMP_UNKNOWN = Integer.MIN_VALUE;
    /** Solid fill while animation is frozen for thermal emergency. */
    private static final int THERMAL_SAFE_COLOUR = 0xff333333;
    /** Matches {@code pref_pixelation} default in {@code preferences.xml}. */
    private static final int DEFAULT_PIXELATION = 4;
    private static final int MIN_PIXELATION = 1;
    private static final int MAX_PIXELATION = 24;

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

        /**
         * Called once when thermal emergency begins (effective status CRITICAL+).
         * Wallpaper may ignore; the dream should sleep the display
         * instead of holding a frozen screensaver.
         */
        default void onThermalHardStop() {}

        /**
         * Display this host is drawing on. Used to cap FPS at the panel's peak
         * refresh rate. Named so it does not override {@link Context#getDisplay()}
         * on windowed hosts (the dream is a {@link android.content.Context}).
         */
        default Display getHostDisplay() {
            return TargetFps.displayFor(getContext());
        }

        /**
         * Whether to call {@link Surface#setFrameRate} on this host's surface.
         * Use {@link Surface#FRAME_RATE_COMPATIBILITY_DEFAULT} only; clear the
         * vote when the host hides or freezes so a later lock does not stall.
         */
        default boolean shouldHintSurfaceFrameRate() {
            return true;
        }

        /**
         * Whether to prefer {@link SurfaceHolder#lockHardwareCanvas()} (API 26+).
         * Hosts should return true; {@link PonySceneController} falls back to
         * {@link SurfaceHolder#lockCanvas()} after a failed hardware lock until
         * the next surface recreate
         * ({@link PonySceneController#allowHardwareCanvasRetry()}).
         */
        default boolean shouldLockHardwareCanvas() {
            return true;
        }

        /**
         * Whether this host is the screen saver. Dream-only display overrides
         * ({@link PonySceneController#PREF_DREAM_CUSTOM_DISPLAY}) apply only
         * when this is true.
         */
        default boolean isDream() {
            return false;
        }
    }

    private final Context appContext;
    private final Handler handler;
    private final FrameSurface surface;

    private Ponies ponies = null;
    private Bitmap background = null;
    private boolean drunkMode = false;
    private final Paint paint = new Paint();
    private final Rect tmpSrc = new Rect();
    private final Rect tmpDst = new Rect();
    private final Rect clipRect = new Rect();
    /** When true, the next locked frame must paint even if ponies look unchanged. */
    private boolean forceSceneRedraw = true;
    private int lastBgDestLeft = Integer.MIN_VALUE;
    private int lastBgDestTop = Integer.MIN_VALUE;
    /** False after {@link HardwareCanvasSupport#lock} fails for this controller. */
    private boolean hardwareCanvasAllowed = true;
    /**
     * True while this controller holds a {@link SpriteCache#addCpuDemand} for
     * software-canvas sprite blits (HARDWARE-only sheets need a CPU reload).
     */
    private boolean cpuSpriteDemandHeld = false;
    /** Debug-APK HUD + force-path helper; unused when {@link BuildConfig#DEBUG} is false. */
    private final DebugOverlay debugOverlay = new DebugOverlay();
    /** Last FPS pushed to {@link Surface#setFrameRate}; negative means unset. */
    private float lastSurfaceFps = -1f;
    /**
     * Wallpaper-picker preview. Set from {@code Engine.onCreate} after the
     * wallpaper wrapper is attached; {@code Engine.isPreview()} NPEs in the
     * engine constructor.
     */
    private boolean previewEngine = false;
    private final DreamClock dreamClock = new DreamClock();
    /** Opaque user-chosen fill; drunk mode may paint a translucent copy. */
    private int baseBackgroundColour = DEFAULT_BACKGROUND_COLOUR;
    private int backgroundColour = DEFAULT_BACKGROUND_COLOUR;
    private int drunkElapsedMs = 0;
    /** Delay between draw callbacks; derived from {@link #PREF_TARGET_FPS}. */
    private int framePeriodMs = 1000 / DEFAULT_TARGET_FPS;
    /** Policy target FPS (idle schedule may use a longer period). */
    private int targetFps = DEFAULT_TARGET_FPS;
    /** Last known system Battery Saver state. */
    private boolean powerSaveMode = false;
    /** True when the device is not plugged in (running on battery). */
    private boolean onBattery = true;
    /**
     * Last status from {@link PowerManager#getCurrentThermalStatus()} (API 29+).
     * Stays {@link #THERMAL_NONE} on older devices.
     */
    private int apiThermalStatus = THERMAL_NONE;
    /** Last battery temperature in tenths of °C, or {@link #BATTERY_TEMP_UNKNOWN}. */
    private int batteryTempTenths = BATTERY_TEMP_UNKNOWN;
    /** True while effective thermal status warrants emergency freeze (CRITICAL+). */
    private boolean thermalEmergency = false;
    /** True while status is MODERATE (soft throttle; not emergency). */
    private boolean thermalThrottle = false;
    /** True while a background library-folder sync is running. */
    private boolean librarySyncInFlight = false;
    /** True while herd construction / background decode is running off the frame thread. */
    private boolean sceneLoadInFlight = false;
    /** Bumped by {@link #dropHerd} so a stale worker result is discarded. */
    private int sceneLoadGeneration = 0;
    /** True after a failed scene load until the next {@link #dropHerd}. */
    private boolean sceneLoadFailed = false;
    /**
     * Collapses a burst of preference notifications (one per checkbox when a
     * mix is applied) into a single unload. Runs on {@link #handler}.
     */
    private final Runnable coalescedDropHerd = new Runnable() {
        @Override
        public void run() {
            if (!started || frozen) return;
            dropHerdNow();
        }
    };
    /** Token from {@link ThermalStatusSupport#register}; typed as Object for pre-Q safety. */
    private Object thermalListenerToken = null;
    /** Peak-refresh listener; null when unregistered. */
    private DisplayManager.DisplayListener displayListener = null;

    private boolean active = false;
    private boolean started = false;
    /**
     * When true, the draw loop is paused and the last surface buffer is left
     * intact. Used by the dream during content fade-out so bitmaps are not
     * redrawn/recycled under a system window transition.
     */
    private boolean frozen = false;
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
            // Battery temp is the pre-Q thermal fallback (and a secondary elevating
            // signal when the Thermal API reports cooler than the pack).
            int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, BATTERY_TEMP_UNKNOWN);
            if (temp != batteryTempTenths) {
                Log.d("PonyPaper", "Battery temperature changed: " + temp + " (tenths °C)");
                batteryTempTenths = temp;
                recomputeThermalPolicy();
            }
        }
    };

    public PonySceneController(Context context, Handler handler, FrameSurface surface) {
        this.appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.handler = handler;
        this.surface = surface;
        // Nearest-neighbour: bilinear filtering of a large cover background is a
        // major heat source on both software and hardware canvas paths.
        paint.setFilterBitmap(false);
        paint.setDither(false);
    }

    /**
     * Re-enable hardware canvas after a surface recreate. A failed
     * {@link HardwareCanvasSupport#lock} disables it until the next buffer.
     * When recovering from software fallback, restores FPS/pony caps and
     * reloads the image background (HARDWARE-only bitmaps were dropped).
     * Debug force-software keeps the software path instead.
     */
    public void allowHardwareCanvasRetry() {
        SharedPreferences prefs = getPreferences();
        if (DebugOverlay.isForceSoftware(prefs)) {
            if (hardwareCanvasAllowed) {
                enterSoftwareCanvasFallback();
            }
            return;
        }
        boolean wasDenied = !hardwareCanvasAllowed;
        if (!wasDenied) {
            hardwareCanvasAllowed = true;
            return;
        }
        int wasEffectivePonies = getEffectivePonyCount(prefs);
        hardwareCanvasAllowed = true;
        // HW path again: drop this host's CPU sprite demand so the cache can
        // return to HARDWARE-only residency when no software host remains.
        releaseCpuSpriteDemand();
        if (!started) return;
        applyTargetFps(prefs);
        int nowEffectivePonies = getEffectivePonyCount(prefs);
        if (!frozen && wasEffectivePonies != nowEffectivePonies) {
            // Herd rebuild also reloads the background via ensureScenePrepared.
            dropHerd();
        } else {
            requestBackgroundReloadIfNeeded();
        }
    }

    /**
     * Registers preference, power, and thermal listeners. Safe to call once per
     * controller lifetime.
     */
    /**
     * Wallpaper picker vs live instance. Call from {@code Engine.onCreate} after
     * {@code super.onCreate}, never from the engine constructor.
     */
    public void setPreviewEngine(boolean preview) {
        if (previewEngine == preview) return;
        previewEngine = preview;
        if (!started) return;
        applyTargetFps(getPreferences());
        handler.removeCallbacks(drawFrameCallback);
        if (active && !frozen && surface.isDrawingEnabled()) {
            lastFrameUptimeMs = 0;
            forceSceneRedraw = true;
            drawFrame();
        }
    }

    public void start() {
        if (started) return;
        started = true;
        Log.d("PonyPaper", "Controller starting...");
        PonySize.ensureDefault(appContext);
        TargetFps.ensureDefault(appContext);
        ensureIdleTimeoutDefault(appContext);
        getPreferences().registerOnSharedPreferenceChangeListener(this);
        powerSaveMode = isSystemPowerSaveMode();
        onBattery = isOnBattery(null);
        batteryTempTenths = readBatteryTemperatureTenths();
        applyDebugRenderPath(getPreferences());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                apiThermalStatus = pm.getCurrentThermalStatus();
                Log.d("PonyPaper", "Initial API thermal status: " + apiThermalStatus);
            }
        }
        Log.d("PonyPaper", "Initial battery temp: " + batteryTempTenths);
        applyTargetFps(getPreferences());
        applyBackgroundColour(getPreferences(), true);
        registerPowerReceivers();
        registerThermalListener();
        registerDisplayListener();
        recomputeThermalPolicy();
    }

    /**
     * Unregisters listeners, stops the frame loop, and releases scene resources.
     */
    public void stop() {
        if (!started) return;
        started = false;
        active = false;
        frozen = false;
        unregisterDisplayListener();
        unregisterThermalListener();
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
        handler.removeCallbacks(coalescedDropHerd);
        dropHerd();
        recycleDisplayedBackground();
        releaseCpuSpriteDemand();
        thermalEmergency = false;
        thermalThrottle = false;
    }

    /**
     * Unload the current herd. Cache pins drop so sheets still used by another
     * host (e.g. dream vs wallpaper) stay decoded. The displayed background is
     * kept so {@link #drawFrame} can paint the last image until
     * {@link #replaceBackground} or {@link #recycleDisplayedBackground}.
     */
    private void dropHerd() {
        handler.removeCallbacks(coalescedDropHerd);
        dropHerdNow();
    }

    /**
     * Preference commits notify once per key. Mix load writes every checkbox
     * in one commit; posting coalesces that burst onto the next looper pass.
     */
    private void scheduleDropHerd() {
        if (!started || frozen) return;
        handler.removeCallbacks(coalescedDropHerd);
        handler.post(coalescedDropHerd);
    }

    private void dropHerdNow() {
        sceneLoadGeneration++;
        sceneLoadInFlight = false;
        sceneLoadFailed = false;
        if (ponies != null) {
            ponies.unloadSprites();
            ponies = null;
        }
        forceSceneRedraw = true;
    }

    boolean isSceneLoadInFlight() {
        return sceneLoadInFlight;
    }

    boolean isThermalLimiting() {
        return thermalEmergency || thermalThrottle;
    }

    /** Recycle and null {@link #background}. Handler thread only. */
    private void recycleDisplayedBackground() {
        if (background != null) {
            if (!background.isRecycled()) {
                background.recycle();
            }
            background = null;
        }
        forceSceneRedraw = true;
    }

    /**
     * Swap in {@code next} (may be null) and recycle the previous displayed
     * bitmap. Handler thread only.
     */
    private void replaceBackground(Bitmap next) {
        Bitmap old = background;
        background = next;
        if (old != null && old != next && !old.isRecycled()) {
            old.recycle();
        }
        forceSceneRedraw = true;
    }

    /**
     * Build the herd and decode the optional background on the decode worker.
     * The frame loop keeps painting the last decoded background bitmap until
     * this completes (solid fill only when there is no image yet).
     */
    private void ensureScenePrepared(final int canvasW, final int canvasH) {
        if (ponies != null || sceneLoadInFlight || sceneLoadFailed || !started) {
            return;
        }
        final SharedPreferences prefs = getPreferences();
        applyTargetFps(prefs);
        drunkMode = prefs.getBoolean("pref_drunk_mode", false);
        applyBackgroundColour(prefs, true);

        final int ponyCount = getEffectivePonyCount(prefs);
        final boolean wantBg = preferredBackgroundEnabled(prefs)
                && !shouldDisableBackgroundImage(prefs);
        final int pixelation = pixelationFromPrefs(prefs);
        File filesDir = appContext.getExternalFilesDir(null);
        final File bgFile = (wantBg && filesDir != null)
                ? new File(filesDir, CustomStorage.BACKGROUND_NAME) : null;
        final int gen = ++sceneLoadGeneration;
        sceneLoadInFlight = true;

        SpriteCache.execute(new Runnable() {
            @Override
            public void run() {
                Ponies herd = null;
                Bitmap bg = null;
                try {
                    herd = new Ponies(appContext, prefs, ponyCount);
                    herd.preloadActiveSprites();
                } catch (RuntimeException e) {
                    Log.e("PonyPaper", "Failed to build pony herd", e);
                    if (herd != null) {
                        herd.unloadSprites();
                        herd = null;
                    }
                }
                final boolean uploadHardware = wantsHardwareCanvasUpload();
                if (bgFile != null && bgFile.exists()) {
                    try {
                        bg = decodeBackgroundFile(bgFile, canvasW, canvasH, pixelation,
                                uploadHardware);
                    } catch (RuntimeException e) {
                        Log.e("PonyPaper", "Failed to decode background", e);
                        bg = null;
                    }
                }
                final Ponies readyHerd = herd;
                final Bitmap readyBg = bg;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != sceneLoadGeneration || !started) {
                            if (readyHerd != null) {
                                readyHerd.unloadSprites();
                            }
                            if (readyBg != null && !readyBg.isRecycled()) {
                                readyBg.recycle();
                            }
                            return;
                        }
                        sceneLoadInFlight = false;
                        if (readyHerd == null) {
                            sceneLoadFailed = true;
                            if (readyBg != null && !readyBg.isRecycled()) {
                                readyBg.recycle();
                            }
                            return;
                        }
                        ponies = readyHerd;
                        replaceBackground(readyBg);
                        if (active && !frozen && !thermalEmergency) {
                            lastFrameUptimeMs = 0;
                            drawFrame();
                        }
                    }
                });
            }
        });
    }

    private static int pixelationFromPrefs(SharedPreferences prefs) {
        int pixelation = prefs.getInt("pref_pixelation", DEFAULT_PIXELATION);
        if (pixelation < MIN_PIXELATION) return MIN_PIXELATION;
        if (pixelation > MAX_PIXELATION) return MAX_PIXELATION;
        return pixelation;
    }

    /**
     * Decode the background at cover-size / {@code pixelation}, in RGB_565 on
     * the CPU. When {@code uploadHardware} is true (HW-canvas hosts on API 26+),
     * uploads to {@link Bitmap.Config#HARDWARE} and recycles the CPU copy.
     * Software-only hosts and pre-O keep RGB_565. The frame loop cover-stretches
     * this bitmap; it is not upsampled back to the surface, so pixelation
     * remains a memory-bandwidth / heat control.
     */
    private static Bitmap decodeBackgroundFile(File bgFile, int canvasW, int canvasH,
            int pixelation, boolean uploadHardware) {
        if (pixelation < MIN_PIXELATION) pixelation = MIN_PIXELATION;
        BitmapFactory.Options bfo = new BitmapFactory.Options();
        bfo.inScaled = false;
        bfo.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(bgFile.toString(), bfo);
        int srcW = bfo.outWidth;
        int srcH = bfo.outHeight;
        int coverW;
        int coverH;
        if (srcW > 0 && srcH > 0 && canvasW > 0 && canvasH > 0) {
            float coverScale = Math.max((float) canvasW / (float) srcW, (float) canvasH / (float) srcH);
            coverW = Math.max(1, Math.round(srcW * coverScale));
            coverH = Math.max(1, Math.round(srcH * coverScale));
        } else {
            coverW = Math.max(1, canvasW > 0 ? canvasW : srcW);
            coverH = Math.max(1, canvasH > 0 ? canvasH : srcH);
        }
        int targetW = Math.max(1, coverW / pixelation);
        int targetH = Math.max(1, coverH / pixelation);

        bfo.inJustDecodeBounds = false;
        bfo.inSampleSize = inSampleSizeForTarget(srcW, srcH, targetW, targetH);
        bfo.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap decoded = BitmapFactory.decodeFile(bgFile.toString(), bfo);
        if (decoded == null) {
            return null;
        }
        Bitmap working = asRgb565(decoded);
        // Shrink extra pixels left by power-of-two inSampleSize. Do not upscale:
        // a full-surface copy would undo pixelation and cost a full blit every frame.
        boolean filter = pixelation == 1;
        working = scaleDownToTarget(working, targetW, targetH, filter);
        working = asRgb565(working);
        if (working != null && !working.isRecycled()) {
            working.prepareToDraw();
            if (uploadHardware) {
                // HARDWARE-only: software canvas fallback drops image backgrounds.
                working = GpuBitmaps.uploadAndRecycleSource(working);
            }
        }
        return working;
    }

    /**
     * Background-only decode when recovering from software-canvas fallback
     * (HARDWARE backgrounds were recycled). Does not rebuild the pony herd.
     */
    private void requestBackgroundReloadIfNeeded() {
        if (!started || frozen || thermalEmergency) return;
        if (background != null && !background.isRecycled()) return;
        final SharedPreferences prefs = getPreferences();
        if (!preferredBackgroundEnabled(prefs) || shouldDisableBackgroundImage(prefs)) {
            return;
        }
        final SurfaceHolder holder = surface.getSurfaceHolder();
        if (holder == null) return;
        Rect frame = holder.getSurfaceFrame();
        if (frame == null || frame.width() <= 0 || frame.height() <= 0) return;
        final int canvasW = frame.width();
        final int canvasH = frame.height();
        final int pixelation = pixelationFromPrefs(prefs);
        File filesDir = appContext.getExternalFilesDir(null);
        if (filesDir == null) return;
        final File bgFile = new File(filesDir, CustomStorage.BACKGROUND_NAME);
        if (!bgFile.exists()) return;
        final int gen = sceneLoadGeneration;
        final boolean uploadHardware = wantsHardwareCanvasUpload();
        SpriteCache.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bg = null;
                try {
                    bg = decodeBackgroundFile(bgFile, canvasW, canvasH, pixelation,
                            uploadHardware);
                } catch (RuntimeException e) {
                    Log.e("PonyPaper", "Failed to reload background", e);
                    bg = null;
                }
                final Bitmap readyBg = bg;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != sceneLoadGeneration || !started) {
                            if (readyBg != null && !readyBg.isRecycled()) {
                                readyBg.recycle();
                            }
                            return;
                        }
                        if (!hardwareCanvasAllowed || shouldDisableBackgroundImage(getPreferences())) {
                            if (readyBg != null && !readyBg.isRecycled()) {
                                readyBg.recycle();
                            }
                            return;
                        }
                        if (background != null && !background.isRecycled()) {
                            if (readyBg != null && readyBg != background && !readyBg.isRecycled()) {
                                readyBg.recycle();
                            }
                            return;
                        }
                        replaceBackground(readyBg);
                        if (active && !frozen && !thermalEmergency && surface.isDrawingEnabled()) {
                            lastFrameUptimeMs = 0;
                            drawFrame();
                        }
                    }
                });
            }
        });
    }

    /** Largest power-of-two {@link BitmapFactory.Options#inSampleSize} that keeps both sides ≥ target. */
    private static int inSampleSizeForTarget(int srcW, int srcH, int targetW, int targetH) {
        if (srcW <= 0 || srcH <= 0 || targetW <= 0 || targetH <= 0) {
            return 1;
        }
        int sample = Math.min(srcW / targetW, srcH / targetH);
        if (sample < 1) {
            return 1;
        }
        int pow2 = 1;
        while (pow2 * 2 <= sample) {
            pow2 *= 2;
        }
        return pow2;
    }

    /** RGB_565 copy when the decoder ignored {@code inPreferredConfig} (typical for PNG). */
    private static Bitmap asRgb565(Bitmap src) {
        if (src == null || src.isRecycled()) {
            return src;
        }
        if (src.getConfig() == Bitmap.Config.RGB_565) {
            return src;
        }
        try {
            Bitmap converted = src.copy(Bitmap.Config.RGB_565, false);
            if (converted != null) {
                src.recycle();
                return converted;
            }
        } catch (OutOfMemoryError e) {
            Log.w("PonyPaper", "Background RGB_565 copy skipped (OOM)", e);
        }
        return src;
    }

    /** Downscale when the decode is larger than the pixelated target. Never upscales. */
    private static Bitmap scaleDownToTarget(Bitmap src, int targetW, int targetH, boolean filter) {
        if (src == null || src.isRecycled() || targetW <= 0 || targetH <= 0) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= targetW && h <= targetH) {
            return src;
        }
        float scale = Math.min((float) targetW / (float) w, (float) targetH / (float) h);
        if (scale >= 1f) {
            return src;
        }
        int dstW = Math.max(1, Math.round(w * scale));
        int dstH = Math.max(1, Math.round(h * scale));
        if (dstW == w && dstH == h) {
            return src;
        }
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(src, dstW, dstH, filter);
            if (scaled != null && scaled != src) {
                src.recycle();
                return scaled;
            }
        } catch (OutOfMemoryError e) {
            Log.w("PonyPaper", "Background downscale skipped (OOM)", e);
        }
        return src;
    }

    /** Cover-fit destination inside the canvas, panned by home-screen offsets. */
    private static void coverDestRect(int srcW, int srcH, int canvasW, int canvasH,
            float xOffset, float yOffset, Rect out) {
        int dstW;
        int dstH;
        if (srcW > 0 && srcH > 0 && canvasW > 0 && canvasH > 0) {
            float scale = Math.max((float) canvasW / (float) srcW, (float) canvasH / (float) srcH);
            dstW = Math.max(1, Math.round(srcW * scale));
            dstH = Math.max(1, Math.round(srcH * scale));
        } else {
            dstW = Math.max(1, canvasW);
            dstH = Math.max(1, canvasH);
        }
        int left = Math.round((canvasW - dstW) * xOffset);
        int top = Math.round((canvasH - dstH) * yOffset);
        out.set(left, top, left + dstW, top + dstH);
    }

    /**
     * Starts or stops the draw loop. Wallpaper maps visibility here; dream maps
     * dreaming + surface-ready. Does not animate while {@link #thermalEmergency}.
     * Clearing active also clears {@link #frozen}.
     */
    public void setActive(boolean active) {
        if (!active) {
            this.active = false;
            this.frozen = false;
            handler.removeCallbacks(drawFrameCallback);
            // Drop any setFrameRate vote before the surface is hidden/destroyed;
            // a stale vote was a historical cause of wallpaper lock stalls.
            clearSurfaceFrameRate(surface.getSurfaceHolder());
            return;
        }
        // Sync in case Battery Saver, plug state, or thermal changed while inactive.
        applyPowerPolicyState(isSystemPowerSaveMode(), isOnBattery(null));
        recomputeThermalPolicy();
        this.active = true;
        this.frozen = false;
        lastFrameUptimeMs = 0;
        forceSceneRedraw = true;
        lastSurfaceFps = -1f;
        if (thermalEmergency) {
            paintSolidFrame(THERMAL_SAFE_COLOUR);
            // Dream may have attached while already hot (hard-stop was a no-op then).
            surface.onThermalHardStop();
            return;
        }
        requestLibrarySync();
        drawFrame();
    }

    /**
     * Best-effort pull/push against the user-chosen library folder. Reloads the
     * herd if files changed. Never blocks the frame loop on SAF I/O.
     */
    private void requestLibrarySync() {
        if (librarySyncInFlight) return;
        if (!CustomStorage.hasLibraryFolder(appContext)) return;
        librarySyncInFlight = true;
        new Thread(new Runnable() {
            public void run() {
                final CustomStorage.SyncResult result = CustomStorage.syncLibrary(appContext);
                handler.post(new Runnable() {
                    public void run() {
                        librarySyncInFlight = false;
                        if (result.changed && started) {
                            dropHerd();
                        }
                    }
                });
            }
        }, "ponypaper-libsync").start();
    }

    /**
     * Freezes or unfreezes the scene. While frozen, no further frames are
     * scheduled and the last posted surface buffer is left as-is (no bitmap
     * recycle, no herd reset). Dream hosts use this during content fade-out
     * before {@code wakeUp()}/{@code finish()}. Also drops any
     * {@code setFrameRate} vote so the display can idle.
     */
    public void setFrozen(boolean frozen) {
        if (this.frozen == frozen) return;
        this.frozen = frozen;
        handler.removeCallbacks(drawFrameCallback);
        if (frozen) {
            handler.removeCallbacks(coalescedDropHerd);
            // Hardware canvas + setFrameRate holds a display vote; drop it so
            // idle-timeout sleep is not fighting an active surface.
            clearSurfaceFrameRate(surface.getSurfaceHolder());
            return;
        }
        if (active && !thermalEmergency && surface.isDrawingEnabled()) {
            lastFrameUptimeMs = 0;
            forceSceneRedraw = true;
            drawFrame();
        }
    }

    /** @return whether {@link #setFrozen(boolean)} is holding the last frame */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Paints a single solid colour to the host surface. Used for thermal safe
     * frames and optional teardown blanks.
     */
    public void paintSolidFrame(int color) {
        final SurfaceHolder holder = surface.getSurfaceHolder();
        if (holder == null) return;
        Canvas c = null;
        try {
            c = lockFrameCanvas(holder);
            if (c != null && c.getWidth() > 0 && c.getHeight() > 0) {
                c.drawColor(color);
            }
        } finally {
            if (c != null) holder.unlockCanvasAndPost(c);
        }
    }

    /**
     * Call when the host surface size changes. Resets pony positions and
     * redraws if the scene is active. Skipped while inactive or frozen so
     * teardown / exit-size thrash does not {@link Pony#reset() recycle} bitmaps
     * under a still-visible surface buffer.
     */
    public void onSurfaceSizeChanged() {
        applyTargetFps(getPreferences());
        if (!active || frozen) {
            return;
        }
        if (ponies != null) ponies.reset();
        if (drunkMode) {
            applyBackgroundColour(getPreferences(), true);
        }
        lastSurfaceFps = -1f;
        lastBgDestLeft = Integer.MIN_VALUE;
        lastBgDestTop = Integer.MIN_VALUE;
        lastFrameUptimeMs = 0;
        forceSceneRedraw = true;
        drawFrame();
    }

    /**
     * Home-screen parallax step. Runs a frame immediately so an idle FPS cap
     * does not delay background panning.
     */
    public void onOffsetsChanged() {
        if (!started || !active || frozen || thermalEmergency) {
            return;
        }
        if (!surface.isDrawingEnabled()) {
            return;
        }
        handler.removeCallbacks(drawFrameCallback);
        drawFrame();
    }

    public void onTouchEvent(MotionEvent event) {
        if (ponies != null) ponies.onTouchEvent(event);
        // Hold-to-drag should follow the finger even while the idle schedule is
        // in effect; home-screen swipes never set this (long-press required).
        if (ponies != null && ponies.didDragThisGesture()
                && active && !frozen && !thermalEmergency
                && surface.isDrawingEnabled()) {
            handler.removeCallbacks(drawFrameCallback);
            drawFrame();
        }
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

    /**
     * Writes the first-run idle timeout if the user has never chosen one.
     * Uses {@code commit()} so a following {@code setDefaultValues} sees the key.
     * Pixel/AOSP get {@link #DEFAULT_DREAM_IDLE_MINUTES}; other OEMs get never
     * until lock is granted.
     */
    static void ensureIdleTimeoutDefault(Context context) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.contains(PREF_DREAM_IDLE_TIMEOUT)) return;
        prefs.edit().putString(PREF_DREAM_IDLE_TIMEOUT,
                Integer.toString(defaultIdleTimeoutMinutes())).commit();
    }

    /**
     * If idle timeout cannot power the panel off, store never so the setting
     * matches what the dream will actually do.
     */
    static void syncIdleTimeoutWithCapability(Context context) {
        if (context == null) return;
        if (DreamSleepAdmin.canTurnScreenOff(context)) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (parseDreamIdleMinutes(prefs.getString(PREF_DREAM_IDLE_TIMEOUT, null)) == 0) {
            return;
        }
        prefs.edit().putString(PREF_DREAM_IDLE_TIMEOUT, DREAM_IDLE_TIMEOUT_NEVER).commit();
    }

    /** {@link #DEFAULT_DREAM_IDLE_MINUTES} where {@code finish()} sleeps; else never. */
    static int defaultIdleTimeoutMinutes() {
        return DreamSleepAdmin.systemSleepsAfterDreamFinish()
                ? DEFAULT_DREAM_IDLE_MINUTES : 0;
    }

    static int parseDreamIdleMinutes(String raw) {
        if (raw == null) return defaultIdleTimeoutMinutes();
        try {
            int minutes = Integer.parseInt(raw.trim());
            if (minutes < 0) return defaultIdleTimeoutMinutes();
            return minutes;
        } catch (NumberFormatException ignored) {
            return defaultIdleTimeoutMinutes();
        }
    }

    /**
     * Idle milliseconds before the dream should sleep the display with no touch.
     * {@code 0} means never (session keep-on / thermal hard-stop still apply),
     * including when this OEM cannot turn the panel off yet.
     */
    static long dreamIdleTimeoutMs(Context context, SharedPreferences prefs) {
        if (context != null && !DreamSleepAdmin.canTurnScreenOff(context)) {
            return 0L;
        }
        String raw = prefs != null ? prefs.getString(PREF_DREAM_IDLE_TIMEOUT, null) : null;
        int minutes = parseDreamIdleMinutes(raw);
        if (minutes <= 0) {
            return 0L;
        }
        return minutes * 60_000L;
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

    /** Soft thermal throttle (MODERATE or SEVERE): lower FPS/ponies; no full emergency freeze. */
    private boolean shouldApplyThermalThrottle() {
        return thermalThrottle && !thermalEmergency;
    }

    /**
     * Soft quality cut while this surface generation is locked to software
     * {@link SurfaceHolder#lockCanvas()}: CPU composition cannot sustain high
     * user FPS/herd settings. Caps to {@link #DEFAULT_TARGET_FPS} /
     * {@link #DEFAULT_NUM_PONIES} (milder than thermal soft throttle).
     */
    private boolean shouldApplySoftwareCanvasLimits() {
        return !hardwareCanvasAllowed;
    }

    private boolean shouldDisableBackgroundImage(SharedPreferences prefs) {
        if (thermalEmergency) return true;
        // Software lockCanvas fallback: HARDWARE-only backgrounds cannot be drawn.
        if (!hardwareCanvasAllowed) return true;
        if (shouldApplyBatterySaverLimits(prefs)) return true;
        return onBattery && prefs.getBoolean(PREF_BATTERY_DISABLE_BACKGROUND, false);
    }

    /** True when this is the dream and the user opted into separate display prefs. */
    private boolean useDreamDisplayOverrides(SharedPreferences prefs) {
        return surface.isDream() && prefs.getBoolean(PREF_DREAM_CUSTOM_DISPLAY, false);
    }

    private int preferredPonyCount(SharedPreferences prefs) {
        if (useDreamDisplayOverrides(prefs) && prefs.contains(PREF_DREAM_NUM_PONIES)) {
            return prefs.getInt(PREF_DREAM_NUM_PONIES, DEFAULT_NUM_PONIES);
        }
        return prefs.getInt(PREF_NUM_PONIES, DEFAULT_NUM_PONIES);
    }

    private boolean preferredBackgroundEnabled(SharedPreferences prefs) {
        if (useDreamDisplayOverrides(prefs) && prefs.contains(PREF_DREAM_BACKGROUND)) {
            return prefs.getBoolean(PREF_DREAM_BACKGROUND, false);
        }
        return prefs.getBoolean(PREF_BACKGROUND, false);
    }

    private String preferredTargetFpsRaw(SharedPreferences prefs) {
        String raw = null;
        if (useDreamDisplayOverrides(prefs) && prefs.contains(PREF_DREAM_TARGET_FPS)) {
            raw = prefs.getString(PREF_DREAM_TARGET_FPS, null);
        }
        if (raw == null) {
            raw = prefs.getString(PREF_TARGET_FPS, Integer.toString(DEFAULT_TARGET_FPS));
        }
        if (raw == null) {
            raw = Integer.toString(DEFAULT_TARGET_FPS);
        }
        return raw;
    }

    private int getEffectivePonyCount(SharedPreferences prefs) {
        int count = preferredPonyCount(prefs);
        if (count < 1) count = DEFAULT_NUM_PONIES;
        if (shouldApplyBatterySaverLimits(prefs)) {
            count = Math.min(count, BATTERY_SAVER_MAX_PONIES);
        }
        if (shouldUseDefaultPoniesOnBattery(prefs)) {
            count = Math.min(count, DEFAULT_NUM_PONIES);
        }
        if (shouldApplySoftwareCanvasLimits()) {
            count = Math.min(count, DEFAULT_NUM_PONIES);
        }
        if (shouldApplyThermalThrottle()) {
            count = Math.min(count, THERMAL_MODERATE_MAX_PONIES);
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

    private void registerThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        if (thermalListenerToken != null) return;
        PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        thermalListenerToken = ThermalStatusSupport.register(pm, handler,
                new ThermalStatusSupport.Callback() {
                    @Override
                    public void onThermalStatusChanged(int status) {
                        if (!started) return;
                        if (apiThermalStatus == status) return;
                        apiThermalStatus = status;
                        recomputeThermalPolicy();
                    }
                });
    }

    private void unregisterThermalListener() {
        if (thermalListenerToken == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            ThermalStatusSupport.unregister(pm, thermalListenerToken);
        }
        thermalListenerToken = null;
    }

    /**
     * Battery pack temperature in tenths of a degree Celsius, or
     * {@link #BATTERY_TEMP_UNKNOWN} if the sticky battery intent is missing.
     */
    private int readBatteryTemperatureTenths() {
        Intent status = appContext.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) {
            Log.d("PonyPaper", "readBatteryTemperatureTenths: Sticky battery intent missing");
            return BATTERY_TEMP_UNKNOWN;
        }
        int temp = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, BATTERY_TEMP_UNKNOWN);
        Log.d("PonyPaper", "readBatteryTemperatureTenths: Current temp = " + temp);
        return temp;
    }

    /**
     * Map battery temperature to the same scale as {@link PowerManager} thermal
     * status. Used as the sole signal on API &lt; 29 and as a max() elevating
     * signal when the Thermal API is available.
     */
    private int batteryTemperatureThermalStatus() {
        if (batteryTempTenths == BATTERY_TEMP_UNKNOWN) return THERMAL_NONE;
        if (batteryTempTenths >= BATTERY_TEMP_CRITICAL_TENTHS) return THERMAL_CRITICAL;
        if (batteryTempTenths >= BATTERY_TEMP_SEVERE_TENTHS) return THERMAL_SEVERE;
        if (batteryTempTenths >= BATTERY_TEMP_MODERATE_TENTHS) return THERMAL_MODERATE;
        return THERMAL_NONE;
    }

    /**
     * Worst-case of system thermal status (API 29+) and battery-temperature
     * fallback. Battery temp alone covers pre-Q devices.
     */
    private int computeEffectiveThermalStatus() {
        int fromBattery = batteryTemperatureThermalStatus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Math.max(apiThermalStatus, fromBattery);
        }
        return fromBattery;
    }

    /**
     * Apply soft throttle (MODERATE or SEVERE) or emergency freeze (CRITICAL+).
     * SEVERE is a quality cut, not a hard stop: boot and charging HALs often
     * report it on a cool device. Leave emergency as soon as status drops
     * below CRITICAL so a CRITICAL→SEVERE settle resumes at min quality.
     */
    private void recomputeThermalPolicy() {
        int effective = computeEffectiveThermalStatus();

        boolean wantEmergency = effective >= THERMAL_CRITICAL;
        boolean wantThrottle = !wantEmergency && effective >= THERMAL_MODERATE;

        Log.d("PonyPaper", "recomputeThermalPolicy: api=" + apiThermalStatus +
                ", batteryTenths=" + batteryTempTenths +
                ", effective=" + effective +
                ", wantEmergency=" + wantEmergency +
                ", wantThrottle=" + wantThrottle);

        if (wantEmergency == thermalEmergency && wantThrottle == thermalThrottle) {
            return;
        }

        SharedPreferences prefs = getPreferences();
        int wasEffectivePonies = getEffectivePonyCount(prefs);
        boolean wasEmergency = thermalEmergency;
        boolean wasThrottle = thermalThrottle;

        thermalEmergency = wantEmergency;
        thermalThrottle = wantThrottle;

        applyTargetFps(prefs);
        int nowEffectivePonies = getEffectivePonyCount(prefs);

        if (thermalEmergency) {
            handler.removeCallbacks(drawFrameCallback);
            dropHerd();
            recycleDisplayedBackground();
            if (active && surface.isDrawingEnabled() && !frozen) {
                paintSolidFrame(THERMAL_SAFE_COLOUR);
            }
            if (!wasEmergency) {
                surface.onThermalHardStop();
            }
            return;
        }

        // Leaving emergency or crossing throttle: rebuild herd when size/profile changes.
        // Skip while frozen so exit fade does not drop bitmaps mid-transition.
        if (!frozen && (wasEmergency || wasThrottle != thermalThrottle
                || wasEffectivePonies != nowEffectivePonies)) {
            dropHerd();
        }

        handler.removeCallbacks(drawFrameCallback);
        if (active && surface.isDrawingEnabled() && !frozen) {
            lastFrameUptimeMs = 0;
            drawFrame();
        }
    }

    /**
     * Reads the opaque user fill. Missing values use
     * {@link #DEFAULT_BACKGROUND_COLOUR}; stored colours have alpha forced to
     * {@code 0xFF}.
     */
    static int backgroundColourFromPrefs(SharedPreferences prefs) {
        int raw = DEFAULT_BACKGROUND_COLOUR;
        if (prefs != null) {
            raw = prefs.getInt(PREF_BACKGROUND_COLOUR, DEFAULT_BACKGROUND_COLOUR);
        }
        return 0xff000000 | (raw & 0x00ffffff);
    }

    private static int fadedFill(int opaque) {
        return (DRUNK_FILL_ALPHA << 24) | (opaque & 0x00ffffff);
    }

    /**
     * @param resetDrunkFade when true, restore opaque fill and restart the
     *        Berry Punch fade timer; when false, keep the current fade stage
     *        and only swap RGB (used for a live colour preference change).
     */
    private void applyBackgroundColour(SharedPreferences prefs, boolean resetDrunkFade) {
        baseBackgroundColour = backgroundColourFromPrefs(prefs);
        if (resetDrunkFade) {
            drunkElapsedMs = 0;
            paint.setAlpha(0xff);
            backgroundColour = baseBackgroundColour;
            forceSceneRedraw = true;
            return;
        }
        if (paint.getAlpha() == 0xff) {
            backgroundColour = baseBackgroundColour;
        } else {
            backgroundColour = fadedFill(baseBackgroundColour);
        }
        forceSceneRedraw = true;
    }

    private void applyTargetFps(SharedPreferences prefs) {
        int fps = DEFAULT_TARGET_FPS;
        try {
            fps = Integer.parseInt(preferredTargetFpsRaw(prefs).trim());
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
        if (shouldApplySoftwareCanvasLimits()) {
            fps = Math.min(fps, DEFAULT_TARGET_FPS);
        }
        if (shouldApplyThermalThrottle()) {
            fps = Math.min(fps, THERMAL_MODERATE_MAX_FPS);
        }
        fps = Math.min(fps, TargetFps.maxListedFps(hostDisplay()));
        if (previewEngine) {
            fps = Math.min(fps, DEFAULT_TARGET_FPS);
        }
        if (fps != targetFps) {
            lastSurfaceFps = -1f;
            forceSceneRedraw = true;
        }
        targetFps = fps;
        framePeriodMs = Math.max(1, 1000 / fps);
    }

    /** Display the host is drawing on, else the default display. */
    private Display hostDisplay() {
        Display display = surface.getHostDisplay();
        if (display != null) return display;
        return TargetFps.displayFor(surface.getContext() != null
                ? surface.getContext() : appContext);
    }

    private void registerDisplayListener() {
        if (displayListener != null) return;
        DisplayManager dm = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return;
        displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                onHostDisplayMaybeChanged();
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                onHostDisplayMaybeChanged();
            }

            @Override
            public void onDisplayChanged(int displayId) {
                onHostDisplayMaybeChanged();
            }
        };
        dm.registerDisplayListener(displayListener, handler);
    }

    private void unregisterDisplayListener() {
        if (displayListener == null) return;
        DisplayManager dm = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null) {
            dm.unregisterDisplayListener(displayListener);
        }
        displayListener = null;
    }

    /**
     * Fold, external display, or a system 60/120 Hz toggle can change peak Hz
     * without a preference edit. Re-clamp and reschedule only when the period
     * actually changes.
     */
    private void onHostDisplayMaybeChanged() {
        if (!started) return;
        int oldPeriod = framePeriodMs;
        applyTargetFps(getPreferences());
        if (framePeriodMs == oldPeriod) return;
        handler.removeCallbacks(drawFrameCallback);
        if (active && !frozen && surface.isDrawingEnabled()) {
            lastFrameUptimeMs = 0;
            drawFrame();
        }
    }

    private int powerPolicySignature(SharedPreferences prefs) {
        int sig = 0;
        if (shouldApplyBatterySaverLimits(prefs)) sig |= 1;
        if (shouldUseDefaultFpsOnBattery(prefs)) sig |= 2;
        if (shouldUseDefaultPoniesOnBattery(prefs)) sig |= 4;
        if (shouldDisableBackgroundImage(prefs)) sig |= 8;
        if (shouldApplyThermalThrottle()) sig |= 16;
        if (thermalEmergency) sig |= 32;
        if (shouldApplySoftwareCanvasLimits()) sig |= 64;
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
        // Leave the herd alone while frozen (exit fade holds the last buffer).
        if (!frozen && (ponies == null || wasEffective != nowEffective || wasSig != nowSig)) {
            dropHerd();
        }

        handler.removeCallbacks(drawFrameCallback);
        if (active && !frozen) {
            lastFrameUptimeMs = 0;
            drawFrame();
        }
    }

    private void reapplyPowerProfilePrefs(SharedPreferences prefs) {
        applyTargetFps(prefs);
        if (!frozen) {
            dropHerd();
        }
        handler.removeCallbacks(drawFrameCallback);
        if (active && !frozen) {
            lastFrameUptimeMs = 0;
            drawFrame();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (DebugOverlay.PREF_HUD.equals(key)) {
            forceSceneRedraw = true;
            redrawIfActive();
            return;
        }
        if (DebugOverlay.PREF_RENDER_PATH.equals(key)) {
            applyDebugRenderPath(prefs);
            redrawIfActive();
            return;
        }
        if (key != null && key.startsWith("pref_dream_")) {
            onDreamPreferenceChanged(prefs, key);
            return;
        }
        if (PREF_TARGET_FPS.equals(key)) {
            if (useDreamDisplayOverrides(prefs)) return;
            applyTargetFpsAndRedraw(prefs);
            return;
        }
        if (PREF_BACKGROUND_COLOUR.equals(key)) {
            applyBackgroundColour(prefs, false);
            redrawIfActive();
            return;
        }
        if (PonySize.PREF_KEY.equals(key)) {
            if (ponies != null) {
                ponies.setSizeFactor(PonySize.factor(prefs));
            }
            redrawIfActive();
            return;
        }
        if (PREF_RESPECT_BATTERY_SAVER.equals(key)
                || PREF_BATTERY_DEFAULT_FPS.equals(key)
                || PREF_BATTERY_DEFAULT_PONIES.equals(key)
                || PREF_BATTERY_DISABLE_BACKGROUND.equals(key)) {
            reapplyPowerProfilePrefs(prefs);
            return;
        }
        if (useDreamDisplayOverrides(prefs)
                && (PREF_NUM_PONIES.equals(key) || PREF_BACKGROUND.equals(key))) {
            return;
        }
        if (isHerdMetadataKey(key)) return;
        scheduleDropHerd();
    }

    /**
     * Dream-only keys. Wallpaper hosts ignore them so a screen-saver edit does
     * not rebuild the live wallpaper. Display overrides are ignored while
     * {@link #PREF_DREAM_CUSTOM_DISPLAY} is off (except the toggle itself).
     */
    private void onDreamPreferenceChanged(SharedPreferences prefs, String key) {
        if (!surface.isDream()) return;
        if (PREF_DREAM_CUSTOM_DISPLAY.equals(key)) {
            applyTargetFps(prefs);
            if (!frozen) dropHerd();
            redrawIfActive();
            return;
        }
        if (PREF_DREAM_TARGET_FPS.equals(key)) {
            if (!useDreamDisplayOverrides(prefs)) return;
            applyTargetFpsAndRedraw(prefs);
            return;
        }
        if (PREF_DREAM_NUM_PONIES.equals(key) || PREF_DREAM_BACKGROUND.equals(key)) {
            if (!useDreamDisplayOverrides(prefs)) return;
            scheduleDropHerd();
            return;
        }
        if (isHerdMetadataKey(key)) return;
        scheduleDropHerd();
    }

    /**
     * Mix sidecar / undo prefs do not change who is on. Rebuilding the herd
     * for those keys is wasted decode work.
     */
    private static boolean isHerdMetadataKey(String key) {
        if (key == null) return false;
        return PonyMixes.PREF_MIXES_JSON.equals(key)
                || PonyMixes.PREF_PREVIOUS_HERD_JSON.equals(key)
                || PonyMixes.PREF_VIEWING_LOADED_MIX.equals(key)
                || CustomStorage.PREF_LIBRARY_TREE_URI.equals(key)
                || CustomStorage.PREF_LIBRARY_SEEN_TREE.equals(key)
                || CustomStorage.PREF_LIBRARY_SEEN_NAMES.equals(key);
    }

    private void applyTargetFpsAndRedraw(SharedPreferences prefs) {
        applyTargetFps(prefs);
        redrawIfActive();
    }

    private void redrawIfActive() {
        handler.removeCallbacks(drawFrameCallback);
        if (active && !frozen) {
            lastFrameUptimeMs = 0;
            drawFrame();
        }
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
        // Exit freeze: leave the last buffer alone (content fade overlay covers it).
        if (frozen) {
            handler.removeCallbacks(drawFrameCallback);
            return;
        }

        // Thermal emergency: solid safe frame only; never schedule animation.
        if (thermalEmergency) {
            paintSolidFrame(THERMAL_SAFE_COLOUR);
            handler.removeCallbacks(drawFrameCallback);
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

        Rect surfaceFrame = holder.getSurfaceFrame();
        int frameW = 0;
        int frameH = 0;
        if (surfaceFrame != null) {
            frameW = surfaceFrame.width();
            frameH = surfaceFrame.height();
        }
        if (ponies == null && frameW > 0 && frameH > 0) {
            ensureScenePrepared(frameW, frameH);
        }

        applySurfaceFrameRate(holder, targetFps);

        if (drunkMode && paint.getAlpha() == 0xff) {
            drunkElapsedMs += (int) deltaMs;
            if (drunkElapsedMs >= DRUNK_FADE_MS) {
                backgroundColour = fadedFill(baseBackgroundColour);
                paint.setAlpha(DRUNK_FILL_ALPHA);
                forceSceneRedraw = true;
            }
        }

        if (frameW > 0 && frameH > 0) {
            clipRect.set(surfaceFrame);
        }

        boolean contentDirty = forceSceneRedraw;
        if (ponies != null && frameW > 0 && frameH > 0) {
            if (ponies.update(clipRect, deltaMs)) {
                contentDirty = true;
            }
        }

        float xOffset = surface.getBackgroundXOffset();
        float yOffset = surface.getBackgroundYOffset();
        if (background != null && !background.isRecycled() && frameW > 0 && frameH > 0) {
            coverDestRect(background.getWidth(), background.getHeight(),
                    frameW, frameH, xOffset, yOffset, tmpDst);
            if (tmpDst.left != lastBgDestLeft || tmpDst.top != lastBgDestTop) {
                contentDirty = true;
            }
        }

        if (surface.shouldShowClock()) {
            contentDirty = true;
        }

        SharedPreferences prefs = getPreferences();
        boolean hudOn = DebugOverlay.hudEnabled(prefs);
        // HUD must post to refresh numbers; content-clean frames still count as skips.
        boolean needDraw = contentDirty || hudOn;

        if (!needDraw) {
            if (DebugOverlay.isDebugBuild()) {
                debugOverlay.recordSchedule(false, false, 0L);
            }
            scheduleNextFrame();
            return;
        }

        Canvas c = null;
        boolean posted = false;
        long drawCostNs = 0L;
        long drawStartNs = SystemClock.elapsedRealtimeNanos();
        try {
            c = lockFrameCanvas(holder);
            // Surface may be lost or not yet ready; never touch a null/zero canvas.
            if (c != null && c.getWidth() > 0 && c.getHeight() > 0) {
                int canvasW = c.getWidth();
                int canvasH = c.getHeight();
                Bitmap drawBg = background;
                if (drawBg != null && drawBg.isRecycled()) {
                    drawBg = null;
                }
                // HARDWARE bitmaps cannot be drawn on a software canvas.
                if (drawBg != null && !c.isHardwareAccelerated() && GpuBitmaps.isHardware(drawBg)) {
                    drawBg = null;
                }
                if (drawBg == null || paint.getAlpha() != 0xff) {
                    c.drawColor(backgroundColour);
                }
                if (drawBg != null) {
                    tmpSrc.set(0, 0, drawBg.getWidth(), drawBg.getHeight());
                    coverDestRect(drawBg.getWidth(), drawBg.getHeight(),
                            canvasW, canvasH, xOffset, yOffset, tmpDst);
                    c.drawBitmap(drawBg, tmpSrc, tmpDst, paint);
                    lastBgDestLeft = tmpDst.left;
                    lastBgDestTop = tmpDst.top;
                }
                if (ponies != null) {
                    ponies.draw(c);
                }
                if (surface.shouldShowClock()) {
                    dreamClock.draw(c, surface.getContext(), surface.shouldShowClockDate());
                }
                if (hudOn) {
                    drawDebugHud(c, prefs, canvasW, canvasH);
                }
                forceSceneRedraw = false;
                posted = true;
            }
        } finally {
            if (c != null) holder.unlockCanvasAndPost(c);
            drawCostNs = SystemClock.elapsedRealtimeNanos() - drawStartNs;
        }

        if (DebugOverlay.isDebugBuild()) {
            debugOverlay.recordSchedule(contentDirty, posted, posted ? drawCostNs : 0L);
        }

        scheduleNextFrame();
    }

    private void scheduleNextFrame() {
        handler.removeCallbacks(drawFrameCallback);
        if (active && surface.isDrawingEnabled() && !thermalEmergency && !frozen) {
            handler.postDelayed(drawFrameCallback, currentSchedulePeriodMs());
        }
    }

    private int currentSchedulePeriodMs() {
        int period = framePeriodMs;
        if (ponies != null && ponies.allIdle()) {
            int idlePeriod = Math.max(1, 1000 / IDLE_MAX_FPS);
            if (idlePeriod > period) {
                period = idlePeriod;
            }
        }
        return period;
    }

    /**
     * Prefer {@link SurfaceHolder#lockHardwareCanvas()} when the host (and any
     * debug force-path) allows it. On null/exception, fall back to
     * {@link SurfaceHolder#lockCanvas()} for this surface generation
     * ({@link #allowHardwareCanvasRetry()} resets): drop any HARDWARE-only image
     * background and cap FPS/ponies to defaults until HW recovers.
     * Never mixes dirty-rect software locks with hardware locks.
     */
    private Canvas lockFrameCanvas(SurfaceHolder holder) {
        Surface s = holder.getSurface();
        if (s == null || !s.isValid()) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && hardwareCanvasAllowed
                && wantsHardwareCanvas()) {
            try {
                Canvas hw = HardwareCanvasSupport.lock(holder);
                if (hw != null) {
                    return hw;
                }
                Log.w("PonyPaper", "Hardware canvas returned null; using software");
            } catch (RuntimeException e) {
                Log.w("PonyPaper", "Hardware canvas unavailable; using software", e);
            }
            enterSoftwareCanvasFallback();
        }
        try {
            return holder.lockCanvas();
        } catch (RuntimeException e) {
            Log.w("PonyPaper", "Software canvas lock failed", e);
            return null;
        }
    }

    /**
     * Whether this frame should attempt a hardware canvas lock / HARDWARE bitmap
     * upload. Debug force-software returns false; force-hardware returns true;
     * otherwise the host preference is used.
     */
    private boolean wantsHardwareCanvas() {
        SharedPreferences prefs = getPreferences();
        DebugOverlay.RenderPath path = DebugOverlay.renderPath(prefs);
        if (path == DebugOverlay.RenderPath.SOFTWARE) return false;
        if (path == DebugOverlay.RenderPath.HARDWARE) return true;
        return surface.shouldLockHardwareCanvas();
    }

    /** Hardware bitmap upload only while the controller is still on the HW path. */
    private boolean wantsHardwareCanvasUpload() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && hardwareCanvasAllowed
                && wantsHardwareCanvas();
    }

    /**
     * Apply debug force-path: enter software fallback, or retry hardware after
     * leaving force-software / choosing force-hardware.
     */
    private void applyDebugRenderPath(SharedPreferences prefs) {
        if (!DebugOverlay.isDebugBuild()) return;
        if (DebugOverlay.isForceSoftware(prefs)) {
            if (hardwareCanvasAllowed) {
                enterSoftwareCanvasFallback();
            }
            return;
        }
        // Auto or force-HW: recover from a prior forced software session.
        if (!hardwareCanvasAllowed) {
            allowHardwareCanvasRetry();
        } else if (DebugOverlay.isForceHardware(prefs)) {
            // Ensure uploads / FPS reflect HW even if we never fell back.
            applyTargetFps(prefs);
            requestBackgroundReloadIfNeeded();
        }
    }

    /**
     * Software composition mode for this surface generation: no more HW locks,
     * no image background (HARDWARE-only BG cannot be blitted on software),
     * CPU sprite reload via {@link SpriteCache#addCpuDemand}, and FPS/pony count
     * capped to defaults. Called from {@link #lockFrameCanvas} mid-draw, so herd
     * unload (when the pony cap changes) is deferred via {@link #scheduleDropHerd()}.
     */
    private void enterSoftwareCanvasFallback() {
        if (!hardwareCanvasAllowed) return;
        SharedPreferences prefs = getPreferences();
        int wasEffectivePonies = getEffectivePonyCount(prefs);
        hardwareCanvasAllowed = false;
        recycleDisplayedBackground();
        forceSceneRedraw = true;
        applyTargetFps(prefs);
        acquireCpuSpriteDemand();
        Log.w("PonyPaper", "Software canvas fallback: image background disabled; "
                + "FPS/ponies capped to defaults; reloading CPU sprites");
        if (!frozen && wasEffectivePonies != getEffectivePonyCount(prefs)) {
            scheduleDropHerd();
        }
    }

    /**
     * Ask {@link SpriteCache} to keep / re-decode CPU sprite bitmaps for
     * software blits. Idempotent per controller; redraws when reload finishes.
     */
    private void acquireCpuSpriteDemand() {
        if (cpuSpriteDemandHeld) return;
        cpuSpriteDemandHeld = true;
        SpriteCache.addCpuDemand(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!started || hardwareCanvasAllowed || !cpuSpriteDemandHeld) {
                            return;
                        }
                        forceSceneRedraw = true;
                        if (active && !frozen && !thermalEmergency
                                && surface.isDrawingEnabled()) {
                            lastFrameUptimeMs = 0;
                            drawFrame();
                        }
                    }
                });
            }
        });
    }

    /** Drop this controller's {@link SpriteCache#addCpuDemand} if held. */
    private void releaseCpuSpriteDemand() {
        if (!cpuSpriteDemandHeld) return;
        cpuSpriteDemandHeld = false;
        SpriteCache.removeCpuDemand();
    }

    private void drawDebugHud(Canvas c, SharedPreferences prefs, int canvasW, int canvasH) {
        DebugOverlay.Snapshot snap = debugOverlay.snapshot();
        debugOverlay.fillRates(snap);
        DebugOverlay.RenderPath path = DebugOverlay.renderPath(prefs);
        snap.hostLabel = surface.isDream() ? "dream" : "wallpaper";
        snap.preview = previewEngine;
        snap.pathRequested = DebugOverlay.pathLabel(path);
        snap.pathActualHw = c.isHardwareAccelerated();
        snap.canvasHwAccel = c.isHardwareAccelerated();
        snap.cpuDemandHeld = cpuSpriteDemandHeld;
        snap.hwAllowed = hardwareCanvasAllowed;
        snap.preferredFps = preferredTargetFpsUncapped(prefs);
        snap.effectiveFps = targetFps;
        snap.schedulePeriodMs = currentSchedulePeriodMs();
        snap.preferredPonies = preferredPonyCount(prefs);
        if (snap.preferredPonies < 1) snap.preferredPonies = DEFAULT_NUM_PONIES;
        snap.effectivePonies = getEffectivePonyCount(prefs);
        snap.livePonies = ponies != null ? ponies.getActiveCount() : 0;
        snap.preferredBg = preferredBackgroundEnabled(prefs);
        snap.bgDisabled = shouldDisableBackgroundImage(prefs);
        snap.bgPresent = background != null && !background.isRecycled();
        snap.caps = debugCapsLabel(prefs);
        snap.surfaceW = canvasW;
        snap.surfaceH = canvasH;
        Display display = hostDisplay();
        snap.displayHz = display != null ? TargetFps.peakRefreshHz(display) : 0f;
        snap.surfaceFpsVote = lastSurfaceFps > 0f ? lastSurfaceFps : 0f;
        snap.active = active;
        snap.frozen = frozen;
        snap.thermalEmergency = thermalEmergency;
        snap.thermalThrottle = thermalThrottle;
        snap.thermalStatus = computeEffectiveThermalStatus();
        snap.powerSave = powerSaveMode;
        snap.onBattery = onBattery;
        snap.sceneLoading = sceneLoadInFlight;
        Context ctx = surface.getContext() != null ? surface.getContext() : appContext;
        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        debugOverlay.draw(c, snap, metrics);
    }

    private int preferredTargetFpsUncapped(SharedPreferences prefs) {
        int fps = DEFAULT_TARGET_FPS;
        try {
            fps = Integer.parseInt(preferredTargetFpsRaw(prefs).trim());
        } catch (NumberFormatException e) {
            fps = DEFAULT_TARGET_FPS;
        }
        if (fps < MIN_TARGET_FPS) fps = DEFAULT_TARGET_FPS;
        if (fps > MAX_TARGET_FPS) fps = MAX_TARGET_FPS;
        return fps;
    }

    private String debugCapsLabel(SharedPreferences prefs) {
        StringBuilder caps = new StringBuilder();
        if (shouldApplyBatterySaverLimits(prefs)) appendCap(caps, "battSaver");
        if (shouldUseDefaultFpsOnBattery(prefs)) appendCap(caps, "battFps");
        if (shouldUseDefaultPoniesOnBattery(prefs)) appendCap(caps, "battPonies");
        if (onBattery && prefs.getBoolean(PREF_BATTERY_DISABLE_BACKGROUND, false)) {
            appendCap(caps, "battBg");
        }
        if (shouldApplySoftwareCanvasLimits()) appendCap(caps, "softSW");
        if (shouldApplyThermalThrottle()) appendCap(caps, "thermal");
        if (thermalEmergency) appendCap(caps, "thermalEmer");
        if (previewEngine) appendCap(caps, "preview");
        int uncapped = preferredTargetFpsUncapped(prefs);
        int displayCap = TargetFps.maxListedFps(hostDisplay());
        if (uncapped > displayCap) appendCap(caps, "displayHz");
        return caps.toString();
    }

    private static void appendCap(StringBuilder caps, String label) {
        if (caps.length() > 0) caps.append(',');
        caps.append(label);
    }

    private void applySurfaceFrameRate(SurfaceHolder holder, float fps) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        if (!surface.shouldHintSurfaceFrameRate()) return;
        if (holder == null || fps <= 0f) return;
        if (fps == lastSurfaceFps) return;
        Surface s = holder.getSurface();
        if (s == null || !s.isValid()) return;
        try {
            SurfaceFrameRateSupport.apply(s, fps);
            lastSurfaceFps = fps;
        } catch (RuntimeException e) {
            Log.d("PonyPaper", "Surface setFrameRate failed", e);
        }
    }

    private void clearSurfaceFrameRate(SurfaceHolder holder) {
        lastSurfaceFps = -1f;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        if (holder == null) return;
        Surface s = holder.getSurface();
        if (s == null || !s.isValid()) return;
        try {
            SurfaceFrameRateSupport.clear(s);
        } catch (RuntimeException e) {
            Log.d("PonyPaper", "Surface clearFrameRate failed", e);
        }
    }

}
