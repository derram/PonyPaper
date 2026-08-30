package uk.cpjsmith.ponypaper.custom;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Loads sprite images for the custom pony editor.
 *
 * <p>Static PNG (or other) files are passed through as-is. Individual PNG
 * frames can be packed into that same strip via {@link #fromFrames} /
 * {@link #fromFrameFiles}: uniform cells, bottom-centre alignment, optional
 * per-frame lift (pixels of air under the sprite), no inter-frame padding.
 * Animated GIFs are decoded, fully coalesced (so each frame is a complete
 * image, not a dirty-rectangle delta), and packed left-to-right into a
 * single PNG spritesheet with matching frame timings. Scale is an exact
 * dyadic divisor ({@link #SCALE_DIVISOR_NATIVE}…{@link #SCALE_DIVISOR_SIXTEENTH}):
 * nearest-neighbour point samples on the even lattice ({@code src[x·D, y·D]}),
 * equivalent to successive top-left halvings, so ÷2 / ÷4 / ÷8 / ÷16 match
 * re-running the 50% option. {@link #SCALE_DIVISOR_HALF} is what the
 * Desktop Ponies folder importer uses so stock ponies match built-in sheet
 * size. Integer sampling avoids {@code Graphics2D} nearest-neighbour, which
 * picks the odd pixel of each 2×2 and can turn isolated encoder speckles
 * into opaque black dots after large shrinks. Divisors ≥4 also ignore an
 * opaque sample when its block has fewer than {@code divisor} opaque pixels
 * (encoder dirt; solid upscaled texels are far denser).
 *
 * <p>Desktop Ponies GIFs are often heavily optimised: partial frames, mixed
 * disposal methods, and occasionally a transparent colour index that lies
 * outside the colour table. Java's {@code ImageIO} GIF reader mishandles the
 * latter case by folding those indices into opaque background pixels, which
 * then show up as solid backgrounds on the spritesheet. This class uses its
 * own decoder so transparency and coalescing stay correct.
 */
public class ImageImport {

    /** Default per-frame duration when packing stills (hundredths of a second). */
    public static final int DEFAULT_FRAME_TIMING_CS = 10;

    /** Pack at the source pixel size (÷1). */
    public static final int SCALE_DIVISOR_NATIVE = 1;

    /**
     * Half linear size (÷2) — Desktop Ponies GIFs vs typical built-in
     * PonyPaper sheets. Successive integer halvings, same as the old silent
     * GIF shrink.
     */
    public static final int SCALE_DIVISOR_HALF = 2;

    /** Quarter linear size (÷4) — two successive 50% passes. */
    public static final int SCALE_DIVISOR_QUARTER = 4;

    /** Eighth linear size (÷8) — three successive 50% passes. */
    public static final int SCALE_DIVISOR_EIGHTH = 8;

    /** Sixteenth linear size (÷16) — four successive 50% passes. */
    public static final int SCALE_DIVISOR_SIXTEENTH = 16;

    /**
     * Allowed dyadic scale divisors, largest scale first (native → ÷16).
     */
    public static final int[] SCALE_DIVISORS = {
        SCALE_DIVISOR_NATIVE,
        SCALE_DIVISOR_HALF,
        SCALE_DIVISOR_QUARTER,
        SCALE_DIVISOR_EIGHTH,
        SCALE_DIVISOR_SIXTEENTH,
    };

    /**
     * Percent label for native size. Prefer {@link #SCALE_DIVISOR_NATIVE} in
     * new code; kept for call sites and docs that speak in percents.
     */
    public static final int SCALE_NATIVE = 100;

    /**
     * Percent label for half size / Desktop Ponies. Prefer
     * {@link #SCALE_DIVISOR_HALF}.
     */
    public static final int SCALE_DESKTOP_PONIES = 50;

    /**
     * Built-in sheets top out around this height. Taller packed cells draw
     * larger than a stock pony on the wallpaper. Also the target for
     * {@link #fitBuiltinScaleDivisor(int)}.
     */
    public static final int LARGE_CELL_HEIGHT_PX = 80;

    /**
     * Soft cap for allocating a live packer strip preview (width×height).
     * Above this the packer dialog defers the strip and asks before Pack.
     * ~8 Mpx ≈ 32 MB as ARGB.
     */
    public static final int SHEET_PIXEL_BUDGET = 8_000_000;

    public final byte[] loadedImage;
    public final String timings;
    /**
     * Packed cell width in pixels, or {@code 0} when unknown (raw file
     * pass-through).
     */
    public final int cellWidth;
    /**
     * Packed cell height in pixels, or {@code 0} when unknown (raw file
     * pass-through).
     */
    public final int cellHeight;

    /**
     * Options for {@link #fromFrames} / {@link #fromFrameFiles}.
     */
    public static final class PackOptions {
        /** Per-frame duration in hundredths of a second (minimum 1). */
        public int defaultTimingCs = DEFAULT_FRAME_TIMING_CS;
        /**
         * When true, refuse frames that are not all the same size instead of
         * padding to the max canvas.
         */
        public boolean rejectMixedSizes = false;
        /**
         * Pixels up from the shared bottom baseline for each frame ({@code 0}
         * = current bottom-align). {@code null} means all zeros. When
         * non-null, length must equal the frame count; values must be
         * {@code >= 0}. Cell height becomes {@code max(frameH + lift)}.
         */
        public int[] lifts;
        /**
         * Dyadic linear shrink applied before packing:
         * {@link #SCALE_DIVISOR_NATIVE} (default), {@link #SCALE_DIVISOR_HALF},
         * {@link #SCALE_DIVISOR_QUARTER}, {@link #SCALE_DIVISOR_EIGHTH}, or
         * {@link #SCALE_DIVISOR_SIXTEENTH}. Ignored when
         * {@link #scaleFitBuiltin} is true.
         */
        public int scaleDivisor = SCALE_DIVISOR_NATIVE;
        /**
         * When true, pick the largest dyadic scale whose max frame height is
         * ≤ {@link #LARGE_CELL_HEIGHT_PX} (see {@link #fitBuiltinScaleDivisor}).
         */
        public boolean scaleFitBuiltin = false;
        /**
         * Optional per-frame timings in hundredths of a second. When
         * {@code null}, every frame uses {@link #defaultTimingCs}. Length
         * must match the frame count; each value is clamped to {@code >= 1}.
         */
        public int[] timingsCs;
    }

    /**
     * Coalesced GIF frames at the file's logical size (no scale, not packed).
     */
    public static final class GifFrames {
        public final List<BufferedImage> frames;
        /** Per-frame delay in hundredths of a second. */
        public final int[] timingsCs;
        public final int logicalWidth;
        public final int logicalHeight;

        GifFrames(List<BufferedImage> frames, int[] timingsCs,
                int logicalWidth, int logicalHeight) {
            this.frames = frames;
            this.timingsCs = timingsCs;
            this.logicalWidth = logicalWidth;
            this.logicalHeight = logicalHeight;
        }
    }

    /**
     * Cell size and mixed-size flag for a list of already-decoded frames.
     */
    public static final class PackPreview {
        public final int frameCount;
        public final int cellWidth;
        public final int cellHeight;
        public final boolean mixedSizes;

        PackPreview(int frameCount, int cellWidth, int cellHeight, boolean mixedSizes) {
            this.frameCount = frameCount;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.mixedSizes = mixedSizes;
        }

        /** {@code frameCount × cellWidth}. */
        public int sheetWidth() {
            return frameCount * cellWidth;
        }
    }

    private ImageImport(byte[] loadedImage, String timings) {
        this(loadedImage, timings, 0, 0);
    }

    private ImageImport(byte[] loadedImage, String timings, int cellWidth, int cellHeight) {
        this.loadedImage = loadedImage;
        this.timings = timings;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
    }

    public static ImageImport load(File file) throws IOException {
        return load(file, null);
    }

    /**
     * Loads a PNG (or other still) as raw bytes, or a GIF as a packed
     * spritesheet. GIF scale / lifts / override timings come from
     * {@code options}; {@code null} means native size and the GIF's own delays.
     */
    public static ImageImport load(File file, PackOptions options) throws IOException {
        String filename = file.getName().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".gif")) {
            GifFrames gif = loadGifFrames(file);
            PackOptions opts = options != null ? options : new PackOptions();
            if (opts.timingsCs == null) {
                opts.timingsCs = gif.timingsCs;
            }
            return fromFrames(gif.frames, opts);
        }
        return new ImageImport(Files.readAllBytes(file.toPath()), null);
    }

    /**
     * Coalesced GIF frames at logical size. Does not scale or pack.
     */
    public static GifFrames loadGifFrames(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        GifAnimation animation = GifAnimation.decode(data);
        int frameCount = animation.frames.size();
        if (frameCount == 0) {
            throw new IOException("GIF has no frames: " + file);
        }
        List<BufferedImage> frames = new ArrayList<BufferedImage>(frameCount);
        int[] timingsCs = new int[frameCount];
        for (int i = 0; i < frameCount; i++) {
            GifFrame frame = animation.frames.get(i);
            frames.add(frame.argb);
            timingsCs[i] = frame.delayCs;
        }
        return new GifFrames(frames, timingsCs, animation.logicalWidth, animation.logicalHeight);
    }

    public static int normalizeScaleDivisor(int scaleDivisor) throws IOException {
        for (int allowed : SCALE_DIVISORS) {
            if (scaleDivisor == allowed) {
                return scaleDivisor;
            }
        }
        throw new IOException(
                "Scale divisor must be 1, 2, 4, 8, or 16 (got " + scaleDivisor + ").");
    }

    /**
     * Maps legacy percent labels ({@link #SCALE_NATIVE}, {@link #SCALE_DESKTOP_PONIES},
     * 25) onto a dyadic divisor. Prefer passing divisors directly.
     */
    public static int scaleDivisorFromPercent(int scalePercent) throws IOException {
        if (scalePercent == SCALE_NATIVE || scalePercent == 100) {
            return SCALE_DIVISOR_NATIVE;
        }
        if (scalePercent == SCALE_DESKTOP_PONIES || scalePercent == 50) {
            return SCALE_DIVISOR_HALF;
        }
        if (scalePercent == 25) {
            return SCALE_DIVISOR_QUARTER;
        }
        throw new IOException(
                "Scale percent must be 100, 50, or 25 (got " + scalePercent
                        + "). Use divisor 8 or 16 for 12.5% / 6.25%.");
    }

    /**
     * Number of successive 50% passes for a normalised divisor (0…4).
     */
    public static int scaleHalveCount(int scaleDivisor) throws IOException {
        int divisor = normalizeScaleDivisor(scaleDivisor);
        return Integer.numberOfTrailingZeros(divisor);
    }

    /**
     * One dimension after {@code scaleDivisor} successive integer halvings.
     */
    public static int scaleDimension(int size, int scaleDivisor) throws IOException {
        int halvings = scaleHalveCount(scaleDivisor);
        int value = Math.max(0, size);
        for (int i = 0; i < halvings; i++) {
            value = Math.max(1, value / 2);
        }
        return value == 0 ? 1 : value;
    }

    /**
     * Largest dyadic scale (smallest divisor) whose height is
     * ≤ {@link #LARGE_CELL_HEIGHT_PX}. Falls back to ÷16 when even that is
     * taller.
     */
    public static int fitBuiltinScaleDivisor(int sourceMaxHeight) throws IOException {
        int height = Math.max(1, sourceMaxHeight);
        for (int divisor : SCALE_DIVISORS) {
            if (scaleDimension(height, divisor) <= LARGE_CELL_HEIGHT_PX) {
                return divisor;
            }
        }
        return SCALE_DIVISOR_SIXTEENTH;
    }

    public static int maxFrameHeight(List<BufferedImage> frames) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IOException("No frames.");
        }
        int max = 0;
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage frame = frames.get(i);
            if (frame == null) {
                throw new IOException("Null frame");
            }
            max = Math.max(max, frame.getHeight());
        }
        return Math.max(1, max);
    }

    public static int maxFrameWidth(List<BufferedImage> frames) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IOException("No frames.");
        }
        int max = 0;
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage frame = frames.get(i);
            if (frame == null) {
                throw new IOException("Null frame");
            }
            max = Math.max(max, frame.getWidth());
        }
        return Math.max(1, max);
    }

    /**
     * Resolves {@link PackOptions#scaleFitBuiltin} or
     * {@link PackOptions#scaleDivisor} against the source frames.
     */
    public static int resolveScaleDivisor(PackOptions options, List<BufferedImage> frames)
            throws IOException {
        PackOptions opts = options != null ? options : new PackOptions();
        if (opts.scaleFitBuiltin) {
            return fitBuiltinScaleDivisor(maxFrameHeight(frames));
        }
        return normalizeScaleDivisor(opts.scaleDivisor);
    }

    /** Human-readable scale, e.g. {@code 100%}, {@code 12.5%}. */
    public static String formatScaleDivisor(int scaleDivisor) throws IOException {
        int divisor = normalizeScaleDivisor(scaleDivisor);
        switch (divisor) {
            case SCALE_DIVISOR_NATIVE:
                return "100%";
            case SCALE_DIVISOR_HALF:
                return "50%";
            case SCALE_DIVISOR_QUARTER:
                return "25%";
            case SCALE_DIVISOR_EIGHTH:
                return "12.5%";
            case SCALE_DIVISOR_SIXTEENTH:
                return "6.25%";
            default:
                return "÷" + divisor;
        }
    }

    /** Short label including the divisor, e.g. {@code 25% (÷4)}. */
    public static String formatScaleDivisorLabel(int scaleDivisor) throws IOException {
        int divisor = normalizeScaleDivisor(scaleDivisor);
        if (divisor == SCALE_DIVISOR_NATIVE) {
            return "100% (native)";
        }
        if (divisor == SCALE_DIVISOR_HALF) {
            return "50% (Desktop Ponies / ÷2)";
        }
        return formatScaleDivisor(divisor) + " (÷" + divisor + ")";
    }

    /**
     * Accepts {@code 100}, {@code 50%}, {@code 12.5}, {@code 1/8}, {@code half},
     * {@code quarter}, {@code eighth}, {@code 16}, {@code fit}, {@code native}.
     * Returns a scale divisor, or {@code -1} for {@code fit} (caller must
     * resolve against frame heights).
     */
    public static int parseScaleDivisor(String text) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            throw new IOException("Scale is empty.");
        }
        String t = text.trim();
        if ("fit".equalsIgnoreCase(t)
                || "builtin".equalsIgnoreCase(t)
                || "built-in".equalsIgnoreCase(t)
                || "auto".equalsIgnoreCase(t)) {
            return -1;
        }
        if ("half".equalsIgnoreCase(t)
                || "dp".equalsIgnoreCase(t)
                || "desktop".equalsIgnoreCase(t)) {
            return SCALE_DIVISOR_HALF;
        }
        if ("quarter".equalsIgnoreCase(t)) {
            return SCALE_DIVISOR_QUARTER;
        }
        if ("eighth".equalsIgnoreCase(t)) {
            return SCALE_DIVISOR_EIGHTH;
        }
        if ("sixteenth".equalsIgnoreCase(t)) {
            return SCALE_DIVISOR_SIXTEENTH;
        }
        if ("native".equalsIgnoreCase(t)
                || "full".equalsIgnoreCase(t)) {
            return SCALE_DIVISOR_NATIVE;
        }
        if (t.startsWith("1/") || t.startsWith("÷")) {
            String rest = t.startsWith("1/") ? t.substring(2).trim() : t.substring("÷".length()).trim();
            try {
                return normalizeScaleDivisor(Integer.parseInt(rest));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid scale: " + text);
            }
        }
        if (t.endsWith("%")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        if ("12.5".equals(t) || "12,5".equals(t)) {
            return SCALE_DIVISOR_EIGHTH;
        }
        if ("6.25".equals(t) || "6,25".equals(t)) {
            return SCALE_DIVISOR_SIXTEENTH;
        }
        int value;
        try {
            value = Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid scale: " + text);
        }
        // Bare 1/2/4/8/16 are divisors; 100/50/25 are percent labels.
        if (value == 100 || value == 50 || value == 25) {
            return scaleDivisorFromPercent(value);
        }
        return normalizeScaleDivisor(value);
    }

    /**
     * @deprecated use {@link #parseScaleDivisor(String)}; still returns a
     *             divisor (not a percent) for the values it accepts.
     */
    @Deprecated
    public static int parseScalePercent(String text) throws IOException {
        int parsed = parseScaleDivisor(text);
        if (parsed < 0) {
            throw new IOException("Scale 'fit' needs frame heights; use parseScaleDivisor.");
        }
        return parsed;
    }

    public static boolean isLargeCell(int cellHeight) {
        return cellHeight > LARGE_CELL_HEIGHT_PX;
    }

    public static String largeCellWarning() {
        return "This will draw larger than a built-in pony.";
    }

    /** {@code (long) width * height}, clamped to non-negative dimensions. */
    public static long sheetPixelCount(int width, int height) {
        long w = Math.max(0, width);
        long h = Math.max(0, height);
        return w * h;
    }

    /**
     * True when allocating a full ARGB strip of this size is likely to stall
     * or OOM the packer UI (see {@link #SHEET_PIXEL_BUDGET}).
     */
    public static boolean exceedsSheetPixelBudget(int width, int height) {
        return sheetPixelCount(width, height) > SHEET_PIXEL_BUDGET;
    }

    /**
     * Rough ARGB byte size for a sheet ({@code width × height × 4}).
     */
    public static long sheetArgbBytes(int width, int height) {
        return sheetPixelCount(width, height) * 4L;
    }

    /**
     * Human-readable size for confirm dialogs, e.g. {@code 32 MB} or
     * {@code 512 KB}.
     */
    public static String formatByteSize(long bytes) {
        if (bytes < 0) {
            bytes = 0;
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Resolves a pack-dialog scale argument (dyadic divisor or legacy percent
     * 100/50/25) to a normalised divisor. {@code <= 0} means native.
     */
    public static int resolveRequestedScaleDivisor(int requested) throws IOException {
        if (requested <= 0 || requested == SCALE_NATIVE || requested == 100) {
            return SCALE_DIVISOR_NATIVE;
        }
        if (requested == SCALE_DESKTOP_PONIES || requested == 50) {
            return SCALE_DIVISOR_HALF;
        }
        if (requested == 25) {
            return SCALE_DIVISOR_QUARTER;
        }
        return normalizeScaleDivisor(requested);
    }

    /**
     * Default pack scale for the GUI: keep {@code requested} when the tallest
     * frame already fits under {@link #LARGE_CELL_HEIGHT_PX} at that scale;
     * otherwise {@link #fitBuiltinScaleDivisor(int)}.
     */
    public static int defaultScaleDivisorForFrames(List<BufferedImage> frames, int requested)
            throws IOException {
        int divisor = resolveRequestedScaleDivisor(requested);
        int maxH = maxFrameHeight(frames);
        if (scaleDimension(maxH, divisor) > LARGE_CELL_HEIGHT_PX) {
            return fitBuiltinScaleDivisor(maxH);
        }
        return divisor;
    }

    /**
     * True when {@link #defaultScaleDivisorForFrames} would pick Fit because
     * the requested scale leaves cells taller than built-in.
     */
    public static boolean shouldDefaultToFitBuiltin(List<BufferedImage> frames, int requested)
            throws IOException {
        int divisor = resolveRequestedScaleDivisor(requested);
        return scaleDimension(maxFrameHeight(frames), divisor) > LARGE_CELL_HEIGHT_PX;
    }

    /**
     * Short note for packer dialogs: Fit is auto-selected when frames are
     * taller than built-in; otherwise 100% is the default.
     */
    public static String packerScaleNotes() {
        return "Scale defaults to 100%, or Fit to built-in when frames are taller than "
                + LARGE_CELL_HEIGHT_PX + "px. Choose 50%/25%/12.5%/6.25% or Fit to shrink further.";
    }

    /**
     * Nearest-neighbour dyadic scale. {@link #SCALE_DIVISOR_NATIVE} returns
     * {@code frames} itself. Other divisors allocate new images by point-sampling
     * {@code src[x·divisor, y·divisor]} (even lattice / top-left of each block),
     * which matches successive integer halvings. For {@link #SCALE_DIVISOR_QUARTER}
     * and smaller scales, an opaque sample whose block has fewer than
     * {@code divisor} opaque pixels becomes transparent so sparse encoder
     * speckles do not survive large shrinks.
     */
    public static List<BufferedImage> scaleFrames(List<BufferedImage> frames, int scaleDivisor)
            throws IOException {
        int divisor = normalizeScaleDivisor(scaleDivisor);
        if (frames == null || frames.isEmpty()) {
            throw new IOException("No frames to scale.");
        }
        if (divisor == SCALE_DIVISOR_NATIVE) {
            return frames;
        }
        List<BufferedImage> out = new ArrayList<BufferedImage>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage frame = frames.get(i);
            if (frame == null) {
                throw new IOException("Null frame");
            }
            out.add(scaleImage(frame, divisor));
        }
        return out;
    }

    public static BufferedImage scaleImage(BufferedImage src, int scaleDivisor) throws IOException {
        int divisor = normalizeScaleDivisor(scaleDivisor);
        if (src == null) {
            throw new IOException("Null frame");
        }
        if (divisor == SCALE_DIVISOR_NATIVE) {
            return src;
        }
        // Even-lattice point sample. Equivalent to successive top-left halvings;
        // do not use Graphics2D drawImage NN — it samples the odd pixel of each
        // 2×2 and amplifies isolated opaque speckles after ÷4 / ÷8 / ÷16.
        // For ÷4 and larger, also drop samples whose D×D block has fewer than
        // D opaque pixels (GIF/encoder dirt). Clean N× upscales fill the whole
        // block; ÷2 keeps thin Desktop Ponies edge detail untouched.
        int sw = src.getWidth();
        int sh = src.getHeight();
        int w = Math.max(1, sw / divisor);
        int h = Math.max(1, sh / divisor);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] srcPixels = src.getRGB(0, 0, sw, sh, null, 0, sw);
        int[] dstPixels = new int[w * h];
        boolean dropSparse = divisor >= SCALE_DIVISOR_QUARTER;
        for (int y = 0; y < h; y++) {
            int srcY = y * divisor;
            int srcRow = srcY * sw;
            int dstRow = y * w;
            for (int x = 0; x < w; x++) {
                int srcX = x * divisor;
                int sample = srcPixels[srcRow + srcX];
                if (dropSparse
                        && ((sample >>> 24) & 0xff) >= 200
                        && !blockOpaqueEnough(srcPixels, sw, sh, srcX, srcY, divisor)) {
                    sample = 0x00000000;
                }
                dstPixels[dstRow + x] = sample;
            }
        }
        out.setRGB(0, 0, w, h, dstPixels, 0, w);
        return out;
    }

    /**
     * True when the {@code divisor×divisor} block with origin
     * {@code (originX, originY)} contains at least {@code divisor} opaque
     * pixels. Sparse blocks are treated as encoder dirt on large shrinks;
     * a solid upscaled texel is fully opaque.
     */
    private static boolean blockOpaqueEnough(
            int[] srcPixels, int sw, int sh, int originX, int originY, int divisor) {
        int x1 = Math.min(sw, originX + divisor);
        int y1 = Math.min(sh, originY + divisor);
        int opaque = 0;
        for (int y = originY; y < y1; y++) {
            int row = y * sw;
            for (int x = originX; x < x1; x++) {
                if (((srcPixels[row + x] >>> 24) & 0xff) >= 200) {
                    opaque++;
                    if (opaque >= divisor) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Number of timing entries in a comma-separated timings string. Empty or
     * null yields 0.
     */
    public static int countTimings(String timings) {
        if (timings == null || timings.trim().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String part : timings.split(",", -1)) {
            if (!part.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Natural-order compare for frame filenames so {@code walk_2} precedes
     * {@code walk_10}. Letter runs are case-insensitive; digit runs compare
     * as integers.
     */
    public static int naturalCompare(String a, String b) {
        String sa = a != null ? a : "";
        String sb = b != null ? b : "";
        int ia = 0;
        int ib = 0;
        int na = sa.length();
        int nb = sb.length();
        while (ia < na && ib < nb) {
            char ca = sa.charAt(ia);
            char cb = sb.charAt(ib);
            if (isAsciiDigit(ca) && isAsciiDigit(cb)) {
                int startA = ia;
                int startB = ib;
                while (ia < na && isAsciiDigit(sa.charAt(ia))) {
                    ia++;
                }
                while (ib < nb && isAsciiDigit(sb.charAt(ib))) {
                    ib++;
                }
                int va = startA;
                int vb = startB;
                while (va < ia - 1 && sa.charAt(va) == '0') {
                    va++;
                }
                while (vb < ib - 1 && sb.charAt(vb) == '0') {
                    vb++;
                }
                int lenA = ia - va;
                int lenB = ib - vb;
                if (lenA != lenB) {
                    return lenA - lenB;
                }
                int cmp = sa.substring(va, ia).compareTo(sb.substring(vb, ib));
                if (cmp != 0) {
                    return cmp;
                }
                continue;
            }
            int cmp = Character.toLowerCase(ca) - Character.toLowerCase(cb);
            if (cmp != 0) {
                return cmp;
            }
            ia++;
            ib++;
        }
        return (na - ia) - (nb - ib);
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * PNG files in {@code dir} (non-recursive), natural-sorted by name.
     */
    public static List<File> listFrameImageFiles(File dir) throws IOException {
        if (dir == null || !dir.isDirectory()) {
            throw new IOException("Not a directory: " + dir);
        }
        File[] listed = dir.listFiles();
        if (listed == null) {
            throw new IOException("Cannot list " + dir);
        }
        List<File> out = new ArrayList<File>();
        for (File f : listed) {
            if (f.isFile() && isPngName(f.getName())) {
                out.add(f);
            }
        }
        if (out.isEmpty()) {
            throw new IOException("No PNG frames in " + dir.getName());
        }
        sortFrameFiles(out);
        return out;
    }

    /**
     * Resolves a folder or a list of PNG files to a natural-sorted frame list.
     * Does not mix a folder with loose files.
     */
    public static List<File> collectFrameFiles(List<File> selected) throws IOException {
        if (selected == null || selected.isEmpty()) {
            throw new IOException("No frames selected.");
        }
        List<File> files = new ArrayList<File>();
        List<File> dirs = new ArrayList<File>();
        for (File f : selected) {
            if (f == null) {
                continue;
            }
            if (f.isDirectory()) {
                dirs.add(f);
            } else {
                files.add(f);
            }
        }
        if (!dirs.isEmpty() && !files.isEmpty()) {
            throw new IOException("Select either a folder or image files, not both.");
        }
        if (dirs.size() > 1) {
            throw new IOException("Select a single folder of frames.");
        }
        if (dirs.size() == 1) {
            return listFrameImageFiles(dirs.get(0));
        }
        List<File> pngs = new ArrayList<File>();
        for (File f : files) {
            String name = f.getName();
            if (isPngName(name)) {
                pngs.add(f);
            } else if (name.toLowerCase(Locale.ROOT).endsWith(".gif")) {
                throw new IOException("GIF animations are not individual frames: " + name
                        + ". Use Import image / -sprite for GIFs.");
            }
        }
        if (pngs.isEmpty()) {
            throw new IOException("No PNG frames selected.");
        }
        sortFrameFiles(pngs);
        return pngs;
    }

    static void sortFrameFiles(List<File> files) {
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return naturalCompare(a.getName(), b.getName());
            }
        });
    }

    private static boolean isPngName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".png");
    }

    /**
     * Decodes PNG frame files in the given order (already sorted by the caller).
     */
    public static List<BufferedImage> loadFrameImages(List<File> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IOException("No frames to load.");
        }
        List<BufferedImage> frames = new ArrayList<BufferedImage>(files.size());
        for (File file : files) {
            String name = file.getName();
            if (name.toLowerCase(Locale.ROOT).endsWith(".gif")) {
                throw new IOException("GIF animations are not individual frames: " + name
                        + ". Use Import image / -sprite for GIFs.");
            }
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                throw new IOException("Could not decode " + name);
            }
            frames.add(ensureArgb(img));
        }
        return frames;
    }

    /**
     * Parses a comma-separated lift list ({@code 0,8,16}). Whitespace around
     * commas is ignored. Empty parts, non-integers, and negatives are errors.
     */
    public static int[] parseLifts(String text) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            throw new IOException("Lifts list is empty.");
        }
        String[] parts = text.split(",", -1);
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                throw new IOException("Empty lift at position " + (i + 1) + ".");
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid lift: " + part);
            }
            if (value < 0) {
                throw new IOException("Lift must be >= 0 (got " + value + ").");
            }
            out[i] = value;
        }
        return out;
    }

    /**
     * {@code lifts} as a comma-separated string, or empty when {@code null}.
     */
    public static String formatLifts(int[] lifts) {
        if (lifts == null || lifts.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lifts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(lifts[i]);
        }
        return sb.toString();
    }

    /**
     * Copies {@code lifts} and checks length / non-negative values.
     * {@code null} becomes {@code frameCount} zeros.
     */
    public static int[] normalizeLifts(int[] lifts, int frameCount) throws IOException {
        if (frameCount < 1) {
            throw new IOException("No frames to pack.");
        }
        if (lifts == null) {
            return new int[frameCount];
        }
        if (lifts.length != frameCount) {
            throw new IOException("Expected " + frameCount + " lifts, got " + lifts.length + ".");
        }
        int[] out = new int[frameCount];
        for (int i = 0; i < frameCount; i++) {
            if (lifts[i] < 0) {
                throw new IOException("Lift must be >= 0 (frame " + (i + 1) + ").");
            }
            out[i] = lifts[i];
        }
        return out;
    }

    /**
     * {@code 0, 1, …, n-1}. {@code n} must be {@code >= 1}.
     */
    public static int[] identityOrder(int n) throws IOException {
        if (n < 1) {
            throw new IOException("No frames to order.");
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = i;
        }
        return out;
    }

    /**
     * True when {@code order} is {@code 0..n-1}. {@code null} is identity
     * when {@code n >= 0}.
     */
    public static boolean isIdentityOrder(int[] order, int n) {
        if (order == null) {
            return n >= 0;
        }
        if (n < 0 || order.length != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (order[i] != i) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when {@code order} is {@code null} or {@code 0, 1, …, length-1}.
     */
    public static boolean isIdentityOrder(int[] order) {
        return isIdentityOrder(order, order == null ? 0 : order.length);
    }

    /**
     * Copies {@code order} and checks it is a permutation of {@code 0..n-1}.
     * {@code null} becomes the identity.
     */
    public static int[] normalizeOrder(int[] order, int n) throws IOException {
        if (n < 1) {
            throw new IOException("No frames to order.");
        }
        if (order == null) {
            return identityOrder(n);
        }
        if (order.length != n) {
            throw new IOException("Expected " + n + " order indices, got " + order.length + ".");
        }
        boolean[] seen = new boolean[n];
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int src = order[i];
            if (src < 0 || src >= n) {
                throw new IOException("Order index out of range: " + src + ".");
            }
            if (seen[src]) {
                throw new IOException("Duplicate order index: " + src + ".");
            }
            seen[src] = true;
            out[i] = src;
        }
        return out;
    }

    /**
     * Items in playback order. {@code order} is a permutation of source
     * indices; {@code null} is identity. Does not mutate {@code items}.
     */
    public static <T> List<T> permute(List<T> items, int[] order) throws IOException {
        if (items == null || items.isEmpty()) {
            throw new IOException("No frames to order.");
        }
        int[] perm = normalizeOrder(order, items.size());
        List<T> out = new ArrayList<T>(perm.length);
        for (int i = 0; i < perm.length; i++) {
            out.add(items.get(perm[i]));
        }
        return out;
    }

    /**
     * Values in playback order. {@code order} is a permutation of source
     * indices; {@code null} is identity. Does not mutate {@code values}.
     */
    public static int[] permute(int[] values, int[] order) throws IOException {
        if (values == null || values.length == 0) {
            throw new IOException("No values to order.");
        }
        int[] perm = normalizeOrder(order, values.length);
        int[] out = new int[perm.length];
        for (int i = 0; i < perm.length; i++) {
            out[i] = values[perm[i]];
        }
        return out;
    }

    /**
     * Parabolic hop: {@code 0} at both ends, {@code peak} at the middle.
     * A single frame yields {@code {0}}. {@code peak} is clamped to
     * {@code >= 0}.
     */
    public static int[] hopCurve(int frameCount, int peak) {
        if (frameCount < 1) {
            return new int[0];
        }
        int[] out = new int[frameCount];
        if (frameCount == 1 || peak <= 0) {
            return out;
        }
        float denom = frameCount - 1;
        for (int i = 0; i < frameCount; i++) {
            float t = i / denom;
            out[i] = Math.round(4f * peak * t * (1f - t));
        }
        return out;
    }

    public static PackPreview inspectFrames(List<BufferedImage> frames) throws IOException {
        return inspectFrames(frames, null);
    }

    /**
     * Cell size for {@code frames}. When {@code lifts} is non-null it must
     * match the frame count; cell height is {@code max(frameH + lift)}.
     */
    public static PackPreview inspectFrames(List<BufferedImage> frames, int[] lifts)
            throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IOException("No frames to pack.");
        }
        int[] resolved = normalizeLifts(lifts, frames.size());
        int cellW = 0;
        int cellH = 0;
        boolean mixed = false;
        BufferedImage first = frames.get(0);
        if (first == null) {
            throw new IOException("Null frame");
        }
        int firstW = first.getWidth();
        int firstH = first.getHeight();
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage frame = frames.get(i);
            if (frame == null) {
                throw new IOException("Null frame");
            }
            int w = frame.getWidth();
            int h = frame.getHeight();
            if (w < 1 || h < 1) {
                throw new IOException("Frame has no size");
            }
            if (w != firstW || h != firstH) {
                mixed = true;
            }
            if (w > cellW) {
                cellW = w;
            }
            int placedH = h + resolved[i];
            if (placedH > cellH) {
                cellH = placedH;
            }
        }
        return new PackPreview(frames.size(), cellW, cellH, mixed);
    }

    /**
     * Composites frames into a left-to-right strip using the same placement as
     * {@link #fromFrames}: centred horizontally, {@code lift} pixels of air
     * under each sprite, no gutters. {@code lifts} must already be
     * {@link #normalizeLifts normalized}.
     */
    public static BufferedImage packSheetImage(
            List<BufferedImage> frames, int cellW, int cellH, int[] lifts)
            throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IOException("No frames to pack.");
        }
        if (cellW < 1 || cellH < 1) {
            throw new IOException("Cell has no size");
        }
        int n = frames.size();
        if (lifts == null || lifts.length != n) {
            throw new IOException("Expected " + n + " lifts, got "
                    + (lifts == null ? 0 : lifts.length) + ".");
        }
        BufferedImage sheet = new BufferedImage(n * cellW, cellH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        for (int i = 0; i < n; i++) {
            BufferedImage frame = frames.get(i);
            if (frame == null) {
                g.dispose();
                throw new IOException("Null frame");
            }
            int dx = i * cellW + (cellW - frame.getWidth()) / 2;
            int dy = cellH - frame.getHeight() - lifts[i];
            g.drawImage(frame, dx, dy, null);
        }
        g.dispose();
        return sheet;
    }

    /**
     * Packs frames left-to-right into a PonyPaper strip: cell width is the max
     * frame width, cell height is {@code max(frameH + lift)}, each frame is
     * drawn bottom-centre plus lift in its cell, and there is no gutter
     * between cells.
     */
    public static ImageImport fromFrames(List<BufferedImage> frames, PackOptions options)
            throws IOException {
        PackOptions opts = options != null ? options : new PackOptions();
        int scaleDivisor = resolveScaleDivisor(opts, frames);
        List<BufferedImage> scaled = scaleFrames(frames, scaleDivisor);
        PackPreview preview = inspectFrames(scaled, opts.lifts);
        if (opts.rejectMixedSizes && preview.mixedSizes) {
            throw new IOException("Frame sizes differ; every frame must be the same size.");
        }
        int cellW = preview.cellWidth;
        int cellH = preview.cellHeight;
        int n = preview.frameCount;
        int[] lifts = normalizeLifts(opts.lifts, n);
        String timings = buildTimings(opts, n);

        BufferedImage sheet = packSheetImage(scaled, cellW, cellH, lifts);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(sheet, "png", out)) {
            throw new IOException("Failed to encode spritesheet PNG");
        }
        return new ImageImport(out.toByteArray(), timings, cellW, cellH);
    }

    private static String buildTimings(PackOptions opts, int n) throws IOException {
        if (opts.timingsCs != null) {
            if (opts.timingsCs.length != n) {
                throw new IOException("Expected " + n + " timings, got " + opts.timingsCs.length + ".");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(Math.max(1, opts.timingsCs[i]));
            }
            return sb.toString();
        }
        int timingCs = Math.max(1, opts.defaultTimingCs);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(timingCs);
        }
        return sb.toString();
    }

    /**
     * Collects, natural-sorts, decodes, and packs PNG frames (files and/or one
     * folder).
     */
    public static ImageImport fromFrameFiles(List<File> selected, PackOptions options)
            throws IOException {
        List<File> files = collectFrameFiles(selected);
        return fromFrames(loadFrameImages(files), options);
    }

    /**
     * Horizontal flip of a single frame (does not reverse animation order).
     */
    public static BufferedImage flopFrame(BufferedImage src) {
        if (src == null) {
            throw new IllegalArgumentException("src");
        }
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, w, 0, 0, h, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    public static List<BufferedImage> flopEachFrame(List<BufferedImage> frames) {
        if (frames == null) {
            throw new IllegalArgumentException("frames");
        }
        List<BufferedImage> out = new ArrayList<BufferedImage>(frames.size());
        for (BufferedImage frame : frames) {
            out.add(flopFrame(frame));
        }
        return out;
    }

    /**
     * File name for exported frame {@code index} (0-based) of {@code count}:
     * {@code prefix_01.png}, {@code prefix_02.png}, … Digit width is at least
     * 2 so a later {@link #collectFrameFiles} natural-sort stays in order.
     */
    public static String frameExportFileName(String prefix, int index, int count) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix");
        }
        if (index < 0 || count < 1 || index >= count) {
            throw new IllegalArgumentException("index/count");
        }
        int digits = Math.max(2, Integer.toString(count).length());
        return String.format(Locale.ROOT, "%s_%0" + digits + "d.png", prefix, index + 1);
    }

    /**
     * Replaces path separators in an action/direction prefix so the name is a
     * single path segment.
     */
    public static String sanitizeExportPrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String t = prefix.trim();
        if (t.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == 0) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Destination files {@link #writeFramePngs} would create, in frame order.
     */
    public static List<File> frameExportFiles(File dir, String prefix, int count) {
        if (dir == null) {
            throw new IllegalArgumentException("dir");
        }
        String safe = sanitizeExportPrefix(prefix);
        if (safe.isEmpty()) {
            throw new IllegalArgumentException("prefix");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count");
        }
        List<File> files = new ArrayList<File>(count);
        for (int i = 0; i < count; i++) {
            files.add(new File(dir, frameExportFileName(safe, i, count)));
        }
        return files;
    }

    /**
     * Writes each cell as a numbered PNG in {@code dir} using
     * {@link #frameExportFileName}. Overwrites existing files of the same name.
     *
     * @return the files written, in frame order
     */
    public static List<File> writeFramePngs(List<BufferedImage> frames, File dir, String prefix)
            throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IOException("No frames to export");
        }
        if (dir == null || !dir.isDirectory()) {
            throw new IOException("Export folder does not exist");
        }
        String safe = sanitizeExportPrefix(prefix);
        if (safe.isEmpty()) {
            throw new IOException("Export prefix is empty");
        }
        int n = frames.size();
        List<File> out = new ArrayList<File>(n);
        for (int i = 0; i < n; i++) {
            BufferedImage frame = frames.get(i);
            if (frame == null) {
                throw new IOException("Null frame " + (i + 1));
            }
            File file = new File(dir, frameExportFileName(safe, i, n));
            if (!ImageIO.write(frame, "png", file)) {
                throw new IOException("Failed to encode " + file.getName());
            }
            out.add(file);
        }
        return out;
    }

    /**
     * Splits a left-to-right strip into {@code frameCount} cells using the same
     * integer division as the wallpaper ({@code width / frameCount}).
     */
    public static List<BufferedImage> splitSheet(BufferedImage sheet, int frameCount)
            throws IOException {
        if (sheet == null) {
            throw new IOException("No sheet");
        }
        if (frameCount < 1) {
            throw new IOException("frameCount must be >= 1");
        }
        int sheetW = sheet.getWidth();
        int sheetH = sheet.getHeight();
        if (sheetW < 1 || sheetH < 1) {
            throw new IOException("Sheet has no size");
        }
        int frameW = Math.max(1, sheetW / frameCount);
        List<BufferedImage> frames = new ArrayList<BufferedImage>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int x = i * frameW;
            int w = Math.min(frameW, sheetW - x);
            if (w <= 0) {
                throw new IOException("Sheet too narrow for " + frameCount + " frames");
            }
            BufferedImage cell = new BufferedImage(frameW, sheetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cell.createGraphics();
            g.drawImage(sheet, 0, 0, w, sheetH, x, 0, x + w, sheetH, null);
            g.dispose();
            frames.add(cell);
        }
        return frames;
    }

    /**
     * User-defined frame intervals on a left-to-right strip before re-packing
     * with {@link #fromFrames} or exporting PNGs. Each frame is
     * {@code [starts[i], ends[i])} (exclusive end). Gaps between frames and
     * unused left/right margins are discarded. Content-aware detection is
     * intentionally not used (props attached to a character stay in the same
     * interval).
     */
    public static final class FrameBorders {
        /** Inclusive left X of each frame; length N. */
        public int[] starts = new int[] {0};
        /** Exclusive right X of each frame; length N; each {@code > starts[i]}. */
        public int[] ends = new int[] {1};
        /**
         * When true, each extracted cell is cropped to its opaque bounding box
         * before packing/export (transparent pad only — does not invent borders).
         */
        public boolean trimMargins = true;

        public int frameCount() {
            return starts != null ? starts.length : 0;
        }

        public FrameBorders copy() {
            FrameBorders out = new FrameBorders();
            out.starts = starts != null ? Arrays.copyOf(starts, starts.length) : null;
            out.ends = ends != null ? Arrays.copyOf(ends, ends.length) : null;
            out.trimMargins = trimMargins;
            return out;
        }
    }

    /**
     * Contiguous equal-split borders matching {@link #splitSheet}: cell width
     * {@code sheetW / frameCount}, covering {@code 0 .. N * cellW} (wallpaper
     * may drop a remainder on the right).
     */
    public static FrameBorders equalBorders(int sheetWidth, int frameCount) {
        int n = Math.max(1, frameCount);
        int cellW = Math.max(1, Math.max(1, sheetWidth) / n);
        FrameBorders borders = new FrameBorders();
        borders.starts = new int[n];
        borders.ends = new int[n];
        for (int i = 0; i < n; i++) {
            borders.starts[i] = i * cellW;
            borders.ends[i] = borders.starts[i] + cellW;
        }
        borders.trimMargins = true;
        return borders;
    }

    /**
     * Prefers {@code hint} when {@code > 1}; otherwise the largest exact divisor
     * of {@code sheetWidth} in {@code [2, 16]} that yields a cell at least 8px
     * wide, or {@code 2}.
     */
    public static int suggestFrameCount(int sheetWidth, int hint) {
        if (hint > 1) {
            return hint;
        }
        int best = 0;
        int limit = Math.min(16, Math.max(1, sheetWidth));
        for (int n = 2; n <= limit; n++) {
            if (sheetWidth % n == 0 && sheetWidth / n >= 8) {
                best = n;
            }
        }
        return best > 0 ? best : 2;
    }

    /** Pixels left of the first frame (0 when the first frame starts at 0). */
    public static int bordersUnusedLeft(FrameBorders borders) {
        if (borders == null || borders.starts == null || borders.starts.length < 1) {
            return 0;
        }
        return Math.max(0, borders.starts[0]);
    }

    /** Pixels right of the last frame ({@code sheetW - ends[N-1]}); may be negative if invalid. */
    public static int bordersUnusedRight(int sheetWidth, FrameBorders borders) {
        if (borders == null || borders.ends == null || borders.ends.length < 1) {
            return sheetWidth;
        }
        return sheetWidth - borders.ends[borders.ends.length - 1];
    }

    /** Sum of gaps between consecutive frames ({@code starts[i+1] - ends[i]} when positive). */
    public static int bordersGapTotal(FrameBorders borders) {
        if (borders == null || borders.starts == null || borders.ends == null) {
            return 0;
        }
        int n = Math.min(borders.starts.length, borders.ends.length);
        int gaps = 0;
        for (int i = 0; i + 1 < n; i++) {
            int gap = borders.starts[i + 1] - borders.ends[i];
            if (gap > 0) {
                gaps += gap;
            }
        }
        return gaps;
    }

    /**
     * Validates ordered, non-overlapping frame intervals inside the sheet.
     */
    public static void validateFrameBorders(BufferedImage sheet, FrameBorders borders)
            throws IOException {
        if (sheet == null) {
            throw new IOException("No sheet");
        }
        if (borders == null) {
            throw new IOException("No frame borders");
        }
        int sheetW = sheet.getWidth();
        int sheetH = sheet.getHeight();
        if (sheetW < 1 || sheetH < 1) {
            throw new IOException("Sheet has no size");
        }
        if (borders.starts == null || borders.ends == null) {
            throw new IOException("Frame borders are incomplete");
        }
        if (borders.starts.length != borders.ends.length) {
            throw new IOException("Frame start/end counts differ");
        }
        int n = borders.starts.length;
        if (n < 1) {
            throw new IOException("Frame count must be >= 1");
        }
        for (int i = 0; i < n; i++) {
            int start = borders.starts[i];
            int end = borders.ends[i];
            if (start < 0) {
                throw new IOException("Frame " + (i + 1) + " starts before the sheet");
            }
            if (end > sheetW) {
                throw new IOException("Frame " + (i + 1) + " extends past the sheet width ("
                        + end + " > " + sheetW + ")");
            }
            if (end <= start) {
                throw new IOException("Frame " + (i + 1) + " has no width");
            }
            if (i > 0 && start < borders.ends[i - 1]) {
                throw new IOException("Frame " + (i + 1) + " overlaps the previous frame");
            }
        }
    }

    /**
     * Copies each {@code [starts[i], ends[i])} column range from {@code sheet}.
     * When {@link FrameBorders#trimMargins} is set, crops opaque bounds inside
     * that cell (props stay with the character).
     */
    public static List<BufferedImage> extractFrames(BufferedImage sheet, FrameBorders borders)
            throws IOException {
        validateFrameBorders(sheet, borders);
        BufferedImage src = ensureArgb(sheet);
        int sheetH = src.getHeight();
        int n = borders.starts.length;
        List<BufferedImage> frames = new ArrayList<BufferedImage>(n);
        for (int i = 0; i < n; i++) {
            int x0 = borders.starts[i];
            int x1 = borders.ends[i];
            int cellW = x1 - x0;
            BufferedImage cell = new BufferedImage(cellW, sheetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cell.createGraphics();
            g.drawImage(src, 0, 0, cellW, sheetH, x0, 0, x1, sheetH, null);
            g.dispose();
            if (borders.trimMargins) {
                cell = trimTransparentMargins(cell);
            }
            frames.add(cell);
        }
        return frames;
    }

    /**
     * Crops to the opaque bounding box. Fully transparent cells become 1×1
     * transparent so packing still has a frame.
     */
    public static BufferedImage trimTransparentMargins(BufferedImage src) {
        if (src == null) {
            throw new IllegalArgumentException("src");
        }
        BufferedImage argb = ensureArgb(src);
        int w = argb.getWidth();
        int h = argb.getHeight();
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            argb.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                if (((row[x] >>> 24) & 0xff) != 0) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) {
            return argb;
        }
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        BufferedImage out = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(argb, 0, 0, cw, ch, minX, minY, maxX + 1, maxY + 1, null);
        g.dispose();
        return out;
    }

    /**
     * Flops each cell of a packed strip and restacks in the same order.
     * Timings are copied when {@code timings} is non-empty; otherwise default
     * timings of length {@code frameCount} are used.
     */
    public static ImageImport mirrorSheet(byte[] pngBytes, int frameCount, String timings)
            throws IOException {
        if (pngBytes == null || pngBytes.length == 0) {
            throw new IOException("No spritesheet to mirror");
        }
        if (frameCount < 1) {
            throw new IOException("frameCount must be >= 1");
        }
        BufferedImage sheet = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (sheet == null) {
            throw new IOException("Could not decode spritesheet");
        }
        List<BufferedImage> frames = splitSheet(sheet, frameCount);
        PackOptions opts = new PackOptions();
        ImageImport packed = fromFrames(flopEachFrame(frames), opts);
        String outTimings = timings;
        if (countTimings(outTimings) != frameCount) {
            outTimings = packed.timings;
        }
        return new ImageImport(packed.loadedImage, outTimings, packed.cellWidth, packed.cellHeight);
    }

    public static ImageImport mirrorImport(ImageImport imported) throws IOException {
        if (imported == null) {
            throw new IOException("No spritesheet to mirror");
        }
        int n = countTimings(imported.timings);
        if (n < 1) {
            throw new IOException("Cannot mirror a sheet without frame timings");
        }
        return mirrorSheet(imported.loadedImage, n, imported.timings);
    }

    static BufferedImage ensureArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }



    // -------------------------------------------------------------------------
    // GIF decoder (coalesced full frames)
    // -------------------------------------------------------------------------

    private static final class GifFrame {
        final BufferedImage argb;
        /** Frame delay in hundredths of a second (PonyPaper timing units). */
        final int delayCs;

        GifFrame(BufferedImage argb, int delayCs) {
            this.argb = argb;
            this.delayCs = delayCs;
        }
    }

    private static final class GifAnimation {
        final int logicalWidth;
        final int logicalHeight;
        final List<GifFrame> frames;

        GifAnimation(int logicalWidth, int logicalHeight, List<GifFrame> frames) {
            this.logicalWidth = logicalWidth;
            this.logicalHeight = logicalHeight;
            this.frames = frames;
        }

        static GifAnimation decode(byte[] data) throws IOException {
            if (data.length < 13) {
                throw new IOException("GIF too short");
            }
            // Header
            if (!(data[0] == 'G' && data[1] == 'I' && data[2] == 'F')) {
                throw new IOException("Not a GIF file");
            }

            int logicalWidth = u16(data, 6);
            int logicalHeight = u16(data, 8);
            int packed = data[10] & 0xff;
            boolean gctFlag = (packed & 0x80) != 0;
            int gctSize = gctFlag ? 1 << ((packed & 0x07) + 1) : 0;
            int pos = 13;
            int[] globalColorTable = null;
            if (gctFlag) {
                globalColorTable = readColorTable(data, pos, gctSize);
                pos += gctSize * 3;
            }

            // Graphic Control Extension state (applies to the next image).
            int disposal = 0;
            int delayCs = 4;
            boolean transparency = false;
            int transparentIndex = 0;

            // Canvas for coalescing. Transparent background so sprites stay clean.
            int[] canvas = new int[logicalWidth * logicalHeight];
            // Previous canvas for restoreToPrevious.
            int[] previous = null;

            List<GifFrame> frames = new ArrayList<GifFrame>();

            while (pos < data.length) {
                int b = data[pos] & 0xff;
                if (b == 0x3b) {
                    break; // trailer
                }
                if (b == 0x21) {
                    // Extension
                    if (pos + 1 >= data.length) {
                        throw new IOException("Truncated GIF extension");
                    }
                    int label = data[pos + 1] & 0xff;
                    pos += 2;
                    if (label == 0xf9) {
                        // Graphic Control Extension
                        if (pos >= data.length) {
                            throw new IOException("Truncated GCE");
                        }
                        int blockSize = data[pos] & 0xff;
                        pos += 1;
                        if (blockSize < 4 || pos + blockSize > data.length) {
                            throw new IOException("Invalid GCE block");
                        }
                        int gcePacked = data[pos] & 0xff;
                        disposal = (gcePacked >> 2) & 0x07;
                        transparency = (gcePacked & 0x01) != 0;
                        delayCs = u16(data, pos + 1);
                        if (delayCs == 0) {
                            delayCs = 4; // match prior editor behaviour
                        }
                        transparentIndex = data[pos + 3] & 0xff;
                        pos += blockSize;
                        // block terminator
                        if (pos >= data.length || data[pos] != 0) {
                            // Some writers omit; skip any residual sub-blocks.
                            pos = skipDataSubBlocks(data, pos);
                        } else {
                            pos += 1;
                        }
                    } else {
                        // Comment / plain text / application — skip sub-blocks.
                        pos = skipDataSubBlocks(data, pos);
                    }
                    continue;
                }
                if (b == 0x2c) {
                    // Image descriptor
                    if (pos + 10 > data.length) {
                        throw new IOException("Truncated image descriptor");
                    }
                    int left = u16(data, pos + 1);
                    int top = u16(data, pos + 3);
                    int width = u16(data, pos + 5);
                    int height = u16(data, pos + 7);
                    int idPacked = data[pos + 9] & 0xff;
                    pos += 10;

                    boolean lctFlag = (idPacked & 0x80) != 0;
                    boolean interlace = (idPacked & 0x40) != 0;
                    int lctSize = lctFlag ? 1 << ((idPacked & 0x07) + 1) : 0;

                    int[] colorTable = globalColorTable;
                    int colorTableSize = gctSize;
                    if (lctFlag) {
                        if (pos + lctSize * 3 > data.length) {
                            throw new IOException("Truncated local colour table");
                        }
                        colorTable = readColorTable(data, pos, lctSize);
                        colorTableSize = lctSize;
                        pos += lctSize * 3;
                    }
                    if (colorTable == null) {
                        throw new IOException("GIF frame has no colour table");
                    }

                    if (pos >= data.length) {
                        throw new IOException("Truncated image data");
                    }
                    int minCodeSize = data[pos] & 0xff;
                    pos += 1;
                    ByteArrayOutputStream lzwData = new ByteArrayOutputStream();
                    pos = readDataSubBlocks(data, pos, lzwData);

                    byte[] indices = lzwDecode(minCodeSize, lzwData.toByteArray(), width * height);

                    // Save canvas before drawing when disposal is restoreToPrevious.
                    if (disposal == 3) {
                        previous = Arrays.copyOf(canvas, canvas.length);
                    }

                    // Composite this frame onto the canvas.
                    paintFrame(
                            canvas, logicalWidth, logicalHeight,
                            indices, width, height, left, top, interlace,
                            colorTable, colorTableSize,
                            transparency, transparentIndex);

                    // Snapshot coalesced frame.
                    BufferedImage snapshot = new BufferedImage(
                            logicalWidth, logicalHeight, BufferedImage.TYPE_INT_ARGB);
                    snapshot.setRGB(0, 0, logicalWidth, logicalHeight, canvas, 0, logicalWidth);
                    frames.add(new GifFrame(snapshot, delayCs));

                    // Apply disposal for subsequent frames.
                    if (disposal == 2) {
                        // restoreToBackgroundColor — clear dirty rect to transparent
                        // (sprite overlays; not the GIF background colour).
                        clearRect(canvas, logicalWidth, logicalHeight, left, top, width, height);
                    } else if (disposal == 3 && previous != null) {
                        System.arraycopy(previous, 0, canvas, 0, canvas.length);
                    }
                    // disposal 0/1 (none / doNotDispose): leave canvas as-is.

                    // Reset GCE defaults for the next frame.
                    disposal = 0;
                    delayCs = 4;
                    transparency = false;
                    transparentIndex = 0;
                    continue;
                }
                throw new IOException("Unknown GIF block 0x" + Integer.toHexString(b));
            }

            if (frames.isEmpty()) {
                throw new IOException("GIF has no image frames");
            }
            // Sprites always use a transparent canvas rather than filling with
            // the GIF background colour index.
            return new GifAnimation(logicalWidth, logicalHeight, frames);
        }
    }

    private static int u16(byte[] data, int off) {
        return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8);
    }

    private static int[] readColorTable(byte[] data, int pos, int size) {
        int[] table = new int[size];
        for (int i = 0; i < size; i++) {
            int r = data[pos++] & 0xff;
            int g = data[pos++] & 0xff;
            int b = data[pos++] & 0xff;
            table[i] = 0xff000000 | (r << 16) | (g << 8) | b;
        }
        return table;
    }

    private static int skipDataSubBlocks(byte[] data, int pos) throws IOException {
        while (pos < data.length) {
            int n = data[pos] & 0xff;
            pos += 1;
            if (n == 0) {
                return pos;
            }
            if (pos + n > data.length) {
                throw new IOException("Truncated GIF sub-block");
            }
            pos += n;
        }
        throw new IOException("Truncated GIF sub-blocks");
    }

    private static int readDataSubBlocks(byte[] data, int pos, ByteArrayOutputStream out)
            throws IOException {
        while (pos < data.length) {
            int n = data[pos] & 0xff;
            pos += 1;
            if (n == 0) {
                return pos;
            }
            if (pos + n > data.length) {
                throw new IOException("Truncated GIF image data");
            }
            out.write(data, pos, n);
            pos += n;
        }
        throw new IOException("Truncated GIF image data");
    }

    /**
     * GIF LZW decode producing one index byte per pixel (indices may exceed the
     * colour table size — callers must handle that for transparency).
     */
    private static byte[] lzwDecode(int minCodeSize, byte[] data, int expectedPixels)
            throws IOException {
        if (minCodeSize < 2 || minCodeSize > 12) {
            throw new IOException("Invalid LZW minimum code size: " + minCodeSize);
        }
        final int clearCode = 1 << minCodeSize;
        final int endCode = clearCode + 1;

        // Dictionary: each entry is [prefixCode+1, suffixByte] packed, or we store
        // lengths + reverse reconstruction. Use explicit byte arrays for clarity.
        int dictSize = 4096;
        int[] prefix = new int[dictSize];
        byte[] suffix = new byte[dictSize];

        int codeSize = minCodeSize + 1;
        int nextCode = endCode + 1;

        for (int i = 0; i < clearCode; i++) {
            prefix[i] = -1;
            suffix[i] = (byte) i;
        }

        byte[] output = new byte[expectedPixels];
        int outPos = 0;
        final int dataLen = data.length;
        int currentBitPos = 0;
        Integer prevCode = null;

        while (outPos < expectedPixels) {
            int code = 0;
            for (int i = 0; i < codeSize; i++) {
                int byteIndex = currentBitPos >> 3;
                if (byteIndex >= dataLen) {
                    code = -1;
                    break;
                }
                int bit = (data[byteIndex] >> (currentBitPos & 7)) & 1;
                code |= bit << i;
                currentBitPos++;
            }
            if (code < 0) {
                break;
            }

            if (code == clearCode) {
                codeSize = minCodeSize + 1;
                nextCode = endCode + 1;
                prevCode = null;
                continue;
            }
            if (code == endCode) {
                break;
            }

            byte[] entry;
            if (code < nextCode) {
                entry = expand(prefix, suffix, code);
            } else if (prevCode != null && code == nextCode) {
                // KwKwK special case
                byte[] prev = expand(prefix, suffix, prevCode);
                entry = Arrays.copyOf(prev, prev.length + 1);
                entry[prev.length] = prev[0];
            } else {
                throw new IOException("Invalid LZW code " + code);
            }

            int copy = Math.min(entry.length, expectedPixels - outPos);
            System.arraycopy(entry, 0, output, outPos, copy);
            outPos += copy;
            if (outPos >= expectedPixels) {
                break;
            }

            if (prevCode != null && nextCode < 4096) {
                prefix[nextCode] = prevCode;
                suffix[nextCode] = entry[0];
                nextCode++;
                if (nextCode == (1 << codeSize) && codeSize < 12) {
                    codeSize++;
                }
            }
            prevCode = code;
        }

        if (outPos < expectedPixels) {
            // Pad remaining with 0 (background index) rather than failing —
            // some writers truncate trailing clear regions.
            Arrays.fill(output, outPos, expectedPixels, (byte) 0);
        }
        return output;
    }

    private static byte[] expand(int[] prefix, byte[] suffix, int code) {
        int len = 0;
        int c = code;
        while (c >= 0) {
            len++;
            c = prefix[c];
            if (len > 4096) {
                break; // corrupt stream protection
            }
        }
        byte[] out = new byte[len];
        c = code;
        for (int i = len - 1; i >= 0; i--) {
            out[i] = suffix[c];
            c = prefix[c];
            if (c < 0) {
                break;
            }
        }
        return out;
    }

    private static void paintFrame(
            int[] canvas, int canvasW, int canvasH,
            byte[] indices, int frameW, int frameH, int left, int top, boolean interlace,
            int[] colorTable, int colorTableSize,
            boolean transparency, int transparentIndex) {

        // Interlace pass row mapping
        int[] passStarts = interlace ? new int[] {0, 4, 2, 1} : new int[] {0};
        int[] passSteps = interlace ? new int[] {8, 8, 4, 2} : new int[] {1};

        int src = 0;
        for (int pass = 0; pass < passStarts.length; pass++) {
            for (int y = passStarts[pass]; y < frameH; y += passSteps[pass]) {
                int destY = top + y;
                for (int x = 0; x < frameW; x++) {
                    if (src >= indices.length) {
                        return;
                    }
                    int idx = indices[src++] & 0xff;
                    int destX = left + x;
                    if (destX < 0 || destY < 0 || destX >= canvasW || destY >= canvasH) {
                        continue;
                    }
                    if (transparency && idx == transparentIndex) {
                        // Leave canvas pixel unchanged (dirty-rect optimisation).
                        continue;
                    }
                    if (idx < colorTableSize) {
                        canvas[destY * canvasW + destX] = colorTable[idx];
                    }
                    // Index outside the colour table and not the transparent
                    // index: leave unchanged. (ImageIO would paint garbage.)
                }
            }
        }
    }

    private static void clearRect(
            int[] canvas, int canvasW, int canvasH, int left, int top, int width, int height) {
        int x0 = Math.max(0, left);
        int y0 = Math.max(0, top);
        int x1 = Math.min(canvasW, left + width);
        int y1 = Math.min(canvasH, top + height);
        for (int y = y0; y < y1; y++) {
            int row = y * canvasW;
            for (int x = x0; x < x1; x++) {
                canvas[row + x] = 0x00000000;
            }
        }
    }
}
