package uk.cpjsmith.ponypaper.custom;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Checks Desktop Ponies Effect-line import (CSV parse via Applejack fixture when
 * present, plus placement token mapping).
 * Run via {@code ./gradlew :custom:testDpEffects}.
 */
public final class DesktopPoniesEffectImportTest {

    private DesktopPoniesEffectImportTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("splitCsvQuotedEffect", DesktopPoniesEffectImportTest::testSplitCsvQuotedEffect);
        failures += run("applejackEffectsWhenPresent",
                DesktopPoniesEffectImportTest::testApplejackEffectsWhenPresent);
        if (failures > 0) {
            System.err.println(failures + " DP effect import check(s) failed.");
            System.exit(1);
        }
        System.out.println("Desktop Ponies effect import checks passed.");
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

    private static void testSplitCsvQuotedEffect() {
        // Mirrors Applejack's Hurdle effect field layout after "Effect,".
        String rest = "\"Hurdle\",\"Hurdle\",\"hurdle_right.gif\",\"hurdle_left.gif\","
                + "0.6,1.32,Right,Top_Left,Left,Top_Right,False,False";
        java.util.List<String> fields = DesktopPoniesImport.splitCsv(rest);
        if (fields.size() < 12) {
            throw new AssertionError("expected >=12 fields, got " + fields.size());
        }
        if (!"Hurdle".equals(fields.get(0)) || !"Hurdle".equals(fields.get(1))) {
            throw new AssertionError("name/behavior mismatch: " + fields.get(0) + "/" + fields.get(1));
        }
        if (!"Right".equals(fields.get(6)) || !"Top_Left".equals(fields.get(7))) {
            throw new AssertionError("placement mismatch");
        }
        if (!"False".equals(fields.get(10))) {
            throw new AssertionError("follow mismatch");
        }
    }

    private static void testApplejackEffectsWhenPresent() throws Exception {
        File aj = findApplejack();
        if (aj == null) {
            System.out.println("skip applejackEffectsWhenPresent (Desktop-Ponies Applejack not found)");
            return;
        }
        DesktopPoniesImport.Result result = DesktopPoniesImport.importPony(aj);
        if (result.actions.isEmpty()) {
            throw new AssertionError("expected imported actions");
        }
        Map<String, DesktopPoniesImport.ImportedEffect> byName =
                new HashMap<String, DesktopPoniesImport.ImportedEffect>();
        for (DesktopPoniesImport.ImportedEffect effect : result.effects) {
            byName.put(effect.name.toLowerCase(Locale.ROOT), effect);
        }

        // Hurdle / tree_buck should import when their behaviors survived the cap.
        Map<String, String> actions = new HashMap<String, String>();
        for (DesktopPoniesImport.ImportedAction action : result.actions) {
            actions.put(action.name.toLowerCase(Locale.ROOT), action.name);
        }

        assertEffectIfActionPresent(byName, actions, "hurdle", "hurdle", false, 0.6f, 1.32f,
                "Right", "Top_Left", "Left", "Top_Right");
        assertEffectIfActionPresent(byName, actions, "tree_buck", "tree_buck", false, 8.96f, 0f,
                "Bottom_Right", "Bottom_Right", "Bottom_Right", "Bottom_Right");
        assertEffectIfActionPresent(byName, actions, "apple drop", "gallop", false, 3.3f, 0.8f,
                "Bottom", "Bottom", "Bottom", "Bottom");
        assertEffectIfActionPresent(byName, actions, "crystalspark", "crystallized", true, 0f, 0f,
                "Center", "Center", "Center", "Center");

        // Every imported effect must point at a loaded action and have image files.
        for (DesktopPoniesImport.ImportedEffect effect : result.effects) {
            if (!actions.containsKey(effect.actionName.toLowerCase(Locale.ROOT))) {
                throw new AssertionError("effect " + effect.name + " trigger missing: "
                        + effect.actionName);
            }
            if (effect.leftImage == null || !effect.leftImage.isFile()
                    || effect.rightImage == null || !effect.rightImage.isFile()) {
                throw new AssertionError("effect " + effect.name + " missing image files");
            }
        }

        boolean mentionedOrphans = false;
        for (String warning : result.warnings) {
            if (warning.contains("Effect line(s) whose behavior was not imported")) {
                mentionedOrphans = true;
                break;
            }
        }
        // With 32 AJ behaviors and a 30-action cap, some effects may be orphaned.
        if (result.effects.size() < 5 && !mentionedOrphans) {
            // Not all 5 effects mapped — expect an orphan warning unless every trigger survived.
            int expectedTriggers = 0;
            if (actions.containsKey("hurdle")) expectedTriggers++;
            if (actions.containsKey("tree_buck")) expectedTriggers++;
            if (actions.containsKey("gallop")) expectedTriggers++;
            if (actions.containsKey("gallop_old")) expectedTriggers++;
            if (actions.containsKey("crystallized")) expectedTriggers++;
            if (result.effects.size() < expectedTriggers && !mentionedOrphans) {
                throw new AssertionError("expected orphan-effect warning when triggers are missing");
            }
        }
    }

    private static void assertEffectIfActionPresent(
            Map<String, DesktopPoniesImport.ImportedEffect> byName,
            Map<String, String> actions,
            String effectKey, String actionKey, boolean follow,
            float duration, float repeat,
            String placeR, String centerR, String placeL, String centerL) {
        if (!actions.containsKey(actionKey)) {
            return;
        }
        DesktopPoniesImport.ImportedEffect effect = byName.get(effectKey);
        if (effect == null) {
            throw new AssertionError("missing effect " + effectKey + " for imported action "
                    + actionKey);
        }
        if (!actionKey.equals(effect.actionName.toLowerCase(Locale.ROOT))) {
            throw new AssertionError(effectKey + " trigger=" + effect.actionName);
        }
        if (effect.follow != follow) {
            throw new AssertionError(effectKey + " follow=" + effect.follow);
        }
        if (Math.abs(effect.duration - duration) > 0.001f
                || Math.abs(effect.repeatDelay - repeat) > 0.001f) {
            throw new AssertionError(effectKey + " timing mismatch");
        }
        if (!placeR.equals(effect.placementRight) || !centerR.equals(effect.centeringRight)
                || !placeL.equals(effect.placementLeft) || !centerL.equals(effect.centeringLeft)) {
            throw new AssertionError(effectKey + " placement mismatch");
        }
    }

    private static File findApplejack() {
        File[] candidates = {
                new File("../Desktop-Ponies/Content/Ponies/Applejack"),
                new File("Desktop-Ponies/Content/Ponies/Applejack"),
                new File("/home/derram/code/Desktop-Ponies/Content/Ponies/Applejack"),
        };
        for (File candidate : candidates) {
            if (candidate.isDirectory() && new File(candidate, "pony.ini").isFile()) {
                return candidate;
            }
        }
        return null;
    }
}
