package uk.cpjsmith.ponypaper.custom;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * In-process checks for the still-frame packer and per-cell flop.
 * Run via {@code ./gradlew :custom:testPacker} or {@code java … ImageImportPackTest}.
 */
public final class ImageImportPackTest {

    private ImageImportPackTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("naturalSort", ImageImportPackTest::testNaturalSort);
        failures += run("packDimensionsAndAlignment", ImageImportPackTest::testPackDimensionsAndAlignment);
        failures += run("packTimings", ImageImportPackTest::testPackTimings);
        failures += run("rejectMixedSizes", ImageImportPackTest::testRejectMixedSizes);
        failures += run("flopEachFrameNotWholeSheet", ImageImportPackTest::testFlopEachFrameNotWholeSheet);
        failures += run("mirrorPreservesOrderAndTimings", ImageImportPackTest::testMirrorPreservesOrderAndTimings);
        failures += run("listFrameFilesNaturalOrder", ImageImportPackTest::testListFrameFilesNaturalOrder);
        failures += run("collectFrameFilesRejectsMix", ImageImportPackTest::testCollectFrameFilesRejectsMix);
        failures += run("parseAndNormalizeLifts", ImageImportPackTest::testParseAndNormalizeLifts);
        failures += run("hopCurve", ImageImportPackTest::testHopCurve);
        failures += run("packLiftsHop", ImageImportPackTest::testPackLiftsHop);
        failures += run("inspectIncludesLiftInCellHeight", ImageImportPackTest::testInspectIncludesLiftInCellHeight);
        failures += run("liftsLengthMismatch", ImageImportPackTest::testLiftsLengthMismatch);
        failures += run("negativeLiftRejected", ImageImportPackTest::testNegativeLiftRejected);
        failures += run("scale50HalvesCell", ImageImportPackTest::testScale50HalvesCell);
        failures += run("scale100IsIdentity", ImageImportPackTest::testScale100IsIdentity);
        failures += run("scaleInvalidRejected", ImageImportPackTest::testScaleInvalidRejected);
        failures += run("parseScalePercent", ImageImportPackTest::testParseScalePercent);
        failures += run("explicitTimingsCs", ImageImportPackTest::testExplicitTimingsCs);
        failures += run("gifLoadIsNativeSize", ImageImportPackTest::testGifLoadIsNativeSize);
        failures += run("gifLoadHalfScale", ImageImportPackTest::testGifLoadHalfScale);
        if (failures > 0) {
            System.err.println(failures + " packer check(s) failed.");
            System.exit(1);
        }
        System.out.println("ImageImport packer checks passed.");
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

    private static void testNaturalSort() {
        assertEq("walk_2 before walk_10",
                true, ImageImport.naturalCompare("walk_2.png", "walk_10.png") < 0);
        assertEq("walk_10 after walk_2",
                true, ImageImport.naturalCompare("walk_10.png", "walk_2.png") > 0);
        assertEq("equal names", 0, ImageImport.naturalCompare("a.png", "a.png"));
        assertEq("case insensitive letters",
                0, ImageImport.naturalCompare("Walk.png", "walk.png"));
        assertEq("leading zeros same value then continue",
                true, ImageImport.naturalCompare("f01.png", "f1.png") == 0
                        || ImageImport.naturalCompare("f01.png", "f1-b.png") < 0);
        List<String> names = new ArrayList<String>(Arrays.asList(
                "walk_10.png", "walk_2.png", "walk_1.png"));
        names.sort(ImageImport::naturalCompare);
        assertEq("sorted[0]", "walk_1.png", names.get(0));
        assertEq("sorted[1]", "walk_2.png", names.get(1));
        assertEq("sorted[2]", "walk_10.png", names.get(2));
    }

    private static void testPackDimensionsAndAlignment() throws IOException {
        // 10×20 red, 20×10 blue → cell 20×20, sheet 40×20. Red centred + bottom;
        // blue full width + bottom.
        BufferedImage red = solid(10, 20, 0xffff0000);
        BufferedImage blue = solid(20, 10, 0xff0000ff);
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(red, blue), new ImageImport.PackOptions());
        assertEq("cellW", 20, packed.cellWidth);
        assertEq("cellH", 20, packed.cellHeight);
        BufferedImage sheet = decode(packed.loadedImage);
        assertEq("sheetW", 40, sheet.getWidth());
        assertEq("sheetH", 20, sheet.getHeight());
        assertEq("sheetW == N*cellW", sheet.getWidth(), 2 * packed.cellWidth);

        // Red occupies x=5..14, y=0..19 in first cell.
        assertEq("red centre", 0xffff0000, sheet.getRGB(10, 10));
        assertEq("red left pad", 0, sheet.getRGB(2, 10));
        assertEq("red right pad", 0, sheet.getRGB(17, 10));

        // Blue occupies x=20..39, y=10..19.
        assertEq("blue pixel", 0xff0000ff, sheet.getRGB(30, 15));
        assertEq("blue top pad", 0, sheet.getRGB(30, 5));
    }

    private static void testPackTimings() throws IOException {
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.defaultTimingCs = 7;
        BufferedImage a = solid(4, 4, 0xff00ff00);
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a, a, a), opts);
        assertEq("timings", "7,7,7", packed.timings);
        assertEq("count", 3, ImageImport.countTimings(packed.timings));
    }

    private static void testRejectMixedSizes() throws IOException {
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.rejectMixedSizes = true;
        BufferedImage a = solid(8, 8, 0xff000000);
        BufferedImage b = solid(4, 8, 0xffffffff);
        try {
            ImageImport.fromFrames(Arrays.asList(a, b), opts);
            throw new AssertionError("expected IOException for mixed sizes");
        } catch (IOException e) {
            if (!e.getMessage().contains("differ")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testFlopEachFrameNotWholeSheet() throws IOException {
        BufferedImage a = marker(8, 8, 0xffff0000, 0xff00ff00);
        BufferedImage b = marker(8, 8, 0xff0000ff, 0xffffff00);
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a, b), new ImageImport.PackOptions());
        ImageImport mirrored = ImageImport.mirrorImport(packed);
        BufferedImage sheet = decode(mirrored.loadedImage);
        assertEq("mirror sheetW", 16, sheet.getWidth());

        // Frame 0 still first: flop(A) has green on the left, red on the right.
        assertEq("flop A left", 0xff00ff00, sheet.getRGB(0, 0));
        assertEq("flop A right", 0xffff0000, sheet.getRGB(7, 0));
        // Frame 1 still second: flop(B).
        assertEq("flop B left", 0xffffff00, sheet.getRGB(8, 0));
        assertEq("flop B right", 0xff0000ff, sheet.getRGB(15, 0));

        // Whole-sheet flop would put flop(B) in the first cell.
        int firstCellLeft = sheet.getRGB(0, 0);
        if (firstCellLeft == 0xffffff00) {
            throw new AssertionError("frame order reversed (whole-sheet flop)");
        }
    }

    private static void testMirrorPreservesOrderAndTimings() throws IOException {
        BufferedImage a = solid(4, 4, 0xff111111);
        BufferedImage b = solid(4, 4, 0xff222222);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.defaultTimingCs = 15;
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a, b), opts);
        ImageImport mirrored = ImageImport.mirrorSheet(packed.loadedImage, 2, "3,9");
        assertEq("copied timings", "3,9", mirrored.timings);
        assertEq("cellW preserved", packed.cellWidth, mirrored.cellWidth);
    }

    private static void testListFrameFilesNaturalOrder() throws Exception {
        File dir = Files.createTempDirectory("pp-frames-").toFile();
        try {
            touchPng(new File(dir, "walk_10.png"));
            touchPng(new File(dir, "walk_2.png"));
            touchPng(new File(dir, "notes.txt"));
            List<File> files = ImageImport.listFrameImageFiles(dir);
            assertEq("count", 2, files.size());
            assertEq("first", "walk_2.png", files.get(0).getName());
            assertEq("second", "walk_10.png", files.get(1).getName());
        } finally {
            deleteTree(dir);
        }
    }

    private static void testParseAndNormalizeLifts() throws IOException {
        int[] parsed = ImageImport.parseLifts(" 0 , 8,16 ");
        assertEq("parse[0]", 0, parsed[0]);
        assertEq("parse[1]", 8, parsed[1]);
        assertEq("parse[2]", 16, parsed[2]);
        assertEq("format", "0,8,16", ImageImport.formatLifts(parsed));

        int[] zeros = ImageImport.normalizeLifts(null, 3);
        assertEq("null lifts length", 3, zeros.length);
        assertEq("null lifts zero", 0, zeros[0] + zeros[1] + zeros[2]);

        try {
            ImageImport.parseLifts("0,,4");
            throw new AssertionError("expected empty-part failure");
        } catch (IOException e) {
            if (!e.getMessage().toLowerCase().contains("empty")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
        try {
            ImageImport.parseLifts("8,-1,0");
            throw new AssertionError("expected negative parse failure");
        } catch (IOException e) {
            if (!e.getMessage().contains(">= 0")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testHopCurve() {
        int[] one = ImageImport.hopCurve(1, 16);
        assertEq("single frame", 1, one.length);
        assertEq("single is 0", 0, one[0]);

        int[] three = ImageImport.hopCurve(3, 20);
        assertEq("n3[0]", 0, three[0]);
        assertEq("n3[1]", 20, three[1]);
        assertEq("n3[2]", 0, three[2]);

        int[] zeroPeak = ImageImport.hopCurve(5, 0);
        assertEq("zero peak mid", 0, zeroPeak[2]);
    }

    private static void testPackLiftsHop() throws IOException {
        BufferedImage a = solid(8, 8, 0xffff0000);
        BufferedImage b = solid(8, 8, 0xff0000ff);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.lifts = new int[] {0, 4};
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a, b), opts);
        assertEq("cellW", 8, packed.cellWidth);
        assertEq("cellH", 12, packed.cellHeight);
        BufferedImage sheet = decode(packed.loadedImage);
        assertEq("sheetW", 16, sheet.getWidth());
        assertEq("sheetH", 12, sheet.getHeight());

        // Ground frame sits on the baseline: opaque at y=11, air at y=0..3.
        assertEq("ground foot", 0xffff0000, sheet.getRGB(4, 11));
        assertEq("ground headroom", 0, sheet.getRGB(4, 1));
        // Lifted frame has 4px of air under the sprite.
        assertEq("hop body", 0xff0000ff, sheet.getRGB(12, 3));
        assertEq("hop air under", 0, sheet.getRGB(12, 10));
        assertEq("hop top", 0xff0000ff, sheet.getRGB(12, 0));
    }

    private static void testInspectIncludesLiftInCellHeight() throws IOException {
        BufferedImage tall = solid(10, 20, 0xffff0000);
        BufferedImage shortFrame = solid(20, 10, 0xff0000ff);
        ImageImport.PackPreview noLift = ImageImport.inspectFrames(Arrays.asList(tall, shortFrame));
        assertEq("no-lift cellH", 20, noLift.cellHeight);
        assertEq("no-lift cellW", 20, noLift.cellWidth);

        ImageImport.PackPreview hop = ImageImport.inspectFrames(
                Arrays.asList(tall, shortFrame), new int[] {0, 10});
        assertEq("hop cellH", 20, hop.cellHeight);
        assertEq("hop cellW", 20, hop.cellWidth);

        ImageImport.PackPreview taller = ImageImport.inspectFrames(
                Arrays.asList(tall, shortFrame), new int[] {0, 16});
        assertEq("taller cellH", 26, taller.cellHeight);
    }

    private static void testLiftsLengthMismatch() throws IOException {
        BufferedImage a = solid(4, 4, 0xff00ff00);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.lifts = new int[] {0, 4};
        try {
            ImageImport.fromFrames(Arrays.asList(a), opts);
            throw new AssertionError("expected lift-count failure");
        } catch (IOException e) {
            if (!e.getMessage().contains("Expected 1 lifts")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testNegativeLiftRejected() throws IOException {
        BufferedImage a = solid(4, 4, 0xff00ff00);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.lifts = new int[] {-1};
        try {
            ImageImport.fromFrames(Arrays.asList(a), opts);
            throw new AssertionError("expected negative lift failure");
        } catch (IOException e) {
            if (!e.getMessage().contains(">= 0")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testScale50HalvesCell() throws IOException {
        BufferedImage a = solid(10, 8, 0xffff0000);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.scalePercent = ImageImport.SCALE_DESKTOP_PONIES;
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a), opts);
        assertEq("cellW", 5, packed.cellWidth);
        assertEq("cellH", 4, packed.cellHeight);
        BufferedImage sheet = decode(packed.loadedImage);
        assertEq("sheetW", 5, sheet.getWidth());
        assertEq("sheetH", 4, sheet.getHeight());
        assertEq("nearest red", 0xffff0000, sheet.getRGB(2, 2));
    }

    private static void testScale100IsIdentity() throws IOException {
        BufferedImage a = solid(10, 8, 0xff00ff00);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.scalePercent = ImageImport.SCALE_NATIVE;
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a), opts);
        assertEq("cellW", 10, packed.cellWidth);
        assertEq("cellH", 8, packed.cellHeight);
    }

    private static void testScaleInvalidRejected() throws IOException {
        BufferedImage a = solid(4, 4, 0xff0000ff);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.scalePercent = 25;
        try {
            ImageImport.fromFrames(Arrays.asList(a), opts);
            throw new AssertionError("expected invalid scale failure");
        } catch (IOException e) {
            if (!e.getMessage().contains("100 or 50")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testParseScalePercent() throws IOException {
        assertEq("100", ImageImport.SCALE_NATIVE, ImageImport.parseScalePercent("100"));
        assertEq("50%", ImageImport.SCALE_DESKTOP_PONIES, ImageImport.parseScalePercent("50%"));
        assertEq("half", ImageImport.SCALE_DESKTOP_PONIES, ImageImport.parseScalePercent("half"));
        assertEq("native", ImageImport.SCALE_NATIVE, ImageImport.parseScalePercent("native"));
        try {
            ImageImport.parseScalePercent("75");
            throw new AssertionError("expected 75 to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("100 or 50")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testExplicitTimingsCs() throws IOException {
        BufferedImage a = solid(4, 4, 0xff112233);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.timingsCs = new int[] {3, 0, 12};
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a, a, a), opts);
        assertEq("gif-like timings (0→1)", "3,1,12", packed.timings);
    }

    private static void testGifLoadIsNativeSize() throws Exception {
        File gif = Files.createTempFile("pp-gif-native-", ".gif").toFile();
        try {
            ImageIO.write(solid(8, 6, 0xffff0000), "gif", gif);
            ImageImport imported = ImageImport.load(gif);
            assertEq("cellW native", 8, imported.cellWidth);
            assertEq("cellH native", 6, imported.cellHeight);
            assertEq("one timing", 1, ImageImport.countTimings(imported.timings));
        } finally {
            gif.delete();
        }
    }

    private static void testGifLoadHalfScale() throws Exception {
        File gif = Files.createTempFile("pp-gif-half-", ".gif").toFile();
        try {
            ImageIO.write(solid(8, 6, 0xff00ff00), "gif", gif);
            ImageImport.PackOptions opts = new ImageImport.PackOptions();
            opts.scalePercent = ImageImport.SCALE_DESKTOP_PONIES;
            ImageImport imported = ImageImport.load(gif, opts);
            assertEq("cellW half", 4, imported.cellWidth);
            assertEq("cellH half", 3, imported.cellHeight);
        } finally {
            gif.delete();
        }
    }

    private static void testCollectFrameFilesRejectsMix() throws Exception {
        File dir = Files.createTempDirectory("pp-mix-").toFile();
        File png = Files.createTempFile("pp-frame-", ".png").toFile();
        try {
            touchPng(new File(dir, "a.png"));
            touchPng(png);
            try {
                ImageImport.collectFrameFiles(Arrays.asList(dir, png));
                throw new AssertionError("expected mix to fail");
            } catch (IOException e) {
                if (!e.getMessage().contains("not both")) {
                    throw new AssertionError("unexpected message: " + e.getMessage());
                }
            }
        } finally {
            deleteTree(dir);
            png.delete();
        }
    }

    private static BufferedImage solid(int w, int h, int argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    /** Unique pixels at top-left and top-right so a flop is detectable. */
    private static BufferedImage marker(int w, int h, int leftArgb, int rightArgb) {
        BufferedImage img = solid(w, h, 0x00000000);
        img.setRGB(0, 0, leftArgb);
        img.setRGB(w - 1, 0, rightArgb);
        return img;
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        if (img == null) {
            throw new IOException("decode failed");
        }
        return img;
    }

    private static void touchPng(File file) throws IOException {
        ImageIO.write(solid(2, 2, 0xffffffff), "png", file);
    }

    private static void deleteTree(File file) {
        File[] kids = file.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                deleteTree(kid);
            }
        }
        file.delete();
    }

    private static void assertEq(String label, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEq(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
