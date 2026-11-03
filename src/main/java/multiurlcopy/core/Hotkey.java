package multiurlcopy.core;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * A modifier + key combination, stored as a string in Burp's preferences
 * and matched against live {@link KeyEvent}s.
 *
 * <p>The default is {@code Ctrl+Alt+U}: Burp already binds {@code Ctrl+U}
 * and {@code Ctrl+Shift+U} to URL encode and decode.
 */
public record Hotkey(int modifiers, int keyCode) {

    private static final int DEFAULT_MODIFIERS = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;

    public static Hotkey defaultHotkey() {
        return new Hotkey(DEFAULT_MODIFIERS, KeyEvent.VK_U);
    }

    /** Parses {@code "Ctrl+Alt+U"}; anything unrecognized yields the default. */
    public static Hotkey parse(String text) {
        if (text == null || text.isBlank()) {
            return defaultHotkey();
        }
        int modifiers = 0;
        int keyCode = -1;
        for (String rawPart : text.split("\\+")) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            switch (part.toLowerCase()) {
                case "ctrl", "control" -> modifiers |= InputEvent.CTRL_DOWN_MASK;
                case "alt" -> modifiers |= InputEvent.ALT_DOWN_MASK;
                case "shift" -> modifiers |= InputEvent.SHIFT_DOWN_MASK;
                case "meta", "cmd", "super" -> modifiers |= InputEvent.META_DOWN_MASK;
                default -> keyCode = keyCodeOf(part);
            }
        }
        return (keyCode == -1 || modifiers == 0) ? defaultHotkey() : new Hotkey(modifiers, keyCode);
    }

    public String display() {
        StringBuilder out = new StringBuilder();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            out.append("Ctrl+");
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            out.append("Alt+");
        }
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            out.append("Shift+");
        }
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
            out.append("Meta+");
        }
        return out.append(KeyEvent.getKeyText(keyCode)).toString();
    }

    /**
     * True only for a key-press of exactly this combination. An extra held
     * modifier is a different shortcut and must not trigger a copy.
     */
    public boolean matches(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_PRESSED) {
            return false;
        }
        int relevant = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
                | InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK;
        return event.getKeyCode() == keyCode && (event.getModifiersEx() & relevant) == modifiers;
    }

    private static int keyCodeOf(String part) {
        if (part.length() == 1) {
            char c = Character.toUpperCase(part.charAt(0));
            if (c >= 'A' && c <= 'Z') {
                return KeyEvent.VK_A + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return KeyEvent.VK_0 + (c - '0');
            }
        }
        return -1;
    }
}
