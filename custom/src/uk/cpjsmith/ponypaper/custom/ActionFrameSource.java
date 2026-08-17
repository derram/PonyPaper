package uk.cpjsmith.ponypaper.custom;

import java.awt.Image;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import uk.cpjsmith.ponypaper.PonyDefinition;

/**
 * Decoded spritesheet + timings + resolved feet anchors for one action and
 * facing, ready for feet-aligned drawing in the desktop editor (mirrors
 * {@code PonyAction#getDrawBounds} / {@code SpriteSheet#getRect}).
 */
public final class ActionFrameSource {

    public final String actionName;
    public final int actionIndex;
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
    /** Explicit {@code <anchorx>} for this facing, or {@link Float#NaN} when unset. */
    public final float explicitAnchorX;
    /** Explicit {@code <anchory>} for this facing, or {@link Float#NaN} when unset. */
    public final float explicitAnchorY;
    public final boolean loops;
    public final float speed;
    public final String specialType;

    private ActionFrameSource(
            String actionName,
            int actionIndex,
            String direction,
            Image image,
            int frameCount,
            int[] frameTimesCs,
            float explicitAnchorX,
            float explicitAnchorY,
            boolean loops,
            float speed,
            String specialType) {
        this.actionName = actionName;
        this.actionIndex = actionIndex;
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
        this.explicitAnchorX = explicitAnchorX;
        this.explicitAnchorY = explicitAnchorY;
        this.loops = loops;
        this.speed = speed > 0f ? speed : 1f;
        this.specialType = specialType != null ? specialType : "";
    }

    public float getDefaultAnchorX() {
        return frameWidth / 2f;
    }

    public float getDefaultAnchorY() {
        return frameHeight;
    }

    /** Resolved feet X (explicit or frame centre). */
    public float getResolvedAnchorX() {
        if (!Float.isNaN(explicitAnchorX) && explicitAnchorX >= 0f) {
            return explicitAnchorX;
        }
        return getDefaultAnchorX();
    }

    /** Resolved feet Y (explicit or frame bottom). */
    public float getResolvedAnchorY() {
        if (!Float.isNaN(explicitAnchorY) && explicitAnchorY >= 0f) {
            return explicitAnchorY;
        }
        return getDefaultAnchorY();
    }

    public boolean usesDefaultAnchorX() {
        return Float.isNaN(explicitAnchorX) || explicitAnchorX < 0f;
    }

    public boolean usesDefaultAnchorY() {
        return Float.isNaN(explicitAnchorY) || explicitAnchorY < 0f;
    }

    /**
     * Frame index for animation clock {@code timeCs} in {@code [0, totalTimeCs)}.
     * Matches wallpaper {@code SpriteSheet#getRect} walk.
     */
    public int frameIndexAt(int timeCs) {
        if (frameCount <= 1) {
            return 0;
        }
        int t = timeCs % totalTimeCs;
        if (t < 0) {
            t += totalTimeCs;
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

    /** Last frame index (end of one play). */
    public int lastFrameIndex() {
        return Math.max(0, frameCount - 1);
    }

    /**
     * Source rectangle of frame {@code frameIndex} within the spritesheet image.
     */
    public Rectangle sourceRect(int frameIndex) {
        int fi = Math.max(0, Math.min(frameCount - 1, frameIndex));
        int srcX = fi * frameWidth;
        int srcW = Math.min(frameWidth, imageWidth - srcX);
        if (srcW <= 0) {
            srcW = frameWidth;
        }
        return new Rectangle(srcX, 0, srcW, frameHeight);
    }

    /**
     * Destination rectangle with feet at {@code (feetX, feetY)} and the given
     * scale — same placement model as {@code PonyAction#getDrawBounds}.
     */
    public Rectangle destinationRect(float feetX, float feetY, float scale) {
        float dW = frameWidth * scale;
        float dH = frameHeight * scale;
        float ax = getResolvedAnchorX() * scale;
        float ay = getResolvedAnchorY() * scale;
        int x = Math.round(feetX - ax);
        int y = Math.round(feetY - ay);
        return new Rectangle(x, y, Math.round(dW), Math.round(dH));
    }

    /**
     * Loads a frame source for {@code actionIndex} facing {@code direction}
     * ({@code "left"} or {@code "right"}). Uses owner sprites when the action
     * is a {@code spritesfrom} alias. Anchors come from the action for this facing.
     *
     * @return loaded source, or {@code null} if no image is available
     * @throws IOException if the image bytes cannot be decoded
     */
    public static ActionFrameSource load(PonyEditor editor, int actionIndex, String direction)
            throws IOException {
        if (editor == null) {
            throw new IllegalArgumentException("editor");
        }
        if (actionIndex < 0 || actionIndex >= editor.getActionCount()) {
            throw new IndexOutOfBoundsException("actionIndex");
        }
        if (!"left".equals(direction) && !"right".equals(direction)) {
            throw new IllegalArgumentException("direction must be left or right");
        }

        String b64 = editor.getActionImage(actionIndex, direction);
        if (b64 == null || b64.isEmpty()) {
            return null;
        }

        byte[] raw = Base64.getDecoder().decode(b64);
        Image image = ImageIO.read(new ByteArrayInputStream(raw));
        if (image == null) {
            throw new IOException("Could not decode spritesheet for "
                    + editor.getActionName(actionIndex) + " (" + direction + ")");
        }

        String timings = editor.getActionTimings(actionIndex, direction);
        int[] frameTimes = parseTimings(timings);
        int frameCount = frameTimes.length;

        return new ActionFrameSource(
                editor.getActionName(actionIndex),
                actionIndex,
                direction,
                image,
                frameCount,
                frameTimes,
                editor.getActionAnchorX(actionIndex, direction),
                editor.getActionAnchorY(actionIndex, direction),
                editor.getActionLoops(actionIndex),
                editor.getActionSpeed(actionIndex),
                editor.getActionSpecial(actionIndex));
    }

    /**
     * True when the action has a non-empty spritesheet for {@code direction}
     * (aliases resolve to the owner).
     */
    public static boolean hasImage(PonyEditor editor, int actionIndex, String direction) {
        try {
            String b64 = editor.getActionImage(actionIndex, direction);
            return b64 != null && !b64.isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static int[] parseTimings(String timings) {
        if (timings == null || timings.trim().isEmpty()) {
            return new int[] { 100 };
        }
        String[] parts = timings.split(",");
        int count = 0;
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                count++;
            }
        }
        if (count < 1) {
            return new int[] { 100 };
        }
        int[] times = new int[count];
        int i = 0;
        for (String part : parts) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(t);
            } catch (NumberFormatException e) {
                value = 10;
            }
            times[i++] = Math.max(1, value);
        }
        return times;
    }

    /**
     * Unique real action names from a comma-separated next/start list
     * (drops empty and {@code none}/{@code -} tokens).
     */
    public static java.util.List<String> parseActionNames(String list) {
        return PonyDefinition.uniqueActionListNames(list);
    }
}
