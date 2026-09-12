package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Saved copies of previously selected background images.
 *
 * <p>The live wallpaper slot remains {@link CustomStorage#BACKGROUND_NAME}.
 * Album files live in {@code backgrounds/<sha1>} and are never written back
 * into that slot by the screensaver cycle. Library zip export still writes
 * only the live slot. The linked library tree may hold copies under
 * {@code album/} for add/remove via the file manager; that folder is not
 * exported.
 *
 * <p>JSON: {@link #PREF_JSON} {@code {members:[{hash,added,name}]}} in add
 * order. {@code name} is the library-folder filename when known.
 */
final class BackgroundAlbum {

    static final String PREF_JSON = "pref_backgrounds_json";
    static final String DIR_NAME = "backgrounds";

    private static final Object LOCK = new Object();

    static final class Member {
        final String hash;
        final long addedMs;
        /** Sanitized album-folder filename, or null for older records. */
        final String name;

        Member(String hash, long addedMs) {
            this(hash, addedMs, null);
        }

        Member(String hash, long addedMs, String name) {
            this.hash = hash;
            this.addedMs = addedMs;
            this.name = name;
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
        HashSet<String> seenNames = new HashSet<String>();
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
                String name = o.optString("name", "");
                if (name == null || name.length() == 0) {
                    name = null;
                } else {
                    name = BackgroundAlbumLogic.sanitizeAlbumFileName(name);
                }
                if (name != null) {
                    if (seenNames.contains(name.toLowerCase(Locale.US))) {
                        name = null;
                    } else {
                        seenNames.add(name.toLowerCase(Locale.US));
                    }
                }
                out.add(new Member(hash, added, name));
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
                    if (m.name != null && BackgroundAlbumLogic.sanitizeAlbumFileName(m.name) != null) {
                        o.put("name", m.name);
                    }
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
     * Hashes whose album files still exist, in JSON order, unique.
     */
    static ArrayList<String> presentHashes(Context context) {
        ArrayList<String> out = new ArrayList<String>();
        if (context == null) return out;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        List<Member> members = load(prefs);
        HashSet<String> seen = new HashSet<String>();
        for (int i = 0; i < members.size(); i++) {
            String hash = members.get(i).hash;
            if (seen.contains(hash)) continue;
            File f = fileForHashOrNull(context, hash);
            if (f != null) {
                seen.add(hash);
                out.add(hash);
            }
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
     * No-op when already present. Evicts oldest other members when over cap
     * and no library folder is linked (linked folders must not lose photos).
     *
     * @return true when the hash is in the album afterwards
     */
    static boolean addFromLive(Context context, String hash) {
        return addFromLive(context, hash, true);
    }

    private static boolean addFromLive(Context context, String hash, boolean writeThrough) {
        String libraryName = null;
        File dest = null;
        boolean added = false;
        boolean copiedNew = false;
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
            try {
                dest = fileForHash(context, hash);
            } catch (IOException e) {
                return false;
            }
            if (dest.isFile() && dest.length() > 0) {
                libraryName = ensureMember(members, hash, nameForFile(members, dest, hash));
                save(prefs, members);
                added = true;
            } else {
                boolean linked = CustomStorage.hasLibraryFolder(context);
                if (!linked) {
                    evictUntilFit(context, members, live.length(), hash);
                }
                long total = albumBytes(context, members);
                if (!BackgroundAlbumLogic.canFit(members.size(), total, live.length())) {
                    return false;
                }
                try {
                    CustomStorage.copyFile(live, dest);
                } catch (IOException e) {
                    dest.delete();
                    return false;
                }
                libraryName = ensureMember(members, hash, nameForFile(members, dest, hash));
                save(prefs, members);
                added = true;
                copiedNew = true;
            }
        }
        if (added && writeThrough && copiedNew && libraryName != null && dest != null) {
            CustomStorage.writeThroughAlbum(context, dest, libraryName);
        }
        return added;
    }

    /**
     * If the album is empty but a live background exists, copy it in so an
     * existing install gets a first saved image without re-picking.
     */
    static void seedFromLive(Context context) {
        String hash = null;
        synchronized (LOCK) {
            if (context == null) return;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!presentHashes(context).isEmpty()) return;
            if (!CustomStorage.hasLocalBackground(context)) return;
            hash = wallpaperHash(prefs);
            if (hash == null) {
                try {
                    File live = CustomStorage.localFile(context, CustomStorage.BACKGROUND_NAME);
                    hash = CustomStorage.sha1OfFile(live);
                } catch (IOException e) {
                    return;
                }
                if (BackgroundAlbumLogic.isSafeHash(hash)) {
                    prefs.edit().putString("pref_select_background", hash).commit();
                } else {
                    return;
                }
            }
        }
        addFromLive(context, hash, false);
    }

    /**
     * Copy an image URI into the album without touching the live wallpaper
     * slot or {@code pref_select_background}. Evicts oldest other members when
     * over cap and no library folder is linked.
     *
     * @return {@code 1} newly stored, {@code 0} already in the album,
     *         {@code -1} rejected (too large or album full after eviction)
     */
    static int addFromUri(Context context, Uri source) throws IOException {
        String libraryName = null;
        File dest = null;
        int result = -1;
        synchronized (LOCK) {
            if (context == null || source == null) {
                throw new IOException("Could not open selected content");
            }
            File dir = albumDir(context);
            File temp = new File(dir, "ingest.tmp");
            if (temp.exists() && !temp.delete()) {
                throw new IOException("Could not prepare album ingest");
            }
            InputStream in = context.getContentResolver().openInputStream(source);
            if (in == null) {
                throw new IOException("Could not open selected content");
            }
            try {
                String hash;
                try {
                    hash = CustomStorage.copyStreamToFile(in, temp,
                            BackgroundAlbumLogic.MAX_MEMBER_BYTES);
                } finally {
                    in.close();
                }
                if (!BackgroundAlbumLogic.isSafeHash(hash)) {
                    throw new IOException("Could not hash image");
                }
                dest = fileForHash(context, hash);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                ArrayList<Member> members = new ArrayList<Member>(load(prefs));
                boolean alreadyMember = indexByHash(members, hash) >= 0;
                String suggested = CustomStorage.queryDisplayName(context, source);
                libraryName = pickName(members, suggested, dest.exists() ? dest : temp, hash);
                if (dest.isFile() && dest.length() > 0) {
                    if (!alreadyMember) {
                        ensureMember(members, hash, libraryName);
                        save(prefs, members);
                        result = 1;
                    } else {
                        ensureMember(members, hash, libraryName);
                        save(prefs, members);
                        result = 0;
                    }
                } else {
                    boolean linked = CustomStorage.hasLibraryFolder(context);
                    if (!linked) {
                        evictUntilFit(context, members, temp.length(), hash);
                    }
                    long total = albumBytes(context, members);
                    if (!BackgroundAlbumLogic.canFit(members.size(), total, temp.length())) {
                        result = -1;
                    } else {
                        if (dest.exists() && !dest.delete()) {
                            throw new IOException("Could not replace album image");
                        }
                        if (!temp.renameTo(dest)) {
                            CustomStorage.copyFile(temp, dest);
                        }
                        ensureMember(members, hash, libraryName);
                        save(prefs, members);
                        result = 1;
                    }
                }
            } finally {
                if (temp.exists()) temp.delete();
            }
        }
        if (result > 0 && libraryName != null && dest != null) {
            CustomStorage.writeThroughAlbum(context, dest, libraryName);
        }
        return result;
    }

    /**
     * Copy an album member onto the live wallpaper slot. Does not remove it
     * from the album. Writes {@code pref_select_background} so hosts reload.
     */
    static void applyAsWallpaper(Context context, String hash) throws IOException {
        File live;
        synchronized (LOCK) {
            File src = fileForHash(context, hash);
            if (!src.isFile() || src.length() <= 0) {
                throw new IOException("That saved background is missing.");
            }
            live = CustomStorage.localFile(context, CustomStorage.BACKGROUND_NAME);
            CustomStorage.copyFile(src, live);
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString("pref_select_background", hash)
                    .commit();
        }
        CustomStorage.writeThroughToLibrary(context, live);
    }

    static boolean remove(Context context, String hash) {
        return remove(context, hash, null);
    }

    /**
     * Remove one album member. When {@code name} is set, only that library
     * filename is dropped; the hash file stays if another member still uses it.
     * When {@code name} is null, every member with {@code hash} is removed.
     */
    static boolean remove(Context context, String hash, String name) {
        ArrayList<String> libraryNames = new ArrayList<String>();
        boolean ok;
        synchronized (LOCK) {
            if (context == null || !BackgroundAlbumLogic.isSafeHash(hash)) return false;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            ArrayList<Member> members = new ArrayList<Member>(load(prefs));
            boolean found = false;
            String wantName = name != null
                    ? BackgroundAlbumLogic.sanitizeAlbumFileName(name) : null;
            for (int i = members.size() - 1; i >= 0; i--) {
                Member m = members.get(i);
                if (!hash.equals(m.hash)) continue;
                if (wantName != null && (m.name == null || !wantName.equalsIgnoreCase(m.name))) {
                    continue;
                }
                if (m.name != null) libraryNames.add(m.name);
                members.remove(i);
                found = true;
            }
            if (indexByHash(members, hash) < 0) {
                try {
                    File f = fileForHash(context, hash);
                    if (f.isFile() && !f.delete()) {
                        return false;
                    }
                } catch (IOException e) {
                    return false;
                }
            }
            if (found) save(prefs, members);
            ok = true;
        }
        for (int i = 0; i < libraryNames.size(); i++) {
            CustomStorage.deleteAlbumLibraryFile(context, libraryNames.get(i));
        }
        return ok;
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

    /**
     * Install a library-folder image already copied to {@code dest} (hash-named).
     * Never evicts other members; extras that do not fit are left in the folder.
     *
     * @return {@code 1} added or hash/name updated, {@code 0} unchanged,
     *         {@code -1} skipped (no room for a new name)
     */
    static int adoptFromLibrary(Context context, String hash, String name, File dest) {
        synchronized (LOCK) {
            if (context == null || !BackgroundAlbumLogic.isSafeHash(hash)) return -1;
            String destName = BackgroundAlbumLogic.sanitizeAlbumFileName(name);
            if (destName == null) return -1;
            if (dest == null || !dest.isFile() || dest.length() <= 0) return -1;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            ArrayList<Member> members = new ArrayList<Member>(load(prefs));
            int named = indexByName(members, destName);
            if (named >= 0) {
                Member old = members.get(named);
                if (hash.equals(old.hash)) return 0;
                members.set(named, new Member(hash, old.addedMs, destName));
                if (indexByHash(members, old.hash) < 0) {
                    File leftover = fileForHashOrNull(context, old.hash);
                    if (leftover != null) leftover.delete();
                }
                save(prefs, members);
                return 1;
            }
            long extra = indexByHash(members, hash) >= 0 ? 0L : dest.length();
            if (!BackgroundAlbumLogic.canFit(members.size(), albumBytes(context, members), extra)) {
                return -1;
            }
            members.add(new Member(hash, System.currentTimeMillis(), destName));
            save(prefs, members);
            return 1;
        }
    }

    /**
     * Drop the member with this library filename. Deletes the hash file when
     * unused. Does not touch the library tree (caller already observed the
     * folder-side delete).
     */
    static boolean dropByLibraryName(Context context, String name) {
        synchronized (LOCK) {
            String destName = BackgroundAlbumLogic.sanitizeAlbumFileName(name);
            if (context == null || destName == null) return false;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            ArrayList<Member> members = new ArrayList<Member>(load(prefs));
            int idx = indexByName(members, destName);
            if (idx < 0) return false;
            Member gone = members.remove(idx);
            if (indexByHash(members, gone.hash) < 0) {
                File f = fileForHashOrNull(context, gone.hash);
                if (f != null) f.delete();
            }
            save(prefs, members);
            return true;
        }
    }

    /**
     * Give nameless members a stable {@code hash + ext} filename so they can
     * be pushed into {@code album/}.
     */
    static ArrayList<Member> ensureLibraryNames(Context context, List<Member> stored) {
        ArrayList<Member> members = new ArrayList<Member>(stored);
        boolean changed = false;
        HashSet<String> taken = namesOf(members);
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            if (m.name != null && BackgroundAlbumLogic.sanitizeAlbumFileName(m.name) != null) {
                continue;
            }
            File f = fileForHashOrNull(context, m.hash);
            if (f == null) continue;
            String ext = extensionForFile(f);
            String desired = m.hash + ext;
            String unique = BackgroundAlbumLogic.uniqueAlbumFileName(desired, taken);
            if (unique == null) continue;
            taken.add(unique);
            members.set(i, new Member(m.hash, m.addedMs, unique));
            changed = true;
        }
        if (changed) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            save(prefs, members);
        }
        return members;
    }

    static HashSet<String> namesOf(List<Member> members) {
        HashSet<String> names = new HashSet<String>();
        if (members == null) return names;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).name != null) names.add(members.get(i).name);
        }
        return names;
    }

    static int indexByName(List<Member> members, String name) {
        if (members == null || name == null) return -1;
        for (int i = 0; i < members.size(); i++) {
            String n = members.get(i).name;
            if (n != null && n.equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    static int indexByHash(List<Member> members, String hash) {
        if (members == null || hash == null) return -1;
        for (int i = 0; i < members.size(); i++) {
            if (hash.equals(members.get(i).hash)) return i;
        }
        return -1;
    }

    /**
     * If {@code hash} is already a member, keep it and fill in {@code name}
     * when missing. Otherwise append. Returns the name stored on the member.
     */
    private static String ensureMember(ArrayList<Member> members, String hash, String name) {
        String safeName = BackgroundAlbumLogic.sanitizeAlbumFileName(name);
        int byHash = indexByHash(members, hash);
        if (byHash >= 0) {
            Member old = members.get(byHash);
            if (old.name == null && safeName != null) {
                members.set(byHash, new Member(old.hash, old.addedMs, safeName));
                return safeName;
            }
            return old.name != null ? old.name : safeName;
        }
        if (safeName != null) {
            int byName = indexByName(members, safeName);
            if (byName >= 0) {
                safeName = BackgroundAlbumLogic.uniqueAlbumFileName(safeName, namesOf(members));
            }
        }
        members.add(new Member(hash, System.currentTimeMillis(), safeName));
        return safeName;
    }

    private static String pickName(List<Member> members, String suggested, File bytes, String hash) {
        HashSet<String> taken = namesOf(members);
        int existing = indexByHash(members, hash);
        if (existing >= 0 && members.get(existing).name != null) {
            return members.get(existing).name;
        }
        String fromSuggested = BackgroundAlbumLogic.uniqueAlbumFileName(suggested, taken);
        if (fromSuggested != null) return fromSuggested;
        String ext = extensionForFile(bytes);
        return BackgroundAlbumLogic.uniqueAlbumFileName(hash + ext, taken);
    }

    private static String nameForFile(List<Member> members, File bytes, String hash) {
        int existing = indexByHash(members, hash);
        if (existing >= 0 && members.get(existing).name != null) {
            return members.get(existing).name;
        }
        return pickName(members, null, bytes, hash);
    }

    static String extensionForFile(File file) {
        if (file == null || !file.isFile()) return ".jpg";
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buf = new byte[16];
            int n = in.read(buf);
            if (n <= 0) return ".jpg";
            if (n < buf.length) {
                byte[] slim = new byte[n];
                System.arraycopy(buf, 0, slim, 0, n);
                buf = slim;
            }
            String ext = BackgroundAlbumLogic.imageExtensionFromPrefix(buf);
            return ext != null ? ext : ".jpg";
        } catch (IOException e) {
            return ".jpg";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static long albumBytes(Context context, List<Member> members) {
        long total = 0;
        HashSet<String> counted = new HashSet<String>();
        for (int i = 0; i < members.size(); i++) {
            String hash = members.get(i).hash;
            if (counted.contains(hash)) continue;
            File f = fileForHashOrNull(context, members.get(i).hash);
            if (f != null) {
                counted.add(hash);
                total += f.length();
            }
        }
        return total;
    }

    /**
     * Drop oldest members that are not {@code keepHash} until {@code extraBytes}
     * fits under count and size caps. Must not be used when a library folder is
     * linked (folder files would come back on the next sync).
     */
    private static void evictUntilFit(Context context, ArrayList<Member> members,
            long extraBytes, String keepHash) {
        int guard = members.size() + 1;
        while (guard-- > 0) {
            int count = members.size();
            long total = albumBytes(context, members);
            if (BackgroundAlbumLogic.canFit(count, total, extraBytes)) {
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
            if (indexByHash(members, gone.hash) < 0) {
                File f = fileForHashOrNull(context, gone.hash);
                if (f != null) f.delete();
            }
        }
    }
}
