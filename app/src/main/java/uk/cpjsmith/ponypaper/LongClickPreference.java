package uk.cpjsmith.ponypaper;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * Preference that supports a long-press action in addition to the normal click.
 * Used for Export library (tap = full backup, long-press = choose categories).
 */
public class LongClickPreference extends Preference {

    private View.OnLongClickListener longClickListener;

    public LongClickPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LongClickPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LongClickPreference(Context context) {
        super(context);
    }

    public void setOnLongClickListener(View.OnLongClickListener listener) {
        longClickListener = listener;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setOnLongClickListener(longClickListener);
        // Keep accessibility long-click in sync with the row.
        holder.itemView.setLongClickable(longClickListener != null);
    }
}
