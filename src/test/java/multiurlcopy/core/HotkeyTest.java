package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotkeyTest {

    private static KeyEvent pressed(int modifiers, int keyCode) {
        return new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0L, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    void defaultIsCtrlAltU() {
        assertEquals("Ctrl+Alt+U", Hotkey.defaultHotkey().display());
    }

    @Test
    void parseRoundTripsThroughDisplay() {
        assertEquals("Ctrl+Alt+U", Hotkey.parse("Ctrl+Alt+U").display());
        assertEquals("Ctrl+Shift+Y", Hotkey.parse("Ctrl+Shift+Y").display());
        assertEquals("Alt+C", Hotkey.parse("Alt+C").display());
    }

    @Test
    void parseIsCaseInsensitiveAndIgnoresSpaces() {
        assertEquals("Ctrl+Alt+U", Hotkey.parse(" ctrl + alt + u ").display());
    }

    @Test
    void unparseableTextFallsBackToTheDefault() {
        assertEquals(Hotkey.defaultHotkey(), Hotkey.parse("nonsense"));
        assertEquals(Hotkey.defaultHotkey(), Hotkey.parse(""));
        assertEquals(Hotkey.defaultHotkey(), Hotkey.parse(null));
    }

    @Test
    void matchesTheExactCombination() {
        Hotkey hotkey = Hotkey.parse("Ctrl+Alt+U");

        assertTrue(hotkey.matches(pressed(InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_U)));
    }

    @Test
    void doesNotMatchWhenAModifierIsMissing() {
        Hotkey hotkey = Hotkey.parse("Ctrl+Alt+U");

        assertFalse(hotkey.matches(pressed(InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_U)));
    }

    @Test
    void doesNotMatchWhenAnExtraModifierIsHeld() {
        Hotkey hotkey = Hotkey.parse("Ctrl+Alt+U");

        assertFalse(hotkey.matches(pressed(
                InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_U)));
    }

    @Test
    void doesNotMatchADifferentKey() {
        Hotkey hotkey = Hotkey.parse("Ctrl+Alt+U");

        assertFalse(hotkey.matches(pressed(InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_K)));
    }

    @Test
    void doesNotMatchKeyReleasedOrKeyTyped() {
        Hotkey hotkey = Hotkey.parse("Ctrl+Alt+U");
        int mods = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        KeyEvent released = new KeyEvent(
                new JPanel(), KeyEvent.KEY_RELEASED, 0L, mods, KeyEvent.VK_U, KeyEvent.CHAR_UNDEFINED);

        assertFalse(hotkey.matches(released));
    }
}
