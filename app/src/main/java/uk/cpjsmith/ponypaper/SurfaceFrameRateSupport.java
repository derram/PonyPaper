package uk.cpjsmith.ponypaper;

import android.view.Surface;

/**
 * API 30+ {@link Surface#setFrameRate(float, int)} isolated so pre-R devices
 * never resolve that method when loading {@link PonySceneController}.
 *
 * <p>Only call when {@code Build.VERSION.SDK_INT >= R}. Uses
 * {@link Surface#FRAME_RATE_COMPATIBILITY_DEFAULT} so the hint does not force a
 * display-mode switch; {@code FIXED_SOURCE} can stall wallpaper {@code lockCanvas}
 * after the dream (or a rate change) returns.
 */
final class SurfaceFrameRateSupport {

    private SurfaceFrameRateSupport() {}

    static void apply(Surface surface, float fps) {
        if (surface == null || !surface.isValid() || fps <= 0f) {
            return;
        }
        surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }
}
