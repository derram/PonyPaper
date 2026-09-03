package uk.cpjsmith.ponypaper;

/**
 * Rewires a {@link Pony}'s action graph for Tableau: waiting bag only, no
 * movers, drag snaps back via the first bag action.
 */
final class TableauPin {

    private static final PonyAction[] EMPTY_MOVERS = new PonyAction[0];

    private TableauPin() {
    }

    /**
     * Stores pin metadata from {@code slot}, replaces next-lists / start
     * actions with {@code bag}, and marks the pony pinned for in-place
     * re-entry. Pixel placement is deferred until {@link Pony#doUpdate} has
     * clip bounds and sheets are ready ({@code MOTION_INIT_PINNED} →
     * {@link Pony#pinAt}).
     *
     * @param bag non-empty stationary actions (equal chance)
     */
    static void pin(Pony pony, PonyScenes.TableauSlot slot, PonyAction[] bag) {
        if (pony == null) {
            throw new IllegalArgumentException("pony");
        }
        if (slot == null) {
            throw new IllegalArgumentException("slot");
        }
        if (bag == null || bag.length == 0) {
            throw new IllegalArgumentException("wait bag must be non-empty");
        }
        applyWaitBag(pony, bag);
        pony.setPinNormsFromSlot(slot);
        pony.setFacingPolicy(slot.facing, PonyAction.LEFT);
        pony.setPinned(true);
    }

    /**
     * Rewire waiting / drag bags only; leaves pin norms and facing alone.
     * Used by the Tableau hot path when only actions change.
     */
    static void applyWaitBag(Pony pony, PonyAction[] bag) {
        if (pony == null) {
            throw new IllegalArgumentException("pony");
        }
        if (bag == null || bag.length == 0) {
            throw new IllegalArgumentException("wait bag must be non-empty");
        }
        PonyAction[] waitBag = new PonyAction[bag.length];
        System.arraycopy(bag, 0, waitBag, 0, bag.length);
        PonyAction[] dragNext = new PonyAction[] { waitBag[0] };

        PonyAction[] all = pony.getAllActions();
        for (int i = 0; i < all.length; i++) {
            all[i].setNextWaiting(waitBag);
            all[i].setNextMoving(EMPTY_MOVERS);
            all[i].setNextDrag(dragNext);
        }

        pony.waitBag = waitBag;
        pony.setStartActions(waitBag);
        pony.setPinned(true);
    }
}
