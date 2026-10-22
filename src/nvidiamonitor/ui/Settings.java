package nvidiamonitor.ui;

import nvidiamonitor.model.Meter;

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
