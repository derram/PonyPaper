package uk.cpjsmith.ponypaper;

import android.os.Bundle;

public class LicensesPreferencesFragment extends PonyPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_licenses, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        settings().bindLicensesPreferences(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        settings().setTitle(R.string.pref_licenses_title);
    }
}
