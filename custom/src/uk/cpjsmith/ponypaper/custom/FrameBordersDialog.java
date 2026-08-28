package uk.cpjsmith.ponypaper.custom;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Modal dialog: pick variable-width frame borders on a left-to-right
 * spritesheet, optionally trim transparent margins inside each interval, then
 * either pack the frames into the action or export them as PNGs.
 */
public final class FrameBordersDialog extends JDialog {

    private static final int MAX_FRAMES = 512;
    private static final int HANDLE_HIT_PX = 6;

    public enum Action {
        PACK,
        EXPORT
    }

    public static final class Result {
        public final List<BufferedImage> frames;
        public final ImageImport.FrameBorders borders;
        public final Action action;

        Result(List<BufferedImage> frames, ImageImport.FrameBorders borders, Action action) {
            this.frames = frames;
            this.borders = borders;
            this.action = action;
        }
    }

    private final BufferedImage sheet;
    private final int sheetW;
    private final int sheetH;
    private final JSpinner framesSpinner;
    private final JCheckBox trimCheck;
    private final JLabel statusLabel;
    private final JLabel warningLabel;
    private final BordersPreview preview;
    private final JButton packButton;
    private final JButton exportButton;

    private ImageImport.FrameBorders borders;
    private boolean updating;
    private boolean accepted;
    private Result result;

    private FrameBordersDialog(Component parent, String title, BufferedImage sheet,
            ImageImport.FrameBorders initial, String notes) {
        super(SwingUtilities.getWindowAncestor(parent),
                title != null ? title : "Frame Borders",
                ModalityType.APPLICATION_MODAL);
        if (sheet == null) {
            throw new IllegalArgumentException("sheet");
        }
        this.sheet = ImageImport.ensureArgb(sheet);
        this.sheetW = this.sheet.getWidth();
        this.sheetH = this.sheet.getHeight();

        ImageImport.FrameBorders start = initial != null
                ? initial.copy()
                : ImageImport.equalBorders(sheetW, 2);
        if (start.frameCount() < 1) {
            start = ImageImport.equalBorders(sheetW, 2);
        }
        this.borders = start;

        framesSpinner = new JSpinner(new SpinnerNumberModel(
                borders.frameCount(), 1, MAX_FRAMES, 1));
        trimCheck = new JCheckBox("Trim empty margins inside each frame", borders.trimMargins);
        trimCheck.setToolTipText(
                "Crop transparent pad inside each border interval before packing or export. "
                        + "Does not change where borders are — props in the frame stay with the character.");

        framesSpinner.setToolTipText(
                "Number of frames. Changing this resets borders to an equal split.");

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 0, 10));
        warningLabel = new JLabel(" ");
        warningLabel.setForeground(EditorTheme.WARNING);
        warningLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));

        preview = new BordersPreview();

        ChangeListener refresh = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updating) {
                    return;
                }
                if (e.getSource() == framesSpinner) {
                    applyEqualSplit(spinnerInt(framesSpinner));
                    return;
                }
                refreshPreview();
            }
        };
        framesSpinner.addChangeListener(refresh);
        trimCheck.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                borders.trimMargins = trimCheck.isSelected();
                refreshPreview();
            }
        });

        JButton equalButton = new JButton("Equal split");
        equalButton.setToolTipText(
                "Reset borders to contiguous sheetWidth ÷ frames (wallpaper-style).");
        equalButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyEqualSplit(spinnerInt(framesSpinner));
            }
        });

        packButton = new JButton("Pack…");
        packButton.setToolTipText(
                "Extract frames with these borders and open the pack dialog for this action.");
        packButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                accept(Action.PACK);
            }
        });

        exportButton = new JButton("Export PNGs…");
        exportButton.setToolTipText(
                "Extract frames with these borders and write numbered PNGs to a folder.");
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                accept(Action.EXPORT);
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                accepted = false;
                result = null;
                dispose();
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.gridx = 0;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel("Frames:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(framesSpinner, c);
        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(trimCheck, c);
        c.gridy = 2;
        c.fill = GridBagConstraints.NONE;
        form.add(equalButton, c);

        JPanel north = new JPanel(new BorderLayout());
        if (notes != null && !notes.isEmpty()) {
            JLabel notesLabel = new JLabel("<html><body style='width:520px'>"
                    + escapeHtml(notes).replace("\n", "<br>") + "</body></html>");
            notesLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
            north.add(notesLabel, BorderLayout.NORTH);
        }
        north.add(form, BorderLayout.CENTER);
        north.add(statusLabel, BorderLayout.SOUTH);

        JPanel south = new JPanel(new BorderLayout());
        south.add(warningLabel, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancelButton);
        buttons.add(exportButton);
        buttons.add(packButton);
        south.add(buttons, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(preview);
        scroll.getViewport().setBackground(EditorTheme.CANVAS_DEEP);
        scroll.setPreferredSize(new Dimension(640, 220));
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JPanel center = new JPanel(new BorderLayout());
        center.add(new JLabel(
                "  Drag left/right edges to set frame borders (gaps are gutters; Ctrl+scroll zooms)"),
                BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                accepted = false;
                result = null;
            }
        });

        getRootPane().setDefaultButton(packButton);
        refreshPreview();
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

    private void applyEqualSplit(int frameCount) {
        boolean trim = trimCheck.isSelected();
        borders = ImageImport.equalBorders(sheetW, frameCount);
        borders.trimMargins = trim;
        updating = true;
        try {
            framesSpinner.setValue(Integer.valueOf(borders.frameCount()));
        } finally {
            updating = false;
        }
        preview.setSelected(0);
        refreshPreview();
    }

    private void refreshPreview() {
        borders.trimMargins = trimCheck.isSelected();
        int n = borders.frameCount();
        StringBuilder widths = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                widths.append(',');
            }
            widths.append(borders.ends[i] - borders.starts[i]);
            if (i >= 7 && n > 8) {
                widths.append(",…");
                break;
            }
        }
        int unusedLeft = ImageImport.bordersUnusedLeft(borders);
        int unusedRight = ImageImport.bordersUnusedRight(sheetW, borders);
        int gaps = ImageImport.bordersGapTotal(borders);
        statusLabel.setText(String.format(
                "Sheet %d×%d — %d frame%s, widths [%s], unused L %d / R %d, gaps %dpx.",
                sheetW, sheetH, n, n == 1 ? "" : "s", widths, unusedLeft, unusedRight, gaps));

        String warning = null;
        try {
            ImageImport.validateFrameBorders(sheet, borders);
            if (sheetW % n != 0 && gaps == 0 && unusedLeft == 0
                    && unusedRight == sheetW % n
                    && borders.ends[0] - borders.starts[0] == sheetW / n) {
                warning = "Equal split drops " + (sheetW % n)
                        + "px on the right (wallpaper-style). Drag edges for padded sheets.";
            } else if (unusedRight < 0) {
                warning = "Frames extend past the sheet.";
            }
        } catch (IOException e) {
            warning = e.getMessage();
        }
        boolean ok = warning == null
                || warning.startsWith("Equal split drops");
        if (!ok) {
            warningLabel.setText(warning);
            warningLabel.setVisible(true);
            packButton.setEnabled(false);
            exportButton.setEnabled(false);
        } else {
            if (warning != null) {
                warningLabel.setText(warning);
                warningLabel.setVisible(true);
            } else {
                warningLabel.setText(" ");
                warningLabel.setVisible(false);
            }
            packButton.setEnabled(true);
            exportButton.setEnabled(true);
        }
        preview.setBorders(borders);
    }

    private void accept(Action action) {
        borders.trimMargins = trimCheck.isSelected();
        try {
            List<BufferedImage> frames = ImageImport.extractFrames(sheet, borders);
            accepted = true;
            result = new Result(frames, borders.copy(), action);
            dispose();
        } catch (IOException e) {
            warningLabel.setText(e.getMessage());
            warningLabel.setVisible(true);
            packButton.setEnabled(false);
            exportButton.setEnabled(false);
        }
    }

    private static int spinnerInt(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Opens the borders dialog. Returns extracted frames + chosen action, or
     * {@code null} if cancelled.
     *
     * @param defaultFrameCount hint for initial frame count ({@code <= 1} means guess)
     */
    public static Result showDialog(Component parent, String title, BufferedImage sheet,
            int defaultFrameCount, String notes) {
        if (sheet == null) {
            throw new IllegalArgumentException("sheet");
        }
        int n = ImageImport.suggestFrameCount(sheet.getWidth(), defaultFrameCount);
        ImageImport.FrameBorders initial = ImageImport.equalBorders(sheet.getWidth(), n);
        FrameBordersDialog dialog = new FrameBordersDialog(parent, title, sheet, initial, notes);
        dialog.setVisible(true);
        return dialog.accepted ? dialog.result : null;
    }

    private final class BordersPreview extends JComponent {
        private ImageImport.FrameBorders view =
                ImageImport.equalBorders(1, 1);
        private int selected = 0;
        private float zoom = 2f;
        /** -1 = none, else frame index whose left (even) or right (odd via dragRight) edge is dragged. */
        private int dragFrame = -1;
        private boolean dragRight;

        BordersPreview() {
            setOpaque(true);
            setBackground(EditorTheme.CANVAS_DEEP);
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Hit hit = hitTest(e.getX(), e.getY());
                    if (hit == null) {
                        return;
                    }
                    selected = hit.frame;
                    if (hit.edge) {
                        dragFrame = hit.frame;
                        dragRight = hit.right;
                        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    } else {
                        dragFrame = -1;
                    }
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragFrame = -1;
                    updateHoverCursor(e.getX(), e.getY());
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    Hit hit = hitTest(e.getX(), e.getY());
                    if (hit != null) {
                        selected = hit.frame;
                        repaint();
                    }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragFrame < 0 || view.starts == null) {
                        return;
                    }
                    Rectangle bounds = imageBounds();
                    float scale = scale();
                    int srcX = Math.round((e.getX() - bounds.x) / scale);
                    moveEdge(dragFrame, dragRight, srcX);
                    refreshPreview();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHoverCursor(e.getX(), e.getY());
                }
            });
            addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    // Ctrl/Meta+wheel zooms; plain wheel must be forwarded so the
                    // enclosing JScrollPane can still scroll (a wheel listener on
                    // the view suppresses default scroll-pane delivery).
                    if (e.isControlDown() || e.isMetaDown()) {
                        if (e.getWheelRotation() < 0) {
                            zoom = Math.min(16f, zoom * 1.25f);
                        } else if (e.getWheelRotation() > 0) {
                            zoom = Math.max(0.5f, zoom / 1.25f);
                        }
                        revalidate();
                        repaint();
                        e.consume();
                        return;
                    }
                    forwardWheelToScrollPane(e);
                }
            });
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

        void setBorders(ImageImport.FrameBorders borders) {
            this.view = borders != null ? borders : ImageImport.equalBorders(1, 1);
            if (selected >= this.view.frameCount()) {
                selected = Math.max(0, this.view.frameCount() - 1);
            }
            revalidate();
            repaint();
        }

        void setSelected(int index) {
            selected = Math.max(0, index);
            repaint();
        }

        private void updateHoverCursor(int mx, int my) {
            Hit hit = hitTest(mx, my);
            if (hit != null && hit.edge) {
                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
        }

        private void moveEdge(int frame, boolean right, int srcX) {
            int n = view.frameCount();
            if (frame < 0 || frame >= n) {
                return;
            }
            int minStart = frame == 0 ? 0 : view.ends[frame - 1];
            int maxEnd = frame + 1 < n ? view.starts[frame + 1] : sheetW;
            if (right) {
                int newEnd = clamp(srcX, view.starts[frame] + 1, maxEnd);
                view.ends[frame] = newEnd;
            } else {
                int newStart = clamp(srcX, minStart, view.ends[frame] - 1);
                view.starts[frame] = newStart;
            }
        }

        private Hit hitTest(int mx, int my) {
            Rectangle bounds = imageBounds();
            if (!bounds.contains(mx, my) || view.frameCount() < 1) {
                return null;
            }
            float scale = scale();
            int srcX = Math.round((mx - bounds.x) / scale);
            int n = view.frameCount();
            // Prefer edge hits.
            for (int i = 0; i < n; i++) {
                int leftPx = bounds.x + Math.round(view.starts[i] * scale);
                int rightPx = bounds.x + Math.round(view.ends[i] * scale);
                if (Math.abs(mx - leftPx) <= HANDLE_HIT_PX) {
                    return new Hit(i, true, false);
                }
                if (Math.abs(mx - rightPx) <= HANDLE_HIT_PX) {
                    return new Hit(i, true, true);
                }
            }
            for (int i = 0; i < n; i++) {
                if (srcX >= view.starts[i] && srcX < view.ends[i]) {
                    return new Hit(i, false, false);
                }
            }
            // Click in a gutter: select nearest frame.
            int best = 0;
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int mid = (view.starts[i] + view.ends[i]) / 2;
                int dist = Math.abs(srcX - mid);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = i;
                }
            }
            return new Hit(best, false, false);
        }

        private float scale() {
            return zoom;
        }

        private Rectangle imageBounds() {
            float scale = scale();
            int w = Math.round(sheetW * scale);
            int h = Math.round(sheetH * scale);
            int x = Math.max(8, (getWidth() - w) / 2);
            int y = Math.max(8, (getHeight() - h) / 2);
            return new Rectangle(x, y, w, h);
        }

        @Override
        public Dimension getPreferredSize() {
            float scale = scale();
            return new Dimension(
                    Math.round(sheetW * scale) + 24,
                    Math.round(sheetH * scale) + 24);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                Rectangle bounds = imageBounds();
                paintChecker(g2, bounds);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(sheet, bounds.x, bounds.y, bounds.width, bounds.height, null);

                float scale = scale();
                int n = view.frameCount();
                // Dim unused / gutters.
                g2.setColor(EditorTheme.DIM_OVERLAY);
                if (n > 0 && view.starts[0] > 0) {
                    int w = Math.round(view.starts[0] * scale);
                    g2.fillRect(bounds.x, bounds.y, w, bounds.height);
                }
                for (int i = 0; i + 1 < n; i++) {
                    int gapStart = view.ends[i];
                    int gapEnd = view.starts[i + 1];
                    if (gapEnd > gapStart) {
                        int x = bounds.x + Math.round(gapStart * scale);
                        int w = Math.round((gapEnd - gapStart) * scale);
                        g2.fillRect(x, bounds.y, w, bounds.height);
                    }
                }
                if (n > 0 && view.ends[n - 1] < sheetW) {
                    int x = bounds.x + Math.round(view.ends[n - 1] * scale);
                    g2.fillRect(x, bounds.y, bounds.x + bounds.width - x, bounds.height);
                }

                for (int i = 0; i < n; i++) {
                    int x = bounds.x + Math.round(view.starts[i] * scale);
                    int w = Math.round((view.ends[i] - view.starts[i]) * scale);
                    int y = bounds.y;
                    int h = bounds.height;
                    if (i == selected) {
                        g2.setColor(new Color(
                                EditorTheme.SELECTION.getRed(),
                                EditorTheme.SELECTION.getGreen(),
                                EditorTheme.SELECTION.getBlue(),
                                40));
                        g2.fillRect(x, y, w, h);
                    }
                    g2.setColor(i == selected ? EditorTheme.SELECTION : EditorTheme.GUIDE);
                    g2.setStroke(new BasicStroke(i == selected ? 2f : 1f));
                    g2.drawRect(x, y, Math.max(0, w - 1), Math.max(0, h - 1));
                    // Edge handles.
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(i == selected ? EditorTheme.ACCENT : EditorTheme.GUIDE_MUTED);
                    g2.drawLine(x, y, x, y + h - 1);
                    g2.drawLine(x + Math.max(0, w - 1), y, x + Math.max(0, w - 1), y + h - 1);
                }
            } finally {
                g2.dispose();
            }
        }

        private void paintChecker(Graphics2D g2, Rectangle bounds) {
            int size = 8;
            for (int y = 0; y < bounds.height; y += size) {
                for (int x = 0; x < bounds.width; x += size) {
                    boolean dark = (((x / size) + (y / size)) & 1) == 0;
                    g2.setColor(dark ? EditorTheme.CHECKER_A : EditorTheme.CHECKER_B);
                    g2.fillRect(bounds.x + x, bounds.y + y,
                            Math.min(size, bounds.width - x), Math.min(size, bounds.height - y));
                }
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Hit {
        final int frame;
        final boolean edge;
        final boolean right;

        Hit(int frame, boolean edge, boolean right) {
            this.frame = frame;
            this.edge = edge;
            this.right = right;
        }
    }
}
