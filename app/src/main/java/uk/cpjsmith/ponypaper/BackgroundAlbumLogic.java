package uk.cpjsmith.ponypaper;

import java.util.List;

/**
 * Pure helpers for the saved-background album and screensaver cycle.
 * Android file/pref I/O lives in {@link BackgroundAlbum}.
 */
public final class BackgroundAlbumLogic {

    public static final int HASH_LENGTH = 40;
    public static final int MAX_MEMBERS = 20;
    public static final long MAX_TOTAL_BYTES = 200L * 1024L * 1024L;
    public static final long MAX_MEMBER_BYTES = 64L * 1024L * 1024L;
    public static final int DEFAULT_INTERVAL_MINUTES = 10;
    public static final int MIN_INTERVAL_MINUTES = 5;
    public static final int MAX_INTERVAL_MINUTES = 30;

    private BackgroundAlbumLogic() {}

    /**
     * SHA-1 hex from {@link CustomStorage} ingest (lowercase, 40 chars).
     * Rejects anything that could be a path element.
     */
    public static boolean isSafeHash(String hash) {
        if (hash == null || hash.length() != HASH_LENGTH) return false;
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) return false;
        }
        return true;
    }

    /** True when the screen saver should walk album files instead of the live slot. */
    public static boolean shouldCycle(boolean prefEnabled, int presentCount) {
        return prefEnabled && presentCount >= 2;
    }

    /**
     * Index of {@code wallpaperHash} in {@code hashes}, or 0 when missing/empty.
     * {@code -1} when the list is empty.
     */
    public static int startingIndex(List<String> hashes, String wallpaperHash) {
        if (hashes == null || hashes.isEmpty()) return -1;
        if (wallpaperHash != null) {
            for (int i = 0; i < hashes.size(); i++) {
                if (wallpaperHash.equals(hashes.get(i))) return i;
            }
        }
        return 0;
    }

    public static String startingHash(List<String> hashes, String wallpaperHash) {
        int i = startingIndex(hashes, wallpaperHash);
        if (i < 0) return null;
        return hashes.get(i);
    }

    /**
     * File to show while cycling. Keep {@code current} across herd rebuilds
     * (mix shuffle) so the album timer is not reset to the wallpaper slot.
     */
    public static String cycleFileHash(List<String> hashes, String current,
            String wallpaperHash) {
        if (current != null && hashes != null) {
            for (int i = 0; i < hashes.size(); i++) {
                if (current.equals(hashes.get(i))) return current;
            }
        }
        return startingHash(hashes, wallpaperHash);
    }

    /**
     * Next hash after {@code current} in list order, wrapping. If {@code current}
     * is missing, returns the first member. Null when the list is empty.
     */
    public static String nextHash(List<String> hashes, String current) {
        if (hashes == null || hashes.isEmpty()) return null;
        if (hashes.size() == 1) return hashes.get(0);
        int i = -1;
        if (current != null) {
            for (int n = 0; n < hashes.size(); n++) {
                if (current.equals(hashes.get(n))) {
                    i = n;
                    break;
                }
            }
        }
        if (i < 0) return hashes.get(0);
        return hashes.get((i + 1) % hashes.size());
    }

    /**
     * Cycle interval from a minutes preference string. Invalid values become
     * {@link #DEFAULT_INTERVAL_MINUTES}. Clamped to
     * [{@link #MIN_INTERVAL_MINUTES}, {@link #MAX_INTERVAL_MINUTES}].
     */
    public static long intervalMs(String raw) {
        int minutes = DEFAULT_INTERVAL_MINUTES;
        if (raw != null) {
            try {
                minutes = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                minutes = DEFAULT_INTERVAL_MINUTES;
            }
        }
        if (minutes < MIN_INTERVAL_MINUTES) minutes = MIN_INTERVAL_MINUTES;
        if (minutes > MAX_INTERVAL_MINUTES) minutes = MAX_INTERVAL_MINUTES;
        return minutes * 60L * 1000L;
    }
}
