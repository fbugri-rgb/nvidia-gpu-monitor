package nvidiamonitor.ui;

import nvidiamonitor.model.Meter;
import nvidiamonitor.service.Platform;

import java.awt.Point;
import java.util.prefs.Preferences;

/**
 * Bewaart instellingen (transparantie, zichtbare meters, gekozen GPU, ...) tussen sessies.
 * Instellingen per venster krijgen de naam van het venster als prefix ("main" of "widget").
 */
public final class Settings {
    private static final Preferences PREFS = Preferences.userNodeForPackage(Settings.class);

    /** Waarde van {@link #gpuSelection()} wanneer alle kaarten getoond worden. */
    public static final int ALL_GPUS = -1;

    /** Startoptie om de doorzichtige achtergrond aan of uit te zetten, bv. {@code -Dnvidiamonitor.transparent=false}. */
    public static final String TRANSPARENT_OPTION = "nvidiamonitor.transparent";

    private Settings() {
    }

    public static float opacity(String window) {
        return PREFS.getFloat(window + ".opacity", 1f);
    }

    public static void opacity(String window, float value) {
        PREFS.putFloat(window + ".opacity", value);
    }

    public static float background(String window, float defaultValue) {
        return PREFS.getFloat(window + ".background", defaultValue);
    }

    public static void saveBackground(String window, float value) {
        PREFS.putFloat(window + ".background", value);
    }

    /**
     * Doorzichtige achtergrond voor beide vensters. De startoptie {@code -Dnvidiamonitor.transparent=true|false}
     * gaat voor op de opgeslagen keuze. Standaard uit op Linux: daar flikkeren doorzichtige vensters,
     * omdat het venster eerst leeggemaakt en dan pas opnieuw getekend wordt.
     */
    public static boolean transparentBackground() {
        String option = System.getProperty(TRANSPARENT_OPTION);
        if (option != null) {
            return Boolean.parseBoolean(option);
        }
        return PREFS.getBoolean("transparentBackground", Platform.current() != Platform.LINUX);
    }

    public static void transparentBackground(boolean value) {
        PREFS.putBoolean("transparentBackground", value);
        System.clearProperty(TRANSPARENT_OPTION); // de keuze in het menu geldt vanaf nu, ook met de startoptie
    }

    public static boolean alwaysOnTop(String window, boolean defaultValue) {
        return PREFS.getBoolean(window + ".onTop", defaultValue);
    }

    public static void saveAlwaysOnTop(String window, boolean value) {
        PREFS.putBoolean(window + ".onTop", value);
    }

    public static boolean meterVisible(Meter meter) {
        return PREFS.getBoolean("meter." + meter.name(), true);
    }

    public static void meterVisible(Meter meter, boolean value) {
        PREFS.putBoolean("meter." + meter.name(), value);
    }

    public static int gpuSelection() {
        return PREFS.getInt("gpu", ALL_GPUS);
    }

    public static void gpuSelection(int index) {
        PREFS.putInt("gpu", index);
    }

    public static boolean compact() {
        return PREFS.getBoolean("compact", false);
    }

    public static void compact(boolean value) {
        PREFS.putBoolean("compact", value);
    }

    public static Point widgetLocation() {
        int x = PREFS.getInt("widgetX", Integer.MIN_VALUE);
        int y = PREFS.getInt("widgetY", Integer.MIN_VALUE);
        return x == Integer.MIN_VALUE ? null : new Point(x, y);
    }

    public static void widgetLocation(Point p) {
        PREFS.putInt("widgetX", p.x);
        PREFS.putInt("widgetY", p.y);
    }
}
