package uk.cpjsmith.ponypaper;

import android.os.Bundle;

public class PoniesPreferencesFragment extends PonyPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_ponies, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        settings().bindPoniesPreferences(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Settings settings = settings();
        settings.setTitle(R.string.pref_screen_ponies_title);
        settings.refreshPoniesScreen();
    }
}
