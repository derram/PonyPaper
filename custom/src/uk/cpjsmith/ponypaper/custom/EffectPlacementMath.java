package uk.cpjsmith.ponypaper.custom;

import java.awt.Rectangle;
import java.util.Random;
import uk.cpjsmith.ponypaper.PonyDefinition;

/**
 * Pure-Java placement math for Desktop Ponies–style effects in the editor.
 * Mirrors {@code PonyEffectDef#computeOrigin} / {@code computeOriginFixed}
 * without Android types so the desktop jar can preview attach points.
 */
public final class EffectPlacementMath {

    public static final int CELL_TOP_LEFT = 0;
    public static final int CELL_TOP = 1;
    public static final int CELL_TOP_RIGHT = 2;
    public static final int CELL_LEFT = 3;
    public static final int CELL_CENTER = 4;
    public static final int CELL_RIGHT = 5;
    public static final int CELL_BOTTOM_LEFT = 6;
    public static final int CELL_BOTTOM = 7;
    public static final int CELL_BOTTOM_RIGHT = 8;
    public static final int CELL_ANY = 9;
    public static final int CELL_ANY_NOT_CENTER = 10;

    /** Fixed 9-cell tokens (no Any variants), row-major Top_Left → Bottom_Right. */
    public static final String[] FIXED_CELL_TOKENS = {
        "Top_Left", "Top", "Top_Right",
        "Left", "Center", "Right",
        "Bottom_Left", "Bottom", "Bottom_Right"
    };

    private EffectPlacementMath() {}

    /**
     * Result of aligning a placement point on the pony bounds with a centering
     * point on the effect frame.
     */
    public static final class Origin {
        /** Point on the pony draw bounds (scaled pixels). */
        public final float attachX;
        public final float attachY;
        /** Top-left of the effect destination rect (scaled pixels). */
        public final float originX;
        public final float originY;
        /** Concrete 0–8 cell used for placement (Any resolved). */
        public final int resolvedPlacementCell;

        public Origin(float attachX, float attachY, float originX, float originY,
                int resolvedPlacementCell) {
            this.attachX = attachX;
            this.attachY = attachY;
            this.originX = originX;
            this.originY = originY;
            this.resolvedPlacementCell = resolvedPlacementCell;
        }

        public Rectangle effectDestRect(float effectW, float effectH) {
            return new Rectangle(
                    Math.round(originX),
                    Math.round(originY),
                    Math.round(effectW),
                    Math.round(effectH));
        }
    }

    /**
     * Maps a canonical placement/centering token to a cell index.
     * Unknown or null tokens become {@link #CELL_CENTER}.
     */
    public static int cellIndex(String token) {
        String canon = PonyDefinition.normalizePlacementToken(token);
        if (canon == null) {
            return CELL_CENTER;
        }
        switch (canon) {
            case "Top_Left":
                return CELL_TOP_LEFT;
            case "Top":
                return CELL_TOP;
            case "Top_Right":
                return CELL_TOP_RIGHT;
            case "Left":
                return CELL_LEFT;
            case "Right":
                return CELL_RIGHT;
            case "Bottom_Left":
                return CELL_BOTTOM_LEFT;
            case "Bottom":
                return CELL_BOTTOM;
            case "Bottom_Right":
                return CELL_BOTTOM_RIGHT;
            case "Any":
                return CELL_ANY;
            case "Any-Not_Center":
                return CELL_ANY_NOT_CENTER;
            case "Center":
            default:
                return CELL_CENTER;
        }
    }

    /** Canonical token for a fixed cell {@code 0..8}; null outside that range. */
    public static String tokenForCell(int cell) {
        if (cell < 0 || cell >= FIXED_CELL_TOKENS.length) {
            return null;
        }
        return FIXED_CELL_TOKENS[cell];
    }

    /** True when the token is {@code Any} or {@code Any-Not_Center}. */
    public static boolean isAnyPlacement(String token) {
        int cell = cellIndex(token);
        return cell == CELL_ANY || cell == CELL_ANY_NOT_CENTER;
    }

    /**
     * {@code float[]{xWeight, yWeight}} in {@code [0,1]} for a fixed cell
     * ({@code 0..8}). Matches wallpaper {@code PonyEffectDef} weights.
     */
    public static float[] cellWeights(int cell) {
        switch (cell) {
            case CELL_TOP_LEFT:
                return new float[] { 0f, 0f };
            case CELL_TOP:
                return new float[] { 0.5f, 0f };
            case CELL_TOP_RIGHT:
                return new float[] { 1f, 0f };
            case CELL_LEFT:
                return new float[] { 0f, 0.5f };
            case CELL_RIGHT:
                return new float[] { 1f, 0.5f };
            case CELL_BOTTOM_LEFT:
                return new float[] { 0f, 1f };
            case CELL_BOTTOM:
                return new float[] { 0.5f, 1f };
            case CELL_BOTTOM_RIGHT:
                return new float[] { 1f, 1f };
            case CELL_CENTER:
            default:
                return new float[] { 0.5f, 0.5f };
        }
    }

    /** Random concrete cell for {@link #CELL_ANY} or {@link #CELL_ANY_NOT_CENTER}. */
    public static int pickRandomCell(int mode, Random random) {
        if (random == null) {
            throw new IllegalArgumentException("random");
        }
        if (mode == CELL_ANY_NOT_CENTER) {
            int roll = random.nextInt(8);
            return roll < CELL_CENTER ? roll : roll + 1;
        }
        return random.nextInt(9);
    }

    /**
     * Computes attach point and effect top-left from pony draw bounds and
     * effect size (already scaled). When placement is Any / Any-Not_Center,
     * {@code resolvedPlacementOverride} must be a concrete cell {@code 0..8}
     * (preview resolves once and reuses / re-rolls).
     */
    public static Origin computeOrigin(
            Rectangle ponyBounds,
            float effectW,
            float effectH,
            String placementToken,
            String centeringToken,
            int resolvedPlacementOverride) {
        if (ponyBounds == null) {
            throw new IllegalArgumentException("ponyBounds");
        }
        int place = cellIndex(placementToken);
        if (place == CELL_ANY || place == CELL_ANY_NOT_CENTER) {
            if (resolvedPlacementOverride < 0 || resolvedPlacementOverride > 8) {
                throw new IllegalArgumentException(
                        "resolvedPlacementOverride must be 0..8 when placement is Any");
            }
            place = resolvedPlacementOverride;
        } else if (place < 0 || place > 8) {
            place = CELL_CENTER;
        }
        int center = cellIndex(centeringToken);
        if (center < 0 || center > 8) {
            center = CELL_CENTER;
        }
        float[] p = cellWeights(place);
        float[] c = cellWeights(center);
        float attachX = ponyBounds.x + ponyBounds.width * p[0];
        float attachY = ponyBounds.y + ponyBounds.height * p[1];
        float originX = attachX - effectW * c[0];
        float originY = attachY - effectH * c[1];
        return new Origin(attachX, attachY, originX, originY, place);
    }

    /**
     * Same as {@link #computeOrigin} but rolls Any when needed using {@code random}.
     */
    public static Origin computeOrigin(
            Rectangle ponyBounds,
            float effectW,
            float effectH,
            String placementToken,
            String centeringToken,
            Random random) {
        int place = cellIndex(placementToken);
        int resolved = place;
        if (place == CELL_ANY || place == CELL_ANY_NOT_CENTER) {
            resolved = pickRandomCell(place, random);
        }
        return computeOrigin(
                ponyBounds, effectW, effectH, placementToken, centeringToken, resolved);
    }
}
