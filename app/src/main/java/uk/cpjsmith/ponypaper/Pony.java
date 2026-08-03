package uk.cpjsmith.ponypaper;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import java.util.Random;

/**
 * Represents the current status of a pony. This class must be subclassed to
 * provide methods to create the initial state of the pony and to determine the
 * transition between states.
 */
public class Pony {
    
    private static final int MOTION_INIT = 0;
    private static final int MOTION_WAITING = 1;
    private static final int MOTION_MOVING = 2;
    private static final int MOTION_DRAGGED = 3;
    private static final int MOTION_SPECIAL = 4;
    
    private static final int LM_NORMAL = 0;
    private static final int LM_GOING = 1;
    private static final int LM_GONE = 2;
    
    /**
     * Sprite timings are stored in centiseconds (1/100 s). Animation time is
     * advanced from real elapsed milliseconds so it is independent of the
     * wallpaper redraw rate.
     */
    private static final float CS_PER_MS = 0.1f;
    
    /**
     * Movement speed ceiling in unscaled pixels per second. Matches the
     * historical behaviour of 3 pixels per frame at 25 FPS. Actual travel uses
     * this times {@link #moveSpeedFactor} (a discrete gait ≤ 1).
     */
    private static final float MOVE_SPEED_PER_SECOND = 75f;
    
    /**
     * Discrete gaits as fractions of {@link #MOVE_SPEED_PER_SECOND}. Chosen once
     * per travel leg so the AI does not always move at the ceiling.
     */
    private static final float GAIT_STROLL = 0.4f;
    private static final float GAIT_WALK = 0.7f;
    private static final float GAIT_TROT = 1.0f;
    
    /** Idle wait range in milliseconds (was 25–274 frames at 25 FPS). */
    private static final int WAIT_MIN_MS = 1000;
    private static final int WAIT_EXTRA_MS = 10000;
    
    private final PonyAction[] allActions;
    private final PonyAction[] startActions;
    
    private Random random;
    private Point targetPos;
    /** Remaining idle time in milliseconds. */
    private float waitTimerMs;
    
    private int motion;
    private int leavingMode;
    
    private PonyAction currentAction;
    private float posX;
    private float posY;
    private int direction;
    /** Animation clock in centiseconds (same unit as sprite frame timings). */
    private float frameTime = 0;
    /**
     * Current gait factor in {@code (0, 1]}, applied to both travel speed and
     * the walk/trot animation rate while {@link #MOTION_MOVING}.
     */
    private float moveSpeedFactor = GAIT_TROT;
    /**
     * Animation rate for idle/stand cycles while {@link #MOTION_WAITING}.
     * Chosen 50/50 between full speed and {@link #GAIT_WALK} each wait.
     */
    private float idleAnimRate = GAIT_TROT;
    
    private Rect screenBounds;
    
    /**
     * Creates a new {@code Pony} object.
     * 
     * @param allActions   all of the actions that this pony is comprised of
     * @param startActions the actions that the pony can enter the screen with
     */
    public Pony(PonyAction[] allActions, PonyAction[] startActions) {
        this.allActions = allActions;
        this.startActions = startActions;
        this.random = new Random();
        this.direction = random.nextBoolean() ? PonyAction.LEFT : PonyAction.RIGHT;
    }
    
    /**
     * Clears the current state of the pony.
     */
    public void reset() {
        waitTimerMs = 0;
        motion = MOTION_INIT;
        leavingMode = LM_NORMAL;
        currentAction = null;
        posX = 0;
        posY = 0;
        frameTime = 0;
        moveSpeedFactor = GAIT_TROT;
        idleAnimRate = GAIT_TROT;
        for (int i = 0; i < allActions.length; i++) {
            allActions[i].unload();
        }
    }
    
    /**
     * Causes the state of the pony to be updated for the elapsed time.
     * 
     * @param clipBounds the bounds of the screen that the pony will be
     *                   positioned on
     * @param deltaMs    milliseconds since the previous update (may be 0 for a
     *                   pure spawn/layout pass)
     */
    public void doUpdate(Rect clipBounds, long deltaMs) {
        screenBounds = clipBounds;
        
        float scale = getScale();
        
        if (motion == MOTION_INIT) {
            for (int i = 0; i < allActions.length; i++) {
                allActions[i].load();
            }
            Point start = randomOffScreen();
            posX = start.x;
            posY = start.y;
            changeAction(startActions[random.nextInt(startActions.length)]);
            motion = currentAction.type == PonyAction.NORMAL ? MOTION_MOVING : MOTION_SPECIAL;
            setRandomTarget();
            if (motion == MOTION_MOVING) chooseGait();
        } else if (deltaMs > 0) {
            // Match gait animation rate to travel speed for normal walks; idle
            // uses a per-wait rate (full or walk-speed 50/50); drag/teleport stay full.
            float animRate = 1f;
            if (motion == MOTION_MOVING && currentAction.type == PonyAction.NORMAL) {
                animRate = moveSpeedFactor;
            } else if (motion == MOTION_WAITING) {
                animRate = idleAnimRate;
            }
            frameTime += deltaMs * CS_PER_MS * animRate;
            int animTime = currentAction.getAnimationTime(direction);
            while (animTime > 0 && frameTime >= animTime) {
                frameTime -= animTime;
                switch (currentAction.type) {
                    case PonyAction.PORT_O:
                        moveTo(targetPos);
                        setMoving();
                        animTime = currentAction.getAnimationTime(direction);
                        break;
                        
                    case PonyAction.PORT_I:
                        arriveTarget();
                        setWaiting();
                        animTime = currentAction.getAnimationTime(direction);
                        break;
                        
                    default:
                        // Looping walk/stand cycles: wrap only.
                        break;
                }
            }
            
            switch (motion) {
                case MOTION_WAITING:
                    waitTimerMs -= deltaMs;
                    if (waitTimerMs <= 0) {
                        waitTimerMs = 0;
                        setMoving();
                        motion = currentAction.type == PonyAction.NORMAL ? MOTION_MOVING : MOTION_SPECIAL;
                        setRandomTarget();
                        if (motion == MOTION_MOVING) chooseGait();
                    }
                    break;
                    
                case MOTION_MOVING:
                    float step = MOVE_SPEED_PER_SECOND * moveSpeedFactor * scale * (deltaMs / 1000f);
                    moveTowardsTarget(step);
                    break;
            }
        }
    }
    
    public void drawOn(Canvas c) {
        int animTime = currentAction.getAnimationTime(direction);
        int time = Math.round(frameTime);
        // SpriteSheet.getRect requires 0 <= time < totalTime.
        if (animTime > 0 && time >= animTime) time = animTime - 1;
        if (time < 0) time = 0;
        currentAction.drawOn(c, direction, time, currentPoint(), getScale(), motion == MOTION_DRAGGED);
    }
    
    /**
     * Determines whether this pony has left the scene to be replaced with
     * another.
     * 
     * @return {@code true} if the pony has completed an exit stage direction
     */
    public boolean goneOffScreen() {
        return leavingMode == LM_GONE;
    }
    
    /**
     * Returns the y-coordinate of the pony's position. This can be used to
     * sort ponies that are higher up the screen as being further away.
     * 
     * @return the screen y-coordinate
     */
    public int getY() {
        return Math.round(posY);
    }
    
    /**
     * Tests whether a click at the given screen point should be considered to
     * be a click on the pony.
     * 
     * @param x the x-coordinate of the click
     * @param y the y-coordinate of the click
     * @return {@code true} iff the point is on top of this pony
     */
    public boolean testHitPoint(float x, float y) {
        float ponySize = 30 * getScale();
        
        float dX = x - posX;
        float dY = y - posY;
        float d2 = dX * dX + dY * dY;
        
        return d2 < ponySize * ponySize;
    }
    
    /**
     * Brings the pony into a dragged state. This means the pony will no longer
     * move on its own accord and will only move as directed with
     * {@link #moveTo(Point)} until {@link #stopDrag()} is called.
     */
    public void startDrag() {
        motion = MOTION_DRAGGED;
        targetPos = null;
        leavingMode = LM_NORMAL;
        setDragged();
    }
    
    /**
     * Brings the pony back out of the dragged state. If the pony has been
     * dragged to the edge of the screen, it will immediately walk (fly, etc.)
     * off screen. Otherwise it will resume normal behaviour.
     */
    public void stopDrag() {
        int s = (int)(30 * getScale());
        int x = Math.round(posX);
        int y = Math.round(posY);
        
        if (x < screenBounds.left + s) {
            motion = MOTION_MOVING;
            leavingMode = LM_GOING;
            targetPos = new Point(screenBounds.left - s, y);
            setMoving();
            // Purposeful exit after drag: always full-speed trot.
            moveSpeedFactor = GAIT_TROT;
        } else if (x >= screenBounds.right - s) {
            motion = MOTION_MOVING;
            leavingMode = LM_GOING;
            targetPos = new Point(screenBounds.right + s, y);
            setMoving();
            moveSpeedFactor = GAIT_TROT;
        } else {
            motion = MOTION_WAITING;
            waitTimerMs = WAIT_MIN_MS + random.nextInt(WAIT_EXTRA_MS);
            setWaiting();
        }
    }
    
    /**
     * Moves the pony to a position.
     * 
     * @param pos the new position for the pony
     */
    public void moveTo(Point pos) {
        setDirection(pos);
        posX = pos.x;
        posY = pos.y;
    }
    
    private Point currentPoint() {
        return new Point(Math.round(posX), Math.round(posY));
    }
    
    private void setWaiting() {
        changeAction(currentAction.getNextWaiting(random));
        // 50/50 full-speed idle vs same rate as a normal walk gait.
        idleAnimRate = random.nextBoolean() ? GAIT_TROT : GAIT_WALK;
    }
    
    private void setMoving() {
        changeAction(currentAction.getNextMoving(random));
    }
    
    private void setDragged() {
        changeAction(currentAction.getNextDrag(random));
    }
    
    /**
     * Picks a discrete gait for the next travel leg. Equal chance of stroll,
     * walk, or trot; never above {@link #GAIT_TROT} (the historical full speed).
     */
    private void chooseGait() {
        switch (random.nextInt(3)) {
            case 0:
                moveSpeedFactor = GAIT_STROLL;
                break;
            case 1:
                moveSpeedFactor = GAIT_WALK;
                break;
            default:
                moveSpeedFactor = GAIT_TROT;
                break;
        }
    }
    
    private void changeAction(PonyAction newAction) {
        if (newAction != currentAction) {
            currentAction = newAction;
            frameTime = 0;
        }
    }
    
    private void arriveTarget() {
        motion = MOTION_WAITING;
        targetPos = null;
        waitTimerMs = WAIT_MIN_MS + random.nextInt(WAIT_EXTRA_MS);
        if (leavingMode == LM_GOING) leavingMode = LM_GONE;
    }
    
    private void setRandomTarget() {
        if (random.nextInt(8) < 1) {
            if (motion == MOTION_MOVING) {
                targetPos = randomOffScreenHoriz();
            } else {
                targetPos = randomOffScreen();
            }
            leavingMode = LM_GOING;
        } else {
            if (motion == MOTION_MOVING) {
                targetPos = randomOnScreenHoriz();
            } else {
                targetPos = randomOnScreen();
            }
        }
    }
    
    /**
     * Moves the pony towards its target by a given number of pixels.
     * 
     * @param speed the distance to move this frame (pixels)
     */
    private void moveTowardsTarget(float speed) {
        setDirection(targetPos);
        float dX = targetPos.x - posX;
        float dY = targetPos.y - posY;
        float dist = (float)Math.sqrt(dX * dX + dY * dY);
        if (dist == 0 || speed >= dist) {
            posX = targetPos.x;
            posY = targetPos.y;
            arriveTarget();
            setWaiting();
        } else {
            float f = speed / dist;
            posX += dX * f;
            posY += dY * f;
        }
    }
    
    private float getScale() {
        return Math.min(screenBounds.width(), screenBounds.height()) / 200.0f;
    }
    
    /**
     * Chooses a random point on the screen.
     * 
     * @return the chosen point
     */
    private Point randomOnScreen() {
        int s = (int)(30 * getScale());
        int usableW = screenBounds.width() - 2 * s;
        int usableH = screenBounds.height() - 2 * s;
        // Transient zero-size surfaces would make nextInt throw IllegalArgumentException.
        if (usableW < 1 || usableH < 1) {
            return new Point(screenBounds.centerX(), screenBounds.centerY());
        }
        return new Point(screenBounds.left + s + random.nextInt(usableW),
                         screenBounds.top + s + random.nextInt(usableH));
    }
    
    /**
     * Chooses a random point on the screen, restricted to areas roughly
     * horizontal with the current position.
     * 
     * @return the chosen point
     */
    private Point randomOnScreenHoriz() {
        Point newPoint = null;
        int curY = Math.round(posY);
        int curX = Math.round(posX);
        for (int i = 0; i < 100; i++) {
            newPoint = randomOnScreen();
            if (Math.abs(newPoint.y - curY) < Math.abs(newPoint.x - curX)) {
                break;
            }
        }
        return newPoint;
    }
    
    /**
     * Chooses a random point just to the side of the screen.
     * 
     * @return the chosen point
     */
    private Point randomOffScreen() {
        int s = (int)(30 * getScale());
        int usableH = screenBounds.height() - 2 * s;
        int y = usableH < 1
                ? screenBounds.centerY()
                : screenBounds.top + s + random.nextInt(usableH);
        return new Point(random.nextBoolean() ? screenBounds.left - s : screenBounds.right + s, y);
    }
    
    /**
     * Chooses a random point just to the side of the screen, restricted to
     * areas roughly horizontal with the current position.
     * 
     * @return the chosen point
     */
    private Point randomOffScreenHoriz() {
        Point newPoint = null;
        int curY = Math.round(posY);
        int curX = Math.round(posX);
        for (int i = 0; i < 100; i++) {
            newPoint = randomOffScreen();
            if (Math.abs(newPoint.y - curY) < Math.abs(newPoint.x - curX)) {
                break;
            }
        }
        return newPoint;
    }
    
    private void setDirection(Point targetPos) {
        float dX = targetPos.x - posX;
        if (dX > 0 && direction != PonyAction.RIGHT) {
            direction = PonyAction.RIGHT;
            frameTime = 0;
        }
        if (dX < 0 && direction != PonyAction.LEFT) {
            direction = PonyAction.LEFT;
            frameTime = 0;
        }
    }
    
}
