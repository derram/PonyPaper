package uk.cpjsmith.ponypaper.custom;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Shared +/- controls for comma-separated frame timing fields (Actions and Effects).
 */
final class TimingsAdjust {

    private TimingsAdjust() {
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

    static JButton createButton(String label, String tooltip) {
        JButton button = new JButton(label);
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(2, 6, 2, 6));
        return button;
    }

    static JPanel wrapField(JTextField field, JButton minus, JButton plus) {
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

    static void wire(JTextField field, JButton minus, JButton plus, java.awt.Component dialogParent) {
        minus.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                adjustField(field, e, -1, dialogParent);
            }
        });
        plus.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                adjustField(field, e, 1, dialogParent);
            }
        });
    }

    static JButton[] createPair(JTextField field, java.awt.Component dialogParent) {
        JButton minus = createButton("−", "Subtract 1 from all frame timings (Shift: −5)");
        JButton plus = createButton("+", "Add 1 to all frame timings (Shift: +5)");
        wire(field, minus, plus, dialogParent);
        return new JButton[] { minus, plus };
    }

    private static void adjustField(JTextField field, ActionEvent e, int sign,
            java.awt.Component dialogParent) {
        int step = ((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) ? 5 : 1;
        int delta = sign * step;
        try {
            String adjusted = adjustAllTimings(field.getText(), delta);
            if (!adjusted.equals(field.getText())) {
                field.setText(adjusted);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    dialogParent,
                    "Timings must be a comma-separated list of integers.",
                    "Invalid Timings",
                    JOptionPane.WARNING_MESSAGE);
        }
    }
}
