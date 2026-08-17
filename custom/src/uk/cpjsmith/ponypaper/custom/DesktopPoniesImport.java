package uk.cpjsmith.ponypaper.custom;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Imports a Desktop Ponies character folder ({@code pony.ini} + GIF sprites)
 * into a structure the custom editor can apply to a {@link PonyEditor}.
 *
 * <p>Desktop Ponies supports effects, speech, multi-pony interactions, and
 * free-form linked sequences that PonyPaper does not model. Those are skipped
 * with warnings. Locomotion, idle, drag, and simple teleport chains are mapped
 * onto PonyPaper actions and next-action lists. The editor packs GIF sprites
 * at 50% ({@link ImageImport#SCALE_DESKTOP_PONIES}) so imported stock ponies
 * match built-in sheet size.
 *
 * @see <a href="https://github.com/RoosterDragon/Desktop-Ponies">Desktop Ponies</a>
 *      {@code techdoc.md} for the {@code pony.ini} line format
 */
public final class DesktopPoniesImport {

    /** Soft limit so pathological configs stay editable. */
    private static final int MAX_ACTIONS = 30;

    private static final Pattern TELEPORT_NAME =
            Pattern.compile("(?i).*(warp|teleport|port|wink).*");

    private static final Pattern CONTROL_NAME =
            Pattern.compile("(?i).*(take_control|theme\\s|conga|banner|truck_).*");

    public enum Role {
        WAITING,
        MOVING,
        DRAG,
        TELEPORT_OUT,
        TELEPORT_IN
    }

    /**
     * Desktop Ponies speed units mapped so classic walk (~3) becomes PonyPaper
     * factor 1.0 (historical full travel rate).
     */
    private static final double DP_SPEED_REF = 3.0;

    /** One action ready to load into the editor. */
    public static final class ImportedAction {
        public final String name;
        public final Role role;
        public final String specialType;
        /** PonyPaper travel/animation speed factor (positive). */
        public final float speed;
        public final File leftImage;
        public final File rightImage;
        public String nextWaiting = "";
        public String nextMoving = "";
        public String nextDrag = "";

        ImportedAction(String name, Role role, String specialType, float speed,
                       File leftImage, File rightImage) {
            this.name = name;
            this.role = role;
            this.specialType = specialType != null ? specialType : "";
            this.speed = speed;
            this.leftImage = leftImage;
            this.rightImage = rightImage;
        }
    }

    /** Result of importing one pony directory. */
    public static final class Result {
        public final String displayName;
        public final List<ImportedAction> actions;
        public final String startActions;
        public final String defaultDrag;
        public final List<String> warnings;

        Result(String displayName, List<ImportedAction> actions, String startActions,
               String defaultDrag, List<String> warnings) {
            this.displayName = displayName;
            this.actions = actions;
            this.startActions = startActions;
            this.defaultDrag = defaultDrag != null ? defaultDrag : "";
            this.warnings = warnings;
        }
    }

    private static final class DpBehavior {
        String name = "";
        double chance;
        double speed;
        String rightImage = "";
        String leftImage = "";
        String movement = "";
        String linked = "";
        boolean skip;
        String followTarget = "";
        int group;
        boolean preventLoop;

        File leftFile;
        File rightFile;
        Role role = Role.WAITING;
        boolean inTeleportChain;
    }

    private DesktopPoniesImport() {}

    /**
     * Import a Desktop Ponies character directory (must contain {@code pony.ini}).
     *
     * @param ponyDir directory named after the pony, containing {@code pony.ini}
     * @return import result with actions and warnings
     * @throws IOException if {@code pony.ini} cannot be read
     * @throws IllegalArgumentException if the directory is not a usable pony folder
     */
    public static Result importPony(File ponyDir) throws IOException {
        if (ponyDir == null || !ponyDir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + ponyDir);
        }
        File ini = new File(ponyDir, "pony.ini");
        if (!ini.isFile()) {
            throw new IllegalArgumentException("No pony.ini in " + ponyDir.getAbsolutePath());
        }

        List<String> warnings = new ArrayList<>();
        String displayName = ponyDir.getName();
        List<DpBehavior> raw = new ArrayList<>();
        int ignoredLines = 0;
        int effectLines = 0;
        int speakLines = 0;
        int interactionLines = 0;

        try (BufferedReader reader = Files.newBufferedReader(ini.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Strip UTF-8 BOM if present (common on pony.ini files)
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith("'")) {
                    continue;
                }
                int comma = line.indexOf(',');
                if (comma < 0) {
                    continue;
                }
                String type = line.substring(0, comma).trim();
                String rest = line.substring(comma + 1);
                String typeKey = type.toLowerCase(Locale.ROOT);

                switch (typeKey) {
                    case "name":
                        displayName = unquote(rest.trim());
                        if (displayName.isEmpty()) {
                            displayName = ponyDir.getName();
                        }
                        break;
                    case "behavior":
                        DpBehavior b = parseBehavior(rest, warnings);
                        if (b != null) {
                            raw.add(b);
                        }
                        break;
                    case "effect":
                        effectLines++;
                        break;
                    case "speak":
                        speakLines++;
                        break;
                    case "interaction":
                        interactionLines++;
                        break;
                    case "categories":
                    case "behaviorgroup":
                        // Not used by PonyPaper
                        break;
                    default:
                        ignoredLines++;
                        break;
                }
            }
        }

        if (effectLines > 0) {
            warnings.add("Skipped " + effectLines + " Effect line(s) (not supported in PonyPaper).");
        }
        if (speakLines > 0) {
            warnings.add("Skipped " + speakLines + " Speak line(s) (not supported in PonyPaper).");
        }
        if (interactionLines > 0) {
            warnings.add("Skipped " + interactionLines + " Interaction line(s) (not supported in PonyPaper).");
        }
        if (ignoredLines > 0) {
            warnings.add("Ignored " + ignoredLines + " unrecognized line(s).");
        }

        // Resolve images and drop unusable behaviors
        List<DpBehavior> resolved = new ArrayList<>();
        Map<String, DpBehavior> byName = new LinkedHashMap<>();
        for (DpBehavior b : raw) {
            if (b.name.isEmpty()) {
                warnings.add("Skipped a behavior with an empty name.");
                continue;
            }
            if (CONTROL_NAME.matcher(b.name).matches()) {
                warnings.add("Skipped control/story behavior \"" + b.name + "\".");
                continue;
            }
            if (!b.followTarget.isEmpty()) {
                warnings.add("Skipped follow/interaction behavior \"" + b.name + "\" (target: "
                        + b.followTarget + ").");
                continue;
            }
            if (b.group != 0) {
                warnings.add("Skipped behavior \"" + b.name + "\" in group " + b.group
                        + " (only group 0 is imported).");
                continue;
            }

            File right = resolveImage(ponyDir, b.rightImage);
            File left = resolveImage(ponyDir, b.leftImage);
            if (right == null || left == null) {
                warnings.add("Skipped behavior \"" + b.name + "\": missing image file(s) "
                        + "left=\"" + b.leftImage + "\" right=\"" + b.rightImage + "\".");
                continue;
            }
            b.leftFile = left;
            b.rightFile = right;

            String key = b.name.toLowerCase(Locale.ROOT);
            if (byName.containsKey(key)) {
                warnings.add("Duplicate behavior name \"" + b.name + "\"; keeping the first.");
                continue;
            }
            byName.put(key, b);
            resolved.add(b);
        }

        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("No importable behaviors found in " + ini.getAbsolutePath());
        }

        // Detect teleport chains before role assignment
        Set<String> teleportMiddle = new HashSet<>();
        Map<String, String> teleportOutToIn = new HashMap<>();
        detectTeleportChains(resolved, byName, teleportMiddle, teleportOutToIn, warnings);

        // Classify roles
        for (DpBehavior b : resolved) {
            String key = b.name.toLowerCase(Locale.ROOT);
            if (teleportOutToIn.containsKey(key)) {
                b.role = Role.TELEPORT_OUT;
                b.inTeleportChain = true;
            } else if (teleportMiddle.contains(key) || isTeleportInName(b, teleportOutToIn)) {
                // middles excluded later; pure ends marked TELEPORT_IN
                if (teleportOutToIn.containsValue(key)) {
                    b.role = Role.TELEPORT_IN;
                    b.inTeleportChain = true;
                } else if (teleportMiddle.contains(key)) {
                    b.role = Role.MOVING; // unused; filtered out
                    b.inTeleportChain = true;
                } else {
                    b.role = classifyRole(b);
                }
            } else {
                b.role = classifyRole(b);
            }
        }
        // Ensure in-targets from map are TELEPORT_IN
        for (String inKey : teleportOutToIn.values()) {
            DpBehavior in = byName.get(inKey);
            if (in != null) {
                in.role = Role.TELEPORT_IN;
                in.inTeleportChain = true;
            }
        }

        // Select which behaviors become actions
        List<DpBehavior> selected = selectBehaviors(resolved, teleportMiddle, teleportOutToIn, warnings);

        // Build weighted lists
        List<String> waitWeights = new ArrayList<>();
        List<String> moveWeights = new ArrayList<>();
        List<String> dragWeights = new ArrayList<>();
        String dragName = null;
        String primaryMove = null;
        double primaryMoveChance = -1;

        for (DpBehavior b : selected) {
            switch (b.role) {
                case WAITING:
                    appendWeighted(waitWeights, b.name, b.chance);
                    break;
                case MOVING:
                    appendWeighted(moveWeights, b.name, b.chance);
                    if (b.chance > primaryMoveChance) {
                        primaryMoveChance = b.chance;
                        primaryMove = b.name;
                    }
                    break;
                case DRAG:
                    dragName = b.name;
                    appendWeighted(dragWeights, b.name, Math.max(b.chance, 0.01));
                    break;
                case TELEPORT_OUT:
                    appendWeighted(moveWeights, b.name, b.chance > 0 ? b.chance : 0.05);
                    break;
                case TELEPORT_IN:
                    // not in random pools
                    break;
            }
        }

        if (waitWeights.isEmpty()) {
            // Prefer a speed-0 behavior; else reuse any action name
            for (DpBehavior b : selected) {
                if (b.role == Role.WAITING || b.speed <= 0) {
                    waitWeights.add(b.name);
                    break;
                }
            }
            if (waitWeights.isEmpty() && !selected.isEmpty()) {
                waitWeights.add(selected.get(0).name);
            }
        }
        if (moveWeights.isEmpty()) {
            if (primaryMove != null) {
                moveWeights.add(primaryMove);
            } else {
                moveWeights.addAll(waitWeights);
            }
        }
        if (dragWeights.isEmpty()) {
            if (primaryMove != null) {
                dragWeights.add(primaryMove);
            } else if (!moveWeights.isEmpty()) {
                dragWeights.add(moveWeights.get(0));
            } else {
                dragWeights.addAll(waitWeights);
            }
            if (dragName == null && primaryMove != null) {
                warnings.add("No Dragged behavior found; using \"" + primaryMove + "\" for drag.");
            } else if (dragName == null) {
                warnings.add("No Dragged behavior found; drag falls back to a waiting/moving action.");
            }
        }

        String waitList = joinComma(waitWeights);
        String moveList = joinComma(moveWeights);
        String dragList = joinComma(dragWeights);
        String startList = joinComma(moveWeights);

        List<ImportedAction> actions = new ArrayList<>();
        for (DpBehavior b : selected) {
            String special = "";
            if (b.role == Role.TELEPORT_OUT) {
                special = "teleport-out";
            } else if (b.role == Role.TELEPORT_IN) {
                special = "teleport-in";
            }

            ImportedAction action = new ImportedAction(
                    b.name, b.role, special, mapDesktopSpeed(b.speed), b.leftFile, b.rightFile);

            if (b.role == Role.TELEPORT_OUT) {
                String inKey = teleportOutToIn.get(b.name.toLowerCase(Locale.ROOT));
                DpBehavior in = inKey != null ? byName.get(inKey) : null;
                String inName = in != null ? in.name : "";
                action.nextWaiting = waitList;
                action.nextMoving = inName.isEmpty() ? moveList : inName;
                action.nextDrag = "";
            } else if (b.role == Role.TELEPORT_IN) {
                action.nextWaiting = waitList;
                action.nextMoving = moveList;
                action.nextDrag = "";
            } else {
                action.nextWaiting = waitList;
                action.nextMoving = moveList;
                action.nextDrag = "";
            }
            actions.add(action);
        }

        warnings.add(0, "Imported " + actions.size() + " action(s) from \"" + displayName + "\".");
        return new Result(displayName, actions, startList, dragList, warnings);
    }

    /**
     * Default directory to open in a folder chooser, if present:
     * {@code ../Desktop-Ponies/Content/Ponies} relative to the process working directory.
     */
    public static File defaultPoniesRoot() {
        File candidate = new File(new File(".."), "Desktop-Ponies/Content/Ponies");
        if (candidate.isDirectory()) {
            return candidate;
        }
        candidate = new File("Desktop-Ponies/Content/Ponies");
        if (candidate.isDirectory()) {
            return candidate;
        }
        return new File(".");
    }

    private static Role classifyRole(DpBehavior b) {
        String movement = b.movement == null ? "" : b.movement.trim();
        if (movement.equalsIgnoreCase("Dragged")) {
            return Role.DRAG;
        }
        if (b.speed > 0
                && !movement.equalsIgnoreCase("None")
                && !movement.equalsIgnoreCase("MouseOver")
                && !movement.equalsIgnoreCase("Sleep")
                && !movement.equalsIgnoreCase("Dragged")) {
            return Role.MOVING;
        }
        return Role.WAITING;
    }

    /**
     * Maps a Desktop Ponies behavior speed onto a PonyPaper action speed factor.
     * Stationary behaviors (speed 0) get full animation rate (1). Moving speeds
     * scale around classic walk = 3 → factor 1.0, clamped to a sensible range.
     */
    static float mapDesktopSpeed(double desktopSpeed) {
        if (desktopSpeed <= 0) {
            return 1.0f;
        }
        float factor = (float)(desktopSpeed / DP_SPEED_REF);
        if (factor < 0.2f) {
            return 0.2f;
        }
        if (factor > 1.5f) {
            return 1.5f;
        }
        return factor;
    }

    private static boolean isTeleportInName(DpBehavior b, Map<String, String> outToIn) {
        return outToIn.containsValue(b.name.toLowerCase(Locale.ROOT));
    }

    private static void detectTeleportChains(
            List<DpBehavior> resolved,
            Map<String, DpBehavior> byName,
            Set<String> middles,
            Map<String, String> outToIn,
            List<String> warnings) {

        // Who links to whom (for skipping sub-chain starts like warp2 in warp1→warp2→warp3)
        Map<String, String> linkedFrom = new HashMap<>();
        for (DpBehavior b : resolved) {
            if (b.linked != null && !b.linked.isEmpty()) {
                linkedFrom.put(b.linked.toLowerCase(Locale.ROOT), b.name.toLowerCase(Locale.ROOT));
            }
        }

        List<List<DpBehavior>> candidates = new ArrayList<>();
        for (DpBehavior start : resolved) {
            if (start.linked == null || start.linked.isEmpty()) {
                continue;
            }
            // Candidate: named like teleport/warp, or prevent-loop stationary that links onward
            boolean nameHint = TELEPORT_NAME.matcher(start.name).matches();
            boolean preventStationary = start.speed <= 0 && start.preventLoop;
            if (!nameHint && !preventStationary) {
                continue;
            }
            // Prefer the root of a chain: if something teleport-like already links here, skip
            String startKey = start.name.toLowerCase(Locale.ROOT);
            String predecessor = linkedFrom.get(startKey);
            if (predecessor != null) {
                DpBehavior pred = byName.get(predecessor);
                if (pred != null && (TELEPORT_NAME.matcher(pred.name).matches()
                        || (pred.speed <= 0 && pred.preventLoop)
                        || TELEPORT_NAME.matcher(start.name).matches())) {
                    continue;
                }
            }

            List<DpBehavior> chain = walkChain(start, byName);
            if (chain.size() < 2 || chain.size() > 3) {
                continue;
            }
            DpBehavior last = chain.get(chain.size() - 1);
            boolean lastHint = TELEPORT_NAME.matcher(last.name).matches()
                    || last.preventLoop
                    || last.speed <= 0;
            if (!nameHint && !lastHint) {
                continue;
            }
            candidates.add(chain);
        }

        // Longer chains first so warp1→warp2→warp3 wins over warp2→warp3
        candidates.sort((a, b) -> Integer.compare(b.size(), a.size()));
        Set<String> claimed = new HashSet<>();
        for (List<DpBehavior> chain : candidates) {
            DpBehavior first = chain.get(0);
            DpBehavior last = chain.get(chain.size() - 1);
            String outKey = first.name.toLowerCase(Locale.ROOT);
            String inKey = last.name.toLowerCase(Locale.ROOT);
            boolean overlap = false;
            for (DpBehavior node : chain) {
                if (claimed.contains(node.name.toLowerCase(Locale.ROOT))) {
                    overlap = true;
                    break;
                }
            }
            if (overlap || outToIn.containsKey(outKey)) {
                continue;
            }
            outToIn.put(outKey, inKey);
            for (DpBehavior node : chain) {
                claimed.add(node.name.toLowerCase(Locale.ROOT));
            }
            for (int i = 1; i < chain.size() - 1; i++) {
                middles.add(chain.get(i).name.toLowerCase(Locale.ROOT));
            }
            warnings.add("Mapped teleport chain \"" + first.name + "\" → \"" + last.name + "\""
                    + (chain.size() == 3 ? " (dropped middle \"" + chain.get(1).name + "\")." : "."));
        }
    }

    private static List<DpBehavior> walkChain(DpBehavior start, Map<String, DpBehavior> byName) {
        List<DpBehavior> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        DpBehavior cur = start;
        while (cur != null) {
            String key = cur.name.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                break;
            }
            chain.add(cur);
            if (cur.linked == null || cur.linked.isEmpty()) {
                break;
            }
            cur = byName.get(cur.linked.toLowerCase(Locale.ROOT));
            if (chain.size() > 4) {
                break;
            }
        }
        return chain;
    }

    private static List<DpBehavior> selectBehaviors(
            List<DpBehavior> resolved,
            Set<String> teleportMiddle,
            Map<String, String> outToIn,
            List<String> warnings) {

        Set<String> mustKeep = new HashSet<>();
        mustKeep.addAll(outToIn.keySet());
        mustKeep.addAll(outToIn.values());

        List<DpBehavior> candidates = new ArrayList<>();
        for (DpBehavior b : resolved) {
            String key = b.name.toLowerCase(Locale.ROOT);
            if (teleportMiddle.contains(key)) {
                warnings.add("Omitted teleport transit behavior \"" + b.name + "\".");
                continue;
            }
            if (b.skip && !mustKeep.contains(key) && b.role != Role.DRAG) {
                warnings.add("Skipped non-random behavior \"" + b.name + "\" (Skip=True).");
                continue;
            }
            // Skip-true teleport ends still kept via mustKeep
            if (b.skip && mustKeep.contains(key)) {
                candidates.add(b);
                continue;
            }
            if (b.role == Role.DRAG || !b.skip) {
                candidates.add(b);
            }
        }

        // Ensure teleport ends present even if filtered oddly
        for (Map.Entry<String, String> e : outToIn.entrySet()) {
            ensurePresent(candidates, resolved, e.getKey());
            ensurePresent(candidates, resolved, e.getValue());
        }

        if (candidates.size() <= MAX_ACTIONS) {
            return candidates;
        }

        // Prefer drag, teleport, high-chance moving, high-chance waiting
        List<DpBehavior> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator
                .comparingInt((DpBehavior b) -> rolePriority(b.role))
                .thenComparing((DpBehavior b) -> -b.chance)
                .thenComparing(b -> b.name, String.CASE_INSENSITIVE_ORDER));

        List<DpBehavior> limited = new ArrayList<>();
        Set<String> included = new HashSet<>();
        for (DpBehavior b : ranked) {
            if (limited.size() >= MAX_ACTIONS) {
                break;
            }
            String key = b.name.toLowerCase(Locale.ROOT);
            if (included.add(key)) {
                limited.add(b);
            }
        }
        // Always force teleport + drag if truncated
        for (DpBehavior b : ranked) {
            if (b.role == Role.DRAG || b.role == Role.TELEPORT_OUT || b.role == Role.TELEPORT_IN) {
                String key = b.name.toLowerCase(Locale.ROOT);
                if (included.add(key)) {
                    if (limited.size() >= MAX_ACTIONS) {
                        // replace lowest-priority non-essential
                        for (int i = limited.size() - 1; i >= 0; i--) {
                            Role r = limited.get(i).role;
                            if (r == Role.WAITING || r == Role.MOVING) {
                                included.remove(limited.get(i).name.toLowerCase(Locale.ROOT));
                                limited.set(i, b);
                                break;
                            }
                        }
                    } else {
                        limited.add(b);
                    }
                }
            }
        }

        warnings.add("Limited import to " + limited.size() + " actions (max " + MAX_ACTIONS + ").");
        // Preserve a stable order: original file order among selected
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < resolved.size(); i++) {
            order.put(resolved.get(i).name.toLowerCase(Locale.ROOT), i);
        }
        limited.sort(Comparator.comparingInt(b -> order.getOrDefault(b.name.toLowerCase(Locale.ROOT), 9999)));
        return limited;
    }

    private static void ensurePresent(List<DpBehavior> candidates, List<DpBehavior> resolved, String key) {
        for (DpBehavior c : candidates) {
            if (c.name.toLowerCase(Locale.ROOT).equals(key)) {
                return;
            }
        }
        for (DpBehavior b : resolved) {
            if (b.name.toLowerCase(Locale.ROOT).equals(key)) {
                candidates.add(b);
                return;
            }
        }
    }

    private static int rolePriority(Role role) {
        switch (role) {
            case DRAG:
                return 0;
            case TELEPORT_OUT:
                return 1;
            case TELEPORT_IN:
                return 2;
            case MOVING:
                return 3;
            case WAITING:
            default:
                return 4;
        }
    }

    private static void appendWeighted(List<String> list, String name, double chance) {
        if (chance <= 0) {
            return;
        }
        int n = (int) Math.round(chance * 20.0);
        if (n < 1) {
            n = 1;
        }
        if (n > 8) {
            n = 8;
        }
        if (n == 1) {
            list.add(name);
        } else {
            list.add(name + ":" + n);
        }
    }

    private static String joinComma(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    private static File resolveImage(File ponyDir, String imageName) {
        if (imageName == null) {
            return null;
        }
        imageName = imageName.trim();
        if (imageName.isEmpty()) {
            return null;
        }
        File f = new File(ponyDir, imageName);
        if (f.isFile()) {
            return f;
        }
        // Case-insensitive fallback for odd packs
        File[] children = ponyDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isFile() && child.getName().equalsIgnoreCase(imageName)) {
                    return child;
                }
            }
        }
        return null;
    }

    private static DpBehavior parseBehavior(String rest, List<String> warnings) {
        List<String> fields = splitCsv(rest);
        // fields are after "Behavior," so index 0 = Name
        if (fields.size() < 8) {
            warnings.add("Skipped incomplete Behavior line (" + fields.size() + " fields).");
            return null;
        }
        DpBehavior b = new DpBehavior();
        b.name = fields.get(0).trim();
        b.chance = parseDouble(fields.get(1), 0);
        // max duration fields.get(2), min fields.get(3) unused
        b.speed = parseDouble(fields.get(4), 0);
        b.rightImage = fields.get(5).trim();
        b.leftImage = fields.get(6).trim();
        b.movement = fields.get(7).trim();
        if (fields.size() > 8) {
            b.linked = fields.get(8).trim();
        }
        // 9 start speech, 10 end speech
        if (fields.size() > 11) {
            b.skip = parseBool(fields.get(11));
        }
        // 12 target x, 13 target y
        if (fields.size() > 14) {
            b.followTarget = fields.get(14).trim();
        }
        // 15 auto follow, 16 follow stopped, 17 follow moving
        // 18 right center, 19 left center
        if (fields.size() > 20) {
            b.preventLoop = parseBool(fields.get(20));
        }
        if (fields.size() > 21) {
            b.group = (int) parseDouble(fields.get(21), 0);
        }
        return b;
    }

    private static double parseDouble(String s, double def) {
        if (s == null) {
            return def;
        }
        s = s.trim();
        if (s.isEmpty()) {
            return def;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean parseBool(String s) {
        return s != null && s.trim().equalsIgnoreCase("True");
    }

    /**
     * Split a Desktop Ponies CSV-ish field list. Quotes protect commas; quote
     * characters themselves are not included in the field value.
     */
    static List<String> splitCsv(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        parts.add(cur.toString());
        return parts;
    }

    static String unquote(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Package-visible helpers for simple unit-style checks from CLI smoke tests. */
    static List<String> splitCsvForTest(String line) {
        return Collections.unmodifiableList(splitCsv(line));
    }
}
