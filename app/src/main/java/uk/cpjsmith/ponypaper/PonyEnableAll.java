package uk.cpjsmith.ponypaper;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bulk enable/disable for a group of pony checkboxes, with a snapshot so
 * Disable all can be undone without forcing every pony on.
 *
 * <p>Live {@code pref_*} booleans stay the source of truth for
 * {@link AllPonies#getPonies}. The snapshot is only consulted by the
 * settings toggle.
 */
final class PonyEnableAll {

    static final String PREF_PONIES_SNAPSHOT = "pref_ponies_snapshot";
    static final String PREF_CUSTOM_SNAPSHOT = "pref_custom_snapshot";

    enum Action {
        DISABLE_ALL,
        RESTORE_PREVIOUS,
        ENABLE_ALL
    }

    private PonyEnableAll() {}

    /**
     * Next tap: disable when anything is on; otherwise restore a still-valid
     * snapshot, or enable every current key.
     */
    static Action nextAction(SharedPreferences prefs, List<String> keys, String snapshotKey) {
        if (anyEnabled(prefs, keys)) return Action.DISABLE_ALL;
        if (restoreCount(prefs, keys, snapshotKey) > 0) return Action.RESTORE_PREVIOUS;
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
     * How many current keys would turn on if the snapshot were restored.
     * Stale names (deleted custom files) are ignored.
     */
    static int restoreCount(SharedPreferences prefs, List<String> keys, String snapshotKey) {
        Set<String> snap = snapshotOf(prefs, snapshotKey);
        if (snap.isEmpty() || keys == null) return 0;
        int n = 0;
        for (int i = 0; i < keys.size(); i++) {
            if (snap.contains(keys.get(i))) n++;
        }
        return n;
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
     * Apply {@link #nextAction} in one commit. Disable all overwrites the
     * snapshot only when at least one key is currently on. Restore turns
     * snapshot keys on and leaves other current keys unchanged.
     */
    static void apply(SharedPreferences prefs, List<String> keys, String snapshotKey) {
        if (prefs == null || keys == null || keys.isEmpty() || snapshotKey == null) return;
        Action action = nextAction(prefs, keys, snapshotKey);
        SharedPreferences.Editor editor = prefs.edit();
        switch (action) {
            case DISABLE_ALL:
                HashSet<String> enabled = enabledKeys(prefs, keys);
                if (!enabled.isEmpty()) {
                    editor.putStringSet(snapshotKey, enabled);
                }
                for (int i = 0; i < keys.size(); i++) {
                    editor.putBoolean(keys.get(i), false);
                }
                break;
            case RESTORE_PREVIOUS:
                Set<String> snap = snapshotOf(prefs, snapshotKey);
                for (int i = 0; i < keys.size(); i++) {
                    String key = keys.get(i);
                    if (snap.contains(key)) {
                        editor.putBoolean(key, true);
                    }
                }
                break;
            case ENABLE_ALL:
                for (int i = 0; i < keys.size(); i++) {
                    editor.putBoolean(keys.get(i), true);
                }
                break;
        }
        editor.commit();
    }

    /**
     * Drop one pony key from a snapshot in {@code editor}. No-op when the key
     * is not in the set. Callers must {@code commit} the editor.
     */
    static void removeKeyFromSnapshot(SharedPreferences prefs, SharedPreferences.Editor editor,
            String snapshotKey, String prefKey) {
        if (prefs == null || editor == null || snapshotKey == null || prefKey == null) return;
        Set<String> snap = prefs.getStringSet(snapshotKey, null);
        if (snap == null || !snap.contains(prefKey)) return;
        HashSet<String> next = new HashSet<String>(snap);
        next.remove(prefKey);
        if (next.isEmpty()) {
            editor.remove(snapshotKey);
        } else {
            editor.putStringSet(snapshotKey, next);
        }
    }

    private static HashSet<String> enabledKeys(SharedPreferences prefs, List<String> keys) {
        HashSet<String> on = new HashSet<String>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (prefs.getBoolean(key, true)) on.add(key);
        }
        return on;
    }

    private static Set<String> snapshotOf(SharedPreferences prefs, String snapshotKey) {
        Set<String> snap = prefs.getStringSet(snapshotKey, null);
        if (snap == null || snap.isEmpty()) return Collections.emptySet();
        return snap;
    }
}
