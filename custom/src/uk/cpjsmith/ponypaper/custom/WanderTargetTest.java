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
        failures += run("effectiveMovement", WanderTargetTest::testEffectiveMovement);
        failures += run("resolveSoftBands", WanderTargetTest::testResolveSoftBands);
        failures += run("resolveHardOverrides", WanderTargetTest::testResolveHardOverrides);
        failures += run("resolveBothInheritIsSoftH", WanderTargetTest::testResolveBothInheritIsSoftH);
        failures += run("softAccept", WanderTargetTest::testSoftAccept);
        failures += run("verticalFacing", WanderTargetTest::testVerticalFacing);
        failures += run("verticalGutters", WanderTargetTest::testVerticalGutters);
        failures += run("defaultMovementForWander", WanderTargetTest::testDefaultMovementForWander);
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
        if (!WanderTarget.MOVE_SOFT_VERTICAL.equals(WanderTarget.normalizeMovement("soft_vertical"))) {
            throw new AssertionError("soft_vertical");
        }
        if (!WanderTarget.MOVE_SOFT_VERTICAL.equals(WanderTarget.normalizeMovement("vertical-wander"))) {
            throw new AssertionError("vertical-wander alias");
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
        if (!WanderTarget.isKnownMovement("soft_vertical")) {
            throw new AssertionError("isKnown soft_vertical");
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

    private static void testEffectiveMovement() {
        if (!WanderTarget.MOVE_SOFT_VERTICAL.equals(
                WanderTarget.effectiveMovement(WanderTarget.WANDER_VERTICAL,
                        WanderTarget.MOVE_INHERIT))) {
            throw new AssertionError("vertical pony + inherit → soft_vertical");
        }
        if (!WanderTarget.MOVE_INHERIT.equals(
                WanderTarget.effectiveMovement(WanderTarget.WANDER_HORIZONTAL,
                        WanderTarget.MOVE_INHERIT))) {
            throw new AssertionError("horizontal pony + inherit stays inherit");
        }
        if (!WanderTarget.MOVE_INHERIT.equals(
                WanderTarget.effectiveMovement(WanderTarget.WANDER_BOTH,
                        WanderTarget.MOVE_INHERIT))) {
            throw new AssertionError("both pony + inherit stays inherit");
        }
        if (!WanderTarget.MOVE_SOFT_VERTICAL.equals(
                WanderTarget.effectiveMovement(WanderTarget.WANDER_HORIZONTAL,
                        WanderTarget.MOVE_SOFT_VERTICAL))) {
            throw new AssertionError("explicit soft_vertical wins");
        }
    }

    private static void testResolveSoftBands() {
        Random r = new Random(1);
        if (WanderTarget.resolveBand(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_INHERIT, r) != WanderTarget.BAND_SOFT_H) {
            throw new AssertionError("inherit → soft H");
        }
        if (WanderTarget.resolveBand(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_INHERIT, r) != WanderTarget.BAND_SOFT_V) {
            throw new AssertionError("vertical pony + inherit compat → soft V");
        }
        if (WanderTarget.resolveBand(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_SOFT_VERTICAL, r) != WanderTarget.BAND_SOFT_V) {
            throw new AssertionError("soft_vertical → soft V on any pony");
        }
        if (WanderTarget.resolveBand(WanderTarget.WANDER_BOTH,
                WanderTarget.MOVE_SOFT_VERTICAL, r) != WanderTarget.BAND_SOFT_V) {
            throw new AssertionError("soft_vertical on both pony");
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

    private static void testResolveBothInheritIsSoftH() {
        Random r = new Random(42);
        for (int i = 0; i < 40; i++) {
            int band = WanderTarget.resolveBand(WanderTarget.WANDER_BOTH,
                    WanderTarget.MOVE_INHERIT, r);
            if (band != WanderTarget.BAND_SOFT_H) {
                throw new AssertionError("both+inherit should be soft H, got " + band);
            }
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
            throw new AssertionError("vertical pony + inherit compat");
        }
        if (!WanderTarget.usesVerticalFacing(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_SOFT_VERTICAL)) {
            throw new AssertionError("soft_vertical on horizontal pony");
        }
        if (!WanderTarget.usesVerticalFacing(WanderTarget.WANDER_BOTH,
                WanderTarget.MOVE_SOFT_VERTICAL)) {
            throw new AssertionError("soft_vertical on both pony");
        }
        if (!WanderTarget.usesVerticalFacing(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_VERTICAL)) {
            throw new AssertionError("hard V facing on horizontal pony");
        }
        if (!WanderTarget.usesVerticalFacing(null, WanderTarget.MOVE_VERTICAL)) {
            throw new AssertionError("hard V facing with null wander");
        }
        if (WanderTarget.usesVerticalFacing(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_HORIZONTAL)) {
            throw new AssertionError("hard H should keep classic facing");
        }
        if (WanderTarget.usesVerticalFacing(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_ANY)) {
            throw new AssertionError("any keeps classic facing");
        }
        if (WanderTarget.usesVerticalFacing(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_INHERIT)) {
            throw new AssertionError("horizontal wander / inherit");
        }
        if (WanderTarget.usesVerticalFacing(WanderTarget.WANDER_BOTH,
                WanderTarget.MOVE_INHERIT)) {
            throw new AssertionError("both + inherit should not remap");
        }
    }

    private static void testVerticalGutters() {
        Random r = new Random(0);
        if (!WanderTarget.usesVerticalGutters(WanderTarget.BAND_SOFT_V)) {
            throw new AssertionError("soft V band");
        }
        if (!WanderTarget.usesVerticalGutters(WanderTarget.BAND_HARD_V)) {
            throw new AssertionError("hard V band");
        }
        if (WanderTarget.usesVerticalGutters(WanderTarget.BAND_SOFT_H)
                || WanderTarget.usesVerticalGutters(WanderTarget.BAND_HARD_H)
                || WanderTarget.usesVerticalGutters(WanderTarget.BAND_ANY)) {
            throw new AssertionError("H/any bands stay on horizontal gutters");
        }
        if (!WanderTarget.usesVerticalGutters(WanderTarget.WANDER_VERTICAL,
                WanderTarget.MOVE_INHERIT, r)) {
            throw new AssertionError("vertical pony + inherit → vertical gutters");
        }
        if (!WanderTarget.usesVerticalGutters(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_SOFT_VERTICAL, r)) {
            throw new AssertionError("soft_vertical → vertical gutters");
        }
        if (!WanderTarget.usesVerticalGutters(WanderTarget.WANDER_BOTH,
                WanderTarget.MOVE_VERTICAL, r)) {
            throw new AssertionError("hard V → vertical gutters");
        }
        if (WanderTarget.usesVerticalGutters(WanderTarget.WANDER_BOTH,
                WanderTarget.MOVE_INHERIT, r)) {
            throw new AssertionError("both + inherit should spawn from sides");
        }
        if (WanderTarget.usesVerticalGutters(WanderTarget.WANDER_HORIZONTAL,
                WanderTarget.MOVE_ANY, r)) {
            throw new AssertionError("any stays on horizontal gutters");
        }
    }

    private static void testDefaultMovementForWander() {
        if (!WanderTarget.MOVE_SOFT_VERTICAL.equals(
                WanderTarget.defaultMovementForWander(WanderTarget.WANDER_VERTICAL))) {
            throw new AssertionError("vertical default");
        }
        if (!WanderTarget.MOVE_INHERIT.equals(
                WanderTarget.defaultMovementForWander(WanderTarget.WANDER_HORIZONTAL))) {
            throw new AssertionError("horizontal default");
        }
        if (!WanderTarget.MOVE_INHERIT.equals(
                WanderTarget.defaultMovementForWander(WanderTarget.WANDER_BOTH))) {
            throw new AssertionError("both default");
        }
    }
}
