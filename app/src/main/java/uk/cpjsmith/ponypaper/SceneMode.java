package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

/**
 * Wallpaper scene-mode preference. Replaces the old boolean
 * {@code pref_drunk_mode} with a string enum so additional modes can be
 * added without stacking checkboxes.
 *
 * <p>Call {@link #migrate(Context)} before {@link PrefDefaults#apply} so a
 * prior Berry Punch toggle is preserved and the XML default does not win.
 */
final class SceneMode {

    static final String PREF_KEY = "pref_scene_mode";
    /** Legacy checkbox; removed after {@link #migrate(Context)}. */
    static final String LEGACY_PREF_KEY = "pref_drunk_mode";

    static final String WANDER = "wander";
    static final String BERRY_PUNCH = "berry_punch";

    private SceneMode() {}

    /**
     * Copies {@link #LEGACY_PREF_KEY} into {@link #PREF_KEY} when needed, then
     * drops the boolean. Uses {@code commit()} so a following
     * {@code setDefaultValues} sees the new key.
     */
    static void migrate(Context context) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        boolean changed = false;

        if (!prefs.contains(PREF_KEY) && prefs.contains(LEGACY_PREF_KEY)) {
            boolean drunk = false;
            try {
                drunk = prefs.getBoolean(LEGACY_PREF_KEY, false);
            } catch (ClassCastException ignored) {
            }
            edit.putString(PREF_KEY, drunk ? BERRY_PUNCH : WANDER);
            changed = true;
        }

        if (prefs.contains(LEGACY_PREF_KEY)) {
            edit.remove(LEGACY_PREF_KEY);
            changed = true;
        }

        if (changed) {
            edit.commit();
        }
    }

    /** Stored mode string, or {@link #WANDER} when unset / unknown. */
    static String mode(SharedPreferences prefs) {
        if (prefs == null) return WANDER;
        String raw;
        try {
            raw = prefs.getString(PREF_KEY, WANDER);
        } catch (ClassCastException e) {
            return WANDER;
        }
        if (BERRY_PUNCH.equals(raw)) return BERRY_PUNCH;
        return WANDER;
    }

    /** True when Berry Punch translucent / fade rendering is active. */
    static boolean isBerryPunch(SharedPreferences prefs) {
        return BERRY_PUNCH.equals(mode(prefs));
    }
}
