package uk.cpjsmith.ponypaper.custom;

import java.util.Random;
import uk.cpjsmith.ponypaper.InactivePick;

/**
 * Checks inactive-pool pick (waifu preference, skip already-prefetched).
 * Run via {@code ./gradlew :custom:testInactivePick}.
 */
public final class InactivePickTest {

    private InactivePickTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("empty", InactivePickTest::testEmpty);
        failures += run("skipAll", InactivePickTest::testSkipAll);
        failures += run("waifuPreferred", InactivePickTest::testWaifuPreferred);
        failures += run("waifuSkippedFallsBack", InactivePickTest::testWaifuSkippedFallsBack);
        failures += run("noWaifuSkipsPrefetch", InactivePickTest::testNoWaifuSkipsPrefetch);
        failures += run("multipleWaifu", InactivePickTest::testMultipleWaifu);
        if (failures > 0) {
            System.err.println(failures + " inactive-pick check(s) failed.");
            System.exit(1);
        }
        System.out.println("InactivePick checks passed.");
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

    private static void testEmpty() {
        Random r = new Random(1);
        if (InactivePick.index(0, new String[0], null, "", r) != -1) {
            throw new AssertionError("empty pool");
        }
        if (InactivePick.index(3, keys("a", "b", "c"), null, "", null) != -1) {
            throw new AssertionError("null random");
        }
    }

    private static void testSkipAll() {
        boolean[] skip = {true, true, true};
        int idx = InactivePick.index(3, keys("a", "b", "c"), skip, "a", new Random(1));
        if (idx != -1) {
            throw new AssertionError("all skipped should be -1, got " + idx);
        }
    }

    private static void testWaifuPreferred() {
        String[] pref = keys("pref_aj", "pref_ts", "pref_rd");
        for (int seed = 0; seed < 20; seed++) {
            int idx = InactivePick.index(3, pref, null, "pref_ts", new Random(seed));
            if (idx != 1) {
                throw new AssertionError("waifu should always win; seed " + seed + " got "
                        + idx);
            }
        }
    }

    private static void testWaifuSkippedFallsBack() {
        String[] pref = keys("pref_aj", "pref_ts", "pref_rd");
        boolean[] skip = {false, true, false};
        int idx = InactivePick.index(3, pref, skip, "pref_ts", new Random(0));
        if (idx != 0 && idx != 2) {
            throw new AssertionError("skipped waifu should pick among others, got " + idx);
        }
        skip = new boolean[] {true, true, false};
        idx = InactivePick.index(3, pref, skip, "pref_ts", new Random(0));
        if (idx != 2) {
            throw new AssertionError("only rd remains, got " + idx);
        }
    }

    private static void testNoWaifuSkipsPrefetch() {
        String[] pref = keys("a", "b", "c");
        boolean[] skip = {true, false, true};
        int idx = InactivePick.index(3, pref, skip, "", new Random(0));
        if (idx != 1) {
            throw new AssertionError("only slot 1 eligible, got " + idx);
        }
    }

    private static void testMultipleWaifu() {
        String[] pref = keys("pref_ts", "pref_aj", "pref_ts");
        boolean[] skip = {true, false, false};
        int idx = InactivePick.index(3, pref, skip, "pref_ts", new Random(0));
        if (idx != 2) {
            throw new AssertionError("only remaining waifu is slot 2, got " + idx);
        }
    }

    private static String[] keys(String... keys) {
        return keys;
    }
}
