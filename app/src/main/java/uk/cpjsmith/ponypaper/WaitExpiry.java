package uk.cpjsmith.ponypaper;

import java.util.Random;

/**
 * Stay-or-go decision when a looping waiter's idle timer expires.
 *
 * <p>Next-waiting and next-moving slot counts are the weights (repeats in
 * those lists already mean extra chance). A waiting slot starts another idle;
 * a moving slot starts travel.
 */
public final class WaitExpiry {

    private WaitExpiry() {}

    /**
     * @param waitingSlots length of the current action's next-waiting list
     * @param movingSlots  length of the current action's next-moving list
     * @param random       source of the weighted pick
     * @return {@code true} to stay idle and re-pick waiting; {@code false} to
     *         start travel. Both lists empty stays on the current sheet.
     */
    public static boolean shouldStayIdle(int waitingSlots, int movingSlots, Random random) {
        if (waitingSlots < 0) waitingSlots = 0;
        if (movingSlots < 0) movingSlots = 0;
        int total = waitingSlots + movingSlots;
        if (total == 0) {
            return true;
        }
        if (waitingSlots == 0) {
            return false;
        }
        return random.nextInt(total) < waitingSlots;
    }
}
