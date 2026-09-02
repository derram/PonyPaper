package uk.cpjsmith.ponypaper.custom;

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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import uk.cpjsmith.ponypaper.EffectPlacement;
import uk.cpjsmith.ponypaper.PonyDefinition;

/**
 * Modal dialog: composite preview of an effect on its trigger action with
 * editable placement/centering (per facing). Mirrors
 * {@link TransitionPreviewDialog}'s stage/playback and {@link AnchorPickerDialog}'s
 * Apply write-back. Includes a travel-direction scrub so motion-relative
 * placement can be checked on diagonals.
 */
public final class EffectPlacementPreviewDialog extends JDialog {

    private static final int TICK_MS = 33;
    private static final float CS_PER_MS = 0.1f;
    private static final int PAD = 24;

    private static final Color ATTACH_RING = new Color(0x33, 0xCC, 0xFF, 0xEE);
    private static final Color ATTACH_CORE = new Color(0x33, 0xCC, 0xFF, 0x99);
    private static final Color CENTER_RING = new Color(0xFF, 0x99, 0x33, 0xEE);
    private static final Color CENTER_CORE = new Color(0xFF, 0x99, 0x33, 0x99);
    private static final Color BOUNDS_STROKE = new Color(0xAA, 0xAA, 0xAA, 0x88);

    /** Preview travel presets: label plus unit vector (screen +x right, +y down). */
    private static final String[] TRAVEL_LABELS = {
        "Idle",
        "Right", "Up-Right", "Up", "Up-Left",
        "Left", "Down-Left", "Down", "Down-Right"
    };
    private static final float[][] TRAVEL_VECTORS = {
        { 0f, 0f },
        { 1f, 0f }, { 1f, -1f }, { 0f, -1f }, { -1f, -1f },
        { -1f, 0f }, { -1f, 1f }, { 0f, 1f }, { 1f, 1f }
    };

    /**
     * Placement/centering tokens to commit for both facings when the user applies.
     */
    public static final class Result {
        public final String placementRight;
        public final String centeringRight;
        public final String placementLeft;
        public final String centeringLeft;

        public Result(String placementRight, String centeringRight,
                String placementLeft, String centeringLeft) {
            this.placementRight = placementRight;
            this.centeringRight = centeringRight;
            this.placementLeft = placementLeft;
            this.centeringLeft = centeringLeft;
        }
    }

    private final PonyEditor editor;
    private final int effectIndex;
    private final int triggerActionIndex;

    private final StagePanel stage;
    private final JComboBox<String> directionCombo;
    private final JComboBox<String> travelCombo;
    private final JComboBox<String> placementCombo;
    private final JComboBox<String> centeringCombo;
    private final CellGrid placementGrid;
    private final CellGrid centeringGrid;
    private final JButton rerollButton;
    private final JLabel resolvedAnyLabel;
    private final JSlider scaleSlider;
    private final JSlider rateSlider;
    private final JLabel statusLabel;
    private final JButton playButton;

    private ActionFrameSource ponySource;
    private EffectFrameSource effectSource;

    private String draftPlacementRight;
    private String draftCenteringRight;
    private String draftPlacementLeft;
    private String draftCenteringLeft;
    private int resolvedAnyRight = EffectPlacementMath.CELL_CENTER;
    private int resolvedAnyLeft = EffectPlacementMath.CELL_CENTER;

    private float ponyTimeCs;
    private float effectTimeCs;
    private boolean playing;
    private final Timer timer;
    private long lastTickNanos;
    private boolean updatingUi;
    private boolean applied;
    private Result result;

    private final Random random = new Random();

    private EffectPlacementPreviewDialog(
            Component parent,
            PonyEditor editor,
            int effectIndex,
            int triggerActionIndex,
            String initialDirection) {
        super(ownerWindow(parent),
                "Effect Placement Preview — " + editor.getEffectName(effectIndex),
                ModalityType.APPLICATION_MODAL);
        this.editor = editor;
        this.effectIndex = effectIndex;
        this.triggerActionIndex = triggerActionIndex;

        draftPlacementRight = editor.getEffectPlacement(effectIndex, "right");
        draftCenteringRight = editor.getEffectCentering(effectIndex, "right");
        draftPlacementLeft = editor.getEffectPlacement(effectIndex, "left");
        draftCenteringLeft = editor.getEffectCentering(effectIndex, "left");
        rollAnyIfNeeded("right");
        rollAnyIfNeeded("left");

        stage = new StagePanel();
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(EditorTheme.STATUS_BAR);
        statusLabel.setForeground(EditorTheme.STATUS_TEXT);

        directionCombo = new JComboBox<String>(new String[] { "right", "left" });
        if ("left".equals(initialDirection)) {
            directionCombo.setSelectedItem("left");
        } else {
            directionCombo.setSelectedItem("right");
        }

        travelCombo = new JComboBox<String>(TRAVEL_LABELS.clone());
        travelCombo.setSelectedItem("Idle");
        travelCombo.setToolTipText(
                "Simulated travel for motion-relative placement. Idle matches waiting; "
                        + "pick a diagonal to verify wake/trail attach. Only applies when "
                        + "the effect has Motion-relative placement enabled.");

        placementCombo = new JComboBox<String>(PonyDefinition.PLACEMENT_TOKENS.clone());
        centeringCombo = new JComboBox<String>(centeringTokens());
        placementGrid = new CellGrid(true);
        centeringGrid = new CellGrid(false);
        resolvedAnyLabel = new JLabel(" ");
        rerollButton = new JButton("Re-roll Any");
        rerollButton.setToolTipText(
                "Pick a new random cell for Any / Any-Not_Center without changing the token.");

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
        JButton stepButton = new JButton("Step");
        JButton restartButton = new JButton("Restart");
        JButton applyButton = new JButton("Apply");
        JButton cancelButton = new JButton("Cancel");

        directionCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!updatingUi) {
                    stopPlaying();
                    syncCombosFromDraft();
                    syncGridsAndAnyUi();
                    loadSources();
                    resetClocks();
                    refreshStage();
                }
            }
        });
        travelCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!updatingUi) {
                    refreshStage();
                }
            }
        });
        placementCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                String token = (String) placementCombo.getSelectedItem();
                setDraftPlacement(currentDirection(), token);
                if (EffectPlacementMath.isAnyPlacement(token)) {
                    rollAnyIfNeeded(currentDirection());
                }
                syncGridsAndAnyUi();
                refreshStage();
            }
        });
        centeringCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                setDraftCentering(currentDirection(), (String) centeringCombo.getSelectedItem());
                syncGridsAndAnyUi();
                refreshStage();
            }
        });
        placementGrid.setListener(new CellGrid.Listener() {
            public void onCellChosen(int cell) {
                String token = EffectPlacementMath.tokenForCell(cell);
                setDraftPlacement(currentDirection(), token);
                syncCombosFromDraft();
                syncGridsAndAnyUi();
                refreshStage();
            }
        });
        centeringGrid.setListener(new CellGrid.Listener() {
            public void onCellChosen(int cell) {
                String token = EffectPlacementMath.tokenForCell(cell);
                setDraftCentering(currentDirection(), token);
                syncCombosFromDraft();
                syncGridsAndAnyUi();
                refreshStage();
            }
        });
        rerollButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String dir = currentDirection();
                String place = getDraftPlacement(dir);
                if (!EffectPlacementMath.isAnyPlacement(place)) {
                    return;
                }
                int mode = EffectPlacementMath.cellIndex(place);
                int rolled = EffectPlacementMath.pickRandomCell(mode, random);
                setResolvedAny(dir, rolled);
                syncGridsAndAnyUi();
                refreshStage();
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
                resetClocks();
                refreshStage();
            }
        });
        applyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                applied = true;
                result = new Result(
                        draftPlacementRight, draftCenteringRight,
                        draftPlacementLeft, draftCenteringLeft);
                dispose();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                applied = false;
                result = null;
                dispose();
            }
        });

        timer = new Timer(TICK_MS, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onTick();
            }
        });
        timer.setRepeats(true);

        JPanel north = new JPanel(new GridBagLayout());
        north.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        int row = 0;
        row = addToolbarRow(north, row, "Facing:", directionCombo,
                "Scale:", scaleSlider);
        row = addToolbarRow(north, row, "Travel:", travelCombo,
                "Rate %:", rateSlider);

        JPanel pickPanel = new JPanel();
        pickPanel.setLayout(new BoxLayout(pickPanel, BoxLayout.Y_AXIS));
        pickPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Placement / centering"),
                BorderFactory.createEmptyBorder(4, 6, 6, 6)));

        JPanel comboRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        comboRow.add(new JLabel("Placement:"));
        comboRow.add(placementCombo);
        comboRow.add(new JLabel("Centering:"));
        comboRow.add(centeringCombo);
        comboRow.add(rerollButton);
        comboRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickPanel.add(comboRow);

        JPanel grids = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        grids.add(labeledGrid("On pony", placementGrid));
        grids.add(labeledGrid("On effect", centeringGrid));
        grids.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickPanel.add(grids);

        resolvedAnyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickPanel.add(resolvedAnyLabel);

        JLabel tip = new JLabel(
                "Click a cell to set a fixed token. Any / Any-Not_Center stay in the combo; Re-roll picks a preview cell.");
        tip.setForeground(EditorTheme.GUIDE_MUTED);
        tip.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickPanel.add(Box.createVerticalStrut(2));
        pickPanel.add(tip);

        JScrollPane stageScroll = new JScrollPane(stage);
        stageScroll.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        stageScroll.getVerticalScrollBar().setUnitIncrement(16);
        stageScroll.getHorizontalScrollBar().setUnitIncrement(16);

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.NORTH);
        JPanel transport = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        transport.add(playButton);
        transport.add(stepButton);
        transport.add(restartButton);
        south.add(transport, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttons.add(cancelButton);
        buttons.add(applyButton);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel centerStack = new JPanel(new BorderLayout());
        centerStack.add(pickPanel, BorderLayout.NORTH);
        centerStack.add(stageScroll, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(centerStack, BorderLayout.CENTER);
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
                stopPlaying();
                applied = false;
                result = null;
            }

            @Override
            public void windowClosed(WindowEvent e) {
                stopPlaying();
            }
        });

        syncCombosFromDraft();
        syncGridsAndAnyUi();
        loadSources();
        resetClocks();
        updateStatus();

        setMinimumSize(new Dimension(640, 520));
        pack();
        // Cap initial size so tall effects (trees, etc.) scroll inside the stage
        // instead of pushing the dialog below the bottom of the display.
        Dimension size = getSize();
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = Math.max(640, (int) (screen.width * 0.9));
        int maxH = Math.max(520, (int) (screen.height * 0.85));
        if (size.width > maxW || size.height > maxH) {
            setSize(Math.min(size.width, maxW), Math.min(size.height, maxH));
        }
        setLocationRelativeTo(parent);
    }

    private static JPanel labeledGrid(String title, CellGrid grid) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.add(new JLabel(title), BorderLayout.NORTH);
        p.add(grid, BorderLayout.CENTER);
        return p;
    }

    private static int addToolbarRow(
            JPanel form, int row, String label1, JComponent c1, String label2, JComponent c2) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(label1), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.5;
        form.add(c1, c);
        if (label2 != null && c2 != null) {
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            form.add(new JLabel(label2), c);
            c.gridx = 3;
            c.weightx = 0.5;
            c.fill = GridBagConstraints.HORIZONTAL;
            form.add(c2, c);
        }
        return row + 1;
    }

    private static String[] centeringTokens() {
        java.util.ArrayList<String> list = new java.util.ArrayList<String>();
        for (String token : PonyDefinition.PLACEMENT_TOKENS) {
            if (!"Any".equals(token) && !"Any-Not_Center".equals(token)) {
                list.add(token);
            }
        }
        return list.toArray(new String[list.size()]);
    }

    private String currentDirection() {
        Object sel = directionCombo.getSelectedItem();
        return "left".equals(sel) ? "left" : "right";
    }

    private String getDraftPlacement(String dir) {
        return "left".equals(dir) ? draftPlacementLeft : draftPlacementRight;
    }

    private String getDraftCentering(String dir) {
        return "left".equals(dir) ? draftCenteringLeft : draftCenteringRight;
    }

    private void setDraftPlacement(String dir, String token) {
        String canon = PonyDefinition.normalizePlacementToken(token);
        if (canon == null) {
            return;
        }
        if ("left".equals(dir)) {
            draftPlacementLeft = canon;
        } else {
            draftPlacementRight = canon;
        }
    }

    private void setDraftCentering(String dir, String token) {
        String canon = PonyDefinition.normalizePlacementToken(token);
        if (canon == null || "Any".equals(canon) || "Any-Not_Center".equals(canon)) {
            return;
        }
        if ("left".equals(dir)) {
            draftCenteringLeft = canon;
        } else {
            draftCenteringRight = canon;
        }
    }

    private int getResolvedAny(String dir) {
        return "left".equals(dir) ? resolvedAnyLeft : resolvedAnyRight;
    }

    private void setResolvedAny(String dir, int cell) {
        if ("left".equals(dir)) {
            resolvedAnyLeft = cell;
        } else {
            resolvedAnyRight = cell;
        }
    }

    private void rollAnyIfNeeded(String dir) {
        String place = getDraftPlacement(dir);
        if (!EffectPlacementMath.isAnyPlacement(place)) {
            return;
        }
        int mode = EffectPlacementMath.cellIndex(place);
        setResolvedAny(dir, EffectPlacementMath.pickRandomCell(mode, random));
    }

    private void syncCombosFromDraft() {
        updatingUi = true;
        try {
            String dir = currentDirection();
            placementCombo.setSelectedItem(getDraftPlacement(dir));
            centeringCombo.setSelectedItem(getDraftCentering(dir));
        } finally {
            updatingUi = false;
        }
    }

    private void syncGridsAndAnyUi() {
        String dir = currentDirection();
        String place = getDraftPlacement(dir);
        String center = getDraftCentering(dir);
        if (EffectPlacementMath.isAnyPlacement(place)) {
            placementGrid.setSelectedCell(getResolvedAny(dir));
            placementGrid.setAnyMode(true);
            rerollButton.setEnabled(true);
            resolvedAnyLabel.setText("Any preview cell: "
                    + EffectPlacementMath.tokenForCell(getResolvedAny(dir))
                    + "  (token stays " + place + " until you click a fixed cell)");
        } else {
            placementGrid.setSelectedCell(EffectPlacementMath.cellIndex(place));
            placementGrid.setAnyMode(false);
            rerollButton.setEnabled(false);
            resolvedAnyLabel.setText(" ");
        }
        centeringGrid.setSelectedCell(EffectPlacementMath.cellIndex(center));
        centeringGrid.setAnyMode(false);
        placementGrid.repaint();
        centeringGrid.repaint();
    }

    private void loadSources() {
        ponySource = null;
        effectSource = null;
        String dir = currentDirection();
        try {
            ponySource = ActionFrameSource.load(editor, triggerActionIndex, dir);
            effectSource = EffectFrameSource.load(editor, effectIndex, dir);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not decode spritesheet: " + e.getMessage(),
                    "Effect Placement Preview",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not load sheets: " + e.getMessage(),
                    "Effect Placement Preview",
                    JOptionPane.ERROR_MESSAGE);
        }
        stage.revalidate();
    }

    private void resetClocks() {
        ponyTimeCs = 0;
        effectTimeCs = 0;
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
            if (ponySource == null || effectSource == null) {
                JOptionPane.showMessageDialog(
                        this,
                        "Need both the trigger action and effect spritesheets for this facing.",
                        "Effect Placement Preview",
                        JOptionPane.WARNING_MESSAGE);
                return;
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

    private void advanceByCs(float deltaCs) {
        if (ponySource != null) {
            ponyTimeCs += deltaCs * ponySource.speed;
            if (ponySource.loops) {
                if (ponyTimeCs >= ponySource.totalTimeCs) {
                    ponyTimeCs %= ponySource.totalTimeCs;
                }
            } else if (ponyTimeCs > ponySource.totalTimeCs - 0.001f) {
                ponyTimeCs = ponySource.totalTimeCs - 0.001f;
            }
            if (ponyTimeCs < 0) {
                ponyTimeCs = 0;
            }
        }
        if (effectSource != null) {
            effectTimeCs += deltaCs;
            if (effectSource.noLoop) {
                if (effectTimeCs > effectSource.totalTimeCs) {
                    effectTimeCs = effectSource.totalTimeCs;
                }
            } else if (effectTimeCs >= effectSource.totalTimeCs) {
                effectTimeCs %= effectSource.totalTimeCs;
            }
            if (effectTimeCs < 0) {
                effectTimeCs = 0;
            }
        }
    }

    private float displayScale() {
        return scaleSlider.getValue();
    }

    private boolean motionPlacementEnabled() {
        return EffectPlacement.isMotionMode(editor.getEffectPlacementMode(effectIndex));
    }

    private int currentFacing() {
        return "left".equals(currentDirection())
                ? EffectPlacement.FACING_LEFT : EffectPlacement.FACING_RIGHT;
    }

    private float[] currentTravel() {
        int index = travelCombo.getSelectedIndex();
        if (index < 0 || index >= TRAVEL_VECTORS.length) {
            return TRAVEL_VECTORS[0];
        }
        return TRAVEL_VECTORS[index];
    }

    private EffectPlacementMath.Origin currentOrigin(Rectangle ponyBounds, float scale) {
        if (effectSource == null || ponyBounds == null) {
            return null;
        }
        String dir = currentDirection();
        float effectW = effectSource.frameWidth * scale;
        float effectH = effectSource.frameHeight * scale;
        int resolved = getResolvedAny(dir);
        float[] travel = currentTravel();
        return EffectPlacementMath.computeOrigin(
                ponyBounds,
                effectW,
                effectH,
                getDraftPlacement(dir),
                getDraftCentering(dir),
                resolved,
                motionPlacementEnabled(),
                travel[0],
                travel[1],
                currentFacing());
    }

    private void updateStatus() {
        if (ponySource == null) {
            statusLabel.setText("No trigger-action spritesheet for this facing.");
            return;
        }
        if (effectSource == null) {
            statusLabel.setText("No effect spritesheet for this facing.");
            return;
        }
        int ponyFrame = ponySource.frameIndexAt((int) ponyTimeCs);
        int effectFrame = effectSource.frameIndexAt(effectTimeCs);
        String dir = currentDirection();
        String place = getDraftPlacement(dir);
        String center = getDraftCentering(dir);
        String anyNote = "";
        if (EffectPlacementMath.isAnyPlacement(place)) {
            anyNote = " → " + EffectPlacementMath.tokenForCell(getResolvedAny(dir));
        }
        float scale = displayScale();
        // Approximate attach using preferred size feet for status numbers.
        Rectangle ponyBounds = ponySource.destinationRect(0, 0, scale);
        float[] travel = currentTravel();
        boolean motion = motionPlacementEnabled();
        // Recompute relative: attach offsets from origin of pony bounds.
        EffectPlacementMath.Origin o = EffectPlacementMath.computeOrigin(
                new Rectangle(0, 0, ponyBounds.width, ponyBounds.height),
                effectSource.frameWidth * scale,
                effectSource.frameHeight * scale,
                place,
                center,
                getResolvedAny(dir),
                motion,
                travel[0],
                travel[1],
                currentFacing());
        String follow = editor.getEffectFollow(effectIndex) ? "follow" : "planted";
        float duration = editor.getEffectDuration(effectIndex);
        String dur = duration <= 0f ? "until action ends" : duration + "s";
        String playState = playing ? "playing" : "paused";
        String motionNote = motion
                ? ("motion/" + travelCombo.getSelectedItem())
                : "bounds";
        int authoredCell = EffectPlacementMath.isAnyPlacement(place)
                ? getResolvedAny(dir) : EffectPlacementMath.cellIndex(place);
        int attachCell = EffectPlacement.maybeRemapCell(
                motion, authoredCell, travel[0], travel[1], currentFacing());
        String remapNote = "";
        if (motion && attachCell != authoredCell) {
            remapNote = " remap→" + EffectPlacementMath.tokenForCell(attachCell);
        }
        statusLabel.setText(String.format(
                "%s on %s (%s)  ·  pony %d/%d  effect %d/%d  ·  %s → %s%s%s  ·  attach (%.0f, %.0f)  origin (%.0f, %.0f)  ·  %s, %s, %s  ·  %s",
                effectSource.effectName,
                ponySource.actionName,
                dir,
                ponyFrame + 1,
                ponySource.frameCount,
                effectFrame + 1,
                effectSource.frameCount,
                place,
                center,
                anyNote,
                remapNote,
                o.attachX,
                o.attachY,
                o.originX,
                o.originY,
                follow,
                motionNote,
                dur,
                playState));
    }

    /**
     * Opens the placement preview. Returns committed tokens when Apply was
     * pressed; {@code null} on cancel.
     */
    public static Result showDialog(
            Component parent, PonyEditor editor, int effectIndex, String preferredDirection) {
        if (editor == null) {
            throw new IllegalArgumentException("editor");
        }
        if (effectIndex < 0 || effectIndex >= editor.getEffectCount()) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Select an effect first.",
                    "Effect Placement Preview",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        String triggerName = editor.getEffectAction(effectIndex);
        int triggerIndex = editor.findAction(triggerName);
        if (triggerIndex < 0) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Trigger action \"" + triggerName + "\" was not found. Set a valid trigger first.",
                    "Effect Placement Preview",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        String dir = preferredDirection;
        if (dir == null || (!"left".equals(dir) && !"right".equals(dir))) {
            dir = "right";
        }
        dir = preferDirectionWithBothSheets(editor, effectIndex, triggerIndex, dir);
        if (dir == null) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Need the trigger action and effect spritesheets for at least one facing.",
                    "Effect Placement Preview",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        EffectPlacementPreviewDialog dialog = new EffectPlacementPreviewDialog(
                parent, editor, effectIndex, triggerIndex, dir);
        dialog.setVisible(true);
        return dialog.applied ? dialog.result : null;
    }

    /**
     * Prefers {@code preferred} when both sheets exist; otherwise the other
     * facing; otherwise {@code null}.
     */
    private static java.awt.Window ownerWindow(Component parent) {
        if (parent == null) {
            return null;
        }
        return SwingUtilities.getWindowAncestor(parent);
    }

    public static String preferDirectionWithBothSheets(
            PonyEditor editor, int effectIndex, int triggerIndex, String preferred) {
        String other = "left".equals(preferred) ? "right" : "left";
        if (hasBoth(editor, effectIndex, triggerIndex, preferred)) {
            return preferred;
        }
        if (hasBoth(editor, effectIndex, triggerIndex, other)) {
            return other;
        }
        return null;
    }

    private static boolean hasBoth(
            PonyEditor editor, int effectIndex, int triggerIndex, String dir) {
        return ActionFrameSource.hasImage(editor, triggerIndex, dir)
                && EffectFrameSource.hasImage(editor, effectIndex, dir);
    }

    // -------------------------------------------------------------------------
    // Stage
    // -------------------------------------------------------------------------

    private final class StagePanel extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            float[] extents = contentExtents(displayScale());
            int w = Math.round(extents[0] + extents[1]) + PAD * 2;
            int h = Math.round(extents[2] + extents[3]) + PAD * 2;
            return new Dimension(Math.max(360, w), Math.max(240, h));
        }

        /**
         * Content reach from a feet origin at (0,0):
         * {@code [left, right, above, below]} in display pixels (already scaled).
         * Uses the union of the pony draw rect and the effect dest rect so tall
         * props (trees) only grow the stage by what they actually cover.
         */
        private float[] contentExtents(float scale) {
            float left = 48f * scale;
            float right = 48f * scale;
            float above = 48f * scale;
            float below = 16f * scale;
            if (ponySource == null) {
                return new float[] { left, right, above, below };
            }
            Rectangle ponyDst = ponySource.destinationRect(0f, 0f, scale);
            float minX = ponyDst.x;
            float minY = ponyDst.y;
            float maxX = ponyDst.x + ponyDst.width;
            float maxY = ponyDst.y + ponyDst.height;
            if (effectSource != null) {
                EffectPlacementMath.Origin origin = currentOrigin(ponyDst, scale);
                if (origin != null) {
                    float ew = effectSource.frameWidth * scale;
                    float eh = effectSource.frameHeight * scale;
                    minX = Math.min(minX, origin.originX);
                    minY = Math.min(minY, origin.originY);
                    maxX = Math.max(maxX, origin.originX + ew);
                    maxY = Math.max(maxY, origin.originY + eh);
                }
            }
            left = Math.max(left, -minX);
            right = Math.max(right, maxX);
            above = Math.max(above, -minY);
            below = Math.max(below, maxY);
            return new float[] { left, right, above, below };
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                int w = getWidth();
                int h = getHeight();
                paintChecker(g2, w, h);

                float scale = displayScale();
                float[] extents = contentExtents(scale);
                float feetX = w / 2f;
                float feetY = h - PAD - extents[3];

                g2.setColor(EditorTheme.GROUND_STAGE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(PAD / 2, Math.round(feetY), w - PAD / 2, Math.round(feetY));

                g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_OFF);

                if (ponySource == null) {
                    return;
                }

                int ponyFrame = ponySource.frameIndexAt((int) ponyTimeCs);
                Rectangle ponyDst = ponySource.destinationRect(feetX, feetY, scale);
                drawPonyFrame(g2, ponySource, ponyFrame, ponyDst);

                g2.setColor(BOUNDS_STROKE);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(ponyDst.x, ponyDst.y, ponyDst.width - 1, ponyDst.height - 1);

                if (effectSource != null) {
                    EffectPlacementMath.Origin origin = currentOrigin(ponyDst, scale);
                    if (origin != null) {
                        int effectFrame = effectSource.frameIndexAt(effectTimeCs);
                        Rectangle effectDst = origin.effectDestRect(
                                effectSource.frameWidth * scale,
                                effectSource.frameHeight * scale);
                        drawEffectFrame(g2, effectSource, effectFrame, effectDst);

                        g2.setColor(BOUNDS_STROKE);
                        g2.drawRect(
                                effectDst.x, effectDst.y,
                                effectDst.width - 1, effectDst.height - 1);

                        paintMarker(
                                g2,
                                Math.round(origin.attachX),
                                Math.round(origin.attachY),
                                ATTACH_RING,
                                ATTACH_CORE);
                        float[] cw = EffectPlacementMath.cellWeights(
                                EffectPlacementMath.cellIndex(getDraftCentering(currentDirection())));
                        int cx = Math.round(effectDst.x + effectDst.width * cw[0]);
                        int cy = Math.round(effectDst.y + effectDst.height * cw[1]);
                        paintMarker(g2, cx, cy, CENTER_RING, CENTER_CORE);
                    }
                }

                paintFeet(g2, Math.round(feetX), Math.round(feetY));
            } finally {
                g2.dispose();
            }
        }

        private void paintChecker(Graphics2D g2, int w, int h) {
            int cell = 12;
            for (int y = 0; y < h; y += cell) {
                for (int x = 0; x < w; x += cell) {
                    boolean dark = ((x / cell) + (y / cell)) % 2 == 0;
                    g2.setColor(dark ? EditorTheme.CHECKER_A : EditorTheme.CHECKER_B);
                    g2.fillRect(x, y, cell, cell);
                }
            }
        }

        private void drawPonyFrame(
                Graphics2D g2, ActionFrameSource src, int frameIndex, Rectangle dst) {
            BufferedImage frame = src.frameImage(frameIndex);
            g2.drawImage(
                    frame,
                    dst.x,
                    dst.y,
                    dst.x + dst.width,
                    dst.y + dst.height,
                    0,
                    0,
                    src.frameWidth,
                    src.frameHeight,
                    null);
        }

        private void drawEffectFrame(
                Graphics2D g2, EffectFrameSource src, int frameIndex, Rectangle dst) {
            BufferedImage frame = src.frameImage(frameIndex);
            g2.drawImage(
                    frame,
                    dst.x,
                    dst.y,
                    dst.x + dst.width,
                    dst.y + dst.height,
                    0,
                    0,
                    src.frameWidth,
                    src.frameHeight,
                    null);
        }

        private void paintFeet(Graphics2D g2, int cx, int cy) {
            int arm = 12;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(EditorTheme.FEET_RING);
            g2.drawLine(cx - arm, cy, cx + arm, cy);
            g2.drawLine(cx, cy - arm, cx, cy + arm);
            g2.setColor(EditorTheme.FEET_CORE);
            g2.fillOval(cx - 3, cy - 3, 7, 7);
            g2.setColor(EditorTheme.FEET_RING);
            g2.drawOval(cx - 4, cy - 4, 9, 9);
        }

        private void paintMarker(Graphics2D g2, int cx, int cy, Color ring, Color core) {
            int arm = 10;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(ring);
            g2.drawLine(cx - arm, cy, cx + arm, cy);
            g2.drawLine(cx, cy - arm, cx, cy + arm);
            g2.setColor(core);
            g2.fillOval(cx - 3, cy - 3, 7, 7);
            g2.setColor(ring);
            g2.drawOval(cx - 4, cy - 4, 9, 9);
        }
    }

    // -------------------------------------------------------------------------
    // 3×3 cell picker
    // -------------------------------------------------------------------------

    private static final class CellGrid extends JComponent {
        interface Listener {
            void onCellChosen(int cell);
        }

        private static final int CELL_PX = 22;
        private static final int GAP = 2;

        private int selectedCell = EffectPlacementMath.CELL_CENTER;
        private boolean anyMode;
        private Listener listener;

        CellGrid(boolean placementSide) {
            setPreferredSize(new Dimension(
                    3 * CELL_PX + 2 * GAP + 4,
                    3 * CELL_PX + 2 * GAP + 4));
            setToolTipText(placementSide
                    ? "Point on the pony image"
                    : "Point on the effect image");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int cell = cellAt(e.getX(), e.getY());
                    if (cell >= 0) {
                        selectedCell = cell;
                        anyMode = false;
                        repaint();
                        if (listener != null) {
                            listener.onCellChosen(cell);
                        }
                    }
                }
            });
        }

        void setListener(Listener listener) {
            this.listener = listener;
        }

        void setSelectedCell(int cell) {
            if (cell >= 0 && cell <= 8) {
                selectedCell = cell;
            }
        }

        void setAnyMode(boolean anyMode) {
            this.anyMode = anyMode;
        }

        private int cellAt(int x, int y) {
            int ox = 2;
            int oy = 2;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int cx = ox + col * (CELL_PX + GAP);
                    int cy = oy + row * (CELL_PX + GAP);
                    if (x >= cx && x < cx + CELL_PX && y >= cy && y < cy + CELL_PX) {
                        return row * 3 + col;
                    }
                }
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(EditorTheme.CANVAS_DEEP);
                g2.fillRect(0, 0, getWidth(), getHeight());
                int ox = 2;
                int oy = 2;
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int cell = row * 3 + col;
                        int cx = ox + col * (CELL_PX + GAP);
                        int cy = oy + row * (CELL_PX + GAP);
                        boolean selected = cell == selectedCell;
                        if (selected) {
                            g2.setColor(anyMode ? EditorTheme.FEET_DEFAULT_CORE : EditorTheme.SELECTION);
                        } else {
                            g2.setColor(EditorTheme.CHECKER_B);
                        }
                        g2.fillRect(cx, cy, CELL_PX, CELL_PX);
                        g2.setColor(selected ? EditorTheme.ACCENT : EditorTheme.GUIDE);
                        g2.drawRect(cx, cy, CELL_PX - 1, CELL_PX - 1);
                    }
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
