# Custom ponies in PonyPaper

Adding extra ponies (griffons, dragons, etc.) to PonyPaper that aren't available by default. This can mean anything from your own OC to characters from the fandom or even canon characters.

![Examples](/screenshots/custom-header.png)

## Overview

PonyPaper represents each custom pony as a single XML file. Once a pony has been created, load it into the wallpaper in any of these ways:

*   **Add custom pony**: In Pony Paper settings, pick the XML file (or a library zip).
*   **Import library**: Merge a zip of custom ponies, background image, and saved mixes (you confirm which categories to apply).
*   **Export library**: Tap to save everything exportable; long-press to choose ponies, background, and/or mixes.
*   **Character library folder** (recommended): Choose a folder (e.g., `Documents/PonyPaper`) to sync XML files via USB, Syncthing, etc.
*   **Advanced / `adb`**: Push files directly to `Android/data/io.github.derram.ponypaper/files`.

Several pre-made characters can be found at the [PonyPaper Library](https://www.ponypaper.net).

## Getting Started

To create or modify your own custom ponies, you will need the **Custom Pony Editor** and an understanding of how PonyPaper handles sprites.

### 1. Sprites
The app requires a spritesheet (a single PNG with frames arranged left-to-right) for each action. The editor can help you create these from:
*   **GIF animations**: Automatically coalesced and packed.
*   **Folders of PNG frames**: Packed into uniform cells with optional "lift" for hop cycles.
*   **Finished spritesheets**: Split by the wallpaper using integer division.

### 2. The Editor
The **PonyPaper Custom Editor** is a desktop Java application (requires Java 17+) used to build the XML definitions.

👉 **[PonyPaper Custom Editor Guide (EDITOR.md)](EDITOR.md)**
*   Installation and Build instructions.
*   GUI walkthrough and property descriptions.
*   Importing characters from Desktop Ponies.

### 3. Technical Reference
For advanced users wanting to understand the XML schema or use the command-line interface.

👉 **[PonyPaper Custom Pony Technical Spec (TECHNICAL_SPEC.md)](TECHNICAL_SPEC.md)**
*   Standalone CLI tools for GIF/PNG processing.
*   Advanced engine logic: Teleporting, one-shots, and stationary behaviors.
*   XML tag reference (gaits, aliases, anchors).

## Examples
The following files are included in this directory as reference:
*   [twilight-sparkle.xml](twilight-sparkle.xml): A copy of the built-in Twilight Sparkle, showing unicorns, alicorns, and teleports.
*   [pinkie-custom.xml](pinkie-custom.xml): Demonstrates complex one-shot transitions like the party cannon.
