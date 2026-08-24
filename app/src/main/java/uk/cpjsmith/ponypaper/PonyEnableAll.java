package uk.cpjsmith.ponypaper;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bulk enable/disable for a group of pony checkboxes.
 *
 * <p>Live {@code pref_*} booleans stay the source of truth for
 * {@link AllPonies#getPonies}. Remembering a selection for later is Mix's
 * job ({@link PonyMixes} named mixes and previous herd).
 */
final class PonyEnableAll {

    enum Action {
        DISABLE_ALL,
        ENABLE_ALL
    }

    private PonyEnableAll() {}

    /** Next tap: disable when anything is on; otherwise enable every key. */
    static Action nextAction(SharedPreferences prefs, List<String> keys) {
        if (anyEnabled(prefs, keys)) return Action.DISABLE_ALL;
        return Action.ENABLE_ALL;
    }

    static boolean anyEnabled(SharedPreferences prefs, List<String> keys) {
        if (keys == null) return false;
        for (int i = 0; i < keys.size(); i++) {
            if (prefs.getBoolean(keys.get(i), true)) return true;
        }
        return false;
    }

    /**
     * How many of {@code keys} appear in {@code enabled}. Stale names are
     * ignored because they are simply not in {@code keys}.
     */
    static int matchingCount(List<String> keys, Set<String> enabled) {
        if (keys == null || enabled == null || enabled.isEmpty()) return 0;
        int n = 0;
        for (int i = 0; i < keys.size(); i++) {
            if (enabled.contains(keys.get(i))) n++;
        }
        return n;
    }

    /** Currently-on keys among {@code keys}. Missing prefs default on. */
    static HashSet<String> capture(SharedPreferences prefs, List<String> keys) {
        return enabledKeys(prefs, keys);
    }

    /**
     * Set every key in {@code keys} on iff it is in {@code enabled}. One
     * commit. Used by named mixes.
     */
    static void applyReplace(SharedPreferences prefs, List<String> keys, Set<String> enabled) {
        if (prefs == null || keys == null || keys.isEmpty()) return;
        PonyMixes.beginProgrammaticHerdChange();
        try {
            SharedPreferences.Editor editor = prefs.edit();
            writeReplace(editor, keys, enabled);
            editor.commit();
        } finally {
            PonyMixes.endProgrammaticHerdChange();
        }
    }

    static void writeReplace(SharedPreferences.Editor editor, List<String> keys, Set<String> enabled) {
        if (editor == null || keys == null) return;
        Set<String> on = enabled != null ? enabled : Collections.<String>emptySet();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            editor.putBoolean(key, on.contains(key));
        }
    }

    /**
     * Default for a newly seen custom pony. An empty library starts on; a
     * muted library (every existing custom checkbox off) stays muted.
     */
    static boolean defaultNewCustomEnabled(SharedPreferences prefs, List<String> existingCustomKeys) {
        if (existingCustomKeys == null || existingCustomKeys.isEmpty()) return true;
        return anyEnabled(prefs, existingCustomKeys);
    }

    /**
     * Apply {@link #nextAction} in one commit. Disable all stashes the live
     * home herd into Mix's previous-herd (when {@code herdKeys} is provided)
     * before clearing the group.
     */
    static void apply(SharedPreferences prefs, List<String> keys, List<String> herdKeys) {
        if (prefs == null || keys == null || keys.isEmpty()) return;
        Action action = nextAction(prefs, keys);
        PonyMixes.beginProgrammaticHerdChange();
        try {
            SharedPreferences.Editor editor = prefs.edit();
            switch (action) {
                case DISABLE_ALL:
                    PonyMixes.writePreviousHerdIfHome(prefs, editor, herdKeys);
                    for (int i = 0; i < keys.size(); i++) {
                        editor.putBoolean(keys.get(i), false);
                    }
                    break;
                case ENABLE_ALL:
                    for (int i = 0; i < keys.size(); i++) {
                        editor.putBoolean(keys.get(i), true);
                    }
                    break;
            }
            editor.commit();
        } finally {
            PonyMixes.endProgrammaticHerdChange();
        }
    }

    private static HashSet<String> enabledKeys(SharedPreferences prefs, List<String> keys) {
        HashSet<String> on = new HashSet<String>();
        if (prefs == null || keys == null) return on;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (prefs.getBoolean(key, true)) on.add(key);
        }
        return on;
    }
}
