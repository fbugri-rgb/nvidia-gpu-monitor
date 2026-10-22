package nvidiamonitor;

import nvidiamonitor.ui.AppController;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        boolean widgetOnly = args.length > 0 && args[0].equals("--widget");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // standaard look & feel is ook goed
            }
            new AppController().start(widgetOnly);
        });
    }
}
