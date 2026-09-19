package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Centers children in a {@code settings_content_max_width} column on wide windows.
 * Padding is applied during measure so the first layout is already inset
 * (RecyclerView holders will not stick at full width until recycled).
 */
public class MaxWidthFrameLayout extends FrameLayout {

    private final int maxWidthPx;

    public MaxWidthFrameLayout(Context context) {
        this(context, null);
    }

    public MaxWidthFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaxWidthFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        maxWidthPx = context.getResources()
                .getDimensionPixelSize(R.dimen.settings_content_max_width);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int extra = contentSideInset(MeasureSpec.getSize(widthMeasureSpec), maxWidthPx);
        if (getPaddingStart() != extra || getPaddingEnd() != extra) {
            setPaddingRelative(extra, getPaddingTop(), extra, getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Equal left/right inset that centers a {@code maxWidthPx} column. */
    static int contentSideInset(int widthPx, int maxWidthPx) {
        if (widthPx <= 0 || maxWidthPx <= 0 || widthPx <= maxWidthPx) return 0;
        return (widthPx - maxWidthPx) / 2;
    }
}
