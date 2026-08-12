package uk.cpjsmith.ponypaper.custom;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * Tab-complete for action-name fields in the custom pony editor.
 * <p>
 * List fields (start / next-actions) complete the comma-separated token under
 * the caret. Single-token mode ({@code multiToken == false}) treats the whole
 * field as one name (e.g. sprites-from).
 * <p>
 * Phase 1: Tab only. One prefix match fills the token; several matches extend
 * to the longest common prefix. No popup yet.
 */
public final class ActionNameCompleter {

    /**
     * Supplies the live list of completable names (action names, optional
     * reserved tokens). Called on each Tab so renames stay current.
     */
    public interface CandidateSource {
        List<String> getCandidates();
    }

    /** Inclusive start / exclusive end of the token under the caret. */
    static final class Token {
        final int start;
        final int end;
        /** Text from token start up to the caret (what the user is typing). */
        final String prefix;

        Token(int start, int end, String prefix) {
            this.start = start;
            this.end = end;
            this.prefix = prefix;
        }
    }

    private ActionNameCompleter() {
    }

    /**
     * Binds Tab on {@code field} to complete against {@code candidates}.
     *
     * @param field      target text field
     * @param candidates live name list
     * @param multiToken {@code true} for comma-separated lists; {@code false}
     *                   for a single action name
     */
    public static void install(JTextField field, CandidateSource candidates, boolean multiToken) {
        if (field == null || candidates == null) {
            throw new IllegalArgumentException("field and candidates required");
        }
        field.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "completeActionName");
        field.getActionMap().put("completeActionName", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!tryComplete(field, candidates, multiToken)) {
                    field.transferFocus();
                }
            }
        });
    }

    /**
     * Attempts Tab-completion. Returns {@code true} if focus should stay in the
     * field (completed something, partial LCP, or ambiguous with no further
     * extension); {@code false} if Tab should move focus (no matches, or the
     * token is already a unique complete name).
     */
    static boolean tryComplete(JTextField field, CandidateSource candidates, boolean multiToken) {
        String text = field.getText();
        if (text == null) {
            text = "";
        }
        int caret = field.getCaretPosition();
        if (caret < 0) {
            caret = 0;
        }
        if (caret > text.length()) {
            caret = text.length();
        }

        Token token = tokenAt(text, caret, multiToken);
        List<String> matches = filterPrefix(candidates.getCandidates(), token.prefix);

        if (matches.isEmpty()) {
            return false;
        }

        String replacement;
        if (matches.size() == 1) {
            replacement = matches.get(0);
            String current = text.substring(token.start, token.end);
            // Already the unique match (allow trivial whitespace differences only at ends).
            if (current.equals(replacement)
                    || (token.prefix.equals(replacement) && current.trim().equals(replacement))) {
                return false;
            }
        } else {
            replacement = longestCommonPrefix(matches);
            if (replacement.length() <= token.prefix.length()) {
                // Ambiguous; nothing more Tab can fill without a popup.
                return true;
            }
        }

        Document doc = field.getDocument();
        try {
            int len = token.end - token.start;
            if (len > 0) {
                doc.remove(token.start, len);
            }
            doc.insertString(token.start, replacement, null);
            field.setCaretPosition(token.start + replacement.length());
        } catch (BadLocationException ex) {
            return false;
        }
        return true;
    }

    /**
     * Finds the token containing {@code caret}. For multi-token fields, commas
     * are separators; leading spaces after a comma are skipped so completion
     * does not eat them.
     */
    static Token tokenAt(String text, int caret, boolean multiToken) {
        if (text == null) {
            text = "";
        }
        if (caret < 0) {
            caret = 0;
        }
        if (caret > text.length()) {
            caret = text.length();
        }

        int start;
        int end;
        if (!multiToken) {
            start = 0;
            end = text.length();
        } else {
            start = 0;
            for (int i = caret - 1; i >= 0; i--) {
                if (text.charAt(i) == ',') {
                    start = i + 1;
                    break;
                }
            }
            end = text.length();
            for (int i = caret; i < text.length(); i++) {
                if (text.charAt(i) == ',') {
                    end = i;
                    break;
                }
            }
        }

        while (start < end && start < text.length() && text.charAt(start) == ' ') {
            start++;
        }
        // Do not trim trailing spaces into the prefix window; caret clamps below.
        if (caret < start) {
            caret = start;
        }
        if (caret > end) {
            caret = end;
        }

        String prefix = text.substring(start, caret);
        return new Token(start, end, prefix);
    }

    /**
     * Case-insensitive prefix filter. Empty prefix matches everything.
     * Preserves candidate order; skips null/blank candidates.
     */
    static List<String> filterPrefix(List<String> candidates, String prefix) {
        List<String> matches = new ArrayList<String>();
        if (candidates == null) {
            return matches;
        }
        String p = prefix != null ? prefix : "";
        for (String name : candidates) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (p.isEmpty() || name.regionMatches(true, 0, p, 0, p.length())) {
                matches.add(name);
            }
        }
        return matches;
    }

    /**
     * Longest common prefix of {@code strings}, using the first string's
     * casing. Comparison is case-insensitive.
     */
    static String longestCommonPrefix(List<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return "";
        }
        String first = strings.get(0);
        if (first == null) {
            return "";
        }
        for (int i = 0; i < first.length(); i++) {
            char c = first.charAt(i);
            char cl = Character.toLowerCase(c);
            for (int j = 1; j < strings.size(); j++) {
                String s = strings.get(j);
                if (s == null || i >= s.length()
                        || Character.toLowerCase(s.charAt(i)) != cl) {
                    return first.substring(0, i);
                }
            }
        }
        return first;
    }

    /**
     * Builds the standard candidate list from an editor: every action name,
     * optionally plus reserved {@code none} / {@code -} tokens.
     */
    public static List<String> candidatesFromEditor(PonyEditor editor, boolean includeNoneTokens) {
        List<String> list = new ArrayList<String>();
        if (editor == null) {
            return list;
        }
        for (int i = 0; i < editor.getActionCount(); i++) {
            list.add(editor.getActionName(i));
        }
        if (includeNoneTokens) {
            list.add("none");
            list.add("-");
        }
        return list;
    }

    /**
     * Action names that own their sprites (valid {@code spritesfrom} targets).
     */
    public static List<String> spriteOwnerCandidates(PonyEditor editor) {
        List<String> list = new ArrayList<String>();
        if (editor == null) {
            return list;
        }
        for (int i = 0; i < editor.getActionCount(); i++) {
            String from = editor.getActionSpritesFrom(i);
            if (from == null || from.isEmpty()) {
                list.add(editor.getActionName(i));
            }
        }
        return list;
    }
}
