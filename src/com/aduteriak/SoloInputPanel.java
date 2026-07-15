package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

public class SoloInputPanel extends JPanel {

    private float entranceAlpha = 1f; // 1 = gelap penuh, 0 = transparan (reveal selesai)
    private Timer entranceTimer;

    public SoloInputPanel(MainFrame parent) {
        setLayout(new BorderLayout());
        setOpaque(true);

        add(buildHeaderPanel(parent), BorderLayout.NORTH);
        add(buildCenterPanel(parent), BorderLayout.CENTER);
        add(buildFooterPanel(parent), BorderLayout.SOUTH);

        startEntranceAnimation();
    }

    // =========================================================
    //  LATAR BELAKANG: Gradient + Siluet Stickman Tunggal (senada Main Menu)
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

        // Satu siluet stickman tipis di tengah, mewakili Pemain Solo
        drawStickman(g2, w / 2, h / 2, Math.min(w, h) * 0.5f);

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
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        MinimalBackButton btnBack = new MinimalBackButton("KEMBALI", () -> parent.showView("MENU_UTAMA"));
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(btnBack, BorderLayout.WEST);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleBox.setBorder(new EmptyBorder(14, 10, 6, 10));

        JLabel sub = new JLabel("PERSIAPAN MODE SOLO");
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setFont(new Font("Arial", Font.PLAIN, 14));
        sub.setForeground(new Color(90, 90, 90));

        JLabel title = new JLabel("MASUKKAN NAMAMU");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(pickTitleFont(38));
        title.setForeground(new Color(15, 15, 15));

        titleBox.add(sub);
        titleBox.add(title);

        wrapper.add(topRow);
        wrapper.add(titleBox);
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
    //  CENTER: Form Nama Pemain (satu kolom, tanpa badge VS)
    // =========================================================
    private JPanel buildCenterPanel(MainFrame parent) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);

        JPanel formBox = new JPanel();
        formBox.setOpaque(false);
        formBox.setLayout(new BoxLayout(formBox, BoxLayout.Y_AXIS));

        JLabel lbl = buildFieldLabel("NAMA PEMAIN");
        RoundedTextField field = new RoundedTextField("Pemain");

        formBox.add(lbl);
        formBox.add(Box.createRigidArea(new Dimension(0, 6)));
        formBox.add(field);

        // Simpan referensi field lewat client property untuk dipakai tombol MULAI di footer
        this.putClientProperty("field", field);

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
    //  FOOTER: Tombol MULAI TERIAK
    // =========================================================
    private JPanel buildFooterPanel(MainFrame parent) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 0, 34, 0));

        GlowButton btnStart = new GlowButton("MULAI TERIAK");
        btnStart.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStart.setMaximumSize(new Dimension(280, 54));

        btnStart.addActionListener(e -> {
            RoundedTextField field = (RoundedTextField) getClientProperty("field");
            String name = field.getText().trim();
            if (name.isEmpty() || name.equals("Pemain")) {
                name = "Pemain";
            }

            GameState.reset();
            GameState.isSoloMode = true;
            Player p = new Player(name);
            GameState.allPlayers.add(p);
            GameState.turnQueue.add(p);

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

    // =========================================================
    //  KOMPONEN KUSTOM: Tombol kembali "← KEMBALI" gaya kartu gelap (SAMA PERSIS di semua halaman)
    // =========================================================
    private static class MinimalBackButton extends JComponent {
        private static final String ARROW = "\u2190";
        private final String label;
        private final Runnable action;
        private float hoverT = 0f;
        private float pressT = 0f;
        private boolean hovering = false;
        private boolean pressed = false;
        private final Timer animTimer;

        MinimalBackButton(String label, Runnable action) {
            this.label = label;
            this.action = action;
            setOpaque(false);
            setFont(new Font("Arial", Font.BOLD, 13));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(16, 16, 8, 8));
            setPreferredSize(new Dimension(164, 58));
            setMaximumSize(new Dimension(174, 58));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovering = false;
                    pressed = false;
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = true;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    boolean wasPressed = pressed;
                    pressed = false;
                    if (wasPressed && contains(e.getPoint())) {
                        action.run();
                    }
                }
            });

            animTimer = new Timer(16, e -> {
                float hoverTarget = hovering ? 1f : 0f;
                float pressTarget = pressed ? 1f : 0f;
                hoverT += (hoverTarget - hoverT) * 0.2f;
                pressT += (pressTarget - pressT) * 0.35f;
                repaint();
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Insets in = getInsets();
            int cardX = in.left;
            int cardY = in.top;
            int cardW = getWidth() - in.left - in.right;
            int cardH = getHeight() - in.top - in.bottom;

            float scale = 1f - pressT * 0.035f;
            double cx = cardX + cardW / 2.0;
            double cy = cardY + cardH / 2.0;
            g2.translate(cx, cy);
            g2.scale(scale, scale);
            g2.translate(-cx, -cy);

            int arc = Math.min(16, cardH / 2);
            RoundRectangle2D shape = new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, arc, arc);

            // Bayangan tipis di bawah kartu (depth)
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(new RoundRectangle2D.Float(cardX + 1, cardY + 3, cardW, cardH, arc, arc));

            // Isi kartu GELAP SOLID -- selalu sama warnanya di background apa pun
            g2.setColor(new Color(22, 22, 24, (int) (215 + 20 * hoverT)));
            g2.fill(shape);

            // Border terang SELALU terlihat, makin cerah saat hover
            g2.setColor(new Color(255, 255, 255, (int) (90 + 90 * hoverT)));
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(shape);

            // Highlight tipis di tepi atas (kesan glass, senada PremiumButton)
            g2.setColor(new Color(255, 255, 255, 45));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(cardX + 6, cardY + 2, cardX + cardW - 6, cardY + 2);

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String full = ARROW + " " + label;
            int tx = cardX + (cardW - fm.stringWidth(full)) / 2;
            int ty = cardY + (cardH + fm.getAscent()) / 2 - 3;

            g2.setColor(new Color(0, 0, 0, 150));
            g2.drawString(full, tx + 1, ty + 1);
            g2.setColor(new Color(235, 235, 235));
            g2.drawString(full, tx, ty);

            g2.dispose();
        }
    }
}