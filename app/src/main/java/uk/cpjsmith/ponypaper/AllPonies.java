package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;

/**
 * Contains the definitions of the available ponies.
 */
public class AllPonies {
    
    public static final FilenameFilter xmlFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
            return filename.endsWith(".xml");
        }
    };
    
    /**
     * Class is not instantiable.
     */
    private AllPonies() {
    }
    
    /** Discrete gait factors (formerly global chooseGait constants). */
    private static final float SPEED_STROLL = 0.5f;
    private static final float SPEED_WALK = 0.7f;
    
    /**
     * Same sprites as {@code source}, different {@link PonyAction#speed}.
     * Used for stroll/walk/trot and slow/fast idle variants without reloading bitmaps.
     */
    private static PonyAction alias(PonyAction source, float speed) {
        return new PonyAction(source, speed);
    }
    
    /**
     * Historical chooseGait weights: stroll 1/5, walk 3/5, full 1/5.
     * {@code fullSpeedMove} should already be at speed 1.
     */
    private static PonyAction[] defaultGaits(PonyAction fullSpeedMove) {
        return new PonyAction[] {
            alias(fullSpeedMove, SPEED_STROLL),
            alias(fullSpeedMove, SPEED_WALK),
            alias(fullSpeedMove, SPEED_WALK),
            alias(fullSpeedMove, SPEED_WALK),
            fullSpeedMove
        };
    }
    
    /**
     * Historical idle 50/50 full-rate vs walk-rate animation.
     * {@code fullSpeedStand} should already be at speed 1.
     */
    private static PonyAction[] defaultIdles(PonyAction fullSpeedStand) {
        return new PonyAction[] {
            fullSpeedStand,
            alias(fullSpeedStand, SPEED_WALK)
        };
    }
    
    private static PonyAction[] concat(PonyAction[] a, PonyAction[] b) {
        PonyAction[] out = new PonyAction[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
    
    private static PonyAction[] concat(PonyAction[] a, PonyAction[] b, PonyAction[] c) {
        return concat(concat(a, b), c);
    }
    
    /**
     * True when {@link #createPony} would likely return non-null for
     * {@code ponyKey}. Built-ins are exact; customs only check key shape
     * (missing files still drop at create time). Allocation-free for Tableau
     * cap math.
     */
    public static boolean canCreatePony(String ponyKey) {
        if (ponyKey == null) return false;
        if (BuiltInActionIds.isBuiltInKey(ponyKey)) return true;
        if (ponyKey.startsWith(PonyMixes.CUSTOM_PREFIX)) {
            String name = ponyKey.substring(PonyMixes.CUSTOM_PREFIX.length());
            return name.length() > 0 && name.endsWith(".xml")
                    && name.indexOf('/') < 0 && name.indexOf('\\') < 0;
        }
        return false;
    }

    /**
     * Checkbox-free factory for any built-in or custom pony key.
     *
     * @param ponyKey preference key such as {@code pref_ts} or
     *                {@code pref_custom_foo.xml}
     * @return a new pony instance, or {@code null} if unknown / unloadable
     */
    public static Pony createPony(Context context, String ponyKey) {
        if (context == null || ponyKey == null || !canCreatePony(ponyKey)) {
            return null;
        }
        if (ponyKey.startsWith(PonyMixes.CUSTOM_PREFIX)) {
            return loadCustomPonyUnchecked(context, ponyKey);
        }
        Resources res = context.getResources();
        Pony pony = makeBuiltIn(res, ponyKey);
        return pony != null ? pony.withPrefKey(ponyKey) : null;
    }

    /**
     * Actions keyed by stable id, plus idle / in-place / selectable lists for
     * the Tableau editor. {@code null} when the pony cannot be created.
     */
    public static ActionCatalog actionCatalog(Context context, String ponyKey) {
        Pony pony = createPony(context, ponyKey);
        if (pony == null) return null;
        return buildActionCatalog(pony);
    }

    /**
     * Catalog of poses for one pony. Speed aliases share an id with their
     * sprite owner and are not listed separately; named custom
     * {@code spritesfrom} aliases keep distinct XML ids.
     */
    public static final class ActionCatalog {
        /** Id'd actions; sprite owner preferred when ids collide. */
        public final Map<String, PonyAction> byId;
        /** Idle-oriented poses (stand, sit, hover, …). */
        public final List<String> idlePoseIds;
        /** Sheets offered as in-place animation (trot, walk, fly, dance, …). */
        public final List<String> inPlaceMoverIds;
        /** idlePoseIds + inPlaceMoverIds (editor multi-choice union). */
        public final List<String> selectableIds;

        ActionCatalog(Map<String, PonyAction> byId, List<String> idlePoseIds,
                List<String> inPlaceMoverIds, List<String> selectableIds) {
            this.byId = byId;
            this.idlePoseIds = idlePoseIds;
            this.inPlaceMoverIds = inPlaceMoverIds;
            this.selectableIds = selectableIds;
        }
    }

    /**
     * Build catalog from an already-constructed pony. Id'd actions only;
     * sprite owner preferred when ids collide; named custom aliases are
     * distinct entries.
     */
    static ActionCatalog buildActionCatalog(Pony pony) {
        LinkedHashMap<String, PonyAction> byId = new LinkedHashMap<String, PonyAction>();
        if (pony == null) {
            return emptyCatalog();
        }
        PonyAction[] all = pony.getAllActions();
        if (all != null) {
            for (int i = 0; i < all.length; i++) {
                PonyAction action = all[i];
                if (action == null) continue;
                String id = action.actionId();
                if (id.length() == 0) continue;
                PonyAction existing = byId.get(id);
                if (existing == null) {
                    byId.put(id, action);
                } else if (existing.isAlias() && !action.isAlias()) {
                    byId.put(id, action);
                }
            }
        }
        ArrayList<String> idle = new ArrayList<String>();
        ArrayList<String> movers = new ArrayList<String>();
        ArrayList<String> selectable = new ArrayList<String>();
        for (Map.Entry<String, PonyAction> e : byId.entrySet()) {
            String id = e.getKey();
            PonyAction action = e.getValue();
            if (!isSelectableCatalogAction(action, id)) continue;
            selectable.add(id);
            if (BuiltInActionIds.isInPlaceMover(id)) {
                movers.add(id);
            } else {
                idle.add(id);
            }
        }
        return new ActionCatalog(
                Collections.unmodifiableMap(byId),
                Collections.unmodifiableList(idle),
                Collections.unmodifiableList(movers),
                Collections.unmodifiableList(selectable));
    }

    private static ActionCatalog emptyCatalog() {
        return new ActionCatalog(
                Collections.<String, PonyAction>emptyMap(),
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.<String>emptyList());
    }

    private static boolean isSelectableCatalogAction(PonyAction action, String id) {
        if (action.type != PonyAction.NORMAL) return false;
        return !BuiltInActionIds.isExcludedSelectableStem(id);
    }

    /**
     * Preferred empty-bag fallback: stand, alicorn_stand, unicorn_stand, else
     * first selectable. {@code null} when the catalog has nothing selectable.
     */
    static PonyAction preferredDefaultAction(ActionCatalog catalog) {
        if (catalog == null || catalog.selectableIds.isEmpty()) return null;
        String[] preferred = {"stand", "alicorn_stand", "unicorn_stand"};
        for (int i = 0; i < preferred.length; i++) {
            if (catalog.selectableIds.contains(preferred[i])) {
                return catalog.byId.get(preferred[i]);
            }
        }
        String first = catalog.selectableIds.get(0);
        return catalog.byId.get(first);
    }

    /**
     * Single factory table for built-ins — kept in sync with
     * {@link BuiltInActionIds#allKeys()} and {@link #stockGroups()} via
     * {@link #selfCheck()}.
     */
    private static final Map<String, Function<Resources, Pony>> BUILTIN_BY_KEY;

    static {
        HashMap<String, Function<Resources, Pony>> m =
                new HashMap<String, Function<Resources, Pony>>();
        m.put("pref_ab", AllPonies::makeAppleBloom);
        m.put("pref_aj", AllPonies::makeApplejack);
        m.put("pref_babs", AllPonies::makeBabsSeed);
        m.put("pref_bp", AllPonies::makeBerryPunch);
        m.put("pref_bigmac", AllPonies::makeBigMcIntosh);
        m.put("pref_derpy", AllPonies::makeDerpyHooves);
        m.put("pref_doctor", AllPonies::makeDoctorHooves);
        m.put("pref_ember", AllPonies::makeEmber);
        m.put("pref_fs", AllPonies::makeFluttershy);
        m.put("pref_gallus", AllPonies::makeGallus);
        m.put("pref_gilda", AllPonies::makeGilda);
        m.put("pref_lyra", AllPonies::makeLyraHeartstrings);
        m.put("pref_minuette", AllPonies::makeMinuette);
        m.put("pref_ocellus", AllPonies::makeOcellus);
        m.put("pref_octavia", AllPonies::makeOctavia);
        m.put("pref_pp", AllPonies::makePinkiePie);
        m.put("pref_cadance", AllPonies::makePrincessCadance);
        m.put("pref_celestia", AllPonies::makePrincessCelestia);
        m.put("pref_luna", AllPonies::makePrincessLuna);
        m.put("pref_rd", AllPonies::makeRainbowDash);
        m.put("pref_rainbowshine", AllPonies::makeRainbowshine);
        m.put("pref_rarity", AllPonies::makeRarity);
        m.put("pref_roseluck", AllPonies::makeRoseluck);
        m.put("pref_sandbar", AllPonies::makeSandbar);
        m.put("pref_scootaloo", AllPonies::makeScootaloo);
        m.put("pref_sa", AllPonies::makeShiningArmor);
        m.put("pref_silverstream", AllPonies::makeSilverstream);
        m.put("pref_smolder", AllPonies::makeSmolder);
        m.put("pref_soarin", AllPonies::makeSoarin);
        m.put("pref_spike", AllPonies::makeSpike);
        m.put("pref_spitfire", AllPonies::makeSpitfire);
        m.put("pref_sg", AllPonies::makeStarlightGlimmer);
        m.put("pref_ss", AllPonies::makeSunsetShimmer);
        m.put("pref_sunburst", AllPonies::makeSunburst);
        m.put("pref_sb", AllPonies::makeSweetieBelle);
        m.put("pref_sd", AllPonies::makeSweetieDrops);
        m.put("pref_thorax", AllPonies::makeThorax);
        m.put("pref_trixie", AllPonies::makeTrixie);
        m.put("pref_ts", AllPonies::makeTwilightSparkle);
        m.put("pref_vinyl", AllPonies::makeVinylScratch);
        m.put("pref_yona", AllPonies::makeYona);
        m.put("pref_zecora", AllPonies::makeZecora);
        BUILTIN_BY_KEY = Collections.unmodifiableMap(m);
        if (BuildConfig.DEBUG) {
            String fail = selfCheck();
            if (fail != null) {
                throw new AssertionError("AllPonies.selfCheck: " + fail);
            }
        }
    }

    private static Pony makeBuiltIn(Resources res, String ponyKey) {
        Function<Resources, Pony> maker = BUILTIN_BY_KEY.get(ponyKey);
        return maker != null ? maker.apply(res) : null;
    }

    /**
     * Stem / mover goldens plus factory ↔ prefix ↔ stockGroups key sync.
     * Invoked from the debug static initializer so mismatches fail closed.
     */
    static String selfCheck() {
        String fail = BuiltInActionIds.selfCheck();
        if (fail != null) return fail;
        fail = BuiltInActionIds.selfCheckKeySetEquals(
                new ArrayList<String>(BUILTIN_BY_KEY.keySet()), "factory");
        if (fail != null) return fail;
        fail = BuiltInActionIds.selfCheckKeySetEquals(builtInPrefKeys(), "stockGroups");
        if (fail != null) return fail;
        return null;
    }

    /**
     * Returns the complete list of ponies.
     * 
     * @param context the current application context
     * @param prefs   the user's preferences of which ponies to load
     * @return ponies, so many ponies
     */
    public static ArrayList<Pony> getPonies(Context context, SharedPreferences prefs) {
        ArrayList<Pony> result = new ArrayList<Pony>();
        
        Resources res = context.getResources();
        if (prefs.getBoolean("pref_ab", true)) result.add(makeAppleBloom(res).withPrefKey("pref_ab"));
        if (prefs.getBoolean("pref_aj", true)) result.add(makeApplejack(res).withPrefKey("pref_aj"));
        if (prefs.getBoolean("pref_babs", true)) result.add(makeBabsSeed(res).withPrefKey("pref_babs"));
        if (prefs.getBoolean("pref_bp", true)) result.add(makeBerryPunch(res).withPrefKey("pref_bp"));
        if (prefs.getBoolean("pref_bigmac", true)) result.add(makeBigMcIntosh(res).withPrefKey("pref_bigmac"));
        if (prefs.getBoolean("pref_derpy", true)) result.add(makeDerpyHooves(res).withPrefKey("pref_derpy"));
        if (prefs.getBoolean("pref_doctor", true)) result.add(makeDoctorHooves(res).withPrefKey("pref_doctor"));
        if (prefs.getBoolean("pref_ember", true)) result.add(makeEmber(res).withPrefKey("pref_ember"));
        if (prefs.getBoolean("pref_fs", true)) result.add(makeFluttershy(res).withPrefKey("pref_fs"));
        if (prefs.getBoolean("pref_gallus", true)) result.add(makeGallus(res).withPrefKey("pref_gallus"));
        if (prefs.getBoolean("pref_gilda", true)) result.add(makeGilda(res).withPrefKey("pref_gilda"));
        if (prefs.getBoolean("pref_lyra", true)) result.add(makeLyraHeartstrings(res).withPrefKey("pref_lyra"));
        if (prefs.getBoolean("pref_minuette", true)) result.add(makeMinuette(res).withPrefKey("pref_minuette"));
        if (prefs.getBoolean("pref_ocellus", true)) result.add(makeOcellus(res).withPrefKey("pref_ocellus"));
        if (prefs.getBoolean("pref_octavia", true)) result.add(makeOctavia(res).withPrefKey("pref_octavia"));
        if (prefs.getBoolean("pref_pp", true)) result.add(makePinkiePie(res).withPrefKey("pref_pp"));
        if (prefs.getBoolean("pref_cadance", true)) result.add(makePrincessCadance(res).withPrefKey("pref_cadance"));
        if (prefs.getBoolean("pref_celestia", true)) result.add(makePrincessCelestia(res).withPrefKey("pref_celestia"));
        if (prefs.getBoolean("pref_luna", true)) result.add(makePrincessLuna(res).withPrefKey("pref_luna"));
        if (prefs.getBoolean("pref_rd", true)) result.add(makeRainbowDash(res).withPrefKey("pref_rd"));
        if (prefs.getBoolean("pref_rainbowshine", true)) result.add(makeRainbowshine(res).withPrefKey("pref_rainbowshine"));
        if (prefs.getBoolean("pref_rarity", true)) result.add(makeRarity(res).withPrefKey("pref_rarity"));
        if (prefs.getBoolean("pref_roseluck", true)) result.add(makeRoseluck(res).withPrefKey("pref_roseluck"));
        if (prefs.getBoolean("pref_sandbar", true)) result.add(makeSandbar(res).withPrefKey("pref_sandbar"));
        if (prefs.getBoolean("pref_scootaloo", true)) result.add(makeScootaloo(res).withPrefKey("pref_scootaloo"));
        if (prefs.getBoolean("pref_sa", true)) result.add(makeShiningArmor(res).withPrefKey("pref_sa"));
        if (prefs.getBoolean("pref_silverstream", true)) result.add(makeSilverstream(res).withPrefKey("pref_silverstream"));
        if (prefs.getBoolean("pref_smolder", true)) result.add(makeSmolder(res).withPrefKey("pref_smolder"));
        if (prefs.getBoolean("pref_soarin", true)) result.add(makeSoarin(res).withPrefKey("pref_soarin"));
        if (prefs.getBoolean("pref_spike", true)) result.add(makeSpike(res).withPrefKey("pref_spike"));
        if (prefs.getBoolean("pref_spitfire", true)) result.add(makeSpitfire(res).withPrefKey("pref_spitfire"));
        if (prefs.getBoolean("pref_sg", true)) result.add(makeStarlightGlimmer(res).withPrefKey("pref_sg"));
        if (prefs.getBoolean("pref_ss", true)) result.add(makeSunsetShimmer(res).withPrefKey("pref_ss"));
        if (prefs.getBoolean("pref_sunburst", true)) result.add(makeSunburst(res).withPrefKey("pref_sunburst"));
        if (prefs.getBoolean("pref_sb", true)) result.add(makeSweetieBelle(res).withPrefKey("pref_sb"));
        if (prefs.getBoolean("pref_sd", true)) result.add(makeSweetieDrops(res).withPrefKey("pref_sd"));
        if (prefs.getBoolean("pref_thorax", true)) result.add(makeThorax(res).withPrefKey("pref_thorax"));
        if (prefs.getBoolean("pref_trixie", true)) result.add(makeTrixie(res).withPrefKey("pref_trixie"));
        if (prefs.getBoolean("pref_ts", true)) result.add(makeTwilightSparkle(res).withPrefKey("pref_ts"));
        if (prefs.getBoolean("pref_vinyl", true)) result.add(makeVinylScratch(res).withPrefKey("pref_vinyl"));
        if (prefs.getBoolean("pref_yona", true)) result.add(makeYona(res).withPrefKey("pref_yona"));
        if (prefs.getBoolean("pref_zecora", true)) result.add(makeZecora(res).withPrefKey("pref_zecora"));
        loadCustomPonies(context, prefs, result);
        
        return result;
    }

    /**
     * Built-in group used by Load mix and the dream mix picker. Keys must
     * stay aligned with the checkbox categories in {@code preferences.xml}.
     */
    static final class StockGroup {
        final int titleRes;
        final String[] keys;

        StockGroup(int titleRes, String... keys) {
            this.titleRes = titleRes;
            this.keys = keys;
        }
    }

    static StockGroup[] stockGroups() {
        return new StockGroup[] {
            new StockGroup(R.string.pony_group_mane6,
                    "pref_aj", "pref_fs", "pref_pp", "pref_rd", "pref_rarity",
                    "pref_spike", "pref_ts"),
            new StockGroup(R.string.pony_group_cmc,
                    "pref_ab", "pref_babs", "pref_scootaloo", "pref_sb"),
            new StockGroup(R.string.pony_group_royalty,
                    "pref_ember", "pref_cadance", "pref_celestia", "pref_luna",
                    "pref_sa", "pref_thorax"),
            new StockGroup(R.string.pony_group_young6,
                    "pref_gallus", "pref_ocellus", "pref_sandbar",
                    "pref_silverstream", "pref_smolder", "pref_yona"),
            new StockGroup(R.string.pony_group_other,
                    "pref_bp", "pref_bigmac", "pref_derpy", "pref_doctor",
                    "pref_gilda", "pref_lyra", "pref_minuette", "pref_octavia",
                    "pref_rainbowshine", "pref_roseluck", "pref_soarin",
                    "pref_spitfire", "pref_sg", "pref_ss", "pref_sunburst",
                    "pref_sd", "pref_trixie", "pref_vinyl", "pref_zecora")
        };
    }

    static ArrayList<String> builtInPrefKeys() {
        StockGroup[] groups = stockGroups();
        ArrayList<String> keys = new ArrayList<String>();
        for (int g = 0; g < groups.length; g++) {
            String[] groupKeys = groups[g].keys;
            for (int i = 0; i < groupKeys.length; i++) {
                keys.add(groupKeys[i]);
            }
        }
        return keys;
    }

    static ArrayList<String> customPrefKeys(Context context) {
        ArrayList<String> keys = new ArrayList<String>();
        File[] files = CustomStorage.listCustomXml(context);
        for (int i = 0; i < files.length; i++) {
            keys.add(PonyMixes.CUSTOM_PREFIX + files[i].getName());
        }
        return keys;
    }

    static ArrayList<String> allHerdKeys(Context context) {
        ArrayList<String> keys = builtInPrefKeys();
        keys.addAll(customPrefKeys(context));
        return keys;
    }
    
    /** Attach stable stem id to a built-in sprite-owning action. */
    private static PonyAction bi(PonyAction action, String ponyKey, String resourceEntryStem) {
        return action.setActionId(BuiltInActionIds.stem(ponyKey, resourceEntryStem));
    }

    private static Pony makeDefaultPony(Resources res, String ponyKey,
            int standId, String standStem, int trotId, String trotStem) {
        PonyAction stand = bi(new PonyAction(res, standId), ponyKey, standStem);
        PonyAction trot = bi(new PonyAction(res, trotId), ponyKey, trotStem);
        PonyAction[] waitStates = defaultIdles(stand);
        PonyAction[] moveStates = defaultGaits(trot);
        PonyAction[] justTrot = {trot};
        PonyAction[] all = concat(waitStates, moveStates);
        
        for (int i = 0; i < all.length; i++) {
            all[i].setNextWaiting(waitStates);
            all[i].setNextMoving(moveStates);
            all[i].setNextDrag(justTrot);
        }
        
        return new Pony(all, moveStates);
    }
    
    private static Pony makeDefaultFlyer(Resources res, String ponyKey,
            int standId, String standStem, int trotId, String trotStem,
            int flyId, String flyStem) {
        PonyAction stand = bi(new PonyAction(res, standId), ponyKey, standStem);
        PonyAction trot = bi(new PonyAction(res, trotId), ponyKey, trotStem);
        PonyAction fly = bi(new PonyAction(res, flyId), ponyKey, flyStem);
        
        PonyAction[] waitIdles = defaultIdles(stand);
        // Hover stays available as a wait; landings pick stand variants.
        PonyAction[] waitFromFly = concat(waitIdles, new PonyAction[] {fly});
        PonyAction[] groundGaits = defaultGaits(trot);
        // Keep roughly equal chance of ground vs air (5 gait slots + 5 fly).
        PonyAction[] moveStates = concat(groundGaits,
                new PonyAction[] {fly, fly, fly, fly, fly});
        PonyAction[] justFly = {fly};
        PonyAction[] justTrot = {trot};
        PonyAction[] all = concat(waitIdles, groundGaits, justFly);
        
        for (int i = 0; i < waitIdles.length; i++) {
            waitIdles[i].setNextWaiting(waitIdles);
            waitIdles[i].setNextMoving(moveStates);
            waitIdles[i].setNextDrag(justTrot);
        }
        for (int i = 0; i < groundGaits.length; i++) {
            groundGaits[i].setNextWaiting(waitIdles);
            groundGaits[i].setNextMoving(moveStates);
            groundGaits[i].setNextDrag(justTrot);
        }
        // Once airborne, keep flying until a wait picks stand/hover.
        fly.setNextWaiting(waitFromFly);
        fly.setNextMoving(justFly);
        // Ground gait while held: fly sheets read as airborne and lift the body.
        fly.setNextDrag(justTrot);
        
        return new Pony(all, moveStates);
    }
    
    private static Pony makeAppleBloom(Resources res) {
        return makeDefaultPony(res, "pref_ab", R.array.ab_stand, "ab_stand", R.array.ab_trot, "ab_trot");
    }
    
    private static Pony makeApplejack(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.aj_stand), "pref_aj", "aj_stand");
        PonyAction trot = bi(new PonyAction(res, R.array.aj_trot), "pref_aj", "aj_trot");
        PonyAction drag = bi(new PonyAction(res, R.array.aj_drag), "pref_aj", "aj_drag");
        
        PonyAction[] waitStates = defaultIdles(stand);
        PonyAction[] moveStates = defaultGaits(trot);
        PonyAction[] justDrag = {drag};
        PonyAction[] all = concat(waitStates, moveStates, justDrag);
        
        for (int i = 0; i < all.length; i++) {
            all[i].setNextWaiting(waitStates);
            all[i].setNextMoving(moveStates);
            all[i].setNextDrag(justDrag);
        }
        
        return new Pony(all, moveStates);
    }
    
    private static Pony makeBabsSeed(Resources res) {
        return makeDefaultPony(res, "pref_babs", R.array.babs_stand, "babs_stand", R.array.babs_trot, "babs_trot");
    }
    
    private static Pony makeBerryPunch(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.bp_stand), "pref_bp", "bp_stand");
        PonyAction trot = bi(new PonyAction(res, R.array.bp_trot), "pref_bp", "bp_trot");
        PonyAction standdrunk = bi(new PonyAction(res, R.array.bp_standdrunk), "pref_bp", "bp_standdrunk");
        // Drunk locomotion is deliberately slower than sober gaits.
        PonyAction trotdrunk = bi(new PonyAction(res, R.array.bp_trotdrunk, SPEED_STROLL), "pref_bp", "bp_trotdrunk");
        
        PonyAction[] soberWait = defaultIdles(stand);
        PonyAction[] drunkWait = defaultIdles(standdrunk);
        PonyAction[] waitStates = concat(soberWait, drunkWait);
        PonyAction[] soberGaits = defaultGaits(trot);
        PonyAction[] moveStates = concat(soberGaits, new PonyAction[] {trotdrunk, trotdrunk});
        PonyAction[] all = concat(waitStates, soberGaits, new PonyAction[] {trotdrunk});
        
        for (int i = 0; i < all.length; i++) {
            all[i].setNextWaiting(waitStates);
            all[i].setNextMoving(moveStates);
            all[i].setNextDrag(moveStates);
        }
        
        return new Pony(all, moveStates);
    }
    
    private static Pony makeBigMcIntosh(Resources res) {
        return makeDefaultPony(res, "pref_bigmac", R.array.bigmac_stand, "bigmac_stand", R.array.bigmac_trot, "bigmac_trot");
    }
    
    private static Pony makeDerpyHooves(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.derpy_stand), "pref_derpy", "derpy_stand");
        PonyAction trot = bi(new PonyAction(res, R.array.derpy_trot), "pref_derpy", "derpy_trot");
        PonyAction hover = bi(new PonyAction(res, R.array.derpy_hover), "pref_derpy", "derpy_hover");
        PonyAction hoverud = bi(new PonyAction(res, R.array.derpy_hoverud), "pref_derpy", "derpy_hoverud");
        PonyAction fly = bi(new PonyAction(res, R.array.derpy_fly), "pref_derpy", "derpy_fly");
        PonyAction flyud = bi(new PonyAction(res, R.array.derpy_flyud), "pref_derpy", "derpy_flyud");
        PonyAction drag = bi(new PonyAction(res, R.array.derpy_drag), "pref_derpy", "derpy_drag");
        
        PonyAction[] waitIdles = defaultIdles(stand);
        PonyAction[] groundGaits = defaultGaits(trot);
        PonyAction[] justFly = {fly};
        PonyAction[] justFlyud = {flyud};
        PonyAction[] justDrag = {drag};
        PonyAction[] waitStatesnorm = concat(waitIdles, new PonyAction[] {hover});
        PonyAction[] waitStatesud = concat(waitIdles, new PonyAction[] {hoverud});
        PonyAction[] waitStates = concat(waitIdles, new PonyAction[] {hover, hoverud});
        PonyAction[] moveStates = concat(groundGaits, new PonyAction[] {fly, flyud});
        PonyAction[] all = concat(concat(waitIdles, groundGaits),
                new PonyAction[] {hover, hoverud, fly, flyud, drag});
        
        for (int i = 0; i < waitIdles.length; i++) {
            waitIdles[i].setNextWaiting(waitIdles);
            waitIdles[i].setNextMoving(moveStates);
            waitIdles[i].setNextDrag(justDrag);
        }
        for (int i = 0; i < groundGaits.length; i++) {
            groundGaits[i].setNextWaiting(waitIdles);
            groundGaits[i].setNextMoving(moveStates);
            groundGaits[i].setNextDrag(justDrag);
        }
        hover.setNextWaiting(waitStatesnorm);
        hoverud.setNextWaiting(waitStatesud);
        fly.setNextWaiting(waitStatesnorm);
        flyud.setNextWaiting(waitStatesud);
        drag.setNextWaiting(waitStates);
        
        hover.setNextMoving(justFly);
        hoverud.setNextMoving(justFlyud);
        fly.setNextMoving(justFly);
        flyud.setNextMoving(justFlyud);
        drag.setNextMoving(moveStates);
        
        hover.setNextDrag(justDrag);
        hoverud.setNextDrag(justDrag);
        fly.setNextDrag(justDrag);
        flyud.setNextDrag(justDrag);
        drag.setNextDrag(justDrag);
        
        return new Pony(all, moveStates);
    }
    
    private static Pony makeDoctorHooves(Resources res) {
        return makeDefaultPony(res, "pref_doctor", R.array.doctor_stand, "doctor_stand", R.array.doctor_trot, "doctor_trot");
    }
    
    private static Pony makeEmber(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.ember_stand), "pref_ember", "ember_stand");
        PonyAction fly = bi(new PonyAction(res, R.array.ember_fly), "pref_ember", "ember_fly");
        
        PonyAction[] waitIdles = defaultIdles(stand);
        PonyAction[] justFly = {fly};
        PonyAction[] waitStates = concat(waitIdles, justFly);
        PonyAction[] all = concat(waitIdles, justFly);
        
        for (int i = 0; i < waitIdles.length; i++) {
            waitIdles[i].setNextWaiting(waitIdles);
            waitIdles[i].setNextMoving(justFly);
            waitIdles[i].setNextDrag(justFly);
        }
        fly.setNextWaiting(waitStates);
        fly.setNextMoving(justFly);
        fly.setNextDrag(justFly);
        
        return new Pony(all, justFly);
    }
    
    private static Pony makeFluttershy(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.fs_stand), "pref_fs", "fs_stand");
        // Fluttershy prefers a gentler ground pace; full trot is rarer.
        PonyAction trot = bi(new PonyAction(res, R.array.fs_trot, SPEED_WALK), "pref_fs", "fs_trot");
        PonyAction fly = bi(new PonyAction(res, R.array.fs_fly, SPEED_WALK), "pref_fs", "fs_fly");
        PonyAction drag = bi(new PonyAction(res, R.array.fs_drag), "pref_fs", "fs_drag");
        
        PonyAction[] waitIdles = defaultIdles(stand);
        PonyAction stroll = alias(trot, SPEED_STROLL);
        PonyAction[] groundGaits = new PonyAction[] {stroll, stroll, trot, trot, trot};
        PonyAction[] justFly = {fly};
        PonyAction[] justDrag = {drag};
        // Prefer standing; fly less often (historical 3:1 stand:fly on wait from air).
        PonyAction[] waitStates = concat(concat(waitIdles, waitIdles), justFly);
        PonyAction[] moveStates = concat(groundGaits, groundGaits, justFly);
        PonyAction[] all = concat(waitIdles, groundGaits, new PonyAction[] {fly, drag});
        
        for (int i = 0; i < waitIdles.length; i++) {
            waitIdles[i].setNextWaiting(waitIdles);
            waitIdles[i].setNextMoving(moveStates);
            waitIdles[i].setNextDrag(justDrag);
        }
        for (int i = 0; i < groundGaits.length; i++) {
            groundGaits[i].setNextWaiting(waitIdles);
            groundGaits[i].setNextMoving(moveStates);
            groundGaits[i].setNextDrag(justDrag);
        }
        fly.setNextWaiting(waitStates);
        fly.setNextMoving(justFly);
        fly.setNextDrag(justDrag);
        drag.setNextWaiting(waitStates);
        drag.setNextMoving(moveStates);
        drag.setNextDrag(justDrag);
        
        return new Pony(all, moveStates);
    }
    
    private static Pony makeGallus(Resources res) {
        return makeDefaultFlyer(res, "pref_gallus", R.array.gallus_stand, "gallus_stand", R.array.gallus_trot, "gallus_trot", R.array.gallus_fly, "gallus_fly");
    }
    
    private static Pony makeGilda(Resources res) {
        return makeDefaultFlyer(res, "pref_gilda", R.array.gilda_stand, "gilda_stand", R.array.gilda_walk, "gilda_walk", R.array.gilda_fly, "gilda_fly");
    }
    
    private static Pony makeLyraHeartstrings(Resources res) {
        PonyAction sit = bi(new PonyAction(res, R.array.lyra_sit), "pref_lyra", "lyra_sit");
        PonyAction stand = bi(new PonyAction(res, R.array.lyra_stand), "pref_lyra", "lyra_stand");
        PonyAction trot = bi(new PonyAction(res, R.array.lyra_trot), "pref_lyra", "lyra_trot");
        
        PonyAction[] waitIdles = defaultIdles(stand);
        PonyAction[] moveStates = defaultGaits(trot);
        PonyAction[] justTrot = {trot};
        // Prefer standing; occasional sit (historical 3:1).
        PonyAction[] waitStates = concat(concat(waitIdles, waitIdles), new PonyAction[] {sit});
        PonyAction[] all = concat(waitIdles, moveStates, new PonyAction[] {sit});
        
        for (int i = 0; i < all.length; i++) {
            all[i].setNextWaiting(waitStates);
            all[i].setNextMoving(moveStates);
            all[i].setNextDrag(justTrot);
        }
        
        return new Pony(all, moveStates);
    }
    
    private static Pony makeMinuette(Resources res) {
        return makeDefaultPony(res, "pref_minuette", R.array.minuette_stand, "minuette_stand", R.array.minuette_trot, "minuette_trot");
    }
    
    private static Pony makeOcellus(Resources res) {
        return makeDefaultPony(res, "pref_ocellus", R.array.ocellus_stand, "ocellus_stand", R.array.ocellus_trot, "ocellus_trot");
    }
    
    private static Pony makeOctavia(Resources res) {
        return makeDefaultPony(res, "pref_octavia", R.array.octavia_stand, "octavia_stand", R.array.octavia_trot, "octavia_trot");
    }
    
    private static Pony makePinkiePie(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.pp_stand), "pref_pp", "pp_stand");
        PonyAction trot = bi(new PonyAction(res, R.array.pp_trot), "pref_pp", "pp_trot");
        // Bounce is energetic — full ceiling speed.
        PonyAction bounce = bi(new PonyAction(res, R.array.pp_bounce), "pref_pp", "pp_bounce");
        PonyAction drag = bi(new PonyAction(res, R.array.pp_drag), "pref_pp", "pp_drag");
        
        PonyAction[] waitStates = defaultIdles(stand);
        PonyAction[] groundGaits = defaultGaits(trot);
        PonyAction[] moveStates = concat(groundGaits, new PonyAction[] {bounce, bounce});
        PonyAction[] justDrag = {drag};
        PonyAction[] all = concat(waitStates, groundGaits, new PonyAction[] {bounce, drag});
        
        for (int i = 0; i < all.length; i++) {
            all[i].setNextWaiting(waitStates);
            all[i].setNextMoving(moveStates);
            all[i].setNextDrag(justDrag);
        }
        
        return new Pony(all, moveStates);
    }
    
    private static Pony makePrincessCadance(Resources res) {
        return makeDefaultFlyer(res, "pref_cadance", R.array.cadance_stand, "cadance_stand", R.array.cadance_walk, "cadance_walk", R.array.cadance_fly, "cadance_fly");
    }
    
    private static Pony makePrincessCelestia(Resources res) {
        return makeDefaultFlyer(res, "pref_celestia", R.array.celestia_stand, "celestia_stand", R.array.celestia_walk, "celestia_walk", R.array.celestia_fly, "celestia_fly");
    }
    
    private static Pony makePrincessLuna(Resources res) {
        return makeDefaultFlyer(res, "pref_luna", R.array.luna_stand, "luna_stand", R.array.luna_walk, "luna_walk", R.array.luna_fly, "luna_fly");
    }
    
    private static Pony makeRainbowDash(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.rd_stand), "pref_rd", "rd_stand");
        PonyAction trot = bi(new PonyAction(res, R.array.rd_trot), "pref_rd", "rd_trot");
        PonyAction fly = bi(new PonyAction(res, R.array.rd_fly), "pref_rd", "rd_fly");
        PonyAction drag = bi(new PonyAction(res, R.array.rd_drag), "pref_rd", "rd_drag");
        
        PonyAction[] waitIdles = defaultIdles(stand);
        PonyAction[] groundGaits = defaultGaits(trot);
        PonyAction[] justFly = {fly};
        PonyAction[] justDrag = {drag};
        // Prefers air time (historical wait/move weighted toward fly).
        PonyAction[] waitStates = concat(waitIdles, new PonyAction[] {fly, fly, fly});
        PonyAction[] moveStates = concat(groundGaits, new PonyAction[] {fly, fly, fly, fly, fly, fly, fly, fly, fly});
        PonyAction[] all = concat(waitIdles, groundGaits, new PonyAction[] {fly, drag});
        
        for (int i = 0; i < waitIdles.length; i++) {
            waitIdles[i].setNextWaiting(waitIdles);
            waitIdles[i].setNextMoving(moveStates);
            waitIdles[i].setNextDrag(justDrag);
        }
        for (int i = 0; i < groundGaits.length; i++) {
            groundGaits[i].setNextWaiting(waitIdles);
            groundGaits[i].setNextMoving(moveStates);
            groundGaits[i].setNextDrag(justDrag);
        }
        fly.setNextWaiting(waitStates);
        fly.setNextMoving(justFly);
        fly.setNextDrag(justDrag);
        drag.setNextWaiting(waitStates);
        drag.setNextMoving(moveStates);
        drag.setNextDrag(justDrag);
        
        return new Pony(all, moveStates);
    }
    private static Pony makeRainbowshine(Resources res) {
        return makeDefaultFlyer(res, "pref_rainbowshine", R.array.rainbowshine_stand, "rainbowshine_stand", R.array.rainbowshine_trot, "rainbowshine_trot", R.array.rainbowshine_fly, "rainbowshine_fly");
    }
    
    private static Pony makeRarity(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.rarity_stand), "pref_rarity", "rarity_stand");
        PonyAction trot = bi(new PonyAction(res, R.array.rarity_trot), "pref_rarity", "rarity_trot");
        PonyAction drag = bi(new PonyAction(res, R.array.rarity_drag), "pref_rarity", "rarity_drag");
        
        PonyAction[] waitStates = defaultIdles(stand);
        PonyAction[] moveStates = defaultGaits(trot);
        PonyAction[] justDrag = {drag};
        PonyAction[] all = concat(waitStates, moveStates, justDrag);
        
        for (int i = 0; i < all.length; i++) {
            all[i].setNextWaiting(waitStates);
            all[i].setNextMoving(moveStates);
            all[i].setNextDrag(justDrag);
        }
        
        return new Pony(all, moveStates);
    }
    private static Pony makeRoseluck(Resources res) {
        return makeDefaultPony(res, "pref_roseluck", R.array.roseluck_stand, "roseluck_stand", R.array.roseluck_trot, "roseluck_trot");
    }
    
    private static Pony makeSandbar(Resources res) {
        return makeDefaultPony(res, "pref_sandbar", R.array.sandbar_stand, "sandbar_stand", R.array.sandbar_trot, "sandbar_trot");
    }
    
    private static Pony makeScootaloo(Resources res) {
        return makeDefaultPony(res, "pref_scootaloo", R.array.scootaloo_stand, "scootaloo_stand", R.array.scootaloo_trot, "scootaloo_trot");
    }
    
    private static Pony makeShiningArmor(Resources res) {
        return makeDefaultPony(res, "pref_sa", R.array.sa_stand, "sa_stand", R.array.sa_walk, "sa_walk");
    }
    
    private static Pony makeSilverstream(Resources res) {
        return makeDefaultFlyer(res, "pref_silverstream", R.array.silverstream_stand, "silverstream_stand", R.array.silverstream_trot, "silverstream_trot", R.array.silverstream_fly, "silverstream_fly");
    }
    
    private static Pony makeSmolder(Resources res) {
        return makeDefaultFlyer(res, "pref_smolder", R.array.smolder_stand, "smolder_stand", R.array.smolder_walk, "smolder_walk", R.array.smolder_fly, "smolder_fly");
    }
    
    private static Pony makeSoarin(Resources res) {
        return makeDefaultFlyer(res, "pref_soarin", R.array.soarin_stand, "soarin_stand", R.array.soarin_trot, "soarin_trot", R.array.soarin_fly, "soarin_fly");
    }
    
    private static Pony makeSpike(Resources res) {
        return makeDefaultPony(res, "pref_spike", R.array.spike_stand, "spike_stand", R.array.spike_walk, "spike_walk");
    }
    
    private static Pony makeSpitfire(Resources res) {
        return makeDefaultFlyer(res, "pref_spitfire", R.array.spitfire_stand, "spitfire_stand", R.array.spitfire_trot, "spitfire_trot", R.array.spitfire_fly, "spitfire_fly");
    }
    
    private static Pony makeStarlightGlimmer(Resources res) {
        return makeDefaultPony(res, "pref_sg", R.array.sg_stand, "sg_stand", R.array.sg_trot, "sg_trot");
    }
    
    private static Pony makeSunsetShimmer(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.ss_stand), "pref_ss", "ss_stand");
        PonyAction trot = bi(new PonyAction(res, R.array.ss_trot), "pref_ss", "ss_trot");
        // Feet row measured on solid body core (VFX extends below hooves).
        PonyAction teleportOut = bi(new PonyAction(res, R.array.ss_teleportout, PonyAction.PORT_O), "pref_ss", "ss_teleportout")
                .setAnchorY(61);
        PonyAction teleportIn = bi(new PonyAction(res, R.array.ss_teleportin, PonyAction.PORT_I), "pref_ss", "ss_teleportin")
                .setAnchorY(61);
        
        PonyAction[] waitStates = defaultIdles(stand);
        PonyAction[] groundGaits = defaultGaits(trot);
        PonyAction[] justTrot = {trot};
        PonyAction[] moveStates = concat(groundGaits, groundGaits, new PonyAction[] {teleportOut});
        PonyAction[] all = concat(waitStates, groundGaits, new PonyAction[] {teleportOut, teleportIn});
        
        for (int i = 0; i < waitStates.length; i++) {
            waitStates[i].setNextWaiting(waitStates);
            waitStates[i].setNextMoving(moveStates);
            waitStates[i].setNextDrag(justTrot);
        }
        for (int i = 0; i < groundGaits.length; i++) {
            groundGaits[i].setNextWaiting(waitStates);
            groundGaits[i].setNextMoving(moveStates);
            groundGaits[i].setNextDrag(justTrot);
        }
        teleportOut.setNextWaiting(waitStates);
        teleportOut.setNextMoving(new PonyAction[] {teleportIn});
        teleportOut.setNextDrag(justTrot);
        teleportIn.setNextWaiting(waitStates);
        teleportIn.setNextMoving(moveStates);
        teleportIn.setNextDrag(justTrot);
        
        return new Pony(all, moveStates);
    }
    
    /**
     * Sunburst: two appearance modes (cape / no cape) that do not mix after
     * spawn. Start weights favour cape (cape_trot, cape_trot, trot). Non-cape
     * trot uses walk-rate travel to match the editor definition.
     */
    private static Pony makeSunburst(Resources res) {
        PonyAction capeStand = bi(new PonyAction(res, R.array.sunburst_cape_stand), "pref_sunburst", "sunburst_cape_stand");
        PonyAction capeTrot = bi(new PonyAction(res, R.array.sunburst_cape_trot), "pref_sunburst", "sunburst_cape_trot");
        PonyAction stand = bi(new PonyAction(res, R.array.sunburst_stand), "pref_sunburst", "sunburst_stand");
        // Editor speed 0.7 for non-cape trot.
        PonyAction trot = bi(new PonyAction(res, R.array.sunburst_trot, SPEED_WALK), "pref_sunburst", "sunburst_trot");
        
        PonyAction[] capeWait = defaultIdles(capeStand);
        PonyAction[] capeMove = defaultGaits(capeTrot);
        PonyAction[] justCapeTrot = {capeTrot};
        PonyAction[] noCapeWait = defaultIdles(stand);
        PonyAction[] noCapeMove = defaultGaits(trot);
        PonyAction[] justTrot = {trot};
        
        for (int i = 0; i < capeWait.length; i++) {
            capeWait[i].setNextWaiting(capeWait);
            capeWait[i].setNextMoving(capeMove);
            capeWait[i].setNextDrag(justCapeTrot);
        }
        for (int i = 0; i < capeMove.length; i++) {
            capeMove[i].setNextWaiting(capeWait);
            capeMove[i].setNextMoving(capeMove);
            capeMove[i].setNextDrag(justCapeTrot);
        }
        for (int i = 0; i < noCapeWait.length; i++) {
            noCapeWait[i].setNextWaiting(noCapeWait);
            noCapeWait[i].setNextMoving(noCapeMove);
            noCapeWait[i].setNextDrag(justTrot);
        }
        for (int i = 0; i < noCapeMove.length; i++) {
            noCapeMove[i].setNextWaiting(noCapeWait);
            noCapeMove[i].setNextMoving(noCapeMove);
            noCapeMove[i].setNextDrag(justTrot);
        }
        
        PonyAction[] all = concat(concat(capeWait, capeMove), concat(noCapeWait, noCapeMove));
        // Historical startactions: cape_trot,cape_trot,trot
        PonyAction[] start = concat(capeMove, capeMove, noCapeMove);
        
        return new Pony(all, start);
    }
    
    private static Pony makeSweetieBelle(Resources res) {
        return makeDefaultPony(res, "pref_sb", R.array.sb_stand, "sb_stand", R.array.sb_trot, "sb_trot");
    }
    
    private static Pony makeSweetieDrops(Resources res) {
        return makeDefaultPony(res, "pref_sd", R.array.sd_stand, "sd_stand", R.array.sd_trot, "sd_trot");
    }
    
    private static Pony makeThorax(Resources res) {
        return makeDefaultFlyer(res, "pref_thorax", R.array.thorax_stand, "thorax_stand", R.array.thorax_trot, "thorax_trot", R.array.thorax_fly, "thorax_fly");
    }
    
    private static Pony makeTrixie(Resources res) {
        return makeDefaultPony(res, "pref_trixie", R.array.trixie_stand, "trixie_stand", R.array.trixie_trot, "trixie_trot");
    }
    
    private static Pony makeTwilightSparkle(Resources res) {
        PonyAction standA = bi(new PonyAction(res, R.array.pts_stand), "pref_ts", "pts_stand");
        PonyAction trotA = bi(new PonyAction(res, R.array.pts_trot), "pref_ts", "pts_trot");
        PonyAction flyA = bi(new PonyAction(res, R.array.pts_fly), "pref_ts", "pts_fly");
        // Feet rows measured on solid body core (VFX may hang below hooves).
        PonyAction teleportOutA = bi(new PonyAction(res, R.array.pts_teleportout, PonyAction.PORT_O), "pref_ts", "pts_teleportout")
                .setAnchorY(44);
        PonyAction teleportInA = bi(new PonyAction(res, R.array.pts_teleportin, PonyAction.PORT_I), "pref_ts", "pts_teleportin")
                .setAnchorY(45);
        PonyAction standU = bi(new PonyAction(res, R.array.ts_stand), "pref_ts", "ts_stand");
        PonyAction trotU = bi(new PonyAction(res, R.array.ts_trot), "pref_ts", "ts_trot");
        PonyAction teleportOutU = bi(new PonyAction(res, R.array.ts_teleportout, PonyAction.PORT_O), "pref_ts", "ts_teleportout")
                .setAnchorY(59);
        PonyAction teleportInU = bi(new PonyAction(res, R.array.ts_teleportin, PonyAction.PORT_I), "pref_ts", "ts_teleportin")
                .setAnchorY(59);
        PonyAction dragU = bi(new PonyAction(res, R.array.ts_drag), "pref_ts", "ts_drag");
        
        PonyAction[] waitIdlesA = defaultIdles(standA);
        PonyAction[] groundGaitsA = defaultGaits(trotA);
        PonyAction[] justTrotA = {trotA};
        PonyAction[] justFlyA = {flyA};
        PonyAction[] waitStatesA = concat(concat(waitIdlesA, waitIdlesA), justFlyA);
        PonyAction[] moveStatesA = concat(groundGaitsA, new PonyAction[] {flyA, teleportOutA});
        
        PonyAction[] waitIdlesU = defaultIdles(standU);
        PonyAction[] groundGaitsU = defaultGaits(trotU);
        PonyAction[] justDragU = {dragU};
        PonyAction[] moveStatesU = concat(groundGaitsU, groundGaitsU, new PonyAction[] {teleportOutU});
        
        for (int i = 0; i < waitIdlesA.length; i++) {
            waitIdlesA[i].setNextWaiting(waitIdlesA);
            waitIdlesA[i].setNextMoving(moveStatesA);
            waitIdlesA[i].setNextDrag(justTrotA);
        }
        for (int i = 0; i < groundGaitsA.length; i++) {
            groundGaitsA[i].setNextWaiting(waitIdlesA);
            groundGaitsA[i].setNextMoving(moveStatesA);
            groundGaitsA[i].setNextDrag(justTrotA);
        }
        flyA.setNextWaiting(waitStatesA);
        flyA.setNextMoving(justFlyA);
        flyA.setNextDrag(justTrotA);
        teleportOutA.setNextWaiting(waitIdlesA);
        teleportOutA.setNextMoving(new PonyAction[] {teleportInA});
        teleportOutA.setNextDrag(justTrotA);
        teleportInA.setNextWaiting(waitIdlesA);
        teleportInA.setNextMoving(moveStatesA);
        teleportInA.setNextDrag(justTrotA);
        
        for (int i = 0; i < waitIdlesU.length; i++) {
            waitIdlesU[i].setNextWaiting(waitIdlesU);
            waitIdlesU[i].setNextMoving(moveStatesU);
            waitIdlesU[i].setNextDrag(justDragU);
        }
        for (int i = 0; i < groundGaitsU.length; i++) {
            groundGaitsU[i].setNextWaiting(waitIdlesU);
            groundGaitsU[i].setNextMoving(moveStatesU);
            groundGaitsU[i].setNextDrag(justDragU);
        }
        teleportOutU.setNextWaiting(waitIdlesU);
        teleportOutU.setNextMoving(new PonyAction[] {teleportInU});
        teleportOutU.setNextDrag(justDragU);
        teleportInU.setNextWaiting(waitIdlesU);
        teleportInU.setNextMoving(moveStatesU);
        teleportInU.setNextDrag(justDragU);
        dragU.setNextWaiting(waitIdlesU);
        dragU.setNextMoving(moveStatesU);
        dragU.setNextDrag(justDragU);
        
        PonyAction[] all = concat(
                concat(waitIdlesA, groundGaitsA, new PonyAction[] {flyA, teleportOutA, teleportInA}),
                concat(waitIdlesU, groundGaitsU, new PonyAction[] {teleportOutU, teleportInU, dragU}));
        PonyAction[] start = concat(moveStatesA, moveStatesU);
        
        return new Pony(all, start);
    }
    
    private static Pony makeVinylScratch(Resources res) {
        PonyAction stand = bi(new PonyAction(res, R.array.vinyl_stand), "pref_vinyl", "vinyl_stand");
        PonyAction trot = bi(new PonyAction(res, R.array.vinyl_trot), "pref_vinyl", "vinyl_trot");
        PonyAction dance = bi(new PonyAction(res, R.array.vinyl_dance), "pref_vinyl", "vinyl_dance");
        // Moonwalk is a slower, showy travel.
        PonyAction moonwalk = bi(new PonyAction(res, R.array.vinyl_moonwalk, SPEED_WALK), "pref_vinyl", "vinyl_moonwalk");
        
        PonyAction[] waitIdles = defaultIdles(stand);
        PonyAction[] groundGaits = defaultGaits(trot);
        PonyAction[] justTrot = {trot};
        PonyAction[] waitStates = concat(waitIdles, new PonyAction[] {dance});
        PonyAction[] moveStates = concat(groundGaits, groundGaits, new PonyAction[] {moonwalk});
        PonyAction[] all = concat(waitIdles, groundGaits, new PonyAction[] {dance, moonwalk});
        
        for (int i = 0; i < all.length; i++) {
            all[i].setNextWaiting(waitStates);
            all[i].setNextMoving(moveStates);
            all[i].setNextDrag(justTrot);
        }
        
        return new Pony(all, moveStates);
    }
    
    private static Pony makeYona(Resources res) {
        return makeDefaultPony(res, "pref_yona", R.array.yona_stand, "yona_stand", R.array.yona_trot, "yona_trot");
    }
    
    private static Pony makeZecora(Resources res) {
        return makeDefaultPony(res, "pref_zecora", R.array.zecora_stand, "zecora_stand", R.array.zecora_trot, "zecora_trot");
    }
    
    private static PonyAction[] getActions(HashMap<String, PonyAction> actions, String[] actionNames) {
        PonyAction[] result = new PonyAction[actionNames.length];
        for (int j = 0; j < actionNames.length; j++) {
            result[j] = actions.get(actionNames[j]);
        }
        return result;
    }
    
    /**
     * Expands a comma-separated action list: {@code name:N} becomes N copies
     * of the name (same as repeating it), then each name that has a gait bag
     * is replaced with that bag's weighted entries. Reserved {@code none}/{@code -}
     * tokens are skipped (empty result means no real successor for that axis).
     */
    private static PonyAction[] expandActionList(String list, HashMap<String, PonyAction[]> bags) {
        java.util.List<String> names = PonyDefinition.expandActionListNames(list);
        ArrayList<PonyAction> out = new ArrayList<PonyAction>();
        for (int i = 0; i < names.size(); i++) {
            PonyAction[] bag = bags.get(names.get(i));
            if (bag != null) {
                for (int j = 0; j < bag.length; j++) {
                    out.add(bag[j]);
                }
            }
        }
        return out.toArray(new PonyAction[out.size()]);
    }
    
    /**
     * Builds the weighted gait bag for one named action. Entries matching the
     * base action's speed reuse that instance; other speeds become aliases that
     * share the base's sprites (resolved through any spritesfrom chain owner).
     */
    private static PonyAction[] buildGaitBag(PonyAction base, String gaitsSpec,
                                               ArrayList<PonyAction> extras) {
        java.util.List<PonyDefinition.GaitEntry> entries =
                PonyDefinition.parseGaits(gaitsSpec, null);
        if (entries.isEmpty()) {
            return new PonyAction[] {base};
        }
        // One PonyAction per distinct speed; weights repeat references.
        HashMap<String, PonyAction> bySpeed = new HashMap<String, PonyAction>();
        bySpeed.put(speedKey(base.speed), base);
        
        ArrayList<PonyAction> bag = new ArrayList<PonyAction>();
        for (int i = 0; i < entries.size(); i++) {
            PonyDefinition.GaitEntry entry = entries.get(i);
            String key = speedKey(entry.speed);
            PonyAction action = bySpeed.get(key);
            if (action == null) {
                // Prefer exact match on base.speed for float formatting quirks.
                if (PonyDefinition.sameSpeed(entry.speed, base.speed)) {
                    action = base;
                } else {
                    action = new PonyAction(base, entry.speed);
                    extras.add(action);
                }
                bySpeed.put(key, action);
            }
            for (int w = 0; w < entry.weight; w++) {
                bag.add(action);
            }
        }
        return bag.toArray(new PonyAction[bag.size()]);
    }
    
    private static String speedKey(float speed) {
        // Stable key so 0.7 and 0.70 collapse; formatSpeed-like for ints.
        if (speed == (int)speed) {
            return Integer.toString((int)speed);
        }
        return Float.toString(speed);
    }
    
    private static void loadCustomPonies(Context context, SharedPreferences prefs, ArrayList<Pony> ponies) {
        File dir = CustomStorage.localDir(context);
        if (dir == null) return; // External storage is unavailable, so we can't load any custom ponies.
        
        try {
            new File(dir, "custom-ponies-go-here").createNewFile();
        } catch (IOException e) {
        }
        
        File[] files = dir.listFiles(xmlFilter);
        if (files == null) return;
        
        for (int i = 0; i < files.length; i++) {
            String prefKey = "pref_custom_" + files[i].getName();
            if (prefs.getBoolean(prefKey, true)) {
                Pony pony = loadCustomPonyUnchecked(context, prefKey);
                if (pony != null) ponies.add(pony);
            }
        }
    }

    /**
     * Checkbox-free custom load for {@link #createPony}. Missing or invalid
     * files return null (slot drop) after logging.
     */
    private static Pony loadCustomPonyUnchecked(Context context, String ponyKey) {
        String fileName = ponyKey.substring(PonyMixes.CUSTOM_PREFIX.length());
        if (fileName.length() == 0 || fileName.indexOf('/') >= 0
                || fileName.indexOf('\\') >= 0) {
            return null;
        }
        File dir = CustomStorage.localDir(context);
        if (dir == null) return null;
        File file = new File(dir, fileName);
        if (!file.isFile()) {
            android.util.Log.w("PonyPaper", "Custom pony missing: " + fileName);
            return null;
        }
        try {
            DocumentBuilder docBuilder = SecureXml.newDocumentBuilder();
            Document document = docBuilder.parse(file);
            PonyDefinition definition = new PonyDefinition(document);
            definition.validate();
            return makeCustomPony(definition).withPrefKey(ponyKey);
        } catch (Exception e) {
            android.util.Log.e("PonyPaper", "Error loading " + file + ": " + e.toString());
            return null;
        }
    }
    
    /**
     * Builds a runtime {@link Pony} from a custom XML definition. Supports
     * {@code <spritesfrom>} aliases (shared bitmaps, different speed) and
     * {@code <gaits>} bags (weighted speed variants expanded into next/start lists).
     */
    private static Pony makeCustomPony(PonyDefinition definition) {
        HashMap<String, PonyAction> actions = new HashMap<String, PonyAction>();
        final int actionCount = definition.actions.length;
        
        // Pass 1: sprite owners (no spritesfrom) own the bitmaps.
        for (int i = 0; i < actionCount; i++) {
            PonyDefinition.Action def = definition.actions[i];
            if (def.spritesFrom == null) {
                def.spritesFrom = "";
            }
            if (def.gaits == null) {
                def.gaits = "";
            }
            if (!def.isAlias()) {
                actions.put(def.name, new PonyAction(def));
            }
        }
        
        // Pass 2: named aliases that share an owner's sprites at a different speed.
        for (int i = 0; i < actionCount; i++) {
            PonyDefinition.Action def = definition.actions[i];
            if (def.isAlias()) {
                PonyAction owner = actions.get(def.spritesFrom);
                if (owner == null) {
                    // validate() should have caught this; skip rather than NPE.
                    continue;
                }
                // Alias may set its own <loop> and special type independently
                // of the owner (e.g. screen-out reusing a stand sheet).
                int aliasType = owner.type;
                if (def.specialType != null && !def.specialType.isEmpty()) {
                    aliasType = PonyAction.typeFromSpecial(def.specialType);
                }
                PonyAction alias = new PonyAction(owner, def.speed, def.loops, aliasType);
                // Named alias is its own catalog entry (XML name), not the owner's stem.
                alias.setActionId(def.name);
                // Optional per-facing <anchorx>/<anchory> on the alias override inherited values.
                float leftX = def.getAnchorX("left");
                float rightX = def.getAnchorX("right");
                float leftY = def.getAnchorY("left");
                float rightY = def.getAnchorY("right");
                if (!Float.isNaN(leftX) && leftX >= 0f) {
                    alias.setAnchorX(PonyAction.LEFT, leftX);
                }
                if (!Float.isNaN(rightX) && rightX >= 0f) {
                    alias.setAnchorX(PonyAction.RIGHT, rightX);
                }
                if (!Float.isNaN(leftY) && leftY >= 0f) {
                    alias.setAnchorY(PonyAction.LEFT, leftY);
                }
                if (!Float.isNaN(rightY) && rightY >= 0f) {
                    alias.setAnchorY(PonyAction.RIGHT, rightY);
                }
                // Alias movement is independent of the sprite owner (default inherit).
                alias.setMovement(def.movement);
                actions.put(def.name, alias);
            }
        }
        
        // Pass 3: expand optional <gaits> into weighted bags (may add extra aliases).
        HashMap<String, PonyAction[]> bags = new HashMap<String, PonyAction[]>();
        ArrayList<PonyAction> gaitExtras = new ArrayList<PonyAction>();
        for (int i = 0; i < actionCount; i++) {
            PonyDefinition.Action def = definition.actions[i];
            PonyAction base = actions.get(def.name);
            if (base == null) {
                continue;
            }
            if (def.gaits.isEmpty()) {
                bags.put(def.name, new PonyAction[] {base});
            } else {
                bags.put(def.name, buildGaitBag(base, def.gaits, gaitExtras));
            }
        }
        
        // Pass 4: wire next/start lists with gait expansion; copy graph onto bag variants.
        for (int i = 0; i < actionCount; i++) {
            PonyDefinition.Action def = definition.actions[i];
            PonyAction[] bag = bags.get(def.name);
            if (bag == null) {
                continue;
            }
            PonyAction[] nextWaiting = expandActionList(def.nextActions.get("waiting"), bags);
            PonyAction[] nextMoving = expandActionList(def.nextActions.get("moving"), bags);
            PonyAction[] nextDrag = expandActionList(definition.effectiveDragActions(def), bags);
            for (int j = 0; j < bag.length; j++) {
                bag[j].setNextWaiting(nextWaiting);
                bag[j].setNextMoving(nextMoving);
                bag[j].setNextDrag(nextDrag);
            }
        }
        
        // Collect every distinct action for load/unload (owners, aliases, gait variants).
        java.util.LinkedHashSet<PonyAction> all = new java.util.LinkedHashSet<PonyAction>();
        for (PonyAction a : actions.values()) {
            all.add(a);
        }
        for (int i = 0; i < gaitExtras.size(); i++) {
            all.add(gaitExtras.get(i));
        }
        
        PonyAction[] start = expandActionList(definition.startActions, bags);
        PonyEffectDef[] effectDefs = buildEffectDefs(definition, bags);
        return new Pony(all.toArray(new PonyAction[all.size()]), start, effectDefs,
                definition.wander);
    }

    /**
     * Builds runtime effect defs keyed to every gait variant of the named
     * trigger action. Invalid/missing triggers are skipped (validate should
     * already have rejected them for custom XML).
     */
    private static PonyEffectDef[] buildEffectDefs(PonyDefinition definition,
            HashMap<String, PonyAction[]> bags) {
        if (definition.effects == null || definition.effects.length == 0) {
            return null;
        }
        ArrayList<PonyEffectDef> list = new ArrayList<PonyEffectDef>();
        for (int i = 0; i < definition.effects.length; i++) {
            PonyDefinition.Effect effect = definition.effects[i];
            if (effect == null || effect.action == null) {
                continue;
            }
            PonyAction[] bag = bags.get(effect.action);
            if (bag == null || bag.length == 0) {
                continue;
            }
            try {
                list.add(new PonyEffectDef(effect, bag));
            } catch (RuntimeException e) {
                // Skip corrupt effect art rather than failing the whole pony.
                android.util.Log.w("PonyPaper", "Skipping effect \"" + effect.name + "\": " + e.getMessage());
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        return list.toArray(new PonyEffectDef[list.size()]);
    }
    
}
