package uk.cpjsmith.ponypaper.custom;

import uk.cpjsmith.ponypaper.WorldFlow;

/**
 * Checks World Flow spawn-bag selection (NORMAL-only, crossing preferred).
 * Run via {@code ./gradlew :custom:testWorldFlow} or {@code java … WorldFlowTest}.
 */
public final class WorldFlowTest {

    private WorldFlowTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("preferCrossingNormals", WorldFlowTest::testPreferCrossingNormals);
        failures += run("fallbackToStartNormals", WorldFlowTest::testFallbackToStartNormals);
        failures += run("ignoreSpecials", WorldFlowTest::testIgnoreSpecials);
        failures += run("noneWhenEmpty", WorldFlowTest::testNoneWhenEmpty);
        failures += run("countNormal", WorldFlowTest::testCountNormal);
        if (failures > 0) {
            System.err.println(failures + " world-flow check(s) failed.");
            System.exit(1);
        }
        System.out.println("WorldFlow checks passed.");
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

    private static void testPreferCrossingNormals() {
        int[] crossing = {WorldFlow.TYPE_NORMAL, WorldFlow.TYPE_NORMAL};
        int[] start = {WorldFlow.TYPE_NORMAL};
        int bag = WorldFlow.selectBagSource(crossing, start);
        if (bag != WorldFlow.BAG_CROSSING) {
            throw new AssertionError("expected BAG_CROSSING, got " + bag);
        }
    }

    private static void testFallbackToStartNormals() {
        int[] crossing = {};
        int[] start = {WorldFlow.TYPE_NORMAL};
        int bag = WorldFlow.selectBagSource(crossing, start);
        if (bag != WorldFlow.BAG_START) {
            throw new AssertionError("expected BAG_START, got " + bag);
        }
        bag = WorldFlow.selectBagSource(null, start);
        if (bag != WorldFlow.BAG_START) {
            throw new AssertionError("null crossing should still fall back to start");
        }
    }

    private static void testIgnoreSpecials() {
        // PonyAction.PORT_O=1, PORT_I=2, SCREEN_IN=3, SCREEN_OUT=4
        int[] crossing = {1, 2, 3, 4};
        int[] start = {1, 4, WorldFlow.TYPE_NORMAL};
        int bag = WorldFlow.selectBagSource(crossing, start);
        if (bag != WorldFlow.BAG_START) {
            throw new AssertionError("specials-only crossing should fall back; got " + bag);
        }
        bag = WorldFlow.selectBagSource(crossing, new int[] {1, 3});
        if (bag != WorldFlow.BAG_NONE) {
            throw new AssertionError("specials-only bags should be BAG_NONE; got " + bag);
        }
    }

    private static void testNoneWhenEmpty() {
        if (WorldFlow.selectBagSource(new int[0], new int[0]) != WorldFlow.BAG_NONE) {
            throw new AssertionError("empty bags should be BAG_NONE");
        }
        if (WorldFlow.selectBagSource(null, null) != WorldFlow.BAG_NONE) {
            throw new AssertionError("null bags should be BAG_NONE");
        }
    }

    private static void testCountNormal() {
        if (WorldFlow.countNormal(null) != 0) {
            throw new AssertionError("null count");
        }
        if (WorldFlow.countNormal(new int[] {1, 0, 0, 4}) != 2) {
            throw new AssertionError("expected 2 normals");
        }
        if (!WorldFlow.isNormalTransit(WorldFlow.TYPE_NORMAL)) {
            throw new AssertionError("TYPE_NORMAL should be transit");
        }
        if (WorldFlow.isNormalTransit(4)) {
            throw new AssertionError("SCREEN_OUT should not be transit");
        }
    }
}
