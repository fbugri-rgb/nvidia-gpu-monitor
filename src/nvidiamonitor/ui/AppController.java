package nvidiamonitor.ui;

import nvidiamonitor.model.GpuStats;
import nvidiamonitor.model.Meter;
import nvidiamonitor.service.AutoStart;
import nvidiamonitor.service.GpuMonitor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Verbindt de GPU-monitor met de vensters en bouwt het rechtsklikmenu dat beide vensters delen.
 */
public class AppController {
    /** Versie uit het manifest van de jar (ingevuld door Gradle), "dev" wanneer je vanuit IntelliJ start. */
    private static final String VERSION = Objects.requireNonNullElse(
            AppController.class.getPackage().getImplementationVersion(), "dev");

    private final GpuMonitor monitor = new GpuMonitor();
    private final MainWindow main;
    private final WidgetWindow widget;
    private List<GpuStats> allGpus = List.of();
    private List<Integer> shownIndices = List.of();

    public AppController() {
        main = new MainWindow(this);
        widget = new WidgetWindow(this);
        monitor.addListener(this::onStats);
        monitor.addErrorListener(message -> {
            main.showError(message);
            widget.showError(message);
        });
    }

    public void start(boolean widgetOnly) {
        main.rebuild(List.of());
        if (widgetOnly) {
            widget.setVisible(true);
        } else {
            main.setVisible(true);
        }
        monitor.start(1000);
    }

    private void onStats(List<GpuStats> gpus) {
        allGpus = gpus;
        List<GpuStats> shown = shownGpus();
        List<Integer> indices = shown.stream().map(GpuStats::index).toList();
        if (!indices.equals(shownIndices)) {
            shownIndices = indices;
            main.rebuild(shown);
            widget.rebuild(shown);
        }
        main.update(shown);
        widget.update(shown);
    }

    /** De kaarten die volgens de keuze onder "Hardware" getoond moeten worden. */
    private List<GpuStats> shownGpus() {
        int selection = Settings.gpuSelection();
        List<GpuStats> selected = allGpus.stream().filter(g -> g.index() == selection).toList();
        return selected.isEmpty() ? allGpus : selected;
    }

    /** Opnieuw opbouwen na een wijziging van meters, hardware of weergave. */
    private void viewChanged() {
        shownIndices = List.of();
        onStats(allGpus);
    }

    // ---------- acties ----------

    public void toggleCompact() {
        Settings.compact(!Settings.compact());
        viewChanged();
    }

    public void toggleWidget() {
        setWidgetVisible(!widget.isVisible());
    }

    public void showMonitor() {
        main.setVisible(true);
        main.setState(Frame.NORMAL);
        main.toFront();
    }

    private void setWidgetVisible(boolean visible) {
        widget.setVisible(visible);
        if (!visible && !main.isVisible()) {
            showMonitor(); // er moet altijd iets zichtbaar blijven
        }
    }

    private void hideMonitor() {
        if (!widget.isVisible()) {
            widget.setVisible(true);
        }
        main.setVisible(false);
    }

    public void exit() {
        monitor.stop();
        System.exit(0);
    }

    // ---------- menu's ----------

    public JPopupMenu mainMenu() {
        JPopupMenu menu = new JPopupMenu();
        JCheckBoxMenuItem compact = new JCheckBoxMenuItem("Compact view (graphs only)", Settings.compact());
        compact.addActionListener(e -> toggleCompact());
        JCheckBoxMenuItem showWidget = new JCheckBoxMenuItem("Show widget", widget.isVisible());
        showWidget.addActionListener(e -> setWidgetVisible(showWidget.isSelected()));
        JMenuItem toWidget = new JMenuItem("Switch to widget only");
        toWidget.addActionListener(e -> hideMonitor());
        menu.add(compact);
        menu.add(showWidget);
        menu.add(toWidget);
        addCommonItems(menu, main, main.style());
        return menu;
    }

    public JPopupMenu widgetMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open monitor");
        open.addActionListener(e -> showMonitor());
        JMenuItem hide = new JMenuItem("Hide widget");
        hide.addActionListener(e -> setWidgetVisible(false));
        menu.add(open);
        menu.add(hide);
        addCommonItems(menu, widget, widget.style());
        return menu;
    }

    private void addCommonItems(JPopupMenu menu, Window window, WindowStyle style) {
        menu.addSeparator();
        menu.add(metersMenu());
        menu.add(hardwareMenu());

        menu.addSeparator();
        menu.add(sliderItem("Window opacity", 30, style.opacity(), style.supportsOpacity(), style::setOpacity));
        menu.add(sliderItem("Background", 5, style.background(), style.supportsBackground(), style::setBackground));
        JCheckBoxMenuItem onTop = new JCheckBoxMenuItem("Always on top", window.isAlwaysOnTop());
        onTop.addActionListener(e -> style.setAlwaysOnTop(onTop.isSelected()));
        menu.add(onTop);
        JCheckBoxMenuItem autoStart = new JCheckBoxMenuItem(AutoStart.label(), AutoStart.isEnabled());
        autoStart.addActionListener(e -> {
            try {
                AutoStart.setEnabled(autoStart.isSelected());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(window, ex.getMessage(), AutoStart.label(), JOptionPane.ERROR_MESSAGE);
            }
        });
        menu.add(autoStart);

        menu.addSeparator();
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> showAbout(window));
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> exit());
        menu.add(about);
        menu.add(exit);
    }

    private JMenu metersMenu() {
        JMenu menu = new JMenu("Show / hide meters");
        for (Meter meter : Meter.values()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(meter.label(), Settings.meterVisible(meter));
            item.addActionListener(e -> {
                long visible = Arrays.stream(Meter.values()).filter(Settings::meterVisible).count();
                if (!item.isSelected() && visible <= 1) {
                    Toolkit.getDefaultToolkit().beep(); // minstens één meter moet zichtbaar blijven
                    return;
                }
                Settings.meterVisible(meter, item.isSelected());
                viewChanged();
            });
            menu.add(item);
        }
        return menu;
    }

    private JMenu hardwareMenu() {
        JMenu menu = new JMenu("Hardware");
        if (allGpus.isEmpty()) {
            JMenuItem none = new JMenuItem("No NVIDIA GPU detected");
            none.setEnabled(false);
            menu.add(none);
            return menu;
        }
        int selection = Settings.gpuSelection();
        boolean selectionExists = allGpus.stream().anyMatch(g -> g.index() == selection);
        ButtonGroup group = new ButtonGroup();

        JRadioButtonMenuItem all = new JRadioButtonMenuItem("All GPUs (" + allGpus.size() + ")", !selectionExists);
        all.addActionListener(e -> selectGpu(Settings.ALL_GPUS));
        group.add(all);
        menu.add(all);
        menu.addSeparator();
        for (GpuStats gpu : allGpus) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem("GPU " + gpu.index() + ":  " + gpu.name(),
                    selectionExists && gpu.index() == selection);
            item.addActionListener(e -> selectGpu(gpu.index()));
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    private void selectGpu(int index) {
        Settings.gpuSelection(index);
        viewChanged();
    }

    /** Een schuifbalk met label en percentage, rechtstreeks in het menu. */
    private static JComponent sliderItem(String text, int min, float value, boolean enabled, Consumer<Float> onChange) {
        JLabel label = new JLabel(text);
        label.setPreferredSize(new Dimension(100, 20));
        JLabel percent = new JLabel(Math.round(value * 100) + " %");
        percent.setPreferredSize(new Dimension(40, 20));
        JSlider slider = new JSlider(min, 100, Math.max(min, Math.round(value * 100)));
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setEnabled(enabled);
        slider.setPreferredSize(new Dimension(140, 24));
        slider.addChangeListener(e -> {
            percent.setText(slider.getValue() + " %");
            onChange.accept(slider.getValue() / 100f);
        });
        if (!enabled) {
            slider.setToolTipText("Not supported on this system");
        }

        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(3, 30, 3, 10));
        panel.add(label, BorderLayout.WEST);
        panel.add(slider, BorderLayout.CENTER);
        panel.add(percent, BorderLayout.EAST);
        return panel;
    }

    private void showAbout(Window owner) {
        StringBuilder hardware = new StringBuilder();
        if (allGpus.isEmpty()) {
            hardware.append("No NVIDIA GPU detected<br>");
        }
        for (GpuStats gpu : allGpus) {
            hardware.append("GPU ").append(gpu.index()).append(": ").append(gpu.name())
                    .append(String.format(" (%.0f GB)", gpu.memoryTotalMb() / 1024)).append("<br>");
        }
        if (!allGpus.isEmpty()) {
            hardware.append("Driver ").append(allGpus.getFirst().driverVersion()).append("<br>");
        }
        String html = "<html><div style='width:300px'>"
                + "<b style='font-size:14px'>NVIDIA GPU Monitor</b>&nbsp;&nbsp;v" + VERSION + "<br><br>"
                + "Shows usage, memory, temperature and power of NVIDIA graphics cards, "
                + "updated every second via <i>nvidia-smi</i>.<br><br>"
                + "<b>Hardware</b><br>" + hardware + "<br>"
                + "<b>Tips</b><br>"
                + "• Right-click anywhere for the menu<br>"
                + "• Double-click the monitor to switch to compact view<br>"
                + "• Double-click the widget to open the monitor<br>"
                + "• Scroll on the widget to change its background<br><br>"
                + "<span style='color:gray'>Java " + System.getProperty("java.version") + " · "
                + System.getProperty("os.name") + "</span>"
                + "</div></html>";
        JOptionPane.showMessageDialog(owner, html, "About NVIDIA GPU Monitor", JOptionPane.INFORMATION_MESSAGE);
    }
}
