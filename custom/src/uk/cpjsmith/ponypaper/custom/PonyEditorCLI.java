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
            int[] packLifts = null;
            
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

                    case "-anchorx":
                    {
                        checkArgument(args, i);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String[] parsed = parseAnchorCliArg(args, i);
                        i = Integer.parseInt(parsed[0]);
                        String direction = parsed[1];
                        String anchorText = parsed[2];
                        try {
                            float value = parseAnchorValueOrClear(anchorText, "anchorx");
                            if ("both".equals(direction)) {
                                editor.setActionAnchorX(currentAction, value);
                            } else {
                                editor.setActionAnchorX(currentAction, direction, value);
                            }
                            guiDirty = true;
                        } catch (NumberFormatException e) {
                            throw new PonyEditor.GenericException("", "Invalid anchorx: " + anchorText);
                        }
                        break;
                    }

                    case "-anchory":
                    {
                        checkArgument(args, i);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String[] parsed = parseAnchorCliArg(args, i);
                        i = Integer.parseInt(parsed[0]);
                        String direction = parsed[1];
                        String anchorText = parsed[2];
                        try {
                            float value = parseAnchorValueOrClear(anchorText, "anchory");
                            if ("both".equals(direction)) {
                                editor.setActionAnchorY(currentAction, value);
                            } else {
                                editor.setActionAnchorY(currentAction, direction, value);
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

                    case "-lifts":
                    {
                        checkArgument(args, i);
                        String liftText = args[++i].trim();
                        if ("none".equalsIgnoreCase(liftText)
                                || "clear".equalsIgnoreCase(liftText)
                                || "-".equals(liftText)) {
                            packLifts = null;
                        } else {
                            try {
                                packLifts = ImageImport.parseLifts(liftText);
                            } catch (java.io.IOException e) {
                                throw new PonyEditor.GenericException("", "Invalid lifts: " + e.getMessage());
                            }
                        }
                        break;
                    }

                    case "-sprite-frames":
                    {
                        checkArgument(args, i, 2);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String spriteDir = args[++i];
                        java.util.List<File> frameFiles = new java.util.ArrayList<File>();
                        while (i + 1 < args.length && !isCliOption(args[i + 1])) {
                            frameFiles.add(new File(args[++i]));
                        }
                        if (frameFiles.isEmpty()) {
                            throw new PonyEditor.GenericException("", "Option -sprite-frames requires a folder or PNG files.");
                        }
                        try {
                            ImageImport.PackOptions packOpts = null;
                            if (packLifts != null) {
                                packOpts = new ImageImport.PackOptions();
                                packOpts.lifts = packLifts;
                            }
                            editor.loadActionSpriteFrames(currentAction, spriteDir, frameFiles, packOpts);
                            guiDirty = true;
                        } catch (IndexOutOfBoundsException e) {
                            throw new PonyEditor.GenericException("", "Can't set sprite for direction " + spriteDir);
                        }
                        break;
                    }

                    case "-mirror-facing":
                    {
                        checkArgument(args, i);
                        if (currentAction < 0) throw new PonyEditor.GenericException("", "No current action for " + args[i]);
                        String fromDir = args[++i];
                        try {
                            editor.mirrorActionSprite(currentAction, fromDir);
                            guiDirty = true;
                        } catch (IndexOutOfBoundsException e) {
                            throw new PonyEditor.GenericException("", "Can't mirror from direction " + fromDir);
                        }
                        break;
                    }
                    case "-start":
                        checkArgument(args, i);
                        editor.setStartActions(args[++i]);
                        guiDirty = true;
                        break;
                    case "-defaultdrag":
                        checkArgument(args, i);
                        editor.setDefaultDrag(args[++i]);
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
        System.out.println("-defaultdrag NAMES");
        System.out.println("    Set the pony-level default drag actions. Actions with an empty");
        System.out.println("    drag override inherit this list.");
        System.out.println("-action NAME");
        System.out.println("    Switch to editing the named action, creating it if it does not exist.");
        System.out.println("-next TYPE NAMES");
        System.out.println("    Set the current action's next actions of the given type.");
        System.out.println("    For type drag this is an override; leave empty to use -defaultdrag.");
        System.out.println("-special TYPE");
        System.out.println("    Set the current action's special type.");
        System.out.println("-anchorx [left|right|both] PIXELS|none");
        System.out.println("    Feet column in pixels from the left of the frame (optional).");
        System.out.println("    Direction defaults to both. Use none/clear/- to restore frame-centre.");
        System.out.println("    Left and right often differ when sheets are horizontal mirrors.");
        System.out.println("-anchory [left|right|both] PIXELS|none");
        System.out.println("    Feet row in pixels from the top of the frame (optional).");
        System.out.println("    Direction defaults to both. Use none/clear/- to restore frame bottom.");
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
        System.out.println("-lifts N,N,...|none");
        System.out.println("    Per-frame lift in pixels up from the baseline for the next");
        System.out.println("    -sprite-frames (hop / jump). Length must match the frame count.");
        System.out.println("    Use none/clear/- to restore all zeros. Persists until changed.");
        System.out.println("-sprite-frames DIRECTION DIR|FILE...");
        System.out.println("    Pack PNG frames (a folder or listed files) into a spritesheet for");
        System.out.println("    DIRECTION. Natural-sorted, bottom-centred cells, no gutters.");
        System.out.println("    Optional -lifts raises frames in a taller cell (baked into the PNG).");
        System.out.println("    Keeps existing timings when the frame count already matches.");
        System.out.println("-mirror-facing DIRECTION");
        System.out.println("    Build the opposite facing by flopping each cell of DIRECTION's sheet");
        System.out.println("    (same frame order). Copies timings and mirrors explicit anchorx.");
    }

    /** True when {@code arg} is a known CLI flag (stops variable-length file lists). */
    private static boolean isCliOption(String arg) {
        if (arg == null || !arg.startsWith("-") || arg.length() < 2) {
            return false;
        }
        switch (arg) {
            case "-action":
            case "-load":
            case "-import-dp":
            case "-next":
            case "-save":
            case "-special":
            case "-anchorx":
            case "-anchory":
            case "-speed":
            case "-loop":
            case "-spritesfrom":
            case "-gaits":
            case "-clone-gait":
            case "-sprite":
            case "-sprite-frames":
            case "-lifts":
            case "-mirror-facing":
            case "-start":
            case "-defaultdrag":
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Parses {@code -anchorx/-anchory} arguments after the option at {@code i}.
     * Accepts {@code VALUE} (both facings) or {@code left|right|both VALUE}.
     *
     * @return {@code {newIndex, direction, valueText}} where {@code newIndex} is the
     *         last consumed argument index (so the main loop can assign {@code i})
     */
    private static String[] parseAnchorCliArg(String[] args, int i)
            throws PonyEditor.GenericException {
        checkArgument(args, i);
        String first = args[i + 1].trim();
        if ("left".equalsIgnoreCase(first)
                || "right".equalsIgnoreCase(first)
                || "both".equalsIgnoreCase(first)) {
            checkArgument(args, i, 2);
            return new String[] {
                Integer.toString(i + 2),
                first.toLowerCase(),
                args[i + 2].trim()
            };
        }
        return new String[] {
            Integer.toString(i + 1),
            "both",
            first
        };
    }
    
    /**
     * Parses a pixel value, or {@link Float#NaN} for clear tokens
     * ({@code none}/{@code clear}/{@code -}/empty).
     */
    private static float parseAnchorValueOrClear(String anchorText, String tag)
            throws PonyEditor.GenericException, NumberFormatException {
        if (anchorText.isEmpty()
                || "none".equalsIgnoreCase(anchorText)
                || "clear".equalsIgnoreCase(anchorText)
                || "-".equals(anchorText)) {
            return Float.NaN;
        }
        float value = Float.parseFloat(anchorText);
        if (Float.isNaN(value) || value < 0f) {
            throw new PonyEditor.GenericException("",
                    "Invalid " + tag + " (use non-negative pixels, or none to clear): "
                            + anchorText);
        }
        return value;
    }
    
}
