package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a Tableau herd: ensure active scene prefs, resolve slots, truncate to
 * the live power/thermal cap (document-order prefix), then {@link Ponies} with
 * an empty inactive pool.
 */
final class TableauBuilder {

    /** Hard ceiling on Tableau slots (matches {@link PonyScenes#MAX_SLOTS}). */
    static final int MAX_SLOTS = 16;

    private TableauBuilder() {
    }

    /**
     * Power/thermal/software pony cap for Tableau. Starts at {@link #MAX_SLOTS}
     * and applies the same clamps as wander, but never reads
     * {@code pref_num_ponies} or {@code pref_dream_num_ponies}.
     */
    static int getTableauCap(boolean batterySaverLimits,
            boolean defaultPoniesOnBattery,
            boolean softwareCanvasLimits,
            boolean thermalThrottle) {
        int cap = MAX_SLOTS;
        if (batterySaverLimits) {
            cap = Math.min(cap, PonySceneController.BATTERY_SAVER_MAX_PONIES);
        }
        if (defaultPoniesOnBattery) {
            cap = Math.min(cap, PonySceneController.DEFAULT_NUM_PONIES);
        }
        if (softwareCanvasLimits) {
            cap = Math.min(cap, PonySceneController.DEFAULT_NUM_PONIES);
        }
        if (thermalThrottle) {
            cap = Math.min(cap, PonySceneController.THERMAL_MODERATE_MAX_PONIES);
        }
        return cap;
    }

    /**
     * Slot count before cap truncation. Used so rebuild comparisons do not
     * churn when the power cap moves but stays above the resolved scene size.
     */
    static int slotCountBeforeCap(SharedPreferences prefs) {
        PonyScenes.TableauScene scene = PonyScenes.loadActiveScene(prefs);
        if (scene == null) return 0;
        return scene.slots.size();
    }

    /**
     * On-screen Tableau count after applying {@link #getTableauCap} to the
     * resolved slot list (document-order prefix length).
     */
    static int effectiveCount(SharedPreferences prefs,
            boolean batterySaverLimits,
            boolean defaultPoniesOnBattery,
            boolean softwareCanvasLimits,
            boolean thermalThrottle) {
        return Math.min(getTableauCap(batterySaverLimits, defaultPoniesOnBattery,
                softwareCanvasLimits, thermalThrottle), slotCountBeforeCap(prefs));
    }

    /**
     * Active-scene Tableau herd truncated to {@code cap} before construction.
     *
     * @param cap from {@link #getTableauCap}; values below 0 are treated as 0
     */
    static Ponies build(Context context, SharedPreferences prefs, int cap) {
        PonyScenes.ensureActiveScene(prefs);
        PonyScenes.TableauScene scene = PonyScenes.loadActiveScene(prefs);
        ArrayList<Pony> slots = resolveSlots(context, scene);
        int kept = cap < 0 ? 0 : cap;
        if (kept > slots.size()) {
            kept = slots.size();
        }
        List<Pony> prefix = slots.subList(0, kept);
        return new Ponies(context, prefix, prefs);
    }

    /**
     * Instantiate and pin one pony per slot. Drops slots whose
     * {@link AllPonies#createPony} returns null or whose wait bag cannot be
     * resolved.
     */
    static ArrayList<Pony> resolveSlots(Context context,
            PonyScenes.TableauScene scene) {
        ArrayList<Pony> out = new ArrayList<Pony>();
        if (context == null || scene == null) return out;
        int n = Math.min(scene.slots.size(), MAX_SLOTS);
        for (int i = 0; i < n; i++) {
            PonyScenes.TableauSlot slot = scene.slots.get(i);
            if (slot == null || slot.ponyKey.length() == 0) continue;
            Pony pony = AllPonies.createPony(context, slot.ponyKey);
            if (pony == null) continue;
            PonyAction[] bag = resolveWaitBag(pony, slot.actions);
            if (bag == null || bag.length == 0) continue;
            TableauPin.pin(pony, slot.xNorm, slot.yNorm, bag, slot.facing);
            out.add(pony);
        }
        return out;
    }

    /**
     * Interim wait-bag resolution until PR4 {@code ActionCatalog} /
     * {@code BuiltInActionIds}. Prefers matching by provisional
     * {@link PonyAction} action id when present; otherwise falls back to
     * {@code pony.getAllActions()[0]} (stand owner for built-ins). De-dupes
     * and caps at {@link PonyScenes#MAX_ACTIONS_PER_SLOT}.
     */
    static PonyAction[] resolveWaitBag(Pony pony, String[] actionIds) {
        if (pony == null) return null;
        ArrayList<PonyAction> matched = new ArrayList<PonyAction>(
                PonyScenes.MAX_ACTIONS_PER_SLOT);
        HashSetByIdentity seen = new HashSetByIdentity();
        if (actionIds != null) {
            for (int i = 0; i < actionIds.length
                    && matched.size() < PonyScenes.MAX_ACTIONS_PER_SLOT; i++) {
                String id = actionIds[i];
                if (id == null || id.length() == 0) continue;
                PonyAction action = findActionById(pony, id);
                if (action == null) continue;
                if (seen.add(action)) matched.add(action);
            }
        }
        if (matched.isEmpty()) {
            PonyAction[] all = pony.getAllActions();
            if (all == null || all.length == 0) return null;
            return new PonyAction[] { all[0] };
        }
        return matched.toArray(new PonyAction[matched.size()]);
    }

    /**
     * Match a catalog / provisional action id. Returns null until PR4 wires
     * {@code PonyAction.actionId()}; callers fall back to {@code allActions[0]}.
     */
    static PonyAction findActionById(Pony pony, String actionId) {
        if (pony == null || actionId == null || actionId.length() == 0) {
            return null;
        }
        // PR4: iterate pony.getAllActions() and compare action.actionId().
        return null;
    }

    /** Identity set without allocating HashSet&lt;PonyAction&gt; wrappers heavily. */
    private static final class HashSetByIdentity {
        private final ArrayList<PonyAction> items = new ArrayList<PonyAction>(8);

        boolean add(PonyAction action) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == action) return false;
            }
            items.add(action);
            return true;
        }
    }
}
