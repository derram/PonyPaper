package uk.cpjsmith.ponypaper;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;

/**
 * Working copy of custom ponies and the optional background image. Lives in
 * {@link Context#getExternalFilesDir(null)} so the wallpaper can keep using
 * {@link java.io.File} paths. Durable copies are a zip export or a user-owned
 * SAF tree (see library-folder methods added alongside this helper).
 */
final class CustomStorage {

    static final String BACKGROUND_NAME = "background";
    static final String PLACEHOLDER_NAME = "custom-ponies-go-here";
    /** SAF display name. Providers append {@code .txt} for {@code text/plain}. */
    static final String PLACEHOLDER_LIBRARY_NAME = PLACEHOLDER_NAME + ".txt";
    /** Tree URI from {@link Intent#ACTION_OPEN_DOCUMENT_TREE}. */
    static final String PREF_LIBRARY_TREE_URI = "pref_library_tree_uri";
    /** Last tree URI whose membership set is stored (kept after disconnect). */
    static final String PREF_LIBRARY_SEEN_TREE = "pref_library_seen_tree_uri";
    /** Dest names last seen in that tree. Used to honor folder-side deletes. */
    static final String PREF_LIBRARY_SEEN_NAMES = "pref_library_seen_names";
    /** Touched so {@link PonySceneController} reloads the herd after file changes. */
    static final String PREF_LIBRARY_GENERATION = "pref_library_generation";

    private static final Object LIBRARY_LOCK = new Object();
    private static final long MAX_ZIP_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long MAX_ZIP_TOTAL_BYTES = 256L * 1024 * 1024;
    private static final int COPY_BUFFER = 8192;

    private CustomStorage() {}

    static File localDir(Context context) {
        return context.getExternalFilesDir(null);
    }

    static File[] listCustomXml(Context context) {
        File dir = localDir(context);
        if (dir == null) return new File[0];
        try {
            new File(dir, PLACEHOLDER_NAME).createNewFile();
        } catch (IOException ignored) {
        }
        File[] files = dir.listFiles(AllPonies.xmlFilter);
        if (files == null) return new File[0];
        Arrays.sort(files);
        return files;
    }

    static File localFile(Context context, String destName) throws IOException {
        File dir = localDir(context);
        if (dir == null) {
            throw new IOException("App storage is not available on this device right now.");
        }
        File dest = new File(dir, destName);
        if (!dest.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())) {
            throw new IOException("Refusing to write outside app files directory");
        }
        return dest;
    }

    static boolean hasExportableFiles(Context context) {
        if (listCustomXml(context).length > 0) return true;
        try {
            File bg = localFile(context, BACKGROUND_NAME);
            return bg.isFile() && bg.length() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Copy {@code source} into the working directory as {@code destName}.
     *
     * @return SHA-1 hex of the bytes written (random fallback if SHA-1 is missing)
     */
    static String copyUriToLocal(Context context, Uri source, String destName) throws IOException {
        File dest = localFile(context, destName);
        InputStream in = context.getContentResolver().openInputStream(source);
        if (in == null) {
            throw new IOException("Could not open selected content");
        }
        try {
            return copyStreamToFile(in, dest);
        } finally {
            in.close();
        }
    }

    static String copyStreamToFile(InputStream in, File dest) throws IOException {
        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            digester = null;
        }

        OutputStream out = new FileOutputStream(dest);
        try {
            byte[] buffer = new byte[COPY_BUFFER];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
                if (digester != null) digester.update(buffer, 0, n);
            }
        } finally {
            out.close();
        }
        return hexDigest(digester);
    }

    static void bumpGeneration(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putLong(PREF_LIBRARY_GENERATION, System.currentTimeMillis()).commit();
    }

    static void exportZip(Context context, Uri dest) throws IOException {
        OutputStream raw = context.getContentResolver().openOutputStream(dest);
        if (raw == null) {
            throw new IOException("Could not open export destination");
        }
        ZipOutputStream zip = new ZipOutputStream(raw);
        try {
            File[] ponies = listCustomXml(context);
            for (int i = 0; i < ponies.length; i++) {
                addFileToZip(zip, ponies[i], ponies[i].getName());
            }
            File bg = localFile(context, BACKGROUND_NAME);
            if (bg.isFile() && bg.length() > 0) {
                addFileToZip(zip, bg, BACKGROUND_NAME);
            }
        } finally {
            zip.close();
        }
    }

    static final class ZipImportResult {
        int poniesAdded;
        int skipped;
        boolean backgroundImported;
        String error;
    }

    /**
     * Merge a library zip into the working directory. Unknown or unsafe entries
     * are skipped. Invalid custom-pony XML is skipped rather than stored.
     */
    static ZipImportResult importZip(Context context, Uri source) {
        ZipImportResult result = new ZipImportResult();
        ZipInputStream zip = null;
        try {
            InputStream in = context.getContentResolver().openInputStream(source);
            if (in == null) {
                result.error = "Could not open selected file";
                return result;
            }
            zip = new ZipInputStream(in);
            long total = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String destName = zipEntryDestName(entry.getName());
                if (destName == null) {
                    result.skipped++;
                    zip.closeEntry();
                    continue;
                }
                long size = entry.getSize();
                if (size > MAX_ZIP_ENTRY_BYTES) {
                    result.skipped++;
                    zip.closeEntry();
                    continue;
                }
                File dest = localFile(context, destName);
                File tmp = File.createTempFile("ppimp", ".tmp", dest.getParentFile());
                try {
                    long written = copyStreamLimited(zip, tmp, MAX_ZIP_ENTRY_BYTES);
                    total += written;
                    if (total > MAX_ZIP_TOTAL_BYTES) {
                        tmp.delete();
                        result.error = "Zip is larger than the import limit";
                        break;
                    }
                    if (BACKGROUND_NAME.equals(destName)) {
                        if (!tmp.renameTo(dest)) {
                            copyFile(tmp, dest);
                            tmp.delete();
                        }
                        result.backgroundImported = true;
                    } else if (isValidCustomPonyFile(tmp)) {
                        if (!tmp.renameTo(dest)) {
                            copyFile(tmp, dest);
                            tmp.delete();
                        }
                        result.poniesAdded++;
                    } else {
                        tmp.delete();
                        result.skipped++;
                    }
                } catch (IOException e) {
                    tmp.delete();
                    result.skipped++;
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            result.error = "Could not read zip: " + e.getMessage();
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                }
            }
        }
        return result;
    }

    static boolean looksLikeZip(String displayName, String mime) {
        if (mime != null) {
            String m = mime.toLowerCase(Locale.US);
            if (m.equals("application/zip")
                    || m.equals("application/x-zip-compressed")
                    || m.equals("application/octet-stream") && displayName != null
                    && displayName.toLowerCase(Locale.US).endsWith(".zip")) {
                return true;
            }
        }
        return displayName != null && displayName.toLowerCase(Locale.US).endsWith(".zip");
    }

    static boolean isValidCustomPonyFile(File file) {
        try {
            DocumentBuilder docBuilder = SecureXml.newDocumentBuilder();
            Document document = docBuilder.parse(file);
            PonyDefinition definition = new PonyDefinition(document);
            definition.validate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isValidCustomPonyUri(Context context, Uri uri) {
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            DocumentBuilder docBuilder = SecureXml.newDocumentBuilder();
            Document document = docBuilder.parse(in);
            PonyDefinition definition = new PonyDefinition(document);
            definition.validate();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Produce a safe basename for custom pony XML under the app files directory.
     * Strips path segments, rejects {@code ..}, allows only {@code [A-Za-z0-9._-]},
     * and forces a {@code .xml} suffix.
     *
     * @return sanitized name, or null if nothing usable remains
     */
    static String sanitizeCustomPonyFileName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return null;
        }
        if (name.regionMatches(true, name.length() - 4, ".xml", 0, 4)) {
            name = name.substring(0, name.length() - 4);
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if ((ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '_' || ch == '-') {
                sb.append(ch);
            } else {
                sb.append('_');
            }
        }
        String base = sb.toString();
        while (base.startsWith(".")) {
            base = base.substring(1);
        }
        if (base.isEmpty() || base.equals(".") || base.equals("..")) {
            return null;
        }
        return base + ".xml";
    }

    /**
     * Map a zip entry path to a working-directory name, or null to skip.
     * Rejects path traversal and everything except {@code *.xml} and {@code background}.
     */
    static String zipEntryDestName(String raw) {
        if (raw == null) return null;
        String name = raw.replace('\\', '/');
        if (name.contains("../") || name.startsWith("/") || name.contains(":")) {
            return null;
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty() || isLibraryMarkerName(name)) {
            return null;
        }
        if (BACKGROUND_NAME.equals(name)) {
            return BACKGROUND_NAME;
        }
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".xml")) {
            return sanitizeCustomPonyFileName(name);
        }
        return null;
    }

    private static void addFileToZip(ZipOutputStream zip, File file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        zip.putNextEntry(entry);
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[COPY_BUFFER];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                zip.write(buffer, 0, n);
            }
        } finally {
            in.close();
            zip.closeEntry();
        }
    }

    private static long copyStreamLimited(InputStream in, File dest, long maxBytes) throws IOException {
        OutputStream out = new FileOutputStream(dest);
        long written = 0;
        try {
            byte[] buffer = new byte[COPY_BUFFER];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                written += n;
                if (written > maxBytes) {
                    throw new IOException("Zip entry exceeds size limit");
                }
                out.write(buffer, 0, n);
            }
        } finally {
            out.close();
        }
        return written;
    }

    private static void copyFile(File from, File to) throws IOException {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream out = new FileOutputStream(to);
            try {
                byte[] buffer = new byte[COPY_BUFFER];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    static final class SyncResult {
        int pulled;
        int pushed;
        int dropped;
        boolean permissionLost;
        boolean changed;
        String error;
    }

    static final class RemoveResult {
        boolean localDeleted;
        boolean libraryDeleted;
        String error;
    }

    static Uri getLibraryTreeUri(Context context) {
        String stored = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_LIBRARY_TREE_URI, "");
        if (stored == null || stored.length() == 0) return null;
        return Uri.parse(stored);
    }

    static boolean hasLibraryFolder(Context context) {
        return getLibraryTreeUri(context) != null;
    }

    /**
     * Persist a user-chosen document tree and drop any previous tree permission.
     */
    static void setLibraryTreeUri(Context context, Uri treeUri, int grantFlags) throws SecurityException {
        synchronized (LIBRARY_LOCK) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String previousSeen = prefs.getString(PREF_LIBRARY_SEEN_TREE, "");
            ContentResolver cr = context.getContentResolver();
            int flags = grantFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (flags == 0) {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            }
            releaseLibraryTreeLocked(context);
            cr.takePersistableUriPermission(treeUri, flags);
            String newUri = treeUri.toString();
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(PREF_LIBRARY_TREE_URI, newUri)
                    .putString(PREF_LIBRARY_SEEN_TREE, newUri);
            if (previousSeen == null || !newUri.equals(previousSeen)) {
                editor.putStringSet(PREF_LIBRARY_SEEN_NAMES, new HashSet<String>());
            }
            editor.commit();
        }
    }

    static void releaseLibraryTree(Context context) {
        synchronized (LIBRARY_LOCK) {
            releaseLibraryTreeLocked(context);
        }
    }

    /** Drops persistable access and the active tree URI; keeps last-seen membership. */
    private static void releaseLibraryTreeLocked(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri current = getLibraryTreeUri(context);
        if (current != null) {
            try {
                cr.releasePersistableUriPermission(current,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }
        List<UriPermission> persisted = cr.getPersistedUriPermissions();
        for (int i = 0; i < persisted.size(); i++) {
            UriPermission perm = persisted.get(i);
            try {
                cr.releasePersistableUriPermission(perm.getUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(PREF_LIBRARY_TREE_URI)
                .commit();
    }

    /**
     * Human-readable label for the connected tree, or null if none is stored.
     */
    static String libraryFolderLabel(Context context) {
        Uri tree = getLibraryTreeUri(context);
        if (tree == null) return null;
        try {
            String docId = DocumentsContract.getTreeDocumentId(tree);
            if (docId != null && docId.length() > 0) {
                int colon = docId.indexOf(':');
                if (colon >= 0 && colon < docId.length() - 1) {
                    return docId.substring(colon + 1);
                }
                return docId;
            }
        } catch (Exception ignored) {
        }
        return tree.getPath();
    }

    static boolean canAccessLibrary(Context context) {
        Uri tree = getLibraryTreeUri(context);
        if (tree == null) return false;
        try {
            listLibraryChildren(context, tree);
            return true;
        } catch (SecurityException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sync against the linked folder. After a successful list, the folder is
     * membership: names that disappeared since last sync are dropped locally.
     * Names never seen in this folder (first connect, or a new local import)
     * are still pushed so an empty folder is seeded.
     */
    static SyncResult syncLibrary(Context context) {
        synchronized (LIBRARY_LOCK) {
            return syncLibraryLocked(context);
        }
    }

    private static SyncResult syncLibraryLocked(Context context) {
        SyncResult result = new SyncResult();
        Uri tree = getLibraryTreeUri(context);
        if (tree == null) return result;
        try {
            List<LibraryChild> children = listLibraryChildren(context, tree);
            writeLibraryMarker(context, tree, children);
            File dir = localDir(context);
            if (dir == null) {
                result.error = "App storage is not available on this device right now.";
                return result;
            }
            try {
                new File(dir, PLACEHOLDER_NAME).createNewFile();
            } catch (IOException ignored) {
            }

            Set<String> lastSeen = loadSeenNames(context);
            HashSet<String> folderNames = folderDestNames(children);

            // Pull library → local (match on sanitized name).
            for (int i = 0; i < children.size(); i++) {
                LibraryChild child = children.get(i);
                if (child.destName == null) continue;
                File local = localFile(context, child.destName);
                boolean missing = !local.isFile();
                boolean libraryNewer = !missing && child.lastModified > 0
                        && child.lastModified > local.lastModified() + 2000L;
                if (missing || libraryNewer) {
                    copyLibraryChildToFile(context, child.uri, local);
                    result.pulled++;
                }
            }

            // Drop working-copy files the folder used to have and no longer does.
            for (String name : lastSeen) {
                if (folderNames.contains(name)) continue;
                if (deleteLocalMember(context, name)) {
                    result.dropped++;
                }
            }

            File[] localXml = dir.listFiles(AllPonies.xmlFilter);
            if (localXml == null) localXml = new File[0];

            // Push updates, and local-only names this folder has never seen.
            for (int i = 0; i < localXml.length; i++) {
                File local = localXml[i];
                if (!local.isFile()) continue;
                LibraryChild match = findChildByDestName(children, local.getName());
                if (shouldPushLocal(local, match, lastSeen)) {
                    writeLocalToLibrary(context, tree, children, local);
                    result.pushed++;
                }
            }
            File bg = new File(dir, BACKGROUND_NAME);
            if (bg.isFile() && bg.length() > 0) {
                LibraryChild match = findChildByDestName(children, BACKGROUND_NAME);
                if (shouldPushLocal(bg, match, lastSeen)) {
                    writeLocalToLibrary(context, tree, children, bg);
                    result.pushed++;
                }
            }

            HashSet<String> newSeen = folderDestNames(children);
            for (String name : lastSeen) {
                if (newSeen.contains(name)) continue;
                try {
                    if (localFile(context, name).isFile()) {
                        newSeen.add(name);
                    }
                } catch (IOException ignored) {
                }
            }
            saveSeenNames(context, newSeen);
            result.changed = result.pulled > 0 || result.pushed > 0 || result.dropped > 0;
        } catch (SecurityException e) {
            result.permissionLost = true;
        } catch (Exception e) {
            result.error = e.getMessage();
        }
        return result;
    }

    /**
     * Copy a working-directory file into the library tree, replacing any child
     * that sanitizes to the same name.
     */
    static void writeThroughToLibrary(Context context, File local) {
        synchronized (LIBRARY_LOCK) {
            Uri tree = getLibraryTreeUri(context);
            if (tree == null || local == null || !local.isFile()) return;
            try {
                List<LibraryChild> children = listLibraryChildren(context, tree);
                writeLocalToLibrary(context, tree, children, local);
                rememberLibraryName(context, local.getName());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Delete a custom pony from the working copy and, if a folder is connected,
     * from that folder. Checkboxes stay hide-only; this is the remove path.
     */
    static RemoveResult removeCustomPony(Context context, String destName) {
        synchronized (LIBRARY_LOCK) {
            RemoveResult result = new RemoveResult();
            String safe = sanitizeCustomPonyFileName(destName);
            if (safe == null || !safe.equals(destName)) {
                result.error = "Invalid pony file name";
                return result;
            }
            Uri tree = getLibraryTreeUri(context);
            if (tree != null) {
                try {
                    List<LibraryChild> children = listLibraryChildren(context, tree);
                    LibraryChild existing = findChildByDestName(children, safe);
                    if (existing != null) {
                        if (!deleteLibraryChild(context, children, existing)) {
                            result.error = "Could not delete the file in the library folder.";
                            return result;
                        }
                        result.libraryDeleted = true;
                    }
                } catch (SecurityException e) {
                    result.error = "Lost access to the library folder. Reconnect it, or delete the file there.";
                    return result;
                } catch (Exception e) {
                    result.error = e.getMessage() != null ? e.getMessage()
                            : "Could not update the library folder.";
                    return result;
                }
            }
            if (!deleteLocalMember(context, safe)) {
                result.error = "Could not delete the working copy.";
                return result;
            }
            result.localDeleted = true;
            forgetLibraryName(context, safe);
            bumpGeneration(context);
            return result;
        }
    }

    private static final class LibraryChild {
        Uri uri;
        String displayName;
        String destName;
        long lastModified;
    }

    private static LibraryChild findChildByDestName(List<LibraryChild> children, String destName) {
        for (int i = 0; i < children.size(); i++) {
            LibraryChild child = children.get(i);
            if (destName.equals(child.destName)) return child;
        }
        return null;
    }

    private static boolean shouldPushLocal(File local, LibraryChild match, Set<String> lastSeen) {
        if (match != null) {
            if (match.lastModified <= 0) return false;
            return local.lastModified() > match.lastModified + 2000L;
        }
        // Missing from the folder: seed/import only if this folder has never listed it.
        return lastSeen == null || !lastSeen.contains(local.getName());
    }

    private static HashSet<String> folderDestNames(List<LibraryChild> children) {
        HashSet<String> names = new HashSet<String>();
        for (int i = 0; i < children.size(); i++) {
            String dest = children.get(i).destName;
            if (dest != null) names.add(dest);
        }
        return names;
    }

    private static HashSet<String> loadSeenNames(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> stored = prefs.getStringSet(PREF_LIBRARY_SEEN_NAMES, null);
        HashSet<String> names = new HashSet<String>();
        if (stored != null) names.addAll(stored);
        return names;
    }

    private static void saveSeenNames(Context context, Set<String> names) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putStringSet(PREF_LIBRARY_SEEN_NAMES, new HashSet<String>(names));
        Uri tree = getLibraryTreeUri(context);
        if (tree != null) {
            editor.putString(PREF_LIBRARY_SEEN_TREE, tree.toString());
        }
        editor.commit();
    }

    private static void rememberLibraryName(Context context, String destName) {
        if (destName == null || destName.length() == 0) return;
        Uri tree = getLibraryTreeUri(context);
        if (tree == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String seenTree = prefs.getString(PREF_LIBRARY_SEEN_TREE, "");
        if (!tree.toString().equals(seenTree)) return;
        HashSet<String> names = loadSeenNames(context);
        if (names.add(destName)) {
            saveSeenNames(context, names);
        }
    }

    private static void forgetLibraryName(Context context, String destName) {
        HashSet<String> names = loadSeenNames(context);
        if (names.remove(destName)) {
            saveSeenNames(context, names);
        }
    }

    /** Delete a working-copy member and its enable/waifu prefs. File already gone is success. */
    private static boolean deleteLocalMember(Context context, String destName) {
        try {
            File local = localFile(context, destName);
            if (local.isFile() && !local.delete()) {
                return false;
            }
            if (BACKGROUND_NAME.equals(destName)) {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putBoolean("pref_background", false)
                        .commit();
            } else {
                clearPonyPreferences(context, destName);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static void clearPonyPreferences(Context context, String destName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("pref_custom_" + destName);
        String waifu = prefs.getString("pref_waifu", "");
        if (("pref_custom_" + destName).equals(waifu)) {
            editor.putString("pref_waifu", "");
        }
        editor.commit();
    }

    private static boolean deleteLibraryChild(Context context, List<LibraryChild> children,
            LibraryChild existing) {
        try {
            DocumentsContract.deleteDocument(context.getContentResolver(), existing.uri);
            children.remove(existing);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<LibraryChild> listLibraryChildren(Context context, Uri treeUri)
            throws SecurityException, IOException {
        ContentResolver cr = context.getContentResolver();
        String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);
        ArrayList<LibraryChild> out = new ArrayList<LibraryChild>();
        Cursor cursor = null;
        try {
            cursor = cr.query(childrenUri, new String[] {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
            }, null, null, null);
            if (cursor == null) {
                throw new IOException("Could not list library folder");
            }
            while (cursor.moveToNext()) {
                String docId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long modified = cursor.isNull(3) ? 0L : cursor.getLong(3);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) continue;
                LibraryChild child = new LibraryChild();
                child.uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                child.displayName = name;
                child.lastModified = modified;
                if (BACKGROUND_NAME.equals(name)) {
                    child.destName = BACKGROUND_NAME;
                } else if (name != null && name.toLowerCase(Locale.US).endsWith(".xml")) {
                    child.destName = sanitizeCustomPonyFileName(name);
                }
                out.add(child);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return out;
    }

    private static void copyLibraryChildToFile(Context context, Uri docUri, File dest) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(docUri);
        if (in == null) {
            throw new IOException("Could not open library file");
        }
        try {
            File tmp = File.createTempFile("pplib", ".tmp", dest.getParentFile());
            try {
                OutputStream out = new FileOutputStream(tmp);
                try {
                    byte[] buffer = new byte[COPY_BUFFER];
                    int n;
                    while ((n = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, n);
                    }
                } finally {
                    out.close();
                }
                if (dest.getName().endsWith(".xml") && !isValidCustomPonyFile(tmp)) {
                    tmp.delete();
                    return;
                }
                if (!tmp.renameTo(dest)) {
                    copyFile(tmp, dest);
                    tmp.delete();
                }
            } catch (IOException e) {
                tmp.delete();
                throw e;
            }
        } finally {
            in.close();
        }
    }

    private static void writeLocalToLibrary(Context context, Uri treeUri,
            List<LibraryChild> children, File local) throws IOException {
        if (local == null || !local.isFile()) {
            throw new IOException("Nothing to write to the library folder");
        }
        ContentResolver cr = context.getContentResolver();
        String destName = local.getName();
        LibraryChild existing = findChildByDestName(children, destName);
        if (existing != null) {
            // Replace rather than truncate: many tree providers reject "wt".
            try {
                DocumentsContract.deleteDocument(cr, existing.uri);
            } catch (Exception ignored) {
            }
            children.remove(existing);
        }
        String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
        String mime = destName.endsWith(".xml") ? "text/xml" : "application/octet-stream";
        Uri created = DocumentsContract.createDocument(cr, parent, mime, destName);
        if (created == null) {
            throw new IOException("Could not create library file");
        }
        OutputStream out = cr.openOutputStream(created);
        if (out == null) {
            throw new IOException("Could not write library file");
        }
        try {
            copyFileToStream(local, out);
        } finally {
            out.close();
        }
        LibraryChild child = new LibraryChild();
        child.uri = created;
        child.displayName = destName;
        child.destName = destName;
        child.lastModified = local.lastModified();
        children.add(child);
    }

    /**
     * True for the library breadcrumb and SAF uniquified copies:
     * {@code custom-ponies-go-here}, {@code .txt}, and {@code (N)} variants.
     */
    static boolean isLibraryMarkerName(String displayName) {
        if (displayName == null) return false;
        String name = displayName.trim().toLowerCase(Locale.US);
        if (!name.startsWith(PLACEHOLDER_NAME)) return false;
        String rest = name.substring(PLACEHOLDER_NAME.length());
        if (rest.startsWith(".txt")) {
            rest = rest.substring(4);
        } else if (rest.endsWith(".txt")) {
            rest = rest.substring(0, rest.length() - 4);
        }
        rest = rest.trim();
        if (rest.length() == 0) return true;
        if (rest.charAt(0) != '(' || rest.charAt(rest.length() - 1) != ')') return false;
        String inner = rest.substring(1, rest.length() - 1);
        if (inner.length() == 0) return false;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    private static int libraryMarkerRank(String displayName) {
        if (displayName == null) return 2;
        if (PLACEHOLDER_LIBRARY_NAME.equalsIgnoreCase(displayName.trim())) return 0;
        if (PLACEHOLDER_NAME.equalsIgnoreCase(displayName.trim())) return 1;
        return 2;
    }

    /**
     * Ensure one visible marker exists. SAF {@code text/plain} create without
     * {@code .txt} used to miss the existing file and spawn {@code (N)} copies.
     */
    private static void writeLibraryMarker(Context context, Uri treeUri, List<LibraryChild> children) {
        LibraryChild keep = null;
        int keepRank = 3;
        for (int i = 0; i < children.size(); i++) {
            LibraryChild child = children.get(i);
            if (!isLibraryMarkerName(child.displayName)) continue;
            int rank = libraryMarkerRank(child.displayName);
            if (keep == null || rank < keepRank) {
                keep = child;
                keepRank = rank;
            }
        }
        if (keep != null) {
            ContentResolver cr = context.getContentResolver();
            for (int i = children.size() - 1; i >= 0; i--) {
                LibraryChild child = children.get(i);
                if (child == keep || !isLibraryMarkerName(child.displayName)) continue;
                try {
                    DocumentsContract.deleteDocument(cr, child.uri);
                } catch (Exception ignored) {
                }
                children.remove(i);
            }
            return;
        }
        try {
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
            Uri created = DocumentsContract.createDocument(context.getContentResolver(), parent,
                    "text/plain", PLACEHOLDER_LIBRARY_NAME);
            if (created == null) return;
            LibraryChild child = new LibraryChild();
            child.uri = created;
            child.displayName = PLACEHOLDER_LIBRARY_NAME;
            child.destName = null;
            child.lastModified = System.currentTimeMillis();
            children.add(child);
        } catch (Exception ignored) {
        }
    }

    private static void copyFileToStream(File from, OutputStream out) throws IOException {
        InputStream in = new FileInputStream(from);
        try {
            byte[] buffer = new byte[COPY_BUFFER];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        } finally {
            in.close();
        }
    }

    private static String hexDigest(MessageDigest digester) {
        byte[] digest;
        if (digester != null) {
            digest = digester.digest();
        } else {
            digest = new byte[20];
            new Random().nextBytes(digest);
        }
        StringBuilder hash = new StringBuilder(digest.length * 2);
        for (int i = 0; i < digest.length; i++) {
            hash.append(String.format("%02x", (256 + digest[i]) % 256));
        }
        return hash.toString();
    }
}
