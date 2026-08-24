package uk.cpjsmith.ponypaper;

import android.content.Context;
import androidx.preference.PreferenceManager;

/**
 * Applies XML default values for all preference screens that declare them.
 * Replaces the old single {@code R.xml.preferences} call after the hierarchy
 * was split for AndroidX {@code PreferenceFragmentCompat}.
 */
final class PrefDefaults {
    private PrefDefaults() {}

    static void apply(Context context) {
        PreferenceManager.setDefaultValues(context, R.xml.pref_display, true);
        PreferenceManager.setDefaultValues(context, R.xml.pref_ponies, true);
        PreferenceManager.setDefaultValues(context, R.xml.pref_library, true);
    }
}
