package uk.cpjsmith.ponypaper.custom;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Decoded effect spritesheet + timings for one facing, ready for composite
 * placement preview. Same strip-isolation approach as {@link ActionFrameSource}
 * (wide sheets must not blit from parent source X past ~11k on VolatileImage).
 */
public final class EffectFrameSource {

    public final String effectName;
    public final int effectIndex;
    public final String direction;
    public final Image image;
    public final int imageWidth;
    public final int imageHeight;
    public final int frameCount;
    public final int frameWidth;
    public final int frameHeight;
    /** Frame durations in centiseconds (same unit as XML timings). */
    public final int[] frameTimesCs;
    public final int totalTimeCs;
    public final boolean noLoop;

    private BufferedImage[] isolatedFrames;

    private EffectFrameSource(
            String effectName,
            int effectIndex,
            String direction,
            Image image,
            int frameCount,
            int[] frameTimesCs,
            boolean noLoop) {
        this.effectName = effectName;
        this.effectIndex = effectIndex;
        this.direction = direction;
        this.image = image;
        this.imageWidth = image.getWidth(null);
        this.imageHeight = image.getHeight(null);
        this.frameCount = frameCount;
        this.frameWidth = Math.max(1, imageWidth / frameCount);
        this.frameHeight = imageHeight;
        this.frameTimesCs = frameTimesCs;
        int total = 0;
        for (int t : frameTimesCs) {
            total += Math.max(1, t);
        }
        this.totalTimeCs = Math.max(1, total);
        this.noLoop = noLoop;
    }

    /**
     * Frame index for animation clock {@code timeCs}. When {@link #noLoop} is
     * true and time is past the end, stays on the last frame.
     */
    public int frameIndexAt(float timeCs) {
        if (frameCount <= 1) {
            return 0;
        }
        int t;
        if (noLoop) {
            if (timeCs >= totalTimeCs) {
                return frameCount - 1;
            }
            t = Math.max(0, (int) timeCs);
        } else {
            t = (int) timeCs % totalTimeCs;
            if (t < 0) {
                t += totalTimeCs;
            }
        }
        for (int i = 0; i < frameTimesCs.length; i++) {
            int dur = Math.max(1, frameTimesCs[i]);
            if (t < dur) {
                return Math.min(i, frameCount - 1);
            }
            t -= dur;
        }
        return frameCount - 1;
    }

    public Rectangle sourceRect(int frameIndex) {
        int fi = Math.max(0, Math.min(frameCount - 1, frameIndex));
        int srcX = fi * frameWidth;
        int srcW = Math.min(frameWidth, imageWidth - srcX);
        if (srcW <= 0) {
            srcW = frameWidth;
        }
        return new Rectangle(srcX, 0, srcW, frameHeight);
    }

    /** Isolated cell raster for safe on-screen scaled blits. */
    public BufferedImage frameImage(int frameIndex) {
        int fi = Math.max(0, Math.min(frameCount - 1, frameIndex));
        if (isolatedFrames == null) {
            isolatedFrames = new BufferedImage[frameCount];
        }
        if (isolatedFrames[fi] != null) {
            return isolatedFrames[fi];
        }
        Rectangle srcR = sourceRect(fi);
        BufferedImage copy = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
        if (srcR.width > 0 && frameHeight > 0) {
            Graphics2D g = copy.createGraphics();
            try {
                g.setComposite(AlphaComposite.Src);
                g.drawImage(
                        image,
                        0,
                        0,
                        srcR.width,
                        frameHeight,
                        srcR.x,
                        0,
                        srcR.x + srcR.width,
                        frameHeight,
                        null);
            } finally {
                g.dispose();
            }
        }
        isolatedFrames[fi] = copy;
        return copy;
    }

    /**
     * @return loaded source, or {@code null} if no image is available
     * @throws IOException if the image bytes cannot be decoded
     */
    public static EffectFrameSource load(PonyEditor editor, int effectIndex, String direction)
            throws IOException {
        if (editor == null) {
            throw new IllegalArgumentException("editor");
        }
        if (effectIndex < 0 || effectIndex >= editor.getEffectCount()) {
            throw new IndexOutOfBoundsException("effectIndex");
        }
        if (!"left".equals(direction) && !"right".equals(direction)) {
            throw new IllegalArgumentException("direction must be left or right");
        }

        String b64 = editor.getEffectImage(effectIndex, direction);
        if (b64 == null || b64.isEmpty()) {
            return null;
        }

        byte[] raw = Base64.getDecoder().decode(b64);
        Image image = ImageIO.read(new ByteArrayInputStream(raw));
        if (image == null) {
            throw new IOException("Could not decode effect spritesheet for "
                    + editor.getEffectName(effectIndex) + " (" + direction + ")");
        }

        int[] frameTimes = ActionFrameSource.parseTimings(
                editor.getEffectTimings(effectIndex, direction));
        return new EffectFrameSource(
                editor.getEffectName(effectIndex),
                effectIndex,
                direction,
                image,
                frameTimes.length,
                frameTimes,
                editor.getEffectNoLoop(effectIndex));
    }

    public static boolean hasImage(PonyEditor editor, int effectIndex, String direction) {
        try {
            String b64 = editor.getEffectImage(effectIndex, direction);
            return b64 != null && !b64.isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
