package uk.cpjsmith.ponypaper;

import java.util.Random;

/**
 * Stay-or-go decision when a looping waiter's idle timer expires, plus the
 * idle-timer refresh used when picking a next waiting action.
 *
 * <p>Next-waiting and next-moving slot counts are the weights (repeats in
 * those lists already mean extra chance). A waiting slot starts another idle;
 * a moving slot starts travel.
 */
public final class WaitExpiry {

    private WaitExpiry() {}

    /**
     * Idle timer after picking a next waiting action. Remaining time is kept;
     * an already-elapsed timer starts a new wait so stay-or-go cannot
     * immediately replace the successor (a waiting oneshot's hand-off would
     * otherwise re-roll next-waiting on the same or next frame).
     *
     * @param waitTimerMs current remaining idle time (may be {@code <= 0})
     * @param minMs       minimum fresh wait
     * @param extraMs     exclusive extra milliseconds on a fresh wait
     * @param random      source for the extra roll
     * @return remaining time, or a new wait in {@code [minMs, minMs + extraMs)}
     */
    public static float timerAfterWaitingPick(float waitTimerMs, int minMs, int extraMs,
            Random random) {
        if (waitTimerMs > 0) {
            return waitTimerMs;
        }
        if (minMs < 0) minMs = 0;
        if (extraMs < 1) extraMs = 1;
        return minMs + random.nextInt(extraMs);
    }

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
