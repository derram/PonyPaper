package uk.cpjsmith.ponypaper;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Base64;
import java.util.Random;

/**
 * Encapsulates a particular action for a pony. Ultimately, this unites the
 * {@code SpriteSheet} for the left- and right-facing modes of the action, as
 * well as information on possible next states. On construction, the sprites
 * are not immediately loaded and the {@link #getAnimationTime} and {@link
 * #drawOn} methods will fail with {@code NullPointerException} until
 * {@link #load} has been called and {@link #isReady} is true.
 *
 * <p>Each action carries a {@link #speed} factor used both as travel speed
 * (when moving) and as animation playback rate (moving or idle). Multiple
 * actions may share one sprite sheet via {@link #PonyAction(PonyAction, float)}
 * so gait/idle variants do not load duplicate bitmaps. Distinct pony
 * instances (wallpaper vs dream) share decoded sheets via {@link SpriteCache}.
 */
public class PonyAction {
    
    /**
     * Type constant for actions which follow the normal rules. I.e. if/when
     * used for moving, the pony travels toward its destination at
     * {@link #speed} times the global movement ceiling.
     */
    public static final int NORMAL = 0;
    /**
     * Type constant for actions which follow the teleport-out rules. I.e. when
     * used for moving, the pony remains stationary for one loop of the
     * action's animation, then jumps to the target and changes to the next
     * moving action.
     */
    public static final int PORT_O = 1;
    /**
     * Type constant for actions which follow the teleport-in rules. I.e. when
     * used for moving, the pony remains stationary for one loop of the
     * action's animation, then changes to the next waiting action.
     */
    public static final int PORT_I = 2;
    /**
     * Type constant for appear-in-place clips. Used as a start action: spawn
     * on-screen, play once without interpolating, then idle.
     */
    public static final int SCREEN_IN = 3;
    /**
     * Type constant for vanish-in-place clips. Used as a mover: play once
     * without interpolating, then leave the scene (after the shared 1-in-8
     * destination roll, unless drag-to-edge forces the exit).
     */
    public static final int SCREEN_OUT = 4;
    
    /**
     * Left-facing sheet index. When {@link WanderTarget#usesVerticalFacing} is
     * true (soft-vertical or hard-vertical movement), this slot is the
     * <em>back</em> view (selected when traveling up).
     */
    public static final int LEFT = 0;
    /**
     * Right-facing sheet index. When {@link WanderTarget#usesVerticalFacing} is
     * true (soft-vertical or hard-vertical movement), this slot is the
     * <em>front</em> view (selected when traveling down).
     */
    public static final int RIGHT = 1;
    
    /** Default speed factor (full historical travel / animation rate). */
    public static final float DEFAULT_SPEED = 1.0f;
    
    /**
     * The type of action; {@code NORMAL}, {@code PORT_O}, {@code PORT_I},
     * {@code SCREEN_IN} or {@code SCREEN_OUT}.
     */
    public final int type;
    
    /**
     * Speed factor for this action. While {@code NORMAL} moving, multiplies
     * both travel distance per second and animation rate. While waiting (or
     * any non-travel use), multiplies animation rate only. Typical values
     * are in {@code (0, 1]}; values above 1 are allowed for fast characters.
     */
    public final float speed;
    
    /**
     * When true, the animation wraps while this action is active. When false,
     * one full play advances via the next-action list for the current motion
     * context (see {@link Pony}). Built-in actions always loop; custom XML
     * may set {@code <loop>false</loop>} for transition clips.
     */
    public final boolean loops;

    /**
     * Destination axis while this action travels. Built-ins and omitted XML
     * use {@link WanderTarget#MOVE_INHERIT} (soft horizontal). See
     * {@link WanderTarget}.
     */
    private String movement = WanderTarget.MOVE_INHERIT;
    
    /* To create the sprite sheets for a built-in pony. */
    private Resources res;
    private int leftDrawableId;
    private int leftTimingId;
    private int rightDrawableId;
    private int rightTimingId;
    /* To create the sprite sheets for a custom pony. */
    private CustomSheets custom;
    /**
     * When non-null, this action reuses {@code spriteSource}'s loaded sheets
     * (gait/idle aliases). Load/unload ownership stays with the source.
     */
    private PonyAction spriteSource;
    
    private SpriteSheet[] sprites;
    private SpriteCache.Pin leftPin;
    private SpriteCache.Pin rightPin;
    
    /**
     * Unscaled X of the feet hotspot within each frame per facing
     * ({@link #LEFT}/{@link #RIGHT}), pixels from the left of that sheet's frame.
     * When {@link Float#NaN}, {@link #getAnchorX} uses the frame centre.
     * Asymmetric VFX sheets set this so world position stays on the body;
     * left and right often differ when sheets are mirrors.
     */
    private final float[] anchorX = new float[] { Float.NaN, Float.NaN };
    
    /**
     * Unscaled Y of the feet hotspot within each frame per facing
     * ({@link #LEFT}/{@link #RIGHT}), pixels from the top of that sheet's frame.
     * When {@link Float#NaN}, {@link #getAnchorY} uses the frame bottom.
     * Tall VFX sheets (teleports) set this so world position stays on the hooves.
     */
    private final float[] anchorY = new float[] { Float.NaN, Float.NaN };
    
    private PonyAction[] nextWaiting;
    private PonyAction[] nextMoving;
    private PonyAction[] nextDrag;

    /**
     * Stable catalog / scene id. Built-ins: semantic stem from
     * {@link BuiltInActionIds#stem}. Customs: XML {@code <action name>}.
     * Speed aliases inherit the sprite owner's id and are not separate catalog
     * entries.
     */
    private String actionId = "";
    
    /**
     * Constructs an action of type {@code NORMAL} at {@link #DEFAULT_SPEED}.
     * 
     * @param res     the {@code Resources} object to load from
     * @param arrayId the ID of the action's main array resource
     * @see #PonyAction(Resources, int, int, float)
     */
    public PonyAction(Resources res, int arrayId) {
        this(res, arrayId, NORMAL, DEFAULT_SPEED);
    }
    
    /**
     * Constructs an action of type {@code NORMAL} at the given speed.
     * 
     * @param res     the {@code Resources} object to load from
     * @param arrayId the ID of the action's main array resource
     * @param speed   travel / animation speed factor
     */
    public PonyAction(Resources res, int arrayId, float speed) {
        this(res, arrayId, NORMAL, speed);
    }
    
    /**
     * Constructs an action of the given type at {@link #DEFAULT_SPEED}.
     * 
     * @param res     the {@code Resources} object to load from
     * @param arrayId the ID of the action's main array resource
     * @param type    the type of action
     */
    public PonyAction(Resources res, int arrayId, int type) {
        this(res, arrayId, type, DEFAULT_SPEED);
    }
    
    /**
     * Constructs an action of the given type and speed. The {@code arrayId}
     * parameter should be the ID of an array resource containing 4 other
     * resources - the drawable for leftwards motion, the array of frame times
     * for leftwards motion, the drawable for rightwards motion and the array
     * of frame times for rightwards motion, respectively.
     * 
     * @param res     the {@code Resources} object to load from
     * @param arrayId the ID of the action's main array resource
     * @param type    the type of action
     * @param speed   travel / animation speed factor
     */
    public PonyAction(Resources res, int arrayId, int type, float speed) {
        this.res = res;
        this.type = type;
        this.speed = sanitizeSpeed(speed);
        this.loops = true;
        TypedArray array = res.obtainTypedArray(arrayId);
        try {
            this.leftDrawableId = array.getResourceId(0, 0);
            this.leftTimingId = array.getResourceId(1, 0);
            this.rightDrawableId = array.getResourceId(2, 0);
            this.rightTimingId = array.getResourceId(3, 0);
        } finally {
            array.recycle();
        }
    }
    
    /**
     * Constructs an action that shares another action's sprite sheets but uses
     * a different speed factor (stroll/walk/trot or slow/fast idle variants).
     * Inherits the source's {@link #loops} flag (gait bags expand a base action).
     * 
     * @param spriteSource action that owns the bitmaps (must not itself be an alias)
     * @param speed        travel / animation speed factor for this variant
     */
    public PonyAction(PonyAction spriteSource, float speed) {
        this(spriteSource, speed, spriteSource != null ? spriteSource.loops : true);
    }
    
    /**
     * Constructs an action that shares another action's sprite sheets with an
     * explicit speed and loop policy (named {@code <spritesfrom>} aliases).
     * 
     * @param spriteSource action that owns the bitmaps (must not itself be an alias)
     * @param speed        travel / animation speed factor for this variant
     * @param loops        whether this alias loops its animation
     */
    public PonyAction(PonyAction spriteSource, float speed, boolean loops) {
        this(spriteSource, speed, loops, spriteSource != null ? spriteSource.type : NORMAL);
    }

    /**
     * Constructs an alias with an explicit action type so a vanish/appear
     * clip can reuse a stand sheet ({@code spritesfrom}) without inheriting
     * {@link #NORMAL} travel.
     *
     * @param spriteSource action that owns the bitmaps (must not itself be an alias)
     * @param speed        travel / animation speed factor for this variant
     * @param loops        whether this alias loops its animation
     * @param type         {@link #NORMAL}, {@link #PORT_O}, {@link #PORT_I},
     *                     {@link #SCREEN_IN} or {@link #SCREEN_OUT}
     */
    public PonyAction(PonyAction spriteSource, float speed, boolean loops, int type) {
        if (spriteSource == null) {
            throw new IllegalArgumentException("spriteSource");
        }
        // Resolve to the true owner so alias chains stay flat.
        this.spriteSource = spriteSource.spriteSource != null
                ? spriteSource.spriteSource : spriteSource;
        this.type = type;
        this.speed = sanitizeSpeed(speed);
        this.loops = loops;
        this.res = this.spriteSource.res;
        this.leftDrawableId = this.spriteSource.leftDrawableId;
        this.leftTimingId = this.spriteSource.leftTimingId;
        this.rightDrawableId = this.spriteSource.rightDrawableId;
        this.rightTimingId = this.spriteSource.rightTimingId;
        this.movement = this.spriteSource.movement;
        // Same sheets → same feet hotspots unless the alias overrides later.
        this.anchorX[LEFT] = this.spriteSource.anchorX[LEFT];
        this.anchorX[RIGHT] = this.spriteSource.anchorX[RIGHT];
        this.anchorY[LEFT] = this.spriteSource.anchorY[LEFT];
        this.anchorY[RIGHT] = this.spriteSource.anchorY[RIGHT];
        // Inherit for gait/idle variants; named custom aliases overwrite via setActionId.
        this.actionId = this.spriteSource.actionId;
    }
    
    /**
     * Constructs an action from a custom XML definition.
     * 
     * @param definition the action definition extracted from XML
     */
    public PonyAction(PonyDefinition.Action definition) {
        this.custom = new CustomSheets(definition);
        this.type = typeFromSpecial(definition.specialType);
        this.speed = sanitizeSpeed(definition.speed);
        this.loops = definition.loops;
        this.movement = WanderTarget.normalizeMovement(definition.movement);
        if (definition.name != null) {
            this.actionId = definition.name;
        }
        copyDefinitionAnchors(definition);
    }

    /** Stable action id for catalog / Tableau JSON (empty when unset). */
    public String actionId() {
        return actionId != null ? actionId : "";
    }

    /**
     * Sets the stable action id at {@code make*} / custom build time.
     *
     * @return this action (for chaining)
     */
    PonyAction setActionId(String actionId) {
        this.actionId = actionId != null ? actionId : "";
        return this;
    }

    /** True when this action borrows another action's sprite sheets. */
    boolean isAlias() {
        return spriteSource != null;
    }

    /**
     * True when left and right (or back and front) resolve to the same decoded
     * sheet. Facing still changes with travel; animation time is not reset.
     */
    boolean sharesFacingSheets() {
        if (spriteSource != null) {
            return spriteSource.sharesFacingSheets();
        }
        synchronized (this) {
            if (sprites != null && sprites.length > RIGHT && sprites[LEFT] != null) {
                return sprites[LEFT] == sprites[RIGHT];
            }
            if (custom != null) {
                custom.ensureKeys();
                return custom.leftKey.equals(custom.rightKey);
            }
            return leftDrawableId != 0 && leftDrawableId == rightDrawableId
                    && leftTimingId == rightTimingId;
        }
    }

    /**
     * Destination movement mode for this action ({@link WanderTarget#MOVE_INHERIT},
     * {@link WanderTarget#MOVE_SOFT_VERTICAL}, {@link WanderTarget#MOVE_HORIZONTAL},
     * {@link WanderTarget#MOVE_VERTICAL}, or {@link WanderTarget#MOVE_ANY}).
     */
    public String getMovement() {
        return movement != null ? movement : WanderTarget.MOVE_INHERIT;
    }

    /**
     * Sets destination movement mode. Used when a named alias overrides the
     * sprite owner's mode, or when Desktop Ponies import maps Allowed Moves.
     *
     * @return this action (for chaining)
     */
    public PonyAction setMovement(String movement) {
        this.movement = WanderTarget.normalizeMovement(movement);
        return this;
    }

    /**
     * Confirm custom images decode (bounds only) and timings are non-empty.
     * Full pixel decode happens later on the cache worker.
     */
    private static void validateDefinitionSide(byte[] data, int[] times, String side) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw new IllegalArgumentException("Failed to decode " + side + " sprite sheet");
        }
        if (times == null || times.length == 0) {
            throw new IllegalArgumentException("Sprite sheet has no frame times");
        }
    }

    /**
     * Decoded custom sheets plus a {@link SpriteCache} factory that captures
     * only those arrays (so an LRU entry does not pin this action). Keys are
     * filled on first {@link #load()} so Tableau wait-bag-only pins do not
     * SHA-256 unused sheets.
     */
    private static final class CustomSheets {
        final byte[] leftBytes;
        final byte[] rightBytes;
        final int[] leftTimes;
        final int[] rightTimes;
        final SpriteCache.SheetFactory leftFactory;
        final SpriteCache.SheetFactory rightFactory;
        String leftKey;
        String rightKey;

        CustomSheets(PonyDefinition.Action definition) {
            String leftB64 = definition.images.get("left");
            String rightB64 = definition.images.get("right");
            String leftTimingText = definition.timings.get("left");
            String rightTimingText = definition.timings.get("right");
            leftBytes = Base64.decode(leftB64, 0);
            leftTimes = parseInts(leftTimingText);
            validateDefinitionSide(leftBytes, leftTimes, "left");
            boolean shared = leftB64 != null && leftB64.equals(rightB64)
                    && leftTimingText != null && leftTimingText.equals(rightTimingText);
            if (shared) {
                rightBytes = leftBytes;
                rightTimes = leftTimes;
                leftFactory = SpriteCache.bytesFactory(leftBytes, leftTimes);
                rightFactory = leftFactory;
            } else {
                rightBytes = Base64.decode(rightB64, 0);
                rightTimes = parseInts(rightTimingText);
                validateDefinitionSide(rightBytes, rightTimes, "right");
                leftFactory = SpriteCache.bytesFactory(leftBytes, leftTimes);
                rightFactory = SpriteCache.bytesFactory(rightBytes, rightTimes);
            }
        }

        void ensureKeys() {
            if (leftKey == null) {
                leftKey = SpriteCache.bytesKey(leftBytes, leftTimes);
            }
            if (rightKey == null) {
                rightKey = (rightBytes == leftBytes && rightTimes == leftTimes)
                        ? leftKey
                        : SpriteCache.bytesKey(rightBytes, rightTimes);
            }
        }
    }
    
    private void copyDefinitionAnchors(PonyDefinition.Action definition) {
        float leftX = definition.getAnchorX("left");
        float rightX = definition.getAnchorX("right");
        float leftY = definition.getAnchorY("left");
        float rightY = definition.getAnchorY("right");
        if (!Float.isNaN(leftX) && leftX >= 0f) {
            this.anchorX[LEFT] = leftX;
        }
        if (!Float.isNaN(rightX) && rightX >= 0f) {
            this.anchorX[RIGHT] = rightX;
        }
        if (!Float.isNaN(leftY) && leftY >= 0f) {
            this.anchorY[LEFT] = leftY;
        }
        if (!Float.isNaN(rightY) && rightY >= 0f) {
            this.anchorY[RIGHT] = rightY;
        }
    }
    
    /**
     * Sets the unscaled feet column for both facings (pixels from the left of
     * each frame). Pass a negative value or {@link Float#NaN} to restore the
     * default frame-centre behaviour on both sides.
     *
     * @param anchorX feet X in source pixels, or {@code NaN}/negative to clear
     * @return this action (for chaining at construction sites)
     */
    public PonyAction setAnchorX(float anchorX) {
        setAnchorX(LEFT, anchorX);
        setAnchorX(RIGHT, anchorX);
        return this;
    }
    
    /**
     * Sets the unscaled feet column for one facing.
     *
     * @param dir     {@link #LEFT} or {@link #RIGHT}
     * @param anchorX feet X in source pixels, or {@code NaN}/negative to clear
     * @return this action (for chaining)
     */
    public PonyAction setAnchorX(int dir, float anchorX) {
        checkDir(dir);
        if (Float.isNaN(anchorX) || anchorX < 0f) {
            this.anchorX[dir] = Float.NaN;
        } else {
            this.anchorX[dir] = anchorX;
        }
        return this;
    }
    
    /**
     * Sets the unscaled feet row for both facings (pixels from the top of each
     * frame). Pass a negative value or {@link Float#NaN} to restore the default
     * bottom-center behaviour on both sides.
     *
     * @param anchorY feet Y in source pixels, or {@code NaN}/negative to clear
     * @return this action (for chaining at construction sites)
     */
    public PonyAction setAnchorY(float anchorY) {
        setAnchorY(LEFT, anchorY);
        setAnchorY(RIGHT, anchorY);
        return this;
    }
    
    /**
     * Sets the unscaled feet row for one facing.
     *
     * @param dir     {@link #LEFT} or {@link #RIGHT}
     * @param anchorY feet Y in source pixels, or {@code NaN}/negative to clear
     * @return this action (for chaining)
     */
    public PonyAction setAnchorY(int dir, float anchorY) {
        checkDir(dir);
        if (Float.isNaN(anchorY) || anchorY < 0f) {
            this.anchorY[dir] = Float.NaN;
        } else {
            this.anchorY[dir] = anchorY;
        }
        return this;
    }
    
    /**
     * Feet hotspot X for drawing and hit-tests. Explicit per-facing
     * {@link #setAnchorX} wins; otherwise the horizontal centre of the loaded
     * frame for that facing (bottom-center default).
     *
     * @param dir {@link #LEFT} or {@link #RIGHT}
     * @return unscaled pixels from the left of the frame to the feet
     */
    public float getAnchorX(int dir) {
        checkDir(dir);
        if (!Float.isNaN(anchorX[dir])) {
            return anchorX[dir];
        }
        return getFrameWidth(dir) / 2f;
    }
    
    /**
     * Feet hotspot Y for drawing and hit-tests. Explicit per-facing
     * {@link #setAnchorY} wins; otherwise the bottom of the loaded frame for
     * that facing (bottom-center default).
     *
     * @param dir {@link #LEFT} or {@link #RIGHT}
     * @return unscaled pixels from the top of the frame to the feet
     */
    public float getAnchorY(int dir) {
        checkDir(dir);
        if (!Float.isNaN(anchorY[dir])) {
            return anchorY[dir];
        }
        return getFrameHeight(dir);
    }
    
    private static void checkDir(int dir) {
        if (dir != LEFT && dir != RIGHT) {
            throw new IllegalArgumentException("dir must be LEFT or RIGHT");
        }
    }
    
    /**
     * Maps a {@code <specialtype>} string to an action type. Unknown or empty
     * values are {@link #NORMAL} ({@link PonyDefinition#validate} rejects
     * unknown names).
     */
    public static int typeFromSpecial(String specialType) {
        if (PonyDefinition.SPECIAL_TELEPORT_OUT.equals(specialType)) {
            return PORT_O;
        }
        if (PonyDefinition.SPECIAL_TELEPORT_IN.equals(specialType)) {
            return PORT_I;
        }
        if (PonyDefinition.SPECIAL_SCREEN_IN.equals(specialType)) {
            return SCREEN_IN;
        }
        if (PonyDefinition.SPECIAL_SCREEN_OUT.equals(specialType)) {
            return SCREEN_OUT;
        }
        return NORMAL;
    }

    private static float sanitizeSpeed(float speed) {
        if (Float.isNaN(speed) || speed <= 0f) {
            return DEFAULT_SPEED;
        }
        return speed;
    }
    
    private static int[] parseInts(String value) {
        String[] array = value.split(",");
        int[] result = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = Integer.parseInt(array[i]);
        }
        return result;
    }
    
    /**
     * Start pinning this action's sheets (or the owner's, for aliases).
     * Decode runs on {@link SpriteCache}'s worker; {@link #isReady()} is false
     * until both facings are published. Idempotent.
     *
     * @see #unload()
     */
    public void load() {
        if (spriteSource != null) {
            spriteSource.load();
            return;
        }
        synchronized (this) {
            if (sprites != null || leftPin != null) {
                return;
            }
            if (res != null) {
                leftPin = SpriteCache.pinResource(res, leftDrawableId, leftTimingId);
                if (leftDrawableId == rightDrawableId && leftTimingId == rightTimingId) {
                    rightPin = leftPin;
                } else {
                    try {
                        rightPin = SpriteCache.pinResource(res, rightDrawableId, rightTimingId);
                    } catch (RuntimeException e) {
                        leftPin.unpin();
                        leftPin = null;
                        throw e;
                    }
                }
            } else if (custom != null) {
                custom.ensureKeys();
                leftPin = SpriteCache.pin(custom.leftKey, custom.leftFactory);
                if (custom.leftKey.equals(custom.rightKey)) {
                    rightPin = leftPin;
                } else {
                    try {
                        rightPin = SpriteCache.pin(custom.rightKey, custom.rightFactory);
                    } catch (RuntimeException e) {
                        leftPin.unpin();
                        leftPin = null;
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * @return true when left and right sheets are decoded and bound
     */
    public boolean isReady() {
        if (spriteSource != null) {
            if (!spriteSource.isReady()) {
                return false;
            }
            synchronized (this) {
                sprites = spriteSource.sprites;
            }
            return sprites != null;
        }
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
            sprites = new SpriteSheet[] {left, right};
            return true;
        }
    }

    /**
     * @return true if a started pin failed to decode
     */
    public boolean loadFailed() {
        if (spriteSource != null) {
            return spriteSource.loadFailed();
        }
        synchronized (this) {
            return (leftPin != null && leftPin.failed())
                    || (rightPin != null && rightPin.failed());
        }
    }
    
    /**
     * Unpin this action's cache entries (owners) or drop the borrowed
     * reference (aliases). Safe when nothing is loaded.
     *
     * @see #load()
     */
    public void unload() {
        if (spriteSource != null) {
            synchronized (this) {
                sprites = null;
            }
            return;
        }
        synchronized (this) {
            SpriteCache.Pin left = leftPin;
            SpriteCache.Pin right = rightPin;
            leftPin = null;
            rightPin = null;
            sprites = null;
            if (left != null) {
                left.unpin();
            }
            if (right != null && right != left) {
                right.unpin();
            }
        }
    }
    
    public int getAnimationTime(int dir) {
        return sprites[dir].totalTime;
    }

    public int getFrameIndex(int dir, int time) {
        return sprites[dir].getFrameIndex(time);
    }
    
    /**
     * Unscaled frame size for the given facing. Used for hit-testing and layout
     * that must match {@link #drawOn}'s anchoring.
     *
     * @param dir {@link #LEFT} or {@link #RIGHT}
     * @return {@code int[]{frameWidth, frameHeight}} in source pixels
     */
    public int[] getFrameSize(int dir) {
        return new int[] { getFrameWidth(dir), getFrameHeight(dir) };
    }

    public int getFrameWidth(int dir) {
        if (sprites == null || dir < 0 || dir >= sprites.length || sprites[dir] == null) {
            return 0;
        }
        return sprites[dir].frameWidth;
    }

    public int getFrameHeight(int dir) {
        if (sprites == null || dir < 0 || dir >= sprites.length || sprites[dir] == null) {
            return 0;
        }
        return sprites[dir].frameHeight;
    }
    
    /**
     * Destination rect for drawing (and matching hit-tests) at logical feet
     * position {@code (x, y)} with the given scale. Horizontal placement uses
     * {@link #getAnchorX} (default frame centre); vertical placement uses
     * {@link #getAnchorY} so VFX can extend past the body without shifting it
     * when actions change.
     */
    public RectF getDrawBounds(float x, float y, float scale, int dir) {
        RectF out = new RectF();
        fillDrawBounds(x, y, scale, dir, out);
        return out;
    }

    void fillDrawBounds(float x, float y, float scale, int dir, RectF out) {
        float dW = getFrameWidth(dir) * scale;
        float dH = getFrameHeight(dir) * scale;
        float ax = getAnchorX(dir) * scale;
        float ay = getAnchorY(dir) * scale;
        out.set(x - ax, y - ay, x - ax + dW, y - ay + dH);
    }

    /**
     * Pixel-snapped destination for {@link Canvas#drawBitmap}. Width/height are
     * rounded independently of origin so the sprite does not breathe by 1px.
     */
    void fillDestRect(float x, float y, float scale, int dir, Rect out) {
        float dW = getFrameWidth(dir) * scale;
        float dH = getFrameHeight(dir) * scale;
        float ax = getAnchorX(dir) * scale;
        float ay = getAnchorY(dir) * scale;
        int left = Math.round(x - ax);
        int top = Math.round(y - ay);
        out.set(left, top, left + Math.round(dW), top + Math.round(dH));
    }
    
    public void drawOn(Canvas c, int dir, int time, float x, float y, float scale,
            boolean dragged, Rect srcScratch, Rect dstScratch) {
        if (sprites == null || dir < 0 || dir >= sprites.length) return;
        SpriteSheet sprite = sprites[dir];
        // Recycled / unloaded / HW-only-on-software sheets must not be blitted:
        // that produces garbage-stripe ("spaghetti") frames during teardown races.
        if (sprite == null || !sprite.hasDrawable()) {
            return;
        }
        Bitmap draw = sprite.bitmapFor(c);
        if (draw == null || draw.isRecycled()) {
            return;
        }

        if (dragged) {
            // Logical position is feet. Wander callers pass true to hang the
            // sprite above an opaque finger; pinned Tableau drag passes false
            // so placement matches the committed feet.
            y -= 20f * scale;
        }

        sprite.getRect(time, srcScratch);
        fillDestRect(x, y, scale, dir, dstScratch);
        c.drawBitmap(draw, srcScratch, dstScratch, null);
    }
    
    public void setNextWaiting(PonyAction[] states) {
        nextWaiting = states != null ? states : new PonyAction[0];
    }
    
    public void setNextMoving(PonyAction[] states) {
        nextMoving = states != null ? states : new PonyAction[0];
    }
    
    public void setNextDrag(PonyAction[] states) {
        nextDrag = states != null ? states : new PonyAction[0];
    }
    
    /** @return true if this action has at least one real next waiting action */
    public boolean hasNextWaiting() {
        return nextWaitingCount() > 0;
    }
    
    /** @return true if this action has at least one real next moving action */
    public boolean hasNextMoving() {
        return nextMovingCount() > 0;
    }
    
    /** @return number of next-waiting slots (repeats count) */
    public int nextWaitingCount() {
        return nextWaiting != null ? nextWaiting.length : 0;
    }
    
    /** @return number of next-moving slots (repeats count) */
    public int nextMovingCount() {
        return nextMoving != null ? nextMoving.length : 0;
    }
    
    /** @return true if this action has at least one real next drag action */
    public boolean hasNextDrag() {
        return nextDrag != null && nextDrag.length > 0;
    }
    
    public PonyAction getNextWaiting(Random random) {
        return nextWaiting[random.nextInt(nextWaiting.length)];
    }
    
    public PonyAction getNextMoving(Random random) {
        return nextMoving[random.nextInt(nextMoving.length)];
    }

    /**
     * Herd-drain / force-leave pick: uniformly among the fastest
     * {@link #NORMAL} / {@link #PORT_O} / {@link #SCREEN_OUT} next-moving
     * slots so stroll/walk gaits do not win the exit.
     *
     * @return a leave mover, or {@code null} if none qualify
     */
    PonyAction pickFastestLeaveMoving(Random random) {
        return pickFastestLeave(nextMoving, random, null, null);
    }

    /**
     * Fastest leave mover whose facing axis matches {@code matchMovement}
     * (back/front vs left/right). {@link #PORT_O} / {@link #SCREEN_OUT} stay
     * eligible on either axis (they leave in place). Used when upgrading an
     * in-flight walk and when drag-to-edge prefers the thrown edge's axis.
     */
    PonyAction pickFastestLeaveMoving(Random random, String wander,
            String matchMovement) {
        return pickFastestLeave(nextMoving, random, wander, matchMovement);
    }

    /**
     * @see #pickFastestLeaveMoving(Random)
     */
    static PonyAction pickFastestLeave(PonyAction[] actions, Random random) {
        return pickFastestLeave(actions, random, null, null);
    }

    /**
     * @param matchMovement when non-null, skip traveling candidates whose
     *                      {@link WanderTarget#sameFacingAxis} does not match.
     *                      {@link #isAxisAgnosticLeave} clips stay eligible.
     */
    static PonyAction pickFastestLeave(PonyAction[] actions, Random random,
            String wander, String matchMovement) {
        if (actions == null || actions.length == 0) {
            return null;
        }
        boolean filterFacing = matchMovement != null;
        float[] speeds = new float[actions.length];
        for (int i = 0; i < actions.length; i++) {
            PonyAction a = actions[i];
            if (!isDrainLeaveMover(a)) {
                speeds[i] = 0f;
                continue;
            }
            if (filterFacing && !isAxisAgnosticLeave(a)
                    && !WanderTarget.sameFacingAxis(wander,
                    matchMovement, a.getMovement())) {
                speeds[i] = 0f;
                continue;
            }
            speeds[i] = a.speed;
        }
        int idx = HerdDrain.pickFastestIndex(speeds, random);
        return idx >= 0 ? actions[idx] : null;
    }

    static boolean isDrainLeaveMover(PonyAction a) {
        if (a == null) {
            return false;
        }
        return a.type == NORMAL || a.type == PORT_O || a.type == SCREEN_OUT;
    }

    /**
     * Vanish / teleport-out: no interpolated travel, so any drag edge is a
     * legal leave (do not require a facing-axis match).
     */
    static boolean isAxisAgnosticLeave(PonyAction a) {
        return a != null && (a.type == PORT_O || a.type == SCREEN_OUT);
    }
    
    public PonyAction getNextDrag(Random random) {
        return nextDrag[random.nextInt(nextDrag.length)];
    }
    
}
