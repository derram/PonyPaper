package uk.cpjsmith.ponypaper.custom;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Modal dialog: pick a spritesheet frame, then place feet anchors on a zoomed
 * view with an optional pixel grid. Returns {@code null} on cancel.
 */
public final class AnchorPickerDialog extends JDialog {

    /**
     * Result of a successful Apply. Either or both coordinates may be
     * {@link Float#NaN} when the user cleared anchors (use defaults).
     */
    public static final class Result {
        public final float anchorX;
        public final float anchorY;

        public Result(float anchorX, float anchorY) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }

        public boolean isCleared() {
            return Float.isNaN(anchorX) && Float.isNaN(anchorY);
        }
    }

    private final SpriteSheetPreview preview;
    private final JLabel statusLabel;
    private final JButton backButton;
    private final JButton applyButton;
    private Result result;
    private boolean applied;
    /** True after the user has entered placement at least once this session. */
    private boolean frameChosen;

    private AnchorPickerDialog(Component parent, Image image, int frameCount,
            float initialAnchorX, float initialAnchorY) {
        super(SwingUtilities.getWindowAncestor(parent), "Pick Anchors", ModalityType.APPLICATION_MODAL);

        preview = new SpriteSheetPreview(image, frameCount, SpriteSheetPreview.Mode.SELECT_FRAME);
        preview.setAnchors(initialAnchorX, initialAnchorY);
        preview.setAutoPlaceZoom(360);
        preview.setShowGrid(true);

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        final JCheckBox gridCheck = new JCheckBox("Show pixel grid", true);
        gridCheck.setToolTipText("Low-opacity 1px grid on the zoomed frame (major lines every 8px).");
        gridCheck.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                preview.setShowGrid(gridCheck.isSelected());
            }
        });

        backButton = new JButton("Back");
        backButton.setToolTipText("Return to frame selection.");
        backButton.setEnabled(false);
        backButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                goSelectFrame();
            }
        });

        JButton clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear anchors (use frame centre / bottom defaults).");
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                preview.clearAnchors();
            }
        });

        applyButton = new JButton("Apply");
        applyButton.setEnabled(false);
        applyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                applied = true;
                result = new Result(preview.getAnchorX(), preview.getAnchorY());
                dispose();
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                applied = false;
                result = null;
                dispose();
            }
        });

        preview.setListener(new SpriteSheetPreview.Listener() {
            public void onStatusChanged(String status) {
                statusLabel.setText(status != null ? status : " ");
            }

            public void onFrameSelected(int frameIndex) {
                goPlaceAnchor(frameIndex);
            }

            public void onAnchorChanged(float anchorX, float anchorY) {
                // Apply stays enabled once a frame was chosen (including Clear → defaults).
                applyButton.setEnabled(frameChosen);
            }
        });

        JScrollPane scroll = new JScrollPane(preview);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(gridCheck);
        controls.add(backButton);
        controls.add(clearButton);
        south.add(controls, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttons.add(cancelButton);
        buttons.add(applyButton);
        south.add(buttons, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(applyButton);
        getRootPane().registerKeyboardAction(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        applied = false;
                        result = null;
                        dispose();
                    }
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                applied = false;
                result = null;
            }
        });

        // Single-frame sheets: skip straight to placement.
        if (frameCount == 1) {
            goPlaceAnchor(0);
        } else {
            // Refresh status after listener is attached.
            statusLabel.setText(preview.buildStatusText());
        }

        pack();
        // Cap initial size so huge sheets scroll instead of overflowing the screen.
        java.awt.Dimension size = getSize();
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = Math.max(480, (int) (screen.width * 0.9));
        int maxH = Math.max(360, (int) (screen.height * 0.85));
        if (size.width > maxW || size.height > maxH) {
            setSize(Math.min(size.width, maxW), Math.min(size.height, maxH));
        }
        setLocationRelativeTo(parent);
    }

    private void goPlaceAnchor(int frameIndex) {
        frameChosen = true;
        preview.setSelectedFrame(frameIndex);
        preview.setMode(SpriteSheetPreview.Mode.PLACE_ANCHOR);
        backButton.setEnabled(preview.getFrameCount() > 1);
        applyButton.setEnabled(true);
        preview.revalidate();
        packIfSmallerSheet();
        preview.requestFocusInWindow();
    }

    private void goSelectFrame() {
        preview.setMode(SpriteSheetPreview.Mode.SELECT_FRAME);
        backButton.setEnabled(false);
        // Keep Apply available after the first placement visit (incl. Clear → defaults).
        applyButton.setEnabled(frameChosen);
        preview.revalidate();
        packIfSmallerSheet();
    }

    private void packIfSmallerSheet() {
        // Re-pack when switching modes so zoomed frame gets room; keep user resize if larger.
        java.awt.Dimension pref = getPreferredSize();
        java.awt.Dimension cur = getSize();
        if (pref.width > cur.width || pref.height > cur.height) {
            pack();
            java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            int maxW = Math.max(480, (int) (screen.width * 0.9));
            int maxH = Math.max(360, (int) (screen.height * 0.85));
            java.awt.Dimension size = getSize();
            if (size.width > maxW || size.height > maxH) {
                setSize(Math.min(size.width, maxW), Math.min(size.height, maxH));
            }
        } else {
            revalidate();
            repaint();
        }
    }

    /**
     * Opens the picker. Returns a {@link Result} if the user applied, or
     * {@code null} if cancelled.
     *
     * @param parent           parent component for modality / positioning
     * @param image            decoded spritesheet
     * @param frameCount       number of frames (from timings length)
     * @param initialAnchorX   current anchor X, or NaN if unset
     * @param initialAnchorY   current anchor Y, or NaN if unset
     */
    public static Result showDialog(Component parent, Image image, int frameCount,
            float initialAnchorX, float initialAnchorY) {
        if (image == null) {
            throw new IllegalArgumentException("image");
        }
        if (frameCount < 1) {
            throw new IllegalArgumentException("frameCount must be >= 1");
        }
        AnchorPickerDialog dialog = new AnchorPickerDialog(
                parent, image, frameCount, initialAnchorX, initialAnchorY);
        dialog.setVisible(true);
        return dialog.applied ? dialog.result : null;
    }
}
