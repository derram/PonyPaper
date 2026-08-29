package uk.cpjsmith.ponypaper.custom;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
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
 * choose a dyadic pack scale (100%…6.25%, or fit-to-built-in), rearrange
 * playback order, set per-frame lift (or apply one value to all frames), and
 * pack. Lift {@code 0} is the usual bottom-centre alignment; positive lift
 * bakes a hop into a taller cell. Returns {@code null} on cancel.
 */
public final class FramePackDialog extends JDialog {

    private static final int MAX_LIFT = 512;
    private static final int THUMB_H = 32;

    /**
     * User choices after Pack. Lift values are in <em>output</em> pixels
     * (after scale) and follow playback order. {@link #order} is a
     * permutation of source indices ({@code 0..n-1}).
     * {@link #scaleDivisor} is the resolved dyadic divisor actually applied
     * (never a "fit" sentinel).
     */
    public static final class Result {
        public final int[] lifts;
        public final int scaleDivisor;
        public final int[] order;

        Result(int[] lifts, int scaleDivisor, int[] order) {
            this.lifts = lifts;
            this.scaleDivisor = scaleDivisor;
            this.order = order;
        }
    }

    private static final class ScaleItem {
        /** Resolved divisor, or {@code -1} for fit-to-built-in. */
        final int divisor;
        final boolean fit;
        String label;

        ScaleItem(int divisor, String label) {
            this.divisor = divisor;
            this.fit = false;
            this.label = label;
        }

        ScaleItem(boolean fit, int resolvedDivisor, String label) {
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
    private final int[] lifts;
    /** Source index at each playback slot. */
    private final int[] order;
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
    private final JButton resetOrderButton;
    private int scaleDivisor;
    private List<BufferedImage> scaledFrames;
    private boolean updatingSpinner;
    private boolean packed;
    private Result result;

    private FramePackDialog(Component parent, String title,
            List<String> frameNames, List<BufferedImage> frames, String notes,
            int defaultScaleDivisor) {
        super(SwingUtilities.getWindowAncestor(parent),
                title != null ? title : "Import Frames",
                ModalityType.APPLICATION_MODAL);
        this.frames = frames;
        this.notes = notes != null ? notes : "";
        this.names = new ArrayList<String>(frames.size());
        this.lifts = new int[frames.size()];
        this.order = new int[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            this.order[i] = i;
        }
        int initialDivisor;
        try {
            initialDivisor = ImageImport.normalizeScaleDivisor(
                    defaultScaleDivisor <= 0
                            ? ImageImport.SCALE_DIVISOR_NATIVE
                            : (defaultScaleDivisor == ImageImport.SCALE_NATIVE
                                    ? ImageImport.SCALE_DIVISOR_NATIVE
                                    : (defaultScaleDivisor == ImageImport.SCALE_DESKTOP_PONIES
                                            ? ImageImport.SCALE_DIVISOR_HALF
                                            : defaultScaleDivisor)));
        } catch (IOException e) {
            initialDivisor = ImageImport.SCALE_DIVISOR_NATIVE;
        }
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
                new ScaleItem(ImageImport.SCALE_DIVISOR_NATIVE, "100% (native)"),
                fitScaleItem,
            };
        }
        scaleCombo = new JComboBox<ScaleItem>(scaleItems);
        selectScaleItem(initialDivisor, false);
        scaleCombo.setToolTipText(
                "Nearest-neighbour dyadic shrink before packing. "
                        + "Prefer ÷2 / ÷4 / ÷8 / ÷16 for crisp sprites. "
                        + "Fit picks the largest scale whose tallest frame is ≤ "
                        + ImageImport.LARGE_CELL_HEIGHT_PX + "px.");
        scaleCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ScaleItem item = (ScaleItem) scaleCombo.getSelectedItem();
                int next = item != null
                        ? (item.fit ? fitScaleDivisor : item.divisor)
                        : ImageImport.SCALE_DIVISOR_NATIVE;
                if (next == scaleDivisor) {
                    return;
                }
                scaleDivisor = next;
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

        JButton hopButton = new JButton("Apply hop");
        hopButton.setToolTipText("Parabola: 0 at both ends, peak in the middle. Baked into the sheet.");
        hopButton.setEnabled(frames.size() >= 3);
        hopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int peak = ((Number) hopPeakSpinner.getValue()).intValue();
                int[] curve = ImageImport.hopCurve(lifts.length, peak);
                System.arraycopy(curve, 0, lifts, 0, lifts.length);
                refreshAll();
            }
        });

        JButton packButton = new JButton("Pack");
        packButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                packed = true;
                result = new Result(lifts.clone(), scaleDivisor, order.clone());
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

        boolean canReorder = frames.size() >= 2;
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
        JButton reverseButton = new JButton("Reverse");
        reverseButton.setToolTipText("Play the clip backwards. Lifts stay on their frames.");
        reverseButton.setEnabled(canReorder);
        reverseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reverseOrder();
            }
        });
        resetOrderButton = new JButton("Reset order");
        resetOrderButton.setToolTipText("Restore the imported / natural-sorted order. Lifts stay on their frames.");
        resetOrderButton.setEnabled(false);
        resetOrderButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetOrder();
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

        JPanel orderButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        orderButtons.add(moveUpButton);
        orderButtons.add(moveDownButton);
        orderButtons.add(reverseButton);
        orderButtons.add(resetOrderButton);

        JPanel listPane = new JPanel(new BorderLayout());
        JLabel listLabel = new JLabel("Animation order (Alt+↑/↓ to move)");
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
        stripPane.add(new JLabel("Sheet preview (click a cell to select)"), BorderLayout.NORTH);
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
     * Opens the pack dialog at native scale. Returns lifts + scale if the user
     * chose Pack, or {@code null} if cancelled.
     */
    public static Result showDialog(Component parent, String title,
            List<File> files, List<BufferedImage> frames, String notes) {
        return showDialog(parent, title, files, frames, notes, ImageImport.SCALE_DIVISOR_NATIVE);
    }

    public static Result showDialog(Component parent, String title,
            List<File> files, List<BufferedImage> frames, String notes, int defaultScaleDivisor) {
        return showDialog(parent, title,
                namesFromFiles(files, frames).toArray(new String[0]),
                frames, notes, defaultScaleDivisor);
    }

    public static Result showDialog(Component parent, String title,
            String[] frameNames, List<BufferedImage> frames, String notes,
            int defaultScaleDivisor) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames");
        }
        List<String> names = frameNames != null ? Arrays.asList(frameNames) : null;
        FramePackDialog dialog = new FramePackDialog(
                parent, title, names, frames, notes, defaultScaleDivisor);
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

    private void selectScaleItem(int divisor, boolean preferFitWhenMatching) {
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
            if (item.divisor == divisor && match < 0) {
                match = i;
            }
        }
        if (preferFitWhenMatching && fitIndex >= 0 && fitScaleDivisor == divisor) {
            scaleCombo.setSelectedIndex(fitIndex);
        } else if (match >= 0) {
            scaleCombo.setSelectedIndex(match);
        } else if (fitIndex >= 0 && fitScaleDivisor == divisor) {
            scaleCombo.setSelectedIndex(fitIndex);
        } else {
            scaleCombo.setSelectedIndex(0);
        }
    }

    private List<BufferedImage> packFrames() {
        if (scaleDivisor == ImageImport.SCALE_DIVISOR_NATIVE) {
            return frames;
        }
        if (scaledFrames == null) {
            try {
                scaledFrames = ImageImport.scaleFrames(frames, scaleDivisor);
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
        return ImageImport.permute(packFrames(), order);
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
        int n = order.length;
        int[] sourceLifts = new int[n];
        for (int p = 0; p < n; p++) {
            sourceLifts[order[p]] = lifts[p];
        }
        int selectedSrc = sourceIndex(selectedIndex());
        for (int i = 0; i < n; i++) {
            order[i] = i;
            lifts[i] = sourceLifts[i];
        }
        if (selectedSrc >= 0) {
            frameList.setSelectedIndex(selectedSrc);
            frameList.ensureIndexIsVisible(selectedSrc);
        }
        refreshAll();
    }

    private void updateOrderButtons() {
        int index = selectedIndex();
        int n = order.length;
        moveUpButton.setEnabled(index > 0);
        moveDownButton.setEnabled(index >= 0 && index < n - 1);
        resetOrderButton.setEnabled(!ImageImport.isIdentityOrder(order));
    }

    private void setLift(int index, int value) {
        int clamped = Math.max(0, Math.min(MAX_LIFT, value));
        if (lifts[index] == clamped) {
            return;
        }
        lifts[index] = clamped;
        refreshAll();
    }

    /** Sets every playback slot to the same clamped lift, then refreshes. */
    private void setAllLifts(int value) {
        int clamped = Math.max(0, Math.min(MAX_LIFT, value));
        Arrays.fill(lifts, clamped);
        refreshAll();
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

    private void refreshAll() {
        try {
            List<BufferedImage> toPack = playbackFrames();
            ImageImport.PackPreview preview = ImageImport.inspectFrames(toPack, lifts);
            String scaleLabel;
            try {
                scaleLabel = ImageImport.formatScaleDivisor(scaleDivisor)
                        + " (÷" + scaleDivisor + ")";
            } catch (IOException e) {
                scaleLabel = "÷" + scaleDivisor;
            }
            ScaleItem selectedScale = (ScaleItem) scaleCombo.getSelectedItem();
            if (selectedScale != null && selectedScale.fit) {
                scaleLabel = "fit → " + scaleLabel;
            }
            headerLabel.setText(String.format(
                    "Pack %d frame%s into %d×%d cells (sheet %d×%d) at %s.",
                    preview.frameCount,
                    preview.frameCount == 1 ? "" : "s",
                    preview.cellWidth,
                    preview.cellHeight,
                    preview.sheetWidth(),
                    preview.cellHeight,
                    scaleLabel));
            if (ImageImport.isLargeCell(preview.cellHeight)) {
                warningLabel.setText(ImageImport.largeCellWarning());
                warningLabel.setVisible(true);
            } else {
                warningLabel.setText(" ");
                warningLabel.setVisible(false);
            }
            cellPreview.setCell(preview.cellWidth, preview.cellHeight);
            stripPreview.setSheet(ImageImport.packSheetImage(
                    toPack, preview.cellWidth, preview.cellHeight, lifts),
                    preview.frameCount, preview.cellHeight);
        } catch (IOException e) {
            headerLabel.setText("Cannot pack: " + e.getMessage());
            warningLabel.setVisible(false);
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
            if (playback < 0 || playback >= frames.size()) {
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
     * Live packed strip. Click a cell to select that frame.
     */
    private final class StripPreview extends JComponent {
        private BufferedImage sheet;
        private int frameCount = 1;

        StripPreview() {
            setOpaque(true);
            setBackground(EditorTheme.CANVAS_DEEP);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (sheet == null || frameCount < 1) {
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
        }

        void setSheet(BufferedImage sheet, int frameCount, int cellH) {
            this.sheet = sheet;
            this.frameCount = Math.max(1, frameCount);
            int prefH = Math.min(96, Math.max(48, cellH * 2));
            int prefW = sheet != null
                    ? Math.max(200, sheet.getWidth() * prefH / Math.max(1, sheet.getHeight()))
                    : 200;
            Dimension next = new Dimension(prefW, prefH);
            if (!next.equals(getPreferredSize())) {
                setPreferredSize(next);
                revalidate();
            }
            repaint();
        }

        private Rectangle imageBounds() {
            if (sheet == null) {
                return new Rectangle(0, 0, 0, 0);
            }
            int pad = 4;
            int availW = Math.max(1, getWidth() - pad * 2);
            int availH = Math.max(1, getHeight() - pad * 2);
            float scale = Math.min(availW / (float) sheet.getWidth(), availH / (float) sheet.getHeight());
            // Prefer integer-ish scale when the strip is small; never downscale below 1 if it fits.
            if (sheet.getWidth() <= availW && sheet.getHeight() <= availH) {
                scale = Math.max(1f, (float) Math.floor(scale));
            }
            int w = Math.max(1, Math.round(sheet.getWidth() * scale));
            int h = Math.max(1, Math.round(sheet.getHeight() * scale));
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (sheet == null) {
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
