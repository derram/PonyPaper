package uk.cpjsmith.ponypaper;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

/**
 * API 26+ {@link SurfaceHolder#lockHardwareCanvas()} isolated so pre-O devices
 * never resolve that method when loading {@link PonySceneController}.
 *
 * <p>Only call when {@code Build.VERSION.SDK_INT >= O}. Unlock with the same
 * {@link SurfaceHolder#unlockCanvasAndPost(Canvas)} used for software locks.
 * On failure, callers should fall back to {@link SurfaceHolder#lockCanvas()}
 * until the next surface recreate.
 */
final class HardwareCanvasSupport {

    private HardwareCanvasSupport() {}

    static Canvas lock(SurfaceHolder holder) {
        return holder.lockHardwareCanvas();
    }
}
