package nvidiamonitor.ui;

import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

/**
 * Transparantie en "altijd bovenaan" van één venster.
 * <ul>
 *     <li>opacity: het hele venster (ook de tekst) wordt doorzichtig</li>
 *     <li>background: alleen de achtergrond wordt doorzichtig, tekst en grafieken blijven scherp</li>
 * </ul>
 * Het venster moet undecorated zijn (geen Windows-rand), anders laat Java dit niet toe.
 */
public class WindowStyle {
    private final Window window;
    private final String key;
    private final boolean uniformSupported;
    private final boolean perPixelSupported;
    private float opacity;
    private float background;

    public WindowStyle(Window window, String key, float defaultBackground, boolean defaultOnTop) {
        this.window = window;
        this.key = key;
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        uniformSupported = device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
        perPixelSupported = device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        if (perPixelSupported) {
            window.setBackground(new Color(0, 0, 0, 0));
        }
        setOpacity(Settings.opacity(key));
        setBackground(Settings.background(key, defaultBackground));
        window.setAlwaysOnTop(Settings.alwaysOnTop(key, defaultOnTop));
    }

    public float opacity() {
        return opacity;
    }

    public void setOpacity(float value) {
        opacity = Math.clamp(value, 0.3f, 1f);
        if (uniformSupported) {
            window.setOpacity(opacity);
        }
        Settings.opacity(key, opacity);
    }

    public float background() {
        return background;
    }

    public void setBackground(float value) {
        background = Math.clamp(value, 0.05f, 1f);
        Settings.saveBackground(key, background);
        window.repaint();
    }

    public boolean supportsOpacity() {
        return uniformSupported;
    }

    public boolean supportsBackground() {
        return perPixelSupported;
    }

    public void setAlwaysOnTop(boolean value) {
        window.setAlwaysOnTop(value);
        Settings.saveAlwaysOnTop(key, value);
    }

    /**
     * Geeft de kleur terug met de alpha van de achtergrond-instelling.
     * Minimum alpha 8: volledig doorzichtige pixels laten op Windows de muisklik door.
     */
    public Color paint(Color color, int maxAlpha) {
        int alpha = perPixelSupported ? Math.max(8, Math.round(maxAlpha * background)) : maxAlpha;
        return Theme.withAlpha(color, alpha);
    }
}
