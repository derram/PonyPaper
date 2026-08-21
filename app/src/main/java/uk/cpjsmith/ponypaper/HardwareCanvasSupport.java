package uk.cpjsmith.ponypaper;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

/**
 * API 26+ {@link SurfaceHolder#lockHardwareCanvas()} isolated so pre-O devices
 * never resolve that method when loading {@link PonySceneController}.
 *
 * <p>Only call when {@code Build.VERSION.SDK_INT >= O}.
 */
final class HardwareCanvasSupport {

    private HardwareCanvasSupport() {}

    static Canvas lock(SurfaceHolder holder) {
        return holder.lockHardwareCanvas();
    }
}
