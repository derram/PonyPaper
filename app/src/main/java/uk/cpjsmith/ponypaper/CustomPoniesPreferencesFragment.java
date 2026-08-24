package uk.cpjsmith.ponypaper;

import android.os.Bundle;

public class CustomPoniesPreferencesFragment extends PonyPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_ponies_custom, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        settings().bindCustomPoniesPreferences(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Settings settings = settings();
        settings.setTitle(R.string.pref_screen_custom_title);
        settings.refreshCustomPoniesScreen();
    }
}
