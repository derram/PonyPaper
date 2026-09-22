package uk.cpjsmith.ponypaper;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;

/**
 * Process-wide cache of validated custom {@link PonyDefinition}s. Herd rebuilds
 * (thermal / mix / reload) and Tableau {@code createPony} share one parse per
 * unchanged file. Key is absolute path plus {@code lastModified} and
 * {@code length}; a swap that changes either misses and re-reads.
 *
 * <p>After a successful parse, {@link CustomSheetStore} unpacks sheet PNGs
 * beside the XML and drops the Base64 strings on the cached definition. A
 * failed unpack leaves those strings in place so wallpaper {@code load()}
 * can still decode from memory. One attempt per stamp.
 *
 * <p>{@link Pony} graphs are not cached — only the immutable-enough definition
 * after {@link PonyDefinition#validate()}. The editor parses its own copy and
 * does not use this cache, so authoring maps there stay intact.
 */
public final class CustomDefinitionCache {

    private static final Object LOCK = new Object();
    private static final HashMap<String, Entry> BY_PATH = new HashMap<String, Entry>();
    /** Per-path gate so a drip warm and a frame-thread create share one unpack. */
    private static final HashMap<String, Object> PREPARE_LOCKS = new HashMap<String, Object>();

    private static final class Entry {
        final long lastModified;
        final long length;
        final PonyDefinition definition;

        Entry(long lastModified, long length, PonyDefinition definition) {
            this.lastModified = lastModified;
            this.length = length;
            this.definition = definition;
        }
    }

    private CustomDefinitionCache() {
    }

    /**
     * Return a validated definition for {@code file}, parsing only on miss or
     * stamp change. Does not cache parse/validate failures.
     */
    public static PonyDefinition get(File file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("file");
        }
        String path = file.getAbsolutePath();
        long mtime = file.lastModified();
        long length = file.length();
        Object gate = prepareLock(path);
        synchronized (gate) {
            synchronized (LOCK) {
                Entry hit = BY_PATH.get(path);
                if (hit != null && hit.lastModified == mtime && hit.length == length) {
                    return hit.definition;
                }
            }

            DocumentBuilder docBuilder = SecureXml.newDocumentBuilder();
            Document document = docBuilder.parse(file);
            PonyDefinition definition = new PonyDefinition(document);
            definition.validate();
            // One shot per stamp. Disk failure keeps Base64 for the byte path.
            CustomSheetStore.prepare(file, definition);

            long mtimeAfter = file.lastModified();
            long lengthAfter = file.length();
            if (mtimeAfter == mtime && lengthAfter == length) {
                synchronized (LOCK) {
                    BY_PATH.put(path, new Entry(mtime, length, definition));
                }
            }
            return definition;
        }
    }

    private static Object prepareLock(String path) {
        synchronized (LOCK) {
            Object gate = PREPARE_LOCKS.get(path);
            if (gate == null) {
                gate = new Object();
                PREPARE_LOCKS.put(path, gate);
            }
            return gate;
        }
    }

    /**
     * Drop the cached entry for {@code file}, if any. Used when the path is no
     * longer a file (deleted / renamed).
     */
    public static void invalidate(File file) {
        if (file == null) {
            return;
        }
        String path = file.getAbsolutePath();
        Object gate = prepareLock(path);
        synchronized (gate) {
            synchronized (LOCK) {
                BY_PATH.remove(path);
            }
            CustomSheetStore.deleteFor(file);
        }
    }

    /**
     * Keep only entries whose path is in {@code files}. Call after listing the
     * custom XML directory so deleted names cannot accumulate.
     */
    public static void retainOnly(File[] files) {
        retainOnly(null, files);
    }

    /**
     * Drop cached definitions whose path is not in {@code files}, and delete
     * unpacked sheet directories under {@code libraryDir} that no longer have
     * an XML. {@code libraryDir} may be null when only the memory map matters.
     */
    public static void retainOnly(File libraryDir, File[] files) {
        HashSet<String> keep = new HashSet<String>();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i] != null) {
                    keep.add(files[i].getAbsolutePath());
                }
            }
        }
        synchronized (LOCK) {
            BY_PATH.keySet().retainAll(keep);
        }
        CustomSheetStore.deleteUnlisted(libraryDir, files);
    }

    /** Drop every entry. Tests only. */
    public static void clear() {
        synchronized (LOCK) {
            BY_PATH.clear();
        }
    }

    /** Cached entry count. Tests only. */
    public static int size() {
        synchronized (LOCK) {
            return BY_PATH.size();
        }
    }
}
