package uk.cpjsmith.ponypaper.custom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import uk.cpjsmith.ponypaper.InactiveRoster;
import uk.cpjsmith.ponypaper.WorldFlowCast;

/**
 * Checks World Flow unique-key cast sampling and drip-rotate.
 * Run via {@code ./gradlew :custom:testWorldFlowCast}.
 */
public final class WorldFlowCastTest {

    private WorldFlowCastTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("smallMixIsFullCast", WorldFlowCastTest::testSmallMixIsFullCast);
        failures += run("capsAtMax", WorldFlowCastTest::testCapsAtMax);
        failures += run("waifuAlwaysIncluded", WorldFlowCastTest::testWaifuAlwaysIncluded);
        failures += run("waifuAbsentFromMixIgnored", WorldFlowCastTest::testWaifuAbsentFromMixIgnored);
        failures += run("sameSeedSameCast", WorldFlowCastTest::testSameSeedSameCast);
        failures += run("dripBeforeIntervalNull", WorldFlowCastTest::testDripBeforeIntervalNull);
        failures += run("dripSwapsOne", WorldFlowCastTest::testDripSwapsOne);
        failures += run("longPauseOneDrip", WorldFlowCastTest::testLongPauseOneDrip);
        failures += run("noVictimDoesNotAdvanceInterval",
                WorldFlowCastTest::testNoVictimDoesNotAdvanceInterval);
        failures += run("evictOldestSeenBeforeUnseen",
                WorldFlowCastTest::testEvictOldestSeenBeforeUnseen);
        failures += run("skipHeldAndWaifu", WorldFlowCastTest::testSkipHeldAndWaifu);
        failures += run("admittedNotImmediateVictim",
                WorldFlowCastTest::testAdmittedNotImmediateVictim);
        if (failures > 0) {
            System.err.println(failures + " world-flow-cast check(s) failed.");
            System.exit(1);
        }
        System.out.println("WorldFlowCast checks passed.");
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

    private static List<String> keys(int n) {
        ArrayList<String> out = new ArrayList<String>(n);
        for (int i = 0; i < n; i++) {
            out.add("k" + i);
        }
        return out;
    }

    private static void testSmallMixIsFullCast() {
        List<String> mix = Arrays.asList("a", "", null, "b", "a", "c");
        WorldFlowCast cast = WorldFlowCast.open(mix, "a", new Random(1));
        if (cast.mixSize() != 3 || cast.size() != 3 || cast.reservoirSize() != 0) {
            throw new AssertionError("unique mix should be the whole cast, got mix="
                    + cast.mixSize() + " cast=" + cast.size() + " res="
                    + cast.reservoirSize());
        }
        if (!cast.contains("a") || !cast.contains("b") || !cast.contains("c")) {
            throw new AssertionError("all unique keys should be in the cast");
        }
        InactiveRoster inactive = new InactiveRoster();
        inactive.setAll(cast.castKeys());
        cast.advance(WorldFlowCast.DRIP_INTERVAL_MS);
        if (cast.tryDrip(inactive, null, "a", new Random(1)) != null) {
            throw new AssertionError("empty reservoir must not drip");
        }
    }

    private static void testCapsAtMax() {
        WorldFlowCast cast = WorldFlowCast.open(keys(25), "", new Random(2));
        if (cast.mixSize() != 25 || cast.size() != WorldFlowCast.MAX_KEYS
                || cast.reservoirSize() != 5) {
            throw new AssertionError("expected 20/25 with 5 in reservoir, got mix="
                    + cast.mixSize() + " cast=" + cast.size() + " res="
                    + cast.reservoirSize());
        }
        HashSet<String> window = new HashSet<String>(cast.castKeys());
        if (window.size() != WorldFlowCast.MAX_KEYS) {
            throw new AssertionError("cast keys must be unique");
        }
    }

    private static void testWaifuAlwaysIncluded() {
        String waifu = "k24";
        for (int seed = 0; seed < 40; seed++) {
            WorldFlowCast cast = WorldFlowCast.open(keys(25), waifu, new Random(seed));
            if (!cast.contains(waifu)) {
                throw new AssertionError("waifu missing from cast at seed " + seed);
            }
            ArrayList<String> order = cast.castKeys();
            if (!waifu.equals(order.get(0))) {
                throw new AssertionError("waifu should be first in sample order");
            }
        }
    }

    private static void testWaifuAbsentFromMixIgnored() {
        WorldFlowCast cast = WorldFlowCast.open(keys(5), "other", new Random(3), 5, 1000L);
        if (cast.contains("other") || cast.size() != 5) {
            throw new AssertionError("absent waifu must not enter the cast");
        }
    }

    private static void testSameSeedSameCast() {
        List<String> mix = keys(30);
        WorldFlowCast a = WorldFlowCast.open(mix, "k0", new Random(99));
        WorldFlowCast b = WorldFlowCast.open(mix, "k0", new Random(99));
        if (!a.castKeys().equals(b.castKeys())) {
            throw new AssertionError("same seed should sample the same window");
        }
    }

    private static void testDripBeforeIntervalNull() {
        WorldFlowCast cast = WorldFlowCast.open(keys(6), "", new Random(4), 3, 1000L);
        InactiveRoster inactive = new InactiveRoster();
        inactive.setAll(cast.castKeys());
        if (cast.tryDrip(inactive, null, "", new Random(4)) != null) {
            throw new AssertionError("drip before interval");
        }
        cast.advance(999L);
        if (cast.tryDrip(inactive, null, "", new Random(4)) != null) {
            throw new AssertionError("drip at 999ms of 1000ms interval");
        }
    }

    private static void testDripSwapsOne() {
        WorldFlowCast cast = WorldFlowCast.open(keys(6), "", new Random(5), 3, 1000L);
        HashSet<String> before = new HashSet<String>(cast.castKeys());
        HashSet<String> resBefore = reservoirOf(cast);
        InactiveRoster inactive = new InactiveRoster();
        inactive.setAll(cast.castKeys());
        cast.advance(1000L);
        WorldFlowCast.Drip drip = cast.tryDrip(inactive, null, "", new Random(5));
        if (drip == null) {
            throw new AssertionError("expected a drip at interval");
        }
        if (!before.contains(drip.evicted) || !resBefore.contains(drip.admitted)) {
            throw new AssertionError("drip should move a cast key to reservoir and back");
        }
        if (cast.size() != 3 || cast.reservoirSize() != 3) {
            throw new AssertionError("window size must stay 3, got " + cast.size()
                    + " res " + cast.reservoirSize());
        }
        if (cast.contains(drip.evicted) || !cast.contains(drip.admitted)) {
            throw new AssertionError("membership after drip");
        }
        if (cast.tryDrip(inactive, null, "", new Random(5)) != null) {
            throw new AssertionError("second drip in the same instant");
        }
    }

    private static void testLongPauseOneDrip() {
        WorldFlowCast cast = WorldFlowCast.open(keys(6), "", new Random(6), 3, 1000L);
        InactiveRoster inactive = new InactiveRoster();
        inactive.setAll(cast.castKeys());
        cast.advance(50_000L);
        WorldFlowCast.Drip first = cast.tryDrip(inactive, null, "", new Random(6));
        if (first == null) {
            throw new AssertionError("long pause should still drip once");
        }
        if (cast.tryDrip(inactive, null, "", new Random(6)) != null) {
            throw new AssertionError("long pause must not catch up multiple drips");
        }
    }

    private static void testNoVictimDoesNotAdvanceInterval() {
        WorldFlowCast cast = WorldFlowCast.open(keys(6), "", new Random(7), 3, 1000L);
        InactiveRoster inactive = new InactiveRoster();
        HashSet<String> held = new HashSet<String>(cast.castKeys());
        cast.advance(1000L);
        if (cast.tryDrip(inactive, held, "", new Random(7)) != null) {
            throw new AssertionError("no inactive victim");
        }
        inactive.setAll(cast.castKeys());
        WorldFlowCast.Drip drip = cast.tryDrip(inactive, null, "", new Random(7));
        if (drip == null) {
            throw new AssertionError("retry same interval once a victim exists");
        }
    }

    private static void testEvictOldestSeenBeforeUnseen() {
        WorldFlowCast cast = WorldFlowCast.open(keys(5), "", new Random(8), 3, 1000L);
        ArrayList<String> window = cast.castKeys();
        String oldest = window.get(0);
        String newer = window.get(1);
        InactiveRoster inactive = new InactiveRoster();
        inactive.setAll(window);
        cast.advance(10L);
        cast.noteOnScreen(oldest);
        cast.advance(20L);
        cast.noteOnScreen(newer);
        cast.advance(1000L);
        WorldFlowCast.Drip drip = cast.tryDrip(inactive, null, "", new Random(8));
        if (drip == null || !oldest.equals(drip.evicted)) {
            throw new AssertionError("should evict oldest seen, got "
                    + (drip == null ? "null" : drip.evicted));
        }
    }

    private static void testSkipHeldAndWaifu() {
        WorldFlowCast cast = WorldFlowCast.open(keys(6), "k0", new Random(9), 3, 1000L);
        ArrayList<String> window = cast.castKeys();
        if (!"k0".equals(window.get(0))) {
            throw new AssertionError("expected waifu first");
        }
        String held = window.get(1);
        String victim = window.get(2);
        InactiveRoster inactive = new InactiveRoster();
        inactive.setAll(window);
        HashSet<String> skip = new HashSet<String>();
        skip.add(held);
        cast.advance(5L);
        cast.noteOnScreen(victim);
        cast.advance(1000L);
        WorldFlowCast.Drip drip = cast.tryDrip(inactive, skip, "k0", new Random(9));
        if (drip == null || !victim.equals(drip.evicted)) {
            throw new AssertionError("should skip waifu and held, evict " + victim
                    + ", got " + (drip == null ? "null" : drip.evicted));
        }
    }

    private static void testAdmittedNotImmediateVictim() {
        WorldFlowCast cast = WorldFlowCast.open(keys(6), "", new Random(10), 3, 1000L);
        InactiveRoster inactive = new InactiveRoster();
        inactive.setAll(cast.castKeys());
        for (int i = 0; i < 3; i++) {
            cast.advance(1L);
            cast.noteOnScreen(cast.castKeys().get(i));
        }
        cast.advance(1000L);
        WorldFlowCast.Drip first = cast.tryDrip(inactive, null, "", new Random(10));
        if (first == null) {
            throw new AssertionError("first drip");
        }
        inactive.removeKey(first.evicted);
        inactive.add(first.admitted);
        cast.advance(1000L);
        WorldFlowCast.Drip second = cast.tryDrip(inactive, null, "", new Random(11));
        if (second == null) {
            throw new AssertionError("second drip");
        }
        if (first.admitted.equals(second.evicted)) {
            throw new AssertionError("freshly admitted key should not be the next victim");
        }
    }

    private static HashSet<String> reservoirOf(WorldFlowCast cast) {
        HashSet<String> mix = new HashSet<String>(keys(6));
        mix.removeAll(cast.castKeys());
        return mix;
    }
}
