package uk.cpjsmith.ponypaper.custom;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Owner-chain dimming for nested editor dialogs.
 * Run via {@code ./gradlew :custom:testEditorWindowFocus}.
 */
public final class EditorWindowFocusTest {

    private EditorWindowFocusTest() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("EditorWindowFocusTest skipped (headless)");
            return;
        }

        int failures = 0;
        failures += run("chromeWalksPastPopup", EditorWindowFocusTest::testChromeWalksPastPopup);
        failures += run("ownersToDimNested", EditorWindowFocusTest::testOwnersToDimNested);
        failures += run("belongsToEditor", EditorWindowFocusTest::testBelongsToEditor);
        failures += run("syncDimsOwnerChain", EditorWindowFocusTest::testSyncDimsOwnerChain);
        failures += run("popupDoesNotClearDim", EditorWindowFocusTest::testPopupDoesNotClearDim);
        failures += run("nullFocusKeepsDim", EditorWindowFocusTest::testNullFocusKeepsDim);
        if (failures > 0) {
            System.err.println(failures + " editor-window-focus check(s) failed.");
            System.exit(1);
        }
        System.out.println("Editor-window-focus checks passed.");
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

    private static void testChromeWalksPastPopup() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("focus-chrome");
                JDialog pack = new JDialog(frame, "pack");
                JDialog loop = new JDialog(pack, "loop");
                JWindow popup = new JWindow(loop);
                JWindow unowned = new JWindow();
                try {
                    assertSame("popup chrome", loop, EditorWindowFocus.chromeWindow(popup));
                    assertSame("dialog chrome", loop, EditorWindowFocus.chromeWindow(loop));
                    assertSame("frame chrome", frame, EditorWindowFocus.chromeWindow(frame));
                    assertSame("unowned popup", null, EditorWindowFocus.chromeWindow(unowned));
                } finally {
                    unowned.dispose();
                    popup.dispose();
                    loop.dispose();
                    pack.dispose();
                    frame.dispose();
                }
            }
        });
    }

    private static void testOwnersToDimNested() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("focus-owners");
                JDialog pack = new JDialog(frame, "pack");
                JDialog loop = new JDialog(pack, "loop");
                try {
                    List<java.awt.Window> nested = EditorWindowFocus.ownersToDim(loop);
                    assertEq("nested size", 2, nested.size());
                    assertSame("pack first", pack, nested.get(0));
                    assertSame("frame second", frame, nested.get(1));

                    List<java.awt.Window> packOwners = EditorWindowFocus.ownersToDim(pack);
                    assertEq("pack size", 1, packOwners.size());
                    assertSame("frame only", frame, packOwners.get(0));

                    assertEq("frame has no owners", 0, EditorWindowFocus.ownersToDim(frame).size());
                    assertEq("null chrome", 0, EditorWindowFocus.ownersToDim(null).size());
                } finally {
                    loop.dispose();
                    pack.dispose();
                    frame.dispose();
                }
            }
        });
    }

    private static void testBelongsToEditor() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("focus-belongs");
                JFrame other = new JFrame("other");
                JDialog child = new JDialog(frame, "child");
                JDialog stranger = new JDialog(other, "stranger");
                try {
                    assertEq("frame belongs", true, EditorWindowFocus.belongsToEditor(frame, frame));
                    assertEq("child belongs", true, EditorWindowFocus.belongsToEditor(child, frame));
                    assertEq("stranger does not", false,
                            EditorWindowFocus.belongsToEditor(stranger, frame));
                    assertEq("null", false, EditorWindowFocus.belongsToEditor(null, frame));
                } finally {
                    child.dispose();
                    stranger.dispose();
                    frame.dispose();
                    other.dispose();
                }
            }
        });
    }

    private static void testSyncDimsOwnerChain() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("focus-sync");
                JDialog pack = new JDialog(frame, "pack");
                pack.setModal(false);
                JDialog loop = new JDialog(pack, "loop");
                loop.setModal(false);
                try {
                    frame.setSize(200, 120);
                    pack.setSize(180, 100);
                    loop.setSize(160, 80);
                    frame.setVisible(true);
                    pack.setVisible(true);
                    loop.setVisible(true);

                    EditorWindowFocus.sync(frame, loop);
                    assertEq("frame dimmed under loop", true, EditorWindowFocus.isDimmed(frame));
                    assertEq("pack dimmed under loop", true, EditorWindowFocus.isDimmed(pack));
                    assertEq("loop not dimmed", false, EditorWindowFocus.isDimmed(loop));

                    EditorWindowFocus.sync(frame, pack);
                    assertEq("frame dimmed under pack", true, EditorWindowFocus.isDimmed(frame));
                    assertEq("pack clear when focused", false, EditorWindowFocus.isDimmed(pack));
                    assertEq("loop still clear", false, EditorWindowFocus.isDimmed(loop));

                    EditorWindowFocus.sync(frame, frame);
                    assertEq("frame clear when focused", false, EditorWindowFocus.isDimmed(frame));
                    assertEq("pack clear when frame focused", false, EditorWindowFocus.isDimmed(pack));
                } finally {
                    loop.dispose();
                    pack.dispose();
                    frame.dispose();
                }
            }
        });
    }

    private static void testPopupDoesNotClearDim() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("focus-popup");
                JDialog dialog = new JDialog(frame, "dialog");
                dialog.setModal(false);
                JWindow popup = new JWindow(dialog);
                try {
                    frame.setSize(200, 120);
                    dialog.setSize(160, 80);
                    frame.setVisible(true);
                    dialog.setVisible(true);
                    EditorWindowFocus.sync(frame, popup);
                    assertEq("frame dimmed under dialog popup", true,
                            EditorWindowFocus.isDimmed(frame));
                    assertEq("dialog not dimmed for its popup", false,
                            EditorWindowFocus.isDimmed(dialog));
                } finally {
                    popup.dispose();
                    dialog.dispose();
                    frame.dispose();
                }
            }
        });
    }

    private static void testNullFocusKeepsDim() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("focus-null");
                JDialog dialog = new JDialog(frame, "dialog");
                dialog.setModal(false);
                try {
                    frame.setSize(200, 120);
                    dialog.setSize(160, 80);
                    frame.setVisible(true);
                    dialog.setVisible(true);
                    EditorWindowFocus.sync(frame, dialog);
                    assertEq("dimmed first", true, EditorWindowFocus.isDimmed(frame));
                    EditorWindowFocus.sync(frame, null);
                    assertEq("null keeps dim", true, EditorWindowFocus.isDimmed(frame));
                } finally {
                    dialog.dispose();
                    frame.dispose();
                }
            }
        });
    }

    private static void onEdt(Runnable task) throws Exception {
        try {
            SwingUtilities.invokeAndWait(task);
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

    private static void assertEq(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " got " + actual);
        }
    }

    private static void assertEq(String label, boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " got " + actual);
        }
    }

    private static void assertSame(String label, Object expected, Object actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " got " + actual);
        }
    }
}
