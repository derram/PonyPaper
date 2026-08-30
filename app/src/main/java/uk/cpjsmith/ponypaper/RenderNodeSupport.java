package uk.cpjsmith.ponypaper;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * API 29+ {@link RenderNode} support for background bitmap drawing.
 * Isolated to avoid verification errors on older devices.
 */
final class RenderNodeSupport {

    private final Object impl;

    @SuppressLint("NewApi")
    private RenderNodeSupport() {
        impl = new Impl();
    }

    @Nullable
    static RenderNodeSupport create() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new RenderNodeSupport();
        }
        return null;
    }

    void update(Bitmap bitmap, int width, int height, Paint paint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ((Impl) impl).update(bitmap, width, height, paint);
        }
    }

    void setTranslation(float x, float y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ((Impl) impl).setTranslation(x, y);
        }
    }

    void draw(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ((Impl) impl).draw(canvas);
        }
    }

    void discard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ((Impl) impl).discard();
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static final class Impl {
        private final RenderNode renderNode;
        private final Rect dstRect = new Rect();
        private Bitmap lastBitmap;
        private int lastWidth;
        private int lastHeight;
        private int lastAlpha = -1;

        Impl() {
            renderNode = new RenderNode("background_layer");
        }

        void update(Bitmap bitmap, int width, int height, Paint paint) {
            if (bitmap == null || bitmap.isRecycled()) {
                lastBitmap = null;
                return;
            }

            int alpha = paint.getAlpha();
            if (bitmap == lastBitmap && width == lastWidth && height == lastHeight && alpha == lastAlpha) {
                return;
            }

            lastBitmap = bitmap;
            lastWidth = width;
            lastHeight = height;
            lastAlpha = alpha;

            renderNode.setPosition(0, 0, width, height);
            RecordingCanvas canvas = renderNode.beginRecording();
            try {
                dstRect.set(0, 0, width, height);
                canvas.drawBitmap(bitmap, null, dstRect, paint);
            } finally {
                renderNode.endRecording();
            }
        }

        void setTranslation(float x, float y) {
            renderNode.setTranslationX(x);
            renderNode.setTranslationY(y);
        }

        void draw(Canvas canvas) {
            if (lastBitmap != null && !lastBitmap.isRecycled()) {
                canvas.drawRenderNode(renderNode);
            }
        }

        void discard() {
            renderNode.discardDisplayList();
            lastBitmap = null;
            lastAlpha = -1;
        }
    }
}
