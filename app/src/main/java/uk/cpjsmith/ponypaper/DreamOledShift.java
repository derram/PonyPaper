package uk.cpjsmith.ponypaper;

import android.graphics.Rect;

/**
 * Slow integer pan of the screensaver background so a still image does not
 * sit on the same pixels for hours on a docked OLED.
 *
 * <p>Uses a Lissajous path like {@link DreamClock}, but slower and smaller.
 * Periods are incommensurate with the clock so the two drifts do not lock.
 * Destinations are pixel-aligned: the scene draws backgrounds with nearest
 * neighbour, so a subpixel pan would shimmer.
 *
 * <p>{@link #coverScale()} slightly oversizes cover-fit so the pan never
 * reveals the fill colour, even when the bitmap already matches the canvas.
 */
final class DreamOledShift {

    /**
     * Horizontal drift period. Incommensurate with {@link #PERIOD_Y_MS} and
     * with {@link DreamClock}'s 97s / 139s path.
     */
    private static final long PERIOD_X_MS = 7 * 60 * 1000L;
    private static final long PERIOD_Y_MS = 11 * 60 * 1000L;
    /** Max offset from rest as a fraction of canvas width / height (±). */
    private static final float AMPLITUDE = 0.02f;

    private DreamOledShift() {}

    /**
     * Extra cover zoom so ±{@link #AMPLITUDE} still covers the canvas.
     * {@code 1 + 2 × amplitude} leaves amplitude of slack on each side.
     */
    static float coverScale() {
        return 1f + 2f * AMPLITUDE;
    }

    /**
     * Pans {@code dest} by a pixel-snapped Lissajous offset, clamped so every
     * canvas pixel stays covered. No-op when {@code dest} is not larger than
     * the canvas on that axis.
     */
    static void apply(int canvasW, int canvasH, Rect dest, long uptimeMs) {
        if (dest == null || canvasW <= 0 || canvasH <= 0) return;
        int destW = dest.width();
        int destH = dest.height();
        int left = dest.left;
        int top = dest.top;
        if (destW > canvasW) {
            double phaseX = (2.0 * Math.PI * (uptimeMs % PERIOD_X_MS)) / PERIOD_X_MS;
            int maxDx = maxShift(canvasW, destW);
            left = clampOrigin(dest.left + (int) Math.round(Math.sin(phaseX) * maxDx),
                    canvasW, destW);
        }
        if (destH > canvasH) {
            double phaseY = (2.0 * Math.PI * (uptimeMs % PERIOD_Y_MS)) / PERIOD_Y_MS;
            int maxDy = maxShift(canvasH, destH);
            // Cosine plus a fixed phase fills a 2D region, not a diagonal line.
            top = clampOrigin(dest.top + (int) Math.round(
                    Math.cos(phaseY + Math.PI * 0.25) * maxDy),
                    canvasH, destH);
        }
        dest.offsetTo(left, top);
    }

    /** Pixels of travel (±), limited by both amplitude and available slack. */
    private static int maxShift(int canvas, int dest) {
        int slackHalf = (dest - canvas) / 2;
        if (slackHalf < 1) return 0;
        int fromAmplitude = Math.round(canvas * AMPLITUDE);
        if (fromAmplitude < 1) return 0;
        return Math.min(slackHalf, fromAmplitude);
    }

    /**
     * Keep {@code dest >= canvas} covering {@code [0, canvas)}. When dest is
     * smaller, the origin is left unchanged.
     */
    private static int clampOrigin(int origin, int canvas, int dest) {
        int min = canvas - dest;
        int max = 0;
        if (min > max) return origin;
        if (origin < min) return min;
        if (origin > max) return max;
        return origin;
    }
}
