package uk.cpjsmith.ponypaper.custom;

import java.util.Random;
import uk.cpjsmith.ponypaper.HerdDrain;

/**
 * Checks herd-drain stagger, timeout, refill gating, and exit decisions.
 * Run via {@code ./gradlew :custom:testHerdDrain} or {@code java … HerdDrainTest}.
 */
public final class HerdDrainTest {

    private HerdDrainTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("staggerLeavingIsZero", HerdDrainTest::testStaggerLeavingIsZero);
        failures += run("staggerTravelingIsZero", HerdDrainTest::testStaggerTravelingIsZero);
        failures += run("staggerCapsWait", HerdDrainTest::testStaggerCapsWait);
        failures += run("staggerSpreadsByIndex", HerdDrainTest::testStaggerSpreadsByIndex);
        failures += run("staggerNegativeWait", HerdDrainTest::testStaggerNegativeWait);
        failures += run("timeout", HerdDrainTest::testTimeout);
        failures += run("refill", HerdDrainTest::testRefill);
        failures += run("complete", HerdDrainTest::testComplete);
        failures += run("decidePinned", HerdDrainTest::testDecidePinned);
        failures += run("decideAlreadyLeaving", HerdDrainTest::testDecideAlreadyLeaving);
        failures += run("decideAlreadyLeavingWalkRetargets",
                HerdDrainTest::testDecideAlreadyLeavingWalkRetargets);
        failures += run("decideNoBoundsAndSpawn", HerdDrainTest::testDecideNoBoundsAndSpawn);
        failures += run("decideDrag", HerdDrainTest::testDecideDrag);
        failures += run("decideWorldFlow", HerdDrainTest::testDecideWorldFlow);
        failures += run("decideRetarget", HerdDrainTest::testDecideRetarget);
        failures += run("decideBeginLeave", HerdDrainTest::testDecideBeginLeave);
        failures += run("pickFastestEmpty", HerdDrainTest::testPickFastestEmpty);
        failures += run("pickFastestSkipsIneligible", HerdDrainTest::testPickFastestSkipsIneligible);
        failures += run("pickFastestPrefersTrot", HerdDrainTest::testPickFastestPrefersTrot);
        failures += run("pickFastestFluttershyCap", HerdDrainTest::testPickFastestFluttershyCap);
        failures += run("pickFastestTiesUniform", HerdDrainTest::testPickFastestTiesUniform);
        failures += run("nearerGutter", HerdDrainTest::testNearerGutter);
        failures += run("rosterReloadWhileDraining",
                HerdDrainTest::testRosterReloadWhileDraining);
        failures += run("generationEcho", HerdDrainTest::testGenerationEcho);
        if (failures > 0) {
            System.err.println(failures + " herd-drain check(s) failed.");
            System.exit(1);
        }
        System.out.println("HerdDrain checks passed.");
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

    private static void testStaggerLeavingIsZero() {
        int d = HerdDrain.staggerDelayMs(true, false, 5000f, 3);
        if (d != 0) {
            throw new AssertionError("leaving should not wait, got " + d);
        }
    }

    private static void testStaggerTravelingIsZero() {
        int d = HerdDrain.staggerDelayMs(false, true, 5000f, 2);
        if (d != 0) {
            throw new AssertionError("travel/spawn should exit now, got " + d);
        }
    }

    private static void testStaggerCapsWait() {
        int d = HerdDrain.staggerDelayMs(false, false, 12_000f, 0);
        if (d != HerdDrain.STAGGER_MAX_MS) {
            throw new AssertionError("expected cap " + HerdDrain.STAGGER_MAX_MS + ", got " + d);
        }
    }

    private static void testStaggerSpreadsByIndex() {
        int d0 = HerdDrain.staggerDelayMs(false, false, 100f, 0);
        int d2 = HerdDrain.staggerDelayMs(false, false, 100f, 2);
        if (d0 != 100) {
            throw new AssertionError("index 0 should keep wait, got " + d0);
        }
        if (d2 != 100 + 2 * HerdDrain.STAGGER_INDEX_MS) {
            throw new AssertionError("index 2 spread, got " + d2);
        }
    }

    private static void testStaggerNegativeWait() {
        int d = HerdDrain.staggerDelayMs(false, false, -20f, 1);
        if (d != HerdDrain.STAGGER_INDEX_MS) {
            throw new AssertionError("negative wait should be 0 + index, got " + d);
        }
    }

    private static void testTimeout() {
        if (HerdDrain.timedOut(0, 10_000)) {
            throw new AssertionError("unset start should not time out");
        }
        if (HerdDrain.timedOut(1000, 1000 + HerdDrain.TIMEOUT_MS - 1)) {
            throw new AssertionError("just under timeout");
        }
        if (!HerdDrain.timedOut(1000, 1000 + HerdDrain.TIMEOUT_MS)) {
            throw new AssertionError("at timeout should fire");
        }
    }

    private static void testRefill() {
        if (!HerdDrain.shouldRefill(false)) {
            throw new AssertionError("live herd still refills");
        }
        if (HerdDrain.shouldRefill(true)) {
            throw new AssertionError("drain must not refill");
        }
    }

    private static void testComplete() {
        if (HerdDrain.isComplete(false, 0)) {
            throw new AssertionError("not draining");
        }
        if (HerdDrain.isComplete(true, 2)) {
            throw new AssertionError("still has active ponies");
        }
        if (!HerdDrain.isComplete(true, 0)) {
            throw new AssertionError("empty drain is complete");
        }
    }

    private static void testDecidePinned() {
        int a = HerdDrain.decideExit(true, false, false, false, false, false, false, true);
        if (a != HerdDrain.EXIT_SKIP) {
            throw new AssertionError("pinned → SKIP, got " + a);
        }
    }

    private static void testDecideAlreadyLeaving() {
        int vanish = HerdDrain.decideExit(false, false, true, false, false, false, false, true);
        if (vanish != HerdDrain.EXIT_NOOP) {
            throw new AssertionError("vanish clip → NOOP, got " + vanish);
        }
        int gone = HerdDrain.decideExit(false, false, false, true, false, false, false, true);
        if (gone != HerdDrain.EXIT_NOOP) {
            throw new AssertionError("already gone → NOOP, got " + gone);
        }
    }

    private static void testDecideAlreadyLeavingWalkRetargets() {
        int a = HerdDrain.decideExit(false, false, true, false, false, false, true, true);
        if (a != HerdDrain.EXIT_RETARGET) {
            throw new AssertionError("leaving walk → RETARGET nearer gutter, got " + a);
        }
        int wf = HerdDrain.decideExit(false, true, true, false, false, false, true, true);
        if (wf != HerdDrain.EXIT_NOOP) {
            throw new AssertionError("leaving World Flow walk keeps crossing, got " + wf);
        }
    }

    private static void testDecideNoBoundsAndSpawn() {
        int noBounds = HerdDrain.decideExit(false, false, false, false, false, false,
                false, false);
        if (noBounds != HerdDrain.EXIT_MARK_GONE) {
            throw new AssertionError("no clip → GONE, got " + noBounds);
        }
        int spawn = HerdDrain.decideExit(false, true, false, false, false, true, false, true);
        if (spawn != HerdDrain.EXIT_MARK_GONE) {
            throw new AssertionError("spawn → GONE (before World Flow enter), got " + spawn);
        }
    }

    private static void testDecideDrag() {
        int a = HerdDrain.decideExit(false, false, false, false, true, false, false, true);
        if (a != HerdDrain.EXIT_DEFER_DRAG) {
            throw new AssertionError("drag → DEFER, got " + a);
        }
    }

    private static void testDecideWorldFlow() {
        int a = HerdDrain.decideExit(false, true, false, false, false, false, true, true);
        if (a != HerdDrain.EXIT_WORLD_FLOW) {
            throw new AssertionError("world flow → crossing resume, got " + a);
        }
    }

    private static void testDecideRetarget() {
        int a = HerdDrain.decideExit(false, false, false, false, false, false, true, true);
        if (a != HerdDrain.EXIT_RETARGET) {
            throw new AssertionError("mid-walk → RETARGET, got " + a);
        }
    }

    private static void testDecideBeginLeave() {
        int a = HerdDrain.decideExit(false, false, false, false, false, false, false, true);
        if (a != HerdDrain.EXIT_BEGIN_LEAVE) {
            throw new AssertionError("idle → BEGIN_LEAVE, got " + a);
        }
    }

    private static void testPickFastestEmpty() {
        if (HerdDrain.pickFastestIndex(null, new Random(1)) != -1) {
            throw new AssertionError("null speeds");
        }
        if (HerdDrain.pickFastestIndex(new float[0], new Random(1)) != -1) {
            throw new AssertionError("empty speeds");
        }
        if (HerdDrain.pickFastestIndex(new float[] {0.5f, 1f}, null) != -1) {
            throw new AssertionError("null random");
        }
        if (HerdDrain.pickFastestIndex(new float[] {0f, -1f, Float.NaN}, new Random(1)) != -1) {
            throw new AssertionError("all ineligible");
        }
    }

    private static void testPickFastestSkipsIneligible() {
        // 0 = screen-in / missing; 0.5 stroll should lose to 1.0
        float[] speeds = {0f, 0.5f, 0f, 1f};
        int idx = HerdDrain.pickFastestIndex(speeds, new Random(0));
        if (idx != 3) {
            throw new AssertionError("expected trot slot 3, got " + idx);
        }
    }

    private static void testPickFastestPrefersTrot() {
        // Built-in defaultGaits: stroll, walk, walk, walk, trot
        float[] bag = {0.5f, 0.7f, 0.7f, 0.7f, 1f};
        for (int seed = 0; seed < 40; seed++) {
            int idx = HerdDrain.pickFastestIndex(bag, new Random(seed));
            if (idx != 4) {
                throw new AssertionError("stroll/walk must not win drain; seed "
                        + seed + " got " + idx);
            }
        }
    }

    private static void testPickFastestFluttershyCap() {
        // No full-speed slot: fastest present (0.7) should always win.
        float[] bag = {0.5f, 0.5f, 0.7f, 0.7f, 0.7f};
        for (int seed = 0; seed < 40; seed++) {
            int idx = HerdDrain.pickFastestIndex(bag, new Random(seed));
            if (idx < 2) {
                throw new AssertionError("expected a 0.7 slot; seed " + seed + " got " + idx);
            }
        }
    }

    private static void testPickFastestTiesUniform() {
        float[] bag = {1f, 0.5f, 1f};
        int a = 0;
        int c = 0;
        Random r = new Random(1);
        for (int i = 0; i < 200; i++) {
            int idx = HerdDrain.pickFastestIndex(bag, r);
            if (idx == 0) a++;
            else if (idx == 2) c++;
            else throw new AssertionError("tie broke to ineligible " + idx);
        }
        if (a < 40 || c < 40) {
            throw new AssertionError("expected both full-speed slots; a=" + a + " c=" + c);
        }
    }

    private static void testNearerGutter() {
        if (!HerdDrain.nearerFirst(10f, 0f, 100f)) {
            throw new AssertionError("near left should take left");
        }
        if (HerdDrain.nearerFirst(90f, 0f, 100f)) {
            throw new AssertionError("near right should take right");
        }
        if (!HerdDrain.nearerFirst(50f, 0f, 100f)) {
            throw new AssertionError("centerline tie prefers first (left/top)");
        }
        // Asymmetric vertical pads: dest top = -8, dest bottom = 120 on a
        // 100-tall screen — a pony at 50 is still closer to the top dest.
        if (!HerdDrain.nearerFirst(50f, -8f, 120f)) {
            throw new AssertionError("asymmetric vertical pads: closer to top dest");
        }
        if (HerdDrain.nearerFirst(90f, -8f, 120f)) {
            throw new AssertionError("near bottom dest should take bottom");
        }
    }

    private static void testRosterReloadWhileDraining() {
        if (!HerdDrain.shouldStartRosterReload(false)) {
            throw new AssertionError("idle herd should accept a roster reload");
        }
        if (HerdDrain.shouldStartRosterReload(true)) {
            throw new AssertionError("in-flight drain must fold later roster changes");
        }
    }

    private static void testGenerationEcho() {
        if (!HerdDrain.shouldReloadForGeneration(Long.MIN_VALUE, 1L)) {
            throw new AssertionError("first generation bump should reload");
        }
        if (HerdDrain.shouldReloadForGeneration(42L, 42L)) {
            throw new AssertionError("echo of a generation this host wrote must not reload");
        }
        if (!HerdDrain.shouldReloadForGeneration(42L, 43L)) {
            throw new AssertionError("a newer generation should reload");
        }
    }
}
