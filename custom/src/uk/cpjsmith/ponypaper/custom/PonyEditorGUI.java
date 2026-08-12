package uk.cpjsmith.ponypaper.custom;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
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
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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

        DocumentListener anchorXLeftListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                applyAnchorField(anchorXLeftField, "left", true);
            }
        };

        DocumentListener anchorYLeftListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                applyAnchorField(anchorYLeftField, "left", false);
            }
        };

        DocumentListener anchorXRightListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                applyAnchorField(anchorXRightField, "right", true);
            }
        };

        DocumentListener anchorYRightListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                applyAnchorField(anchorYRightField, "right", false);
            }
        };

        private void applyAnchorField(JTextField field, String direction, boolean isX) {
            if (currentIndex < 0) {
                return;
            }
            String text = field.getText().trim();
            if (text.isEmpty()) {
                if (isX) {
                    editor.setActionAnchorX(currentIndex, direction, Float.NaN);
                } else {
                    editor.setActionAnchorY(currentIndex, direction, Float.NaN);
                }
                hasChanges = true;
                return;
            }
            try {
                float value = Float.parseFloat(text);
                if (!Float.isNaN(value) && value >= 0f) {
                    if (isX) {
                        editor.setActionAnchorX(currentIndex, direction, value);
                    } else {
                        editor.setActionAnchorY(currentIndex, direction, value);
                    }
                    hasChanges = true;
                }
            } catch (NumberFormatException ex) {
                // Leave previous value until the field parses cleanly.
            }
        }

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

        ActionListener loopListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionLoops(currentIndex, loopCheckBox.isSelected());
                    hasChanges = true;
                }
            }
        };

        DocumentListener spritesFromListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    try {
                        editor.setActionSpritesFrom(currentIndex, spritesFromField.getText());
                        hasChanges = true;
                        refreshSpriteFieldsFromEditor();
                    } catch (IllegalArgumentException ex) {
                        // Incomplete name while typing, or invalid owner — keep typing.
                    }
                }
            }
        };

        DocumentListener gaitsListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    try {
                        editor.setActionGaits(currentIndex, gaitsField.getText());
                        hasChanges = true;
                    } catch (IllegalArgumentException ex) {
                        // Incomplete gaits string while typing.
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

        ActionListener exportLeftListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportSpritesheet("left");
            }
        };
        
        DocumentListener timingsLeftListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionTimings(currentIndex, "left", timingsLeftField.getText());
                    // Editing timings on an alias detaches it into a full owner.
                    if (!editor.getActionSpritesFrom(currentIndex).equals(spritesFromField.getText())) {
                        spritesFromField.setText(editor.getActionSpritesFrom(currentIndex));
                    }
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

        ActionListener exportRightListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportSpritesheet("right");
            }
        };
        
        DocumentListener timingsRightListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionTimings(currentIndex, "right", timingsRightField.getText());
                    if (!editor.getActionSpritesFrom(currentIndex).equals(spritesFromField.getText())) {
                        spritesFromField.setText(editor.getActionSpritesFrom(currentIndex));
                    }
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
        JTextField anchorXLeftField;
        JTextField anchorYLeftField;
        JTextField anchorXRightField;
        JTextField anchorYRightField;
        JButton pickAnchorsLeftButton;
        JButton pickAnchorsRightButton;
        JButton checkTransitionsButton;
        JTextField speedField;
        JCheckBox loopCheckBox;
        JTextField spritesFromField;
        JTextField gaitsField;
        JButton gaitsDefaultButton;
        JButton gaitsIdleButton;
        JButton gaitsClearButton;
        JButton cloneGaitButton;
        JTextField imageLeftField;
        JButton imageLeftPreview;
        JButton imageLeftImport;
        JButton imageLeftExport;
        JTextField timingsLeftField;
        JButton timingsLeftMinus;
        JButton timingsLeftPlus;
        JTextField imageRightField;
        JButton imageRightPreview;
        JButton imageRightImport;
        JButton imageRightExport;
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

            JLabel anchorLeftLabel = new JLabel("Anchors left (X,Y):");
            c = getConstraints(0, 1);
            c.anchor = GridBagConstraints.WEST;
            add(anchorLeftLabel, c);

            anchorXLeftField = new JTextField();
            anchorXLeftField.setToolTipText("Optional. Left sheet feet column in pixels from the left of "
                    + "each frame. Leave empty for frame centre. Often differs from right when sheets are mirrors.");
            anchorXLeftField.getDocument().addDocumentListener(anchorXLeftListener);
            anchorYLeftField = new JTextField();
            anchorYLeftField.setToolTipText("Optional. Left sheet feet row in pixels from the top of each "
                    + "frame. Leave empty for bottom of frame. Set on tall VFX/teleport sheets.");
            anchorYLeftField.getDocument().addDocumentListener(anchorYLeftListener);
            pickAnchorsLeftButton = new JButton("Pick L…");
            pickAnchorsLeftButton.setToolTipText("Pick feet anchors on the left spritesheet.");
            pickAnchorsLeftButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    pickAnchors("left");
                }
            });
            c = getConstraints(1, 1);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(wrapAnchorRow(anchorXLeftField, anchorYLeftField, pickAnchorsLeftButton), c);

            JLabel anchorRightLabel = new JLabel("Anchors right (X,Y):");
            c = getConstraints(0, 2);
            c.anchor = GridBagConstraints.WEST;
            add(anchorRightLabel, c);

            anchorXRightField = new JTextField();
            anchorXRightField.setToolTipText("Optional. Right sheet feet column in pixels from the left of "
                    + "each frame. Leave empty for frame centre. Often differs from left when sheets are mirrors.");
            anchorXRightField.getDocument().addDocumentListener(anchorXRightListener);
            anchorYRightField = new JTextField();
            anchorYRightField.setToolTipText("Optional. Right sheet feet row in pixels from the top of each "
                    + "frame. Leave empty for bottom of frame.");
            anchorYRightField.getDocument().addDocumentListener(anchorYRightListener);
            pickAnchorsRightButton = new JButton("Pick R…");
            pickAnchorsRightButton.setToolTipText("Pick feet anchors on the right spritesheet.");
            pickAnchorsRightButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    pickAnchors("right");
                }
            });
            checkTransitionsButton = new JButton("Check…");
            checkTransitionsButton.setToolTipText("Preview this action transitioning into a next action "
                    + "with feet locked, to verify anchors. Includes onion-skin of A last under B.");
            checkTransitionsButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    checkTransitions();
                }
            });
            c = getConstraints(1, 2);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(wrapAnchorRow(anchorXRightField, anchorYRightField, pickAnchorsRightButton, checkTransitionsButton), c);

            JLabel speedLabel = new JLabel("Speed:");
            c = getConstraints(0, 3);
            c.anchor = GridBagConstraints.WEST;
            add(speedLabel, c);

            speedField = new JTextField();
            speedField.setToolTipText("Travel/animation factor. Typical gaits: 0.5 stroll, 0.7 walk, 1.0 trot.");
            speedField.getDocument().addDocumentListener(speedListener);
            c = getConstraints(1, 3);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(speedField, c);

            JLabel loopLabel = new JLabel("Loop animation:");
            c = getConstraints(0, 4);
            c.anchor = GridBagConstraints.WEST;
            add(loopLabel, c);

            loopCheckBox = new JCheckBox("Loop while active");
            loopCheckBox.setSelected(true);
            loopCheckBox.setToolTipText("Uncheck for one-shot transitions (intros/outros/reactions). "
                    + "After one play, advances via Next waiting/moving/drag for the current motion.");
            loopCheckBox.addActionListener(loopListener);
            c = getConstraints(1, 4);
            c.weighty = 1.0;
            c.anchor = GridBagConstraints.WEST;
            add(loopCheckBox, c);

            JLabel spritesFromLabel = new JLabel("Sprites from:");
            c = getConstraints(0, 5);
            c.anchor = GridBagConstraints.WEST;
            add(spritesFromLabel, c);

            spritesFromField = new JTextField();
            spritesFromField.setToolTipText("Reuse another action's bitmaps (leave empty to own sprites). "
                    + "Alias needs only speed + next lists. Tab completes owner action names.");
            spritesFromField.getDocument().addDocumentListener(spritesFromListener);
            ActionNameCompleter.install(spritesFromField, new ActionNameCompleter.CandidateSource() {
                @Override
                public List<String> getCandidates() {
                    return ActionNameCompleter.spriteOwnerCandidates(editor);
                }
            }, false);
            c = getConstraints(1, 5);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(spritesFromField, c);

            JLabel gaitsLabel = new JLabel("Gaits:");
            c = getConstraints(0, 6);
            c.anchor = GridBagConstraints.WEST;
            add(gaitsLabel, c);

            gaitsField = new JTextField();
            gaitsField.setToolTipText("Load-time bag speed:weight,... e.g. 0.5:1,0.7:3,1:1. Empty = single speed.");
            gaitsField.getDocument().addDocumentListener(gaitsListener);
            gaitsDefaultButton = new JButton("Ground");
            gaitsDefaultButton.setToolTipText("Built-in ground bag: 0.5:1,0.7:3,1:1");
            gaitsDefaultButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
            gaitsDefaultButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (currentIndex >= 0) {
                        editor.applyDefaultGaits(currentIndex);
                        gaitsField.setText(editor.getActionGaits(currentIndex));
                        hasChanges = true;
                    }
                }
            });
            gaitsIdleButton = new JButton("Idle");
            gaitsIdleButton.setToolTipText("Built-in idle bag: 1:1,0.7:1");
            gaitsIdleButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
            gaitsIdleButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (currentIndex >= 0) {
                        editor.applyDefaultIdleGaits(currentIndex);
                        gaitsField.setText(editor.getActionGaits(currentIndex));
                        hasChanges = true;
                    }
                }
            });
            gaitsClearButton = new JButton("Clear");
            gaitsClearButton.setToolTipText("Remove gait expansion");
            gaitsClearButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
            gaitsClearButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (currentIndex >= 0) {
                        editor.setActionGaits(currentIndex, "");
                        gaitsField.setText("");
                        hasChanges = true;
                    }
                }
            });
            c = getConstraints(1, 6);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(wrapGaitsField(gaitsField, gaitsDefaultButton, gaitsIdleButton, gaitsClearButton), c);

            cloneGaitButton = new JButton("Clone as gait…");
            cloneGaitButton.setToolTipText("Create a new action that reuses this action's sprites at another speed.");
            cloneGaitButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    cloneAsGait();
                }
            });
            c = getConstraints(1, 7);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(cloneGaitButton, c);
            
            JLabel imageLeftLabel = new JLabel("Left sprite:");
            c = getConstraints(0, 8);
            c.anchor = GridBagConstraints.SOUTHWEST;
            add(imageLeftLabel, c);
            
            imageLeftField = new JTextField();
            imageLeftField.setEditable(false);
            c = getConstraints(1, 8);
            c.weighty = 0.5;
            c.anchor = GridBagConstraints.SOUTH;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageLeftField, c);
            
            imageLeftPreview = new JButton("Preview");
            imageLeftPreview.addActionListener(previewLeftListener);
            c = getConstraints(1, 9);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageLeftPreview, c);
            
            imageLeftImport = new JButton("Import image");
            imageLeftImport.addActionListener(importLeftListener);
            imageLeftExport = new JButton("Export Spritesheet");
            imageLeftExport.setToolTipText("Save the left spritesheet as a PNG file.");
            imageLeftExport.addActionListener(exportLeftListener);
            c = getConstraints(1, 10);
            c.weighty = 0.5;
            c.anchor = GridBagConstraints.NORTH;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(wrapImportExportButtons(imageLeftImport, imageLeftExport), c);
            
            JLabel timingsLeftLabel = new JLabel("Left timings:");
            c = getConstraints(0, 11);
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
            c = getConstraints(1, 11);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(wrapTimingsField(timingsLeftField, timingsLeftMinus, timingsLeftPlus), c);
            
            JLabel imageRightLabel = new JLabel("Right sprite:");
            c = getConstraints(0, 12);
            c.anchor = GridBagConstraints.SOUTHWEST;
            add(imageRightLabel, c);
            
            imageRightField = new JTextField();
            imageRightField.setEditable(false);
            c = getConstraints(1, 12);
            c.weighty = 0.5;
            c.anchor = GridBagConstraints.SOUTH;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageRightField, c);
            
            imageRightPreview = new JButton("Preview");
            imageRightPreview.addActionListener(previewRightListener);
            c = getConstraints(1, 13);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(imageRightPreview, c);
            
            imageRightImport = new JButton("Import image");
            imageRightImport.addActionListener(importRightListener);
            imageRightExport = new JButton("Export Spritesheet");
            imageRightExport.setToolTipText("Save the right spritesheet as a PNG file.");
            imageRightExport.addActionListener(exportRightListener);
            c = getConstraints(1, 14);
            c.weighty = 0.5;
            c.anchor = GridBagConstraints.NORTH;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(wrapImportExportButtons(imageRightImport, imageRightExport), c);
            
            JLabel timingsRightLabel = new JLabel("Right timings:");
            c = getConstraints(0, 15);
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
            c = getConstraints(1, 15);
            c.weighty = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(wrapTimingsField(timingsRightField, timingsRightMinus, timingsRightPlus), c);
            
            JLabel nextWaitingLabel = new JLabel("Next waiting actions:");
            c = getConstraints(0, 16);
            c.weighty = 1.0;
            c.anchor = GridBagConstraints.WEST;
            add(nextWaitingLabel, c);
            
            nextWaitingField = new JTextField();
            nextWaitingField.setToolTipText("Comma-separated actions. Use none or - for no successor "
                    + "(one-shots fall through to next moving). Tab completes the token under the caret.");
            nextWaitingField.getDocument().addDocumentListener(nextWaitingListener);
            ActionNameCompleter.install(nextWaitingField, new ActionNameCompleter.CandidateSource() {
                @Override
                public List<String> getCandidates() {
                    return ActionNameCompleter.candidatesFromEditor(editor, true);
                }
            }, true);
            c = getConstraints(1, 16);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(nextWaitingField, c);
            
            JLabel nextMovingLabel = new JLabel("Next moving actions:");
            c = getConstraints(0, 17);
            c.weighty = 1.0;
            c.anchor = GridBagConstraints.WEST;
            add(nextMovingLabel, c);
            
            nextMovingField = new JTextField();
            nextMovingField.setToolTipText("Comma-separated actions. Use none or - for no successor "
                    + "(one-shots fall through to next waiting). Tab completes the token under the caret.");
            nextMovingField.getDocument().addDocumentListener(nextMovingListener);
            ActionNameCompleter.install(nextMovingField, new ActionNameCompleter.CandidateSource() {
                @Override
                public List<String> getCandidates() {
                    return ActionNameCompleter.candidatesFromEditor(editor, true);
                }
            }, true);
            c = getConstraints(1, 17);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(nextMovingField, c);
            
            JLabel nextDragLabel = new JLabel("Next drag actions:");
            c = getConstraints(0, 18);
            c.weighty = 1.0;
            c.anchor = GridBagConstraints.WEST;
            add(nextDragLabel, c);
            
            nextDragField = new JTextField();
            nextDragField.setToolTipText("Comma-separated actions. Must list at least one real action. "
                    + "Tab completes the token under the caret.");
            nextDragField.getDocument().addDocumentListener(nextDragListener);
            ActionNameCompleter.install(nextDragField, new ActionNameCompleter.CandidateSource() {
                @Override
                public List<String> getCandidates() {
                    return ActionNameCompleter.candidatesFromEditor(editor, false);
                }
            }, true);
            c = getConstraints(1, 18);
            c.fill = GridBagConstraints.HORIZONTAL;
            add(nextDragField, c);
            
            setAction(-1);
        }
        
        void setAction(int index) {
            currentIndex = -1;
            
            if (index >= 0) {
                specialTypeField.setText(editor.getActionSpecial(index));
                anchorXLeftField.setText(formatAnchor(editor.getActionAnchorX(index, "left")));
                anchorYLeftField.setText(formatAnchor(editor.getActionAnchorY(index, "left")));
                anchorXRightField.setText(formatAnchor(editor.getActionAnchorX(index, "right")));
                anchorYRightField.setText(formatAnchor(editor.getActionAnchorY(index, "right")));
                speedField.setText(formatSpeed(editor.getActionSpeed(index)));
                loopCheckBox.setSelected(editor.getActionLoops(index));
                spritesFromField.setText(editor.getActionSpritesFrom(index));
                gaitsField.setText(editor.getActionGaits(index));
                fillSpriteFields(index);
                nextWaitingField.setText(editor.getActionNext(index, "waiting"));
                nextMovingField.setText(editor.getActionNext(index, "moving"));
                nextDragField.setText(editor.getActionNext(index, "drag"));
                
                setEnabled(true);
            } else {
                specialTypeField.setText("");
                anchorXLeftField.setText("");
                anchorYLeftField.setText("");
                anchorXRightField.setText("");
                anchorYRightField.setText("");
                speedField.setText("");
                loopCheckBox.setSelected(true);
                spritesFromField.setText("");
                gaitsField.setText("");
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

        private void fillSpriteFields(int index) {
            String from = editor.getActionSpritesFrom(index);
            boolean alias = from != null && !from.isEmpty();
            String leftImg = editor.getActionImage(index, "left");
            String rightImg = editor.getActionImage(index, "right");
            if (alias) {
                imageLeftField.setText(leftImg.isEmpty() ? "(from " + from + ")" : "<image from " + from + ">");
                imageRightField.setText(rightImg.isEmpty() ? "(from " + from + ")" : "<image from " + from + ">");
            } else {
                imageLeftField.setText(leftImg.isEmpty() ? "" : "<image>");
                imageRightField.setText(rightImg.isEmpty() ? "" : "<image>");
            }
            timingsLeftField.setText(editor.getActionTimings(index, "left"));
            timingsRightField.setText(editor.getActionTimings(index, "right"));
        }

        private void refreshSpriteFieldsFromEditor() {
            if (currentIndex >= 0) {
                fillSpriteFields(currentIndex);
            }
        }

        private void cloneAsGait() {
            if (currentIndex < 0) {
                return;
            }
            String suggested = editor.getActionName(currentIndex) + "_walk";
            String name = JOptionPane.showInputDialog(this, "New action name:", suggested);
            if (name == null || name.trim().isEmpty()) {
                return;
            }
            String speedText = JOptionPane.showInputDialog(this,
                    "Speed factor (0.5 stroll, 0.7 walk, 1.0 trot):", "0.7");
            if (speedText == null || speedText.trim().isEmpty()) {
                return;
            }
            try {
                float speed = Float.parseFloat(speedText.trim());
                int newIndex = editor.cloneActionAsGait(currentIndex, name.trim(), speed);
                hasChanges = true;
                // Parent list refresh is handled via PonyEditorGUI.reloadActionList if present;
                // fall back to notifying through a package-visible helper.
                PonyEditorGUI.this.afterCloneGait(newIndex);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid speed.", "Clone as Gait", JOptionPane.ERROR_MESSAGE);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Clone as Gait", JOptionPane.ERROR_MESSAGE);
            }
        }

        private static JPanel wrapGaitsField(JTextField field, JButton... buttons) {
            JPanel buttonsPanel = new JPanel();
            buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
            for (int i = 0; i < buttons.length; i++) {
                if (i > 0) {
                    buttonsPanel.add(Box.createHorizontalStrut(2));
                }
                buttonsPanel.add(buttons[i]);
            }
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.add(field, BorderLayout.CENTER);
            row.add(buttonsPanel, BorderLayout.EAST);
            return row;
        }

        private static String formatSpeed(float speed) {
            if (speed == (int)speed) {
                return Integer.toString((int)speed);
            }
            return Float.toString(speed);
        }

        /** Empty when unset ({@link Float#NaN}); otherwise same formatting as speed. */
        private static String formatAnchor(float anchor) {
            if (Float.isNaN(anchor)) {
                return "";
            }
            return formatSpeed(anchor);
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

        private static JPanel wrapImportExportButtons(JButton importButton, JButton exportButton) {
            JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
            row.add(importButton);
            row.add(exportButton);
            return row;
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
                int frames = frameCountFromTimings(timings);
                JOptionPane.showMessageDialog(this, new SpriteSheetPreview(image, frames), "Image Preview", JOptionPane.PLAIN_MESSAGE);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, "The image could not be decoded. Please load a new image.", "Image Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "The image could not be decoded. Please load a new image.", "Image Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        /**
         * Opens the feet-locked A→B transition preview for the current action so
         * anchors can be verified against next waiting/moving/drag successors.
         */
        void checkTransitions() {
            if (currentIndex < 0) {
                return;
            }
            String direction = "right";
            String rightImg = editor.getActionImage(currentIndex, "right");
            String leftImg = editor.getActionImage(currentIndex, "left");
            boolean hasRight = rightImg != null && !rightImg.isEmpty();
            boolean hasLeft = leftImg != null && !leftImg.isEmpty();
            if (!hasRight && hasLeft) {
                direction = "left";
            }
            TransitionPreviewDialog.showDialog(this, editor, currentIndex, direction);
        }

        /**
         * Opens the visual anchor picker on a left or right spritesheet for the
         * current action, then writes the chosen feet hotspot into the anchor fields.
         */
        void pickAnchors(String direction) {
            if (currentIndex < 0) {
                return;
            }
            if (!"left".equals(direction) && !"right".equals(direction)) {
                return;
            }

            String b64Image = editor.getActionImage(currentIndex, direction);
            String timings = editor.getActionTimings(currentIndex, direction);
            if (b64Image == null || b64Image.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "No " + direction + " spritesheet is available for this action.",
                        "Pick Anchors",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                Base64.Decoder b64Dec = Base64.getDecoder();
                byte[] rawImage = b64Dec.decode(b64Image);
                Image image = ImageIO.read(new ByteArrayInputStream(rawImage));
                if (image == null) {
                    throw new IllegalArgumentException();
                }
                int frames = frameCountFromTimings(timings);
                float initialX = editor.getActionAnchorX(currentIndex, direction);
                float initialY = editor.getActionAnchorY(currentIndex, direction);

                AnchorPickerDialog.Result result = AnchorPickerDialog.showDialog(
                        this, image, frames, initialX, initialY);
                if (result == null) {
                    return;
                }

                // Setting the text fields drives the existing document listeners
                // (editor + hasChanges).
                if ("left".equals(direction)) {
                    anchorXLeftField.setText(formatAnchor(result.anchorX));
                    anchorYLeftField.setText(formatAnchor(result.anchorY));
                } else {
                    anchorXRightField.setText(formatAnchor(result.anchorX));
                    anchorYRightField.setText(formatAnchor(result.anchorY));
                }
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(
                        this,
                        "The image could not be decoded. Please load a new image.",
                        "Image Error",
                        JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        this,
                        "The image could not be decoded. Please load a new image.",
                        "Image Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        private static int frameCountFromTimings(String timings) {
            if (timings == null || timings.trim().isEmpty()) {
                return 1;
            }
            String[] parts = timings.split(",");
            int count = 0;
            for (String part : parts) {
                if (!part.trim().isEmpty()) {
                    count++;
                }
            }
            return count < 1 ? 1 : count;
        }

        private static JPanel wrapFieldWithButton(JTextField field, JButton button) {
            return wrapFieldWithButtons(field, button);
        }

        /** X field + Y field + trailing buttons (Pick / Check). */
        private static JPanel wrapAnchorRow(JTextField xField, JTextField yField, JButton... buttons) {
            JPanel row = new JPanel(new BorderLayout(4, 0));
            JPanel xy = new JPanel(new GridLayout(1, 2, 4, 0));
            xy.add(xField);
            xy.add(yField);
            row.add(xy, BorderLayout.CENTER);
            if (buttons != null && buttons.length > 0) {
                JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
                east.setOpaque(false);
                for (JButton button : buttons) {
                    east.add(button);
                }
                row.add(east, BorderLayout.EAST);
            }
            return row;
        }

        private static JPanel wrapFieldWithButtons(JTextField field, JButton... buttons) {
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.add(field, BorderLayout.CENTER);
            if (buttons != null && buttons.length > 0) {
                JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
                east.setOpaque(false);
                for (JButton button : buttons) {
                    east.add(button);
                }
                row.add(east, BorderLayout.EAST);
            }
            return row;
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

        /**
         * Saves the current action's spritesheet for {@code direction} as a PNG file.
         * Aliases export the owner's sheet. Empty or undecodable images are rejected.
         */
        void exportSpritesheet(String direction) {
            if (currentIndex < 0) {
                return;
            }
            String b64Image = editor.getActionImage(currentIndex, direction);
            if (b64Image == null || b64Image.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "No " + direction + " spritesheet is loaded for this action.",
                        "Export Spritesheet",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            byte[] rawImage;
            try {
                rawImage = Base64.getDecoder().decode(b64Image);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(
                        this,
                        "The image could not be decoded. Please load a new image.",
                        "Export Spritesheet",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String suggestedName = editor.getActionName(currentIndex) + "_" + direction + ".png";
            File previous = fc.getSelectedFile();
            fc.setSelectedFile(new File(fc.getCurrentDirectory(), suggestedName));
            fc.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
            int result = fc.showSaveDialog(this);
            File chosen = result == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
            // Restore previous selection so open/save of XML keeps its last path.
            if (previous != null) {
                fc.setSelectedFile(previous);
            }
            fc.resetChoosableFileFilters();
            if (chosen == null) {
                return;
            }

            File file = ensurePngExtension(chosen);
            if (!confirmOverwrite(file)) {
                return;
            }
            try {
                Files.write(file.toPath(), rawImage);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        this,
                        "Failed to write " + file.getName() + ": " + e.getMessage(),
                        "Export Spritesheet",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        
        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            
            specialTypeField.setEnabled(enabled);
            anchorXLeftField.setEnabled(enabled);
            anchorYLeftField.setEnabled(enabled);
            anchorXRightField.setEnabled(enabled);
            anchorYRightField.setEnabled(enabled);
            pickAnchorsLeftButton.setEnabled(enabled);
            pickAnchorsRightButton.setEnabled(enabled);
            checkTransitionsButton.setEnabled(enabled);
            speedField.setEnabled(enabled);
            loopCheckBox.setEnabled(enabled);
            spritesFromField.setEnabled(enabled);
            gaitsField.setEnabled(enabled);
            gaitsDefaultButton.setEnabled(enabled);
            gaitsIdleButton.setEnabled(enabled);
            gaitsClearButton.setEnabled(enabled);
            cloneGaitButton.setEnabled(enabled);
            imageLeftField.setEnabled(enabled);
            imageLeftPreview.setEnabled(enabled);
            imageLeftImport.setEnabled(enabled);
            imageLeftExport.setEnabled(enabled);
            timingsLeftField.setEnabled(enabled);
            timingsLeftMinus.setEnabled(enabled);
            timingsLeftPlus.setEnabled(enabled);
            imageRightField.setEnabled(enabled);
            imageRightPreview.setEnabled(enabled);
            imageRightImport.setEnabled(enabled);
            imageRightExport.setEnabled(enabled);
            timingsRightField.setEnabled(enabled);
            timingsRightMinus.setEnabled(enabled);
            timingsRightPlus.setEnabled(enabled);
            nextWaitingField.setEnabled(enabled);
            nextMovingField.setEnabled(enabled);
            nextDragField.setEnabled(enabled);
        }
        
    }

    /**
     * After cloning a gait alias, rebuild the action list and select the new action.
     */
    void afterCloneGait(int newIndex) {
        actionListModel.clear();
        for (int i = 0; i < editor.getActionCount(); i++) {
            actionListModel.addElement(editor.getActionName(i));
        }
        if (newIndex >= 0 && newIndex < editor.getActionCount()) {
            actionList.setSelectedIndex(newIndex);
        } else if (editor.getActionCount() > 0) {
            actionList.setSelectedIndex(0);
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
        this(parentFrame, null, null, false);
    }

    /**
     * @param parentFrame host frame for dialogs and the window title
     * @param existing    pre-populated editor model, or {@code null} for a blank pony
     * @param initialFile path for title/Save when opened via {@code -load}, or {@code null}
     * @param dirty       whether to treat the model as having unsaved changes
     */
    private PonyEditorGUI(JFrame parentFrame, PonyEditor existing, File initialFile, boolean dirty) {
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
        setFile(initialFile);
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

    /**
     * Ensures a save path ends with {@code .png} when the user omits the extension.
     */
    static File ensurePngExtension(File file) {
        if (file == null) {
            return null;
        }
        String name = file.getName();
        if (name.isEmpty()) {
            return file;
        }
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            File parent = file.getParentFile();
            return parent != null ? new File(parent, name + ".png") : new File(name + ".png");
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
        startActionsField.setToolTipText("Comma-separated entry actions. Tab completes the token under the caret.");
        startActionsField.getDocument().addDocumentListener(startActionsListener);
        ActionNameCompleter.install(startActionsField, new ActionNameCompleter.CandidateSource() {
            @Override
            public List<String> getCandidates() {
                return ActionNameCompleter.candidatesFromEditor(editor, false);
            }
        }, true);
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
    
    private static void createAndShowGUI(PonyEditor existing, File initialFile, boolean dirty) {
        JFrame frame = new JFrame();
        frame.setMinimumSize(new Dimension(600, 450));
        frame.setPreferredSize(new Dimension(800, 600));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        PonyEditorGUI contentPane = new PonyEditorGUI(frame, existing, initialFile, dirty);
        contentPane.setOpaque(true);
        frame.setContentPane(contentPane);
        frame.addWindowListener(contentPane.windowListener);
        
        frame.setJMenuBar(contentPane.createMenuBar());
        
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    public static void start() {
        start(null, null, false);
    }

    /**
     * Starts the GUI, optionally with a pre-filled {@link PonyEditor} (e.g. after
     * {@code -load} or {@code -import-dp} without {@code -save}).
     *
     * @param existing    pre-populated model, or {@code null} for a blank pony
     * @param initialFile path for title/Save when opened via {@code -load}, or {@code null}
     * @param dirty       whether the model should be treated as unsaved
     */
    public static void start(PonyEditor existing, File initialFile, boolean dirty) {
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
                createAndShowGUI(existing, initialFile, dirty);
            }
        });
    }
    
}
