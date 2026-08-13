package uk.cpjsmith.ponypaper.custom;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * {@link #fromFrameFiles}: uniform cells, bottom-centre alignment, no
 * inter-frame padding. Animated GIFs —
 * typically Desktop Ponies sprites — are decoded, fully coalesced (so each
 * frame is a complete image, not a dirty-rectangle delta), scaled to half
 * size for PonyPaper, and packed left-to-right into a single PNG spritesheet
 * with matching frame timings.
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
        String filename = file.getName().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".gif")) {
            return loadGIF(file);
        } else {
            return new ImageImport(Files.readAllBytes(file.toPath()), null);
        }
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

    public static PackPreview inspectFrames(List<BufferedImage> frames) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IOException("No frames to pack.");
        }
        int cellW = 0;
        int cellH = 0;
        boolean mixed = false;
        BufferedImage first = frames.get(0);
        if (first == null) {
            throw new IOException("Null frame");
        }
        int firstW = first.getWidth();
        int firstH = first.getHeight();
        for (BufferedImage frame : frames) {
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
            if (h > cellH) {
                cellH = h;
            }
        }
        return new PackPreview(frames.size(), cellW, cellH, mixed);
    }

    /**
     * Packs frames left-to-right into a PonyPaper strip: cell size is the max
     * frame width × max frame height, each frame is drawn bottom-centre in its
     * cell, and there is no gutter between cells.
     */
    public static ImageImport fromFrames(List<BufferedImage> frames, PackOptions options)
            throws IOException {
        PackPreview preview = inspectFrames(frames);
        PackOptions opts = options != null ? options : new PackOptions();
        if (opts.rejectMixedSizes && preview.mixedSizes) {
            throw new IOException("Frame sizes differ; expected "
                    + preview.cellWidth + "×" + preview.cellHeight + " for every frame.");
        }
        int timingCs = Math.max(1, opts.defaultTimingCs);
        int cellW = preview.cellWidth;
        int cellH = preview.cellHeight;
        int n = preview.frameCount;

        BufferedImage sheet = new BufferedImage(n * cellW, cellH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        StringBuilder timings = new StringBuilder();
        for (int i = 0; i < n; i++) {
            BufferedImage frame = frames.get(i);
            int dx = i * cellW + (cellW - frame.getWidth()) / 2;
            int dy = cellH - frame.getHeight();
            g.drawImage(frame, dx, dy, null);
            if (i > 0) {
                timings.append(',');
            }
            timings.append(timingCs);
        }
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(sheet, "png", out)) {
            throw new IOException("Failed to encode spritesheet PNG");
        }
        return new ImageImport(out.toByteArray(), timings.toString(), cellW, cellH);
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

    private static ImageImport loadGIF(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        GifAnimation animation = GifAnimation.decode(data);

        int frameCount = animation.frames.size();
        if (frameCount == 0) {
            throw new IOException("GIF has no frames: " + file);
        }

        // PonyPaper sprites are half the Desktop Ponies resolution.
        int frameWidth = Math.max(1, animation.logicalWidth / 2);
        int frameHeight = Math.max(1, animation.logicalHeight / 2);

        BufferedImage sheet = new BufferedImage(
                frameCount * frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sheetG = sheet.createGraphics();
        sheetG.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        sheetG.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        StringBuilder timings = new StringBuilder();
        for (int i = 0; i < frameCount; i++) {
            GifFrame frame = animation.frames.get(i);
            sheetG.drawImage(
                    frame.argb,
                    i * frameWidth, 0, (i + 1) * frameWidth, frameHeight,
                    0, 0, animation.logicalWidth, animation.logicalHeight,
                    null);
            if (i != 0) {
                timings.append(',');
            }
            timings.append(frame.delayCs);
        }
        sheetG.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(sheet, "png", out);
        return new ImageImport(out.toByteArray(), timings.toString(), frameWidth, frameHeight);
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
