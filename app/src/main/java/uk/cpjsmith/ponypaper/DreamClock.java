package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.format.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Everyday Clock–style digital overlay for the screensaver: large time and an
 * optional date line, sized relative to the surface so it stays readable on
 * phones and tablets without covering the whole herd.
 *
 * <p>Type scales with the short side of the canvas, with a larger fraction in
 * landscape so the clock reads bigger when the device is docked horizontally.
 *
 * <p>Position drifts slowly in a Lissajous path so the same pixels are not lit
 * for hours on a docked OLED.
 */
final class DreamClock {

    /** Soft white; remains legible when the dream is dimmed. */
    private static final int TEXT_COLOR = 0xE6FFFFFF;
    private static final int SHADOW_COLOR = 0x99000000;

    /**
     * Horizontal drift period. Incommensurate with {@link #DRIFT_PERIOD_Y_MS}
     * so the path does not retrace a simple line or small loop.
     */
    private static final long DRIFT_PERIOD_X_MS = 97_000;
    private static final long DRIFT_PERIOD_Y_MS = 139_000;
    /** Max offset from rest as a fraction of width / height (±). */
    private static final float DRIFT_AMPLITUDE_X = 0.06f;
    private static final float DRIFT_AMPLITUDE_Y = 0.05f;

    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Calendar calendar = Calendar.getInstance();

    private SimpleDateFormat timeFormat;
    private SimpleDateFormat dateFormat;
    private boolean last24Hour = false;
    private Locale lastLocale = null;
    private int lastWidth = 0;
    private int lastHeight = 0;
    /**
     * Layout offsets computed when type size changes (integer pixels). Fixed so
     * time and date stay a rigid block as the drift path crosses subpixel
     * boundaries — independent glyph rounding used to make the date lead, then
     * snap back under the time.
     */
    private float timeBaselineFromCy = 0f;
    private float dateBaselineOffset = 0f;

    DreamClock() {
        timePaint.setColor(TEXT_COLOR);
        timePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        timePaint.setShadowLayer(8f, 0f, 3f, SHADOW_COLOR);

        datePaint.setColor(TEXT_COLOR);
        datePaint.setTextAlign(Paint.Align.CENTER);
        datePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        datePaint.setShadowLayer(4f, 0f, 2f, SHADOW_COLOR);
    }

    /**
     * Draws the clock near centre on {@code canvas}, with a slow position drift.
     * Call after the scene so the digits sit above ponies and the background.
     */
    void draw(Canvas canvas, Context context, boolean showDate) {
        if (canvas == null || context == null) return;
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;

        ensureFormats(context, width, height);

        calendar.setTimeInMillis(System.currentTimeMillis());
        String timeText = timeFormat.format(calendar.getTime());
        String dateText = showDate ? dateFormat.format(calendar.getTime()) : null;

        // Rest slightly above vertical centre so legs/hooves still have room below.
        // Slow Lissajous drift keeps the same pixels from burning in on OLED docks.
        long t = SystemClock.uptimeMillis();
        double phaseX = (2.0 * Math.PI * (t % DRIFT_PERIOD_X_MS)) / DRIFT_PERIOD_X_MS;
        double phaseY = (2.0 * Math.PI * (t % DRIFT_PERIOD_Y_MS)) / DRIFT_PERIOD_Y_MS;
        float cx = width * 0.5f
                + (float) Math.sin(phaseX) * width * DRIFT_AMPLITUDE_X;
        // Cosine on Y (plus a fixed phase) fills a 2D region, not a diagonal line.
        float cy = height * 0.42f
                + (float) Math.cos(phaseY + Math.PI * 0.25) * height * DRIFT_AMPLITUDE_Y;

        // Pixel-align the shared origin so time and date stay a rigid block.
        // Without this, different type sizes round subpixel positions independently
        // and the date appears to lead the drift, then snap back.
        float cxPx = Math.round(cx);
        float timeBaseline = Math.round(cy + timeBaselineFromCy);

        canvas.drawText(timeText, cxPx, timeBaseline, timePaint);

        if (dateText != null) {
            canvas.drawText(dateText, cxPx, timeBaseline + dateBaselineOffset, datePaint);
        }
    }

    private void ensureFormats(Context context, int width, int height) {
        boolean is24 = DateFormat.is24HourFormat(context);
        Locale locale = Locale.getDefault();
        boolean sizeChanged = width != lastWidth || height != lastHeight;
        boolean formatChanged = timeFormat == null
                || is24 != last24Hour
                || !locale.equals(lastLocale);

        if (formatChanged) {
            last24Hour = is24;
            lastLocale = locale;
            String timePattern = is24 ? "HH:mm" : "h:mm";
            timeFormat = new SimpleDateFormat(timePattern, locale);
            // e.g. "Monday, 3 August"
            dateFormat = new SimpleDateFormat("EEEE, d MMMM", locale);
        }

        if (formatChanged || sizeChanged) {
            lastWidth = width;
            lastHeight = height;
            // Size relative to the short side so type stays proportional on
            // phones and tablets. Landscape uses a larger fraction so the
            // clock fills more of the docked / horizontal view.
            float shortSide = Math.min(width, height);
            boolean landscape = width > height;
            float timeFraction = landscape ? 0.30f : 0.18f;
            float dateFraction = landscape ? 0.075f : 0.045f;
            float timeSize = shortSide * timeFraction;
            float dateSize = shortSide * dateFraction;
            timePaint.setTextSize(timeSize);
            datePaint.setTextSize(dateSize);
            // Shadow scales with type so large landscape digits keep a soft edge.
            float timeShadow = Math.max(8f, timeSize * 0.04f);
            float dateShadow = Math.max(4f, dateSize * 0.06f);
            timePaint.setShadowLayer(timeShadow, 0f, timeShadow * 0.35f, SHADOW_COLOR);
            datePaint.setShadowLayer(dateShadow, 0f, dateShadow * 0.5f, SHADOW_COLOR);
            // Keep the time on one line (e.g. "12:59 PM" or "23:59").
            float maxTimeWidth = width * 0.85f;
            String sample = is24 ? "00:00" : "12:59 PM";
            float measured = timePaint.measureText(sample);
            if (measured > maxTimeWidth && measured > 0f) {
                float scale = maxTimeWidth / measured;
                timePaint.setTextSize(timeSize * scale);
                datePaint.setTextSize(dateSize * scale);
            }
            // Cache integer layout offsets (time centred on cy; date under time).
            Paint.FontMetrics timeFm = timePaint.getFontMetrics();
            Paint.FontMetrics dateFm = datePaint.getFontMetrics();
            timeBaselineFromCy = Math.round(
                    -(timeFm.ascent + timeFm.descent) * 0.5f);
            float gap = height * 0.02f;
            dateBaselineOffset = Math.round(
                    timeFm.descent - dateFm.ascent + gap);
        }
    }
}
