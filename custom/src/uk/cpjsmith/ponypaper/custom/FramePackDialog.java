package uk.cpjsmith.ponypaper.custom;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Modal dialog: review imported frames (PNG stills or coalesced GIF frames),
 * choose a pack scale (200% / 150%…6.25%, or fit-to-built-in), rearrange
 * playback order (move, reverse, clone, or delete), set per-frame lift (or
 * apply one value to all frames), and pack. Frames taller than built-in open
 * on Fit. Sheets over {@link ImageImport#SHEET_PIXEL_BUDGET} defer the strip
 * preview and require a Pack confirmation. Lift {@code 0} is the usual
 * bottom-centre alignment; positive lift bakes a hop into a taller cell. Play
 * loop opens a feet-locked loop of the current draft before Pack. Returns
 * {@code null} on cancel.
 */
public final class FramePackDialog extends JDialog {

    private static final int MAX_LIFT = 512;
    private static final int THUMB_H = 32;

    /**
     * User choices after Pack. Lift values are in <em>output</em> pixels
     * (after scale) and follow playback order. {@link #order} is a playback
     * sequence of source indices ({@code 0..sourceCount-1}); duplicates
     * (clone) and omissions (delete) are allowed, so its length is the packed
     * frame count. {@link #scaleNumerator}/{@link #scaleDivisor} is the
     * resolved ratio actually applied (never a "fit" sentinel).
     */
    public static final class Result {
        public final int[] lifts;
        public final int scaleNumerator;
        public final int scaleDivisor;
        public final int[] order;

        Result(int[] lifts, int scaleNumerator, int scaleDivisor, int[] order) {
            this.lifts = lifts;
            this.scaleNumerator = scaleNumerator;
            this.scaleDivisor = scaleDivisor;
            this.order = order;
        }

        /** Copies the resolved pack scale onto {@code options} (clears Fit). */
        public void copyScaleTo(ImageImport.PackOptions options) {
            options.scaleNumerator = scaleNumerator;
            options.scaleDivisor = scaleDivisor;
            options.scaleFitBuiltin = false;
        }
    }

    private static final class ScaleItem {
        final int numerator;
        /** Resolved divisor; Fit stores the shrink it would apply. */
        final int divisor;
        final boolean fit;
        String label;

        ScaleItem(int divisor, String label) {
            this(ImageImport.SCALE_NUMERATOR_NATIVE, divisor, label);
        }

        ScaleItem(int numerator, int divisor, String label) {
            this.numerator = numerator;
            this.divisor = divisor;
            this.fit = false;
            this.label = label;
        }

        ScaleItem(boolean fit, int resolvedDivisor, String label) {
            this.numerator = ImageImport.SCALE_NUMERATOR_NATIVE;
            this.divisor = resolvedDivisor;
            this.fit = fit;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final List<BufferedImage> frames;
    private final List<String> names;
    /** Lift of the frame at each playback slot (travels with the frame). */
    private int[] lifts;
    /** Source index at each playback slot (duplicates and omissions allowed). */
    private int[] order;
    /**
     * Last lift written for each source frame. Reset order restores the
     * import sequence using these values.
     */
    private final int[] sourceLifts;
    /**
     * Optional per-source-frame timings (centiseconds), e.g. GIF delays. When
     * length matches {@link #frames}, Play loop gathers them with
     * {@link #order}; otherwise default timing is used.
     */
    private final int[] sourceTimingsCs;
    private final String notes;
    private final JLabel headerLabel;
    private final JLabel warningLabel;
    private final JList<Integer> frameList;
    private final CellPreview cellPreview;
    private final StripPreview stripPreview;
    private final JSpinner liftSpinner;
    private final JSpinner hopPeakSpinner;
    private final SpinnerNumberModel liftModel;
    private final JComboBox<ScaleItem> scaleCombo;
    private final ScaleItem fitScaleItem;
    private final int fitScaleDivisor;
    private final JButton moveUpButton;
    private final JButton moveDownButton;
    private final JButton reverseButton;
    private final JButton resetOrderButton;
    private final JButton cloneButton;
    private final JButton deleteButton;
    private final JButton hopButton;
    private int scaleNumerator;
    private int scaleDivisor;
    private List<BufferedImage> scaledFrames;
    private boolean updatingSpinner;
    private boolean packed;
    private Result result;

    private FramePackDialog(Component parent, String title,
            List<String> frameNames, List<BufferedImage> frames, String notes,
            int defaultScaleDivisor, int[] sourceTimingsCs) {
        super(SwingUtilities.getWindowAncestor(parent),
                title != null ? title : "Import Frames",
                ModalityType.APPLICATION_MODAL);
        this.frames = frames;
        this.notes = notes != null ? notes : "";
        this.sourceTimingsCs = sourceTimingsCs != null && sourceTimingsCs.length == frames.size()
                ? sourceTimingsCs.clone()
                : null;
        this.names = new ArrayList<String>(frames.size());
        this.lifts = new int[frames.size()];
        this.sourceLifts = new int[frames.size()];
        this.order = new int[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            this.order[i] = i;
        }
        int initialDivisor = ImageImport.SCALE_DIVISOR_NATIVE;
        boolean preferFit = false;
        try {
            preferFit = ImageImport.shouldDefaultToFitBuiltin(frames, defaultScaleDivisor);
            initialDivisor = ImageImport.defaultScaleDivisorForFrames(
                    frames, defaultScaleDivisor);
        } catch (IOException e) {
            try {
                initialDivisor = ImageImport.resolveRequestedScaleDivisor(defaultScaleDivisor);
            } catch (IOException ignored) {
                initialDivisor = ImageImport.SCALE_DIVISOR_NATIVE;
            }
        }
        this.scaleNumerator = ImageImport.SCALE_NUMERATOR_NATIVE;
        this.scaleDivisor = initialDivisor;

        int computedFitDivisor = ImageImport.SCALE_DIVISOR_NATIVE;
        String fitLabel = "Fit to built-in";
        try {
            int maxW = ImageImport.maxFrameWidth(frames);
            int maxH = ImageImport.maxFrameHeight(frames);
            computedFitDivisor = ImageImport.fitBuiltinScaleDivisor(maxH);
            int scaledW = ImageImport.scaleDimension(maxW, computedFitDivisor);
            int scaledH = ImageImport.scaleDimension(maxH, computedFitDivisor);
            fitLabel = String.format(
                    "Fit to built-in — %s → %d×%d",
                    ImageImport.formatScaleDivisorLabel(computedFitDivisor),
                    scaledW,
                    scaledH);
        } catch (IOException ignored) {
            // Keep a safe fallback label; packing will surface real errors.
        }
        this.fitScaleDivisor = computedFitDivisor;
        this.fitScaleItem = new ScaleItem(true, computedFitDivisor, fitLabel);
        final boolean selectFitByDefault = preferFit;

        for (int i = 0; i < frames.size(); i++) {
            if (frameNames != null && i < frameNames.size() && frameNames.get(i) != null) {
                names.add(frameNames.get(i));
            } else {
                names.add("frame " + (i + 1));
            }
        }

        headerLabel = new JLabel(" ");
        headerLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        warningLabel = new JLabel(" ");
        warningLabel.setForeground(EditorTheme.WARNING);
        warningLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));

        DefaultListModel<Integer> model = new DefaultListModel<Integer>();
        for (int i = 0; i < frames.size(); i++) {
            model.addElement(Integer.valueOf(i));
        }
        frameList = new JList<Integer>(model);
        frameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        frameList.setCellRenderer(new FrameRenderer());
        frameList.setVisibleRowCount(8);

        cellPreview = new CellPreview();
        stripPreview = new StripPreview();

        liftModel = new SpinnerNumberModel(0, 0, MAX_LIFT, 1);
        liftSpinner = new JSpinner(liftModel);
        liftSpinner.setToolTipText("Pixels of air under this frame (0 = feet on the ground line).");
        hopPeakSpinner = new JSpinner(new SpinnerNumberModel(16, 1, MAX_LIFT, 1));
        hopPeakSpinner.setToolTipText("Peak height in pixels for the hop-curve preset.");

        ScaleItem[] scaleItems;
        try {
            scaleItems = new ScaleItem[] {
                new ScaleItem(ImageImport.SCALE_NUMERATOR_DOUBLE,
                        ImageImport.SCALE_DIVISOR_NATIVE,
                        ImageImport.formatScaleLabel(
                                ImageImport.SCALE_NUMERATOR_DOUBLE,
                                ImageImport.SCALE_DIVISOR_NATIVE)),
                new ScaleItem(ImageImport.SCALE_NUMERATOR_THREE_HALVES,
                        ImageImport.SCALE_DIVISOR_THREE_HALVES,
                        ImageImport.formatScaleLabel(
                                ImageImport.SCALE_NUMERATOR_THREE_HALVES,
                                ImageImport.SCALE_DIVISOR_THREE_HALVES)),
                new ScaleItem(ImageImport.SCALE_DIVISOR_NATIVE,
                        ImageImport.formatScaleDivisorLabel(ImageImport.SCALE_DIVISOR_NATIVE)),
                new ScaleItem(ImageImport.SCALE_DIVISOR_HALF,
                        ImageImport.formatScaleDivisorLabel(ImageImport.SCALE_DIVISOR_HALF)),
                new ScaleItem(ImageImport.SCALE_DIVISOR_QUARTER,
                        ImageImport.formatScaleDivisorLabel(ImageImport.SCALE_DIVISOR_QUARTER)),
                new ScaleItem(ImageImport.SCALE_DIVISOR_EIGHTH,
                        ImageImport.formatScaleDivisorLabel(ImageImport.SCALE_DIVISOR_EIGHTH)),
                new ScaleItem(ImageImport.SCALE_DIVISOR_SIXTEENTH,
                        ImageImport.formatScaleDivisorLabel(ImageImport.SCALE_DIVISOR_SIXTEENTH)),
                fitScaleItem,
            };
        } catch (IOException e) {
            scaleItems = new ScaleItem[] {
                new ScaleItem(ImageImport.SCALE_NUMERATOR_DOUBLE,
                        ImageImport.SCALE_DIVISOR_NATIVE, "200% (×2)"),
                new ScaleItem(ImageImport.SCALE_NUMERATOR_THREE_HALVES,
                        ImageImport.SCALE_DIVISOR_THREE_HALVES, "150% (×1.5)"),
                new ScaleItem(ImageImport.SCALE_DIVISOR_NATIVE, "100% (native)"),
                fitScaleItem,
            };
        }
        scaleCombo = new JComboBox<ScaleItem>(scaleItems);
        selectScaleItem(ImageImport.SCALE_NUMERATOR_NATIVE, initialDivisor, selectFitByDefault);
        scaleCombo.setToolTipText(
                "Nearest-neighbour scale before packing. "
                        + "200% pixel-doubles undersized art; 150% is ×1.5. "
                        + "Prefer ÷2 / ÷4 / ÷8 / ÷16 to shrink. "
                        + "Fit picks the largest shrink whose tallest frame is ≤ "
                        + ImageImport.LARGE_CELL_HEIGHT_PX + "px (never 150% or 200%). "
                        + "Oversized imports open on Fit automatically. "
                        + "Lifts are in output pixels after scale.");
        scaleCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ScaleItem item = (ScaleItem) scaleCombo.getSelectedItem();
                int nextNum = item != null
                        ? (item.fit ? ImageImport.SCALE_NUMERATOR_NATIVE : item.numerator)
                        : ImageImport.SCALE_NUMERATOR_NATIVE;
                int nextDiv = item != null
                        ? (item.fit ? fitScaleDivisor : item.divisor)
                        : ImageImport.SCALE_DIVISOR_NATIVE;
                if (nextNum == scaleNumerator && nextDiv == scaleDivisor) {
                    return;
                }
                scaleNumerator = nextNum;
                scaleDivisor = nextDiv;
                scaledFrames = null;
                refreshAll();
            }
        });

        liftSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingSpinner) {
                    return;
                }
                int index = selectedIndex();
                if (index < 0) {
                    return;
                }
                int value = ((Number) liftSpinner.getValue()).intValue();
                setLift(index, value);
            }
        });

        frameList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    syncSelection();
                }
            }
        });

        JButton applyAllButton = new JButton("Apply to all");
        applyAllButton.setToolTipText(
                "Set every frame to this lift (same value on all cells).");
        applyAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int value = ((Number) liftSpinner.getValue()).intValue();
                setAllLifts(value);
            }
        });

        JButton resetButton = new JButton("Reset lifts");
        resetButton.setToolTipText("Set every frame back to 0 (bottom-aligned).");
        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setAllLifts(0);
            }
        });

        hopButton = new JButton("Apply hop");
        hopButton.setToolTipText("Parabola: 0 at both ends, peak in the middle. Baked into the sheet.");
        hopButton.setEnabled(frames.size() >= 3);
        hopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int peak = ((Number) hopPeakSpinner.getValue()).intValue();
                int[] curve = ImageImport.hopCurve(order.length, peak);
                System.arraycopy(curve, 0, lifts, 0, lifts.length);
                writeLiftsToSources();
                refreshAll();
            }
        });

        JButton playLoopButton = new JButton("Play loop…");
        playLoopButton.setToolTipText(
                "Animate the current order, scale, and lifts on a loop "
                        + "(feet locked to the ground line). Does not Pack.");
        playLoopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openLoopPreview();
            }
        });

        JButton packButton = new JButton("Pack");
        packButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!confirmPackIfHuge()) {
                    return;
                }
                packed = true;
                result = new Result(lifts.clone(), scaleNumerator, scaleDivisor, order.clone());
                dispose();
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                packed = false;
                result = null;
                dispose();
            }
        });

        moveUpButton = new JButton("Move up");
        moveUpButton.setToolTipText("Play this frame earlier (Alt+Up).");
        moveUpButton.setEnabled(false);
        moveUpButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelected(-1);
            }
        });
        moveDownButton = new JButton("Move down");
        moveDownButton.setToolTipText("Play this frame later (Alt+Down).");
        moveDownButton.setEnabled(false);
        moveDownButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelected(1);
            }
        });
        reverseButton = new JButton("Reverse");
        reverseButton.setToolTipText("Play the clip backwards. Lifts stay on their frames.");
        reverseButton.setEnabled(frames.size() >= 2);
        reverseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reverseOrder();
            }
        });
        resetOrderButton = new JButton("Reset order");
        resetOrderButton.setToolTipText(
                "Restore the imported / natural-sorted order (clones removed, deleted frames restored). "
                        + "Lifts stay on their source frames.");
        resetOrderButton.setEnabled(false);
        resetOrderButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetOrder();
            }
        });
        cloneButton = new JButton("Clone");
        cloneButton.setToolTipText("Insert a copy of this frame after it (Ctrl+D).");
        cloneButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cloneSelected();
            }
        });
        deleteButton = new JButton("Delete");
        deleteButton.setToolTipText("Remove this frame from playback (Delete). The last frame cannot be removed.");
        deleteButton.setEnabled(frames.size() > 1);
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelected();
            }
        });

        frameList.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelected(-1);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK),
                JComponent.WHEN_FOCUSED);
        frameList.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelected(1);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK),
                JComponent.WHEN_FOCUSED);
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getRootPane().registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cloneSelected();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_D, menuMask),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        frameList.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelected();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                JComponent.WHEN_FOCUSED);
        frameList.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelected();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),
                JComponent.WHEN_FOCUSED);

        JPanel orderButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        orderButtons.add(moveUpButton);
        orderButtons.add(moveDownButton);
        orderButtons.add(cloneButton);
        orderButtons.add(deleteButton);
        orderButtons.add(reverseButton);
        orderButtons.add(resetOrderButton);

        JPanel listPane = new JPanel(new BorderLayout());
        JLabel listLabel = new JLabel("Animation order (Alt+↑/↓, Ctrl+D clone, Delete)");
        listLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        listPane.add(listLabel, BorderLayout.NORTH);
        listPane.add(new JScrollPane(frameList), BorderLayout.CENTER);
        listPane.add(orderButtons, BorderLayout.SOUTH);
        listPane.setPreferredSize(new Dimension(280, 240));

        JPanel previewPane = new JPanel(new BorderLayout());
        previewPane.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        previewPane.add(new JLabel("Selected frame (drag up/down or use the spinner)"), BorderLayout.NORTH);
        previewPane.add(cellPreview, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPane, previewPane);
        split.setResizeWeight(0.4);
        split.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JPanel stripPane = new JPanel(new BorderLayout());
        stripPane.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        stripPane.add(new JLabel(
                "Sheet preview (click a cell to select; Ctrl+scroll zooms)"),
                BorderLayout.NORTH);
        JScrollPane stripScroll = new JScrollPane(stripPreview);
        stripScroll.setPreferredSize(new Dimension(640, 120));
        stripScroll.getHorizontalScrollBar().setUnitIncrement(16);
        stripPane.add(stripScroll, BorderLayout.CENTER);

        JPanel liftRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        liftRow.add(new JLabel("Scale:"));
        liftRow.add(scaleCombo);
        liftRow.add(new JLabel("Lift:"));
        liftRow.add(liftSpinner);
        liftRow.add(applyAllButton);
        liftRow.add(resetButton);
        liftRow.add(new JLabel("Hop peak:"));
        liftRow.add(hopPeakSpinner);
        liftRow.add(hopButton);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttons.add(playLoopButton);
        buttons.add(cancelButton);
        buttons.add(packButton);

        JPanel south = new JPanel(new BorderLayout());
        south.add(stripPane, BorderLayout.CENTER);
        JPanel southButtons = new JPanel(new BorderLayout());
        southButtons.add(liftRow, BorderLayout.WEST);
        southButtons.add(buttons, BorderLayout.EAST);
        south.add(southButtons, BorderLayout.SOUTH);

        JPanel notesPane = new JPanel(new BorderLayout());
        JPanel headerPane = new JPanel(new BorderLayout());
        headerPane.add(headerLabel, BorderLayout.NORTH);
        headerPane.add(warningLabel, BorderLayout.SOUTH);
        notesPane.add(headerPane, BorderLayout.NORTH);
        if (!this.notes.isEmpty()) {
            JLabel noteLabel = new JLabel("<html>" + escapeHtml(this.notes).replace("\n", "<br>") + "</html>");
            noteLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
            notesPane.add(noteLabel, BorderLayout.SOUTH);
        }

        setLayout(new BorderLayout());
        add(notesPane, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(packButton);
        getRootPane().registerKeyboardAction(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        packed = false;
                        result = null;
                        dispose();
                    }
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                packed = false;
                result = null;
            }
        });

        frameList.setSelectedIndex(0);
        refreshAll();

        pack();
        Dimension size = getSize();
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = Math.max(640, (int) (screen.width * 0.9));
        int maxH = Math.max(480, (int) (screen.height * 0.85));
        if (size.width > maxW || size.height > maxH) {
            setSize(Math.min(size.width, maxW), Math.min(size.height, maxH));
        }
        setLocationRelativeTo(parent);
    }

    /**
     * Opens the pack dialog. Oversized frames (taller than built-in) default
     * to Fit; otherwise native scale. Returns lifts + scale if the user chose
     * Pack, or {@code null} if cancelled.
     */
    public static Result showDialog(Component parent, String title,
            List<File> files, List<BufferedImage> frames, String notes) {
        return showDialog(parent, title, files, frames, notes, ImageImport.SCALE_DIVISOR_NATIVE);
    }

    public static Result showDialog(Component parent, String title,
            List<File> files, List<BufferedImage> frames, String notes, int defaultScaleDivisor) {
        return showDialog(parent, title, files, frames, notes, defaultScaleDivisor, null);
    }

    public static Result showDialog(Component parent, String title,
            List<File> files, List<BufferedImage> frames, String notes,
            int defaultScaleDivisor, int[] sourceTimingsCs) {
        return showDialog(parent, title,
                namesFromFiles(files, frames).toArray(new String[0]),
                frames, notes, defaultScaleDivisor, sourceTimingsCs);
    }

    public static Result showDialog(Component parent, String title,
            String[] frameNames, List<BufferedImage> frames, String notes,
            int defaultScaleDivisor) {
        return showDialog(parent, title, frameNames, frames, notes, defaultScaleDivisor, null);
    }

    /**
     * @param sourceTimingsCs optional per-source-frame timings (centiseconds);
     *                        used by Play loop when length matches {@code frames}
     */
    public static Result showDialog(Component parent, String title,
            String[] frameNames, List<BufferedImage> frames, String notes,
            int defaultScaleDivisor, int[] sourceTimingsCs) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames");
        }
        List<String> names = frameNames != null ? Arrays.asList(frameNames) : null;
        FramePackDialog dialog = new FramePackDialog(
                parent, title, names, frames, notes, defaultScaleDivisor, sourceTimingsCs);
        dialog.setVisible(true);
        return dialog.packed ? dialog.result : null;
    }

    private static List<String> namesFromFiles(List<File> files, List<BufferedImage> frames) {
        List<String> names = new ArrayList<String>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            if (files != null && i < files.size() && files.get(i) != null) {
                names.add(files.get(i).getName());
            } else {
                names.add("frame " + (i + 1));
            }
        }
        return names;
    }

    private void selectScaleItem(int numerator, int divisor, boolean preferFitWhenMatching) {
        int match = -1;
        int fitIndex = -1;
        for (int i = 0; i < scaleCombo.getItemCount(); i++) {
            ScaleItem item = scaleCombo.getItemAt(i);
            if (item == null) {
                continue;
            }
            if (item.fit) {
                fitIndex = i;
                continue;
            }
            if (item.numerator == numerator && item.divisor == divisor && match < 0) {
                match = i;
            }
        }
        if (preferFitWhenMatching && fitIndex >= 0 && fitScaleDivisor == divisor
                && numerator == ImageImport.SCALE_NUMERATOR_NATIVE) {
            scaleCombo.setSelectedIndex(fitIndex);
        } else if (match >= 0) {
            scaleCombo.setSelectedIndex(match);
        } else if (fitIndex >= 0 && fitScaleDivisor == divisor
                && numerator == ImageImport.SCALE_NUMERATOR_NATIVE) {
            scaleCombo.setSelectedIndex(fitIndex);
        } else {
            int nativeIndex = -1;
            for (int i = 0; i < scaleCombo.getItemCount(); i++) {
                ScaleItem item = scaleCombo.getItemAt(i);
                if (item != null && !item.fit
                        && item.numerator == ImageImport.SCALE_NUMERATOR_NATIVE
                        && item.divisor == ImageImport.SCALE_DIVISOR_NATIVE) {
                    nativeIndex = i;
                    break;
                }
            }
            scaleCombo.setSelectedIndex(nativeIndex >= 0
                    ? nativeIndex
                    : Math.min(1, scaleCombo.getItemCount() - 1));
        }
    }

    private List<BufferedImage> packFrames() {
        if (scaleNumerator == ImageImport.SCALE_NUMERATOR_NATIVE
                && scaleDivisor == ImageImport.SCALE_DIVISOR_NATIVE) {
            return frames;
        }
        if (scaledFrames == null) {
            try {
                scaledFrames = ImageImport.scaleFrames(frames, scaleNumerator, scaleDivisor);
            } catch (IOException e) {
                return frames;
            }
        }
        return scaledFrames;
    }

    private int selectedIndex() {
        return frameList.getSelectedIndex();
    }

    private int sourceIndex(int playback) {
        return playback >= 0 && playback < order.length ? order[playback] : -1;
    }

    private BufferedImage frameAtPlayback(int playback) {
        int src = sourceIndex(playback);
        return src >= 0 ? packFrames().get(src) : null;
    }

    private List<BufferedImage> playbackFrames() throws IOException {
        return ImageImport.gather(packFrames(), order);
    }

    private void swapPlayback(int a, int b) {
        int tmpOrder = order[a];
        order[a] = order[b];
        order[b] = tmpOrder;
        int tmpLift = lifts[a];
        lifts[a] = lifts[b];
        lifts[b] = tmpLift;
    }

    private void moveSelected(int delta) {
        int from = selectedIndex();
        int to = from + delta;
        if (from < 0 || to < 0 || to >= order.length) {
            return;
        }
        swapPlayback(from, to);
        frameList.setSelectedIndex(to);
        frameList.ensureIndexIsVisible(to);
        refreshAll();
    }

    private void reverseOrder() {
        int n = order.length;
        if (n < 2) {
            return;
        }
        int selected = selectedIndex();
        for (int i = 0; i < n / 2; i++) {
            swapPlayback(i, n - 1 - i);
        }
        if (selected >= 0) {
            frameList.setSelectedIndex(n - 1 - selected);
            frameList.ensureIndexIsVisible(n - 1 - selected);
        }
        refreshAll();
    }

    private void resetOrder() {
        int n = frames.size();
        int selectedSrc = sourceIndex(selectedIndex());
        order = new int[n];
        lifts = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
            lifts[i] = sourceLifts[i];
        }
        int select = selectedSrc >= 0 ? selectedSrc : 0;
        rebuildPlaybackList(select);
        refreshAll();
    }

    private void cloneSelected() {
        int from = selectedIndex();
        if (from < 0) {
            return;
        }
        order = ImageImport.insertAfter(order, from, order[from]);
        lifts = ImageImport.insertAfter(lifts, from, lifts[from]);
        rebuildPlaybackList(from + 1);
        refreshAll();
    }

    private void deleteSelected() {
        int from = selectedIndex();
        if (from < 0 || order.length <= 1) {
            return;
        }
        order = ImageImport.removeAt(order, from);
        lifts = ImageImport.removeAt(lifts, from);
        int select = from < order.length ? from : order.length - 1;
        rebuildPlaybackList(select);
        refreshAll();
    }

    private void rebuildPlaybackList(int select) {
        DefaultListModel<Integer> model = (DefaultListModel<Integer>) frameList.getModel();
        model.clear();
        for (int i = 0; i < order.length; i++) {
            model.addElement(Integer.valueOf(i));
        }
        if (select < 0) {
            select = 0;
        }
        if (select >= order.length) {
            select = order.length - 1;
        }
        frameList.setSelectedIndex(select);
        frameList.ensureIndexIsVisible(select);
    }

    private void updateOrderButtons() {
        int index = selectedIndex();
        int n = order.length;
        moveUpButton.setEnabled(index > 0);
        moveDownButton.setEnabled(index >= 0 && index < n - 1);
        reverseButton.setEnabled(n >= 2);
        resetOrderButton.setEnabled(!ImageImport.isIdentityOrder(order, frames.size()));
        cloneButton.setEnabled(index >= 0);
        deleteButton.setEnabled(index >= 0 && n > 1);
        hopButton.setEnabled(n >= 3);
    }

    private void setLift(int index, int value) {
        int clamped = Math.max(0, Math.min(MAX_LIFT, value));
        if (lifts[index] == clamped) {
            return;
        }
        lifts[index] = clamped;
        int src = sourceIndex(index);
        if (src >= 0) {
            sourceLifts[src] = clamped;
        }
        refreshAll();
    }

    /** Sets every playback slot to the same clamped lift, then refreshes. */
    private void setAllLifts(int value) {
        int clamped = Math.max(0, Math.min(MAX_LIFT, value));
        Arrays.fill(lifts, clamped);
        writeLiftsToSources();
        refreshAll();
    }

    /** Copies playback lifts onto their source frames (last slot wins). */
    private void writeLiftsToSources() {
        for (int p = 0; p < order.length; p++) {
            int src = order[p];
            if (src >= 0 && src < sourceLifts.length) {
                sourceLifts[src] = lifts[p];
            }
        }
    }

    private void syncSelection() {
        int index = selectedIndex();
        if (index < 0) {
            return;
        }
        updatingSpinner = true;
        try {
            liftSpinner.setValue(Integer.valueOf(lifts[index]));
        } finally {
            updatingSpinner = false;
        }
        cellPreview.repaint();
        stripPreview.repaint();
        updateOrderButtons();
    }

    /**
     * Opens a looping feet-locked preview of the current draft (order / scale /
     * lifts). Does not allocate a packed strip and does not commit Pack.
     */
    private void openLoopPreview() {
        try {
            List<BufferedImage> toPack = playbackFrames();
            ImageImport.PackPreview preview = ImageImport.inspectFrames(toPack, lifts);
            int[] timings = playbackTimings(preview.frameCount);
            ActionFrameSource source = ActionFrameSource.fromDraftFrames(
                    toPack, lifts, preview.cellWidth, preview.cellHeight, timings);
            FrameLoopPreviewDialog.showDialog(this, source, "Loop Preview");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    e.getMessage(),
                    "Loop Preview Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Timings in playback order for the draft sheet. Uses gathered
     * {@link #sourceTimingsCs} when available; otherwise default frame timing.
     */
    private int[] playbackTimings(int frameCount) {
        if (sourceTimingsCs != null && sourceTimingsCs.length == frames.size()) {
            try {
                return ImageImport.gather(sourceTimingsCs, order);
            } catch (IOException ignored) {
                // Fall through to defaults.
            }
        }
        int[] timings = new int[frameCount];
        Arrays.fill(timings, Math.max(1, ImageImport.DEFAULT_FRAME_TIMING_CS));
        return timings;
    }

    /**
     * Asks before allocating a sheet over {@link ImageImport#SHEET_PIXEL_BUDGET}.
     * Returns {@code false} when the user cancels.
     */
    private boolean confirmPackIfHuge() {
        try {
            List<BufferedImage> toPack = playbackFrames();
            ImageImport.PackPreview preview = ImageImport.inspectFrames(toPack, lifts);
            int sheetW = preview.sheetWidth();
            int sheetH = preview.cellHeight;
            boolean hugePixels = ImageImport.exceedsSheetPixelBudget(sheetW, sheetH);
            boolean hugeWidth = ImageImport.exceedsSheetWidthBudget(sheetW);
            if (!hugePixels && !hugeWidth) {
                return true;
            }
            StringBuilder message = new StringBuilder();
            message.append(String.format("This sheet would be %d×%d", sheetW, sheetH));
            if (hugePixels) {
                message.append(String.format(" (~%s as ARGB)",
                        ImageImport.formatByteSize(ImageImport.sheetArgbBytes(sheetW, sheetH))));
            }
            message.append(", which is very large.\n\n");
            if (hugeWidth) {
                message.append("Strip width exceeds ")
                        .append(ImageImport.SHEET_WIDTH_BUDGET)
                        .append("px (a common GPU texture limit).\n\n");
            }
            message.append("Prefer Fit to built-in, fewer frames, or a smaller scale ")
                    .append("(200% quadruples pixels; 150% is 2.25×) unless you need the full resolution.")
                    .append("\n\nPack anyway?");
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    message.toString(),
                    "Large spritesheet",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            return choice == JOptionPane.YES_OPTION;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    e.getMessage(),
                    "Cannot pack",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void refreshAll() {
        try {
            List<BufferedImage> toPack = playbackFrames();
            ImageImport.PackPreview preview = ImageImport.inspectFrames(toPack, lifts);
            String scaleLabel;
            try {
                scaleLabel = ImageImport.formatScaleMarker(scaleNumerator, scaleDivisor);
            } catch (IOException e) {
                scaleLabel = scaleNumerator + "/" + scaleDivisor;
            }
            ScaleItem selectedScale = (ScaleItem) scaleCombo.getSelectedItem();
            if (selectedScale != null && selectedScale.fit) {
                scaleLabel = "fit → " + scaleLabel;
            }
            int sheetW = preview.sheetWidth();
            int sheetH = preview.cellHeight;
            headerLabel.setText(String.format(
                    "Pack %d frame%s into %d×%d cells (sheet %d×%d) at %s.",
                    preview.frameCount,
                    preview.frameCount == 1 ? "" : "s",
                    preview.cellWidth,
                    preview.cellHeight,
                    sheetW,
                    sheetH,
                    scaleLabel));
            boolean hugeSheet = ImageImport.exceedsSheetPixelBudget(sheetW, sheetH);
            boolean hugeWidth = ImageImport.exceedsSheetWidthBudget(sheetW);
            if (hugeSheet) {
                warningLabel.setText(String.format(
                        "Sheet preview deferred (%d×%d, ~%s) — choose a smaller scale, "
                                + "or Pack with confirmation.",
                        sheetW,
                        sheetH,
                        ImageImport.formatByteSize(ImageImport.sheetArgbBytes(sheetW, sheetH))));
                warningLabel.setVisible(true);
                stripPreview.setDeferred(preview.frameCount, sheetH);
            } else if (hugeWidth) {
                warningLabel.setText(String.format(
                        "Sheet is %dpx wide (GPU textures often cap at %d). "
                                + "Prefer fewer frames or a smaller scale.",
                        sheetW,
                        ImageImport.SHEET_WIDTH_BUDGET));
                warningLabel.setVisible(true);
                stripPreview.setSheet(ImageImport.packSheetImage(
                        toPack, preview.cellWidth, preview.cellHeight, lifts),
                        preview.frameCount, preview.cellHeight);
            } else if (ImageImport.isLargeCell(preview.cellHeight)) {
                warningLabel.setText(ImageImport.largeCellWarning());
                warningLabel.setVisible(true);
                stripPreview.setSheet(ImageImport.packSheetImage(
                        toPack, preview.cellWidth, preview.cellHeight, lifts),
                        preview.frameCount, preview.cellHeight);
            } else {
                warningLabel.setText(" ");
                warningLabel.setVisible(false);
                stripPreview.setSheet(ImageImport.packSheetImage(
                        toPack, preview.cellWidth, preview.cellHeight, lifts),
                        preview.frameCount, preview.cellHeight);
            }
            cellPreview.setCell(preview.cellWidth, preview.cellHeight);
        } catch (IOException e) {
            headerLabel.setText("Cannot pack: " + e.getMessage());
            warningLabel.setVisible(false);
            stripPreview.setDeferred(frames.size(), 1);
        }
        syncSelection();
        frameList.repaint();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private final class FrameRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            int playback = index;
            if (playback < 0 || playback >= order.length) {
                return this;
            }
            BufferedImage frame = frameAtPlayback(playback);
            int src = sourceIndex(playback);
            setIcon(new ImageIcon(thumbnail(frame)));
            setText(String.format("%d. %s  (%d×%d, lift %d)",
                    playback + 1, names.get(src), frame.getWidth(), frame.getHeight(),
                    lifts[playback]));
            return this;
        }
    }

    private static Image thumbnail(BufferedImage src) {
        int h = THUMB_H;
        int w = Math.max(1, src.getWidth() * h / Math.max(1, src.getHeight()));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /**
     * One frame on the computed cell, ground line at the bottom. Drag up to
     * increase lift; wheel and arrows nudge 1px (Shift: 5).
     */
    private final class CellPreview extends JComponent {
        private int cellW = 1;
        private int cellH = 1;
        private int dragStartY;
        private int dragStartLift;
        private float dragStartScale = 1f;
        private boolean dragging;

        CellPreview() {
            setOpaque(true);
            setBackground(EditorTheme.CANVAS);
            setPreferredSize(new Dimension(280, 240));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    int index = selectedIndex();
                    if (index < 0 || e.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    dragging = true;
                    dragStartY = e.getY();
                    dragStartLift = lifts[index];
                    dragStartScale = cellScale();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!dragging) {
                        return;
                    }
                    int index = selectedIndex();
                    if (index < 0) {
                        return;
                    }
                    float scale = dragStartScale > 0f ? dragStartScale : 1f;
                    int delta = Math.round((dragStartY - e.getY()) / scale);
                    setLift(index, dragStartLift + delta);
                }
            });
            addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    int index = selectedIndex();
                    if (index < 0) {
                        return;
                    }
                    int step = e.isShiftDown() ? 5 : 1;
                    int notches = e.getWheelRotation();
                    if (notches != 0) {
                        setLift(index, lifts[index] + (notches < 0 ? step : -step));
                    }
                    e.consume();
                }
            });
            registerKeyboardAction(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    nudge(1);
                }
            }, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), WHEN_FOCUSED);
            registerKeyboardAction(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    nudge(5);
                }
            }, KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), WHEN_FOCUSED);
            registerKeyboardAction(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    nudge(-1);
                }
            }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), WHEN_FOCUSED);
            registerKeyboardAction(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    nudge(-5);
                }
            }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), WHEN_FOCUSED);
            registerKeyboardAction(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    deleteSelected();
                }
            }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), WHEN_FOCUSED);
            registerKeyboardAction(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    deleteSelected();
                }
            }, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), WHEN_FOCUSED);
        }

        void setCell(int cellW, int cellH) {
            this.cellW = Math.max(1, cellW);
            this.cellH = Math.max(1, cellH);
            repaint();
        }

        private void nudge(int delta) {
            int index = selectedIndex();
            if (index >= 0) {
                setLift(index, lifts[index] + delta);
            }
        }

        private float cellScale() {
            int pad = 16;
            int availW = Math.max(1, getWidth() - pad * 2);
            int availH = Math.max(1, getHeight() - pad * 2);
            return Math.max(1f, Math.min(availW / (float) cellW, availH / (float) cellH));
        }

        private Rectangle cellBounds() {
            float scale = cellScale();
            int w = Math.round(cellW * scale);
            int h = Math.round(cellH * scale);
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                int index = selectedIndex();
                if (index < 0) {
                    return;
                }
                BufferedImage frame = frameAtPlayback(index);
                Rectangle bounds = cellBounds();
                paintChecker(g2, bounds);
                g2.setColor(EditorTheme.GUIDE);
                g2.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);

                float scale = cellScale();
                int dx = bounds.x + Math.round(((cellW - frame.getWidth()) / 2f) * scale);
                int dy = bounds.y + Math.round((cellH - frame.getHeight() - lifts[index]) * scale);
                int dw = Math.round(frame.getWidth() * scale);
                int dh = Math.round(frame.getHeight() * scale);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(frame, dx, dy, dw, dh, null);

                int groundY = bounds.y + bounds.height - 1;
                g2.setColor(EditorTheme.GROUND_LINE);
                g2.fillRect(bounds.x, groundY - 1, bounds.width, 2);
                g2.setColor(EditorTheme.GROUND_LABEL);
                g2.drawString("ground", bounds.x + 4, groundY - 4);
            } finally {
                g2.dispose();
            }
        }
    }

    private static void paintChecker(Graphics2D g2, Rectangle bounds) {
        int size = 8;
        Color a = EditorTheme.CHECKER_CELL_A;
        Color b = EditorTheme.CHECKER_CELL_B;
        for (int y = 0; y < bounds.height; y += size) {
            for (int x = 0; x < bounds.width; x += size) {
                g2.setColor((((x / size) + (y / size)) & 1) == 0 ? a : b);
                g2.fillRect(bounds.x + x, bounds.y + y,
                        Math.min(size, bounds.width - x), Math.min(size, bounds.height - y));
            }
        }
    }

    /**
     * Live packed strip. Click a cell to select that frame. Drawn at a fixed
     * zoom (readable cell size by default) with scrollbars when the strip is
     * wider than the viewport — never shrunk to fit dozens of frames into
     * view. Ctrl+scroll (Meta+scroll on macOS) zooms toward the cursor. When
     * the sheet would exceed {@link ImageImport#SHEET_PIXEL_BUDGET}, the
     * bitmap is deferred and a placeholder is shown instead.
     */
    private final class StripPreview extends JComponent {
        private static final int STRIP_PAD = 4;
        private static final float MIN_ZOOM = 0.25f;
        private static final float MAX_ZOOM = 16f;

        private BufferedImage sheet;
        private int frameCount = 1;
        private int cellH = 1;
        private boolean deferred;
        /** Screen pixels per sheet pixel. */
        private float zoom = 2f;

        StripPreview() {
            setOpaque(true);
            setBackground(EditorTheme.CANVAS_DEEP);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (deferred || sheet == null || frameCount < 1) {
                        return;
                    }
                    Rectangle bounds = imageBounds();
                    if (!bounds.contains(e.getPoint()) || bounds.width <= 0) {
                        return;
                    }
                    int index = (e.getX() - bounds.x) * frameCount / bounds.width;
                    if (index < 0) {
                        index = 0;
                    }
                    if (index >= frameCount) {
                        index = frameCount - 1;
                    }
                    frameList.setSelectedIndex(index);
                    frameList.ensureIndexIsVisible(index);
                }
            });
            addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    // Ctrl/Meta+wheel zooms; plain wheel must be forwarded so the
                    // enclosing JScrollPane can still scroll (a wheel listener on
                    // the view suppresses default scroll-pane delivery).
                    if (deferred || sheet == null) {
                        forwardWheelToScrollPane(e);
                        return;
                    }
                    if (e.isControlDown() || e.isMetaDown()) {
                        int notches = e.getWheelRotation();
                        if (notches != 0) {
                            float next = notches < 0
                                    ? Math.min(MAX_ZOOM, zoom * 1.25f)
                                    : Math.max(MIN_ZOOM, zoom / 1.25f);
                            zoomToward(e.getPoint(), next);
                        }
                        e.consume();
                        return;
                    }
                    forwardWheelToScrollPane(e);
                }
            });
        }

        void setDeferred(int frameCount, int cellH) {
            this.sheet = null;
            this.deferred = true;
            this.frameCount = Math.max(1, frameCount);
            this.cellH = Math.max(1, cellH);
            applyPreferredSize();
            repaint();
        }

        void setSheet(BufferedImage sheet, int frameCount, int cellH) {
            this.sheet = sheet;
            this.deferred = false;
            this.frameCount = Math.max(1, frameCount);
            this.cellH = Math.max(1, cellH);
            // Readable default: roughly 2× cell height, clamped to 48–96 px.
            float targetH = Math.min(96f, Math.max(48f, this.cellH * 2f));
            this.zoom = clampZoom(targetH / this.cellH);
            applyPreferredSize();
            repaint();
        }

        private float clampZoom(float z) {
            if (z < MIN_ZOOM) {
                return MIN_ZOOM;
            }
            if (z > MAX_ZOOM) {
                return MAX_ZOOM;
            }
            return z;
        }

        private void applyPreferredSize() {
            Dimension next;
            if (deferred || sheet == null) {
                int prefH = Math.min(96, Math.max(48, cellH > 1 ? Math.min(96, cellH * 2) : 64));
                next = new Dimension(640, prefH);
            } else {
                int w = Math.max(200, Math.round(sheet.getWidth() * zoom) + STRIP_PAD * 2);
                int h = Math.max(48, Math.round(sheet.getHeight() * zoom) + STRIP_PAD * 2);
                next = new Dimension(w, h);
            }
            if (!next.equals(getPreferredSize())) {
                setPreferredSize(next);
                revalidate();
            }
        }

        /**
         * Changes zoom while keeping the sheet pixel under {@code mouseInPreview}
         * fixed in the enclosing scroll viewport (cursor-centered zoom).
         */
        private void zoomToward(Point mouseInPreview, float newZoom) {
            float z = clampZoom(newZoom);
            if (Math.abs(z - zoom) < 1e-4f || sheet == null || mouseInPreview == null) {
                if (Math.abs(z - zoom) >= 1e-4f) {
                    zoom = z;
                    applyPreferredSize();
                    repaint();
                }
                return;
            }

            Rectangle bounds = imageBounds();
            float srcX;
            float srcY;
            if (bounds.width > 0 && bounds.height > 0 && bounds.contains(mouseInPreview)) {
                srcX = (mouseInPreview.x - bounds.x) * (float) sheet.getWidth() / bounds.width;
                srcY = (mouseInPreview.y - bounds.y) * (float) sheet.getHeight() / bounds.height;
            } else {
                srcX = sheet.getWidth() / 2f;
                srcY = sheet.getHeight() / 2f;
            }

            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class, this);
            JViewport viewport = scroll != null ? scroll.getViewport() : null;
            Point mouseInViewport = viewport != null
                    ? SwingUtilities.convertPoint(this, mouseInPreview, viewport)
                    : null;

            zoom = z;
            applyPreferredSize();

            if (viewport == null || mouseInViewport == null) {
                repaint();
                return;
            }

            Dimension pref = getPreferredSize();
            setSize(pref);
            viewport.setViewSize(pref);

            Rectangle newBounds = imageBounds();
            int contentX = newBounds.x
                    + Math.round(srcX * newBounds.width / (float) sheet.getWidth());
            int contentY = newBounds.y
                    + Math.round(srcY * newBounds.height / (float) sheet.getHeight());

            int viewX = contentX - mouseInViewport.x;
            int viewY = contentY - mouseInViewport.y;

            Dimension extent = viewport.getExtentSize();
            int maxX = Math.max(0, pref.width - extent.width);
            int maxY = Math.max(0, pref.height - extent.height);
            viewX = Math.max(0, Math.min(maxX, viewX));
            viewY = Math.max(0, Math.min(maxY, viewY));

            viewport.setViewPosition(new Point(viewX, viewY));
            repaint();
        }

        private void forwardWheelToScrollPane(MouseWheelEvent e) {
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class, this);
            if (scroll == null) {
                return;
            }
            Point p = SwingUtilities.convertPoint(this, e.getPoint(), scroll);
            MouseWheelEvent copy = new MouseWheelEvent(
                    scroll,
                    e.getID(),
                    e.getWhen(),
                    e.getModifiersEx(),
                    p.x,
                    p.y,
                    e.getXOnScreen(),
                    e.getYOnScreen(),
                    e.getClickCount(),
                    e.isPopupTrigger(),
                    e.getScrollType(),
                    e.getScrollAmount(),
                    e.getWheelRotation(),
                    e.getPreciseWheelRotation());
            scroll.dispatchEvent(copy);
        }

        private Rectangle imageBounds() {
            if (sheet == null) {
                return new Rectangle(0, 0, 0, 0);
            }
            int w = Math.max(1, Math.round(sheet.getWidth() * zoom));
            int h = Math.max(1, Math.round(sheet.getHeight() * zoom));
            int x = Math.max(STRIP_PAD, (getWidth() - w) / 2);
            int y = Math.max(STRIP_PAD, (getHeight() - h) / 2);
            return new Rectangle(x, y, w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (deferred || sheet == null) {
                    if (deferred) {
                        g2.setColor(EditorTheme.WARNING);
                        String line1 = "Sheet preview deferred — sheet too large to allocate.";
                        String line2 = "Select frames in the list; Pack will ask for confirmation.";
                        int y = getHeight() / 2;
                        g2.drawString(line1, 12, y - 6);
                        g2.drawString(line2, 12, y + 12);
                    }
                    return;
                }
                Rectangle bounds = imageBounds();
                paintChecker(g2, bounds);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(sheet, bounds.x, bounds.y, bounds.width, bounds.height, null);
                int selected = selectedIndex();
                if (selected >= 0 && frameCount > 0) {
                    int x0 = bounds.x + bounds.width * selected / frameCount;
                    int x1 = bounds.x + bounds.width * (selected + 1) / frameCount;
                    g2.setColor(EditorTheme.SELECTION);
                    g2.drawRect(x0, bounds.y, Math.max(1, x1 - x0 - 1), bounds.height - 1);
                }
                g2.setColor(EditorTheme.GUIDE_MUTED);
                for (int i = 1; i < frameCount; i++) {
                    int x = bounds.x + bounds.width * i / frameCount;
                    g2.drawLine(x, bounds.y, x, bounds.y + bounds.height);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
