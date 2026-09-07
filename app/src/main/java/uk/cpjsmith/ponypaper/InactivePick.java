package uk.cpjsmith.ponypaper;

import java.util.Random;

/**
 * Chooses an inactive-pool index for a live swap or replacement prefetch.
 * Prefers {@code waifuKey} matches when any remain; otherwise uniform among
 * slots not marked {@code skip}.
 */
public final class InactivePick {

    private InactivePick() {}

    /**
     * @param size     inactive slot count
     * @param prefKeys {@code prefKeys[i]} is the pony key at slot {@code i}
     * @param skip     when non-null, {@code skip[i]} excludes that slot
     * @param waifuKey favorite key, or empty/null for none
     * @return a slot in {@code [0, size)}, or {@code -1} if none remain
     */
    public static int index(int size, String[] prefKeys, boolean[] skip, String waifuKey,
            Random random) {
        if (size <= 0 || random == null) {
            return -1;
        }
        String waifu = waifuKey != null ? waifuKey : "";
        if (waifu.length() > 0) {
            int matchCount = 0;
            for (int i = 0; i < size; i++) {
                if (isSkip(skip, i)) {
                    continue;
                }
                if (waifu.equals(keyAt(prefKeys, i))) {
                    matchCount++;
                }
            }
            if (matchCount > 0) {
                return nthEligible(size, skip, prefKeys, waifu, random.nextInt(matchCount));
            }
        }
        int eligible = 0;
        for (int i = 0; i < size; i++) {
            if (!isSkip(skip, i)) {
                eligible++;
            }
        }
        if (eligible == 0) {
            return -1;
        }
        return nthEligible(size, skip, null, null, random.nextInt(eligible));
    }

    private static int nthEligible(int size, boolean[] skip, String[] prefKeys,
            String requireKey, int nth) {
        for (int i = 0; i < size; i++) {
            if (isSkip(skip, i)) {
                continue;
            }
            if (requireKey != null && !requireKey.equals(keyAt(prefKeys, i))) {
                continue;
            }
            if (nth == 0) {
                return i;
            }
            nth--;
        }
        return -1;
    }

    private static boolean isSkip(boolean[] skip, int i) {
        return skip != null && i < skip.length && skip[i];
    }

    private static String keyAt(String[] prefKeys, int i) {
        if (prefKeys == null || i < 0 || i >= prefKeys.length) {
            return "";
        }
        String key = prefKeys[i];
        return key != null ? key : "";
    }
}
