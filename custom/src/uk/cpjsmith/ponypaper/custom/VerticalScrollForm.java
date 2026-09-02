package uk.cpjsmith.ponypaper.custom;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/**
 * Form panel that tracks the scroll viewport's width so wide two-column rows
 * shrink instead of clipping when horizontal scrolling is disabled.
 */
final class VerticalScrollForm extends JPanel implements Scrollable {

    /** Vertical {@link BoxLayout} form (action inspector). */
    VerticalScrollForm() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /** Same viewport-width tracking with a caller-chosen layout (effect form). */
    VerticalScrollForm(LayoutManager layout) {
        super(layout);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(visibleRect.height * 9 / 10, 1);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport
                && getPreferredSize().height < ((JViewport) getParent()).getHeight();
    }
}
