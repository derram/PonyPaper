package uk.cpjsmith.ponypaper;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Named Tableau scene library and the active posed layout.
 *
 * <p>Library document: {@link #PREF_SCENES_JSON}. Active composition:
 * {@link #PREF_ACTIVE_JSON} (+ id / epoch). Structural and load writers always
 * put JSON + epoch (+ id) in one {@link SharedPreferences.Editor} batch.
 */
final class PonyScenes {

    static final String PREF_SCENES_JSON = "pref_tableau_scenes_json";
    static final String PREF_ACTIVE_ID = "pref_tableau_active_id";
    static final String PREF_ACTIVE_JSON = "pref_tableau_active_json";
    static final String PREF_ACTIVE_EPOCH = "pref_tableau_active_epoch";
    static final String PREF_PREVIOUS_JSON = "pref_tableau_previous_json";

    static final int MAX_USER_SCENES = 20;
    static final int MAX_SLOTS = 16;
    static final int MAX_ACTIONS_PER_SLOT = 8;
    static final int MAX_NAME_LENGTH = 40;

    enum SaveResult {
        SAVED,
        REPLACED,
        FULL,
        BAD_NAME
    }

    static final class SceneMergeResult {
        int added;
        int replaced;
        int skipped;
        boolean invalid;
    }

    static final class TableauSlot {
        final String ponyKey;
        final float xNorm;
        final float yNorm;
        final String[] actions;
        final String facing;

        TableauSlot(String ponyKey, float xNorm, float yNorm, String[] actions,
                String facing) {
            this.ponyKey = ponyKey != null ? ponyKey : "";
            this.xNorm = clamp01(xNorm);
            this.yNorm = clamp01(yNorm);
            this.actions = normalizeActions(actions);
            this.facing = normalizeFacing(facing);
        }

        /** Hot fields only (norms / facing / actions); ignores ponyKey. */
        boolean sameHot(TableauSlot other) {
            if (other == null) return false;
            return xNorm == other.xNorm
                    && yNorm == other.yNorm
                    && facing.equals(other.facing)
                    && actionsEqual(actions, other.actions);
        }
    }

    static final class TableauScene {
        final String id;
        final String name;
        final List<TableauSlot> slots;

        TableauScene(String id, String name, List<TableauSlot> slots) {
            this.id = id != null ? id : "";
            this.name = name != null ? name : "";
            if (slots == null || slots.isEmpty()) {
                this.slots = Collections.emptyList();
            } else {
                this.slots = Collections.unmodifiableList(
                        new ArrayList<TableauSlot>(slots));
            }
        }
    }

    private PonyScenes() {}

    static String normalizeName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        if (name.length() == 0) return null;
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH).trim();
            if (name.length() == 0) return null;
        }
        return name;
    }

    static List<TableauScene> loadUserScenes(SharedPreferences prefs) {
        if (prefs == null) return Collections.emptyList();
        return parse(prefs.getString(PREF_SCENES_JSON, ""));
    }

    static boolean hasName(SharedPreferences prefs, String rawName) {
        String name = normalizeName(rawName);
        if (name == null) return false;
        return indexOfName(loadUserScenes(prefs), name) >= 0;
    }

    static SaveResult save(SharedPreferences prefs, String rawName,
            List<TableauSlot> slots) {
        if (prefs == null) return SaveResult.BAD_NAME;
        String name = normalizeName(rawName);
        if (name == null) return SaveResult.BAD_NAME;
        ArrayList<TableauScene> scenes =
                new ArrayList<TableauScene>(loadUserScenes(prefs));
        int idx = indexOfName(scenes, name);
        if (idx < 0 && scenes.size() >= MAX_USER_SCENES) return SaveResult.FULL;
        ArrayList<TableauSlot> copy = new ArrayList<TableauSlot>();
        if (slots != null) {
            int n = Math.min(slots.size(), MAX_SLOTS);
            for (int i = 0; i < n; i++) {
                if (slots.get(i) != null) copy.add(slots.get(i));
            }
        }
        TableauScene next = idx >= 0
                ? new TableauScene(scenes.get(idx).id, name, copy)
                : new TableauScene(newId(), name, copy);
        if (idx >= 0) {
            scenes.set(idx, next);
        } else {
            scenes.add(next);
        }
        prefs.edit().putString(PREF_SCENES_JSON, encode(scenes)).commit();
        return idx >= 0 ? SaveResult.REPLACED : SaveResult.SAVED;
    }

    static void deleteById(SharedPreferences prefs, String id) {
        if (prefs == null || id == null) return;
        ArrayList<TableauScene> scenes =
                new ArrayList<TableauScene>(loadUserScenes(prefs));
        boolean removed = false;
        for (int i = scenes.size() - 1; i >= 0; i--) {
            if (id.equals(scenes.get(i).id)) {
                scenes.remove(i);
                removed = true;
            }
        }
        if (!removed) return;
        if (scenes.isEmpty()) {
            prefs.edit().remove(PREF_SCENES_JSON).commit();
        } else {
            prefs.edit().putString(PREF_SCENES_JSON, encode(scenes)).commit();
        }
    }

    /**
     * Merge library-zip scenes by name. Stub-friendly: invalid JSON sets
     * {@link SceneMergeResult#invalid}; does not touch the active scene.
     */
    static SceneMergeResult mergeImported(SharedPreferences prefs, String json) {
        SceneMergeResult result = new SceneMergeResult();
        if (prefs == null || json == null || json.length() == 0) {
            result.invalid = true;
            return result;
        }
        List<TableauScene> incoming;
        try {
            JSONObject root = new JSONObject(json);
            if (root.optJSONArray("scenes") == null) {
                result.invalid = true;
                return result;
            }
            incoming = parse(json);
        } catch (Exception e) {
            result.invalid = true;
            return result;
        }
        if (incoming.isEmpty()) return result;

        ArrayList<TableauScene> existing =
                new ArrayList<TableauScene>(loadUserScenes(prefs));
        HashSet<String> seenIds = new HashSet<String>();
        for (int i = 0; i < existing.size(); i++) {
            String id = existing.get(i).id;
            if (id.length() > 0) seenIds.add(id);
        }
        for (int i = 0; i < incoming.size(); i++) {
            TableauScene scene = incoming.get(i);
            if (scene == null || scene.name.length() == 0) continue;
            int idx = indexOfName(existing, scene.name);
            if (idx >= 0) {
                TableauScene previous = existing.get(idx);
                String id = scene.id;
                if (id.length() == 0
                        || (seenIds.contains(id) && !id.equals(previous.id))) {
                    id = previous.id;
                } else if (!id.equals(previous.id)) {
                    seenIds.remove(previous.id);
                    seenIds.add(id);
                }
                existing.set(idx, new TableauScene(id, scene.name, scene.slots));
                result.replaced++;
            } else if (existing.size() >= MAX_USER_SCENES) {
                result.skipped++;
            } else {
                String id = scene.id;
                if (id.length() == 0 || seenIds.contains(id)) id = newId();
                seenIds.add(id);
                existing.add(new TableauScene(id, scene.name, scene.slots));
                result.added++;
            }
        }
        if (result.added > 0 || result.replaced > 0) {
            prefs.edit().putString(PREF_SCENES_JSON, encode(existing)).commit();
        }
        return result;
    }

    static boolean hasActiveJson(SharedPreferences prefs) {
        if (prefs == null) return false;
        String json = prefs.getString(PREF_ACTIVE_JSON, "");
        return json != null && json.length() > 0;
    }

    static boolean hasPreviousJson(SharedPreferences prefs) {
        if (prefs == null) return false;
        String json = prefs.getString(PREF_PREVIOUS_JSON, "");
        return json != null && json.length() > 0;
    }

    /** Parsed active scene, or {@code null} when missing / invalid. */
    static TableauScene loadActiveScene(SharedPreferences prefs) {
        if (prefs == null) return null;
        return parseSceneObject(prefs.getString(PREF_ACTIVE_JSON, ""));
    }

    static int activeEpoch(SharedPreferences prefs) {
        if (prefs == null) return 0;
        return prefs.getInt(PREF_ACTIVE_EPOCH, 0);
    }

    /**
     * Built-in lower-third demo (TS / FS / AJ) with stable selectable action
     * ids resolved through {@link AllPonies#buildActionCatalog}.
     */
    static TableauScene demoScene() {
        ArrayList<TableauSlot> slots = new ArrayList<TableauSlot>(3);
        slots.add(new TableauSlot("pref_ts", 0.25f, 0.72f,
                new String[] { "alicorn_stand" }, Pony.FACING_RANDOM));
        slots.add(new TableauSlot("pref_fs", 0.50f, 0.74f,
                new String[] { "stand" }, Pony.FACING_RANDOM));
        slots.add(new TableauSlot("pref_aj", 0.75f, 0.72f,
                new String[] { "stand" }, Pony.FACING_LEFT));
        return new TableauScene("", "", slots);
    }

    /**
     * When active JSON is empty or unparseable: restore
     * {@link #PREF_PREVIOUS_JSON} else install {@link #demoScene()}. Writes
     * JSON + epoch (+ cleared id) in one editor batch. Returns true when prefs
     * were written.
     */
    static boolean ensureActiveScene(SharedPreferences prefs) {
        if (prefs == null) return false;
        if (hasActiveJson(prefs) && loadActiveScene(prefs) != null) return false;
        TableauScene scene = null;
        if (hasPreviousJson(prefs)) {
            scene = parseSceneObject(prefs.getString(PREF_PREVIOUS_JSON, ""));
        }
        if (scene == null) {
            scene = demoScene();
        }
        writeActiveStructural(prefs, "", scene);
        return true;
    }

    /** Copy active JSON into {@link #PREF_PREVIOUS_JSON} when leaving Tableau. */
    static void snapshotActiveToPrevious(SharedPreferences prefs) {
        if (prefs == null || !hasActiveJson(prefs)) return;
        String json = prefs.getString(PREF_ACTIVE_JSON, "");
        if (json == null || json.length() == 0) return;
        prefs.edit().putString(PREF_PREVIOUS_JSON, json).commit();
    }

    /**
     * Structural / load write: active JSON + epoch bump (+ id) in one batch.
     */
    static void writeActiveStructural(SharedPreferences prefs, String id,
            TableauScene scene) {
        if (prefs == null || scene == null) return;
        int epoch = prefs.getInt(PREF_ACTIVE_EPOCH, 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_ACTIVE_JSON, encodeScene(scene));
        editor.putString(PREF_ACTIVE_ID, id != null ? id : "");
        editor.putInt(PREF_ACTIVE_EPOCH, epoch);
        editor.commit();
    }

    /**
     * Load a library scene into active (previous snapshot + id + json + epoch)
     * in one batch. Returns false when the id is missing.
     */
    static boolean loadSceneById(SharedPreferences prefs, String id) {
        if (prefs == null || id == null || id.length() == 0) return false;
        List<TableauScene> scenes = loadUserScenes(prefs);
        for (int i = 0; i < scenes.size(); i++) {
            TableauScene scene = scenes.get(i);
            if (id.equals(scene.id)) {
                int epoch = prefs.getInt(PREF_ACTIVE_EPOCH, 0) + 1;
                SharedPreferences.Editor editor = prefs.edit();
                String active = prefs.getString(PREF_ACTIVE_JSON, "");
                if (active != null && active.length() > 0) {
                    editor.putString(PREF_PREVIOUS_JSON, active);
                }
                editor.putString(PREF_ACTIVE_JSON, encodeScene(scene));
                editor.putString(PREF_ACTIVE_ID, scene.id);
                editor.putInt(PREF_ACTIVE_EPOCH, epoch);
                editor.commit();
                return true;
            }
        }
        return false;
    }

    /** Hot path: rewrite active JSON only (no epoch bump). */
    static void writeActiveHot(SharedPreferences prefs, TableauScene scene) {
        if (prefs == null || scene == null) return;
        String id = prefs.getString(PREF_ACTIVE_ID, "");
        prefs.edit()
                .putString(PREF_ACTIVE_JSON, encodeScene(
                        new TableauScene(id, scene.name, scene.slots)))
                .commit();
    }

    static String encode(List<TableauScene> scenes) {
        JSONObject root = new JSONObject();
        JSONArray arr = new JSONArray();
        try {
            if (scenes != null) {
                for (int i = 0; i < scenes.size(); i++) {
                    TableauScene scene = scenes.get(i);
                    if (scene == null || scene.name.length() == 0) continue;
                    arr.put(sceneToJson(scene, true));
                }
            }
            root.put("version", 1);
            root.put("scenes", arr);
            return root.toString();
        } catch (Exception e) {
            return "{\"version\":1,\"scenes\":[]}";
        }
    }

    static List<TableauScene> parse(String json) {
        if (json == null || json.length() == 0) return Collections.emptyList();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("scenes");
            if (arr == null || arr.length() == 0) return Collections.emptyList();
            ArrayList<TableauScene> out = new ArrayList<TableauScene>(arr.length());
            HashSet<String> seenIds = new HashSet<String>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String name = normalizeName(obj.optString("name", ""));
                if (name == null) continue;
                String id = obj.optString("id", "");
                if (id.length() == 0 || seenIds.contains(id)) id = newId();
                seenIds.add(id);
                out.add(new TableauScene(id, name, parseSlots(obj.optJSONArray("slots"))));
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    static String encodeScene(TableauScene scene) {
        if (scene == null) return "{}";
        try {
            return sceneToJson(scene, false).toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    static TableauScene parseSceneObject(String json) {
        if (json == null || json.length() == 0) return null;
        try {
            JSONObject obj = new JSONObject(json);
            if (!obj.has("slots") && obj.has("scenes")) return null;
            String id = obj.optString("id", "");
            String name = obj.optString("name", "");
            String normalized = normalizeName(name);
            if (normalized != null) name = normalized;
            else name = "";
            List<TableauSlot> slots = parseSlots(obj.optJSONArray("slots"));
            return new TableauScene(id, name, slots);
        } catch (Exception e) {
            return null;
        }
    }

    static List<TableauSlot> snapshotSlots(TableauScene scene) {
        if (scene == null || scene.slots.isEmpty()) return Collections.emptyList();
        return new ArrayList<TableauSlot>(scene.slots);
    }

    private static JSONObject sceneToJson(TableauScene scene, boolean requireName)
            throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("id", scene.id.length() == 0 && requireName ? newId() : scene.id);
        obj.put("name", scene.name);
        JSONArray slots = new JSONArray();
        int n = Math.min(scene.slots.size(), MAX_SLOTS);
        for (int i = 0; i < n; i++) {
            TableauSlot slot = scene.slots.get(i);
            if (slot == null || slot.ponyKey.length() == 0) continue;
            JSONObject s = new JSONObject();
            s.put("ponyKey", slot.ponyKey);
            s.put("xNorm", slot.xNorm);
            s.put("yNorm", slot.yNorm);
            JSONArray actions = new JSONArray();
            for (int a = 0; a < slot.actions.length; a++) {
                actions.put(slot.actions[a]);
            }
            s.put("actions", actions);
            s.put("facing", slot.facing);
            slots.put(s);
        }
        obj.put("slots", slots);
        return obj;
    }

    private static List<TableauSlot> parseSlots(JSONArray arr) {
        if (arr == null || arr.length() == 0) return Collections.emptyList();
        ArrayList<TableauSlot> out = new ArrayList<TableauSlot>(
                Math.min(arr.length(), MAX_SLOTS));
        for (int i = 0; i < arr.length() && out.size() < MAX_SLOTS; i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            String ponyKey = obj.optString("ponyKey", "");
            if (ponyKey.length() == 0) continue;
            String[] actions = null;
            JSONArray actionArr = obj.optJSONArray("actions");
            if (actionArr != null) {
                actions = new String[actionArr.length()];
                for (int a = 0; a < actionArr.length(); a++) {
                    actions[a] = actionArr.optString(a, "");
                }
            }
            out.add(new TableauSlot(
                    ponyKey,
                    (float) obj.optDouble("xNorm", 0.5),
                    (float) obj.optDouble("yNorm", 0.5),
                    actions,
                    obj.optString("facing", Pony.FACING_RANDOM)));
        }
        return out;
    }

    private static String[] normalizeActions(String[] raw) {
        if (raw == null || raw.length == 0) return new String[0];
        ArrayList<String> out = new ArrayList<String>(Math.min(raw.length,
                MAX_ACTIONS_PER_SLOT));
        HashSet<String> seen = new HashSet<String>();
        for (int i = 0; i < raw.length && out.size() < MAX_ACTIONS_PER_SLOT; i++) {
            String id = raw[i] != null ? raw[i].trim() : "";
            if (id.length() == 0) continue;
            if (seen.add(id)) out.add(id);
        }
        return out.toArray(new String[out.size()]);
    }

    private static String normalizeFacing(String facing) {
        if (Pony.FACING_LEFT.equals(facing)) return Pony.FACING_LEFT;
        if (Pony.FACING_RIGHT.equals(facing)) return Pony.FACING_RIGHT;
        return Pony.FACING_RANDOM;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static boolean actionsEqual(String[] a, String[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) return false;
        }
        return true;
    }

    private static int indexOfName(List<TableauScene> scenes, String name) {
        if (scenes == null || name == null) return -1;
        for (int i = 0; i < scenes.size(); i++) {
            if (name.equalsIgnoreCase(scenes.get(i).name)) return i;
        }
        return -1;
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
