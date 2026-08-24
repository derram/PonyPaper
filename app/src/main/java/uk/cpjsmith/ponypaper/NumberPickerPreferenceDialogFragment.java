package uk.cpjsmith.ponypaper;

import android.os.Bundle;
import android.view.View;
import android.widget.NumberPicker;
import androidx.preference.PreferenceDialogFragmentCompat;

public class NumberPickerPreferenceDialogFragment extends PreferenceDialogFragmentCompat {

    private NumberPicker picker;

    public static NumberPickerPreferenceDialogFragment newInstance(String key) {
        NumberPickerPreferenceDialogFragment fragment = new NumberPickerPreferenceDialogFragment();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        NumberPickerPreference preference = (NumberPickerPreference) getPreference();
        picker = view.findViewById(R.id.number_picker);
        picker.setMinValue(preference.getMinValue());
        picker.setMaxValue(preference.getMaxValue());
        picker.setWrapSelectorWheel(preference.getWrapSelectorWheel());
        picker.setValue(preference.getValue());
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (!positiveResult || picker == null) return;
        NumberPickerPreference preference = (NumberPickerPreference) getPreference();
        int newValue = picker.getValue();
        if (preference.callChangeListener(newValue)) {
            preference.setValue(newValue);
        }
    }
}
