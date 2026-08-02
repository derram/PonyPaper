package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;
import java.util.ArrayList;

/**
 * Target wallpaper redraw rates and which of them a given display can usefully
 * present. Uses {@link Display#getSupportedModes()} (device capability) rather
 * than {@link Display#getRefreshRate()} (current active mode), so high-refresh
 * phones still offer 90/120 when the panel is temporarily running at 60 Hz.
 */
final class TargetFps {
    
    static final String PREF_KEY = "pref_target_fps";
    /** Battery-friendly default; always offered. */
    static final int DEFAULT = 30;
    /**
     * Full menu of targets (must match {@code pref_target_fps_*} string arrays).
     * Higher values are filtered per device.
     */
    static final int[] CANDIDATES = {30, 60, 90, 120};
    
    /** Allow modes reported slightly under the integer label (e.g. 119.9 for 120). */
    private static final float RATE_SLACK_HZ = 0.5f;
    
    private TargetFps() {}
    
    /**
     * Highest refresh rate among {@link Display#getSupportedModes()}, falling
     * back to {@link Display#getRefreshRate()} when modes are unavailable.
     */
    static float maxSupportedRefreshHz(Context context) {
        Display display = getDisplay(context);
        if (display == null) {
            return 60f;
        }
        float max = 0f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode[] modes = display.getSupportedModes();
            if (modes != null) {
                for (Display.Mode mode : modes) {
                    max = Math.max(max, mode.getRefreshRate());
                }
            }
        }
        if (max <= 0f) {
            max = display.getRefreshRate();
        }
        if (max <= 0f) {
            max = 60f;
        }
        return max;
    }
    
    /**
     * Whether this target FPS should appear in settings / be allowed to run.
     * 30 FPS is always offered as a software throttle. Higher rates require a
     * supported mode at least that high (with slack for float reporting).
     */
    static boolean isOffered(int fps, float maxSupportedHz) {
        if (fps <= DEFAULT) {
            return true;
        }
        return maxSupportedHz + RATE_SLACK_HZ >= fps;
    }
    
    /** Candidate rates offered on this device, ascending. */
    static int[] offered(Context context) {
        float maxHz = maxSupportedRefreshHz(context);
        ArrayList<Integer> list = new ArrayList<Integer>(CANDIDATES.length);
        for (int fps : CANDIDATES) {
            if (isOffered(fps, maxHz)) {
                list.add(fps);
            }
        }
        if (list.isEmpty()) {
            list.add(DEFAULT);
        }
        int[] result = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }
    
    /**
     * Clamp a stored preference value to an offered rate. If the stored rate is
     * higher than the panel supports, drop to the highest offered option.
     */
    static int clamp(Context context, int fps) {
        int[] offered = offered(context);
        for (int o : offered) {
            if (o == fps) {
                return fps;
            }
        }
        // Prefer nearest offered rate at or below the request; else default.
        int best = DEFAULT;
        for (int o : offered) {
            if (o <= fps) {
                best = o;
            }
        }
        return best;
    }
    
    static String labelFor(int fps) {
        if (fps == DEFAULT) {
            return fps + " FPS (recommended)";
        }
        return fps + " FPS";
    }
    
    @SuppressWarnings("deprecation")
    private static Display getDisplay(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Display display = context.getDisplay();
            if (display != null) {
                return display;
            }
        }
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return null;
        }
        return wm.getDefaultDisplay();
    }
    
}
