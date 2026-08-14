package uk.cpjsmith.ponypaper;

import android.content.res.Resources;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 * Process-wide refcounted {@link SpriteSheet} store. Wallpaper and dream each
 * keep their own {@link Pony} motion state, but identical sheets (same resource
 * ids, or the same custom image bytes and frame times) share one decoded
 * bitmap. The last {@link #unpin} recycles that bitmap.
 *
 * <p>Pin/unpin is per owning {@link PonyAction} load/unload. Gait aliases still
 * delegate to the owner so one pony contributes at most one pin per sheet.
 */
final class SpriteCache {

    private static final Object LOCK = new Object();
    private static final HashMap<String, Entry> BY_KEY = new HashMap<String, Entry>();
    private static final IdentityHashMap<SpriteSheet, Entry> BY_SHEET =
            new IdentityHashMap<SpriteSheet, Entry>();

    private static final class Entry {
        final String key;
        final SpriteSheet sheet;
        int refs;

        Entry(String key, SpriteSheet sheet) {
            this.key = key;
            this.sheet = sheet;
            this.refs = 0;
        }
    }

    interface SheetFactory {
        SpriteSheet create();
    }

    private SpriteCache() {
    }

    static String resourceKey(int drawId, int timesId) {
        return "r:" + drawId + ":" + timesId;
    }

    static String bytesKey(byte[] bitmapData, int[] frameTimes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (bitmapData != null) {
                md.update(bitmapData);
            }
            if (frameTimes != null && frameTimes.length > 0) {
                ByteBuffer buf = ByteBuffer.allocate(frameTimes.length * 4);
                buf.asIntBuffer().put(frameTimes);
                md.update(buf.array());
            }
            return "b:" + toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static SpriteSheet pinResource(final Resources res, final int drawId, final int timesId) {
        return pin(resourceKey(drawId, timesId), new SheetFactory() {
            @Override
            public SpriteSheet create() {
                return new SpriteSheet(res, drawId, timesId);
            }
        });
    }

    static SpriteSheet pinBytes(final byte[] bitmapData, final int[] frameTimes) {
        return pin(bytesKey(bitmapData, frameTimes), new SheetFactory() {
            @Override
            public SpriteSheet create() {
                return new SpriteSheet(bitmapData, frameTimes);
            }
        });
    }

    /**
     * Increment the pin count for {@code key}, decoding via {@code factory} on
     * a miss. Decode happens outside the lock; a lost race recycles the extra
     * sheet and returns the winner.
     */
    static SpriteSheet pin(String key, SheetFactory factory) {
        if (key == null || factory == null) {
            throw new IllegalArgumentException("key/factory");
        }
        synchronized (LOCK) {
            Entry existing = BY_KEY.get(key);
            if (existing != null) {
                existing.refs++;
                return existing.sheet;
            }
        }
        SpriteSheet created = factory.create();
        if (created == null || created.bitmap == null) {
            throw new IllegalArgumentException("Failed to decode sprite sheet");
        }
        synchronized (LOCK) {
            Entry existing = BY_KEY.get(key);
            if (existing != null) {
                created.recycle();
                existing.refs++;
                return existing.sheet;
            }
            Entry entry = new Entry(key, created);
            entry.refs = 1;
            BY_KEY.put(key, entry);
            BY_SHEET.put(created, entry);
            return created;
        }
    }

    /**
     * Drop one pin. Recycles and evicts the sheet when the count reaches zero.
     * Unknown or already-evicted sheets are ignored.
     */
    static void unpin(SpriteSheet sheet) {
        if (sheet == null) {
            return;
        }
        synchronized (LOCK) {
            Entry entry = BY_SHEET.get(sheet);
            if (entry == null) {
                return;
            }
            entry.refs--;
            if (entry.refs > 0) {
                return;
            }
            BY_SHEET.remove(sheet);
            BY_KEY.remove(entry.key);
            entry.sheet.recycle();
        }
    }

    private static String toHex(byte[] digest) {
        char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int v = digest[i] & 0xff;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 0x0f];
        }
        return new String(out);
    }
}
