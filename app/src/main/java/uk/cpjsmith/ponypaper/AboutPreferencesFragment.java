package uk.cpjsmith.ponypaper;

import android.os.Bundle;

public class AboutPreferencesFragment extends PonyPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_about, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        settings().bindAboutPreferences(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        settings().setTitle(R.string.pref_about_category);
    }
}
