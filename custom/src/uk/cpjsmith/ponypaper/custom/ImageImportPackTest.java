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
        failures += run("parseAndNormalizeNudges", ImageImportPackTest::testParseAndNormalizeNudges);
        failures += run("hopCurve", ImageImportPackTest::testHopCurve);
        failures += run("packLiftsHop", ImageImportPackTest::testPackLiftsHop);
        failures += run("packNudgesShift", ImageImportPackTest::testPackNudgesShift);
        failures += run("packCellMatchesSheetSlice", ImageImportPackTest::testPackCellMatchesSheetSlice);
        failures += run("packCellMatchesSheetSliceWithNudge", ImageImportPackTest::testPackCellMatchesSheetSliceWithNudge);
        failures += run("inspectIncludesLiftInCellHeight", ImageImportPackTest::testInspectIncludesLiftInCellHeight);
        failures += run("inspectIncludesNudgeInCellWidth", ImageImportPackTest::testInspectIncludesNudgeInCellWidth);
        failures += run("liftsLengthMismatch", ImageImportPackTest::testLiftsLengthMismatch);
        failures += run("nudgesLengthMismatch", ImageImportPackTest::testNudgesLengthMismatch);
        failures += run("negativeLiftRejected", ImageImportPackTest::testNegativeLiftRejected);
        failures += run("scale50HalvesCell", ImageImportPackTest::testScale50HalvesCell);
        failures += run("scale100IsIdentity", ImageImportPackTest::testScale100IsIdentity);
        failures += run("scale25AndSixteenth", ImageImportPackTest::testScale25AndSixteenth);
        failures += run("scaleSuccessiveHalvesMatchRepeated50", ImageImportPackTest::testScaleSuccessiveHalvesMatchRepeated50);
        failures += run("scaleEvenPhaseIgnoresOddSpeckles", ImageImportPackTest::testScaleEvenPhaseIgnoresOddSpeckles);
        failures += run("scaleEvenPhaseKeepsBlockOrigin", ImageImportPackTest::testScaleEvenPhaseKeepsBlockOrigin);
        failures += run("scaleDropsLonelyEvenSpeckles", ImageImportPackTest::testScaleDropsLonelyEvenSpeckles);
        failures += run("scaleHalfKeepsLonelyPixel", ImageImportPackTest::testScaleHalfKeepsLonelyPixel);
        failures += run("scaleInvalidRejected", ImageImportPackTest::testScaleInvalidRejected);
        failures += run("scale200DoublesCell", ImageImportPackTest::testScale200DoublesCell);
        failures += run("scale200ThenHalfIsIdentity", ImageImportPackTest::testScale200ThenHalfIsIdentity);
        failures += run("scale200CannotCombineWithShrink", ImageImportPackTest::testScale200CannotCombineWithShrink);
        failures += run("parseScaleDivisor", ImageImportPackTest::testParseScaleDivisor);
        failures += run("parseScale200", ImageImportPackTest::testParseScale200);
        failures += run("parseScale150Removed", ImageImportPackTest::testParseScale150Removed);
        failures += run("fitBuiltinScaleDivisor", ImageImportPackTest::testFitBuiltinScaleDivisor);
        failures += run("defaultScaleForOversizedFrames", ImageImportPackTest::testDefaultScaleForOversizedFrames);
        failures += run("sheetPixelBudget", ImageImportPackTest::testSheetPixelBudget);
        failures += run("explicitTimingsCs", ImageImportPackTest::testExplicitTimingsCs);
        failures += run("gifLoadIsNativeSize", ImageImportPackTest::testGifLoadIsNativeSize);
        failures += run("gifLoadHalfScale", ImageImportPackTest::testGifLoadHalfScale);
        failures += run("gifLoadSharedOptionsDoesNotLeakTimings",
                ImageImportPackTest::testGifLoadSharedOptionsDoesNotLeakTimings);
        failures += run("permuteOrder", ImageImportPackTest::testPermuteOrder);
        failures += run("permutePacksInGivenOrder", ImageImportPackTest::testPermutePacksInGivenOrder);
        failures += run("permuteTimingsTravel", ImageImportPackTest::testPermuteTimingsTravel);
        failures += run("gatherSequence", ImageImportPackTest::testGatherSequence);
        failures += run("gatherPacksClonesAndDeletes", ImageImportPackTest::testGatherPacksClonesAndDeletes);
        failures += run("gatherTimingsTravel", ImageImportPackTest::testGatherTimingsTravel);
        failures += run("insertAfterAndRemoveAt", ImageImportPackTest::testInsertAfterAndRemoveAt);
        failures += run("applyFlops", ImageImportPackTest::testApplyFlops);
        failures += run("gatherThenFlopOneClone", ImageImportPackTest::testGatherThenFlopOneClone);
        failures += run("splitSheetRoundTrip", ImageImportPackTest::testSplitSheetRoundTrip);
        failures += run("extractFramesUnevenAndTrim", ImageImportPackTest::testExtractFramesUnevenAndTrim);
        failures += run("extractFramesKeepsPropInCell", ImageImportPackTest::testExtractFramesKeepsPropInCell);
        failures += run("extractFramesRejectsOverlap", ImageImportPackTest::testExtractFramesRejectsOverlap);
        failures += run("suggestFrameCount", ImageImportPackTest::testSuggestFrameCount);
        failures += run("equalBordersMatchesSplitSheet", ImageImportPackTest::testEqualBordersMatchesSplitSheet);
        failures += run("frameExportFileName", ImageImportPackTest::testFrameExportFileName);
        failures += run("writeFramePngs", ImageImportPackTest::testWriteFramePngs);
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

    private static void testParseAndNormalizeNudges() throws IOException {
        int[] parsed = ImageImport.parseNudges(" 0 , -4,8 ");
        assertEq("len", 3, parsed.length);
        assertEq("n0", 0, parsed[0]);
        assertEq("n1", -4, parsed[1]);
        assertEq("n2", 8, parsed[2]);
        assertEq("format", "0,-4,8", ImageImport.formatNudges(parsed));

        int[] zeros = ImageImport.normalizeNudges(null, 3);
        assertEq("null nudges length", 3, zeros.length);
        assertEq("null nudges zero", 0, zeros[0] + zeros[1] + zeros[2]);

        try {
            ImageImport.parseNudges("0,,4");
            throw new AssertionError("expected empty-part failure");
        } catch (IOException e) {
            if (!e.getMessage().contains("Empty nudge")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }

        int[] kept = ImageImport.normalizeNudges(new int[] {-2, 0, 5}, 3);
        assertEq("kept -2", -2, kept[0]);
        assertEq("kept 5", 5, kept[2]);
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

    private static void testPackNudgesShift() throws IOException {
        BufferedImage a = solid(8, 8, 0xffff0000);
        BufferedImage b = solid(8, 8, 0xff0000ff);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.nudges = new int[] {0, 4};
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a, b), opts);
        assertEq("cellW", 16, packed.cellWidth);
        assertEq("cellH", 8, packed.cellHeight);
        BufferedImage sheet = decode(packed.loadedImage);
        assertEq("sheetW", 32, sheet.getWidth());
        assertEq("sheetH", 8, sheet.getHeight());

        // Centred frame sits at dx=4 in a 16px cell.
        assertEq("centred body", 0xffff0000, sheet.getRGB(4, 4));
        assertEq("centred left air", 0, sheet.getRGB(3, 4));
        assertEq("centred right of sprite", 0, sheet.getRGB(12, 4));
        // Nudged +4 sits at dx=8; left of that is air, right edge is opaque.
        assertEq("nudged body", 0xff0000ff, sheet.getRGB(16 + 8, 4));
        assertEq("nudged left air", 0, sheet.getRGB(16 + 7, 4));
        assertEq("nudged right edge", 0xff0000ff, sheet.getRGB(16 + 15, 4));
    }

    private static void testPackCellMatchesSheetSlice() throws IOException {
        BufferedImage a = solid(8, 8, 0xffff0000);
        BufferedImage b = solid(8, 8, 0xff0000ff);
        int[] lifts = new int[] {0, 4};
        List<BufferedImage> frames = Arrays.asList(a, b);
        ImageImport.PackPreview preview = ImageImport.inspectFrames(frames, lifts);
        BufferedImage sheet = ImageImport.packSheetImage(
                frames, preview.cellWidth, preview.cellHeight, lifts);
        BufferedImage cell0 = ImageImport.packCellImage(
                a, preview.cellWidth, preview.cellHeight, lifts[0]);
        BufferedImage cell1 = ImageImport.packCellImage(
                b, preview.cellWidth, preview.cellHeight, lifts[1]);
        assertEq("cellW", preview.cellWidth, cell0.getWidth());
        assertEq("cellH", preview.cellHeight, cell0.getHeight());
        for (int y = 0; y < preview.cellHeight; y++) {
            for (int x = 0; x < preview.cellWidth; x++) {
                assertEq("cell0 " + x + "," + y, sheet.getRGB(x, y), cell0.getRGB(x, y));
                assertEq("cell1 " + x + "," + y,
                        sheet.getRGB(preview.cellWidth + x, y), cell1.getRGB(x, y));
            }
        }
    }

    private static void testPackCellMatchesSheetSliceWithNudge() throws IOException {
        BufferedImage a = solid(8, 8, 0xffff0000);
        BufferedImage b = solid(8, 8, 0xff0000ff);
        int[] lifts = new int[] {0, 0};
        int[] nudges = new int[] {-3, 4};
        List<BufferedImage> frames = Arrays.asList(a, b);
        ImageImport.PackPreview preview = ImageImport.inspectFrames(frames, lifts, nudges);
        BufferedImage sheet = ImageImport.packSheetImage(
                frames, preview.cellWidth, preview.cellHeight, lifts, nudges);
        BufferedImage cell0 = ImageImport.packCellImage(
                a, preview.cellWidth, preview.cellHeight, lifts[0], nudges[0]);
        BufferedImage cell1 = ImageImport.packCellImage(
                b, preview.cellWidth, preview.cellHeight, lifts[1], nudges[1]);
        assertEq("cellW", preview.cellWidth, cell0.getWidth());
        assertEq("cellH", preview.cellHeight, cell0.getHeight());
        for (int y = 0; y < preview.cellHeight; y++) {
            for (int x = 0; x < preview.cellWidth; x++) {
                assertEq("cell0 " + x + "," + y, sheet.getRGB(x, y), cell0.getRGB(x, y));
                assertEq("cell1 " + x + "," + y,
                        sheet.getRGB(preview.cellWidth + x, y), cell1.getRGB(x, y));
            }
        }
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

    private static void testInspectIncludesNudgeInCellWidth() throws IOException {
        BufferedImage tall = solid(10, 20, 0xffff0000);
        BufferedImage shortFrame = solid(20, 10, 0xff0000ff);
        ImageImport.PackPreview noNudge = ImageImport.inspectFrames(Arrays.asList(tall, shortFrame));
        assertEq("no-nudge cellW", 20, noNudge.cellWidth);
        assertEq("no-nudge cellH", 20, noNudge.cellHeight);

        ImageImport.PackPreview shifted = ImageImport.inspectFrames(
                Arrays.asList(tall, shortFrame), new int[] {0, 0}, new int[] {0, 4});
        assertEq("shifted cellW", 28, shifted.cellWidth);
        assertEq("shifted cellH", 20, shifted.cellHeight);

        ImageImport.PackPreview wider = ImageImport.inspectFrames(
                Arrays.asList(tall, shortFrame), null, new int[] {6, 0});
        assertEq("wider cellW", 22, wider.cellWidth);
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

    private static void testNudgesLengthMismatch() throws IOException {
        BufferedImage a = solid(4, 4, 0xff00ff00);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.nudges = new int[] {0, 4};
        try {
            ImageImport.fromFrames(Arrays.asList(a), opts);
            throw new AssertionError("expected nudge-count failure");
        } catch (IOException e) {
            if (!e.getMessage().contains("Expected 1 nudges")) {
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
        opts.scaleDivisor = ImageImport.SCALE_DIVISOR_HALF;
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
        opts.scaleDivisor = ImageImport.SCALE_DIVISOR_NATIVE;
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a), opts);
        assertEq("cellW", 10, packed.cellWidth);
        assertEq("cellH", 8, packed.cellHeight);
    }

    private static void testScale25AndSixteenth() throws IOException {
        BufferedImage a = solid(64, 64, 0xff00ff00);
        ImageImport.PackOptions quarter = new ImageImport.PackOptions();
        quarter.scaleDivisor = ImageImport.SCALE_DIVISOR_QUARTER;
        ImageImport packedQ = ImageImport.fromFrames(Arrays.asList(a), quarter);
        assertEq("quarter W", 16, packedQ.cellWidth);
        assertEq("quarter H", 16, packedQ.cellHeight);

        ImageImport.PackOptions sixteenth = new ImageImport.PackOptions();
        sixteenth.scaleDivisor = ImageImport.SCALE_DIVISOR_SIXTEENTH;
        ImageImport packedS = ImageImport.fromFrames(Arrays.asList(a), sixteenth);
        assertEq("sixteenth W", 4, packedS.cellWidth);
        assertEq("sixteenth H", 4, packedS.cellHeight);
    }

    private static void testScaleSuccessiveHalvesMatchRepeated50() throws IOException {
        BufferedImage src = solid(101, 80, 0xffff00ff);
        BufferedImage once = ImageImport.scaleImage(src, ImageImport.SCALE_DIVISOR_SIXTEENTH);
        BufferedImage step = src;
        for (int i = 0; i < 4; i++) {
            step = ImageImport.scaleImage(step, ImageImport.SCALE_DIVISOR_HALF);
        }
        assertEq("w", step.getWidth(), once.getWidth());
        assertEq("h", step.getHeight(), once.getHeight());
        for (int y = 0; y < step.getHeight(); y++) {
            for (int x = 0; x < step.getWidth(); x++) {
                assertEq("px " + x + "," + y, step.getRGB(x, y), once.getRGB(x, y));
            }
        }
    }

    /**
     * Graphics2D nearest-neighbour halves sample the odd pixel of each 2×2, so
     * an opaque speck in the bottom-right of an 8×8 empty block became a full
     * black pixel after fit/÷8. Even-lattice sampling must discard it.
     */
    private static void testScaleEvenPhaseIgnoresOddSpeckles() throws IOException {
        BufferedImage src = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        src.setRGB(7, 7, 0xff000000);
        src.setRGB(15, 15, 0xff000000);
        BufferedImage scaled = ImageImport.scaleImage(src, ImageImport.SCALE_DIVISOR_EIGHTH);
        assertEq("w", 2, scaled.getWidth());
        assertEq("h", 2, scaled.getHeight());
        assertEq("top-left block", 0x00000000, scaled.getRGB(0, 0));
        assertEq("bottom-right block", 0x00000000, scaled.getRGB(1, 1));
    }

    /** Top-left of each D×D block is the sample kept through a large shrink. */
    private static void testScaleEvenPhaseKeepsBlockOrigin() throws IOException {
        BufferedImage src = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);
        // Fill each 8×8 texel so ÷8 density check keeps the even-lattice sample.
        fillRect(src, 0, 0, 8, 8, 0xffff0000);
        fillRect(src, 8, 0, 8, 8, 0xff00ff00);
        src.setRGB(1, 1, 0xff0000ff); // odd-phase neighbour must not win
        BufferedImage scaled = ImageImport.scaleImage(src, ImageImport.SCALE_DIVISOR_EIGHTH);
        assertEq("w", 2, scaled.getWidth());
        assertEq("h", 1, scaled.getHeight());
        assertEq("left", 0xffff0000, scaled.getRGB(0, 0));
        assertEq("right", 0xff00ff00, scaled.getRGB(1, 0));
    }

    /** Sparse even-lattice speckles must die on ÷4 and larger. */
    private static void testScaleDropsLonelyEvenSpeckles() throws IOException {
        BufferedImage src = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        src.setRGB(0, 0, 0xff000000);
        src.setRGB(1, 1, 0xff000000); // still only 2 opaque in the 8×8 (< 8)
        src.setRGB(8, 8, 0xff000000);
        BufferedImage eighth = ImageImport.scaleImage(src, ImageImport.SCALE_DIVISOR_EIGHTH);
        assertEq("eighth sparse TL", 0x00000000, eighth.getRGB(0, 0));
        assertEq("eighth sparse BR", 0x00000000, eighth.getRGB(1, 1));

        BufferedImage quarterSrc = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        quarterSrc.setRGB(0, 0, 0xff000000);
        quarterSrc.setRGB(1, 0, 0xff000000);
        quarterSrc.setRGB(0, 1, 0xff000000); // 3 opaque in 4×4 (< 4)
        BufferedImage quarter = ImageImport.scaleImage(quarterSrc, ImageImport.SCALE_DIVISOR_QUARTER);
        assertEq("quarter sparse", 0x00000000, quarter.getRGB(0, 0));

        // A dense enough block keeps the even-lattice sample.
        BufferedImage solidish = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        fillRect(solidish, 0, 0, 8, 1, 0xffff0000); // 8 opaque (== divisor)
        BufferedImage kept = ImageImport.scaleImage(solidish, ImageImport.SCALE_DIVISOR_EIGHTH);
        assertEq("dense keeps sample", 0xffff0000, kept.getRGB(0, 0));
    }

    /** ÷2 must not strip thin Desktop Ponies edge detail. */
    private static void testScaleHalfKeepsLonelyPixel() throws IOException {
        BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        src.setRGB(0, 0, 0xff00ff00);
        BufferedImage half = ImageImport.scaleImage(src, ImageImport.SCALE_DIVISOR_HALF);
        assertEq("half keeps lonely", 0xff00ff00, half.getRGB(0, 0));
    }

    private static void testScaleInvalidRejected() throws IOException {
        BufferedImage a = solid(4, 4, 0xff0000ff);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.scaleDivisor = 3;
        try {
            ImageImport.fromFrames(Arrays.asList(a), opts);
            throw new AssertionError("expected invalid scale failure");
        } catch (IOException e) {
            if (!e.getMessage().contains("1, 2, 4, 8, or 16")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testScale200DoublesCell() throws IOException {
        BufferedImage a = new BufferedImage(5, 3, BufferedImage.TYPE_INT_ARGB);
        a.setRGB(0, 0, 0xffff0000);
        a.setRGB(4, 2, 0xff00ff00);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.scaleNumerator = ImageImport.SCALE_NUMERATOR_DOUBLE;
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(a), opts);
        assertEq("cellW", 10, packed.cellWidth);
        assertEq("cellH", 6, packed.cellHeight);
        BufferedImage sheet = decode(packed.loadedImage);
        assertEq("tl", 0xffff0000, sheet.getRGB(0, 0));
        assertEq("tl replica", 0xffff0000, sheet.getRGB(1, 1));
        assertEq("br", 0xff00ff00, sheet.getRGB(8, 4));
        assertEq("br replica", 0xff00ff00, sheet.getRGB(9, 5));
        assertEq("dim", 10, ImageImport.scaleDimension(5,
                ImageImport.SCALE_NUMERATOR_DOUBLE, ImageImport.SCALE_DIVISOR_NATIVE));
        try {
            ImageImport.scaleDimension(Integer.MAX_VALUE / 2 + 1,
                    ImageImport.SCALE_NUMERATOR_DOUBLE, ImageImport.SCALE_DIVISOR_NATIVE);
            throw new AssertionError("expected overflow failure");
        } catch (IOException e) {
            if (!e.getMessage().contains("too large")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testScale200ThenHalfIsIdentity() throws IOException {
        BufferedImage src = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        src.setRGB(0, 0, 0xffff0000);
        src.setRGB(1, 0, 0xff00ff00);
        src.setRGB(2, 0, 0xff0000ff);
        src.setRGB(3, 0, 0xffffff00);
        src.setRGB(0, 1, 0xff00ffff);
        src.setRGB(1, 1, 0xffff00ff);
        src.setRGB(2, 1, 0xff000000);
        src.setRGB(3, 1, 0xffffffff);
        BufferedImage doubled = ImageImport.scaleImage(src,
                ImageImport.SCALE_NUMERATOR_DOUBLE, ImageImport.SCALE_DIVISOR_NATIVE);
        assertEq("doubled W", 8, doubled.getWidth());
        assertEq("doubled H", 4, doubled.getHeight());
        BufferedImage back = ImageImport.scaleImage(doubled, ImageImport.SCALE_DIVISOR_HALF);
        assertEq("back W", src.getWidth(), back.getWidth());
        assertEq("back H", src.getHeight(), back.getHeight());
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                assertEq("px " + x + "," + y, src.getRGB(x, y), back.getRGB(x, y));
            }
        }
    }

    private static void testScale200CannotCombineWithShrink() throws IOException {
        BufferedImage a = solid(4, 4, 0xff112233);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.scaleNumerator = ImageImport.SCALE_NUMERATOR_DOUBLE;
        opts.scaleDivisor = ImageImport.SCALE_DIVISOR_HALF;
        try {
            ImageImport.fromFrames(Arrays.asList(a), opts);
            throw new AssertionError("expected combine failure");
        } catch (IOException e) {
            if (!e.getMessage().contains("cannot combine")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testParseScaleDivisor() throws IOException {
        assertEq("100", ImageImport.SCALE_DIVISOR_NATIVE, ImageImport.parseScaleDivisor("100"));
        assertEq("50%", ImageImport.SCALE_DIVISOR_HALF, ImageImport.parseScaleDivisor("50%"));
        assertEq("half", ImageImport.SCALE_DIVISOR_HALF, ImageImport.parseScaleDivisor("half"));
        assertEq("native", ImageImport.SCALE_DIVISOR_NATIVE, ImageImport.parseScaleDivisor("native"));
        assertEq("25", ImageImport.SCALE_DIVISOR_QUARTER, ImageImport.parseScaleDivisor("25"));
        assertEq("12.5", ImageImport.SCALE_DIVISOR_EIGHTH, ImageImport.parseScaleDivisor("12.5"));
        assertEq("6.25%", ImageImport.SCALE_DIVISOR_SIXTEENTH, ImageImport.parseScaleDivisor("6.25%"));
        assertEq("1/8", ImageImport.SCALE_DIVISOR_EIGHTH, ImageImport.parseScaleDivisor("1/8"));
        assertEq("fit", -1, ImageImport.parseScaleDivisor("fit"));
        assertEq("legacy parseScalePercent", ImageImport.SCALE_DIVISOR_HALF,
                ImageImport.parseScalePercent("half"));
        try {
            ImageImport.parseScaleDivisor("75");
            throw new AssertionError("expected 75 to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("1, 2, 4, 8, or 16")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
        try {
            ImageImport.parseScaleDivisor("200");
            throw new AssertionError("expected 200 shrink parse to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("200%")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
        try {
            ImageImport.parseScaleDivisor("150");
            throw new AssertionError("expected 150 shrink parse to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("150%")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testParseScale200() throws IOException {
        assertScale("200", 2, 1, false, ImageImport.parseScale("200"));
        assertScale("200%", 2, 1, false, ImageImport.parseScale("200%"));
        assertScale("2x", 2, 1, false, ImageImport.parseScale("2x"));
        assertScale("double", 2, 1, false, ImageImport.parseScale("double"));
        assertScale("2/1", 2, 1, false, ImageImport.parseScale("2/1"));
        assertScale("bare 2 is half", 1, 2, false, ImageImport.parseScale("2"));
        assertScale("fit", 1, 1, true, ImageImport.parseScale("fit"));
        assertEq("format 200", "200%", ImageImport.formatScale(2, 1));
        assertEq("label 200", "200% (×2)", ImageImport.formatScaleLabel(2, 1));
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        ImageImport.applyScale(opts, ImageImport.parseScale("2x"));
        assertEq("applied num", 2, opts.scaleNumerator);
        assertEq("applied div", 1, opts.scaleDivisor);
        assertEq("applied fit", false, opts.scaleFitBuiltin);
        ImageImport.applyScale(opts, ImageImport.parseScale("fit"));
        assertEq("fit flag", true, opts.scaleFitBuiltin);

        BufferedImage small = solid(40, 40, 0xff112233);
        ImageImport.PackOptions fit = new ImageImport.PackOptions();
        fit.scaleFitBuiltin = true;
        ImageImport.ScaleSpec resolved = ImageImport.resolveScale(fit, Arrays.asList(small));
        assertEq("fit num", 1, resolved.numerator);
        assertEq("fit div", ImageImport.SCALE_DIVISOR_NATIVE, resolved.divisor);
        assertEq("fit not pending", false, resolved.fit);
    }

    private static void testParseScale150Removed() throws IOException {
        String[] tokens = { "150", "150%", "1.5x", "x1.5", "1.5", "3/2" };
        for (int i = 0; i < tokens.length; i++) {
            try {
                ImageImport.parseScale(tokens[i]);
                throw new AssertionError("expected " + tokens[i] + " to fail");
            } catch (IOException e) {
                if (!e.getMessage().contains("150%")) {
                    throw new AssertionError(tokens[i] + ": " + e.getMessage());
                }
            }
        }
        try {
            ImageImport.normalizeScaleNumerator(3);
            throw new AssertionError("expected numerator 3 to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("1 or 2")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void assertScale(String label, int numerator, int divisor, boolean fit,
            ImageImport.ScaleSpec spec) throws IOException {
        if (spec == null) {
            throw new AssertionError(label + ": null spec");
        }
        assertEq(label + " num", numerator, spec.numerator);
        assertEq(label + " div", divisor, spec.divisor);
        assertEq(label + " fit", fit, spec.fit);
    }

    private static void testFitBuiltinScaleDivisor() throws IOException {
        assertEq("already small", ImageImport.SCALE_DIVISOR_NATIVE,
                ImageImport.fitBuiltinScaleDivisor(40));
        assertEq("just over", ImageImport.SCALE_DIVISOR_HALF,
                ImageImport.fitBuiltinScaleDivisor(ImageImport.LARGE_CELL_HEIGHT_PX + 1));
        assertEq("needs quarter", ImageImport.SCALE_DIVISOR_QUARTER,
                ImageImport.fitBuiltinScaleDivisor(200));
        assertEq("needs sixteenth", ImageImport.SCALE_DIVISOR_SIXTEENTH,
                ImageImport.fitBuiltinScaleDivisor(2000));

        BufferedImage tall = solid(100, 200, 0xffabcdef);
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.scaleFitBuiltin = true;
        ImageImport packed = ImageImport.fromFrames(Arrays.asList(tall), opts);
        assertEq("fit cellW", 25, packed.cellWidth);
        assertEq("fit cellH", 50, packed.cellHeight);
        assertEq("fit under limit", true, packed.cellHeight <= ImageImport.LARGE_CELL_HEIGHT_PX);
    }

    private static void testDefaultScaleForOversizedFrames() throws IOException {
        BufferedImage small = solid(40, 40, 0xff112233);
        BufferedImage tall = solid(64, 864, 0xff445566);
        List<BufferedImage> smallFrames = Arrays.asList(small, small);
        List<BufferedImage> tallFrames = Arrays.asList(tall, tall);

        assertEq("small stays native", ImageImport.SCALE_DIVISOR_NATIVE,
                ImageImport.defaultScaleDivisorForFrames(
                        smallFrames, ImageImport.SCALE_DIVISOR_NATIVE));
        assertEq("small not fit", false,
                ImageImport.shouldDefaultToFitBuiltin(
                        smallFrames, ImageImport.SCALE_DIVISOR_NATIVE));

        assertEq("864px → sixteenth", ImageImport.SCALE_DIVISOR_SIXTEENTH,
                ImageImport.defaultScaleDivisorForFrames(
                        tallFrames, ImageImport.SCALE_DIVISOR_NATIVE));
        assertEq("tall prefers fit", true,
                ImageImport.shouldDefaultToFitBuiltin(
                        tallFrames, ImageImport.SCALE_DIVISOR_NATIVE));
        assertEq("legacy percent 100 also fits", ImageImport.SCALE_DIVISOR_SIXTEENTH,
                ImageImport.defaultScaleDivisorForFrames(tallFrames, 100));

        // Explicit half on mid-size art that already fits after ÷2 stays half.
        BufferedImage mid = solid(80, 100, 0xff778899);
        assertEq("requested half kept when under limit", ImageImport.SCALE_DIVISOR_HALF,
                ImageImport.defaultScaleDivisorForFrames(
                        Arrays.asList(mid), ImageImport.SCALE_DIVISOR_HALF));
    }

    private static void testSheetPixelBudget() throws IOException {
        assertEq("under budget", false,
                ImageImport.exceedsSheetPixelBudget(1000, 80));
        assertEq("over budget", true,
                ImageImport.exceedsSheetPixelBudget(90112, 864));
        assertEq("pixel count", 90112L * 864L,
                ImageImport.sheetPixelCount(90112, 864));
        assertEq("argb bytes", 90112L * 864L * 4L,
                ImageImport.sheetArgbBytes(90112, 864));
        String sized = ImageImport.formatByteSize(ImageImport.sheetArgbBytes(90112, 864));
        if (!sized.endsWith("MB") && !sized.endsWith("GB")) {
            throw new AssertionError("expected MB/GB label, got " + sized);
        }
        assertEq("notes mention fit", true,
                ImageImport.packerScaleNotes().contains("Fit to built-in"));
        assertEq("notes mention 200", true,
                ImageImport.packerScaleNotes().contains("200%"));
        assertEq("notes omit 150 pack", false,
                ImageImport.packerScaleNotes().contains("150%"));
        assertEq("order notes mention clone", true,
                ImageImport.packerOrderNotes().contains("Clone"));
        assertEq("order notes mention delete", true,
                ImageImport.packerOrderNotes().contains("Delete"));
        assertEq("order notes mention mirror", true,
                ImageImport.packerOrderNotes().contains("Mirror"));
        assertEq("placement notes mention lift", true,
                ImageImport.packerPlacementNotes().contains("Lift"));
        assertEq("placement notes mention nudge", true,
                ImageImport.packerPlacementNotes().contains("Nudge"));
        assertEq("width under", false, ImageImport.exceedsSheetWidthBudget(4096));
        assertEq("width over", true, ImageImport.exceedsSheetWidthBudget(4097));
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

    private static void testPermuteOrder() throws IOException {
        int[] id = ImageImport.normalizeOrder(null, 4);
        assertEq("null order length", 4, id.length);
        assertEq("null is identity", true, ImageImport.isIdentityOrder(id));
        assertEq("null,n identity", true, ImageImport.isIdentityOrder(null, 3));
        assertEq("explicit identity", true, ImageImport.isIdentityOrder(new int[] {0, 1, 2}));
        assertEq("reversed not identity", false, ImageImport.isIdentityOrder(new int[] {2, 1, 0}));
        assertEq("wrong length not identity", false, ImageImport.isIdentityOrder(new int[] {0, 1}, 3));

        List<String> items = Arrays.asList("a", "b", "c");
        List<String> permuted = ImageImport.permute(items, new int[] {2, 0, 1});
        assertEq("list[0]", "c", permuted.get(0));
        assertEq("list[1]", "a", permuted.get(1));
        assertEq("list[2]", "b", permuted.get(2));
        assertEq("source unchanged", "a", items.get(0));

        List<String> same = ImageImport.permute(items, new int[] {0, 1, 2});
        assertEq("identity[0]", "a", same.get(0));
        assertEq("identity[2]", "c", same.get(2));

        int[] vals = ImageImport.permute(new int[] {10, 20, 30}, new int[] {2, 0, 1});
        assertEq("int[0]", 30, vals[0]);
        assertEq("int[1]", 10, vals[1]);
        assertEq("int[2]", 20, vals[2]);

        try {
            ImageImport.normalizeOrder(new int[] {0, 0, 1}, 3);
            throw new AssertionError("expected duplicate order to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("Duplicate")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
        try {
            ImageImport.normalizeOrder(new int[] {0, 3}, 2);
            throw new AssertionError("expected out-of-range order to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("out of range")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
        try {
            ImageImport.normalizeOrder(new int[] {0}, 2);
            throw new AssertionError("expected length mismatch to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("Expected 2")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testGatherSequence() throws IOException {
        List<String> items = Arrays.asList("a", "b", "c");
        List<String> cloned = ImageImport.gather(items, new int[] {0, 1, 1, 2});
        assertEq("clone length", 4, cloned.size());
        assertEq("clone[0]", "a", cloned.get(0));
        assertEq("clone[1]", "b", cloned.get(1));
        assertEq("clone[2]", "b", cloned.get(2));
        assertEq("clone[3]", "c", cloned.get(3));
        assertEq("source unchanged", "b", items.get(1));

        List<String> deleted = ImageImport.gather(items, new int[] {0, 2});
        assertEq("delete length", 2, deleted.size());
        assertEq("delete[0]", "a", deleted.get(0));
        assertEq("delete[1]", "c", deleted.get(1));

        List<String> identity = ImageImport.gather(items, null);
        assertEq("null identity length", 3, identity.size());
        assertEq("null identity[2]", "c", identity.get(2));

        int[] vals = ImageImport.gather(new int[] {3, 7, 11}, new int[] {2, 2, 0});
        assertEq("int clone[0]", 11, vals[0]);
        assertEq("int clone[1]", 11, vals[1]);
        assertEq("int clone[2]", 3, vals[2]);

        assertEq("prefix is identity of its length", true,
                ImageImport.isIdentityOrder(new int[] {0, 1}));
        assertEq("prefix is not identity of source n", false,
                ImageImport.isIdentityOrder(new int[] {0, 1}, 3));
        assertEq("clone is not identity", false,
                ImageImport.isIdentityOrder(new int[] {0, 1, 1, 2}, 3));

        try {
            ImageImport.normalizeSequence(new int[0], 3);
            throw new AssertionError("expected empty sequence to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("empty")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
        try {
            ImageImport.normalizeSequence(new int[] {0, 3}, 3);
            throw new AssertionError("expected out-of-range sequence to fail");
        } catch (IOException e) {
            if (!e.getMessage().contains("out of range")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
        int[] dupOk = ImageImport.normalizeSequence(new int[] {0, 0, 1}, 3);
        assertEq("dup allowed", 0, dupOk[1]);
        assertEq("dup length", 3, dupOk.length);
    }

    private static void testGatherPacksClonesAndDeletes() throws IOException {
        BufferedImage red = solid(8, 8, 0xffff0000);
        BufferedImage blue = solid(8, 8, 0xff0000ff);
        List<BufferedImage> cloned = ImageImport.gather(
                Arrays.asList(red, blue), new int[] {0, 0, 1});
        ImageImport packed = ImageImport.fromFrames(cloned, new ImageImport.PackOptions());
        BufferedImage sheet = decode(packed.loadedImage);
        assertEq("clone sheetW", 24, sheet.getWidth());
        assertEq("cell 0 red", 0xffff0000, sheet.getRGB(4, 4));
        assertEq("cell 1 red clone", 0xffff0000, sheet.getRGB(12, 4));
        assertEq("cell 2 blue", 0xff0000ff, sheet.getRGB(20, 4));

        List<BufferedImage> dropped = ImageImport.gather(
                Arrays.asList(red, blue), new int[] {1});
        ImageImport one = ImageImport.fromFrames(dropped, new ImageImport.PackOptions());
        BufferedImage oneSheet = decode(one.loadedImage);
        assertEq("delete sheetW", 8, oneSheet.getWidth());
        assertEq("only blue", 0xff0000ff, oneSheet.getRGB(4, 4));
    }

    private static void testGatherTimingsTravel() throws IOException {
        BufferedImage a = solid(4, 4, 0xff00ff00);
        int[] order = {1, 1, 0};
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.timingsCs = ImageImport.gather(new int[] {3, 7}, order);
        ImageImport packed = ImageImport.fromFrames(
                ImageImport.gather(Arrays.asList(a, a), order), opts);
        assertEq("cloned timings", "7,7,3", packed.timings);
    }

    private static void testInsertAfterAndRemoveAt() {
        int[] cloned = ImageImport.insertAfter(new int[] {0, 1, 2}, 1, 1);
        assertEq("insert length", 4, cloned.length);
        assertEq("insert[0]", 0, cloned[0]);
        assertEq("insert[1]", 1, cloned[1]);
        assertEq("insert[2]", 1, cloned[2]);
        assertEq("insert[3]", 2, cloned[3]);

        int[] atEnd = ImageImport.insertAfter(new int[] {0, 1}, 1, 1);
        assertEq("end length", 3, atEnd.length);
        assertEq("end last", 1, atEnd[2]);

        int[] removed = ImageImport.removeAt(new int[] {0, 1, 2}, 1);
        assertEq("remove length", 2, removed.length);
        assertEq("remove[0]", 0, removed[0]);
        assertEq("remove[1]", 2, removed[1]);

        int[] dropLast = ImageImport.removeAt(new int[] {0, 1, 2}, 2);
        assertEq("drop last length", 2, dropLast.length);
        assertEq("drop last[1]", 1, dropLast[1]);

        try {
            ImageImport.removeAt(new int[] {0}, 0);
            throw new AssertionError("expected last-frame remove to fail");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            ImageImport.insertAfter(new int[] {0, 1}, 2, 0);
            throw new AssertionError("expected out-of-range insert to fail");
        } catch (IllegalArgumentException ignored) {
        }

        boolean[] clonedFlags = ImageImport.insertAfter(new boolean[] {false, true, false}, 1, true);
        assertEq("bool insert length", 4, clonedFlags.length);
        assertEq("bool insert[1]", true, clonedFlags[1]);
        assertEq("bool insert[2]", true, clonedFlags[2]);
        assertEq("bool insert[3]", false, clonedFlags[3]);
        boolean[] droppedFlags = ImageImport.removeAt(new boolean[] {true, false, true}, 1);
        assertEq("bool remove length", 2, droppedFlags.length);
        assertEq("bool remove[0]", true, droppedFlags[0]);
        assertEq("bool remove[1]", true, droppedFlags[1]);
        assertEq("anyTrue null", false, ImageImport.anyTrue(null));
        assertEq("anyTrue none", false, ImageImport.anyTrue(new boolean[] {false, false}));
        assertEq("anyTrue one", true, ImageImport.anyTrue(new boolean[] {false, true}));
    }

    private static void testApplyFlops() throws IOException {
        BufferedImage a = marker(8, 8, 0xffff0000, 0xff00ff00);
        BufferedImage b = marker(8, 8, 0xff0000ff, 0xffffff00);
        List<BufferedImage> frames = Arrays.asList(a, b);

        List<BufferedImage> same = ImageImport.applyFlops(frames, null);
        assertEq("null is same list", true, same == frames);
        same = ImageImport.applyFlops(frames, new boolean[] {false, false});
        assertEq("all-false is same list", true, same == frames);

        List<BufferedImage> one = ImageImport.applyFlops(frames, new boolean[] {true, false});
        assertEq("length", 2, one.size());
        assertEq("flopped A left", 0xff00ff00, one.get(0).getRGB(0, 0));
        assertEq("flopped A right", 0xffff0000, one.get(0).getRGB(7, 0));
        assertEq("B unchanged", true, one.get(1) == b);

        BufferedImage twice = ImageImport.applyFlops(
                Arrays.asList(ImageImport.flopFrame(a)), new boolean[] {true}).get(0);
        assertEq("double flop left", 0xffff0000, twice.getRGB(0, 0));
        assertEq("double flop right", 0xff00ff00, twice.getRGB(7, 0));

        try {
            ImageImport.applyFlops(frames, new boolean[] {true});
            throw new AssertionError("expected length mismatch");
        } catch (IOException e) {
            if (!e.getMessage().contains("length")) {
                throw new AssertionError("unexpected message: " + e.getMessage());
            }
        }
    }

    private static void testGatherThenFlopOneClone() throws IOException {
        BufferedImage a = marker(8, 8, 0xffff0000, 0xff00ff00);
        BufferedImage b = marker(8, 8, 0xff0000ff, 0xffffff00);
        List<BufferedImage> gathered = ImageImport.gather(
                Arrays.asList(a, b), new int[] {0, 1, 0});
        List<BufferedImage> flopped = ImageImport.applyFlops(
                gathered, new boolean[] {false, true, true});
        ImageImport packed = ImageImport.fromFrames(flopped, new ImageImport.PackOptions());
        BufferedImage sheet = decode(packed.loadedImage);
        assertEq("sheetW", 24, sheet.getWidth());
        // Slot 0: original A (red left).
        assertEq("A left", 0xffff0000, sheet.getRGB(0, 0));
        assertEq("A right", 0xff00ff00, sheet.getRGB(7, 0));
        // Slot 1: flopped B (yellow left).
        assertEq("flop B left", 0xffffff00, sheet.getRGB(8, 0));
        assertEq("flop B right", 0xff0000ff, sheet.getRGB(15, 0));
        // Slot 2: flopped A clone (green left); unflopped clone would stay red-left.
        assertEq("flop A clone left", 0xff00ff00, sheet.getRGB(16, 0));
        assertEq("flop A clone right", 0xffff0000, sheet.getRGB(23, 0));
    }

    private static void testPermutePacksInGivenOrder() throws IOException {
        BufferedImage red = solid(8, 8, 0xffff0000);
        BufferedImage blue = solid(8, 8, 0xff0000ff);
        List<BufferedImage> frames = ImageImport.permute(
                Arrays.asList(red, blue), new int[] {1, 0});
        ImageImport packed = ImageImport.fromFrames(frames, new ImageImport.PackOptions());
        BufferedImage sheet = decode(packed.loadedImage);
        assertEq("sheetW", 16, sheet.getWidth());
        assertEq("first cell is blue", 0xff0000ff, sheet.getRGB(4, 4));
        assertEq("second cell is red", 0xffff0000, sheet.getRGB(12, 4));
    }

    private static void testPermuteTimingsTravel() throws IOException {
        BufferedImage a = solid(4, 4, 0xff00ff00);
        int[] order = {2, 0, 1};
        ImageImport.PackOptions opts = new ImageImport.PackOptions();
        opts.timingsCs = ImageImport.permute(new int[] {3, 7, 11}, order);
        ImageImport packed = ImageImport.fromFrames(
                ImageImport.permute(Arrays.asList(a, a, a), order), opts);
        assertEq("timings follow frames", "11,3,7", packed.timings);
    }

    private static void testSplitSheetRoundTrip() throws IOException {
        BufferedImage red = solid(12, 8, 0xffff0000);
        BufferedImage blue = solid(12, 8, 0xff0000ff);
        BufferedImage green = solid(12, 8, 0xff00ff00);
        ImageImport packed = ImageImport.fromFrames(
                Arrays.asList(red, blue, green), new ImageImport.PackOptions());
        BufferedImage sheet = decode(packed.loadedImage);
        assertEq("sheetW", 36, sheet.getWidth());
        List<BufferedImage> cells = ImageImport.splitSheet(sheet, 3);
        assertEq("cell count", 3, cells.size());
        assertEq("cellW", 12, cells.get(0).getWidth());
        assertEq("cellH", 8, cells.get(0).getHeight());
        assertEq("cell 0", 0xffff0000, cells.get(0).getRGB(6, 4));
        assertEq("cell 1", 0xff0000ff, cells.get(1).getRGB(6, 4));
        assertEq("cell 2", 0xff00ff00, cells.get(2).getRGB(6, 4));

        // Remainder pixels on the right of a non-divisible sheet are dropped
        // (11 / 2 = 5; pixel x=10 is unused).
        BufferedImage uneven = new BufferedImage(11, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 11; x++) {
                uneven.setRGB(x, y, x < 5 ? 0xffff0000 : (x < 10 ? 0xff0000ff : 0xff00ff00));
            }
        }
        List<BufferedImage> two = ImageImport.splitSheet(uneven, 2);
        assertEq("uneven cellW", 5, two.get(0).getWidth());
        assertEq("first cell", 0xffff0000, two.get(0).getRGB(4, 0));
        assertEq("second cell", 0xff0000ff, two.get(1).getRGB(0, 0));
        assertEq("second cell last used", 0xff0000ff, two.get(1).getRGB(4, 0));
    }

    private static void testExtractFramesUnevenAndTrim() throws IOException {
        // Uneven frames with a 2px gutter and 1px left pad.
        // Frame0 [1,9): 4×4 red inset; Frame1 [11,19): 4×4 blue inset.
        BufferedImage sheet = new BufferedImage(19, 8, BufferedImage.TYPE_INT_ARGB);
        fillRect(sheet, 1 + 2, 2, 4, 4, 0xffff0000);
        fillRect(sheet, 11 + 2, 2, 4, 4, 0xff0000ff);

        ImageImport.FrameBorders borders = new ImageImport.FrameBorders();
        borders.starts = new int[] {1, 11};
        borders.ends = new int[] {9, 19};
        borders.trimMargins = true;

        assertEq("unused L", 1, ImageImport.bordersUnusedLeft(borders));
        assertEq("unused R", 0, ImageImport.bordersUnusedRight(19, borders));
        assertEq("gaps", 2, ImageImport.bordersGapTotal(borders));

        List<BufferedImage> frames = ImageImport.extractFrames(sheet, borders);
        assertEq("count", 2, frames.size());
        assertEq("trim W0", 4, frames.get(0).getWidth());
        assertEq("trim H0", 4, frames.get(0).getHeight());
        assertEq("red", 0xffff0000, frames.get(0).getRGB(0, 0));
        assertEq("blue", 0xff0000ff, frames.get(1).getRGB(0, 0));

        borders.trimMargins = false;
        List<BufferedImage> raw = ImageImport.extractFrames(sheet, borders);
        assertEq("raw W0", 8, raw.get(0).getWidth());
        assertEq("raw W1", 8, raw.get(1).getWidth());
        assertEq("raw H", 8, raw.get(0).getHeight());
        assertEq("raw pad", 0, raw.get(0).getRGB(0, 0) >>> 24);
    }

    private static void testExtractFramesKeepsPropInCell() throws IOException {
        // Character (left) + prop (right) in one interval with a transparent column
        // between them — content-aware split would break them apart; borders must not.
        BufferedImage sheet = new BufferedImage(20, 6, BufferedImage.TYPE_INT_ARGB);
        fillRect(sheet, 1, 1, 3, 4, 0xffff00ff); // pony
        fillRect(sheet, 6, 2, 2, 3, 0xffffff00); // prop (gap at x=4,5)
        fillRect(sheet, 11, 1, 3, 4, 0xff00ffff); // frame 1 body
        fillRect(sheet, 16, 2, 2, 3, 0xff00ff00); // frame 1 prop

        ImageImport.FrameBorders borders = new ImageImport.FrameBorders();
        borders.starts = new int[] {0, 10};
        borders.ends = new int[] {9, 19};
        borders.trimMargins = true;

        List<BufferedImage> frames = ImageImport.extractFrames(sheet, borders);
        assertEq("count", 2, frames.size());
        boolean hasPony = false;
        boolean hasProp = false;
        BufferedImage f0 = frames.get(0);
        for (int y = 0; y < f0.getHeight(); y++) {
            for (int x = 0; x < f0.getWidth(); x++) {
                int rgb = f0.getRGB(x, y);
                if (rgb == 0xffff00ff) {
                    hasPony = true;
                }
                if (rgb == 0xffffff00) {
                    hasProp = true;
                }
            }
        }
        assertEq("pony kept", true, hasPony);
        assertEq("prop kept", true, hasProp);
    }

    private static void testExtractFramesRejectsOverlap() {
        BufferedImage sheet = new BufferedImage(20, 8, BufferedImage.TYPE_INT_ARGB);
        ImageImport.FrameBorders borders = new ImageImport.FrameBorders();
        borders.starts = new int[] {0, 6};
        borders.ends = new int[] {8, 16};
        try {
            ImageImport.validateFrameBorders(sheet, borders);
            throw new AssertionError("expected overlap");
        } catch (IOException e) {
            assertEq("mentions overlap", true, e.getMessage().contains("overlaps"));
        }

        borders.starts = new int[] {0, 8, 16};
        borders.ends = new int[] {8, 16, 24};
        try {
            ImageImport.validateFrameBorders(sheet, borders);
            throw new AssertionError("expected overflow");
        } catch (IOException e) {
            assertEq("mentions width", true, e.getMessage().contains("sheet width"));
        }
    }

    private static void testSuggestFrameCount() {
        assertEq("hint wins", 6, ImageImport.suggestFrameCount(341, 6));
        // 64: largest n in [2,16] with cell >= 8 is 8
        assertEq("exact divisor", 8, ImageImport.suggestFrameCount(64, 0));
        // 341 = 11×31 → n=11, cell 31
        assertEq("odd width divisor", 11, ImageImport.suggestFrameCount(341, 0));
        assertEq("prime fallback", 2, ImageImport.suggestFrameCount(17, 0));
    }

    private static void testEqualBordersMatchesSplitSheet() throws IOException {
        BufferedImage sheet = new BufferedImage(341, 64, BufferedImage.TYPE_INT_ARGB);
        fillRect(sheet, 0, 0, 341, 64, 0xff112233);
        ImageImport.FrameBorders eq = ImageImport.equalBorders(341, 6);
        eq.trimMargins = false;
        assertEq("count", 6, eq.frameCount());
        assertEq("cell0", 56, eq.ends[0] - eq.starts[0]);
        assertEq("start1", 56, eq.starts[1]);
        assertEq("unused R", 341 - 6 * 56, ImageImport.bordersUnusedRight(341, eq));

        List<BufferedImage> fromBorders = ImageImport.extractFrames(sheet, eq);
        List<BufferedImage> fromSplit = ImageImport.splitSheet(sheet, 6);
        assertEq("same count", fromSplit.size(), fromBorders.size());
        for (int i = 0; i < fromSplit.size(); i++) {
            assertEq("w" + i, fromSplit.get(i).getWidth(), fromBorders.get(i).getWidth());
            assertEq("h" + i, fromSplit.get(i).getHeight(), fromBorders.get(i).getHeight());
        }
    }

    private static void fillRect(BufferedImage img, int x, int y, int w, int h, int argb) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                img.setRGB(xx, yy, argb);
            }
        }
    }

    private static void testFrameExportFileName() {
        assertEq("two digits", "walk_left_01.png",
                ImageImport.frameExportFileName("walk_left", 0, 8));
        assertEq("last of eight", "walk_left_08.png",
                ImageImport.frameExportFileName("walk_left", 7, 8));
        assertEq("three digits at 100", "hop_right_001.png",
                ImageImport.frameExportFileName("hop_right", 0, 100));
        assertEq("sanitize slash", "a_b", ImageImport.sanitizeExportPrefix("a/b"));
        assertEq("sanitize colon", "c_d", ImageImport.sanitizeExportPrefix("c:d"));
    }

    private static void testWriteFramePngs() throws Exception {
        File dir = Files.createTempDirectory("pp-export-frames-").toFile();
        try {
            BufferedImage a = solid(6, 4, 0xffff0000);
            BufferedImage b = solid(6, 4, 0xff0000ff);
            List<File> written = ImageImport.writeFramePngs(Arrays.asList(a, b), dir, "stand_left");
            assertEq("wrote 2", 2, written.size());
            assertEq("name 0", "stand_left_01.png", written.get(0).getName());
            assertEq("name 1", "stand_left_02.png", written.get(1).getName());
            BufferedImage loaded0 = ImageIO.read(written.get(0));
            BufferedImage loaded1 = ImageIO.read(written.get(1));
            assertEq("w0", 6, loaded0.getWidth());
            assertEq("h0", 4, loaded0.getHeight());
            assertEq("px0", 0xffff0000, loaded0.getRGB(3, 2));
            assertEq("px1", 0xff0000ff, loaded1.getRGB(3, 2));

            List<File> planned = ImageImport.frameExportFiles(dir, "stand_left", 2);
            assertEq("planned exists", true, planned.get(0).exists());
        } finally {
            deleteTree(dir);
        }
    }

    private static void testGifLoadHalfScale() throws Exception {
        File gif = Files.createTempFile("pp-gif-half-", ".gif").toFile();
        try {
            ImageIO.write(solid(8, 6, 0xff00ff00), "gif", gif);
            ImageImport.PackOptions opts = new ImageImport.PackOptions();
            opts.scaleDivisor = ImageImport.SCALE_DIVISOR_HALF;
            ImageImport imported = ImageImport.load(gif, opts);
            assertEq("cellW half", 4, imported.cellWidth);
            assertEq("cellH half", 3, imported.cellHeight);
        } finally {
            gif.delete();
        }
    }

    /**
     * Desktop Ponies import reuses one {@link ImageImport.PackOptions} for every
     * GIF. Loading must not leave the previous GIF's delays on that instance,
     * or a later GIF with a different frame count fails with a timing mismatch
     * (Applejack: apple_drop then tree/hurdle/sparkle).
     */
    private static void testGifLoadSharedOptionsDoesNotLeakTimings() throws Exception {
        File multi = findApplejackGif("apple_drop.gif");
        File single = findApplejackGif("hurdle_left.gif");
        if (multi == null || single == null) {
            System.out.println("skip gifLoadSharedOptionsDoesNotLeakTimings (Applejack GIFs not found)");
            return;
        }
        ImageImport.PackOptions shared = new ImageImport.PackOptions();
        shared.scaleDivisor = ImageImport.SCALE_DIVISOR_HALF;
        ImageImport first = ImageImport.load(multi, shared);
        ImageImport second = ImageImport.load(single, shared);
        int firstFrames = ImageImport.countTimings(first.timings);
        int secondFrames = ImageImport.countTimings(second.timings);
        if (firstFrames <= 1) {
            throw new AssertionError("expected multi-frame apple_drop, got " + firstFrames);
        }
        assertEq("hurdle frames", 1, secondFrames);
        if (shared.timingsCs != null) {
            throw new AssertionError("shared PackOptions.timingsCs should stay null");
        }
    }

    private static File findApplejackGif(String name) {
        File[] roots = {
                new File("../Desktop-Ponies/Content/Ponies/Applejack"),
                new File("Desktop-Ponies/Content/Ponies/Applejack"),
                new File("/home/derram/code/Desktop-Ponies/Content/Ponies/Applejack"),
        };
        for (File root : roots) {
            File gif = new File(root, name);
            if (gif.isFile()) {
                return gif;
            }
        }
        return null;
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
