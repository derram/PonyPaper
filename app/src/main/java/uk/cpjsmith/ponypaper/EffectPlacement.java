package uk.cpjsmith.ponypaper;

/**
 * Shared effect placement helpers used by the wallpaper and the desktop
 * editor. Desktop Ponies–compatible {@code bounds} mode attaches to the
 * sprite AABB as authored. Opt-in {@code motion} mode rotates directional
 * cells so Left/Right/Top/Bottom track travel elevation on diagonals while
 * leaving idle and pure-horizontal placement unchanged.
 */
public final class EffectPlacement {

    /** Attach using the sprite draw bounds only (Desktop Ponies default). */
    public static final String MODE_BOUNDS = "bounds";
    /**
     * Rotate authored placement cells with the pony's travel direction so
     * side attaches stay in the wake / lead of diagonal motion.
     */
    public static final String MODE_MOTION = "motion";

    /** Same values as {@link PonyAction#LEFT} / {@link PonyAction#RIGHT}. */
    public static final int FACING_LEFT = 0;
    public static final int FACING_RIGHT = 1;

    /** Ignore travel shorter than this (pixels) — treat as idle. */
    public static final float TRAVEL_EPSILON = 0.5f;

    private EffectPlacement() {}

    /**
     * Normalizes a placement-mode token. Unknown or empty values become
     * {@link #MODE_BOUNDS}.
     */
    public static String normalizeMode(String raw) {
        if (raw == null) {
            return MODE_BOUNDS;
        }
        String t = raw.trim().toLowerCase();
        if (t.equals(MODE_MOTION)) {
            return MODE_MOTION;
        }
        return MODE_BOUNDS;
    }

    public static boolean isMotionMode(String mode) {
        return MODE_MOTION.equals(normalizeMode(mode));
    }

    /**
     * Remaps a concrete placement cell {@code 0..8} for motion-relative
     * attach. {@link #CELL_CENTER} and idle (near-zero travel) are unchanged.
     * The cell is rotated by the angle between horizontal facing and the
     * travel vector so authored Left/Right stay correct when moving
     * horizontally, and pick up Top/Bottom bias on diagonals.
     *
     * @param cell   concrete cell index {@code 0..8}
     * @param travelX travel delta X (screen space; +right)
     * @param travelY travel delta Y (screen space; +down)
     * @param facing  {@link #FACING_LEFT} or {@link #FACING_RIGHT}
     * @return remapped cell {@code 0..8}
     */
    public static int remapCellForMotion(int cell, float travelX, float travelY, int facing) {
        if (cell < 0 || cell > 8 || cell == 4 /* center */) {
            return cell;
        }
        float mag = (float) Math.sqrt(travelX * travelX + travelY * travelY);
        if (mag < TRAVEL_EPSILON) {
            return cell;
        }
        float tx = travelX / mag;
        float ty = travelY / mag;

        int col = cell % 3;
        int row = cell / 3;
        float ox = col - 1f;
        float oy = row - 1f;

        float baseAngle = facing == FACING_RIGHT ? 0f : (float) Math.PI;
        float travelAngle = (float) Math.atan2(ty, tx);
        float delta = travelAngle - baseAngle;
        if (delta > Math.PI) {
            delta -= (float) (2.0 * Math.PI);
        } else if (delta < -Math.PI) {
            delta += (float) (2.0 * Math.PI);
        }

        float cos = (float) Math.cos(delta);
        float sin = (float) Math.sin(delta);
        float rx = ox * cos - oy * sin;
        float ry = ox * sin + oy * cos;

        int newCol = clamp(Math.round(rx) + 1, 0, 2);
        int newRow = clamp(Math.round(ry) + 1, 0, 2);
        return newRow * 3 + newCol;
    }

    /**
     * Applies motion remapping when {@code motionMode} is true; otherwise
     * returns {@code cell} unchanged.
     */
    public static int maybeRemapCell(boolean motionMode, int cell,
            float travelX, float travelY, int facing) {
        if (!motionMode) {
            return cell;
        }
        return remapCellForMotion(cell, travelX, travelY, facing);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
