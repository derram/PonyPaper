package uk.cpjsmith.ponypaper.custom;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Checks that the anchor-picker place view keeps a single frame on screen when
 * zoomed dest sizes would overflow Java2D's scaled {@code drawImage}.
 * Run via {@code ./gradlew :custom:testSpritePreview} or {@code java … SpriteSheetPreviewTest}.
 */
public final class SpriteSheetPreviewTest {

    private SpriteSheetPreviewTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("sourceSpanIntegerZoom", SpriteSheetPreviewTest::testSourceSpanIntegerZoom);
        failures += run("sourceSpanClipAtOrigin", SpriteSheetPreviewTest::testSourceSpanClipAtOrigin);
        failures += run("sourceSpanScrolledClip", SpriteSheetPreviewTest::testSourceSpanScrolledClip);
        failures += run("mulSizeClamps", SpriteSheetPreviewTest::testMulSizeClamps);
        failures += run("placeModeLargeZoomKeepsSelectedFrame",
                SpriteSheetPreviewTest::testPlaceModeLargeZoomKeepsSelectedFrame);
        failures += run("placeModeLargeZoomScrolledNoNeighborLeak",
                SpriteSheetPreviewTest::testPlaceModeLargeZoomScrolledNoNeighborLeak);
        if (failures > 0) {
            System.err.println(failures + " sprite-preview check(s) failed.");
            System.exit(1);
        }
        System.out.println("SpriteSheetPreview checks passed.");
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

    private static void testSourceSpanIntegerZoom() {
        Rectangle bounds = new Rectangle(0, 0, 320, 80);
        Rectangle clip = new Rectangle(0, 0, 80, 80);
        int[] span = SpriteSheetPreview.visibleSourceSpan(bounds, clip, 80, 20);
        assertEq("span present", true, span != null);
        assertEq("sx0", 0, span[0]);
        assertEq("sy0", 0, span[1]);
        assertEq("sx1", 20, span[2]);
        assertEq("sy1", 20, span[3]);
    }

    private static void testSourceSpanClipAtOrigin() {
        // 2048px frame @ 16x → dest 32768, classic Java2D overflow size.
        Rectangle bounds = new Rectangle(0, 0, 32768, 512);
        Rectangle clip = new Rectangle(0, 0, 400, 64);
        int[] span = SpriteSheetPreview.visibleSourceSpan(bounds, clip, 2048, 32);
        assertEq("span present", true, span != null);
        assertEq("sx0", 0, span[0]);
        assertEq("sx1 covers clip", true, span[2] >= 400 / 16);
        assertEq("sx1 not whole frame", true, span[2] < 2048);
    }

    private static void testSourceSpanScrolledClip() {
        Rectangle bounds = new Rectangle(0, 0, 32768, 512);
        Rectangle clip = new Rectangle(16000, 0, 400, 64);
        int[] span = SpriteSheetPreview.visibleSourceSpan(bounds, clip, 2048, 32);
        assertEq("span present", true, span != null);
        assertEq("scrolled sx0", 1000, span[0]);
        assertEq("not starting at 0", true, span[0] > 0);
        assertEq("not past end", true, span[2] <= 2048);
    }

    private static void testMulSizeClamps() {
        assertEq("normal", 32768, SpriteSheetPreview.mulSize(2048, 16));
        assertEq("no overflow wrap", Integer.MAX_VALUE, SpriteSheetPreview.mulSize(100_000, 100_000));
    }

    private static void testPlaceModeLargeZoomKeepsSelectedFrame() {
        assertNoNeighborLeak(0, 16);
    }

    private static void testPlaceModeLargeZoomScrolledNoNeighborLeak() {
        // Mid-frame: dest x ≈ 16000 at 16x on a 2048px cell.
        assertNoNeighborLeak(16000, 16);
    }

    /**
     * Three wide cells in distinct colours. Place-mode at high zoom used to
     * vanish, then show the other cells as dest size crossed Java2D's limit.
     */
    private static void assertNoNeighborLeak(int clipX, int zoom) {
        final int frameW = 2048;
        final int frameH = 32;
        final int frames = 3;
        final int selected = 1;
        final int leftRgb = 0xFF1122AA;
        final int midRgb = 0xFF22CC44;
        final int rightRgb = 0xFFDD8811;

        BufferedImage sheet = new BufferedImage(frameW * frames, frameH, BufferedImage.TYPE_INT_ARGB);
        fillRect(sheet, 0, 0, frameW, frameH, leftRgb);
        fillRect(sheet, frameW, 0, frameW, frameH, midRgb);
        fillRect(sheet, frameW * 2, 0, frameW, frameH, rightRgb);

        SpriteSheetPreview preview = new SpriteSheetPreview(sheet, frames, SpriteSheetPreview.Mode.PLACE_ANCHOR);
        preview.setSelectedFrame(selected);
        preview.setPlaceZoom(zoom);
        preview.setShowGrid(false);
        preview.setAnchors(0f, 0f);

        int destW = frameW * zoom;
        int destH = frameH * zoom;
        preview.setSize(destW, destH);

        int viewW = 400;
        int viewH = 64;
        BufferedImage out = new BufferedImage(viewW, viewH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.translate(-clipX, 0);
            g.setClip(clipX, 0, viewW, viewH);
            preview.paint(g);
        } finally {
            g.dispose();
        }

        int[] counts = countExact(out, leftRgb, midRgb, rightRgb);
        if (counts[0] > 0 || counts[2] > 0) {
            throw new AssertionError(
                    "neighbour frames leaked into place view at clipX=" + clipX
                            + " zoom=" + zoom + " counts=" + Arrays.toString(counts));
        }
        if (counts[1] < viewW * 8) {
            throw new AssertionError(
                    "selected frame missing or vanished at clipX=" + clipX
                            + " zoom=" + zoom + " midPixels=" + counts[1]
                            + " (sample ARGB=" + Integer.toHexString(out.getRGB(8, 8)) + ")");
        }
    }

    private static void fillRect(BufferedImage img, int x, int y, int w, int h, int argb) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                img.setRGB(xx, yy, argb);
            }
        }
    }

    private static int[] countExact(BufferedImage img, int... rgb) {
        int[] counts = new int[rgb.length];
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(x, y);
                for (int i = 0; i < rgb.length; i++) {
                    if (p == rgb[i]) {
                        counts[i]++;
                    }
                }
            }
        }
        return counts;
    }

    private static void assertEq(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEq(String label, boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
