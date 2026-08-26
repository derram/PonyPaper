package uk.cpjsmith.ponypaper;

import android.content.res.Resources;
import android.graphics.Bitmap;
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
 *
 * <p>On API 26+, published sheets prefer a HARDWARE bitmap and drop the CPU
 * copy while no host holds a {@link #addCpuDemand} (software-canvas fallback).
 * Demand re-decodes CPU pixels for pinned sheets via the retained factory;
 * releasing the last demand uploads again and drops CPU copies.
 */
final class SpriteCache {

    private static final String TAG = "PonyPaper";

    private static final Object LOCK = new Object();
    private static final HashMap<String, Entry> BY_KEY = new HashMap<String, Entry>();
    private static final IdentityHashMap<SpriteSheet, Entry> BY_SHEET =
            new IdentityHashMap<SpriteSheet, Entry>();
    private static final HashMap<String, InFlight> IN_FLIGHT = new HashMap<String, InFlight>();
    private static final ArrayList<Runnable> CPU_WAITERS = new ArrayList<Runnable>();

    /** Hosts that need software-canvas blit (refcount). */
    private static int cpuDemand = 0;
    private static boolean cpuEnsureInFlight = false;

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
        final SheetFactory factory;
        final SpriteSheet sheet;
        int refs;

        Entry(String key, SheetFactory factory, SpriteSheet sheet) {
            this.key = key;
            this.factory = factory;
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

    private static final class CpuReload {
        final String key;
        final SheetFactory factory;
        final SpriteSheet sheet;

        CpuReload(String key, SheetFactory factory, SpriteSheet sheet) {
            this.key = key;
            this.factory = factory;
            this.sheet = sheet;
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

    /**
     * Declare that a host needs CPU-resident sprite pixels (software
     * {@code lockCanvas}). Re-decodes any HARDWARE-only pinned sheets on the
     * decode thread. {@code whenReady} runs on that thread when every published
     * sheet has a CPU bitmap (or immediately if already satisfied).
     */
    static void addCpuDemand(Runnable whenReady) {
        final ArrayList<Runnable> readyNow;
        synchronized (LOCK) {
            cpuDemand++;
            if (whenReady != null) {
                CPU_WAITERS.add(whenReady);
            }
            if (allHaveCpuLocked()) {
                readyNow = takeCpuWaitersLocked();
            } else {
                readyNow = null;
                if (!cpuEnsureInFlight) {
                    cpuEnsureInFlight = true;
                    DECODE_EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            ensureCpuBitmapsWork();
                        }
                    });
                }
            }
        }
        runAll(readyNow);
    }

    /**
     * Drop one software-canvas CPU demand. When the count reaches zero, upload
     * any missing HARDWARE copies and recycle CPU bitmaps again.
     */
    static void removeCpuDemand() {
        final ArrayList<SpriteSheet> toTrim;
        synchronized (LOCK) {
            if (cpuDemand > 0) {
                cpuDemand--;
            }
            if (cpuDemand != 0) {
                return;
            }
            toTrim = new ArrayList<SpriteSheet>(BY_SHEET.size());
            for (Entry entry : BY_KEY.values()) {
                toTrim.add(entry.sheet);
            }
        }
        for (int i = 0; i < toTrim.size(); i++) {
            SpriteSheet sheet = toTrim.get(i);
            if (sheet == null) {
                continue;
            }
            if (!sheet.hasHardwareBitmap() && sheet.hasCpuBitmap()) {
                sheet.uploadToGpu();
            }
            synchronized (LOCK) {
                if (cpuDemand > 0) {
                    return;
                }
                if (BY_SHEET.get(sheet) == null) {
                    continue;
                }
                if (sheet.hasHardwareBitmap()) {
                    sheet.recycleCpuBitmap();
                }
            }
        }
    }

    private static void finishDecode(InFlight inflight) {
        SpriteSheet created = null;
        RuntimeException err = null;
        try {
            created = inflight.factory.create();
            if (created == null || !created.hasCpuBitmap()) {
                err = new IllegalArgumentException("Failed to decode sprite sheet");
            } else {
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
            // Prefer HW-only when no software host needs CPU pixels.
            if (cpuDemand == 0 && created.hasHardwareBitmap()) {
                created.recycleCpuBitmap();
            }
            Entry entry = new Entry(inflight.key, inflight.factory, created);
            entry.refs = inflight.refs;
            BY_KEY.put(inflight.key, entry);
            BY_SHEET.put(created, entry);
            for (int i = 0; i < inflight.waiters.size(); i++) {
                inflight.waiters.get(i).sheet = created;
            }
        }
    }

    private static void ensureCpuBitmapsWork() {
        final ArrayList<CpuReload> jobs = new ArrayList<CpuReload>();
        synchronized (LOCK) {
            for (Entry entry : BY_KEY.values()) {
                if (!entry.sheet.hasCpuBitmap()) {
                    jobs.add(new CpuReload(entry.key, entry.factory, entry.sheet));
                }
            }
        }
        for (int i = 0; i < jobs.size(); i++) {
            CpuReload job = jobs.get(i);
            Bitmap cpu = null;
            try {
                SpriteSheet tmp = job.factory.create();
                if (tmp != null) {
                    cpu = tmp.detachCpuBitmap();
                    tmp.recycle();
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "CPU sprite reload failed for " + job.key, e);
                if (cpu != null && !cpu.isRecycled()) {
                    cpu.recycle();
                }
                cpu = null;
            }
            synchronized (LOCK) {
                Entry live = BY_KEY.get(job.key);
                if (live == null || live.sheet != job.sheet) {
                    if (cpu != null && !cpu.isRecycled()) {
                        cpu.recycle();
                    }
                    continue;
                }
                if (cpuDemand <= 0) {
                    if (cpu != null && !cpu.isRecycled()) {
                        cpu.recycle();
                    }
                    continue;
                }
                if (live.sheet.hasCpuBitmap()) {
                    if (cpu != null && !cpu.isRecycled()) {
                        cpu.recycle();
                    }
                    continue;
                }
                if (cpu != null) {
                    live.sheet.installCpuBitmap(cpu);
                }
            }
        }

        final ArrayList<Runnable> ready;
        synchronized (LOCK) {
            cpuEnsureInFlight = false;
            // finishDecode keeps CPU while cpuDemand > 0, so one pass covers
            // published sheets. Failed reloads stay undrawable on software;
            // still notify waiters so the host can redraw.
            ready = takeCpuWaitersLocked();
        }
        runAll(ready);
    }

    private static boolean allHaveCpuLocked() {
        for (Entry entry : BY_KEY.values()) {
            if (!entry.sheet.hasCpuBitmap()) {
                return false;
            }
        }
        return true;
    }

    private static ArrayList<Runnable> takeCpuWaitersLocked() {
        if (CPU_WAITERS.isEmpty()) {
            return null;
        }
        ArrayList<Runnable> out = new ArrayList<Runnable>(CPU_WAITERS);
        CPU_WAITERS.clear();
        return out;
    }

    private static void runAll(ArrayList<Runnable> runnables) {
        if (runnables == null) {
            return;
        }
        for (int i = 0; i < runnables.size(); i++) {
            Runnable r = runnables.get(i);
            if (r != null) {
                r.run();
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
