package uk.cpjsmith.ponypaper;

/**
 * Feet-anchored vertical insets for spawn and wander targets.
 *
 * <p>Logical pony position is feet (bottom-center of the sprite). On-screen
 * destinations keep the whole body below the top edge. Horizontal gutter
 * enter/exit Y uses a shorter top inset so crossings may peek over the top,
 * while feet can still sit near the bottom (no dead band from the old
 * center-anchor symmetric margin).
 *
 * <p>Vertical gutter enter/exit Y (top/bottom off-screen) is asymmetric: a
 * small pad above the top edge is enough (sprite hangs above the feet), while
 * the bottom must clear a full frame height so the body is off-screen when
 * despawn runs.
 */
public final class SpawnYBand {

    /** Extra pad above a full scaled frame height for on-screen destinations. */
    public static final float TOP_PAD = 8f;

    /** Clearance below feet at the bottom edge. */
    public static final float BOTTOM_PAD = 8f;

    /** Off-screen pad past the top edge for vertical gutter enter/exit. */
    public static final float VERTICAL_GUTTER_PAD = 30f;

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

    /**
     * Feet Y just past the top or bottom edge for a vertical gutter enter/exit.
     *
     * @param exitTop           {@code true} for above the top edge
     * @param screenTop         clip top
     * @param screenBottom      clip bottom ({@code top + height})
     * @param maxUnscaledFrameH largest loaded frame height in unscaled pixels
     * @param scale             pony draw scale
     */
    public static int verticalGutterY(boolean exitTop, int screenTop, int screenBottom,
            int maxUnscaledFrameH, float scale) {
        int pad = (int) (VERTICAL_GUTTER_PAD * scale);
        if (exitTop) {
            return screenTop - pad;
        }
        return screenBottom + onScreenTopInset(maxUnscaledFrameH, scale);
    }

    /**
     * Opposite vertical-gutter Y for a crossing that started at {@code startY},
     * keeping the same feet X at the call site.
     */
    public static int oppositeVerticalGutterY(int startY, int screenCenterY,
            int screenTop, int screenBottom, int maxUnscaledFrameH, float scale) {
        boolean startedTop = startY < screenCenterY;
        return verticalGutterY(!startedTop, screenTop, screenBottom,
                maxUnscaledFrameH, scale);
    }
}
