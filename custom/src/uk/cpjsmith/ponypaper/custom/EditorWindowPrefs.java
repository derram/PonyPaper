package uk.cpjsmith.ponypaper.custom;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JFrame;

/**
 * Persists the main editor window's normal bounds and maximized state across runs
 * via {@link Preferences}, matching {@link EditorTheme}'s storage style.
 */
public final class EditorWindowPrefs {

    private static final String PREF_SAVED = "windowGeometrySaved";
    private static final String PREF_X = "windowX";
    private static final String PREF_Y = "windowY";
    private static final String PREF_WIDTH = "windowWidth";
    private static final String PREF_HEIGHT = "windowHeight";
    private static final String PREF_MAXIMIZED = "windowMaximized";

    private EditorWindowPrefs() {
    }

    /** Whether a previous session stored geometry. */
    public static boolean hasSavedGeometry() {
        return prefs().getBoolean(PREF_SAVED, false);
    }

    /**
     * Restores saved bounds (and maximized state) onto {@code frame}.
     * Call after {@link JFrame#pack()} / preferred size is set. Returns
     * {@code false} when nothing was stored so the caller can center the window.
     */
    public static boolean apply(JFrame frame) {
        Preferences node = prefs();
        if (!node.getBoolean(PREF_SAVED, false)) {
            return false;
        }

        Dimension min = frame.getMinimumSize();
        int minW = Math.max(1, min.width);
        int minH = Math.max(1, min.height);

        int width = Math.max(minW, node.getInt(PREF_WIDTH, minW));
        int height = Math.max(minH, node.getInt(PREF_HEIGHT, minH));
        int x = node.getInt(PREF_X, 0);
        int y = node.getInt(PREF_Y, 0);
        boolean maximized = node.getBoolean(PREF_MAXIMIZED, false);

        Rectangle bounds = clampToVisibleScreens(new Rectangle(x, y, width, height), minW, minH);
        frame.setBounds(bounds);
        if (maximized) {
            frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
        return true;
    }

    /**
     * Writes current geometry. When maximized or iconified, keeps the last normal
     * bounds and only updates the maximized flag.
     */
    public static void save(JFrame frame) {
        Preferences node = prefs();
        int state = frame.getExtendedState();
        boolean maximized = (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        boolean iconified = (state & Frame.ICONIFIED) != 0;

        if (!maximized && !iconified) {
            Rectangle b = frame.getBounds();
            node.putInt(PREF_X, b.x);
            node.putInt(PREF_Y, b.y);
            node.putInt(PREF_WIDTH, Math.max(1, b.width));
            node.putInt(PREF_HEIGHT, Math.max(1, b.height));
        }
        node.putBoolean(PREF_MAXIMIZED, maximized);
        node.putBoolean(PREF_SAVED, true);
        try {
            node.flush();
        } catch (BackingStoreException ignored) {
            // Next run falls back to defaults if the store failed.
        }
    }

    /**
     * Tracks normal (non-maximized) bounds while the user resizes/moves the
     * window. Call {@link #save(JFrame)} from the frame's close handler to flush.
     */
    public static void installPersistence(final JFrame frame) {
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                captureNormalBounds(frame);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                captureNormalBounds(frame);
            }
        });
        frame.addWindowStateListener(new WindowAdapter() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                // When returning to a normal state, refresh stored bounds.
                int newState = e.getNewState();
                if ((newState & Frame.MAXIMIZED_BOTH) != Frame.MAXIMIZED_BOTH
                        && (newState & Frame.ICONIFIED) == 0) {
                    captureNormalBounds(frame);
                }
            }
        });
    }

    /** Updates stored normal bounds without flushing (flushed on {@link #save}). */
    private static void captureNormalBounds(JFrame frame) {
        int state = frame.getExtendedState();
        if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            return;
        }
        if ((state & Frame.ICONIFIED) != 0) {
            return;
        }
        Rectangle b = frame.getBounds();
        if (b.width <= 0 || b.height <= 0) {
            return;
        }
        Preferences node = prefs();
        node.putInt(PREF_X, b.x);
        node.putInt(PREF_Y, b.y);
        node.putInt(PREF_WIDTH, b.width);
        node.putInt(PREF_HEIGHT, b.height);
        node.putBoolean(PREF_SAVED, true);
    }

    /**
     * Keeps the window usable if the saved monitor is gone or the size is larger
     * than the available area.
     */
    private static Rectangle clampToVisibleScreens(Rectangle desired, int minW, int minH) {
        Rectangle virtual = virtualScreenBounds();
        int width = Math.min(Math.max(desired.width, minW), Math.max(minW, virtual.width));
        int height = Math.min(Math.max(desired.height, minH), Math.max(minH, virtual.height));

        int x = desired.x;
        int y = desired.y;
        // Keep at least a 64px strip of the title bar area on-screen.
        int margin = 64;
        if (x + width < virtual.x + margin) {
            x = virtual.x;
        }
        if (y + margin < virtual.y) {
            y = virtual.y;
        }
        if (x > virtual.x + virtual.width - margin) {
            x = virtual.x + Math.max(0, virtual.width - width);
        }
        if (y > virtual.y + virtual.height - margin) {
            y = virtual.y + Math.max(0, virtual.height - height);
        }

        Rectangle clamped = new Rectangle(x, y, width, height);
        if (!intersectsAnyScreen(clamped)) {
            Rectangle primary = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            width = Math.min(width, Math.max(minW, primary.width));
            height = Math.min(height, Math.max(minH, primary.height));
            x = primary.x + Math.max(0, (primary.width - width) / 2);
            y = primary.y + Math.max(0, (primary.height - height) / 2);
            clamped = new Rectangle(x, y, width, height);
        }
        return clamped;
    }

    private static Rectangle virtualScreenBounds() {
        Rectangle union = null;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration config = device.getDefaultConfiguration();
            Rectangle b = config.getBounds();
            if (union == null) {
                union = new Rectangle(b);
            } else {
                union = union.union(b);
            }
        }
        if (union == null) {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        }
        return union;
    }

    private static boolean intersectsAnyScreen(Rectangle bounds) {
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (bounds.intersects(device.getDefaultConfiguration().getBounds())) {
                return true;
            }
        }
        return false;
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(EditorTheme.class);
    }
}
