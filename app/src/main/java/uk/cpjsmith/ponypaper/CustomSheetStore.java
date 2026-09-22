package uk.cpjsmith.ponypaper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Derived PNG cache for one custom XML file. The XML stays the library copy
 * (authoring and export). Wallpaper loads pin these files and then drop the
 * in-memory PNG bytes. A stamp of the XML mtime and length decides whether
 * the directory is reused.
 *
 * <p>Layout, beside the XML's parent:
 * {@code pony-cache/<xml name>/stamp}, {@code index}, and {@code N.png}.
 * Identical image strings share one file. Alias actions (no images) are
 * skipped. Failure leaves the definition's Base64 maps in place so the
 * wallpaper can still decode from memory.
 */
public final class CustomSheetStore {

    public static final String DIR_NAME = "pony-cache";
    private static final String STAMP_NAME = "stamp";
    private static final String INDEX_NAME = "index";
    private static final int STAMP_VERSION = 1;

    private CustomSheetStore() {
    }

    /**
     * Bind unpacked files onto {@code definition}, writing the cache when the
     * stamp misses. Returns false when a sheet cannot be decoded or written;
     * {@code definition} is unchanged in that case.
     */
    public static boolean prepare(File xmlFile, PonyDefinition definition) {
        if (xmlFile == null || definition == null) {
            return false;
        }
        File root = cacheRoot(xmlFile);
        File dest = cacheDir(xmlFile);
        if (root == null || dest == null || !contained(root, dest)) {
            return false;
        }
        long mtime = xmlFile.lastModified();
        long length = xmlFile.length();
        try {
            if (bindIfReady(dest, definition, mtime, length)) {
                return true;
            }
            // A stamp miss with no Base64 left cannot rebuild. Leave any
            // existing directory alone (the caller parsed a fresh XML).
            if (!hasEncodedSheets(definition)) {
                return alreadyInstalled(definition);
            }
            return rewrite(root, dest, definition, mtime, length);
        } catch (IOException e) {
            return false;
        }
    }

    /** Delete the cache directory for {@code xmlFile}, if any. */
    public static void deleteFor(File xmlFile) {
        if (xmlFile == null) {
            return;
        }
        File root = cacheRoot(xmlFile);
        File dest = cacheDir(xmlFile);
        if (root == null || dest == null) {
            return;
        }
        deleteIfInside(root, dest);
        deleteIfInside(root, new File(root, xmlFile.getName() + ".tmp"));
    }

    /**
     * Delete {@code pony-cache} children of {@code libraryDir} whose names are
     * not in {@code xmlFiles}. Orphan {@code .tmp} directories are removed.
     * No-op when {@code libraryDir} is null.
     */
    public static void deleteUnlisted(File libraryDir, File[] xmlFiles) {
        if (libraryDir == null) {
            return;
        }
        File root = new File(libraryDir, DIR_NAME);
        if (!root.isDirectory()) {
            return;
        }
        HashSet<String> keep = new HashSet<String>();
        if (xmlFiles != null) {
            for (int i = 0; i < xmlFiles.length; i++) {
                if (xmlFiles[i] != null) {
                    keep.add(xmlFiles[i].getName());
                }
            }
        }
        File[] kids = root.listFiles();
        if (kids == null) {
            return;
        }
        for (int i = 0; i < kids.length; i++) {
            String name = kids[i].getName();
            if (name.endsWith(".tmp") || !keep.contains(name)) {
                deleteIfInside(root, kids[i]);
            }
        }
    }

    private static boolean bindIfReady(File dest, PonyDefinition definition,
            long mtime, long length) throws IOException {
        if (!stampMatches(dest, mtime, length)) {
            return false;
        }
        if (alreadyInstalled(definition)) {
            return true;
        }
        HashMap<String, String> index = readIndex(new File(dest, INDEX_NAME));
        if (index == null) {
            return false;
        }
        ArrayList<Pending> pending = new ArrayList<Pending>();
        if (!collect(definition, dest, index, pending)) {
            return false;
        }
        apply(definition, pending);
        return true;
    }

    private static boolean rewrite(File root, File dest, PonyDefinition definition,
            long mtime, long length) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) {
            return false;
        }
        File tmp = new File(root, dest.getName() + ".tmp");
        deleteIfInside(root, tmp);
        if (!tmp.mkdir()) {
            return false;
        }
        try {
            ArrayList<Pending> pending = new ArrayList<Pending>();
            HashMap<String, String> written = new HashMap<String, String>();
            int[] seq = new int[] {0};
            if (!writeSheets(definition, tmp, written, seq, pending, dest)) {
                deleteIfInside(root, tmp);
                return false;
            }
            writeString(new File(tmp, INDEX_NAME), indexText(definition, written));
            writeString(new File(tmp, STAMP_NAME),
                    STAMP_VERSION + " " + mtime + " " + length + "\n");
            deleteIfInside(root, dest);
            if (!tmp.renameTo(dest)) {
                deleteIfInside(root, tmp);
                return false;
            }
            apply(definition, pending);
            return true;
        } catch (IOException e) {
            deleteIfInside(root, tmp);
            throw e;
        } catch (RuntimeException e) {
            deleteIfInside(root, tmp);
            return false;
        }
    }

    /**
     * @param publishDest directory the absolute paths should name (the final
     *        cache dir, even while bytes are still in a temp dir)
     */
    private static boolean writeSheets(PonyDefinition definition, File writeDir,
            HashMap<String, String> written, int[] seq, ArrayList<Pending> pending,
            File publishDest) throws IOException {
        if (definition.actions != null) {
            for (int i = 0; i < definition.actions.length; i++) {
                PonyDefinition.Action action = definition.actions[i];
                if (action == null || action.isAlias()) {
                    continue;
                }
                if (!writePair(action.images, action.timings, writeDir, written, seq,
                        pending, publishDest, false, i)) {
                    return false;
                }
            }
        }
        if (definition.effects != null) {
            for (int i = 0; i < definition.effects.length; i++) {
                PonyDefinition.Effect effect = definition.effects[i];
                if (effect == null) {
                    continue;
                }
                if (!writePair(effect.images, effect.timings, writeDir, written, seq,
                        pending, publishDest, true, i)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean writePair(Map<String, String> images,
            Map<String, String> timings, File writeDir, HashMap<String, String> written,
            int[] seq, ArrayList<Pending> pending, File publishDest, boolean effect, int index)
            throws IOException {
        String leftB64 = images != null ? images.get("left") : null;
        String rightB64 = images != null ? images.get("right") : null;
        if (isBlank(leftB64) && isBlank(rightB64)) {
            return true;
        }
        if (isBlank(leftB64) || isBlank(rightB64)) {
            return false;
        }
        String leftName = blobFile(leftB64, writeDir, written, seq);
        String rightName = blobFile(rightB64, writeDir, written, seq);
        if (leftName == null || rightName == null) {
            return false;
        }
        int[] leftTimes = parseTimes(timings != null ? timings.get("left") : null);
        int[] rightTimes = parseTimes(timings != null ? timings.get("right") : null);
        if (leftTimes == null || rightTimes == null) {
            return false;
        }
        if (leftB64.equals(rightB64)
                && timings.get("left") != null
                && timings.get("left").equals(timings.get("right"))) {
            rightTimes = leftTimes;
        }
        pending.add(new Pending(effect, index,
                new File(publishDest, leftName).getAbsolutePath(), leftTimes,
                new File(publishDest, rightName).getAbsolutePath(), rightTimes));
        return true;
    }

    private static String blobFile(String b64, File writeDir, HashMap<String, String> written,
            int[] seq) throws IOException {
        String existing = written.get(b64);
        if (existing != null) {
            return existing;
        }
        byte[] bytes = decodeBase64(b64);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String name = seq[0] + ".png";
        seq[0]++;
        File dest = new File(writeDir, name);
        FileOutputStream out = new FileOutputStream(dest);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        written.put(b64, name);
        return name;
    }

    private static String indexText(PonyDefinition definition, HashMap<String, String> written) {
        StringBuilder sb = new StringBuilder();
        if (definition.actions != null) {
            for (int i = 0; i < definition.actions.length; i++) {
                PonyDefinition.Action action = definition.actions[i];
                if (action == null || action.isAlias()) {
                    continue;
                }
                appendIndex(sb, "A", action.name, action.images, written);
            }
        }
        if (definition.effects != null) {
            for (int i = 0; i < definition.effects.length; i++) {
                PonyDefinition.Effect effect = definition.effects[i];
                if (effect == null) {
                    continue;
                }
                appendIndex(sb, "E", effect.name, effect.images, written);
            }
        }
        return sb.toString();
    }

    private static void appendIndex(StringBuilder sb, String kind, String name,
            Map<String, String> images, HashMap<String, String> written) {
        if (images == null) {
            return;
        }
        String left = images.get("left");
        String right = images.get("right");
        if (isBlank(left) || isBlank(right)) {
            return;
        }
        String leftFile = written.get(left);
        String rightFile = written.get(right);
        if (leftFile == null || rightFile == null) {
            return;
        }
        sb.append(kind).append('\t').append(escape(name != null ? name : ""))
                .append("\tL\t").append(leftFile).append('\n');
        sb.append(kind).append('\t').append(escape(name != null ? name : ""))
                .append("\tR\t").append(rightFile).append('\n');
    }

    private static boolean collect(PonyDefinition definition, File dest,
            HashMap<String, String> index, ArrayList<Pending> pending) {
        if (definition.actions != null) {
            for (int i = 0; i < definition.actions.length; i++) {
                PonyDefinition.Action action = definition.actions[i];
                if (action == null || action.isAlias()) {
                    continue;
                }
                if (!collectPair(action.images, action.timings, action.name, "A",
                        dest, index, pending, false, i)) {
                    return false;
                }
            }
        }
        if (definition.effects != null) {
            for (int i = 0; i < definition.effects.length; i++) {
                PonyDefinition.Effect effect = definition.effects[i];
                if (effect == null) {
                    continue;
                }
                if (!collectPair(effect.images, effect.timings, effect.name, "E",
                        dest, index, pending, true, i)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean collectPair(Map<String, String> images,
            Map<String, String> timings, String name, String kind, File dest,
            HashMap<String, String> index, ArrayList<Pending> pending, boolean effect,
            int indexInDef) {
        String leftB64 = images != null ? images.get("left") : null;
        String rightB64 = images != null ? images.get("right") : null;
        if (isBlank(leftB64) && isBlank(rightB64)) {
            return true;
        }
        String leftRel = index.get(sideKey(kind, name, "L"));
        String rightRel = index.get(sideKey(kind, name, "R"));
        if (!isPngName(leftRel) || !isPngName(rightRel)) {
            return false;
        }
        File leftFile = new File(dest, leftRel);
        File rightFile = new File(dest, rightRel);
        if (!leftFile.isFile() || leftFile.length() <= 0
                || !rightFile.isFile() || rightFile.length() <= 0) {
            return false;
        }
        int[] leftTimes = parseTimes(timings != null ? timings.get("left") : null);
        int[] rightTimes = parseTimes(timings != null ? timings.get("right") : null);
        if (leftTimes == null || rightTimes == null) {
            return false;
        }
        if (leftRel.equals(rightRel)
                && timings.get("left") != null
                && timings.get("left").equals(timings.get("right"))) {
            rightTimes = leftTimes;
        }
        pending.add(new Pending(effect, indexInDef,
                leftFile.getAbsolutePath(), leftTimes,
                rightFile.getAbsolutePath(), rightTimes));
        return true;
    }

    private static void apply(PonyDefinition definition, ArrayList<Pending> pending) {
        for (int i = 0; i < pending.size(); i++) {
            pending.get(i).install(definition);
        }
    }

    private static boolean hasEncodedSheets(PonyDefinition definition) {
        if (definition.actions != null) {
            for (int i = 0; i < definition.actions.length; i++) {
                PonyDefinition.Action action = definition.actions[i];
                if (action == null || action.isAlias() || action.images == null) {
                    continue;
                }
                if (!isBlank(action.images.get("left")) || !isBlank(action.images.get("right"))) {
                    return true;
                }
            }
        }
        if (definition.effects != null) {
            for (int i = 0; i < definition.effects.length; i++) {
                PonyDefinition.Effect effect = definition.effects[i];
                if (effect == null || effect.images == null) {
                    continue;
                }
                if (!isBlank(effect.images.get("left")) || !isBlank(effect.images.get("right"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean alreadyInstalled(PonyDefinition definition) {
        boolean any = false;
        if (definition.actions != null) {
            for (int i = 0; i < definition.actions.length; i++) {
                PonyDefinition.Action action = definition.actions[i];
                if (action == null || action.isAlias()) {
                    continue;
                }
                if (isBlank(action.images.get("left")) && action.runtimeFileLeft != null) {
                    any = true;
                    continue;
                }
                return false;
            }
        }
        if (definition.effects != null) {
            for (int i = 0; i < definition.effects.length; i++) {
                PonyDefinition.Effect effect = definition.effects[i];
                if (effect == null) {
                    continue;
                }
                if (isBlank(effect.images.get("left")) && effect.runtimeFileLeft != null) {
                    any = true;
                    continue;
                }
                if (!isBlank(effect.images.get("left"))) {
                    return false;
                }
            }
        }
        return any;
    }

    private static boolean stampMatches(File dest, long mtime, long length) throws IOException {
        File stamp = new File(dest, STAMP_NAME);
        if (!stamp.isFile()) {
            return false;
        }
        String text = readString(stamp).trim();
        String[] parts = text.split(" ");
        if (parts.length != 3) {
            return false;
        }
        try {
            return Integer.parseInt(parts[0]) == STAMP_VERSION
                    && Long.parseLong(parts[1]) == mtime
                    && Long.parseLong(parts[2]) == length;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static HashMap<String, String> readIndex(File indexFile) throws IOException {
        if (!indexFile.isFile()) {
            return null;
        }
        String text = readString(indexFile);
        HashMap<String, String> map = new HashMap<String, String>();
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = text.length();
            }
            String line = text.substring(start, end).trim();
            start = end + 1;
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length != 4) {
                return null;
            }
            String kind = parts[0];
            String side = parts[2];
            String fileName = parts[3];
            if ((!"A".equals(kind) && !"E".equals(kind))
                    || (!"L".equals(side) && !"R".equals(side))
                    || !isPngName(fileName)) {
                return null;
            }
            map.put(sideKey(kind, unescape(parts[1]), side), fileName);
        }
        return map;
    }

    private static String sideKey(String kind, String name, String side) {
        return kind + "\0" + (name != null ? name : "") + "\0" + side;
    }

    private static boolean isPngName(String name) {
        if (name == null || name.length() < 5 || !name.endsWith(".png")) {
            return false;
        }
        for (int i = 0; i < name.length() - 4; i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    static int[] parseTimes(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
            if (out[i] < 0) {
                return null;
            }
        }
        return out;
    }

    /**
     * Standard Base64, whitespace ignored. {@code null} when the input is not
     * a valid padded or unpadded group. Avoids {@code java.util.Base64}, which
     * is absent before API 26.
     */
    static byte[] decodeBase64(String raw) {
        if (raw == null) {
            return null;
        }
        int groups = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            groups++;
        }
        if (groups == 0 || (groups & 3) != 0) {
            return null;
        }
        byte[] out = new byte[(groups / 4) * 3];
        int o = 0;
        int s = 0;
        int[] sextet = new int[4];
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (c == '=') {
                sextet[s++] = -2;
            } else {
                int d = decodeChar(c);
                if (d < 0) {
                    return null;
                }
                sextet[s++] = d;
            }
            if (s < 4) {
                continue;
            }
            if (sextet[0] < 0 || sextet[1] < 0) {
                return null;
            }
            out[o++] = (byte) ((sextet[0] << 2) | (sextet[1] >> 4));
            if (sextet[2] != -2) {
                out[o++] = (byte) (((sextet[1] & 0xF) << 4) | (sextet[2] >> 2));
            } else if (sextet[3] != -2) {
                return null;
            }
            if (sextet[3] != -2) {
                if (sextet[2] < 0) {
                    return null;
                }
                out[o++] = (byte) (((sextet[2] & 0x3) << 6) | sextet[3]);
            }
            s = 0;
        }
        if (o == out.length) {
            return out;
        }
        byte[] exact = new byte[o];
        System.arraycopy(out, 0, exact, 0, o);
        return exact;
    }

    private static int decodeChar(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 26;
        }
        if (c >= '0' && c <= '9') {
            return c - '0' + 52;
        }
        if (c == '+') {
            return 62;
        }
        if (c == '/') {
            return 63;
        }
        return -1;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String escape(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unescape(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\\' && i + 1 < name.length()) {
                char n = name.charAt(++i);
                if (n == '\\') {
                    sb.append('\\');
                } else if (n == 't') {
                    sb.append('\t');
                } else if (n == 'n') {
                    sb.append('\n');
                } else if (n == 'r') {
                    sb.append('\r');
                } else {
                    sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static File cacheRoot(File xmlFile) {
        File parent = xmlFile.getParentFile();
        if (parent == null) {
            return null;
        }
        return new File(parent, DIR_NAME);
    }

    private static File cacheDir(File xmlFile) {
        String name = xmlFile.getName();
        if (name.length() == 0 || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            return null;
        }
        File root = cacheRoot(xmlFile);
        if (root == null) {
            return null;
        }
        return new File(root, name);
    }

    private static boolean contained(File root, File child) {
        try {
            File rootCanon = root.getCanonicalFile();
            File childCanon = child.getCanonicalFile();
            File parent = childCanon.getParentFile();
            return parent != null && parent.equals(rootCanon);
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteIfInside(File root, File child) {
        if (child == null || !child.exists()) {
            return;
        }
        if (!contained(root, child) && !childInside(root, child)) {
            return;
        }
        deleteTree(child);
    }

    /** Path-prefix check used when {@code child} is not yet canonical-stable. */
    private static boolean childInside(File root, File child) {
        try {
            String rootPath = root.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            return childPath.startsWith(rootPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids != null) {
                for (int i = 0; i < kids.length; i++) {
                    deleteTree(kids[i]);
                }
            }
        }
        file.delete();
    }

    private static void writeString(File file, String text) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    private static String readString(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[(int) file.length()];
            int o = 0;
            while (o < buf.length) {
                int n = in.read(buf, o, buf.length - o);
                if (n < 0) {
                    break;
                }
                o += n;
            }
            return new String(buf, 0, o, StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static final class Pending {
        final boolean effect;
        final int index;
        final String leftPath;
        final int[] leftTimes;
        final String rightPath;
        final int[] rightTimes;

        Pending(boolean effect, int index, String leftPath, int[] leftTimes,
                String rightPath, int[] rightTimes) {
            this.effect = effect;
            this.index = index;
            this.leftPath = leftPath;
            this.leftTimes = leftTimes;
            this.rightPath = rightPath;
            this.rightTimes = rightTimes;
        }

        void install(PonyDefinition definition) {
            if (effect) {
                definition.effects[index].installRuntimeFiles(
                        leftPath, leftTimes, rightPath, rightTimes);
            } else {
                definition.actions[index].installRuntimeFiles(
                        leftPath, leftTimes, rightPath, rightTimes);
            }
        }
    }
}
