package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.PowerManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Builds a Tableau herd: ensure active scene prefs, resolve slots, truncate to
 * the live power/thermal cap (document-order prefix of the resolved list), then
 * {@link Ponies} with an empty inactive pool. Keeps a JSON-index → live-index
 * map so hot edits use full active-JSON indices after mid-list drops.
 */
final class TableauBuilder {

    /** Hard ceiling on Tableau slots (matches {@link PonyScenes#MAX_SLOTS}). */
    static final int MAX_SLOTS = 16;

    /**
     * Resolved live ponies (compressed, then capped) plus JSON→live mapping.
     * {@code jsonToLive[j] == -1} when JSON slot {@code j} was dropped or
     * clipped by the cap.
     */
    static final class ResolvedHerd {
        final List<Pony> live;
        final int[] jsonToLive;

        ResolvedHerd(List<Pony> live, int[] jsonToLive) {
            this.live = live != null ? live : Collections.<Pony>emptyList();
            this.jsonToLive = jsonToLive != null ? jsonToLive : new int[0];
        }
    }

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
     * Best-effort cap for Settings UI dimming/annotation. Uses power-save /
     * on-battery prefs only — software-canvas and thermal state live in the
     * engine, so the editor may under-annotate vs the live herd.
     */
    static int estimateCapForSettings(Context context, SharedPreferences prefs) {
        if (context == null || prefs == null) return MAX_SLOTS;
        boolean powerSave = false;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) powerSave = pm.isPowerSaveMode();
        boolean batterySaverLimits = powerSave
                && prefs.getBoolean(PonySceneController.PREF_RESPECT_BATTERY_SAVER, true);
        boolean onBattery = true;
        try {
            Intent status = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (status != null) {
                int plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                onBattery = plugged == 0;
            }
        } catch (Exception ignored) {
        }
        boolean defaultPoniesOnBattery = onBattery
                && prefs.getBoolean(PonySceneController.PREF_BATTERY_DEFAULT_PONIES, false);
        return getTableauCap(batterySaverLimits, defaultPoniesOnBattery, false, false);
    }

    /**
     * Resolved slot count before cap truncation (same drop rules as build).
     * Uses a cheap key probe — not {@link AllPonies#createPony} — so power /
     * thermal rebuild comparisons stay allocation-free.
     */
    static int slotCountBeforeCap(SharedPreferences prefs) {
        return slotCountBeforeCap(prefs, false);
    }

    static int slotCountBeforeCap(SharedPreferences prefs, boolean isDream) {
        PonyScenes.TableauScene scene =
                PonyScenes.resolveTableauScene(prefs, isDream);
        return countResolvable(scene);
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
        return effectiveCount(prefs, batterySaverLimits, defaultPoniesOnBattery,
                softwareCanvasLimits, thermalThrottle, false);
    }

    static int effectiveCount(SharedPreferences prefs,
            boolean batterySaverLimits,
            boolean defaultPoniesOnBattery,
            boolean softwareCanvasLimits,
            boolean thermalThrottle,
            boolean isDream) {
        return Math.min(getTableauCap(batterySaverLimits, defaultPoniesOnBattery,
                softwareCanvasLimits, thermalThrottle),
                slotCountBeforeCap(prefs, isDream));
    }

    /**
     * Active-scene Tableau herd truncated to {@code cap} before construction.
     *
     * @param cap from {@link #getTableauCap}; values below 0 are treated as 0
     */
    static Ponies build(Context context, SharedPreferences prefs, int cap) {
        return build(context, prefs, cap, false);
    }

    /**
     * Tableau herd for wallpaper or dream. Wallpaper (and dream when following
     * the wallpaper active scene) may seed active JSON via
     * {@link PonyScenes#ensureActiveScene}. A dream library pick never writes
     * wallpaper active prefs.
     *
     * @param cap from {@link #getTableauCap}; values below 0 are treated as 0
     */
    static Ponies build(Context context, SharedPreferences prefs, int cap,
            boolean isDream) {
        PonyScenes.TableauScene scene;
        if (!isDream || PonyScenes.dreamUsesWallpaperActive(prefs)) {
            PonyScenes.ensureActiveScene(prefs);
            scene = PonyScenes.resolveTableauScene(prefs, isDream);
        } else {
            scene = PonyScenes.resolveTableauScene(prefs, true);
            if (scene == null) {
                scene = PonyScenes.demoScene();
            }
        }
        ResolvedHerd resolved = resolveAndCap(context, scene, cap);
        return new Ponies(context, resolved.live, prefs, resolved.jsonToLive);
    }

    /**
     * Resolve (drop failures), then keep the first {@code cap} resolved ponies.
     * Builds {@code jsonToLive} so full JSON indices map to live herd slots.
     */
    static ResolvedHerd resolveAndCap(Context context,
            PonyScenes.TableauScene scene, int cap) {
        int jsonCount = scene != null ? Math.min(scene.slots.size(), MAX_SLOTS) : 0;
        int[] jsonToLive = new int[jsonCount];
        Arrays.fill(jsonToLive, -1);
        if (context == null || scene == null || jsonCount == 0) {
            return new ResolvedHerd(Collections.<Pony>emptyList(), jsonToLive);
        }

        ArrayList<Pony> resolved = new ArrayList<Pony>(jsonCount);
        int[] resolvedFromJson = new int[jsonCount];
        int resolvedCount = 0;
        for (int i = 0; i < jsonCount; i++) {
            Pony pony = tryResolveSlot(context, scene.slots.get(i));
            if (pony == null) continue;
            resolvedFromJson[resolvedCount] = i;
            resolved.add(pony);
            resolvedCount++;
        }

        int kept = cap < 0 ? 0 : cap;
        if (kept > resolved.size()) {
            kept = resolved.size();
        }
        for (int live = 0; live < kept; live++) {
            int jsonIndex = resolvedFromJson[live];
            jsonToLive[jsonIndex] = live;
            // Stable across Ponies Y-sort of the live array.
            resolved.get(live).setTableauSlotIndex(jsonIndex);
        }
        List<Pony> live = kept == 0
                ? Collections.<Pony>emptyList()
                : new ArrayList<Pony>(resolved.subList(0, kept));
        return new ResolvedHerd(live, jsonToLive);
    }

    /** How many slots would survive resolve (no cap). Allocation-free. */
    static int countResolvable(PonyScenes.TableauScene scene) {
        if (scene == null) return 0;
        int n = Math.min(scene.slots.size(), MAX_SLOTS);
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (canResolveSlot(scene.slots.get(i))) count++;
        }
        return count;
    }

    /**
     * True when the slot has a creatable pony key. Actual pin still requires a
     * resolvable wait bag (selectable ids or preferred stand); empty-selectable
     * catalogs drop the slot in {@link #tryResolveSlot}.
     */
    static boolean canResolveSlot(PonyScenes.TableauSlot slot) {
        return slot != null
                && slot.ponyKey.length() > 0
                && AllPonies.canCreatePony(slot.ponyKey);
    }

    /**
     * Instantiate and pin one pony for a slot, or {@code null} when the pony
     * cannot be created or the wait bag cannot be resolved.
     */
    static Pony tryResolveSlot(Context context, PonyScenes.TableauSlot slot) {
        if (context == null || slot == null || slot.ponyKey.length() == 0) {
            return null;
        }
        Pony pony = AllPonies.createPony(context, slot.ponyKey);
        if (pony == null) return null;
        PonyAction[] bag = resolveWaitBag(pony, slot.actions);
        if (bag == null || bag.length == 0) return null;
        TableauPin.pin(pony, slot, bag);
        return pony;
    }

    /**
     * Resolve wait-bag action ids through {@link AllPonies#buildActionCatalog}.
     * Unknown and non-selectable ids are skipped. Empty results fall back to
     * preferred stand (then first selectable); if nothing selectable remains,
     * returns null so {@link #tryResolveSlot} drops the slot. De-dupes and caps
     * at {@link PonyScenes#MAX_ACTIONS_PER_SLOT}.
     */
    static PonyAction[] resolveWaitBag(Pony pony, String[] actionIds) {
        if (pony == null) return null;
        AllPonies.ActionCatalog catalog = AllPonies.buildActionCatalog(pony);
        ArrayList<PonyAction> matched = new ArrayList<PonyAction>(
                PonyScenes.MAX_ACTIONS_PER_SLOT);
        HashSetByIdentity seen = new HashSetByIdentity();
        if (actionIds != null) {
            for (int i = 0; i < actionIds.length
                    && matched.size() < PonyScenes.MAX_ACTIONS_PER_SLOT; i++) {
                String id = actionIds[i];
                if (id == null || id.length() == 0) continue;
                // Only selectable catalog ids (drops drag / teleport / unknown).
                if (!catalog.selectableIds.contains(id)) continue;
                PonyAction action = catalog.byId.get(id);
                if (action == null) continue;
                if (seen.add(action)) matched.add(action);
            }
        }
        if (matched.isEmpty()) {
            PonyAction preferred = AllPonies.preferredDefaultAction(catalog);
            if (preferred == null) return null;
            return new PonyAction[] { preferred };
        }
        return matched.toArray(new PonyAction[matched.size()]);
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
