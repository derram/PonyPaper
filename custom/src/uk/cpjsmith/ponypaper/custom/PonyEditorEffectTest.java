package uk.cpjsmith.ponypaper.custom;

/**
 * Checks effect CRUD and action rename/delete scrubbing for effect triggers.
 * Run via {@code ./gradlew :custom:testEditorEffects}.
 */
public final class PonyEditorEffectTest {

    private PonyEditorEffectTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("addAndRenameEffect", PonyEditorEffectTest::testAddAndRenameEffect);
        failures += run("renameActionRewritesTrigger", PonyEditorEffectTest::testRenameActionRewritesTrigger);
        failures += run("deleteActionRemovesOrphanEffect", PonyEditorEffectTest::testDeleteActionRemovesOrphanEffect);
        failures += run("duplicateEffectNameRejected", PonyEditorEffectTest::testDuplicateEffectNameRejected);
        if (failures > 0) {
            System.err.println(failures + " editor effect check(s) failed.");
            System.exit(1);
        }
        System.out.println("PonyEditor effect checks passed.");
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

    private static void testAddAndRenameEffect() {
        PonyEditor editor = new PonyEditor();
        editor.addAction("stand");
        editor.addAction("trot");
        int i = editor.addEffect("Sparkle");
        editor.setEffectAction(i, "stand");
        editor.setEffectDuration(i, 2.5f);
        editor.setEffectFollow(i, true);
        editor.setEffectPlacementMode(i, "motion");
        editor.setEffectPlacement(i, "right", "Top");
        if (editor.getEffectCount() != 1) {
            throw new AssertionError("expected 1 effect");
        }
        editor.setEffectName(i, "Glow");
        if (!"Glow".equals(editor.getEffectName(0))) {
            throw new AssertionError("rename failed");
        }
        if (!"stand".equals(editor.getEffectAction(0)) || !editor.getEffectFollow(0)) {
            throw new AssertionError("fields not preserved");
        }
        if (!"motion".equals(editor.getEffectPlacementMode(0))) {
            throw new AssertionError("placementMode not preserved");
        }
    }

    private static void testRenameActionRewritesTrigger() {
        PonyEditor editor = new PonyEditor();
        editor.addAction("stand");
        editor.addAction("trot");
        editor.setStartActions("trot");
        editor.setDefaultDrag("trot");
        int e = editor.addEffect("Sparkle");
        editor.setEffectAction(e, "stand");
        editor.setActionName(0, "idle");
        if (!"idle".equals(editor.getEffectAction(0))) {
            throw new AssertionError("expected trigger rewrite to idle, got "
                    + editor.getEffectAction(0));
        }
    }

    private static void testDeleteActionRemovesOrphanEffect() {
        PonyEditor editor = new PonyEditor();
        editor.addAction("stand");
        editor.addAction("trot");
        editor.setStartActions("trot");
        editor.setDefaultDrag("trot");
        editor.addEffect("Keep");
        editor.setEffectAction(0, "trot");
        editor.addEffect("Orphan");
        editor.setEffectAction(1, "stand");
        editor.removeAction(0); // remove stand
        if (editor.getEffectCount() != 1) {
            throw new AssertionError("expected orphan effect removed, count="
                    + editor.getEffectCount());
        }
        if (!"Keep".equals(editor.getEffectName(0))
                || !"trot".equals(editor.getEffectAction(0))) {
            throw new AssertionError("kept wrong effect");
        }
    }

    private static void testDuplicateEffectNameRejected() {
        PonyEditor editor = new PonyEditor();
        editor.addEffect("Sparkle");
        try {
            editor.addEffect("Sparkle");
            throw new AssertionError("expected duplicate name rejection");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
