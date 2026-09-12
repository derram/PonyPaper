package uk.cpjsmith.ponypaper;

/**
 * Dream album-cycle cross-fade: incoming src-over alpha over an opaque outgoing
 * bitmap. Duration is a constant; the controller owns the two bitmaps.
 */
public final class BackgroundCrossfade {

    /** Incoming overlay length. Cycle dwell is minutes; this is a brief blend. */
    public static final int DURATION_MS = 600;

    private BackgroundCrossfade() {}

    /**
     * Incoming src-over alpha in {@code 0..255}. {@code 255} means the fade is
     * done (draw only the new bitmap).
     */
    public static int incomingAlpha(long elapsedMs) {
        return incomingAlpha(elapsedMs, DURATION_MS);
    }

    public static int incomingAlpha(long elapsedMs, int durationMs) {
        if (durationMs <= 0 || elapsedMs >= durationMs) return 255;
        if (elapsedMs <= 0) return 0;
        return (int) (elapsedMs * 255L / durationMs);
    }

    /**
     * Fade when there is a previous image and the fill/sprite paint is still
     * opaque. Berry Punch's settled translucent fill would double-darken.
     */
    public static boolean shouldFade(boolean hasPrevious, boolean fillOpaque) {
        return hasPrevious && fillOpaque;
    }
}
