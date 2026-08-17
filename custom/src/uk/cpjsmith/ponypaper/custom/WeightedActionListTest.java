package uk.cpjsmith.ponypaper.custom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uk.cpjsmith.ponypaper.PonyDefinition;
import uk.cpjsmith.ponypaper.PonyDefinition.ActionListEntry;
import uk.cpjsmith.ponypaper.PonyDefinition.WeightedToken;

/**
 * Checks {@code name:N} / repeat sugar for next/start/drag lists.
 * Run via {@code ./gradlew :custom:testWeightedLists} or
 * {@code java … WeightedActionListTest}.
 */
public final class WeightedActionListTest {

    private WeightedActionListTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("parseBareAndWeighted", WeightedActionListTest::testParseBareAndWeighted);
        failures += run("parseMixedRepeats", WeightedActionListTest::testParseMixedRepeats);
        failures += run("parseErrors", WeightedActionListTest::testParseErrors);
        failures += run("noneCannotHaveWeight", WeightedActionListTest::testNoneCannotHaveWeight);
        failures += run("expandEqualsRepeats", WeightedActionListTest::testExpandEqualsRepeats);
        failures += run("uniqueNamesStripWeight", WeightedActionListTest::testUniqueNamesStripWeight);
        failures += run("renamePreservesWeight", WeightedActionListTest::testRenamePreservesWeight);
        failures += run("filterDropsMissingWeighted", WeightedActionListTest::testFilterDropsMissingWeighted);
        failures += run("parseGaitsStillWorks", WeightedActionListTest::testParseGaitsStillWorks);
        if (failures > 0) {
            System.err.println(failures + " weighted-list check(s) failed.");
            System.exit(1);
        }
        System.out.println("Weighted action-list checks passed.");
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

    private static void testParseBareAndWeighted() {
        WeightedToken bare = PonyDefinition.parseWeightedToken("stand", null);
        assertNotNull(bare);
        assertEquals("stand", bare.token);
        assertEquals(1, bare.weight);
        assertEquals(false, bare.weightExplicit);

        WeightedToken weighted = PonyDefinition.parseWeightedToken("stand:3", null);
        assertNotNull(weighted);
        assertEquals("stand", weighted.token);
        assertEquals(3, weighted.weight);
        assertEquals(true, weighted.weightExplicit);

        WeightedToken spaced = PonyDefinition.parseWeightedToken(" cheer : 2 ", null);
        assertNotNull(spaced);
        assertEquals("cheer", spaced.token);
        assertEquals(2, spaced.weight);
    }

    private static void testParseMixedRepeats() {
        List<ActionListEntry> entries = PonyDefinition.parseActionList("stand:3,cheer,cheer", null);
        assertEquals(3, entries.size());
        assertEquals("stand", entries.get(0).name);
        assertEquals(3, entries.get(0).weight);
        assertEquals("cheer", entries.get(1).name);
        assertEquals(1, entries.get(1).weight);
        assertEquals("cheer", entries.get(2).name);
        assertEquals(1, entries.get(2).weight);
    }

    private static void testParseErrors() {
        List<String> errors = new ArrayList<String>();
        assertEquals(true, PonyDefinition.parseActionList("stand:", errors).isEmpty());
        assertContains(errors, "Invalid weight");

        errors.clear();
        assertEquals(true, PonyDefinition.parseActionList("stand:0", errors).isEmpty());
        assertContains(errors, "Invalid weight");

        errors.clear();
        assertEquals(true, PonyDefinition.parseActionList("stand:1.5", errors).isEmpty());
        assertContains(errors, "Invalid weight");

        errors.clear();
        String tooBig = "stand:" + (PonyDefinition.MAX_ACTION_LIST_WEIGHT + 1);
        assertEquals(true, PonyDefinition.parseActionList(tooBig, errors).isEmpty());
        assertContains(errors, "too large");
    }

    private static void testNoneCannotHaveWeight() {
        List<String> errors = new ArrayList<String>();
        assertEquals(true, PonyDefinition.parseActionList("none:3", errors).isEmpty());
        assertContains(errors, "cannot have a weight");

        errors.clear();
        List<ActionListEntry> ok = PonyDefinition.parseActionList("none", errors);
        assertEquals(0, errors.size());
        assertEquals(1, ok.size());
        assertEquals("none", ok.get(0).name);
    }

    private static void testExpandEqualsRepeats() {
        List<String> compact = PonyDefinition.expandActionListNames("stand:3,cheer:1");
        List<String> repeats = PonyDefinition.expandActionListNames("stand,stand,stand,cheer");
        assertEquals(repeats, compact);
        assertEquals(4, compact.size());
        assertEquals("stand", compact.get(0));
        assertEquals("stand", compact.get(1));
        assertEquals("stand", compact.get(2));
        assertEquals("cheer", compact.get(3));

        List<String> mixed = PonyDefinition.expandActionListNames("stand:2,cheer,cheer");
        assertEquals(PonyDefinition.expandActionListNames("stand,stand,cheer,cheer"), mixed);
    }

    private static void testUniqueNamesStripWeight() {
        List<String> names = PonyDefinition.uniqueActionListNames("stand:3,cheer,stand:1,none");
        assertEquals(2, names.size());
        assertEquals("stand", names.get(0));
        assertEquals("cheer", names.get(1));
    }

    private static void testRenamePreservesWeight() {
        String renamed = PonyDefinition.renameInActionList("stand:3,cheer,stand", "stand", "idle");
        assertEquals("idle:3,cheer,idle", renamed);
        String explicitOne = PonyDefinition.renameInActionList("stand:1", "stand", "idle");
        assertEquals("idle:1", explicitOne);
    }

    private static void testFilterDropsMissingWeighted() {
        Set<String> present = new HashSet<String>();
        present.add("stand");
        String kept = PonyDefinition.filterActionList("stand:3,cheer:1,none", present);
        assertEquals("stand:3,none", kept);
    }

    private static void testParseGaitsStillWorks() {
        List<PonyDefinition.GaitEntry> gaits = PonyDefinition.parseGaits("0.5:1,0.7:3,1", null);
        assertEquals(3, gaits.size());
        assertEquals(0.5f, gaits.get(0).speed);
        assertEquals(1, gaits.get(0).weight);
        assertEquals(0.7f, gaits.get(1).speed);
        assertEquals(3, gaits.get(1).weight);
        assertEquals(1f, gaits.get(2).speed);
        assertEquals(1, gaits.get(2).weight);
    }

    private static void assertNotNull(Object value) {
        if (value == null) {
            throw new AssertionError("expected non-null");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(float expected, float actual) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertContains(List<String> errors, String mustContain) {
        for (int i = 0; i < errors.size(); i++) {
            if (errors.get(i).contains(mustContain)) {
                return;
            }
        }
        throw new AssertionError("expected an error containing \"" + mustContain
                + "\" but got " + errors);
    }
}
