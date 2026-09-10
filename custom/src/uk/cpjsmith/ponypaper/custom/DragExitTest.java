package uk.cpjsmith.ponypaper.custom;

import uk.cpjsmith.ponypaper.DragExit;
import uk.cpjsmith.ponypaper.WanderTarget;

/**
 * Checks drag-to-edge margin picking and facing-axis matching.
 * Run via {@code ./gradlew :custom:testDragExit} or {@code java … DragExitTest}.
 */
public final class DragExitTest {

    private static final int L = 0;
    private static final int T = 0;
    private static final int R = 200;
    private static final int B = 100;
    private static final float PAD = 30f;

    private DragExitTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("interiorIsNone", DragExitTest::testInteriorIsNone);
        failures += run("fourEdges", DragExitTest::testFourEdges);
        failures += run("pastEdgeStillCounts", DragExitTest::testPastEdgeStillCounts);
        failures += run("padLineInclusive", DragExitTest::testPadLineInclusive);
        failures += run("cornerNearerWins", DragExitTest::testCornerNearerWins);
        failures += run("cornerTiePrefersLeft", DragExitTest::testCornerTiePrefersLeft);
        failures += run("sampleMovement", DragExitTest::testSampleMovement);
        failures += run("travelMatchesHorizontal", DragExitTest::testTravelMatchesHorizontal);
        failures += run("travelMatchesVertical", DragExitTest::testTravelMatchesVertical);
        failures += run("anyMatchesSidesOnly", DragExitTest::testAnyMatchesSidesOnly);
        failures += run("verticalPonyInheritIsVertical",
                DragExitTest::testVerticalPonyInheritIsVertical);
        failures += run("hardHorizontalOnVerticalPony",
                DragExitTest::testHardHorizontalOnVerticalPony);
        if (failures > 0) {
            System.err.println(failures + " drag-exit check(s) failed.");
            System.exit(1);
        }
        System.out.println("DragExit checks passed.");
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

    private static void testInteriorIsNone() {
        int e = DragExit.nearestEdge(100f, 50f, L, R, T, B, PAD);
        if (e != DragExit.EDGE_NONE) {
            throw new AssertionError("center should be none, got " + e);
        }
    }

    private static void testFourEdges() {
        expect("left", DragExit.EDGE_LEFT, DragExit.nearestEdge(10f, 50f, L, R, T, B, PAD));
        expect("right", DragExit.EDGE_RIGHT, DragExit.nearestEdge(190f, 50f, L, R, T, B, PAD));
        expect("top", DragExit.EDGE_TOP, DragExit.nearestEdge(100f, 10f, L, R, T, B, PAD));
        expect("bottom", DragExit.EDGE_BOTTOM, DragExit.nearestEdge(100f, 90f, L, R, T, B, PAD));
    }

    private static void testPastEdgeStillCounts() {
        expect("past left", DragExit.EDGE_LEFT,
                DragExit.nearestEdge(-20f, 50f, L, R, T, B, PAD));
        expect("past top", DragExit.EDGE_TOP,
                DragExit.nearestEdge(100f, -8f, L, R, T, B, PAD));
        expect("past bottom", DragExit.EDGE_BOTTOM,
                DragExit.nearestEdge(100f, 140f, L, R, T, B, PAD));
    }

    private static void testPadLineInclusive() {
        expect("left pad line", DragExit.EDGE_LEFT,
                DragExit.nearestEdge(L + PAD, 50f, L, R, T, B, PAD));
        expect("just inside left pad", DragExit.EDGE_NONE,
                DragExit.nearestEdge(L + PAD + 0.5f, 50f, L, R, T, B, PAD));
        expect("right pad line", DragExit.EDGE_RIGHT,
                DragExit.nearestEdge(R - PAD, 50f, L, R, T, B, PAD));
        expect("top pad line", DragExit.EDGE_TOP,
                DragExit.nearestEdge(100f, T + PAD, L, R, T, B, PAD));
        expect("bottom pad line", DragExit.EDGE_BOTTOM,
                DragExit.nearestEdge(100f, B - PAD, L, R, T, B, PAD));
    }

    private static void testCornerNearerWins() {
        // 20px into left pad, 5px into top pad → left
        expect("deeper left", DragExit.EDGE_LEFT,
                DragExit.nearestEdge(10f, 25f, L, R, T, B, PAD));
        // 5px into left, 20px into top → top
        expect("deeper top", DragExit.EDGE_TOP,
                DragExit.nearestEdge(25f, 10f, L, R, T, B, PAD));
        // far off the left but also in the top pad: left penetration is larger
        expect("way left beats slight top", DragExit.EDGE_LEFT,
                DragExit.nearestEdge(-40f, 10f, L, R, T, B, PAD));
    }

    private static void testCornerTiePrefersLeft() {
        expect("equal left/top", DragExit.EDGE_LEFT,
                DragExit.nearestEdge(10f, 10f, L, R, T, B, PAD));
    }

    private static void testSampleMovement() {
        if (!WanderTarget.MOVE_HORIZONTAL.equals(
                DragExit.sampleMovementForEdge(DragExit.EDGE_LEFT))) {
            throw new AssertionError("left sample");
        }
        if (!WanderTarget.MOVE_HORIZONTAL.equals(
                DragExit.sampleMovementForEdge(DragExit.EDGE_RIGHT))) {
            throw new AssertionError("right sample");
        }
        if (!WanderTarget.MOVE_VERTICAL.equals(
                DragExit.sampleMovementForEdge(DragExit.EDGE_TOP))) {
            throw new AssertionError("top sample");
        }
        if (!WanderTarget.MOVE_VERTICAL.equals(
                DragExit.sampleMovementForEdge(DragExit.EDGE_BOTTOM))) {
            throw new AssertionError("bottom sample");
        }
        if (!WanderTarget.MOVE_HORIZONTAL.equals(
                DragExit.sampleMovementForEdge(DragExit.EDGE_NONE))) {
            throw new AssertionError("none sample defaults horizontal");
        }
    }

    private static void testTravelMatchesHorizontal() {
        String w = WanderTarget.WANDER_BOTH;
        String move = WanderTarget.MOVE_INHERIT;
        if (!DragExit.travelMatchesEdge(w, move, DragExit.EDGE_LEFT)) {
            throw new AssertionError("inherit should match left");
        }
        if (DragExit.travelMatchesEdge(w, move, DragExit.EDGE_TOP)) {
            throw new AssertionError("inherit must not match top");
        }
        if (DragExit.travelMatchesEdge(w, move, DragExit.EDGE_NONE)) {
            throw new AssertionError("none never matches");
        }
    }

    private static void testTravelMatchesVertical() {
        String w = WanderTarget.WANDER_BOTH;
        String move = WanderTarget.MOVE_SOFT_VERTICAL;
        if (!DragExit.travelMatchesEdge(w, move, DragExit.EDGE_TOP)) {
            throw new AssertionError("soft_vertical should match top");
        }
        if (!DragExit.travelMatchesEdge(w, move, DragExit.EDGE_BOTTOM)) {
            throw new AssertionError("soft_vertical should match bottom");
        }
        if (DragExit.travelMatchesEdge(w, move, DragExit.EDGE_LEFT)) {
            throw new AssertionError("soft_vertical must not match left");
        }
        if (!DragExit.travelMatchesEdge(w, WanderTarget.MOVE_VERTICAL,
                DragExit.EDGE_BOTTOM)) {
            throw new AssertionError("hard vertical should match bottom");
        }
    }

    private static void testAnyMatchesSidesOnly() {
        String w = WanderTarget.WANDER_BOTH;
        String move = WanderTarget.MOVE_ANY;
        if (!DragExit.travelMatchesEdge(w, move, DragExit.EDGE_RIGHT)) {
            throw new AssertionError("any should match right");
        }
        if (DragExit.travelMatchesEdge(w, move, DragExit.EDGE_TOP)) {
            throw new AssertionError("any must not match top");
        }
    }

    private static void testVerticalPonyInheritIsVertical() {
        String w = WanderTarget.WANDER_VERTICAL;
        String move = WanderTarget.MOVE_INHERIT;
        if (!DragExit.travelMatchesEdge(w, move, DragExit.EDGE_TOP)) {
            throw new AssertionError("vertical pony inherit matches top");
        }
        if (DragExit.travelMatchesEdge(w, move, DragExit.EDGE_LEFT)) {
            throw new AssertionError("vertical pony inherit must not match left");
        }
        // sampleMovementForEdge(left) is hard horizontal so a left throw still
        // asks for a side-facing clip, not the inherit shim.
        if (WanderTarget.usesVerticalFacing(w,
                DragExit.sampleMovementForEdge(DragExit.EDGE_LEFT))) {
            throw new AssertionError("left sample must stay side-facing on vertical ponies");
        }
    }

    private static void testHardHorizontalOnVerticalPony() {
        String w = WanderTarget.WANDER_VERTICAL;
        if (!DragExit.travelMatchesEdge(w, WanderTarget.MOVE_HORIZONTAL,
                DragExit.EDGE_LEFT)) {
            throw new AssertionError("hard H on vertical pony matches left");
        }
        if (DragExit.travelMatchesEdge(w, WanderTarget.MOVE_HORIZONTAL,
                DragExit.EDGE_TOP)) {
            throw new AssertionError("hard H on vertical pony must not match top");
        }
    }

    private static void expect(String label, int want, int got) {
        if (want != got) {
            throw new AssertionError(label + ": expected " + want + ", got " + got);
        }
    }
}
