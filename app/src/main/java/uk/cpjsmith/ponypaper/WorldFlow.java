package uk.cpjsmith.ponypaper;

/**
 * Spawn-bag policy for {@link SceneMode#WORLD_FLOW}.
 *
 * <p>World Flow reuses Wander herd preferences (checkboxes, mixes, pony count,
 * size, battery/dream caps) and forces every enter to be a crossing: always
 * leave via the opposite horizontal gutter, with no idle waiting.
 *
 * <p>Only {@linkplain #TYPE_NORMAL NORMAL} movers are selected. Authored
 * {@code <crossingactions>} NORMAL entries are preferred; when that bag has
 * none, start-action NORMAL movers are used as a fallback (most built-ins have
 * an empty crossing list).
 *
 * <p>Technically incomplete until vertical-wander spawning support exists:
 * every transit still uses horizontal opposite-gutter enter/leave even when
 * the picked mover prefers vertical travel.
 */
public final class WorldFlow {

    /** Keep in sync with {@link PonyAction#NORMAL}. */
    public static final int TYPE_NORMAL = 0;

    public static final int BAG_NONE = -1;
    public static final int BAG_CROSSING = 0;
    public static final int BAG_START = 1;

    private WorldFlow() {}

    /** True when {@code type} may be used for a World Flow transit. */
    public static boolean isNormalTransit(int type) {
        return type == TYPE_NORMAL;
    }

    /**
     * Prefers crossing NORMAL movers; otherwise falls back to start NORMAL
     * movers. Specials (teleport / screen-in/out) are never selected.
     *
     * @param crossingTypes action types in the crossing bag (may be null/empty)
     * @param startTypes    action types in the start bag (may be null/empty)
     * @return {@link #BAG_CROSSING}, {@link #BAG_START}, or {@link #BAG_NONE}
     */
    public static int selectBagSource(int[] crossingTypes, int[] startTypes) {
        if (countNormal(crossingTypes) > 0) {
            return BAG_CROSSING;
        }
        if (countNormal(startTypes) > 0) {
            return BAG_START;
        }
        return BAG_NONE;
    }

    /** Number of NORMAL entries in {@code types}. */
    public static int countNormal(int[] types) {
        if (types == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < types.length; i++) {
            if (isNormalTransit(types[i])) {
                n++;
            }
        }
        return n;
    }
}
