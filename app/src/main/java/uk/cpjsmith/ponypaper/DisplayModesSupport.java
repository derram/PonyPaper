package uk.cpjsmith.ponypaper;

import android.view.Display;

/**
 * API 23+ {@link Display#getSupportedModes()} isolated so pre-M devices never
 * resolve {@link Display.Mode} when loading {@link TargetFps}.
 *
 * <p>Only call when {@code Build.VERSION.SDK_INT >= M}.
 */
final class DisplayModesSupport {

    private DisplayModesSupport() {}

    static float peakRefreshHz(Display display, float seed) {
        if (display == null) return seed;
        Display.Mode[] modes = display.getSupportedModes();
        if (modes == null) return seed;
        float peak = seed;
        for (int i = 0; i < modes.length; i++) {
            peak = Math.max(peak, modes[i].getRefreshRate());
        }
        return peak;
    }
}
