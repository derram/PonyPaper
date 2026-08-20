package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * A {@link android.preference.Preference} that picks an opaque RGB colour.
 * Persists a packed ARGB int with alpha forced to {@code 0xFF}.
 */
public class ColorPreference extends DialogPreference {

    private static final int DEFAULT_COLOUR = PonySceneController.DEFAULT_BACKGROUND_COLOUR;

    private static final int[] PRESET_COLOURS = {
            0xff000000,
            0xff333333,
            0xff1a2744,
            0xff1e3a2f,
            0xffe27aa5,
            0xffffffff
    };

    private static final int[] PRESET_SWATCH_IDS = {
            R.id.color_swatch_0,
            R.id.color_swatch_1,
            R.id.color_swatch_2,
            R.id.color_swatch_3,
            R.id.color_swatch_4,
            R.id.color_swatch_5
    };

    private static final int[] PRESET_LABEL_IDS = {
            R.string.pref_background_colour_swatch_black,
            R.string.pref_background_colour_swatch_grey,
            R.string.pref_background_colour_swatch_navy,
            R.string.pref_background_colour_swatch_forest,
            R.string.pref_background_colour_swatch_pink,
            R.string.pref_background_colour_swatch_white
    };

    private int value = DEFAULT_COLOUR;
    private int dialogColour = DEFAULT_COLOUR;
    private boolean updatingSliders;

    private View preview;
    private TextView hexLabel;
    private SeekBar redBar;
    private SeekBar greenBar;
    private SeekBar blueBar;
    private final View[] swatches = new View[PRESET_SWATCH_IDS.length];

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
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        bindChip(view.findViewById(R.id.pref_color_swatch), value, false);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        dialogColour = value;
        preview = view.findViewById(R.id.color_preview);
        hexLabel = (TextView) view.findViewById(R.id.color_hex);
        redBar = (SeekBar) view.findViewById(R.id.color_red);
        greenBar = (SeekBar) view.findViewById(R.id.color_green);
        blueBar = (SeekBar) view.findViewById(R.id.color_blue);

        SeekBar.OnSeekBarChangeListener sliders = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updatingSliders) return;
                dialogColour = opaque(
                        (redBar.getProgress() << 16)
                                | (greenBar.getProgress() << 8)
                                | blueBar.getProgress());
                refreshDialogChrome();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        redBar.setOnSeekBarChangeListener(sliders);
        greenBar.setOnSeekBarChangeListener(sliders);
        blueBar.setOnSeekBarChangeListener(sliders);

        for (int i = 0; i < PRESET_SWATCH_IDS.length; i++) {
            final int colour = PRESET_COLOURS[i];
            View swatch = view.findViewById(PRESET_SWATCH_IDS[i]);
            swatches[i] = swatch;
            if (swatch == null) continue;
            swatch.setContentDescription(getContext().getString(PRESET_LABEL_IDS[i]));
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialogColour = opaque(colour);
                    applySlidersFromColour();
                    refreshDialogChrome();
                }
            });
        }
        applySlidersFromColour();
        refreshDialogChrome();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        preview = null;
        hexLabel = null;
        redBar = null;
        greenBar = null;
        blueBar = null;
        for (int i = 0; i < swatches.length; i++) {
            swatches[i] = null;
        }
        if (positiveResult && callChangeListener(dialogColour)) {
            setValue(dialogColour);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return opaque(a.getInt(index, DEFAULT_COLOUR));
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        int fallback = DEFAULT_COLOUR;
        if (defaultValue instanceof Integer) {
            fallback = opaque((Integer) defaultValue);
        }
        setValue(restorePersistedValue ? getPersistedInt(fallback) : fallback);
    }

    private void setValue(int newValue) {
        value = opaque(newValue);
        persistInt(value);
        setSummary(formatHex(value));
        notifyChanged();
    }

    private void applySlidersFromColour() {
        if (redBar == null || greenBar == null || blueBar == null) return;
        updatingSliders = true;
        redBar.setProgress((dialogColour >> 16) & 0xff);
        greenBar.setProgress((dialogColour >> 8) & 0xff);
        blueBar.setProgress(dialogColour & 0xff);
        updatingSliders = false;
    }

    private void refreshDialogChrome() {
        bindChip(preview, dialogColour, false);
        if (hexLabel != null) {
            hexLabel.setText(formatHex(dialogColour));
        }
        for (int i = 0; i < swatches.length; i++) {
            if (swatches[i] == null) continue;
            bindChip(swatches[i], PRESET_COLOURS[i], opaque(PRESET_COLOURS[i]) == dialogColour);
        }
    }

    private void bindChip(View view, int colour, boolean selected) {
        if (view == null) return;
        int opaque = opaque(colour);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(opaque);
        float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f,
                getContext().getResources().getDisplayMetrics());
        gd.setCornerRadius(radius);
        float strokeDp = selected ? 3f : 1f;
        int strokePx = Math.max(1, Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, strokeDp,
                getContext().getResources().getDisplayMetrics())));
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
