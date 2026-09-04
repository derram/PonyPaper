package uk.cpjsmith.ponypaper;

/**
 * Feet-anchored vertical insets for spawn and wander targets.
 *
 * <p>Logical pony position is feet (bottom-center of the sprite). On-screen
 * destinations keep the whole body below the top edge. Horizontal gutter
 * enter/exit Y uses a shorter top inset so crossings may peek over the top,
 * while feet can still sit near the bottom (no dead band from the old
 * center-anchor symmetric margin).
 */
public final class SpawnYBand {

    /** Extra pad above a full scaled frame height for on-screen destinations. */
    public static final float TOP_PAD = 8f;

    /** Clearance below feet at the bottom edge. */
    public static final float BOTTOM_PAD = 8f;

    /**
     * Fraction of scaled frame height used as the top inset for horizontal
     * gutter crossings (allows intentional top clipping).
     */
    public static final float CROSSING_TOP_FRACTION = 0.45f;

    /** Floor for crossing top inset (legacy side-gutter scale units). */
    public static final float CROSSING_TOP_MIN = 30f;

    /** Cap crossing top inset as a fraction of screen height (tall sheets). */
    public static final float CROSSING_TOP_MAX_SCREEN_FRACTION = 0.25f;

    private SpawnYBand() {}

    /**
     * Top inset so the full sprite stays on-screen under feet anchoring.
     *
     * @param maxUnscaledFrameH largest loaded frame height in unscaled pixels
     * @param scale             pony draw scale
     */
    public static int onScreenTopInset(int maxUnscaledFrameH, float scale) {
        int frameH = Math.max(0, maxUnscaledFrameH);
        return (int) (frameH * scale) + (int) (TOP_PAD * scale);
    }

    /**
     * Top inset for horizontal off-screen enter/exit Y: partial frame height so
     * some top clipping remains, floored at the old {@code 30×scale} margin and
     * capped for oversized sheets.
     *
     * @param maxUnscaledFrameH largest loaded frame height in unscaled pixels
     * @param scale             pony draw scale
     * @param screenHeight      current clip height in pixels
     */
    public static int crossingTopInset(int maxUnscaledFrameH, float scale, int screenHeight) {
        int frameH = Math.max(0, maxUnscaledFrameH);
        int fromFrame = (int) (frameH * scale * CROSSING_TOP_FRACTION);
        int min = (int) (CROSSING_TOP_MIN * scale);
        int top = Math.max(fromFrame, min);
        if (screenHeight > 0) {
            int cap = (int) (screenHeight * CROSSING_TOP_MAX_SCREEN_FRACTION);
            if (cap > 0 && top > cap) {
                top = cap;
            }
        }
        return top;
    }

    /** Bottom inset: feet may sit near the bottom edge. */
    public static int bottomInset(float scale) {
        return (int) (BOTTOM_PAD * scale);
    }

    /**
     * Inclusive minimum feet Y for a given top inset, or the vertical center
     * when the usable band collapses.
     */
    public static int minY(int screenTop, int screenHeight, int topInset, int bottomInset) {
        int min = screenTop + topInset;
        int max = screenTop + screenHeight - bottomInset;
        if (max < min) {
            return screenTop + screenHeight / 2;
        }
        return min;
    }

    /**
     * Inclusive maximum feet Y for a given bottom inset, or the vertical center
     * when the usable band collapses.
     */
    public static int maxY(int screenTop, int screenHeight, int topInset, int bottomInset) {
        int min = screenTop + topInset;
        int max = screenTop + screenHeight - bottomInset;
        if (max < min) {
            return screenTop + screenHeight / 2;
        }
        return max;
    }

    /**
     * Usable height for {@code Random#nextInt}; {@code 0} when the band is empty
     * (caller should fall back to center).
     */
    public static int usableHeight(int screenHeight, int topInset, int bottomInset) {
        int h = screenHeight - topInset - bottomInset;
        return h < 1 ? 0 : h;
    }
}
