package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

/**
 * Wallpaper / dream scene-mode preference. Replaces the old boolean
 * {@code pref_drunk_mode} with a string enum so additional modes can be
 * added without stacking checkboxes.
 *
 * <p>Wallpaper uses {@link #PREF_KEY}. The screen saver optionally overrides
 * via {@link #PREF_DREAM_KEY} ({@link #SAME} inherits the wallpaper mode).
 *
 * <p>Call {@link #migrate(Context)} before {@link PrefDefaults#apply} so a
 * prior Berry Punch toggle is preserved and the XML default does not win.
 */
final class SceneMode {

    static final String PREF_KEY = "pref_scene_mode";
    /**
     * Dream scene mode. {@link #SAME} (default) follows {@link #PREF_KEY};
     * otherwise one of the concrete mode values.
     */
    static final String PREF_DREAM_KEY = "pref_dream_scene_mode";
    /** Legacy checkbox; removed after {@link #migrate(Context)}. */
    static final String LEGACY_PREF_KEY = "pref_drunk_mode";

    /** Dream inherits the wallpaper scene mode. */
    static final String SAME = "same";
    static final String WANDER = "wander";
    static final String BERRY_PUNCH = "berry_punch";
    /** Each pony rolls a random size from the Character size ladder on enter. */
    static final String MY_QUESTION = "my_question";
    /** Fixed posed slots; herd checkboxes and num-ponies prefs are ignored. */
    static final String TABLEAU = "tableau";

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

    /** Wallpaper mode string, or {@link #WANDER} when unset / unknown. */
    static String mode(SharedPreferences prefs) {
        return normalizeConcrete(readString(prefs, PREF_KEY));
    }

    /**
     * Stored dream preference: {@link #SAME} or a concrete mode. Unknown /
     * missing values become {@link #SAME}.
     */
    static String dreamModePreference(SharedPreferences prefs) {
        String raw = readString(prefs, PREF_DREAM_KEY);
        if (raw == null || raw.length() == 0 || SAME.equals(raw)) return SAME;
        if (isConcrete(raw)) return raw;
        return SAME;
    }

    /**
     * Mode that applies on this host. Wallpaper always uses {@link #mode};
     * dream uses {@link #dreamModePreference} unless it is {@link #SAME}.
     */
    static String effectiveMode(SharedPreferences prefs, boolean isDream) {
        if (!isDream) return mode(prefs);
        String dream = dreamModePreference(prefs);
        if (SAME.equals(dream)) return mode(prefs);
        return dream;
    }

    /** True when the dream preference is {@link #SAME} (inherits wallpaper). */
    static boolean dreamFollowsWallpaper(SharedPreferences prefs) {
        return SAME.equals(dreamModePreference(prefs));
    }

    /** True when Berry Punch translucent / fade rendering is active. */
    static boolean isBerryPunch(SharedPreferences prefs) {
        return isBerryPunch(prefs, false);
    }

    static boolean isBerryPunch(SharedPreferences prefs, boolean isDream) {
        return BERRY_PUNCH.equals(effectiveMode(prefs, isDream));
    }

    /** True when each pony gets a random Character-size ladder step on enter. */
    static boolean isRandomSize(SharedPreferences prefs) {
        return isRandomSize(prefs, false);
    }

    static boolean isRandomSize(SharedPreferences prefs, boolean isDream) {
        return MY_QUESTION.equals(effectiveMode(prefs, isDream));
    }

    /** True when fixed Tableau poses are active (not wander / Berry / random-size). */
    static boolean isTableau(SharedPreferences prefs) {
        return isTableau(prefs, false);
    }

    static boolean isTableau(SharedPreferences prefs, boolean isDream) {
        return TABLEAU.equals(effectiveMode(prefs, isDream));
    }

    private static boolean isConcrete(String raw) {
        return WANDER.equals(raw)
                || BERRY_PUNCH.equals(raw)
                || MY_QUESTION.equals(raw)
                || TABLEAU.equals(raw);
    }

    private static String normalizeConcrete(String raw) {
        if (TABLEAU.equals(raw)) return TABLEAU;
        if (BERRY_PUNCH.equals(raw)) return BERRY_PUNCH;
        if (MY_QUESTION.equals(raw)) return MY_QUESTION;
        return WANDER;
    }

    private static String readString(SharedPreferences prefs, String key) {
        if (prefs == null) return null;
        try {
            return prefs.getString(key, null);
        } catch (ClassCastException e) {
            return null;
        }
    }
}
