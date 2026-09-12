package uk.cpjsmith.ponypaper.custom;

import uk.cpjsmith.ponypaper.BackgroundCrossfade;

/**
 * Checks dream album-cycle cross-fade alpha.
 * Run via {@code ./gradlew :custom:testBackgroundCrossfade}.
 */
public final class BackgroundCrossfadeTest {

    private BackgroundCrossfadeTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("incomingAlpha", BackgroundCrossfadeTest::testIncomingAlpha);
        failures += run("shouldFade", BackgroundCrossfadeTest::testShouldFade);
        if (failures > 0) {
            System.err.println(failures + " background-crossfade check(s) failed.");
            System.exit(1);
        }
        System.out.println("BackgroundCrossfade checks passed.");
    }

    private interface Check {
        void run() throws Exception;
    }

    private static int run(String name, Check check) {
        try {
            check.run();
            System.out.println("ok  " + name);
            return 0;
        } catch (Throwable t) {
            System.err.println("FAIL " + name + ": " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    private static void testIncomingAlpha() {
        int d = BackgroundCrossfade.DURATION_MS;
        if (BackgroundCrossfade.incomingAlpha(0) != 0) {
            throw new AssertionError("start");
        }
        if (BackgroundCrossfade.incomingAlpha(-1) != 0) {
            throw new AssertionError("negative");
        }
        if (BackgroundCrossfade.incomingAlpha(d) != 255) {
            throw new AssertionError("end");
        }
        if (BackgroundCrossfade.incomingAlpha(d + 50) != 255) {
            throw new AssertionError("overshoot");
        }
        int mid = BackgroundCrossfade.incomingAlpha(d / 2);
        if (mid != (d / 2) * 255 / d) {
            throw new AssertionError("mid " + mid);
        }
        if (BackgroundCrossfade.incomingAlpha(10, 0) != 255) {
            throw new AssertionError("zero duration");
        }
        if (BackgroundCrossfade.incomingAlpha(10, -5) != 255) {
            throw new AssertionError("negative duration");
        }
    }

    private static void testShouldFade() {
        if (!BackgroundCrossfade.shouldFade(true, true)) {
            throw new AssertionError("cycle");
        }
        if (BackgroundCrossfade.shouldFade(false, true)) {
            throw new AssertionError("first image");
        }
        if (BackgroundCrossfade.shouldFade(true, false)) {
            throw new AssertionError("drunk fill");
        }
        if (BackgroundCrossfade.shouldFade(false, false)) {
            throw new AssertionError("both false");
        }
    }
}
