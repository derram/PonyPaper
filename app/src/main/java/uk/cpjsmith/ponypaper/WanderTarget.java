package uk.cpjsmith.ponypaper;

import java.util.Random;

/**
 * Shared wander / movement-mode helpers for custom characters. Pony-level
 * {@code <wander>} sets defaults and editor chrome; action-level
 * {@code <movement>} chooses the destination band and whether left/right
 * sheets mean back/front. Built-ins keep soft-horizontal behaviour via
 * omitted/{@link #MOVE_INHERIT} movement.
 *
 * <p>Facing: {@link #MOVE_SOFT_VERTICAL} and hard {@link #MOVE_VERTICAL} treat
 * left/right slots as back/front (up→left/back, down→right/front). See
 * {@link #usesVerticalFacing}. Compat: omitted/{@link #MOVE_INHERIT} on a
 * {@link #WANDER_VERTICAL} pony still resolves as soft vertical.
 */
public final class WanderTarget {

    /** Soft prefer mostly-horizontal destinations (historical default). */
    public static final String WANDER_HORIZONTAL = "horizontal";
    /** Soft prefer mostly-vertical destinations (default for omitted movement). */
    public static final String WANDER_VERTICAL = "vertical";
    /**
     * Mixed-axis authoring: use per-action {@link #MOVE_INHERIT} (soft H) and
     * {@link #MOVE_SOFT_VERTICAL} clips rather than a coin-flip.
     */
    public static final String WANDER_BOTH = "both";

    /**
     * Soft horizontal wander (default; omitted in XML). Always soft-H except
     * the vertical-pony compat shim in {@link #effectiveMovement}.
     */
    public static final String MOVE_INHERIT = "inherit";
    /** Soft vertical wander with back/front facing. */
    public static final String MOVE_SOFT_VERTICAL = "soft_vertical";
    /** Hard lock: destination keeps current Y (or leaves left/right at that Y). */
    public static final String MOVE_HORIZONTAL = "horizontal";
    /** Hard lock: destination keeps current X (or leaves top/bottom at that X). */
    public static final String MOVE_VERTICAL = "vertical";
    /** Free 2D destination; ignores pony wander. Classic left/right facing. */
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
     * aliases ({@code Horizontal_Only}, {@code All}, …) and
     * {@code vertical_wander} as {@link #MOVE_SOFT_VERTICAL}. Unknown or empty →
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
        if (t.equals(MOVE_SOFT_VERTICAL) || t.equals("vertical_wander")) {
            return MOVE_SOFT_VERTICAL;
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
        if (t.equals(MOVE_INHERIT) || t.equals(MOVE_SOFT_VERTICAL) || t.equals("vertical_wander")
                || t.equals(MOVE_HORIZONTAL) || t.equals(MOVE_VERTICAL)
                || t.equals(MOVE_ANY) || t.equals("horizontal_only") || t.equals("vertical_only")
                || t.equals("all") || t.equals("horizontal_vertical") || t.equals("diagonal_only")
                || t.equals("diagonal_horizontal") || t.equals("diagonal_vertical")) {
            return true;
        }
        return false;
    }

    /**
     * Resolves stored movement against pony wander. Omitted/{@link #MOVE_INHERIT}
     * on a {@link #WANDER_VERTICAL} pony becomes {@link #MOVE_SOFT_VERTICAL}
     * so existing vertical OCs keep soft-V behaviour.
     */
    public static String effectiveMovement(String wander, String movement) {
        String move = normalizeMovement(movement);
        if (MOVE_INHERIT.equals(move)
                && WANDER_VERTICAL.equals(normalizeWander(wander))) {
            return MOVE_SOFT_VERTICAL;
        }
        return move;
    }

    /**
     * Default movement for a newly created action given pony wander.
     * Vertical ponies start on {@link #MOVE_SOFT_VERTICAL}; others on
     * {@link #MOVE_INHERIT} (soft horizontal).
     */
    public static String defaultMovementForWander(String wander) {
        if (WANDER_VERTICAL.equals(normalizeWander(wander))) {
            return MOVE_SOFT_VERTICAL;
        }
        return MOVE_INHERIT;
    }

    /**
     * Maps pony wander + action movement to a destination band.
     * {@link #MOVE_INHERIT} is soft-H except the vertical-pony compat shim;
     * {@link #MOVE_SOFT_VERTICAL} is always soft-V; hard horizontal/vertical
     * pin an axis; {@link #MOVE_ANY} is free 2D.
     */
    public static int resolveBand(String wander, String movement, Random random) {
        String move = effectiveMovement(wander, movement);
        if (move.equals(MOVE_HORIZONTAL)) {
            return BAND_HARD_H;
        }
        if (move.equals(MOVE_VERTICAL)) {
            return BAND_HARD_V;
        }
        if (move.equals(MOVE_ANY)) {
            return BAND_ANY;
        }
        if (move.equals(MOVE_SOFT_VERTICAL)) {
            return BAND_SOFT_V;
        }
        // inherit (soft horizontal) — random unused after Both coin-flip removal
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
     * horizontal (Δx). True for {@link #MOVE_SOFT_VERTICAL} and hard
     * {@link #MOVE_VERTICAL}, including vertical-pony + omitted inherit via
     * {@link #effectiveMovement}. Hard {@link #MOVE_HORIZONTAL} and
     * {@link #MOVE_ANY} keep classic left/right by Δx.
     *
     * <p>Convention: XML {@code direction="left"} = back (up),
     * {@code direction="right"} = front (down).
     */
    public static boolean usesVerticalFacing(String wander, String movement) {
        String move = effectiveMovement(wander, movement);
        return MOVE_SOFT_VERTICAL.equals(move) || MOVE_VERTICAL.equals(move);
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
