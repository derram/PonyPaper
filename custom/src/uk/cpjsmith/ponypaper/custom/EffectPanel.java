package uk.cpjsmith.ponypaper.custom;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Image;
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
import uk.cpjsmith.ponypaper.PonyDefinition;

/**
 * Detail form for one Desktop Ponies–style effect: trigger, lifetime, placement,
 * and left/right spritesheets. Owned by {@link PonyEditorGUI}'s Effects tab.
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
    private final JComboBox<String> placementRight = new JComboBox<String>(placementTokens());
    private final JComboBox<String> centeringRight = new JComboBox<String>(centeringTokens());
    private final JComboBox<String> placementLeft = new JComboBox<String>(placementTokens());
    private final JComboBox<String> centeringLeft = new JComboBox<String>(centeringTokens());
    private final JTextField timingsLeftField = new JTextField();
    private final JTextField timingsRightField = new JTextField();
    private final JLabel imageLeftStatus = new JLabel(" ");
    private final JLabel imageRightStatus = new JLabel(" ");
    private final JButton checkPlacementButton;

    EffectPanel(Host host) {
        super(new BorderLayout());
        this.host = host;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Effect"),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        setMinimumSize(new Dimension(420, 200));

        JPanel form = new JPanel(new GridBagLayout());
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

        row = addLabeled(form, row, "Duration (s):", durationField,
                "0 = until the trigger action ends. Timed effects may outlive the action.");
        durationField.getDocument().addDocumentListener(new SimpleDoc() {
            @Override
            void changed() {
                commitFloat(durationField, true);
            }
        });

        row = addLabeled(form, row, "Repeat delay (s):", repeatField,
                "0 = spawn once. Otherwise re-spawn while the trigger action is current.");
        repeatField.getDocument().addDocumentListener(new SimpleDoc() {
            @Override
            void changed() {
                commitFloat(repeatField, false);
            }
        });

        followCheck.setToolTipText("When checked, the effect stays glued to the pony; otherwise it is planted.");
        noLoopCheck.setToolTipText("Play the effect sheet once even if the image would loop.");
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
        GridBagConstraints c = constraints(0, row);
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        form.add(followCheck, c);
        row++;
        c = constraints(0, row);
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        form.add(noLoopCheck, c);
        row++;

        row = addCombo(form, row, "Placement right:", placementRight,
                "Point on the pony image when facing right.");
        row = addCombo(form, row, "Centering right:", centeringRight,
                "Point on the effect image aligned to placement (facing right).");
        row = addCombo(form, row, "Placement left:", placementLeft,
                "Point on the pony image when facing left.");
        row = addCombo(form, row, "Centering left:", centeringLeft,
                "Point on the effect image aligned to placement (facing left).");

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

        wireCombo(placementRight, true, true);
        wireCombo(centeringRight, false, true);
        wireCombo(placementLeft, true, false);
        wireCombo(centeringLeft, false, false);

        form.add(spriteBlock("right", timingsRightField, imageRightStatus),
                fullWidth(row++));
        form.add(spriteBlock("left", timingsLeftField, imageLeftStatus),
                fullWidth(row++));

        c = constraints(0, row);
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        form.add(Box.createVerticalGlue(), c);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        setEffect(-1);
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
                placementRight.setSelectedItem("Center");
                centeringRight.setSelectedItem("Center");
                placementLeft.setSelectedItem("Center");
                centeringLeft.setSelectedItem("Center");
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
            placementRight.setSelectedItem(editor.getEffectPlacement(index, "right"));
            centeringRight.setSelectedItem(editor.getEffectCentering(index, "right"));
            placementLeft.setSelectedItem(editor.getEffectPlacement(index, "left"));
            centeringLeft.setSelectedItem(editor.getEffectCentering(index, "left"));
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
        placementRight.setEnabled(enabled);
        centeringRight.setEnabled(enabled);
        placementLeft.setEnabled(enabled);
        centeringLeft.setEnabled(enabled);
        checkPlacementButton.setEnabled(enabled);
        timingsLeftField.setEnabled(enabled);
        timingsRightField.setEnabled(enabled);
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

    private JPanel spriteBlock(String direction, JTextField timingsField, JLabel status) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(direction.substring(0, 1).toUpperCase()
                        + direction.substring(1) + " sprite"),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(button("Import image", e -> importImage(direction)));
        buttons.add(Box.createHorizontalStrut(4));
        buttons.add(button("Import frames", e -> importFrames(direction)));
        buttons.add(Box.createHorizontalStrut(4));
        buttons.add(button("Mirror →", e -> mirrorFacing(direction)));
        buttons.add(Box.createHorizontalStrut(4));
        buttons.add(button("Preview", e -> previewImage(direction)));
        buttons.add(Box.createHorizontalStrut(4));
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
        timingsRow.add(timingsField, BorderLayout.CENTER);
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
            JOptionPane.showMessageDialog(this, wrapPreview(preview), "Image Preview",
                    JOptionPane.PLAIN_MESSAGE);
        } catch (IllegalArgumentException | IOException e) {
            JOptionPane.showMessageDialog(this,
                    "The image could not be decoded. Please load a new image.",
                    "Image Error", JOptionPane.ERROR_MESSAGE);
        }
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

    private static JComponent wrapPreview(SpriteSheetPreview preview) {
        JScrollPane scroll = new JScrollPane(preview);
        scroll.setPreferredSize(new Dimension(520, 360));
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
        GridBagConstraints c = constraints(0, row);
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        form.add(new JLabel(label), c);
        field.setToolTipText(tip);
        c = constraints(1, row);
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
        return row + 1;
    }

    private static int addCombo(JPanel form, int row, String label, JComboBox<String> combo, String tip) {
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
