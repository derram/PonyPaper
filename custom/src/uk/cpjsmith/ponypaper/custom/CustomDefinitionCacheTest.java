package uk.cpjsmith.ponypaper.custom;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import uk.cpjsmith.ponypaper.CustomDefinitionCache;
import uk.cpjsmith.ponypaper.PonyDefinition;

/**
 * Checks stamp-keyed custom XML definition caching (path + mtime + length).
 * Run via {@code ./gradlew :custom:testDefinitionCache}.
 */
public final class CustomDefinitionCacheTest {

    private static final String VALID_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<pony>\n"
            + "  <action name=\"stand\">\n"
            + "    <image direction=\"left\">x</image>\n"
            + "    <timings direction=\"left\">10</timings>\n"
            + "    <image direction=\"right\">x</image>\n"
            + "    <timings direction=\"right\">10</timings>\n"
            + "    <nextactions type=\"waiting\">stand</nextactions>\n"
            + "    <nextactions type=\"moving\">trot</nextactions>\n"
            + "  </action>\n"
            + "  <action name=\"trot\">\n"
            + "    <image direction=\"left\">x</image>\n"
            + "    <timings direction=\"left\">10</timings>\n"
            + "    <image direction=\"right\">x</image>\n"
            + "    <timings direction=\"right\">10</timings>\n"
            + "    <nextactions type=\"waiting\">stand</nextactions>\n"
            + "    <nextactions type=\"moving\">trot</nextactions>\n"
            + "  </action>\n"
            + "  <startactions>trot</startactions>\n"
            + "  <defaultdrag>trot</defaultdrag>\n"
            + "</pony>\n";

    private CustomDefinitionCacheTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("hitSameInstance", CustomDefinitionCacheTest::testHitSameInstance);
        failures += run("lengthChangeMisses", CustomDefinitionCacheTest::testLengthChangeMisses);
        failures += run("mtimeChangeMisses", CustomDefinitionCacheTest::testMtimeChangeMisses);
        failures += run("failedParseNotCached", CustomDefinitionCacheTest::testFailedParseNotCached);
        failures += run("retainOnlyDropsMissing", CustomDefinitionCacheTest::testRetainOnlyDropsMissing);
        failures += run("invalidateRemoves", CustomDefinitionCacheTest::testInvalidateRemoves);
        if (failures > 0) {
            System.err.println(failures + " definition-cache check(s) failed.");
            System.exit(1);
        }
        System.out.println("Custom definition cache checks passed.");
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

    private static void testHitSameInstance() throws Exception {
        File file = tempXml("hit.xml", VALID_XML);
        PonyDefinition first = CustomDefinitionCache.get(file);
        PonyDefinition second = CustomDefinitionCache.get(file);
        if (first != second) {
            throw new AssertionError("unchanged stamp must return the same instance");
        }
        if (CustomDefinitionCache.size() != 1) {
            throw new AssertionError("expected 1 cached entry, got " + CustomDefinitionCache.size());
        }
        if (first.actions.length != 2) {
            throw new AssertionError("expected 2 actions, got " + first.actions.length);
        }
    }

    private static void testLengthChangeMisses() throws Exception {
        File file = tempXml("len.xml", VALID_XML);
        PonyDefinition first = CustomDefinitionCache.get(file);
        writeUtf8(file, VALID_XML + "\n\n");
        PonyDefinition second = CustomDefinitionCache.get(file);
        if (first == second) {
            throw new AssertionError("length change must re-parse");
        }
        if (CustomDefinitionCache.size() != 1) {
            throw new AssertionError("replace must keep one entry, got " + CustomDefinitionCache.size());
        }
    }

    private static void testMtimeChangeMisses() throws Exception {
        File file = tempXml("mtime.xml", VALID_XML);
        PonyDefinition first = CustomDefinitionCache.get(file);
        long old = file.lastModified();
        if (!file.setLastModified(old + 5000L) || file.lastModified() == old) {
            throw new AssertionError("could not bump lastModified on " + file);
        }
        PonyDefinition second = CustomDefinitionCache.get(file);
        if (first == second) {
            throw new AssertionError("mtime change must re-parse");
        }
    }

    private static void testFailedParseNotCached() throws Exception {
        File file = tempXml("bad.xml", "<pony></pony>\n");
        try {
            CustomDefinitionCache.get(file);
            throw new AssertionError("invalid xml must throw");
        } catch (PonyDefinition.InvalidPonyException expected) {
            // ok
        }
        if (CustomDefinitionCache.size() != 0) {
            throw new AssertionError("failed parse must not be cached, size="
                    + CustomDefinitionCache.size());
        }
        writeUtf8(file, VALID_XML);
        PonyDefinition ok = CustomDefinitionCache.get(file);
        if (ok == null || CustomDefinitionCache.size() != 1) {
            throw new AssertionError("valid rewrite should cache");
        }
    }

    private static void testRetainOnlyDropsMissing() throws Exception {
        File keep = tempXml("keep.xml", VALID_XML);
        File drop = tempXml("drop.xml", VALID_XML);
        CustomDefinitionCache.get(keep);
        CustomDefinitionCache.get(drop);
        if (CustomDefinitionCache.size() != 2) {
            throw new AssertionError("expected 2 entries");
        }
        CustomDefinitionCache.retainOnly(new File[] { keep });
        if (CustomDefinitionCache.size() != 1) {
            throw new AssertionError("retainOnly should drop missing paths, size="
                    + CustomDefinitionCache.size());
        }
        if (CustomDefinitionCache.get(keep) == null) {
            throw new AssertionError("kept file should still hit");
        }
    }

    private static void testInvalidateRemoves() throws Exception {
        File file = tempXml("gone.xml", VALID_XML);
        CustomDefinitionCache.get(file);
        CustomDefinitionCache.invalidate(file);
        if (CustomDefinitionCache.size() != 0) {
            throw new AssertionError("invalidate should empty the entry");
        }
        PonyDefinition again = CustomDefinitionCache.get(file);
        if (again == null || CustomDefinitionCache.size() != 1) {
            throw new AssertionError("next get should parse and cache again");
        }
    }

    private static File tempXml(String name, String xml) throws Exception {
        File dir = File.createTempFile("pp-def-cache-", "");
        if (!dir.delete() || !dir.mkdir()) {
            throw new AssertionError("could not create temp dir for " + name);
        }
        dir.deleteOnExit();
        File file = new File(dir, name);
        writeUtf8(file, xml);
        file.deleteOnExit();
        return file;
    }

    private static void writeUtf8(File file, String xml) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(xml.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }
}
