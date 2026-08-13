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
