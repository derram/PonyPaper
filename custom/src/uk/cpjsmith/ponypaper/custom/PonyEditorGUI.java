package uk.cpjsmith.ponypaper.custom;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.Component;
import javax.swing.BorderFactory;
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
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
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
                    setDirty(true);
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
                setDirty(true);
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
                    setDirty(true);
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
                            setDirty(true);
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
                    setDirty(true);
                }
            }
        };

        DocumentListener spritesFromListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    try {
                        editor.setActionSpritesFrom(currentIndex, spritesFromField.getText());
                        setDirty(true);
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
                        setDirty(true);
                    } catch (IllegalArgumentException ex) {
                        // Incomplete gaits string while typing.
                    }
                }
            }
        };
        
        ActionListener previewLeftListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewImage("left");
            }
        };

        ActionListener mirrorLeftListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mirrorFacing("left");
            }
        };
        
        ActionListener importLeftListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                importImage("left");
            }
        };

        ActionListener importFramesLeftListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                importFrames("left");
            }
        };

        ActionListener exportLeftListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportSpritesheet("left");
            }
        };

        ActionListener exportFramesLeftListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportFrames("left");
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
                    setDirty(true);
                }
            }
        };
        
        ActionListener previewRightListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewImage("right");
            }
        };

        ActionListener mirrorRightListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mirrorFacing("right");
            }
        };
        
        ActionListener importRightListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                importImage("right");
            }
        };

        ActionListener importFramesRightListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                importFrames("right");
            }
        };

        ActionListener exportRightListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportSpritesheet("right");
            }
        };

        ActionListener exportFramesRightListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportFrames("right");
            }
        };
        
        DocumentListener timingsRightListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionTimings(currentIndex, "right", timingsRightField.getText());
                    if (!editor.getActionSpritesFrom(currentIndex).equals(spritesFromField.getText())) {
                        spritesFromField.setText(editor.getActionSpritesFrom(currentIndex));
                    }
                    setDirty(true);
                }
            }
        };
        
        DocumentListener nextWaitingListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionNext(currentIndex, "waiting", nextWaitingField.getText());
                    setDirty(true);
                }
            }
        };
        
        DocumentListener nextMovingListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionNext(currentIndex, "moving", nextMovingField.getText());
                    setDirty(true);
                }
            }
        };
        
        DocumentListener nextDragListener = new MyDocumentListener() {
            public void update(DocumentEvent e) {
                if (currentIndex >= 0) {
                    editor.setActionNext(currentIndex, "drag", nextDragField.getText());
                    setDirty(true);
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
        JButton imageLeftMirror;
        JButton imageLeftImport;
        JButton imageLeftImportFrames;
        JButton imageLeftExport;
        JButton imageLeftExportFrames;
        JTextField timingsLeftField;
        JButton timingsLeftMinus;
        JButton timingsLeftPlus;
        JTextField imageRightField;
        JButton imageRightPreview;
        JButton imageRightMirror;
        JButton imageRightImport;
        JButton imageRightImportFrames;
        JButton imageRightExport;
        JButton imageRightExportFrames;
        JTextField timingsRightField;
        JButton timingsRightMinus;
        JButton timingsRightPlus;
        JTextField nextWaitingField;
        JTextField nextMovingField;
        JTextField nextDragField;
        
        int currentIndex;
        
        ActionPanel() {
            super(new BorderLayout());

            JPanel identity = newSection("Identity & motion");
            JPanel anchors = newSection("Anchors");
            JPanel leftSprites = newSection("Sprites — left");
            JPanel rightSprites = newSection("Sprites — right");
            JPanel transitions = newSection("Transitions");

            // --- Identity & motion ---
            specialTypeField = new JTextField();
            constrainGrowableField(specialTypeField);
            specialTypeField.setToolTipText("Usually blank. teleport-out / teleport-in jump to a "
                    + "destination. screen-in appears on-screen; screen-out vanishes in place "
                    + "(1-in-8 leave chance, same as walking off).");
            specialTypeField.getDocument().addDocumentListener(specialTypeListener);
            addFormRow(identity, 0, new JLabel("Special type:"), specialTypeField, 1.0);

            speedField = new JTextField();
            speedField.setToolTipText("Travel/animation factor. Typical gaits: 0.5 stroll, 0.7 walk, 1.0 trot.");
            speedField.getDocument().addDocumentListener(speedListener);
            addFormRow(identity, 1, new JLabel("Speed:"), speedField, 1.0);

            loopCheckBox = new JCheckBox("Loop while active");
            loopCheckBox.setSelected(true);
            loopCheckBox.setToolTipText("Uncheck for one-shot transitions (intros/outros/reactions). "
                    + "After one play, advances via Next waiting/moving or Default drag / drag override.");
            loopCheckBox.addActionListener(loopListener);
            addFormRow(identity, 2, new JLabel("Loop animation:"), loopCheckBox, 1.0, GridBagConstraints.WEST, false);

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
            addFormRow(identity, 3, new JLabel("Sprites from:"), spritesFromField, 1.0);

            gaitsField = new JTextField();
            constrainGrowableField(gaitsField);
            gaitsField.setToolTipText("Load-time bag speed:weight,... e.g. 0.5:1,0.7:3,1:1. Empty = single speed.");
            gaitsField.getDocument().addDocumentListener(gaitsListener);
            gaitsDefaultButton = new JButton("Ground");
            gaitsDefaultButton.setToolTipText("Built-in ground bag: 0.5:1,0.7:3,1:1");
            gaitsDefaultButton.setMargin(new Insets(2, 6, 2, 6));
            gaitsDefaultButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (currentIndex >= 0) {
                        editor.applyDefaultGaits(currentIndex);
                        gaitsField.setText(editor.getActionGaits(currentIndex));
                        setDirty(true);
                    }
                }
            });
            gaitsIdleButton = new JButton("Idle");
            gaitsIdleButton.setToolTipText("Built-in idle bag: 1:1,0.7:1");
            gaitsIdleButton.setMargin(new Insets(2, 6, 2, 6));
            gaitsIdleButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (currentIndex >= 0) {
                        editor.applyDefaultIdleGaits(currentIndex);
                        gaitsField.setText(editor.getActionGaits(currentIndex));
                        setDirty(true);
                    }
                }
            });
            gaitsClearButton = new JButton("Clear");
            gaitsClearButton.setToolTipText("Remove gait expansion");
            gaitsClearButton.setMargin(new Insets(2, 6, 2, 6));
            gaitsClearButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (currentIndex >= 0) {
                        editor.setActionGaits(currentIndex, "");
                        gaitsField.setText("");
                        setDirty(true);
                    }
                }
            });
            addFormRow(identity, 4, new JLabel("Gaits:"),
                    wrapGaitsField(gaitsField, gaitsDefaultButton, gaitsIdleButton, gaitsClearButton), 1.0);

            cloneGaitButton = new JButton("Clone as gait…");
            cloneGaitButton.setToolTipText("Create a new action that reuses this action's sprites at another speed.");
            cloneGaitButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    cloneAsGait();
                }
            });
            addFormRow(identity, 5, new JLabel(""), cloneGaitButton, 0.0, GridBagConstraints.WEST, true);

            // --- Anchors ---
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
            addFormRow(anchors, 0, new JLabel("Left (X,Y):"),
                    wrapAnchorRow(anchorXLeftField, anchorYLeftField, pickAnchorsLeftButton), 1.0);

            anchorXRightField = new JTextField();
            anchorXRightField.setToolTipText("Optional. Right sheet feet column in pixels from the left of "
                    + "each frame. Leave empty for frame centre. Often differs from left when sheets are horizontal mirrors.");
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
            addFormRow(anchors, 1, new JLabel("Right (X,Y):"),
                    wrapAnchorRow(anchorXRightField, anchorYRightField, pickAnchorsRightButton, checkTransitionsButton), 1.0);

            // --- Left sprites ---
            imageLeftField = new JTextField();
            imageLeftField.setEditable(false);
            addFormRow(leftSprites, 0, new JLabel("Sheet:"), imageLeftField, 0.5);

            imageLeftPreview = new JButton("Preview");
            imageLeftPreview.addActionListener(previewLeftListener);
            imageLeftMirror = new JButton("Mirror to right");
            imageLeftMirror.setToolTipText(
                    "Build the right spritesheet by flopping each left frame (same order and timings).");
            imageLeftMirror.addActionListener(mirrorLeftListener);
            styleSecondary(imageLeftMirror);
            addFormRow(leftSprites, 1, new JLabel(""), wrapTwoButtons(imageLeftPreview, imageLeftMirror), 0.0);

            imageLeftImport = new JButton("Import image");
            imageLeftImport.setToolTipText(
                    "Load a packed PNG strip as-is, or a GIF (coalesced and packed). "
                            + "GIFs open the same pack dialog as Import frames (scale 100% by default). "
                            + "For padded / uneven sheets, Import as-is then use Export Frames.");
            imageLeftImport.addActionListener(importLeftListener);
            imageLeftImportFrames = new JButton("Import frames");
            imageLeftImportFrames.setToolTipText(
                    "Build a spritesheet from a folder or several PNG frames. "
                            + "Optional dyadic scale (100%…6.25% or fit-to-built-in) and per-frame lift for hops.");
            imageLeftImportFrames.addActionListener(importFramesLeftListener);
            imageLeftExport = new JButton("Export Spritesheet");
            imageLeftExport.setToolTipText("Save the left spritesheet as a PNG file.");
            imageLeftExport.addActionListener(exportLeftListener);
            imageLeftExportFrames = new JButton("Export Frames");
            imageLeftExportFrames.setToolTipText(
                    "Pick frame borders on the left sheet, then Pack… into this action "
                            + "or Export PNGs…. Use for padded or uneven third-party strips.");
            imageLeftExportFrames.addActionListener(exportFramesLeftListener);
            styleSecondary(imageLeftExport);
            styleSecondary(imageLeftExportFrames);
            addFormRow(leftSprites, 2, new JLabel(""), wrapImportExportButtons(
                    imageLeftImport, imageLeftImportFrames, imageLeftExport, imageLeftExportFrames), 0.5);

            timingsLeftField = new JTextField();
            constrainGrowableField(timingsLeftField);
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
            addFormRow(leftSprites, 3, new JLabel("Timings:"),
                    wrapTimingsField(timingsLeftField, timingsLeftMinus, timingsLeftPlus), 1.0);

            // --- Right sprites ---
            imageRightField = new JTextField();
            imageRightField.setEditable(false);
            addFormRow(rightSprites, 0, new JLabel("Sheet:"), imageRightField, 0.5);

            imageRightPreview = new JButton("Preview");
            imageRightPreview.addActionListener(previewRightListener);
            imageRightMirror = new JButton("Mirror to left");
            imageRightMirror.setToolTipText(
                    "Build the left spritesheet by flopping each right frame (same order and timings).");
            imageRightMirror.addActionListener(mirrorRightListener);
            styleSecondary(imageRightMirror);
            addFormRow(rightSprites, 1, new JLabel(""), wrapTwoButtons(imageRightPreview, imageRightMirror), 0.0);

            imageRightImport = new JButton("Import image");
            imageRightImport.setToolTipText(
                    "Load a packed PNG strip as-is, or a GIF (coalesced and packed). "
                            + "GIFs open the same pack dialog as Import frames (scale 100% by default). "
                            + "For padded / uneven sheets, Import as-is then use Export Frames.");
            imageRightImport.addActionListener(importRightListener);
            imageRightImportFrames = new JButton("Import frames");
            imageRightImportFrames.setToolTipText(
                    "Build a spritesheet from a folder or several PNG frames. "
                            + "Optional dyadic scale (100%…6.25% or fit-to-built-in) and per-frame lift for hops.");
            imageRightImportFrames.addActionListener(importFramesRightListener);
            imageRightExport = new JButton("Export Spritesheet");
            imageRightExport.setToolTipText("Save the right spritesheet as a PNG file.");
            imageRightExport.addActionListener(exportRightListener);
            imageRightExportFrames = new JButton("Export Frames");
            imageRightExportFrames.setToolTipText(
                    "Pick frame borders on the right sheet, then Pack… into this action "
                            + "or Export PNGs…. Use for padded or uneven third-party strips.");
            imageRightExportFrames.addActionListener(exportFramesRightListener);
            styleSecondary(imageRightExport);
            styleSecondary(imageRightExportFrames);
            addFormRow(rightSprites, 2, new JLabel(""), wrapImportExportButtons(
                    imageRightImport, imageRightImportFrames, imageRightExport, imageRightExportFrames), 0.5);

            timingsRightField = new JTextField();
            constrainGrowableField(timingsRightField);
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
            addFormRow(rightSprites, 3, new JLabel("Timings:"),
                    wrapTimingsField(timingsRightField, timingsRightMinus, timingsRightPlus), 1.0);

            // --- Transitions ---
            nextWaitingField = new JTextField();
            constrainGrowableField(nextWaitingField);
            nextWaitingField.setToolTipText("Comma-separated actions. Repeats raise chance, or write "
                    + "name:N (same as N copies). That action's gait bag then expands. When a "
                    + "looping idle's timer ends, these slots compete with next moving. Use none or - "
                    + "for no successor (one-shots fall through to next moving). Looping actions still "
                    + "need a real waiting list. Tab completes the name; :N is optional.");
            nextWaitingField.getDocument().addDocumentListener(nextWaitingListener);
            ActionNameCompleter.install(nextWaitingField, new ActionNameCompleter.CandidateSource() {
                @Override
                public List<String> getCandidates() {
                    return ActionNameCompleter.candidatesFromEditor(editor, true);
                }
            }, true);
            addFormRow(transitions, 0, new JLabel("Next waiting:"), nextWaitingField, 1.0);

            nextMovingField = new JTextField();
            constrainGrowableField(nextMovingField);
            nextMovingField.setToolTipText("Comma-separated actions. Repeats raise chance, or write "
                    + "name:N (same as N copies). That action's gait bag then expands. When a "
                    + "looping idle's timer ends, these slots compete with next waiting. Use none or - "
                    + "for no successor (one-shots fall through to next waiting; looping idles stay "
                    + "idle and re-pick waiting). Tab completes the name; :N is optional.");
            nextMovingField.getDocument().addDocumentListener(nextMovingListener);
            ActionNameCompleter.install(nextMovingField, new ActionNameCompleter.CandidateSource() {
                @Override
                public List<String> getCandidates() {
                    return ActionNameCompleter.candidatesFromEditor(editor, true);
                }
            }, true);
            addFormRow(transitions, 1, new JLabel("Next moving:"), nextMovingField, 1.0);

            nextDragField = new JTextField();
            constrainGrowableField(nextDragField);
            nextDragField.setToolTipText("Optional. Leave empty to use Default drag. When set, replaces "
                    + "the default for this action only. Repeats or name:N raise chance. Tab completes "
                    + "the name; :N is optional.");
            nextDragField.getDocument().addDocumentListener(nextDragListener);
            ActionNameCompleter.install(nextDragField, new ActionNameCompleter.CandidateSource() {
                @Override
                public List<String> getCandidates() {
                    return ActionNameCompleter.candidatesFromEditor(editor, false);
                }
            }, true);
            addFormRow(transitions, 2, new JLabel("Drag override:"), nextDragField, 1.0);

            JPanel stack = new JPanel();
            stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
            stack.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
            stack.add(identity);
            stack.add(Box.createVerticalStrut(2));
            stack.add(anchors);
            stack.add(Box.createVerticalStrut(2));
            stack.add(leftSprites);
            stack.add(Box.createVerticalStrut(2));
            stack.add(rightSprites);
            stack.add(Box.createVerticalStrut(2));
            stack.add(transitions);
            stack.add(Box.createVerticalGlue());

            // Full width in the scroll pane, but keep natural section heights.
            capSectionWidth(identity);
            capSectionWidth(anchors);
            capSectionWidth(leftSprites);
            capSectionWidth(rightSprites);
            capSectionWidth(transitions);

            JScrollPane scroll = new JScrollPane(stack);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            add(scroll, BorderLayout.CENTER);

            setAction(-1);
        }

        private static JPanel newSection(String title) {
            JPanel p = new JPanel(new GridBagLayout());
            ((GridBagLayout) p.getLayout()).columnWeights = new double[] { 0.0, 1.0 };
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(title),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            return p;
        }

        private static void capSectionWidth(JPanel section) {
            Dimension pref = section.getPreferredSize();
            section.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        }

        private static void addFormRow(JPanel section, int row, JComponent label, JComponent field, double weighty) {
            addFormRow(section, row, label, field, weighty, GridBagConstraints.WEST, true);
        }

        private static void addFormRow(JPanel section, int row, JComponent label, JComponent field,
                double weighty, int fieldAnchor, boolean fillHorizontal) {
            GridBagConstraints c = getConstraints(0, row);
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(2, 0, 2, 8);
            section.add(label, c);

            c = getConstraints(1, row);
            c.weighty = weighty;
            c.anchor = fieldAnchor;
            c.insets = new Insets(2, 0, 2, 0);
            if (fillHorizontal) {
                c.fill = GridBagConstraints.HORIZONTAL;
            }
            section.add(field, c);
        }

        private static void styleSecondary(JButton button) {
            // Slightly tighter chrome so Preview / Import read as primary.
            button.setMargin(new Insets(2, 6, 2, 6));
        }

        /**
         * Keep long comma-lists (timings, next actions) from driving the section
         * preferred width; the field still fills and scrolls caret within the row.
         */
        private static void constrainGrowableField(JTextField field) {
            field.setColumns(20);
            Dimension pref = field.getPreferredSize();
            field.setMinimumSize(new Dimension(48, pref.height));
            field.setPreferredSize(pref);
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
                setTextIfChanged(imageLeftField, leftImg.isEmpty() ? "(from " + from + ")" : "<image from " + from + ">");
                setTextIfChanged(imageRightField, rightImg.isEmpty() ? "(from " + from + ")" : "<image from " + from + ">");
            } else {
                setTextIfChanged(imageLeftField, leftImg.isEmpty() ? "" : "<image>");
                setTextIfChanged(imageRightField, rightImg.isEmpty() ? "" : "<image>");
            }
            // Skip setText when the value already matches. Editing timings on an
            // alias detaches it and refreshes these fields from inside the
            // timings DocumentListener; mutating that same document throws
            // IllegalStateException ("Attempt to mutate in notification").
            setTextIfChanged(timingsLeftField, editor.getActionTimings(index, "left"));
            setTextIfChanged(timingsRightField, editor.getActionTimings(index, "right"));
        }

        /** No-op when {@code field} already shows {@code text} (avoids re-entrant DocumentEvents). */
        private static void setTextIfChanged(JTextField field, String text) {
            if (text == null) {
                text = "";
            }
            if (!text.equals(field.getText())) {
                field.setText(text);
            }
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
                setDirty(true);
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

        private static JPanel wrapTwoButtons(JButton left, JButton right) {
            JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
            row.add(left);
            row.add(right);
            return row;
        }

        private static JPanel wrapImportExportButtons(
                JButton importButton,
                JButton importFramesButton,
                JButton exportSheetButton,
                JButton exportFramesButton) {
            JPanel grid = new JPanel(new GridLayout(2, 2, 4, 4));
            grid.add(importButton);
            grid.add(importFramesButton);
            grid.add(exportSheetButton);
            grid.add(exportFramesButton);
            return grid;
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
            // BorderLayout reports the center's preferred width; keep it column-sized
            // so long timing lists do not stretch the Sprites section / button rows.
            Dimension pref = row.getPreferredSize();
            row.setPreferredSize(pref);
            row.setMinimumSize(new Dimension(120, pref.height));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
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
        
        void previewImage(String direction) {
            if (currentIndex < 0) {
                return;
            }
            if (!"left".equals(direction) && !"right".equals(direction)) {
                return;
            }
            try {
                String b64Image = editor.getActionImage(currentIndex, direction);
                String timings = editor.getActionTimings(currentIndex, direction);
                Base64.Decoder b64Dec = Base64.getDecoder();
                byte[] rawImage = b64Dec.decode(b64Image);
                Image image = ImageIO.read(new ByteArrayInputStream(rawImage));
                if (image == null) throw new IllegalArgumentException();
                int frames = frameCountFromTimings(timings);
                SpriteSheetPreview preview = new SpriteSheetPreview(image, frames);
                String[] options = { "Open in Packer", "OK" };
                int choice = JOptionPane.showOptionDialog(
                        this,
                        wrapPreviewForDialog(preview),
                        "Image Preview",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        options,
                        options[1]);
                if (choice == 0) {
                    openSheetInPacker(direction, image, frames, timings);
                }
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, "The image could not be decoded. Please load a new image.", "Image Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "The image could not be decoded. Please load a new image.", "Image Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        /**
         * Caps the preview pane so wide or tall sheets scroll instead of forcing
         * {@link JOptionPane} to pack to the full spritesheet size.
         */
        private static JScrollPane wrapPreviewForDialog(SpriteSheetPreview preview) {
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

        /**
         * Splits the previewed strip into cells (same integer division as the
         * wallpaper) and opens the pack dialog so the sheet can be reordered,
         * lifted, or scaled.
         */
        void openSheetInPacker(String direction, Image image, int frameCount, String timings) {
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
                notes.append("\n\nScale is 100% by default (cells are already packed). ")
                        .append("Choose 50%/25%/12.5%/6.25% or Fit to built-in only if you want to shrink this sheet.");
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
                JOptionPane.showMessageDialog(
                        this,
                        e.getMessage(),
                        "Open in Packer Failed",
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
        
        /**
         * Builds the opposite facing from {@code fromDirection} by flopping each
         * cell. Confirms before replacing an existing destination sheet.
         */
        void mirrorFacing(String fromDirection) {
            if (currentIndex < 0) {
                return;
            }
            String toDirection = "left".equals(fromDirection) ? "right"
                    : "right".equals(fromDirection) ? "left" : null;
            if (toDirection == null) {
                return;
            }

            String source = editor.getActionImage(currentIndex, fromDirection);
            if (source == null || source.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "No " + fromDirection + " spritesheet to mirror.",
                        "Mirror Facing",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String dest = editor.getActionImage(currentIndex, toDirection);
            if (dest != null && !dest.isEmpty()) {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Replace the " + toDirection + " spritesheet with a per-cell mirror of the "
                                + fromDirection + " sheet?\n"
                                + "Frame order and timings are copied; explicit feet X is flipped.",
                        "Mirror to " + toDirection,
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.OK_OPTION) {
                    return;
                }
            }

            try {
                editor.mirrorActionSprite(currentIndex, fromDirection);
                setAction(currentIndex);
                setDirty(true);
                previewImage(toDirection);
            } catch (PonyEditor.GenericException e) {
                JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        }

        void importImage(String direction) {
            if (currentIndex < 0) {
                return;
            }
            fc.setFileFilter(new FileNameExtensionFilter("All Supported Formats", "png", "gif"));
            fc.addChoosableFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
            fc.addChoosableFileFilter(new FileNameExtensionFilter("GIF Animations", "gif"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                try {
                    if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
                        importGif(direction, file);
                    } else {
                        importPng(direction, file);
                    }
                } catch (PonyEditor.GenericException e) {
                    JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(
                            this,
                            e.getMessage(),
                            "Import Image Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
            fc.resetChoosableFileFilters();
        }

        /**
         * PNG import: load a finished left-to-right strip as-is. For padded or
         * uneven sheets, load then fix borders via {@link #exportFrames}.
         */
        void importPng(String direction, File file) throws IOException, PonyEditor.GenericException {
            editor.loadActionSprite(currentIndex, direction, file);
            setAction(currentIndex);
            setDirty(true);
        }

        /**
         * Coalesces a GIF and opens the same pack dialog as Import frames.
         * Default scale is native; dyadic shrinks and fit-to-built-in are choices.
         */
        void importGif(String direction, File file) throws IOException, PonyEditor.GenericException {
            ImageImport.GifFrames gif = ImageImport.loadGifFrames(file);
            int existingCount = ImageImport.countTimings(editor.getActionTimings(currentIndex, direction));
            StringBuilder notes = new StringBuilder();
            notes.append(file.getName()).append(" — ").append(gif.frames.size()).append(" coalesced frame")
                    .append(gif.frames.size() == 1 ? "" : "s")
                    .append(" at ").append(gif.logicalWidth).append("×").append(gif.logicalHeight).append(".");
            notes.append("\n\nGIF delays will be used as timings");
            if (existingCount == gif.frames.size()) {
                notes.append(" unless you keep the existing ").append(existingCount)
                        .append(" entries (reordering frames replaces them)");
            }
            notes.append(".");
            notes.append("\n\nList order is playback order — Move up/down, Reverse, or Alt+↑/↓.");
            notes.append("\n\nScale is 100% by default. Choose 50% (Desktop Ponies), a smaller ÷4/÷8/÷16, or Fit to built-in.");
            notes.append("\n\nLift is pixels of air under a frame (0 = on the ground). ");

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

            applyPackedFrames(direction, gif.frames, gif.timingsCs, packed);
        }

        /**
         * Packs {@code sourceFrames} in {@code packed.order}. Per-frame timings
         * (GIF delays) are permuted with the frames. Reordering replaces any
         * existing action timings of the same length — those numbers are
         * playback slots, not images.
         */
        void applyPackedFrames(String direction, List<java.awt.image.BufferedImage> sourceFrames,
                int[] sourceTimingsCs, FramePackDialog.Result packed)
                throws IOException, PonyEditor.GenericException {
            List<java.awt.image.BufferedImage> frames =
                    ImageImport.permute(sourceFrames, packed.order);
            ImageImport.PackOptions options = new ImageImport.PackOptions();
            options.lifts = packed.lifts;
            options.scaleDivisor = packed.scaleDivisor;
            if (sourceTimingsCs != null) {
                options.timingsCs = ImageImport.permute(sourceTimingsCs, packed.order);
            }
            ImageImport imported = editor.loadActionSpriteFromFrames(
                    currentIndex, direction, frames, options);
            if (!ImageImport.isIdentityOrder(packed.order)) {
                editor.setActionTimings(currentIndex, direction, imported.timings);
            }
            setAction(currentIndex);
            setDirty(true);
            previewImage(direction);
        }

        /**
         * Packs a folder of PNG frames or a multi-selection into a left-to-right
         * spritesheet for {@code direction}. Opens the lift / pack dialog, then
         * Preview so hover-split can verify the timing count.
         */
        void importFrames(String direction) {
            if (currentIndex < 0) {
                return;
            }
            FileFilter previousFilter = fc.getFileFilter();
            boolean previousMulti = fc.isMultiSelectionEnabled();
            int previousMode = fc.getFileSelectionMode();
            fc.setMultiSelectionEnabled(true);
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            fc.setAcceptAllFileFilterUsed(true);
            FileFilter pngOrDir = new PngFramesFilter();
            fc.setFileFilter(pngOrDir);
            int result = fc.showOpenDialog(this);
            File[] chosen = fc.getSelectedFiles();
            File single = fc.getSelectedFile();
            fc.setMultiSelectionEnabled(previousMulti);
            fc.setFileSelectionMode(previousMode);
            fc.resetChoosableFileFilters();
            if (previousFilter != null) {
                fc.setFileFilter(previousFilter);
            }
            if (result != JFileChooser.APPROVE_OPTION) {
                return;
            }

            List<File> selected = new ArrayList<File>();
            if (chosen != null && chosen.length > 0) {
                selected.addAll(Arrays.asList(chosen));
            } else if (single != null) {
                selected.add(single);
            }
            if (selected.isEmpty()) {
                return;
            }

            try {
                List<File> files = ImageImport.collectFrameFiles(selected);
                List<java.awt.image.BufferedImage> frames = ImageImport.loadFrameImages(files);
                ImageImport.PackPreview preview = ImageImport.inspectFrames(frames);
                int existingCount = ImageImport.countTimings(editor.getActionTimings(currentIndex, direction));
                StringBuilder notes = new StringBuilder();
                notes.append(summarizeFrameFiles(files));
                if (preview.mixedSizes) {
                    notes.append("\n\nFrame sizes differ; smaller frames are padded to the cell and can be lifted.");
                }
                if (existingCount == preview.frameCount) {
                    notes.append("\n\nExisting timings (").append(existingCount)
                            .append(" entries) will be kept if you leave the imported order.");
                } else {
                    notes.append("\n\nTimings will be set to ").append(preview.frameCount)
                            .append(" × ").append(ImageImport.DEFAULT_FRAME_TIMING_CS)
                            .append(" (hundredths of a second).");
                }
                notes.append("\n\nList order is playback order — Move up/down, Reverse, or Alt+↑/↓.");
                notes.append("\n\nScale is 100% by default. Choose 50% (Desktop Ponies), ÷4/÷8/÷16, or Fit to built-in if these frames are oversized.");
                notes.append("\n\nLift is pixels of air under a frame (0 = on the ground). ")
                        .append("It is baked into the sheet — leave <anchory> empty so feet stay on the ground line.");

                FramePackDialog.Result packed = FramePackDialog.showDialog(
                        this,
                        "Import Frames (" + direction + ")",
                        files,
                        frames,
                        notes.toString(),
                        ImageImport.SCALE_DIVISOR_NATIVE);
                if (packed == null) {
                    return;
                }

                applyPackedFrames(direction, frames, null, packed);
            } catch (PonyEditor.GenericException e) {
                JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        this,
                        e.getMessage(),
                        "Import Frames Failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        private static String summarizeFrameFiles(List<File> files) {
            StringBuilder sb = new StringBuilder();
            int shown = Math.min(files.size(), 8);
            for (int i = 0; i < shown; i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(files.get(i).getName());
            }
            if (files.size() > shown) {
                sb.append("\n… +").append(files.size() - shown).append(" more");
            }
            return sb.toString();
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

        /**
         * Opens border picking on the current action spritesheet. The user can
         * Pack… the extracted frames back into this action, or Export PNGs… to
         * a folder.
         */
        void exportFrames(String direction) {
            if (currentIndex < 0) {
                return;
            }
            String b64Image = editor.getActionImage(currentIndex, direction);
            if (b64Image == null || b64Image.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "No " + direction + " spritesheet is loaded for this action.",
                        "Export Frames",
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
                        "Export Frames",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            BufferedImage sheet;
            int timingHint;
            try {
                Image image = ImageIO.read(new ByteArrayInputStream(rawImage));
                if (image == null) {
                    throw new IOException("Could not decode spritesheet");
                }
                sheet = SpriteSheetPreview.toBufferedImage(image);
                timingHint = frameCountFromTimings(editor.getActionTimings(currentIndex, direction));
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        this,
                        e.getMessage() != null ? e.getMessage()
                                : "The image could not be decoded. Please load a new image.",
                        "Export Frames",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String notes = "Action \"" + editor.getActionName(currentIndex) + "\" (" + direction
                    + ") — " + sheet.getWidth() + "×" + sheet.getHeight()
                    + ".\n\nDrag each frame's left and right edges. Gaps between frames are gutters "
                    + "and are discarded. Trim removes transparent pad inside each interval; "
                    + "it does not invent borders.\n\nPack… re-packs into this action. "
                    + "Export PNGs… writes numbered files to a folder.";
            FrameBordersDialog.Result cut = FrameBordersDialog.showDialog(
                    this,
                    "Export Frames (" + direction + ")",
                    sheet,
                    timingHint,
                    notes);
            if (cut == null || cut.frames == null || cut.frames.isEmpty()) {
                return;
            }

            if (cut.action == FrameBordersDialog.Action.PACK) {
                packExportedFrames(direction, cut.frames);
            } else if (cut.action == FrameBordersDialog.Action.EXPORT) {
                writeExportedFramePngs(direction, cut.frames);
            }
        }

        /**
         * Opens {@link FramePackDialog} on extracted frames and replaces this
         * action's sheet in place.
         */
        void packExportedFrames(String direction, List<BufferedImage> frames) {
            try {
                int existingCount = ImageImport.countTimings(
                        editor.getActionTimings(currentIndex, direction));
                String[] names = new String[frames.size()];
                for (int i = 0; i < frames.size(); i++) {
                    names[i] = "frame " + (i + 1);
                }
                StringBuilder packNotes = new StringBuilder();
                packNotes.append("Packing ").append(frames.size()).append(" frame")
                        .append(frames.size() == 1 ? "" : "s")
                        .append(" cut from the current ").append(direction).append(" sheet.");
                if (existingCount == frames.size()) {
                    packNotes.append("\n\nExisting timings (").append(existingCount)
                            .append(" entries) will be kept if you leave the imported order.");
                } else {
                    packNotes.append("\n\nTimings will be set to ").append(frames.size())
                            .append(" × ").append(ImageImport.DEFAULT_FRAME_TIMING_CS)
                            .append(" (hundredths of a second).");
                }
                packNotes.append("\n\nList order is playback order — Move up/down, Reverse, or Alt+↑/↓.");
                packNotes.append("\n\nScale is 100% by default. Choose 50%/25%/12.5%/6.25% or Fit to built-in if needed.");
                packNotes.append("\n\nLift is pixels of air under a frame (0 = on the ground).");

                FramePackDialog.Result packed = FramePackDialog.showDialog(
                        this,
                        "Pack Frames (" + direction + ")",
                        names,
                        frames,
                        packNotes.toString(),
                        ImageImport.SCALE_DIVISOR_NATIVE);
                if (packed == null) {
                    return;
                }
                applyPackedFrames(direction, frames, null, packed);
            } catch (PonyEditor.GenericException e) {
                JOptionPane.showMessageDialog(this, e.detail, e.getMessage(), JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        this,
                        e.getMessage(),
                        "Pack Frames Failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        /** Writes extracted frames as numbered PNGs into a chosen folder. */
        void writeExportedFramePngs(String direction, List<BufferedImage> frames) {
            String prefix = ImageImport.sanitizeExportPrefix(
                    editor.getActionName(currentIndex) + "_" + direction);
            File dir = chooseExportFramesDirectory();
            if (dir == null) {
                return;
            }
            if (!dir.isDirectory()) {
                JOptionPane.showMessageDialog(
                        this,
                        "\"" + dir.getName() + "\" is not a folder.",
                        "Export Frames",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<File> dest = ImageImport.frameExportFiles(dir, prefix, frames.size());
            int existing = 0;
            String firstExisting = null;
            for (File file : dest) {
                if (file.exists()) {
                    existing++;
                    if (firstExisting == null) {
                        firstExisting = file.getName();
                    }
                }
            }
            if (existing > 0) {
                String message = existing == 1
                        ? "\"" + firstExisting + "\" already exists.\nDo you want to replace it?"
                        : existing + " files already exist (e.g. \"" + firstExisting
                                + "\").\nDo you want to replace them?";
                int choice = JOptionPane.showConfirmDialog(
                        this,
                        message,
                        "Confirm Overwrite",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            try {
                ImageImport.writeFramePngs(frames, dir, prefix);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        this,
                        "Failed to write frames: " + e.getMessage(),
                        "Export Frames",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String sizeNote = frameSizeSummary(frames);
            String firstName = dest.get(0).getName();
            String lastName = dest.get(dest.size() - 1).getName();
            String range = dest.size() == 1 ? firstName : firstName + " … " + lastName;
            JOptionPane.showMessageDialog(
                    this,
                    "Wrote " + dest.size() + " frame" + (dest.size() == 1 ? "" : "s")
                            + " (" + sizeNote + "):\n" + range,
                    "Export Frames",
                    JOptionPane.INFORMATION_MESSAGE);
        }

        private static String frameSizeSummary(List<BufferedImage> frames) {
            if (frames == null || frames.isEmpty()) {
                return "no frames";
            }
            int w0 = frames.get(0).getWidth();
            int h0 = frames.get(0).getHeight();
            boolean same = true;
            for (int i = 1; i < frames.size(); i++) {
                if (frames.get(i).getWidth() != w0 || frames.get(i).getHeight() != h0) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return w0 + "×" + h0 + " px each";
            }
            return "variable sizes; first " + w0 + "×" + h0 + " px";
        }

        /**
         * Folder chooser for {@link #exportFrames}. Restores the shared
         * {@link JFileChooser} so XML / PNG saves keep their last path.
         */
        private File chooseExportFramesDirectory() {
            int oldMode = fc.getFileSelectionMode();
            boolean oldAcceptAll = fc.isAcceptAllFileFilterUsed();
            File previous = fc.getSelectedFile();
            fc.resetChoosableFileFilters();
            fc.setAcceptAllFileFilterUsed(true);
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Export Frames");
            fc.setSelectedFile(fc.getCurrentDirectory());
            int result = fc.showDialog(this, "Export");
            File chosen = result == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
            if (previous != null) {
                fc.setSelectedFile(previous);
            } else {
                fc.setSelectedFile(null);
            }
            fc.setFileSelectionMode(oldMode);
            fc.setAcceptAllFileFilterUsed(oldAcceptAll);
            fc.resetChoosableFileFilters();
            fc.setDialogTitle(null);
            return chosen;
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
            imageLeftMirror.setEnabled(enabled);
            imageLeftImport.setEnabled(enabled);
            imageLeftImportFrames.setEnabled(enabled);
            imageLeftExport.setEnabled(enabled);
            imageLeftExportFrames.setEnabled(enabled);
            timingsLeftField.setEnabled(enabled);
            timingsLeftMinus.setEnabled(enabled);
            timingsLeftPlus.setEnabled(enabled);
            imageRightField.setEnabled(enabled);
            imageRightPreview.setEnabled(enabled);
            imageRightMirror.setEnabled(enabled);
            imageRightImport.setEnabled(enabled);
            imageRightImportFrames.setEnabled(enabled);
            imageRightExport.setEnabled(enabled);
            imageRightExportFrames.setEnabled(enabled);
            timingsRightField.setEnabled(enabled);
            timingsRightMinus.setEnabled(enabled);
            timingsRightPlus.setEnabled(enabled);
            nextWaitingField.setEnabled(enabled);
            nextMovingField.setEnabled(enabled);
            nextDragField.setEnabled(enabled);
        }
        
    }

    /**
     * Lets the frames chooser show folders (a {@link FileNameExtensionFilter}
     * for {@code png} hides directories on some look-and-feels).
     */
    private static final class PngFramesFilter extends FileFilter {
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
        refreshStatusBar();
    }
    
    private WindowListener windowListener = new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
            if (!checkChanges()) return;
            if (e.getWindow() instanceof JFrame) {
                EditorWindowPrefs.save((JFrame) e.getWindow());
            }
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
            if (e.getValueIsAdjusting()) {
                return;
            }
            actionSettingsPane.setAction(actionList.getSelectedIndex());
            refreshStatusBar();
        }
    };
    
    private ActionListener newActionListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            String actionName = JOptionPane.showInputDialog(PonyEditorGUI.this, "Enter a name for the new action:", "New Action", JOptionPane.PLAIN_MESSAGE);
            if (actionName != null && !actionName.equals("")) {
                int i = editor.addAction(actionName);
                setDirty(true);
                
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
                setDirty(true);
                
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
                defaultDragField.setText(editor.getDefaultDrag());
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
                setDirty(true);
            }
        }
    };
    
    private DocumentListener defaultDragListener = new MyDocumentListener() {
        public void update(DocumentEvent e) {
            if (!defaultDragField.getText().equals(editor.getDefaultDrag())) {
                editor.setDefaultDrag(defaultDragField.getText());
                setDirty(true);
            }
        }
    };
    
    private JFrame parentFrame;
    private DefaultListModel<String> actionListModel;
    private JList<String> actionList;
    private ActionPanel actionSettingsPane;
    private JTextField startActionsField;
    private JTextField defaultDragField;
    private JLabel statusLabel;
    
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
        super(new BorderLayout(0, 0));
        
        this.parentFrame = parentFrame;
        
        fc = new JFileChooser(".");
        fc.setAcceptAllFileFilterUsed(false);

        add(createToolBar(), BorderLayout.NORTH);

        JComponent actionListPane = createActionListPane();
        actionListPane.setPreferredSize(new Dimension(220, 400));
        actionListPane.setMinimumSize(new Dimension(160, 200));

        actionSettingsPane = new ActionPanel();
        // Match Actions pane chrome so both split children share the same outer
        // bottom edge; without this, only the left titled card fills the height
        // and the right sections look short.
        actionSettingsPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Action"),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        actionSettingsPane.setMinimumSize(new Dimension(420, 200));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, actionListPane, actionSettingsPane);
        split.setResizeWeight(0.22);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(6);

        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        center.add(split, BorderLayout.CENTER);
        center.add(createStartActionsPane(), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
        
        editor = existing != null ? existing : new PonyEditor();
        setFile(initialFile);
        setDirty(dirty);
        if (existing != null) {
            setUIFromPony();
        } else {
            refreshStatusBar();
        }
    }

    private JToolBar createToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        bar.add(toolButton("New", "New pony (Ctrl+N)", fileNewListener));
        bar.add(toolButton("Open", "Open pony XML (Ctrl+O)", fileOpenListener));
        bar.add(toolButton("Save", "Save (Ctrl+S)", fileSaveListener));
        bar.addSeparator();
        bar.add(toolButton("Import DP", "Import from Desktop-Ponies (Ctrl+I)", fileImportDesktopPoniesListener));
        bar.addSeparator();
        JButton check = toolButton("Check transitions", "Preview feet-locked A→B transition for the selected action",
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        actionSettingsPane.checkTransitions();
                    }
                });
        bar.add(check);
        return bar;
    }

    private static JButton toolButton(String text, String tooltip, ActionListener listener) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.addActionListener(listener);
        return button;
    }

    private JComponent createStatusBar() {
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    private void setDirty(boolean dirty) {
        hasChanges = dirty;
        refreshStatusBar();
    }

    private void refreshStatusBar() {
        if (statusLabel == null) {
            return;
        }
        int actions = editor != null ? editor.getActionCount() : 0;
        int sel = actionList != null ? actionList.getSelectedIndex() : -1;
        String actionName = "—";
        if (editor != null && sel >= 0 && sel < editor.getActionCount()) {
            actionName = editor.getActionName(sel);
        }
        String path = currentFile != null ? currentFile.getAbsolutePath() : "(unsaved)";
        String dirty = hasChanges ? "Modified" : "Saved";
        String actionWord = actions == 1 ? "action" : "actions";
        statusLabel.setText(dirty + "  ·  " + actions + " " + actionWord
                + "  ·  " + actionName + "  ·  " + path);
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
        refreshStatusBar();
    }
    
    private void setUIFromPony() {
        actionListModel.clear();
        for (int i = 0; i < editor.getActionCount(); i++) {
            actionListModel.addElement(editor.getActionName(i));
        }
        startActionsField.setText(editor.getStartActions());
        defaultDragField.setText(editor.getDefaultDrag());
        
        if (editor.getActionCount() > 0) {
            actionList.setSelectedIndex(0);
        } else {
            actionList.clearSelection();
            actionSettingsPane.setAction(-1);
        }
        refreshStatusBar();
    }
    
    private void createNewPony() {
        editor.reset();
        
        setUIFromPony();
        
        setFile(null);
        setDirty(false);
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
        setDirty(false);
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
        setDirty(true);

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
        setDirty(false);
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
        setDirty(true);
        actionListModel.set(i, actionName);
        // Refresh next-action fields and start actions so rewritten names show.
        actionSettingsPane.setAction(i);
        startActionsField.setText(editor.getStartActions());
        defaultDragField.setText(editor.getDefaultDrag());
    }

    private JComponent createActionListPane() {
        JPanel result = new JPanel(new BorderLayout(0, 4));
        result.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Actions"),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        
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
        result.add(new JScrollPane(actionList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
        JButton newAction = new JButton("New");
        newAction.setToolTipText("Create a new action");
        newAction.addActionListener(newActionListener);
        JButton renameAction = new JButton("Rename");
        renameAction.setToolTipText("Rename the selected action and update all references");
        renameAction.addActionListener(renameActionListener);
        JButton deleteAction = new JButton("Delete");
        deleteAction.setToolTipText("Delete the selected action");
        deleteAction.addActionListener(deleteActionListener);
        buttons.add(newAction);
        buttons.add(renameAction);
        buttons.add(deleteAction);
        result.add(buttons, BorderLayout.SOUTH);
        
        return result;
    }
    
    private JComponent createStartActionsPane() {
        JPanel result = new JPanel(new GridBagLayout());
        result.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Pony"),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        
        GridBagConstraints c;
        
        JLabel startActionsLabel = new JLabel("Start actions:");
        c = getConstraints(0, 0);
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        result.add(startActionsLabel, c);
        
        startActionsField = new JTextField();
        startActionsField.setToolTipText("Comma-separated entry actions. Repeats or name:N raise chance. "
                + "Tab completes the name; :N is optional.");
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
        c.insets = new Insets(2, 0, 2, 0);
        result.add(startActionsField, c);
        
        JLabel defaultDragLabel = new JLabel("Default drag:");
        c = getConstraints(2, 0);
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 12, 2, 8);
        result.add(defaultDragLabel, c);
        
        defaultDragField = new JTextField();
        defaultDragField.setToolTipText("Comma-separated drag actions used when an action has no drag override. "
                + "Repeats or name:N raise chance. Tab completes the name; :N is optional.");
        defaultDragField.getDocument().addDocumentListener(defaultDragListener);
        ActionNameCompleter.install(defaultDragField, new ActionNameCompleter.CandidateSource() {
            @Override
            public List<String> getCandidates() {
                return ActionNameCompleter.candidatesFromEditor(editor, false);
            }
        }, true);
        c = getConstraints(3, 0);
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);
        result.add(defaultDragField, c);
        
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

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);

        JMenuItem themeDark = new JMenuItem("Dark theme");
        themeDark.setToolTipText("FlatLaf dark chrome (default). Preview canvases stay dark either way.");
        themeDark.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                EditorTheme.install(true);
            }
        });
        viewMenu.add(themeDark);

        JMenuItem themeLight = new JMenuItem("Light theme");
        themeLight.setToolTipText("FlatLaf light chrome. Preview canvases stay dark for sprite contrast.");
        themeLight.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                EditorTheme.install(false);
            }
        });
        viewMenu.add(themeLight);

        result.add(viewMenu);
        
        return result;
    }
    
    private static void createAndShowGUI(PonyEditor existing, File initialFile, boolean dirty) {
        JFrame frame = new JFrame();
        frame.setMinimumSize(new Dimension(720, 640));
        frame.setPreferredSize(new Dimension(960, 1000));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        PonyEditorGUI contentPane = new PonyEditorGUI(frame, existing, initialFile, dirty);
        contentPane.setOpaque(true);
        frame.setContentPane(contentPane);
        frame.addWindowListener(contentPane.windowListener);
        
        frame.setJMenuBar(contentPane.createMenuBar());
        
        frame.pack();
        if (!EditorWindowPrefs.apply(frame)) {
            frame.setLocationRelativeTo(null);
        }
        EditorWindowPrefs.installPersistence(frame);
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
        EditorTheme.install(true);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                createAndShowGUI(existing, initialFile, dirty);
            }
        });
    }
    
}
