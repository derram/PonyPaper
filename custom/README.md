# Custom ponies in PonyPaper
I.e. adding extra ponies (griffons, dragons, etc.) to PonyPaper that aren't available by default. This can mean anything from your own OC to characters from the fandom or even canon characters that I haven't added yet (and how could I possibly not have added *insert name here* because they are clearly **best horse**).

## Overview
PonyPaper represent each custom pony as a single XML file.

Once a pony has been created by the editor, it can be loaded into the wallpaper in either of two ways:
* Connect your device to your computer and copy the .xml file into the app’s external files directory, then force the wallpaper to reload custom ponies by going to the wallpaper's preferences ensuring the custom pony is enabled. (Note: if you're in the right place, there should be a file already in this directory titled `custom-ponies-go-here`.)
  * Release builds: `Android/data/io.github.derram.ponypaper/files`
  * Debug builds: `Android/data/io.github.derram.ponypaper.debug/files`
  * Upstream original (if still installed): `Android/data/uk.cpjsmith.ponypaper/files`
* Have the XML file hosted on a website and download it with your device's web browser; then go to the wallpaper's preferences, choose "Add custom pony" and select the file from your downloads. (Note: This may or may not work for you; it works on my phone, but when I try it on a virtual device, I don't get an option to select from downloads. Not sure what's going on here.)

After the new pony is added, they can be enabled and disabled just like built-in ponies. Currently the only way to remove them completely is to delete the XML file manually.

Files for some of my own favourite fan characters are available at http://cpjsmith.co.uk/downloads/ponypaper/.

# PonyPaper Custom Pony Editor
This tool is capable of creating and editting the XML files that represent custom ponies, selecting the desired sprites and behaviours of the pony.

## Sprites
For each action that your pony can perform, the app requires a sprite each for the left- and right-facing directions. The editor can import sprites in three ways.

1. You can import a GIF animation designed for use in Desktop Ponies. The editor will convert such files into a left-to-right PNG strip so that the app can use them.
2. You can import a folder of PNG frames, or multi-select the frames, with **Import frames**. The editor packs them into that same strip: one uniform cell per frame, each frame drawn **bottom-centre** on a transparent canvas, **no padding between cells**. Cell width is the max frame width; cell height is `max(frameH + lift)`. **Lift** is optional pixels of air under a frame (0 = on the ground) so hop / jump cycles can be built from same-sized crops. Sheet width is exactly `frameCount × cellWidth`. Timings default to `10` (hundredths of a second) for each frame; existing timings are kept when the count already matches. Prefer this over building a sheet by hand.
3. You can import a finished spritesheet: a single PNG, all frames left to right, plus a timings list whose length is the frame count. See [the built-in spritesheets](/res/drawable) for examples. The wallpaper splits the sheet with integer division (`sheetWidth / timings.length`) — extra pixels on the right are dropped, and gutters between frames will slice the animation wrong. Do not add padding between cells.

The file [twilight-sparkle.xml](/custom/twilight-sparkle.xml) contains a copy of the built-in Twilight Sparkle. Twilight can be either a unicorn or an alicorn and can both fly and teleport, so she has examples of many possible details in creating ponies.

## The Editor

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

### GIF → spritesheet converter (standalone)

Same conversion the editor uses when you import a Desktop Ponies GIF (`ImageImport`: coalesce frames, half-scale, pack left-to-right, emit timings in hundredths of a second):

```bash
# Via the editor JAR
java -jar custom/build/libs/customponies.jar \
  -gif-to-sheet walk_left.gif walk_left.png

# Or by main class
java -cp custom/build/libs/customponies.jar \
  uk.cpjsmith.ponypaper.custom.GifToSpritesheet walk_left.gif

# Options: -q (quiet), -t timings.txt (write timings file), -h (help)
# Timings (comma-separated cs) are always printed to stdout.
```

If the output path is omitted, the converter writes `INPUT` with a `.png` extension beside the input.

### PNG frames → spritesheet packer (standalone)

Same packing the editor uses for **Import frames** (`ImageImport.fromFrameFiles`: natural-sort, pad mixed sizes bottom-centre, optional per-frame lift, pack left-to-right with no gutters, default timings 10 cs):

```bash
# Folder of frames
java -jar custom/build/libs/customponies.jar \
  -pack-sheet walk_left.png walk_left_frames/

# Explicit files (any order; they are natural-sorted)
java -jar custom/build/libs/customponies.jar \
  -pack-sheet walk_left.png walk_2.png walk_10.png walk_1.png

# Hop: lift each frame N pixels off the baseline (length = frame count)
java -jar custom/build/libs/customponies.jar \
  -pack-sheet hop_left.png --lifts 0,8,16,20,16,8,0 hop_left_frames/

# Options: -q (quiet), -t timings.txt, --timing-cs N, --strict-size, --lifts N,N,..., -h (help)
# Timings (comma-separated cs) are always printed to stdout.
```

Into a pony from the sequential CLI:

```bash
java -jar custom/build/libs/customponies.jar \
  -action walk \
  -sprite-frames left walk_left_frames/ \
  -mirror-facing left \
  -save oc.xml
```

For a hop, set `-lifts` before `-sprite-frames` (persists until `none`):

```bash
java -jar custom/build/libs/customponies.jar \
  -action hop \
  -lifts 0,8,16,20,16,8,0 \
  -sprite-frames left hop_left_frames/ \
  -mirror-facing left \
  -save oc.xml
```

`-mirror-facing left` flops **each cell** of the left sheet onto the right (same frame order — not a whole-image `-flop`, which would play the clip backwards). Explicit `<anchorx>` is rewritten as `cellWidth − x`; unset X stays unset.

### Using the GUI

It can be started by launching the JAR from the file manager (if `.jar` is associated with Java) or from the command line with `java -jar …`.

#### Import from Desktop-Ponies

**File → Import from Desktop-Ponies…** (Ctrl+I) opens a folder chooser. Select a character directory that contains a `pony.ini` and GIF sprites (as shipped in [Desktop Ponies](https://github.com/RoosterDragon/Desktop-Ponies) under `Content/Ponies/<Name>/`). The editor will:

* create actions from importable behaviors (group 0, no multi-pony follow targets)
* convert left/right GIFs into PonyPaper spritesheets and fill frame timings
* build **start** / **next waiting** / **next moving** / **next drag** lists from Chance, Speed, and Movement
* map simple teleport chains (e.g. Twilight’s warp) to `teleport-out` / `teleport-in`
* use a `Dragged` behavior for drag when present

Effects, speech, interactions, non-zero behavior groups, and most `Skip=True` story sequences are skipped. A summary dialog lists what was imported or omitted—always review the action graph before saving.

From the command line:

```bash
# Open an existing pony XML in the GUI
java -jar custom/build/libs/customponies.jar \
  -load twilight-sparkle.xml

# Import and open the GUI with fields filled (review, then Save)
java -jar custom/build/libs/customponies.jar \
  -import-dp ../Desktop-Ponies/Content/Ponies/Ace

# Import and write XML without opening the GUI
java -jar custom/build/libs/customponies.jar \
  -import-dp ../Desktop-Ponies/Content/Ponies/Ace \
  -save ace.xml
```

If the working directory is the PonyPaper repo root (or next to a `Desktop-Ponies` checkout), the folder chooser prefers `../Desktop-Ponies/Content/Ponies`. Save dialogs append `.xml` when the extension is omitted.

On the left side of the editor is the list of actions. You can create a new action or delete the selected action using the buttons underneath the list. Selecting an action in this list allows its properties to be edited on the right. These properties are:
* Special type: This field should usually be left blank. the only current exceptions to this rule are actions related to teleporting; see the section on 'Teleporting', below.
* Anchors left/right (`<anchorx>` / `<anchory>`): Optional. Unscaled pixel coordinates of the pony’s feet on **each** spritesheet. X is from the **left** of that direction’s frame; Y is from the **top**. Leave empty (or omit the XML) for normal sheets that are already centre-bottom aligned. Set X when asymmetric VFX/padding would slide the body sideways; set Y on tall VFX sheets—especially teleports—so the body does not jump onto a shorter stand/walk sheet. **Do not use `<anchory>` to animate a hop** — that pins the feet and cancels the lift. Use **Import frames** lift (or `--lifts` / `-lifts`) so the hop is extra air *under* the sprite and the default feet row stays the cell bottom. Left and right **often differ** when sheets are mirrors (e.g. left X `40`, right X `frameWidth−40`). XML may use a bare tag to set both facings to the same value (`<anchory>59</anchory>`), or directed tags (`<anchorx direction="left">40</anchorx>`). CLI: `-anchorx 42`, `-anchorx left 40`, `-anchorx right none`, same for `-anchory`.
* Speed: Travel and animation rate for this action (positive float; default `1`). While the pony is moving with this action, both how fast it crosses the screen and how fast the sheet plays are multiplied by this factor. While waiting, only animation rate is affected. Typical values match the built-in gaits: **`0.5` stroll**, **`0.7` walk**, **`1.0` trot** (full historical rate). Values above `1` are allowed for very fast characters.
* Loop animation: Checked by default. Uncheck for **one-shot transition** clips (intros, outros, reactions). After one full play of the sheet, the pony picks the next waiting/moving/drag action for the current motion and keeps the existing wait timer or travel target. See [One-shot / transition actions](#one-shot--transition-actions) below. CLI: `-loop false`.
* Sprites from: When set to another action’s name, this action is an **alias**: it reuses that action’s left/right bitmaps and timings, and only stores its own speed and next-action lists. Use this for stroll/walk variants of one trot sheet without embedding the PNG three times. The owner must not itself be an alias (no chains). Leave empty when this action owns its sprites. **Clone as gait…** creates a named alias in one step.
* Gaits: Optional load-time bag of `speed:weight` entries (e.g. `0.5:1,0.7:3,1:1`). When set, every reference to this action in start/next lists is expanded into weighted speed variants that share this action’s sprites—the same idea as built-in discrete gaits, without listing separate actions. Use the **Ground** button for the built-in ground bag (stroll 1/5, walk 3/5, full 1/5) or **Idle** for a 50/50 full vs walk-rate stand bag. Leave empty for a single fixed speed.
* Left/right sprite: The text field simply states whether an image has been loaded or not. **Preview** displays the strip and highlights frames from the timings count. **Mirror to right** / **Mirror to left** (beside Preview) builds the opposite facing by flopping each cell — same frame order and timings, not a whole-image flip. Confirm if the destination already has a sheet. **Import image** loads one GIF (converted) or one already-packed PNG strip. **Import frames** opens a folder or a multi-selection of PNGs, then a pack dialog: set per-frame **lift** (pixels up from the ground line; drag the preview, spinner, or **Apply hop** for a parabola), confirm cell/sheet size, pack, and open Preview so you can check the split. Lift is baked into the PNG — the wallpaper does not store per-frame offsets. **Export Spritesheet** writes the packed PNG. Aliases show the owner’s image; importing or mirroring on an alias detaches it into a full owner.
* Left/right timings: The list of durations for each frame of the animation. These are represented in hundredths of a second and seperated by commas. GIF import and **Import frames** fill this in automatically.
* Next moving/waiting actions: The comma-seperated list of possible actions the pony can transition to when it decides to move or wait. Note that the same action can be used for more than one of these states; for example, many pegasi reuse the same flying action for both movement and hovering in-place. The reserved tokens **`none`** or **`-`** mean “no real successor” for that list (see [One-shot / transition actions](#one-shot--transition-actions)).
* Drag override: Optional per-action replacement for **Default drag**. Leave empty to inherit the pony-level default. Set it only when this action should use a different drag clip (or list).

At the bottom is the list of **Start actions** (how the pony can enter the scene) and **Default drag** (the drag successors used by every action that has no drag override). Most ponies only need a single drag clip in Default drag.

In the action lists (either 'Start actions', 'Default drag', or 'Next [whatever] actions') the same action can be repeated multiple times to make it more likely to be selected. For example, the built-in Rainbow Dash has a start action list of `trot,fly,fly,fly`, so she will choose the 'fly' action three-quarters of the time. Fluttershy, who prefers to keep her hooves on the ground, has a start action list of `trot,trot,trot,fly`.

## Speed, aliases, and gaits

Built-in ponies do not pick a continuous random speed. They pick among **discrete** stroll / walk / trot (and slow/full idle) actions that share one spritesheet. Custom ponies can match that in two ways:

### 1. Named aliases (`<spritesfrom>`)

Define one full action with images, then alias variants:

```xml
<action name="trot">
    <speed>1</speed>
    <image direction="left">…</image>
    <timings direction="left">…</timings>
    <image direction="right">…</image>
    <timings direction="right">…</timings>
    <nextactions type="waiting">stand</nextactions>
    <nextactions type="moving">stroll,walk,walk,walk,trot</nextactions>
</action>
<action name="walk">
    <speed>0.7</speed>
    <spritesfrom>trot</spritesfrom>
    <nextactions type="waiting">stand</nextactions>
    <nextactions type="moving">stroll,walk,walk,walk,trot</nextactions>
</action>
<action name="stroll">
    <speed>0.5</speed>
    <spritesfrom>trot</spritesfrom>
    <!-- same next lists as walk/trot -->
</action>
<startactions>trot</startactions>
<defaultdrag>trot</defaultdrag>
```

CLI helpers: `-spritesfrom trot`, `-clone-gait walk 0.7`, `-speed 0.5`.

### 2. Load-time gait bag (`<gaits>`)

Keep a single action and expand weights at wallpaper load time:

```xml
<action name="trot">
    <speed>1</speed>
    <!-- images… -->
    <gaits>0.5:1,0.7:3,1:1</gaits>
    <nextactions type="waiting">stand</nextactions>
    <nextactions type="moving">trot</nextactions>
</action>
<startactions>trot</startactions>
<defaultdrag>trot</defaultdrag>
```

Whenever `trot` appears in a start or next list, the runtime substitutes five weighted slots (stroll, walk×3, full)—the same distribution as built-in ground ponies. CLI: `-gaits default` (or `idle` / `none` / a custom `speed:weight` list).

Prefer **aliases** when you want different next graphs per gait; prefer **`<gaits>`** when one sheet and one graph should behave like the built-in bag.

## One-shot / transition actions

By default every action **loops** its spritesheet until the wait timer ends or the pony arrives at a destination. Many characters need short clips that play once and then hand off to another action—intros into a looping pose, outros, or reactions after a move.

Set `<loop>false</loop>` on those actions (or uncheck **Loop animation** in the editor / `-loop false` on the CLI). Missing `<loop>` means true, so existing ponies are unchanged.

On animation complete:

| Current motion | Advances via |
|----------------|--------------|
| Waiting | Next **waiting** list (idle wait timer continues) |
| Moving | Next **moving** list (keeps current target) |
| Dragged | Next **drag** list |

**Wait timer vs one-shots:** the idle timer still counts down during a waiting one-shot, but **expiry is deferred** while the current action has `loop` false. The engine will not start travel or re-pick waiting mid-clip; once the one-shot finishes (and hands off to a looping waiter, or fall-through starts travel), a timer that already hit zero fires on the next opportunity. Looping idle sheets are still interruptible by the timer as before.

### `none` / `-` (no successor)

Use the reserved token **`none`** or **`-`** when a list should have no real action:

```xml
<nextactions type="waiting">none</nextactions>
```

- Tokens are stripped at load; the list becomes empty.
- **Motion and action picks are atomic:** the engine never switches to moving/waiting/drag motion unless it also selected a real next action for that mode. A waiting clip with `moving=none` will **not** be scooted around when the idle timer ends — the timer re-rolls and idle continues (until a oneshot finishes and fall-through applies, or another path has a real mover).
- **One-shot** (`loop` false) fall-through (on animation complete only):
  - Waiting + empty waiting → pick **moving** and start travel (only if a real mover exists)
  - Moving + empty moving → land and pick **waiting** (only if a real waiter exists)
- **Wait timer expired** + empty moving → stay waiting (re-roll timer; optional re-pick waiting). Does **not** fall through to invent travel.
- **Arrive** + empty waiting → keep traveling if a real mover exists; otherwise stop in place.
- At least one of waiting/moving must list a real action on a one-shot.
- **Looping** actions must still have a real successor on waiting and moving lists.
- Drag must always resolve to a real action: either **Default drag** or a per-action **Drag override**. `none`/`-` is not allowed for drag.
- Start actions cannot be only `none`/`-`.
- You cannot name an action `none` or `-`.

This is what makes series like **cakecannon** safe: a pure idle one-shot can use `moving=none` so the sheet never plays while the engine drags the pony across the screen.

Example pattern (see [pinkie-custom.xml](pinkie-custom.xml)):

```xml
<!-- Intro: play once, then start traveling as haters (no idle hold) -->
<action name="haterstart">
    <speed>1</speed>
    <loop>false</loop>
    <!-- images… -->
    <nextactions type="waiting">none</nextactions>
    <nextactions type="moving">haters</nextactions>
</action>

<!-- Loop while walking; after arrive → outro -->
<action name="haters">
    <speed>1</speed>
    <!-- images… (loops by default) -->
    <nextactions type="waiting">haterstop</nextactions>
    <nextactions type="moving">haters</nextactions>
</action>

<!-- Outro: play once, then normal idle bag -->
<action name="haterstop">
    <speed>1</speed>
    <loop>false</loop>
    <nextactions type="waiting">chicken,twitch,tongue,jumpy</nextactions>
    <nextactions type="moving">hop,trot,parade,partycanon</nextactions>
</action>

<!-- Reaction after a travel action arrives -->
<action name="partycanon">
    <nextactions type="waiting">boom</nextactions>
    <!-- … -->
</action>
<action name="boom">
    <loop>false</loop>
    <nextactions type="waiting">chicken,giggle,jumpy,haterstart</nextactions>
    <!-- … -->
</action>
```

Do **not** use teleport special types for ordinary transitions; teleports also change position. Loop policy is independent of special type, speed, spritesfrom, and gaits.

## Teleporting
Normally, when a pony selects a moving action, it loops the animation while gradually moving towards its destination. To enable teleporting requires special handling.

Teleporting requires two actions. The first should have a 'Special type' of `teleport-out` and the second `teleport-in`. Other actions should contain the 'teleport-out' action on their 'Next moving actions' lists; the 'teleport-out' action should have the 'teleport-in' action as its only next moving action. When the pony decides to use the 'teleport-out' action, that animation will play only once without moving, then the pony will move instantly to the destination and play the 'teleport-in' animation once.

Teleport sheets are usually taller than stand/trot sheets because of sparkle VFX under (and above) the body. Without an explicit feet row, bottom-center anchoring treats the bottom of the VFX as the feet and the character will **drop** when the animation finishes. Set `<anchory>` on both the out and in actions to the Y of the hooves in the sheet (pixels from the top of a single frame). Measure on a mid-animation frame where the body is solid; see `twilight-sparkle.xml` for values that match the built-in Twilight teleports. A bare `<anchory>59</anchory>` applies to both left and right sheets; use `direction="left"` / `direction="right"` when the two sheets need different rows.

If a sheet is also wider or padded unevenly (VFX mostly on one side), the body can **slide** sideways on action change or when turning. Set optional `<anchorx>` per facing (pixels from the left of that direction’s frame). Mirror pairs usually need different X values (`W − ax` on the flipped side). Leave unset to keep the default frame centre for that sheet.
