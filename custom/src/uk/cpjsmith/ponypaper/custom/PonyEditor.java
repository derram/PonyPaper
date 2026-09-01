package uk.cpjsmith.ponypaper.custom;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import uk.cpjsmith.ponypaper.EffectPlacement;
import uk.cpjsmith.ponypaper.PonyDefinition;
import uk.cpjsmith.ponypaper.WanderTarget;

/**
 * Wraps a {@code PonyDefinition} with the operational functions needed to
 * provide an editor. Also contains the {@code main(String[])} function to
 * start a graphical or command-line editor.
 */
public class PonyEditor {
    
    /**
     * Represents an error that should be displayed to the user.
     */
    public static class GenericException extends Exception {
        
        /** Contains one or more lines of text to show to the user. */
        public String[] detail;
        
        /**
         * Creates a new {@code GenericException} object.
         * 
         * @param message a brief description of the error
         * @param detail  contains enough the information that the user just
         *                does know what went wrong
         */
        public GenericException(String message, String... detail) {
            super(message);
            this.detail = detail;
        }
        
    }
    
    private PonyDefinition ponyDefinition;
    
    /**
     * Creates a new editor instance with a blank pony.
     */
    public PonyEditor() {
        ponyDefinition = new PonyDefinition();
    }
    
    /**
     * Replaces the current pony with a blank slate.
     */
    public void reset() {
        ponyDefinition = new PonyDefinition();
    }
    
    /**
     * Loads a pony definition from the given file. If loading fails, a {@code
     * GenericException} is thrown and the current pony is unchanged.
     * 
     * @param file the file to load from
     * @throws GenericException if the file cannot be opened or is invalid
     */
    public void load(File file) throws GenericException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder;
        try {
            docBuilder = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("An internal error occurred, cannot load file", e);
        }
        
        Document document;
        try {
            document = docBuilder.parse(file);
        } catch (IOException e) {
            throw new GenericException("Invalid File", "Failed to read " + file);
        } catch (SAXException e) {
            throw new GenericException("Invalid Pony", "Failed to load " + file + " due to XML errors.");
        }
        
        try {
            ponyDefinition = new PonyDefinition(document);
        } catch (PonyDefinition.InvalidPonyException e) {
            String[] messages = new String[e.errors.size() + 1];
            messages[0] = "Failed to load " + file + " due to the following errors:";
            for (int i = 0; i < e.errors.size(); i++) messages[i + 1] = e.errors.get(i);
            throw new GenericException("Invalid Pony", messages);
        }
    }
    
    /**
     * Saves the current pony to the given file.
     * 
     * @param file the file to save to
     * @throws GenericException if the file cannot be written
     */
    public void save(File file) throws GenericException {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(file);
            ponyDefinition.writeDefinition(writer);
        } catch (IOException e) {
            throw new GenericException("File Error", "An error occurred writing " + file + ".");
        } finally {
            if (writer != null) writer.close();
        }
    }
    
    /**
     * Checks that the pony is currently valid. If this method raises an
     * exception, the pony will not be usable in PonyPaper; the user should be
     * usually be warned of this prior to saving.
     * 
     * @throws GenericException if the pony is invalid
     */
    public void validate() throws GenericException {
        try {
            ponyDefinition.validate();
        } catch (PonyDefinition.InvalidPonyException e) {
            String[] messages = new String[e.errors.size() + 1];
            messages[0] = "The current pony fails to validate, it will not be usable in the app due to the following errors:";
            for (int i = 0; i < e.errors.size(); i++) messages[i + 1] = e.errors.get(i);
            throw new GenericException("Invalid Pony", messages);
        }
    }

    /**
     * Soft issues that do not make the pony unusable (for example defined
     * actions that are unreachable from start). Empty when there are none.
     *
     * @return warning lines; never {@code null}
     */
    public List<String> collectWarnings() {
        return ponyDefinition.collectWarnings();
    }
    
    /**
     * Returns the pony's start actions.
     * 
     * @return the start actions as a comma-separated string
     */
    public String getStartActions() {
        return ponyDefinition.startActions;
    }
    
    /**
     * Changes the pony's start actions.
     * 
     * @param actionNames the start actions as a comma-separated string
     */
    public void setStartActions(String actionNames) {
        ponyDefinition.startActions = actionNames;
    }
    
    /**
     * Returns the pony-level default drag actions.
     *
     * @return the default drag actions as a comma-separated string
     */
    public String getDefaultDrag() {
        return ponyDefinition.defaultDrag != null ? ponyDefinition.defaultDrag : "";
    }
    
    /**
     * Changes the pony-level default drag actions. Actions with an empty
     * drag override inherit this list.
     *
     * @param actionNames the default drag actions as a comma-separated string
     */
    public void setDefaultDrag(String actionNames) {
        ponyDefinition.defaultDrag = actionNames != null ? actionNames : "";
    }

    /**
     * Pony-level wander default / authoring mode.
     *
     * @return {@code horizontal}, {@code vertical}, or {@code both}
     */
    public String getWander() {
        return WanderTarget.normalizeWander(ponyDefinition.wander);
    }

    /**
     * Sets the pony-level wander default / authoring mode.
     *
     * @param wander {@code horizontal}, {@code vertical}, or {@code both}
     */
    public void setWander(String wander) {
        ponyDefinition.wander = WanderTarget.normalizeWander(wander);
    }
    
    /**
     * Returns the number of actions that the pony has. All methods that take
     * an action index require it to be at least {@code 0} and strictly less
     * than {@code getActionCount()}; they throw {@code
     * IndexOutOfBoundsException} otherwise.
     * 
     * @return the number of actions
     */
    public int getActionCount() {
        return ponyDefinition.actions.length;
    }
    
    /**
     * Finds an action with the given name.
     * 
     * @param name the name of the action
     * @return the index of the action or {@code -1} if the name was not found
     */
    public int findAction(String name) {
        for (int i = 0; i < ponyDefinition.actions.length; i++) {
            if (ponyDefinition.actions[i].name.equals(name)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Creates a new action with the given name.
     * 
     * @param name the name of the action
     * @return the index of the newly created action
     */
    public int addAction(String name) {
        checkActionName(name);
        PonyDefinition.Action[] oldActions = ponyDefinition.actions;
        int oldCount = oldActions.length;
        int newCount = oldCount + 1;
        PonyDefinition.Action[] newActions = new PonyDefinition.Action[newCount];
        for (int i = 0; i < oldCount; i++) newActions[i] = oldActions[i];
        
        newActions[oldCount] = new PonyDefinition.Action();
        newActions[oldCount].name = name;
        // Ensure new fields are non-null for older code paths.
        newActions[oldCount].spritesFrom = "";
        newActions[oldCount].gaits = "";
        newActions[oldCount].movement =
                WanderTarget.defaultMovementForWander(getWander());
        
        ponyDefinition.actions = newActions;
        
        return oldCount;
    }
    
    /**
     * Removes an action and strips its name from every next-action list and
     * from the start-actions list so no dangling references remain.
     * 
     * @param index the index of the action to remove
     * @throws IndexOutOfBoundsException if {@code index < 0 || index >=
     *                                   getActionCount()}
     */
    public void removeAction(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        
        PonyDefinition.Action[] oldActions = ponyDefinition.actions;
        int oldCount = oldActions.length;
        int newCount = oldCount - 1;
        PonyDefinition.Action[] newActions = new PonyDefinition.Action[newCount];
        
        for (int i = 0; i < index; i++) newActions[i] = oldActions[i];
        for (int i = index; i < newCount; i++) newActions[i] = oldActions[i + 1];
        
        ponyDefinition.actions = newActions;
        scrubMissingActionReferences();
    }

    /**
     * Drops any action names from next/start lists that are not present in the
     * current action set (e.g. after a delete or a partial import). Also clears
     * {@code spritesfrom} when the owner was removed, and removes effects whose
     * trigger action no longer exists.
     */
    private void scrubMissingActionReferences() {
        java.util.Set<String> present = new java.util.HashSet<String>();
        for (int i = 0; i < ponyDefinition.actions.length; i++) {
            present.add(ponyDefinition.actions[i].name);
        }
        for (int i = 0; i < ponyDefinition.actions.length; i++) {
            setActionNext(i, "waiting", filterActionList(getActionNext(i, "waiting"), present));
            setActionNext(i, "moving", filterActionList(getActionNext(i, "moving"), present));
            setActionNext(i, "drag", filterActionList(getActionNext(i, "drag"), present));
            String from = ponyDefinition.actions[i].spritesFrom;
            if (from != null && !from.isEmpty() && !present.contains(from)) {
                ponyDefinition.actions[i].spritesFrom = "";
            }
        }
        setStartActions(filterActionList(getStartActions(), present));
        setDefaultDrag(filterActionList(getDefaultDrag(), present));
        scrubMissingEffectTriggers(present);
    }

    /** Removes effects whose trigger action is not in {@code present}. */
    private void scrubMissingEffectTriggers(java.util.Set<String> present) {
        ensureEffectsArray();
        PonyDefinition.Effect[] old = ponyDefinition.effects;
        if (old.length == 0) {
            return;
        }
        java.util.ArrayList<PonyDefinition.Effect> keep =
                new java.util.ArrayList<PonyDefinition.Effect>();
        for (int i = 0; i < old.length; i++) {
            String trigger = old[i].action;
            if (trigger != null && present.contains(trigger)) {
                keep.add(old[i]);
            }
        }
        if (keep.size() != old.length) {
            ponyDefinition.effects = keep.toArray(new PonyDefinition.Effect[keep.size()]);
        }
    }
    
    public String getActionName(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        return ponyDefinition.actions[index].name;
    }
    
    /**
     * Renames an action and rewrites every next-action list, the start
     * actions list, and default drag so references to the old name point at the new one.
     *
     * @param index the index of the action to rename
     * @param name  the new name (must be non-empty and not used by another action)
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     * @throws IllegalArgumentException if {@code name} is empty or already used
     */
    public void setActionName(int index, String name) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        checkActionName(name);
        String oldName = ponyDefinition.actions[index].name;
        if (oldName.equals(name)) {
            return;
        }
        for (int i = 0; i < ponyDefinition.actions.length; i++) {
            if (i != index && ponyDefinition.actions[i].name.equals(name)) {
                throw new IllegalArgumentException("Action name already in use: " + name);
            }
        }
        ponyDefinition.actions[index].name = name;
        renameActionReferences(oldName, name);
    }

    /**
     * Replaces {@code oldName} with {@code newName} in every next/start list,
     * {@code spritesfrom} references, and effect trigger actions.
     */
    private void renameActionReferences(String oldName, String newName) {
        for (int i = 0; i < ponyDefinition.actions.length; i++) {
            setActionNext(i, "waiting", renameInActionList(getActionNext(i, "waiting"), oldName, newName));
            setActionNext(i, "moving", renameInActionList(getActionNext(i, "moving"), oldName, newName));
            setActionNext(i, "drag", renameInActionList(getActionNext(i, "drag"), oldName, newName));
            if (oldName.equals(ponyDefinition.actions[i].spritesFrom)) {
                ponyDefinition.actions[i].spritesFrom = newName;
            }
        }
        setStartActions(renameInActionList(getStartActions(), oldName, newName));
        setDefaultDrag(renameInActionList(getDefaultDrag(), oldName, newName));
        ensureEffectsArray();
        for (int i = 0; i < ponyDefinition.effects.length; i++) {
            if (oldName.equals(ponyDefinition.effects[i].action)) {
                ponyDefinition.effects[i].action = newName;
            }
        }
    }

    private static String renameInActionList(String list, String oldName, String newName) {
        return PonyDefinition.renameInActionList(list, oldName, newName);
    }

    private static void checkActionName(String name) {
        String reason = PonyDefinition.illegalActionNameReason(name);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
    }
    
    public String getActionSpecial(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        return ponyDefinition.actions[index].specialType;
    }
    
    public void setActionSpecial(int index, String specialType) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        ponyDefinition.actions[index].specialType = specialType;
    }
    
    /**
     * Feet column for {@code direction} ({@code "left"} or {@code "right"}),
     * pixels from the left of that sheet's frame, or {@link Float#NaN} when
     * unset (frame-centre default).
     */
    public float getActionAnchorX(int index, String direction) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        checkAnchorDirection(direction);
        return ponyDefinition.actions[index].getAnchorX(direction);
    }
    
    /**
     * Sets the feet hotspot X for one facing. Pass {@link Float#NaN} or a
     * negative value to clear (use frame centre). Non-negative values are
     * unscaled pixels from the left of that direction's frame.
     */
    public void setActionAnchorX(int index, String direction, float anchorX) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        checkAnchorDirection(direction);
        ponyDefinition.actions[index].setAnchorX(direction, anchorX);
    }
    
    /**
     * Sets the feet hotspot X for both facings (legacy / bulk clear or assign).
     */
    public void setActionAnchorX(int index, float anchorX) {
        setActionAnchorX(index, "left", anchorX);
        setActionAnchorX(index, "right", anchorX);
    }
    
    /**
     * Feet row for {@code direction} ({@code "left"} or {@code "right"}),
     * pixels from the top of that sheet's frame, or {@link Float#NaN} when
     * unset (bottom-center default).
     */
    public float getActionAnchorY(int index, String direction) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        checkAnchorDirection(direction);
        return ponyDefinition.actions[index].getAnchorY(direction);
    }
    
    /**
     * Sets the feet hotspot Y for one facing. Pass {@link Float#NaN} or a
     * negative value to clear (use bottom-center). Non-negative values are
     * unscaled pixels from the top of that direction's frame.
     */
    public void setActionAnchorY(int index, String direction, float anchorY) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        checkAnchorDirection(direction);
        ponyDefinition.actions[index].setAnchorY(direction, anchorY);
    }
    
    /**
     * Sets the feet hotspot Y for both facings (legacy / bulk clear or assign).
     */
    public void setActionAnchorY(int index, float anchorY) {
        setActionAnchorY(index, "left", anchorY);
        setActionAnchorY(index, "right", anchorY);
    }
    
    private static void checkAnchorDirection(String direction) {
        if (!"left".equals(direction) && !"right".equals(direction)) {
            throw new IllegalArgumentException("direction must be left or right");
        }
    }
    
    public float getActionSpeed(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        return ponyDefinition.actions[index].speed;
    }
    
    public void setActionSpeed(int index, float speed) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (Float.isNaN(speed) || speed <= 0f) {
            throw new IllegalArgumentException("speed must be positive");
        }
        ponyDefinition.actions[index].speed = speed;
    }
    
    /**
     * @return whether this action's animation loops (default true)
     */
    public boolean getActionLoops(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        return ponyDefinition.actions[index].loops;
    }
    
    /**
     * Sets whether the animation loops while this action is active. When false,
     * one full play advances via the next-action list for the current motion.
     */
    public void setActionLoops(int index, boolean loops) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        ponyDefinition.actions[index].loops = loops;
    }

    /**
     * Destination movement for this action while traveling.
     *
     * @return {@code inherit}, {@code soft_vertical}, {@code horizontal},
     *         {@code vertical}, or {@code any}
     */
    public String getActionMovement(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        return WanderTarget.normalizeMovement(ponyDefinition.actions[index].movement);
    }

    /**
     * Sets destination movement. {@code inherit} (default) is soft horizontal;
     * {@code soft_vertical} is soft vertical with back/front facing;
     * {@code horizontal}/{@code vertical} hard-lock the other axis;
     * {@code any} is free 2D.
     */
    public void setActionMovement(int index, String movement) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        ponyDefinition.actions[index].movement = WanderTarget.normalizeMovement(movement);
    }
    
    /**
     * @return the action whose sprites this action reuses, or empty if this
     *         action owns its own images
     */
    public String getActionSpritesFrom(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        String s = ponyDefinition.actions[index].spritesFrom;
        return s != null ? s : "";
    }
    
    /**
     * Makes this action an alias of {@code ownerName} (shared sprites). Clears
     * local images/timings. Pass empty to become a sprite owner again.
     *
     * @param index     action to modify
     * @param ownerName name of the action that owns the bitmaps, or empty
     */
    public void setActionSpritesFrom(int index, String ownerName) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (ownerName == null) {
            ownerName = "";
        }
        ownerName = ownerName.trim();
        PonyDefinition.Action action = ponyDefinition.actions[index];
        if (ownerName.isEmpty()) {
            action.spritesFrom = "";
            return;
        }
        if (ownerName.equals(action.name)) {
            throw new IllegalArgumentException("Action cannot use spritesfrom itself");
        }
        int ownerIndex = findAction(ownerName);
        if (ownerIndex < 0) {
            throw new IllegalArgumentException("Unknown action: " + ownerName);
        }
        if (ponyDefinition.actions[ownerIndex].isAlias()) {
            throw new IllegalArgumentException("Cannot alias an alias: " + ownerName);
        }
        action.spritesFrom = ownerName;
        // Aliases must not carry their own images/timings.
        action.images.put("left", "");
        action.images.put("right", "");
        action.timings.put("left", "");
        action.timings.put("right", "");
    }
    
    /**
     * @return the gaits specification (e.g. {@code 0.5:1,0.7:3,1:1}), or empty
     */
    public String getActionGaits(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        String g = ponyDefinition.actions[index].gaits;
        return g != null ? g : "";
    }
    
    /**
     * Sets the load-time gait bag for this action. Empty clears expansion.
     * Value must parse as {@code speed[:weight],...} with positive speeds.
     */
    public void setActionGaits(int index, String gaits) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (gaits == null) {
            gaits = "";
        }
        gaits = gaits.replaceAll("\\s+", "");
        if (gaits.isEmpty()) {
            ponyDefinition.actions[index].gaits = "";
            return;
        }
        java.util.List<String> errors = new java.util.ArrayList<String>();
        java.util.List<PonyDefinition.GaitEntry> entries = PonyDefinition.parseGaits(gaits, errors);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.get(0));
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("gaits must list at least one speed");
        }
        ponyDefinition.actions[index].gaits = gaits;
    }
    
    /**
     * Applies the built-in ground gait bag ({@link PonyDefinition#DEFAULT_GAITS})
     * to this action (typically a full-speed trot sheet).
     */
    public void applyDefaultGaits(int index) {
        setActionGaits(index, PonyDefinition.DEFAULT_GAITS);
    }
    
    /**
     * Applies the built-in idle gait bag ({@link PonyDefinition#DEFAULT_IDLE_GAITS})
     * to this action (typically a stand sheet).
     */
    public void applyDefaultIdleGaits(int index) {
        setActionGaits(index, PonyDefinition.DEFAULT_IDLE_GAITS);
    }
    
    /**
     * Creates a new named action that reuses {@code sourceIndex}'s sprites at
     * the given speed (or reuses the source's owner if the source is already
     * an alias). Copies next-action lists from the source. Does not rewrite
     * any lists to include the new name.
     *
     * @return index of the new alias action
     */
    public int cloneActionAsGait(int sourceIndex, String newName, float speed) {
        if (sourceIndex < 0 || sourceIndex >= ponyDefinition.actions.length) {
            throw new IndexOutOfBoundsException();
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Action name must not be empty");
        }
        newName = newName.trim();
        checkActionName(newName);
        if (findAction(newName) >= 0) {
            throw new IllegalArgumentException("Action name already in use: " + newName);
        }
        if (Float.isNaN(speed) || speed <= 0f) {
            throw new IllegalArgumentException("speed must be positive");
        }
        PonyDefinition.Action source = ponyDefinition.actions[sourceIndex];
        String ownerName = source.isAlias() ? source.spritesFrom : source.name;
        // Ensure owner is a real sprite owner.
        int ownerIndex = findAction(ownerName);
        if (ownerIndex < 0 || ponyDefinition.actions[ownerIndex].isAlias()) {
            throw new IllegalArgumentException("Cannot resolve sprite owner for clone");
        }
        int index = addAction(newName);
        setActionSpeed(index, speed);
        setActionSpritesFrom(index, ownerName);
        setActionSpecial(index, source.specialType);
        setActionLoops(index, source.loops);
        setActionMovement(index, source.movement);
        setActionAnchorX(index, "left", source.getAnchorX("left"));
        setActionAnchorX(index, "right", source.getAnchorX("right"));
        setActionAnchorY(index, "left", source.getAnchorY("left"));
        setActionAnchorY(index, "right", source.getAnchorY("right"));
        setActionNext(index, "waiting", getActionNext(sourceIndex, "waiting"));
        setActionNext(index, "moving", getActionNext(sourceIndex, "moving"));
        setActionNext(index, "drag", getActionNext(sourceIndex, "drag"));
        // Aliases typically do not expand their own gaits; clear if addAction defaulted.
        ponyDefinition.actions[index].gaits = "";
        return index;
    }
    
    public String getActionImage(int index, String direction) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].images.containsKey(direction)) throw new IndexOutOfBoundsException();
        // Aliases inherit images from the owner for preview.
        if (ponyDefinition.actions[index].isAlias()) {
            int owner = findAction(ponyDefinition.actions[index].spritesFrom);
            if (owner >= 0) {
                return ponyDefinition.actions[owner].images.get(direction);
            }
        }
        return ponyDefinition.actions[index].images.get(direction);
    }
    
    public String getActionTimings(int index, String direction) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].timings.containsKey(direction)) throw new IndexOutOfBoundsException();
        // Aliases inherit timings from the owner for preview / bulk edit display.
        if (ponyDefinition.actions[index].isAlias()) {
            int owner = findAction(ponyDefinition.actions[index].spritesFrom);
            if (owner >= 0) {
                return ponyDefinition.actions[owner].timings.get(direction);
            }
        }
        return ponyDefinition.actions[index].timings.get(direction);
    }
    
    public void setActionTimings(int index, String direction, String timings) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].timings.containsKey(direction)) throw new IndexOutOfBoundsException();
        // Editing timings on an alias converts it to an owner for that side only is messy;
        // clear spritesfrom so the action owns its sheets going forward.
        if (ponyDefinition.actions[index].isAlias()) {
            detachAliasCopyingSprites(index);
        }
        ponyDefinition.actions[index].timings.put(direction, timings);
    }
    
    /**
     * If the action is an alias, copy the owner's images/timings into it and
     * clear {@code spritesFrom} so it becomes a full owner. No-op if already owner.
     */
    private void detachAliasCopyingSprites(int index) {
        PonyDefinition.Action action = ponyDefinition.actions[index];
        if (!action.isAlias()) {
            return;
        }
        int owner = findAction(action.spritesFrom);
        if (owner >= 0) {
            PonyDefinition.Action o = ponyDefinition.actions[owner];
            action.images.put("left", o.images.get("left"));
            action.images.put("right", o.images.get("right"));
            action.timings.put("left", o.timings.get("left"));
            action.timings.put("right", o.timings.get("right"));
        }
        action.spritesFrom = "";
    }
    
    /**
     * Loads a new sprite for an action. If the loaded image is an animation,
     * it is converted into a spritesheet and both the image and timings are
     * set, otherwise, only the image is set.
     * 
     * @param index      the index of the action
     * @param direction  the direction of the sprite to set
     * @param spriteFile the file to load the sprite from
     * @throws IndexOutOfBoundsException if {@code index < 0 || index >=
     *                                   getActionCount()}
     * @throws IndexOutOfBoundsException if {@code direction} is not a valid
     *                                   direction ("left" or "right")
     * @throws GenericException if the file cannot be loaded as an image
     */
    public void loadActionSprite(int index, String direction, File spriteFile) throws GenericException {
        loadActionSprite(index, direction, spriteFile, null);
    }

    /**
     * Like {@link #loadActionSprite(int, String, File)} but GIFs honour
     * {@code options} (scale, lifts, timings). PNG stills stay pass-through.
     */
    public void loadActionSprite(int index, String direction, File spriteFile,
            ImageImport.PackOptions options) throws GenericException {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].images.containsKey(direction)) throw new IndexOutOfBoundsException();

        if (ponyDefinition.actions[index].isAlias()) {
            detachAliasCopyingSprites(index);
        }

        try {
            ImageImport imported = ImageImport.load(spriteFile, options);
            ponyDefinition.actions[index].images.put(direction, Base64.getEncoder().encodeToString(imported.loadedImage));
            if (imported.timings != null) {
                ponyDefinition.actions[index].timings.put(direction, imported.timings);
            }
        } catch (IOException e) {
            String detail = e.getMessage();
            if (detail == null || detail.isEmpty()) {
                detail = "Failed to read " + spriteFile + ".";
            } else {
                detail = "Failed to read " + spriteFile + ": " + detail;
            }
            throw new GenericException("", detail);
        }
    }

    /**
     * Packs PNG frames (a folder or loose files) into a left-to-right
     * spritesheet for {@code direction}. {@link ImageImport.PackOptions#lifts}
     * raises frames off the baseline (baked into the PNG). Existing timings
     * are kept when they already have one entry per frame; otherwise each
     * frame gets {@link ImageImport#DEFAULT_FRAME_TIMING_CS}.
     *
     * @return the packed import (cell size, timings, PNG bytes)
     */
    public ImageImport loadActionSpriteFrames(
            int index, String direction, java.util.List<File> selected, ImageImport.PackOptions options)
            throws GenericException {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].images.containsKey(direction)) throw new IndexOutOfBoundsException();

        if (ponyDefinition.actions[index].isAlias()) {
            detachAliasCopyingSprites(index);
        }

        try {
            java.util.List<File> files = ImageImport.collectFrameFiles(selected);
            return loadActionSpriteFromFrames(index, direction, ImageImport.loadFrameImages(files), options);
        } catch (IOException e) {
            throw new GenericException("Import Frames Failed", e.getMessage());
        }
    }

    /**
     * Packs already-decoded frames (PNG stills or coalesced GIF frames) with
     * {@code options} (scale, lifts, optional per-frame timings).
     */
    public ImageImport loadActionSpriteFromFrames(
            int index, String direction, java.util.List<java.awt.image.BufferedImage> frames,
            ImageImport.PackOptions options)
            throws GenericException {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].images.containsKey(direction)) throw new IndexOutOfBoundsException();

        if (ponyDefinition.actions[index].isAlias()) {
            detachAliasCopyingSprites(index);
        }

        try {
            ImageImport imported = ImageImport.fromFrames(frames, options);
            applyPackedSprite(index, direction, imported);
            return imported;
        } catch (IOException e) {
            throw new GenericException("Import Failed", e.getMessage());
        }
    }

    /**
     * Builds the opposite facing by flopping each cell of {@code fromDirection}'s
     * sheet (same frame order and timings). Explicit {@code anchorx} is mirrored
     * as {@code cellW − x}; unset X stays unset. {@code anchory} is copied.
     */
    public ImageImport mirrorActionSprite(int index, String fromDirection) throws GenericException {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].images.containsKey(fromDirection)) {
            throw new IndexOutOfBoundsException();
        }
        String toDirection = "left".equals(fromDirection) ? "right" : "right".equals(fromDirection) ? "left" : null;
        if (toDirection == null) {
            throw new IndexOutOfBoundsException();
        }

        if (ponyDefinition.actions[index].isAlias()) {
            detachAliasCopyingSprites(index);
        }

        String b64 = ponyDefinition.actions[index].images.get(fromDirection);
        if (b64 == null || b64.isEmpty()) {
            throw new GenericException("Mirror Failed", "No " + fromDirection + " spritesheet to mirror.");
        }
        String timings = ponyDefinition.actions[index].timings.get(fromDirection);
        int frameCount = ImageImport.countTimings(timings);
        if (frameCount < 1) {
            throw new GenericException(
                    "Mirror Failed",
                    "Set " + fromDirection + " timings first so the sheet can be split into frames.");
        }

        try {
            byte[] png = Base64.getDecoder().decode(b64);
            ImageImport mirrored = ImageImport.mirrorSheet(png, frameCount, timings);
            applyPackedSprite(index, toDirection, mirrored);

            float ax = getActionAnchorX(index, fromDirection);
            float ay = getActionAnchorY(index, fromDirection);
            if (!Float.isNaN(ax) && ax >= 0f && mirrored.cellWidth > 0) {
                setActionAnchorX(index, toDirection, mirrored.cellWidth - ax);
            } else {
                setActionAnchorX(index, toDirection, Float.NaN);
            }
            setActionAnchorY(index, toDirection, ay);
            return mirrored;
        } catch (IllegalArgumentException e) {
            throw new GenericException("Mirror Failed", "The " + fromDirection + " image could not be decoded.");
        } catch (IOException e) {
            throw new GenericException("Mirror Failed", e.getMessage());
        }
    }

    private void applyPackedSprite(int index, String direction, ImageImport imported) {
        ponyDefinition.actions[index].images.put(
                direction, Base64.getEncoder().encodeToString(imported.loadedImage));
        String existing = ponyDefinition.actions[index].timings.get(direction);
        int packedCount = ImageImport.countTimings(imported.timings);
        if (ImageImport.countTimings(existing) == packedCount && packedCount > 0) {
            return;
        }
        ponyDefinition.actions[index].timings.put(direction, imported.timings);
    }
    
    public String getActionNext(int index, String type) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].nextActions.containsKey(type)) throw new IndexOutOfBoundsException();
        return ponyDefinition.actions[index].nextActions.get(type);
    }
    
    public void setActionNext(int index, String type, String actionNames) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].nextActions.containsKey(type)) throw new IndexOutOfBoundsException();
        ponyDefinition.actions[index].nextActions.put(type, actionNames);
    }
    
    /**
     * Next-action list used at runtime for {@code type}. For {@code drag}, an
     * empty override falls back to {@link #getDefaultDrag()}.
     */
    public String getEffectiveActionNext(int index, String type) {
        String listed = getActionNext(index, type);
        if ("drag".equals(type) && !PonyDefinition.actionListHasTokens(listed)) {
            return getDefaultDrag();
        }
        return listed;
    }

    /**
     * Replaces the current pony by importing a Desktop Ponies character folder
     * ({@code pony.ini} plus GIF sprites). On failure the current pony is left
     * unchanged.
     *
     * @param ponyDir directory containing {@code pony.ini}
     * @return human-readable import notes (counts, skipped features, etc.)
     * @throws GenericException if the folder cannot be imported
     */
    public String[] importDesktopPonies(File ponyDir) throws GenericException {
        DesktopPoniesImport.Result result;
        try {
            result = DesktopPoniesImport.importPony(ponyDir);
        } catch (IllegalArgumentException e) {
            throw new GenericException("Import Failed", e.getMessage());
        } catch (IOException e) {
            throw new GenericException("Import Failed", "Failed to read " + ponyDir + ": " + e.getMessage());
        }

        // Build into a temporary definition so a mid-import sprite failure does not wipe work
        PonyDefinition previous = ponyDefinition;
        java.util.List<String> notes = new java.util.ArrayList<String>();
        for (String w : result.warnings) {
            notes.add(w);
        }
        try {
            ponyDefinition = new PonyDefinition();
            int loaded = 0;
            for (DesktopPoniesImport.ImportedAction action : result.actions) {
                try {
                    int index = addAction(action.name);
                    setActionSpecial(index, action.specialType);
                    setActionSpeed(index, action.speed);
                    setActionMovement(index, action.movement);
                    ImageImport.PackOptions dpOpts = new ImageImport.PackOptions();
                    dpOpts.scaleDivisor = ImageImport.SCALE_DIVISOR_HALF;
                    loadActionSprite(index, "left", action.leftImage, dpOpts);
                    loadActionSprite(index, "right", action.rightImage, dpOpts);
                    setActionNext(index, "waiting", action.nextWaiting);
                    setActionNext(index, "moving", action.nextMoving);
                    setActionNext(index, "drag", action.nextDrag);
                    loaded++;
                } catch (GenericException e) {
                    int idx = findActionByName(action.name);
                    if (idx >= 0) {
                        removeAction(idx);
                    }
                    notes.add("Skipped action \"" + action.name + "\": could not load sprites ("
                            + (e.detail != null && e.detail.length > 0 ? e.detail[0] : e.getMessage()) + ").");
                } catch (RuntimeException e) {
                    int idx = findActionByName(action.name);
                    if (idx >= 0) {
                        removeAction(idx);
                    }
                    notes.add("Skipped action \"" + action.name + "\": " + e.getMessage());
                }
            }
            if (loaded == 0) {
                ponyDefinition = previous;
                throw new GenericException("Import Failed", "No actions could be loaded from " + ponyDir);
            }
            // Rebuild next/start lists if some actions were dropped mid-import.
            setStartActions(result.startActions);
            setDefaultDrag(result.defaultDrag);
            scrubMissingActionReferences();

            int effectsLoaded = 0;
            ImageImport.PackOptions effectOpts = new ImageImport.PackOptions();
            effectOpts.scaleDivisor = ImageImport.SCALE_DIVISOR_HALF;
            for (DesktopPoniesImport.ImportedEffect effect : result.effects) {
                if (findAction(effect.actionName) < 0) {
                    notes.add("Skipped effect \"" + effect.name
                            + "\": trigger action \"" + effect.actionName + "\" was not loaded.");
                    continue;
                }
                try {
                    int index = addEffect(effect.name);
                    setEffectAction(index, effect.actionName);
                    setEffectDuration(index, effect.duration);
                    setEffectRepeatDelay(index, effect.repeatDelay);
                    setEffectFollow(index, effect.follow);
                    setEffectNoLoop(index, effect.noLoop);
                    setEffectPlacement(index, "right", effect.placementRight);
                    setEffectCentering(index, "right", effect.centeringRight);
                    setEffectPlacement(index, "left", effect.placementLeft);
                    setEffectCentering(index, "left", effect.centeringLeft);
                    loadEffectSprite(index, "left", effect.leftImage, effectOpts);
                    loadEffectSprite(index, "right", effect.rightImage, effectOpts);
                    effectsLoaded++;
                } catch (GenericException e) {
                    int idx = findEffect(effect.name);
                    if (idx >= 0) {
                        removeEffect(idx);
                    }
                    notes.add("Skipped effect \"" + effect.name + "\": could not load sprites ("
                            + (e.detail != null && e.detail.length > 0 ? e.detail[0] : e.getMessage())
                            + ").");
                } catch (RuntimeException e) {
                    int idx = findEffect(effect.name);
                    if (idx >= 0) {
                        removeEffect(idx);
                    }
                    notes.add("Skipped effect \"" + effect.name + "\": " + e.getMessage());
                }
            }

            notes.add(0, "Loaded " + loaded + " action(s) and " + effectsLoaded
                    + " effect(s) into the editor.");
            notes.add(1, "GIF sprites were scaled to 50% so they match built-in PonyPaper size.");
        } catch (GenericException e) {
            ponyDefinition = previous;
            throw e;
        } catch (RuntimeException e) {
            ponyDefinition = previous;
            throw new GenericException("Import Failed", "Unexpected error: " + e.getMessage());
        }

        return notes.toArray(new String[0]);
    }

    private int findActionByName(String name) {
        for (int i = 0; i < ponyDefinition.actions.length; i++) {
            if (ponyDefinition.actions[i].name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String filterActionList(String list, java.util.Set<String> present) {
        return PonyDefinition.filterActionList(list, present);
    }

    private void ensureEffectsArray() {
        if (ponyDefinition.effects == null) {
            ponyDefinition.effects = new PonyDefinition.Effect[0];
        }
    }

    private void checkEffectIndex(int index) {
        ensureEffectsArray();
        if (index < 0 || index >= ponyDefinition.effects.length) {
            throw new IndexOutOfBoundsException();
        }
    }

    private static void checkEffectDirection(String direction) {
        if (!"left".equals(direction) && !"right".equals(direction)) {
            throw new IndexOutOfBoundsException();
        }
    }

    /**
     * @return number of effects; indices for effect methods are in
     *         {@code [0, getEffectCount())}
     */
    public int getEffectCount() {
        ensureEffectsArray();
        return ponyDefinition.effects.length;
    }

    /**
     * @return index of the effect with {@code name}, or {@code -1}
     */
    public int findEffect(String name) {
        ensureEffectsArray();
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < ponyDefinition.effects.length; i++) {
            if (name.equals(ponyDefinition.effects[i].name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Creates a new effect with the given name. Defaults: duration 0, no repeat,
     * follow false, placement/centering Center, empty images. Trigger action is
     * left empty until {@link #setEffectAction} is called.
     *
     * @return index of the new effect
     */
    public int addEffect(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Effect name must not be empty");
        }
        name = name.trim();
        checkEffectName(name);
        ensureEffectsArray();
        if (findEffect(name) >= 0) {
            throw new IllegalArgumentException("Effect name already in use: " + name);
        }
        PonyDefinition.Effect[] old = ponyDefinition.effects;
        PonyDefinition.Effect[] next = new PonyDefinition.Effect[old.length + 1];
        System.arraycopy(old, 0, next, 0, old.length);
        PonyDefinition.Effect effect = new PonyDefinition.Effect();
        effect.name = name;
        next[old.length] = effect;
        ponyDefinition.effects = next;
        return old.length;
    }

    /**
     * Removes the effect at {@code index}.
     */
    public void removeEffect(int index) {
        checkEffectIndex(index);
        PonyDefinition.Effect[] old = ponyDefinition.effects;
        PonyDefinition.Effect[] next = new PonyDefinition.Effect[old.length - 1];
        System.arraycopy(old, 0, next, 0, index);
        System.arraycopy(old, index + 1, next, index, old.length - index - 1);
        ponyDefinition.effects = next;
    }

    public String getEffectName(int index) {
        checkEffectIndex(index);
        return ponyDefinition.effects[index].name;
    }

    /**
     * Renames an effect. Does not need to rewrite cross-references (effects are
     * not referenced by name elsewhere).
     */
    public void setEffectName(int index, String name) {
        checkEffectIndex(index);
        if (name == null) {
            throw new IllegalArgumentException("Effect name must not be empty");
        }
        name = name.trim();
        checkEffectName(name);
        if (name.equals(ponyDefinition.effects[index].name)) {
            return;
        }
        if (findEffect(name) >= 0) {
            throw new IllegalArgumentException("Effect name already in use: " + name);
        }
        ponyDefinition.effects[index].name = name;
    }

    private static void checkEffectName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Effect name must not be empty");
        }
        if (name.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Effect name must not contain ':'");
        }
    }

    /** Trigger action name for this effect (may be empty until set). */
    public String getEffectAction(int index) {
        checkEffectIndex(index);
        String a = ponyDefinition.effects[index].action;
        return a != null ? a : "";
    }

    /**
     * Sets the trigger action name. Empty is allowed while editing; {@link #validate()}
     * requires a defined action before the pony is usable.
     */
    public void setEffectAction(int index, String actionName) {
        checkEffectIndex(index);
        if (actionName == null) {
            actionName = "";
        }
        ponyDefinition.effects[index].action = actionName.trim();
    }

    public float getEffectDuration(int index) {
        checkEffectIndex(index);
        return ponyDefinition.effects[index].duration;
    }

    public void setEffectDuration(int index, float duration) {
        checkEffectIndex(index);
        if (Float.isNaN(duration) || duration < 0f || duration > PonyDefinition.MAX_EFFECT_SECONDS) {
            throw new IllegalArgumentException(
                    "duration must be between 0 and " + (int)PonyDefinition.MAX_EFFECT_SECONDS);
        }
        ponyDefinition.effects[index].duration = duration;
    }

    public float getEffectRepeatDelay(int index) {
        checkEffectIndex(index);
        return ponyDefinition.effects[index].repeatDelay;
    }

    public void setEffectRepeatDelay(int index, float repeatDelay) {
        checkEffectIndex(index);
        if (Float.isNaN(repeatDelay) || repeatDelay < 0f
                || repeatDelay > PonyDefinition.MAX_EFFECT_SECONDS) {
            throw new IllegalArgumentException(
                    "repeatdelay must be between 0 and " + (int)PonyDefinition.MAX_EFFECT_SECONDS);
        }
        ponyDefinition.effects[index].repeatDelay = repeatDelay;
    }

    public boolean getEffectFollow(int index) {
        checkEffectIndex(index);
        return ponyDefinition.effects[index].follow;
    }

    public void setEffectFollow(int index, boolean follow) {
        checkEffectIndex(index);
        ponyDefinition.effects[index].follow = follow;
    }

    public boolean getEffectNoLoop(int index) {
        checkEffectIndex(index);
        return ponyDefinition.effects[index].noLoop;
    }

    public void setEffectNoLoop(int index, boolean noLoop) {
        checkEffectIndex(index);
        ponyDefinition.effects[index].noLoop = noLoop;
    }

    public String getEffectPlacementMode(int index) {
        checkEffectIndex(index);
        return EffectPlacement.normalizeMode(ponyDefinition.effects[index].placementMode);
    }

    public void setEffectPlacementMode(int index, String mode) {
        checkEffectIndex(index);
        ponyDefinition.effects[index].placementMode = EffectPlacement.normalizeMode(mode);
    }

    public String getEffectPlacement(int index, String direction) {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        String value = ponyDefinition.effects[index].placement.get(direction);
        return value != null ? value : "Center";
    }

    public void setEffectPlacement(int index, String direction, String token) {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        String canon = PonyDefinition.normalizePlacementToken(token);
        if (canon == null) {
            throw new IllegalArgumentException("Unknown placement: " + token);
        }
        ponyDefinition.effects[index].placement.put(direction, canon);
    }

    public String getEffectCentering(int index, String direction) {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        String value = ponyDefinition.effects[index].centering.get(direction);
        return value != null ? value : "Center";
    }

    public void setEffectCentering(int index, String direction, String token) {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        String canon = PonyDefinition.normalizePlacementToken(token);
        if (canon == null) {
            throw new IllegalArgumentException("Unknown centering: " + token);
        }
        if ("Any".equals(canon) || "Any-Not_Center".equals(canon)) {
            throw new IllegalArgumentException("centering cannot be Any or Any-Not_Center");
        }
        ponyDefinition.effects[index].centering.put(direction, canon);
    }

    public String getEffectImage(int index, String direction) {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        String value = ponyDefinition.effects[index].images.get(direction);
        return value != null ? value : "";
    }

    public String getEffectTimings(int index, String direction) {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        String value = ponyDefinition.effects[index].timings.get(direction);
        return value != null ? value : "";
    }

    public void setEffectTimings(int index, String direction, String timings) {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        ponyDefinition.effects[index].timings.put(direction, timings != null ? timings : "");
    }

    public void loadEffectSprite(int index, String direction, File spriteFile) throws GenericException {
        loadEffectSprite(index, direction, spriteFile, null);
    }

    public void loadEffectSprite(int index, String direction, File spriteFile,
            ImageImport.PackOptions options) throws GenericException {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        try {
            ImageImport imported = ImageImport.load(spriteFile, options);
            ponyDefinition.effects[index].images.put(
                    direction, Base64.getEncoder().encodeToString(imported.loadedImage));
            if (imported.timings != null) {
                ponyDefinition.effects[index].timings.put(direction, imported.timings);
            }
        } catch (IOException e) {
            String detail = e.getMessage();
            if (detail == null || detail.isEmpty()) {
                detail = "Failed to read " + spriteFile + ".";
            } else {
                detail = "Failed to read " + spriteFile + ": " + detail;
            }
            throw new GenericException("", detail);
        }
    }

    public ImageImport loadEffectSpriteFrames(
            int index, String direction, java.util.List<File> selected, ImageImport.PackOptions options)
            throws GenericException {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        try {
            java.util.List<File> files = ImageImport.collectFrameFiles(selected);
            return loadEffectSpriteFromFrames(index, direction, ImageImport.loadFrameImages(files), options);
        } catch (IOException e) {
            throw new GenericException("Import Frames Failed", e.getMessage());
        }
    }

    public ImageImport loadEffectSpriteFromFrames(
            int index, String direction, java.util.List<java.awt.image.BufferedImage> frames,
            ImageImport.PackOptions options) throws GenericException {
        checkEffectIndex(index);
        checkEffectDirection(direction);
        try {
            ImageImport imported = ImageImport.fromFrames(frames, options);
            applyPackedEffectSprite(index, direction, imported);
            return imported;
        } catch (IOException e) {
            throw new GenericException("Import Failed", e.getMessage());
        }
    }

    public ImageImport mirrorEffectSprite(int index, String fromDirection) throws GenericException {
        checkEffectIndex(index);
        checkEffectDirection(fromDirection);
        String toDirection = "left".equals(fromDirection) ? "right" : "left";
        String b64 = ponyDefinition.effects[index].images.get(fromDirection);
        if (b64 == null || b64.isEmpty()) {
            throw new GenericException("Mirror Failed", "No " + fromDirection + " spritesheet to mirror.");
        }
        String timings = ponyDefinition.effects[index].timings.get(fromDirection);
        int frameCount = ImageImport.countTimings(timings);
        if (frameCount < 1) {
            throw new GenericException(
                    "Mirror Failed",
                    "Set " + fromDirection + " timings first so the sheet can be split into frames.");
        }
        try {
            byte[] png = Base64.getDecoder().decode(b64);
            ImageImport mirrored = ImageImport.mirrorSheet(png, frameCount, timings);
            applyPackedEffectSprite(index, toDirection, mirrored);
            return mirrored;
        } catch (IllegalArgumentException e) {
            throw new GenericException("Mirror Failed", "The " + fromDirection + " image could not be decoded.");
        } catch (IOException e) {
            throw new GenericException("Mirror Failed", e.getMessage());
        }
    }

    private void applyPackedEffectSprite(int index, String direction, ImageImport imported) {
        ponyDefinition.effects[index].images.put(
                direction, Base64.getEncoder().encodeToString(imported.loadedImage));
        String existing = ponyDefinition.effects[index].timings.get(direction);
        int packedCount = ImageImport.countTimings(imported.timings);
        if (ImageImport.countTimings(existing) == packedCount && packedCount > 0) {
            return;
        }
        ponyDefinition.effects[index].timings.put(direction, imported.timings);
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            PonyEditorGUI.start();
        } else if (args.length == 1 && "-help".equals(args[0])) {
            System.out.println("PonyPaper custom pony editor");
            System.out.println("With no arguments, run a graphical user interface.");
            System.out.println("With -help, print this help.");
            System.out.println("With other arguments, process them in turn as follows:");
            System.out.println("");
            PonyEditorCLI.showArguments();
            System.out.println("-gif-to-sheet [options] INPUT.gif [OUTPUT.png]");
            System.out.println("    Convert a GIF to a PonyPaper spritesheet (standalone; ignores other options).");
            System.out.println("    Same ImageImport path as Import image. Default is native size;");
            System.out.println("    use --half for 50%, or --scale 25|12.5|6.25|fit for other dyadic shrinks.");
            System.out.println("    Run with -gif-to-sheet -help for converter options.");
            System.out.println("-pack-sheet [options] OUTPUT.png FRAME.png...|DIR");
            System.out.println("    Pack PNG frames (or a folder of them) into a left-to-right spritesheet.");
            System.out.println("    Same ImageImport packer as Import frames (including --lifts / --scale).");
            System.out.println("    Run with -pack-sheet -help.");
        } else if ("-gif-to-sheet".equals(args[0])) {
            String[] converterArgs = new String[args.length - 1];
            System.arraycopy(args, 1, converterArgs, 0, converterArgs.length);
            int status = GifToSpritesheet.run(converterArgs);
            if (status != 0) {
                System.exit(status);
            }
        } else if ("-pack-sheet".equals(args[0])) {
            String[] packerArgs = new String[args.length - 1];
            System.arraycopy(args, 1, packerArgs, 0, packerArgs.length);
            int status = FramesToSpritesheet.run(packerArgs);
            if (status != 0) {
                System.exit(status);
            }
        } else {
            PonyEditorCLI cli = new PonyEditorCLI();
            cli.processArguments(args);
            if (cli.shouldOpenGui()) {
                // -load or -import-dp filled the model; open the editor so the user can review/save.
                PonyEditorGUI.start(cli.getEditor(), cli.getLoadedFile(), cli.isGuiDirty());
            }
        }
    }
    
}
