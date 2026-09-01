package uk.cpjsmith.ponypaper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Represents a definition of a pony that can be loaded from XML at runtime.
 */
public class PonyDefinition {
    
    public static class InvalidPonyException extends Exception {
        
        public List<String> errors;
        
        public InvalidPonyException(List<String> errors) {
            super("The pony definition was invalid.");
            this.errors = errors;
        }
        
    }
    
    /**
     * One entry in a {@code <gaits>} list: a speed factor and how many weighted
     * slots it occupies when the owning action is selected for travel/wait.
     */
    public static class GaitEntry {
        public final float speed;
        public final int weight;
        
        public GaitEntry(float speed, int weight) {
            this.speed = speed;
            this.weight = weight;
        }
    }

    /**
     * One {@code token} or {@code token:N} fragment. Weight defaults to 1
     * when the colon is omitted ({@link #weightExplicit} is then false).
     */
    public static class WeightedToken {
        public final String token;
        public final int weight;
        public final boolean weightExplicit;

        public WeightedToken(String token, int weight, boolean weightExplicit) {
            this.token = token;
            this.weight = weight;
            this.weightExplicit = weightExplicit;
        }
    }

    /**
     * One next/start/drag list entry: an action name (or reserved
     * {@code none}/{@code -}) and how many slots it occupies.
     */
    public static class ActionListEntry {
        public final String name;
        public final int weight;

        public ActionListEntry(String name, int weight) {
            this.name = name;
            this.weight = weight;
        }
    }

    /**
     * Maximum {@code :N} weight in next/start/drag lists. Gait bags are not
     * capped (they already use small integers).
     */
    public static final int MAX_ACTION_LIST_WEIGHT = 64;
    
    /**
     * Built-in ground gait bag: stroll 1/5, walk 3/5, full 1/5
     * ({@code 0.5:1,0.7:3,1:1}).
     */
    public static final String DEFAULT_GAITS = "0.5:1,0.7:3,1:1";
    
    /**
     * Built-in idle bag: full vs walk-rate 50/50 ({@code 1:1,0.7:1}).
     */
    public static final String DEFAULT_IDLE_GAITS = "1:1,0.7:1";

    /** Special type: play once in place, then jump to the travel target. */
    public static final String SPECIAL_TELEPORT_OUT = "teleport-out";
    /** Special type: play once at the destination, then idle. */
    public static final String SPECIAL_TELEPORT_IN = "teleport-in";
    /** Special type: appear in place (start on-screen; no interpolation). */
    public static final String SPECIAL_SCREEN_IN = "screen-in";
    /** Special type: vanish in place, then leave the herd slot. */
    public static final String SPECIAL_SCREEN_OUT = "screen-out";

    /**
     * Desktop Ponies–compatible placement / centering tokens (canonical casing).
     * {@code Any} and {@code Any-Not_Center} are valid for placement only at
     * spawn time (one cell chosen at random).
     */
    public static final String[] PLACEMENT_TOKENS = {
        "Top_Left", "Top", "Top_Right",
        "Left", "Center", "Right",
        "Bottom_Left", "Bottom", "Bottom_Right",
        "Any", "Any-Not_Center"
    };

    /** Maximum effect duration / repeat delay in seconds (Desktop Ponies bound). */
    public static final float MAX_EFFECT_SECONDS = 300.0f;

    /**
     * @return true when {@code specialType} is empty or a known special
     */
    public static boolean isKnownSpecialType(String specialType) {
        if (specialType == null || specialType.isEmpty()) {
            return true;
        }
        return SPECIAL_TELEPORT_OUT.equals(specialType)
                || SPECIAL_TELEPORT_IN.equals(specialType)
                || SPECIAL_SCREEN_IN.equals(specialType)
                || SPECIAL_SCREEN_OUT.equals(specialType);
    }

    /**
     * Normalizes a placement/centering token to canonical Desktop Ponies casing.
     * Comparison is case-insensitive; hyphens and underscores are accepted for
     * {@code Any-Not_Center}. Returns {@code null} when unrecognized.
     */
    public static String normalizePlacementToken(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.replaceAll("\\s+", "");
        if (t.isEmpty()) {
            return null;
        }
        String compact = t.replace('_', '-');
        for (int i = 0; i < PLACEMENT_TOKENS.length; i++) {
            String canon = PLACEMENT_TOKENS[i];
            if (canon.equalsIgnoreCase(t)
                    || canon.replace('_', '-').equalsIgnoreCase(compact)) {
                return canon;
            }
        }
        return null;
    }

    /**
     * @return true when {@code token} is a known placement/centering value
     *         (any casing)
     */
    public static boolean isKnownPlacementToken(String token) {
        return normalizePlacementToken(token) != null;
    }
    
    /**
     * Parses one {@code token} or {@code token:N} fragment. Weight defaults
     * to 1 when the colon is omitted. On error, a message is appended to
     * {@code errors} when non-null and {@code null} is returned.
     */
    public static WeightedToken parseWeightedToken(String part, List<String> errors) {
        if (part == null) {
            if (errors != null) {
                errors.add("Empty weighted entry.");
            }
            return null;
        }
        String trimmed = part.trim();
        if (trimmed.isEmpty()) {
            if (errors != null) {
                errors.add("Empty weighted entry.");
            }
            return null;
        }
        int colon = trimmed.indexOf(':');
        String tokenText;
        String weightText = null;
        if (colon >= 0) {
            tokenText = trimmed.substring(0, colon).trim();
            weightText = trimmed.substring(colon + 1).trim();
        } else {
            tokenText = trimmed;
        }
        if (tokenText.isEmpty()) {
            if (errors != null) {
                errors.add("Missing name in \"" + trimmed + "\".");
            }
            return null;
        }
        int weight = 1;
        boolean explicit = weightText != null;
        if (explicit) {
            if (weightText.isEmpty()) {
                if (errors != null) {
                    errors.add("Invalid weight in \"" + trimmed
                            + "\" (must be a positive integer).");
                }
                return null;
            }
            try {
                weight = Integer.parseInt(weightText);
            } catch (NumberFormatException e) {
                if (errors != null) {
                    errors.add("Invalid weight in \"" + trimmed
                            + "\" (must be a positive integer).");
                }
                return null;
            }
            if (weight <= 0) {
                if (errors != null) {
                    errors.add("Invalid weight in \"" + trimmed
                            + "\" (must be a positive integer).");
                }
                return null;
            }
        }
        return new WeightedToken(tokenText, weight, explicit);
    }

    /**
     * Formats a list entry. Implicit weight 1 is just {@code name};
     * an explicit weight is always {@code name:N}.
     */
    public static String formatActionListEntry(String name, int weight, boolean weightExplicit) {
        if (name == null) {
            name = "";
        }
        if (!weightExplicit) {
            return name;
        }
        return name + ":" + weight;
    }

    /**
     * Parses a next/start/drag list. Repeats and {@code name:N} are both
     * allowed; empty comma slots are skipped. Reserved {@code none}/{@code -}
     * cannot carry an explicit weight. Weights above
     * {@link #MAX_ACTION_LIST_WEIGHT} are rejected. Invalid fragments are
     * omitted; messages go to {@code errors} when non-null.
     */
    public static List<ActionListEntry> parseActionList(String value, List<String> errors) {
        List<ActionListEntry> result = new ArrayList<ActionListEntry>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        String[] parts = value.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            WeightedToken token = parseWeightedToken(part, errors);
            if (token == null) {
                continue;
            }
            if (isNoneToken(token.token) && token.weightExplicit) {
                if (errors != null) {
                    errors.add("Reserved token \"" + token.token + "\" cannot have a weight.");
                }
                continue;
            }
            if (token.weight > MAX_ACTION_LIST_WEIGHT) {
                if (errors != null) {
                    errors.add("Weight in \"" + part + "\" is too large (max "
                            + MAX_ACTION_LIST_WEIGHT + ").");
                }
                continue;
            }
            result.add(new ActionListEntry(token.token, token.weight));
        }
        return result;
    }

    /**
     * Unique real action names from a next/start/drag list (drops empty and
     * {@code none}/{@code -} tokens; strips {@code :N} weights).
     */
    public static List<String> uniqueActionListNames(String list) {
        List<String> out = new ArrayList<String>();
        List<ActionListEntry> entries = parseActionList(list, null);
        for (int i = 0; i < entries.size(); i++) {
            String name = entries.get(i).name;
            if (isNoneToken(name)) {
                continue;
            }
            if (!out.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    /**
     * Expands {@code name:N} and bare repeats into N copies of each real
     * name. Reserved {@code none}/{@code -} tokens are dropped. Callers then
     * expand each name through that action's gait bag.
     */
    public static List<String> expandActionListNames(String list) {
        List<String> out = new ArrayList<String>();
        List<ActionListEntry> entries = parseActionList(list, null);
        for (int i = 0; i < entries.size(); i++) {
            ActionListEntry entry = entries.get(i);
            if (isNoneToken(entry.name)) {
                continue;
            }
            for (int w = 0; w < entry.weight; w++) {
                out.add(entry.name);
            }
        }
        return out;
    }

    /**
     * Rewrites every real {@code oldName} (including {@code oldName:N}) to
     * {@code newName}, preserving explicit weights.
     */
    public static String renameInActionList(String list, String oldName, String newName) {
        if (list == null || list.isEmpty()) {
            return list == null ? "" : list;
        }
        StringBuilder sb = new StringBuilder();
        String[] parts = list.split(",");
        for (int i = 0; i < parts.length; i++) {
            String raw = parts[i].trim();
            if (raw.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            WeightedToken token = parseWeightedToken(raw, null);
            if (token != null && token.token.equals(oldName)) {
                sb.append(formatActionListEntry(newName, token.weight, token.weightExplicit));
            } else if (raw.equals(oldName)) {
                sb.append(newName);
            } else {
                sb.append(raw);
            }
        }
        return sb.toString();
    }

    /**
     * Keeps reserved {@code none}/{@code -} tokens and entries whose parsed
     * name is in {@code present}. Unparseable fragments are dropped.
     */
    public static String filterActionList(String list, java.util.Set<String> present) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] parts = list.split(",");
        for (int i = 0; i < parts.length; i++) {
            String raw = parts[i].trim();
            if (raw.isEmpty()) {
                continue;
            }
            WeightedToken token = parseWeightedToken(raw, null);
            String name = token != null ? token.token : raw;
            if (!isNoneToken(name) && (present == null || !present.contains(name))) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(raw);
        }
        return sb.toString();
    }

    /**
     * Parses a gaits specification such as {@code 0.5:1,0.7:3,1} (weight
     * defaults to 1 when omitted). On error, messages are appended to
     * {@code errors} when non-null and an empty list is returned.
     */
    public static List<GaitEntry> parseGaits(String value, List<String> errors) {
        List<GaitEntry> result = new ArrayList<GaitEntry>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        String[] parts = value.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                if (errors != null) {
                    errors.add("Empty entry in <gaits> list.");
                }
                continue;
            }
            WeightedToken token = parseWeightedToken(part, errors);
            if (token == null) {
                continue;
            }
            try {
                float speed = Float.parseFloat(token.token);
                if (Float.isNaN(speed) || speed <= 0f) {
                    if (errors != null) {
                        errors.add("Invalid gait speed \"" + part + "\" (must be positive).");
                    }
                    continue;
                }
                result.add(new GaitEntry(speed, token.weight));
            } catch (NumberFormatException e) {
                if (errors != null) {
                    errors.add("Invalid gait entry \"" + part + "\".");
                }
            }
        }
        return result;
    }
    
    /**
     * True when two speed factors should be treated as the same gait slot.
     */
    public static boolean sameSpeed(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
    
    public static class Action {
        
        public String name;
        public String specialType;
        /**
         * Travel / animation speed factor. Defaults to 1. Missing or invalid
         * XML values become 1 at parse time.
         */
        public float speed;
        /**
         * When true (default), the sprite sheet wraps and continues while this
         * action is active. When false, the animation plays once then the pony
         * advances via the next-action list for the current motion context
         * (waiting / moving / drag). Use for transition clips (intros, outros,
         * reactions) that should not loop for the full idle timer.
         */
        public boolean loops;
        /**
         * When non-empty, this action reuses another action's sprites
         * ({@code <spritesfrom>ownerName</spritesfrom>}) and only supplies its
         * own speed / next-action lists. Images and timings must be empty.
         */
        public String spritesFrom;
        /**
         * Optional load-time gait bag ({@code speed:weight,...}). When set,
         * every reference to this action in start/next lists is expanded to
         * weighted speed variants that share this action's sprites.
         */
        public String gaits;
        /**
         * Destination axis for this action while traveling. {@code inherit}
         * (default) uses the pony {@link #wander} soft preference.
         * {@code horizontal}/{@code vertical} hard-lock the other axis;
         * {@code any} is free 2D. See {@link WanderTarget}.
         */
        public String movement;
        /**
         * Unscaled feet column within each frame per direction ({@code "left"} /
         * {@code "right"}), pixels from the left of that sheet's frame.
         * {@link Float#NaN} means omit and use frame centre (default). Used when
         * asymmetric VFX/padding would otherwise shift the body sideways on action
         * or facing change. Left and right often differ when sheets are mirrors.
         */
        public final Map<String, Float> anchorX = new HashMap<String, Float>();
        /**
         * Unscaled feet row within each frame per direction ({@code "left"} /
         * {@code "right"}), pixels from the top of that sheet's frame.
         * {@link Float#NaN} means omit and use bottom-center (default). Used so
         * tall VFX sheets keep the body on the same ground line as shorter poses.
         */
        public final Map<String, Float> anchorY = new HashMap<String, Float>();
        public final Map<String, String> images = new HashMap<String, String>();
        public final Map<String, String> timings = new HashMap<String, String>();
        /**
         * Next-action lists by motion type ({@code waiting}, {@code moving},
         * {@code drag}). Waiting and moving are required. Drag is an optional
         * override: empty/omitted means use the pony-level {@link #defaultDrag}.
         */
        public final Map<String, String> nextActions = new HashMap<String, String>();
        
        public Action() {
            name = "";
            specialType = "";
            speed = 1.0f;
            loops = true;
            spritesFrom = "";
            gaits = "";
            movement = WanderTarget.MOVE_INHERIT;
            anchorX.put("left", Float.NaN);
            anchorX.put("right", Float.NaN);
            anchorY.put("left", Float.NaN);
            anchorY.put("right", Float.NaN);
            images.put("left", "");
            timings.put("left", "");
            images.put("right", "");
            timings.put("right", "");
            nextActions.put("waiting", "");
            nextActions.put("moving", "");
            nextActions.put("drag", "");
        }
        
        /**
         * Explicit feet X for {@code direction} ({@code "left"} or {@code "right"}),
         * or {@link Float#NaN} when unset (frame-centre default).
         */
        public float getAnchorX(String direction) {
            Float value = anchorX.get(direction);
            return value != null ? value.floatValue() : Float.NaN;
        }
        
        /**
         * Explicit feet Y for {@code direction} ({@code "left"} or {@code "right"}),
         * or {@link Float#NaN} when unset (frame-bottom default).
         */
        public float getAnchorY(String direction) {
            Float value = anchorY.get(direction);
            return value != null ? value.floatValue() : Float.NaN;
        }
        
        /**
         * Sets feet X for one direction. Pass {@link Float#NaN} or a negative
         * value to clear (use frame centre).
         */
        public void setAnchorX(String direction, float value) {
            if (Float.isNaN(value) || value < 0f) {
                anchorX.put(direction, Float.NaN);
            } else {
                anchorX.put(direction, Float.valueOf(value));
            }
        }
        
        /**
         * Sets feet Y for one direction. Pass {@link Float#NaN} or a negative
         * value to clear (use frame bottom).
         */
        public void setAnchorY(String direction, float value) {
            if (Float.isNaN(value) || value < 0f) {
                anchorY.put(direction, Float.NaN);
            } else {
                anchorY.put(direction, Float.valueOf(value));
            }
        }
        
        public Action(Element element) throws InvalidPonyException {
            List<String> errors = new ArrayList<String>();
            
            name = element.getAttribute("name");
            if (name.equals("")) {
                errors.add("An <action> must have a name.");
            }
            
            // Optional; null means "not set" until defaults after parse.
            Float parsedSpeed = null;
            Boolean parsedLoops = null;
            String parsedSpritesFrom = null;
            String parsedGaits = null;
            String parsedMovement = null;
            // Bare (no direction) anchor applies to any facing without a directed tag.
            Float bareAnchorX = null;
            Float bareAnchorY = null;
            
            for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                    {
                        String nodeName = node.getNodeName();
                        if (nodeName.equals("specialtype")) {
                            addSpecialType((Element)node, errors);
                        } else if (nodeName.equals("speed")) {
                            parsedSpeed = addSpeed((Element)node, parsedSpeed, errors);
                        } else if (nodeName.equals("loop")) {
                            parsedLoops = addLoop((Element)node, parsedLoops, errors);
                        } else if (nodeName.equals("spritesfrom")) {
                            parsedSpritesFrom = addSpritesFrom((Element)node, parsedSpritesFrom, errors);
                        } else if (nodeName.equals("gaits")) {
                            parsedGaits = addGaits((Element)node, parsedGaits, errors);
                        } else if (nodeName.equals("movement")) {
                            parsedMovement = addMovement((Element)node, parsedMovement, errors);
                        } else if (nodeName.equals("anchorx")) {
                            bareAnchorX = addAnchorAxis((Element)node, "anchorx", anchorX, bareAnchorX, errors);
                        } else if (nodeName.equals("anchory")) {
                            bareAnchorY = addAnchorAxis((Element)node, "anchory", anchorY, bareAnchorY, errors);
                        } else if (nodeName.equals("image")) {
                            addImage((Element)node, errors);
                        } else if (nodeName.equals("timings")) {
                            addTimings((Element)node, errors);
                        } else if (nodeName.equals("nextactions")) {
                            addNextActions((Element)node, errors);
                        } else {
                            errors.add("Unexpected " + node.getNodeName() + " element.");
                        }
                        break;
                    }
                    
                    case Node.TEXT_NODE:
                    {
                        String text = node.getNodeValue().trim();
                        if (!text.isEmpty()) {
                            errors.add("Unexpected text " + text + ".");
                        }
                        break;
                    }
                    
                    default:
                        errors.add("Unexpected " + node.getNodeName() + " node.");
                        break;
                }
            }
            
            if (!errors.isEmpty()) throw new InvalidPonyException(errors);
            
            if (specialType == null) specialType = "";
            speed = parsedSpeed != null ? parsedSpeed.floatValue() : 1.0f;
            loops = parsedLoops != null ? parsedLoops.booleanValue() : true;
            spritesFrom = parsedSpritesFrom != null ? parsedSpritesFrom : "";
            gaits = parsedGaits != null ? parsedGaits : "";
            movement = parsedMovement != null
                    ? WanderTarget.normalizeMovement(parsedMovement)
                    : WanderTarget.MOVE_INHERIT;
            // Directed tags win; bare (legacy) fills any missing facing.
            applyBareAnchor(anchorX, bareAnchorX);
            applyBareAnchor(anchorY, bareAnchorY);
            if (!anchorX.containsKey("left")) anchorX.put("left", Float.NaN);
            if (!anchorX.containsKey("right")) anchorX.put("right", Float.NaN);
            if (!anchorY.containsKey("left")) anchorY.put("left", Float.NaN);
            if (!anchorY.containsKey("right")) anchorY.put("right", Float.NaN);
            if (!images.containsKey("left")) images.put("left", "");
            if (!timings.containsKey("left")) timings.put("left", "");
            if (!images.containsKey("right")) images.put("right", "");
            if (!timings.containsKey("right")) timings.put("right", "");
            if (!nextActions.containsKey("waiting")) nextActions.put("waiting", "");
            if (!nextActions.containsKey("moving")) nextActions.put("moving", "");
            if (!nextActions.containsKey("drag")) nextActions.put("drag", "");
        }
        
        private static void applyBareAnchor(Map<String, Float> map, Float bare) {
            if (bare == null) {
                return;
            }
            if (!map.containsKey("left")) {
                map.put("left", bare);
            }
            if (!map.containsKey("right")) {
                map.put("right", bare);
            }
        }
        
        /** @return true if this action reuses another action's bitmaps */
        public boolean isAlias() {
            return spritesFrom != null && !spritesFrom.isEmpty();
        }
        
        private void addSpecialType(Element element, List<String> errors) {
            if (specialType != null) {
                errors.add("Too many <specialtype> elements.");
                return;
            }
            specialType = getContent(element, errors).replaceAll("\\s+", "");
        }
        
        private Float addSpeed(Element element, Float existing, List<String> errors) {
            if (existing != null) {
                errors.add("Too many <speed> elements.");
                return existing;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            try {
                float value = Float.parseFloat(text.replaceAll("\\s+", ""));
                if (Float.isNaN(value) || value <= 0f) {
                    errors.add("<speed> must be a positive number.");
                    return null;
                }
                return Float.valueOf(value);
            } catch (NumberFormatException e) {
                errors.add("Invalid <speed> value.");
                return null;
            }
        }
        
        private Boolean addLoop(Element element, Boolean existing, List<String> errors) {
            if (existing != null) {
                errors.add("Too many <loop> elements.");
                return existing;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            text = text.replaceAll("\\s+", "").toLowerCase();
            if (text.equals("true") || text.equals("yes") || text.equals("1")) {
                return Boolean.TRUE;
            }
            if (text.equals("false") || text.equals("no") || text.equals("0")) {
                return Boolean.FALSE;
            }
            errors.add("<loop> must be true or false.");
            return null;
        }
        
        private String addSpritesFrom(Element element, String existing, List<String> errors) {
            if (existing != null) {
                errors.add("Too many <spritesfrom> elements.");
                return existing;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            text = text.replaceAll("\\s+", "");
            if (text.isEmpty()) {
                errors.add("<spritesfrom> must name an action.");
                return null;
            }
            return text;
        }
        
        private String addGaits(Element element, String existing, List<String> errors) {
            if (existing != null) {
                errors.add("Too many <gaits> elements.");
                return existing;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            text = text.replaceAll("\\s+", "");
            if (text.isEmpty()) {
                errors.add("<gaits> must not be empty.");
                return null;
            }
            // Syntax check only; full validation runs in validate().
            List<String> gaitErrors = new ArrayList<String>();
            List<GaitEntry> entries = parseGaits(text, gaitErrors);
            if (!gaitErrors.isEmpty()) {
                errors.addAll(gaitErrors);
                return null;
            }
            if (entries.isEmpty()) {
                errors.add("<gaits> must list at least one speed.");
                return null;
            }
            return text;
        }

        private String addMovement(Element element, String existing, List<String> errors) {
            if (existing != null) {
                errors.add("Too many <movement> elements.");
                return existing;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            String trimmed = text.replaceAll("\\s+", "");
            if (trimmed.isEmpty()) {
                return WanderTarget.MOVE_INHERIT;
            }
            if (!WanderTarget.isKnownMovement(trimmed)) {
                errors.add("Unknown <movement> value \"" + text.trim() + "\".");
                return null;
            }
            return WanderTarget.normalizeMovement(trimmed);
        }
        
        /**
         * Parses one {@code <anchorx>} or {@code <anchory>} element.
         * With {@code direction="left|right"}, stores on {@code directed}.
         * Without direction (legacy), returns the bare value for both facings.
         *
         * @return updated bare value (only changes when the element has no direction)
         */
        private Float addAnchorAxis(Element element, String tagName, Map<String, Float> directed,
                Float bareExisting, List<String> errors) {
            String direction = element.getAttribute("direction");
            if (direction == null) {
                direction = "";
            }
            direction = direction.trim();
            
            Float value = parseAnchorNumber(element, tagName, errors);
            if (value == null) {
                return bareExisting;
            }
            
            if (direction.isEmpty()) {
                if (bareExisting != null) {
                    errors.add("Too many bare <" + tagName + "> elements (omit direction to apply to both).");
                    return bareExisting;
                }
                return value;
            }
            
            if (!(direction.equals("left") || direction.equals("right"))) {
                errors.add("<" + tagName + "> direction must be left or right (or omitted for both).");
                return bareExisting;
            }
            if (directed.containsKey(direction)) {
                errors.add("Too many <" + tagName + "> elements with direction " + direction + ".");
                return bareExisting;
            }
            directed.put(direction, value);
            return bareExisting;
        }
        
        private Float parseAnchorNumber(Element element, String tagName, List<String> errors) {
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            try {
                float value = Float.parseFloat(text.replaceAll("\\s+", ""));
                if (Float.isNaN(value) || value < 0f) {
                    if (tagName.equals("anchorx")) {
                        errors.add("<anchorx> must be a non-negative number (pixels from left of frame).");
                    } else {
                        errors.add("<anchory> must be a non-negative number (pixels from top of frame).");
                    }
                    return null;
                }
                return Float.valueOf(value);
            } catch (NumberFormatException e) {
                errors.add("Invalid <" + tagName + "> value.");
                return null;
            }
        }
        
        private void addImage(Element element, List<String> errors) {
            String direction = element.getAttribute("direction");
            if (!(direction.equals("left") || direction.equals("right"))) {
                errors.add("<image> must have a direction of left or right.");
                return;
            }
            if (images.containsKey(direction)) {
                errors.add("Too many <image> elements with direction " + direction + ".");
                return;
            }
            images.put(direction, getContent(element, errors).replaceAll("\\s+", ""));
        }
        
        private void addTimings(Element element, List<String> errors) {
            String direction = element.getAttribute("direction");
            if (!(direction.equals("left") || direction.equals("right"))) {
                errors.add("<timings> must have a direction of left or right.");
                return;
            }
            if (timings.containsKey(direction)) {
                errors.add("Too many <timings> elements with direction " + direction + ".");
                return;
            }
            timings.put(direction, getContent(element, errors));
        }
        
        private void addNextActions(Element element, List<String> errors) {
            String type = element.getAttribute("type");
            if (!(type.equals("waiting") || type.equals("moving") || type.equals("drag"))) {
                errors.add("<nextactions> must have a type of waiting, moving or drag.");
                return;
            }
            if (nextActions.containsKey(type)) {
                errors.add("Too many <nextactions> elements with type " + type + ".");
                return;
            }
            nextActions.put(type, getContent(element, errors));
        }
        
    }

    /**
     * A Desktop Ponies–style effect: a sprite spawned when a named action
     * starts. Placement aligns a point on the pony image with a point on the
     * effect image. Motion is either planted ({@link #follow} false) or glued
     * to the pony ({@link #follow} true); apparent motion comes from the
     * spritesheet, not velocity.
     */
    public static class Effect {
        public String name;
        /** Name of the {@link Action} that triggers this effect when it starts. */
        public String action;
        /**
         * Lifetime in seconds. {@code 0} means until the triggering action
         * ends (or the pony leaves). Timed effects may outlive the action.
         */
        public float duration;
        /**
         * Seconds between additional spawns while the triggering action is
         * still current. {@code 0} means spawn once only.
         */
        public float repeatDelay;
        /** When true, re-attach to the pony each frame; otherwise plant once. */
        public boolean follow;
        /**
         * When true, play the effect animation once even if the sheet would
         * loop (Desktop Ponies "Prevent Animation Loop").
         */
        public boolean noLoop;
        /**
         * How placement cells attach: {@link EffectPlacement#MODE_BOUNDS}
         * (default, Desktop Ponies AABB) or {@link EffectPlacement#MODE_MOTION}
         * (rotate side cells with travel so diagonals stay in the wake).
         */
        public String placementMode;
        /**
         * Placement on the pony image per facing ({@code left}/{@code right}).
         * Values are canonical tokens from {@link #PLACEMENT_TOKENS}.
         */
        public final Map<String, String> placement = new HashMap<String, String>();
        /**
         * Centering point on the effect image per facing ({@code left}/{@code right}).
         */
        public final Map<String, String> centering = new HashMap<String, String>();
        public final Map<String, String> images = new HashMap<String, String>();
        public final Map<String, String> timings = new HashMap<String, String>();

        public Effect() {
            name = "";
            action = "";
            duration = 0.0f;
            repeatDelay = 0.0f;
            follow = false;
            noLoop = false;
            placementMode = EffectPlacement.MODE_BOUNDS;
            placement.put("left", "Center");
            placement.put("right", "Center");
            centering.put("left", "Center");
            centering.put("right", "Center");
            images.put("left", "");
            images.put("right", "");
            timings.put("left", "");
            timings.put("right", "");
        }

        public Effect(Element element) throws InvalidPonyException {
            List<String> errors = new ArrayList<String>();

            name = element.getAttribute("name");
            if (name.equals("")) {
                errors.add("An <effect> must have a name.");
            }

            String parsedAction = null;
            Float parsedDuration = null;
            Float parsedRepeat = null;
            Boolean parsedFollow = null;
            Boolean parsedNoLoop = null;
            String parsedPlacementMode = null;

            for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                    {
                        String nodeName = node.getNodeName();
                        if (nodeName.equals("action")) {
                            parsedAction = addActionName((Element)node, parsedAction, errors);
                        } else if (nodeName.equals("duration")) {
                            parsedDuration = addSeconds((Element)node, "duration",
                                    parsedDuration, errors);
                        } else if (nodeName.equals("repeatdelay")) {
                            parsedRepeat = addSeconds((Element)node, "repeatdelay",
                                    parsedRepeat, errors);
                        } else if (nodeName.equals("follow")) {
                            parsedFollow = addBoolean((Element)node, "follow",
                                    parsedFollow, errors);
                        } else if (nodeName.equals("noloop")) {
                            parsedNoLoop = addBoolean((Element)node, "noloop",
                                    parsedNoLoop, errors);
                        } else if (nodeName.equals("placementmode")) {
                            parsedPlacementMode = addPlacementMode((Element)node,
                                    parsedPlacementMode, errors);
                        } else if (nodeName.equals("placement")) {
                            addDirectedToken((Element)node, "placement", placement, errors);
                        } else if (nodeName.equals("centering")) {
                            addDirectedToken((Element)node, "centering", centering, errors);
                        } else if (nodeName.equals("image")) {
                            addImage((Element)node, errors);
                        } else if (nodeName.equals("timings")) {
                            addTimings((Element)node, errors);
                        } else {
                            errors.add("Unexpected " + node.getNodeName()
                                    + " element in <effect>.");
                        }
                        break;
                    }

                    case Node.TEXT_NODE:
                    {
                        String text = node.getNodeValue().trim();
                        if (!text.isEmpty()) {
                            errors.add("Unexpected text " + text + " in <effect>.");
                        }
                        break;
                    }

                    default:
                        errors.add("Unexpected " + node.getNodeName() + " node in <effect>.");
                        break;
                }
            }

            if (parsedAction == null || parsedAction.isEmpty()) {
                errors.add("Effect " + (name.isEmpty() ? "(unnamed)" : name)
                        + " needs an <action> naming the trigger action.");
            }

            action = parsedAction != null ? parsedAction : "";
            duration = parsedDuration != null ? parsedDuration.floatValue() : 0.0f;
            repeatDelay = parsedRepeat != null ? parsedRepeat.floatValue() : 0.0f;
            follow = parsedFollow != null ? parsedFollow.booleanValue() : false;
            noLoop = parsedNoLoop != null ? parsedNoLoop.booleanValue() : false;
            placementMode = parsedPlacementMode != null
                    ? parsedPlacementMode : EffectPlacement.MODE_BOUNDS;

            if (!placement.containsKey("left")) {
                placement.put("left", "Center");
            }
            if (!placement.containsKey("right")) {
                placement.put("right", "Center");
            }
            if (!centering.containsKey("left")) {
                centering.put("left", "Center");
            }
            if (!centering.containsKey("right")) {
                centering.put("right", "Center");
            }
            if (!images.containsKey("left")) {
                images.put("left", "");
            }
            if (!images.containsKey("right")) {
                images.put("right", "");
            }
            if (!timings.containsKey("left")) {
                timings.put("left", "");
            }
            if (!timings.containsKey("right")) {
                timings.put("right", "");
            }

            if (!errors.isEmpty()) {
                throw new InvalidPonyException(errors);
            }
        }

        private String addActionName(Element element, String existing, List<String> errors) {
            if (existing != null) {
                errors.add("Too many <action> elements in <effect>.");
                return existing;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            text = text.replaceAll("\\s+", "");
            if (text.isEmpty()) {
                errors.add("<action> must name a trigger action.");
                return null;
            }
            return text;
        }

        private Float addSeconds(Element element, String tag, Float existing,
                List<String> errors) {
            if (existing != null) {
                errors.add("Too many <" + tag + "> elements.");
                return existing;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            try {
                float value = Float.parseFloat(text.replaceAll("\\s+", ""));
                if (Float.isNaN(value) || value < 0f || value > MAX_EFFECT_SECONDS) {
                    errors.add("<" + tag + "> must be between 0 and "
                            + (int)MAX_EFFECT_SECONDS + ".");
                    return null;
                }
                return Float.valueOf(value);
            } catch (NumberFormatException e) {
                errors.add("Invalid <" + tag + "> value.");
                return null;
            }
        }

        private Boolean addBoolean(Element element, String tag, Boolean existing,
                List<String> errors) {
            if (existing != null) {
                errors.add("Too many <" + tag + "> elements.");
                return existing;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            text = text.replaceAll("\\s+", "").toLowerCase();
            if (text.equals("true") || text.equals("yes") || text.equals("1")) {
                return Boolean.TRUE;
            }
            if (text.equals("false") || text.equals("no") || text.equals("0")) {
                return Boolean.FALSE;
            }
            errors.add("<" + tag + "> must be true or false.");
            return null;
        }

        private String addPlacementMode(Element element, String existing, List<String> errors) {
            if (existing != null) {
                errors.add("Too many <placementmode> elements.");
                return existing;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return null;
            }
            String canon = EffectPlacement.normalizeMode(text);
            String trimmed = text.replaceAll("\\s+", "").toLowerCase();
            if (!trimmed.equals(EffectPlacement.MODE_BOUNDS)
                    && !trimmed.equals(EffectPlacement.MODE_MOTION)) {
                errors.add("<placementmode> must be bounds or motion.");
                return null;
            }
            return canon;
        }

        private void addDirectedToken(Element element, String tag,
                Map<String, String> target, List<String> errors) {
            String direction = element.getAttribute("direction");
            if (!(direction.equals("left") || direction.equals("right"))) {
                errors.add("<" + tag + "> must have a direction of left or right.");
                return;
            }
            if (target.containsKey(direction)) {
                errors.add("Too many <" + tag + "> elements with direction " + direction + ".");
                return;
            }
            String text = getContent(element, errors);
            if (text == null) {
                return;
            }
            String canon = normalizePlacementToken(text);
            if (canon == null) {
                errors.add("Unknown <" + tag + "> value \"" + text.trim() + "\".");
                return;
            }
            target.put(direction, canon);
        }

        private void addImage(Element element, List<String> errors) {
            String direction = element.getAttribute("direction");
            if (!(direction.equals("left") || direction.equals("right"))) {
                errors.add("<image> must have a direction of left or right.");
                return;
            }
            if (images.containsKey(direction)) {
                errors.add("Too many <image> elements with direction " + direction + ".");
                return;
            }
            images.put(direction, getContent(element, errors).replaceAll("\\s+", ""));
        }

        private void addTimings(Element element, List<String> errors) {
            String direction = element.getAttribute("direction");
            if (!(direction.equals("left") || direction.equals("right"))) {
                errors.add("<timings> must have a direction of left or right.");
                return;
            }
            if (timings.containsKey(direction)) {
                errors.add("Too many <timings> elements with direction " + direction + ".");
                return;
            }
            timings.put(direction, getContent(element, errors));
        }
    }
    
    public Action[] actions;
    /**
     * Optional effect definitions (may be empty). Triggered when the named
     * {@link Effect#action} starts at runtime.
     */
    public Effect[] effects;
    public String startActions;
    /**
     * Pony-level drag successors used when an action has no drag override.
     * Empty means every action must list its own real drag next-actions
     * (legacy files).
     */
    public String defaultDrag;
    /**
     * Soft destination preference for traveling actions that
     * {@link WanderTarget#MOVE_INHERIT inherit} movement.
     * {@link WanderTarget#WANDER_HORIZONTAL}, {@link WanderTarget#WANDER_VERTICAL},
     * or {@link WanderTarget#WANDER_BOTH}. Defaults to horizontal.
     */
    public String wander;
    
    public PonyDefinition() {
        actions = new Action[0];
        effects = new Effect[0];
        startActions = "";
        defaultDrag = "";
        wander = WanderTarget.WANDER_HORIZONTAL;
    }
    
    public PonyDefinition(Document document) throws InvalidPonyException {
        List<String> errors = new ArrayList<String>();
        
        Element element = document.getDocumentElement();
        
        if (!element.getTagName().equals("pony")) {
            errors.add("The root element must be <pony>.");
            throw new InvalidPonyException(errors);
        }
        
        List<Action> actions = new ArrayList<Action>();
        List<Effect> effects = new ArrayList<Effect>();
        String parsedWander = null;
        
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE:
                {
                    String nodeName = node.getNodeName();
                    if (nodeName.equals("action")) {
                        try {
                            actions.add(new Action((Element)node));
                        } catch (InvalidPonyException e) {
                            errors.addAll(e.errors);
                        }
                    } else if (nodeName.equals("effect")) {
                        try {
                            effects.add(new Effect((Element)node));
                        } catch (InvalidPonyException e) {
                            errors.addAll(e.errors);
                        }
                    } else if (nodeName.equals("startactions")) {
                        if (startActions != null) {
                            errors.add("Too many <startactions> elements.");
                        } else {
                            startActions = getContent((Element)node, errors);
                        }
                    } else if (nodeName.equals("defaultdrag")) {
                        if (defaultDrag != null) {
                            errors.add("Too many <defaultdrag> elements.");
                        } else {
                            defaultDrag = getContent((Element)node, errors);
                        }
                    } else if (nodeName.equals("wander")) {
                        if (parsedWander != null) {
                            errors.add("Too many <wander> elements.");
                        } else {
                            String text = getContent((Element)node, errors);
                            if (text != null) {
                                String trimmed = text.replaceAll("\\s+", "");
                                if (trimmed.isEmpty()) {
                                    parsedWander = WanderTarget.WANDER_HORIZONTAL;
                                } else if (!WanderTarget.isKnownWander(trimmed)) {
                                    errors.add("Unknown <wander> value \"" + text.trim() + "\".");
                                } else {
                                    parsedWander = WanderTarget.normalizeWander(trimmed);
                                }
                            }
                        }
                    } else {
                        errors.add("Unexpected " + node.getNodeName() + " element.");
                    }
                    break;
                }
                
                case Node.TEXT_NODE:
                {
                    String text = node.getNodeValue().trim();
                    if (!text.isEmpty()) {
                        errors.add("Unexpected text " + text + ".");
                    }
                    break;
                }
                
                default:
                    errors.add("Unexpected " + node.getNodeName() + " node.");
                    break;
            }
        }
        
        if (!errors.isEmpty()) throw new InvalidPonyException(errors);
        
        this.actions = actions.toArray(new Action[actions.size()]);
        this.effects = effects.toArray(new Effect[effects.size()]);
        if (defaultDrag == null) {
            defaultDrag = "";
        }
        this.wander = parsedWander != null
                ? parsedWander
                : WanderTarget.WANDER_HORIZONTAL;
    }
    
    private static String getContent(Element container, List<String> errors) {
        String result = "";
        boolean valid = true;
        
        for (Node node = container.getFirstChild(); node != null; node = node.getNextSibling()) {
            switch (node.getNodeType()) {
                case Node.TEXT_NODE:
                    result += node.getNodeValue();
                    break;
                    
                default:
                    errors.add("Unexpected " + node.getNodeName() + " node.");
                    valid = false;
                    break;
            }
        }
        
        return valid ? result.trim() : null;
    }
    
    private boolean hasAction(String name) {
        for (int i = 0; i < actions.length; i++) {
            if (actions[i].name.equals(name)) return true;
        }
        return false;
    }
    
    /**
     * Reserved next/start-list tokens meaning "no real successor" ({@code none}
     * or {@code -}). Skipped at load; one-shot actions may fall through to the
     * other motion axis when the current list has only these.
     */
    public static boolean isNoneToken(String name) {
        if (name == null) {
            return false;
        }
        String t = name.trim();
        return t.equals("-") || t.equalsIgnoreCase("none");
    }

    /**
     * @return an error message when {@code name} cannot be used as a defined
     *         action (empty, reserved {@code none}/{@code -}, or contains
     *         {@code :}), else {@code null}
     */
    public static String illegalActionNameReason(String name) {
        if (name == null || name.isEmpty()) {
            return "Action name must not be empty";
        }
        if (isNoneToken(name)) {
            return "Action name \"" + name + "\" is reserved (use it only in next/start lists).";
        }
        if (name.indexOf(':') >= 0) {
            return "Action name \"" + name + "\" must not contain ':' (reserved for list weights).";
        }
        return null;
    }
    
    /**
     * @return true if {@code value} lists at least one non-empty token
     *         (including reserved {@link #isNoneToken none} tokens)
     */
    public static boolean actionListHasTokens(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String[] names = value.split(",");
        for (int i = 0; i < names.length; i++) {
            if (!names[i].trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Drag successor list for {@code action}: the per-action override if it
     * has any tokens, otherwise {@link #defaultDrag}.
     */
    public String effectiveDragActions(Action action) {
        if (action == null) {
            return defaultDrag != null ? defaultDrag : "";
        }
        String override = action.nextActions.get("drag");
        if (actionListHasTokens(override)) {
            return override;
        }
        return defaultDrag != null ? defaultDrag : "";
    }
    
    /**
     * @return true if {@code value} lists at least one real action name (not
     *         empty, not only {@link #isNoneToken none} tokens)
     */
    public static boolean actionListHasReal(String value) {
        List<ActionListEntry> entries = parseActionList(value, null);
        for (int i = 0; i < entries.size(); i++) {
            if (!isNoneToken(entries.get(i).name)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Validates a comma-separated action list. Allows reserved {@code none}/{@code -}
     * tokens and {@code name:N} weights; other names must be defined actions.
     * Does not require a real successor (callers enforce that per list / action).
     */
    private void validateActionList(String value, String field1, String field2, List<String> errors) {
        if (value == null || value.length() == 0) {
            errors.add("Missing " + field1 + field2 + ".");
            return;
        }
        if (!actionListHasTokens(value)) {
            errors.add("Missing " + field1 + field2 + ".");
            return;
        }
        List<String> listErrors = new ArrayList<String>();
        List<ActionListEntry> entries = parseActionList(value, listErrors);
        for (int i = 0; i < listErrors.size(); i++) {
            errors.add(field1 + field2 + ": " + listErrors.get(i));
        }
        for (int i = 0; i < entries.size(); i++) {
            String n = entries.get(i).name;
            if (isNoneToken(n)) {
                continue;
            }
            if (!hasAction(n)) {
                errors.add("Action " + n + " not defined.");
            }
        }
    }
    
    private void validateIntegerList(String value, String field1, String field2, List<String> errors) {
        if (value.length() == 0) {
            errors.add("Missing " + field1 + field2 + ".");
        } else {
            String[] valArray = value.split(",");
            try {
                for (int i = 0; i < valArray.length; i++) {
                    Integer.parseInt(valArray[i]);
                }
            } catch (NumberFormatException e) {
                errors.add("Invalid integer in " + field1 + field2 + ".");
            }
        }
    }
    
    public void validate() throws InvalidPonyException {
        List<String> errors = new ArrayList<String>();
        
        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            String name = action.name;
            
            for (int j = 0; j < actions.length; j++) {
                if (i != j && name.equals(actions[j].name)) {
                    errors.add("Multiple actions with name " + name);
                }
            }
            
            String nameError = illegalActionNameReason(name);
            if (nameError != null) {
                errors.add(nameError);
            }
            
            String specialType = action.specialType;
            if (!isKnownSpecialType(specialType)) {
                errors.add("Invalid specialtype for " + name + ".");
            }
            
            if (Float.isNaN(action.speed) || action.speed <= 0f) {
                errors.add("Invalid speed for " + name + " (must be positive).");
            }
            
            validateAnchorMap(action.anchorX, "anchorx", name, errors);
            validateAnchorMap(action.anchorY, "anchory", name, errors);
            
            if (action.spritesFrom == null) {
                action.spritesFrom = "";
            }
            if (action.gaits == null) {
                action.gaits = "";
            }
            if (action.movement == null || action.movement.isEmpty()) {
                action.movement = WanderTarget.MOVE_INHERIT;
            } else if (!WanderTarget.isKnownMovement(action.movement)) {
                errors.add("Unknown movement for " + name + " (\"" + action.movement + "\").");
            } else {
                action.movement = WanderTarget.normalizeMovement(action.movement);
            }
            
            boolean alias = action.isAlias();
            if (alias) {
                if (action.spritesFrom.equals(name)) {
                    errors.add("Action " + name + " cannot use spritesfrom itself.");
                } else if (!hasAction(action.spritesFrom)) {
                    errors.add("Action " + name + " spritesfrom \"" + action.spritesFrom + "\" not defined.");
                } else {
                    Action owner = findAction(action.spritesFrom);
                    if (owner != null && owner.isAlias()) {
                        errors.add("Action " + name + " spritesfrom \"" + action.spritesFrom
                                + "\" is itself an alias (chains are not allowed).");
                    }
                }
                if (!action.images.get("left").isEmpty() || !action.images.get("right").isEmpty()) {
                    errors.add("Alias action " + name + " must not define images (inherited from "
                            + action.spritesFrom + ").");
                }
                if (!action.timings.get("left").isEmpty() || !action.timings.get("right").isEmpty()) {
                    errors.add("Alias action " + name + " must not define timings (inherited from "
                            + action.spritesFrom + ").");
                }
            } else {
                if (action.images.get("left").isEmpty()) {
                    errors.add("Missing left image for " + name + ".");
                }
                validateIntegerList(action.timings.get("left"), "left timings for ", name, errors);
                if (action.images.get("right").isEmpty()) {
                    errors.add("Missing right image for " + name + ".");
                }
                validateIntegerList(action.timings.get("right"), "right timings for ", name, errors);
            }
            
            if (!action.gaits.isEmpty()) {
                List<String> gaitErrors = new ArrayList<String>();
                List<GaitEntry> entries = parseGaits(action.gaits, gaitErrors);
                if (!gaitErrors.isEmpty()) {
                    for (int g = 0; g < gaitErrors.size(); g++) {
                        errors.add("Action " + name + ": " + gaitErrors.get(g));
                    }
                } else if (entries.isEmpty()) {
                    errors.add("Action " + name + " has empty <gaits>.");
                }
            }
            
            validateActionList(action.nextActions.get("waiting"), "waiting actions for ", name, errors);
            validateActionList(action.nextActions.get("moving"), "moving actions for ", name, errors);
            String dragOverride = action.nextActions.get("drag");
            if (actionListHasTokens(dragOverride)) {
                validateActionList(dragOverride, "drag override for ", name, errors);
            }
            
            boolean hasWait = actionListHasReal(action.nextActions.get("waiting"));
            boolean hasMove = actionListHasReal(action.nextActions.get("moving"));
            boolean hasDrag = actionListHasReal(effectiveDragActions(action));
            boolean screenOut = SPECIAL_SCREEN_OUT.equals(action.specialType);
            if (screenOut) {
                // Completion leaves the scene; next waiting/moving are unused.
                if (!hasDrag) {
                    errors.add("Action " + name
                            + " needs a real next drag action (set Default drag or a drag override; "
                            + "none/- alone is not allowed for drag).");
                }
            } else if (!action.loops) {
                // One-shots may use none/- on waiting or moving so they fall through
                // to the other axis; at least one of those two must be real.
                if (!hasWait && !hasMove) {
                    errors.add("One-shot action " + name
                            + " needs a real next waiting or moving action (not only none/-).");
                }
                if (!hasDrag) {
                    errors.add("Action " + name
                            + " needs a real next drag action (set Default drag or a drag override; "
                            + "none/- alone is not allowed for drag).");
                }
            } else {
                if (!hasWait) {
                    errors.add("Looping action " + name + " needs a real next waiting action.");
                }
                // Empty moving is allowed: sit/sleep/etc. re-pick waiting when
                // the idle timer ends instead of starting travel.
                if (!hasDrag) {
                    errors.add("Looping action " + name
                            + " needs a real next drag action (set Default drag or a drag override).");
                }
            }
        }
        
        validateActionList(startActions, "start actions", "", errors);
        if (!actionListHasReal(startActions)) {
            errors.add("Start actions must list at least one real action (not only none/-).");
        }
        
        if (actionListHasTokens(defaultDrag)) {
            validateActionList(defaultDrag, "default drag actions", "", errors);
            if (!actionListHasReal(defaultDrag)) {
                errors.add("Default drag must list at least one real action (not only none/-).");
            }
        }

        if (wander == null || wander.isEmpty()) {
            wander = WanderTarget.WANDER_HORIZONTAL;
        } else if (!WanderTarget.isKnownWander(wander)) {
            errors.add("Unknown <wander> value \"" + wander + "\".");
        } else {
            wander = WanderTarget.normalizeWander(wander);
        }

        if (!canReachSceneExit()) {
            errors.add("No reachable action can leave the scene (need a next moving "
                    + "action that walks, teleports out, or is screen-out).");
        }

        validateEffects(errors);
        
        if (!errors.isEmpty()) throw new InvalidPonyException(errors);
    }

    private void validateEffects(List<String> errors) {
        if (effects == null) {
            effects = new Effect[0];
            return;
        }
        for (int i = 0; i < effects.length; i++) {
            Effect effect = effects[i];
            String label = (effect.name == null || effect.name.isEmpty())
                    ? ("effect #" + (i + 1)) : ("Effect " + effect.name);

            if (effect.name == null || effect.name.isEmpty()) {
                errors.add(label + " must have a name.");
            } else if (effect.name.indexOf(':') >= 0) {
                errors.add(label + " name must not contain ':'.");
            }

            if (effect.action == null || effect.action.isEmpty()) {
                errors.add(label + " needs a trigger <action>.");
            } else if (!hasAction(effect.action)) {
                errors.add(label + " trigger action \"" + effect.action + "\" not defined.");
            }

            if (Float.isNaN(effect.duration) || effect.duration < 0f
                    || effect.duration > MAX_EFFECT_SECONDS) {
                errors.add(label + " duration must be between 0 and "
                        + (int)MAX_EFFECT_SECONDS + ".");
            }
            if (Float.isNaN(effect.repeatDelay) || effect.repeatDelay < 0f
                    || effect.repeatDelay > MAX_EFFECT_SECONDS) {
                errors.add(label + " repeatdelay must be between 0 and "
                        + (int)MAX_EFFECT_SECONDS + ".");
            }

            effect.placementMode = EffectPlacement.normalizeMode(effect.placementMode);

            validateEffectFacing(effect, "left", label, errors);
            validateEffectFacing(effect, "right", label, errors);
        }
    }

    private void validateEffectFacing(Effect effect, String direction, String label,
            List<String> errors) {
        String place = effect.placement.get(direction);
        if (place == null || place.isEmpty()) {
            errors.add(label + " needs a " + direction + " placement.");
        } else {
            String canon = normalizePlacementToken(place);
            if (canon == null) {
                errors.add(label + " has unknown " + direction + " placement \"" + place + "\".");
            } else {
                effect.placement.put(direction, canon);
            }
        }

        String center = effect.centering.get(direction);
        if (center == null || center.isEmpty()) {
            errors.add(label + " needs a " + direction + " centering.");
        } else {
            String canon = normalizePlacementToken(center);
            if (canon == null) {
                errors.add(label + " has unknown " + direction + " centering \"" + center + "\".");
            } else if ("Any".equals(canon) || "Any-Not_Center".equals(canon)) {
                // Centering on a random cell of the effect image is meaningless.
                errors.add(label + " " + direction
                        + " centering cannot be Any or Any-Not_Center.");
            } else {
                effect.centering.put(direction, canon);
            }
        }

        if (effect.images.get(direction) == null || effect.images.get(direction).isEmpty()) {
            errors.add(label + " missing " + direction + " image.");
        }
        validateIntegerList(effect.timings.get(direction),
                direction + " timings for ", label, errors);
    }

    /**
     * Soft issues that do not make the pony unusable. Currently reports
     * actions that are defined but never reachable from {@link #startActions}
     * via waiting, moving, or effective drag lists.
     *
     * @return warning messages (empty when none); never {@code null}
     */
    public List<String> collectWarnings() {
        List<String> warnings = new ArrayList<String>();
        if (actions == null || actions.length == 0) {
            return warnings;
        }
        List<String> reachable = reachableViaWaitMoveDrag();
        for (int i = 0; i < actions.length; i++) {
            String name = actions[i].name;
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (!reachable.contains(name)) {
                warnings.add("Action " + name
                        + " is defined but not used (unreachable from start via waiting, moving, or drag).");
            }
        }
        return warnings;
    }

    /**
     * True when some action reachable from {@link #startActions} via waiting
     * and moving lists can start a leave (walk, teleport-out, or screen-out).
     * Sit/sleep with {@code moving=none} do not count; they must reach a pose
     * that is allowed to leave. Drag is ignored here — dragging is not how
     * the wallpaper rotates the herd off-screen.
     */
    private boolean canReachSceneExit() {
        if (actions == null || !actionListHasReal(startActions)) {
            return false;
        }
        List<String> seen = new ArrayList<String>();
        List<String> queue = new ArrayList<String>();
        addReachableNames(startActions, seen, queue);
        for (int i = 0; i < queue.size(); i++) {
            Action action = findAction(queue.get(i));
            if (action == null) {
                continue;
            }
            if (actionCanStartLeave(action)) {
                return true;
            }
            addReachableNames(action.nextActions.get("waiting"), seen, queue);
            addReachableNames(action.nextActions.get("moving"), seen, queue);
        }
        return false;
    }

    /**
     * Action names reachable from {@link #startActions} by following waiting,
     * moving, and {@link #effectiveDragActions(Action) effective drag} lists.
     * Used for unused-action warnings; does not follow {@code spritesfrom}.
     */
    private List<String> reachableViaWaitMoveDrag() {
        List<String> seen = new ArrayList<String>();
        if (actions == null || !actionListHasReal(startActions)) {
            return seen;
        }
        List<String> queue = new ArrayList<String>();
        addReachableNames(startActions, seen, queue);
        for (int i = 0; i < queue.size(); i++) {
            Action action = findAction(queue.get(i));
            if (action == null) {
                continue;
            }
            addReachableNames(action.nextActions.get("waiting"), seen, queue);
            addReachableNames(action.nextActions.get("moving"), seen, queue);
            addReachableNames(effectiveDragActions(action), seen, queue);
        }
        return seen;
    }

    private boolean actionCanStartLeave(Action action) {
        if (action == null) {
            return false;
        }
        String moving = action.nextActions.get("moving");
        if (!actionListHasReal(moving)) {
            return false;
        }
        List<ActionListEntry> entries = parseActionList(moving, null);
        for (int i = 0; i < entries.size(); i++) {
            String n = entries.get(i).name;
            if (isNoneToken(n)) {
                continue;
            }
            Action mover = findAction(n);
            if (mover != null && isLeaveMover(mover)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Walkers, {@code teleport-out}, and {@code screen-out} can take the pony
     * off-screen. Appear / teleport-in clips only land in place.
     */
    private static boolean isLeaveMover(Action mover) {
        if (mover == null) {
            return false;
        }
        String special = mover.specialType != null ? mover.specialType : "";
        if (SPECIAL_SCREEN_IN.equals(special) || SPECIAL_TELEPORT_IN.equals(special)) {
            return false;
        }
        return true;
    }

    private void addReachableNames(String list, List<String> seen, List<String> queue) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<ActionListEntry> entries = parseActionList(list, null);
        for (int i = 0; i < entries.size(); i++) {
            String n = entries.get(i).name;
            if (n.isEmpty() || isNoneToken(n) || !hasAction(n)) {
                continue;
            }
            if (!seen.contains(n)) {
                seen.add(n);
                queue.add(n);
            }
        }
    }
    
    private Action findAction(String name) {
        for (int i = 0; i < actions.length; i++) {
            if (actions[i].name.equals(name)) {
                return actions[i];
            }
        }
        return null;
    }
    
    private static String formatSpeed(float speed) {
        // Avoid ugly binary float tails for common values like 0.5 / 0.7 / 1.
        if (speed == (int)speed) {
            return Integer.toString((int)speed);
        }
        return Float.toString(speed);
    }
    
    private static void validateAnchorMap(Map<String, Float> map, String tag, String actionName,
            List<String> errors) {
        String[] dirs = { "left", "right" };
        for (int i = 0; i < dirs.length; i++) {
            Float value = map.get(dirs[i]);
            if (value != null && !Float.isNaN(value.floatValue()) && value.floatValue() < 0f) {
                errors.add("Invalid " + tag + " (" + dirs[i] + ") for " + actionName
                        + " (must be non-negative).");
            }
        }
    }
    
    /**
     * Writes {@code <tag>} for left/right feet anchors. When both facings share
     * the same explicit value, emits one bare element (legacy shape). When only
     * one facing is set, or they differ, emits directed elements.
     */
    private static void writeAnchorElements(PrintWriter writer, String tag, float left, float right) {
        boolean leftSet = !Float.isNaN(left);
        boolean rightSet = !Float.isNaN(right);
        if (!leftSet && !rightSet) {
            return;
        }
        if (leftSet && rightSet && left == right) {
            writer.print("        <");
            writer.print(tag);
            writer.print(">");
            writeCharacters(writer, formatSpeed(left));
            writer.print("</");
            writer.print(tag);
            writer.println(">");
            return;
        }
        if (leftSet) {
            writer.print("        <");
            writer.print(tag);
            writeAttribute(writer, "direction", "left");
            writer.print(">");
            writeCharacters(writer, formatSpeed(left));
            writer.print("</");
            writer.print(tag);
            writer.println(">");
        }
        if (rightSet) {
            writer.print("        <");
            writer.print(tag);
            writeAttribute(writer, "direction", "right");
            writer.print(">");
            writeCharacters(writer, formatSpeed(right));
            writer.print("</");
            writer.print(tag);
            writer.println(">");
        }
    }
    
    private static void writeAttribute(PrintWriter writer, String name, String value) {
        writer.print(" ");
        writer.print(name);
        writer.print("=\"");
        writer.print(value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll("\"", "&quot;"));
        writer.print("\"");
    }
    
    private static void writeCharacters(PrintWriter writer, String value) {
        writer.print(value.replaceAll("&", "&amp;").replaceAll("<", "&lt;"));
    }
    
    private static void writeSplit(PrintWriter writer, String value, String indent) {
        final int N = 128;
        for (int i = 0; i < value.length(); i += N) {
            writer.print(indent);
            writeCharacters(writer, value.substring(i, Math.min(i + N, value.length())));
            writer.println();
        }
    }
    
    public void writeDefinition(PrintWriter writer) {
        writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        writer.println("<pony>");
        
        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            if (action.spritesFrom == null) {
                action.spritesFrom = "";
            }
            if (action.gaits == null) {
                action.gaits = "";
            }
            if (action.movement == null || action.movement.isEmpty()) {
                action.movement = WanderTarget.MOVE_INHERIT;
            } else {
                action.movement = WanderTarget.normalizeMovement(action.movement);
            }
            
            writer.print("    <action");
            writeAttribute(writer, "name", action.name);
            writer.println(">");
            
            if (!action.specialType.isEmpty()) {
                writer.print("        <specialtype>");
                writeCharacters(writer, action.specialType);
                writer.println("</specialtype>");
            }
            
            // Always write speed so editors round-trip; 1 is the default.
            writer.print("        <speed>");
            writeCharacters(writer, formatSpeed(action.speed));
            writer.println("</speed>");
            
            // Omitted <loop> means true; only write the uncommon non-looping case.
            if (!action.loops) {
                writer.println("        <loop>false</loop>");
            }

            // Omitted <movement> means inherit pony <wander>.
            if (!WanderTarget.MOVE_INHERIT.equals(action.movement)) {
                writer.print("        <movement>");
                writeCharacters(writer, action.movement);
                writer.println("</movement>");
            }
            
            // Omitted anchors mean frame centre (X) / frame bottom (Y).
            // Same value on both facings → bare tag (legacy-compatible); else direction=.
            writeAnchorElements(writer, "anchorx", action.getAnchorX("left"), action.getAnchorX("right"));
            writeAnchorElements(writer, "anchory", action.getAnchorY("left"), action.getAnchorY("right"));
            
            if (action.isAlias()) {
                writer.print("        <spritesfrom>");
                writeCharacters(writer, action.spritesFrom);
                writer.println("</spritesfrom>");
            } else {
                writer.println("        <image direction=\"left\">");
                writeSplit(writer, action.images.get("left"), "            ");
                writer.println("        </image>");
                
                writer.print("        <timings direction=\"left\">");
                writeCharacters(writer, action.timings.get("left"));
                writer.println("</timings>");
                
                writer.println("        <image direction=\"right\">");
                writeSplit(writer, action.images.get("right"), "            ");
                writer.println("        </image>");
                
                writer.print("        <timings direction=\"right\">");
                writeCharacters(writer, action.timings.get("right"));
                writer.println("</timings>");
            }
            
            if (!action.gaits.isEmpty()) {
                writer.print("        <gaits>");
                writeCharacters(writer, action.gaits);
                writer.println("</gaits>");
            }
            
            writer.print("        <nextactions type=\"waiting\">");
            writeCharacters(writer, action.nextActions.get("waiting"));
            writer.println("</nextactions>");
            
            writer.print("        <nextactions type=\"moving\">");
            writeCharacters(writer, action.nextActions.get("moving"));
            writer.println("</nextactions>");
            
            String dragOverride = action.nextActions.get("drag");
            if (actionListHasTokens(dragOverride)) {
                writer.print("        <nextactions type=\"drag\">");
                writeCharacters(writer, dragOverride);
                writer.println("</nextactions>");
            }
            
            writer.println("    </action>");
        }

        if (effects != null) {
            for (int i = 0; i < effects.length; i++) {
                writeEffect(writer, effects[i]);
            }
        }
        
        writer.print("    <startactions>");
        writeCharacters(writer, startActions);
        writer.println("</startactions>");
        
        if (actionListHasTokens(defaultDrag)) {
            writer.print("    <defaultdrag>");
            writeCharacters(writer, defaultDrag);
            writer.println("</defaultdrag>");
        }

        // Always write wander so editors round-trip; horizontal is the default.
        String wanderOut = wander == null || wander.isEmpty()
                ? WanderTarget.WANDER_HORIZONTAL
                : WanderTarget.normalizeWander(wander);
        writer.print("    <wander>");
        writeCharacters(writer, wanderOut);
        writer.println("</wander>");
        
        writer.println("</pony>");
    }

    private static void writeEffect(PrintWriter writer, Effect effect) {
        writer.print("    <effect");
        writeAttribute(writer, "name", effect.name != null ? effect.name : "");
        writer.println(">");

        writer.print("        <action>");
        writeCharacters(writer, effect.action != null ? effect.action : "");
        writer.println("</action>");

        writer.print("        <duration>");
        writeCharacters(writer, formatSpeed(effect.duration));
        writer.println("</duration>");

        if (effect.repeatDelay > 0f) {
            writer.print("        <repeatdelay>");
            writeCharacters(writer, formatSpeed(effect.repeatDelay));
            writer.println("</repeatdelay>");
        }

        if (effect.follow) {
            writer.println("        <follow>true</follow>");
        }
        if (effect.noLoop) {
            writer.println("        <noloop>true</noloop>");
        }
        if (EffectPlacement.isMotionMode(effect.placementMode)) {
            writer.println("        <placementmode>motion</placementmode>");
        }

        writeDirectedToken(writer, "placement", "right", effect.placement.get("right"));
        writeDirectedToken(writer, "centering", "right", effect.centering.get("right"));
        writeDirectedToken(writer, "placement", "left", effect.placement.get("left"));
        writeDirectedToken(writer, "centering", "left", effect.centering.get("left"));

        writer.println("        <image direction=\"left\">");
        writeSplit(writer, nullToEmpty(effect.images.get("left")), "            ");
        writer.println("        </image>");
        writer.print("        <timings direction=\"left\">");
        writeCharacters(writer, nullToEmpty(effect.timings.get("left")));
        writer.println("</timings>");

        writer.println("        <image direction=\"right\">");
        writeSplit(writer, nullToEmpty(effect.images.get("right")), "            ");
        writer.println("        </image>");
        writer.print("        <timings direction=\"right\">");
        writeCharacters(writer, nullToEmpty(effect.timings.get("right")));
        writer.println("</timings>");

        writer.println("    </effect>");
    }

    private static void writeDirectedToken(PrintWriter writer, String tag, String direction,
            String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        writer.print("        <");
        writer.print(tag);
        writeAttribute(writer, "direction", direction);
        writer.print(">");
        writeCharacters(writer, value);
        writer.print("</");
        writer.print(tag);
        writer.println(">");
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
    
}
