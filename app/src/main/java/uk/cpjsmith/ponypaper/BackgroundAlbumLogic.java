package uk.cpjsmith.ponypaper;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

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

    /** Subfolder of the linked library tree that holds album images. */
    public static final String ALBUM_DIR_NAME = "album";
    /** SAF display-name prefix; providers often append {@code .txt}. */
    public static final String ALBUM_MARKER_PREFIX = "album-images-go-here";
    public static final String ALBUM_MARKER_LIBRARY_NAME = ALBUM_MARKER_PREFIX + ".txt";
    public static final int MAX_ALBUM_FILE_NAME = 120;

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

    /**
     * True when {@code extraBytes} can join {@code count} members totalling
     * {@code totalBytes} without exceeding the album caps.
     */
    public static boolean canFit(int count, long totalBytes, long extraBytes) {
        if (extraBytes < 0 || extraBytes > MAX_MEMBER_BYTES) return false;
        if (count < 0 || totalBytes < 0) return false;
        return count < MAX_MEMBERS && totalBytes + extraBytes <= MAX_TOTAL_BYTES;
    }

    /**
     * {@code custom-ponies-go-here.txt}-style marker in {@link #ALBUM_DIR_NAME},
     * including SAF {@code (N)} copies.
     */
    public static boolean isAlbumMarkerName(String displayName) {
        return albumMarkerRank(displayName) >= 0;
    }

    /**
     * 0 = canonical {@code .txt}, 1 = prefix only, 2 = uniquified copy,
     * {@code -1} = not a marker.
     */
    public static int albumMarkerRank(String displayName) {
        if (displayName == null) return -1;
        String name = displayName.trim().toLowerCase(Locale.US);
        if (!name.startsWith(ALBUM_MARKER_PREFIX)) return -1;
        if (ALBUM_MARKER_LIBRARY_NAME.equals(name)) return 0;
        if (ALBUM_MARKER_PREFIX.equals(name)) return 1;
        String rest = name.substring(ALBUM_MARKER_PREFIX.length());
        if (rest.startsWith(".txt")) {
            rest = rest.substring(4);
        } else if (rest.endsWith(".txt")) {
            rest = rest.substring(0, rest.length() - 4);
        }
        rest = rest.trim();
        if (rest.length() == 0) return 1;
        if (rest.charAt(0) != '(' || rest.charAt(rest.length() - 1) != ')') return -1;
        String inner = rest.substring(1, rest.length() - 1);
        if (inner.length() == 0) return -1;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (ch < '0' || ch > '9') return -1;
        }
        return 2;
    }

    public static boolean isImageExtension(String ext) {
        if (ext == null || ext.length() == 0) return false;
        String e = ext.toLowerCase(Locale.US);
        if (e.charAt(0) != '.') e = "." + e;
        return e.equals(".jpg") || e.equals(".jpeg") || e.equals(".jpe") || e.equals(".jfif")
                || e.equals(".png") || e.equals(".webp") || e.equals(".gif") || e.equals(".bmp");
    }

    public static boolean isImageFileName(String name) {
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) return false;
        return isImageExtension(name.substring(dot));
    }

    /**
     * Safe basename for an album folder file. Path segments stripped, marker
     * names rejected, only {@code [A-Za-z0-9._-]} kept, image extension required.
     *
     * @return sanitized name, or null if nothing usable remains
     */
    public static String sanitizeAlbumFileName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return null;
        }
        if (isAlbumMarkerName(name)) return null;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) return null;
        String ext = name.substring(dot).toLowerCase(Locale.US);
        if (!isImageExtension(ext)) return null;
        String base = name.substring(0, dot);
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            if ((ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '_' || ch == '-') {
                sb.append(ch);
            } else {
                sb.append('_');
            }
        }
        String safeBase = sb.toString();
        while (safeBase.startsWith(".")) {
            safeBase = safeBase.substring(1);
        }
        if (safeBase.isEmpty() || safeBase.equals(".") || safeBase.equals("..")) {
            return null;
        }
        int maxBase = MAX_ALBUM_FILE_NAME - ext.length();
        if (maxBase < 1) return null;
        if (safeBase.length() > maxBase) {
            safeBase = safeBase.substring(0, maxBase);
        }
        return safeBase + ext;
    }

    /**
     * {@code desired} if unused, otherwise {@code base-2.ext}, {@code base-3.ext},
     * … Comparison is case-insensitive.
     */
    public static String uniqueAlbumFileName(String desired, Collection<String> taken) {
        String sanitized = sanitizeAlbumFileName(desired);
        if (sanitized == null) return null;
        if (!containsIgnoreCase(taken, sanitized)) return sanitized;
        int dot = sanitized.lastIndexOf('.');
        String base = sanitized.substring(0, dot);
        String ext = sanitized.substring(dot);
        for (int n = 2; n < 1000; n++) {
            String candidate = base + "-" + n + ext;
            if (candidate.length() > MAX_ALBUM_FILE_NAME) {
                int keep = MAX_ALBUM_FILE_NAME - ext.length() - ("-" + n).length();
                if (keep < 1) return null;
                candidate = base.substring(0, Math.min(keep, base.length())) + "-" + n + ext;
            }
            if (!containsIgnoreCase(taken, candidate)) return candidate;
        }
        return null;
    }

    public static boolean containsIgnoreCase(Collection<String> names, String value) {
        if (names == null || value == null) return false;
        for (String name : names) {
            if (name != null && name.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    /**
     * Image extension (including the dot) from a file prefix, or null.
     */
    public static String imageExtensionFromPrefix(byte[] prefix) {
        if (prefix == null || prefix.length < 3) return null;
        if ((prefix[0] & 0xff) == 0xff && (prefix[1] & 0xff) == 0xd8 && (prefix[2] & 0xff) == 0xff) {
            return ".jpg";
        }
        if (prefix.length >= 8
                && (prefix[0] & 0xff) == 0x89
                && prefix[1] == 0x50 && prefix[2] == 0x4e && prefix[3] == 0x47
                && prefix[4] == 0x0d && prefix[5] == 0x0a
                && prefix[6] == 0x1a && prefix[7] == 0x0a) {
            return ".png";
        }
        if (prefix[0] == 'G' && prefix[1] == 'I' && prefix[2] == 'F') {
            return ".gif";
        }
        if (prefix[0] == 'B' && prefix[1] == 'M') {
            return ".bmp";
        }
        if (prefix.length >= 12
                && prefix[0] == 'R' && prefix[1] == 'I' && prefix[2] == 'F' && prefix[3] == 'F'
                && prefix[8] == 'W' && prefix[9] == 'E' && prefix[10] == 'B' && prefix[11] == 'P') {
            return ".webp";
        }
        return null;
    }

    public static String mimeForAlbumFileName(String name) {
        if (name == null) return "application/octet-stream";
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = name.substring(dot).toLowerCase(Locale.US);
        if (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".jpe") || ext.equals(".jfif")) {
            return "image/jpeg";
        }
        if (ext.equals(".png")) return "image/png";
        if (ext.equals(".webp")) return "image/webp";
        if (ext.equals(".gif")) return "image/gif";
        if (ext.equals(".bmp")) return "image/bmp";
        return "application/octet-stream";
    }
}
