package uk.cpjsmith.ponypaper.custom;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Headful check: after scrolling a FlatLaf file list and changing directory,
 * the file pane must end at the origin even if scroll is disturbed mid-reload.
 */
public final class EditorFileChoosersTest {

    private EditorFileChoosersTest() {
    }

    public static void main(String[] args) throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("EditorFileChoosersTest skipped (headless)");
            return;
        }

        FlatDarkLaf.setup();

        File parent = Files.createTempDirectory("fc-scroll-parent").toFile();
        File other = Files.createTempDirectory("fc-scroll-other").toFile();
        try {
            populateMany(parent);
            populateMany(other);

            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> failure = new AtomicReference<String>(null);

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        JFrame frame = new JFrame("EditorFileChoosersTest");
                        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        JFileChooser fc = EditorFileChoosers.create(parent);
                        frame.add(fc);
                        frame.setSize(480, 360);
                        frame.setLocation(40, 40);
                        frame.setVisible(true);

                        Timer step1 = new Timer(500, null);
                        step1.setRepeats(false);
                        step1.addActionListener(new java.awt.event.ActionListener() {
                            @Override
                            public void actionPerformed(java.awt.event.ActionEvent e) {
                                try {
                                    JScrollPane sp = findFileScroll(fc);
                                    JList<?> list = (JList<?>) sp.getViewport().getView();
                                    if (list.getModel().getSize() < 20) {
                                        failure.set("expected a long file list to scroll");
                                        frame.dispose();
                                        done.countDown();
                                        return;
                                    }
                                    list.ensureIndexIsVisible(list.getModel().getSize() - 1);
                                    Point scrolled = sp.getViewport().getViewPosition();
                                    if (scrolled.x == 0 && scrolled.y == 0) {
                                        failure.set("could not scroll away from origin: " + scrolled);
                                        frame.dispose();
                                        done.countDown();
                                        return;
                                    }

                                    // Disturb scroll after FilePane's own reset, as FlatLaf's
                                    // async directory reload can.
                                    fc.addPropertyChangeListener(
                                            JFileChooser.DIRECTORY_CHANGED_PROPERTY,
                                            new java.beans.PropertyChangeListener() {
                                                @Override
                                                public void propertyChange(java.beans.PropertyChangeEvent evt) {
                                                    SwingUtilities.invokeLater(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            sp.getHorizontalScrollBar().setValue(1200);
                                                            sp.getVerticalScrollBar().setValue(1200);
                                                        }
                                                    });
                                                }
                                            });

                                    fc.setCurrentDirectory(other);

                                    Timer step2 = new Timer(350, null);
                                    step2.setRepeats(false);
                                    step2.addActionListener(new java.awt.event.ActionListener() {
                                        @Override
                                        public void actionPerformed(java.awt.event.ActionEvent e2) {
                                            Point after = findFileScroll(fc).getViewport().getViewPosition();
                                            if (after.x != 0 || after.y != 0) {
                                                failure.set("scroll did not return home: " + after);
                                            }
                                            frame.dispose();
                                            done.countDown();
                                        }
                                    });
                                    step2.start();
                                } catch (RuntimeException ex) {
                                    failure.set(ex.toString());
                                    frame.dispose();
                                    done.countDown();
                                }
                            }
                        });
                        step1.start();
                    } catch (RuntimeException ex) {
                        failure.set(ex.toString());
                        done.countDown();
                    }
                }
            });

            if (!done.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out");
            }
            if (failure.get() != null) {
                throw new AssertionError(failure.get());
            }
            System.out.println("EditorFileChoosersTest OK");
        } finally {
            deleteRecursively(parent);
            deleteRecursively(other);
        }
    }

    private static void populateMany(File dir) throws IOException {
        for (int i = 0; i < 60; i++) {
            Files.createDirectory(new File(dir, String.format("folder_%03d", i)).toPath());
            Files.writeString(new File(dir, String.format("file_%03d.txt", i)).toPath(), "x");
        }
    }

    private static JScrollPane findFileScroll(java.awt.Component c) {
        if (c instanceof JScrollPane) {
            java.awt.Component view = ((JScrollPane) c).getViewport().getView();
            if (view instanceof JList || view instanceof javax.swing.JTable) {
                return (JScrollPane) c;
            }
        }
        if (c instanceof java.awt.Container) {
            java.awt.Component[] children = ((java.awt.Container) c).getComponents();
            for (int i = 0; i < children.length; i++) {
                JScrollPane found = findFileScroll(children[i]);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                deleteRecursively(children[i]);
            }
        }
        file.delete();
    }
}
