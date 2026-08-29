package uk.cpjsmith.ponypaper.custom;

import java.util.ArrayList;
import java.util.List;
import uk.cpjsmith.ponypaper.PonyDefinition;

/**
 * Checks action-graph validation (looping wait-only idles, one-shot none lists,
 * unused-action warnings).
 * Run via {@code ./gradlew :custom:testDefinition} or {@code java … PonyDefinitionValidateTest}.
 */
public final class PonyDefinitionValidateTest {

    private PonyDefinitionValidateTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("loopingWaitOnlyIsValid", PonyDefinitionValidateTest::testLoopingWaitOnlyIsValid);
        failures += run("loopingNeedsWaiting", PonyDefinitionValidateTest::testLoopingNeedsWaiting);
        failures += run("loopingWaitAndMoveStillValid", PonyDefinitionValidateTest::testLoopingWaitAndMoveStillValid);
        failures += run("oneshotBothNoneInvalid", PonyDefinitionValidateTest::testOneshotBothNoneInvalid);
        failures += run("oneshotMovingNoneValid", PonyDefinitionValidateTest::testOneshotMovingNoneValid);
        failures += run("loopingNeedsDrag", PonyDefinitionValidateTest::testLoopingNeedsDrag);
        failures += run("idleOnlyCannotLeave", PonyDefinitionValidateTest::testIdleOnlyCannotLeave);
        failures += run("sitThenStandVanishCanLeave", PonyDefinitionValidateTest::testSitThenStandVanishCanLeave);
        failures += run("appearThenIdleOnlyCannotLeave",
                PonyDefinitionValidateTest::testAppearThenIdleOnlyCannotLeave);
        failures += run("statueAppearVanishValid", PonyDefinitionValidateTest::testStatueAppearVanishValid);
        failures += run("screenInOnMovingIsNotLeave",
                PonyDefinitionValidateTest::testScreenInOnMovingIsNotLeave);
        failures += run("knownScreenSpecialsAccepted",
                PonyDefinitionValidateTest::testKnownScreenSpecialsAccepted);
        failures += run("weightedNextListIsValid",
                PonyDefinitionValidateTest::testWeightedNextListIsValid);
        failures += run("weightedZeroInvalid",
                PonyDefinitionValidateTest::testWeightedZeroInvalid);
        failures += run("noneWithWeightInvalid",
                PonyDefinitionValidateTest::testNoneWithWeightInvalid);
        failures += run("unknownWeightedNameInvalid",
                PonyDefinitionValidateTest::testUnknownWeightedNameInvalid);
        failures += run("colonInActionNameInvalid",
                PonyDefinitionValidateTest::testColonInActionNameInvalid);
        failures += run("weightedMovingCanLeave",
                PonyDefinitionValidateTest::testWeightedMovingCanLeave);
        failures += run("unusedActionWarns",
                PonyDefinitionValidateTest::testUnusedActionWarns);
        failures += run("dragOnlyReachableIsUsed",
                PonyDefinitionValidateTest::testDragOnlyReachableIsUsed);
        failures += run("spritesfromAloneDoesNotCountAsUsed",
                PonyDefinitionValidateTest::testSpritesfromAloneDoesNotCountAsUsed);
        failures += run("allReachableHasNoWarnings",
                PonyDefinitionValidateTest::testAllReachableHasNoWarnings);
        if (failures > 0) {
            System.err.println(failures + " definition check(s) failed.");
            System.exit(1);
        }
        System.out.println("PonyDefinition validation checks passed.");
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

    private static void testLoopingWaitOnlyIsValid() throws Exception {
        PonyDefinition def = pony(
                action("sit", true, "sit,stand", "none"),
                action("stand", true, "sit,stand", "trot"),
                action("trot", true, "stand", "trot"));
        def.validate();
    }

    private static void testLoopingNeedsWaiting() {
        PonyDefinition def = pony(
                action("sit", true, "none", "none"),
                action("trot", true, "sit", "trot"));
        assertInvalid(def, "needs a real next waiting action");
    }

    private static void testLoopingWaitAndMoveStillValid() throws Exception {
        PonyDefinition def = pony(
                action("stand", true, "stand", "trot"),
                action("trot", true, "stand", "trot"));
        def.validate();
    }

    private static void testOneshotBothNoneInvalid() {
        PonyDefinition def = pony(
                action("boom", false, "none", "none"),
                action("stand", true, "stand", "trot"),
                action("trot", true, "stand", "trot"));
        assertInvalid(def, "needs a real next waiting or moving action");
    }

    private static void testOneshotMovingNoneValid() throws Exception {
        PonyDefinition def = pony(
                action("boom", false, "stand", "none"),
                action("stand", true, "stand", "trot"),
                action("trot", true, "stand", "trot"));
        def.validate();
    }

    private static void testLoopingNeedsDrag() {
        PonyDefinition def = pony(
                action("stand", true, "stand", "trot"),
                action("trot", true, "stand", "trot"));
        def.defaultDrag = "";
        for (int i = 0; i < def.actions.length; i++) {
            def.actions[i].nextActions.put("drag", "");
        }
        assertInvalid(def, "needs a real next drag action");
    }

    private static void testIdleOnlyCannotLeave() {
        PonyDefinition def = pony(
                action("stand", true, "stand,sit", "none"),
                action("sit", true, "sit,stand", "none"));
        def.startActions = "stand";
        def.defaultDrag = "stand";
        assertInvalid(def, "No reachable action can leave the scene");
    }

    private static void testSitThenStandVanishCanLeave() throws Exception {
        PonyDefinition def = pony(
                action("sit", true, "sit,stand", "none"),
                action("stand", true, "stand,sit", "vanish"),
                action("vanish", false, "none", "none", PonyDefinition.SPECIAL_SCREEN_OUT));
        def.startActions = "sit";
        def.defaultDrag = "stand";
        def.validate();
    }

    private static void testAppearThenIdleOnlyCannotLeave() {
        PonyDefinition def = pony(
                action("appear", false, "stand", "none", PonyDefinition.SPECIAL_SCREEN_IN),
                action("stand", true, "stand", "none"));
        def.startActions = "appear";
        def.defaultDrag = "stand";
        assertInvalid(def, "No reachable action can leave the scene");
    }

    private static void testStatueAppearVanishValid() throws Exception {
        PonyDefinition def = pony(
                action("appear", false, "stand", "none", PonyDefinition.SPECIAL_SCREEN_IN),
                action("stand", true, "stand,sit", "vanish"),
                action("sit", true, "sit,sit,stand", "none"),
                action("vanish", false, "none", "none", PonyDefinition.SPECIAL_SCREEN_OUT));
        def.startActions = "appear";
        def.defaultDrag = "stand";
        def.validate();
    }

    private static void testScreenInOnMovingIsNotLeave() {
        PonyDefinition def = pony(
                action("stand", true, "stand", "appear"),
                action("appear", false, "stand", "none", PonyDefinition.SPECIAL_SCREEN_IN));
        def.startActions = "appear";
        def.defaultDrag = "stand";
        assertInvalid(def, "No reachable action can leave the scene");
    }

    private static void testKnownScreenSpecialsAccepted() throws Exception {
        PonyDefinition def = pony(
                action("appear", false, "stand", "none", PonyDefinition.SPECIAL_SCREEN_IN),
                action("stand", true, "stand", "vanish"),
                action("vanish", false, "none", "none", PonyDefinition.SPECIAL_SCREEN_OUT));
        def.startActions = "appear";
        def.defaultDrag = "stand";
        def.validate();
    }

    private static void testWeightedNextListIsValid() throws Exception {
        PonyDefinition def = pony(
                action("stand", true, "stand:3,cheer:1", "trot"),
                action("cheer", true, "stand", "trot"),
                action("trot", true, "stand", "trot"));
        def.startActions = "stand:2,trot";
        def.validate();
    }

    private static void testWeightedZeroInvalid() {
        PonyDefinition def = pony(
                action("stand", true, "stand:0", "trot"),
                action("trot", true, "stand", "trot"));
        assertInvalid(def, "Invalid weight");
    }

    private static void testNoneWithWeightInvalid() {
        PonyDefinition def = pony(
                action("sit", true, "sit", "none:2"),
                action("trot", true, "sit", "trot"));
        assertInvalid(def, "cannot have a weight");
    }

    private static void testUnknownWeightedNameInvalid() {
        PonyDefinition def = pony(
                action("stand", true, "stand", "trot"),
                action("trot", true, "stand", "missing:3"));
        assertInvalid(def, "Action missing not defined");
    }

    private static void testColonInActionNameInvalid() {
        PonyDefinition def = pony(
                action("stand:fast", true, "stand:fast", "trot"),
                action("trot", true, "stand:fast", "trot"));
        assertInvalid(def, "must not contain ':'");
    }

    private static void testWeightedMovingCanLeave() throws Exception {
        PonyDefinition def = pony(
                action("sit", true, "sit:3,stand:1", "none"),
                action("stand", true, "stand,sit", "vanish:1"),
                action("vanish", false, "none", "none", PonyDefinition.SPECIAL_SCREEN_OUT));
        def.startActions = "sit";
        def.defaultDrag = "stand";
        def.validate();
    }

    private static void testUnusedActionWarns() throws Exception {
        PonyDefinition def = pony(
                action("stand", true, "stand", "trot"),
                action("trot", true, "stand", "trot"),
                action("orphan", true, "orphan", "trot"));
        def.validate();
        assertWarning(def, "Action orphan is defined but not used");
    }

    private static void testDragOnlyReachableIsUsed() throws Exception {
        PonyDefinition def = pony(
                action("stand", true, "stand", "trot"),
                action("trot", true, "stand", "trot"),
                action("dragged", true, "stand", "trot"));
        def.defaultDrag = "dragged";
        def.validate();
        List<String> warnings = def.collectWarnings();
        if (!warnings.isEmpty()) {
            throw new AssertionError("expected no unused-action warnings but got " + warnings);
        }
    }

    private static void testSpritesfromAloneDoesNotCountAsUsed() throws Exception {
        PonyDefinition def = pony(
                action("stand", true, "stand", "trot"),
                action("trot", true, "stand", "trot"),
                action("sheet", true, "sheet", "trot"));
        def.actions[0].spritesFrom = "sheet";
        def.actions[0].images.put("left", "");
        def.actions[0].images.put("right", "");
        def.actions[0].timings.put("left", "");
        def.actions[0].timings.put("right", "");
        def.validate();
        assertWarning(def, "Action sheet is defined but not used");
    }

    private static void testAllReachableHasNoWarnings() throws Exception {
        PonyDefinition def = pony(
                action("stand", true, "stand", "trot"),
                action("trot", true, "stand", "trot"));
        def.validate();
        List<String> warnings = def.collectWarnings();
        if (!warnings.isEmpty()) {
            throw new AssertionError("expected no warnings but got " + warnings);
        }
    }

    private static PonyDefinition pony(PonyDefinition.Action... actions) {
        PonyDefinition def = new PonyDefinition();
        def.actions = actions;
        def.startActions = "trot";
        def.defaultDrag = "trot";
        return def;
    }

    private static PonyDefinition.Action action(String name, boolean loops,
                                                String waiting, String moving) {
        return action(name, loops, waiting, moving, "");
    }

    private static PonyDefinition.Action action(String name, boolean loops,
                                                String waiting, String moving,
                                                String specialType) {
        PonyDefinition.Action a = new PonyDefinition.Action();
        a.name = name;
        a.loops = loops;
        a.specialType = specialType;
        a.images.put("left", "x");
        a.images.put("right", "x");
        a.timings.put("left", "10");
        a.timings.put("right", "10");
        a.nextActions.put("waiting", waiting);
        a.nextActions.put("moving", moving);
        return a;
    }

    private static void assertInvalid(PonyDefinition def, String mustContain) {
        try {
            def.validate();
            throw new AssertionError("expected validation to fail containing \"" + mustContain + "\"");
        } catch (PonyDefinition.InvalidPonyException e) {
            List<String> errors = e.errors != null ? e.errors : new ArrayList<String>();
            for (int i = 0; i < errors.size(); i++) {
                if (errors.get(i).contains(mustContain)) {
                    return;
                }
            }
            throw new AssertionError("expected an error containing \"" + mustContain
                    + "\" but got " + errors);
        }
    }

    private static void assertWarning(PonyDefinition def, String mustContain) {
        List<String> warnings = def.collectWarnings();
        for (int i = 0; i < warnings.size(); i++) {
            if (warnings.get(i).contains(mustContain)) {
                return;
            }
        }
        throw new AssertionError("expected a warning containing \"" + mustContain
                + "\" but got " + warnings);
    }
}
