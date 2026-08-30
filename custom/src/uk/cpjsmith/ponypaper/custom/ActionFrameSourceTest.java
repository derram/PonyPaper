package uk.cpjsmith.ponypaper.custom;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

/**
 * Checks that late cells of a wide spritesheet stay drawable on-screen.
 * Java2D's VolatileImage scaled {@code drawImage} silently drops blits once
 * source X is past ~11k; {@link ActionFrameSource#frameImage} isolates cells so
 * transition preview (and anything else using it) keeps painting.
 * Run via {@code ./gradlew :custom:testActionFrames} or {@code java … ActionFrameSourceTest}.
 */
public final class ActionFrameSourceTest {

    private ActionFrameSourceTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("lateFramePixelsMatchSheet", ActionFrameSourceTest::testLateFramePixelsMatchSheet);
        failures += run("isolatedLateFrameDrawsOnVolatileImage",
                ActionFrameSourceTest::testIsolatedLateFrameDrawsOnVolatileImage);
        if (failures > 0) {
            System.err.println(failures + " action-frame check(s) failed.");
            System.exit(1);
        }
        System.out.println("ActionFrameSource checks passed.");
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

    /** Wide enough that a late cell sits past the VolatileImage src-X cliff. */
    private static final int FRAME_W = 300;
    private static final int FRAME_H = 40;
    private static final int FRAME_COUNT = 48; // sheet width 14400, last srcX = 14100
    private static final int LATE_FRAME = 40; // srcX = 12000
    private static final int EARLY_RGB = 0xFF1122AA;
    private static final int LATE_RGB = 0xFF22CC44;

    private static void testLateFramePixelsMatchSheet() {
        BufferedImage sheet = buildWideSheet();
        ActionFrameSource src = sourceFromSheet(sheet);
        BufferedImage cell = src.frameImage(LATE_FRAME);
        assertEq("cellW", FRAME_W, cell.getWidth());
        assertEq("cellH", FRAME_H, cell.getHeight());
        assertEq("late centre", LATE_RGB, cell.getRGB(FRAME_W / 2, FRAME_H / 2));
        assertEq("not early colour", true, cell.getRGB(FRAME_W / 2, FRAME_H / 2) != EARLY_RGB);

        BufferedImage early = src.frameImage(0);
        assertEq("early centre", EARLY_RGB, early.getRGB(FRAME_W / 2, FRAME_H / 2));
        assertEq("sourceRect srcX", LATE_FRAME * FRAME_W, src.sourceRect(LATE_FRAME).x);
    }

    private static void testIsolatedLateFrameDrawsOnVolatileImage() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("  (skip volatile check: headless)");
            return;
        }
        BufferedImage sheet = buildWideSheet();
        ActionFrameSource src = sourceFromSheet(sheet);
        int srcX = LATE_FRAME * FRAME_W;
        int scale = 3;
        int dstW = FRAME_W * scale;
        int dstH = FRAME_H * scale;

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        VolatileImage vimg = gc.createCompatibleVolatileImage(
                dstW + 20, dstH + 20, Transparency.TRANSLUCENT);

        // Isolation keeps source coords small so the scaled on-screen blit works.
        BufferedImage cell = src.frameImage(LATE_FRAME);
        int isolatedOpaque = blitOpaque(vimg, cell, 0, 0, FRAME_W, FRAME_H, dstW, dstH);
        assertEq("isolated late blit visible", true, isolatedOpaque > 0);

        // When the platform still exhibits the wide-strip cliff, confirm we are
        // covering the real failure mode. Soft: other JVMs/GPUs may not vanish.
        int directOpaque = blitOpaque(vimg, sheet, srcX, 0, srcX + FRAME_W, FRAME_H, dstW, dstH);
        if (directOpaque == 0) {
            System.out.println("  (platform reproduces wide-strip VolatileImage vanish at srcX="
                    + srcX + ")");
        } else {
            System.out.println("  (platform draws wide-strip late cells directly; isolation still OK)");
        }
    }

    private static int blitOpaque(
            VolatileImage vimg,
            BufferedImage src,
            int sx0,
            int sy0,
            int sx1,
            int sy1,
            int dstW,
            int dstH) {
        do {
            Graphics2D g2 = vimg.createGraphics();
            try {
                g2.setBackground(new Color(0, 0, 0, 0));
                g2.clearRect(0, 0, vimg.getWidth(), vimg.getHeight());
                g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(src, 10, 10, 10 + dstW, 10 + dstH, sx0, sy0, sx1, sy1, null);
            } finally {
                g2.dispose();
            }
        } while (vimg.contentsLost());

        BufferedImage snap = vimg.getSnapshot();
        int opaque = 0;
        for (int y = 0; y < snap.getHeight(); y++) {
            for (int x = 0; x < snap.getWidth(); x++) {
                if ((snap.getRGB(x, y) >>> 24) != 0) {
                    opaque++;
                }
            }
        }
        return opaque;
    }

    private static BufferedImage buildWideSheet() {
        // TYPE_4BYTE_ABGR matches ImageIO PNG sheets. VolatileImage scaled blits
        // from that layout are what vanish past ~11k source X; INT_ARGB often does not.
        BufferedImage sheet = new BufferedImage(
                FRAME_W * FRAME_COUNT, FRAME_H, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = sheet.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(EARLY_RGB, true));
            g.fillRect(0, 0, FRAME_W, FRAME_H);
            g.setColor(new Color(LATE_RGB, true));
            g.fillRect(LATE_FRAME * FRAME_W, 0, FRAME_W, FRAME_H);
        } finally {
            g.dispose();
        }
        return sheet;
    }

    private static ActionFrameSource sourceFromSheet(BufferedImage sheet) {
        int[] times = new int[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            times[i] = 10;
        }
        return ActionFrameSource.fromImage(sheet, times, Float.NaN, Float.NaN);
    }

    private static void assertEq(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEq(String label, boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
