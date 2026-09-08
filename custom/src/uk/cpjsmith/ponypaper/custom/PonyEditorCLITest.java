package uk.cpjsmith.ponypaper.custom;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Checks implied {@code -load} for a bare XML path on the editor CLI.
 * Run via {@code ./gradlew :custom:testEditorCli}.
 */
public final class PonyEditorCLITest {

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

    private PonyEditorCLITest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("barePathLoads", PonyEditorCLITest::testBarePathLoads);
        failures += run("dashLoadStillWorks", PonyEditorCLITest::testDashLoadStillWorks);
        failures += run("unknownFlagStillErrors", PonyEditorCLITest::testUnknownFlagStillErrors);
        failures += run("bareThenSaveDoesNotOpenGui", PonyEditorCLITest::testBareThenSaveDoesNotOpenGui);
        if (failures > 0) {
            System.err.println(failures + " editor CLI check(s) failed.");
            System.exit(1);
        }
        System.out.println("PonyEditor CLI checks passed.");
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

    private static void testBarePathLoads() throws Exception {
        File file = tempXml("bare.xml", VALID_XML);
        PonyEditorCLI cli = new PonyEditorCLI();
        cli.processArguments(new String[] { file.getAbsolutePath() });
        if (cli.hadError()) {
            throw new AssertionError("bare XML path must not error");
        }
        if (!cli.shouldOpenGui()) {
            throw new AssertionError("bare XML path should open the GUI");
        }
        if (!file.equals(cli.getLoadedFile())) {
            throw new AssertionError("loaded file was " + cli.getLoadedFile());
        }
        if (cli.isGuiDirty()) {
            throw new AssertionError("pure load must be clean");
        }
        if (cli.getEditor().getActionCount() != 2) {
            throw new AssertionError("expected 2 actions, got " + cli.getEditor().getActionCount());
        }
    }

    private static void testDashLoadStillWorks() throws Exception {
        File file = tempXml("flag.xml", VALID_XML);
        PonyEditorCLI cli = new PonyEditorCLI();
        cli.processArguments(new String[] { "-load", file.getAbsolutePath() });
        if (cli.hadError() || !cli.shouldOpenGui() || !file.equals(cli.getLoadedFile())) {
            throw new AssertionError("-load FILE must still load and open GUI");
        }
    }

    private static void testUnknownFlagStillErrors() {
        PonyEditorCLI cli = new PonyEditorCLI();
        cli.processArguments(new String[] { "-nonesuch" });
        if (!cli.hadError()) {
            throw new AssertionError("unknown -flag must still error");
        }
        if (cli.shouldOpenGui()) {
            throw new AssertionError("unknown -flag must not open the GUI");
        }
        if (cli.getLoadedFile() != null) {
            throw new AssertionError("unknown -flag must not set a loaded file");
        }
    }

    private static void testBareThenSaveDoesNotOpenGui() throws Exception {
        File in = tempXml("in.xml", VALID_XML);
        File out = tempXml("out.xml", VALID_XML);
        PonyEditorCLI cli = new PonyEditorCLI();
        cli.processArguments(new String[] { in.getAbsolutePath(), "-save", out.getAbsolutePath() });
        if (cli.hadError()) {
            throw new AssertionError("bare load then -save must not error");
        }
        if (cli.shouldOpenGui()) {
            throw new AssertionError("-save after load must not open the GUI");
        }
        if (!in.equals(cli.getLoadedFile())) {
            throw new AssertionError("loaded file was " + cli.getLoadedFile());
        }
    }

    private static File tempXml(String name, String xml) throws Exception {
        File dir = File.createTempFile("pp-editor-cli-", "");
        if (!dir.delete() || !dir.mkdir()) {
            throw new AssertionError("could not create temp dir for " + name);
        }
        dir.deleteOnExit();
        File file = new File(dir, name);
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(xml.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
        file.deleteOnExit();
        return file;
    }
}
