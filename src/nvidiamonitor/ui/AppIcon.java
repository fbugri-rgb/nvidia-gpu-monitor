package nvidiamonitor.ui;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Het app-icoon in verschillende formaten (gegenereerd met tools/IconGenerator.java).
 * Het besturingssysteem kiest zelf het formaat voor de titelbalk, taakbalk en Alt+Tab.
 */
public final class AppIcon {
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final List<Image> IMAGES = load();

    private AppIcon() {
    }

    public static void apply(Window window) {
        if (!IMAGES.isEmpty()) {
            window.setIconImages(IMAGES);
        }
    }

    /** Icoon in het Dock van macOS wanneer de app als jar draait (de native app heeft er al een). */
    public static void applyToTaskbar() {
        if (IMAGES.isEmpty() || !Taskbar.isTaskbarSupported()) return;
        Taskbar taskbar = Taskbar.getTaskbar();
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            taskbar.setIconImage(IMAGES.getLast());
        }
    }

    private static List<Image> load() {
        List<Image> images = new ArrayList<>();
        for (int size : SIZES) {
            try (InputStream in = AppIcon.class.getResourceAsStream("/nvidiamonitor/icons/icon-" + size + ".png")) {
                if (in != null) {
                    images.add(ImageIO.read(in));
                }
            } catch (IOException ignored) {
                // zonder icoon werkt de app ook
            }
        }
        return List.copyOf(images);
    }
}
