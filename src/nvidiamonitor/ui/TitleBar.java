package nvidiamonitor.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Eigen titelbalk voor een venster zonder Windows-rand (nodig om het venster transparant te kunnen maken).
 * Slepen en rechtsklikken worden doorgegeven aan het venster (zie {@link WindowMouse}).
 */
public class TitleBar extends JPanel {
    private final WindowStyle style;
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

    public TitleBar(WindowStyle style, String title) {
        super(new BorderLayout());
        this.style = style;
        setOpaque(false);
        setPreferredSize(new Dimension(0, 34));

        JLabel label = new JLabel("  ●  " + title);
        label.setFont(Theme.TITLE);
        label.setForeground(Theme.TEXT);
        add(label, BorderLayout.WEST);

        buttons.setOpaque(false);
        add(buttons, BorderLayout.EAST);
    }

    public JButton addButton(String text, String tooltip, Color hoverColor, ActionListener action) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        // symbolen zoals ☰ en ✕ zitten niet in elk lettertype
        button.setFont(text.length() <= 2 ? Theme.SYMBOL : Theme.LABEL);
        button.setForeground(Theme.TEXT);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(text.length() > 2 ? 70 : 44, 34));
        button.addActionListener(action);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hoverColor);
                button.setOpaque(true);
                button.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setOpaque(false);
                button.repaint();
            }
        });
        buttons.add(button);
        return button;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(style.paint(Theme.TITLE_BAR, 255));
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}
