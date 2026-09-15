package uk.cpjsmith.ponypaper;

/**
 * Parent-relative draw order for a grouped effect (follow, or planted while
 * it still overlaps the parent). Loose planted effects ignore this and
 * Y-sort with the herd.
 */
public final class EffectLayer {

    /** Paint after the parent sprite (default, omitted in XML). */
    public static final String FRONT = "front";
    /** Paint before the parent sprite (saddlebags, wings, ground shadow). */
    public static final String BACK = "back";

    private EffectLayer() {}

    /**
     * Normalizes a layer token. Null, empty, and unknown values become
     * {@link #FRONT}.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return FRONT;
        }
        String t = raw.trim().toLowerCase();
        if (t.equals(BACK)) {
            return BACK;
        }
        return FRONT;
    }

    /**
     * True when {@code raw} is omitted, empty, {@link #FRONT}, or {@link #BACK}.
     */
    public static boolean isKnownToken(String raw) {
        if (raw == null) {
            return true;
        }
        String t = raw.trim().toLowerCase();
        return t.isEmpty() || t.equals(FRONT) || t.equals(BACK);
    }

    public static boolean isBack(String layer) {
        return BACK.equals(normalize(layer));
    }
}
