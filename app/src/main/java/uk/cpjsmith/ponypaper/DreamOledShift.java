package uk.cpjsmith.ponypaper;

/**
 * Slow integer pan of the screensaver background so a still image does not
 * sit on the same pixels for hours on a docked OLED.
 *
 * <p>Uses a Lissajous path like {@link DreamClock}, but slower and smaller.
 * Periods are incommensurate with the clock so the two drifts do not lock.
 * Destinations are pixel-aligned: the scene draws backgrounds with nearest
 * neighbour, so a subpixel pan would shimmer.
 *
 * <p>Cover dests (larger than the canvas) pan inside the crop. Contain dests
 * (smaller on one axis) pan inside the letterbox/pillarbox. Matching aspect
 * is a no-op: the dest is not over-zoomed to invent slack.
 */
final class DreamOledShift {

    /**
     * Horizontal drift period. Incommensurate with {@link #PERIOD_Y_MS} and
     * with {@link DreamClock}'s 97s / 139s path.
     */
    private static final long PERIOD_X_MS = 7 * 60 * 1000L;
    private static final long PERIOD_Y_MS = 11 * 60 * 1000L;
    /** Max offset from rest as a fraction of canvas width / height (±). */
    public static final float AMPLITUDE = 0.02f;

    private DreamOledShift() {}

    /**
     * Pans {@code dest} by a pixel-snapped Lissajous offset. Cover dests stay
     * covering the canvas; contain dests stay fully on-canvas. No-op on an
     * axis where {@code dest} matches the canvas.
     */
    static void apply(int canvasW, int canvasH, BackgroundFit.Dest dest, long uptimeMs) {
        if (dest == null || canvasW <= 0 || canvasH <= 0) return;
        int destW = dest.width();
        int destH = dest.height();
        int left = dest.left;
        int top = dest.top;
        if (destW != canvasW) {
            double phaseX = (2.0 * Math.PI * (uptimeMs % PERIOD_X_MS)) / PERIOD_X_MS;
            left = BackgroundFit.oledShiftedOrigin(dest.left, canvasW, destW,
                    AMPLITUDE, Math.sin(phaseX));
        }
        if (destH != canvasH) {
            double phaseY = (2.0 * Math.PI * (uptimeMs % PERIOD_Y_MS)) / PERIOD_Y_MS;
            // Cosine plus a fixed phase fills a 2D region, not a diagonal line.
            top = BackgroundFit.oledShiftedOrigin(dest.top, canvasH, destH,
                    AMPLITUDE, Math.cos(phaseY + Math.PI * 0.25));
        }
        dest.offsetTo(left, top);
    }
}
