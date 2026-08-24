package uk.cpjsmith.ponypaper;

import android.os.Bundle;

public class RootPreferencesFragment extends PonyPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        settings().bindRootPreferences(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        settings().setTitle(R.string.app_settings_name);
    }
}
