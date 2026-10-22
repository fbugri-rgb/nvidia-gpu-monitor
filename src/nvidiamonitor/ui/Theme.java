package nvidiamonitor.ui;

import nvidiamonitor.model.Meter;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

/**
 * Kleuren en lettertypes van de app (donker, zoals Taakbeheer in dark mode).
 */
public final class Theme {
    public static final Color BACKGROUND = new Color(0x1E1F22);
    public static final Color SURFACE = new Color(0x2B2D31);
    public static final Color TITLE_BAR = new Color(0x18191C);
    public static final Color GRID = new Color(0x3A3C42);
    public static final Color TEXT = new Color(0xE6E6E6);
    public static final Color TEXT_MUTED = new Color(0x9A9CA3);

    public static final Color NVIDIA_GREEN = new Color(0x76B900);
    public static final Color MEMORY_PURPLE = new Color(0xA77BFF);
    public static final Color TEMP_ORANGE = new Color(0xFF8A3D);
    public static final Color POWER_BLUE = new Color(0x3DA9FF);
    public static final Color DANGER = new Color(0xFF4D4D);

    // Segoe UI bestaat enkel op Windows; op Linux en macOS valt Java terug op het standaardlettertype.
    private static final String FAMILY = fontExists("Segoe UI") ? "Segoe UI" : Font.SANS_SERIF;
    private static final String SYMBOL_FAMILY = fontExists("Segoe UI Symbol") ? "Segoe UI Symbol" : Font.DIALOG;

    public static final Font TITLE = new Font(FAMILY, Font.BOLD, 13);
    public static final Font LABEL = new Font(FAMILY, Font.PLAIN, 12);
    public static final Font VALUE = new Font(FAMILY, Font.BOLD, 22);
    public static final Font SMALL = new Font(FAMILY, Font.PLAIN, 11);
    public static final Font HEADING = new Font(FAMILY, Font.BOLD, 20);
    /** Voor symbolen zoals ☰ en ✕. */
    public static final Font SYMBOL = new Font(SYMBOL_FAMILY, Font.PLAIN, 14);

    private Theme() {
    }

    public static Color color(Meter meter) {
        return switch (meter) {
            case GPU -> NVIDIA_GREEN;
            case MEMORY -> MEMORY_PURPLE;
            case TEMPERATURE -> TEMP_ORANGE;
            case POWER -> POWER_BLUE;
        };
    }

    /** Kleur voor de temperatuur: groen → oranje → rood. */
    public static Color temperatureColor(double celsius) {
        if (celsius >= 85) return DANGER;
        if (celsius >= 70) return TEMP_ORANGE;
        return NVIDIA_GREEN;
    }

    private static boolean fontExists(String family) {
        return Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                .contains(family);
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
