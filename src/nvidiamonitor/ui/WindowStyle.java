package nvidiamonitor.ui;

import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

/**
 * Transparantie en "altijd bovenaan" van één venster.
 * <ul>
 *     <li>opacity: het hele venster (ook de tekst) wordt doorzichtig</li>
 *     <li>background: alleen de achtergrond wordt doorzichtig, tekst en grafieken blijven scherp.
 *         Kan uitgezet worden met {@link Settings#transparentBackground()} (standaard uit op Linux,
 *         waar zo'n venster flikkert); het venster krijgt dan een gewone, effen achtergrond.</li>
 * </ul>
 * Het venster moet undecorated zijn (geen Windows-rand), anders laat Java dit niet toe.
 */
public class WindowStyle {
    private final Window window;
    private final String key;
    private final Color solidBackground;
    private final boolean uniformSupported;
    private final boolean perPixelSupported;
    private boolean transparent;
    private float opacity;
    private float background;

    /**
     * @param solidBackground kleur van het venster wanneer de achtergrond niet doorzichtig is
     *                        (dezelfde kleur als wat het venster zelf tekent, zodat afgeronde hoeken niet opvallen)
     */
    public WindowStyle(Window window, String key, Color solidBackground, float defaultBackground, boolean defaultOnTop) {
        this.window = window;
        this.key = key;
        this.solidBackground = solidBackground;
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        uniformSupported = device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
        perPixelSupported = device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        applyTransparentBackground();
        setOpacity(Settings.opacity(key));
        setBackground(Settings.background(key, defaultBackground));
        window.setAlwaysOnTop(Settings.alwaysOnTop(key, defaultOnTop));
    }

    /**
     * Zet de achtergrond doorzichtig of effen volgens {@link Settings#transparentBackground()}.
     * Op een venster dat al getoond is, werkt dit pas nadat het met dispose() gesloten en opnieuw getoond is:
     * op Linux kiest Java alleen bij het aanmaken van het venster of het doorzichtig kan zijn.
     */
    void applyTransparentBackground() {
        transparent = perPixelSupported && Settings.transparentBackground();
        window.setBackground(transparent ? new Color(0, 0, 0, 0) : solidBackground);
        window.repaint();
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

    /** Of dit systeem een doorzichtige achtergrond aankan. */
    public boolean supportsTransparentBackground() {
        return perPixelSupported;
    }

    /** Of de achtergrond nu doorzichtig is (en de schuifbalk "Background" dus iets doet). */
    public boolean isTransparentBackground() {
        return transparent;
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
        int alpha = transparent ? Math.max(8, Math.round(maxAlpha * background)) : maxAlpha;
        return Theme.withAlpha(color, alpha);
    }
}
