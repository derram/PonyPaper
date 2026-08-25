package uk.cpjsmith.ponypaper;

import android.content.res.Resources;
import android.util.Log;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Process-wide refcounted {@link SpriteSheet} store. Wallpaper and dream each
 * keep their own {@link Pony} motion state, but identical sheets (same resource
 * ids, or the same custom image bytes and frame times) share one decoded
 * bitmap. The last {@link Pin#unpin} recycles that bitmap.
 *
 * <p>BitmapFactory work runs on a single background thread. {@link #pin}
 * returns immediately; a cache hit is ready, a miss completes when decode
 * finishes. Concurrent pins of the same key share one decode.
 *
 * <p>Pin/unpin is per owning {@link PonyAction} load/unload. Gait aliases still
 * delegate to the owner so one pony contributes at most one pin per sheet.
 */
final class SpriteCache {

    private static final String TAG = "PonyPaper";

    private static final Object LOCK = new Object();
    private static final HashMap<String, Entry> BY_KEY = new HashMap<String, Entry>();
    private static final IdentityHashMap<SpriteSheet, Entry> BY_SHEET =
            new IdentityHashMap<SpriteSheet, Entry>();
    private static final HashMap<String, InFlight> IN_FLIGHT = new HashMap<String, InFlight>();

    private static final Executor DECODE_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ponypaper-decode");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });

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

    private static final class InFlight {
        final String key;
        final SheetFactory factory;
        final ArrayList<Pin> waiters = new ArrayList<Pin>();
        int refs;

        InFlight(String key, SheetFactory factory) {
            this.key = key;
            this.factory = factory;
        }
    }

    interface SheetFactory {
        SpriteSheet create();
    }

    /**
     * One pin on a cache key. {@link #getSheet()} is null until decode
     * succeeds; {@link #failed()} is true if decode threw or returned nothing.
     * {@link #unpin()} exactly once (safe if decode later fails).
     */
    static final class Pin {
        private final String key;
        private SpriteSheet sheet;
        private RuntimeException error;
        private boolean unpinned;

        private Pin(String key) {
            this.key = key;
        }

        SpriteSheet getSheet() {
            synchronized (LOCK) {
                return sheet;
            }
        }

        boolean failed() {
            synchronized (LOCK) {
                return error != null;
            }
        }

        void unpin() {
            SpriteCache.release(this);
        }
    }

    private SpriteCache() {
    }

    /**
     * Run {@code work} on the process-wide decode thread (serial with
     * {@link BitmapFactory} jobs). Used for herd construction so it never
     * overlaps a decode or the frame callback.
     */
    static void execute(Runnable work) {
        if (work == null) {
            throw new IllegalArgumentException("work");
        }
        DECODE_EXECUTOR.execute(work);
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

    static Pin pinResource(final Resources res, final int drawId, final int timesId) {
        return pin(resourceKey(drawId, timesId), new SheetFactory() {
            @Override
            public SpriteSheet create() {
                return new SpriteSheet(res, drawId, timesId);
            }
        });
    }

    static Pin pinBytes(final byte[] bitmapData, final int[] frameTimes) {
        return pin(bytesKey(bitmapData, frameTimes), new SheetFactory() {
            @Override
            public SpriteSheet create() {
                return new SpriteSheet(bitmapData, frameTimes);
            }
        });
    }

    /**
     * Increment the pin count for {@code key}. A ready sheet is returned at
     * once; otherwise decode is started (or joined) on the decode thread.
     */
    static Pin pin(String key, SheetFactory factory) {
        if (key == null || factory == null) {
            throw new IllegalArgumentException("key/factory");
        }
        final InFlight started;
        final Pin pin = new Pin(key);
        synchronized (LOCK) {
            Entry existing = BY_KEY.get(key);
            if (existing != null) {
                existing.refs++;
                pin.sheet = existing.sheet;
                return pin;
            }
            InFlight inflight = IN_FLIGHT.get(key);
            if (inflight == null) {
                inflight = new InFlight(key, factory);
                IN_FLIGHT.put(key, inflight);
                started = inflight;
            } else {
                started = null;
            }
            inflight.refs++;
            inflight.waiters.add(pin);
        }
        if (started != null) {
            DECODE_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    finishDecode(started);
                }
            });
        }
        return pin;
    }

    private static void finishDecode(InFlight inflight) {
        SpriteSheet created = null;
        RuntimeException err = null;
        try {
            created = inflight.factory.create();
            if (created == null || created.bitmap == null) {
                err = new IllegalArgumentException("Failed to decode sprite sheet");
            } else {
                // CPU bitmap kept for software lockCanvas; HW copy for lockHardwareCanvas.
                created.uploadToGpu();
            }
        } catch (RuntimeException e) {
            err = e;
        }
        synchronized (LOCK) {
            InFlight current = IN_FLIGHT.get(inflight.key);
            if (current != inflight) {
                if (created != null) {
                    created.recycle();
                }
                return;
            }
            IN_FLIGHT.remove(inflight.key);
            if (inflight.refs <= 0) {
                if (created != null) {
                    created.recycle();
                }
                return;
            }
            if (err != null) {
                Log.e(TAG, "Sprite decode failed for " + inflight.key, err);
                for (int i = 0; i < inflight.waiters.size(); i++) {
                    inflight.waiters.get(i).error = err;
                }
                return;
            }
            Entry entry = new Entry(inflight.key, created);
            entry.refs = inflight.refs;
            BY_KEY.put(inflight.key, entry);
            BY_SHEET.put(created, entry);
            for (int i = 0; i < inflight.waiters.size(); i++) {
                inflight.waiters.get(i).sheet = created;
            }
        }
    }

    private static void release(Pin pin) {
        if (pin == null) {
            return;
        }
        synchronized (LOCK) {
            if (pin.unpinned) {
                return;
            }
            pin.unpinned = true;
            if (pin.sheet != null) {
                unpinLocked(pin.sheet);
                return;
            }
            InFlight inflight = IN_FLIGHT.get(pin.key);
            if (inflight == null) {
                return;
            }
            inflight.refs--;
            inflight.waiters.remove(pin);
            if (inflight.refs <= 0) {
                // Decode still running; finishDecode recycles the unused bitmap.
                // Leave the slot so a late finishDecode sees refs == 0.
            }
        }
    }

    /**
     * Drop one pin on a published sheet. Recycles and evicts when the count
     * reaches zero. Unknown or already-evicted sheets are ignored.
     */
    static void unpin(SpriteSheet sheet) {
        if (sheet == null) {
            return;
        }
        synchronized (LOCK) {
            unpinLocked(sheet);
        }
    }

    private static void unpinLocked(SpriteSheet sheet) {
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
