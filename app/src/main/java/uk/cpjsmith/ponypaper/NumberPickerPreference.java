package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import androidx.preference.DialogPreference;

/**
 * A {@link DialogPreference} that displays a number picker as a dialog.
 */
public class NumberPickerPreference extends DialogPreference {

    private static final String XMLNS_CUSTOM = "http://cpjsmith.uk/ponypaper/custom";

    private int value;
    private int maxValue;
    private int minValue;
    private boolean wrapSelectorWheel;

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        commonInit(attrs);
    }

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        commonInit(attrs);
    }

    private void commonInit(AttributeSet attrs) {
        minValue = attrs.getAttributeIntValue(XMLNS_CUSTOM, "minValue", 1);
        maxValue = attrs.getAttributeIntValue(XMLNS_CUSTOM, "maxValue", 10);
        wrapSelectorWheel = attrs.getAttributeBooleanValue(XMLNS_CUSTOM, "wrap", true);
        setDialogLayoutResource(R.layout.number_picker_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, minValue);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        int fallback = minValue;
        if (defaultValue instanceof Integer) {
            fallback = (Integer) defaultValue;
        }
        setValue(getPersistedInt(fallback));
    }

    void setValue(int newValue) {
        value = newValue;
        persistInt(value);
        setSummary(Integer.toString(value));
    }

    int getValue() {
        return value;
    }

    int getMinValue() {
        return minValue;
    }

    int getMaxValue() {
        return maxValue;
    }

    boolean getWrapSelectorWheel() {
        return wrapSelectorWheel;
    }

    /** Reload after an external {@link android.content.SharedPreferences} write. */
    void reloadFromPersisted() {
        setValue(getPersistedInt(minValue));
    }
}
