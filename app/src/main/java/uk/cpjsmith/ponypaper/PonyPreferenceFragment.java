package uk.cpjsmith.ponypaper;

import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Shared dialog routing for custom {@link DialogPreference} subclasses.
 */
public abstract class PonyPreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment dialog = null;
        if (preference instanceof NumberPickerPreference) {
            dialog = NumberPickerPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof ColorPreference) {
            dialog = ColorPreferenceDialogFragment.newInstance(preference.getKey());
        }
        if (dialog != null) {
            // PreferenceDialogFragmentCompat still resolves the host via
            // getTargetFragment(); Fragment Result API is not wired here yet.
            showPreferenceDialog(dialog, preference.getKey());
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    @SuppressWarnings("deprecation")
    private void showPreferenceDialog(DialogFragment dialog, String tag) {
        dialog.setTargetFragment(this, 0);
        dialog.show(getParentFragmentManager(), tag);
    }

    protected Settings settings() {
        return (Settings) requireActivity();
    }
}
