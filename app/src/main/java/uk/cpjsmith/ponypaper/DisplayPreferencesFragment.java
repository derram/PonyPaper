package uk.cpjsmith.ponypaper;

import android.os.Bundle;

public class DisplayPreferencesFragment extends PonyPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_display, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        settings().bindDisplayPreferences(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Settings settings = settings();
        settings.setTitle(R.string.pref_screen_display_title);
        settings.refreshDisplayScreen();
    }
}
