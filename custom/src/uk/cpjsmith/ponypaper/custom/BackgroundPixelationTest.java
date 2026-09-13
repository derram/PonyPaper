package uk.cpjsmith.ponypaper.custom;

import uk.cpjsmith.ponypaper.BackgroundFit;
import uk.cpjsmith.ponypaper.BackgroundPixelation;

/**
 * Checks host defaults, clamp, and decode target size.
 * Run via {@code ./gradlew :custom:testBackgroundPixelation}.
 */
public final class BackgroundPixelationTest {

    private BackgroundPixelationTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("hostDefaults", BackgroundPixelationTest::testHostDefaults);
        failures += run("clamp", BackgroundPixelationTest::testClamp);
        failures += run("fromStored", BackgroundPixelationTest::testFromStored);
        failures += run("targetEdge", BackgroundPixelationTest::testTargetEdge);
        failures += run("dreamFullResMatchesDest",
                BackgroundPixelationTest::testDreamFullResMatchesDest);
        if (failures > 0) {
            System.err.println(failures + " background-pixelation check(s) failed.");
            System.exit(1);
        }
        System.out.println("BackgroundPixelation checks passed.");
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

    private static void testHostDefaults() {
        if (BackgroundPixelation.defaultLevel(false) != 4) {
            throw new AssertionError("wallpaper default");
        }
        if (BackgroundPixelation.defaultLevel(true) != 1) {
            throw new AssertionError("dream default");
        }
    }

    private static void testClamp() {
        if (BackgroundPixelation.clamp(0) != 1) {
            throw new AssertionError("below min");
        }
        if (BackgroundPixelation.clamp(1) != 1) {
            throw new AssertionError("min");
        }
        if (BackgroundPixelation.clamp(24) != 24) {
            throw new AssertionError("max");
        }
        if (BackgroundPixelation.clamp(25) != 24) {
            throw new AssertionError("above max");
        }
    }

    private static void testFromStored() {
        if (BackgroundPixelation.fromStored(null, false) != 4) {
            throw new AssertionError("missing wallpaper");
        }
        if (BackgroundPixelation.fromStored(null, true) != 1) {
            throw new AssertionError("missing dream");
        }
        if (BackgroundPixelation.fromStored(4, true) != 4) {
            throw new AssertionError("dream explicit wallpaper-like");
        }
        if (BackgroundPixelation.fromStored(1, false) != 1) {
            throw new AssertionError("wallpaper explicit full-res");
        }
        if (BackgroundPixelation.fromStored(0, true) != 1) {
            throw new AssertionError("dream invalid");
        }
    }

    private static void testTargetEdge() {
        if (BackgroundPixelation.targetEdge(1080, 1) != 1080) {
            throw new AssertionError("full res");
        }
        if (BackgroundPixelation.targetEdge(1080, 4) != 270) {
            throw new AssertionError("level 4");
        }
        if (BackgroundPixelation.targetEdge(1, 24) != 1) {
            throw new AssertionError("tiny dest");
        }
    }

    /** Pixelation 1 decode size equals the contain dest the dream will blit. */
    private static void testDreamFullResMatchesDest() {
        int srcW = 1080;
        int srcH = 1920;
        int canvasW = 2560;
        int canvasH = 1600;
        BackgroundFit.Dest d = new BackgroundFit.Dest();
        BackgroundFit.layout(srcW, srcH, canvasW, canvasH, true, 0.5f, 0.5f, d);
        int fitW = BackgroundFit.fittedWidth(srcW, srcH, canvasW, canvasH, true);
        int fitH = BackgroundFit.fittedHeight(srcW, srcH, canvasW, canvasH, true);
        int tw = BackgroundPixelation.targetEdge(fitW, BackgroundPixelation.DEFAULT_DREAM);
        int th = BackgroundPixelation.targetEdge(fitH, BackgroundPixelation.DEFAULT_DREAM);
        if (tw != d.width() || th != d.height()) {
            throw new AssertionError("decode " + tw + "x" + th + " dest " + d.width()
                    + "x" + d.height());
        }
    }
}
