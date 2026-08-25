package uk.cpjsmith.ponypaper;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;

/**
 * Encapsulates a linear sequence of images with associated timings. The images
 * are all stored in a single Bitmap.
 *
 * <p>Optional {@link #hardwareBitmap} is a GPU-resident copy for
 * {@link android.view.SurfaceHolder#lockHardwareCanvas()}; the CPU
 * {@link #bitmap} is always kept for software {@code lockCanvas} fallback.
 */
public class SpriteSheet {
    
    public Bitmap bitmap;
    /**
     * Optional {@link Bitmap.Config#HARDWARE} copy of {@link #bitmap}. Null when
     * upload was skipped or failed. Never drawn on a software canvas.
     */
    Bitmap hardwareBitmap;
    public int totalTime;
    public int frameWidth;
    public int frameHeight;
    
    private int[] frameTimes;
    /** Exclusive end time of each frame; {@code cumulative[i] == sum(frameTimes[0..i])}. */
    private int[] cumulative;
    
    private static BitmapFactory.Options decodeOptions() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        return opts;
    }
    
    /**
     * Constructs a new SpriteSheet object from a drawable resource and an
     * integer array resource. The frame count is extracted from the length of
     * the frame time array.
     * 
     * @param res     the Resources object to use
     * @param drawId  the identifier of the drawable resource containing the
     *                frames
     * @param timesId the identifier of the integer array resource containing
     *                the frame times
     */
    public SpriteSheet(Resources res, int drawId, int timesId) {
        bitmap = BitmapFactory.decodeResource(res, drawId, decodeOptions());
        frameTimes = res.getIntArray(timesId);
        setInternals();
    }
    
    /**
     * Constructs a new SpriteSheet object from an in-memory image file and an
     * array of frame times. The frame count is extracted from the length of
     * the frame time array.
     * 
     * @param bitmapData the image file as a byte array
     * @param frameTimes the integer array containing the frame times
     */
    public SpriteSheet(byte[] bitmapData, int[] frameTimes) {
        this.bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length, decodeOptions());
        this.frameTimes = frameTimes;
        setInternals();
    }
    
    /**
     * Return the boundary of the region of the complete image that should be
     * displayed at the given time. Requires {@code 0 <= time < totalTime}.
     * 
     * @param time the number of 10-millisecond intervals since the start of
     *             the animation
     * @return the rectangle to use as the {@code src} parameter to
     *         {@code android.graphics.canvas.drawBitmap()}
     * @throws IllegalArgumentException if {@code time} is invalid
     */
    public Rect getRect(int time) {
        Rect out = new Rect();
        getRect(time, out);
        return out;
    }

    /**
     * Same as {@link #getRect(int)} but writes into {@code out} (no allocation).
     */
    public void getRect(int time, Rect out) {
        int frame = getFrameIndex(time);
        int left = frameWidth * frame;
        out.set(left, 0, left + frameWidth, frameHeight);
    }

    /**
     * Zero-based frame whose interval contains {@code time}.
     * Requires {@code 0 <= time < totalTime}.
     */
    public int getFrameIndex(int time) {
        if (time < 0 || time >= totalTime) {
            throw new IllegalArgumentException("Invalid frame time.");
        }
        int lo = 0;
        int hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] <= time) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
    
    private void setInternals() {
        if (bitmap == null) {
            throw new IllegalArgumentException("Failed to decode sprite sheet bitmap");
        }
        if (frameTimes == null || frameTimes.length == 0) {
            throw new IllegalArgumentException("Sprite sheet has no frame times");
        }
        cumulative = new int[frameTimes.length];
        int sum = 0;
        for (int i = 0; i < frameTimes.length; i++) {
            sum += frameTimes[i];
            cumulative[i] = sum;
        }
        totalTime = sum;
        frameWidth = bitmap.getWidth() / frameTimes.length;
        frameHeight = bitmap.getHeight();
    }
    
    /**
     * Upload {@link #bitmap} to {@link #hardwareBitmap} once (API 26+). Keeps
     * the CPU bitmap for software-canvas fallback. Safe to call more than once.
     */
    void uploadToGpu() {
        if (hardwareBitmap != null && !hardwareBitmap.isRecycled()) {
            return;
        }
        hardwareBitmap = null;
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        Bitmap hw = GpuBitmaps.upload(bitmap);
        if (hw != null && hw != bitmap) {
            hardwareBitmap = hw;
        }
    }

    /**
     * Bitmap to blit on {@code c}: GPU copy on a hardware canvas when available,
     * otherwise the CPU {@link #bitmap}.
     */
    Bitmap bitmapFor(Canvas c) {
        if (c != null && c.isHardwareAccelerated()
                && hardwareBitmap != null && !hardwareBitmap.isRecycled()) {
            return hardwareBitmap;
        }
        return bitmap;
    }

    /**
     * Release the pixel buffer held by this sheet. Safe to call more than once;
     * after this, the sheet must not be drawn until reloaded.
     *
     * <p>Shared sheets are recycled only by {@link SpriteCache} when the last
     * pin is dropped. Direct callers must own the bitmap exclusively.
     */
    void recycle() {
        if (hardwareBitmap != null && hardwareBitmap != bitmap
                && !hardwareBitmap.isRecycled()) {
            hardwareBitmap.recycle();
        }
        hardwareBitmap = null;
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmap = null;
    }
    
}
