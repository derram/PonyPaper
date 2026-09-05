package uk.cpjsmith.ponypaper.custom;

import uk.cpjsmith.ponypaper.SpawnYBand;

/**
 * Checks feet-anchored spawn Y insets (on-screen vs crossing).
 * Run via {@code ./gradlew :custom:testSpawnYBand}.
 */
public final class SpawnYBandTest {

    private SpawnYBandTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("onScreenFullHeight", SpawnYBandTest::testOnScreenFullHeight);
        failures += run("crossingPartialTop", SpawnYBandTest::testCrossingPartialTop);
        failures += run("crossingFloor", SpawnYBandTest::testCrossingFloor);
        failures += run("crossingScreenCap", SpawnYBandTest::testCrossingScreenCap);
        failures += run("bottomNearEdge", SpawnYBandTest::testBottomNearEdge);
        failures += run("usableAndClamp", SpawnYBandTest::testUsableAndClamp);
        failures += run("crossingReachesLowerThanLegacy", SpawnYBandTest::testCrossingReachesLowerThanLegacy);
        failures += run("verticalGutterY", SpawnYBandTest::testVerticalGutterY);
        failures += run("oppositeVerticalGutterY", SpawnYBandTest::testOppositeVerticalGutterY);
        if (failures > 0) {
            System.err.println(failures + " spawn-y-band check(s) failed.");
            System.exit(1);
        }
        System.out.println("SpawnYBand checks passed.");
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

    private static void testOnScreenFullHeight() {
        float scale = 2f;
        int frameH = 50;
        int top = SpawnYBand.onScreenTopInset(frameH, scale);
        int expected = (int) (frameH * scale) + (int) (SpawnYBand.TOP_PAD * scale);
        if (top != expected) {
            throw new AssertionError("on-screen top " + top + " != " + expected);
        }
    }

    private static void testCrossingPartialTop() {
        float scale = 2f;
        int frameH = 100;
        int screenH = 2000;
        int top = SpawnYBand.crossingTopInset(frameH, scale, screenH);
        int fromFrame = (int) (frameH * scale * SpawnYBand.CROSSING_TOP_FRACTION);
        if (top != fromFrame) {
            throw new AssertionError("crossing top " + top + " != " + fromFrame);
        }
        int onScreen = SpawnYBand.onScreenTopInset(frameH, scale);
        if (top >= onScreen) {
            throw new AssertionError("crossing top should be shorter than on-screen");
        }
    }

    private static void testCrossingFloor() {
        float scale = 2f;
        // Tiny frame → floor at 30×scale wins over 45% of frame.
        int top = SpawnYBand.crossingTopInset(10, scale, 2000);
        int floor = (int) (SpawnYBand.CROSSING_TOP_MIN * scale);
        if (top != floor) {
            throw new AssertionError("expected floor " + floor + ", got " + top);
        }
    }

    private static void testCrossingScreenCap() {
        float scale = 2f;
        int frameH = 500;
        int screenH = 400;
        int top = SpawnYBand.crossingTopInset(frameH, scale, screenH);
        int cap = (int) (screenH * SpawnYBand.CROSSING_TOP_MAX_SCREEN_FRACTION);
        if (top != cap) {
            throw new AssertionError("expected screen cap " + cap + ", got " + top);
        }
    }

    private static void testBottomNearEdge() {
        float scale = 2f;
        int bottom = SpawnYBand.bottomInset(scale);
        if (bottom != (int) (SpawnYBand.BOTTOM_PAD * scale)) {
            throw new AssertionError("bottom inset " + bottom);
        }
    }

    private static void testUsableAndClamp() {
        int screenTop = 10;
        int screenH = 1000;
        int top = 100;
        int bottom = 16;
        int usable = SpawnYBand.usableHeight(screenH, top, bottom);
        if (usable != screenH - top - bottom) {
            throw new AssertionError("usable " + usable);
        }
        int min = SpawnYBand.minY(screenTop, screenH, top, bottom);
        int max = SpawnYBand.maxY(screenTop, screenH, top, bottom);
        if (min != screenTop + top) {
            throw new AssertionError("minY " + min);
        }
        if (max != screenTop + screenH - bottom) {
            throw new AssertionError("maxY " + max);
        }
        if (SpawnYBand.usableHeight(50, 40, 40) != 0) {
            throw new AssertionError("collapsed usable should be 0");
        }
        int mid = SpawnYBand.minY(0, 50, 40, 40);
        if (mid != 25) {
            throw new AssertionError("collapsed min should be center, got " + mid);
        }
    }

    /**
     * Crossing max feet Y should sit near the bottom (8×scale), not the legacy
     * 30×scale dead band.
     */
    private static void testCrossingReachesLowerThanLegacy() {
        float scale = 2f;
        int screenH = 1920;
        int frameH = 60;
        int top = SpawnYBand.crossingTopInset(frameH, scale, screenH);
        int bottom = SpawnYBand.bottomInset(scale);
        int maxY = SpawnYBand.maxY(0, screenH, top, bottom);
        int legacyMax = screenH - (int) (30 * scale);
        if (maxY <= legacyMax) {
            throw new AssertionError(
                    "crossing maxY " + maxY + " should exceed legacy " + legacyMax);
        }
        if (maxY != screenH - bottom) {
            throw new AssertionError("maxY should be bottom-pad from edge: " + maxY);
        }
    }

    private static void testVerticalGutterY() {
        float scale = 2f;
        int frameH = 50;
        int screenTop = 0;
        int screenBottom = 1920;
        int topY = SpawnYBand.verticalGutterY(true, screenTop, screenBottom, frameH, scale);
        int expectedTop = screenTop - (int) (SpawnYBand.VERTICAL_GUTTER_PAD * scale);
        if (topY != expectedTop) {
            throw new AssertionError("top gutter Y " + topY + " != " + expectedTop);
        }
        int bottomY = SpawnYBand.verticalGutterY(false, screenTop, screenBottom, frameH, scale);
        int expectedBottom = screenBottom + SpawnYBand.onScreenTopInset(frameH, scale);
        if (bottomY != expectedBottom) {
            throw new AssertionError("bottom gutter Y " + bottomY + " != " + expectedBottom);
        }
        if (bottomY - screenBottom <= (int) (SpawnYBand.VERTICAL_GUTTER_PAD * scale)) {
            throw new AssertionError("bottom clearance should exceed top pad");
        }
    }

    private static void testOppositeVerticalGutterY() {
        float scale = 1f;
        int frameH = 40;
        int screenTop = 10;
        int screenBottom = 1010;
        int centerY = 510;
        int fromTop = SpawnYBand.verticalGutterY(true, screenTop, screenBottom, frameH, scale);
        int opposite = SpawnYBand.oppositeVerticalGutterY(fromTop, centerY, screenTop,
                screenBottom, frameH, scale);
        int expectBottom = SpawnYBand.verticalGutterY(false, screenTop, screenBottom,
                frameH, scale);
        if (opposite != expectBottom) {
            throw new AssertionError("from top should target bottom: " + opposite);
        }
        int fromBottom = expectBottom;
        opposite = SpawnYBand.oppositeVerticalGutterY(fromBottom, centerY, screenTop,
                screenBottom, frameH, scale);
        if (opposite != fromTop) {
            throw new AssertionError("from bottom should target top: " + opposite);
        }
    }
}
