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
                System.err.println("Frames: " + frames
                        + "  cell: " + packed.cellWidth + "×" + packed.cellHeight
                        + "  sheet: " + (frames * packed.cellWidth) + "×" + packed.cellHeight
                        + "  timings (cs): " + timings);
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
        System.out.println();
        System.out.println("Frames are natural-sorted, packed left-to-right with no gutters.");
        System.out.println("Frame timings (hundredths of a second, comma-separated) are printed to stdout.");
    }
}
