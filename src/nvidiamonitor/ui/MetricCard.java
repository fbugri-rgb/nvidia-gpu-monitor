package nvidiamonitor.ui;

import nvidiamonitor.model.Meter;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Kaartje met titel, huidige waarde en een grafiek van de geschiedenis.
 * In compacte modus blijft enkel een korte titel, de waarde en een kleinere grafiek over.
 */
public class MetricCard extends JPanel {
    private final Meter meter;
    private final WindowStyle style;
    private final JLabel titleLabel = new JLabel();
    private final JLabel valueLabel = new JLabel("-");
    private final JLabel detailLabel = new JLabel(" ");
    private final JPanel header = new JPanel();
    private final HistoryGraph graph;

    public MetricCard(Meter meter, WindowStyle style, double max) {
        super(new BorderLayout());
        this.meter = meter;
        this.style = style;
        setOpaque(false);

        titleLabel.setForeground(Theme.color(meter));
        valueLabel.setForeground(Theme.TEXT);
        detailLabel.setFont(Theme.SMALL);
        detailLabel.setForeground(Theme.TEXT_MUTED);
        header.setOpaque(false);

        graph = new HistoryGraph(Theme.color(meter), max);
        add(header, BorderLayout.NORTH);
        add(graph, BorderLayout.CENTER);
        setCompact(false);
    }

    public void setCompact(boolean compact) {
        header.removeAll();
        if (compact) {
            header.setLayout(new BorderLayout(8, 0));
            titleLabel.setText(meter.shortLabel());
            titleLabel.setFont(Theme.TITLE);
            valueLabel.setFont(Theme.TITLE);
            header.add(titleLabel, BorderLayout.WEST);
            header.add(valueLabel, BorderLayout.EAST);
            setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
            ((BorderLayout) getLayout()).setVgap(4);
            graph.setPreferredSize(new Dimension(150, 56));
        } else {
            header.setLayout(new BorderLayout());
            titleLabel.setText(meter.label());
            titleLabel.setFont(Theme.TITLE);
            valueLabel.setFont(Theme.VALUE);
            header.add(titleLabel, BorderLayout.NORTH);
            header.add(valueLabel, BorderLayout.CENTER);
            header.add(detailLabel, BorderLayout.SOUTH);
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            ((BorderLayout) getLayout()).setVgap(8);
            graph.setPreferredSize(new Dimension(300, 120));
        }
        revalidate();
    }

    public void update(double graphValue, String value, String detail) {
        graph.add(graphValue);
        valueLabel.setText(value);
        detailLabel.setText(detail);
    }

    public void setValueColor(Color color) {
        valueLabel.setForeground(color);
    }

    public HistoryGraph graph() {
        return graph;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(style.paint(Theme.SURFACE, 255));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g2.dispose();
    }
}
