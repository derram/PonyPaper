package uk.cpjsmith.ponypaper;

/**
 * Spawn-bag policy for {@link SceneMode#WORLD_FLOW}.
 *
 * <p>World Flow reuses Wander herd preferences (checkboxes, mixes, pony count,
 * size, battery/dream caps) and forces every enter to be a crossing: always
 * leave via the opposite gutter, with no idle waiting. Soft/hard vertical
 * movers use top↔bottom at constant X; other bands use left↔right at constant Y.
 *
 * <p>Only {@linkplain #TYPE_NORMAL NORMAL} movers are selected. Authored
 * {@code <crossingactions>} and {@code <startactions>} NORMAL entries are
 * <strong>unioned</strong> so specialty crossing clips (e.g. Twilight's
 * {@code owl_trot}) share the stage with ordinary start gaits. Customs need
 * not duplicate their start list into crossing for World Flow variety; an
 * empty crossing list still uses start NORMALs alone (most built-ins).
 */
public final class WorldFlow {

    /** Keep in sync with {@link PonyAction#NORMAL}. */
    public static final int TYPE_NORMAL = 0;

    public static final int BAG_NONE = -1;
    public static final int BAG_CROSSING = 0;
    public static final int BAG_START = 1;
    /** Both bags contribute NORMAL movers (crossing first, then start). */
    public static final int BAG_UNION = 2;

    private WorldFlow() {}

    /** True when {@code type} may be used for a World Flow transit. */
    public static boolean isNormalTransit(int type) {
        return type == TYPE_NORMAL;
    }

    /**
     * Chooses which spawn bags supply World Flow NORMAL movers. When both
     * crossing and start have at least one NORMAL, returns {@link #BAG_UNION}
     * so specialty crossings need not re-list start gaits. Specials (teleport
     * / screen-in/out) are never selected.
     *
     * @param crossingTypes action types in the crossing bag (may be null/empty)
     * @param startTypes    action types in the start bag (may be null/empty)
     * @return {@link #BAG_UNION}, {@link #BAG_CROSSING}, {@link #BAG_START},
     *         or {@link #BAG_NONE}
     */
    public static int selectBagSource(int[] crossingTypes, int[] startTypes) {
        boolean cross = countNormal(crossingTypes) > 0;
        boolean start = countNormal(startTypes) > 0;
        if (cross && start) {
            return BAG_UNION;
        }
        if (cross) {
            return BAG_CROSSING;
        }
        if (start) {
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
