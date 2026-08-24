package uk.cpjsmith.ponypaper;

import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.PreferenceDialogFragmentCompat;

public class ColorPreferenceDialogFragment extends PreferenceDialogFragmentCompat {

    private int dialogColour = ColorPreference.DEFAULT_COLOUR;
    private boolean updatingSliders;

    private View preview;
    private TextView hexLabel;
    private SeekBar redBar;
    private SeekBar greenBar;
    private SeekBar blueBar;
    private final View[] swatches = new View[ColorPreference.PRESET_SWATCH_IDS.length];

    public static ColorPreferenceDialogFragment newInstance(String key) {
        ColorPreferenceDialogFragment fragment = new ColorPreferenceDialogFragment();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        ColorPreference preference = (ColorPreference) getPreference();
        dialogColour = preference.getValue();
        preview = view.findViewById(R.id.color_preview);
        hexLabel = view.findViewById(R.id.color_hex);
        redBar = view.findViewById(R.id.color_red);
        greenBar = view.findViewById(R.id.color_green);
        blueBar = view.findViewById(R.id.color_blue);

        SeekBar.OnSeekBarChangeListener sliders = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updatingSliders) return;
                dialogColour = ColorPreference.opaque(
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

        for (int i = 0; i < ColorPreference.PRESET_SWATCH_IDS.length; i++) {
            final int colour = ColorPreference.PRESET_COLOURS[i];
            View swatch = view.findViewById(ColorPreference.PRESET_SWATCH_IDS[i]);
            swatches[i] = swatch;
            if (swatch == null) continue;
            swatch.setContentDescription(getString(ColorPreference.PRESET_LABEL_IDS[i]));
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialogColour = ColorPreference.opaque(colour);
                    applySlidersFromColour();
                    refreshDialogChrome();
                }
            });
        }
        applySlidersFromColour();
        refreshDialogChrome();
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        preview = null;
        hexLabel = null;
        redBar = null;
        greenBar = null;
        blueBar = null;
        for (int i = 0; i < swatches.length; i++) {
            swatches[i] = null;
        }
        if (!positiveResult) return;
        ColorPreference preference = (ColorPreference) getPreference();
        if (preference.callChangeListener(dialogColour)) {
            preference.setValue(dialogColour);
        }
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
        ColorPreference.bindChip(preview, dialogColour, false);
        if (hexLabel != null) {
            hexLabel.setText(ColorPreference.formatHex(dialogColour));
        }
        for (int i = 0; i < swatches.length; i++) {
            if (swatches[i] == null) continue;
            ColorPreference.bindChip(swatches[i], ColorPreference.PRESET_COLOURS[i],
                    ColorPreference.opaque(ColorPreference.PRESET_COLOURS[i]) == dialogColour);
        }
    }
}
