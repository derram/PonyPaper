package uk.cpjsmith.ponypaper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * World Flow unique-key window. The full mix stays the checkbox/library
 * roster; live replacements and prefetch pick only from this cast so the
 * unpinned sprite LRU can stay hot. When the mix is larger than
 * {@link #MAX_KEYS}, one reservoir character is admitted every
 * {@link #DRIP_INTERVAL_MS}, evicting the inactive (non-prefetched, non-waifu)
 * key that last appeared on-screen least recently.
 */
public final class WorldFlowCast {

    public static final int MAX_KEYS = 20;
    public static final long DRIP_INTERVAL_MS = 10L * 60L * 1000L;

    /** One drip: {@code evicted} leaves the window, {@code admitted} enters. */
    public static final class Drip {
        public final String evicted;
        public final String admitted;

        Drip(String evicted, String admitted) {
            this.evicted = evicted;
            this.admitted = admitted;
        }
    }

    private final int mixSize;
    private final long dripIntervalMs;
    private final LinkedHashSet<String> cast = new LinkedHashSet<String>();
    private final ArrayList<String> reservoir = new ArrayList<String>();
    private final HashMap<String, Long> lastOnScreen = new HashMap<String, Long>();
    private long elapsedMs;
    private long nextDripElapsed;

    private WorldFlowCast(int mixSize, long dripIntervalMs) {
        this.mixSize = mixSize;
        this.dripIntervalMs = dripIntervalMs;
        this.nextDripElapsed = dripIntervalMs;
    }

    /**
     * Sample a World Flow cast from {@code mixKeys}. Always includes
     * {@code waifuKey} when that key is in the mix.
     */
    public static WorldFlowCast open(List<String> mixKeys, String waifuKey,
            Random random) {
        return open(mixKeys, waifuKey, random, MAX_KEYS, DRIP_INTERVAL_MS);
    }

    /**
     * Same as {@link #open(List, String, Random)} with an explicit window and
     * drip interval (tests).
     */
    public static WorldFlowCast open(List<String> mixKeys, String waifuKey,
            Random random, int maxKeys, long dripIntervalMs) {
        int cap = maxKeys > 0 ? maxKeys : MAX_KEYS;
        long interval = dripIntervalMs > 0 ? dripIntervalMs : DRIP_INTERVAL_MS;
        ArrayList<String> mix = uniqueKeys(mixKeys);
        WorldFlowCast opened = new WorldFlowCast(mix.size(), interval);
        Random rng = random != null ? random : new Random();
        String waifu = waifuKey != null ? waifuKey : "";
        if (mix.size() <= cap) {
            opened.cast.addAll(mix);
            return opened;
        }
        ArrayList<String> rest = new ArrayList<String>(mix.size());
        boolean waifuInMix = waifu.length() > 0 && mix.contains(waifu);
        for (int i = 0; i < mix.size(); i++) {
            String key = mix.get(i);
            if (waifuInMix && waifu.equals(key)) {
                continue;
            }
            rest.add(key);
        }
        shuffle(rest, rng);
        if (waifuInMix) {
            opened.cast.add(waifu);
        }
        int take = 0;
        while (opened.cast.size() < cap && take < rest.size()) {
            opened.cast.add(rest.get(take));
            take++;
        }
        for (int i = take; i < rest.size(); i++) {
            opened.reservoir.add(rest.get(i));
        }
        return opened;
    }

    /** Unique mix size at {@link #open} (empty names dropped). */
    public int mixSize() {
        return mixSize;
    }

    /** Keys currently in the live window. */
    public int size() {
        return cast.size();
    }

    /** Mix members waiting to drip in. */
    public int reservoirSize() {
        return reservoir.size();
    }

    public boolean contains(String key) {
        return key != null && cast.contains(key);
    }

    /**
     * Current window keys, in sample order (waifu first when included).
     * Safe to pass to {@link InactiveRoster#setAll}.
     */
    public ArrayList<String> castKeys() {
        return new ArrayList<String>(cast);
    }

    /** Animation-clock elapsed since open. */
    public long elapsedMs() {
        return elapsedMs;
    }

    /** Advance the drip clock. Negative {@code deltaMs} is ignored. */
    public void advance(long deltaMs) {
        if (deltaMs > 0L) {
            elapsedMs += deltaMs;
        }
    }

    /**
     * Record that {@code key} was on-screen at the current elapsed time.
     * Ignored when the key is not in the cast.
     */
    public void noteOnScreen(String key) {
        if (key == null || key.length() == 0 || !cast.contains(key)) {
            return;
        }
        lastOnScreen.put(key, Long.valueOf(elapsedMs));
    }

    /**
     * Admit one reservoir key if the drip interval has elapsed and an
     * inactive, non-held, non-waifu cast member can leave. At most one swap
     * per call, even after a long pause. Does not advance the interval when
     * no victim is available (retry next frame).
     *
     * @param inactive  current inactive roster (subset of the cast)
     * @param heldKeys  prefetched keys that must stay in the window
     * @param waifuKey  favorite key, never evicted
     * @return a swap, or {@code null}
     */
    public Drip tryDrip(InactiveRoster inactive, Collection<String> heldKeys,
            String waifuKey, Random random) {
        if (reservoir.isEmpty() || random == null || elapsedMs < nextDripElapsed) {
            return null;
        }
        String victim = pickVictim(inactive, heldKeys, waifuKey);
        if (victim == null) {
            return null;
        }
        int incomingIdx = random.nextInt(reservoir.size());
        String admitted = reservoir.remove(incomingIdx);
        cast.remove(victim);
        lastOnScreen.remove(victim);
        cast.add(admitted);
        lastOnScreen.put(admitted, Long.valueOf(elapsedMs));
        reservoir.add(victim);
        nextDripElapsed = elapsedMs + dripIntervalMs;
        return new Drip(victim, admitted);
    }

    private String pickVictim(InactiveRoster inactive, Collection<String> heldKeys,
            String waifuKey) {
        if (inactive == null || inactive.isEmpty()) {
            return null;
        }
        String waifu = waifuKey != null ? waifuKey : "";
        String bestSeen = null;
        long bestT = Long.MAX_VALUE;
        String firstUnseen = null;
        int n = inactive.size();
        for (int i = 0; i < n; i++) {
            String key = inactive.get(i);
            if (key == null || key.length() == 0) {
                continue;
            }
            if (waifu.length() > 0 && waifu.equals(key)) {
                continue;
            }
            if (heldKeys != null && heldKeys.contains(key)) {
                continue;
            }
            if (!cast.contains(key)) {
                continue;
            }
            Long t = lastOnScreen.get(key);
            if (t == null) {
                if (firstUnseen == null) {
                    firstUnseen = key;
                }
                continue;
            }
            long at = t.longValue();
            if (bestSeen == null || at < bestT) {
                bestSeen = key;
                bestT = at;
            }
        }
        return bestSeen != null ? bestSeen : firstUnseen;
    }

    private static ArrayList<String> uniqueKeys(List<String> in) {
        ArrayList<String> out = new ArrayList<String>();
        if (in == null) {
            return out;
        }
        HashSet<String> seen = new HashSet<String>();
        for (int i = 0; i < in.size(); i++) {
            String key = in.get(i);
            if (key == null || key.length() == 0) {
                continue;
            }
            if (seen.add(key)) {
                out.add(key);
            }
        }
        return out;
    }

    private static void shuffle(ArrayList<String> list, Random random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            String tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
