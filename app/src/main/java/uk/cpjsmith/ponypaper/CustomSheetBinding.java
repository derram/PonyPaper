package uk.cpjsmith.ponypaper;

import java.io.File;

/**
 * SpriteCache factories for one custom action or effect. File paths are
 * preferred; PNG byte arrays are the fallback when unpack did not run.
 */
final class CustomSheetBinding {

    private boolean prepared;
    SpriteCache.SheetFactory leftFactory;
    SpriteCache.SheetFactory rightFactory;
    String leftKey;
    String rightKey;

    boolean isPrepared() {
        return prepared;
    }

    static boolean sameTimes(int[] a, int[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    boolean sharesFacing(String fileLeft, String fileRight,
            int[] timesLeft, int[] timesRight,
            byte[] imageLeft, byte[] imageRight,
            String b64Left, String b64Right,
            String timingLeft, String timingRight) {
        if (fileLeft != null) {
            return fileLeft.equals(fileRight) && sameTimes(timesLeft, timesRight);
        }
        if (imageLeft != null) {
            return imageLeft == imageRight && timesLeft == timesRight;
        }
        return b64Left != null && b64Left.equals(b64Right)
                && timingLeft != null && timingLeft.equals(timingRight);
    }

    void bindFiles(String fileLeft, String fileRight, int[] timesLeft, int[] timesRight) {
        leftFactory = SpriteCache.fileFactory(new File(fileLeft), timesLeft);
        if (fileLeft.equals(fileRight) && sameTimes(timesLeft, timesRight)) {
            rightFactory = leftFactory;
        } else {
            rightFactory = SpriteCache.fileFactory(new File(fileRight), timesRight);
        }
        prepared = true;
    }

    void bindBytes(byte[] left, byte[] right, int[] timesLeft, int[] timesRight) {
        leftFactory = SpriteCache.bytesFactory(left, timesLeft);
        if (left == right && timesLeft == timesRight) {
            rightFactory = leftFactory;
        } else {
            rightFactory = SpriteCache.bytesFactory(right, timesRight);
        }
        prepared = true;
    }

    /**
     * Fill {@link #leftKey} and {@link #rightKey}, storing them on the
     * definition when this is the first graph to pin the sheet.
     */
    void ensureKeys(String fileLeft, String fileRight,
            byte[] imageLeft, byte[] imageRight,
            int[] timesLeft, int[] timesRight,
            String storedLeft, String storedRight,
            KeySink sink) {
        if (!prepared) {
            throw new IllegalStateException("sheet binding");
        }
        if (leftKey == null) {
            if (storedLeft != null) {
                leftKey = storedLeft;
            } else if (fileLeft != null) {
                leftKey = SpriteCache.fileKey(new File(fileLeft), timesLeft);
                sink.storeLeft(leftKey);
            } else {
                leftKey = SpriteCache.bytesKey(imageLeft, timesLeft);
                sink.storeLeft(leftKey);
            }
        }
        if (rightKey == null) {
            boolean sharedFiles = fileLeft != null && fileLeft.equals(fileRight)
                    && sameTimes(timesLeft, timesRight);
            boolean sharedBytes = fileLeft == null && imageLeft == imageRight
                    && timesLeft == timesRight;
            if (sharedFiles || sharedBytes) {
                rightKey = leftKey;
                if (storedRight == null) {
                    sink.storeRight(rightKey);
                }
            } else if (storedRight != null) {
                rightKey = storedRight;
            } else if (fileRight != null) {
                rightKey = SpriteCache.fileKey(new File(fileRight), timesRight);
                sink.storeRight(rightKey);
            } else {
                rightKey = SpriteCache.bytesKey(imageRight, timesRight);
                sink.storeRight(rightKey);
            }
        }
    }

    interface KeySink {
        void storeLeft(String key);

        void storeRight(String key);
    }
}
