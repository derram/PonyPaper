package uk.cpjsmith.ponypaper;

import android.os.Bundle;

public class DebugPreferencesFragment extends PonyPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_debug, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        settings().bindDebugPreferences(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        settings().setTitle(R.string.pref_screen_debug_title);
    }
}
