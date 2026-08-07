package uk.cpjsmith.ponypaper.custom;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowListener;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

public class PonyEditorGUI extends JPanel {
    
    private static abstract class MyDocumentListener implements DocumentListener {
        
        public abstract void update(DocumentEvent e);
        
        @Override
        public void insertUpdate(DocumentEvent e) {
            update(e);
        }
        
        @Override
        public void removeUpdate(DocumentEvent e) {
            update(e);
        }
        
        @Override
        public void changedUpdate(DocumentEvent e) {
            update(e);
        }
        
    }
    
    private class ActionPanel extends JPanel {
        
        DocumentListener specialTypeListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionSpecial(currentIndex, specialTypeField.getText());
                    hasChanges = true;
                }
            }
        };

        DocumentListener speedListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    String text = speedField.getText().trim();
                    if (text.isEmpty()) {
                        return;
                    }
                    try {
                        float speed = Float.parseFloat(text);
                        if (speed > 0f && !Float.isNaN(speed)) {
                            editor.setActionSpeed(currentIndex, speed);
                            hasChanges = true;
                        }
                    } catch (NumberFormatException ex) {
                        // Leave previous value until the field parses cleanly.
                    }
                }
            }
        };
        
        ActionListener previewLeftListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewImage(editor.getActionImage(currentIndex, "left"), editor.getActionTimings(currentIndex, "left"));
            }
        };
        
        ActionListener importLeftListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                importImage("left");
            }
        };
        
        DocumentListener timingsLeftListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionTimings(currentIndex, "left", timingsLeftField.getText());
                    hasChanges = true;
                }
            }
        };
        
        ActionListener previewRightListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewImage(editor.getActionImage(currentIndex, "right"), editor.getActionTimings(currentIndex, "right"));
            }
        };
        
        ActionListener importRightListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                importImage("right");
            }
        };
        
        DocumentListener timingsRightListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionTimings(currentIndex, "right", timingsRightField.getText());
                    hasChanges = true;
                }
            }
        };
        
        DocumentListener nextWaitingListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionNext(currentIndex, "waiting", nextWaitingField.getText());
                    hasChanges = true;
                }
            }
        };
        
        DocumentListener nextMovingListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionNext(currentIndex, "moving", nextMovingField.getText());
                    hasChanges = true;
                }
            }
        };
        
        DocumentListener nextDragListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionNext(currentIndex, "drag", nextDragField.getText());
                    hasChanges = true;
                }
            }
        };
        
        JTextField specialTypeField;
        JTextField speedField;
        JTextField imageLeftField;
        JButton imageLeftPreview;
        JButton imageLeftImport;
        JTextField timingsLeftField;
        JButton timingsLeftMinus;
        JButton timingsLeftPlus;
        JTextField imageRightField;
        JButton imageRightPreview;
        JButton imageRightImport;
        JTextField timingsRightField;
        JButton timingsRightMinus;
        JButton timingsRightPlus;
        JTextField nextWaitingField;
        JTextField nextMovingField;
        JTextField nextDragField;
        
        int currentIndex;
        
        ActionPanel() {
            super(new GridBagLayout());
            
            ((GridBagLayout)getLayout()).columnWeights = new double[] { 0.0, 1.0 };
            GridBagConstraints c;
            
            JLabel specialTypeLabel = new JLabel("Special type:");
            c = getConstraints(0, 0);
            c.anchor = GridBagConstraints.WEST;
            add(specialTypeLabel, c);
            
            specialTypeField = new JTextField();
            specialTypeField.getDocument().addDocumentListener(specialTypeListener);
            c = getConstraints(1, 0);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(specialTypeField, c);

            JLabel speedLabel = new JLabel("Speed:");
            c = getConstraints(0, 1);
            c.anchor = GridBagConstraints.WEST;
            add(speedLabel, c);

            speedField = new JTextField();
            speedField.getDocument().addDocumentListener(speedListener);
            c = getConstraints(1, 1);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(speedField, c);
            
            JLabel imageLeftLabel = new JLabel("Left sprite:");
            c = getConstraints(0, 2);
            c.anchor = GridBagConstraints.SOUTHWEST;
            add(imageLeftLabel, c);
            
            imageLeftField = new JTextField();
            imageLeftField.setEditable(false);
            c = getConstraints(1, 2);
            c.weighty = 0.5;
            c.anchor = GridBagConstraints.SOUTH;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageLeftField, c);
            
            imageLeftPreview = new JButton("Preview");
            imageLeftPreview.addActionListener(previewLeftListener);
            c = getConstraints(1, 3);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageLeftPreview, c);
            
            imageLeftImport = new JButton("Import image");
            imageLeftImport.addActionListener(importLeftListener);
            c = getConstraints(1, 4);
            c.weighty = 0.5;
            c.anchor = GridBagConstraints.NORTH;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageLeftImport, c);
            
            JLabel timingsLeftLabel = new JLabel("Left timings:");
            c = getConstraints(0, 5);
            c.anchor = GridBagConstraints.WEST;
            add(timingsLeftLabel, c);
            
            timingsLeftField = new JTextField();
            timingsLeftField.getDocument().addDocumentListener(timingsLeftListener);
            timingsLeftMinus = createTimingsAdjustButton("−", "Subtract 1 from all frame timings (Shift: −5)");
            timingsLeftPlus = createTimingsAdjustButton("+", "Add 1 to all frame timings (Shift: +5)");
            timingsLeftMinus.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    adjustTimingsField(timingsLeftField, e, -1);
                }
            });
            timingsLeftPlus.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    adjustTimingsField(timingsLeftField, e, 1);
                }
            });
            c = getConstraints(1, 5);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(wrapTimingsField(timingsLeftField, timingsLeftMinus, timingsLeftPlus), c);
            
            JLabel imageRightLabel = new JLabel("Right sprite:");
            c = getConstraints(0, 6);
            c.anchor = GridBagConstraints.SOUTHWEST;
            add(imageRightLabel, c);
            
            imageRightField = new JTextField();
            imageRightField.setEditable(false);
            c = getConstraints(1, 6);
            c.weighty = 0.5;
            c.anchor = GridBagConstraints.SOUTH;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageRightField, c);
            
            imageRightPreview = new JButton("Preview");
            imageRightPreview.addActionListener(previewRightListener);
            c = getConstraints(1, 7);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageRightPreview, c);
            
            imageRightImport = new JButton("Import image");
            imageRightImport.addActionListener(importRightListener);
            c = getConstraints(1, 8);
            c.weighty = 0.5;
            c.anchor = GridBagConstraints.NORTH;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageRightImport, c);
            
            JLabel timingsRightLabel = new JLabel("Right timings:");
            c = getConstraints(0, 9);
            c.anchor = GridBagConstraints.WEST;
            add(timingsRightLabel, c);
            
            timingsRightField = new JTextField();
            timingsRightField.getDocument().addDocumentListener(timingsRightListener);
            timingsRightMinus = createTimingsAdjustButton("−", "Subtract 1 from all frame timings (Shift: −5)");
            timingsRightPlus = createTimingsAdjustButton("+", "Add 1 to all frame timings (Shift: +5)");
            timingsRightMinus.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    adjustTimingsField(timingsRightField, e, -1);
                }
            });
            timingsRightPlus.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    adjustTimingsField(timingsRightField, e, 1);
                }
            });
            c = getConstraints(1, 9);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(wrapTimingsField(timingsRightField, timingsRightMinus, timingsRightPlus), c);
            
            JLabel nextWaitingLabel = new JLabel("Next waiting actions:");
            c = getConstraints(0, 10);
            c.weighty = 1.0;
            c.anchor = GridBagConstraints.WEST;
            add(nextWaitingLabel, c);
            
            nextWaitingField = new JTextField();
            nextWaitingField.getDocument().addDocumentListener(nextWaitingListener);
            c = getConstraints(1, 10);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(nextWaitingField, c);
            
            JLabel nextMovingLabel = new JLabel("Next moving actions:");
            c = getConstraints(0, 11);
            c.weighty = 1.0;
            c.anchor = GridBagConstraints.WEST;
            add(nextMovingLabel, c);
            
            nextMovingField = new JTextField();
            nextMovingField.getDocument().addDocumentListener(nextMovingListener);
            c = getConstraints(1, 11);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(nextMovingField, c);
            
            JLabel nextDragLabel = new JLabel("Next drag actions:");
            c = getConstraints(0, 12);
            c.weighty = 1.0;
            c.anchor = GridBagConstraints.WEST;
            add(nextDragLabel, c);
            
            nextDragField = new JTextField();
            nextDragField.getDocument().addDocumentListener(nextDragListener);
            c = getConstraints(1, 12);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(nextDragField, c);
            
            setAction(-1);
        }
        
        void setAction(int index) {
            currentIndex = -1;
            
            if (index >= 0) {
                specialTypeField.setText(editor.getActionSpecial(index));
                speedField.setText(formatSpeed(editor.getActionSpeed(index)));
                imageLeftField.setText(editor.getActionImage(index, "left").isEmpty() ? "" : "<image>");
                timingsLeftField.setText(editor.getActionTimings(index, "left"));
                imageRightField.setText(editor.getActionImage(index, "right").isEmpty() ? "" : "<image>");
                timingsRightField.setText(editor.getActionTimings(index, "right"));
                nextWaitingField.setText(editor.getActionNext(index, "waiting"));
                nextMovingField.setText(editor.getActionNext(index, "moving"));
                nextDragField.setText(editor.getActionNext(index, "drag"));
                
                setEnabled(true);
            } else {
                specialTypeField.setText("");
                speedField.setText("");
                imageLeftField.setText("");
                timingsLeftField.setText("");
                imageRightField.setText("");
                timingsRightField.setText("");
                nextWaitingField.setText("");
                nextMovingField.setText("");
                nextDragField.setText("");
                
                setEnabled(false);
            }
            
            currentIndex = index;
        }

        private static String formatSpeed(float speed) {
            if (speed == (int)speed) {
                return Integer.toString((int)speed);
            }
            return Float.toString(speed);
        }

        /**
         * Add {@code delta} to every comma-separated integer timing, clamping each
         * result to a minimum of 1. Throws {@link NumberFormatException} if the
         * field is empty or any token is not an integer.
         */
        static String adjustAllTimings(String timings, int delta) {
            if (timings == null || timings.trim().isEmpty()) {
                throw new NumberFormatException("empty");
            }
            String[] parts = timings.split(",");
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                int v = Integer.parseInt(parts[i].trim()) + delta;
                if (v < 1) {
                    v = 1;
                }
                if (i > 0) {
                    out.append(',');
                }
                out.append(v);
            }
            return out.toString();
        }

        private static JButton createTimingsAdjustButton(String label, String tooltip) {
            JButton button = new JButton(label);
            button.setToolTipText(tooltip);
            button.setMargin(new java.awt.Insets(2, 6, 2, 6));
            return button;
        }

        private static JPanel wrapTimingsField(JTextField field, JButton minus, JButton plus) {
            JPanel buttons = new JPanel();
            buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
            buttons.add(minus);
            buttons.add(Box.createHorizontalStrut(2));
            buttons.add(plus);

            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.add(field, BorderLayout.CENTER);
            row.add(buttons, BorderLayout.EAST);
            return row;
        }

        private void adjustTimingsField(JTextField field, ActionEvent e, int sign) {
            int step = ((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) ? 5 : 1;
            int delta = sign * step;
            try {
                String adjusted = adjustAllTimings(field.getText(), delta);
                if (!adjusted.equals(field.getText())) {
                    field.setText(adjusted);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Timings must be a comma-separated list of integers.",
                        "Invalid Timings",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
        
        void previewImage(String b64Image, String timings) {
            try {
                Base64.Decoder b64Dec = Base64.getDecoder();
                byte[] rawImage = b64Dec.decode(b64Image);
                Image image = ImageIO.read(new ByteArrayInputStream(rawImage));
                if (image == null) throw new IllegalArgumentException();
                JOptionPane.showMessageDialog(this, new SpriteSheetPreview(image, timings.split(",").length), "Image Preview", JOptionPane.PLAIN_MESSAGE);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, "The image could not be decoded. Please load a new image.", "Image Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "The image could not be decoded. Please load a new image.", "Image Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        
        void importImage(String direction) {
            fc.setFileFilter(new FileNameExtensionFilter("All Supported Formats", "png", "gif"));
            fc.addChoosableFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
            fc.addChoosableFileFilter(new FileNameExtensionFilter("GIF Animations", "gif"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                try {
                    editor.loadActionSprite(currentIndex, direction, file);
                    setAction(currentIndex);
                    hasChanges = true;
                } catch (PonyEditor.GenericException e) {
                    JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
                }
            }
            fc.resetChoosableFileFilters();
        }
        
        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            
            specialTypeField.setEnabled(enabled);
            speedField.setEnabled(enabled);
            imageLeftField.setEnabled(enabled);
            imageLeftPreview.setEnabled(enabled);
            imageLeftImport.setEnabled(enabled);
            timingsLeftField.setEnabled(enabled);
            timingsLeftMinus.setEnabled(enabled);
            timingsLeftPlus.setEnabled(enabled);
            imageRightField.setEnabled(enabled);
            imageRightPreview.setEnabled(enabled);
            imageRightImport.setEnabled(enabled);
            timingsRightField.setEnabled(enabled);
            timingsRightMinus.setEnabled(enabled);
            timingsRightPlus.setEnabled(enabled);
            nextWaitingField.setEnabled(enabled);
            nextMovingField.setEnabled(enabled);
            nextDragField.setEnabled(enabled);
        }
        
    }
    
    private WindowListener windowListener = new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
            if (!checkChanges()) return;
            e.getWindow().dispose();
        }
    };
    
    private ActionListener fileNewListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            if (!checkChanges()) return;
            createNewPony();
        }
    };
    
    private ActionListener fileOpenListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            if (!checkChanges()) return;
            fc.setFileFilter(new FileNameExtensionFilter("XML Files", "xml"));
            if (fc.showOpenDialog(PonyEditorGUI.this) == JFileChooser.APPROVE_OPTION) {
                loadPony(fc.getSelectedFile());
            }
            fc.resetChoosableFileFilters();
        }
    };
    
    private ActionListener fileSaveListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            defaultSave();
        }
    };
    
    private ActionListener fileSaveAsListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            fc.setFileFilter(new FileNameExtensionFilter("XML Files", "xml"));
            if (fc.showSaveDialog(PonyEditorGUI.this) == JFileChooser.APPROVE_OPTION) {
                // Always confirm overwrite when the user picked a path via Save As.
                savePony(ensureXmlExtension(fc.getSelectedFile()), true);
            }
            fc.resetChoosableFileFilters();
        }
    };

    private ActionListener fileImportDesktopPoniesListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            if (!checkChanges()) return;
            importDesktopPonies();
        }
    };
    
    private ListSelectionListener actionListSelectionListener = new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
            actionSettingsPane.setAction(actionList.getSelectedIndex());
        }
    };
    
    private ActionListener newActionListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            String actionName = JOptionPane.showInputDialog(PonyEditorGUI.this, "Enter a name for the new action:", "New Action", JOptionPane.PLAIN_MESSAGE);
            if (actionName != null && !actionName.equals("")) {
                int i = editor.addAction(actionName);
                hasChanges = true;
                
                actionListModel.addElement(actionName);
                actionList.setSelectedIndex(i);
            }
        }
    };
    
    private ActionListener deleteActionListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            int i = actionList.getSelectedIndex();
            if (i != -1) {
                editor.removeAction(i);
                hasChanges = true;
                
                actionListModel.remove(i);
                int newIndex = i < editor.getActionCount() ? i : i - 1;
                if (newIndex >= 0) {
                    actionList.setSelectedIndex(newIndex);
                    // Force-refresh even when the selected index is unchanged so
                    // next-action fields drop the deleted name.
                    actionSettingsPane.setAction(newIndex);
                } else {
                    actionList.clearSelection();
                    actionSettingsPane.setAction(-1);
                }
                // Model already scrubbed start actions; keep the field in sync.
                startActionsField.setText(editor.getStartActions());
            }
        }
    };

    private ActionListener renameActionListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            renameSelectedAction();
        }
    };
    
    private DocumentListener startActionsListener = new MyDocumentListener() {
        public void update(DocumentEvent e) {
            if (!startActionsField.getText().equals(editor.getStartActions())) {
                editor.setStartActions(startActionsField.getText());
                hasChanges = true;
            }
        }
    };
    
    private JFrame parentFrame;
    private DefaultListModel<String> actionListModel;
    private JList<String> actionList;
    private ActionPanel actionSettingsPane;
    private JTextField startActionsField;
    
    private JFileChooser fc;
    
    private PonyEditor editor;
    private File currentFile;
    private boolean hasChanges;
    
    private PonyEditorGUI(JFrame parentFrame) {
        this(parentFrame, null, false);
    }

    /**
     * @param parentFrame host frame for dialogs and the window title
     * @param existing    pre-populated editor model, or {@code null} for a blank pony
     * @param dirty       whether to treat the model as having unsaved changes
     */
    private PonyEditorGUI(JFrame parentFrame, PonyEditor existing, boolean dirty) {
        super(new GridBagLayout());
        
        this.parentFrame = parentFrame;
        
        fc = new JFileChooser(".");
        fc.setAcceptAllFileFilterUsed(false);
        
        GridBagConstraints c;
        
        JComponent actionListPane = createActionListPane();
        c = getConstraints(0, 0);
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        add(actionListPane, c);
        
        actionSettingsPane = new ActionPanel();
        c = getConstraints(1, 0);
        c.weightx = 1.0;
        c.fill = GridBagConstraints.BOTH;
        add(actionSettingsPane, c);
        
        JComponent startActionsPane = createStartActionsPane();
        c = getConstraints(0, 1);
        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = 2;
        add(startActionsPane, c);
        
        editor = existing != null ? existing : new PonyEditor();
        setFile(null);
        hasChanges = dirty;
        if (existing != null) {
            setUIFromPony();
        }
    }

    /**
     * Ensures a save path ends with {@code .xml} when the user omits the extension.
     */
    static File ensureXmlExtension(File file) {
        if (file == null) {
            return null;
        }
        String name = file.getName();
        if (name.isEmpty()) {
            return file;
        }
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
            File parent = file.getParentFile();
            return parent != null ? new File(parent, name + ".xml") : new File(name + ".xml");
        }
        return file;
    }
    
    private void setFile(File file) {
        currentFile = file;
        if (file != null) {
            parentFrame.setTitle("PonyPaper Custom Pony Editor - " + file.getName());
        } else {
            parentFrame.setTitle("PonyPaper Custom Pony Editor");
        }
    }
    
    private void setUIFromPony() {
        actionListModel.clear();
        for (int i = 0; i < editor.getActionCount(); i++) {
            actionListModel.addElement(editor.getActionName(i));
        }
        startActionsField.setText(editor.getStartActions());
        
        if (editor.getActionCount() > 0) {
            actionList.setSelectedIndex(0);
        }
    }
    
    private void createNewPony() {
        editor.reset();
        
        setUIFromPony();
        
        setFile(null);
        hasChanges = false;
    }
    
    private void loadPony(File file) {
        try {
            editor.load(file);
        } catch (PonyEditor.GenericException e) {
            JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        setUIFromPony();
        
        setFile(file);
        hasChanges = false;
    }

    /**
     * Prompts for a Desktop Ponies character folder and imports it into the editor.
     */
    private void importDesktopPonies() {
        File startDir = DesktopPoniesImport.defaultPoniesRoot();
        JFileChooser dirChooser = new JFileChooser(startDir);
        dirChooser.setDialogTitle("Import from Desktop-Ponies");
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        dirChooser.setAcceptAllFileFilterUsed(false);
        dirChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Desktop Ponies character folders (containing pony.ini)";
            }
        });

        if (dirChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File ponyDir = dirChooser.getSelectedFile();
        String[] notes;
        try {
            notes = editor.importDesktopPonies(ponyDir);
        } catch (PonyEditor.GenericException e) {
            JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
            return;
        }

        setUIFromPony();
        // Unsaved import; suggest a filename from the folder name on first Save
        setFile(null);
        hasChanges = true;

        String message = String.join("\n", notes);
        int messageType = JOptionPane.INFORMATION_MESSAGE;
        for (String note : notes) {
            if (note.startsWith("Skipped") || note.startsWith("Omitted") || note.startsWith("Limited")
                    || note.startsWith("No Dragged") || note.startsWith("Duplicate")
                    || note.startsWith("Ignored")) {
                messageType = JOptionPane.WARNING_MESSAGE;
                break;
            }
        }
        JOptionPane.showMessageDialog(this, message, "Desktop-Ponies Import", messageType);
    }
    
    /**
     * Asks before overwriting a file that already exists on disk.
     *
     * @return {@code true} if saving may proceed
     */
    private boolean confirmOverwrite(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(
                this,
                "\"" + file.getName() + "\" already exists.\nDo you want to replace it?",
                "Confirm Overwrite",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /**
     * @param confirmOverwrite when {@code true}, prompt if the target path
     *                         already exists (Save As / first Save with a chooser).
     *                         When {@code false}, write without asking (plain Save
     *                         of the already-open file).
     */
    private boolean savePony(File file, boolean confirmOverwrite) {
        file = ensureXmlExtension(file);
        if (confirmOverwrite && !confirmOverwrite(file)) {
            return false;
        }
        try {
            editor.validate();
        } catch (PonyEditor.GenericException e) {
            int len = e.detail.length;
            String[] messages = Arrays.copyOf(e.detail, len + 2);
            messages[len + 0] = "";
            messages[len + 1] = "Save anyway?";
            if (JOptionPane.showConfirmDialog(this, messages, "Invalid Pony", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return false;
        }
        try {
            editor.save(file);
        } catch (PonyEditor.GenericException e) {
            JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        setFile(file);
        hasChanges = false;
        return true;
    }
    
    /**
     * Saves the pony, prompting for file path only if there is none currently.
     * 
     * @return {@code true} if the pony was saved, {@code false} if the user
     *         cancelled the 'Save as' dialog or some other error occurred
     */
    private boolean defaultSave() {
        File file = currentFile;
        boolean pickedPath = false;
        if (file == null) {
            fc.setFileFilter(new FileNameExtensionFilter("XML Files", "xml"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                file = ensureXmlExtension(fc.getSelectedFile());
                pickedPath = true;
            }
            fc.resetChoosableFileFilters();
        }
        
        if (file != null) {
            // Confirm only when the user just chose a path; re-saving the open file never asks.
            return savePony(file, pickedPath);
        } else {
            return false;
        }
    }
    
    /**
     * Offers to save changes to the pony, if any. The user may chose to save
     * the changes (which is performed by this method), abandon them or cancel
     * whatever operation would cause them to be lost.
     * 
     * @return {@code true} iff the operation should proceed (i.e. either the
     *         changes have been saved or the user has opted to discard them)
     */
    private boolean checkChanges() {
        if (!hasChanges) return true;
        
        switch (JOptionPane.showConfirmDialog(this, "The current pony has unsaved changes. Save now?", "Save Changes", JOptionPane.YES_NO_CANCEL_OPTION)) {
            case JOptionPane.YES_OPTION:
                return defaultSave();
                
            case JOptionPane.NO_OPTION:
                return true;
                
            case JOptionPane.CANCEL_OPTION:
            default:
                return false;
        }
    }
    
    private static GridBagConstraints getConstraints(int gridx, int gridy) {
        GridBagConstraints result = new GridBagConstraints();
        result.gridx = gridx;
        result.gridy = gridy;
        return result;
    }
    
    /**
     * Prompts for a new name for the selected action and rewrites all
     * next/start references to match.
     */
    private void renameSelectedAction() {
        int i = actionList.getSelectedIndex();
        if (i < 0) {
            return;
        }
        String current = editor.getActionName(i);
        String actionName = (String)JOptionPane.showInputDialog(
                this,
                "Enter a new name for the action:",
                "Rename Action",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                current);
        if (actionName == null) {
            return;
        }
        actionName = actionName.trim();
        if (actionName.isEmpty() || actionName.equals(current)) {
            return;
        }
        try {
            editor.setActionName(i, actionName);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Rename Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        hasChanges = true;
        actionListModel.set(i, actionName);
        // Refresh next-action fields and start actions so rewritten names show.
        actionSettingsPane.setAction(i);
        startActionsField.setText(editor.getStartActions());
    }

    private JComponent createActionListPane() {
        JPanel result = new JPanel(new GridBagLayout());
        
        GridBagConstraints c;
        
        actionListModel = new DefaultListModel<String>();
        actionList = new JList<String>(actionListModel);
        actionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionList.setVisibleRowCount(-1);
        actionList.getSelectionModel().addListSelectionListener(actionListSelectionListener);
        actionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && actionList.locationToIndex(e.getPoint()) >= 0) {
                    renameSelectedAction();
                }
            }
        });
        JScrollPane actionListScroller = new JScrollPane(actionList);
        c = getConstraints(0, 0);
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1.0;
        c.gridwidth = 3;
        result.add(actionListScroller, c);
        
        JButton newAction = new JButton("New action");
        newAction.addActionListener(newActionListener);
        c = getConstraints(0, 1);
        result.add(newAction, c);

        JButton renameAction = new JButton("Rename");
        renameAction.setToolTipText("Rename the selected action and update all references");
        renameAction.addActionListener(renameActionListener);
        c = getConstraints(1, 1);
        result.add(renameAction, c);
        
        JButton deleteAction = new JButton("Delete action");
        deleteAction.addActionListener(deleteActionListener);
        c = getConstraints(2, 1);
        result.add(deleteAction, c);
        
        return result;
    }
    
    private JComponent createStartActionsPane() {
        JPanel result = new JPanel(new GridBagLayout());
        
        GridBagConstraints c;
        
        JLabel startActionsLabel = new JLabel("Start actions:");
        c = getConstraints(0, 0);
        c.weighty = 1.0;
        c.anchor = GridBagConstraints.WEST;
        result.add(startActionsLabel, c);
        
        startActionsField = new JTextField();
        startActionsField.getDocument().addDocumentListener(startActionsListener);
        c = getConstraints(1, 0);
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        result.add(startActionsField, c);
        
        return result;
    }
    
    private JMenuBar createMenuBar() {
        JMenuBar result = new JMenuBar();
        
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        
        JMenuItem newPony = new JMenuItem("New");
        newPony.setMnemonic(KeyEvent.VK_N);
        newPony.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        newPony.addActionListener(fileNewListener);
        fileMenu.add(newPony);
        
        JMenuItem open = new JMenuItem("Open...");
        open.setMnemonic(KeyEvent.VK_O);
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        open.addActionListener(fileOpenListener);
        fileMenu.add(open);

        JMenuItem importDp = new JMenuItem("Import from Desktop-Ponies...");
        importDp.setMnemonic(KeyEvent.VK_I);
        importDp.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, ActionEvent.CTRL_MASK));
        importDp.addActionListener(fileImportDesktopPoniesListener);
        fileMenu.add(importDp);
        
        JMenuItem save = new JMenuItem("Save");
        save.setMnemonic(KeyEvent.VK_S);
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        save.addActionListener(fileSaveListener);
        fileMenu.add(save);
        
        JMenuItem saveAs = new JMenuItem("Save As...");
        saveAs.setMnemonic(KeyEvent.VK_A);
        saveAs.addActionListener(fileSaveAsListener);
        fileMenu.add(saveAs);
        
        result.add(fileMenu);
        
        return result;
    }
    
    private static void createAndShowGUI(PonyEditor existing, boolean dirty) {
        JFrame frame = new JFrame();
        frame.setMinimumSize(new Dimension(600, 450));
        frame.setPreferredSize(new Dimension(800, 600));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        PonyEditorGUI contentPane = new PonyEditorGUI(frame, existing, dirty);
        contentPane.setOpaque(true);
        frame.setContentPane(contentPane);
        frame.addWindowListener(contentPane.windowListener);
        
        frame.setJMenuBar(contentPane.createMenuBar());
        
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    public static void start() {
        start(null, false);
    }

    /**
     * Starts the GUI, optionally with a pre-filled {@link PonyEditor} (e.g. after
     * {@code -import-dp} without {@code -save}).
     *
     * @param existing pre-populated model, or {@code null} for a blank pony
     * @param dirty    whether the model should be treated as unsaved
     */
    public static void start(PonyEditor existing, boolean dirty) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if (info.getName().equals("Nimbus")) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                createAndShowGUI(existing, dirty);
            }
        });
    }
    
}
