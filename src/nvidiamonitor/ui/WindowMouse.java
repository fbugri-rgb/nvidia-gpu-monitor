package nvidiamonitor.ui;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/**
 * Muisgedrag dat het hoofdvenster en de widget delen:
 * slepen = venster verplaatsen, rechtsklik = menu, dubbelklik = actie.
 * Onderliggende componenten zonder eigen muis-listeners geven hun klikken door aan dit component.
 */
final class WindowMouse {
    private WindowMouse() {
    }

    static void install(Window window, JComponent target, Supplier<JPopupMenu> menu,
                        Runnable doubleClick, Runnable moved) {
        MouseAdapter mouse = new MouseAdapter() {
            private Point offset;

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu.get().show(e.getComponent(), e.getX(), e.getY());
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    Point screen = e.getLocationOnScreen();
                    Point location = window.getLocation();
                    offset = new Point(screen.x - location.x, screen.y - location.y);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu.get().show(e.getComponent(), e.getX(), e.getY());
                } else if (offset != null) {
                    offset = null;
                    moved.run();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (offset != null && SwingUtilities.isLeftMouseButton(e)) {
                    Point screen = e.getLocationOnScreen();
                    window.setLocation(screen.x - offset.x, screen.y - offset.y);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    doubleClick.run();
                }
            }
        };
        target.addMouseListener(mouse);
        target.addMouseMotionListener(mouse);
    }
}
