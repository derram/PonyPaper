package uk.cpjsmith.ponypaper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Wander inactive pool as preference keys. {@link Ponies} materializes a
 * {@link Pony} only when taking or prefetching a slot, so a large mix does
 * not construct unused graphs at herd rebuild.
 */
public final class InactiveRoster {

    private final ArrayList<String> keys = new ArrayList<String>();

    public InactiveRoster() {}

    public void setAll(List<String> in) {
        keys.clear();
        if (in == null) {
            return;
        }
        for (int i = 0; i < in.size(); i++) {
            String key = in.get(i);
            if (key != null && key.length() > 0) {
                keys.add(key);
            }
        }
    }

    public void add(String key) {
        if (key != null && key.length() > 0) {
            keys.add(key);
        }
    }

    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public boolean contains(String key) {
        return key != null && keys.contains(key);
    }

    public String get(int index) {
        return keys.get(index);
    }

    public String removeAt(int index) {
        return keys.remove(index);
    }

    public boolean removeKey(String key) {
        return key != null && keys.remove(key);
    }

    /**
     * Pick a slot, skipping keys in {@code skipKeys} (already prefetched).
     *
     * @return index in {@code [0, size)}, or {@code -1} if none remain
     */
    public int pickIndex(Collection<String> skipKeys, String waifuKey, Random random) {
        int n = keys.size();
        if (n == 0) {
            return -1;
        }
        String[] pref = new String[n];
        boolean[] skip = null;
        boolean anySkip = skipKeys != null && !skipKeys.isEmpty();
        if (anySkip) {
            skip = new boolean[n];
        }
        for (int i = 0; i < n; i++) {
            pref[i] = keys.get(i);
            if (skip != null && skipKeys.contains(pref[i])) {
                skip[i] = true;
            }
        }
        return InactivePick.index(n, pref, skip, waifuKey, random);
    }
}
