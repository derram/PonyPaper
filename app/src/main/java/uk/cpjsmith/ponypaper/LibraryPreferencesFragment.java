package uk.cpjsmith.ponypaper;

import android.os.Bundle;

public class LibraryPreferencesFragment extends PonyPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_library, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        settings().bindLibraryPreferences(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Settings settings = settings();
        settings.setTitle(R.string.pref_screen_library_title);
        settings.refreshLibraryScreen();
    }
}
