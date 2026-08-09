package uk.cpjsmith.ponypaper.custom;

import java.io.File;

public class PonyEditorCLI {
    
    private final PonyEditor editor;
    private boolean usedLoad;
    private boolean usedImportDp;
    private boolean usedSave;
    private boolean hadError;
    /** Unsaved changes for the GUI: true after import or CLI mutations; false after a pure load. */
    private boolean guiDirty;
    /** Path from the most recent {@code -load}, or {@code null} after import. */
    private File loadedFile;
    
    public PonyEditorCLI() {
        editor = new PonyEditor();
    }

    public PonyEditor getEditor() {
        return editor;
    }

    /**
     * File associated with a successful {@code -load}, for the GUI title and Save target.
     * {@code null} after {@code -import-dp} (unsaved import) or when no load was used.
     */
    public File getLoadedFile() {
        return loadedFile;
    }

    /**
     * Whether the model should be treated as having unsaved changes when opening the GUI.
     * Pure {@code -load} is clean; imports and CLI mutations are dirty.
     */
    public boolean isGuiDirty() {
        return guiDirty;
    }

    /**
     * @return {@code true} if the GUI should open with the current editor state
     *         ({@code -load} or {@code -import-dp} succeeded and no {@code -save} was given)
     */
    public boolean shouldOpenGui() {
        return (usedLoad || usedImportDp) && !usedSave && !hadError;
    }
    
    private static void checkArgument(String[] args, int i) throws PonyEditor.GenericException {
        if (i + 1 >= args.length) throw new PonyEditor.GenericException("", "Option " + args[i] + " requires an argument.");
    }
    
    private static void checkArgument(String[] args, int i, int count) throws PonyEditor.GenericException {
        if (i + count >= args.length) throw new PonyEditor.GenericException("", "Option " + args[i] + " requires " + count + " arguments.");
    }
    
    public void processArguments(String[] args) {
        try {
            int currentAction = -1;
            
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-action":
                    {
                        checkArgument(args, i);
                        String actionName = args[++i];
                        currentAction = editor.findAction(actionName);
                        if (currentAction < 0) {
                            currentAction = editor.addAction(actionName);
                            guiDirty = true;
                        }
                        break;
                    }
                    case "-load":
                    {
                        checkArgument(args, i);
                        File file = new File(args[++i]);
                        editor.load(file);
                        usedLoad = true;
                        loadedFile = file;
                        guiDirty = false;
                        break;
                    }

                    case "-import-dp":
                    {
                        checkArgument(args, i);
                        File ponyDir = new File(args[++i]);
                        String[] notes = editor.importDesktopPonies(ponyDir);
                        usedImportDp = true;
                        loadedFile = null;
                        guiDirty = true;
                        for (String note : notes) {
                            System.err.println(note);
                        }
                        break;
                    }
                        
                    case "-next":
                    {
                        checkArgument(args, i, 2);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String actionType = args[++i];
                        String actionNames = args[++i];
                        try {
                            editor.setActionNext(currentAction, actionType, actionNames);
                            guiDirty = true;
                        } catch (IndexOutOfBoundsException e) {
                            throw new PonyEditor.GenericException("", "Can't set next actions for type " + actionType);
                        }
                        break;
                    }
                    case "-save":
                        checkArgument(args, i);
                        try {
                            editor.validate();
                        } catch (PonyEditor.GenericException e) {
                            for (String s : e.detail) System.err.println(s);
                        }
                        editor.save(new File(args[++i]));
                        usedSave = true;
                        break;
                        
                    case "-special":
                        checkArgument(args, i);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        editor.setActionSpecial(currentAction, args[++i]);
                        guiDirty = true;
                        break;

                    case "-anchory":
                    {
                        checkArgument(args, i);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String anchorText = args[++i].trim();
                        try {
                            if (anchorText.isEmpty()
                                    || "none".equalsIgnoreCase(anchorText)
                                    || "clear".equalsIgnoreCase(anchorText)
                                    || "-".equals(anchorText)) {
                                editor.setActionAnchorY(currentAction, Float.NaN);
                            } else {
                                float anchorY = Float.parseFloat(anchorText);
                                if (Float.isNaN(anchorY) || anchorY < 0f) {
                                    throw new PonyEditor.GenericException("",
                                            "Invalid anchory (use non-negative pixels, or none to clear): "
                                                    + anchorText);
                                }
                                editor.setActionAnchorY(currentAction, anchorY);
                            }
                            guiDirty = true;
                        } catch (NumberFormatException e) {
                            throw new PonyEditor.GenericException("", "Invalid anchory: " + anchorText);
                        }
                        break;
                    }

                    case "-speed":
                    {
                        checkArgument(args, i);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String speedText = args[++i];
                        try {
                            float speed = Float.parseFloat(speedText);
                            editor.setActionSpeed(currentAction, speed);
                            guiDirty = true;
                        } catch (NumberFormatException e) {
                            throw new PonyEditor.GenericException("", "Invalid speed: " + speedText);
                        } catch (IllegalArgumentException e) {
                            throw new PonyEditor.GenericException("", e.getMessage());
                        }
                        break;
                    }

                    case "-loop":
                    {
                        checkArgument(args, i);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String loopText = args[++i].trim().toLowerCase();
                        boolean loops;
                        if (loopText.equals("true") || loopText.equals("yes") || loopText.equals("1")) {
                            loops = true;
                        } else if (loopText.equals("false") || loopText.equals("no") || loopText.equals("0")) {
                            loops = false;
                        } else {
                            throw new PonyEditor.GenericException("", "Invalid -loop value (use true or false): " + loopText);
                        }
                        editor.setActionLoops(currentAction, loops);
                        guiDirty = true;
                        break;
                    }

                    case "-spritesfrom":
                    {
                        checkArgument(args, i);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        try {
                            editor.setActionSpritesFrom(currentAction, args[++i]);
                            guiDirty = true;
                        } catch (IllegalArgumentException e) {
                            throw new PonyEditor.GenericException("", e.getMessage());
                        }
                        break;
                    }

                    case "-gaits":
                    {
                        checkArgument(args, i);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        try {
                            String value = args[++i];
                            if ("default".equalsIgnoreCase(value) || "builtin".equalsIgnoreCase(value)) {
                                editor.applyDefaultGaits(currentAction);
                            } else if ("idle".equalsIgnoreCase(value)) {
                                editor.applyDefaultIdleGaits(currentAction);
                            } else if ("none".equalsIgnoreCase(value) || "clear".equalsIgnoreCase(value) || "-".equals(value)) {
                                editor.setActionGaits(currentAction, "");
                            } else {
                                editor.setActionGaits(currentAction, value);
                            }
                            guiDirty = true;
                        } catch (IllegalArgumentException e) {
                            throw new PonyEditor.GenericException("", e.getMessage());
                        }
                        break;
                    }

                    case "-clone-gait":
                    {
                        checkArgument(args, i, 2);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String newName = args[++i];
                        String speedText = args[++i];
                        try {
                            float speed = Float.parseFloat(speedText);
                            currentAction = editor.cloneActionAsGait(currentAction, newName, speed);
                            guiDirty = true;
                        } catch (NumberFormatException e) {
                            throw new PonyEditor.GenericException("", "Invalid speed: " + speedText);
                        } catch (IllegalArgumentException e) {
                            throw new PonyEditor.GenericException("", e.getMessage());
                        }
                        break;
                    }
                        
                    case "-sprite":
                    {
                        checkArgument(args, i, 2);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String spriteDir = args[++i];
                        String spritePath = args[++i];
                        try {
                            editor.loadActionSprite(currentAction, spriteDir, new File(spritePath));
                            guiDirty = true;
                        } catch (IndexOutOfBoundsException e) {
                            throw new PonyEditor.GenericException("", "Can't set sprite for direction " + spriteDir);
                        }
                        break;
                    }
                    case "-start":
                        checkArgument(args, i);
                        editor.setStartActions(args[++i]);
                        guiDirty = true;
                        break;
                        
                    default:
                        throw new PonyEditor.GenericException("", "Invalid option: " + args[i]);
                }
            }
        } catch (PonyEditor.GenericException e) {
            hadError = true;
            for (String s : e.detail) System.err.println(s);
        }
    }
    
    public static void showArguments() {
        System.out.println("-load FILE");
        System.out.println("    Load a pony definition from the given file path.");
        System.out.println("    Without -save, opens the GUI with that pony loaded.");
        System.out.println("-import-dp DIR");
        System.out.println("    Import a Desktop Ponies character folder (pony.ini + GIFs),");
        System.out.println("    replacing the current pony. Notes are printed to stderr.");
        System.out.println("    Without -save, opens the GUI with the imported pony loaded.");
        System.out.println("-save FILE");
        System.out.println("    Save the pony definition to the given file path.");
        System.out.println("-start NAMES");
        System.out.println("    Set the starting actions.");
        System.out.println("-action NAME");
        System.out.println("    Switch to editing the named action, creating it if it does not exist.");
        System.out.println("-next TYPE NAMES");
        System.out.println("    Set the current action's next actions of the given type.");
        System.out.println("-special TYPE");
        System.out.println("    Set the current action's special type.");
        System.out.println("-anchory PIXELS|none");
        System.out.println("    Feet row in pixels from the top of each frame (optional).");
        System.out.println("    Use none/clear/- or empty to restore bottom-center default.");
        System.out.println("-speed VALUE");
        System.out.println("    Set the current action's travel/animation speed factor (positive float).");
        System.out.println("    Typical gaits: 0.5 stroll, 0.7 walk, 1.0 trot.");
        System.out.println("-loop true|false");
        System.out.println("    Whether the animation loops (default true). Use false for one-shot");
        System.out.println("    transition clips that advance via next waiting/moving/drag lists.");
        System.out.println("-spritesfrom NAME");
        System.out.println("    Reuse another action's sprites (empty string clears). Alias actions");
        System.out.println("    only need speed + next lists; images come from NAME.");
        System.out.println("-gaits SPEC");
        System.out.println("    Set load-time gait bag as speed:weight list (e.g. 0.5:1,0.7:3,1:1).");
        System.out.println("    Use 'default' for built-in ground bag, 'idle' for stand bag,");
        System.out.println("    'none' to clear.");
        System.out.println("-clone-gait NAME SPEED");
        System.out.println("    Create NAME as a spritesfrom-alias of the current action at SPEED,");
        System.out.println("    then select the new action.");
        System.out.println("-sprite DIRECTION FILE");
        System.out.println("    Set the current action's sprite for the given direction.");
    }
    
}
