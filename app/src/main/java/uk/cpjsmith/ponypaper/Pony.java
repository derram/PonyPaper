package uk.cpjsmith.ponypaper;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
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
    private static final int WAIT_MIN_MS = 2000;
    private static final int WAIT_EXTRA_MS = 10000;
    
    private static final PonyEffectDef[] NO_EFFECTS = new PonyEffectDef[0];

    private final PonyAction[] allActions;
    private final PonyAction[] startActions;
    private final PonyEffectDef[] effectDefs;
    /**
     * Preference key that enabled this pony (e.g. {@code pref_ts},
     * {@code pref_custom_foo.xml}). Used for waifu / priority selection.
     */
    private String prefKey = "";

    /** Scene that owns live effect instances; null until {@link Ponies} wires it. */
    private EffectHost effectHost;
    
    private Random random;
    private Point targetPos;
    /** Remaining idle time in milliseconds. */
    private float waitTimerMs;
    
    private int motion;
    private int leavingMode;
    
    private PonyAction currentAction;
    private float posX;
    /**
     * Vertical world position of the pony's feet (ground contact). Sprite sheets
     * are drawn relative to this via each action's anchor (see
     * {@link PonyAction#getAnchorX} / {@link PonyAction#getAnchorY}).
     */
    private float posY;
    private int direction;
    /** Animation clock in centiseconds (same unit as sprite frame timings). */
    private float frameTime = 0;
    
    private Rect screenBounds;
    /**
     * User size multiplier from {@link PonySize} (1 = original
     * {@code min(w,h)/200} scale).
     */
    private float sizeFactor = 1f;

    /**
     * Receives action-change and leave notifications so the scene can spawn
     * and cull effect sprites.
     */
    interface EffectHost {
        void onPonyActionChanged(Pony pony, PonyAction previous, PonyAction next);
        void onPonyEffectsCleared(Pony pony);
    }
    
    /**
     * Creates a new {@code Pony} object.
     * 
     * @param allActions   all of the actions that this pony is comprised of
     * @param startActions the actions that the pony can enter the screen with
     */
    public Pony(PonyAction[] allActions, PonyAction[] startActions) {
        this(allActions, startActions, null);
    }

    /**
     * Creates a pony with optional effect definitions (custom characters).
     *
     * @param effectDefs may be {@code null} or empty when the character has none
     */
    public Pony(PonyAction[] allActions, PonyAction[] startActions, PonyEffectDef[] effectDefs) {
        this.allActions = allActions;
        this.startActions = startActions;
        this.effectDefs = effectDefs != null ? effectDefs : NO_EFFECTS;
        this.random = new Random();
        this.direction = random.nextBoolean() ? PonyAction.LEFT : PonyAction.RIGHT;
    }

    void setEffectHost(EffectHost host) {
        this.effectHost = host;
    }

    PonyEffectDef[] getEffectDefs() {
        return effectDefs;
    }

    boolean hasEffects() {
        return effectDefs.length > 0;
    }

    int getDirection() {
        return direction;
    }

    float getScale() {
        if (screenBounds == null) {
            return sizeFactor;
        }
        return Math.min(screenBounds.width(), screenBounds.height()) / 200.0f * sizeFactor;
    }

    Random effectRandom() {
        return random;
    }

    /**
     * Current sprite draw bounds (feet at {@link #posX}/{@link #posY}), including
     * the drag lift so follow effects track the visible sprite.
     */
    void fillCurrentDrawBounds(RectF out) {
        if (currentAction == null || !currentAction.isReady() || out == null) {
            if (out != null) {
                out.setEmpty();
            }
            return;
        }
        float y = posY;
        if (motion == MOTION_DRAGGED) {
            y -= 20f * getScale();
        }
        currentAction.fillDrawBounds(posX, y, getScale(), direction, out);
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
     * Sets the user character-size multiplier. {@code 1} is the original scale.
     * Values {@code <= 0} are ignored.
     */
    public void setSizeFactor(float factor) {
        if (factor > 0f) {
            sizeFactor = factor;
        }
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
        if (effectHost != null) {
            effectHost.onPonyEffectsCleared(this);
        }
        unloadActions();
    }

    /**
     * Unpin every action's sheets. Safe when already unloaded. Used by
     * {@link #reset()} and when a host drops the herd.
     */
    public void unloadActions() {
        for (int i = 0; i < allActions.length; i++) {
            allActions[i].unload();
        }
        for (int i = 0; i < effectDefs.length; i++) {
            effectDefs[i].unload();
        }
    }

    /**
     * Start pinning every action's sheets. Decode is asynchronous; spawn waits
     * on {@link #actionsReady()}.
     */
    public void loadActions() {
        for (int i = 0; i < allActions.length; i++) {
            allActions[i].load();
        }
        for (int i = 0; i < effectDefs.length; i++) {
            effectDefs[i].load();
        }
    }

    /**
     * @return true when every action has both facings decoded
     */
    public boolean actionsReady() {
        for (int i = 0; i < allActions.length; i++) {
            if (!allActions[i].isReady()) {
                return false;
            }
        }
        for (int i = 0; i < effectDefs.length; i++) {
            if (!effectDefs[i].isReady()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if any started pin failed to decode
     */
    public boolean actionsFailed() {
        for (int i = 0; i < allActions.length; i++) {
            if (allActions[i].loadFailed()) {
                return true;
            }
        }
        for (int i = 0; i < effectDefs.length; i++) {
            if (effectDefs[i].loadFailed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true while this pony is traveling off-screen to be replaced
     */
    public boolean isLeavingScene() {
        return leavingMode == LM_GOING;
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
        if (screenBounds == null) {
            screenBounds = new Rect();
        }
        screenBounds.set(clipBounds);
        
        float scale = getScale();
        
        if (motion == MOTION_INIT) {
            loadActions();
            if (actionsFailed()) {
                leavingMode = LM_GONE;
                return;
            }
            if (!actionsReady()) {
                return;
            }
            changeAction(startActions[random.nextInt(startActions.length)]);
            if (currentAction.type == PonyAction.SCREEN_IN
                    || currentAction.type == PonyAction.SCREEN_OUT) {
                // Appear/vanish clips spawn on-screen and do not interpolate.
                Point startOn = randomOnScreen();
                posX = startOn.x;
                posY = startOn.y;
                motion = MOTION_SPECIAL;
                if (currentAction.type == PonyAction.SCREEN_OUT) {
                    leavingMode = LM_GOING;
                }
            } else if (currentAction.type == PonyAction.PORT_O
                    || currentAction.type == PonyAction.PORT_I) {
                // Scene enter via teleport: only the visible half. Spawning
                // off-screen to play teleport-out lets VFX bleed at the gutter;
                // land on-screen and play teleport-in (or keep in if that was
                // the start pick). Mid-scene teleports still use the full pair.
                if (currentAction.type == PonyAction.PORT_O) {
                    setMoving();
                }
                Point startOn = randomOnScreen();
                posX = startOn.x;
                posY = startOn.y;
                motion = MOTION_SPECIAL;
                targetPos = null;
            } else {
                Point startOff = randomOffScreen();
                posX = startOff.x;
                posY = startOff.y;
                motion = currentAction.type == PonyAction.NORMAL
                        ? MOTION_MOVING : MOTION_SPECIAL;
                setRandomTarget();
            }
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
                        if (leavingMode == LM_GOING) {
                            // Scene leave: only the visible half — vanish in
                            // place. Skip off-screen teleport-in so VFX does
                            // not bleed at the gutter.
                            leavingMode = LM_GONE;
                            animTime = currentAction.getAnimationTime(direction);
                            break;
                        }
                        if (targetPos == null) {
                            // No destination (e.g. teleport-out start with no
                            // teleport-in successor). Idle here rather than NPE.
                            if (!tryLandAndWait()) {
                                arriveTarget();
                            }
                            animTime = currentAction.getAnimationTime(direction);
                            break;
                        }
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

                    case PonyAction.SCREEN_IN:
                        // Appear clip finished — idle at the current point.
                        if (!tryLandAndWait()) {
                            arriveTarget();
                        }
                        animTime = currentAction.getAnimationTime(direction);
                        break;

                    case PonyAction.SCREEN_OUT:
                        // Vanish clip finished — leave the herd slot.
                        leavingMode = LM_GONE;
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
                        // One-shots own the stage until the sheet finishes
                        // (advanceOneshot above). Defer idle→travel / re-roll so
                        // the timer does not cut a mid-play transition clip.
                        // Timer stays at 0; the next frame after a looping
                        // waiter is selected (or fall-through starts travel)
                        // runs the stay-or-go expiry path.
                        if (!currentAction.loops) {
                            break;
                        }
                        // Stay vs leave is weighted by next-waiting vs
                        // next-moving slot counts. Empty moving always stays
                        // (re-pick waiting). Empty waiting always leaves.
                        if (WaitExpiry.shouldStayIdle(
                                currentAction.nextWaitingCount(),
                                currentAction.nextMovingCount(),
                                random)) {
                            waitTimerMs = WAIT_MIN_MS + random.nextInt(WAIT_EXTRA_MS);
                            setWaiting();
                        } else if (!tryBeginMoving(true)) {
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
    
    public void drawOn(Canvas c, Rect srcScratch, Rect dstScratch) {
        if (currentAction == null || !currentAction.isReady()) {
            return;
        }
        if (currentAction.getAnimationTime(direction) <= 0) {
            return;
        }
        int time = clampedAnimTime();
        currentAction.drawOn(c, direction, time, posX, posY, getScale(),
                motion == MOTION_DRAGGED, srcScratch, dstScratch);
    }

    /**
     * Packed visual identity after {@link #doUpdate}: action, facing, sprite
     * frame, and pixel-snapped feet (with drag lift). Used to skip identical
     * canvas locks.
     */
    void writeVisualStamp(int[] out, int offset) {
        if (currentAction == null || !currentAction.isReady() || screenBounds == null) {
            out[offset] = 0;
            out[offset + 1] = 0;
            out[offset + 2] = -1;
            out[offset + 3] = 0;
            out[offset + 4] = 0;
            return;
        }
        int animTime = currentAction.getAnimationTime(direction);
        int time = animTime > 0 ? clampedAnimTime() : 0;
        float scale = getScale();
        int x = Math.round(posX);
        int y = Math.round(posY);
        if (motion == MOTION_DRAGGED) {
            y -= Math.round(20f * scale);
        }
        out[offset] = System.identityHashCode(currentAction);
        out[offset + 1] = direction;
        out[offset + 2] = animTime > 0 ? currentAction.getFrameIndex(direction, time) : -1;
        out[offset + 3] = x;
        out[offset + 4] = y;
    }

    /**
     * True when this pony is not interpolating travel or a special clip, so a
     * lower redraw rate is enough until a sprite frame or wait expiry.
     */
    boolean isVisuallyIdle() {
        return motion == MOTION_WAITING || motion == MOTION_INIT;
    }

    private int clampedAnimTime() {
        int animTime = currentAction.getAnimationTime(direction);
        int time = Math.round(frameTime);
        if (animTime > 0 && time >= animTime) time = animTime - 1;
        if (time < 0) time = 0;
        return time;
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
     * Returns the y-coordinate of the pony's feet (ground contact). This is used
     * to sort ponies that are higher up the screen as being further away.
     * 
     * @return the screen y-coordinate of ground contact
     */
    public int getY() {
        return Math.round(posY);
    }
    
    /**
     * Tests whether a click at the given screen point should be considered to
     * be a click on the pony. Matches {@link PonyAction#drawOn}'s sprite bounds
     * (feet at {@link #posY} via the action's anchor), with a small pad for touch.
     * 
     * @param x the x-coordinate of the click
     * @param y the y-coordinate of the click
     * @return {@code true} iff the point is on top of this pony
     */
    public boolean testHitPoint(float x, float y) {
        if (currentAction == null || !currentAction.isReady()) {
            return false;
        }
        float scale = getScale();
        RectF bounds = currentAction.getDrawBounds(posX, posY, scale, direction);
        // Same pad idea as the old radius (~30 unscaled px), applied outward.
        float pad = 8 * scale;
        return x >= bounds.left - pad && x < bounds.right + pad
                && y >= bounds.top - pad && y < bounds.bottom + pad;
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
            beginForcedExit(new Point(screenBounds.left - s, y));
        } else if (x >= screenBounds.right - s) {
            beginForcedExit(new Point(screenBounds.right + s, y));
        } else {
            beginWaitingInPlace();
        }
    }
    
    /**
     * Moves the pony to a position. {@code pos} is the feet / ground-contact point
     * (same space as wander targets).
     * 
     * @param pos the new feet position for the pony
     */
    public void moveTo(Point pos) {
        setDirection(pos);
        posX = pos.x;
        posY = pos.y;
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
        return tryBeginMoving(alwaysNewTarget, false);
    }

    /**
     * @param forceLeave if true, skip the 1-in-8 roll for {@code screen-out}
     *                   (drag-to-edge already chose to leave)
     */
    private boolean tryBeginMoving(boolean alwaysNewTarget, boolean forceLeave) {
        if (!currentAction.hasNextMoving()) {
            return false;
        }
        PonyAction next = currentAction.getNextMoving(random);
        if (next.type == PonyAction.SCREEN_OUT) {
            if (!forceLeave && !SceneExit.shouldLeaveScene(random)) {
                return false;
            }
            changeAction(next);
            motion = MOTION_SPECIAL;
            leavingMode = LM_GOING;
            targetPos = null;
            return true;
        }
        if (next.type == PonyAction.SCREEN_IN) {
            // Appear-in-place is not travel; play here then idle.
            changeAction(next);
            motion = MOTION_SPECIAL;
            return true;
        }
        changeAction(next);
        motion = next.type == PonyAction.NORMAL ? MOTION_MOVING : MOTION_SPECIAL;
        if (alwaysNewTarget || targetPos == null) {
            setRandomTarget();
        }
        return true;
    }

    /**
     * Drag-to-edge: leave now. A {@code screen-out} clip plays in place;
     * interpolating movers walk/fly/teleport to {@code offScreenTarget}.
     */
    private void beginForcedExit(Point offScreenTarget) {
        if (tryBeginMoving(false, true)) {
            leavingMode = LM_GOING;
            if (currentAction.type != PonyAction.SCREEN_OUT) {
                targetPos = offScreenTarget;
            }
        } else {
            beginWaitingInPlace();
        }
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
            PonyAction previous = currentAction;
            currentAction = newAction;
            frameTime = 0;
            if (effectHost != null && effectDefs.length > 0) {
                effectHost.onPonyActionChanged(this, previous, newAction);
            }
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
        if (SceneExit.shouldLeaveScene(random)) {
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
    

    
    /**
     * Largest unscaled frame height among this pony's loaded actions. Used so
     * on-screen wander targets keep the full sprite below the top edge under
     * bottom-center (feet) anchoring.
     */
    private int maxUnscaledFrameHeight() {
        int maxH = 0;
        for (int i = 0; i < allActions.length; i++) {
            for (int dir = PonyAction.LEFT; dir <= PonyAction.RIGHT; dir++) {
                int h = allActions[i].isReady() ? allActions[i].getFrameHeight(dir) : 0;
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
