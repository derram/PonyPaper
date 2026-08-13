package uk.cpjsmith.ponypaper.custom;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Modal dialog: play action A then B with feet locked to a fixed ground point,
 * and optional onion-skin of A's last frame under B's first (or current) frame.
 * Used to verify per-facing {@code <anchorx>}/{@code <anchory>} across transitions.
 */
public final class TransitionPreviewDialog extends JDialog {

    private static final int TICK_MS = 33;
    private static final float CS_PER_MS = 0.1f;
    private static final int PAD = 24;
    private static final Color GROUND = new Color(0x44, 0xaa, 0x44, 0xcc);
    private static final Color FEET_RING = new Color(0xff, 0x33, 0x33, 0xee);
    private static final Color FEET_CORE = new Color(0xff, 0x33, 0x33, 0x99);
    private static final Color CHECKER_A = new Color(0x2a, 0x2a, 0x2e);
    private static final Color CHECKER_B = new Color(0x36, 0x36, 0x3c);
    private static final Color STATUS_BG = new Color(0x1e, 0x1e, 0x22);

    private final PonyEditor editor;
    private final StagePanel stage;
    private final JComboBox<ActionItem> actionACombo;
    private final JComboBox<ActionItem> actionBCombo;
    private final JComboBox<String> directionCombo;
    private final JCheckBox onionCheck;
    private final JSlider scaleSlider;
    private final JSlider rateSlider;
    private final JLabel statusLabel;
    private final JButton playButton;
    private final JButton stepButton;
    private final JButton restartButton;

    private ActionFrameSource sourceA;
    private ActionFrameSource sourceB;
    /** {@code true} while playing phase A; {@code false} for B / onion focus. */
    private boolean phaseA = true;
    /** Animation clock in centiseconds within the current phase. */
    private float animTimeCs;
    private boolean playing;
    private final Timer timer;
    private long lastTickNanos;
    /** Suppresses reload while combos are rebuilt. */
    private boolean updatingUi;

    private TransitionPreviewDialog(Component parent, PonyEditor editor, int initialActionIndex,
            String initialDirection) {
        super(SwingUtilities.getWindowAncestor(parent), "Transition Preview", ModalityType.APPLICATION_MODAL);
        this.editor = editor;

        stage = new StagePanel();
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(STATUS_BG);
        statusLabel.setForeground(Color.LIGHT_GRAY);

        actionACombo = new JComboBox<ActionItem>();
        actionBCombo = new JComboBox<ActionItem>();
        directionCombo = new JComboBox<String>(new String[] { "right", "left" });
        if ("left".equals(initialDirection)) {
            directionCombo.setSelectedItem("left");
        } else {
            directionCombo.setSelectedItem("right");
        }

        onionCheck = new JCheckBox("Onion skin (A last under B)", true);
        onionCheck.setToolTipText(
                "When showing B (or paused on B), draw A's last frame semi-transparent "
                        + "at the same feet point so anchor pops are obvious.");

        scaleSlider = new JSlider(1, 8, 3);
        scaleSlider.setMajorTickSpacing(1);
        scaleSlider.setPaintTicks(true);
        scaleSlider.setSnapToTicks(true);
        scaleSlider.setToolTipText("Nearest-neighbour display scale.");

        rateSlider = new JSlider(25, 300, 100);
        rateSlider.setMajorTickSpacing(25);
        rateSlider.setPaintTicks(true);
        rateSlider.setToolTipText("Playback rate (percent).");

        playButton = new JButton("Play");
        stepButton = new JButton("Step");
        restartButton = new JButton("Restart");
        JButton closeButton = new JButton("Close");

        actionACombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!updatingUi) {
                    // New A → rebuild B neighbors, show A at feet (anchors for A).
                    stopPlaying();
                    rebuildBList(selectedActionIndex(actionACombo), -1);
                    loadSources();
                    showActionA();
                }
            }
        });
        actionBCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!updatingUi) {
                    // New B must re-anchor the stage to B immediately. Previously we
                    // kept phase A, so the feet crosshair/sprite stayed on the first
                    // action until the user played through the whole sequence.
                    stopPlaying();
                    loadSources();
                    showActionB();
                }
            }
        });
        directionCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!updatingUi) {
                    // Keep A and B selections; only facing/sheets change with direction.
                    rebuildActionLists(
                            selectedActionIndex(actionACombo),
                            selectedActionIndex(actionBCombo));
                    loadSources();
                    showActionA();
                }
            }
        });
        onionCheck.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stage.repaint();
                updateStatus();
            }
        });
        scaleSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                stage.revalidate();
                stage.repaint();
                updateStatus();
            }
        });
        rateSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateStatus();
            }
        });

        playButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                togglePlay();
            }
        });
        stepButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stepOnce();
            }
        });
        restartButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopPlaying();
                resetToStart();
            }
        });
        closeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        timer = new Timer(TICK_MS, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onTick();
            }
        });

        JPanel north = new JPanel(new GridBagLayout());
        north.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        north.add(new JLabel("Action A:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        north.add(actionACombo, c);

        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        north.add(new JLabel("Action B:"), c);
        c.gridx = 3;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        north.add(actionBCombo, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        north.add(new JLabel("Direction:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        north.add(directionCombo, c);

        c.gridx = 2;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        north.add(onionCheck, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy = 2;
        north.add(new JLabel("Scale:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        north.add(scaleSlider, c);

        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        north.add(new JLabel("Rate %:"), c);
        c.gridx = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        north.add(rateSlider, c);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(playButton);
        controls.add(stepButton);
        controls.add(restartButton);
        controls.add(closeButton);

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(controls, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(stage);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(playButton);
        getRootPane().registerKeyboardAction(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        dispose();
                    }
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopPlaying();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                stopPlaying();
            }
        });

        rebuildActionLists(initialActionIndex, -1);
        loadSources();
        resetToStart();

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

    private String currentDirection() {
        Object sel = directionCombo.getSelectedItem();
        return sel != null ? sel.toString() : "right";
    }

    private int selectedActionIndex(JComboBox<ActionItem> combo) {
        ActionItem item = (ActionItem) combo.getSelectedItem();
        return item != null ? item.index : -1;
    }

    /**
     * Rebuilds Action A/B combo models for the current direction.
     *
     * @param preferAIndex action index to keep selected for A, or -1 for default
     * @param preferBIndex action index to keep selected for B, or -1 for neighbor default
     */
    private void rebuildActionLists(int preferAIndex, int preferBIndex) {
        updatingUi = true;
        try {
            String dir = currentDirection();
            List<ActionItem> all = listActionsWithImage(dir);
            DefaultComboBoxModel<ActionItem> modelA = new DefaultComboBoxModel<ActionItem>();
            for (ActionItem item : all) {
                modelA.addElement(item);
            }
            actionACombo.setModel(modelA);

            int aIndex = preferAIndex;
            if (aIndex < 0 || !ActionFrameSource.hasImage(editor, aIndex, dir)) {
                aIndex = all.isEmpty() ? -1 : all.get(0).index;
            }
            selectAction(actionACombo, aIndex);

            // Preserve B when it still has a sheet for this facing; otherwise fall back.
            rebuildBList(aIndex, preferBIndex);
        } finally {
            updatingUi = false;
        }
    }

    private void rebuildBList(int aIndex, int preferBIndex) {
        updatingUi = true;
        try {
            String dir = currentDirection();
            DefaultComboBoxModel<ActionItem> modelB = new DefaultComboBoxModel<ActionItem>();
            LinkedHashSet<Integer> seen = new LinkedHashSet<Integer>();

            // Preferred neighbors from A's next lists, then all others.
            if (aIndex >= 0) {
                for (Neighbor n : collectNeighbors(aIndex)) {
                    if (n.index == aIndex) {
                        continue;
                    }
                    if (!ActionFrameSource.hasImage(editor, n.index, dir)) {
                        continue;
                    }
                    if (seen.add(n.index)) {
                        modelB.addElement(new ActionItem(n.index, editor.getActionName(n.index), n.tag));
                    }
                }
            }
            for (ActionItem item : listActionsWithImage(dir)) {
                if (item.index == aIndex) {
                    continue;
                }
                if (seen.add(item.index)) {
                    modelB.addElement(new ActionItem(item.index, item.name, null));
                }
            }

            actionBCombo.setModel(modelB);

            int bIndex = preferBIndex;
            if (bIndex < 0 || !containsIndex(modelB, bIndex)) {
                bIndex = defaultBIndex(aIndex, modelB);
            }
            selectAction(actionBCombo, bIndex);
        } finally {
            updatingUi = false;
        }
    }

    private int defaultBIndex(int aIndex, DefaultComboBoxModel<ActionItem> modelB) {
        if (modelB.getSize() == 0) {
            return -1;
        }
        if (aIndex < 0) {
            return modelB.getElementAt(0).index;
        }

        // Prefer teleport-in when A is teleport-out.
        String special = editor.getActionSpecial(aIndex);
        if ("teleport-out".equals(special)) {
            List<String> moving = ActionFrameSource.parseActionNames(editor.getActionNext(aIndex, "moving"));
            for (String name : moving) {
                int idx = editor.findAction(name);
                if (containsIndex(modelB, idx)) {
                    return idx;
                }
            }
        }

        String[] order = { "waiting", "moving", "drag" };
        for (String type : order) {
            for (String name : ActionFrameSource.parseActionNames(editor.getEffectiveActionNext(aIndex, type))) {
                int idx = editor.findAction(name);
                if (idx != aIndex && containsIndex(modelB, idx)) {
                    return idx;
                }
            }
        }
        return modelB.getElementAt(0).index;
    }

    private static boolean containsIndex(DefaultComboBoxModel<ActionItem> model, int index) {
        for (int i = 0; i < model.getSize(); i++) {
            if (model.getElementAt(i).index == index) {
                return true;
            }
        }
        return false;
    }

    private static void selectAction(JComboBox<ActionItem> combo, int index) {
        DefaultComboBoxModel<ActionItem> model = (DefaultComboBoxModel<ActionItem>) combo.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            if (model.getElementAt(i).index == index) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        if (model.getSize() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    private List<ActionItem> listActionsWithImage(String direction) {
        List<ActionItem> list = new ArrayList<ActionItem>();
        for (int i = 0; i < editor.getActionCount(); i++) {
            if (ActionFrameSource.hasImage(editor, i, direction)) {
                list.add(new ActionItem(i, editor.getActionName(i), null));
            }
        }
        return list;
    }

    private List<Neighbor> collectNeighbors(int aIndex) {
        List<Neighbor> out = new ArrayList<Neighbor>();
        String[] types = { "waiting", "moving", "drag" };
        for (String type : types) {
            for (String name : ActionFrameSource.parseActionNames(editor.getEffectiveActionNext(aIndex, type))) {
                int idx = editor.findAction(name);
                if (idx >= 0) {
                    out.add(new Neighbor(idx, "next " + type));
                }
            }
        }
        return out;
    }

    private void loadSources() {
        sourceA = null;
        sourceB = null;
        String dir = currentDirection();
        int aIndex = selectedActionIndex(actionACombo);
        int bIndex = selectedActionIndex(actionBCombo);
        try {
            if (aIndex >= 0) {
                sourceA = ActionFrameSource.load(editor, aIndex, dir);
            }
            if (bIndex >= 0) {
                sourceB = ActionFrameSource.load(editor, bIndex, dir);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not decode spritesheet: " + e.getMessage(),
                    "Transition Preview",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not load action: " + e.getMessage(),
                    "Transition Preview",
                    JOptionPane.ERROR_MESSAGE);
        }
        stage.revalidate();
    }

    /** Show the start of action A (sequence start / after A is re-chosen). */
    private void showActionA() {
        phaseA = true;
        animTimeCs = 0;
        refreshStage();
    }

    /**
     * Show the start of action B with A→B handoff framing (onion of A last under
     * B first when onion is enabled). Used when B is re-chosen so anchors for the
     * new successor are visible immediately.
     */
    private void showActionB() {
        if (sourceB == null) {
            showActionA();
            return;
        }
        phaseA = false;
        animTimeCs = 0;
        refreshStage();
    }

    private void resetToStart() {
        showActionA();
    }

    private void refreshStage() {
        stage.revalidate();
        stage.repaint();
        updateStatus();
    }

    private void togglePlay() {
        if (playing) {
            stopPlaying();
        } else {
            if (sourceA == null || sourceB == null) {
                JOptionPane.showMessageDialog(
                        this,
                        "Select two actions that both have a spritesheet for this direction.",
                        "Transition Preview",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            // If finished on last frame of B, restart for another play-through.
            if (!phaseA && sourceB != null && animTimeCs >= sourceB.totalTimeCs - 0.5f) {
                resetToStart();
            }
            startPlaying();
        }
    }

    private void startPlaying() {
        playing = true;
        lastTickNanos = System.nanoTime();
        playButton.setText("Pause");
        timer.start();
        updateStatus();
    }

    private void stopPlaying() {
        playing = false;
        timer.stop();
        playButton.setText("Play");
        updateStatus();
    }

    private void stepOnce() {
        stopPlaying();
        if (sourceA == null) {
            return;
        }
        advanceByCs(10f);
        stage.repaint();
        updateStatus();
    }

    private void onTick() {
        if (!playing) {
            return;
        }
        long now = System.nanoTime();
        float deltaMs = (now - lastTickNanos) / 1_000_000f;
        lastTickNanos = now;
        if (deltaMs < 0 || deltaMs > 250f) {
            deltaMs = TICK_MS;
        }
        float rate = rateSlider.getValue() / 100f;
        float deltaCs = deltaMs * CS_PER_MS * rate;
        advanceByCs(deltaCs);
        stage.repaint();
        updateStatus();
    }

    /**
     * Advances the sequence clock. A plays one full cycle (even if looping),
     * then B plays one full cycle and stops on its last frame.
     */
    private void advanceByCs(float deltaCs) {
        if (sourceA == null) {
            return;
        }
        ActionFrameSource cur = phaseA ? sourceA : sourceB;
        if (cur == null) {
            return;
        }
        // Action speed multiplies animation rate like the wallpaper.
        animTimeCs += deltaCs * cur.speed;

        while (true) {
            cur = phaseA ? sourceA : sourceB;
            if (cur == null) {
                break;
            }
            if (animTimeCs < cur.totalTimeCs) {
                break;
            }
            if (phaseA) {
                // One full play of A, then hand off to B.
                phaseA = false;
                animTimeCs -= sourceA.totalTimeCs;
                if (sourceB == null) {
                    animTimeCs = 0;
                    stopPlaying();
                    break;
                }
                // Keep residual time into B so handoff is continuous.
            } else {
                animTimeCs = sourceB.totalTimeCs - 0.001f;
                if (animTimeCs < 0) {
                    animTimeCs = 0;
                }
                stopPlaying();
                break;
            }
        }
    }

    private float displayScale() {
        return scaleSlider.getValue();
    }

    private void updateStatus() {
        if (sourceA == null) {
            statusLabel.setText("No spritesheet for action A in this direction.");
            return;
        }
        ActionFrameSource cur = phaseA ? sourceA : sourceB;
        if (cur == null) {
            statusLabel.setText("No spritesheet for action B in this direction.");
            return;
        }
        int frame = cur.frameIndexAt((int) animTimeCs);
        String phase = phaseA ? "A" : "B";
        String anchors = String.format(
                "A feet (%.1f, %.1f)%s  ·  B feet (%.1f, %.1f)%s",
                sourceA.getResolvedAnchorX(),
                sourceA.getResolvedAnchorY(),
                anchorNote(sourceA),
                sourceB != null ? sourceB.getResolvedAnchorX() : 0f,
                sourceB != null ? sourceB.getResolvedAnchorY() : 0f,
                sourceB != null ? anchorNote(sourceB) : "");

        String handoff = "";
        if (!phaseA && sourceB != null) {
            // How much the top-left of the draw rect would jump if anchors were wrong
            // is less useful than body origin delta at feet: report anchor delta.
            float dAx = sourceB.getResolvedAnchorX() - sourceA.getResolvedAnchorX();
            float dAy = sourceB.getResolvedAnchorY() - sourceA.getResolvedAnchorY();
            // Frame-size change relative to feet (extent beyond feet):
            float aAbove = sourceA.getResolvedAnchorY();
            float bAbove = sourceB.getResolvedAnchorY();
            float aBelow = sourceA.frameHeight - sourceA.getResolvedAnchorY();
            float bBelow = sourceB.frameHeight - sourceB.getResolvedAnchorY();
            handoff = String.format(
                    "  ·  handoff Δanchor (%.1f, %.1f)  above feet A/B %.0f/%.0f  below %.0f/%.0f",
                    dAx, dAy, aAbove, bAbove, aBelow, bBelow);
        }

        statusLabel.setText(String.format(
                "Phase %s: %s  frame %d/%d  t=%.0f/%d cs  %s%s%s",
                phase,
                cur.actionName,
                frame + 1,
                cur.frameCount,
                animTimeCs,
                cur.totalTimeCs,
                playing ? "playing" : "paused",
                onionCheck.isSelected() && !phaseA ? "  ·  onion on" : "",
                "  ·  " + anchors + handoff));
    }

    private static String anchorNote(ActionFrameSource s) {
        boolean dx = s.usesDefaultAnchorX();
        boolean dy = s.usesDefaultAnchorY();
        if (dx && dy) {
            return " [default]";
        }
        if (dx) {
            return " [default X]";
        }
        if (dy) {
            return " [default Y]";
        }
        return "";
    }

    /**
     * Opens the transition preview. No-op messaging if the initial action has
     * no sheet for either direction.
     */
    public static void showDialog(Component parent, PonyEditor editor, int actionIndex,
            String preferredDirection) {
        if (editor == null) {
            throw new IllegalArgumentException("editor");
        }
        if (editor.getActionCount() < 1) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Add at least one action with sprites before previewing transitions.",
                    "Transition Preview",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String dir = preferredDirection;
        if (dir == null || (!"left".equals(dir) && !"right".equals(dir))) {
            dir = "right";
        }
        if (actionIndex >= 0
                && !ActionFrameSource.hasImage(editor, actionIndex, dir)
                && ActionFrameSource.hasImage(editor, actionIndex, opposite(dir))) {
            dir = opposite(dir);
        }

        boolean any = false;
        for (int i = 0; i < editor.getActionCount(); i++) {
            if (ActionFrameSource.hasImage(editor, i, "left")
                    || ActionFrameSource.hasImage(editor, i, "right")) {
                any = true;
                break;
            }
        }
        if (!any) {
            JOptionPane.showMessageDialog(
                    parent,
                    "No action has a spritesheet yet. Import left/right images first.",
                    "Transition Preview",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        TransitionPreviewDialog dialog = new TransitionPreviewDialog(parent, editor, actionIndex, dir);
        dialog.setVisible(true);
    }

    private static String opposite(String direction) {
        return "left".equals(direction) ? "right" : "left";
    }

    // -------------------------------------------------------------------------
    // Stage
    // -------------------------------------------------------------------------

    private final class StagePanel extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            float scale = displayScale();
            int maxW = 64;
            int maxH = 64;
            int maxAbove = 32;
            int maxBelow = 8;
            int maxLeft = 32;
            int maxRight = 32;
            for (ActionFrameSource s : new ActionFrameSource[] { sourceA, sourceB }) {
                if (s == null) {
                    continue;
                }
                maxW = Math.max(maxW, s.frameWidth);
                maxH = Math.max(maxH, s.frameHeight);
                float ax = s.getResolvedAnchorX();
                float ay = s.getResolvedAnchorY();
                maxAbove = Math.max(maxAbove, Math.round(ay));
                maxBelow = Math.max(maxBelow, Math.round(s.frameHeight - ay));
                maxLeft = Math.max(maxLeft, Math.round(ax));
                maxRight = Math.max(maxRight, Math.round(s.frameWidth - ax));
            }
            int w = Math.round((maxLeft + maxRight) * scale) + PAD * 2;
            int h = Math.round((maxAbove + maxBelow) * scale) + PAD * 2;
            return new Dimension(Math.max(320, w), Math.max(200, h));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                int w = getWidth();
                int h = getHeight();
                paintChecker(g2, w, h);

                float scale = displayScale();
                // Feet locked near bottom-centre of the stage content.
                float feetX = w / 2f;
                float feetY = h - PAD - maxExtentBelow() * scale;

                // Ground line
                g2.setColor(GROUND);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(PAD / 2, Math.round(feetY), w - PAD / 2, Math.round(feetY));

                g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_OFF);

                ActionFrameSource cur = phaseA ? sourceA : sourceB;
                if (cur != null) {
                    int frame = cur.frameIndexAt((int) animTimeCs);

                    // Onion: A's last frame under B when onion is on and we're on B.
                    if (onionCheck.isSelected() && !phaseA && sourceA != null && sourceB != null) {
                        drawFrame(g2, sourceA, sourceA.lastFrameIndex(), feetX, feetY, scale, 0.38f);
                    }

                    drawFrame(g2, cur, frame, feetX, feetY, scale, 1f);
                } else if (sourceA != null) {
                    drawFrame(g2, sourceA, 0, feetX, feetY, scale, 1f);
                }

                // Feet crosshair on top
                paintFeet(g2, Math.round(feetX), Math.round(feetY));
            } finally {
                g2.dispose();
            }
        }

        private float maxExtentBelow() {
            float max = 8f;
            for (ActionFrameSource s : new ActionFrameSource[] { sourceA, sourceB }) {
                if (s == null) {
                    continue;
                }
                max = Math.max(max, s.frameHeight - s.getResolvedAnchorY());
            }
            return max;
        }

        private void paintChecker(Graphics2D g2, int w, int h) {
            int cell = 12;
            for (int y = 0; y < h; y += cell) {
                for (int x = 0; x < w; x += cell) {
                    boolean dark = ((x / cell) + (y / cell)) % 2 == 0;
                    g2.setColor(dark ? CHECKER_A : CHECKER_B);
                    g2.fillRect(x, y, cell, cell);
                }
            }
        }

        private void drawFrame(
                Graphics2D g2,
                ActionFrameSource src,
                int frameIndex,
                float feetX,
                float feetY,
                float scale,
                float alpha) {
            Rectangle srcR = src.sourceRect(frameIndex);
            Rectangle dst = src.destinationRect(feetX, feetY, scale);
            if (alpha < 1f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
            g2.drawImage(
                    src.image,
                    dst.x,
                    dst.y,
                    dst.x + dst.width,
                    dst.y + dst.height,
                    srcR.x,
                    srcR.y,
                    srcR.x + srcR.width,
                    srcR.y + srcR.height,
                    null);
            if (alpha < 1f) {
                g2.setComposite(AlphaComposite.SrcOver);
            }
        }

        private void paintFeet(Graphics2D g2, int cx, int cy) {
            int arm = 12;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(FEET_RING);
            g2.drawLine(cx - arm, cy, cx + arm, cy);
            g2.drawLine(cx, cy - arm, cx, cy + arm);
            g2.setColor(FEET_CORE);
            g2.fillOval(cx - 3, cy - 3, 7, 7);
            g2.setColor(FEET_RING);
            g2.drawOval(cx - 4, cy - 4, 9, 9);
        }
    }

    // -------------------------------------------------------------------------
    // Combo models
    // -------------------------------------------------------------------------

    private static final class ActionItem {
        final int index;
        final String name;
        final String tag;

        ActionItem(int index, String name, String tag) {
            this.index = index;
            this.name = name;
            this.tag = tag;
        }

        @Override
        public String toString() {
            if (tag != null && !tag.isEmpty()) {
                return name + "  (" + tag + ")";
            }
            return name;
        }
    }

    private static final class Neighbor {
        final int index;
        final String tag;

        Neighbor(int index, String tag) {
            this.index = index;
            this.tag = tag;
        }
    }
}
