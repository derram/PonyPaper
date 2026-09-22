package uk.cpjsmith.ponypaper.custom;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import uk.cpjsmith.ponypaper.CustomDefinitionCache;
import uk.cpjsmith.ponypaper.CustomSheetStore;
import uk.cpjsmith.ponypaper.PonyDefinition;

/**
 * Unpacked custom sheet cache: one PNG per distinct image, stamp reuse, and
 * definition Base64 dropped after a successful install.
 * Run via {@code ./gradlew :custom:testSheetStore}.
 */
public final class CustomSheetStoreTest {

    /** "a" */
    private static final String B64_A = "YQ==";
    /** "ab" */
    private static final String B64_AB = "YWI=";
    /** "abc" */
    private static final String B64_ABC = "YWJj";

    private CustomSheetStoreTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("sharedFacingOnePng", CustomSheetStoreTest::testSharedFacingOnePng);
        failures += run("distinctImages", CustomSheetStoreTest::testDistinctImages);
        failures += run("dedupeAcrossActionsAndEffect",
                CustomSheetStoreTest::testDedupeAcrossActionsAndEffect);
        failures += run("stampHitDoesNotRewrite", CustomSheetStoreTest::testStampHitDoesNotRewrite);
        failures += run("mtimeChangeRewrites", CustomSheetStoreTest::testMtimeChangeRewrites);
        failures += run("invalidBase64LeavesImages", CustomSheetStoreTest::testInvalidBase64LeavesImages);
        failures += run("deleteForRemovesDirectory", CustomSheetStoreTest::testDeleteForRemovesDirectory);
        failures += run("deleteUnlisted", CustomSheetStoreTest::testDeleteUnlisted);
        failures += run("cacheGetDropsBytes", CustomSheetStoreTest::testCacheGetDropsBytes);
        failures += run("newlineBase64", CustomSheetStoreTest::testNewlineBase64);
        if (failures > 0) {
            System.err.println(failures + " sheet-store check(s) failed.");
            System.exit(1);
        }
        System.out.println("Custom sheet store checks passed.");
    }

    private interface Check {
        void run() throws Exception;
    }

    private static int run(String name, Check check) {
        CustomDefinitionCache.clear();
        try {
            check.run();
            System.out.println("ok  " + name);
            return 0;
        } catch (Throwable t) {
            System.err.println("FAIL " + name + ": " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        } finally {
            CustomDefinitionCache.clear();
        }
    }

    private static void testSharedFacingOnePng() throws Exception {
        File xml = tempXml("shared.xml");
        PonyDefinition def = definition(action("stand", B64_A, B64_A));
        if (!CustomSheetStore.prepare(xml, def)) {
            throw new AssertionError("prepare should succeed");
        }
        if (pngCount(xml) != 1) {
            throw new AssertionError("shared facings should write one png, got " + pngCount(xml));
        }
        assertInstalled(def.actions[0], new byte[] {'a'}, true);
        if (!"10".equals(def.actions[0].timings.get("left"))) {
            throw new AssertionError("timings should stay");
        }
    }

    private static void testDistinctImages() throws Exception {
        File xml = tempXml("distinct.xml");
        PonyDefinition def = definition(action("stand", B64_A, B64_AB));
        if (!CustomSheetStore.prepare(xml, def)) {
            throw new AssertionError("prepare should succeed");
        }
        if (pngCount(xml) != 2) {
            throw new AssertionError("two images should write two pngs, got " + pngCount(xml));
        }
        assertInstalled(def.actions[0], new byte[] {'a'}, false);
        if (!java.util.Arrays.equals(readFile(new File(def.actions[0].runtimeFileRight)),
                new byte[] {'a', 'b'})) {
            throw new AssertionError("right png bytes");
        }
    }

    private static void testDedupeAcrossActionsAndEffect() throws Exception {
        File xml = tempXml("dedupe.xml");
        PonyDefinition.Action alias = new PonyDefinition.Action();
        alias.name = "stand-slow";
        alias.spritesFrom = "stand";
        PonyDefinition def = definition(
                action("stand", B64_A, B64_A),
                action("trot", B64_ABC, B64_ABC),
                alias);
        PonyDefinition.Effect effect = new PonyDefinition.Effect();
        effect.name = "spark";
        effect.images.put("left", B64_A);
        effect.images.put("right", B64_A);
        effect.timings.put("left", "5");
        effect.timings.put("right", "5");
        def.effects = new PonyDefinition.Effect[] { effect };
        if (!CustomSheetStore.prepare(xml, def)) {
            throw new AssertionError("prepare should succeed");
        }
        if (pngCount(xml) != 2) {
            throw new AssertionError("alias and repeated image should not add pngs, got "
                    + pngCount(xml));
        }
        if (effect.runtimeImageLeft != null || !effect.images.get("left").isEmpty()) {
            throw new AssertionError("effect bytes and Base64 should be dropped");
        }
        if (!def.actions[0].runtimeFileLeft.equals(effect.runtimeFileLeft)) {
            throw new AssertionError("effect should share the action png");
        }
        if (alias.runtimeFileLeft != null) {
            throw new AssertionError("alias should not get a sheet file");
        }
    }

    private static void testStampHitDoesNotRewrite() throws Exception {
        File xml = tempXml("hit.xml");
        if (!CustomSheetStore.prepare(xml, definition(action("stand", B64_A, B64_A)))) {
            throw new AssertionError("first prepare");
        }
        File png = onlyPng(xml);
        if (!png.setLastModified(0L) || png.lastModified() != 0L) {
            throw new AssertionError("could not stamp png mtime");
        }
        PonyDefinition again = definition(action("stand", B64_A, B64_A));
        if (!CustomSheetStore.prepare(xml, again)) {
            throw new AssertionError("stamp hit should bind");
        }
        if (png.lastModified() != 0L) {
            throw new AssertionError("stamp hit rewrote the png");
        }
        assertInstalled(again.actions[0], new byte[] {'a'}, true);
    }

    private static void testMtimeChangeRewrites() throws Exception {
        File xml = tempXml("mtime.xml");
        if (!CustomSheetStore.prepare(xml, definition(action("stand", B64_A, B64_A)))) {
            throw new AssertionError("first prepare");
        }
        File png = onlyPng(xml);
        if (!png.setLastModified(0L)) {
            throw new AssertionError("could not stamp png mtime");
        }
        long old = xml.lastModified();
        if (!xml.setLastModified(old + 5000L) || xml.lastModified() == old) {
            throw new AssertionError("could not bump xml mtime");
        }
        PonyDefinition again = definition(action("stand", B64_AB, B64_AB));
        if (!CustomSheetStore.prepare(xml, again)) {
            throw new AssertionError("stamp miss should rewrite");
        }
        File rewritten = onlyPng(xml);
        if (rewritten.lastModified() == 0L) {
            throw new AssertionError("mtime change should rewrite the png");
        }
        if (!java.util.Arrays.equals(readFile(rewritten), new byte[] {'a', 'b'})) {
            throw new AssertionError("rewritten png bytes");
        }
        assertInstalled(again.actions[0], new byte[] {'a', 'b'}, true);
    }

    private static void testInvalidBase64LeavesImages() throws Exception {
        File xml = tempXml("bad.xml");
        PonyDefinition def = definition(action("stand", "!!!!", "!!!!"));
        if (CustomSheetStore.prepare(xml, def)) {
            throw new AssertionError("invalid base64 should fail");
        }
        if (!"!!!!".equals(def.actions[0].images.get("left"))) {
            throw new AssertionError("failed prepare must keep Base64");
        }
        if (def.actions[0].runtimeImageLeft != null || def.actions[0].runtimeFileLeft != null) {
            throw new AssertionError("failed prepare must not install runtime sheets");
        }
        if (cacheDir(xml).exists()) {
            throw new AssertionError("failed prepare must not leave a cache directory");
        }
    }

    private static void testDeleteForRemovesDirectory() throws Exception {
        File xml = tempXml("gone.xml");
        if (!CustomSheetStore.prepare(xml, definition(action("stand", B64_A, B64_A)))) {
            throw new AssertionError("prepare");
        }
        if (!cacheDir(xml).isDirectory()) {
            throw new AssertionError("cache dir missing");
        }
        CustomSheetStore.deleteFor(xml);
        if (cacheDir(xml).exists()) {
            throw new AssertionError("deleteFor should remove the cache directory");
        }
    }

    private static void testDeleteUnlisted() throws Exception {
        File xml = tempXml("keep.xml");
        if (!CustomSheetStore.prepare(xml, definition(action("stand", B64_A, B64_A)))) {
            throw new AssertionError("prepare");
        }
        File orphan = new File(new File(xml.getParentFile(), CustomSheetStore.DIR_NAME), "orphan.xml");
        if (!orphan.mkdir()) {
            throw new AssertionError("could not create orphan cache dir");
        }
        CustomSheetStore.deleteUnlisted(xml.getParentFile(), new File[] { xml });
        if (orphan.exists()) {
            throw new AssertionError("unlisted cache dir should be deleted");
        }
        if (!cacheDir(xml).isDirectory()) {
            throw new AssertionError("listed cache dir should stay");
        }
    }

    private static void testCacheGetDropsBytes() throws Exception {
        File xml = tempXml("cached.xml");
        writeUtf8(xml, validXml(B64_A));
        PonyDefinition first = CustomDefinitionCache.get(xml);
        PonyDefinition second = CustomDefinitionCache.get(xml);
        if (first != second) {
            throw new AssertionError("unchanged file should return the cached definition");
        }
        assertInstalled(first.actions[0], new byte[] {'a'}, true);
        if (first.actions[0].runtimeImageLeft != null) {
            throw new AssertionError("cached definition should not keep PNG bytes");
        }
    }

    private static void testNewlineBase64() throws Exception {
        File xml = tempXml("wrap.xml");
        String wrapped = "YQ\n==";
        PonyDefinition def = definition(action("stand", wrapped, wrapped));
        if (!CustomSheetStore.prepare(xml, def)) {
            throw new AssertionError("whitespace in base64 should decode");
        }
        assertInstalled(def.actions[0], new byte[] {'a'}, true);
    }

    private static void assertInstalled(PonyDefinition.Action action, byte[] expect, boolean shared)
            throws Exception {
        if (!action.images.get("left").isEmpty() || !action.images.get("right").isEmpty()) {
            throw new AssertionError("Base64 should be dropped");
        }
        if (action.runtimeImageLeft != null || action.runtimeImageRight != null) {
            throw new AssertionError("PNG bytes should be null");
        }
        if (action.runtimeFileLeft == null || action.runtimeFileRight == null) {
            throw new AssertionError("runtime files missing");
        }
        if (shared != action.runtimeFileLeft.equals(action.runtimeFileRight)) {
            throw new AssertionError("shared=" + shared + " paths "
                    + action.runtimeFileLeft + " / " + action.runtimeFileRight);
        }
        if (action.runtimeTimesLeft == null || action.runtimeTimesLeft.length == 0) {
            throw new AssertionError("times missing");
        }
        if (!java.util.Arrays.equals(readFile(new File(action.runtimeFileLeft)), expect)) {
            throw new AssertionError("png bytes");
        }
    }

    private static PonyDefinition definition(PonyDefinition.Action... actions) {
        PonyDefinition def = new PonyDefinition();
        def.actions = actions;
        def.effects = new PonyDefinition.Effect[0];
        return def;
    }

    private static PonyDefinition.Action action(String name, String left, String right) {
        PonyDefinition.Action action = new PonyDefinition.Action();
        action.name = name;
        action.images.put("left", left);
        action.images.put("right", right);
        action.timings.put("left", "10");
        action.timings.put("right", "10");
        return action;
    }

    private static String validXml(String b64) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<pony>\n"
                + "  <action name=\"stand\">\n"
                + "    <image direction=\"left\">" + b64 + "</image>\n"
                + "    <timings direction=\"left\">10</timings>\n"
                + "    <image direction=\"right\">" + b64 + "</image>\n"
                + "    <timings direction=\"right\">10</timings>\n"
                + "    <nextactions type=\"waiting\">stand</nextactions>\n"
                + "    <nextactions type=\"moving\">trot</nextactions>\n"
                + "  </action>\n"
                + "  <action name=\"trot\">\n"
                + "    <image direction=\"left\">" + b64 + "</image>\n"
                + "    <timings direction=\"left\">10</timings>\n"
                + "    <image direction=\"right\">" + b64 + "</image>\n"
                + "    <timings direction=\"right\">10</timings>\n"
                + "    <nextactions type=\"waiting\">stand</nextactions>\n"
                + "    <nextactions type=\"moving\">trot</nextactions>\n"
                + "  </action>\n"
                + "  <startactions>trot</startactions>\n"
                + "  <defaultdrag>trot</defaultdrag>\n"
                + "</pony>\n";
    }

    private static File tempXml(String name) throws Exception {
        File dir = File.createTempFile("pp-sheets-", "");
        if (!dir.delete() || !dir.mkdir()) {
            throw new AssertionError("temp dir");
        }
        dir.deleteOnExit();
        File file = new File(dir, name);
        writeUtf8(file, "<unused/>\n");
        file.deleteOnExit();
        return file;
    }

    private static void writeUtf8(File file, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    private static File cacheDir(File xml) {
        return new File(new File(xml.getParentFile(), CustomSheetStore.DIR_NAME), xml.getName());
    }

    private static int pngCount(File xml) {
        File[] kids = cacheDir(xml).listFiles();
        if (kids == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < kids.length; i++) {
            if (kids[i].getName().endsWith(".png")) {
                n++;
            }
        }
        return n;
    }

    private static File onlyPng(File xml) {
        File[] kids = cacheDir(xml).listFiles();
        File found = null;
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) {
                if (kids[i].getName().endsWith(".png")) {
                    if (found != null) {
                        throw new AssertionError("expected one png");
                    }
                    found = kids[i];
                }
            }
        }
        if (found == null) {
            throw new AssertionError("png missing");
        }
        return found;
    }

    private static byte[] readFile(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[(int) file.length()];
            int o = 0;
            while (o < buf.length) {
                int n = in.read(buf, o, buf.length - o);
                if (n < 0) {
                    break;
                }
                o += n;
            }
            if (o != buf.length) {
                byte[] exact = new byte[o];
                System.arraycopy(buf, 0, exact, 0, o);
                return exact;
            }
            return buf;
        } finally {
            in.close();
        }
    }
}
