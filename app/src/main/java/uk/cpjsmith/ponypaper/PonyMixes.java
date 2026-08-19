package uk.cpjsmith.ponypaper;

import android.content.SharedPreferences;
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
 * <p>Custom ponies are stored by preference key ({@code pref_custom_} +
 * filename). Missing files are skipped on load; they are not copied into
 * the mix.
 */
final class PonyMixes {

    static final String PREF_MIXES_JSON = "pref_mixes_json";
    static final String PREF_WAIFU = "pref_waifu";
    static final String CUSTOM_PREFIX = "pref_custom_";
    static final int MAX_USER_MIXES = 20;
    static final int MAX_NAME_LENGTH = 40;

    enum SaveResult {
        SAVED,
        REPLACED,
        FULL,
        BAD_NAME
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
     */
    static void applyUserMix(SharedPreferences prefs, Mix mix, List<String> herdKeys) {
        if (prefs == null || mix == null || herdKeys == null || herdKeys.isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        PonyEnableAll.writeReplace(editor, herdKeys, mix.keys);
        editor.putString(PREF_WAIFU, resolvedWaifu(mix.waifu, herdKeys));
        editor.commit();
    }

    /**
     * Replace only {@code builtInKeys}. Custom checkboxes and waifu are left
     * alone (stock category shortcuts).
     */
    static void applyStockMix(SharedPreferences prefs, Set<String> enabledBuiltIn, List<String> builtInKeys) {
        if (prefs == null || builtInKeys == null || builtInKeys.isEmpty()) return;
        PonyEnableAll.applyReplace(prefs, builtInKeys, enabledBuiltIn);
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

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
