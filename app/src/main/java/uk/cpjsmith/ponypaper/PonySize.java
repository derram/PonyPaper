package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import java.util.Random;

/**
 * Runtime character-size preference. Multiplies {@link Pony}'s short-side scale
 * so ponies can be smaller on large tablets without a second sprite set.
 *
 * <p>On first launch (key unset) the default is {@link #TABLET_FIRST_RUN_PERCENT}
 * when {@code smallestScreenWidthDp >=} {@link #TABLET_SMALLEST_WIDTH_DP},
 * otherwise {@link #DEFAULT_PERCENT}. Call {@link #ensureDefault(Context)}
 * before {@link PrefDefaults#apply} or inflating preferences so
 * the XML {@code 100} default does not win on tablets.
 */
final class PonySize {

    static final String PREF_KEY = "pref_pony_size";

    /** Original {@code min(w,h)/200} scale. */
    static final int DEFAULT_PERCENT = 100;
    /** First-run default on tablet-class screens. */
    static final int TABLET_FIRST_RUN_PERCENT = 50;
    /** Android's usual tablet smallest-width breakpoint. */
    static final int TABLET_SMALLEST_WIDTH_DP = 600;

    private static final int[] ALLOWED = {25, 50, 75, 100, 125, 150};

    private PonySize() {}

    /** Uniform pick from the Character size ladder, as a linear multiplier. */
    static float randomFactor(Random random) {
        if (random == null) return DEFAULT_PERCENT / 100.0f;
        return ALLOWED[random.nextInt(ALLOWED.length)] / 100.0f;
    }

    /**
     * Writes the first-run default if the user has never chosen a size.
     * Uses {@code commit()} so a following {@code setDefaultValues} sees the key.
     */
    static void ensureDefault(Context context) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.contains(PREF_KEY)) return;
        prefs.edit().putString(PREF_KEY, Integer.toString(defaultPercent(context))).commit();
    }

    /** {@link #TABLET_FIRST_RUN_PERCENT} on sw600dp+, else {@link #DEFAULT_PERCENT}. */
    static int defaultPercent(Context context) {
        if (context == null) return DEFAULT_PERCENT;
        int sw = context.getResources().getConfiguration().smallestScreenWidthDp;
        return sw >= TABLET_SMALLEST_WIDTH_DP ? TABLET_FIRST_RUN_PERCENT : DEFAULT_PERCENT;
    }

    /** Linear multiplier for {@link Pony} drawing and motion. */
    static float factor(SharedPreferences prefs) {
        return parsePercent(prefs != null ? prefs.getString(PREF_KEY, null) : null)
                / 100.0f;
    }

    static int parsePercent(String raw) {
        if (raw == null) return DEFAULT_PERCENT;
        try {
            int value = Integer.parseInt(raw.trim());
            for (int i = 0; i < ALLOWED.length; i++) {
                if (ALLOWED[i] == value) return value;
            }
        } catch (NumberFormatException ignored) {
        }
        return DEFAULT_PERCENT;
    }
}
