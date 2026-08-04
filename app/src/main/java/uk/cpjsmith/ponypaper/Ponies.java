package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/**
 * Class to hold the collection of ponies and coordinate their overall motion.
 */
public class Ponies {
    
    /**
     * Hold duration before a touch on a pony becomes a drag. Prevents
     * accidental grabs from home-screen swipes and casual taps.
     */
    private static final int LONG_PRESS_MS = 300;
    
    private static final Comparator<Pony> compareY = new Comparator<Pony>() {
        @Override
        public int compare(Pony lhs, Pony rhs) {
            int yL = lhs.getY();
            int yR = rhs.getY();
            return yL < yR ? -1 : yL > yR ? 1 : 0;
        }
    };
    
    private int activeCount;
    
    private Random random;
    
    private ArrayList<Pony> inactivePonies;
    private Pony[] activePonies;
    /**
     * Preference key of the user's favorite pony ({@code pref_waifu}), or empty
     * for none. When non-empty, inactive ponies with this key are preferred when
     * filling or replacing active slots.
     */
    private final String waifuKey;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int touchSlop;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (pendingPony == null) return;
            draggedPony = pendingPony;
            pendingPony = null;
            draggedThisGesture = true;
            draggedPony.startDrag();
            draggedPony.moveTo(new Point(Math.round(lastX), Math.round(lastY)));
        }
    };
    
    private int initialPointerId = -1;
    private Pony draggedPony = null;
    /** Pony under the finger waiting for the long-press timeout. */
    private Pony pendingPony = null;
    /**
     * True after a long-press drag starts within the current gesture. Used by
     * hosts (e.g. dream/screensaver) that dismiss on tap but keep a drag open.
     */
    private boolean draggedThisGesture = false;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    
    /**
     * Creates a new {@code Ponies} instance using {@code pref_num_ponies}.
     *
     * @param context the current application context
     * @param prefs   the user's preferences of which ponies to load
     */
    public Ponies(Context context, SharedPreferences prefs) {
        this(context, prefs, prefs.getInt("pref_num_ponies", 4));
    }

    /**
     * Creates a new {@code Ponies} instance with an explicit active-pony count.
     * Callers can pass a battery-saver (or other policy) capped value instead of
     * reading the preference themselves.
     *
     * @param context     the current application context
     * @param prefs       the user's preferences of which ponies to load
     * @param desiredCount requested number of on-screen ponies (clamped to the
     *                    available pool size; values below 1 become 0)
     */
    public Ponies(Context context, SharedPreferences prefs, int desiredCount) {
        inactivePonies = AllPonies.getPonies(context, prefs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        String rawWaifu = prefs.getString("pref_waifu", "");
        waifuKey = rawWaifu != null ? rawWaifu : "";

        if (desiredCount < 0) desiredCount = 0;
        activeCount = Math.min(inactivePonies.size(), desiredCount);

        random = new Random();
        activePonies = new Pony[activeCount];
        for (int i = 0; i < activeCount; i++) {
            activePonies[i] = takeFromInactive();
        }
    }

    /**
     * @return how many ponies are currently drawn on screen
     */
    public int getActiveCount() {
        return activeCount;
    }

    /**
     * Resets the position of all active (on-screen) ponies.
     */
    public void reset() {
        for (Pony pony : activePonies) pony.reset();
    }
    
    /**
     * Updates all active ponies for the elapsed time and draws them on the
     * given canvas.
     * 
     * @param c       the canvas to draw on
     * @param deltaMs milliseconds since the previous frame (animation and
     *                motion are scaled by this so they stay consistent across
     *                framerates)
     */
    public void drawAndUpdate(Canvas c, long deltaMs) {
        for (int i = 0; i < activePonies.length; i++) {
            activePonies[i].doUpdate(c.getClipBounds(), deltaMs);
            if (activePonies[i].goneOffScreen()) {
                Pony temp = activePonies[i];
                temp.reset();
                if (inactivePonies.size() != 0) {
                    activePonies[i] = takeFromInactive();
                    inactivePonies.add(temp);
                }
                activePonies[i].doUpdate(c.getClipBounds(), 0);
            }
        }
        Arrays.sort(activePonies, compareY);
        for (int i = 0; i < activePonies.length; i++) {
            activePonies[i].drawOn(c);
        }
    }
    
    /**
     * Handles a touch event on the screen. This allows the user a means of
     * dragging ponies around the screen. A short hold is required before a
     * drag starts so home-screen swipes and taps do not grab a pony by
     * accident.
     * 
     * @param event the touch event that was performed by the user
     */
    public void onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                endDrag();
                cancelPendingDrag();
                draggedThisGesture = false;
                
                initialPointerId = event.getPointerId(0);
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                
                for (Pony pony : activePonies) {
                    if (pony.testHitPoint(downX, downY)) pendingPony = pony;
                }
                if (pendingPony != null) {
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endDrag();
                cancelPendingDrag();
                initialPointerId = -1;
                break;
                
            case MotionEvent.ACTION_MOVE:
                lastX = event.getX();
                lastY = event.getY();
                if (pendingPony != null) {
                    float dx = lastX - downX;
                    float dy = lastY - downY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        // Finger moved before the hold completed — treat as a
                        // home-screen gesture, not a grab.
                        cancelPendingDrag();
                    }
                }
                if (draggedPony != null) {
                    draggedPony.moveTo(new Point(Math.round(lastX), Math.round(lastY)));
                }
                break;
                
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == initialPointerId) {
                    endDrag();
                    cancelPendingDrag();
                    initialPointerId = -1;
                }
                break;
        }
    }

    /**
     * Whether the current (or just-finished) gesture long-press-dragged a pony.
     * Cleared on the next {@link MotionEvent#ACTION_DOWN}.
     */
    public boolean didDragThisGesture() {
        return draggedThisGesture;
    }
    
    private void cancelPendingDrag() {
        handler.removeCallbacks(longPressRunnable);
        pendingPony = null;
    }
    
    private void endDrag() {
        if (draggedPony != null) {
            draggedPony.stopDrag();
            draggedPony = null;
        }
    }
    
    /**
     * Removes and returns one pony from the inactive pool. If a waifu key is set
     * and any inactive pony matches it, picks uniformly among those; otherwise
     * picks uniformly among all inactive ponies.
     */
    private Pony takeFromInactive() {
        if (inactivePonies.isEmpty()) {
            throw new IllegalStateException("inactive pool is empty");
        }
        if (waifuKey.length() > 0) {
            int matchCount = 0;
            for (int i = 0; i < inactivePonies.size(); i++) {
                if (waifuKey.equals(inactivePonies.get(i).getPrefKey())) {
                    matchCount++;
                }
            }
            if (matchCount > 0) {
                int pick = random.nextInt(matchCount);
                for (int i = 0; i < inactivePonies.size(); i++) {
                    if (waifuKey.equals(inactivePonies.get(i).getPrefKey())) {
                        if (pick == 0) {
                            return inactivePonies.remove(i);
                        }
                        pick--;
                    }
                }
            }
        }
        return inactivePonies.remove(random.nextInt(inactivePonies.size()));
    }
    
}
