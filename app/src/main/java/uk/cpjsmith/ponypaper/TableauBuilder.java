package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a Tableau herd: resolve slots, truncate to the live power/thermal
 * cap (document-order prefix), then {@link Ponies} with an empty inactive pool.
 * PR3 replaces the demo slots with JSON scene data.
 */
final class TableauBuilder {

    /** Hard ceiling on Tableau slots (matches the future scene schema). */
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
     * Demo/scratch Tableau herd truncated to {@code cap} before construction.
     *
     * @param cap from {@link #getTableauCap}; values below 0 are treated as 0
     */
    static Ponies build(Context context, SharedPreferences prefs, int cap) {
        ArrayList<Pony> slots = Ponies.createTableauDemoSlots(context);
        int kept = cap < 0 ? 0 : cap;
        if (kept > slots.size()) {
            kept = slots.size();
        }
        List<Pony> prefix = slots.subList(0, kept);
        return new Ponies(context, prefix, prefs);
    }
}
