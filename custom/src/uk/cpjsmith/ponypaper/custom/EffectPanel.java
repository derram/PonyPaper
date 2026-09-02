package uk.cpjsmith.ponypaper.custom;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import uk.cpjsmith.ponypaper.EffectPlacement;
import uk.cpjsmith.ponypaper.PonyDefinition;
import uk.cpjsmith.ponypaper.WanderTarget;

/**
 * Detail form for one Desktop Ponies–style effect: trigger, lifetime, placement,
 * and left/right spritesheets. Owned by {@link PonyEditorGUI}'s Effects tab.
 * When pony Wander is Vertical, effect labels show Back/Front (XML stays left/right).
 * Action sheet remapping is per-action movement; effects stay pony-Wander-based.
 */
final class EffectPanel extends JPanel {

    interface Host {
        PonyEditor editor();
        JFileChooser fileChooser();
        void markDirty();
        Component dialogParent();
    }

    private final Host host;
    private int currentIndex = -1;
    private boolean suppressListeners;

    private final JTextField triggerField = new JTextField();
    private final JTextField durationField = new JTextField();
    private final JTextField repeatField = new JTextField();
    private final JCheckBox followCheck = new JCheckBox("Follow pony");
    private final JCheckBox noLoopCheck = new JCheckBox("Prevent animation loop");
    private final JCheckBox motionPlacementCheck = new JCheckBox("Motion-relative placement");
    private final JComboBox<String> placementLeft = new JComboBox<String>(placementTokens());
    private final JComboBox<String> centeringLeft = new JComboBox<String>(centeringTokens());
    private final JComboBox<String> placementRight = new JComboBox<String>(placementTokens());
    private final JComboBox<String> centeringRight = new JComboBox<String>(centeringTokens());
    private final JTextField timingsLeftField = new JTextField();
    private final JTextField timingsRightField = new JTextField();
    private final JButton timingsLeftMinus;
    private final JButton timingsLeftPlus;
    private final JButton timingsRightMinus;
    private final JButton timingsRightPlus;
    private final JLabel imageLeftStatus = new JLabel(" ");
    private final JLabel imageRightStatus = new JLabel(" ");
    private final JButton checkPlacementButton;
    private JLabel placementLeftLabel;
    private JLabel centeringLeftLabel;
    private JLabel placementRightLabel;
    private JLabel centeringRightLabel;
    private JPanel placementLeftCol;
    private JPanel placementRightCol;
    private JPanel spriteLeftBlock;
    private JPanel spriteRightBlock;

    EffectPanel(Host host) {
        super(new BorderLayout());
        this.host = host;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Effect"),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        setMinimumSize(new Dimension(420, 200));

        VerticalScrollForm form = new VerticalScrollForm(new GridBagLayout());
        int row = 0;
        row = addLabeled(form, row, "Trigger action:", triggerField,
                "Action that starts this effect. Tab completes the name.");
        ActionNameCompleter.install(triggerField, new ActionNameCompleter.CandidateSource() {
            @Override
            public List<String> getCandidates() {
                return ActionNameCompleter.candidatesFromEditor(host.editor(), false);
            }
        }, false);
        triggerField.getDocument().addDocumentListener(new SimpleDoc() {
            @Override
            void changed() {
                if (suppressListeners || currentIndex < 0) {
                    return;
                }
                host.editor().setEffectAction(currentIndex, triggerField.getText());
                host.markDirty();
            }
        });

        durationField.setToolTipText(
                "0 = until the trigger action ends. Timed effects may outlive the action.");
        durationField.getDocument().addDocumentListener(new SimpleDoc() {
            @Override
            void changed() {
                commitFloat(durationField, true);
            }
        });
        repeatField.setToolTipText(
                "0 = spawn once. Otherwise re-spawn while the trigger action is current.");
        repeatField.getDocument().addDocumentListener(new SimpleDoc() {
            @Override
            void changed() {
                commitFloat(repeatField, false);
            }
        });
        // Duration + repeat share one row to cut vertical height.
        JPanel timingRow = new JPanel(new GridBagLayout());
        GridBagConstraints tc = constraints(0, 0);
        tc.anchor = GridBagConstraints.WEST;
        tc.insets = new Insets(0, 0, 0, 8);
        timingRow.add(new JLabel("Duration (s):"), tc);
        tc = constraints(1, 0);
        tc.weightx = 0.5;
        tc.fill = GridBagConstraints.HORIZONTAL;
        tc.insets = new Insets(0, 0, 0, 12);
        timingRow.add(durationField, tc);
        tc = constraints(2, 0);
        tc.anchor = GridBagConstraints.WEST;
        tc.insets = new Insets(0, 0, 0, 8);
        timingRow.add(new JLabel("Repeat delay (s):"), tc);
        tc = constraints(3, 0);
        tc.weightx = 0.5;
        tc.fill = GridBagConstraints.HORIZONTAL;
        timingRow.add(repeatField, tc);
        GridBagConstraints c = fullWidth(row++);
        c.insets = new Insets(2, 0, 2, 0);
        form.add(timingRow, c);

        followCheck.setToolTipText("When checked, the effect stays glued to the pony; otherwise it is planted.");
        noLoopCheck.setToolTipText("Play the effect sheet once even if the image would loop.");
        motionPlacementCheck.setToolTipText(
                "Rotate Left/Right/Top/Bottom attach points with travel so diagonal "
                        + "movers keep trails in their wake. Off = Desktop Ponies bounds attach.");
        followCheck.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (suppressListeners || currentIndex < 0) {
                    return;
                }
                host.editor().setEffectFollow(currentIndex, followCheck.isSelected());
                host.markDirty();
            }
        });
        noLoopCheck.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (suppressListeners || currentIndex < 0) {
                    return;
                }
                host.editor().setEffectNoLoop(currentIndex, noLoopCheck.isSelected());
                host.markDirty();
            }
        });
        motionPlacementCheck.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (suppressListeners || currentIndex < 0) {
                    return;
                }
                host.editor().setEffectPlacementMode(currentIndex,
                        motionPlacementCheck.isSelected()
                                ? EffectPlacement.MODE_MOTION
                                : EffectPlacement.MODE_BOUNDS);
                host.markDirty();
            }
        });
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        checks.add(followCheck);
        checks.add(noLoopCheck);
        checks.add(motionPlacementCheck);
        c = fullWidth(row++);
        c.insets = new Insets(2, 0, 2, 0);
        c.anchor = GridBagConstraints.WEST;
        form.add(checks, c);

        placementLeftLabel = new JLabel("Placement:");
        centeringLeftLabel = new JLabel("Centering:");
        placementRightLabel = new JLabel("Placement:");
        centeringRightLabel = new JLabel("Centering:");
        placementLeftCol = facingPlacementColumn(
                "Facing left", placementLeftLabel, placementLeft, centeringLeftLabel, centeringLeft,
                "Point on the pony image when facing left.",
                "Point on the effect image aligned to placement (facing left).");
        placementRightCol = facingPlacementColumn(
                "Facing right", placementRightLabel, placementRight, centeringRightLabel, centeringRight,
                "Point on the pony image when facing right.",
                "Point on the effect image aligned to placement (facing right).");
        JPanel placementRow = new JPanel(new GridLayout(1, 2, 6, 0));
        placementRow.add(placementLeftCol);
        placementRow.add(placementRightCol);
        form.add(placementRow, fullWidth(row++));

        checkPlacementButton = button("Check placement…", e -> checkPlacement());
        checkPlacementButton.setToolTipText(
                "Preview this effect on its trigger action and adjust placement/centering "
                        + "(like Check… for action anchors).");
        GridBagConstraints checkConstraints = constraints(0, row);
        checkConstraints.gridwidth = 2;
        checkConstraints.anchor = GridBagConstraints.WEST;
        checkConstraints.insets = new Insets(4, 0, 4, 0);
        form.add(checkPlacementButton, checkConstraints);
        row++;

        wireCombo(placementLeft, true, false);
        wireCombo(centeringLeft, false, false);
        wireCombo(placementRight, true, true);
        wireCombo(centeringRight, false, true);

        JButton[] leftAdjust = TimingsAdjust.createPair(timingsLeftField, this);
        timingsLeftMinus = leftAdjust[0];
        timingsLeftPlus = leftAdjust[1];
        JButton[] rightAdjust = TimingsAdjust.createPair(timingsRightField, this);
        timingsRightMinus = rightAdjust[0];
        timingsRightPlus = rightAdjust[1];

        spriteLeftBlock = spriteBlock("left", timingsLeftField, timingsLeftMinus, timingsLeftPlus,
                imageLeftStatus);
        spriteRightBlock = spriteBlock("right", timingsRightField, timingsRightMinus, timingsRightPlus,
                imageRightStatus);
        JPanel spritesRow = new JPanel(new GridLayout(1, 2, 6, 0));
        spritesRow.add(spriteLeftBlock);
        spritesRow.add(spriteRightBlock);
        form.add(spritesRow, fullWidth(row++));

        c = constraints(0, row);
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        form.add(Box.createVerticalGlue(), c);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        setEffect(-1);
    }

    private static JPanel facingPlacementColumn(
            String title,
            JLabel placementLabel,
            JComboBox<String> placement,
            JLabel centeringLabel,
            JComboBox<String> centering,
            String placementTip,
            String centeringTip) {
        JPanel col = new JPanel(new GridBagLayout());
        ((GridBagLayout) col.getLayout()).columnWeights = new double[] { 0.0, 1.0 };
        col.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        placement.setToolTipText(placementTip);
        centering.setToolTipText(centeringTip);
        GridBagConstraints c = constraints(0, 0);
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        col.add(placementLabel, c);
        c = constraints(1, 0);
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        col.add(placement, c);
        c = constraints(0, 1);
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        col.add(centeringLabel, c);
        c = constraints(1, 1);
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        col.add(centering, c);
        return col;
    }

    /**
     * When pony Wander is Vertical, left/right effect slots mean back/front —
     * update labels. XML direction tokens stay left/right.
     */
    void refreshFacingLabels() {
        boolean vertical = WanderTarget.WANDER_VERTICAL.equals(
                WanderTarget.normalizeWander(host.editor().getWander()));
        String leftName = vertical ? "back" : "left";
        String rightName = vertical ? "front" : "right";
        placementLeftLabel.setText("Placement:");
        centeringLeftLabel.setText("Centering:");
        placementRightLabel.setText("Placement:");
        centeringRightLabel.setText("Centering:");
        setTitledBorderTitle(placementLeftCol, "Facing " + leftName);
        setTitledBorderTitle(placementRightCol, "Facing " + rightName);
        placementLeft.setToolTipText("Point on the pony image when facing " + leftName + ".");
        centeringLeft.setToolTipText(
                "Point on the effect image aligned to placement (facing " + leftName + ").");
        placementRight.setToolTipText("Point on the pony image when facing " + rightName + ".");
        centeringRight.setToolTipText(
                "Point on the effect image aligned to placement (facing " + rightName + ").");
        setSpriteBlockTitle(spriteLeftBlock, leftName);
        setSpriteBlockTitle(spriteRightBlock, rightName);
    }

    private static void setSpriteBlockTitle(JPanel block, String directionName) {
        String title = directionName.substring(0, 1).toUpperCase()
                + directionName.substring(1) + " sprite";
        setTitledBorderTitle(block, title);
    }

    private static void setTitledBorderTitle(JPanel block, String title) {
        if (block == null || !(block.getBorder() instanceof CompoundBorder)) {
            return;
        }
        CompoundBorder compound = (CompoundBorder) block.getBorder();
        if (compound.getOutsideBorder() instanceof TitledBorder) {
            ((TitledBorder) compound.getOutsideBorder()).setTitle(title);
            block.repaint();
        }
    }

    void setEffect(int index) {
        currentIndex = index;
        suppressListeners = true;
        try {
            boolean enabled = index >= 0;
            setFormEnabled(enabled);
            if (!enabled) {
                triggerField.setText("");
                durationField.setText("");
                repeatField.setText("");
                followCheck.setSelected(false);
                noLoopCheck.setSelected(false);
                motionPlacementCheck.setSelected(false);
                placementLeft.setSelectedItem("Center");
                centeringLeft.setSelectedItem("Center");
                placementRight.setSelectedItem("Center");
                centeringRight.setSelectedItem("Center");
                timingsLeftField.setText("");
                timingsRightField.setText("");
                imageLeftStatus.setText(" ");
                imageRightStatus.setText(" ");
                return;
            }
            PonyEditor editor = host.editor();
            triggerField.setText(editor.getEffectAction(index));
            durationField.setText(formatFloat(editor.getEffectDuration(index)));
            repeatField.setText(formatFloat(editor.getEffectRepeatDelay(index)));
            followCheck.setSelected(editor.getEffectFollow(index));
            noLoopCheck.setSelected(editor.getEffectNoLoop(index));
            motionPlacementCheck.setSelected(EffectPlacement.isMotionMode(
                    editor.getEffectPlacementMode(index)));
            placementLeft.setSelectedItem(editor.getEffectPlacement(index, "left"));
            centeringLeft.setSelectedItem(editor.getEffectCentering(index, "left"));
            placementRight.setSelectedItem(editor.getEffectPlacement(index, "right"));
            centeringRight.setSelectedItem(editor.getEffectCentering(index, "right"));
            timingsLeftField.setText(editor.getEffectTimings(index, "left"));
            timingsRightField.setText(editor.getEffectTimings(index, "right"));
            refreshImageStatus("left");
            refreshImageStatus("right");
        } finally {
            suppressListeners = false;
        }
    }

    private void setFormEnabled(boolean enabled) {
        triggerField.setEnabled(enabled);
        durationField.setEnabled(enabled);
        repeatField.setEnabled(enabled);
        followCheck.setEnabled(enabled);
        noLoopCheck.setEnabled(enabled);
        motionPlacementCheck.setEnabled(enabled);
        placementLeft.setEnabled(enabled);
        centeringLeft.setEnabled(enabled);
        placementRight.setEnabled(enabled);
        centeringRight.setEnabled(enabled);
        checkPlacementButton.setEnabled(enabled);
        timingsLeftField.setEnabled(enabled);
        timingsLeftMinus.setEnabled(enabled);
        timingsLeftPlus.setEnabled(enabled);
        timingsRightField.setEnabled(enabled);
        timingsRightMinus.setEnabled(enabled);
        timingsRightPlus.setEnabled(enabled);
    }

    private void commitFloat(JTextField field, boolean duration) {
        if (suppressListeners || currentIndex < 0) {
            return;
        }
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            float value = Float.parseFloat(text);
            if (duration) {
                host.editor().setEffectDuration(currentIndex, value);
            } else {
                host.editor().setEffectRepeatDelay(currentIndex, value);
            }
            host.markDirty();
        } catch (IllegalArgumentException ignored) {
            // Leave the typed text; save validation reports bad values.
        }
    }

    private void wireCombo(JComboBox<String> combo, boolean placement, boolean right) {
        combo.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (suppressListeners || currentIndex < 0 || e.getStateChange() != ItemEvent.SELECTED) {
                    return;
                }
                String token = (String)combo.getSelectedItem();
                String direction = right ? "right" : "left";
                try {
                    if (placement) {
                        host.editor().setEffectPlacement(currentIndex, direction, token);
                    } else {
                        host.editor().setEffectCentering(currentIndex, direction, token);
                    }
                    host.markDirty();
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(EffectPanel.this, ex.getMessage(),
                            "Invalid value", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private JPanel spriteBlock(String direction, JTextField timingsField, JButton timingsMinus,
            JButton timingsPlus, JLabel status) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(direction.substring(0, 1).toUpperCase()
                        + direction.substring(1) + " sprite"),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        // 2×3 grid fits half-width columns without a horizontal scrollbar.
        JPanel buttons = new JPanel(new GridLayout(2, 3, 4, 4));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(button("Import image", e -> importImage(direction)));
        buttons.add(button("Import frames", e -> importFrames(direction)));
        buttons.add(button("Mirror →", e -> mirrorFacing(direction)));
        buttons.add(button("Preview", e -> previewImage(direction)));
        buttons.add(button("Export", e -> exportSpritesheet(direction)));
        block.add(buttons);

        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(Box.createVerticalStrut(4));
        block.add(status);

        JPanel timingsRow = new JPanel(new BorderLayout(6, 0));
        timingsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        timingsRow.add(new JLabel("Timings:"), BorderLayout.WEST);
        timingsField.setToolTipText("Comma-separated frame durations in hundredths of a second.");
        timingsField.getDocument().addDocumentListener(new SimpleDoc() {
            @Override
            void changed() {
                if (suppressListeners || currentIndex < 0) {
                    return;
                }
                host.editor().setEffectTimings(currentIndex, direction, timingsField.getText());
                host.markDirty();
            }
        });
        JPanel fieldWithAdjust = TimingsAdjust.wrapField(timingsField, timingsMinus, timingsPlus);
        fieldWithAdjust.setAlignmentX(Component.LEFT_ALIGNMENT);
        timingsRow.add(fieldWithAdjust, BorderLayout.CENTER);
        block.add(Box.createVerticalStrut(4));
        block.add(timingsRow);
        return block;
    }

    private JButton button(String text, ActionListener listener) {
        JButton b = new JButton(text);
        b.addActionListener(listener);
        return b;
    }

    private void refreshImageStatus(String direction) {
        JLabel status = "left".equals(direction) ? imageLeftStatus : imageRightStatus;
        String b64 = host.editor().getEffectImage(currentIndex, direction);
        if (b64 == null || b64.isEmpty()) {
            status.setText("No image");
        } else {
            int frames = ImageImport.countTimings(host.editor().getEffectTimings(currentIndex, direction));
            status.setText("Loaded (" + b64.length() + " chars base64"
                    + (frames > 0 ? ", " + frames + " frame timing(s)" : "") + ")");
        }
    }

    private void importImage(String direction) {
        if (currentIndex < 0) {
            return;
        }
        JFileChooser fc = host.fileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("All Supported Formats", "png", "gif"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("GIF Animations", "gif"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            try {
                if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
                    importGif(direction, file);
                } else {
                    host.editor().loadEffectSprite(currentIndex, direction, file);
                    setEffect(currentIndex);
                    host.markDirty();
                }
            } catch (PonyEditor.GenericException e) {
                JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Import Image Failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        fc.resetChoosableFileFilters();
    }

    private void importGif(String direction, File file) throws IOException, PonyEditor.GenericException {
        ImageImport.GifFrames gif = ImageImport.loadGifFrames(file);
        StringBuilder notes = new StringBuilder();
        notes.append(file.getName()).append(" — ").append(gif.frames.size()).append(" coalesced frame")
                .append(gif.frames.size() == 1 ? "" : "s")
                .append(" at ").append(gif.logicalWidth).append("×").append(gif.logicalHeight).append(".");
        notes.append("\n\n").append(ImageImport.packerScaleNotes());
        String[] names = new String[gif.frames.size()];
        for (int i = 0; i < gif.frames.size(); i++) {
            names[i] = file.getName() + " #" + (i + 1);
        }
        FramePackDialog.Result packed = FramePackDialog.showDialog(
                this,
                "Import GIF (" + direction + ")",
                names,
                gif.frames,
                notes.toString(),
                ImageImport.SCALE_DIVISOR_NATIVE);
        if (packed == null) {
            return;
        }
        List<BufferedImage> frames = ImageImport.permute(gif.frames, packed.order);
        ImageImport.PackOptions options = new ImageImport.PackOptions();
        options.lifts = packed.lifts;
        options.scaleDivisor = packed.scaleDivisor;
        if (gif.timingsCs != null) {
            options.timingsCs = ImageImport.permute(gif.timingsCs, packed.order);
        }
        host.editor().loadEffectSpriteFromFrames(currentIndex, direction, frames, options);
        setEffect(currentIndex);
        host.markDirty();
        previewImage(direction);
    }

    private void importFrames(String direction) {
        if (currentIndex < 0) {
            return;
        }
        JFileChooser fc = host.fileChooser();
        boolean previousMulti = fc.isMultiSelectionEnabled();
        int previousMode = fc.getFileSelectionMode();
        FileFilter previousFilter = fc.getFileFilter();
        fc.setMultiSelectionEnabled(true);
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setAcceptAllFileFilterUsed(true);
        fc.setFileFilter(new PngOrDirFilter());
        int result = fc.showOpenDialog(this);
        File[] chosen = fc.getSelectedFiles();
        fc.setMultiSelectionEnabled(previousMulti);
        fc.setFileSelectionMode(previousMode);
        fc.setFileFilter(previousFilter);
        fc.resetChoosableFileFilters();
        if (result != JFileChooser.APPROVE_OPTION || chosen == null || chosen.length == 0) {
            return;
        }
        try {
            List<File> files = ImageImport.collectFrameFiles(java.util.Arrays.asList(chosen));
            List<BufferedImage> frames = ImageImport.loadFrameImages(files);
            String[] names = new String[files.size()];
            for (int i = 0; i < files.size(); i++) {
                names[i] = files.get(i).getName();
            }
            FramePackDialog.Result packed = FramePackDialog.showDialog(
                    this,
                    "Import frames (" + direction + ")",
                    names,
                    frames,
                    ImageImport.packerScaleNotes(),
                    ImageImport.SCALE_DIVISOR_NATIVE);
            if (packed == null) {
                return;
            }
            List<BufferedImage> ordered = ImageImport.permute(frames, packed.order);
            ImageImport.PackOptions options = new ImageImport.PackOptions();
            options.lifts = packed.lifts;
            options.scaleDivisor = packed.scaleDivisor;
            host.editor().loadEffectSpriteFromFrames(currentIndex, direction, ordered, options);
            setEffect(currentIndex);
            host.markDirty();
            previewImage(direction);
        } catch (PonyEditor.GenericException e) {
            JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Import Frames Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mirrorFacing(String fromDirection) {
        if (currentIndex < 0) {
            return;
        }
        try {
            host.editor().mirrorEffectSprite(currentIndex, fromDirection);
            setEffect(currentIndex);
            host.markDirty();
        } catch (PonyEditor.GenericException e) {
            JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void checkPlacement() {
        if (currentIndex < 0) {
            return;
        }
        // Prefer right when both facings work; dialog may flip if needed.
        EffectPlacementPreviewDialog.Result result = EffectPlacementPreviewDialog.showDialog(
                host.dialogParent(),
                host.editor(),
                currentIndex,
                "right");
        if (result == null) {
            return;
        }
        try {
            host.editor().setEffectPlacement(currentIndex, "right", result.placementRight);
            host.editor().setEffectCentering(currentIndex, "right", result.centeringRight);
            host.editor().setEffectPlacement(currentIndex, "left", result.placementLeft);
            host.editor().setEffectCentering(currentIndex, "left", result.centeringLeft);
            host.markDirty();
            setEffect(currentIndex);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Invalid placement", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void previewImage(String direction) {
        if (currentIndex < 0) {
            return;
        }
        try {
            String b64Image = host.editor().getEffectImage(currentIndex, direction);
            String timings = host.editor().getEffectTimings(currentIndex, direction);
            byte[] rawImage = Base64.getDecoder().decode(b64Image);
            Image image = ImageIO.read(new ByteArrayInputStream(rawImage));
            if (image == null) {
                throw new IllegalArgumentException();
            }
            int frames = Math.max(1, ImageImport.countTimings(timings));
            SpriteSheetPreview preview = new SpriteSheetPreview(image, frames);
            String[] options = { "Open in Packer", "OK" };
            int choice = JOptionPane.showOptionDialog(
                    this,
                    wrapPreview(preview),
                    "Image Preview",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[1]);
            if (choice == 0) {
                openSheetInPacker(direction, image, frames, timings);
            }
        } catch (IllegalArgumentException | IOException e) {
            JOptionPane.showMessageDialog(this,
                    "The image could not be decoded. Please load a new image.",
                    "Image Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Splits the previewed strip into cells and opens the pack dialog so the
     * sheet can be reordered, lifted, or scaled (same path as Actions Preview).
     */
    private void openSheetInPacker(String direction, Image image, int frameCount, String timings) {
        try {
            BufferedImage sheet = SpriteSheetPreview.toBufferedImage(image);
            List<BufferedImage> frames = ImageImport.splitSheet(sheet, frameCount);
            int existingCount = ImageImport.countTimings(timings);
            StringBuilder notes = new StringBuilder();
            notes.append("Split this ").append(direction)
                    .append(" spritesheet into ").append(frames.size())
                    .append(" cell").append(frames.size() == 1 ? "" : "s")
                    .append(" using the current timings.");
            if (existingCount == frames.size()) {
                notes.append("\n\nExisting timings (").append(existingCount)
                        .append(" entries) will be kept if you leave the imported order.");
            } else {
                notes.append("\n\nTimings will be set to ").append(frames.size())
                        .append(" × ").append(ImageImport.DEFAULT_FRAME_TIMING_CS)
                        .append(" (hundredths of a second).");
            }
            notes.append("\n\nList order is playback order — Move up/down, Reverse, or Alt+↑/↓.");
            notes.append("\n\n").append(ImageImport.packerScaleNotes());
            notes.append("\n\nLift is pixels of air under a cell (0 = keep the sprite grounded). ");

            String[] names = new String[frames.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = "frame " + (i + 1);
            }
            FramePackDialog.Result packed = FramePackDialog.showDialog(
                    this,
                    "Pack Spritesheet (" + direction + ")",
                    names,
                    frames,
                    notes.toString(),
                    ImageImport.SCALE_DIVISOR_NATIVE);
            if (packed == null) {
                return;
            }

            applyPackedFrames(direction, frames, timingsCsForPack(timings, frames.size()), packed);
        } catch (PonyEditor.GenericException e) {
            JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Open in Packer Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static int[] timingsCsForPack(String timings, int frameCount) {
        int[] parsed = ActionFrameSource.parseTimings(timings);
        if (parsed.length == frameCount) {
            return parsed;
        }
        return null;
    }

    private void applyPackedFrames(String direction, List<BufferedImage> sourceFrames,
            int[] sourceTimingsCs, FramePackDialog.Result packed)
            throws IOException, PonyEditor.GenericException {
        List<BufferedImage> frames = ImageImport.permute(sourceFrames, packed.order);
        ImageImport.PackOptions options = new ImageImport.PackOptions();
        options.lifts = packed.lifts;
        options.scaleDivisor = packed.scaleDivisor;
        if (sourceTimingsCs != null) {
            options.timingsCs = ImageImport.permute(sourceTimingsCs, packed.order);
        }
        ImageImport imported = host.editor().loadEffectSpriteFromFrames(
                currentIndex, direction, frames, options);
        if (!ImageImport.isIdentityOrder(packed.order)) {
            host.editor().setEffectTimings(currentIndex, direction, imported.timings);
        }
        setEffect(currentIndex);
        host.markDirty();
        previewImage(direction);
    }

    private void exportSpritesheet(String direction) {
        if (currentIndex < 0) {
            return;
        }
        String b64Image = host.editor().getEffectImage(currentIndex, direction);
        if (b64Image == null || b64Image.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No " + direction + " spritesheet is loaded.",
                    "Export Spritesheet", JOptionPane.WARNING_MESSAGE);
            return;
        }
        byte[] rawImage;
        try {
            rawImage = Base64.getDecoder().decode(b64Image);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "The image could not be decoded.",
                    "Export Spritesheet", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fc = host.fileChooser();
        File previous = fc.getSelectedFile();
        String suggested = host.editor().getEffectName(currentIndex) + "_" + direction + ".png";
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), suggested));
        fc.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
        int result = fc.showSaveDialog(this);
        File chosen = result == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
        if (previous != null) {
            fc.setSelectedFile(previous);
        }
        fc.resetChoosableFileFilters();
        if (chosen == null) {
            return;
        }
        File file = PonyEditorGUI.ensurePngExtension(chosen);
        if (file.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    file.getName() + " already exists. Overwrite?",
                    "Export Spritesheet", JOptionPane.YES_NO_OPTION);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            Files.write(file.toPath(), rawImage);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to write " + file.getName() + ": " + e.getMessage(),
                    "Export Spritesheet", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Caps the preview pane so wide or tall sheets scroll instead of forcing
     * {@link JOptionPane} to pack to the full spritesheet size.
     */
    private static JComponent wrapPreview(SpriteSheetPreview preview) {
        JScrollPane scroll = new JScrollPane(preview);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Dimension sheet = preview.getPreferredSize();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = Math.max(480, (int) (screen.width * 0.9) - 80);
        int maxH = Math.max(200, (int) (screen.height * 0.7));
        scroll.setPreferredSize(new Dimension(
                Math.min(sheet.width + 4, maxW),
                Math.min(sheet.height + 4, maxH)));
        return scroll;
    }

    private static String[] placementTokens() {
        return PonyDefinition.PLACEMENT_TOKENS.clone();
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

    private static String formatFloat(float value) {
        if (value == (int)value) {
            return Integer.toString((int)value);
        }
        return Float.toString(value);
    }

    private static int addLabeled(JPanel form, int row, String label, JComponent field, String tip) {
        return addLabeled(form, row, new JLabel(label), field, tip);
    }

    private static int addLabeled(JPanel form, int row, JLabel label, JComponent field, String tip) {
        GridBagConstraints c = constraints(0, row);
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        form.add(label, c);
        field.setToolTipText(tip);
        c = constraints(1, row);
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
        return row + 1;
    }

    private static int addCombo(JPanel form, int row, JLabel label, JComboBox<String> combo, String tip) {
        combo.setToolTipText(tip);
        return addLabeled(form, row, label, combo, tip);
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        return c;
    }

    private static GridBagConstraints fullWidth(int row) {
        GridBagConstraints c = constraints(0, row);
        c.gridwidth = 2;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 0, 0);
        return c;
    }

    private abstract static class SimpleDoc implements DocumentListener {
        abstract void changed();

        @Override
        public void insertUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            changed();
        }
    }

    private static final class PngOrDirFilter extends FileFilter {
        @Override
        public boolean accept(File f) {
            if (f.isDirectory()) {
                return true;
            }
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot < 0) {
                return false;
            }
            return "png".equalsIgnoreCase(name.substring(dot + 1));
        }

        @Override
        public String getDescription() {
            return "PNG frames or folder";
        }
    }
}
