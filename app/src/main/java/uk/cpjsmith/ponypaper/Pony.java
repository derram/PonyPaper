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
     * this times the current action's {@link PonyAction#speed}.
     */
    private static final float MOVE_SPEED_PER_SECOND = 75f;
    
    /** Idle wait range in milliseconds (was 25–274 frames at 25 FPS). */
    private static final int WAIT_MIN_MS = 1000;
    private static final int WAIT_EXTRA_MS = 10000;
    
    private final PonyAction[] allActions;
    private final PonyAction[] startActions;
    /**
     * Preference key that enabled this pony (e.g. {@code pref_ts},
     * {@code pref_custom_foo.xml}). Used for waifu / priority selection.
     */
    private String prefKey = "";
    
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
     * @return the SharedPreferences key for this pony definition, or empty if unset
     */
    public String getPrefKey() {
        return prefKey != null ? prefKey : "";
    }
    
    /**
     * Tags this instance with the preference key that selected it into the pool.
     * 
     * @param prefKey e.g. {@code pref_ts} or {@code pref_custom_name.xml}
     * @return this pony (for chaining at construction sites)
     */
    public Pony withPrefKey(String prefKey) {
        this.prefKey = prefKey != null ? prefKey : "";
        return this;
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
        } else if (deltaMs > 0) {
            // Animation rate comes from the current action (travel and idle).
            // Drag / teleport keep full-rate playback so one-shot sheets finish
            // on their authored timings.
            float animRate = 1f;
            if (motion == MOTION_MOVING && currentAction.type == PonyAction.NORMAL) {
                animRate = currentAction.speed;
            } else if (motion == MOTION_WAITING) {
                animRate = currentAction.speed;
            }
            frameTime += deltaMs * CS_PER_MS * animRate;
            int animTime = currentAction.getAnimationTime(direction);
            while (animTime > 0 && frameTime >= animTime) {
                frameTime -= animTime;
                switch (currentAction.type) {
                    case PonyAction.PORT_O:
                        moveTo(targetPos);
                        // Teleport-out always continues via next moving (atomic).
                        if (setMoving()) {
                            motion = currentAction.type == PonyAction.NORMAL
                                    ? MOTION_MOVING : MOTION_SPECIAL;
                        }
                        animTime = currentAction.getAnimationTime(direction);
                        break;
                        
                    case PonyAction.PORT_I:
                        // Teleport-in lands into waiting when a real waiter exists.
                        if (!tryLandAndWait()) {
                            arriveTarget();
                        }
                        animTime = currentAction.getAnimationTime(direction);
                        break;
                        
                    default:
                        if (!currentAction.loops) {
                            // One-shot: advance via next lists for this motion.
                            // Lists that are only none/- expand empty → fall
                            // through to the other axis (see advanceOneshot).
                            advanceOneshot();
                            animTime = currentAction.getAnimationTime(direction);
                        }
                        // Looping walk/stand cycles: wrap only.
                        break;
                }
            }
            
            switch (motion) {
                case MOTION_WAITING:
                    waitTimerMs -= deltaMs;
                    if (waitTimerMs <= 0) {
                        waitTimerMs = 0;
                        // Only leave idle if a real next moving action exists.
                        // Otherwise re-roll wait (none/- means "does not start travel").
                        if (!tryBeginMoving(true)) {
                            waitTimerMs = WAIT_MIN_MS + random.nextInt(WAIT_EXTRA_MS);
                            setWaiting();
                        }
                    }
                    break;
                    
                case MOTION_MOVING:
                    float step = MOVE_SPEED_PER_SECOND * currentAction.speed * scale * (deltaMs / 1000f);
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
     * Returns the y-coordinate of the pony's feet (bottom-center anchor). This
     * is used to sort ponies that are higher up the screen as being further away.
     * 
     * @return the screen y-coordinate of ground contact
     */
    public int getY() {
        return Math.round(posY);
    }
    
    /**
     * Tests whether a click at the given screen point should be considered to
     * be a click on the pony. Matches {@link PonyAction#drawOn}'s bottom-center
     * sprite bounds (logical position is feet), with a small pad for touch.
     * 
     * @param x the x-coordinate of the click
     * @param y the y-coordinate of the click
     * @return {@code true} iff the point is on top of this pony
     */
    public boolean testHitPoint(float x, float y) {
        if (currentAction == null) {
            return false;
        }
        float scale = getScale();
        int[] size = currentAction.getFrameSize(direction);
        float dW = size[0] * scale;
        float dH = size[1] * scale;
        // Same pad idea as the old radius (~30 unscaled px), applied outward.
        float pad = 8 * scale;
        float left = posX - dW / 2 - pad;
        float right = posX + dW / 2 + pad;
        float top = posY - dH - pad;
        float bottom = posY + pad;
        return x >= left && x < right && y >= top && y < bottom;
    }
    
    /**
     * Brings the pony into a dragged state. This means the pony will no longer
     * move on its own accord and will only move as directed with
     * {@link #moveTo(Point)} until {@link #stopDrag()} is called.
     */
    public void startDrag() {
        // Only enter drag motion when a real next drag action exists.
        if (setDragged()) {
            motion = MOTION_DRAGGED;
            targetPos = null;
            leavingMode = LM_NORMAL;
        }
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
            if (tryBeginMoving(false)) {
                leavingMode = LM_GOING;
                targetPos = new Point(screenBounds.left - s, y);
            } else {
                beginWaitingInPlace();
            }
        } else if (x >= screenBounds.right - s) {
            if (tryBeginMoving(false)) {
                leavingMode = LM_GOING;
                targetPos = new Point(screenBounds.right + s, y);
            } else {
                beginWaitingInPlace();
            }
        } else {
            beginWaitingInPlace();
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
    
    /**
     * Picks a next waiting action if the list has real successors.
     *
     * @return true if {@link #currentAction} was changed (or re-selected)
     */
    private boolean setWaiting() {
        if (!currentAction.hasNextWaiting()) {
            return false;
        }
        changeAction(currentAction.getNextWaiting(random));
        return true;
    }
    
    /**
     * Picks a next moving action if the list has real successors.
     *
     * @return true if {@link #currentAction} was changed (or re-selected)
     */
    private boolean setMoving() {
        if (!currentAction.hasNextMoving()) {
            return false;
        }
        changeAction(currentAction.getNextMoving(random));
        return true;
    }
    
    /**
     * Picks a next drag action if the list has real successors.
     *
     * @return true if {@link #currentAction} was changed (or re-selected)
     */
    private boolean setDragged() {
        if (!currentAction.hasNextDrag()) {
            return false;
        }
        changeAction(currentAction.getNextDrag(random));
        return true;
    }
    
    /**
     * Starts travel only when a real next moving action exists. Motion mode is
     * set together with the action pick so a {@code none} moving list can never
     * scoot the pony while still playing a waiting sheet.
     *
     * @param alwaysNewTarget if true, always assign a new destination; if false,
     *                        only assign one when {@link #targetPos} is null
     * @return true if travel began
     */
    private boolean tryBeginMoving(boolean alwaysNewTarget) {
        if (!setMoving()) {
            return false;
        }
        motion = currentAction.type == PonyAction.NORMAL ? MOTION_MOVING : MOTION_SPECIAL;
        if (alwaysNewTarget || targetPos == null) {
            setRandomTarget();
        }
        return true;
    }
    
    /**
     * Enters idle waiting with a fresh timer, picking a next waiting action when
     * available. Used when travel is not possible (e.g. stop-drag with no mover).
     */
    private void beginWaitingInPlace() {
        motion = MOTION_WAITING;
        targetPos = null;
        waitTimerMs = WAIT_MIN_MS + random.nextInt(WAIT_EXTRA_MS);
        setWaiting();
    }
    
    /**
     * After a non-looping action finishes one play: pick the next action for
     * the current motion context. If that list has no real successors
     * ({@code none}/{@code -} only), fall through to the other travel axis
     * (waiting ↔ moving). Drag with an empty list keeps the current action.
     * Fall-through also keeps motion and action picks atomic.
     */
    private void advanceOneshot() {
        switch (motion) {
            case MOTION_WAITING:
                if (!setWaiting()) {
                    // No idle hold after this transition — start traveling only
                    // if a real mover exists (tryBeginMoving is atomic).
                    tryBeginMoving(false);
                }
                break;
                
            case MOTION_MOVING:
                if (!setMoving()) {
                    // No further travel clip — land only if a real waiter exists.
                    tryLandAndWait();
                }
                break;
                
            case MOTION_DRAGGED:
                setDragged();
                break;
                
            default:
                break;
        }
    }
    
    /**
     * Completes an arrival into idle when a real next waiting action exists.
     * Used by oneshot fall-through and by {@link #moveTowardsTarget}.
     *
     * @return true if waiting began
     */
    private boolean tryLandAndWait() {
        if (!currentAction.hasNextWaiting()) {
            return false;
        }
        // Snapshot next before arriveTarget; setWaiting reads currentAction.
        arriveTarget();
        setWaiting();
        return true;
    }
    
    private void changeAction(PonyAction newAction) {
        if (newAction != currentAction) {
            currentAction = newAction;
            frameTime = 0;
        }
    }
    
    /**
     * Snaps into idle-at-destination bookkeeping (timer, clear target, exit flag).
     * Does not pick a next action — callers must call {@link #setWaiting()} when
     * a real waiting successor exists.
     */
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
            // Leaving the scene always completes exit bookkeeping.
            if (leavingMode == LM_GOING) {
                arriveTarget();
                setWaiting();
                return;
            }
            // Normal arrive: idle only with a real waiter; else keep traveling.
            if (tryLandAndWait()) {
                return;
            }
            if (tryBeginMoving(true)) {
                return;
            }
            // No successors either way — stop in place on the current sheet.
            arriveTarget();
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
     * Largest unscaled frame height among this pony's loaded actions. Used so
     * on-screen wander targets keep the full sprite below the top edge under
     * bottom-center (feet) anchoring.
     */
    private int maxUnscaledFrameHeight() {
        int maxH = 0;
        for (int i = 0; i < allActions.length; i++) {
            for (int dir = PonyAction.LEFT; dir <= PonyAction.RIGHT; dir++) {
                int h = allActions[i].getFrameSize(dir)[1];
                if (h > maxH) {
                    maxH = h;
                }
            }
        }
        // ~median sheet height if nothing is loaded yet (should not happen after init).
        return maxH > 0 ? maxH : 50;
    }
    
    /**
     * Chooses a random point on the screen. Logical position is feet
     * (bottom-center), so the top inset is a full sprite height plus a small
     * pad rather than the historical symmetric center-anchor margin.
     * Off-screen enter/exit targets intentionally keep the older taller band
     * so approach angles stay varied.
     * 
     * @return the chosen point
     */
    private Point randomOnScreen() {
        float scale = getScale();
        int side = (int)(30 * scale);
        // Goal A: whole sprite stays on-screen at the top; feet may sit near bottom.
        int top = (int)(maxUnscaledFrameHeight() * scale) + (int)(8 * scale);
        int bottom = (int)(8 * scale);
        int usableW = screenBounds.width() - 2 * side;
        int usableH = screenBounds.height() - top - bottom;
        // Transient zero-size surfaces would make nextInt throw IllegalArgumentException.
        if (usableW < 1 || usableH < 1) {
            return new Point(screenBounds.centerX(), screenBounds.centerY());
        }
        return new Point(screenBounds.left + side + random.nextInt(usableW),
                         screenBounds.top + top + random.nextInt(usableH));
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
