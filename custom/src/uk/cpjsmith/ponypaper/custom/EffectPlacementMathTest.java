package uk.cpjsmith.ponypaper.custom;

import java.awt.Rectangle;
import java.util.Random;
import uk.cpjsmith.ponypaper.EffectPlacement;

/**
 * Locks effect placement math to the wallpaper formula.
 * Run via {@code ./gradlew :custom:testEffectPlacement}.
 */
public final class EffectPlacementMathTest {

    private EffectPlacementMathTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("centerOnCenter", EffectPlacementMathTest::testCenterOnCenter);
        failures += run("topPlacementBottomLeftCentering",
                EffectPlacementMathTest::testTopPlacementBottomLeftCentering);
        failures += run("rightPlacementTopLeftCentering",
                EffectPlacementMathTest::testRightPlacementTopLeftCentering);
        failures += run("anyUsesOverride", EffectPlacementMathTest::testAnyUsesOverride);
        failures += run("anyNotCenterNeverCenter",
                EffectPlacementMathTest::testAnyNotCenterNeverCenter);
        failures += run("tokenRoundTrip", EffectPlacementMathTest::testTokenRoundTrip);
        failures += run("motionIdleUnchanged", EffectPlacementMathTest::testMotionIdleUnchanged);
        failures += run("motionHorizontalUnchanged",
                EffectPlacementMathTest::testMotionHorizontalUnchanged);
        failures += run("motionDiagonalBehind",
                EffectPlacementMathTest::testMotionDiagonalBehind);
        failures += run("motionDiagonalBehindLeftFacing",
                EffectPlacementMathTest::testMotionDiagonalBehindLeftFacing);
        failures += run("motionModeOffIgnoresTravel",
                EffectPlacementMathTest::testMotionModeOffIgnoresTravel);
        if (failures > 0) {
            System.err.println(failures + " effect placement check(s) failed.");
            System.exit(1);
        }
        System.out.println("Effect placement math checks passed.");
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

    private static void assertNear(float expected, float actual, String label) {
        if (Math.abs(expected - actual) > 0.01f) {
            throw new AssertionError(label + ": expected " + expected + " got " + actual);
        }
    }

    /** Pony 100×80 at (10,20); effect 40×40; Center↔Center → origin at pony centre − half effect. */
    private static void testCenterOnCenter() {
        Rectangle pony = new Rectangle(10, 20, 100, 80);
        EffectPlacementMath.Origin o = EffectPlacementMath.computeOrigin(
                pony, 40f, 40f, "Center", "Center", EffectPlacementMath.CELL_CENTER);
        assertNear(60f, o.attachX, "attachX");
        assertNear(60f, o.attachY, "attachY");
        assertNear(40f, o.originX, "originX");
        assertNear(40f, o.originY, "originY");
        if (o.resolvedPlacementCell != EffectPlacementMath.CELL_CENTER) {
            throw new AssertionError("resolved cell");
        }
    }

    /** Placement Top (mid-top of pony) + centering Bottom_Left → effect hangs below attach. */
    private static void testTopPlacementBottomLeftCentering() {
        Rectangle pony = new Rectangle(0, 0, 100, 100);
        EffectPlacementMath.Origin o = EffectPlacementMath.computeOrigin(
                pony, 20f, 30f, "Top", "Bottom_Left", 0);
        assertNear(50f, o.attachX, "attachX");
        assertNear(0f, o.attachY, "attachY");
        // origin = attach - (0*w, 1*h) for Bottom_Left centering
        assertNear(50f, o.originX, "originX");
        assertNear(-30f, o.originY, "originY");
    }

    private static void testRightPlacementTopLeftCentering() {
        Rectangle pony = new Rectangle(0, 0, 200, 100);
        EffectPlacementMath.Origin o = EffectPlacementMath.computeOrigin(
                pony, 50f, 50f, "Right", "Top_Left", 0);
        assertNear(200f, o.attachX, "attachX");
        assertNear(50f, o.attachY, "attachY");
        assertNear(200f, o.originX, "originX");
        assertNear(50f, o.originY, "originY");
    }

    private static void testAnyUsesOverride() {
        Rectangle pony = new Rectangle(0, 0, 90, 90);
        EffectPlacementMath.Origin o = EffectPlacementMath.computeOrigin(
                pony, 10f, 10f, "Any", "Center", EffectPlacementMath.CELL_TOP_LEFT);
        assertNear(0f, o.attachX, "attachX");
        assertNear(0f, o.attachY, "attachY");
        assertNear(-5f, o.originX, "originX");
        assertNear(-5f, o.originY, "originY");
        if (o.resolvedPlacementCell != EffectPlacementMath.CELL_TOP_LEFT) {
            throw new AssertionError("expected Top_Left resolve");
        }
    }

    private static void testAnyNotCenterNeverCenter() {
        Random fixed = new Random(42L);
        for (int i = 0; i < 50; i++) {
            int cell = EffectPlacementMath.pickRandomCell(
                    EffectPlacementMath.CELL_ANY_NOT_CENTER, fixed);
            if (cell == EffectPlacementMath.CELL_CENTER) {
                throw new AssertionError("Any-Not_Center rolled Center");
            }
            if (cell < 0 || cell > 8) {
                throw new AssertionError("cell out of range: " + cell);
            }
        }
    }

    private static void testTokenRoundTrip() {
        for (int i = 0; i < EffectPlacementMath.FIXED_CELL_TOKENS.length; i++) {
            String token = EffectPlacementMath.FIXED_CELL_TOKENS[i];
            if (EffectPlacementMath.cellIndex(token) != i) {
                throw new AssertionError("cellIndex mismatch for " + token);
            }
            if (!token.equals(EffectPlacementMath.tokenForCell(i))) {
                throw new AssertionError("tokenForCell mismatch for " + i);
            }
        }
        if (!EffectPlacementMath.isAnyPlacement("Any")
                || !EffectPlacementMath.isAnyPlacement("any-not-center")) {
            throw new AssertionError("isAnyPlacement");
        }
        if (EffectPlacementMath.isAnyPlacement("Center")) {
            throw new AssertionError("Center should not be Any");
        }
    }

    /** Idle travel must not rotate cells (waiting actions stay AABB-accurate). */
    private static void testMotionIdleUnchanged() {
        int left = EffectPlacementMath.CELL_LEFT;
        int remapped = EffectPlacement.remapCellForMotion(
                left, 0f, 0f, EffectPlacement.FACING_RIGHT);
        if (remapped != left) {
            throw new AssertionError("idle remapped Left to " + remapped);
        }
    }

    /** Pure horizontal travel keeps Left/Right on the horizontal mid-line. */
    private static void testMotionHorizontalUnchanged() {
        int left = EffectPlacementMath.CELL_LEFT;
        int remapped = EffectPlacement.remapCellForMotion(
                left, 10f, 0f, EffectPlacement.FACING_RIGHT);
        if (remapped != left) {
            throw new AssertionError("horizontal remapped Left to " + remapped);
        }
        int right = EffectPlacementMath.CELL_RIGHT;
        remapped = EffectPlacement.remapCellForMotion(
                right, -10f, 0f, EffectPlacement.FACING_LEFT);
        if (remapped != right) {
            throw new AssertionError("left-facing horizontal remapped Right to " + remapped);
        }
    }

    /**
     * Facing right, moving up-right: authored Left (behind) becomes Bottom_Left.
     */
    private static void testMotionDiagonalBehind() {
        int remapped = EffectPlacement.remapCellForMotion(
                EffectPlacementMath.CELL_LEFT, 1f, -1f, EffectPlacement.FACING_RIGHT);
        if (remapped != EffectPlacementMath.CELL_BOTTOM_LEFT) {
            throw new AssertionError("expected Bottom_Left, got "
                    + EffectPlacementMath.tokenForCell(remapped));
        }
        Rectangle pony = new Rectangle(0, 0, 100, 100);
        EffectPlacementMath.Origin o = EffectPlacementMath.computeOrigin(
                pony, 10f, 10f, "Left", "Center", 0,
                true, 1f, -1f, EffectPlacement.FACING_RIGHT);
        assertNear(0f, o.attachX, "attachX");
        assertNear(100f, o.attachY, "attachY");
    }

    /**
     * Facing left, moving up-left: authored Right (behind) becomes Bottom_Right.
     */
    private static void testMotionDiagonalBehindLeftFacing() {
        int remapped = EffectPlacement.remapCellForMotion(
                EffectPlacementMath.CELL_RIGHT, -1f, -1f, EffectPlacement.FACING_LEFT);
        if (remapped != EffectPlacementMath.CELL_BOTTOM_RIGHT) {
            throw new AssertionError("expected Bottom_Right, got "
                    + EffectPlacementMath.tokenForCell(remapped));
        }
    }

    /** Bounds mode must ignore travel even when a diagonal vector is passed. */
    private static void testMotionModeOffIgnoresTravel() {
        Rectangle pony = new Rectangle(0, 0, 100, 100);
        EffectPlacementMath.Origin o = EffectPlacementMath.computeOrigin(
                pony, 10f, 10f, "Left", "Center", 0,
                false, 1f, -1f, EffectPlacement.FACING_RIGHT);
        assertNear(0f, o.attachX, "attachX");
        assertNear(50f, o.attachY, "attachY");
    }
}
