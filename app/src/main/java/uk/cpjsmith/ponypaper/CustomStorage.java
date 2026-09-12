package uk.cpjsmith.ponypaper;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
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
 * {@link java.io.File} paths. Durable copies are a zip export (custom XML,
 * optional live background, saved mixes, and saved scenes) or a user-owned SAF
 * tree (see library-folder methods added alongside this helper). The tree may
 * also contain an {@code album/} subfolder of saved backgrounds; that folder
 * is never written into a library zip.
 */
final class CustomStorage {

    static final String BACKGROUND_NAME = "background";
    /** Reserved library-zip sidecar for {@link PonyMixes}; never a working-dir file. */
    static final String MIXES_NAME = "ponypaper-mixes.json";
    /** Reserved library-zip sidecar for {@link PonyScenes}; never a working-dir file. */
    static final String SCENES_NAME = "ponypaper-scenes.json";
    static final String PLACEHOLDER_NAME = "custom-ponies-go-here";
    /** SAF display name. Providers append {@code .txt} for {@code text/plain}. */
    static final String PLACEHOLDER_LIBRARY_NAME = PLACEHOLDER_NAME + ".txt";
    /** Tree URI from {@link Intent#ACTION_OPEN_DOCUMENT_TREE}. */
    static final String PREF_LIBRARY_TREE_URI = "pref_library_tree_uri";
    /** Last tree URI whose membership set is stored (kept after disconnect). */
    static final String PREF_LIBRARY_SEEN_TREE = "pref_library_seen_tree_uri";
    /** Dest names last seen in that tree. Used to honor folder-side deletes. */
    static final String PREF_LIBRARY_SEEN_NAMES = "pref_library_seen_names";
    /** Album-folder filenames last seen in the linked tree. */
    static final String PREF_LIBRARY_SEEN_ALBUM_NAMES = "pref_library_seen_album_names";
    /** Touched so {@link PonySceneController} reloads the herd after file changes. */
    static final String PREF_LIBRARY_GENERATION = "pref_library_generation";
    private static final String ALBUM_MARKER_BODY =
            "Drop image files here to add them to the Pony Paper saved-backgrounds album. "
                    + "Delete a file to remove it from the album.\n\n"
                    + "This does not change the live wallpaper image (that is the \"background\" file "
                    + "in the parent folder).\n\n"
                    + "At most 20 images, 64 MB each, and 200 MB total. Extra files are ignored, "
                    + "not deleted.\n\n"
                    + "Album images are not included when you Export library.\n";

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

    /** What to include when writing a library zip. All true = full backup. */
    static final class ExportOptions {
        boolean ponies = true;
        boolean background = true;
        boolean mixes = true;
        boolean scenes = true;

        static ExportOptions all() {
            return new ExportOptions();
        }
    }

    /** What to apply when reading a library zip. All true = merge everything found. */
    static final class ImportOptions {
        boolean ponies = true;
        boolean background = true;
        boolean mixes = true;
        boolean scenes = true;

        static ImportOptions all() {
            return new ImportOptions();
        }
    }

    /** Non-destructive scan of a library zip for the import confirmation dialog. */
    static final class ZipPeekResult {
        int ponyCount;
        boolean hasBackground;
        int mixCount;
        int sceneCount;
        String error;

        boolean isEmpty() {
            return ponyCount == 0 && !hasBackground && mixCount == 0 && sceneCount == 0;
        }
    }

    static boolean hasLocalBackground(Context context) {
        try {
            File bg = localFile(context, BACKGROUND_NAME);
            return bg.isFile() && bg.length() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean hasExportableFiles(Context context) {
        return hasExportableFiles(context, ExportOptions.all());
    }

    static boolean hasExportableFiles(Context context, ExportOptions options) {
        if (options == null) options = ExportOptions.all();
        if (options.ponies && listCustomXml(context).length > 0) return true;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (options.mixes && !PonyMixes.loadUserMixes(prefs).isEmpty()) return true;
        if (options.scenes && !PonyScenes.loadUserScenes(prefs).isEmpty()) return true;
        return options.background && hasLocalBackground(context);
    }

    /**
     * Copy {@code source} into the working directory as {@code destName}.
     *
     * @return SHA-1 hex of the bytes written (random fallback if SHA-1 is missing)
     */
    static String queryDisplayName(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && name.length() > 0) return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return uri.getLastPathSegment();
    }

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
        return copyStreamToFile(in, dest, Long.MAX_VALUE);
    }

    static String copyStreamToFile(InputStream in, File dest, long maxBytes) throws IOException {
        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            digester = null;
        }

        boolean tooLarge = false;
        OutputStream out = new FileOutputStream(dest);
        try {
            byte[] buffer = new byte[COPY_BUFFER];
            long written = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                written += n;
                if (written > maxBytes) {
                    tooLarge = true;
                    throw new IOException("Image exceeds size limit");
                }
                out.write(buffer, 0, n);
                if (digester != null) digester.update(buffer, 0, n);
            }
        } finally {
            out.close();
            if (tooLarge) dest.delete();
        }
        return hexDigest(digester);
    }

    static void bumpGeneration(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putLong(PREF_LIBRARY_GENERATION, System.currentTimeMillis()).commit();
    }

    static void exportZip(Context context, Uri dest) throws IOException {
        exportZip(context, dest, ExportOptions.all());
    }

    static void exportZip(Context context, Uri dest, ExportOptions options) throws IOException {
        if (options == null) options = ExportOptions.all();
        OutputStream raw = context.getContentResolver().openOutputStream(dest);
        if (raw == null) {
            throw new IOException("Could not open export destination");
        }
        ZipOutputStream zip = new ZipOutputStream(raw);
        try {
            if (options.ponies) {
                File[] ponies = listCustomXml(context);
                for (int i = 0; i < ponies.length; i++) {
                    addFileToZip(zip, ponies[i], ponies[i].getName());
                }
            }
            if (options.background) {
                File bg = localFile(context, BACKGROUND_NAME);
                if (bg.isFile() && bg.length() > 0) {
                    addFileToZip(zip, bg, BACKGROUND_NAME);
                }
            }
            // Album copies under backgrounds/ and library album/ are not exported.
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (options.mixes) {
                List<PonyMixes.Mix> mixes = PonyMixes.loadUserMixes(prefs);
                if (!mixes.isEmpty()) {
                    byte[] json = PonyMixes.encode(mixes).getBytes(Charset.forName("UTF-8"));
                    addBytesToZip(zip, MIXES_NAME, json);
                }
            }
            if (options.scenes) {
                List<PonyScenes.TableauScene> scenes = PonyScenes.loadUserScenes(prefs);
                if (!scenes.isEmpty()) {
                    byte[] json = PonyScenes.encode(scenes).getBytes(Charset.forName("UTF-8"));
                    addBytesToZip(zip, SCENES_NAME, json);
                }
            }
        } finally {
            zip.close();
        }
    }

    static final class ZipImportResult {
        int poniesAdded;
        int skipped;
        int mixesAdded;
        int mixesReplaced;
        int mixesSkipped;
        int scenesAdded;
        int scenesReplaced;
        int scenesSkipped;
        boolean backgroundImported;
        String error;
    }

    /**
     * Scan a library zip without writing. Validates pony XML the same way import
     * does so the confirmation counts match what would be applied.
     */
    static ZipPeekResult peekZip(Context context, Uri source) {
        ZipPeekResult result = new ZipPeekResult();
        ZipInputStream zip = null;
        File dir = localDir(context);
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
                long size = entry.getSize();
                if (size > MAX_ZIP_ENTRY_BYTES) {
                    zip.closeEntry();
                    continue;
                }
                String baseName = zipEntryBaseName(entry.getName());
                if (MIXES_NAME.equals(baseName) || SCENES_NAME.equals(baseName)) {
                    try {
                        byte[] jsonBytes = readEntryLimited(zip, MAX_ZIP_ENTRY_BYTES);
                        total += jsonBytes.length;
                        if (total > MAX_ZIP_TOTAL_BYTES) {
                            result.error = "Zip is larger than the import limit";
                            break;
                        }
                        String json = new String(jsonBytes, Charset.forName("UTF-8"));
                        if (MIXES_NAME.equals(baseName)) {
                            List<PonyMixes.Mix> mixes = PonyMixes.parse(json);
                            if (!mixes.isEmpty()) {
                                result.mixCount = mixes.size();
                            }
                        } else {
                            List<PonyScenes.TableauScene> scenes = PonyScenes.parse(json);
                            if (!scenes.isEmpty()) {
                                result.sceneCount = scenes.size();
                            }
                        }
                    } catch (IOException ignored) {
                    }
                    zip.closeEntry();
                    continue;
                }
                String destName = zipEntryDestName(entry.getName());
                if (destName == null) {
                    zip.closeEntry();
                    continue;
                }
                if (BACKGROUND_NAME.equals(destName)) {
                    try {
                        byte[] bytes = readEntryLimited(zip, MAX_ZIP_ENTRY_BYTES);
                        total += bytes.length;
                        if (total > MAX_ZIP_TOTAL_BYTES) {
                            result.error = "Zip is larger than the import limit";
                            break;
                        }
                        if (bytes.length > 0) {
                            result.hasBackground = true;
                        }
                    } catch (IOException ignored) {
                    }
                    zip.closeEntry();
                    continue;
                }
                File tmp = null;
                try {
                    if (dir == null) {
                        zip.closeEntry();
                        continue;
                    }
                    tmp = File.createTempFile("pppeek", ".tmp", dir);
                    long written = copyStreamLimited(zip, tmp, MAX_ZIP_ENTRY_BYTES);
                    total += written;
                    if (total > MAX_ZIP_TOTAL_BYTES) {
                        result.error = "Zip is larger than the import limit";
                        break;
                    }
                    if (isValidCustomPonyFile(tmp)) {
                        result.ponyCount++;
                    }
                } catch (IOException ignored) {
                } finally {
                    if (tmp != null) tmp.delete();
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

    /**
     * Merge a library zip into the working directory. Unknown or unsafe entries
     * are skipped. Invalid custom-pony XML is skipped rather than stored.
     * Mixes and scenes sidecars are merged into preferences and are not written
     * locally. Categories disabled in {@code options} are skipped without writing.
     */
    static ZipImportResult importZip(Context context, Uri source) {
        return importZip(context, source, ImportOptions.all());
    }

    static ZipImportResult importZip(Context context, Uri source, ImportOptions options) {
        if (options == null) options = ImportOptions.all();
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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                long size = entry.getSize();
                if (size > MAX_ZIP_ENTRY_BYTES) {
                    result.skipped++;
                    zip.closeEntry();
                    continue;
                }
                String baseName = zipEntryBaseName(entry.getName());
                if (MIXES_NAME.equals(baseName) || SCENES_NAME.equals(baseName)) {
                    try {
                        byte[] jsonBytes = readEntryLimited(zip, MAX_ZIP_ENTRY_BYTES);
                        total += jsonBytes.length;
                        if (total > MAX_ZIP_TOTAL_BYTES) {
                            result.error = "Zip is larger than the import limit";
                            break;
                        }
                        boolean want = MIXES_NAME.equals(baseName) ? options.mixes : options.scenes;
                        if (!want) {
                            zip.closeEntry();
                            continue;
                        }
                        String json = new String(jsonBytes, Charset.forName("UTF-8"));
                        if (MIXES_NAME.equals(baseName)) {
                            PonyMixes.MixMergeResult merged = PonyMixes.mergeImported(prefs, json);
                            if (merged.invalid) {
                                result.skipped++;
                            } else {
                                result.mixesAdded += merged.added;
                                result.mixesReplaced += merged.replaced;
                                result.mixesSkipped += merged.skipped;
                            }
                        } else {
                            PonyScenes.SceneMergeResult merged =
                                    PonyScenes.mergeImported(prefs, json);
                            if (merged.invalid) {
                                result.skipped++;
                            } else {
                                result.scenesAdded += merged.added;
                                result.scenesReplaced += merged.replaced;
                                result.scenesSkipped += merged.skipped;
                            }
                        }
                    } catch (IOException e) {
                        result.skipped++;
                    }
                    zip.closeEntry();
                    continue;
                }
                String destName = zipEntryDestName(entry.getName());
                if (destName == null) {
                    result.skipped++;
                    zip.closeEntry();
                    continue;
                }
                boolean skipCategory = (BACKGROUND_NAME.equals(destName) && !options.background)
                        || (!BACKGROUND_NAME.equals(destName) && !options.ponies);
                if (skipCategory) {
                    try {
                        byte[] ignored = readEntryLimited(zip, MAX_ZIP_ENTRY_BYTES);
                        total += ignored.length;
                        if (total > MAX_ZIP_TOTAL_BYTES) {
                            result.error = "Zip is larger than the import limit";
                            break;
                        }
                    } catch (IOException e) {
                        result.skipped++;
                    }
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
     * Mixes and scenes sidecars are handled separately and never become dest names.
     */
    static String zipEntryDestName(String raw) {
        String name = zipEntryBaseName(raw);
        if (name == null || isLibraryMarkerName(name)
                || BackgroundAlbumLogic.isAlbumMarkerName(name)
                || MIXES_NAME.equals(name) || SCENES_NAME.equals(name)) {
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

    private static String zipEntryBaseName(String raw) {
        if (raw == null) return null;
        String name = raw.replace('\\', '/');
        if (name.contains("../") || name.startsWith("/") || name.contains(":")) {
            return null;
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty()) return null;
        return name;
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

    private static void addBytesToZip(ZipOutputStream zip, String entryName, byte[] data)
            throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static byte[] readEntryLimited(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long written = 0;
        byte[] buffer = new byte[COPY_BUFFER];
        int n;
        while ((n = in.read(buffer)) >= 0) {
            written += n;
            if (written > maxBytes) {
                throw new IOException("Zip entry exceeds size limit");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
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

    static String sha1OfFile(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Missing file");
        }
        InputStream in = new FileInputStream(file);
        try {
            MessageDigest digester;
            try {
                digester = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                digester = null;
            }
            byte[] buffer = new byte[COPY_BUFFER];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (digester != null) digester.update(buffer, 0, n);
            }
            return hexDigest(digester);
        } finally {
            in.close();
        }
    }

    static void copyFile(File from, File to) throws IOException {
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
                editor.putStringSet(PREF_LIBRARY_SEEN_ALBUM_NAMES, new HashSet<String>());
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

            syncAlbumFolder(context, tree, children, result);

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
     * Copy an album hash-file into {@code album/} under {@code displayName}.
     * No-op when no library folder is linked. Must not be called while holding
     * {@link BackgroundAlbum}'s lock.
     */
    static void writeThroughAlbum(Context context, File local, String displayName) {
        synchronized (LIBRARY_LOCK) {
            Uri tree = getLibraryTreeUri(context);
            String destName = BackgroundAlbumLogic.sanitizeAlbumFileName(displayName);
            if (tree == null || local == null || !local.isFile() || destName == null) return;
            try {
                List<LibraryChild> root = listLibraryChildren(context, tree);
                LibraryChild albumDir = findOrCreateAlbumDir(context, tree, root);
                if (albumDir == null || albumDir.documentId == null) return;
                List<LibraryChild> albumChildren =
                        listLibraryChildren(context, tree, albumDir.documentId, true);
                writeAlbumMarker(context, albumDir.uri, albumChildren);
                writeLocalToParent(context, albumDir.uri, albumChildren, local, destName,
                        BackgroundAlbumLogic.mimeForAlbumFileName(destName));
                rememberAlbumName(context, destName);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Delete one file from {@code album/} if a library folder is linked.
     * Must not be called while holding {@link BackgroundAlbum}'s lock.
     */
    static void deleteAlbumLibraryFile(Context context, String displayName) {
        synchronized (LIBRARY_LOCK) {
            Uri tree = getLibraryTreeUri(context);
            String destName = BackgroundAlbumLogic.sanitizeAlbumFileName(displayName);
            if (tree == null || destName == null) return;
            try {
                List<LibraryChild> root = listLibraryChildren(context, tree);
                LibraryChild albumDir = findAlbumDir(root);
                if (albumDir == null || albumDir.documentId == null) {
                    forgetAlbumName(context, destName);
                    return;
                }
                List<LibraryChild> albumChildren =
                        listLibraryChildren(context, tree, albumDir.documentId, true);
                LibraryChild existing = findChildByDestNameIgnoreCase(albumChildren, destName);
                if (existing != null) {
                    deleteLibraryChild(context, albumChildren, existing);
                }
                forgetAlbumName(context, destName);
            } catch (Exception ignored) {
            }
        }
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

    /**
     * Remove the shared background image from the working copy and, if linked,
     * from the library folder. Clears both wallpaper and dream background toggles.
     */
    static RemoveResult clearBackground(Context context) {
        synchronized (LIBRARY_LOCK) {
            RemoveResult result = new RemoveResult();
            Uri tree = getLibraryTreeUri(context);
            if (tree != null) {
                try {
                    List<LibraryChild> children = listLibraryChildren(context, tree);
                    LibraryChild existing = findChildByDestName(children, BACKGROUND_NAME);
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
            if (!deleteLocalMember(context, BACKGROUND_NAME)) {
                result.error = "Could not delete the working copy.";
                return result;
            }
            result.localDeleted = true;
            forgetLibraryName(context, BACKGROUND_NAME);
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .remove("pref_select_background")
                    .commit();
            bumpGeneration(context);
            return result;
        }
    }

    private static final class LibraryChild {
        Uri uri;
        String documentId;
        String displayName;
        String destName;
        long lastModified;
        boolean isDir;
    }

    private static LibraryChild findChildByDestName(List<LibraryChild> children, String destName) {
        if (destName == null) return null;
        for (int i = 0; i < children.size(); i++) {
            LibraryChild child = children.get(i);
            if (destName.equals(child.destName)) return child;
        }
        return null;
    }

    private static LibraryChild findChildByDestNameIgnoreCase(List<LibraryChild> children,
            String destName) {
        if (destName == null) return null;
        for (int i = 0; i < children.size(); i++) {
            LibraryChild child = children.get(i);
            if (child.destName != null && destName.equalsIgnoreCase(child.destName)) {
                return child;
            }
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

    private static HashSet<String> loadSeenAlbumNames(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> stored = prefs.getStringSet(PREF_LIBRARY_SEEN_ALBUM_NAMES, null);
        HashSet<String> names = new HashSet<String>();
        if (stored != null) names.addAll(stored);
        return names;
    }

    private static void saveSeenAlbumNames(Context context, Set<String> names) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putStringSet(PREF_LIBRARY_SEEN_ALBUM_NAMES, new HashSet<String>(names))
                .commit();
    }

    private static void rememberAlbumName(Context context, String destName) {
        if (destName == null || destName.length() == 0) return;
        Uri tree = getLibraryTreeUri(context);
        if (tree == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String seenTree = prefs.getString(PREF_LIBRARY_SEEN_TREE, "");
        if (!tree.toString().equals(seenTree)) return;
        HashSet<String> names = loadSeenAlbumNames(context);
        if (names.add(destName)) {
            saveSeenAlbumNames(context, names);
        }
    }

    private static void forgetAlbumName(Context context, String destName) {
        HashSet<String> names = loadSeenAlbumNames(context);
        if (names.remove(destName)) {
            saveSeenAlbumNames(context, names);
        } else {
            String found = null;
            for (String n : names) {
                if (n != null && n.equalsIgnoreCase(destName)) {
                    found = n;
                    break;
                }
            }
            if (found != null) {
                names.remove(found);
                saveSeenAlbumNames(context, names);
            }
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
                        .putBoolean(PonySceneController.PREF_DREAM_BACKGROUND, false)
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
        String prefKey = "pref_custom_" + destName;
        editor.remove(prefKey);
        String waifu = prefs.getString("pref_waifu", "");
        if (prefKey.equals(waifu)) {
            editor.putString("pref_waifu", "");
        }
        PonyMixes.removeKeyFromAllMixes(prefs, editor, prefKey);
        PonyMixes.removeKeyFromPreviousHerd(prefs, editor, prefKey);
        PonyMixes.beginProgrammaticHerdChange();
        try {
            editor.commit();
        } finally {
            PonyMixes.endProgrammaticHerdChange();
        }
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
        return listLibraryChildren(context, treeUri,
                DocumentsContract.getTreeDocumentId(treeUri), false);
    }

    private static List<LibraryChild> listLibraryChildren(Context context, Uri treeUri,
            String parentDocId, boolean albumListing) throws SecurityException, IOException {
        ContentResolver cr = context.getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
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
                LibraryChild child = new LibraryChild();
                child.documentId = docId;
                child.uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                child.displayName = name;
                child.lastModified = modified;
                child.isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                if (child.isDir) {
                    child.destName = null;
                } else if (albumListing) {
                    child.destName = BackgroundAlbumLogic.sanitizeAlbumFileName(name);
                } else if (BACKGROUND_NAME.equals(name)) {
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

    private static void copyLibraryChildToFile(Context context, Uri docUri, File dest)
            throws IOException {
        copyLibraryChildToFile(context, docUri, dest, Long.MAX_VALUE);
    }

    private static void copyLibraryChildToFile(Context context, Uri docUri, File dest,
            long maxBytes) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(docUri);
        if (in == null) {
            throw new IOException("Could not open library file");
        }
        try {
            File tmp = File.createTempFile("pplib", ".tmp", dest.getParentFile());
            try {
                copyStreamToFile(in, tmp, maxBytes);
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
        String destName = local.getName();
        String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
        String mime = destName.endsWith(".xml") ? "text/xml" : "application/octet-stream";
        writeLocalToParent(context, parent, children, local, destName, mime);
    }

    private static void writeLocalToParent(Context context, Uri parentDocUri,
            List<LibraryChild> children, File local, String destName, String mime)
            throws IOException {
        if (local == null || !local.isFile()) {
            throw new IOException("Nothing to write to the library folder");
        }
        if (destName == null || destName.length() == 0) {
            throw new IOException("Nothing to write to the library folder");
        }
        ContentResolver cr = context.getContentResolver();
        LibraryChild existing = findChildByDestNameIgnoreCase(children, destName);
        if (existing == null) {
            existing = findChildByDestName(children, destName);
        }
        if (existing != null) {
            try {
                DocumentsContract.deleteDocument(cr, existing.uri);
            } catch (Exception ignored) {
            }
            children.remove(existing);
        }
        if (mime == null) mime = "application/octet-stream";
        Uri created = DocumentsContract.createDocument(cr, parentDocUri, mime, destName);
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
        child.documentId = DocumentsContract.getDocumentId(created);
        child.displayName = destName;
        child.destName = destName;
        child.lastModified = local.lastModified();
        child.isDir = false;
        children.add(child);
    }

    private static LibraryChild findAlbumDir(List<LibraryChild> rootChildren) {
        LibraryChild exact = null;
        LibraryChild any = null;
        for (int i = 0; i < rootChildren.size(); i++) {
            LibraryChild child = rootChildren.get(i);
            if (!child.isDir || child.displayName == null) continue;
            String name = child.displayName.trim();
            if (BackgroundAlbumLogic.ALBUM_DIR_NAME.equals(name)) {
                exact = child;
                break;
            }
            if (any == null
                    && BackgroundAlbumLogic.ALBUM_DIR_NAME.equalsIgnoreCase(name)) {
                any = child;
            }
        }
        return exact != null ? exact : any;
    }

    private static LibraryChild findOrCreateAlbumDir(Context context, Uri treeUri,
            List<LibraryChild> rootChildren) {
        LibraryChild existing = findAlbumDir(rootChildren);
        if (existing != null) return existing;
        try {
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
            Uri created = DocumentsContract.createDocument(context.getContentResolver(), parent,
                    DocumentsContract.Document.MIME_TYPE_DIR, BackgroundAlbumLogic.ALBUM_DIR_NAME);
            if (created == null) return null;
            LibraryChild child = new LibraryChild();
            child.uri = created;
            child.documentId = DocumentsContract.getDocumentId(created);
            child.displayName = BackgroundAlbumLogic.ALBUM_DIR_NAME;
            child.destName = null;
            child.isDir = true;
            child.lastModified = System.currentTimeMillis();
            rootChildren.add(child);
            return child;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Two-way sync of {@code album/} against {@link BackgroundAlbum}. Extra
     * folder files past the cap are left in place. Does not write the live
     * {@code background} slot.
     */
    private static void syncAlbumFolder(Context context, Uri treeUri,
            List<LibraryChild> rootChildren, SyncResult result) {
        try {
            LibraryChild albumDirChild = findOrCreateAlbumDir(context, treeUri, rootChildren);
            if (albumDirChild == null || albumDirChild.documentId == null) return;
            List<LibraryChild> albumChildren =
                    listLibraryChildren(context, treeUri, albumDirChild.documentId, true);
            writeAlbumMarker(context, albumDirChild.uri, albumChildren);

            Set<String> lastSeen = loadSeenAlbumNames(context);
            HashSet<String> folderNames = new HashSet<String>();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            BackgroundAlbum.ensureLibraryNames(context, BackgroundAlbum.load(prefs));

            File localAlbumDir = BackgroundAlbum.albumDir(context);
            File temp = new File(localAlbumDir, "lib-ingest.tmp");
            try {
                for (int i = 0; i < albumChildren.size(); i++) {
                    LibraryChild child = albumChildren.get(i);
                    if (child.isDir) continue;
                    if (BackgroundAlbumLogic.isAlbumMarkerName(child.displayName)) continue;
                    String destName = child.destName;
                    if (destName == null) {
                        destName = BackgroundAlbumLogic.sanitizeAlbumFileName(child.displayName);
                    }
                    if (destName == null) continue;
                    folderNames.add(destName);

                    List<BackgroundAlbum.Member> members = BackgroundAlbum.load(prefs);
                    int named = BackgroundAlbum.indexByName(members, destName);
                    File local = named >= 0
                            ? BackgroundAlbum.fileForHashOrNull(context, members.get(named).hash)
                            : null;
                    boolean missing = local == null;
                    boolean libraryNewer = !missing && child.lastModified > 0
                            && child.lastModified > local.lastModified() + 2000L;
                    if (!missing && !libraryNewer) continue;
                    if (named < 0 && members.size() >= BackgroundAlbumLogic.MAX_MEMBERS) {
                        continue;
                    }
                    if (temp.exists() && !temp.delete()) continue;
                    try {
                        copyLibraryChildToFile(context, child.uri, temp,
                                BackgroundAlbumLogic.MAX_MEMBER_BYTES);
                    } catch (IOException e) {
                        if (temp.exists()) temp.delete();
                        continue;
                    }
                    if (!temp.isFile() || temp.length() <= 0) {
                        if (temp.exists()) temp.delete();
                        continue;
                    }
                    String hash;
                    try {
                        hash = sha1OfFile(temp);
                    } catch (IOException e) {
                        temp.delete();
                        continue;
                    }
                    if (!BackgroundAlbumLogic.isSafeHash(hash)) {
                        temp.delete();
                        continue;
                    }
                    File dest;
                    try {
                        dest = BackgroundAlbum.fileForHash(context, hash);
                    } catch (IOException e) {
                        temp.delete();
                        continue;
                    }
                    if (!dest.isFile() || dest.length() <= 0) {
                        if (dest.exists() && !dest.delete()) {
                            temp.delete();
                            continue;
                        }
                        if (!temp.renameTo(dest)) {
                            try {
                                copyFile(temp, dest);
                            } catch (IOException e) {
                                dest.delete();
                                temp.delete();
                                continue;
                            }
                        }
                    }
                    if (temp.exists()) temp.delete();
                    int adopted = BackgroundAlbum.adoptFromLibrary(context, hash, destName, dest);
                    if (adopted < 0) {
                        members = BackgroundAlbum.load(prefs);
                        if (BackgroundAlbum.indexByHash(members, hash) < 0) {
                            dest.delete();
                        }
                    } else if (adopted > 0) {
                        result.pulled++;
                    }
                }
            } finally {
                if (temp.exists()) temp.delete();
            }

            List<BackgroundAlbum.Member> members = BackgroundAlbum.load(prefs);
            ArrayList<String> toDrop = new ArrayList<String>();
            for (int i = 0; i < members.size(); i++) {
                String name = members.get(i).name;
                if (name == null) continue;
                if (BackgroundAlbumLogic.containsIgnoreCase(folderNames, name)) continue;
                if (BackgroundAlbumLogic.containsIgnoreCase(lastSeen, name)) {
                    toDrop.add(name);
                }
            }
            for (int i = 0; i < toDrop.size(); i++) {
                if (BackgroundAlbum.dropByLibraryName(context, toDrop.get(i))) {
                    result.dropped++;
                }
            }

            members = BackgroundAlbum.ensureLibraryNames(context, BackgroundAlbum.load(prefs));
            for (int i = 0; i < members.size(); i++) {
                BackgroundAlbum.Member member = members.get(i);
                if (member.name == null) continue;
                File local = BackgroundAlbum.fileForHashOrNull(context, member.hash);
                if (local == null) continue;
                LibraryChild match = findChildByDestNameIgnoreCase(albumChildren, member.name);
                boolean shouldPush;
                if (match != null) {
                    shouldPush = match.lastModified > 0
                            && local.lastModified() > match.lastModified + 2000L;
                } else {
                    shouldPush = !BackgroundAlbumLogic.containsIgnoreCase(lastSeen, member.name);
                }
                if (!shouldPush) continue;
                writeLocalToParent(context, albumDirChild.uri, albumChildren, local, member.name,
                        BackgroundAlbumLogic.mimeForAlbumFileName(member.name));
                result.pushed++;
            }

            HashSet<String> newSeen = new HashSet<String>(folderNames);
            members = BackgroundAlbum.load(prefs);
            for (String name : lastSeen) {
                if (BackgroundAlbumLogic.containsIgnoreCase(newSeen, name)) continue;
                if (BackgroundAlbum.indexByName(members, name) >= 0) {
                    newSeen.add(name);
                }
            }
            saveSeenAlbumNames(context, newSeen);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception ignored) {
        }
    }

    /**
     * Ensure one visible album marker exists and has the note body when newly
     * created. Extra SAF {@code (N)} copies are removed.
     */
    private static void writeAlbumMarker(Context context, Uri albumDirUri,
            List<LibraryChild> children) {
        LibraryChild keep = null;
        int keepRank = 3;
        for (int i = 0; i < children.size(); i++) {
            LibraryChild child = children.get(i);
            int rank = BackgroundAlbumLogic.albumMarkerRank(child.displayName);
            if (rank < 0) continue;
            if (keep == null || rank < keepRank) {
                keep = child;
                keepRank = rank;
            }
        }
        ContentResolver cr = context.getContentResolver();
        if (keep != null) {
            for (int i = children.size() - 1; i >= 0; i--) {
                LibraryChild child = children.get(i);
                if (child == keep) continue;
                if (BackgroundAlbumLogic.albumMarkerRank(child.displayName) < 0) continue;
                try {
                    DocumentsContract.deleteDocument(cr, child.uri);
                } catch (Exception ignored) {
                }
                children.remove(i);
            }
            return;
        }
        try {
            Uri created = DocumentsContract.createDocument(cr, albumDirUri,
                    "text/plain", BackgroundAlbumLogic.ALBUM_MARKER_LIBRARY_NAME);
            if (created == null) return;
            OutputStream out = cr.openOutputStream(created);
            if (out != null) {
                try {
                    out.write(ALBUM_MARKER_BODY.getBytes(Charset.forName("UTF-8")));
                } finally {
                    out.close();
                }
            }
            LibraryChild child = new LibraryChild();
            child.uri = created;
            child.documentId = DocumentsContract.getDocumentId(created);
            child.displayName = BackgroundAlbumLogic.ALBUM_MARKER_LIBRARY_NAME;
            child.destName = null;
            child.lastModified = System.currentTimeMillis();
            child.isDir = false;
            children.add(child);
        } catch (Exception ignored) {
        }
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
