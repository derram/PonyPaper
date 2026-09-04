package uk.cpjsmith.ponypaper.custom;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Compact modal: play a draft spritesheet on a loop with feet locked to a
 * ground line. Used from {@link FramePackDialog} so order / scale / lift can
 * be checked before Pack commits the sheet to an action or effect.
 */
public final class FrameLoopPreviewDialog extends JDialog {

    private static final int TICK_MS = 33;
    private static final float CS_PER_MS = 0.1f;
    private static final int PAD = 24;

    private final ActionFrameSource source;
    private final StagePanel stage;
    private final JLabel statusLabel;
    private final JSlider scaleSlider;
    private final JSlider rateSlider;
    private final JButton playButton;

    private float animTimeCs;
    private boolean playing;
    private final Timer timer;
    private long lastTickNanos;

    private FrameLoopPreviewDialog(Component parent, ActionFrameSource source, String title) {
        // APPLICATION_MODAL nests cleanly under FramePackDialog (also app-modal).
        super(SwingUtilities.getWindowAncestor(parent),
                title != null ? title : "Loop Preview",
                ModalityType.APPLICATION_MODAL);
        this.source = source;

        stage = new StagePanel();
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(EditorTheme.STATUS_BAR);
        statusLabel.setForeground(EditorTheme.STATUS_TEXT);

        scaleSlider = new JSlider(1, 8, 3);
        scaleSlider.setMajorTickSpacing(1);
        scaleSlider.setPaintTicks(true);
        scaleSlider.setSnapToTicks(true);
        scaleSlider.setToolTipText("Nearest-neighbour display scale.");

        rateSlider = new JSlider(25, 300, 100);
        rateSlider.setMajorTickSpacing(25);
        rateSlider.setPaintTicks(true);
        rateSlider.setToolTipText("Playback rate (percent).");

        playButton = new JButton("Pause");
        JButton restartButton = new JButton("Restart");
        JButton closeButton = new JButton("Close");

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
        restartButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                animTimeCs = 0;
                stage.repaint();
                updateStatus();
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

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        north.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        north.add(new JLabel("Scale:"));
        north.add(scaleSlider);
        north.add(new JLabel("Rate %:"));
        north.add(rateSlider);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(playButton);
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
        getRootPane().registerKeyboardAction(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        togglePlay();
                    }
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
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

            @Override
            public void windowOpened(WindowEvent e) {
                startPlaying();
            }
        });

        animTimeCs = 0;
        updateStatus();

        pack();
        Dimension size = getSize();
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = Math.max(480, (int) (screen.width * 0.7));
        int maxH = Math.max(360, (int) (screen.height * 0.7));
        if (size.width > maxW || size.height > maxH) {
            setSize(Math.min(size.width, maxW), Math.min(size.height, maxH));
        }
        setLocationRelativeTo(parent);
    }

    /**
     * Opens a looping feet-locked preview of {@code sheet}. {@code timingsCs}
     * length is the frame count (same rule as XML timings).
     */
    public static void showDialog(Component parent, BufferedImage sheet, int[] timingsCs,
            String title) {
        if (sheet == null) {
            throw new IllegalArgumentException("sheet");
        }
        int[] times = timingsCs != null && timingsCs.length > 0
                ? timingsCs.clone()
                : new int[] { ImageImport.DEFAULT_FRAME_TIMING_CS };
        ActionFrameSource source = ActionFrameSource.fromImage(
                sheet, times, Float.NaN, Float.NaN);
        FrameLoopPreviewDialog dialog = new FrameLoopPreviewDialog(parent, source, title);
        dialog.setVisible(true);
    }

    private float displayScale() {
        return scaleSlider.getValue();
    }

    private float rateFactor() {
        return Math.max(0.25f, rateSlider.getValue() / 100f);
    }

    private void togglePlay() {
        if (playing) {
            stopPlaying();
        } else {
            startPlaying();
        }
    }

    private void startPlaying() {
        if (playing) {
            return;
        }
        playing = true;
        playButton.setText("Pause");
        lastTickNanos = System.nanoTime();
        timer.start();
        updateStatus();
    }

    private void stopPlaying() {
        if (!playing) {
            playButton.setText("Play");
            return;
        }
        playing = false;
        playButton.setText("Play");
        timer.stop();
        updateStatus();
    }

    private void onTick() {
        long now = System.nanoTime();
        float elapsedMs = (now - lastTickNanos) / 1_000_000f;
        lastTickNanos = now;
        animTimeCs += elapsedMs * CS_PER_MS * rateFactor();
        // Always wrap: this dialog is a loop check.
        int total = source.totalTimeCs;
        if (total > 0) {
            animTimeCs %= total;
            if (animTimeCs < 0) {
                animTimeCs += total;
            }
        }
        stage.repaint();
        updateStatus();
    }

    private void updateStatus() {
        int frame = source.frameIndexAt((int) animTimeCs);
        statusLabel.setText(String.format(
                "Frame %d / %d · %d cs · loop · ×%d · %d%%",
                frame + 1,
                source.frameCount,
                Math.max(1, source.frameTimesCs[frame]),
                scaleSlider.getValue(),
                rateSlider.getValue()));
    }

    private final class StagePanel extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            float scale = displayScale();
            float ax = source.getResolvedAnchorX();
            float ay = source.getResolvedAnchorY();
            int maxAbove = Math.max(32, Math.round(ay));
            int maxBelow = Math.max(8, Math.round(source.frameHeight - ay));
            int maxLeft = Math.max(32, Math.round(ax));
            int maxRight = Math.max(32, Math.round(source.frameWidth - ax));
            int w = Math.round((maxLeft + maxRight) * scale) + PAD * 2;
            int h = Math.round((maxAbove + maxBelow) * scale) + PAD * 2;
            return new Dimension(Math.max(280, w), Math.max(180, h));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                int w = getWidth();
                int h = getHeight();
                paintChecker(g2, w, h);

                float scale = displayScale();
                float feetX = w / 2f;
                float below = Math.max(8f, source.frameHeight - source.getResolvedAnchorY());
                float feetY = h - PAD - below * scale;

                g2.setColor(EditorTheme.GROUND_STAGE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(PAD / 2, Math.round(feetY), w - PAD / 2, Math.round(feetY));

                g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_OFF);

                int frame = source.frameIndexAt((int) animTimeCs);
                BufferedImage cell = source.frameImage(frame);
                Rectangle dst = source.destinationRect(feetX, feetY, scale);
                g2.setComposite(AlphaComposite.SrcOver);
                g2.drawImage(
                        cell,
                        dst.x,
                        dst.y,
                        dst.x + dst.width,
                        dst.y + dst.height,
                        0,
                        0,
                        source.frameWidth,
                        source.frameHeight,
                        null);

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
    }
}
