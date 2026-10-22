package nvidiamonitor.ui;

import nvidiamonitor.model.GpuStats;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hoofdvenster in de stijl van Taakbeheer. Twee weergaven:
 * volledig (titelbalk, naam, kaartjes, info) of compact (enkel de grafieken).
 */
public class MainWindow extends JFrame {
    private static final Dimension FULL_SIZE = new Dimension(860, 620);

    private final WindowStyle style;
    private final TitleBar titleBar;
    private final JPanel center = new JPanel(new BorderLayout(0, 8));
    private final JPanel body = new JPanel();
    private final JPanel footer = new JPanel(new BorderLayout());
    private final JLabel messageLabel = new JLabel("Searching for NVIDIA GPU…");
    private final Map<Integer, GpuSection> sections = new HashMap<>();
    private Dimension fullSize = FULL_SIZE;
    private Boolean lastCompact;
    private int lastCount = -1;

    public MainWindow(AppController app) {
        super("NVIDIA GPU Monitor");
        setUndecorated(true); // nodig voor transparantie
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        style = new WindowStyle(this, "main", 1f, false);

        titleBar = new TitleBar(style, "NVIDIA GPU Monitor");
        titleBar.addButton("☰", "Menu", Theme.GRID, e -> {
            JButton button = (JButton) e.getSource();
            app.mainMenu().show(button, 0, button.getHeight());
        });
        titleBar.addButton("Compact", "Show only the graphs (double-click to switch back)", Theme.GRID,
                e -> app.toggleCompact());
        titleBar.addButton("Widget", "Show/hide the floating widget", Theme.GRID, e -> app.toggleWidget());
        titleBar.addButton("—", "Minimize", Theme.GRID, e -> setState(ICONIFIED));
        titleBar.addButton("✕", "Exit", Theme.DANGER, e -> app.exit());

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(style.paint(Theme.BACKGROUND, 255));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(style.paint(Theme.GRID, 255));
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
        };
        root.setOpaque(false);
        root.add(titleBar, BorderLayout.NORTH);

        messageLabel.setFont(Theme.LABEL);
        messageLabel.setForeground(Theme.TEXT_MUTED);
        body.setOpaque(false);
        footer.setOpaque(false);
        footer.add(resizeGrip(), BorderLayout.EAST);
        center.setOpaque(false);
        center.add(messageLabel, BorderLayout.NORTH);
        center.add(body, BorderLayout.CENTER);
        center.add(footer, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        setContentPane(root);

        WindowMouse.install(this, root, app::mainMenu, app::toggleCompact, () -> { });
        setSize(FULL_SIZE);
        setLocationRelativeTo(null);
    }

    public WindowStyle style() {
        return style;
    }

    /** Bouwt het venster opnieuw op voor deze kaarten, met de huidige weergave en zichtbare meters. */
    public void rebuild(List<GpuStats> gpus) {
        boolean compact = Settings.compact();
        if (Boolean.FALSE.equals(lastCompact) && lastCount <= 1) {
            fullSize = getSize(); // grootte die de gebruiker koos onthouden
        }

        titleBar.setVisible(!compact);
        footer.setVisible(!compact);
        center.setBorder(compact
                ? BorderFactory.createEmptyBorder(6, 6, 6, 6)
                : BorderFactory.createEmptyBorder(14, 16, 4, 6));

        body.removeAll();
        // compact: kaarten onder elkaar (smalle stroken), volledig: naast elkaar
        int count = Math.max(1, gpus.size());
        body.setLayout(compact ? new GridLayout(count, 1, 0, 6) : new GridLayout(1, count, 18, 0));
        for (GpuStats gpu : gpus) {
            GpuSection section = sections.computeIfAbsent(gpu.index(), i -> new GpuSection(style));
            section.layoutFor(compact, gpus.size() > 1);
            body.add(section);
        }
        messageLabel.setVisible(gpus.isEmpty());

        setMinimumSize(compact ? new Dimension(120, 60) : new Dimension(640, 420));
        if (lastCompact == null || compact != lastCompact || gpus.size() != lastCount) {
            if (compact) {
                pack();
            } else if (gpus.size() > 1) {
                int screenWidth = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width;
                setSize(Math.min(screenWidth - 40, gpus.size() * 620), fullSize.height);
            } else {
                setSize(fullSize);
            }
            keepOnScreen();
        }
        lastCompact = compact;
        lastCount = gpus.size();
        revalidate();
        repaint();
    }

    private void keepOnScreen() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = Math.max(screen.x, Math.min(getX(), screen.x + screen.width - getWidth()));
        int y = Math.max(screen.y, Math.min(getY(), screen.y + screen.height - getHeight()));
        setLocation(x, y);
    }

    public void update(List<GpuStats> gpus) {
        messageLabel.setVisible(gpus.isEmpty());
        for (GpuStats gpu : gpus) {
            GpuSection section = sections.get(gpu.index());
            if (section != null) {
                section.update(gpu);
            }
        }
    }

    public void showError(String message) {
        messageLabel.setForeground(Theme.DANGER);
        messageLabel.setText("nvidia-smi error: " + message);
        messageLabel.setVisible(true);
    }

    /** Hoekje rechtsonder om het venster te vergroten (een venster zonder rand heeft dat niet). */
    private JComponent resizeGrip() {
        JComponent grip = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(Theme.TEXT_MUTED);
                int w = getWidth(), h = getHeight();
                for (int i = 4; i <= 12; i += 4) {
                    g2.drawLine(w - i, h - 1, w - 1, h - i);
                }
            }
        };
        grip.setPreferredSize(new Dimension(16, 16));
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
        MouseAdapter resize = new MouseAdapter() {
            private Point start;
            private Dimension startSize;

            @Override
            public void mousePressed(MouseEvent e) {
                start = e.getLocationOnScreen();
                startSize = getSize();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point now = e.getLocationOnScreen();
                Dimension min = getMinimumSize();
                setSize(Math.max(min.width, startSize.width + now.x - start.x),
                        Math.max(min.height, startSize.height + now.y - start.y));
                validate();
            }
        };
        grip.addMouseListener(resize);
        grip.addMouseMotionListener(resize);
        return grip;
    }
}
