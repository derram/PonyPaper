package uk.cpjsmith.ponypaper.custom;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;

/**
 * Typing a valid owner into {@code Sprites from:} used to throw
 * {@code IllegalStateException: Attempt to mutate in notification}: the
 * sprites-from DocumentListener refreshed timings fields, and those listeners
 * detached the alias and wrote back into sprites-from while its document was
 * still notifying.
 */
public final class SpritesFromFieldTest {

    private SpritesFromFieldTest() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SpritesFromFieldTest skipped (headless)");
            return;
        }

        int failures = 0;
        failures += run("aliasNameDoesNotMutateDuringNotification",
                SpritesFromFieldTest::testAliasNameDoesNotMutateDuringNotification);
        if (failures > 0) {
            System.err.println(failures + " sprites-from field check(s) failed.");
            System.exit(1);
        }
        System.out.println("Sprites-from field checks passed.");
    }

    private interface Check {
        void run() throws Exception;
    }

    private static int run(String name, Check check) {
        try {
            check.run();
            System.out.println("ok  " + name);
            return 0;
        } catch (Throwable t) {
            System.err.println("FAIL " + name + ": " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    private static void testAliasNameDoesNotMutateDuringNotification() throws Exception {
        PonyEditor editor = new PonyEditor();
        editor.addAction("stand");
        editor.addAction("trot");
        editor.setActionTimings(0, "left", "10,20");
        editor.setActionTimings(0, "right", "10,20");

        AtomicReference<Throwable> edtError = new AtomicReference<Throwable>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("SpritesFromFieldTest");
                try {
                    PonyEditorGUI gui = PonyEditorGUI.newForTest(frame, editor);
                    JList<?> actions = findActionList(gui);
                    if (actions == null) {
                        throw new AssertionError("action list not found");
                    }
                    actions.setSelectedIndex(1);

                    JTextField spritesFrom = findSpritesFromField(gui);
                    if (spritesFrom == null) {
                        throw new AssertionError("Sprites from field not found");
                    }

                    // Same write path as typing a complete owner name.
                    spritesFrom.setText("stand");

                    if (!"stand".equals(editor.getActionSpritesFrom(1))) {
                        throw new AssertionError("expected alias to stay stand, got \""
                                + editor.getActionSpritesFrom(1) + "\"");
                    }
                    if (!"stand".equals(spritesFrom.getText())) {
                        throw new AssertionError("expected field to keep stand, got \""
                                + spritesFrom.getText() + "\"");
                    }
                } catch (Throwable t) {
                    edtError.set(t);
                } finally {
                    frame.dispose();
                }
            }
        });
        if (edtError.get() != null) {
            if (edtError.get() instanceof Exception) {
                throw (Exception) edtError.get();
            }
            if (edtError.get() instanceof Error) {
                throw (Error) edtError.get();
            }
            throw new RuntimeException(edtError.get());
        }
    }

    private static void onEdt(Runnable r) throws Exception {
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    private static JList<?> findActionList(Component root) {
        if (root instanceof JList) {
            ListModel<?> model = ((JList<?>) root).getModel();
            if (model.getSize() >= 2
                    && "stand".equals(String.valueOf(model.getElementAt(0)))
                    && "trot".equals(String.valueOf(model.getElementAt(1)))) {
                return (JList<?>) root;
            }
        }
        if (root instanceof Container) {
            Component[] children = ((Container) root).getComponents();
            for (int i = 0; i < children.length; i++) {
                JList<?> found = findActionList(children[i]);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JTextField findSpritesFromField(Component root) {
        if (root instanceof JTextField) {
            String tip = ((JTextField) root).getToolTipText();
            if (tip != null && tip.startsWith("Reuse another action's bitmaps")) {
                return (JTextField) root;
            }
        }
        if (root instanceof Container) {
            Component[] children = ((Container) root).getComponents();
            for (int i = 0; i < children.length; i++) {
                JTextField found = findSpritesFromField(children[i]);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
