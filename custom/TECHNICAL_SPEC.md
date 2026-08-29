# PonyPaper Custom Pony XML Specification

This document details the XML format and engine logic used by PonyPaper to define custom ponies, as well as the command-line interface for the Custom Pony Editor.

## Standalone CLI Tools

The editor JAR contains several standalone utilities for processing assets.

### GIF → spritesheet converter

Converts a GIF into a packed PNG strip with timings.

```bash
java -jar custom/build/libs/customponies.jar \
  -gif-to-sheet walk_left.gif walk_left.png

# Options: -q (quiet), -t timings.txt, --scale 100|50|25|12.5|6.25|fit, --half, -h (help)
```

### PNG frames → spritesheet packer

Packs individual PNG frames into a single strip.

```bash
java -jar custom/build/libs/customponies.jar \
  -pack-sheet walk_left.png walk_left_frames/

# Options: --timing-cs N, --strict-size, --scale 100|50|25|12.5|6.25|fit, --lifts N,N,...
```

### CLI Pony Creation

You can build or modify XML files entirely from the command line:

```bash
java -jar custom/build/libs/customponies.jar \
  -action walk \
  -scale 50 \
  -sprite left walk_left.gif \
  -mirror-facing left \
  -save oc.xml
```

## Engine Logic & Behaviors

### Speed, aliases, and gaits

Custom ponies can handle discrete speeds in two ways:

1.  **Named aliases** (`<spritesfrom>`): Define one action with images, then create variants that reuse them with different speeds and next-action lists.
2.  **Load-time gait bag** (`<gaits>`): A single action expands into weighted speed variants (e.g. `0.5:1,0.7:3,1:1`) at load time.

### One-shot / transition actions

By default every action **loops**. Set `<loop>false</loop>` for clips that play once and then hand off to a successor from the next-action lists. The idle timer expiry is deferred until a one-shot finishes.

### The `none` / `-` token

Use **`none`** or **`-`** when a next-action list should be empty.
- A waiting clip with `moving=none` will not start travel when the timer expires.
- `screen-out` clips use `none` for both lists as they remove the pony from the scene.
- **Note**: Drag and Start actions must always resolve to a real action.

### Teleporting

Teleporting requires a pair of actions with special types `teleport-out` and `teleport-in`.
1.  The `teleport-out` action plays in place.
2.  The pony instantly moves to the destination.
3.  The `teleport-in` action plays at the new location.

**Scene enter / leave:** only the on-screen half of the pair runs. Entering with a teleport start plays `teleport-in` on-screen (skipping an off-screen `teleport-out`). Leaving via teleport plays `teleport-out` in place and then removes the pony (skipping an off-screen `teleport-in`). This avoids VFX bleeding in from just past the viewport edge. Mid-scene teleports still use the full out → jump → in chain.

Use `<anchory>` to ensure the body doesn't "jump" if the teleport VFX makes the sheet taller than standard poses.

### Stationary enter / exit

For characters that don't walk:
- `screen-in`: Use on **Start actions** to appear on-screen in place.
- `screen-out`: Use on **Next moving** to vanish in place (triggered by a 1-in-8 roll or drag-to-edge).
