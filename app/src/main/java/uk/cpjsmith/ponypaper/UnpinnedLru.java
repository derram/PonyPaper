package uk.cpjsmith.ponypaper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Access-order LRU of unpinned cache keys, accounted by decoded byte size.
 * Eldest is the least recently added or touched key.
 */
public final class UnpinnedLru {

    private static final class Node {
        final String key;
        long bytes;

        Node(String key, long bytes) {
            this.key = key;
            this.bytes = bytes;
        }
    }

    private final LinkedHashMap<String, Node> map =
            new LinkedHashMap<String, Node>(16, 0.75f, true);
    private long totalBytes;

    public UnpinnedLru() {}

    public void addOrTouch(String key, long bytes) {
        if (key == null) {
            return;
        }
        if (bytes < 0) {
            bytes = 0;
        }
        Node existing = map.get(key);
        if (existing != null) {
            totalBytes -= existing.bytes;
            existing.bytes = bytes;
            totalBytes += bytes;
            return;
        }
        map.put(key, new Node(key, bytes));
        totalBytes += bytes;
    }

    public void remove(String key) {
        if (key == null) {
            return;
        }
        Node removed = map.remove(key);
        if (removed != null) {
            totalBytes -= removed.bytes;
        }
    }

    public boolean contains(String key) {
        return key != null && map.containsKey(key);
    }

    public long bytes() {
        return totalBytes;
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
        totalBytes = 0;
    }

    /** Keys from eldest to newest. */
    public ArrayList<String> keysEldestFirst() {
        return new ArrayList<String>(map.keySet());
    }

    /**
     * Eldest keys that must leave so {@link #bytes()} would be {@code <= budget}.
     * Does not mutate the map.
     */
    public ArrayList<String> eldestOverBudget(long budget) {
        if (budget < 0) {
            budget = 0;
        }
        ArrayList<String> out = new ArrayList<String>();
        long running = totalBytes;
        for (Map.Entry<String, Node> e : map.entrySet()) {
            if (running <= budget) {
                break;
            }
            Node node = e.getValue();
            out.add(node.key);
            running -= node.bytes;
        }
        return out;
    }
}
