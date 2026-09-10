package uk.cpjsmith.ponypaper;

/**
 * Drag-to-edge scene leave: which physical edge the feet are in, and whether
 * a traveling clip's facing axis may walk toward that edge.
 *
 * <p>{@code screen-out} / {@code teleport-out} leave in place and are not
 * tested here (they are legal on any edge).
 */
public final class DragExit {

    public static final int EDGE_NONE = 0;
    public static final int EDGE_LEFT = 1;
    public static final int EDGE_RIGHT = 2;
    public static final int EDGE_TOP = 3;
    public static final int EDGE_BOTTOM = 4;

    private DragExit() {}

    /**
     * Physical edge whose margin the feet sit in (or past). {@code pad} is the
     * same on-screen inset used for left/right gutters ({@code 30×scale}).
     * Corners pick the larger penetration; ties prefer left, then right, then
     * top, then bottom.
     *
     * @return {@link #EDGE_NONE} when the feet are inside all four pads
     */
    public static int nearestEdge(float x, float y, int left, int right,
            int top, int bottom, float pad) {
        if (pad < 0f) {
            pad = 0f;
        }
        float penLeft = (left + pad) - x;
        float penRight = x - (right - pad);
        float penTop = (top + pad) - y;
        float penBottom = y - (bottom - pad);
        int best = EDGE_NONE;
        float bestPen = Float.NEGATIVE_INFINITY;
        if (penLeft >= 0f && penLeft > bestPen) {
            bestPen = penLeft;
            best = EDGE_LEFT;
        }
        if (penRight >= 0f && penRight > bestPen) {
            bestPen = penRight;
            best = EDGE_RIGHT;
        }
        if (penTop >= 0f && penTop > bestPen) {
            bestPen = penTop;
            best = EDGE_TOP;
        }
        if (penBottom >= 0f && penBottom > bestPen) {
            best = EDGE_BOTTOM;
        }
        return best;
    }

    /** True for {@link #EDGE_TOP} / {@link #EDGE_BOTTOM}. */
    public static boolean usesVerticalEdge(int edge) {
        return edge == EDGE_TOP || edge == EDGE_BOTTOM;
    }

    /**
     * Sample {@code <movement>} token with the facing axis of {@code edge},
     * for {@link WanderTarget#sameFacingAxis} / leave picks. Side edges use
     * hard horizontal so a vertical-wander pony's inherit shim does not
     * reclassify a left/right throw as back/front.
     */
    public static String sampleMovementForEdge(int edge) {
        if (usesVerticalEdge(edge)) {
            return WanderTarget.MOVE_VERTICAL;
        }
        return WanderTarget.MOVE_HORIZONTAL;
    }

    /**
     * True when a traveling clip should walk toward {@code edge} rather than
     * its own nearest legal gutter. {@link WanderTarget#MOVE_ANY} matches
     * left/right only (same gutters as spawn / World Flow).
     */
    public static boolean travelMatchesEdge(String wander, String movement, int edge) {
        if (edge == EDGE_NONE) {
            return false;
        }
        return WanderTarget.usesVerticalFacing(wander, movement)
                == usesVerticalEdge(edge);
    }
}
