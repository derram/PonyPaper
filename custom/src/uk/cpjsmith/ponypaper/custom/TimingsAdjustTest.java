package uk.cpjsmith.ponypaper.custom;

/**
 * Checks for {@link TimingsAdjust#adjustAllTimings}.
 */
public final class TimingsAdjustTest {

    public static void main(String[] args) {
        int failures = 0;
        failures += run("addAndSubtract", TimingsAdjustTest::testAddAndSubtract);
        failures += run("clampAtOne", TimingsAdjustTest::testClampAtOne);
        failures += run("rejectsEmpty", TimingsAdjustTest::testRejectsEmpty);
        failures += run("rejectsNonInteger", TimingsAdjustTest::testRejectsNonInteger);
        if (failures > 0) {
            System.err.println(failures + " timings-adjust check(s) failed.");
            System.exit(1);
        }
        System.out.println("All timings-adjust checks passed.");
    }

    private static int run(String name, Runnable check) {
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

    private static void testAddAndSubtract() {
        assertEquals("11,6,21", TimingsAdjust.adjustAllTimings("10,5,20", 1));
        assertEquals("9,4,19", TimingsAdjust.adjustAllTimings("10,5,20", -1));
        assertEquals("15,10,25", TimingsAdjust.adjustAllTimings("10,5,20", 5));
    }

    private static void testClampAtOne() {
        assertEquals("1,1,3", TimingsAdjust.adjustAllTimings("1,2,5", -2));
        assertEquals("1", TimingsAdjust.adjustAllTimings("1", -100));
    }

    private static void testRejectsEmpty() {
        try {
            TimingsAdjust.adjustAllTimings("  ", 1);
            throw new AssertionError("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    private static void testRejectsNonInteger() {
        try {
            TimingsAdjust.adjustAllTimings("10,x,20", 1);
            throw new AssertionError("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
