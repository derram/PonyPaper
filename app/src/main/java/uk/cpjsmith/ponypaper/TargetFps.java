package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

/**
 * Listed target frame rates and the peak-refresh cap used by
 * {@link PonySceneController} and the Settings list.
 *
 * <p>Caps use the display's <em>highest supported</em> refresh rate, not the
 * current one. Adaptive-refresh panels often sit at 60 Hz until a client asks
 * for more; capping at the current rate would make 90/120 unreachable.
 */
final class TargetFps {

    /** Same values as {@code pref_target_fps_values}. */
    static final int[] LISTED = {30, 60, 90, 120};

    static final int DEFAULT = PonySceneController.DEFAULT_TARGET_FPS;

    /**
     * Modes report 59.94 / 119.88 / 90.0001. A listed target is allowed when it
     * is at most this many Hz above the measured peak.
     */
    static final float PEAK_EPSILON_HZ = 0.5f;

    private TargetFps() {}

    /**
     * Best-effort display for a context. Application contexts on API 30+ have
     * no display; those fall back to {@link DisplayManager} then the legacy
     * default display.
     */
    static Display displayFor(Context context) {
        if (context == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Display display = context.getDisplay();
                if (display != null) return display;
            } catch (UnsupportedOperationException ignored) {
                // Application context.
            }
        }
        DisplayManager dm =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null) {
            Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) return display;
        }
        return legacyDefaultDisplay(context);
    }

    /** API &lt; R / missing DisplayManager fallback. */
    @SuppressWarnings("deprecation")
    private static Display legacyDefaultDisplay(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return wm != null ? wm.getDefaultDisplay() : null;
    }

    /**
     * Highest refresh rate the display advertises, in Hz. When {@code display}
     * is null the listed maximum is returned so a missing handle does not
     * hide 90/120 on a capable panel.
     */
    static float peakRefreshHz(Display display) {
        if (display == null) {
            return LISTED[LISTED.length - 1];
        }
        float peak = display.getRefreshRate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return DisplayModesSupport.peakRefreshHz(display, peak);
        }
        return legacyPeakRefreshHz(display, peak);
    }

    /** Pre-M: {@link Display#getSupportedModes()} is unavailable. */
    @SuppressWarnings("deprecation")
    private static float legacyPeakRefreshHz(Display display, float peak) {
        float[] rates = display.getSupportedRefreshRates();
        if (rates != null) {
            for (int i = 0; i < rates.length; i++) {
                peak = Math.max(peak, rates[i]);
            }
        }
        return peak;
    }

    /**
     * Highest listed target that this peak can run (always at least
     * {@link #DEFAULT}).
     */
    static int maxListedFps(float peakHz) {
        int best = DEFAULT;
        for (int i = 0; i < LISTED.length; i++) {
            int fps = LISTED[i];
            if (fps <= peakHz + PEAK_EPSILON_HZ) {
                best = fps;
            }
        }
        return best;
    }

    static int maxListedFps(Display display) {
        return maxListedFps(peakRefreshHz(display));
    }

    static int maxListedFps(Context context) {
        return maxListedFps(displayFor(context));
    }

    /** Whether a listed target should appear for a display whose cap is {@code maxListed}. */
    static boolean isListedAllowed(int fps, int maxListed) {
        if (fps == DEFAULT) return true;
        return fps <= maxListed;
    }
}
