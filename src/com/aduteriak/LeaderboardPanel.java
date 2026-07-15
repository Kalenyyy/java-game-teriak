package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

public class LeaderboardPanel extends JPanel {

    private float entranceAlpha = 1f; // 1 = gelap penuh, 0 = transparan (reveal selesai)
    private Timer entranceTimer;
    private JPanel listWrapper;

    public LeaderboardPanel(MainFrame parent) {
        setLayout(new BorderLayout());
        setOpaque(true);

        add(buildHeaderPanel(parent), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildFooterPanel(parent), BorderLayout.SOUTH);

        // Jika panel ini ditampilkan kembali (mis. setelah babak baru selesai), tarik data terbaru
        this.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    refreshData();
                }
            }
        });

        startEntranceAnimation();
        refreshData();
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

        // Tiga siluet stickman tipis di podium, mewakili juara 1-2-3
        drawStickman(g2, (int) (w * 0.5f), h / 2, Math.min(w, h) * 0.5f);
        drawStickman(g2, (int) (w * 0.22f), h / 2, Math.min(w, h) * 0.34f);
        drawStickman(g2, (int) (w * 0.78f), h / 2, Math.min(w, h) * 0.34f);

        g2.dispose();

        if (entranceAlpha > 0f) {
            Graphics2D gf = (Graphics2D) g.create();
            gf.setColor(new Color(0, 0, 0, (int) (entranceAlpha * 255)));
            gf.fillRect(0, 0, w, h);
            gf.dispose();
        }
    }

    private void drawStickman(Graphics2D g2, int cx, int cy, float scale) {
        g2.setColor(new Color(255, 255, 255, 18));
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

        JLabel sub = new JLabel("HALL OF FAME");
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setFont(new Font("Arial", Font.PLAIN, 14));
        sub.setForeground(new Color(90, 90, 90));

        JLabel title = new JLabel("PAPAN PERINGKAT");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(pickTitleFont(36));
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
    //  CENTER: Daftar Peringkat (kartu baris rounded, bisa discroll)
    // =========================================================
    private JScrollPane buildCenterPanel() {
        listWrapper = new JPanel();
        listWrapper.setOpaque(false);
        listWrapper.setLayout(new BoxLayout(listWrapper, BoxLayout.Y_AXIS));
        listWrapper.setBorder(new EmptyBorder(10, 40, 10, 40));

        JScrollPane scroll = new JScrollPane(listWrapper);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    public void refreshData() {
        listWrapper.removeAll();

        List<Player> scores = ScoreManager.getTopScores();

        if (scores.isEmpty()) {
            JLabel empty = new JLabel("BELUM ADA DATA SKOR");
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setForeground(new Color(70, 70, 70));
            empty.setFont(new Font("Arial", Font.ITALIC, 18));
            empty.setBorder(new EmptyBorder(50, 0, 0, 0));
            listWrapper.add(empty);
        } else {
            for (int i = 0; i < scores.size(); i++) {
                Player p = scores.get(i);
                LeaderboardRow row = new LeaderboardRow(i + 1, p.getName(), (int) p.getScore());
                row.setAlignmentX(Component.CENTER_ALIGNMENT);
                listWrapper.add(row);
                listWrapper.add(Box.createRigidArea(new Dimension(0, 12)));
            }
        }

        listWrapper.revalidate();
        listWrapper.repaint();
    }

    // =========================================================
    //  FOOTER: Tombol KEMBALI KE MENU
    // =========================================================
    private JPanel buildFooterPanel(MainFrame parent) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 0, 34, 0));

        GlowButton btnBack = new GlowButton("KEMBALI KE MENU");
        btnBack.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnBack.setMaximumSize(new Dimension(280, 54));
        btnBack.addActionListener(e -> parent.showView("MENU_UTAMA"));

        panel.add(btnBack);
        return panel;
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Kartu Baris Peringkat (nomor, nama, skor)
    // =========================================================
    private static class LeaderboardRow extends JPanel {
        private final int rank;

        LeaderboardRow(int rank, String name, int score) {
            this.rank = rank;
            setOpaque(false);
            setLayout(new BorderLayout(14, 0));
            setMaximumSize(new Dimension(560, 60));
            setPreferredSize(new Dimension(560, 60));
            setBorder(new EmptyBorder(8, 18, 8, 22));

            JLabel badge = buildRankBadge(rank);

            JLabel nameLbl = new JLabel(name.toUpperCase());
            nameLbl.setFont(new Font("Arial", Font.BOLD, 18));
            nameLbl.setForeground(new Color(25, 25, 25));

            JLabel scoreLbl = new JLabel(score + " PTS");
            scoreLbl.setFont(new Font("Arial", Font.BOLD, 18));
            scoreLbl.setForeground(rankColor(rank).darker());
            scoreLbl.setHorizontalAlignment(SwingConstants.RIGHT);

            add(badge, BorderLayout.WEST);
            add(nameLbl, BorderLayout.CENTER);
            add(scoreLbl, BorderLayout.EAST);
        }

        private JLabel buildRankBadge(int rank) {
            String text = rank <= 3 ? medalEmoji(rank) : String.valueOf(rank);
            JLabel badge = new JLabel(text, SwingConstants.CENTER);
            badge.setPreferredSize(new Dimension(36, 36));
            badge.setFont(new Font("Arial", Font.BOLD, rank <= 3 ? 18 : 16));
            badge.setForeground(rank <= 3 ? Color.WHITE : new Color(60, 60, 60));
            badge.setOpaque(false);
            return badge;
        }

        private String medalEmoji(int rank) {
            switch (rank) {
                case 1: return "\uD83E\uDD47"; // 🥇
                case 2: return "\uD83E\uDD48"; // 🥈
                default: return "\uD83E\uDD49"; // 🥉
            }
        }

        private Color rankColor(int rank) {
            switch (rank) {
                case 1: return new Color(255, 205, 60);  // emas
                case 2: return new Color(200, 200, 205); // perak
                case 3: return new Color(205, 140, 90);  // perunggu
                default: return new Color(210, 210, 210);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            boolean top3 = rank <= 3;

            // Glow tipis untuk 3 besar
            if (top3) {
                Color glow = rankColor(rank);
                g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 45));
                g2.fillRoundRect(-3, -3, w + 6, h + 6, 20, 20);
            }

            g2.setColor(new Color(240, 240, 240, top3 ? 235 : 190));
            g2.fillRoundRect(0, 0, w, h, 16, 16);

            g2.setColor(top3 ? rankColor(rank) : new Color(190, 190, 190));
            g2.setStroke(new BasicStroke(top3 ? 2f : 1f));
            g2.drawRoundRect(1, 1, w - 2, h - 2, 16, 16);

            // Lingkaran badge peringkat
            int badgeSize = 36;
            int bx = 18;
            int by = (h - badgeSize) / 2;
            g2.setColor(top3 ? rankColor(rank) : new Color(220, 220, 220));
            g2.fillOval(bx, by, badgeSize, badgeSize);
            if (!top3) {
                g2.setColor(new Color(160, 160, 160));
                g2.drawOval(bx, by, badgeSize, badgeSize);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Tombol dengan hover + glow pulsing
    // =========================================================
    private static class GlowButton extends JButton {
        private boolean hover = false;
        private float pulse = 0f;
        private final Timer pulseTimer;

        GlowButton(String text) {
            super(text);
            setFont(new Font("Arial", Font.BOLD, 17));
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