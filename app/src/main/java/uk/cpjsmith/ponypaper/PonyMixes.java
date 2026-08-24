package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Named herd snapshots: which ponies are on, plus the favorite (waifu).
 *
 * <p>Disable-all undo stays in {@link PonyEnableAll}'s one-slot snapshots.
 * User mixes are a separate JSON list. Live checkbox booleans remain the
 * source of truth for the wallpaper.
 *
 * <p>Load also keeps one unnamed previous-herd snapshot so the usual
 * checkboxes can be restored. That snapshot is written only when leaving a
 * non-loaded (home) state; hopping from mix to mix leaves it alone. A
 * checkbox or favorite change marks the herd as home again.
 *
 * <p>Custom ponies are stored by preference key ({@code pref_custom_} +
 * filename). Missing files are skipped on load; they are not copied into
 * the mix.
 *
 * <p>Library zip export stores this list as a sidecar (see
 * {@link CustomStorage#MIXES_NAME}). Import merges by name and does not
 * apply a mix to the live checkboxes.
 */
final class PonyMixes {

    static final String PREF_MIXES_JSON = "pref_mixes_json";
    static final String PREF_PREVIOUS_HERD_JSON = "pref_previous_herd_json";
    static final String PREF_VIEWING_LOADED_MIX = "pref_viewing_loaded_mix";
    static final String PREF_WAIFU = "pref_waifu";
    static final String CUSTOM_PREFIX = "pref_custom_";
    static final int MAX_USER_MIXES = 20;
    static final int MAX_NAME_LENGTH = 40;

    private static int programmaticHerdDepth;

    enum SaveResult {
        SAVED,
        REPLACED,
        FULL,
        BAD_NAME
    }

    static final class MixMergeResult {
        int added;
        int replaced;
        int skipped;
        boolean invalid;
    }

    static final class Mix {
        final String id;
        final String name;
        final Set<String> keys;
        final String waifu;

        Mix(String id, String name, Set<String> keys, String waifu) {
            this.id = id != null ? id : "";
            this.name = name != null ? name : "";
            this.keys = keys != null ? keys : Collections.<String>emptySet();
            this.waifu = waifu != null ? waifu : "";
        }
    }

    private PonyMixes() {}

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

    static HashSet<String> captureKeys(SharedPreferences prefs, List<String> keys) {
        return PonyEnableAll.capture(prefs, keys);
    }

    static String currentWaifu(SharedPreferences prefs) {
        if (prefs == null) return "";
        String waifu = prefs.getString(PREF_WAIFU, "");
        return waifu != null ? waifu : "";
    }

    static int countBuiltIn(Set<String> keys) {
        if (keys == null || keys.isEmpty()) return 0;
        int n = 0;
        for (String key : keys) {
            if (key != null && !key.startsWith(CUSTOM_PREFIX)) n++;
        }
        return n;
    }

    static int countCustom(Set<String> keys) {
        if (keys == null || keys.isEmpty()) return 0;
        int n = 0;
        for (String key : keys) {
            if (key != null && key.startsWith(CUSTOM_PREFIX)) n++;
        }
        return n;
    }

    static List<Mix> loadUserMixes(SharedPreferences prefs) {
        if (prefs == null) return Collections.emptyList();
        return parse(prefs.getString(PREF_MIXES_JSON, ""));
    }

    static boolean hasName(SharedPreferences prefs, String rawName) {
        String name = normalizeName(rawName);
        if (name == null) return false;
        return indexOfName(loadUserMixes(prefs), name) >= 0;
    }

    static SaveResult save(SharedPreferences prefs, String rawName, Set<String> keys, String waifu) {
        if (prefs == null) return SaveResult.BAD_NAME;
        String name = normalizeName(rawName);
        if (name == null) return SaveResult.BAD_NAME;
        ArrayList<Mix> mixes = new ArrayList<Mix>(loadUserMixes(prefs));
        int idx = indexOfName(mixes, name);
        if (idx < 0 && mixes.size() >= MAX_USER_MIXES) return SaveResult.FULL;
        HashSet<String> copy = new HashSet<String>();
        if (keys != null) copy.addAll(keys);
        Mix next = idx >= 0
                ? new Mix(mixes.get(idx).id, name, copy, waifu)
                : new Mix(newId(), name, copy, waifu);
        if (idx >= 0) {
            mixes.set(idx, next);
        } else {
            mixes.add(next);
        }
        prefs.edit().putString(PREF_MIXES_JSON, encode(mixes)).commit();
        return idx >= 0 ? SaveResult.REPLACED : SaveResult.SAVED;
    }

    /**
     * Merge mixes from a library zip sidecar into the stored list.
     * Same name (case-insensitive) replaces; new names append until
     * {@link #MAX_USER_MIXES}. Does not change live checkboxes.
     */
    static MixMergeResult mergeImported(SharedPreferences prefs, String json) {
        MixMergeResult result = new MixMergeResult();
        if (prefs == null || json == null || json.length() == 0) {
            result.invalid = true;
            return result;
        }
        List<Mix> incoming;
        try {
            JSONObject root = new JSONObject(json);
            if (root.optJSONArray("mixes") == null) {
                result.invalid = true;
                return result;
            }
            incoming = parse(json);
        } catch (Exception e) {
            result.invalid = true;
            return result;
        }
        if (incoming.isEmpty()) return result;

        ArrayList<Mix> existing = new ArrayList<Mix>(loadUserMixes(prefs));
        HashSet<String> seenIds = new HashSet<String>();
        for (int i = 0; i < existing.size(); i++) {
            String id = existing.get(i).id;
            if (id.length() > 0) seenIds.add(id);
        }
        for (int i = 0; i < incoming.size(); i++) {
            Mix mix = incoming.get(i);
            if (mix == null || mix.name.length() == 0) continue;
            int idx = indexOfName(existing, mix.name);
            if (idx >= 0) {
                Mix previous = existing.get(idx);
                String id = mix.id;
                if (id.length() == 0 || (seenIds.contains(id) && !id.equals(previous.id))) {
                    id = previous.id;
                } else if (!id.equals(previous.id)) {
                    seenIds.remove(previous.id);
                    seenIds.add(id);
                }
                HashSet<String> keys = new HashSet<String>();
                keys.addAll(mix.keys);
                existing.set(idx, new Mix(id, mix.name, keys, mix.waifu));
                result.replaced++;
            } else if (existing.size() >= MAX_USER_MIXES) {
                result.skipped++;
            } else {
                String id = mix.id;
                if (id.length() == 0 || seenIds.contains(id)) id = newId();
                seenIds.add(id);
                HashSet<String> keys = new HashSet<String>();
                keys.addAll(mix.keys);
                existing.add(new Mix(id, mix.name, keys, mix.waifu));
                result.added++;
            }
        }
        if (result.added > 0 || result.replaced > 0) {
            prefs.edit().putString(PREF_MIXES_JSON, encode(existing)).commit();
        }
        return result;
    }

    static void deleteById(SharedPreferences prefs, String id) {
        if (prefs == null || id == null) return;
        ArrayList<Mix> mixes = new ArrayList<Mix>(loadUserMixes(prefs));
        boolean removed = false;
        for (int i = mixes.size() - 1; i >= 0; i--) {
            if (id.equals(mixes.get(i).id)) {
                mixes.remove(i);
                removed = true;
            }
        }
        if (removed) {
            if (mixes.isEmpty()) {
                prefs.edit().remove(PREF_MIXES_JSON).commit();
            } else {
                prefs.edit().putString(PREF_MIXES_JSON, encode(mixes)).commit();
            }
        }
    }

    /**
     * Replace every key in {@code herdKeys}; set waifu if it still exists.
     * Custom files are not loaded — missing {@code pref_custom_*} keys stay off.
     *
     * <p>Remembers the live herd as previous when this is the first load
     * after a home state, then marks the checkboxes as a loaded mix.
     */
    static void applyUserMix(Context context, Mix mix) {
        if (context == null || mix == null) return;
        applyUserMix(prefsOf(context), mix, AllPonies.allHerdKeys(context));
    }

    static void applyUserMix(SharedPreferences prefs, Mix mix, List<String> herdKeys) {
        if (prefs == null || mix == null || herdKeys == null || herdKeys.isEmpty()) return;
        beginProgrammaticHerdChange();
        try {
            SharedPreferences.Editor editor = prefs.edit();
            writePreviousHerdIfHome(prefs, editor, herdKeys);
            PonyEnableAll.writeReplace(editor, herdKeys, mix.keys);
            editor.putString(PREF_WAIFU, resolvedWaifu(mix.waifu, herdKeys));
            editor.putBoolean(PREF_VIEWING_LOADED_MIX, true);
            editor.commit();
        } finally {
            endProgrammaticHerdChange();
        }
    }

    /**
     * Replace only {@code builtInKeys}. Custom checkboxes and waifu are left
     * alone (stock category shortcuts).
     *
     * <p>{@code herdKeys} is the full built-in plus custom list, used only
     * for the previous-herd snapshot.
     */
    static void applyStockMix(Context context, Set<String> enabledBuiltIn) {
        if (context == null) return;
        applyStockMix(prefsOf(context), enabledBuiltIn, AllPonies.builtInPrefKeys(),
                AllPonies.allHerdKeys(context));
    }

    static void applyStockMix(SharedPreferences prefs, Set<String> enabledBuiltIn,
            List<String> builtInKeys, List<String> herdKeys) {
        if (prefs == null || builtInKeys == null || builtInKeys.isEmpty()) return;
        beginProgrammaticHerdChange();
        try {
            SharedPreferences.Editor editor = prefs.edit();
            writePreviousHerdIfHome(prefs, editor, herdKeys != null ? herdKeys : builtInKeys);
            PonyEnableAll.writeReplace(editor, builtInKeys, enabledBuiltIn);
            editor.putBoolean(PREF_VIEWING_LOADED_MIX, true);
            editor.commit();
        } finally {
            endProgrammaticHerdChange();
        }
    }

    /**
     * Restore the unnamed previous herd and mark the checkboxes as home.
     * Returns the stored mix, or {@code null} when there is nothing to apply.
     */
    static Mix applyPreviousHerd(Context context) {
        if (context == null) return null;
        return applyPreviousHerd(prefsOf(context), AllPonies.allHerdKeys(context));
    }

    static Mix applyPreviousHerd(SharedPreferences prefs, List<String> herdKeys) {
        Mix prev = loadPreviousHerd(prefs);
        if (prefs == null || prev == null || herdKeys == null || herdKeys.isEmpty()) return null;
        beginProgrammaticHerdChange();
        try {
            SharedPreferences.Editor editor = prefs.edit();
            PonyEnableAll.writeReplace(editor, herdKeys, prev.keys);
            editor.putString(PREF_WAIFU, resolvedWaifu(prev.waifu, herdKeys));
            editor.putBoolean(PREF_VIEWING_LOADED_MIX, false);
            editor.commit();
        } finally {
            endProgrammaticHerdChange();
        }
        return prev;
    }

    /**
     * First saved mix whose live checkboxes and favorite match {@code mix}.
     * Missing custom files are treated as off, same as apply.
     */
    static Mix matchingUserMix(SharedPreferences prefs, List<String> herdKeys) {
        List<Mix> mixes = loadUserMixes(prefs);
        for (int i = 0; i < mixes.size(); i++) {
            Mix mix = mixes.get(i);
            if (sameLiveHerd(prefs, mix, herdKeys)) return mix;
        }
        return null;
    }

    /**
     * Built-in group whose enabled set matches the live built-in checkboxes.
     * Custom ponies and favorite are ignored (stock shortcuts leave those).
     */
    static AllPonies.StockGroup matchingStockGroup(SharedPreferences prefs) {
        if (prefs == null) return null;
        List<String> builtIn = AllPonies.builtInPrefKeys();
        HashSet<String> live = captureKeys(prefs, builtIn);
        AllPonies.StockGroup[] groups = AllPonies.stockGroups();
        for (int i = 0; i < groups.length; i++) {
            AllPonies.StockGroup group = groups[i];
            HashSet<String> want = new HashSet<String>();
            for (int k = 0; k < group.keys.length; k++) {
                want.add(group.keys[k]);
            }
            if (live.equals(want)) return group;
        }
        return null;
    }

    static Mix loadPreviousHerd(SharedPreferences prefs) {
        if (prefs == null) return null;
        return parsePrevious(prefs.getString(PREF_PREVIOUS_HERD_JSON, ""));
    }

    /**
     * True when a previous herd exists, still has at least one live pony,
     * and differs from the current checkboxes or favorite.
     */
    static boolean hasPreviousHerdDistinct(SharedPreferences prefs, List<String> herdKeys) {
        Mix prev = loadPreviousHerd(prefs);
        if (prev == null || retainedCount(prev.keys, herdKeys) == 0) return false;
        return !sameLiveHerd(prefs, prev, herdKeys);
    }

    /**
     * A user checkbox or favorite change leaves the loaded-mix state so the
     * next load can refresh the previous-herd snapshot.
     */
    static void noteManualHerdEdit(SharedPreferences prefs) {
        if (prefs == null || programmaticHerdDepth > 0) return;
        if (!prefs.getBoolean(PREF_VIEWING_LOADED_MIX, false)) return;
        prefs.edit().putBoolean(PREF_VIEWING_LOADED_MIX, false).commit();
    }

    static void beginProgrammaticHerdChange() {
        programmaticHerdDepth++;
    }

    static void endProgrammaticHerdChange() {
        if (programmaticHerdDepth > 0) programmaticHerdDepth--;
    }

    /**
     * Write previous-herd when the live checkboxes are still a home state.
     * An all-off home falls back to Disable-all snapshots so mute-then-load
     * does not lose the usual herd. Empty captures do not overwrite.
     */
    static void writePreviousHerdIfHome(SharedPreferences prefs, SharedPreferences.Editor editor,
            List<String> herdKeys) {
        if (prefs == null || editor == null || herdKeys == null) return;
        if (prefs.getBoolean(PREF_VIEWING_LOADED_MIX, false)) return;
        HashSet<String> on = captureKeys(prefs, herdKeys);
        if (on.isEmpty()) {
            on = snapshotFallback(prefs, herdKeys);
            if (on.isEmpty()) return;
        }
        editor.putString(PREF_PREVIOUS_HERD_JSON, encodePrevious(on, currentWaifu(prefs)));
    }

    /**
     * Drop a custom-pony key from the unnamed previous herd on the same
     * editor. Callers must {@code commit}.
     */
    static void removeKeyFromPreviousHerd(SharedPreferences prefs, SharedPreferences.Editor editor,
            String prefKey) {
        if (prefs == null || editor == null || prefKey == null) return;
        Mix prev = loadPreviousHerd(prefs);
        if (prev == null) return;
        boolean dropKey = prev.keys.contains(prefKey);
        boolean dropWaifu = prefKey.equals(prev.waifu);
        if (!dropKey && !dropWaifu) return;
        HashSet<String> keys = new HashSet<String>(prev.keys);
        keys.remove(prefKey);
        if (keys.isEmpty()) {
            editor.remove(PREF_PREVIOUS_HERD_JSON);
            return;
        }
        String waifu = dropWaifu ? "" : prev.waifu;
        editor.putString(PREF_PREVIOUS_HERD_JSON, encodePrevious(keys, waifu));
    }

    /**
     * Drop a custom-pony key from every saved mix (and waifu) on the same
     * editor. Callers must {@code commit}.
     */
    static void removeKeyFromAllMixes(SharedPreferences prefs, SharedPreferences.Editor editor,
            String prefKey) {
        if (prefs == null || editor == null || prefKey == null) return;
        List<Mix> mixes = loadUserMixes(prefs);
        if (mixes.isEmpty()) return;
        ArrayList<Mix> next = new ArrayList<Mix>(mixes.size());
        boolean changed = false;
        for (int i = 0; i < mixes.size(); i++) {
            Mix mix = mixes.get(i);
            boolean dropKey = mix.keys.contains(prefKey);
            boolean dropWaifu = prefKey.equals(mix.waifu);
            if (!dropKey && !dropWaifu) {
                next.add(mix);
                continue;
            }
            changed = true;
            HashSet<String> keys = new HashSet<String>(mix.keys);
            keys.remove(prefKey);
            String waifu = dropWaifu ? "" : mix.waifu;
            next.add(new Mix(mix.id, mix.name, keys, waifu));
        }
        if (!changed) return;
        if (next.isEmpty()) {
            editor.remove(PREF_MIXES_JSON);
        } else {
            editor.putString(PREF_MIXES_JSON, encode(next));
        }
    }

    static String encode(List<Mix> mixes) {
        JSONObject root = new JSONObject();
        JSONArray arr = new JSONArray();
        try {
            if (mixes != null) {
                for (int i = 0; i < mixes.size(); i++) {
                    Mix mix = mixes.get(i);
                    if (mix == null || mix.name.length() == 0) continue;
                    JSONObject obj = new JSONObject();
                    obj.put("id", mix.id.length() == 0 ? newId() : mix.id);
                    obj.put("name", mix.name);
                    obj.put("waifu", mix.waifu);
                    JSONArray keys = new JSONArray();
                    ArrayList<String> sorted = new ArrayList<String>(mix.keys);
                    Collections.sort(sorted);
                    for (int k = 0; k < sorted.size(); k++) {
                        String key = sorted.get(k);
                        if (key != null && key.length() > 0) keys.put(key);
                    }
                    obj.put("keys", keys);
                    arr.put(obj);
                }
            }
            root.put("version", 1);
            root.put("mixes", arr);
            return root.toString();
        } catch (Exception e) {
            return "{\"mixes\":[]}";
        }
    }

    static List<Mix> parse(String json) {
        if (json == null || json.length() == 0) return Collections.emptyList();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("mixes");
            if (arr == null || arr.length() == 0) return Collections.emptyList();
            ArrayList<Mix> out = new ArrayList<Mix>(arr.length());
            HashSet<String> seenIds = new HashSet<String>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String name = normalizeName(obj.optString("name", ""));
                if (name == null) continue;
                String id = obj.optString("id", "");
                if (id.length() == 0 || seenIds.contains(id)) id = newId();
                seenIds.add(id);
                HashSet<String> keys = new HashSet<String>();
                JSONArray keyArr = obj.optJSONArray("keys");
                if (keyArr != null) {
                    for (int k = 0; k < keyArr.length(); k++) {
                        String key = keyArr.optString(k, "");
                        if (key.length() > 0) keys.add(key);
                    }
                }
                String waifu = obj.optString("waifu", "");
                out.add(new Mix(id, name, keys, waifu));
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    static String encodePrevious(Set<String> keys, String waifu) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("waifu", waifu != null ? waifu : "");
            JSONArray arr = new JSONArray();
            ArrayList<String> sorted = new ArrayList<String>();
            if (keys != null) sorted.addAll(keys);
            Collections.sort(sorted);
            for (int i = 0; i < sorted.size(); i++) {
                String key = sorted.get(i);
                if (key != null && key.length() > 0) arr.put(key);
            }
            obj.put("keys", arr);
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    static Mix parsePrevious(String json) {
        if (json == null || json.length() == 0) return null;
        try {
            JSONObject obj = new JSONObject(json);
            HashSet<String> keys = new HashSet<String>();
            JSONArray keyArr = obj.optJSONArray("keys");
            if (keyArr != null) {
                for (int k = 0; k < keyArr.length(); k++) {
                    String key = keyArr.optString(k, "");
                    if (key.length() > 0) keys.add(key);
                }
            }
            if (keys.isEmpty()) return null;
            return new Mix("", "", keys, obj.optString("waifu", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sameLiveHerd(SharedPreferences prefs, Mix mix, List<String> herdKeys) {
        HashSet<String> live = captureKeys(prefs, herdKeys);
        HashSet<String> mixLive = retainedKeys(mix != null ? mix.keys : null, herdKeys);
        if (!live.equals(mixLive)) return false;
        String mixWaifu = resolvedWaifu(mix != null ? mix.waifu : "", herdKeys);
        return mixWaifu.equals(resolvedWaifu(currentWaifu(prefs), herdKeys));
    }

    private static HashSet<String> retainedKeys(Set<String> keys, List<String> herdKeys) {
        HashSet<String> out = new HashSet<String>();
        if (keys == null || herdKeys == null) return out;
        for (int i = 0; i < herdKeys.size(); i++) {
            String key = herdKeys.get(i);
            if (keys.contains(key)) out.add(key);
        }
        return out;
    }

    private static int retainedCount(Set<String> keys, List<String> herdKeys) {
        return retainedKeys(keys, herdKeys).size();
    }

    private static HashSet<String> snapshotFallback(SharedPreferences prefs, List<String> herdKeys) {
        HashSet<String> on = new HashSet<String>();
        if (prefs == null) return on;
        on.addAll(retainedKeys(prefs.getStringSet(PonyEnableAll.PREF_PONIES_SNAPSHOT, null), herdKeys));
        on.addAll(retainedKeys(prefs.getStringSet(PonyEnableAll.PREF_CUSTOM_SNAPSHOT, null), herdKeys));
        return on;
    }

    private static String resolvedWaifu(String waifu, List<String> herdKeys) {
        if (waifu == null || waifu.length() == 0) return "";
        if (herdKeys == null) return "";
        for (int i = 0; i < herdKeys.size(); i++) {
            if (waifu.equals(herdKeys.get(i))) return waifu;
        }
        return "";
    }

    private static int indexOfName(List<Mix> mixes, String name) {
        if (mixes == null || name == null) return -1;
        for (int i = 0; i < mixes.size(); i++) {
            if (name.equalsIgnoreCase(mixes.get(i).name)) return i;
        }
        return -1;
    }

    private static SharedPreferences prefsOf(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
