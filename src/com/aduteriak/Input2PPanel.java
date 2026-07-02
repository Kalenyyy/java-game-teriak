package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * Input2PPanel.java
 * Halaman input nama untuk Mode Duel (2 pemain), sebelum masuk ke GamePanel.
 *
 * Tema visual disamakan dengan MainMenuPanel: gradient monochrome, siluet
 * stickman transparan, font Impact/Arial Black untuk judul, dan gaya tombol
 * borderless dengan efek hover hitam-putih.
 *
 * Efek tambahan:
 * - Fade-in dari gelap saat panel pertama kali tampil.
 * - Kolom nama custom (rounded) dengan glow border saat sedang fokus diketik.
 * - Badge "VS" berdenyut lembut di antara dua kolom nama.
 * - Tombol MULAI dengan efek hover + glow pulsing halus.
 *
 * Logika inti (pembuatan Player, pengisian queue, navigasi) TIDAK diubah
 * dari versi asli, hanya dibungkus tampilan yang lebih sesuai tema.
 */
public class Input2PPanel extends JPanel {

    private float entranceAlpha = 1f; // 1 = gelap penuh, 0 = transparan (reveal selesai)
    private Timer entranceTimer;

    public Input2PPanel(MainFrame parent) {
        setLayout(new BorderLayout());
        setOpaque(true);

        add(buildHeaderPanel(parent), BorderLayout.NORTH);
        add(buildCenterPanel(parent), BorderLayout.CENTER);
        add(buildFooterPanel(), BorderLayout.SOUTH);

        startEntranceAnimation();
    }

    // =========================================================
    //  LATAR BELAKANG: Gradient + Siluet Stickman (senada Main Menu)
    // =========================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(200, 200, 200),
                0, h, new Color(15, 15, 15)
        );
        g2.setPaint(gradient);
        g2.fillRect(0, 0, w, h);

        // Dua siluet stickman tipis, mewakili Pemain 1 & Pemain 2
        drawStickman(g2, (int) (w * 0.28f), h / 2, Math.min(w, h) * 0.42f);
        drawStickman(g2, (int) (w * 0.72f), h / 2, Math.min(w, h) * 0.42f);

        g2.dispose();

        // Overlay fade-in dari hitam pekat -> transparan saat panel baru dibuka
        if (entranceAlpha > 0f) {
            Graphics2D gf = (Graphics2D) g.create();
            gf.setColor(new Color(0, 0, 0, (int) (entranceAlpha * 255)));
            gf.fillRect(0, 0, w, h);
            gf.dispose();
        }
    }

    private void drawStickman(Graphics2D g2, int cx, int cy, float scale) {
        g2.setColor(new Color(255, 255, 255, 22));
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

    private void startEntranceAnimation() {
        entranceTimer = new Timer(16, e -> {
            entranceAlpha -= 0.045f;
            if (entranceAlpha <= 0f) {
                entranceAlpha = 0f;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        entranceTimer.start();
    }

    // =========================================================
    //  HEADER: Tombol Kembali + Judul
    // =========================================================
    private JPanel buildHeaderPanel(MainFrame parent) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);

        JButton btnBack = new JButton("\u2190 KEMBALI");
        btnBack.setFont(new Font("Arial", Font.PLAIN, 14));
        btnBack.setForeground(new Color(40, 40, 40));
        btnBack.setContentAreaFilled(false);
        btnBack.setBorderPainted(false);
        btnBack.setFocusPainted(false);
        btnBack.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBack.setBorder(new EmptyBorder(16, 18, 0, 0));
        btnBack.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnBack.setText("<html><u>\u2190 KEMBALI</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnBack.setText("\u2190 KEMBALI");
            }
        });
        btnBack.addActionListener(e -> parent.showView("MENU_UTAMA"));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setBorder(new EmptyBorder(30, 10, 6, 10));

        JLabel sub = new JLabel("PERSIAPAN MODE DUEL");
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setFont(new Font("Arial", Font.PLAIN, 14));
        sub.setForeground(new Color(90, 90, 90));

        JLabel title = new JLabel("MASUKKAN NAMA");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(pickTitleFont(38));
        title.setForeground(new Color(15, 15, 15));

        titleBox.add(sub);
        titleBox.add(title);

        wrapper.add(btnBack, BorderLayout.WEST);
        wrapper.add(titleBox, BorderLayout.CENTER);
        return wrapper;
    }

    private Font pickTitleFont(int size) {
        String[] candidates = {"Impact", "Arial Black", "Haettenschweiler", "Arial"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        java.util.List<String> availableList = java.util.Arrays.asList(available);
        for (String name : candidates) {
            if (availableList.contains(name)) {
                int style = name.equals("Arial") ? Font.BOLD : Font.PLAIN;
                return new Font(name, style, size);
            }
        }
        return new Font("SansSerif", Font.BOLD, size);
    }

    // =========================================================
    //  CENTER: Form Nama Pemain (vertikal, dipisah badge VS)
    // =========================================================
    private JPanel buildCenterPanel(MainFrame parent) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);

        JPanel formBox = new JPanel();
        formBox.setOpaque(false);
        formBox.setLayout(new BoxLayout(formBox, BoxLayout.Y_AXIS));

        JLabel lbl1 = buildFieldLabel("PEMAIN 1");
        RoundedTextField field1 = new RoundedTextField("Pemain 1");

        VsBadge vsBadge = new VsBadge();

        JLabel lbl2 = buildFieldLabel("PEMAIN 2");
        RoundedTextField field2 = new RoundedTextField("Pemain 2");

        formBox.add(lbl1);
        formBox.add(Box.createRigidArea(new Dimension(0, 6)));
        formBox.add(field1);
        formBox.add(Box.createRigidArea(new Dimension(0, 18)));
        formBox.add(vsBadge);
        formBox.add(Box.createRigidArea(new Dimension(0, 18)));
        formBox.add(lbl2);
        formBox.add(Box.createRigidArea(new Dimension(0, 6)));
        formBox.add(field2);

        // Simpan referensi field lewat closure untuk dipakai tombol MULAI di footer
        this.putClientProperty("field1", field1);
        this.putClientProperty("field2", field2);

        wrapper.add(formBox);
        return wrapper;
    }

    private JLabel buildFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setForeground(new Color(70, 70, 70));
        return lbl;
    }

    // =========================================================
    //  FOOTER: Tombol MULAI
    // =========================================================
    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 0, 34, 0));

        GlowButton btnStart = new GlowButton("MULAI");
        btnStart.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStart.setMaximumSize(new Dimension(280, 54));

        btnStart.addActionListener(e -> {
            RoundedTextField field1 = (RoundedTextField) getClientProperty("field1");
            RoundedTextField field2 = (RoundedTextField) getClientProperty("field2");

            String name1 = field1.getText().trim();
            String name2 = field2.getText().trim();
            if (name1.isEmpty()) name1 = "Pemain 1";
            if (name2.isEmpty()) name2 = "Pemain 2";

            MainFrame parent = (MainFrame) SwingUtilities.getWindowAncestor(this);

            GameState.reset();

            Player p1 = new Player(name1);
            Player p2 = new Player(name2);

            GameState.allPlayers.add(p1);
            GameState.allPlayers.add(p2);
            GameState.turnQueue.add(p1);
            GameState.turnQueue.add(p2);

            parent.startGame();
        });

        panel.add(btnStart);
        return panel;
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Kolom nama dengan glow saat fokus
    // =========================================================
    private static class RoundedTextField extends JTextField {
        private boolean focused = false;

        RoundedTextField(String defaultText) {
            super(defaultText);
            setFont(new Font("Arial", Font.BOLD, 16));
            setForeground(new Color(30, 30, 30));
            setCaretColor(new Color(30, 30, 30));
            setSelectionColor(new Color(0, 0, 0, 40));
            setHorizontalAlignment(JTextField.CENTER);
            setOpaque(false);
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setMaximumSize(new Dimension(300, 46));
            setPreferredSize(new Dimension(300, 46));
            setAlignmentX(Component.CENTER_ALIGNMENT);

            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    selectAll();
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Glow lembut saat fokus (beberapa lapis rounded rect dengan alpha menurun)
            if (focused) {
                for (int i = 3; i >= 1; i--) {
                    g2.setColor(new Color(255, 255, 255, 28 / i));
                    g2.fillRoundRect(-i * 2, -i * 2, w + i * 4, h + i * 4, 16 + i * 2, 16 + i * 2);
                }
            }

            g2.setColor(new Color(240, 240, 240, focused ? 235 : 190));
            g2.fillRoundRect(0, 0, w, h, 14, 14);

            g2.setColor(focused ? new Color(20, 20, 20) : new Color(180, 180, 180));
            g2.setStroke(new BasicStroke(focused ? 2f : 1f));
            g2.drawRoundRect(1, 1, w - 2, h - 2, 14, 14);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Badge "VS" berdenyut
    // =========================================================
    private static class VsBadge extends JComponent {
        private float pulse = 0f;
        private final Timer pulseTimer;

        VsBadge() {
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setPreferredSize(new Dimension(56, 56));
            setMaximumSize(new Dimension(56, 56));
            setOpaque(false);

            pulseTimer = new Timer(30, e -> {
                pulse += 0.06f;
                repaint();
            });
            pulseTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2;
            int baseRadius = Math.min(w, h) / 2 - 6;

            float glowT = (float) (0.5 + 0.5 * Math.sin(pulse));
            int glowRadius = baseRadius + (int) (glowT * 5);

            g2.setColor(new Color(255, 255, 255, (int) (30 + glowT * 40)));
            g2.fillOval(cx - glowRadius - 4, cy - glowRadius - 4, (glowRadius + 4) * 2, (glowRadius + 4) * 2);

            g2.setColor(new Color(15, 15, 15));
            g2.fillOval(cx - baseRadius, cy - baseRadius, baseRadius * 2, baseRadius * 2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics fm = g2.getFontMetrics();
            String text = "VS";
            g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2 - 2);

            g2.dispose();
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Tombol MULAI dengan hover + glow pulsing
    // =========================================================
    private static class GlowButton extends JButton {
        private boolean hover = false;
        private float pulse = 0f;
        private final Timer pulseTimer;

        GlowButton(String text) {
            super(text);
            setFont(new Font("Arial", Font.BOLD, 19));
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

            pulseTimer = new Timer(30, e -> {
                pulse += 0.05f;
                repaint();
            });
            pulseTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            float glowT = (float) (0.5 + 0.5 * Math.sin(pulse));
            g2.setColor(new Color(255, 255, 255, (int) (18 + glowT * 22)));
            g2.fillRoundRect(-4, -4, w + 8, h + 8, 16, 16);

            g2.setColor(hover ? new Color(15, 15, 15) : new Color(30, 30, 30, 235));
            g2.fillRoundRect(0, 0, w, h, 12, 12);

            g2.dispose();

            setForeground(Color.WHITE);
            super.paintComponent(g);
        }
    }
}