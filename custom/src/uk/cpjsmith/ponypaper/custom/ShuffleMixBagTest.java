package uk.cpjsmith.ponypaper.custom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import uk.cpjsmith.ponypaper.ShuffleMixBag;

/**
 * Checks dream-shuffle include-set filtering (absent = all).
 * Run via {@code ./gradlew :custom:testShuffleMixBag}.
 */
public final class ShuffleMixBagTest {

    private ShuffleMixBagTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("nullIncludeIsAll", ShuffleMixBagTest::testNullIncludeIsAll);
        failures += run("emptyIncludeIsNone", ShuffleMixBagTest::testEmptyIncludeIsNone);
        failures += run("subsetPreservesOrder", ShuffleMixBagTest::testSubsetPreservesOrder);
        failures += run("skipsEmptyIds", ShuffleMixBagTest::testSkipsEmptyIds);
        failures += run("ignoresStaleInclude", ShuffleMixBagTest::testIgnoresStaleInclude);
        failures += run("nullCandidates", ShuffleMixBagTest::testNullCandidates);
        failures += run("coversAllNullChecked", ShuffleMixBagTest::testCoversAllNullChecked);
        failures += run("coversAllComplete", ShuffleMixBagTest::testCoversAllComplete);
        failures += run("coversAllMissingOne", ShuffleMixBagTest::testCoversAllMissingOne);
        failures += run("coversAllExtraIds", ShuffleMixBagTest::testCoversAllExtraIds);
        failures += run("coversAllEmptyCandidates", ShuffleMixBagTest::testCoversAllEmptyCandidates);
        if (failures > 0) {
            System.err.println(failures + " shuffle-bag check(s) failed.");
            System.exit(1);
        }
        System.out.println("ShuffleMixBag checks passed.");
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

    private static void testNullIncludeIsAll() {
        List<String> ids = Arrays.asList("a", "b", "__previous_herd__");
        ArrayList<String> out = ShuffleMixBag.filterIds(ids, null);
        if (!ids.equals(out)) {
            throw new AssertionError("null include should keep all, got " + out);
        }
    }

    private static void testEmptyIncludeIsNone() {
        List<String> ids = Arrays.asList("a", "b");
        ArrayList<String> out = ShuffleMixBag.filterIds(ids, new HashSet<String>());
        if (!out.isEmpty()) {
            throw new AssertionError("empty include should keep none, got " + out);
        }
    }

    private static void testSubsetPreservesOrder() {
        List<String> ids = Arrays.asList("a", "b", "c");
        HashSet<String> include = new HashSet<String>(Arrays.asList("c", "a"));
        ArrayList<String> out = ShuffleMixBag.filterIds(ids, include);
        if (!Arrays.asList("a", "c").equals(out)) {
            throw new AssertionError("expected [a, c], got " + out);
        }
    }

    private static void testSkipsEmptyIds() {
        List<String> ids = Arrays.asList("", "a", null, "b");
        ArrayList<String> out = ShuffleMixBag.filterIds(ids, null);
        if (!Arrays.asList("a", "b").equals(out)) {
            throw new AssertionError("empty ids should drop, got " + out);
        }
    }

    private static void testIgnoresStaleInclude() {
        List<String> ids = Arrays.asList("a", "b");
        HashSet<String> include = new HashSet<String>(Arrays.asList("a", "gone"));
        ArrayList<String> out = ShuffleMixBag.filterIds(ids, include);
        if (!Arrays.asList("a").equals(out)) {
            throw new AssertionError("stale ids should drop, got " + out);
        }
    }

    private static void testNullCandidates() {
        ArrayList<String> out = ShuffleMixBag.filterIds(null, null);
        if (!out.isEmpty()) {
            throw new AssertionError("null candidates should be empty, got " + out);
        }
    }

    private static void testCoversAllNullChecked() {
        if (!ShuffleMixBag.coversAll(Arrays.asList("a"), null)) {
            throw new AssertionError("null checked means all");
        }
    }

    private static void testCoversAllComplete() {
        HashSet<String> checked = new HashSet<String>(Arrays.asList("a", "b"));
        if (!ShuffleMixBag.coversAll(Arrays.asList("a", "b"), checked)) {
            throw new AssertionError("full check should cover all");
        }
    }

    private static void testCoversAllMissingOne() {
        HashSet<String> checked = new HashSet<String>(Arrays.asList("a"));
        if (ShuffleMixBag.coversAll(Arrays.asList("a", "b"), checked)) {
            throw new AssertionError("missing b should not cover all");
        }
    }

    private static void testCoversAllExtraIds() {
        HashSet<String> checked = new HashSet<String>(Arrays.asList("a", "b", "extra"));
        if (!ShuffleMixBag.coversAll(Arrays.asList("a", "b"), checked)) {
            throw new AssertionError("extra ids should still cover all");
        }
    }

    private static void testCoversAllEmptyCandidates() {
        HashSet<String> checked = new HashSet<String>();
        if (!ShuffleMixBag.coversAll(new ArrayList<String>(), checked)) {
            throw new AssertionError("no candidates is covered");
        }
    }
}
