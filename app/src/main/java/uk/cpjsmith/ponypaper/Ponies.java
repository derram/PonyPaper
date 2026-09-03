package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Class to hold the collection of ponies and coordinate their overall motion.
 * Also owns live {@link EffectInstance} sprites spawned by custom characters.
 */
public class Ponies implements Pony.EffectHost {
    
    /**
     * Hold duration before a touch on a pony becomes a drag. Prevents
     * accidental grabs from home-screen swipes and casual taps.
     */
    private static final int LONG_PRESS_MS = 300;
    
    private static final Comparator<Pony> compareY = new Comparator<Pony>() {
        @Override
        public int compare(Pony lhs, Pony rhs) {
            int yL = lhs.getY();
            int yR = rhs.getY();
            return yL < yR ? -1 : yL > yR ? 1 : 0;
        }
    };
    
    private int activeCount;
    
    private Random random;
    
    private ArrayList<Pony> inactivePonies;
    private Pony[] activePonies;
    /**
     * Tableau only: maps full active-JSON slot index → live {@link #activePonies}
     * index, or {@code -1} when the JSON slot was dropped or cap-clipped.
     * Null for wander herds.
     */
    private final int[] tableauJsonToLive;
    /** Tableau only: prefs for hot-writing slot norms after drag; null for wander. */
    private final SharedPreferences tableauPrefs;
    private final Rect clipBounds = new Rect();
    private final Rect spriteSrc = new Rect();
    private final Rect spriteDst = new Rect();
    private final RectF effectPonyBounds = new RectF();
    private final float[] effectOrigin = new float[2];
    private final float[] effectTravel = new float[2];
    private final int[] effectPlaceScratch = new int[1];
    /** Live effect sprites (not pony herd slots). */
    private final ArrayList<EffectInstance> effectInstances = new ArrayList<EffectInstance>();
    /** Pending repeats while a trigger action remains current. */
    private final ArrayList<EffectRepeat> effectRepeats = new ArrayList<EffectRepeat>();
    /** 5 ints per active pony (+5 per effect); compared to skip blits. */
    private int[] visualStamp;
    private int[] lastVisualStamp;
    private int visualStampLen = -1;

    private static final class EffectRepeat {
        final Pony pony;
        final PonyEffectDef def;
        float remainingMs;

        EffectRepeat(Pony pony, PonyEffectDef def, float remainingMs) {
            this.pony = pony;
            this.def = def;
            this.remainingMs = remainingMs;
        }
    }
    /**
     * Preference key of the user's favorite pony ({@code pref_waifu}), or empty
     * for none. When non-empty, inactive ponies with this key are preferred when
     * filling or replacing active slots.
     */
    private final String waifuKey;
    /**
     * Inactive pony whose sheets are pinned because an on-screen pony is
     * leaving. At most one extra character is decoded this way.
     */
    private Pony prefetched;
    /** When true, each enter from the inactive pool rolls a ladder size. */
    private final boolean randomSizeMode;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int touchSlop;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (pendingPony == null) return;
            draggedPony = pendingPony;
            pendingPony = null;
            draggedThisGesture = true;
            draggedPony.startDrag();
            draggedPony.moveTo(new Point(Math.round(lastX), Math.round(lastY)));
        }
    };
    
    private int initialPointerId = -1;
    private Pony draggedPony = null;
    /** Pony under the finger waiting for the long-press timeout. */
    private Pony pendingPony = null;
    /**
     * True after a long-press drag starts within the current gesture. Used by
     * hosts (e.g. dream/screensaver) that dismiss on tap but keep a drag open.
     */
    private boolean draggedThisGesture = false;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    
    /**
     * Creates a new {@code Ponies} instance using {@code pref_num_ponies}.
     *
     * @param context the current application context
     * @param prefs   the user's preferences of which ponies to load
     */
    public Ponies(Context context, SharedPreferences prefs) {
        this(context, prefs, prefs.getInt("pref_num_ponies", 4));
    }

    /**
     * Creates a new {@code Ponies} instance with an explicit active-pony count.
     * Callers can pass a battery-saver (or other policy) capped value instead of
     * reading the preference themselves.
     *
     * @param context     the current application context
     * @param prefs       the user's preferences of which ponies to load
     * @param desiredCount requested number of on-screen ponies (clamped to the
     *                    available pool size; values below 1 become 0)
     */
    public Ponies(Context context, SharedPreferences prefs, int desiredCount) {
        inactivePonies = AllPonies.getPonies(context, prefs);
        randomSizeMode = SceneMode.isRandomSize(prefs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        String rawWaifu = prefs.getString("pref_waifu", "");
        waifuKey = rawWaifu != null ? rawWaifu : "";
        tableauJsonToLive = null;
        tableauPrefs = null;

        if (desiredCount < 0) desiredCount = 0;
        activeCount = Math.min(inactivePonies.size(), desiredCount);

        random = new Random();
        if (!randomSizeMode) {
            applySizeFactor(PonySize.factor(prefs));
        }
        wireEffectHosts(inactivePonies);
        activePonies = new Pony[activeCount];
        for (int i = 0; i < activeCount; i++) {
            activePonies[i] = takeFromInactive();
        }
    }

    /**
     * Active-only Tableau herd: every list member is on-screen; the inactive
     * pool is empty so gone-off-screen cannot swap in a replacement.
     *
     * @param context      used for touch slop
     * @param pinnedPonies already-pinned ponies (resolved document order)
     * @param prefs        size and related prefs
     * @param jsonToLive   full JSON slot index → live index, or {@code -1}
     */
    public Ponies(Context context, List<Pony> pinnedPonies, SharedPreferences prefs,
            int[] jsonToLive) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        randomSizeMode = false;
        waifuKey = "";
        inactivePonies = new ArrayList<Pony>();
        random = new Random();
        tableauJsonToLive = jsonToLive;
        tableauPrefs = prefs;

        activeCount = pinnedPonies != null ? pinnedPonies.size() : 0;
        activePonies = new Pony[activeCount];
        ArrayList<Pony> wired = new ArrayList<Pony>(activeCount);
        float size = PonySize.factor(prefs);
        for (int i = 0; i < activeCount; i++) {
            Pony pony = pinnedPonies.get(i);
            pony.setSizeFactor(size);
            activePonies[i] = pony;
            wired.add(pony);
        }
        wireEffectHosts(wired);
    }

    /**
     * Hot-path Tableau slot update. {@code index} is the full active-JSON slot
     * index; looks up the pony by {@link Pony#tableauSlotIndex} (stable across
     * Y-sort). Dropped or cap-clipped slots are persist-only no-ops.
     * Idempotent: unchanged norms / facing / actions do not restart the wait
     * timer, re-roll random facing, or rewrite bags.
     */
    void applyTableauHotSlot(int index, PonyScenes.TableauSlot slot, Rect clip) {
        if (slot == null || tableauJsonToLive == null) return;
        if (index < 0) return;
        Pony pony = findPinnedByTableauSlot(index);
        if (pony == null) return;

        boolean normsChanged = pony.pinXNorm != slot.xNorm
                || pony.pinYNorm != slot.yNorm;
        boolean facingChanged = !slot.facing.equals(pony.facingPolicy);
        PonyAction[] newBag = TableauBuilder.resolveWaitBag(pony, slot.actions);
        if (newBag == null || newBag.length == 0) return;
        boolean actionsChanged = !sameActionBag(pony.waitBag, newBag);

        if (!normsChanged && !facingChanged && !actionsChanged) return;

        if (normsChanged) {
            pony.setPinNorms(slot.xNorm, slot.yNorm);
            pony.moveFeetToPin(clip);
        }
        if (facingChanged) {
            pony.setFacingPolicy(slot.facing, PonyAction.LEFT);
            if (Pony.FACING_LEFT.equals(slot.facing)) {
                pony.setFacingDirection(PonyAction.LEFT);
            } else if (Pony.FACING_RIGHT.equals(slot.facing)) {
                pony.setFacingDirection(PonyAction.RIGHT);
            }
            // Switching to random: keep current direction until next setWaiting.
        }
        if (actionsChanged) {
            TableauPin.pin(pony, pony.pinXNorm, pony.pinYNorm, newBag,
                    pony.facingPolicy);
            if (!actionInBag(pony.getCurrentAction(), newBag)) {
                pony.changeActionKeepingWait(newBag[0]);
            }
        }
        invalidateVisualStamp();
    }

    private static boolean sameActionBag(PonyAction[] a, PonyAction[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    private static boolean actionInBag(PonyAction action, PonyAction[] bag) {
        if (action == null || bag == null) return false;
        for (int i = 0; i < bag.length; i++) {
            if (bag[i] == action) return true;
        }
        return false;
    }

    private void wireEffectHosts(ArrayList<Pony> ponies) {
        if (ponies == null) {
            return;
        }
        for (int i = 0; i < ponies.size(); i++) {
            ponies.get(i).setEffectHost(this);
        }
    }

    /**
     * @return how many ponies are currently drawn on screen
     */
    public int getActiveCount() {
        return activeCount;
    }

    /**
     * Applies a character-size multiplier to every loaded pony (active and
     * waiting). Takes effect on the next draw without resetting positions.
     */
    public void setSizeFactor(float factor) {
        applySizeFactor(factor);
        if (activePonies != null) {
            for (int i = 0; i < activePonies.length; i++) {
                activePonies[i].setSizeFactor(factor);
            }
        }
        invalidateVisualStamp();
    }

    private void applySizeFactor(float factor) {
        if (inactivePonies == null) return;
        for (int i = 0; i < inactivePonies.size(); i++) {
            inactivePonies.get(i).setSizeFactor(factor);
        }
    }

    /**
     * Resets the position of all active (on-screen) ponies.
     */
    public void reset() {
        clearAllEffects();
        for (Pony pony : activePonies) pony.reset();
        invalidateVisualStamp();
    }

    /**
     * Unpin every pony's sprite sheets so {@link SpriteCache} can recycle
     * bitmaps no other host still holds. Call before dropping this herd.
     */
    public void unloadSprites() {
        prefetched = null;
        clearAllEffects();
        for (Pony pony : activePonies) {
            pony.unloadActions();
        }
        for (int i = 0; i < inactivePonies.size(); i++) {
            inactivePonies.get(i).unloadActions();
        }
    }

    private void clearAllEffects() {
        effectInstances.clear();
        effectRepeats.clear();
    }

    /**
     * Start pinning sheets for every on-screen pony. Safe to call from the
     * decode worker after the herd is built.
     */
    void preloadActiveSprites() {
        if (activePonies == null) {
            return;
        }
        for (int i = 0; i < activePonies.length; i++) {
            activePonies[i].loadActions();
        }
    }

    /**
     * Tableau preload: pin only wait/start bag sheets so the scene-ready gate
     * can trip without decoding unused catalog actions first.
     */
    void preloadActiveWaitBags() {
        if (activePonies == null) {
            return;
        }
        for (int i = 0; i < activePonies.length; i++) {
            activePonies[i].loadWaitBagActions();
        }
    }

    /**
     * True when every live Tableau pony has left {@code MOTION_INIT_PINNED}
     * (pinned and drawable, or marked gone after a decode failure).
     */
    boolean allPinnedSpawnsComplete() {
        if (activePonies == null) {
            return true;
        }
        for (int i = 0; i < activePonies.length; i++) {
            if (activePonies[i].isAwaitingPinnedSpawn()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Updates all active ponies for the elapsed time and draws them on the
     * given canvas.
     * 
     * @param c       the canvas to draw on
     * @param deltaMs milliseconds since the previous frame (animation and
     *                motion are scaled by this so they stay consistent across
     *                framerates)
     */
    public void drawAndUpdate(Canvas c, long deltaMs) {
        c.getClipBounds(clipBounds);
        update(clipBounds, deltaMs);
        draw(c);
    }

    /**
     * Advances motion/animation. {@code clip} is copied; the caller may reuse it.
     *
     * @return true if a later {@link #draw} would differ from the previous one
     */
    boolean update(Rect clip, long deltaMs) {
        clipBounds.set(clip);
        for (int i = 0; i < activePonies.length; i++) {
            activePonies[i].doUpdate(clipBounds, deltaMs);
            if (activePonies[i].goneOffScreen()) {
                Pony temp = activePonies[i];
                temp.reset();
                if (inactivePonies.size() != 0) {
                    activePonies[i] = takeFromInactive();
                    inactivePonies.add(temp);
                } else if (randomSizeMode) {
                    // Full pool is on-screen; re-roll size on the same pony.
                    temp.setSizeFactor(PonySize.randomFactor(random));
                }
                activePonies[i].doUpdate(clipBounds, 0);
            }
        }
        updateEffects(deltaMs);
        updateReplacementPrefetch();
        Arrays.sort(activePonies, compareY);
        return captureVisualDirty();
    }

    void draw(Canvas c) {
        // Draw planted effects behind/among ponies by Y, then ponies, then
        // follow effects immediately after their parent so they stick visually.
        ArrayList<EffectInstance> planted = null;
        for (int i = 0; i < effectInstances.size(); i++) {
            EffectInstance effect = effectInstances.get(i);
            if (effect.def.follow) {
                continue;
            }
            if (planted == null) {
                planted = new ArrayList<EffectInstance>();
            }
            planted.add(effect);
        }
        int ponyIndex = 0;
        int plantIndex = 0;
        if (planted != null) {
            // Insertion order is fine for a small list; sort by bottom Y.
            java.util.Collections.sort(planted, new Comparator<EffectInstance>() {
                @Override
                public int compare(EffectInstance a, EffectInstance b) {
                    int yA = a.sortY();
                    int yB = b.sortY();
                    return yA < yB ? -1 : yA > yB ? 1 : 0;
                }
            });
        }
        while (ponyIndex < activePonies.length
                || (planted != null && plantIndex < planted.size())) {
            boolean drawPlant = planted != null && plantIndex < planted.size()
                    && (ponyIndex >= activePonies.length
                    || planted.get(plantIndex).sortY() <= activePonies[ponyIndex].getY());
            if (drawPlant) {
                planted.get(plantIndex).drawOn(c, spriteSrc, spriteDst);
                plantIndex++;
            } else {
                Pony pony = activePonies[ponyIndex];
                pony.drawOn(c, spriteSrc, spriteDst);
                for (int e = 0; e < effectInstances.size(); e++) {
                    EffectInstance effect = effectInstances.get(e);
                    if (effect.def.follow && effect.parent == pony) {
                        effect.drawOn(c, spriteSrc, spriteDst);
                    }
                }
                ponyIndex++;
            }
        }
    }

    /** True when every on-screen pony is waiting or still spawning. */
    boolean allIdle() {
        if (!effectInstances.isEmpty()) {
            return false;
        }
        for (int i = 0; i < activePonies.length; i++) {
            if (!activePonies[i].isVisuallyIdle()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onPonyActionChanged(Pony pony, PonyAction previous, PonyAction next) {
        stopRepeatsFor(pony);
        expireDurationZeroFor(pony);
        if (next == null || !pony.hasEffects()) {
            return;
        }
        PonyEffectDef[] defs = pony.getEffectDefs();
        for (int i = 0; i < defs.length; i++) {
            PonyEffectDef def = defs[i];
            if (!def.triggersOn(next) || !def.isReady()) {
                continue;
            }
            spawnEffect(pony, def);
            if (def.repeatDelayMs > 0f) {
                effectRepeats.add(new EffectRepeat(pony, def, def.repeatDelayMs));
            }
        }
    }

    @Override
    public void onPonyEffectsCleared(Pony pony) {
        stopRepeatsFor(pony);
        for (int i = effectInstances.size() - 1; i >= 0; i--) {
            if (effectInstances.get(i).parent == pony) {
                effectInstances.remove(i);
            }
        }
        invalidateVisualStamp();
    }

    private void stopRepeatsFor(Pony pony) {
        for (int i = effectRepeats.size() - 1; i >= 0; i--) {
            if (effectRepeats.get(i).pony == pony) {
                effectRepeats.remove(i);
            }
        }
    }

    private void expireDurationZeroFor(Pony pony) {
        for (int i = effectInstances.size() - 1; i >= 0; i--) {
            EffectInstance effect = effectInstances.get(i);
            if (effect.parent == pony && effect.def.durationMs <= 0f) {
                effectInstances.remove(i);
            }
        }
    }

    private void updateEffects(long deltaMs) {
        float dt = deltaMs > 0 ? (float)deltaMs : 0f;
        for (int i = effectRepeats.size() - 1; i >= 0; i--) {
            EffectRepeat repeat = effectRepeats.get(i);
            repeat.remainingMs -= dt;
            while (repeat.remainingMs <= 0f) {
                spawnEffect(repeat.pony, repeat.def);
                if (repeat.def.repeatDelayMs <= 0f) {
                    effectRepeats.remove(i);
                    break;
                }
                repeat.remainingMs += repeat.def.repeatDelayMs;
            }
        }
        for (int i = effectInstances.size() - 1; i >= 0; i--) {
            EffectInstance effect = effectInstances.get(i);
            if (!effect.update(dt, effectPonyBounds, effectOrigin)) {
                effectInstances.remove(i);
            }
        }
    }

    private void spawnEffect(Pony pony, PonyEffectDef def) {
        if (!def.isReady()) {
            return;
        }
        while (effectInstances.size() >= PonyEffectDef.MAX_LIVE_INSTANCES) {
            if (!evictOldestPlanted()) {
                return;
            }
        }
        pony.fillCurrentDrawBounds(effectPonyBounds);
        if (effectPonyBounds.isEmpty()) {
            return;
        }
        int facing = pony.getDirection();
        pony.fillTravelVector(effectTravel);
        def.computeOrigin(effectPonyBounds, facing, pony.getScale(),
                pony.effectRandom(), effectOrigin, effectPlaceScratch,
                effectTravel[0], effectTravel[1]);
        effectInstances.add(new EffectInstance(def, pony, facing, effectPlaceScratch[0],
                effectOrigin[0], effectOrigin[1]));
        invalidateVisualStamp();
    }

    /** Prefer dropping the oldest non-follow instance when over cap. */
    private boolean evictOldestPlanted() {
        for (int i = 0; i < effectInstances.size(); i++) {
            if (!effectInstances.get(i).def.follow) {
                effectInstances.remove(i);
                return true;
            }
        }
        if (!effectInstances.isEmpty()) {
            effectInstances.remove(0);
            return true;
        }
        return false;
    }

    private void invalidateVisualStamp() {
        visualStampLen = -1;
    }

    private boolean captureVisualDirty() {
        int effectCount = effectInstances.size();
        int n = activePonies.length * 5 + effectCount * 5;
        if (visualStamp == null || visualStamp.length < n) {
            visualStamp = new int[Math.max(n, 16)];
            lastVisualStamp = new int[visualStamp.length];
            visualStampLen = -1;
        }
        for (int i = 0; i < activePonies.length; i++) {
            activePonies[i].writeVisualStamp(visualStamp, i * 5);
        }
        int base = activePonies.length * 5;
        for (int i = 0; i < effectCount; i++) {
            effectInstances.get(i).writeVisualStamp(visualStamp, base + i * 5);
        }
        boolean dirty = visualStampLen != n;
        if (!dirty) {
            for (int i = 0; i < n; i++) {
                if (visualStamp[i] != lastVisualStamp[i]) {
                    dirty = true;
                    break;
                }
            }
        }
        if (lastVisualStamp.length < n) {
            lastVisualStamp = new int[visualStamp.length];
        }
        System.arraycopy(visualStamp, 0, lastVisualStamp, 0, n);
        visualStampLen = n;
        return dirty;
    }
    
    /**
     * Handles a touch event on the screen. This allows the user a means of
     * dragging ponies around the screen. A short hold is required before a
     * drag starts so home-screen swipes and taps do not grab a pony by
     * accident.
     * 
     * @param event the touch event that was performed by the user
     */
    public void onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                endDrag();
                cancelPendingDrag();
                draggedThisGesture = false;
                
                initialPointerId = event.getPointerId(0);
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                
                for (Pony pony : activePonies) {
                    if (pony.testHitPoint(downX, downY)) pendingPony = pony;
                }
                if (pendingPony != null) {
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endDrag();
                cancelPendingDrag();
                initialPointerId = -1;
                break;
                
            case MotionEvent.ACTION_MOVE:
                lastX = event.getX();
                lastY = event.getY();
                if (pendingPony != null) {
                    float dx = lastX - downX;
                    float dy = lastY - downY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        // Finger moved before the hold completed — treat as a
                        // home-screen gesture, not a grab.
                        cancelPendingDrag();
                    }
                }
                if (draggedPony != null) {
                    draggedPony.moveTo(new Point(Math.round(lastX), Math.round(lastY)));
                }
                break;
                
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == initialPointerId) {
                    endDrag();
                    cancelPendingDrag();
                    initialPointerId = -1;
                }
                break;
        }
    }

    /**
     * Whether the current (or just-finished) gesture long-press-dragged a pony.
     * Cleared on the next {@link MotionEvent#ACTION_DOWN}.
     */
    public boolean didDragThisGesture() {
        return draggedThisGesture;
    }
    
    private void cancelPendingDrag() {
        handler.removeCallbacks(longPressRunnable);
        pendingPony = null;
    }
    
    private void endDrag() {
        if (draggedPony != null) {
            Pony pony = draggedPony;
            pony.stopDrag();
            draggedPony = null;
            persistTableauDragNorms(pony);
        }
    }

    /**
     * After a pinned drag release, write the pony's new pin norms into the
     * matching full-JSON slot via {@link PonyScenes#writeActiveSlotNormsHot}.
     * Uses {@link Pony#tableauSlotIndex} so Y-sort cannot cross-wire slots.
     */
    private void persistTableauDragNorms(Pony pony) {
        if (tableauPrefs == null || pony == null || !pony.isPinned()) return;
        int jsonIndex = pony.getTableauSlotIndex();
        if (jsonIndex < 0) return;
        PonyScenes.writeActiveSlotNormsHot(tableauPrefs, jsonIndex,
                pony.pinXNorm, pony.pinYNorm);
    }

    /** Live pinned pony for a full-JSON slot index, or null if clipped/absent. */
    private Pony findPinnedByTableauSlot(int jsonIndex) {
        for (int i = 0; i < activeCount; i++) {
            Pony pony = activePonies[i];
            if (pony != null && pony.isPinned()
                    && pony.getTableauSlotIndex() == jsonIndex) {
                return pony;
            }
        }
        return null;
    }
    
    /**
     * Removes and returns one pony from the inactive pool. If a waifu key is set
     * and any inactive pony matches it, picks uniformly among those; otherwise
     * picks uniformly among all inactive ponies.
     */
    /**
     * When someone is leaving, pin one inactive replacement (same pick as
     * {@link #takeFromInactive}). Drop that pin if the exit is cancelled.
     */
    private void updateReplacementPrefetch() {
        boolean leaving = false;
        for (int i = 0; i < activePonies.length; i++) {
            if (activePonies[i].isLeavingScene()) {
                leaving = true;
                break;
            }
        }
        if (!leaving) {
            if (prefetched != null) {
                if (inactivePonies.contains(prefetched)) {
                    prefetched.unloadActions();
                }
                prefetched = null;
            }
            return;
        }
        if (prefetched != null && inactivePonies.contains(prefetched)) {
            return;
        }
        prefetched = peekFromInactive();
        if (prefetched != null) {
            prefetched.loadActions();
        }
    }

    private Pony peekFromInactive() {
        if (inactivePonies.isEmpty()) {
            return null;
        }
        return inactivePonies.get(indexToTakeFromInactive());
    }

    private Pony takeFromInactive() {
        if (inactivePonies.isEmpty()) {
            throw new IllegalStateException("inactive pool is empty");
        }
        Pony pony;
        if (prefetched != null) {
            int prefIdx = inactivePonies.indexOf(prefetched);
            if (prefIdx >= 0) {
                prefetched = null;
                pony = inactivePonies.remove(prefIdx);
            } else {
                prefetched = null;
                pony = inactivePonies.remove(indexToTakeFromInactive());
            }
        } else {
            pony = inactivePonies.remove(indexToTakeFromInactive());
        }
        if (randomSizeMode) {
            pony.setSizeFactor(PonySize.randomFactor(random));
        }
        return pony;
    }

    private int indexToTakeFromInactive() {
        if (waifuKey.length() > 0) {
            int matchCount = 0;
            for (int i = 0; i < inactivePonies.size(); i++) {
                if (waifuKey.equals(inactivePonies.get(i).getPrefKey())) {
                    matchCount++;
                }
            }
            if (matchCount > 0) {
                int pick = random.nextInt(matchCount);
                for (int i = 0; i < inactivePonies.size(); i++) {
                    if (waifuKey.equals(inactivePonies.get(i).getPrefKey())) {
                        if (pick == 0) {
                            return i;
                        }
                        pick--;
                    }
                }
            }
        }
        return random.nextInt(inactivePonies.size());
    }
    
}
