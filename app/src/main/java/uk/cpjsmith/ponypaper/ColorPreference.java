package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceViewHolder;

/**
 * A {@link DialogPreference} that picks an opaque RGB colour.
 * Persists a packed ARGB int with alpha forced to {@code 0xFF}.
 */
public class ColorPreference extends DialogPreference {

    static final int DEFAULT_COLOUR = PonySceneController.DEFAULT_BACKGROUND_COLOUR;

    static final int[] PRESET_COLOURS = {
            0xff000000,
            0xff333333,
            0xff1a2744,
            0xff1e3a2f,
            0xffe27aa5,
            0xffffffff
    };

    static final int[] PRESET_SWATCH_IDS = {
            R.id.color_swatch_0,
            R.id.color_swatch_1,
            R.id.color_swatch_2,
            R.id.color_swatch_3,
            R.id.color_swatch_4,
            R.id.color_swatch_5
    };

    static final int[] PRESET_LABEL_IDS = {
            R.string.pref_background_colour_swatch_black,
            R.string.pref_background_colour_swatch_grey,
            R.string.pref_background_colour_swatch_navy,
            R.string.pref_background_colour_swatch_forest,
            R.string.pref_background_colour_swatch_pink,
            R.string.pref_background_colour_swatch_white
    };

    private int value = DEFAULT_COLOUR;

    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        commonInit();
    }

    public ColorPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        commonInit();
    }

    private void commonInit() {
        setDialogLayoutResource(R.layout.color_picker_dialog);
        setWidgetLayoutResource(R.layout.pref_color_widget);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        bindChip(holder.findViewById(R.id.pref_color_swatch), value, false);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return opaque(a.getInt(index, DEFAULT_COLOUR));
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        int fallback = DEFAULT_COLOUR;
        if (defaultValue instanceof Integer) {
            fallback = opaque((Integer) defaultValue);
        }
        setValue(getPersistedInt(fallback));
    }

    void setValue(int newValue) {
        value = opaque(newValue);
        persistInt(value);
        setSummary(formatHex(value));
        notifyChanged();
    }

    int getValue() {
        return value;
    }

    static void bindChip(View view, int colour, boolean selected) {
        if (view == null) return;
        int opaque = opaque(colour);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(opaque);
        float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f,
                view.getResources().getDisplayMetrics());
        gd.setCornerRadius(radius);
        float strokeDp = selected ? 3f : 1f;
        int strokePx = Math.max(1, Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, strokeDp,
                view.getResources().getDisplayMetrics())));
        int strokeColour;
        if (selected) {
            strokeColour = isLight(opaque) ? 0xff000000 : 0xffffffff;
        } else {
            strokeColour = 0x88999999;
        }
        gd.setStroke(strokePx, strokeColour);
        view.setBackground(gd);
    }

    static int opaque(int colour) {
        return 0xff000000 | (colour & 0x00ffffff);
    }

    static String formatHex(int colour) {
        return String.format("#%06X", colour & 0x00ffffff);
    }

    private static boolean isLight(int colour) {
        int r = (colour >> 16) & 0xff;
        int g = (colour >> 8) & 0xff;
        int b = colour & 0xff;
        return (r * 299 + g * 587 + b * 114) >= 160000;
    }
}
