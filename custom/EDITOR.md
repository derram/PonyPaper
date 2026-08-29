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
* map simple teleport chains (e.g. Twilight’s warp) to `teleport-out` / `teleport-in`
* use a `Dragged` behavior for drag when present

Effects, speech, interactions, non-zero behavior groups, and most `Skip=True` story sequences are skipped. A summary dialog lists what was imported or omitted—always review the action graph before saving.

### Action Properties

On the left side of the editor is the list of actions. Selecting an action in this list allows its properties to be edited on the right:

* **Special type**: Leave blank for normal walk/idle clips. Known values: `teleport-out` / `teleport-in` and `screen-in` / `screen-out`. See the [Technical Spec](TECHNICAL_SPEC.md) for details.
* **Anchors left/right** (`<anchorx>` / `<anchory>`): Optional pixel coordinates of the pony’s feet. Leave empty for normal sheets that are already centre-bottom aligned.
* **Speed**: Travel and animation rate for this action (positive float; default `1`).
* **Loop animation**: Checked by default. Uncheck for **one-shot transition** clips (intros, outros, reactions).
* **Sprites from**: When set to another action’s name, this action is an **alias**: it reuses that action’s bitmaps and timings.
* **Gaits**: Optional load-time bag of `speed:weight` entries (e.g. `0.5:1,0.7:3,1:1`). Use the **Ground** button for the built-in ground bag.
* **Left/right sprite**:
    * **Import image**: Loads one already-packed PNG strip or one GIF.
    * **Import frames**: Opens a folder of PNGs and handles packing, scaling, and per-frame **lift**.
    * **Mirror to right/left**: Builds the opposite facing by flopping cells.
    * **Preview**: Displays the strip and highlights frames.
* **Left/right timings**: Comma-separated durations for each frame (hundredths of a second).
* **Next moving/waiting actions**: Lists of possible transitions. Use `name:N` for weighting. Use `none` or `-` to signify no successor.
* **Drag override**: Optional per-action replacement for **Default drag**.

At the bottom is the list of **Start actions** (how the pony can enter the scene) and **Default drag** (fallback for actions with no override).
