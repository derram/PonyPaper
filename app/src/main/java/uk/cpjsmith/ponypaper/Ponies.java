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
import java.util.HashMap;
import java.util.HashSet;
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

    private static final Comparator<EffectInstance> comparePlantedY =
            new Comparator<EffectInstance>() {
        @Override
        public int compare(EffectInstance a, EffectInstance b) {
            int yA = a.sortY();
            int yB = b.sortY();
            return yA < yB ? -1 : yA > yB ? 1 : 0;
        }
    };
    
    private int activeCount;
    
    private Random random;

    private final Context ponyContext;
    /** Keys of ponies not on screen; graphs are created on take/prefetch. */
    private final InactiveRoster inactiveKeys = new InactiveRoster();
    /** Already-built ponies still in {@link #inactiveKeys}. */
    private final HashMap<String, Pony> inactiveReady = new HashMap<String, Pony>();
    private boolean worldFlow;
    private float sizeFactor = 1f;
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
    /** Reused each {@link #draw}: planted (non-follow) instances, Y-sorted. */
    private final ArrayList<EffectInstance> plantedDrawList = new ArrayList<EffectInstance>();
    /** Parallel to {@link #plantedDrawList}: overlap-parent grouping for this frame. */
    private boolean[] plantedGroupedScratch = new boolean[8];
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
     * Inactive ponies whose sheets are pinned because that many on-screen
     * ponies are leaving. Same pick as {@link #takeFromInactive}.
     */
    private final ArrayList<Pony> prefetched = new ArrayList<Pony>();
    /** When true, each enter from the inactive pool rolls a ladder size. */
    private final boolean randomSizeMode;
    /**
     * Roster reload: force exits and do not {@link #takeFromInactive}. Tableau
     * herds never drain ({@link #tableauJsonToLive} non-null).
     */
    private boolean draining;
    
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
        this(context, prefs, desiredCount, false);
    }

    /**
     * @param isDream when true, My ??? Pony sizing follows the dream scene mode
     */
    public Ponies(Context context, SharedPreferences prefs, int desiredCount,
            boolean isDream) {
        ponyContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        randomSizeMode = SceneMode.isRandomSize(prefs, isDream);
        worldFlow = SceneMode.isWorldFlow(prefs, isDream);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        String rawWaifu = prefs.getString("pref_waifu", "");
        waifuKey = rawWaifu != null ? rawWaifu : "";
        tableauJsonToLive = null;
        tableauPrefs = null;
        inactiveKeys.setAll(AllPonies.enabledHerdKeys(context, prefs));
        sizeFactor = PonySize.factor(prefs);

        if (desiredCount < 0) desiredCount = 0;
        int want = Math.min(inactiveKeys.size(), desiredCount);

        random = new Random();
        ArrayList<Pony> spawned = new ArrayList<Pony>(want);
        while (spawned.size() < want && !inactiveKeys.isEmpty()) {
            Pony next = takeFromInactiveOrNull();
            if (next == null) {
                break;
            }
            spawned.add(next);
        }
        activeCount = spawned.size();
        activePonies = spawned.toArray(new Pony[activeCount]);
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
        ponyContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        randomSizeMode = false;
        worldFlow = false;
        waifuKey = "";
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

        boolean normsChanged = pony.pinXNormPort != slot.xNorm
                || pony.pinYNormPort != slot.yNorm
                || pony.hasLandNorms != slot.hasLandNorms
                || (slot.hasLandNorms && (pony.pinXNormLand != slot.xNormLand
                        || pony.pinYNormLand != slot.yNormLand));
        boolean facingChanged = !slot.facing.equals(pony.facingPolicy);
        PonyAction[] newBag = TableauBuilder.resolveWaitBag(pony, slot.actions);
        if (newBag == null || newBag.length == 0) return;
        boolean actionsChanged = !sameActionBag(pony.waitBag, newBag);

        if (!normsChanged && !facingChanged && !actionsChanged) return;

        if (normsChanged) {
            boolean land = PonyScenes.clipIsLandscape(clip);
            float oldX = land && pony.hasLandNorms
                    ? pony.pinXNormLand : pony.pinXNormPort;
            float oldY = land && pony.hasLandNorms
                    ? pony.pinYNormLand : pony.pinYNormPort;
            float newX = slot.xFor(land);
            float newY = slot.yFor(land);
            pony.setPinNormsFromSlot(slot);
            // Only move when the active orientation's effective feet change.
            if (oldX != newX || oldY != newY) {
                pony.moveFeetToPin(clip);
            }
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
            TableauPin.applyWaitBag(pony, newBag);
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
        sizeFactor = factor;
        for (Pony pony : inactiveReady.values()) {
            pony.setSizeFactor(factor);
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
     * bitmaps no other host still holds (or keep them in the unpinned LRU).
     * Call before dropping this herd.
     */
    public void unloadSprites() {
        prefetched.clear();
        clearAllEffects();
        for (Pony pony : activePonies) {
            pony.unloadActions();
        }
        for (Pony pony : inactiveReady.values()) {
            pony.unloadActions();
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
                if (!HerdDrain.shouldRefill(draining)) {
                    retireActiveAt(i);
                    i--;
                    continue;
                }
                Pony temp = activePonies[i];
                temp.reset();
                if (!inactiveKeys.isEmpty()) {
                    activePonies[i] = takeFromInactiveOrNull();
                    if (activePonies[i] == null) {
                        activePonies[i] = temp;
                    } else {
                        retireToInactive(temp);
                    }
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
        // Herd stays Y-sorted. Each pony is a group: sprite, then follow
        // effects, then planted effects that still overlap the parent so
        // character VFX sit on top of the body. Planted effects that no
        // longer overlap Y-sort with the herd as world props; ponies in
        // front still cover the whole group.
        plantedDrawList.clear();
        for (int i = 0; i < effectInstances.size(); i++) {
            EffectInstance effect = effectInstances.get(i);
            if (!effect.def.follow) {
                plantedDrawList.add(effect);
            }
        }
        int nPlant = plantedDrawList.size();
        if (nPlant > 1) {
            java.util.Collections.sort(plantedDrawList, comparePlantedY);
        }
        if (plantedGroupedScratch.length < nPlant) {
            plantedGroupedScratch = new boolean[Math.max(nPlant, plantedGroupedScratch.length * 2)];
        }
        for (int i = 0; i < nPlant; i++) {
            EffectInstance effect = plantedDrawList.get(i);
            plantedGroupedScratch[i] = parentIsActive(effect.parent)
                    && effect.overlapsParent(effectPonyBounds, spriteDst);
        }

        int ponyIndex = 0;
        int plantIndex = nextLoosePlanted(0, nPlant);
        while (ponyIndex < activePonies.length || plantIndex < nPlant) {
            boolean drawPlant = plantIndex < nPlant
                    && (ponyIndex >= activePonies.length
                    || plantedDrawList.get(plantIndex).sortY()
                        <= activePonies[ponyIndex].getY());
            if (drawPlant) {
                plantedDrawList.get(plantIndex).drawOn(c, spriteSrc, spriteDst);
                plantIndex = nextLoosePlanted(plantIndex + 1, nPlant);
            } else {
                Pony pony = activePonies[ponyIndex];
                pony.drawOn(c, spriteSrc, spriteDst);
                for (int e = 0; e < effectInstances.size(); e++) {
                    EffectInstance effect = effectInstances.get(e);
                    if (effect.def.follow && effect.parent == pony) {
                        effect.drawOn(c, spriteSrc, spriteDst);
                    }
                }
                for (int i = 0; i < nPlant; i++) {
                    if (plantedGroupedScratch[i]
                            && plantedDrawList.get(i).parent == pony) {
                        plantedDrawList.get(i).drawOn(c, spriteSrc, spriteDst);
                    }
                }
                ponyIndex++;
            }
        }
    }

    /** Next planted effect that is not grouped onto its overlapping parent. */
    private int nextLoosePlanted(int from, int nPlant) {
        while (from < nPlant && plantedGroupedScratch[from]) {
            from++;
        }
        return from;
    }

    private boolean parentIsActive(Pony parent) {
        if (parent == null) {
            return false;
        }
        for (int i = 0; i < activePonies.length; i++) {
            if (activePonies[i] == parent) {
                return true;
            }
        }
        return false;
    }

    /**
     * Start a wander-herd drain: cancel replacement prefetch, unload inactive
     * sheets, stagger {@link Pony#forceSceneExit()}. Idempotent. Tableau and
     * empty herds return false.
     */
    boolean beginDrain() {
        if (!canDrain()) {
            return false;
        }
        if (draining) {
            return true;
        }
        draining = true;
        clearPrefetchUnloading();
        for (Pony pony : inactiveReady.values()) {
            pony.unloadActions();
        }
        for (int i = 0; i < activePonies.length; i++) {
            Pony pony = activePonies[i];
            boolean leaving = pony.isLeavingScene() || pony.goneOffScreen();
            int delay = HerdDrain.staggerDelayMs(leaving, pony.isTravelingOrSpawning(),
                    pony.remainingWaitMs(), i);
            pony.scheduleForceSceneExit(delay);
        }
        invalidateVisualStamp();
        return true;
    }

    boolean canDrain() {
        return tableauJsonToLive == null && activePonies != null && activePonies.length > 0;
    }

    boolean isDraining() {
        return draining;
    }

    boolean isDrainComplete() {
        return HerdDrain.isComplete(draining, activeCount);
    }

    /** Timeout / resize abort: mark every live slot gone and compact them out. */
    void completeDrainNow() {
        if (!draining || activePonies == null) {
            return;
        }
        while (activePonies.length > 0) {
            activePonies[0].completeExitNow();
            retireActiveAt(0);
        }
    }

    /**
     * Remove live slot {@code i} without taking a replacement. The leaver is
     * reset into the inactive pool (sheets unpinned).
     */
    private void retireActiveAt(int i) {
        if (activePonies == null || i < 0 || i >= activePonies.length) {
            return;
        }
        Pony temp = activePonies[i];
        temp.reset();
        retireToInactive(temp);
        int n = activePonies.length;
        Pony[] next = new Pony[n - 1];
        if (i > 0) {
            System.arraycopy(activePonies, 0, next, 0, i);
        }
        if (i < n - 1) {
            System.arraycopy(activePonies, i + 1, next, i, n - 1 - i);
        }
        activePonies = next;
        activeCount = next.length;
        invalidateVisualStamp();
    }

    private void clearPrefetchUnloading() {
        while (!prefetched.isEmpty()) {
            Pony extra = prefetched.remove(prefetched.size() - 1);
            extra.unloadActions();
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
                pony.pinClipIsLandscape(),
                pony.effectivePinXNorm(), pony.effectivePinYNorm());
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
     * When ponies are leaving, pin that many inactive replacements (same pick
     * as {@link #takeFromInactiveOrNull}). Drop extras if exits are cancelled;
     * keep the rest while any crossing is still in flight.
     */
    private void updateReplacementPrefetch() {
        if (draining) {
            return;
        }
        int leaving = 0;
        for (int i = 0; i < activePonies.length; i++) {
            if (activePonies[i].isLeavingScene()) {
                leaving++;
            }
        }
        for (int i = prefetched.size() - 1; i >= 0; i--) {
            if (!inactiveKeys.contains(prefetched.get(i).getPrefKey())) {
                prefetched.remove(i);
            }
        }
        int want = leaving;
        if (want > inactiveKeys.size()) {
            want = inactiveKeys.size();
        }
        while (prefetched.size() > want) {
            Pony extra = prefetched.remove(prefetched.size() - 1);
            extra.unloadActions();
        }
        HashSet<String> skipKeys = prefetchedKeys();
        while (prefetched.size() < want) {
            int idx = inactiveKeys.pickIndex(skipKeys, waifuKey, random);
            if (idx < 0) {
                break;
            }
            String key = inactiveKeys.get(idx);
            Pony next = materialize(key);
            if (next == null) {
                inactiveKeys.removeAt(idx);
                continue;
            }
            prefetched.add(next);
            skipKeys.add(key);
            next.loadActions();
        }
    }

    /**
     * Removes and returns one pony from the inactive pool. If a waifu key is set
     * and any inactive pony matches it, picks uniformly among those; otherwise
     * picks uniformly among all inactive ponies. Prefetched replacements are
     * consumed first so their sheets stay warm.
     */
    private Pony takeFromInactiveOrNull() {
        while (!inactiveKeys.isEmpty()) {
            Pony pony = takePrefetchedStillInactive();
            if (pony != null) {
                applyEnterSize(pony);
                return pony;
            }
            int idx = inactiveKeys.pickIndex(prefetchedKeys(), waifuKey, random);
            if (idx < 0) {
                idx = inactiveKeys.pickIndex(null, waifuKey, random);
            }
            if (idx < 0) {
                return null;
            }
            String key = inactiveKeys.removeAt(idx);
            for (int i = prefetched.size() - 1; i >= 0; i--) {
                if (key.equals(prefetched.get(i).getPrefKey())) {
                    prefetched.remove(i);
                }
            }
            pony = materialize(key);
            if (pony == null) {
                continue;
            }
            inactiveReady.remove(key);
            applyEnterSize(pony);
            return pony;
        }
        return null;
    }

    private void retireToInactive(Pony pony) {
        if (pony == null) {
            return;
        }
        String key = pony.getPrefKey();
        inactiveKeys.add(key);
        if (key.length() > 0) {
            inactiveReady.put(key, pony);
        }
    }

    /**
     * Build or reuse a pony for {@code key} without removing it from the
     * roster. Failed creates return null (caller drops the key).
     */
    private Pony materialize(String key) {
        if (key == null || key.length() == 0) {
            return null;
        }
        Pony existing = inactiveReady.get(key);
        if (existing != null) {
            return existing;
        }
        Pony created = AllPonies.createPony(ponyContext, key);
        if (created == null) {
            return null;
        }
        created.setEffectHost(this);
        if (worldFlow) {
            created.setWorldFlow(true);
        }
        if (!randomSizeMode) {
            created.setSizeFactor(sizeFactor);
        }
        inactiveReady.put(key, created);
        return created;
    }

    private void applyEnterSize(Pony pony) {
        if (randomSizeMode) {
            pony.setSizeFactor(PonySize.randomFactor(random));
        } else {
            pony.setSizeFactor(sizeFactor);
        }
    }

    private HashSet<String> prefetchedKeys() {
        HashSet<String> skip = new HashSet<String>();
        for (int i = 0; i < prefetched.size(); i++) {
            skip.add(prefetched.get(i).getPrefKey());
        }
        return skip;
    }

    private Pony takePrefetchedStillInactive() {
        for (int i = 0; i < prefetched.size(); i++) {
            Pony warmed = prefetched.get(i);
            String key = warmed.getPrefKey();
            if (inactiveKeys.removeKey(key)) {
                prefetched.remove(i);
                inactiveReady.remove(key);
                return warmed;
            }
            prefetched.remove(i);
            i--;
        }
        return null;
    }
    
}
