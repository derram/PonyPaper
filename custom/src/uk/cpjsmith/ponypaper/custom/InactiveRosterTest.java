package uk.cpjsmith.ponypaper.custom;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import uk.cpjsmith.ponypaper.InactiveRoster;

/**
 * Checks key-only inactive roster pick/skip. Run via
 * {@code ./gradlew :custom:testInactiveRoster}.
 */
public final class InactiveRosterTest {

    private InactiveRosterTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("setAllIgnoresEmpty", InactiveRosterTest::testSetAllIgnoresEmpty);
        failures += run("pickSkipsPrefetchedKeys", InactiveRosterTest::testPickSkipsPrefetchedKeys);
        failures += run("pickWaifuAmongUnskipped", InactiveRosterTest::testPickWaifuAmongUnskipped);
        failures += run("removeKeyThenPick", InactiveRosterTest::testRemoveKeyThenPick);
        failures += run("contains", InactiveRosterTest::testContains);
        if (failures > 0) {
            System.err.println(failures + " inactive-roster check(s) failed.");
            System.exit(1);
        }
        System.out.println("InactiveRoster checks passed.");
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

    private static void testSetAllIgnoresEmpty() {
        InactiveRoster roster = new InactiveRoster();
        roster.setAll(Arrays.asList("pref_aj", "", null, "pref_ts"));
        if (roster.size() != 2) {
            throw new AssertionError("expected 2 keys, got " + roster.size());
        }
        if (!"pref_aj".equals(roster.get(0)) || !"pref_ts".equals(roster.get(1))) {
            throw new AssertionError("load order not preserved");
        }
    }

    private static void testPickSkipsPrefetchedKeys() {
        InactiveRoster roster = new InactiveRoster();
        roster.setAll(Arrays.asList("a", "b", "c"));
        HashSet<String> skip = new HashSet<String>(Arrays.asList("a", "c"));
        int idx = roster.pickIndex(skip, "", new Random(0));
        if (idx != 1) {
            throw new AssertionError("only b remains, got " + idx);
        }
        if (roster.pickIndex(skip, "a", new Random(1)) != 1) {
            throw new AssertionError("skipped waifu must fall back to b");
        }
    }

    private static void testPickWaifuAmongUnskipped() {
        InactiveRoster roster = new InactiveRoster();
        roster.setAll(Arrays.asList("pref_aj", "pref_ts", "pref_rd"));
        for (int seed = 0; seed < 20; seed++) {
            int idx = roster.pickIndex(Collections.<String>emptySet(), "pref_ts",
                    new Random(seed));
            if (idx != 1) {
                throw new AssertionError("waifu should win; seed " + seed + " got " + idx);
            }
        }
    }

    private static void testRemoveKeyThenPick() {
        InactiveRoster roster = new InactiveRoster();
        roster.setAll(Arrays.asList("a", "b", "c"));
        if (!roster.removeKey("b")) {
            throw new AssertionError("removeKey should find b");
        }
        if (roster.size() != 2) {
            throw new AssertionError("expected 2 after remove, got " + roster.size());
        }
        int idx = roster.pickIndex(null, "", new Random(0));
        if (idx != 0 && idx != 1) {
            throw new AssertionError("pick among a,c got " + idx);
        }
        if (!"a".equals(roster.get(0)) || !"c".equals(roster.get(1))) {
            throw new AssertionError("remaining keys should be a,c");
        }
        roster.add("b");
        if (roster.size() != 3 || !"b".equals(roster.get(2))) {
            throw new AssertionError("add should append retired key");
        }
    }

    private static void testContains() {
        InactiveRoster roster = new InactiveRoster();
        roster.setAll(Arrays.asList("pref_aj", "pref_ts"));
        if (!roster.contains("pref_ts") || roster.contains("pref_rd")) {
            throw new AssertionError("contains should match roster keys only");
        }
        if (roster.contains(null) || roster.contains("")) {
            throw new AssertionError("null/empty must not match");
        }
    }
}
