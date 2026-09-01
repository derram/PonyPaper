package uk.cpjsmith.ponypaper.custom;

import java.util.Random;
import uk.cpjsmith.ponypaper.WanderTarget;

/**
 * Unit checks for wander / movement token normalization and band resolution.
 * Run via {@code ./gradlew :custom:testWanderTarget}.
 */
public final class WanderTargetTest {

    private WanderTargetTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("normalizeWander", WanderTargetTest::testNormalizeWander);
        failures += run("normalizeMovement", WanderTargetTest::testNormalizeMovement);
        failures += run("desktopAliases", WanderTargetTest::testDesktopAliases);
        failures += run("resolveInheritSoft", WanderTargetTest::testResolveInheritSoft);
        failures += run("resolveHardOverrides", WanderTargetTest::testResolveHardOverrides);
        failures += run("resolveBothCoinFlip", WanderTargetTest::testResolveBothCoinFlip);
        failures += run("softAccept", WanderTargetTest::testSoftAccept);
        failures += run("verticalFacing", WanderTargetTest::testVerticalFacing);
        if (failures > 0) {
            System.err.println(failures + " wander target check(s) failed.");
            System.exit(1);
        }
        System.out.println("Wander target checks passed.");
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

    private static void testNormalizeWander() {
        if (!WanderTarget.WANDER_HORIZONTAL.equals(WanderTarget.normalizeWander(null))) {
            throw new AssertionError("null wander");
        }
        if (!WanderTarget.WANDER_VERTICAL.equals(WanderTarget.normalizeWander("Vertical"))) {
            throw new AssertionError("Vertical");
        }
        if (!WanderTarget.WANDER_BOTH.equals(WanderTarget.normalizeWander("BOTH"))) {
            throw new AssertionError("BOTH");
        }
        if (!WanderTarget.WANDER_HORIZONTAL.equals(WanderTarget.normalizeWander("nope"))) {
            throw new AssertionError("unknown → horizontal");
        }
    }

    private static void testNormalizeMovement() {
        if (!WanderTarget.MOVE_INHERIT.equals(WanderTarget.normalizeMovement(null))) {
            throw new AssertionError("null movement");
        }
        if (!WanderTarget.MOVE_INHERIT.equals(WanderTarget.normalizeMovement(""))) {
            throw new AssertionError("empty movement");
        }
        if (!WanderTarget.MOVE_HORIZONTAL.equals(WanderTarget.normalizeMovement("horizontal"))) {
            throw new AssertionError("horizontal");
        }
        if (!WanderTarget.MOVE_VERTICAL.equals(WanderTarget.normalizeMovement("vertical"))) {
            throw new AssertionError("vertical");
        }
        if (!WanderTarget.MOVE_ANY.equals(WanderTarget.normalizeMovement("any"))) {
            throw new AssertionError("any");
        }
    }

    private static void testDesktopAliases() {
        if (!WanderTarget.MOVE_HORIZONTAL.equals(
                WanderTarget.normalizeMovement("Horizontal_Only"))) {
            throw new AssertionError("Horizontal_Only");
        }
        if (!WanderTarget.MOVE_VERTICAL.equals(
                WanderTarget.normalizeMovement("Vertical_Only"))) {
            throw new AssertionError("Vertical_Only");
        }
        if (!WanderTarget.MOVE_ANY.equals(WanderTarget.normalizeMovement("All"))) {
            throw new AssertionError("All");
        }
        if (!WanderTarget.MOVE_ANY.equals(
                WanderTarget.normalizeMovement("Diagonal_horizontal"))) {
            throw new AssertionError("Diagonal_horizontal");
        }
        if (!WanderTarget.MOVE_HORIZONTAL.equals(
                WanderTarget.movementFromDesktopAllowedMoves("Horizontal_Only"))) {
            throw new AssertionError("DP Horizontal_Only map");
        }
        if (!WanderTarget.MOVE_INHERIT.equals(
                WanderTarget.movementFromDesktopAllowedMoves("None"))) {
            throw new AssertionError("DP None → inherit");
        }
    }

    private static void testResolveInheritSoft() {
        Random r = new Random(1);
        if (WanderTarget.resolveBand(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_INHERIT, r) != WanderTarget.BAND_SOFT_H) {
            throw new AssertionError("inherit+horizontal wander");
        }
        if (WanderTarget.resolveBand(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_INHERIT, r) != WanderTarget.BAND_SOFT_V) {
            throw new AssertionError("inherit+vertical wander");
        }
    }

    private static void testResolveHardOverrides() {
        Random r = new Random(1);
        if (WanderTarget.resolveBand(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_HORIZONTAL, r) != WanderTarget.BAND_HARD_H) {
            throw new AssertionError("hard H overrides vertical wander");
        }
        if (WanderTarget.resolveBand(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_VERTICAL, r) != WanderTarget.BAND_HARD_V) {
            throw new AssertionError("hard V overrides horizontal wander");
        }
        if (WanderTarget.resolveBand(WanderTarget.WANDER_BOTH,
                WanderTarget.MOVE_ANY, r) != WanderTarget.BAND_ANY) {
            throw new AssertionError("any ignores wander");
        }
    }

    private static void testResolveBothCoinFlip() {
        // Fixed seed that produces both outcomes across many draws.
        Random r = new Random(42);
        boolean sawH = false;
        boolean sawV = false;
        for (int i = 0; i < 40; i++) {
            int band = WanderTarget.resolveBand(WanderTarget.WANDER_BOTH,
                    WanderTarget.MOVE_INHERIT, r);
            if (band == WanderTarget.BAND_SOFT_H) {
                sawH = true;
            } else if (band == WanderTarget.BAND_SOFT_V) {
                sawV = true;
            } else {
                throw new AssertionError("both produced band " + band);
            }
        }
        if (!sawH || !sawV) {
            throw new AssertionError("both should coin-flip H and V (sawH=" + sawH + " sawV=" + sawV + ")");
        }
    }

    private static void testSoftAccept() {
        if (!WanderTarget.acceptsSoftHorizontal(10f, 3f)) {
            throw new AssertionError("soft H accept");
        }
        if (WanderTarget.acceptsSoftHorizontal(3f, 10f)) {
            throw new AssertionError("soft H reject");
        }
        if (!WanderTarget.acceptsSoftVertical(3f, 10f)) {
            throw new AssertionError("soft V accept");
        }
        if (WanderTarget.acceptsSoftVertical(10f, 3f)) {
            throw new AssertionError("soft V reject");
        }
    }

    private static void testVerticalFacing() {
        if (!WanderTarget.usesVerticalFacing(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_INHERIT)) {
            throw new AssertionError("vertical wander + inherit");
        }
        if (!WanderTarget.usesVerticalFacing(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_VERTICAL)) {
            throw new AssertionError("vertical wander + hard V");
        }
        if (!WanderTarget.usesVerticalFacing(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_ANY)) {
            throw new AssertionError("vertical wander + any");
        }
        if (WanderTarget.usesVerticalFacing(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_HORIZONTAL)) {
            throw new AssertionError("hard H should keep classic facing");
        }
        if (WanderTarget.usesVerticalFacing(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_INHERIT)) {
            throw new AssertionError("horizontal wander");
        }
        if (WanderTarget.usesVerticalFacing(WanderTarget.WANDER_BOTH,
                WanderTarget.MOVE_INHERIT)) {
            throw new AssertionError("both should not remap");
        }
        if (WanderTarget.usesVerticalFacing(null, WanderTarget.MOVE_VERTICAL)) {
            throw new AssertionError("null wander defaults horizontal");
        }
    }
}
