package uk.cpjsmith.ponypaper.custom;

import uk.cpjsmith.ponypaper.BackgroundFit;

/**
 * Checks background cover/contain layout, thin-bar snap, and OLED pan slack.
 * Run via {@code ./gradlew :custom:testBackgroundFit}.
 */
public final class BackgroundFitTest {

    /** Matches {@code DreamOledShift.AMPLITUDE}. */
    private static final float OLED_AMPLITUDE = 0.02f;

    private BackgroundFitTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("wantContainDefaults", BackgroundFitTest::testWantContainDefaults);
        failures += run("coverPhonePortrait", BackgroundFitTest::testCoverPhonePortrait);
        failures += run("containPortraitOnLandscapeTablet",
                BackgroundFitTest::testContainPortraitOnLandscapeTablet);
        failures += run("snapSixteenNineOnSixteenTen",
                BackgroundFitTest::testSnapSixteenNineOnSixteenTen);
        failures += run("noSnapWideMismatch", BackgroundFitTest::testNoSnapWideMismatch);
        failures += run("matchingAspectNoSnap", BackgroundFitTest::testMatchingAspectNoSnap);
        failures += run("containPinsOffset", BackgroundFitTest::testContainPinsOffset);
        failures += run("coverUsesOffset", BackgroundFitTest::testCoverUsesOffset);
        failures += run("decodeSizeMatchesDest", BackgroundFitTest::testDecodeSizeMatchesDest);
        failures += run("oledMatchingNoShift", BackgroundFitTest::testOledMatchingNoShift);
        failures += run("oledContainPanStaysOnCanvas",
                BackgroundFitTest::testOledContainPanStaysOnCanvas);
        failures += run("oledCoverPanStaysCovering",
                BackgroundFitTest::testOledCoverPanStaysCovering);
        if (failures > 0) {
            System.err.println(failures + " background-fit check(s) failed.");
            System.exit(1);
        }
        System.out.println("BackgroundFit checks passed.");
    }

    private interface Check {
        void run() throws Exception;
    }

    private static int run(String name, Check check) {
        try {
            check.run();
            System.out.println("ok  " + name);
            return 0;
        } catch (Throwable t) {
            System.err.println("FAIL " + name + ": " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    private static void testWantContainDefaults() {
        if (!BackgroundFit.wantContain(null, true)) {
            throw new AssertionError("dream default");
        }
        if (BackgroundFit.wantContain(null, false)) {
            throw new AssertionError("wallpaper default");
        }
        if (!BackgroundFit.wantContain("", true)) {
            throw new AssertionError("dream empty");
        }
        if (BackgroundFit.wantContain("cover", true)) {
            throw new AssertionError("dream explicit cover");
        }
        if (!BackgroundFit.wantContain("contain", false)) {
            throw new AssertionError("wallpaper explicit contain");
        }
    }

    /** Landscape art on a portrait phone: cover fills height, extra width for pan. */
    private static void testCoverPhonePortrait() {
        BackgroundFit.Dest d = new BackgroundFit.Dest();
        BackgroundFit.layout(1920, 1080, 1080, 1920, false, 0.5f, 0.5f, d);
        if (d.height() != 1920) {
            throw new AssertionError("height " + d.height());
        }
        if (d.width() <= 1080) {
            throw new AssertionError("width " + d.width());
        }
        if (!d.fillsCanvas(1080, 1920)) {
            throw new AssertionError("fills");
        }
    }

    /** Portrait art on a landscape tablet: contain pillarboxes, no snap. */
    private static void testContainPortraitOnLandscapeTablet() {
        int canvasW = 2560;
        int canvasH = 1600;
        int srcW = 1080;
        int srcH = 1920;
        if (BackgroundFit.shouldSnapToCover(srcW, srcH, canvasW, canvasH)) {
            throw new AssertionError("snap");
        }
        if (BackgroundFit.useCover(true, srcW, srcH, canvasW, canvasH)) {
            throw new AssertionError("useCover");
        }
        BackgroundFit.Dest d = new BackgroundFit.Dest();
        BackgroundFit.layout(srcW, srcH, canvasW, canvasH, true, 0.5f, 0.5f, d);
        if (d.height() != canvasH) {
            throw new AssertionError("height " + d.height());
        }
        if (d.width() >= canvasW) {
            throw new AssertionError("width " + d.width());
        }
        if (d.fillsCanvas(canvasW, canvasH)) {
            throw new AssertionError("should letterbox");
        }
        if (d.left != (canvasW - d.width()) / 2
                && d.left != Math.round((canvasW - d.width()) * 0.5f)) {
            throw new AssertionError("centered left " + d.left);
        }
    }

    /** 16:9 on 16:10: ~6.25% bars, under 8% snap → cover. */
    private static void testSnapSixteenNineOnSixteenTen() {
        int canvasW = 2560;
        int canvasH = 1600;
        int srcW = 1920;
        int srcH = 1080;
        if (!BackgroundFit.shouldSnapToCover(srcW, srcH, canvasW, canvasH)) {
            throw new AssertionError("expected snap");
        }
        if (!BackgroundFit.useCover(true, srcW, srcH, canvasW, canvasH)) {
            throw new AssertionError("useCover");
        }
        BackgroundFit.Dest d = new BackgroundFit.Dest();
        BackgroundFit.layout(srcW, srcH, canvasW, canvasH, true, 0.5f, 0.5f, d);
        if (d.height() != canvasH) {
            throw new AssertionError("height " + d.height());
        }
        if (d.width() <= canvasW) {
            throw new AssertionError("width " + d.width());
        }
        if (!d.fillsCanvas(canvasW, canvasH)) {
            throw new AssertionError("fills after snap");
        }
    }

    /** 16:9 on 4:3: bars too large to snap. */
    private static void testNoSnapWideMismatch() {
        int canvasW = 2048;
        int canvasH = 1536;
        int srcW = 1920;
        int srcH = 1080;
        if (BackgroundFit.shouldSnapToCover(srcW, srcH, canvasW, canvasH)) {
            throw new AssertionError("snap");
        }
        BackgroundFit.Dest d = new BackgroundFit.Dest();
        BackgroundFit.layout(srcW, srcH, canvasW, canvasH, true, 0.5f, 0.5f, d);
        if (d.width() != canvasW) {
            throw new AssertionError("width " + d.width());
        }
        if (d.height() >= canvasH) {
            throw new AssertionError("height " + d.height());
        }
    }

    private static void testMatchingAspectNoSnap() {
        if (BackgroundFit.shouldSnapToCover(1920, 1080, 1920, 1080)) {
            throw new AssertionError("snap");
        }
        BackgroundFit.Dest d = new BackgroundFit.Dest();
        BackgroundFit.layout(1920, 1080, 1920, 1080, true, 0.5f, 0.5f, d);
        if (d.left != 0 || d.top != 0 || d.width() != 1920 || d.height() != 1080) {
            throw new AssertionError("dest " + d.left + "," + d.top
                    + " " + d.width() + "x" + d.height());
        }
        if (!d.fillsCanvas(1920, 1080)) {
            throw new AssertionError("fills");
        }
    }

    private static void testContainPinsOffset() {
        BackgroundFit.Dest a = new BackgroundFit.Dest();
        BackgroundFit.Dest b = new BackgroundFit.Dest();
        BackgroundFit.layout(1080, 1920, 2560, 1600, true, 0f, 0f, a);
        BackgroundFit.layout(1080, 1920, 2560, 1600, true, 1f, 1f, b);
        if (a.left != b.left || a.top != b.top) {
            throw new AssertionError("contain must ignore offsets");
        }
    }

    private static void testCoverUsesOffset() {
        BackgroundFit.Dest left = new BackgroundFit.Dest();
        BackgroundFit.Dest right = new BackgroundFit.Dest();
        BackgroundFit.layout(1920, 1080, 1080, 1920, false, 0f, 0.5f, left);
        BackgroundFit.layout(1920, 1080, 1080, 1920, false, 1f, 0.5f, right);
        if (left.left != 0) {
            throw new AssertionError("xOffset 0 → left 0, got " + left.left);
        }
        if (right.right != 1080) {
            throw new AssertionError("xOffset 1 → right 1080, got " + right.right);
        }
        if (left.left == right.left) {
            throw new AssertionError("cover offsets should differ");
        }
    }

    private static void testDecodeSizeMatchesDest() {
        int srcW = 1080;
        int srcH = 1920;
        int canvasW = 2560;
        int canvasH = 1600;
        BackgroundFit.Dest d = new BackgroundFit.Dest();
        BackgroundFit.layout(srcW, srcH, canvasW, canvasH, true, 0.5f, 0.5f, d);
        int w = BackgroundFit.fittedWidth(srcW, srcH, canvasW, canvasH, true);
        int h = BackgroundFit.fittedHeight(srcW, srcH, canvasW, canvasH, true);
        if (w != d.width() || h != d.height()) {
            throw new AssertionError("decode " + w + "x" + h + " dest " + d.width()
                    + "x" + d.height());
        }
    }

    private static void testOledMatchingNoShift() {
        if (BackgroundFit.oledMaxShift(1920, 1920, OLED_AMPLITUDE) != 0) {
            throw new AssertionError("width slack");
        }
        if (BackgroundFit.oledMaxShift(1080, 1080, OLED_AMPLITUDE) != 0) {
            throw new AssertionError("height slack");
        }
        int origin = BackgroundFit.oledShiftedOrigin(0, 1920, 1920,
                OLED_AMPLITUDE, 1.0);
        if (origin != 0) {
            throw new AssertionError("shifted " + origin);
        }
    }

    private static void testOledContainPanStaysOnCanvas() {
        int canvas = 2560;
        int dest = 900;
        int rest = Math.round((canvas - dest) * 0.5f);
        int max = BackgroundFit.oledMaxShift(canvas, dest, OLED_AMPLITUDE);
        if (max < 1) {
            throw new AssertionError("expected slack");
        }
        int left = BackgroundFit.oledShiftedOrigin(rest, canvas, dest,
                OLED_AMPLITUDE, -1.0);
        int right = BackgroundFit.oledShiftedOrigin(rest, canvas, dest,
                OLED_AMPLITUDE, 1.0);
        if (left < 0 || left + dest > canvas) {
            throw new AssertionError("left " + left);
        }
        if (right < 0 || right + dest > canvas) {
            throw new AssertionError("right " + right);
        }
        if (BackgroundFit.oledClampOrigin(-10, canvas, dest) != 0) {
            throw new AssertionError("clamp min");
        }
        if (BackgroundFit.oledClampOrigin(canvas, canvas, dest) != canvas - dest) {
            throw new AssertionError("clamp max");
        }
    }

    private static void testOledCoverPanStaysCovering() {
        int canvas = 1080;
        int dest = 1920;
        int rest = Math.round((canvas - dest) * 0.5f);
        int max = BackgroundFit.oledMaxShift(canvas, dest, OLED_AMPLITUDE);
        if (max < 1) {
            throw new AssertionError("expected slack");
        }
        int left = BackgroundFit.oledShiftedOrigin(rest, canvas, dest,
                OLED_AMPLITUDE, -1.0);
        int right = BackgroundFit.oledShiftedOrigin(rest, canvas, dest,
                OLED_AMPLITUDE, 1.0);
        if (left > 0 || left + dest < canvas) {
            throw new AssertionError("left " + left);
        }
        if (right > 0 || right + dest < canvas) {
            throw new AssertionError("right " + right);
        }
        if (BackgroundFit.oledClampOrigin(1, canvas, dest) != 0) {
            throw new AssertionError("clamp max");
        }
        if (BackgroundFit.oledClampOrigin(canvas - dest - 1, canvas, dest) != canvas - dest) {
            throw new AssertionError("clamp min");
        }
    }
}
