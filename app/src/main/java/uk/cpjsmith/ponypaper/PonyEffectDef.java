package uk.cpjsmith.ponypaper;

import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.util.Base64;
import java.util.Random;

/**
 * Runtime definition of a Desktop Ponies–style effect: left/right sheets plus
 * placement, lifetime, and which {@link PonyAction} instances trigger it.
 * Sheets load through {@link SpriteCache} like custom actions.
 */
final class PonyEffectDef {

    /** Cap concurrent live effect instances across the scene. */
    static final int MAX_LIVE_INSTANCES = 48;

    private static final int CELL_TOP_LEFT = 0;
    private static final int CELL_TOP = 1;
    private static final int CELL_TOP_RIGHT = 2;
    private static final int CELL_LEFT = 3;
    private static final int CELL_CENTER = 4;
    private static final int CELL_RIGHT = 5;
    private static final int CELL_BOTTOM_LEFT = 6;
    private static final int CELL_BOTTOM = 7;
    private static final int CELL_BOTTOM_RIGHT = 8;
    private static final int CELL_ANY = 9;
    private static final int CELL_ANY_NOT_CENTER = 10;

    final String name;
    /** Action instances (including gait aliases) that start this effect. */
    private final PonyAction[] triggers;
    final float durationMs;
    final float repeatDelayMs;
    final boolean follow;
    final boolean noLoop;

    private final int placementLeft;
    private final int placementRight;
    private final int centeringLeft;
    private final int centeringRight;

    private final byte[] leftBytes;
    private final int[] leftTimes;
    private final byte[] rightBytes;
    private final int[] rightTimes;

    private SpriteSheet[] sprites;
    private SpriteCache.Pin leftPin;
    private SpriteCache.Pin rightPin;

    PonyEffectDef(PonyDefinition.Effect def, PonyAction[] triggers) {
        if (triggers == null || triggers.length == 0) {
            throw new IllegalArgumentException("triggers");
        }
        this.name = def.name != null ? def.name : "";
        this.triggers = triggers;
        this.durationMs = Math.max(0f, def.duration) * 1000f;
        this.repeatDelayMs = Math.max(0f, def.repeatDelay) * 1000f;
        this.follow = def.follow;
        this.noLoop = def.noLoop;
        this.placementLeft = cellIndex(def.placement.get("left"));
        this.placementRight = cellIndex(def.placement.get("right"));
        this.centeringLeft = cellIndex(def.centering.get("left"));
        this.centeringRight = cellIndex(def.centering.get("right"));
        this.leftBytes = Base64.decode(def.images.get("left"), 0);
        this.rightBytes = Base64.decode(def.images.get("right"), 0);
        this.leftTimes = parseTimes(def.timings.get("left"));
        this.rightTimes = parseTimes(def.timings.get("right"));
        validateSide(leftBytes, leftTimes, "left");
        validateSide(rightBytes, rightTimes, "right");
    }

    boolean triggersOn(PonyAction action) {
        if (action == null) {
            return false;
        }
        for (int i = 0; i < triggers.length; i++) {
            if (triggers[i] == action) {
                return true;
            }
        }
        return false;
    }

    void load() {
        synchronized (this) {
            if (sprites != null || leftPin != null) {
                return;
            }
            leftPin = SpriteCache.pinBytes(leftBytes, leftTimes);
            try {
                rightPin = SpriteCache.pinBytes(rightBytes, rightTimes);
            } catch (RuntimeException e) {
                leftPin.unpin();
                leftPin = null;
                throw e;
            }
        }
    }

    boolean isReady() {
        synchronized (this) {
            if (sprites != null) {
                return true;
            }
            if (leftPin == null || rightPin == null) {
                return false;
            }
            SpriteSheet left = leftPin.getSheet();
            SpriteSheet right = rightPin.getSheet();
            if (left == null || right == null) {
                return false;
            }
            sprites = new SpriteSheet[] { left, right };
            return true;
        }
    }

    boolean loadFailed() {
        synchronized (this) {
            return (leftPin != null && leftPin.failed())
                    || (rightPin != null && rightPin.failed());
        }
    }

    void unload() {
        synchronized (this) {
            if (leftPin != null) {
                leftPin.unpin();
                leftPin = null;
            }
            if (rightPin != null) {
                rightPin.unpin();
                rightPin = null;
            }
            sprites = null;
        }
    }

    SpriteSheet sheet(int dir) {
        if (sprites == null || dir < 0 || dir >= sprites.length) {
            return null;
        }
        return sprites[dir];
    }

    int animationTime(int dir) {
        SpriteSheet sheet = sheet(dir);
        return sheet != null ? sheet.totalTime : 0;
    }

    /**
     * Writes the effect's top-left destination origin into {@code outPos} (x,y)
     * given the pony's current draw bounds and facing.
     */
    void computeOrigin(RectF ponyBounds, int facing, float effectScale,
            Random random, float[] outPos, int[] resolvedPlacementOut) {
        int place = facing == PonyAction.LEFT ? placementLeft : placementRight;
        int center = facing == PonyAction.LEFT ? centeringLeft : centeringRight;
        if (place == CELL_ANY || place == CELL_ANY_NOT_CENTER) {
            place = pickRandomCell(place, random);
        }
        if (resolvedPlacementOut != null && resolvedPlacementOut.length > 0) {
            resolvedPlacementOut[0] = place;
        }
        SpriteSheet sheet = sheet(facing);
        float effectW = sheet != null ? sheet.frameWidth * effectScale : 0f;
        float effectH = sheet != null ? sheet.frameHeight * effectScale : 0f;
        float[] p = cellWeights(place);
        float[] c = cellWeights(center);
        float attachX = ponyBounds.left + ponyBounds.width() * p[0];
        float attachY = ponyBounds.top + ponyBounds.height() * p[1];
        outPos[0] = attachX - effectW * c[0];
        outPos[1] = attachY - effectH * c[1];
    }

    /** Recompute origin using a previously resolved placement cell (not Any). */
    void computeOriginFixed(RectF ponyBounds, int facing, float effectScale,
            int resolvedPlacement, float[] outPos) {
        int center = facing == PonyAction.LEFT ? centeringLeft : centeringRight;
        SpriteSheet sheet = sheet(facing);
        float effectW = sheet != null ? sheet.frameWidth * effectScale : 0f;
        float effectH = sheet != null ? sheet.frameHeight * effectScale : 0f;
        float[] p = cellWeights(resolvedPlacement);
        float[] c = cellWeights(center);
        float attachX = ponyBounds.left + ponyBounds.width() * p[0];
        float attachY = ponyBounds.top + ponyBounds.height() * p[1];
        outPos[0] = attachX - effectW * c[0];
        outPos[1] = attachY - effectH * c[1];
    }

    private static void validateSide(byte[] data, int[] times, String side) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw new IllegalArgumentException("Failed to decode effect " + side + " sprite sheet");
        }
        if (times.length == 0) {
            throw new IllegalArgumentException("Effect sprite sheet has no frame times");
        }
    }

    private static int[] parseTimes(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new int[0];
        }
        String[] parts = value.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }

    private static int cellIndex(String token) {
        String canon = PonyDefinition.normalizePlacementToken(token);
        if (canon == null) {
            return CELL_CENTER;
        }
        if (canon.equals("Top_Left")) return CELL_TOP_LEFT;
        if (canon.equals("Top")) return CELL_TOP;
        if (canon.equals("Top_Right")) return CELL_TOP_RIGHT;
        if (canon.equals("Left")) return CELL_LEFT;
        if (canon.equals("Center")) return CELL_CENTER;
        if (canon.equals("Right")) return CELL_RIGHT;
        if (canon.equals("Bottom_Left")) return CELL_BOTTOM_LEFT;
        if (canon.equals("Bottom")) return CELL_BOTTOM;
        if (canon.equals("Bottom_Right")) return CELL_BOTTOM_RIGHT;
        if (canon.equals("Any")) return CELL_ANY;
        if (canon.equals("Any-Not_Center")) return CELL_ANY_NOT_CENTER;
        return CELL_CENTER;
    }

    private static int pickRandomCell(int mode, Random random) {
        if (mode == CELL_ANY_NOT_CENTER) {
            int roll = random.nextInt(8);
            return roll < CELL_CENTER ? roll : roll + 1;
        }
        return random.nextInt(9);
    }

    /** @return {@code float[]{xWeight, yWeight}} in {@code [0,1]} */
    private static float[] cellWeights(int cell) {
        switch (cell) {
            case CELL_TOP_LEFT: return new float[] { 0f, 0f };
            case CELL_TOP: return new float[] { 0.5f, 0f };
            case CELL_TOP_RIGHT: return new float[] { 1f, 0f };
            case CELL_LEFT: return new float[] { 0f, 0.5f };
            case CELL_RIGHT: return new float[] { 1f, 0.5f };
            case CELL_BOTTOM_LEFT: return new float[] { 0f, 1f };
            case CELL_BOTTOM: return new float[] { 0.5f, 1f };
            case CELL_BOTTOM_RIGHT: return new float[] { 1f, 1f };
            case CELL_CENTER:
            default:
                return new float[] { 0.5f, 0.5f };
        }
    }
}
