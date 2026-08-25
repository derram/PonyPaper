package uk.cpjsmith.ponypaper;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

/**
 * API 26+ {@link Bitmap.Config#HARDWARE} uploads isolated so pre-O devices never
 * resolve that config when loading callers. Decode and scale on the CPU first,
 * then upload once for {@link android.view.SurfaceHolder#lockHardwareCanvas()}.
 */
final class GpuBitmaps {

    private static final String TAG = "PonyPaper";

    private GpuBitmaps() {}

    /**
     * GPU-resident immutable copy of {@code src}, or null when unsupported,
     * unnecessary, or the copy failed. Never recycles {@code src}.
     */
    static Bitmap upload(Bitmap src) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }
        if (src == null || src.isRecycled()) {
            return null;
        }
        return GpuBitmapsO.upload(src);
    }

    /**
     * Prefer a HARDWARE copy of {@code src}. On success recycles {@code src} and
     * returns the GPU bitmap; on failure returns {@code src} unchanged.
     */
    static Bitmap uploadAndRecycleSource(Bitmap src) {
        Bitmap hw = upload(src);
        if (hw == null) {
            return src;
        }
        if (src != null && src != hw && !src.isRecycled()) {
            src.recycle();
        }
        return hw;
    }

    /** True when {@code b} is a non-recycled {@link Bitmap.Config#HARDWARE} bitmap. */
    static boolean isHardware(Bitmap b) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        if (b == null || b.isRecycled()) {
            return false;
        }
        return GpuBitmapsO.isHardware(b);
    }

    /** API 26+ implementation; only loaded when {@link #upload} / {@link #isHardware} run. */
    private static final class GpuBitmapsO {
        private GpuBitmapsO() {}

        static Bitmap upload(Bitmap src) {
            if (src.getConfig() == Bitmap.Config.HARDWARE) {
                return src;
            }
            try {
                Bitmap hw = src.copy(Bitmap.Config.HARDWARE, false);
                if (hw == null) {
                    Log.w(TAG, "HARDWARE bitmap copy returned null");
                }
                return hw;
            } catch (OutOfMemoryError e) {
                Log.w(TAG, "HARDWARE bitmap copy skipped (OOM)", e);
                return null;
            } catch (RuntimeException e) {
                Log.w(TAG, "HARDWARE bitmap copy failed", e);
                return null;
            }
        }

        static boolean isHardware(Bitmap b) {
            return b.getConfig() == Bitmap.Config.HARDWARE;
        }
    }
}
