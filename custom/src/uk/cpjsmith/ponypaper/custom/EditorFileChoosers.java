package uk.cpjsmith.ponypaper.custom;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Factory helpers for editor {@link JFileChooser}s.
 * <p>
 * FlatLaf (Metal-based) file choosers keep the file list/table scroll offset
 * across directory changes when the async directory model finishes loading after
 * {@code FilePane}'s own scroll-to-index-0. That leaves the list mid-folder
 * instead of jumping back to the start. These helpers reset the file pane to
 * the origin after every directory change.
 */
public final class EditorFileChoosers {

    private static final String SCROLL_HOME_INSTALLED = "ponypaper.fileChooser.scrollHomeInstalled";

    private EditorFileChoosers() {
    }

    /** New chooser starting in {@code currentDirectory}, with scroll-home fix applied. */
    public static JFileChooser create(File currentDirectory) {
        JFileChooser fc = new JFileChooser(currentDirectory);
        installDirectoryScrollHome(fc);
        return fc;
    }

    /** New chooser starting in {@code currentDirectoryPath}, with scroll-home fix applied. */
    public static JFileChooser create(String currentDirectoryPath) {
        JFileChooser fc = new JFileChooser(currentDirectoryPath);
        installDirectoryScrollHome(fc);
        return fc;
    }

    /**
     * Ensures the file list/details view scrolls to the origin whenever the
     * chooser changes directory. Safe to call more than once on the same instance.
     */
    public static void installDirectoryScrollHome(JFileChooser fc) {
        if (Boolean.TRUE.equals(fc.getClientProperty(SCROLL_HOME_INSTALLED))) {
            return;
        }
        fc.putClientProperty(SCROLL_HOME_INSTALLED, Boolean.TRUE);
        fc.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                scrollFilePaneHome(fc);
                // FilePane scrolls before BasicDirectoryModel finishes reloading;
                // re-apply after the model swap lands on the EDT.
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        scrollFilePaneHome(fc);
                    }
                });
                Timer later = new Timer(100, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        scrollFilePaneHome(fc);
                    }
                });
                later.setRepeats(false);
                later.start();
            }
        });
    }

    static void scrollFilePaneHome(Component root) {
        List<JScrollPane> panes = new ArrayList<JScrollPane>();
        collectFileScrollPanes(root, panes);
        for (JScrollPane sp : panes) {
            JScrollBar horizontal = sp.getHorizontalScrollBar();
            JScrollBar vertical = sp.getVerticalScrollBar();
            if (horizontal != null) {
                horizontal.setValue(0);
            }
            if (vertical != null) {
                vertical.setValue(0);
            }
            sp.getViewport().setViewPosition(new Point(0, 0));

            Component view = sp.getViewport().getView();
            if (view instanceof JList) {
                JList<?> list = (JList<?>) view;
                if (list.getModel().getSize() > 0) {
                    list.ensureIndexIsVisible(0);
                }
            } else if (view instanceof JTable) {
                JTable table = (JTable) view;
                if (table.getRowCount() > 0) {
                    table.scrollRectToVisible(table.getCellRect(0, 0, true));
                }
            }
        }
    }

    private static void collectFileScrollPanes(Component c, List<JScrollPane> out) {
        if (c instanceof JScrollPane) {
            Component view = ((JScrollPane) c).getViewport().getView();
            if (view instanceof JList || view instanceof JTable) {
                out.add((JScrollPane) c);
            }
        }
        if (c instanceof Container) {
            Component[] children = ((Container) c).getComponents();
            for (int i = 0; i < children.length; i++) {
                collectFileScrollPanes(children[i], out);
            }
        }
    }
}
