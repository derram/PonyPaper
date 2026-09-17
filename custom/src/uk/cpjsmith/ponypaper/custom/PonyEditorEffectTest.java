package uk.cpjsmith.ponypaper.custom;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import javax.imageio.ImageIO;
import uk.cpjsmith.ponypaper.EffectLayer;

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
        failures += run("multiTriggerRenameAndDelete", PonyEditorEffectTest::testMultiTriggerRenameAndDelete);
        failures += run("deleteOneTriggerKeepsEffect", PonyEditorEffectTest::testDeleteOneTriggerKeepsEffect);
        failures += run("duplicateEffectNameRejected", PonyEditorEffectTest::testDuplicateEffectNameRejected);
        failures += run("copyActionSpriteSharesFacings",
                PonyEditorEffectTest::testCopyActionSpriteSharesFacings);
        failures += run("copyEffectSpriteSharesFacings",
                PonyEditorEffectTest::testCopyEffectSpriteSharesFacings);
        failures += run("mirrorActionSpriteFlopsHorizontal",
                PonyEditorEffectTest::testMirrorActionSpriteFlopsHorizontal);
        failures += run("mirrorActionSpriteFlipsVertical",
                PonyEditorEffectTest::testMirrorActionSpriteFlipsVertical);
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
        editor.setEffectLayer(i, EffectLayer.BACK);
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
        if (!EffectLayer.BACK.equals(editor.getEffectLayer(0))) {
            throw new AssertionError("layer not preserved");
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

    private static void testMultiTriggerRenameAndDelete() {
        PonyEditor editor = new PonyEditor();
        editor.addAction("stand");
        editor.addAction("trot");
        editor.setStartActions("trot");
        editor.setDefaultDrag("trot");
        int e = editor.addEffect("Sparkle");
        editor.setEffectAction(e, "stand, trot, stand");
        if (!"stand, trot".equals(editor.getEffectAction(0))) {
            throw new AssertionError("expected collapsed triggers, got "
                    + editor.getEffectAction(0));
        }
        editor.setActionName(0, "idle");
        if (!"idle, trot".equals(editor.getEffectAction(0))) {
            throw new AssertionError("expected idle, trot after rename, got "
                    + editor.getEffectAction(0));
        }
    }

    private static void testDeleteOneTriggerKeepsEffect() {
        PonyEditor editor = new PonyEditor();
        editor.addAction("stand");
        editor.addAction("trot");
        editor.setStartActions("trot");
        editor.setDefaultDrag("trot");
        editor.addEffect("Sparkle");
        editor.setEffectAction(0, "stand, trot");
        editor.removeAction(0); // stand
        if (editor.getEffectCount() != 1) {
            throw new AssertionError("effect should remain with remaining trigger");
        }
        if (!"trot".equals(editor.getEffectAction(0))) {
            throw new AssertionError("expected remaining trigger trot, got "
                    + editor.getEffectAction(0));
        }
        editor.removeAction(0); // last action (trot) — editor may require actions?
        if (editor.getEffectCount() != 0) {
            throw new AssertionError("expected effect removed when last trigger gone, count="
                    + editor.getEffectCount());
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

    private static void testMirrorActionSpriteFlopsHorizontal() throws Exception {
        PonyEditor editor = new PonyEditor();
        editor.addAction("stand");
        editor.setWander("horizontal");
        editor.setActionMovement(0, "horizontal");
        BufferedImage src = cornerMarker(8, 8);
        editor.loadActionSpriteFromFrames(0, "left", Arrays.asList(src),
                new ImageImport.PackOptions());
        editor.setActionAnchorX(0, "left", 2f);
        editor.setActionAnchorY(0, "left", 1f);
        ImageImport mirrored = editor.mirrorActionSprite(0, "left");
        BufferedImage sheet = decodeB64(editor.getActionImage(0, "right"));
        // Flop: top-left red → top-right.
        if (sheet.getRGB(7, 0) != 0xffff0000) {
            throw new AssertionError("horizontal mirror should flop, top-right="
                    + Integer.toHexString(sheet.getRGB(7, 0)));
        }
        if (sheet.getRGB(0, 0) != 0xff00ff00) {
            throw new AssertionError("horizontal mirror should flop, top-left="
                    + Integer.toHexString(sheet.getRGB(0, 0)));
        }
        if (editor.getActionAnchorX(0, "right") != mirrored.cellWidth - 2f) {
            throw new AssertionError("flop should mirror X, got "
                    + editor.getActionAnchorX(0, "right"));
        }
        if (editor.getActionAnchorY(0, "right") != 1f) {
            throw new AssertionError("flop should copy Y, got "
                    + editor.getActionAnchorY(0, "right"));
        }
    }

    private static void testMirrorActionSpriteFlipsVertical() throws Exception {
        PonyEditor editor = new PonyEditor();
        editor.setWander("vertical");
        editor.addAction("climb");
        editor.setActionMovement(0, "soft_vertical");
        BufferedImage src = cornerMarker(8, 8);
        editor.loadActionSpriteFromFrames(0, "left", Arrays.asList(src),
                new ImageImport.PackOptions());
        editor.setActionAnchorX(0, "left", 2f);
        editor.setActionAnchorY(0, "left", 1f);
        ImageImport mirrored = editor.mirrorActionSprite(0, "left");
        BufferedImage sheet = decodeB64(editor.getActionImage(0, "right"));
        // Flip: top-left red → bottom-left.
        if (sheet.getRGB(0, 7) != 0xffff0000) {
            throw new AssertionError("vertical mirror should flip, bottom-left="
                    + Integer.toHexString(sheet.getRGB(0, 7)));
        }
        if (sheet.getRGB(0, 0) != 0xff0000ff) {
            throw new AssertionError("vertical mirror should flip, top-left="
                    + Integer.toHexString(sheet.getRGB(0, 0)));
        }
        if (sheet.getRGB(7, 0) == 0xffff0000) {
            throw new AssertionError("vertical mirror flopped instead of flipping");
        }
        if (editor.getActionAnchorX(0, "right") != 2f) {
            throw new AssertionError("flip should copy X, got "
                    + editor.getActionAnchorX(0, "right"));
        }
        if (editor.getActionAnchorY(0, "right") != mirrored.cellHeight - 1f) {
            throw new AssertionError("flip should mirror Y, got "
                    + editor.getActionAnchorY(0, "right"));
        }
    }

    private static BufferedImage cornerMarker(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xffff0000);
        img.setRGB(w - 1, 0, 0xff00ff00);
        img.setRGB(0, h - 1, 0xff0000ff);
        return img;
    }

    private static BufferedImage decodeB64(String b64) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
        if (img == null) {
            throw new AssertionError("decode failed");
        }
        return img;
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
