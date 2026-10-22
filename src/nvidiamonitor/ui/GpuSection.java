package nvidiamonitor.ui;

import nvidiamonitor.model.GpuStats;
import nvidiamonitor.model.Meter;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Alle meters van één grafische kaart in het hoofdvenster.
 */
class GpuSection extends JPanel {
    private final Map<Meter, MetricCard> cards = new EnumMap<>(Meter.class);
    private final JLabel nameLabel = new JLabel(" ");
    private final JLabel driverLabel = new JLabel(" ");
    private final JLabel infoLabel = new JLabel(" ");
    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel grid = new JPanel();
    private boolean compact;
    private boolean multipleGpus;

    GpuSection(WindowStyle style) {
        super(new BorderLayout());
        setOpaque(false);
        cards.put(Meter.GPU, new MetricCard(Meter.GPU, style, 100));
        cards.put(Meter.MEMORY, new MetricCard(Meter.MEMORY, style, 1));
        cards.put(Meter.TEMPERATURE, new MetricCard(Meter.TEMPERATURE, style, 100));
        cards.put(Meter.POWER, new MetricCard(Meter.POWER, style, 175));

        nameLabel.setForeground(Theme.TEXT);
        driverLabel.setFont(Theme.LABEL);
        driverLabel.setForeground(Theme.TEXT_MUTED);
        infoLabel.setFont(Theme.LABEL);
        infoLabel.setForeground(Theme.TEXT_MUTED);
        header.setOpaque(false);
        grid.setOpaque(false);
    }

    /** Bouwt de sectie opnieuw op met de zichtbare meters (de geschiedenis van de grafieken blijft behouden). */
    void layoutFor(boolean compact, boolean multipleGpus) {
        this.compact = compact;
        this.multipleGpus = multipleGpus;
        removeAll();
        header.removeAll();
        grid.removeAll();

        List<Meter> visible = Arrays.stream(Meter.values()).filter(Settings::meterVisible).toList();
        int n = Math.max(1, visible.size());
        grid.setLayout(compact
                ? new GridLayout(1, n, 8, 0)
                : new GridLayout((n + 1) / 2, Math.min(n, 2), 12, 12));
        for (Meter meter : visible) {
            MetricCard card = cards.get(meter);
            card.setCompact(compact);
            grid.add(card);
        }

        ((BorderLayout) getLayout()).setVgap(compact ? 4 : 10);
        if (compact) {
            // compact: enkel de grafieken, de naam alleen als er meerdere kaarten zijn
            if (multipleGpus) {
                nameLabel.setFont(Theme.SMALL);
                add(nameLabel, BorderLayout.NORTH);
            }
        } else {
            nameLabel.setFont(Theme.HEADING);
            header.add(nameLabel, BorderLayout.NORTH);
            header.add(driverLabel, BorderLayout.SOUTH);
            add(header, BorderLayout.NORTH);
            add(infoLabel, BorderLayout.SOUTH);
        }
        add(grid, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    void update(GpuStats s) {
        nameLabel.setText(multipleGpus ? "GPU " + s.index() + "  ·  " + s.name() : s.name());
        driverLabel.setText("Driver " + s.driverVersion());

        cards.get(Meter.GPU).update(s.gpuUsage(), String.format("%.0f %%", s.gpuUsage()),
                String.format("Memory controller load %.0f %%", s.memoryUsage()));

        MetricCard memory = cards.get(Meter.MEMORY);
        memory.graph().setMax(s.memoryTotalMb());
        memory.update(s.memoryUsedMb(),
                compact ? String.format("%.1f/%.0f GB", s.memoryUsedMb() / 1024, s.memoryTotalMb() / 1024)
                        : formatMemory(s.memoryUsedMb()) + " / " + formatMemory(s.memoryTotalMb()),
                String.format("%.1f %% in use", s.memoryPercent()));

        MetricCard temp = cards.get(Meter.TEMPERATURE);
        temp.update(s.temperature(), String.format("%.0f °C", s.temperature()), temperatureHint(s.temperature()));
        temp.setValueColor(Theme.temperatureColor(s.temperature()));

        MetricCard power = cards.get(Meter.POWER);
        if (GpuStats.known(s.powerDraw())) {
            boolean limitKnown = GpuStats.known(s.powerLimit());
            power.graph().setMax(limitKnown ? s.powerLimit() : 175);
            power.update(s.powerDraw(), String.format(compact ? "%.0f W" : "%.1f W", s.powerDraw()),
                    limitKnown ? String.format("Limit %.0f W", s.powerLimit()) : "Limit not reported");
        } else {
            power.update(0, "N/A", "Power draw not reported by driver");
        }

        infoLabel.setText(String.format("Core clock  %s     Memory clock  %s     Fan  %s",
                mhz(s.coreClock()), mhz(s.memoryClock()),
                GpuStats.known(s.fanSpeed()) ? String.format("%.0f %%", s.fanSpeed()) : "N/A"));
    }

    private static String temperatureHint(double celsius) {
        if (celsius >= 85) return "Hot – check cooling";
        if (celsius >= 70) return "Warm";
        return "Normal";
    }

    private static String mhz(double value) {
        return GpuStats.known(value) ? String.format("%.0f MHz", value) : "N/A";
    }

    private static String formatMemory(double mb) {
        return mb >= 1024 ? String.format("%.1f GB", mb / 1024) : String.format("%.0f MB", mb);
    }
}
