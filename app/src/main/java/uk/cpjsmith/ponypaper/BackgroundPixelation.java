package uk.cpjsmith.ponypaper;

/**
 * Host-specific background pixelation. The wallpaper default stays chunky so
 * sprites match; the screen saver defaults to full fitted resolution.
 *
 * <p>Level {@code N} decodes the image at {@code fitted dest / N}. The frame
 * loop nearest-neighbour stretches that bitmap; it is not upsampled back to
 * the surface.
 */
public final class BackgroundPixelation {

    /** Live wallpaper {@link android.content.SharedPreferences} key. */
    public static final String PREF_WALLPAPER = "pref_pixelation";
    /** Dream {@link android.content.SharedPreferences} key. */
    public static final String PREF_DREAM = "pref_dream_pixelation";
    /** Matches {@code pref_pixelation} default in {@code pref_display.xml}. */
    public static final int DEFAULT_WALLPAPER = 4;
    /** Matches {@code pref_dream_pixelation} default in {@code pref_display.xml}. */
    public static final int DEFAULT_DREAM = 1;
    public static final int MIN = 1;
    public static final int MAX = 24;

    private BackgroundPixelation() {}

    public static int defaultLevel(boolean isDream) {
        return isDream ? DEFAULT_DREAM : DEFAULT_WALLPAPER;
    }

    public static int clamp(int pixelation) {
        if (pixelation < MIN) return MIN;
        if (pixelation > MAX) return MAX;
        return pixelation;
    }

    /**
     * {@code stored} from prefs, or the host default when the key is missing
     * ({@code null}).
     */
    public static int fromStored(Integer stored, boolean isDream) {
        if (stored == null) return defaultLevel(isDream);
        return clamp(stored);
    }

    /** Decode width/height: {@code fitted / pixelation}, at least 1. */
    public static int targetEdge(int fitted, int pixelation) {
        return Math.max(1, fitted / clamp(pixelation));
    }
}
