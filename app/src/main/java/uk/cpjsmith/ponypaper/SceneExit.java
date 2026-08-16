package uk.cpjsmith.ponypaper;

import java.util.Random;

/**
 * Destination leave-vs-stay roll when a move starts.
 *
 * <p>Walkers apply this to the travel target (off-screen vs on-screen).
 * {@code screen-out} applies it to vanish vs keep idling. Same 1-in-8
 * chance so stationary characters cycle at the same rate as walkers
 * with the same stay/go weights.
 */
public final class SceneExit {

    /** Off-screen / vanish chance denominator ({@code 1/LEAVE_DENOMINATOR}). */
    public static final int LEAVE_DENOMINATOR = 8;

    private SceneExit() {}

    /**
     * @return {@code true} when this move should leave the scene
     */
    public static boolean shouldLeaveScene(Random random) {
        return random.nextInt(LEAVE_DENOMINATOR) < 1;
    }
}
