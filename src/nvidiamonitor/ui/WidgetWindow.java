package nvidiamonitor.ui;

import nvidiamonitor.model.GpuStats;
import nvidiamonitor.model.Meter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Klein, zwevend venster dat standaard altijd bovenaan blijft.
 * Slepen = verplaatsen, scrollwiel = achtergrond, dubbelklik = monitor openen, rechtsklik = menu.
 */
public class WidgetWindow extends JWindow {
    private static final int WIDTH = 250;
    private static final int INNER_WIDTH = WIDTH - 24;

    private final WindowStyle style;
    private final JPanel content;
    private final JLabel messageLabel = new JLabel("Loading…");
    private final Map<Integer, Block> blocks = new HashMap<>();

    public WidgetWindow(AppController app) {
        setType(Type.UTILITY); // geen knop in de taakbalk
        style = new WindowStyle(this, "widget", 0.85f, true);

        content = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(style.paint(Theme.TITLE_BAR, 235));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(Theme.withAlpha(Theme.NVIDIA_GREEN, Math.max(30, Math.round(style.background() * 120))));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        messageLabel.setFont(Theme.LABEL);
        messageLabel.setForeground(Theme.TEXT_MUTED);
        setContentPane(content);

        rebuild(List.of());
        placeOnScreen();
        WindowMouse.install(this, content, app::widgetMenu, app::showMonitor,
                () -> Settings.widgetLocation(getLocation()));
        content.addMouseWheelListener(e ->
                style.setBackground(style.background() - (float) e.getPreciseWheelRotation() * 0.05f));
    }

    public WindowStyle style() {
        return style;
    }

    public void rebuild(List<GpuStats> gpus) {
        content.removeAll();
        if (gpus.isEmpty()) {
            content.add(messageLabel);
        }
        for (int i = 0; i < gpus.size(); i++) {
            Block block = blocks.computeIfAbsent(gpus.get(i).index(), k -> new Block());
            block.layoutFor(gpus.size() > 1);
            if (i > 0) {
                content.add(Box.createVerticalStrut(10));
            }
            content.add(block);
        }
        pack();
        setSize(WIDTH, Math.max(44, getHeight()));
        revalidate();
        repaint();
    }

    public void update(List<GpuStats> gpus) {
        for (GpuStats gpu : gpus) {
            Block block = blocks.get(gpu.index());
            if (block != null) {
                block.update(gpu);
            }
        }
    }

    public void showError(String message) {
        messageLabel.setForeground(Theme.DANGER);
        messageLabel.setText("<html><div style='width:" + (INNER_WIDTH - 10) + "px'>GPU not available: "
                + message + "</div></html>");
        if (messageLabel.getParent() != null) {
            pack();
            setSize(WIDTH, getHeight());
        }
    }

    private void placeOnScreen() {
        Point saved = Settings.widgetLocation();
        if (saved != null && isOnAnyScreen(saved)) {
            setLocation(saved);
        } else {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            setLocation(screen.x + screen.width - WIDTH - 20, screen.y + 20);
        }
    }

    private static boolean isOnAnyScreen(Point p) {
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().contains(p.x + 20, p.y + 20)) {
                return true;
            }
        }
        return false;
    }

    /** De regels en het grafiekje van één kaart. */
    private static class Block extends JPanel {
        private final Rows rows = new Rows();
        private final HistoryGraph sparkline = new HistoryGraph(Theme.NVIDIA_GREEN, 100);

        Block() {
            super(new BorderLayout(0, 4));
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            sparkline.setDrawGrid(false);
            sparkline.setPreferredSize(new Dimension(INNER_WIDTH, 30));
            add(rows, BorderLayout.CENTER);
            add(sparkline, BorderLayout.SOUTH);
        }

        void layoutFor(boolean multipleGpus) {
            rows.multipleGpus = multipleGpus;
            rows.meters = Arrays.stream(Meter.values()).filter(Settings::meterVisible).toList();
            rows.setPreferredSize(new Dimension(INNER_WIDTH, 18 + rows.meters.size() * Rows.ROW_HEIGHT));
            sparkline.setVisible(Settings.meterVisible(Meter.GPU));
        }

        void update(GpuStats stats) {
            rows.stats = stats;
            sparkline.add(stats.gpuUsage());
            rows.repaint();
        }
    }

    /** Tekent de naam van de kaart en per zichtbare meter een regel met een balkje. */
    private static class Rows extends JComponent {
        private static final int ROW_HEIGHT = 22;
        private GpuStats stats;
        private List<Meter> meters = List.of();
        private boolean multipleGpus;

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();

            g2.setFont(Theme.SMALL);
            g2.setColor(Theme.TEXT_MUTED);
            if (stats == null) {
                g2.drawString("Loading…", 0, 12);
                g2.dispose();
                return;
            }
            String name = stats.name().replace("NVIDIA ", "").replace("GeForce ", "");
            g2.drawString(multipleGpus ? "GPU " + stats.index() + "  ·  " + name : name, 0, 12);

            int y = 20;
            for (Meter meter : meters) {
                switch (meter) {
                    case GPU -> row(g2, y, w, "GPU", stats.gpuUsage() / 100,
                            String.format("%.0f %%", stats.gpuUsage()), Theme.NVIDIA_GREEN);
                    case MEMORY -> row(g2, y, w, "MEM", stats.memoryPercent() / 100,
                            String.format("%.1f / %.0f GB", stats.memoryUsedMb() / 1024, stats.memoryTotalMb() / 1024),
                            Theme.MEMORY_PURPLE);
                    case TEMPERATURE -> row(g2, y, w, "TEMP", stats.temperature() / 100,
                            String.format("%.0f °C", stats.temperature()), Theme.temperatureColor(stats.temperature()));
                    case POWER -> {
                        if (GpuStats.known(stats.powerDraw())) {
                            double max = GpuStats.known(stats.powerLimit()) ? stats.powerLimit() : 175;
                            row(g2, y, w, "PWR", stats.powerDraw() / max,
                                    String.format("%.0f W", stats.powerDraw()), Theme.POWER_BLUE);
                        } else {
                            row(g2, y, w, "CLK", stats.coreClock() / 2100,
                                    String.format("%.0f MHz", stats.coreClock()), Theme.POWER_BLUE);
                        }
                    }
                }
                y += ROW_HEIGHT;
            }
            g2.dispose();
        }

        private void row(Graphics2D g2, int y, int w, String label, double fraction, String value, Color color) {
            g2.setFont(Theme.TITLE);
            g2.setColor(color);
            g2.drawString(label, 0, y + 14);

            g2.setFont(Theme.LABEL);
            g2.setColor(Theme.TEXT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(value, w - fm.stringWidth(value), y + 14);

            int barX = 44;
            int barW = w - barX - 98;
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRoundRect(barX, y + 6, barW, 8, 8, 8);
            g2.setColor(color);
            g2.fillRoundRect(barX, y + 6, (int) Math.round(barW * Math.clamp(fraction, 0, 1)), 8, 8, 8);
        }
    }
}
