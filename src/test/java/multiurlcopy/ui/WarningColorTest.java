package multiurlcopy.ui;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarningColorTest {

    @Test
    void prefersTheLookAndFeelsOwnWarningColour() {
        Color themed = new Color(1, 2, 3);
        Map<String, Color> laf = Map.of("Component.warning.focusedBorderColor", themed);

        assertEquals(themed, WarningColor.resolve(laf::get, true));
    }

    @Test
    void fallsThroughToTheNextKeyWhenTheFirstIsAbsent() {
        Color themed = new Color(4, 5, 6);
        Map<String, Color> laf = Map.of("Actions.Yellow", themed);

        assertEquals(themed, WarningColor.resolve(laf::get, false));
    }

    @Test
    void withoutAnyLafColourThePickDependsOnTheTheme() {
        Color onDark = WarningColor.resolve(key -> null, true);
        Color onLight = WarningColor.resolve(key -> null, false);

        assertNotEquals(onDark, onLight);
    }

    @Test
    void theDarkThemeFallbackIsTheLighterOfTheTwo() {
        Color onDark = WarningColor.resolve(key -> null, true);
        Color onLight = WarningColor.resolve(key -> null, false);

        assertTrue(brightness(onDark) > brightness(onLight),
                "a warning on a dark panel must be lighter than one on a light panel");
    }

    @Test
    void backgroundLightnessDecidesTheTheme() {
        assertTrue(WarningColor.isDark(new Color(0x2B, 0x2B, 0x2B)));
        assertFalse(WarningColor.isDark(new Color(0xF2, 0xF2, 0xF2)));
    }

    @Test
    void anUnknownBackgroundIsTreatedAsLight() {
        assertFalse(WarningColor.isDark(null));
    }

    private static int brightness(Color c) {
        return c.getRed() + c.getGreen() + c.getBlue();
    }
}
