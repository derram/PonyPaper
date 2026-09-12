package uk.cpjsmith.ponypaper.custom;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import uk.cpjsmith.ponypaper.BackgroundAlbumLogic;

/**
 * Checks saved-background album cycle helpers.
 * Run via {@code ./gradlew :custom:testBackgroundAlbum}.
 */
public final class BackgroundAlbumLogicTest {

    private BackgroundAlbumLogicTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("safeHash", BackgroundAlbumLogicTest::testSafeHash);
        failures += run("shouldCycle", BackgroundAlbumLogicTest::testShouldCycle);
        failures += run("startingIndex", BackgroundAlbumLogicTest::testStartingIndex);
        failures += run("cycleFileHash", BackgroundAlbumLogicTest::testCycleFileHash);
        failures += run("nextHash", BackgroundAlbumLogicTest::testNextHash);
        failures += run("intervalMs", BackgroundAlbumLogicTest::testIntervalMs);
        failures += run("canFit", BackgroundAlbumLogicTest::testCanFit);
        failures += run("albumMarker", BackgroundAlbumLogicTest::testAlbumMarker);
        failures += run("sanitizeAlbumFileName", BackgroundAlbumLogicTest::testSanitizeAlbumFileName);
        failures += run("uniqueAlbumFileName", BackgroundAlbumLogicTest::testUniqueAlbumFileName);
        failures += run("imageMagic", BackgroundAlbumLogicTest::testImageMagic);
        if (failures > 0) {
            System.err.println(failures + " background-album check(s) failed.");
            System.exit(1);
        }
        System.out.println("BackgroundAlbumLogic checks passed.");
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

    private static String hash(char c) {
        char[] buf = new char[BackgroundAlbumLogic.HASH_LENGTH];
        java.util.Arrays.fill(buf, c);
        return new String(buf);
    }

    private static void testSafeHash() {
        if (BackgroundAlbumLogic.isSafeHash(null)) {
            throw new AssertionError("null");
        }
        if (BackgroundAlbumLogic.isSafeHash("abc")) {
            throw new AssertionError("short");
        }
        String ok = hash('a');
        if (!BackgroundAlbumLogic.isSafeHash(ok)) {
            throw new AssertionError("lowercase hex");
        }
        if (BackgroundAlbumLogic.isSafeHash(hash('A'))) {
            throw new AssertionError("uppercase");
        }
        char[] slash = ok.toCharArray();
        slash[0] = '.';
        if (BackgroundAlbumLogic.isSafeHash(new String(slash))) {
            throw new AssertionError("dot");
        }
    }

    private static void testShouldCycle() {
        if (BackgroundAlbumLogic.shouldCycle(true, 1)) {
            throw new AssertionError("one image is still");
        }
        if (!BackgroundAlbumLogic.shouldCycle(true, 2)) {
            throw new AssertionError("two images cycle");
        }
        if (BackgroundAlbumLogic.shouldCycle(false, 5)) {
            throw new AssertionError("pref off");
        }
    }

    private static void testStartingIndex() {
        String a = hash('a');
        String b = hash('b');
        List<String> hashes = Arrays.asList(a, b);
        if (BackgroundAlbumLogic.startingIndex(hashes, b) != 1) {
            throw new AssertionError("wallpaper match");
        }
        if (BackgroundAlbumLogic.startingIndex(hashes, hash('c')) != 0) {
            throw new AssertionError("unknown wallpaper starts at 0");
        }
        if (BackgroundAlbumLogic.startingIndex(Collections.<String>emptyList(), a) != -1) {
            throw new AssertionError("empty");
        }
        if (!a.equals(BackgroundAlbumLogic.startingHash(hashes, null))) {
            throw new AssertionError("null wallpaper");
        }
    }

    private static void testCycleFileHash() {
        String a = hash('a');
        String b = hash('b');
        List<String> hashes = Arrays.asList(a, b);
        if (!b.equals(BackgroundAlbumLogic.cycleFileHash(hashes, b, a))) {
            throw new AssertionError("keep current across rebuild");
        }
        if (!a.equals(BackgroundAlbumLogic.cycleFileHash(hashes, null, a))) {
            throw new AssertionError("first show uses wallpaper");
        }
        if (!a.equals(BackgroundAlbumLogic.cycleFileHash(hashes, hash('c'), a))) {
            throw new AssertionError("stale current falls back");
        }
    }

    private static void testNextHash() {
        String a = hash('a');
        String b = hash('b');
        String c = hash('c');
        List<String> hashes = Arrays.asList(a, b, c);
        if (!b.equals(BackgroundAlbumLogic.nextHash(hashes, a))) {
            throw new AssertionError("a -> b");
        }
        if (!a.equals(BackgroundAlbumLogic.nextHash(hashes, c))) {
            throw new AssertionError("wrap");
        }
        if (!a.equals(BackgroundAlbumLogic.nextHash(hashes, null))) {
            throw new AssertionError("missing current");
        }
        if (BackgroundAlbumLogic.nextHash(Collections.<String>emptyList(), a) != null) {
            throw new AssertionError("empty next");
        }
        if (!a.equals(BackgroundAlbumLogic.nextHash(Collections.singletonList(a), a))) {
            throw new AssertionError("singleton");
        }
    }

    private static void testIntervalMs() {
        long ten = 10L * 60L * 1000L;
        if (BackgroundAlbumLogic.intervalMs(null) != ten) {
            throw new AssertionError("default");
        }
        if (BackgroundAlbumLogic.intervalMs("5") != 5L * 60L * 1000L) {
            throw new AssertionError("5");
        }
        if (BackgroundAlbumLogic.intervalMs("99") != 30L * 60L * 1000L) {
            throw new AssertionError("clamp high");
        }
        if (BackgroundAlbumLogic.intervalMs("1") != 5L * 60L * 1000L) {
            throw new AssertionError("clamp low");
        }
        if (BackgroundAlbumLogic.intervalMs("nope") != ten) {
            throw new AssertionError("invalid");
        }
    }

    private static void testCanFit() {
        if (!BackgroundAlbumLogic.canFit(0, 0L, 1L)) {
            throw new AssertionError("empty album");
        }
        if (BackgroundAlbumLogic.canFit(BackgroundAlbumLogic.MAX_MEMBERS, 0L, 1L)) {
            throw new AssertionError("count cap");
        }
        if (BackgroundAlbumLogic.canFit(0, 0L, BackgroundAlbumLogic.MAX_MEMBER_BYTES + 1)) {
            throw new AssertionError("one file too big");
        }
        if (BackgroundAlbumLogic.canFit(1, BackgroundAlbumLogic.MAX_TOTAL_BYTES, 1L)) {
            throw new AssertionError("total cap");
        }
        if (!BackgroundAlbumLogic.canFit(19, 0L, BackgroundAlbumLogic.MAX_MEMBER_BYTES)) {
            throw new AssertionError("19+64MB should fit");
        }
    }

    private static void testAlbumMarker() {
        if (!BackgroundAlbumLogic.isAlbumMarkerName("album-images-go-here.txt")) {
            throw new AssertionError("canonical");
        }
        if (BackgroundAlbumLogic.albumMarkerRank("album-images-go-here.txt") != 0) {
            throw new AssertionError("rank txt");
        }
        if (BackgroundAlbumLogic.albumMarkerRank("album-images-go-here") != 1) {
            throw new AssertionError("rank bare");
        }
        if (BackgroundAlbumLogic.albumMarkerRank("album-images-go-here (1).txt") != 2) {
            throw new AssertionError("rank uniquified");
        }
        if (BackgroundAlbumLogic.isAlbumMarkerName("sunset.png")) {
            throw new AssertionError("image is not marker");
        }
        if (BackgroundAlbumLogic.isAlbumMarkerName("custom-ponies-go-here.txt")) {
            throw new AssertionError("pony marker is not album marker");
        }
    }

    private static void testSanitizeAlbumFileName() {
        if (!"sunset.png".equals(BackgroundAlbumLogic.sanitizeAlbumFileName("sunset.png"))) {
            throw new AssertionError("plain");
        }
        if (!"foo_bar.jpg".equals(BackgroundAlbumLogic.sanitizeAlbumFileName("foo bar.jpg"))) {
            throw new AssertionError("space");
        }
        if (!"photo.jpeg".equals(BackgroundAlbumLogic.sanitizeAlbumFileName("dir/photo.JPEG"))) {
            throw new AssertionError("path and ext case");
        }
        if (BackgroundAlbumLogic.sanitizeAlbumFileName("notes.txt") != null) {
            throw new AssertionError("txt");
        }
        if (BackgroundAlbumLogic.sanitizeAlbumFileName("album-images-go-here.txt") != null) {
            throw new AssertionError("marker");
        }
        if (!"x.png".equals(BackgroundAlbumLogic.sanitizeAlbumFileName("../x.png"))) {
            throw new AssertionError("basename only");
        }
        if (BackgroundAlbumLogic.sanitizeAlbumFileName("noext") != null) {
            throw new AssertionError("no extension");
        }
        if (!BackgroundAlbumLogic.isImageFileName("a.webp")) {
            throw new AssertionError("webp");
        }
        if (BackgroundAlbumLogic.isImageFileName("a.xml")) {
            throw new AssertionError("xml");
        }
    }

    private static void testUniqueAlbumFileName() {
        java.util.HashSet<String> taken = new java.util.HashSet<String>();
        taken.add("sunset.png");
        if (!"sunset-2.png".equals(BackgroundAlbumLogic.uniqueAlbumFileName("sunset.png", taken))) {
            throw new AssertionError("suffix");
        }
        taken.add("Sunset-2.PNG");
        String third = BackgroundAlbumLogic.uniqueAlbumFileName("sunset.png", taken);
        if (!"sunset-3.png".equals(third)) {
            throw new AssertionError("case-insensitive taken: " + third);
        }
        if (BackgroundAlbumLogic.uniqueAlbumFileName("notes.txt", taken) != null) {
            throw new AssertionError("non-image");
        }
    }

    private static void testImageMagic() {
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        if (!".jpg".equals(BackgroundAlbumLogic.imageExtensionFromPrefix(jpeg))) {
            throw new AssertionError("jpeg");
        }
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (!".png".equals(BackgroundAlbumLogic.imageExtensionFromPrefix(png))) {
            throw new AssertionError("png");
        }
        byte[] gif = new byte[] {'G', 'I', 'F', '8', '9', 'a'};
        if (!".gif".equals(BackgroundAlbumLogic.imageExtensionFromPrefix(gif))) {
            throw new AssertionError("gif");
        }
        if (BackgroundAlbumLogic.imageExtensionFromPrefix(new byte[] {0, 1, 2}) != null) {
            throw new AssertionError("unknown");
        }
        if (!"image/png".equals(BackgroundAlbumLogic.mimeForAlbumFileName("x.png"))) {
            throw new AssertionError("mime png");
        }
        if (!"image/jpeg".equals(BackgroundAlbumLogic.mimeForAlbumFileName("x.JPG"))) {
            throw new AssertionError("mime jpeg");
        }
    }
}
