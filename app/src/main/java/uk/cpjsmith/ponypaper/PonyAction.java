package uk.cpjsmith.ponypaper;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.Base64;
import java.util.Random;

/**
 * Encapsulates a particular action for a pony. Ultimately, this unites the
 * {@code SpriteSheet} for the left- and right-facing modes of the action, as
 * well as information on possible next states. On construction, the sprites
 * are not immediately loaded and the {@link #getAnimationTime} and {@link
 * #drawOn} methods will fail with {@code NullPointerException} until the
 * {@link #load} method is called.
 *
 * <p>Each action carries a {@link #speed} factor used both as travel speed
 * (when moving) and as animation playback rate (moving or idle). Multiple
 * actions may share one sprite sheet via {@link #PonyAction(PonyAction, float)}
 * so gait/idle variants do not load duplicate bitmaps.
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
    
    /** Represents motion towards the left (negative x) direction. */
    public static final int LEFT = 0;
    /** Represents motion towards the right (positive x) direction. */
    public static final int RIGHT = 1;
    
    /** Default speed factor (full historical travel / animation rate). */
    public static final float DEFAULT_SPEED = 1.0f;
    
    /** The type of action; {@code NORMAL}, {@code PORT_O} or {@code PORT_I} */
    public final int type;
    
    /**
     * Speed factor for this action. While {@code NORMAL} moving, multiplies
     * both travel distance per second and animation rate. While waiting (or
     * any non-travel use), multiplies animation rate only. Typical values
     * are in {@code (0, 1]}; values above 1 are allowed for fast characters.
     */
    public final float speed;
    
    /* To create the sprite sheets for a built-in pony. */
    private Resources res;
    private int arrayId;
    /* To create the sprite sheets for a custom pony. */
    private PonyDefinition.Action definition;
    /**
     * When non-null, this action reuses {@code spriteSource}'s loaded sheets
     * (gait/idle aliases). Load/unload ownership stays with the source.
     */
    private PonyAction spriteSource;
    
    private SpriteSheet[] sprites;
    
    private PonyAction[] nextWaiting;
    private PonyAction[] nextMoving;
    private PonyAction[] nextDrag;
    
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
        this.arrayId = arrayId;
        this.type = type;
        this.speed = sanitizeSpeed(speed);
    }
    
    /**
     * Constructs an action that shares another action's sprite sheets but uses
     * a different speed factor (stroll/walk/trot or slow/fast idle variants).
     * 
     * @param spriteSource action that owns the bitmaps (must not itself be an alias)
     * @param speed        travel / animation speed factor for this variant
     */
    public PonyAction(PonyAction spriteSource, float speed) {
        if (spriteSource == null) {
            throw new IllegalArgumentException("spriteSource");
        }
        // Resolve to the true owner so alias chains stay flat.
        this.spriteSource = spriteSource.spriteSource != null
                ? spriteSource.spriteSource : spriteSource;
        this.type = spriteSource.type;
        this.speed = sanitizeSpeed(speed);
        this.res = this.spriteSource.res;
        this.arrayId = this.spriteSource.arrayId;
        this.definition = this.spriteSource.definition;
    }
    
    /**
     * Constructs an action from a custom XML definition.
     * 
     * @param definition the action definition extracted from XML
     */
    public PonyAction(PonyDefinition.Action definition) {
        this.definition = definition;
        if (definition.specialType.equals("teleport-out")) {
            this.type = PORT_O;
        } else if (definition.specialType.equals("teleport-in")) {
            this.type = PORT_I;
        } else {
            this.type = NORMAL;
        }
        this.speed = sanitizeSpeed(definition.speed);
        // Test the images.
        load();
        unload();
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
     * Load the sprites into memory. After this is called, all the methods of
     * this class become functional. It will also consume far more memory.
     * 
     * <p>Alias actions only pin the source's sheets; the source owns recycle.
     * 
     * @see #unload()
     */
    public void load() {
        if (spriteSource != null) {
            spriteSource.load();
            sprites = spriteSource.sprites;
            return;
        }
        // Already loaded (idempotent so aliases can load the owner first).
        if (sprites != null) {
            return;
        }
        if (res != null) {
            TypedArray array = res.obtainTypedArray(arrayId);
            
            int leftDrawableId = array.getResourceId(0, 0);
            int leftTimingId = array.getResourceId(1, 0);
            int rightDrawableId = array.getResourceId(2, 0);
            int rightTimingId = array.getResourceId(3, 0);
            
            sprites = new SpriteSheet[] {
                new SpriteSheet(res, leftDrawableId, leftTimingId),
                new SpriteSheet(res, rightDrawableId, rightTimingId)
            };
            
            array.recycle();
        } else if (definition != null) {
            sprites = new SpriteSheet[] {
                new SpriteSheet(Base64.decode(definition.images.get("left"), 0),
                                parseInts(definition.timings.get("left"))),
                new SpriteSheet(Base64.decode(definition.images.get("right"), 0),
                                parseInts(definition.timings.get("right")))
            };
        }
    }
    
    /**
     * Unload the sprites from memory. Recycles each sheet's {@link android.graphics.Bitmap}
     * so native pixel buffers are freed promptly when ponies cycle off-screen
     * (not only after GC). Some methods of this class will cease to function
     * until {@link #load()} is called again.
     * 
     * <p>Alias actions only drop their reference; the owning action recycles.
     * 
     * @see #load()
     */
    public void unload() {
        if (spriteSource != null) {
            sprites = null;
            return;
        }
        if (sprites != null) {
            for (int i = 0; i < sprites.length; i++) {
                if (sprites[i] != null) {
                    sprites[i].recycle();
                }
            }
            sprites = null;
        }
    }
    
    public int getAnimationTime(int dir) {
        return sprites[dir].totalTime;
    }
    
    public void drawOn(Canvas c, int dir, int time, Point p, float scale, boolean dragged) {
        SpriteSheet sprite = sprites[dir];
        
        int sW = sprite.frameWidth;
        int sH = sprite.frameHeight;
        float dW = sW * scale;
        float dH = sH * scale;
        
        if (dragged) {
            p = new Point(p);
            // Hang above the finger so the sprite stays visible under the touch.
            p.y -= dH/2 + 20 * scale;
        }
        
        RectF dstRect = new RectF(p.x - dW/2, p.y - dH/2, p.x + dW/2, p.y + dH/2);
        
        c.drawBitmap(sprite.bitmap, sprite.getRect(time), dstRect, null);
    }
    
    public void setNextWaiting(PonyAction[] states) {
        nextWaiting = states;
    }
    
    public void setNextMoving(PonyAction[] states) {
        nextMoving = states;
    }
    
    public void setNextDrag(PonyAction[] states) {
        nextDrag = states;
    }
    
    public PonyAction getNextWaiting(Random random) {
        return nextWaiting[random.nextInt(nextWaiting.length)];
    }
    
    public PonyAction getNextMoving(Random random) {
        return nextMoving[random.nextInt(nextMoving.length)];
    }
    
    public PonyAction getNextDrag(Random random) {
        return nextDrag[random.nextInt(nextDrag.length)];
    }
    
}
