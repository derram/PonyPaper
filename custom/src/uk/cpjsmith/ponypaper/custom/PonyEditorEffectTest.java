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
        failures += run("copyActionSpriteSharesFacings",
                PonyEditorEffectTest::testCopyActionSpriteSharesFacings);
        failures += run("copyEffectSpriteSharesFacings",
                PonyEditorEffectTest::testCopyEffectSpriteSharesFacings);
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

    private static void testCopyActionSpriteSharesFacings() throws Exception {
        java.io.File file = java.io.File.createTempFile("copy-action", ".xml");
        file.deleteOnExit();
        java.nio.file.Files.writeString(file.toPath(), ponyXml(
                "  <action name=\"stand\">\n"
                + "    <image direction=\"left\">AAA</image>\n"
                + "    <timings direction=\"left\">10</timings>\n"
                + "    <image direction=\"right\">BBB</image>\n"
                + "    <timings direction=\"right\">20</timings>\n"
                + "    <nextactions type=\"waiting\">stand</nextactions>\n"
                + "    <nextactions type=\"moving\">trot</nextactions>\n"
                + "  </action>\n"
                + "  <action name=\"trot\">\n"
                + "    <image>x</image>\n"
                + "    <timings>10</timings>\n"
                + "    <nextactions type=\"waiting\">stand</nextactions>\n"
                + "    <nextactions type=\"moving\">trot</nextactions>\n"
                + "  </action>\n"));
        PonyEditor editor = new PonyEditor();
        editor.load(file);
        if (editor.actionSharesFacingSprites(0)) {
            throw new AssertionError("should not share before copy");
        }
        editor.copyActionSprite(0, "left");
        if (!editor.actionSharesFacingSprites(0)) {
            throw new AssertionError("copy should share facings");
        }
        if (!"AAA".equals(editor.getActionImage(0, "right"))
                || !"10".equals(editor.getActionTimings(0, "right"))) {
            throw new AssertionError("right should match left after copy");
        }
        float leftAx = editor.getActionAnchorX(0, "left");
        float rightAx = editor.getActionAnchorX(0, "right");
        if (Float.isNaN(leftAx) != Float.isNaN(rightAx)
                || (!Float.isNaN(leftAx) && leftAx != rightAx)) {
            throw new AssertionError("copy should copy anchors as-is");
        }
    }

    private static void testCopyEffectSpriteSharesFacings() throws Exception {
        java.io.File file = java.io.File.createTempFile("copy-effect", ".xml");
        file.deleteOnExit();
        java.nio.file.Files.writeString(file.toPath(), ponyXml(
                "  <action name=\"stand\">\n"
                + "    <image>x</image>\n"
                + "    <timings>10</timings>\n"
                + "    <nextactions type=\"waiting\">stand</nextactions>\n"
                + "    <nextactions type=\"moving\">trot</nextactions>\n"
                + "  </action>\n"
                + "  <action name=\"trot\">\n"
                + "    <image>x</image>\n"
                + "    <timings>10</timings>\n"
                + "    <nextactions type=\"waiting\">stand</nextactions>\n"
                + "    <nextactions type=\"moving\">trot</nextactions>\n"
                + "  </action>\n"
                + "  <effect name=\"Sparkle\">\n"
                + "    <action>trot</action>\n"
                + "    <duration>1</duration>\n"
                + "    <placement direction=\"left\">Left</placement>\n"
                + "    <placement direction=\"right\">Right</placement>\n"
                + "    <centering direction=\"left\">Center</centering>\n"
                + "    <centering direction=\"right\">Center</centering>\n"
                + "    <image direction=\"left\">AAA</image>\n"
                + "    <timings direction=\"left\">10</timings>\n"
                + "    <image direction=\"right\">BBB</image>\n"
                + "    <timings direction=\"right\">20</timings>\n"
                + "  </effect>\n"));
        PonyEditor editor = new PonyEditor();
        editor.load(file);
        if (editor.effectSharesFacingSprites(0)) {
            throw new AssertionError("effect should not share before copy");
        }
        editor.copyEffectSprite(0, "left");
        if (!editor.effectSharesFacingSprites(0)) {
            throw new AssertionError("copy should share effect facings");
        }
        if (!"AAA".equals(editor.getEffectImage(0, "right"))
                || !"10".equals(editor.getEffectTimings(0, "right"))) {
            throw new AssertionError("right effect sheet should match left after copy");
        }
        if (!"Left".equals(editor.getEffectPlacement(0, "left"))
                || !"Right".equals(editor.getEffectPlacement(0, "right"))) {
            throw new AssertionError("copy must not change per-facing placement");
        }
    }

    private static String ponyXml(String body) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<pony>\n"
                + body
                + "  <startactions>trot</startactions>\n"
                + "  <defaultdrag>trot</defaultdrag>\n"
                + "</pony>\n";
    }
}
