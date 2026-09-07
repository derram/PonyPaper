package uk.cpjsmith.ponypaper.custom;

import java.util.ArrayList;
import uk.cpjsmith.ponypaper.UnpinnedLru;

/**
 * Checks unpinned-sheet LRU accounting (byte budget, MRU touch).
 * Run via {@code ./gradlew :custom:testUnpinnedLru}.
 */
public final class UnpinnedLruTest {

    private UnpinnedLruTest() {}

    public static void main(String[] args) {
        int failures = 0;
        failures += run("evictEldestOverBudget", UnpinnedLruTest::testEvictEldestOverBudget);
        failures += run("touchMakesMru", UnpinnedLruTest::testTouchMakesMru);
        failures += run("removeSubtractsBytes", UnpinnedLruTest::testRemoveSubtractsBytes);
        failures += run("updateBytesOnTouch", UnpinnedLruTest::testUpdateBytesOnTouch);
        failures += run("singleEntryOverBudget", UnpinnedLruTest::testSingleEntryOverBudget);
        if (failures > 0) {
            System.err.println(failures + " unpinned-lru check(s) failed.");
            System.exit(1);
        }
        System.out.println("UnpinnedLru checks passed.");
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

    private static void testEvictEldestOverBudget() {
        UnpinnedLru lru = new UnpinnedLru();
        lru.addOrTouch("a", 10);
        lru.addOrTouch("b", 10);
        lru.addOrTouch("c", 10);
        ArrayList<String> victims = lru.eldestOverBudget(20);
        if (victims.size() != 1 || !"a".equals(victims.get(0))) {
            throw new AssertionError("expected eldest a, got " + victims);
        }
        if (lru.size() != 3 || lru.bytes() != 30) {
            throw new AssertionError("eldestOverBudget must not mutate");
        }
    }

    private static void testTouchMakesMru() {
        UnpinnedLru lru = new UnpinnedLru();
        lru.addOrTouch("a", 10);
        lru.addOrTouch("b", 10);
        lru.addOrTouch("c", 10);
        lru.addOrTouch("a", 10);
        ArrayList<String> victims = lru.eldestOverBudget(20);
        if (victims.size() != 1 || !"b".equals(victims.get(0))) {
            throw new AssertionError("touched a should be MRU; expected evict b, got "
                    + victims);
        }
        ArrayList<String> order = lru.keysEldestFirst();
        if (order.size() != 3 || !"b".equals(order.get(0)) || !"c".equals(order.get(1))
                || !"a".equals(order.get(2))) {
            throw new AssertionError("order eldest→MRU should be b,c,a; got " + order);
        }
    }

    private static void testRemoveSubtractsBytes() {
        UnpinnedLru lru = new UnpinnedLru();
        lru.addOrTouch("a", 10);
        lru.addOrTouch("b", 7);
        lru.remove("a");
        if (lru.size() != 1 || lru.bytes() != 7 || lru.contains("a") || !lru.contains("b")) {
            throw new AssertionError("remove a should leave b at 7 bytes");
        }
        lru.remove("missing");
        if (lru.bytes() != 7) {
            throw new AssertionError("missing remove should be a no-op");
        }
        lru.clear();
        if (lru.size() != 0 || lru.bytes() != 0) {
            throw new AssertionError("clear should empty");
        }
    }

    private static void testUpdateBytesOnTouch() {
        UnpinnedLru lru = new UnpinnedLru();
        lru.addOrTouch("a", 10);
        lru.addOrTouch("b", 10);
        lru.addOrTouch("a", 4);
        if (lru.bytes() != 14) {
            throw new AssertionError("expected 14 bytes after shrinking a, got " + lru.bytes());
        }
        ArrayList<String> order = lru.keysEldestFirst();
        if (!"b".equals(order.get(0)) || !"a".equals(order.get(1))) {
            throw new AssertionError("touch should move a to MRU; got " + order);
        }
    }

    private static void testSingleEntryOverBudget() {
        UnpinnedLru lru = new UnpinnedLru();
        lru.addOrTouch("fat", 50);
        ArrayList<String> victims = lru.eldestOverBudget(24);
        if (victims.size() != 1 || !"fat".equals(victims.get(0))) {
            throw new AssertionError("over-budget single entry should be evicted; got "
                    + victims);
        }
        victims = lru.eldestOverBudget(50);
        if (!victims.isEmpty()) {
            throw new AssertionError("at-budget should evict nothing; got " + victims);
        }
    }
}
