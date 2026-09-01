package uk.cpjsmith.ponypaper;

import java.util.Random;

/**
 * Shared wander / movement-mode helpers for custom characters. Pony-level
 * {@code <wander>} is a soft destination band; action-level {@code <movement>}
 * can hard-lock an axis or opt into free targeting. Built-ins keep the default
 * soft-horizontal behaviour via inherit + wander={@link #WANDER_HORIZONTAL}.
 *
 * <p>When wander is {@link #WANDER_VERTICAL} (and the action is not hard
 * horizontal), the existing left/right sprite slots are treated as back/front:
 * moving up uses left (back), moving down uses right (front). See
 * {@link #usesVerticalFacing}.
 */
public final class WanderTarget {

    /** Soft prefer mostly-horizontal destinations (historical default). */
    public static final String WANDER_HORIZONTAL = "horizontal";
    /** Soft prefer mostly-vertical destinations. */
    public static final String WANDER_VERTICAL = "vertical";
    /** Each pick is soft-horizontal or soft-vertical (uniform). */
    public static final String WANDER_BOTH = "both";

    /** Use the pony {@code <wander>} preference (soft). */
    public static final String MOVE_INHERIT = "inherit";
    /** Hard lock: destination keeps current Y (or leaves left/right at that Y). */
    public static final String MOVE_HORIZONTAL = "horizontal";
    /** Hard lock: destination keeps current X (or leaves top/bottom at that X). */
    public static final String MOVE_VERTICAL = "vertical";
    /** Free 2D destination; ignores pony wander. */
    public static final String MOVE_ANY = "any";

    /** Soft horizontal band ({@code |Δy| < |Δx|}). */
    public static final int BAND_SOFT_H = 0;
    /** Soft vertical band ({@code |Δx| < |Δy|}). */
    public static final int BAND_SOFT_V = 1;
    /** Hard horizontal (pin Y). */
    public static final int BAND_HARD_H = 2;
    /** Hard vertical (pin X). */
    public static final int BAND_HARD_V = 3;
    /** Unconstrained on-screen / free off-screen. */
    public static final int BAND_ANY = 4;

    private WanderTarget() {}

    /**
     * Normalizes a pony {@code <wander>} token. Unknown or empty →
     * {@link #WANDER_HORIZONTAL}.
     */
    public static String normalizeWander(String raw) {
        if (raw == null) {
            return WANDER_HORIZONTAL;
        }
        String t = raw.trim().toLowerCase();
        if (t.equals(WANDER_VERTICAL)) {
            return WANDER_VERTICAL;
        }
        if (t.equals(WANDER_BOTH)) {
            return WANDER_BOTH;
        }
        // horizontal, horizontal_only, and anything else → horizontal default
        return WANDER_HORIZONTAL;
    }

    /**
     * Normalizes an action {@code <movement>} token. Accepts Desktop Ponies
     * aliases ({@code Horizontal_Only}, {@code All}, …). Unknown or empty →
     * {@link #MOVE_INHERIT}.
     */
    public static String normalizeMovement(String raw) {
        if (raw == null) {
            return MOVE_INHERIT;
        }
        String t = raw.trim().toLowerCase().replace('-', '_');
        if (t.isEmpty() || t.equals(MOVE_INHERIT)) {
            return MOVE_INHERIT;
        }
        if (t.equals(MOVE_HORIZONTAL) || t.equals("horizontal_only")) {
            return MOVE_HORIZONTAL;
        }
        if (t.equals(MOVE_VERTICAL) || t.equals("vertical_only")) {
            return MOVE_VERTICAL;
        }
        if (t.equals(MOVE_ANY) || t.equals("all")
                || t.equals("horizontal_vertical")
                || t.equals("diagonal_only")
                || t.equals("diagonal_horizontal")
                || t.equals("diagonal_vertical")) {
            return MOVE_ANY;
        }
        return MOVE_INHERIT;
    }

    public static boolean isKnownWander(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim().toLowerCase();
        return t.equals(WANDER_HORIZONTAL) || t.equals(WANDER_VERTICAL) || t.equals(WANDER_BOTH);
    }

    /**
     * True when {@code raw} normalizes to a concrete movement token (including
     * Desktop Ponies aliases). Empty / inherit / unknown → false for “is set”.
     */
    public static boolean isKnownMovement(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim().toLowerCase().replace('-', '_');
        if (t.isEmpty()) {
            return false;
        }
        if (t.equals(MOVE_INHERIT) || t.equals(MOVE_HORIZONTAL) || t.equals(MOVE_VERTICAL)
                || t.equals(MOVE_ANY) || t.equals("horizontal_only") || t.equals("vertical_only")
                || t.equals("all") || t.equals("horizontal_vertical") || t.equals("diagonal_only")
                || t.equals("diagonal_horizontal") || t.equals("diagonal_vertical")) {
            return true;
        }
        return false;
    }

    /**
     * Maps pony wander + action movement to a destination band. {@code inherit}
     * uses soft bands from wander ({@link #WANDER_BOTH} coin-flips); explicit
     * horizontal/vertical on the action are hard locks.
     */
    public static int resolveBand(String wander, String movement, Random random) {
        String move = normalizeMovement(movement);
        if (move.equals(MOVE_HORIZONTAL)) {
            return BAND_HARD_H;
        }
        if (move.equals(MOVE_VERTICAL)) {
            return BAND_HARD_V;
        }
        if (move.equals(MOVE_ANY)) {
            return BAND_ANY;
        }
        // inherit → soft from wander
        String w = normalizeWander(wander);
        if (w.equals(WANDER_VERTICAL)) {
            return BAND_SOFT_V;
        }
        if (w.equals(WANDER_BOTH)) {
            if (random != null && random.nextBoolean()) {
                return BAND_SOFT_V;
            }
            return BAND_SOFT_H;
        }
        return BAND_SOFT_H;
    }

    /** Soft-horizontal accept: major axis is X. */
    public static boolean acceptsSoftHorizontal(float dx, float dy) {
        return Math.abs(dy) < Math.abs(dx);
    }

    /** Soft-vertical accept: major axis is Y. */
    public static boolean acceptsSoftVertical(float dx, float dy) {
        return Math.abs(dx) < Math.abs(dy);
    }

    /**
     * True when facing should follow vertical travel (Δy) instead of
     * horizontal (Δx). Only when pony wander is {@link #WANDER_VERTICAL}, and
     * not when the action hard-locks {@link #MOVE_HORIZONTAL} (those clips keep
     * classic left/right by Δx). {@link #WANDER_BOTH} never remaps — only two
     * sprite slots exist.
     *
     * <p>Convention: XML {@code direction="left"} = back (up),
     * {@code direction="right"} = front (down).
     */
    public static boolean usesVerticalFacing(String wander, String movement) {
        if (!WANDER_VERTICAL.equals(normalizeWander(wander))) {
            return false;
        }
        return !MOVE_HORIZONTAL.equals(normalizeMovement(movement));
    }

    /**
     * Maps a Desktop Ponies Allowed Moves string onto an action
     * {@code <movement>} value. Stationary / drag tokens return
     * {@link #MOVE_INHERIT} (role classification handles those separately).
     */
    public static String movementFromDesktopAllowedMoves(String raw) {
        if (raw == null) {
            return MOVE_INHERIT;
        }
        String t = raw.trim().toLowerCase().replace('-', '_');
        if (t.equals("none") || t.equals("mouseover") || t.equals("sleep") || t.equals("dragged")) {
            return MOVE_INHERIT;
        }
        return normalizeMovement(raw);
    }
}
