package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
    
    private static Pony makeDefaultPony(Resources res, int standId, int trotId) {
        PonyAction stand = new PonyAction(res, standId);
        PonyAction trot = new PonyAction(res, trotId);
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
    
    private static Pony makeDefaultFlyer(Resources res, int standId, int trotId, int flyId) {
        PonyAction stand = new PonyAction(res, standId);
        PonyAction trot = new PonyAction(res, trotId);
        PonyAction fly = new PonyAction(res, flyId);
        
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
        return makeDefaultPony(res, R.array.ab_stand, R.array.ab_trot);
    }
    
    private static Pony makeApplejack(Resources res) {
        PonyAction stand = new PonyAction(res, R.array.aj_stand);
        PonyAction trot = new PonyAction(res, R.array.aj_trot);
        PonyAction drag = new PonyAction(res, R.array.aj_drag);
        
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
        return makeDefaultPony(res, R.array.babs_stand, R.array.babs_trot);
    }
    
    private static Pony makeBerryPunch(Resources res) {
        PonyAction stand = new PonyAction(res, R.array.bp_stand);
        PonyAction trot = new PonyAction(res, R.array.bp_trot);
        PonyAction standdrunk = new PonyAction(res, R.array.bp_standdrunk);
        // Drunk locomotion is deliberately slower than sober gaits.
        PonyAction trotdrunk = new PonyAction(res, R.array.bp_trotdrunk, SPEED_STROLL);
        
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
        return makeDefaultPony(res, R.array.bigmac_stand, R.array.bigmac_trot);
    }
    
    private static Pony makeDerpyHooves(Resources res) {
        PonyAction stand = new PonyAction(res, R.array.derpy_stand);
        PonyAction trot = new PonyAction(res, R.array.derpy_trot);
        PonyAction hover = new PonyAction(res, R.array.derpy_hover);
        PonyAction hoverud = new PonyAction(res, R.array.derpy_hoverud);
        PonyAction fly = new PonyAction(res, R.array.derpy_fly);
        PonyAction flyud = new PonyAction(res, R.array.derpy_flyud);
        PonyAction drag = new PonyAction(res, R.array.derpy_drag);
        
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
        return makeDefaultPony(res, R.array.doctor_stand, R.array.doctor_trot);
    }
    
    private static Pony makeEmber(Resources res) {
        PonyAction stand = new PonyAction(res, R.array.ember_stand);
        PonyAction fly = new PonyAction(res, R.array.ember_fly);
        
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
        PonyAction stand = new PonyAction(res, R.array.fs_stand);
        // Fluttershy prefers a gentler ground pace; full trot is rarer.
        PonyAction trot = new PonyAction(res, R.array.fs_trot, SPEED_WALK);
        PonyAction fly = new PonyAction(res, R.array.fs_fly, SPEED_WALK);
        PonyAction drag = new PonyAction(res, R.array.fs_drag);
        
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
        return makeDefaultFlyer(res, R.array.gallus_stand, R.array.gallus_trot, R.array.gallus_fly);
    }
    
    private static Pony makeGilda(Resources res) {
        return makeDefaultFlyer(res, R.array.gilda_stand, R.array.gilda_walk, R.array.gilda_fly);
    }
    
    private static Pony makeLyraHeartstrings(Resources res) {
        PonyAction sit = new PonyAction(res, R.array.lyra_sit);
        PonyAction stand = new PonyAction(res, R.array.lyra_stand);
        PonyAction trot = new PonyAction(res, R.array.lyra_trot);
        
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
        return makeDefaultPony(res, R.array.minuette_stand, R.array.minuette_trot);
    }
    
    private static Pony makeOcellus(Resources res) {
        return makeDefaultPony(res, R.array.ocellus_stand, R.array.ocellus_trot);
    }
    
    private static Pony makeOctavia(Resources res) {
        return makeDefaultPony(res, R.array.octavia_stand, R.array.octavia_trot);
    }
    
    private static Pony makePinkiePie(Resources res) {
        PonyAction stand = new PonyAction(res, R.array.pp_stand);
        PonyAction trot = new PonyAction(res, R.array.pp_trot);
        // Bounce is energetic — full ceiling speed.
        PonyAction bounce = new PonyAction(res, R.array.pp_bounce);
        PonyAction drag = new PonyAction(res, R.array.pp_drag);
        
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
        return makeDefaultFlyer(res, R.array.cadance_stand, R.array.cadance_walk, R.array.cadance_fly);
    }
    
    private static Pony makePrincessCelestia(Resources res) {
        return makeDefaultFlyer(res, R.array.celestia_stand, R.array.celestia_walk, R.array.celestia_fly);
    }
    
    private static Pony makePrincessLuna(Resources res) {
        return makeDefaultFlyer(res, R.array.luna_stand, R.array.luna_walk, R.array.luna_fly);
    }
    
    private static Pony makeRainbowDash(Resources res) {
        PonyAction stand = new PonyAction(res, R.array.rd_stand);
        PonyAction trot = new PonyAction(res, R.array.rd_trot);
        PonyAction fly = new PonyAction(res, R.array.rd_fly);
        PonyAction drag = new PonyAction(res, R.array.rd_drag);
        
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
        return makeDefaultFlyer(res, R.array.rainbowshine_stand, R.array.rainbowshine_trot, R.array.rainbowshine_fly);
    }
    
    private static Pony makeRarity(Resources res) {
        PonyAction stand = new PonyAction(res, R.array.rarity_stand);
        PonyAction trot = new PonyAction(res, R.array.rarity_trot);
        PonyAction drag = new PonyAction(res, R.array.rarity_drag);
        
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
        return makeDefaultPony(res, R.array.roseluck_stand, R.array.roseluck_trot);
    }
    
    private static Pony makeSandbar(Resources res) {
        return makeDefaultPony(res, R.array.sandbar_stand, R.array.sandbar_trot);
    }
    
    private static Pony makeScootaloo(Resources res) {
        return makeDefaultPony(res, R.array.scootaloo_stand, R.array.scootaloo_trot);
    }
    
    private static Pony makeShiningArmor(Resources res) {
        return makeDefaultPony(res, R.array.sa_stand, R.array.sa_walk);
    }
    
    private static Pony makeSilverstream(Resources res) {
        return makeDefaultFlyer(res, R.array.silverstream_stand, R.array.silverstream_trot, R.array.silverstream_fly);
    }
    
    private static Pony makeSmolder(Resources res) {
        return makeDefaultFlyer(res, R.array.smolder_stand, R.array.smolder_walk, R.array.smolder_fly);
    }
    
    private static Pony makeSoarin(Resources res) {
        return makeDefaultFlyer(res, R.array.soarin_stand, R.array.soarin_trot, R.array.soarin_fly);
    }
    
    private static Pony makeSpike(Resources res) {
        return makeDefaultPony(res, R.array.spike_stand, R.array.spike_walk);
    }
    
    private static Pony makeSpitfire(Resources res) {
        return makeDefaultFlyer(res, R.array.spitfire_stand, R.array.spitfire_trot, R.array.spitfire_fly);
    }
    
    private static Pony makeStarlightGlimmer(Resources res) {
        return makeDefaultPony(res, R.array.sg_stand, R.array.sg_trot);
    }
    
    private static Pony makeSunsetShimmer(Resources res) {
        PonyAction stand = new PonyAction(res, R.array.ss_stand);
        PonyAction trot = new PonyAction(res, R.array.ss_trot);
        PonyAction teleportOut = new PonyAction(res, R.array.ss_teleportout, PonyAction.PORT_O);
        PonyAction teleportIn = new PonyAction(res, R.array.ss_teleportin, PonyAction.PORT_I);
        
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
        PonyAction capeStand = new PonyAction(res, R.array.sunburst_cape_stand);
        PonyAction capeTrot = new PonyAction(res, R.array.sunburst_cape_trot);
        PonyAction stand = new PonyAction(res, R.array.sunburst_stand);
        // Editor speed 0.7 for non-cape trot.
        PonyAction trot = new PonyAction(res, R.array.sunburst_trot, SPEED_WALK);
        
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
        return makeDefaultPony(res, R.array.sb_stand, R.array.sb_trot);
    }
    
    private static Pony makeSweetieDrops(Resources res) {
        return makeDefaultPony(res, R.array.sd_stand, R.array.sd_trot);
    }
    
    private static Pony makeThorax(Resources res) {
        return makeDefaultFlyer(res, R.array.thorax_stand, R.array.thorax_trot, R.array.thorax_fly);
    }
    
    private static Pony makeTrixie(Resources res) {
        return makeDefaultPony(res, R.array.trixie_stand, R.array.trixie_trot);
    }
    
    private static Pony makeTwilightSparkle(Resources res) {
        PonyAction standA = new PonyAction(res, R.array.pts_stand);
        PonyAction trotA = new PonyAction(res, R.array.pts_trot);
        PonyAction flyA = new PonyAction(res, R.array.pts_fly);
        PonyAction teleportOutA = new PonyAction(res, R.array.pts_teleportout, PonyAction.PORT_O);
        PonyAction teleportInA = new PonyAction(res, R.array.pts_teleportin, PonyAction.PORT_I);
        PonyAction standU = new PonyAction(res, R.array.ts_stand);
        PonyAction trotU = new PonyAction(res, R.array.ts_trot);
        PonyAction teleportOutU = new PonyAction(res, R.array.ts_teleportout, PonyAction.PORT_O);
        PonyAction teleportInU = new PonyAction(res, R.array.ts_teleportin, PonyAction.PORT_I);
        PonyAction dragU = new PonyAction(res, R.array.ts_drag);
        
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
        PonyAction stand = new PonyAction(res, R.array.vinyl_stand);
        PonyAction trot = new PonyAction(res, R.array.vinyl_trot);
        PonyAction dance = new PonyAction(res, R.array.vinyl_dance);
        // Moonwalk is a slower, showy travel.
        PonyAction moonwalk = new PonyAction(res, R.array.vinyl_moonwalk, SPEED_WALK);
        
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
        return makeDefaultPony(res, R.array.yona_stand, R.array.yona_trot);
    }
    
    private static Pony makeZecora(Resources res) {
        return makeDefaultPony(res, R.array.zecora_stand, R.array.zecora_trot);
    }
    
    private static PonyAction[] getActions(HashMap<String, PonyAction> actions, String[] actionNames) {
        PonyAction[] result = new PonyAction[actionNames.length];
        for (int j = 0; j < actionNames.length; j++) {
            result[j] = actions.get(actionNames[j]);
        }
        return result;
    }
    
    /**
     * Expands a comma-separated action list, substituting each name that has a
     * gait bag with that bag's weighted entries.
     */
    private static PonyAction[] expandActionList(String list, HashMap<String, PonyAction[]> bags) {
        if (list == null || list.isEmpty()) {
            return new PonyAction[0];
        }
        String[] names = list.split(",");
        ArrayList<PonyAction> out = new ArrayList<PonyAction>();
        for (int i = 0; i < names.length; i++) {
            String name = names[i].trim();
            if (name.isEmpty()) {
                continue;
            }
            PonyAction[] bag = bags.get(name);
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
        File dir = context.getExternalFilesDir(null);
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
                try {
                    DocumentBuilder docBuilder = SecureXml.newDocumentBuilder();
                    Document document = docBuilder.parse(files[i]);
                    PonyDefinition definition = new PonyDefinition(document);
                    definition.validate();
                    ponies.add(makeCustomPony(definition).withPrefKey(prefKey));
                } catch (Exception e) {
                    android.util.Log.e("PonyPaper", "Error loading " + files[i] + ": " + e.toString());
                }
            }
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
                actions.put(def.name, new PonyAction(owner, def.speed));
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
            PonyAction[] nextDrag = expandActionList(def.nextActions.get("drag"), bags);
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
        return new Pony(all.toArray(new PonyAction[all.size()]), start);
    }
    
}
