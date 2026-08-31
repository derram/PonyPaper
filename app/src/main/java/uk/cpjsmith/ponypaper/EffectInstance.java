package uk.cpjsmith.ponypaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * One live effect sprite in the scene. Position is the top-left of the scaled
 * frame. Planted effects keep a fixed origin; follow effects re-attach each
 * update using the resolved placement cell from spawn.
 */
final class EffectInstance {

    final PonyEffectDef def;
    final Pony parent;
    /** Facing snapshot at spawn ({@link PonyAction#LEFT}/{@link PonyAction#RIGHT}). */
    final int facing;
    /** Placement cell resolved at spawn (never Any). */
    final int resolvedPlacement;

    float originX;
    float originY;
    float ageMs;
    float animTimeCs;
    boolean expired;

    EffectInstance(PonyEffectDef def, Pony parent, int facing, int resolvedPlacement,
            float originX, float originY) {
        this.def = def;
        this.parent = parent;
        this.facing = facing;
        this.resolvedPlacement = resolvedPlacement;
        this.originX = originX;
        this.originY = originY;
        this.ageMs = 0f;
        this.animTimeCs = 0f;
        this.expired = false;
    }

    /**
     * @return true if still alive after this tick
     */
    boolean update(float deltaMs, RectF ponyBoundsScratch, float[] originScratch) {
        if (expired) {
            return false;
        }
        ageMs += deltaMs;
        if (def.durationMs > 0f && ageMs >= def.durationMs) {
            expired = true;
            return false;
        }

        SpriteSheet sheet = def.sheet(facing);
        if (sheet != null && sheet.totalTime > 0) {
            // Animation clock is in centiseconds (same as pony actions).
            animTimeCs += deltaMs / 10f;
            int total = sheet.totalTime;
            if (def.noLoop) {
                if (animTimeCs >= total) {
                    animTimeCs = total - 1;
                }
            } else {
                while (animTimeCs >= total) {
                    animTimeCs -= total;
                }
            }
        }

        if (def.follow && parent != null) {
            parent.fillCurrentDrawBounds(ponyBoundsScratch);
            float scale = parent.getScale();
            def.computeOriginFixed(ponyBoundsScratch, facing, scale,
                    resolvedPlacement, originScratch);
            originX = originScratch[0];
            originY = originScratch[1];
        }
        return true;
    }

    void drawOn(Canvas c, Rect srcScratch, Rect dstScratch) {
        SpriteSheet sheet = def.sheet(facing);
        if (sheet == null || !sheet.hasDrawable()) {
            return;
        }
        Bitmap draw = sheet.bitmapFor(c);
        if (draw == null || draw.isRecycled()) {
            return;
        }
        int total = sheet.totalTime;
        if (total <= 0) {
            return;
        }
        int time = (int)animTimeCs;
        if (time < 0) {
            time = 0;
        }
        if (time >= total) {
            time = total - 1;
        }
        float scale = parent != null ? parent.getScale() : 1f;
        int w = Math.round(sheet.frameWidth * scale);
        int h = Math.round(sheet.frameHeight * scale);
        sheet.getRect(time, srcScratch);
        int left = Math.round(originX);
        int top = Math.round(originY);
        dstScratch.set(left, top, left + w, top + h);
        c.drawBitmap(draw, srcScratch, dstScratch, null);
    }

    /** Rough sort key: bottom of the effect sprite (Y-depth). */
    int sortY() {
        SpriteSheet sheet = def.sheet(facing);
        float scale = parent != null ? parent.getScale() : 1f;
        float h = sheet != null ? sheet.frameHeight * scale : 0f;
        return Math.round(originY + h);
    }

    void writeVisualStamp(int[] out, int offset) {
        out[offset] = System.identityHashCode(def);
        out[offset + 1] = facing;
        SpriteSheet sheet = def.sheet(facing);
        int frame = 0;
        if (sheet != null && sheet.totalTime > 0) {
            int time = (int)animTimeCs;
            if (time < 0) {
                time = 0;
            }
            if (time >= sheet.totalTime) {
                time = sheet.totalTime - 1;
            }
            frame = sheet.getFrameIndex(time);
        }
        out[offset + 2] = frame;
        out[offset + 3] = Math.round(originX);
        out[offset + 4] = Math.round(originY);
    }
}
