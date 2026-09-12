package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Saved copies of previously selected background images.
 *
 * <p>The live wallpaper slot remains {@link CustomStorage#BACKGROUND_NAME}.
 * Album files live in {@code backgrounds/<sha1>} and are never written back
 * into that slot by the screensaver cycle. Library zip / SAF still export
 * only the live slot.
 *
 * <p>JSON: {@link #PREF_JSON} {@code {members:[{hash,added}]}} in add order.
 */
final class BackgroundAlbum {

    static final String PREF_JSON = "pref_backgrounds_json";
    static final String DIR_NAME = "backgrounds";

    private static final Object LOCK = new Object();

    static final class Member {
        final String hash;
        final long addedMs;

        Member(String hash, long addedMs) {
            this.hash = hash;
            this.addedMs = addedMs;
        }
    }

    private BackgroundAlbum() {}

    static File albumDir(Context context) throws IOException {
        File parent = CustomStorage.localDir(context);
        if (parent == null) {
            throw new IOException("App storage is not available on this device right now.");
        }
        File dir = new File(parent, DIR_NAME);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create backgrounds folder");
        }
        File canonical = dir.getCanonicalFile();
        if (!canonical.getParentFile().equals(parent.getCanonicalFile())) {
            throw new IOException("Refusing to write outside app files directory");
        }
        return dir;
    }

    static File fileForHash(Context context, String hash) throws IOException {
        if (!BackgroundAlbumLogic.isSafeHash(hash)) {
            throw new IOException("Invalid background id");
        }
        File dir = albumDir(context);
        File dest = new File(dir, hash);
        if (!dest.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())) {
            throw new IOException("Refusing to write outside backgrounds folder");
        }
        return dest;
    }

    static File fileForHashOrNull(Context context, String hash) {
        try {
            File f = fileForHash(context, hash);
            return f.isFile() && f.length() > 0 ? f : null;
        } catch (IOException e) {
            return null;
        }
    }

    static List<Member> load(SharedPreferences prefs) {
        ArrayList<Member> out = new ArrayList<Member>();
        if (prefs == null) return out;
        String raw = prefs.getString(PREF_JSON, null);
        if (raw == null || raw.length() == 0) return out;
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray arr = root.optJSONArray("members");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String hash = o.optString("hash", "");
                if (!BackgroundAlbumLogic.isSafeHash(hash)) continue;
                long added = o.optLong("added", 0L);
                boolean dup = false;
                for (int j = 0; j < out.size(); j++) {
                    if (hash.equals(out.get(j).hash)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) out.add(new Member(hash, added));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void save(SharedPreferences prefs, List<Member> members) {
        if (prefs == null) return;
        JSONArray arr = new JSONArray();
        if (members != null) {
            for (int i = 0; i < members.size(); i++) {
                Member m = members.get(i);
                if (m == null || !BackgroundAlbumLogic.isSafeHash(m.hash)) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("hash", m.hash);
                    o.put("added", m.addedMs);
                    arr.put(o);
                } catch (Exception ignored) {
                }
            }
        }
        try {
            JSONObject root = new JSONObject();
            root.put("members", arr);
            prefs.edit().putString(PREF_JSON, root.toString()).commit();
        } catch (Exception ignored) {
        }
    }

    /**
     * Hashes whose album files still exist, in JSON order.
     */
    static ArrayList<String> presentHashes(Context context) {
        ArrayList<String> out = new ArrayList<String>();
        if (context == null) return out;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        List<Member> members = load(prefs);
        for (int i = 0; i < members.size(); i++) {
            File f = fileForHashOrNull(context, members.get(i).hash);
            if (f != null) out.add(members.get(i).hash);
        }
        return out;
    }

    static int presentCount(Context context) {
        return presentHashes(context).size();
    }

    static boolean cyclePrefEnabled(SharedPreferences prefs) {
        return prefs != null
                && prefs.getBoolean(PonySceneController.PREF_DREAM_CYCLE_BACKGROUNDS, false);
    }

    static boolean shouldCycle(Context context, SharedPreferences prefs) {
        return BackgroundAlbumLogic.shouldCycle(cyclePrefEnabled(prefs), presentCount(context));
    }

    static long intervalMs(SharedPreferences prefs) {
        String raw = prefs != null
                ? prefs.getString(PonySceneController.PREF_DREAM_CYCLE_INTERVAL, null)
                : null;
        return BackgroundAlbumLogic.intervalMs(raw);
    }

    static String wallpaperHash(SharedPreferences prefs) {
        if (prefs == null) return null;
        String hash = prefs.getString("pref_select_background", null);
        return BackgroundAlbumLogic.isSafeHash(hash) ? hash : null;
    }

    /**
     * Copy the live {@code background} file into the album under {@code hash}.
     * No-op when already present. Evicts oldest other members when over cap.
     *
     * @return true when the hash is in the album afterwards
     */
    static boolean addFromLive(Context context, String hash) {
        synchronized (LOCK) {
            if (context == null || !BackgroundAlbumLogic.isSafeHash(hash)) return false;
            File live;
            try {
                live = CustomStorage.localFile(context, CustomStorage.BACKGROUND_NAME);
            } catch (IOException e) {
                return false;
            }
            if (!live.isFile() || live.length() <= 0) return false;
            if (live.length() > BackgroundAlbumLogic.MAX_MEMBER_BYTES) return false;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            ArrayList<Member> members = new ArrayList<Member>(load(prefs));
            File dest;
            try {
                dest = fileForHash(context, hash);
            } catch (IOException e) {
                return false;
            }
            if (dest.isFile() && dest.length() > 0) {
                ensureMember(members, hash);
                save(prefs, members);
                return true;
            }
            evictUntilFit(context, members, live.length(), hash);
            long total = albumBytes(context, members);
            if (members.size() >= BackgroundAlbumLogic.MAX_MEMBERS
                    || total + live.length() > BackgroundAlbumLogic.MAX_TOTAL_BYTES) {
                return false;
            }
            try {
                CustomStorage.copyFile(live, dest);
            } catch (IOException e) {
                dest.delete();
                return false;
            }
            ensureMember(members, hash);
            save(prefs, members);
            return true;
        }
    }

    /**
     * If the album is empty but a live background exists, copy it in so an
     * existing install gets a first saved image without re-picking.
     */
    static void seedFromLive(Context context) {
        synchronized (LOCK) {
            if (context == null) return;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!presentHashes(context).isEmpty()) return;
            if (!CustomStorage.hasLocalBackground(context)) return;
            String hash = wallpaperHash(prefs);
            if (hash == null) {
                try {
                    File live = CustomStorage.localFile(context, CustomStorage.BACKGROUND_NAME);
                    hash = CustomStorage.sha1OfFile(live);
                } catch (IOException e) {
                    return;
                }
                if (BackgroundAlbumLogic.isSafeHash(hash)) {
                    prefs.edit().putString("pref_select_background", hash).commit();
                }
            }
            addFromLive(context, hash);
        }
    }

    /**
     * Copy an album member onto the live wallpaper slot. Does not remove it
     * from the album. Writes {@code pref_select_background} so hosts reload.
     */
    static void applyAsWallpaper(Context context, String hash) throws IOException {
        synchronized (LOCK) {
            File src = fileForHash(context, hash);
            if (!src.isFile() || src.length() <= 0) {
                throw new IOException("That saved background is missing.");
            }
            File live = CustomStorage.localFile(context, CustomStorage.BACKGROUND_NAME);
            CustomStorage.copyFile(src, live);
            CustomStorage.writeThroughToLibrary(context, live);
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString("pref_select_background", hash)
                    .commit();
        }
    }

    static boolean remove(Context context, String hash) {
        synchronized (LOCK) {
            if (context == null || !BackgroundAlbumLogic.isSafeHash(hash)) return false;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            ArrayList<Member> members = new ArrayList<Member>(load(prefs));
            boolean found = false;
            for (int i = members.size() - 1; i >= 0; i--) {
                if (hash.equals(members.get(i).hash)) {
                    members.remove(i);
                    found = true;
                }
            }
            try {
                File f = fileForHash(context, hash);
                if (f.isFile() && !f.delete()) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
            if (found) save(prefs, members);
            return true;
        }
    }

    static List<Member> membersForUi(Context context) {
        if (context == null) return Collections.emptyList();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        List<Member> stored = load(prefs);
        ArrayList<Member> out = new ArrayList<Member>();
        for (int i = 0; i < stored.size(); i++) {
            Member m = stored.get(i);
            if (fileForHashOrNull(context, m.hash) != null) out.add(m);
        }
        return out;
    }

    private static void ensureMember(ArrayList<Member> members, String hash) {
        for (int i = 0; i < members.size(); i++) {
            if (hash.equals(members.get(i).hash)) return;
        }
        members.add(new Member(hash, System.currentTimeMillis()));
    }

    private static long albumBytes(Context context, List<Member> members) {
        long total = 0;
        for (int i = 0; i < members.size(); i++) {
            File f = fileForHashOrNull(context, members.get(i).hash);
            if (f != null) total += f.length();
        }
        return total;
    }

    /**
     * Drop oldest members that are not {@code keepHash} until {@code extraBytes}
     * fits under count and size caps.
     */
    private static void evictUntilFit(Context context, ArrayList<Member> members,
            long extraBytes, String keepHash) {
        int guard = members.size() + 1;
        while (guard-- > 0) {
            int count = members.size();
            long total = albumBytes(context, members);
            if (count < BackgroundAlbumLogic.MAX_MEMBERS
                    && total + extraBytes <= BackgroundAlbumLogic.MAX_TOTAL_BYTES) {
                return;
            }
            int evict = -1;
            for (int i = 0; i < members.size(); i++) {
                if (!members.get(i).hash.equals(keepHash)) {
                    evict = i;
                    break;
                }
            }
            if (evict < 0) return;
            Member gone = members.remove(evict);
            File f = fileForHashOrNull(context, gone.hash);
            if (f != null) f.delete();
        }
    }
}
