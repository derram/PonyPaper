package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * {@link LinearLayout} that honours {@code android:maxWidth}. The framework class
 * ignores that attribute, so a {@code match_parent} session sheet would span
 * landscape and tablet screens and split labels from their switches.
 */
public class MaxWidthLinearLayout extends LinearLayout {

    private int maxWidth = Integer.MAX_VALUE;

    public MaxWidthLinearLayout(Context context) {
        super(context);
    }

    public MaxWidthLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        applyMaxWidth(context, attrs);
    }

    public MaxWidthLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyMaxWidth(context, attrs);
    }

    public MaxWidthLinearLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        applyMaxWidth(context, attrs);
    }

    private void applyMaxWidth(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] { android.R.attr.maxWidth });
        try {
            maxWidth = a.getDimensionPixelSize(0, Integer.MAX_VALUE);
        } finally {
            a.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxWidth < Integer.MAX_VALUE) {
            int size = MeasureSpec.getSize(widthMeasureSpec);
            int mode = MeasureSpec.getMode(widthMeasureSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > maxWidth) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
