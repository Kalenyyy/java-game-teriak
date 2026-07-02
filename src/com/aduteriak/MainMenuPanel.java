package com.aduteriak;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MainMenuPanel.java
 * Halaman Main Menu game "AAAAAAAAAAAA" (voice-powered scream game).
 * Versi JPanel agar bisa ditempel langsung ke MainFrame (CardLayout / showView).
 */
public class MainMenuPanel extends JPanel {

    public MainMenuPanel(MainFrame parent) {
        setLayout(new BorderLayout());
        setOpaque(true);

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(parent), BorderLayout.CENTER);
        add(buildFooterPanel(), BorderLayout.SOUTH);
    }

    // =========================================================
    //  LATAR BELAKANG (Gradient + Siluet Stickman)
    // =========================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Vertical Gradient: abu-abu terang (atas) -> hitam (bawah)
        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(210, 210, 210),
                0, h, new Color(20, 20, 20)
        );
        g2.setPaint(gradient);
        g2.fillRect(0, 0, w, h);

        drawStickman(g2, w / 2, h / 2, Math.min(w, h) * 0.55f);

        g2.dispose();
    }

    private void drawStickman(Graphics2D g2, int cx, int cy, float scale) {
        g2.setColor(new Color(255, 255, 255, 25)); // putih tipis, opacity rendah
        g2.setStroke(new BasicStroke(scale * 0.035f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        float headR = scale * 0.14f;
        float bodyTop = cy - scale * 0.28f;
        float bodyBottom = cy + scale * 0.15f;

        Ellipse2D head = new Ellipse2D.Float(cx - headR, bodyTop - headR * 2, headR * 2, headR * 2);
        g2.draw(head);

        g2.draw(new Line2D.Float(cx, bodyTop, cx, bodyBottom));
        g2.draw(new Line2D.Float(cx, bodyTop + scale * 0.06f, cx - scale * 0.22f, bodyTop + scale * 0.22f));
        g2.draw(new Line2D.Float(cx, bodyTop + scale * 0.06f, cx + scale * 0.22f, bodyTop + scale * 0.22f));
        g2.draw(new Line2D.Float(cx, bodyBottom, cx - scale * 0.20f, cy + scale * 0.45f));
        g2.draw(new Line2D.Float(cx, bodyBottom, cx + scale * 0.20f, cy + scale * 0.45f));
    }

    // =========================================================
    //  HEADER: Judul Utama + Sub-teks
    // =========================================================
    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(40, 10, 10, 10));

        JLabel title = new JLabel("AAAAAAAAAAAA");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(pickTitleFont(64));
        title.setForeground(new Color(15, 15, 15));

        JLabel subtitle = new JLabel("VOICE-POWERED  .  TERIAK SEKENCENG MUNGKIN");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setFont(new Font("Arial", Font.PLAIN, 15));
        subtitle.setForeground(new Color(90, 90, 90));
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        panel.add(title);
        panel.add(subtitle);
        return panel;
    }

    private Font pickTitleFont(int size) {
        String[] candidates = {"Impact", "Arial Black", "Haettenschweiler", "Arial"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        List<String> availableList = java.util.Arrays.asList(available);
        for (String name : candidates) {
            if (availableList.contains(name)) {
                int style = name.equals("Arial") ? Font.BOLD : Font.PLAIN;
                return new Font(name, style, size);
            }
        }
        return new Font("SansSerif", Font.BOLD, size);
    }

    // =========================================================
    //  CENTER: Tombol-tombol Menu
    // =========================================================
    private JPanel buildCenterPanel(MainFrame parent) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);

        JPanel menuBox = new JPanel();
        menuBox.setOpaque(false);
        menuBox.setLayout(new BoxLayout(menuBox, BoxLayout.Y_AXIS));

        List<MenuButton> buttons = new ArrayList<>();

        MenuButton btnMainSendiri = new MenuButton("MAIN SENDIRI (Belum Ada)", false);
        MenuButton btnDuel = new MenuButton("MODE DUEL", false);
        MenuButton btnTurnamen = new MenuButton("MODE TURNAMEN", false);
        MenuButton btnPeringkat = new MenuButton("PAPAN PERINGKAT (Belum Ada)", false);
        MenuButton btnPengaturan = new MenuButton("PENGATURAN (Belum Ada)", false);

        buttons.add(btnMainSendiri);
        buttons.add(btnDuel);
        buttons.add(btnTurnamen);
        buttons.add(btnPeringkat);
        buttons.add(btnPengaturan);

        // Klik tombol -> jadikan tombol tsb aktif, lainnya non-aktif
        for (MenuButton b : buttons) {
            b.addActionListener(e -> {
                for (MenuButton other : buttons) other.setActiveState(other == b);
            });
        }

        // Sambungkan ke navigasi MainFrame kamu (sesuaikan nama view sesuai kebutuhan)
        btnMainSendiri.addActionListener(e -> parent.showView("SOLO"));
        btnDuel.addActionListener(e -> parent.showView("INPUT_2P"));
        btnTurnamen.addActionListener(e -> parent.showView("INPUT_TOURNAMENT"));
        btnPeringkat.addActionListener(e -> parent.showView("LEADERBOARD"));
        btnPengaturan.addActionListener(e -> parent.showView("SETTINGS"));

        menuBox.add(Box.createVerticalGlue());
        for (MenuButton b : buttons) {
            menuBox.add(b);
            menuBox.add(Box.createRigidArea(new Dimension(0, 14)));
        }

        // Tombol "KELUAR" dengan gaya kotak yang sama seperti tombol lain
        MenuButton btnExit = new MenuButton("KELUAR", false);
        btnExit.addActionListener(e -> System.exit(0));

        menuBox.add(btnExit);
        menuBox.add(Box.createVerticalGlue());

        wrapper.add(menuBox);
        return wrapper;
    }

    // =========================================================
    //  FOOTER
    // =========================================================
    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 22, 0));

        JLabel footer = new JLabel("AKTIFKAN MIKROFON  .  TERIAK SEKENCANG MUNGKIN");
        footer.setFont(new Font("Arial", Font.BOLD, 13));
        footer.setForeground(new Color(25, 25, 25));

        panel.add(footer);
        return panel;
    }

    // =========================================================
    //  TOMBOL MENU KUSTOM (borderless, hover hitam-putih)
    // =========================================================
    static class MenuButton extends JButton {
        private boolean hover = false;
        private boolean selected;

        private static final Color BG_NORMAL = new Color(210, 210, 210, 130);
        private static final Color TEXT_NORMAL = new Color(55, 55, 55);
        private static final Color BG_ACTIVE = new Color(15, 15, 15, 235);
        private static final Color TEXT_ACTIVE = Color.WHITE;

        MenuButton(String text, boolean selectedDefault) {
            super(text);
            this.selected = selectedDefault;

            setFont(new Font("Arial", Font.BOLD, 18));
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setMaximumSize(new Dimension(340, 52));
            setPreferredSize(new Dimension(340, 52));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setActiveState(boolean value) {
            this.selected = value;
            repaint();
        }

        private boolean isActiveVisual() {
            return hover || selected;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(isActiveVisual() ? BG_ACTIVE : BG_NORMAL);
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.dispose();

            setForeground(isActiveVisual() ? TEXT_ACTIVE : TEXT_NORMAL);
            super.paintComponent(g);
        }
    }
}