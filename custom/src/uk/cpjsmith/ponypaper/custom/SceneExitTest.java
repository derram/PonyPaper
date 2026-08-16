package uk.cpjsmith.ponypaper.custom;

import java.util.Random;
import uk.cpjsmith.ponypaper.SceneExit;

/**
 * Checks the shared 1-in-8 scene-leave roll.
 * Run via {@code ./gradlew :custom:testSceneExit} or {@code java … SceneExitTest}.
 */
public final class SceneExitTest {

    private SceneExitTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("aboutOneInEight", SceneExitTest::testAboutOneInEight);
        if (failures > 0) {
            System.err.println(failures + " scene-exit check(s) failed.");
            System.exit(1);
        }
        System.out.println("SceneExit checks passed.");
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

    private static void testAboutOneInEight() {
        Random r = new Random(6);
        int leaves = 0;
        int trials = 8000;
        for (int i = 0; i < trials; i++) {
            if (SceneExit.shouldLeaveScene(r)) {
                leaves++;
            }
        }
        // Binomial n=8000 p=1/8: expected 1000; 3σ ≈ 29.6.
        if (leaves < 850 || leaves > 1150) {
            throw new AssertionError("expected ~1000 leaves in " + trials + ", got " + leaves);
        }
    }
}
