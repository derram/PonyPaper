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
import android.graphics.RectF;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
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
    /** Packed ARGB for the solid fill behind ponies (and while a scene reload is in flight). */
    static final String PREF_BACKGROUND_COLOUR = "pref_background_colour";
    /** Historical hardcoded fill; also the preference default. */
    static final int DEFAULT_BACKGROUND_COLOUR = 0xff333333;
    /** Preference key for the user's preferred number of on-screen ponies. */
    static final String PREF_NUM_PONIES = "pref_num_ponies";
    /** When true, the dream draws a large digital clock over the scene. */
    static final String PREF_DREAM_SHOW_CLOCK = "pref_dream_show_clock";
    /** When true (and the clock is shown), draw the date under the time. */
    static final String PREF_DREAM_SHOW_DATE = "pref_dream_show_date";
    /**
     * Dream idle timeout in minutes as a string ({@code "0"} = never).
     * Missing or invalid values use {@link #DEFAULT_DREAM_IDLE_MINUTES}.
     */
    static final String PREF_DREAM_IDLE_TIMEOUT = "pref_dream_idle_timeout";
    /** Historical hardcoded idle timeout; also the preference default. */
    static final int DEFAULT_DREAM_IDLE_MINUTES = 10;
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
    /** Fill/sprite alpha after the Berry Punch fade. */
    private static final int DRUNK_FILL_ALPHA = 0x33;
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
         * Wallpaper may ignore; the dream should {@code finish()} so the display
         * can sleep instead of holding a frozen screensaver.
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
    }

    private final Context appContext;
    private final Handler handler;
    private final FrameSurface surface;

    private Ponies ponies = null;
    private Bitmap background = null;
    private boolean drunkMode = false;
    private final Paint paint = new Paint();
    private final DreamClock dreamClock = new DreamClock();
    /** Opaque user-chosen fill; drunk mode may paint a translucent copy. */
    private int baseBackgroundColour = DEFAULT_BACKGROUND_COLOUR;
    private int backgroundColour = DEFAULT_BACKGROUND_COLOUR;
    private int drunkElapsedMs = 0;
    /** Delay between draw callbacks; derived from {@link #PREF_TARGET_FPS}. */
    private int framePeriodMs = 1000 / DEFAULT_TARGET_FPS;
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
    }

    /**
     * Registers preference, power, and thermal listeners. Safe to call once per
     * controller lifetime.
     */
    public void start() {
        if (started) return;
        started = true;
        Log.d("PonyPaper", "Controller starting...");
        PonySize.ensureDefault(appContext);
        getPreferences().registerOnSharedPreferenceChangeListener(this);
        powerSaveMode = isSystemPowerSaveMode();
        onBattery = isOnBattery(null);
        batteryTempTenths = readBatteryTemperatureTenths();
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
        dropHerd();
        thermalEmergency = false;
        thermalThrottle = false;
    }

    /**
     * Unload the current herd and background. Cache pins drop so sheets still
     * used by another host (e.g. dream vs wallpaper) stay decoded.
     */
    private void dropHerd() {
        sceneLoadGeneration++;
        sceneLoadInFlight = false;
        sceneLoadFailed = false;
        if (ponies != null) {
            ponies.unloadSprites();
            ponies = null;
        }
        if (background != null) {
            if (!background.isRecycled()) {
                background.recycle();
            }
            background = null;
        }
    }

    /**
     * Build the herd and decode the optional background on the decode worker.
     * The frame loop paints a solid (or last-good) buffer until this completes.
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
        final boolean wantBg = prefs.getBoolean("pref_background", false)
                && !shouldDisableBackgroundImage(prefs);
        final int pixelation = prefs.getInt("pref_pixelation", 1);
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
                if (bgFile != null && bgFile.exists()) {
                    try {
                        bg = decodeBackgroundFile(bgFile, canvasW, canvasH, pixelation);
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
                            return;
                        }
                        ponies = readyHerd;
                        background = readyBg;
                        if (active && !frozen && !thermalEmergency) {
                            lastFrameUptimeMs = 0;
                            drawFrame();
                        }
                    }
                });
            }
        });
    }

    private static Bitmap decodeBackgroundFile(File bgFile, int canvasW, int canvasH,
            int pixelation) {
        BitmapFactory.Options bfo = new BitmapFactory.Options();
        bfo.inScaled = false;
        bfo.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(bgFile.toString(), bfo);
        int h = bfo.outHeight;
        int w = bfo.outWidth;
        int scale = 1;
        if (h > 0 && w > 0 && canvasH > 0 && canvasW > 0) {
            scale = Math.min(h / canvasH, w / canvasW);
            if (scale < 1) {
                scale = 1;
            }
        }
        scale *= pixelation;
        if (scale < 1) {
            scale = 1;
        }
        bfo.inJustDecodeBounds = false;
        bfo.inSampleSize = scale;
        return BitmapFactory.decodeFile(bgFile.toString(), bfo);
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
            return;
        }
        // Sync in case Battery Saver, plug state, or thermal changed while inactive.
        applyPowerPolicyState(isSystemPowerSaveMode(), isOnBattery(null));
        recomputeThermalPolicy();
        this.active = true;
        this.frozen = false;
        lastFrameUptimeMs = 0;
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
     * before {@code wakeUp()}/{@code finish()}.
     */
    public void setFrozen(boolean frozen) {
        if (this.frozen == frozen) return;
        this.frozen = frozen;
        handler.removeCallbacks(drawFrameCallback);
        if (!frozen && active && !thermalEmergency && surface.isDrawingEnabled()) {
            lastFrameUptimeMs = 0;
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
            c = holder.lockCanvas();
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
        lastFrameUptimeMs = 0;
        drawFrame();
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

    /**
     * Idle milliseconds before the dream should {@code finish()} with no touch.
     * {@code 0} means never (session keep-on / thermal hard-stop still apply).
     */
    static long dreamIdleTimeoutMs(SharedPreferences prefs) {
        int minutes = DEFAULT_DREAM_IDLE_MINUTES;
        String raw = prefs != null
                ? prefs.getString(PREF_DREAM_IDLE_TIMEOUT,
                Integer.toString(DEFAULT_DREAM_IDLE_MINUTES))
                : null;
        if (raw != null) {
            try {
                minutes = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                minutes = DEFAULT_DREAM_IDLE_MINUTES;
            }
        }
        if (minutes < 0) {
            minutes = DEFAULT_DREAM_IDLE_MINUTES;
        }
        if (minutes == 0) {
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

    private boolean shouldDisableBackgroundImage(SharedPreferences prefs) {
        if (thermalEmergency) return true;
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
            return;
        }
        if (paint.getAlpha() == 0xff) {
            backgroundColour = baseBackgroundColour;
        } else {
            backgroundColour = fadedFill(baseBackgroundColour);
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
        if (shouldApplyThermalThrottle()) {
            fps = Math.min(fps, THERMAL_MODERATE_MAX_FPS);
        }
        fps = Math.min(fps, TargetFps.maxListedFps(hostDisplay()));
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
        if (PREF_TARGET_FPS.equals(key)) {
            applyTargetFps(prefs);
            handler.removeCallbacks(drawFrameCallback);
            if (active && !frozen) {
                lastFrameUptimeMs = 0;
                drawFrame();
            }
            return;
        }
        if (PREF_BACKGROUND_COLOUR.equals(key)) {
            applyBackgroundColour(prefs, false);
            handler.removeCallbacks(drawFrameCallback);
            if (active && !frozen) {
                lastFrameUptimeMs = 0;
                drawFrame();
            }
            return;
        }
        if (PonySize.PREF_KEY.equals(key)) {
            if (ponies != null) {
                ponies.setSizeFactor(PonySize.factor(prefs));
            }
            handler.removeCallbacks(drawFrameCallback);
            if (active && !frozen) {
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
        if (!frozen) {
            dropHerd();
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
        if (ponies == null && surfaceFrame != null
                && surfaceFrame.width() > 0 && surfaceFrame.height() > 0) {
            ensureScenePrepared(surfaceFrame.width(), surfaceFrame.height());
        }

        Canvas c = null;
        try {
            c = holder.lockCanvas();
            // Surface may be lost or not yet ready; never touch a null/zero canvas.
            if (c != null && c.getWidth() > 0 && c.getHeight() > 0) {
                if (drunkMode && paint.getAlpha() == 0xff) {
                    drunkElapsedMs += (int) deltaMs;
                    if (drunkElapsedMs >= DRUNK_FADE_MS) {
                        backgroundColour = fadedFill(baseBackgroundColour);
                        paint.setAlpha(DRUNK_FILL_ALPHA);
                    }
                }
                float xOffset = surface.getBackgroundXOffset();
                float yOffset = surface.getBackgroundYOffset();
                if (background != null && !background.isRecycled()) {
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
                if (ponies != null) {
                    ponies.drawAndUpdate(c, deltaMs);
                }
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
        if (active && surface.isDrawingEnabled() && !thermalEmergency && !frozen) {
            handler.postDelayed(drawFrameCallback, framePeriodMs);
        }
    }
}
