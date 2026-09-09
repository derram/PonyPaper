package uk.cpjsmith.ponypaper.custom;

import java.util.Random;
import uk.cpjsmith.ponypaper.WaitExpiry;

/**
 * Checks stay-or-go weights when a looping waiter's idle timer expires.
 * Run via {@code ./gradlew :custom:testWaitExpiry} or {@code java … WaitExpiryTest}.
 */
public final class WaitExpiryTest {

    private WaitExpiryTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("bothEmptyStays", WaitExpiryTest::testBothEmptyStays);
        failures += run("noWaitersAlwaysLeaves", WaitExpiryTest::testNoWaitersAlwaysLeaves);
        failures += run("noMoversAlwaysStays", WaitExpiryTest::testNoMoversAlwaysStays);
        failures += run("equalListsAboutHalf", WaitExpiryTest::testEqualListsAboutHalf);
        failures += run("waitHeavyStaysMore", WaitExpiryTest::testWaitHeavyStaysMore);
        failures += run("elapsedTimerGetsFreshWait", WaitExpiryTest::testElapsedTimerGetsFreshWait);
        failures += run("remainingTimerKept", WaitExpiryTest::testRemainingTimerKept);
        if (failures > 0) {
            System.err.println(failures + " wait-expiry check(s) failed.");
            System.exit(1);
        }
        System.out.println("WaitExpiry checks passed.");
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

    private static void testBothEmptyStays() {
        Random r = new Random(1);
        for (int i = 0; i < 50; i++) {
            if (!WaitExpiry.shouldStayIdle(0, 0, r)) {
                throw new AssertionError("empty lists should stay idle");
            }
        }
    }

    private static void testNoWaitersAlwaysLeaves() {
        Random r = new Random(2);
        for (int i = 0; i < 50; i++) {
            if (WaitExpiry.shouldStayIdle(0, 5, r)) {
                throw new AssertionError("no waiting slots should always leave");
            }
        }
    }

    private static void testNoMoversAlwaysStays() {
        Random r = new Random(3);
        for (int i = 0; i < 50; i++) {
            if (!WaitExpiry.shouldStayIdle(7, 0, r)) {
                throw new AssertionError("no moving slots should always stay");
            }
        }
    }

    private static void testEqualListsAboutHalf() {
        int stays = countStays(1, 1, 4000, 4);
        // Binomial n=4000 p=0.5: 3σ ≈ 95. Extreme 30–70% would already be wrong.
        if (stays < 1700 || stays > 2300) {
            throw new AssertionError("1 vs 1 expected ~2000 stays, got " + stays);
        }
    }

    private static void testWaitHeavyStaysMore() {
        int stays = countStays(7, 2, 4500, 5);
        // p = 7/9 ≈ 0.778, expected 3500; 3σ ≈ 83.
        if (stays < 3200 || stays > 3800) {
            throw new AssertionError("7 vs 2 expected ~3500 stays, got " + stays);
        }
    }

    private static void testElapsedTimerGetsFreshWait() {
        Random r = new Random(6);
        for (int i = 0; i < 40; i++) {
            float next = WaitExpiry.timerAfterWaitingPick(0f, 2000, 10000, r);
            if (next < 2000f || next >= 12000f) {
                throw new AssertionError("fresh wait out of range: " + next);
            }
            float also = WaitExpiry.timerAfterWaitingPick(-3f, 2000, 10000, r);
            if (also < 2000f || also >= 12000f) {
                throw new AssertionError("elapsed wait out of range: " + also);
            }
        }
    }

    private static void testRemainingTimerKept() {
        Random r = new Random(7);
        float kept = WaitExpiry.timerAfterWaitingPick(1500f, 2000, 10000, r);
        if (kept != 1500f) {
            throw new AssertionError("remaining wait should be kept, got " + kept);
        }
    }

    private static int countStays(int waitN, int moveN, int trials, long seed) {
        Random r = new Random(seed);
        int stays = 0;
        for (int i = 0; i < trials; i++) {
            if (WaitExpiry.shouldStayIdle(waitN, moveN, r)) {
                stays++;
            }
        }
        return stays;
    }
}
