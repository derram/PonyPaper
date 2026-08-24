package uk.cpjsmith.ponypaper;

import android.os.Bundle;

public class BuiltInPoniesPreferencesFragment extends PonyPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_ponies_builtin, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        settings().bindBuiltInPoniesPreferences(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Settings settings = settings();
        settings.setTitle(R.string.pref_screen_builtin_title);
        settings.refreshBuiltInPoniesScreen();
    }
}
