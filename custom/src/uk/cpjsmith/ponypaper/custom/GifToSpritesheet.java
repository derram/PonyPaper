package uk.cpjsmith.ponypaper.custom;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Standalone GIF → PonyPaper spritesheet converter.
 *
 * <p>Uses {@link ImageImport} — the same path the custom editor uses when you
 * import a GIF — so coalescing, transparency, and left-to-right packing stay
 * identical. Default scale is native size; {@code --half} matches the Desktop
 * Ponies folder importer.
 *
 * <p>Usage:
 * <pre>
 *   java -cp customponies.jar uk.cpjsmith.ponypaper.custom.GifToSpritesheet INPUT.gif [OUTPUT.png]
 *   java -jar customponies.jar -gif-to-sheet INPUT.gif [OUTPUT.png]
 * </pre>
 */
public final class GifToSpritesheet {

    private GifToSpritesheet() {}

    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * @return process exit code (0 success, non-zero failure)
     */
    public static int run(String[] args) {
        boolean quiet = false;
        File timingsFile = null;
        File input = null;
        File output = null;
        ImageImport.PackOptions options = new ImageImport.PackOptions();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-h".equals(arg) || "--help".equals(arg) || "-help".equals(arg)) {
                showUsage();
                return 0;
            }
            if ("-q".equals(arg) || "--quiet".equals(arg)) {
                quiet = true;
                continue;
            }
            if ("-t".equals(arg) || "--timings".equals(arg)) {
                if (i + 1 >= args.length) {
                    System.err.println("Option " + arg + " requires a file path.");
                    return 2;
                }
                timingsFile = new File(args[++i]);
                continue;
            }
            if ("--half".equals(arg)) {
                options.scalePercent = ImageImport.SCALE_DESKTOP_PONIES;
                continue;
            }
            if ("--scale".equals(arg)) {
                if (i + 1 >= args.length) {
                    System.err.println("Option " + arg + " requires 100 or 50.");
                    return 2;
                }
                try {
                    options.scalePercent = ImageImport.parseScalePercent(args[++i]);
                } catch (IOException e) {
                    System.err.println("Invalid --scale: " + e.getMessage());
                    return 2;
                }
                continue;
            }
            if (arg.startsWith("-")) {
                System.err.println("Unknown option: " + arg);
                showUsage();
                return 2;
            }
            if (input == null) {
                input = new File(arg);
            } else if (output == null) {
                output = new File(arg);
            } else {
                System.err.println("Too many arguments.");
                showUsage();
                return 2;
            }
        }

        if (input == null) {
            showUsage();
            return 2;
        }
        if (!input.isFile()) {
            System.err.println("Input not found or not a file: " + input);
            return 1;
        }
        if (output == null) {
            output = defaultOutput(input);
        }

        try {
            ImageImport imported = ImageImport.load(input, options);
            if (imported.timings == null) {
                // Non-GIF: still write the raw bytes if the user asked, but warn.
                System.err.println("Warning: " + input.getName()
                        + " is not a GIF; writing original bytes without frame timings.");
            }
            Files.write(output.toPath(), imported.loadedImage);

            String timings = imported.timings != null ? imported.timings : "";
            if (timingsFile != null) {
                Files.writeString(timingsFile.toPath(), timings + System.lineSeparator());
            }

            // Timings always on stdout so scripts can capture them.
            System.out.println(timings);

            if (!quiet) {
                System.err.println("Wrote " + output.getPath());
                if (imported.timings != null) {
                    int frames = imported.timings.isEmpty()
                            ? 0
                            : imported.timings.split(",", -1).length;
                    System.err.println("Frames: " + frames
                            + (imported.cellWidth > 0
                                    ? "  cell: " + imported.cellWidth + "×" + imported.cellHeight
                                    : "")
                            + "  scale: " + options.scalePercent + "%"
                            + "  timings (cs): " + imported.timings);
                }
                if (timingsFile != null) {
                    System.err.println("Timings file: " + timingsFile.getPath());
                }
            }
            return 0;
        } catch (IOException e) {
            System.err.println("Conversion failed: " + e.getMessage());
            return 1;
        }
    }

    private static File defaultOutput(File input) {
        String name = input.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        File parent = input.getParentFile();
        return parent != null ? new File(parent, base + ".png") : new File(base + ".png");
    }

    public static void showUsage() {
        System.out.println("GifToSpritesheet — convert a GIF to a PonyPaper spritesheet PNG");
        System.out.println("Uses the same ImageImport logic as the custom pony editor.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -cp customponies.jar uk.cpjsmith.ponypaper.custom.GifToSpritesheet [options] INPUT.gif [OUTPUT.png]");
        System.out.println("  java -jar customponies.jar -gif-to-sheet [options] INPUT.gif [OUTPUT.png]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help           Show this help");
        System.out.println("  -q, --quiet          Suppress status on stderr (timings still on stdout)");
        System.out.println("  -t, --timings FILE   Also write comma-separated frame timings to FILE");
        System.out.println("  --scale 100|50       Linear size (default 100 / native pixels)");
        System.out.println("  --half               Same as --scale 50 (Desktop Ponies → built-in size)");
        System.out.println();
        System.out.println("If OUTPUT is omitted, writes INPUT with the extension replaced by .png.");
        System.out.println("Frame timings (hundredths of a second, comma-separated) are printed to stdout.");
        System.out.println("The GIF is coalesced and packed left-to-right. Default is native size.");
    }
}
