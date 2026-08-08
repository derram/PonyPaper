package uk.cpjsmith.ponypaper.custom;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import uk.cpjsmith.ponypaper.PonyDefinition;

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
     * {@code spritesfrom} when the owner was removed.
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
    }
    
    public String getActionName(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        return ponyDefinition.actions[index].name;
    }
    
    /**
     * Renames an action and rewrites every next-action list and the start
     * actions list so references to the old name point at the new one.
     *
     * @param index the index of the action to rename
     * @param name  the new name (must be non-empty and not used by another action)
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     * @throws IllegalArgumentException if {@code name} is empty or already used
     */
    public void setActionName(int index, String name) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Action name must not be empty");
        }
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
     * Replaces {@code oldName} with {@code newName} in every next/start list
     * and in {@code spritesfrom} references. Duplicate weighted entries are all rewritten.
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
    }

    private static String renameInActionList(String list, String oldName, String newName) {
        if (list == null || list.isEmpty()) {
            return list == null ? "" : list;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : list.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(token.equals(oldName) ? newName : token);
        }
        return sb.toString();
    }
    
    public String getActionSpecial(int index) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        return ponyDefinition.actions[index].specialType;
    }
    
    public void setActionSpecial(int index, String specialType) {
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        ponyDefinition.actions[index].specialType = specialType;
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
        if (index < 0 || index >= ponyDefinition.actions.length) throw new IndexOutOfBoundsException();
        if (!ponyDefinition.actions[index].images.containsKey(direction)) throw new IndexOutOfBoundsException();
        
        // Importing art on an alias detaches it so the new sprites are stored here.
        if (ponyDefinition.actions[index].isAlias()) {
            detachAliasCopyingSprites(index);
        }
        
        try {
            ImageImport imported = ImageImport.load(spriteFile);
            ponyDefinition.actions[index].images.put(direction, Base64.getEncoder().encodeToString(imported.loadedImage));
            if (imported.timings != null) {
                ponyDefinition.actions[index].timings.put(direction, imported.timings);
            }
        } catch (IOException e) {
            throw new GenericException("", "Failed to read " + spriteFile + ".");
        }
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
                    loadActionSprite(index, "left", action.leftImage);
                    loadActionSprite(index, "right", action.rightImage);
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
            scrubMissingActionReferences();
            notes.add(0, "Loaded " + loaded + " action(s) into the editor.");
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
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : list.split(",")) {
            String name = part.trim();
            if (name.isEmpty() || !present.contains(name)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(name);
        }
        return sb.toString();
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
            System.out.println("    Same ImageImport path as Import image / Desktop-Ponies import.");
            System.out.println("    Run with -gif-to-sheet -help for converter options.");
        } else if ("-gif-to-sheet".equals(args[0])) {
            String[] converterArgs = new String[args.length - 1];
            System.arraycopy(args, 1, converterArgs, 0, converterArgs.length);
            int status = GifToSpritesheet.run(converterArgs);
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
