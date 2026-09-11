package uk.cpjsmith.ponypaper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Dream shuffle include-set helpers. {@code null} include means every
 * candidate (legacy / Settings never customized).
 */
public final class ShuffleMixBag {

    private ShuffleMixBag() {}

    /**
     * Candidate ids that belong in the bag, in candidate order. Empty
     * candidate ids are skipped. Stale include ids are ignored.
     *
     * @param includeIds {@code null} means all candidates
     */
    public static ArrayList<String> filterIds(List<String> candidateIds, Set<String> includeIds) {
        ArrayList<String> out = new ArrayList<String>();
        if (candidateIds == null) return out;
        for (int i = 0; i < candidateIds.size(); i++) {
            String id = candidateIds.get(i);
            if (id == null || id.length() == 0) continue;
            if (includeIds == null || includeIds.contains(id)) out.add(id);
        }
        return out;
    }

    /**
     * True when every non-empty candidate id is in {@code checked}.
     * {@code null} checked means all. Extra ids in {@code checked} do not
     * prevent a match — Settings then stores "all" (key absent) so new mixes
     * still join.
     */
    public static boolean coversAll(List<String> candidateIds, Set<String> checked) {
        if (checked == null) return true;
        if (candidateIds == null) return true;
        for (int i = 0; i < candidateIds.size(); i++) {
            String id = candidateIds.get(i);
            if (id == null || id.length() == 0) continue;
            if (!checked.contains(id)) return false;
        }
        return true;
    }
}
