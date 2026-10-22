package nvidiamonitor.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Lijngrafiek van de laatste 60 metingen, zoals in Taakbeheer.
 */
public class HistoryGraph extends JComponent {
    private static final int CAPACITY = 60;

    private final double[] values = new double[CAPACITY];
    private int count;
    private int head; // index van de volgende schrijfpositie
    private final Color color;
    private double max;
    private boolean drawGrid = true;

    public HistoryGraph(Color color, double max) {
        this.color = color;
        this.max = max;
        setPreferredSize(new Dimension(300, 120));
        setOpaque(false);
    }

    public void setMax(double max) {
        this.max = max;
    }

    public void setDrawGrid(boolean drawGrid) {
        this.drawGrid = drawGrid;
    }

    public void add(double value) {
        values[head] = Math.max(0, value);
        head = (head + 1) % CAPACITY;
        count = Math.min(count + 1, CAPACITY);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - 1;
        int h = getHeight() - 1;

        if (drawGrid) {
            g2.setColor(Theme.GRID);
            for (int i = 1; i < 4; i++) {
                int y = h * i / 4;
                g2.drawLine(0, y, w, y);
            }
            for (int i = 1; i < 6; i++) {
                int x = w * i / 6;
                g2.drawLine(x, 0, x, h);
            }
            g2.setColor(Theme.withAlpha(color, 160));
            g2.drawRect(0, 0, w, h);
        }

        if (count > 1 && max > 0) {
            Path2D.Double line = new Path2D.Double();
            double step = (double) w / (CAPACITY - 1);
            int start = CAPACITY - count;
            for (int i = 0; i < count; i++) {
                double v = values[(head - count + i + CAPACITY) % CAPACITY];
                double x = (start + i) * step;
                double y = h - Math.min(v / max, 1) * h;
                if (i == 0) line.moveTo(x, y);
                else line.lineTo(x, y);
            }
            Path2D.Double area = new Path2D.Double(line);
            area.lineTo(w, h);
            area.lineTo(start * step, h);
            area.closePath();

            g2.setPaint(new GradientPaint(0, 0, Theme.withAlpha(color, 110), 0, h, Theme.withAlpha(color, 15)));
            g2.fill(area);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(line);
        }
        g2.dispose();
    }
}
