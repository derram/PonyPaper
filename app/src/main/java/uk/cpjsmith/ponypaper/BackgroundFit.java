package uk.cpjsmith.ponypaper;

/**
 * Uniform-scale layout for the wallpaper and dream background image.
 *
 * <p>{@code cover} matches {@code ImageView} {@code CENTER_CROP}: scale to fill
 * the canvas and crop overflow. {@code contain} matches {@code FIT_CENTER}:
 * show the whole image and letterbox with the base colour.
 *
 * <p>Contain snaps to cover when the unused bars would be thinner than
 * {@link #SNAP_BAR_FRACTION} of the long canvas edge, so 16:9 art on a 16:10
 * tablet fills instead of a thin letterbox.
 */
public final class BackgroundFit {

    /** Live wallpaper {@link android.content.SharedPreferences} key. */
    public static final String PREF_WALLPAPER = "pref_background_fit";
    /** Dream {@link android.content.SharedPreferences} key. */
    public static final String PREF_DREAM = "pref_dream_background_fit";
    public static final String COVER = "cover";
    public static final String CONTAIN = "contain";
    /** Snap contain → cover when unused bars are this fraction of the long edge. */
    public static final float SNAP_BAR_FRACTION = 0.08f;

    private BackgroundFit() {}

    /**
     * {@code true} to prefer contain. Missing/unknown values use contain on the
     * dream and cover on the wallpaper.
     */
    public static boolean wantContain(String stored, boolean isDream) {
        if (CONTAIN.equals(stored)) return true;
        if (COVER.equals(stored)) return false;
        return isDream;
    }

    /**
     * Cover, or contain after the thin-bar snap. {@code true} means fill the
     * canvas (crop if needed).
     */
    public static boolean useCover(boolean wantContain, int srcW, int srcH,
            int canvasW, int canvasH) {
        if (!wantContain) return true;
        return shouldSnapToCover(srcW, srcH, canvasW, canvasH);
    }

    /**
     * Unused contain bars as a fraction of {@code max(canvasW, canvasH)}.
     * Matching aspect (no bars) is {@code false} — that is not a snap.
     */
    public static boolean shouldSnapToCover(int srcW, int srcH, int canvasW, int canvasH) {
        if (srcW <= 0 || srcH <= 0 || canvasW <= 0 || canvasH <= 0) return false;
        float scale = containScale(srcW, srcH, canvasW, canvasH);
        int dstW = Math.max(1, Math.round(srcW * scale));
        int dstH = Math.max(1, Math.round(srcH * scale));
        int unused = Math.max(canvasW - dstW, canvasH - dstH);
        if (unused <= 0) return false;
        int longEdge = Math.max(canvasW, canvasH);
        return unused <= longEdge * SNAP_BAR_FRACTION;
    }

    public static float fittedScale(boolean cover, int srcW, int srcH,
            int canvasW, int canvasH) {
        if (srcW <= 0 || srcH <= 0 || canvasW <= 0 || canvasH <= 0) return 1f;
        float sx = (float) canvasW / (float) srcW;
        float sy = (float) canvasH / (float) srcH;
        return cover ? Math.max(sx, sy) : Math.min(sx, sy);
    }

    public static int fittedWidth(int srcW, int srcH, int canvasW, int canvasH,
            boolean wantContain) {
        return fittedSize(srcW, srcH, canvasW, canvasH, wantContain, true);
    }

    public static int fittedHeight(int srcW, int srcH, int canvasW, int canvasH,
            boolean wantContain) {
        return fittedSize(srcW, srcH, canvasW, canvasH, wantContain, false);
    }

    /**
     * Destination in canvas pixels. Contain (after snap) pins the image to the
     * centre so home-screen offsets cannot scoot letterboxes. Cover still uses
     * {@code xOffset}/{@code yOffset} as the crop origin.
     */
    public static void layout(int srcW, int srcH, int canvasW, int canvasH,
            boolean wantContain, float xOffset, float yOffset, Dest out) {
        if (out == null) return;
        if (srcW <= 0 || srcH <= 0 || canvasW <= 0 || canvasH <= 0) {
            out.set(0, 0, Math.max(1, canvasW), Math.max(1, canvasH));
            return;
        }
        boolean cover = useCover(wantContain, srcW, srcH, canvasW, canvasH);
        float scale = fittedScale(cover, srcW, srcH, canvasW, canvasH);
        int dstW = Math.max(1, Math.round(srcW * scale));
        int dstH = Math.max(1, Math.round(srcH * scale));
        float ox = cover ? xOffset : 0.5f;
        float oy = cover ? yOffset : 0.5f;
        int left = Math.round((canvasW - dstW) * ox);
        int top = Math.round((canvasH - dstH) * oy);
        out.set(left, top, left + dstW, top + dstH);
    }

    /**
     * Travel (± pixels) for OLED pan. Zero when {@code dest} matches
     * {@code canvas} — matching aspect is not over-zoomed.
     */
    public static int oledMaxShift(int canvas, int dest, float amplitude) {
        int slackHalf = Math.abs(dest - canvas) / 2;
        if (slackHalf < 1) return 0;
        int fromAmplitude = Math.round(canvas * amplitude);
        if (fromAmplitude < 1) return 0;
        return Math.min(slackHalf, fromAmplitude);
    }

    /**
     * Keep a cover dest covering {@code [0, canvas)}, or a contain dest fully
     * on-canvas. Matching size leaves {@code origin} unchanged.
     */
    public static int oledClampOrigin(int origin, int canvas, int dest) {
        int min;
        int max;
        if (dest > canvas) {
            min = canvas - dest;
            max = 0;
        } else if (dest < canvas) {
            min = 0;
            max = canvas - dest;
        } else {
            return origin;
        }
        if (origin < min) return min;
        if (origin > max) return max;
        return origin;
    }

    public static int oledShiftedOrigin(int origin, int canvas, int dest,
            float amplitude, double unit) {
        int max = oledMaxShift(canvas, dest, amplitude);
        if (max < 1) return oledClampOrigin(origin, canvas, dest);
        return oledClampOrigin(origin + (int) Math.round(unit * max), canvas, dest);
    }

    private static float containScale(int srcW, int srcH, int canvasW, int canvasH) {
        return Math.min((float) canvasW / (float) srcW, (float) canvasH / (float) srcH);
    }

    private static int fittedSize(int srcW, int srcH, int canvasW, int canvasH,
            boolean wantContain, boolean width) {
        if (srcW <= 0 || srcH <= 0) {
            return Math.max(1, width ? canvasW : canvasH);
        }
        if (canvasW <= 0 || canvasH <= 0) {
            return Math.max(1, width ? srcW : srcH);
        }
        boolean cover = useCover(wantContain, srcW, srcH, canvasW, canvasH);
        float scale = fittedScale(cover, srcW, srcH, canvasW, canvasH);
        int src = width ? srcW : srcH;
        return Math.max(1, Math.round(src * scale));
    }

    /** Integer destination rectangle (no Android {@code Rect}). */
    public static final class Dest {
        public int left;
        public int top;
        public int right;
        public int bottom;

        public void set(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int width() {
            return right - left;
        }

        public int height() {
            return bottom - top;
        }

        public void offsetTo(int newLeft, int newTop) {
            int w = width();
            int h = height();
            left = newLeft;
            top = newTop;
            right = newLeft + w;
            bottom = newTop + h;
        }

        public boolean fillsCanvas(int canvasW, int canvasH) {
            return left <= 0 && top <= 0 && right >= canvasW && bottom >= canvasH;
        }
    }
}
