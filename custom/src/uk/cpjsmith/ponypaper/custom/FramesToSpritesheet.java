package uk.cpjsmith.ponypaper.custom;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone PNG-frame → PonyPaper spritesheet packer.
 *
 * <p>Uses {@link ImageImport#fromFrameFiles} — the same path the custom editor
 * uses for folder / multi-select import.
 *
 * <p>Usage:
 * <pre>
 *   java -cp customponies.jar uk.cpjsmith.ponypaper.custom.FramesToSpritesheet OUT.png FRAME.png...
 *   java -jar customponies.jar -pack-sheet OUT.png FRAME.png...
 *   java -jar customponies.jar -pack-sheet OUT.png framedir/
 * </pre>
 */
public final class FramesToSpritesheet {

    private FramesToSpritesheet() {}

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
        ImageImport.PackOptions options = new ImageImport.PackOptions();
        File output = null;
        List<File> inputs = new ArrayList<File>();

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
            if ("--timing-cs".equals(arg)) {
                if (i + 1 >= args.length) {
                    System.err.println("Option " + arg + " requires an integer.");
                    return 2;
                }
                try {
                    options.defaultTimingCs = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid --timing-cs: " + args[i]);
                    return 2;
                }
                if (options.defaultTimingCs < 1) {
                    System.err.println("--timing-cs must be >= 1");
                    return 2;
                }
                continue;
            }
            if ("--strict-size".equals(arg)) {
                options.rejectMixedSizes = true;
                continue;
            }
            if ("--half".equals(arg)) {
                options.scaleNumerator = ImageImport.SCALE_NUMERATOR_NATIVE;
                options.scaleDivisor = ImageImport.SCALE_DIVISOR_HALF;
                options.scaleFitBuiltin = false;
                continue;
            }
            if ("--scale".equals(arg)) {
                if (i + 1 >= args.length) {
                    System.err.println("Option " + arg + " requires "
                            + ImageImport.SCALE_CLI_TOKENS + ".");
                    return 2;
                }
                try {
                    ImageImport.applyScale(options, ImageImport.parseScale(args[++i]));
                } catch (IOException e) {
                    System.err.println("Invalid --scale: " + e.getMessage());
                    return 2;
                }
                continue;
            }
            if ("--lifts".equals(arg)) {
                if (i + 1 >= args.length) {
                    System.err.println("Option " + arg + " requires a comma-separated list.");
                    return 2;
                }
                try {
                    options.lifts = ImageImport.parseLifts(args[++i]);
                } catch (IOException e) {
                    System.err.println("Invalid --lifts: " + e.getMessage());
                    return 2;
                }
                continue;
            }
            if ("--nudges".equals(arg)) {
                if (i + 1 >= args.length) {
                    System.err.println("Option " + arg + " requires a comma-separated list.");
                    return 2;
                }
                try {
                    options.nudges = ImageImport.parseNudges(args[++i]);
                } catch (IOException e) {
                    System.err.println("Invalid --nudges: " + e.getMessage());
                    return 2;
                }
                continue;
            }
            if (arg.startsWith("-")) {
                System.err.println("Unknown option: " + arg);
                showUsage();
                return 2;
            }
            if (output == null) {
                output = new File(arg);
            } else {
                inputs.add(new File(arg));
            }
        }

        if (output == null || inputs.isEmpty()) {
            showUsage();
            return 2;
        }

        try {
            ImageImport packed = ImageImport.fromFrameFiles(inputs, options);
            Files.write(output.toPath(), packed.loadedImage);

            String timings = packed.timings != null ? packed.timings : "";
            if (timingsFile != null) {
                Files.writeString(timingsFile.toPath(), timings + System.lineSeparator());
            }
            System.out.println(timings);

            if (!quiet) {
                int frames = ImageImport.countTimings(timings);
                System.err.println("Wrote " + output.getPath());
                String scaleLabel;
                try {
                    if (options.scaleFitBuiltin) {
                        scaleLabel = "fit → " + packed.cellWidth + "×" + packed.cellHeight
                                + " cells";
                    } else {
                        scaleLabel = ImageImport.formatScaleMarker(
                                options.scaleNumerator, options.scaleDivisor);
                    }
                } catch (IOException e) {
                    scaleLabel = options.scaleFitBuiltin ? "fit"
                            : (options.scaleNumerator + "/" + options.scaleDivisor);
                }
                System.err.println("Frames: " + frames
                        + "  cell: " + packed.cellWidth + "×" + packed.cellHeight
                        + "  sheet: " + (frames * packed.cellWidth) + "×" + packed.cellHeight
                        + "  scale: " + scaleLabel
                        + "  timings (cs): " + timings);
                if (options.lifts != null) {
                    System.err.println("Lifts: " + ImageImport.formatLifts(options.lifts));
                }
                if (options.nudges != null) {
                    System.err.println("Nudges: " + ImageImport.formatNudges(options.nudges));
                }
                if (timingsFile != null) {
                    System.err.println("Timings file: " + timingsFile.getPath());
                }
            }
            return 0;
        } catch (IOException e) {
            System.err.println("Pack failed: " + e.getMessage());
            return 1;
        }
    }

    public static void showUsage() {
        System.out.println("FramesToSpritesheet — pack PNG frames into a PonyPaper spritesheet");
        System.out.println("Uses the same ImageImport packer as Import frames in the custom editor.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -cp customponies.jar uk.cpjsmith.ponypaper.custom.FramesToSpritesheet [options] OUTPUT.png FRAME.png...");
        System.out.println("  java -jar customponies.jar -pack-sheet [options] OUTPUT.png FRAME.png...");
        System.out.println("  java -jar customponies.jar -pack-sheet [options] OUTPUT.png FRAMEDIR");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help           Show this help");
        System.out.println("  -q, --quiet          Suppress status on stderr (timings still on stdout)");
        System.out.println("  -t, --timings FILE   Also write comma-separated frame timings to FILE");
        System.out.println("  --timing-cs N        Duration for every frame (hundredths of a second, default 10)");
        System.out.println("  --strict-size        Fail if frame pixel sizes differ (default: pad to max, bottom-centre)");
        System.out.println("  --scale " + ImageImport.SCALE_CLI_TOKENS);
        System.out.println("                       Nearest-neighbour scale before packing");
        System.out.println("                       (default 100). 200/2x/double pixel-doubles;");
        System.out.println("                       bare 2 is ÷2 (50%).");
        System.out.println("                       fit = largest shrink with frame height ≤ "
                + ImageImport.LARGE_CELL_HEIGHT_PX + "px (never 200%)");
        System.out.println("  --half               Same as --scale 50 (Desktop Ponies → built-in size)");
        System.out.println("  --lifts N,N,...      Pixels up from the baseline for each frame (0 = on the ground).");
        System.out.println("                       Length must match the frame count. Omit for all zeros.");
        System.out.println("  --nudges N,N,...     Pixels right of centre for each frame (negative = left).");
        System.out.println("                       Length must match the frame count. Omit for all zeros.");
        System.out.println();
        System.out.println("Frames are natural-sorted, packed left-to-right with no gutters.");
        System.out.println("Lift raises a frame in a taller cell (hop / jump). It is baked into the PNG.");
        System.out.println("Nudge shifts a frame from centre (asymmetric crop / registration). It is baked into the PNG.");
        System.out.println("Frame timings (hundredths of a second, comma-separated) are printed to stdout.");
    }
}
