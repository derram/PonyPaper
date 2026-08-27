package uk.cpjsmith.ponypaper.custom;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.util.Collections;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Shared look-and-feel bootstrap and colour tokens for the custom pony editor.
 * <p>
 * Preview canvases stay dark regardless of chrome theme so sprite contrast is
 * stable; chrome follows FlatLaf dark/light. The View-menu choice is stored in
 * {@link Preferences} so it survives restarts.
 */
public final class EditorTheme {

    private static final String PREF_DARK = "darkTheme";
    private static final boolean PREF_DARK_DEFAULT = true;

    /** Soft violet accent — echoes the Android launcher background {@code #241F40}. */
    public static final Color ACCENT = new Color(0x7C, 0x6A, 0xF0);

    public static final Color CANVAS = new Color(0x2B, 0x2B, 0x2B);
    public static final Color CANVAS_DEEP = new Color(0x22, 0x22, 0x22);
    public static final Color STATUS_BAR = new Color(0x1E, 0x1E, 0x22);
    public static final Color STATUS_TEXT = new Color(0xC8, 0xC8, 0xC8);

    public static final Color CHECKER_A = new Color(0x2A, 0x2A, 0x2E);
    public static final Color CHECKER_B = new Color(0x36, 0x36, 0x3C);
    /** Slightly stronger checker used inside pack-dialog cell/strip bounds. */
    public static final Color CHECKER_CELL_A = new Color(0x3A, 0x3A, 0x3A);
    public static final Color CHECKER_CELL_B = new Color(0x4A, 0x4A, 0x4A);

    public static final Color GUIDE = new Color(0x66, 0x66, 0x66);
    public static final Color GUIDE_MUTED = new Color(0x88, 0x88, 0x88);
    public static final Color GROUND_LINE = new Color(0xFF, 0x55, 0x33);
    public static final Color GROUND_LABEL = new Color(0xFF, 0xCC, 0x00);
    public static final Color GROUND_STAGE = new Color(0x44, 0xAA, 0x44, 0xCC);

    public static final Color SELECTION = new Color(0x33, 0x99, 0xFF, 0xCC);
    public static final Color DIM_OVERLAY = new Color(0, 0, 0, 0x66);

    public static final Color FEET_RING = new Color(0xFF, 0x33, 0x33, 0xEE);
    public static final Color FEET_CORE = new Color(0xFF, 0x33, 0x33, 0x99);
    public static final Color FEET_DEFAULT_RING = new Color(0xFF, 0xCC, 0x00, 0xAA);
    public static final Color FEET_DEFAULT_CORE = new Color(0xFF, 0xCC, 0x00, 0x66);

    public static final Color WARNING = new Color(0xB8, 0x5C, 0x00);

    public static final Color GRID_DARK = new Color(0, 0, 0, 40);
    public static final Color GRID_LIGHT = new Color(255, 255, 255, 36);
    public static final Color GRID_MAJOR_DARK = new Color(0, 0, 0, 70);
    public static final Color GRID_MAJOR_LIGHT = new Color(255, 255, 255, 55);

    private static boolean dark = true;

    private EditorTheme() {
    }

    /** Whether the chrome (not canvas) is currently using the dark FlatLaf theme. */
    public static boolean isDark() {
        return dark;
    }

    /** Last saved chrome preference (defaults to dark when unset). */
    public static boolean isPreferredDark() {
        return prefs().getBoolean(PREF_DARK, PREF_DARK_DEFAULT);
    }

    /**
     * Installs FlatLaf before any Swing window is created. Safe to call more
     * than once; subsequent calls switch theme and refresh open windows.
     * Does not write preferences — use {@link #setPreferredDark(boolean)} for
     * View-menu changes that should persist.
     *
     * @param preferDark {@code true} for FlatDarkLaf, {@code false} for FlatLightLaf
     */
    public static void install(boolean preferDark) {
        dark = preferDark;
        FlatLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", "#7C6AF0"));
        if (preferDark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        applyUiDefaults();
        if (SwingUtilities.isEventDispatchThread()) {
            FlatLaf.updateUI();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    FlatLaf.updateUI();
                }
            });
        }
    }

    /** Bootstrap from the saved preference (dark when none is stored). */
    public static void install() {
        install(isPreferredDark());
    }

    /**
     * Saves the chrome preference and applies it immediately.
     *
     * @param preferDark {@code true} for FlatDarkLaf, {@code false} for FlatLightLaf
     */
    public static void setPreferredDark(boolean preferDark) {
        Preferences node = prefs();
        node.putBoolean(PREF_DARK, preferDark);
        try {
            node.flush();
        } catch (BackingStoreException ignored) {
            // In-memory theme still applies; next run falls back to default if store failed.
        }
        install(preferDark);
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(EditorTheme.class);
    }

    private static void applyUiDefaults() {
        // Keep focus/selection readable next to pixel-art previews.
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("TitlePane.unifiedBackground", Boolean.TRUE);
        UIManager.put("MenuItem.selectionType", "underline");
    }
}
