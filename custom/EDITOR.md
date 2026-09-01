# PonyPaper Custom Pony Editor

This tool is capable of creating and editing the XML files that represent custom ponies, selecting the desired sprites and behaviours of the pony.

![Editor screenshot](/screenshots/custom-editor.png)

## Installation

### Download (recommended)

Each project [Release](https://github.com/derram/PonyPaper/releases) includes a prebuilt editor JAR:

- Asset name: `PonyPaper-CustomEditor-<version>.jar`
- Requires a desktop **Java 17+** runtime

```bash
java -jar PonyPaper-CustomEditor-<version>.jar
```

With no arguments the GUI starts. Use `-help` for the command-line interface.

### Build from source

From the repository root (full JDK 17+; Android SDK not required for the editor):

```bash
./gradlew :custom:jar
java -jar custom/build/libs/customponies.jar
```

The Ant `build.xml` in this directory is deprecated and will refuse to run; use Gradle as above.

## Using the GUI

It can be started by launching the JAR from the file manager (if `.jar` is associated with Java) or from the command line with `java -jar …`.

### Import from Desktop-Ponies

**File → Import from Desktop-Ponies…** (Ctrl+I) opens a folder chooser. Select a character directory that contains a `pony.ini` and GIF sprites (as shipped in [Desktop Ponies](https://github.com/RoosterDragon/Desktop-Ponies) under `Content/Ponies/<Name>/`). The editor will:

* create actions from importable behaviors (group 0, no multi-pony follow targets)
* convert left/right GIFs into PonyPaper spritesheets at **50%** scale (so they match built-in ponies) and fill frame timings
* build **start** / **next waiting** / **next moving** / **next drag** lists from Chance, Speed, and Movement (Chance becomes `name:N`)
* map Allowed Moves onto per-action **Movement** (`Horizontal_Only` → horizontal, `Vertical_Only` → vertical, diagonals/`All` → any; stationary/drag stay inherit)
* map simple teleport chains (e.g. Twilight’s warp) to `teleport-out` / `teleport-in`
* use a `Dragged` behavior for drag when present

Speech, interactions, non-zero behavior groups, and most `Skip=True` story sequences are skipped. **Effect lines are imported** when their trigger behavior is available (GIFs packed at 50% like actions). If an Effect references a `Skip=True` behavior (e.g. Applejack’s gallop apple drops), that behavior is kept so the effect can load. Effects whose behavior was omitted for other reasons (follow targets, non-zero groups, or the 30-action cap) are listed in the import summary. Always review the action graph, **Wander** preference, and Effects tab before saving. Vertical-preferring characters usually need **Wander → Vertical** set by hand after import.

### Effects (spawned sprites)

The center of the editor has **Actions** and **Effects** tabs. Effects are Desktop Ponies–style prop/VFX sprites that appear when a named action starts. See the [Technical Spec](TECHNICAL_SPEC.md#effects-spawned-sprites).

On the **Effects** tab:

* **New / Rename / Delete** manage the effect list (double-click renames).
* **Trigger action**: which action starts the effect (Tab completes action names).
* **Duration** (seconds): `0` = until that action ends; timed effects may outlive the action (e.g. a tree after a short buck).
* **Repeat delay**: `0` = spawn once; otherwise re-spawn while the trigger action is still current.
* **Follow pony**: glue to the character each frame; unchecked plants the sprite in the world.
* **Prevent animation loop**: play the sheet once even if it would loop.
* **Motion-relative placement**: when checked, Left/Right/Top/Bottom attach points rotate with travel so diagonal movers keep side trails in their wake. Off (default) matches Desktop Ponies axis-aligned bounds attach; idle and pure-horizontal travel look the same either way. Use this for wake/trail effects on flyers; leave off for props that must stay on a fixed side of the sprite (saddlebags, held objects).
* **Placement / centering** (per facing): 9-point attach on the pony vs the effect image (`Any` / `Any-Not_Center` allowed for placement only).
* **Check placement…**: Opens a composite preview of the effect on its trigger action (feet-locked stage, like **Check…** for anchors). Change facing, **Travel** (Idle / compass directions including diagonals), play/step both sheets, and edit placement/centering via combos or the 3×3 grids. Travel only remaps cells when **Motion-relative placement** is on. **Apply** writes both facings back to the form; Cancel discards. For `Any` / `Any-Not_Center`, **Re-roll Any** picks a preview cell without changing the token — click a fixed cell to replace Any.
* **Left/right sprite**: same Import image / Import frames / Mirror / Preview / Export tools as actions. Apparent motion (falling apples, shaking trees) belongs in the spritesheet — there is no velocity/physics.

Renaming an action rewrites matching effect triggers. Deleting an action removes effects that pointed only at that action. The wallpaper loads and draws effects for custom characters automatically.

### Pony-level wander

Above the action list, the **Pony** strip has **Start actions**, **Default drag**, and **Wander**:

* **Wander**: Soft destination preference for actions whose **Movement** is Inherit. **Horizontal** (default) matches historical mostly-sideways travel with slight vertical drift; **Vertical** is the opposite; **Both (H or V)** picks a soft horizontal or soft vertical band each time. Individual actions can hard-lock an axis or opt into free 2D.

### Action Properties

On the left side of the editor is the list of actions. Selecting an action in this list allows its properties to be edited on the right:

* **Special type**: Leave blank for normal walk/idle clips. Known values: `teleport-out` / `teleport-in` and `screen-in` / `screen-out`. See the [Technical Spec](TECHNICAL_SPEC.md) for details.
* **Anchors left/right** (`<anchorx>` / `<anchory>`): Optional pixel coordinates of the pony’s feet. Leave empty for normal sheets that are already centre-bottom aligned.
* **Speed**: Travel and animation rate for this action (positive float; default `1`).
* **Movement**: Destination axis while traveling. **Inherit** (default) uses the pony Wander preference with soft drift. **Horizontal only** / **Vertical only** hard-lock the other axis (Desktop Ponies–style; use for rainboom / trail clips). **Any direction** is free 2D and ignores Wander. Teleport and screen-in/out specials ignore this field.
* **Loop animation**: Checked by default. Uncheck for **one-shot transition** clips (intros, outros, reactions).
* **Sprites from**: When set to another action’s name, this action is an **alias**: it reuses that action’s bitmaps and timings.
* **Gaits**: Optional load-time bag of `speed:weight` entries (e.g. `0.5:1,0.7:3,1:1`). Use the **Ground** button for the built-in ground bag.
* **Left/right sprite**:
    * **Import image**: Loads one already-packed PNG strip or one GIF.
    * **Import frames**: Opens a folder of PNGs and handles packing, scaling, and per-frame **lift**. In the packer, **Apply to all** sets every frame to the current Lift value; **Reset lifts** clears to 0; **Apply hop** builds a parabola.
    * **Mirror to right/left**: Builds the opposite facing by flopping cells.
    * **Preview**: Displays the strip and highlights frames.
* **Left/right timings**: Comma-separated durations for each frame (hundredths of a second).
* **Next moving/waiting actions**: Lists of possible transitions. Use `name:N` for weighting. Use `none` or `-` to signify no successor.
* **Drag override**: Optional per-action replacement for **Default drag**.

At the bottom is the list of **Start actions** (how the pony can enter the scene) and **Default drag** (fallback for actions with no override).
