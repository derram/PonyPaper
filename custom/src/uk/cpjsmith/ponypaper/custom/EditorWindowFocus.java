package uk.cpjsmith.ponypaper.custom;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

/**
 * Dims editor owner windows while a child {@link JDialog} holds focus.
 * <p>
 * Modal dialogs already swallow clicks on the parent; on Linux the maximized
 * inspector still looks fully live. A glass-pane overlay using
 * {@link EditorTheme#DIM_OVERLAY} marks every {@link JFrame}/{@link JDialog}
 * in the focused dialog's owner chain (so a loop preview dims both the pack
 * dialog and the main window). Combo popups and tooltips are {@code JWindow}s
 * and are walked through to their owning chrome window so they do not clear
 * the dim.
 */
public final class EditorWindowFocus {

    private static final String INSTALLED = "ponypaper.windowFocus";
    private static final String PREV_GLASS = "ponypaper.prevGlass";
    private static final String FOCUS_LISTENER = "ponypaper.focusListener";

    private EditorWindowFocus() {
    }

    /**
     * Listens for {@code focusedWindow} changes on {@code frame} until it is
     * disposed. Safe to call more than once on the same frame.
     */
    public static void install(final JFrame frame) {
        if (frame == null) {
            return;
        }
        JRootPane root = frame.getRootPane();
        if (Boolean.TRUE.equals(root.getClientProperty(INSTALLED))) {
            return;
        }
        root.putClientProperty(INSTALLED, Boolean.TRUE);

        final KeyboardFocusManager manager =
                KeyboardFocusManager.getCurrentKeyboardFocusManager();
        PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                final Window focused = (Window) evt.getNewValue();
                Runnable task = new Runnable() {
                    @Override
                    public void run() {
                        sync(frame, focused);
                    }
                };
                if (SwingUtilities.isEventDispatchThread()) {
                    task.run();
                } else {
                    SwingUtilities.invokeLater(task);
                }
            }
        };
        root.putClientProperty(FOCUS_LISTENER, listener);
        manager.addPropertyChangeListener("focusedWindow", listener);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                manager.removePropertyChangeListener("focusedWindow", listener);
                root.putClientProperty(FOCUS_LISTENER, null);
                root.putClientProperty(INSTALLED, null);
                sync(frame, frame);
            }
        });
    }

    /**
     * Walks past {@code JWindow} popups to the {@link JFrame} or {@link JDialog}
     * that should be treated as focused chrome. {@code null} if {@code focused}
     * is {@code null} or has no such ancestor (alt-tab / empty owner).
     */
    static Window chromeWindow(Window focused) {
        Window w = focused;
        while (w != null && !isChrome(w)) {
            w = w.getOwner();
        }
        return w;
    }

    /** Owner-chain {@link JFrame}/{@link JDialog} ancestors of {@code chrome}. */
    static List<Window> ownersToDim(Window chrome) {
        List<Window> out = new ArrayList<Window>();
        if (chrome == null) {
            return out;
        }
        Window owner = chrome.getOwner();
        while (owner != null) {
            if (isChrome(owner)) {
                out.add(owner);
            }
            owner = owner.getOwner();
        }
        return out;
    }

    static boolean isOwnerAncestor(Window ancestor, Window descendant) {
        if (ancestor == null || descendant == null) {
            return false;
        }
        Window owner = descendant.getOwner();
        while (owner != null) {
            if (owner == ancestor) {
                return true;
            }
            owner = owner.getOwner();
        }
        return false;
    }

    static boolean belongsToEditor(Window chrome, JFrame editorFrame) {
        if (chrome == null || editorFrame == null) {
            return false;
        }
        Window w = chrome;
        while (w != null) {
            if (w == editorFrame) {
                return true;
            }
            w = w.getOwner();
        }
        return false;
    }

    static boolean isDimmed(Window window) {
        if (!(window instanceof RootPaneContainer)) {
            return false;
        }
        Component glass = ((RootPaneContainer) window).getGlassPane();
        return glass instanceof DimPane && glass.isVisible();
    }

    /**
     * Applies or clears dim overlays for {@code editorFrame} and its owned
     * chrome windows. A {@code null} {@code focused} window is ignored so
     * alt-tab does not flash the overlay off.
     */
    static void sync(JFrame editorFrame, Window focused) {
        if (editorFrame == null) {
            return;
        }
        Window chrome = chromeWindow(focused);
        if (chrome == null || !belongsToEditor(chrome, editorFrame)) {
            return;
        }
        applyTree(editorFrame, chrome);
    }

    private static void applyTree(Window window, Window chrome) {
        if (isChrome(window) && window instanceof RootPaneContainer) {
            boolean dim = window.isShowing()
                    && window != chrome
                    && isOwnerAncestor(window, chrome);
            setDimmed((RootPaneContainer) window, dim);
        }
        Window[] owned = window.getOwnedWindows();
        for (int i = 0; i < owned.length; i++) {
            applyTree(owned[i], chrome);
        }
    }

    private static boolean isChrome(Window window) {
        return window instanceof JFrame || window instanceof JDialog;
    }

    private static void setDimmed(RootPaneContainer container, boolean dim) {
        JRootPane root = container.getRootPane();
        if (root == null) {
            return;
        }
        Component glass = root.getGlassPane();
        if (dim) {
            if (glass instanceof DimPane) {
                if (!glass.isVisible()) {
                    glass.setVisible(true);
                }
                return;
            }
            root.putClientProperty(PREV_GLASS, glass);
            DimPane pane = new DimPane();
            root.setGlassPane(pane);
            pane.setVisible(true);
        } else if (glass instanceof DimPane) {
            glass.setVisible(false);
            Object previous = root.getClientProperty(PREV_GLASS);
            root.putClientProperty(PREV_GLASS, null);
            if (previous instanceof Component && previous != glass) {
                root.setGlassPane((Component) previous);
            }
        }
    }

    /**
     * Translucent overlay. Clicks try to raise the focused dialog; application
     * modality usually consumes parent events first, so this is a fallback for
     * nested / non-blocked owners.
     */
    private static final class DimPane extends JComponent {
        DimPane() {
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Window focused = KeyboardFocusManager
                            .getCurrentKeyboardFocusManager().getFocusedWindow();
                    if (focused != null) {
                        focused.toFront();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(EditorTheme.DIM_OVERLAY);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
