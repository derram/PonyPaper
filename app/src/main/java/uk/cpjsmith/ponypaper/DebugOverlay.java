package uk.cpjsmith.ponypaper;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.DisplayMetrics;

/**
 * Debug-APK overlay: force hardware/software canvas path, plus live FPS /
 * frame-time / skip-rate and effective policy lines. All entry points no-op
 * when {@link BuildConfig#DEBUG} is false so release stays clean.
 */
final class DebugOverlay {

    static final String PREF_HUD = "pref_debug_hud";
    static final String PREF_RENDER_PATH = "pref_debug_render_path";

    static final String PATH_AUTO = "auto";
    static final String PATH_HARDWARE = "hardware";
    static final String PATH_SOFTWARE = "software";

    enum RenderPath {
        AUTO,
        HARDWARE,
        SOFTWARE
    }

    /** Per-frame / policy snapshot assembled by {@link PonySceneController}. */
    static final class Snapshot {
        String hostLabel = "?";
        String pathRequested = "auto";
        boolean pathActualHw;
        boolean canvasHwAccel;
        boolean cpuDemandHeld;
        boolean hwAllowed;
        float drawnFps;
        float scheduleFps;
        float skipPct;
        float avgDrawMs;
        float p95DrawMs;
        int preferredFps;
        int effectiveFps;
        int schedulePeriodMs;
        int preferredPonies;
        int effectivePonies;
        int livePonies;
        boolean preferredBg;
        boolean bgDisabled;
        boolean bgPresent;
        String caps;
        int surfaceW;
        int surfaceH;
        float displayHz;
        float surfaceFpsVote;
        boolean active;
        boolean frozen;
        boolean thermalEmergency;
        boolean thermalThrottle;
        int thermalStatus;
        boolean powerSave;
        boolean onBattery;
        boolean preview;
        boolean sceneLoading;
    }

    private static final long WINDOW_MS = 1000L;
    private static final int TIMING_CAP = 64;
    private static final float PAD_DP = 8f;
    private static final float TEXT_SP = 11f;
    private static final int BG_COLOR = 0x99000000;
    private static final int FG_COLOR = 0xffe0ffe0;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final long[] drawCostsNs = new long[TIMING_CAP];
    private int drawCostCount = 0;
    private int drawCostNext = 0;

    private long windowStartMs = 0L;
    private int windowSchedules = 0;
    private int windowDraws = 0;
    private int windowSkips = 0;

    private float lastDrawnFps = 0f;
    private float lastScheduleFps = 0f;
    private float lastSkipPct = 0f;
    private float lastAvgDrawMs = 0f;
    private float lastP95DrawMs = 0f;

    private final StringBuilder lineBuf = new StringBuilder(192);
    private final Snapshot reuse = new Snapshot();

    DebugOverlay() {
        textPaint.setColor(FG_COLOR);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextAlign(Paint.Align.LEFT);
        bgPaint.setColor(BG_COLOR);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    static boolean isDebugBuild() {
        return BuildConfig.DEBUG;
    }

    static boolean hudEnabled(SharedPreferences prefs) {
        return isDebugBuild() && prefs != null && prefs.getBoolean(PREF_HUD, true);
    }

    static RenderPath renderPath(SharedPreferences prefs) {
        if (!isDebugBuild() || prefs == null) return RenderPath.AUTO;
        String raw = prefs.getString(PREF_RENDER_PATH, PATH_AUTO);
        if (PATH_HARDWARE.equals(raw)) return RenderPath.HARDWARE;
        if (PATH_SOFTWARE.equals(raw)) return RenderPath.SOFTWARE;
        return RenderPath.AUTO;
    }

    static boolean isForceSoftware(SharedPreferences prefs) {
        return renderPath(prefs) == RenderPath.SOFTWARE;
    }

    static boolean isForceHardware(SharedPreferences prefs) {
        return renderPath(prefs) == RenderPath.HARDWARE;
    }

    static String pathLabel(RenderPath path) {
        switch (path) {
            case HARDWARE:
                return "force-HW";
            case SOFTWARE:
                return "force-SW";
            case AUTO:
            default:
                return "auto";
        }
    }

    static String thermalLabel(int status) {
        switch (status) {
            case 0:
                return "NONE";
            case 1:
                return "LIGHT";
            case 2:
                return "MODERATE";
            case 3:
                return "SEVERE";
            case 4:
                return "CRITICAL";
            case 5:
                return "EMERGENCY";
            case 6:
                return "SHUTDOWN";
            default:
                return Integer.toString(status);
        }
    }

    Snapshot snapshot() {
        return reuse;
    }

    void fillRates(Snapshot snap) {
        snap.drawnFps = lastDrawnFps;
        snap.scheduleFps = lastScheduleFps;
        snap.skipPct = lastSkipPct;
        snap.avgDrawMs = lastAvgDrawMs;
        snap.p95DrawMs = lastP95DrawMs;
    }

    /**
     * Record one scheduled frame tick.
     *
     * @param contentDirty true when ponies/bg/clock needed a redraw
     * @param posted true when a canvas was locked and unlocked
     * @param drawCostNs lock+draw+unlock cost when {@code posted}; else ignored
     */
    void recordSchedule(boolean contentDirty, boolean posted, long drawCostNs) {
        if (!isDebugBuild()) return;
        long now = SystemClock.uptimeMillis();
        if (windowStartMs == 0L) {
            windowStartMs = now;
        }
        windowSchedules++;
        if (posted) {
            windowDraws++;
            if (drawCostNs > 0L) {
                drawCostsNs[drawCostNext] = drawCostNs;
                drawCostNext = (drawCostNext + 1) % TIMING_CAP;
                if (drawCostCount < TIMING_CAP) drawCostCount++;
            }
        }
        if (!contentDirty) {
            windowSkips++;
        }
        long elapsed = now - windowStartMs;
        if (elapsed >= WINDOW_MS) {
            float secs = elapsed / 1000f;
            if (secs < 0.001f) secs = 0.001f;
            lastDrawnFps = windowDraws / secs;
            lastScheduleFps = windowSchedules / secs;
            lastSkipPct = windowSchedules > 0
                    ? (100f * windowSkips) / (float) windowSchedules
                    : 0f;
            lastAvgDrawMs = averageDrawMs();
            lastP95DrawMs = p95DrawMs();
            windowStartMs = now;
            windowSchedules = 0;
            windowDraws = 0;
            windowSkips = 0;
        }
    }

    void draw(Canvas c, Snapshot snap, DisplayMetrics metrics) {
        if (!isDebugBuild() || c == null || snap == null) return;
        float density = metrics != null ? metrics.density : 1f;
        float scaledDensity = metrics != null ? metrics.scaledDensity : density;
        float pad = PAD_DP * density;
        textPaint.setTextSize(TEXT_SP * scaledDensity);
        float lineHeight = textPaint.getFontSpacing();
        float x = pad;
        float y = pad - textPaint.ascent();

        String[] lines = buildLines(snap);
        float maxWidth = 0f;
        for (int i = 0; i < lines.length; i++) {
            float w = textPaint.measureText(lines[i]);
            if (w > maxWidth) maxWidth = w;
        }
        float boxH = lines.length * lineHeight + pad;
        float boxW = maxWidth + pad * 2f;
        c.drawRect(0, 0, boxW, boxH, bgPaint);
        for (int i = 0; i < lines.length; i++) {
            c.drawText(lines[i], x, y + i * lineHeight, textPaint);
        }
    }

    private String[] buildLines(Snapshot snap) {
        String[] out = new String[5];
        lineBuf.setLength(0);
        lineBuf.append("host ").append(snap.hostLabel);
        if (snap.preview) lineBuf.append("/preview");
        lineBuf.append("  ").append(snap.surfaceW).append('x').append(snap.surfaceH);
        if (snap.displayHz > 0f) {
            lineBuf.append("  panel ").append(Math.round(snap.displayHz)).append("Hz");
        }
        if (snap.surfaceFpsVote > 0f) {
            lineBuf.append("  vote ").append(Math.round(snap.surfaceFpsVote));
        }
        out[0] = lineBuf.toString();

        lineBuf.setLength(0);
        lineBuf.append("path ").append(snap.pathRequested)
                .append(" | actual ").append(snap.pathActualHw ? "HW" : "SW");
        if (snap.canvasHwAccel) lineBuf.append(" accel");
        lineBuf.append(" | allow=").append(snap.hwAllowed ? "Y" : "N");
        lineBuf.append(" cpuDem=").append(snap.cpuDemandHeld ? "Y" : "N");
        out[1] = lineBuf.toString();

        lineBuf.setLength(0);
        lineBuf.append("fps ")
                .append(format1(snap.drawnFps)).append(" drawn / ")
                .append(format1(snap.scheduleFps)).append(" sched")
                .append("  skip ").append(Math.round(snap.skipPct)).append('%')
                .append("  draw ")
                .append(format1(snap.avgDrawMs)).append("ms avg / ")
                .append(format1(snap.p95DrawMs)).append("ms p95");
        out[2] = lineBuf.toString();

        lineBuf.setLength(0);
        lineBuf.append("fps ").append(snap.preferredFps)
                .append("→").append(snap.effectiveFps)
                .append(" (").append(snap.schedulePeriodMs).append("ms)")
                .append("  ponies ").append(snap.preferredPonies)
                .append("→").append(snap.effectivePonies)
                .append(" live=").append(snap.livePonies)
                .append("  bg ").append(snap.preferredBg ? "on" : "off")
                .append("→");
        if (snap.bgDisabled) {
            lineBuf.append("off");
        } else if (snap.bgPresent) {
            lineBuf.append("img");
        } else {
            lineBuf.append("color");
        }
        out[3] = lineBuf.toString();

        lineBuf.setLength(0);
        lineBuf.append("caps ").append(snap.caps != null && snap.caps.length() > 0
                ? snap.caps : "none");
        lineBuf.append("  thr ").append(thermalLabel(snap.thermalStatus));
        if (snap.thermalEmergency) lineBuf.append("/EMER");
        else if (snap.thermalThrottle) lineBuf.append("/thr");
        if (snap.powerSave) lineBuf.append("  battSaver");
        if (snap.onBattery) lineBuf.append("  unplugged");
        else lineBuf.append("  AC");
        if (!snap.active) lineBuf.append("  inactive");
        if (snap.frozen) lineBuf.append("  frozen");
        if (snap.sceneLoading) lineBuf.append("  loading");
        out[4] = lineBuf.toString();
        return out;
    }

    private float averageDrawMs() {
        if (drawCostCount <= 0) return 0f;
        long sum = 0L;
        for (int i = 0; i < drawCostCount; i++) {
            sum += drawCostsNs[i];
        }
        return (sum / (float) drawCostCount) / 1_000_000f;
    }

    private float p95DrawMs() {
        if (drawCostCount <= 0) return 0f;
        long[] copy = new long[drawCostCount];
        System.arraycopy(drawCostsNs, 0, copy, 0, drawCostCount);
        java.util.Arrays.sort(copy);
        int idx = (int) Math.ceil(drawCostCount * 0.95) - 1;
        if (idx < 0) idx = 0;
        if (idx >= drawCostCount) idx = drawCostCount - 1;
        return copy[idx] / 1_000_000f;
    }

    private static String format1(float v) {
        if (v >= 100f) return Integer.toString(Math.round(v));
        int tenths = Math.round(v * 10f);
        int whole = tenths / 10;
        int frac = Math.abs(tenths % 10);
        return whole + "." + frac;
    }
}
