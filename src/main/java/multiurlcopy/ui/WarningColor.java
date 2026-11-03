package multiurlcopy.ui;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.function.Function;

/**
 * The colour a "not a clean success" status is shown in.
 *
 * <p>A hard-coded orange reads as a different kind of warning under
 * Burp's light and dark themes, so the look-and-feel is asked first.
 * Burp themes through FlatLaf, which publishes warning and accent
 * colours already tuned to the active theme; only if none of them exist
 * does this fall back to a tone picked from the background's lightness.
 */
public final class WarningColor {

    /** FlatLaf keys, most specific first. */
    private static final String[] KEYS = {
            "Component.warning.focusedBorderColor",
            "Component.warning.borderColor",
            "Actions.Yellow",
            "Objects.Yellow",
            "Component.error.borderColor",
    };

    private static final Color ON_DARK = new Color(0xE2, 0xA0, 0x3F);
    private static final Color ON_LIGHT = new Color(0xA8, 0x5C, 0x00);

    private WarningColor() {
    }

    /** Resolves against the live look-and-feel for the given background. */
    public static Color forBackground(Color background) {
        return resolve(UIManager::getColor, isDark(background));
    }

    static Color resolve(Function<String, Color> lookup, boolean dark) {
        for (String key : KEYS) {
            Color found = lookup.apply(key);
            if (found != null) {
                return found;
            }
        }
        return dark ? ON_DARK : ON_LIGHT;
    }

    /** Perceived lightness, so the fallback suits the theme in use. */
    static boolean isDark(Color background) {
        if (background == null) {
            return false;
        }
        double luminance = (0.2126 * background.getRed()
                + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue()) / 255.0;
        return luminance < 0.5;
    }
}
