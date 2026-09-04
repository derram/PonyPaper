package uk.cpjsmith.ponypaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stable semantic stems for built-in pony actions. Catalog / scene JSON use
 * these strings — never Android {@code R.array} ints. Custom ponies use the
 * XML {@code <action name>} instead (attached in custom pony construction).
 */
final class BuiltInActionIds {

    private static final HashMap<String, String> SIMPLE_PREFIX =
            new HashMap<String, String>();

    static {
        // ponyKey → resource prefix stripped at make* time.
        SIMPLE_PREFIX.put("pref_ab", "ab_");
        SIMPLE_PREFIX.put("pref_aj", "aj_");
        SIMPLE_PREFIX.put("pref_babs", "babs_");
        SIMPLE_PREFIX.put("pref_bp", "bp_");
        SIMPLE_PREFIX.put("pref_bigmac", "bigmac_");
        SIMPLE_PREFIX.put("pref_derpy", "derpy_");
        SIMPLE_PREFIX.put("pref_doctor", "doctor_");
        SIMPLE_PREFIX.put("pref_ember", "ember_");
        SIMPLE_PREFIX.put("pref_fs", "fs_");
        SIMPLE_PREFIX.put("pref_gallus", "gallus_");
        SIMPLE_PREFIX.put("pref_gilda", "gilda_");
        SIMPLE_PREFIX.put("pref_lyra", "lyra_");
        SIMPLE_PREFIX.put("pref_minuette", "minuette_");
        SIMPLE_PREFIX.put("pref_ocellus", "ocellus_");
        SIMPLE_PREFIX.put("pref_octavia", "octavia_");
        SIMPLE_PREFIX.put("pref_pp", "pp_");
        SIMPLE_PREFIX.put("pref_cadance", "cadance_");
        SIMPLE_PREFIX.put("pref_celestia", "celestia_");
        SIMPLE_PREFIX.put("pref_luna", "luna_");
        SIMPLE_PREFIX.put("pref_rd", "rd_");
        SIMPLE_PREFIX.put("pref_rainbowshine", "rainbowshine_");
        SIMPLE_PREFIX.put("pref_rarity", "rarity_");
        SIMPLE_PREFIX.put("pref_roseluck", "roseluck_");
        SIMPLE_PREFIX.put("pref_sandbar", "sandbar_");
        SIMPLE_PREFIX.put("pref_scootaloo", "scootaloo_");
        SIMPLE_PREFIX.put("pref_sa", "sa_");
        SIMPLE_PREFIX.put("pref_silverstream", "silverstream_");
        SIMPLE_PREFIX.put("pref_smolder", "smolder_");
        SIMPLE_PREFIX.put("pref_soarin", "soarin_");
        SIMPLE_PREFIX.put("pref_spike", "spike_");
        SIMPLE_PREFIX.put("pref_spitfire", "spitfire_");
        SIMPLE_PREFIX.put("pref_sg", "sg_");
        SIMPLE_PREFIX.put("pref_ss", "ss_");
        SIMPLE_PREFIX.put("pref_sunburst", "sunburst_");
        SIMPLE_PREFIX.put("pref_sb", "sb_");
        SIMPLE_PREFIX.put("pref_sd", "sd_");
        SIMPLE_PREFIX.put("pref_thorax", "thorax_");
        SIMPLE_PREFIX.put("pref_trixie", "trixie_");
        // pref_ts: dual remap pts_→alicorn_*, ts_→unicorn_* (see stem).
        SIMPLE_PREFIX.put("pref_vinyl", "vinyl_");
        SIMPLE_PREFIX.put("pref_yona", "yona_");
        SIMPLE_PREFIX.put("pref_zecora", "zecora_");
    }

    private static final String[] MOVER_EXACT = {
            "trot", "walk", "fly", "flyud", "bounce", "dance", "moonwalk", "trotdrunk"
    };

    private static final String[] MOVER_SUFFIX = {
            "_trot", "_walk", "_fly", "_flyud", "_bounce", "_dance", "_moonwalk",
            "_trotdrunk"
    };

    private BuiltInActionIds() {
    }

    /** True when {@code ponyKey} is a known built-in preference key. */
    static boolean isBuiltInKey(String ponyKey) {
        if (ponyKey == null) return false;
        return "pref_ts".equals(ponyKey) || SIMPLE_PREFIX.containsKey(ponyKey);
    }

    /**
     * Every built-in pref key (prefix table + Twilight). Canonical set for
     * {@link #isBuiltInKey} / factory sync checks.
     */
    static List<String> allKeys() {
        ArrayList<String> keys = new ArrayList<String>(SIMPLE_PREFIX.size() + 1);
        keys.add("pref_ts");
        keys.addAll(SIMPLE_PREFIX.keySet());
        Collections.sort(keys);
        return keys;
    }

    /**
     * Maps a resource entry stem (e.g. {@code lyra_sit}, {@code pts_stand}) to
     * the stable action id stored on the sprite-owning {@link PonyAction}.
     */
    static String stem(String ponyKey, String resourceEntryStem) {
        if (resourceEntryStem == null || resourceEntryStem.length() == 0) {
            return "";
        }
        if ("pref_ts".equals(ponyKey)) {
            if (resourceEntryStem.startsWith("pts_")) {
                return "alicorn_" + resourceEntryStem.substring(4);
            }
            if (resourceEntryStem.startsWith("ts_")) {
                return "unicorn_" + resourceEntryStem.substring(3);
            }
            return resourceEntryStem;
        }
        String prefix = SIMPLE_PREFIX.get(ponyKey);
        if (prefix != null && resourceEntryStem.startsWith(prefix)) {
            return resourceEntryStem.substring(prefix.length());
        }
        return resourceEntryStem;
    }

    /**
     * In-place mover iff exact gait name or ends with a gait suffix
     * ({@code alicorn_trot}, {@code cape_trot}, …). Otherwise idle pose.
     */
    static boolean isInPlaceMover(String actionId) {
        if (actionId == null || actionId.length() == 0) return false;
        for (int i = 0; i < MOVER_EXACT.length; i++) {
            if (actionId.equals(MOVER_EXACT[i])) return true;
        }
        for (int i = 0; i < MOVER_SUFFIX.length; i++) {
            if (actionId.endsWith(MOVER_SUFFIX[i])) return true;
        }
        return false;
    }

    /**
     * Stems never offered in the Tableau action multi-choice: bare
     * {@code drag}/{@code teleport*} and remapped forms ({@code unicorn_drag},
     * {@code alicorn_teleportout}, …).
     */
    static boolean isExcludedSelectableStem(String actionId) {
        if (actionId == null || actionId.length() == 0) return false;
        return "drag".equals(actionId)
                || "teleportout".equals(actionId)
                || "teleportin".equals(actionId)
                || actionId.endsWith("_drag")
                || actionId.endsWith("_teleportout")
                || actionId.endsWith("_teleportin");
    }

    /**
     * Golden checks for stem remap, mover classification, and exclusions.
     * Returns null when all assertions pass; otherwise a short failure description.
     */
    static String selfCheck() {
        if (!"sit".equals(stem("pref_lyra", "lyra_sit"))) {
            return "lyra_sit→sit";
        }
        if (!"stand".equals(stem("pref_aj", "aj_stand"))) {
            return "aj_stand→stand";
        }
        if (!"stand".equals(stem("pref_sunburst", "sunburst_stand"))
                || !"trot".equals(stem("pref_sunburst", "sunburst_trot"))) {
            return "sunburst stand/trot";
        }
        if (!"cape_stand".equals(stem("pref_sunburst", "sunburst_cape_stand"))) {
            return "sunburst_cape_stand→cape_stand";
        }
        if (!"cape_trot".equals(stem("pref_sunburst", "sunburst_cape_trot"))) {
            return "sunburst_cape_trot→cape_trot";
        }
        // Twilight full dual-sheet set.
        if (!"alicorn_stand".equals(stem("pref_ts", "pts_stand"))
                || !"alicorn_trot".equals(stem("pref_ts", "pts_trot"))
                || !"alicorn_fly".equals(stem("pref_ts", "pts_fly"))
                || !"alicorn_teleportout".equals(stem("pref_ts", "pts_teleportout"))
                || !"alicorn_teleportin".equals(stem("pref_ts", "pts_teleportin"))) {
            return "pts_* remap";
        }
        if (!"unicorn_stand".equals(stem("pref_ts", "ts_stand"))
                || !"unicorn_trot".equals(stem("pref_ts", "ts_trot"))
                || !"unicorn_teleportout".equals(stem("pref_ts", "ts_teleportout"))
                || !"unicorn_teleportin".equals(stem("pref_ts", "ts_teleportin"))
                || !"unicorn_drag".equals(stem("pref_ts", "ts_drag"))) {
            return "ts_* remap";
        }
        // Owl crossing clip keeps a bare id (not ts_/pts_ remapped).
        if (!"owl_trot".equals(stem("pref_ts", "owl_trot"))
                || !isInPlaceMover("owl_trot")) {
            return "owl_trot stem/mover";
        }
        // Sunset teleports.
        if (!"teleportout".equals(stem("pref_ss", "ss_teleportout"))
                || !"teleportin".equals(stem("pref_ss", "ss_teleportin"))) {
            return "ss teleport stems";
        }
        // Vinyl / Berry / Derpy samples.
        if (!"dance".equals(stem("pref_vinyl", "vinyl_dance"))
                || !"moonwalk".equals(stem("pref_vinyl", "vinyl_moonwalk"))) {
            return "vinyl stems";
        }
        if (!"standdrunk".equals(stem("pref_bp", "bp_standdrunk"))
                || !"trotdrunk".equals(stem("pref_bp", "bp_trotdrunk"))) {
            return "berry stems";
        }
        if (!"hover".equals(stem("pref_derpy", "derpy_hover"))
                || !"hoverud".equals(stem("pref_derpy", "derpy_hoverud"))
                || !"flyud".equals(stem("pref_derpy", "derpy_flyud"))) {
            return "derpy stems";
        }
        if (!isInPlaceMover("trot") || !isInPlaceMover("walk")
                || !isInPlaceMover("fly") || !isInPlaceMover("flyud")
                || !isInPlaceMover("bounce") || !isInPlaceMover("dance")
                || !isInPlaceMover("moonwalk") || !isInPlaceMover("trotdrunk")) {
            return "mover exact";
        }
        if (!isInPlaceMover("alicorn_trot") || !isInPlaceMover("unicorn_fly")
                || !isInPlaceMover("cape_trot")) {
            return "mover suffix";
        }
        if (isInPlaceMover("stand") || isInPlaceMover("alicorn_stand")
                || isInPlaceMover("unicorn_stand") || isInPlaceMover("sit")
                || isInPlaceMover("hover") || isInPlaceMover("hoverud")
                || isInPlaceMover("cape_stand") || isInPlaceMover("standdrunk")) {
            return "idle misclassified as mover";
        }
        if (!isExcludedSelectableStem("drag")
                || !isExcludedSelectableStem("teleportout")
                || !isExcludedSelectableStem("teleportin")
                || !isExcludedSelectableStem("unicorn_drag")
                || !isExcludedSelectableStem("alicorn_teleportout")
                || !isExcludedSelectableStem("alicorn_teleportin")
                || !isExcludedSelectableStem("unicorn_teleportout")) {
            return "exclusion stems";
        }
        if (!isBuiltInKey("pref_ts") || !isBuiltInKey("pref_sunburst")
                || isBuiltInKey("pref_custom_foo.xml")) {
            return "isBuiltInKey";
        }
        if (allKeys().size() != SIMPLE_PREFIX.size() + 1) {
            return "allKeys size";
        }
        return null;
    }

    /**
     * Assert {@code otherKeys} is exactly the built-in key set. Returns null on
     * match; otherwise a short mismatch description.
     */
    static String selfCheckKeySetEquals(List<String> otherKeys, String label) {
        if (otherKeys == null) return label + " null";
        Set<String> expected = new HashSet<String>(allKeys());
        Set<String> actual = new HashSet<String>(otherKeys);
        if (expected.equals(actual)) return null;
        return label + " key mismatch (expected " + expected.size()
                + ", got " + actual.size() + ")";
    }
}
