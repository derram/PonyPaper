package uk.cpjsmith.ponypaper;

import java.util.Random;

/**
 * Wander-herd drain: force on-screen ponies to exit, do not refill from the
 * inactive pool, then rebuild on an empty screen.
 *
 * <p>Tableau reloads keep {@code outgoingPonies} instead; they do not drain.
 */
public final class HerdDrain {

    /**
     * Wall-clock cap so a stuck clip cannot block mix shuffle forever.
     * Long enough for a full-speed crossing of a large screen; drain exits
     * also pick the fastest leave gait so this is a backstop, not the
     * typical wait.
     */
    public static final int TIMEOUT_MS = 15_000;

    /** Longest wait-timer contribution to a staggered exit. */
    public static final int STAGGER_MAX_MS = 800;

    /** Extra delay per active index so identical waits do not chorus-line. */
    public static final int STAGGER_INDEX_MS = 50;

    /** Already {@code LM_GOING}/{@code LM_GONE}, or already playing a leave clip. */
    public static final int EXIT_NOOP = 0;
    /** Spawn / no bounds / no usable mover: drop the slot immediately. */
    public static final int EXIT_MARK_GONE = 1;
    /** Finish the drag, then leave (do not yank a grabbed pony). */
    public static final int EXIT_DEFER_DRAG = 2;
    /** Mid-walk: keep the current clip, retarget the current band off-screen. */
    public static final int EXIT_RETARGET = 3;
    /** Idle / special: {@code tryBeginMoving} with force-leave. */
    public static final int EXIT_BEGIN_LEAVE = 4;
    /** World Flow: resume a crossing exit from the current feet. */
    public static final int EXIT_WORLD_FLOW = 5;
    /** Pinned Tableau: drain is not used. */
    public static final int EXIT_SKIP = 6;

    private HerdDrain() {}

    /**
     * @param alreadyLeaving {@code LM_GOING} or already playing teleport-out /
     *                       screen-out
     * @param traveling      interpolating, special clip, or still spawning
     * @param waitTimerMs    remaining idle wait (ignored when traveling)
     * @param index          active-slot index for a small spread
     */
    public static int staggerDelayMs(boolean alreadyLeaving, boolean traveling,
            float waitTimerMs, int index) {
        if (alreadyLeaving || traveling) {
            return 0;
        }
        int wait = (int) waitTimerMs;
        if (wait < 0) {
            wait = 0;
        }
        if (wait > STAGGER_MAX_MS) {
            wait = STAGGER_MAX_MS;
        }
        if (index < 0) {
            index = 0;
        }
        return wait + index * STAGGER_INDEX_MS;
    }

    public static boolean timedOut(long startUptimeMs, long nowUptimeMs) {
        if (startUptimeMs <= 0) {
            return false;
        }
        return nowUptimeMs - startUptimeMs >= TIMEOUT_MS;
    }

    /** Gone-off-screen refill from the inactive pool. Drain never refills. */
    public static boolean shouldRefill(boolean draining) {
        return !draining;
    }

    public static boolean isComplete(boolean draining, int activeCount) {
        return draining && activeCount <= 0;
    }

    /**
     * @param pinned            Tableau pin (never drain)
     * @param worldFlow         {@link SceneMode#WORLD_FLOW}
     * @param alreadyLeaving    {@code LM_GOING} or leave clip already current
     * @param alreadyGone       {@code LM_GONE}
     * @param dragged           {@code MOTION_DRAGGED}
     * @param spawning          {@code MOTION_INIT} / {@code INIT_PINNED}, or no action
     * @param interpolatingWalk {@code MOTION_MOVING} on a NORMAL clip
     * @param hasScreenBounds   clip has been applied at least once
     */
    public static int decideExit(boolean pinned, boolean worldFlow,
            boolean alreadyLeaving, boolean alreadyGone, boolean dragged,
            boolean spawning, boolean interpolatingWalk, boolean hasScreenBounds) {
        if (pinned) {
            return EXIT_SKIP;
        }
        if (alreadyGone || alreadyLeaving) {
            return EXIT_NOOP;
        }
        if (!hasScreenBounds || spawning) {
            return EXIT_MARK_GONE;
        }
        if (dragged) {
            return EXIT_DEFER_DRAG;
        }
        if (worldFlow) {
            return EXIT_WORLD_FLOW;
        }
        if (interpolatingWalk) {
            return EXIT_RETARGET;
        }
        return EXIT_BEGIN_LEAVE;
    }

    /**
     * Index of a max-speed drain leave. Slots with {@code speed <= 0} (or NaN)
     * are skipped — callers zero out ineligible movers such as {@code screen-in}.
     * Ties pick uniformly.
     *
     * @return an index in {@code speeds}, or {@code -1} if none are eligible
     */
    public static int pickFastestIndex(float[] speeds, Random random) {
        if (speeds == null || speeds.length == 0 || random == null) {
            return -1;
        }
        float max = 0f;
        int nMax = 0;
        for (int i = 0; i < speeds.length; i++) {
            float s = speeds[i];
            if (Float.isNaN(s) || s <= 0f) {
                continue;
            }
            if (nMax == 0 || s > max) {
                max = s;
                nMax = 1;
            } else if (s == max) {
                nMax++;
            }
        }
        if (nMax == 0) {
            return -1;
        }
        int pick = random.nextInt(nMax);
        for (int i = 0; i < speeds.length; i++) {
            if (speeds[i] == max) {
                if (pick == 0) {
                    return i;
                }
                pick--;
            }
        }
        return -1;
    }
}
