package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.ScrollView;

/**
 * {@link ScrollView} that honours {@code android:maxHeight}. Used for the dream
 * session sheet (main page and mix picker): a short display — especially
 * landscape — measures the sheet with {@code AT_MOST}, and without a scroll
 * viewport the bottom rows are clipped.
 *
 * <p>Clickable ancestors (the full-screen chrome overlay and the sheet) must
 * not steal the drag. Nested scrolling is off so a dream overlay cannot eat
 * the gesture as a nested pre-scroll.
 */
public class MaxHeightScrollView extends ScrollView {

    private int xmlMaxHeight = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;

    public MaxHeightScrollView(Context context) {
        super(context);
        init();
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        applyMaxHeight(context, attrs);
        init();
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyMaxHeight(context, attrs);
        init();
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        applyMaxHeight(context, attrs);
        init();
    }

    private void init() {
        setNestedScrollingEnabled(false);
    }

    private void applyMaxHeight(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] { android.R.attr.maxHeight });
        try {
            xmlMaxHeight = a.getDimensionPixelSize(0, Integer.MAX_VALUE);
            maxHeight = xmlMaxHeight;
        } finally {
            a.recycle();
        }
    }

    /**
     * Layout cap from XML ({@code android:maxHeight}), before any runtime shrink
     * to fit the remaining window.
     */
    public int getXmlMaxHeight() {
        return xmlMaxHeight;
    }

    /**
     * Bound the viewport. Pass {@link Integer#MAX_VALUE} to use only the XML cap.
     */
    public void setMaxHeight(int pixels) {
        int next = pixels < 0 ? 0 : pixels;
        if (xmlMaxHeight < Integer.MAX_VALUE && next > xmlMaxHeight) {
            next = xmlMaxHeight;
        }
        if (maxHeight == next) return;
        maxHeight = next;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeight < Integer.MAX_VALUE) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            if (mode == MeasureSpec.UNSPECIFIED) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            } else {
                int capped = size > maxHeight ? maxHeight : size;
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(capped, mode);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            disallowParentIntercept(true);
        } else if (ev.getActionMasked() == MotionEvent.ACTION_UP
                || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            disallowParentIntercept(false);
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            disallowParentIntercept(true);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            disallowParentIntercept(false);
        }
        return super.onTouchEvent(ev);
    }

    private void disallowParentIntercept(boolean disallow) {
        ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }
}
