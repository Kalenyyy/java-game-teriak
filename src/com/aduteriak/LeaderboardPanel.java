package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.List;

/**
 * LeaderboardPanel.java
 *
 * Direstyle agar satu bahasa visual dengan InputTournamentPanel &
 * TournamentBracketPanel: background prosedural cyan (gradient + grid HUD,
 * TANPA aset gambar/siluet stickman lama), bracket sudut viewfinder,
 * scanline yang menyapu, kartu baris peringkat kaca cyan dengan glow untuk
 * 3 besar, serta tombol kembali & CTA bergaya glass cyan yang identik.
 *
 * Logika inti (refreshData, ScoreManager, urutan peringkat, auto-refresh
 * saat panel ditampilkan kembali) TIDAK DIUBAH — murni perubahan tampilan.
 */
public class LeaderboardPanel extends JPanel {

    // =========================================================
    //  PALET WARNA — sama persis dengan panel turnamen lain
    // =========================================================
    private static final Color C_WHITE       = new Color(240, 248, 252);
    private static final Color C_SOFT_BLUE   = new Color(176, 208, 226);
    private static final Color C_MUTED_BLUE  = new Color(120, 148, 168);
    private static final Color C_CYAN        = new Color(140, 226, 255);
    private static final Color C_CYAN_BRIGHT = new Color(200, 244, 255);
    private static final Color C_CYAN_DIM    = new Color(90, 160, 190);

    private static final Color GLASS_FILL_TOP    = new Color(10, 24, 34, 150);
    private static final Color GLASS_FILL_BOTTOM = new Color(3, 9, 14, 205);

    // Aksen warna medali (satu-satunya "warna lain" selain cyan, khusus 3 besar)
    private static final Color GOLD   = new Color(255, 209, 102);
    private static final Color SILVER = new Color(206, 214, 224);
    private static final Color BRONZE = new Color(214, 150, 96);

    private float entranceAlpha = 1f;
    private Timer entranceTimer;
    private JPanel listWrapper;

    // ---- Animasi latar HUD ----
    private Timer masterTimer;
    private float glowBreathPhase = 0f;
    private float scanPhase = -0.2f;

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

        masterTimer = new Timer(16, e -> {
            glowBreathPhase += 0.02f;
            scanPhase += 0.0035f;
            if (scanPhase > 1.2f) scanPhase = -0.2f;
            repaint();
        });
        masterTimer.start();
    }

    // =========================================================
    //  LATAR BELAKANG — prosedural cyan (gradient + grid HUD + scanline)
    // =========================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g2.setPaint(new GradientPaint(0, 0, new Color(14, 22, 30), 0, h, new Color(4, 7, 10)));
        g2.fillRect(0, 0, w, h);

        RadialGradientPaint spotlight = new RadialGradientPaint(
                new Point2D.Float(w / 2f, h * 0.12f), w * 0.65f,
                new float[]{0f, 1f},
                new Color[]{new Color(140, 226, 255, 22), new Color(140, 226, 255, 0)}
        );
        g2.setPaint(spotlight);
        g2.fillRect(0, 0, w, h);

        paintHudDotGrid(g2, w, h);
        paintHudScanline(g2, w, h);
        paintCornerBrackets(g2, w, h);

        g2.dispose();

        if (entranceAlpha > 0f) {
            Graphics2D gf = (Graphics2D) g.create();
            gf.setColor(new Color(0, 0, 0, (int) (entranceAlpha * 255)));
            gf.fillRect(0, 0, w, h);
            gf.dispose();
        }
    }

    /** Dot-grid HUD sangat samar agar latar tidak terasa polos/kosong tanpa jadi ramai. */
    private void paintHudDotGrid(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(160, 230, 255, 10));
        int step = 34;
        for (int x = step; x < w; x += step) {
            for (int y = step; y < h; y += step) {
                g2.fillOval(x, y, 1, 1);
            }
        }
    }

    /** Scanline HUD tipis yang menyapu turun terus-menerus, senada panel turnamen lain. */
    private void paintHudScanline(Graphics2D g2, int w, int h) {
        float y = h * scanPhase;
        float bandH = Math.max(14f, h * 0.05f);
        g2.setPaint(new GradientPaint(0, y - bandH / 2f, new Color(160, 230, 255, 0), 0, y, new Color(160, 230, 255, 20)));
        g2.fillRect(0, (int) (y - bandH / 2f), w, (int) (bandH / 2f));
        g2.setPaint(new GradientPaint(0, y, new Color(160, 230, 255, 20), 0, y + bandH / 2f, new Color(160, 230, 255, 0)));
        g2.fillRect(0, (int) y, w, (int) (bandH / 2f));
    }

    /** Bracket sudut viewfinder di keempat pojok layar, kesan terminal HUD. */
    private void paintCornerBrackets(Graphics2D g2, int w, int h) {
        float breathe = 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(glowBreathPhase));
        int len = 22, pad = 16;
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(160, 230, 255, (int) (110 + 80 * breathe)));

        g2.drawLine(pad, pad, pad + len, pad);
        g2.drawLine(pad, pad, pad, pad + len);
        g2.drawLine(w - pad, pad, w - pad - len, pad);
        g2.drawLine(w - pad, pad, w - pad, pad + len);
        g2.drawLine(pad, h - pad, pad + len, h - pad);
        g2.drawLine(pad, h - pad, pad, h - pad - len);
        g2.drawLine(w - pad, h - pad, w - pad - len, h - pad);
        g2.drawLine(w - pad, h - pad, w - pad, h - pad - len);
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

    private static int clampAlpha(float a) {
        return Math.max(0, Math.min(255, (int) (a * 255)));
    }

    private static Font pickTitleFont(int size) {
        String[] candidates = {"Impact", "Arial Black", "Haettenschweiler", "Arial"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        List<String> availableList = Arrays.asList(available);
        for (String name : candidates) {
            if (availableList.contains(name)) {
                return new Font(name, name.equals("Arial") ? Font.BOLD : Font.PLAIN, size);
            }
        }
        return new Font("SansSerif", Font.BOLD, size);
    }

    private static Font pickTechFont(int size, int style) {
        String[] candidates = {"Orbitron", "Rajdhani", "Exo 2", "Eurostile", "Consolas", "Segoe UI Semibold", "Arial"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        List<String> availableList = Arrays.asList(available);
        for (String name : candidates) {
            if (availableList.contains(name)) return new Font(name, style, size);
        }
        return new Font(Font.MONOSPACED, style, size);
    }

    // =========================================================
    //  HEADER: Tombol Kembali (glass cyan) + Judul + Subjudul + Divider HUD
    // =========================================================
    private JPanel buildHeaderPanel(MainFrame parent) {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(new EmptyBorder(26, 34, 6, 34));

        MinimalBackButton btnBack = new MinimalBackButton("KEMBALI", () -> parent.showView("MENU_UTAMA"));
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(btnBack, BorderLayout.WEST);
        wrapper.add(topRow);
        wrapper.add(Box.createRigidArea(new Dimension(0, 4)));

        MonoLabel title = new MonoLabel("PAPAN PERINGKAT", C_WHITE, C_CYAN, true, 3f);
        title.setFont(pickTitleFont(30));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setPreferredSize(new Dimension(10, 40));
        wrapper.add(title);

        MonoLabel sub = new MonoLabel("HALL OF FAME", C_CYAN_DIM, C_CYAN_DIM, false, 2.6f);
        sub.setFont(pickTechFont(11, Font.PLAIN));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        wrapper.add(Box.createRigidArea(new Dimension(0, 4)));
        wrapper.add(sub);

        wrapper.add(Box.createRigidArea(new Dimension(0, 10)));
        HudDivider divider = new HudDivider();
        divider.setAlignmentX(Component.CENTER_ALIGNMENT);
        divider.setMaximumSize(new Dimension(4000, 18));
        wrapper.add(divider);

        return wrapper;
    }

    /** Garis dekorasi HUD tipis dengan node kecil di tengah, aksen cyan (identik panel lain). */
    private static class HudDivider extends JComponent {
        HudDivider() {
            setOpaque(false);
            setPreferredSize(new Dimension(10, 14));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int cy = getHeight() / 2;

            g2.setPaint(new GradientPaint(0, 0, new Color(140, 226, 255, 0), w / 2f, 0, new Color(140, 226, 255, 130)));
            g2.drawLine(0, cy, w / 2 - 8, cy);
            g2.setPaint(new GradientPaint(w / 2f, 0, new Color(140, 226, 255, 130), w, 0, new Color(140, 226, 255, 0)));
            g2.drawLine(w / 2 + 8, cy, w, cy);

            g2.setColor(C_CYAN);
            g2.fillOval(w / 2 - 3, cy - 3, 6, 6);
            g2.setColor(new Color(140, 226, 255, 70));
            g2.fillOval(w / 2 - 6, cy - 6, 12, 12);
            g2.dispose();
        }
    }

    /** Label dengan letter-spacing manual + auto-fit ukuran font agar teks tak pernah terpotong. */
    private static class MonoLabel extends JLabel {
        private final Color glowColor;
        private final boolean brackets;
        private final float letterSpacing;

        MonoLabel(String text, Color fg, Color glowColor, boolean brackets, float letterSpacing) {
            super(text);
            this.glowColor = glowColor;
            this.brackets = brackets;
            this.letterSpacing = letterSpacing;
            setForeground(fg);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setPreferredSize(new Dimension(10, 22));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            String text = getText();
            String left = brackets ? "[  " : "";
            String right = brackets ? "  ]" : "";

            Font fitFont = getFont();
            FontMetrics fm = g2.getFontMetrics(fitFont);
            float spacing = letterSpacing;
            float availableW = getWidth() - 6f;
            float bracketW = brackets ? fm.stringWidth(left) + fm.stringWidth(right) : 0f;
            float totalW = trackedWidth(fm, text, spacing) + bracketW;

            int minSize = 8;
            while (totalW > availableW && fitFont.getSize() > minSize) {
                fitFont = fitFont.deriveFont((float) (fitFont.getSize() - 1));
                fm = g2.getFontMetrics(fitFont);
                spacing = letterSpacing * (fitFont.getSize() / (float) getFont().getSize());
                bracketW = brackets ? fm.stringWidth(left) + fm.stringWidth(right) : 0f;
                totalW = trackedWidth(fm, text, spacing) + bracketW;
            }
            g2.setFont(fitFont);

            boolean centered = getHorizontalAlignment() == SwingConstants.CENTER;
            float x = centered ? Math.max(2f, (getWidth() - totalW) / 2f) : 0f;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

            if (brackets) {
                g2.setColor(glowColor);
                g2.drawString(left, x, y);
                x += fm.stringWidth(left);
            }
            g2.setColor(getForeground());
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                g2.drawString(ch, x, y);
                x += fm.stringWidth(ch) + spacing;
            }
            if (brackets) {
                g2.setColor(glowColor);
                g2.drawString(right, x - spacing, y);
            }
            g2.dispose();
        }

        private static float trackedWidth(FontMetrics fm, String text, float spacing) {
            float w = 0f;
            for (int i = 0; i < text.length(); i++) w += fm.stringWidth(String.valueOf(text.charAt(i))) + spacing;
            return Math.max(0f, w - spacing);
        }
    }

    // =========================================================
    //  CENTER: Daftar Peringkat (kartu kaca cyan, bisa discroll)
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
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUI(new ThinFadeScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        return scroll;
    }

    public void refreshData() {
        listWrapper.removeAll();

        List<Player> scores = ScoreManager.getTopScores();

        if (scores.isEmpty()) {
            MonoLabel empty = new MonoLabel("BELUM ADA DATA SKOR", C_MUTED_BLUE, C_MUTED_BLUE, false, 2f);
            empty.setFont(pickTechFont(15, Font.PLAIN));
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setPreferredSize(new Dimension(10, 26));
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
    //  FOOTER: Tombol KEMBALI KE MENU (CTA glass cyan)
    // =========================================================
    private JPanel buildFooterPanel(MainFrame parent) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(14, 0, 30, 0));

        HudDivider divider = new HudDivider();
        divider.setAlignmentX(Component.CENTER_ALIGNMENT);
        divider.setMaximumSize(new Dimension(4000, 18));
        panel.add(divider);
        panel.add(Box.createRigidArea(new Dimension(0, 14)));

        GlowButton btnBack = new GlowButton("\u2190  KEMBALI KE MENU");
        btnBack.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnBack.setMaximumSize(new Dimension(300, 56));
        btnBack.setPreferredSize(new Dimension(300, 56));
        btnBack.addActionListener(e -> parent.showView("MENU_UTAMA"));

        panel.add(btnBack);
        return panel;
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Kartu Baris Peringkat — glass cyan, glow untuk 3 besar
    // =========================================================
    private static class LeaderboardRow extends JPanel {
        private final int rank;
        private float breathePhase;
        private final Timer animTimer;

        LeaderboardRow(int rank, String name, int score) {
            this.rank = rank;
            this.breathePhase = (float) (Math.random() * 6.28);
            setOpaque(false);
            setLayout(new BorderLayout(14, 0));
            setMaximumSize(new Dimension(560, 60));
            setPreferredSize(new Dimension(560, 60));
            setBorder(new EmptyBorder(8, 20, 8, 24));

            JLabel badge = buildRankBadge(rank);

            JLabel nameLbl = new JLabel(name.toUpperCase());
            nameLbl.setFont(pickTechFont(16, Font.BOLD));
            nameLbl.setForeground(rank <= 3 ? C_WHITE : C_SOFT_BLUE);

            JLabel scoreLbl = new JLabel(score + " PTS");
            scoreLbl.setFont(pickTechFont(16, Font.BOLD));
            scoreLbl.setForeground(rank <= 3 ? rankAccent(rank) : C_CYAN_BRIGHT);
            scoreLbl.setHorizontalAlignment(SwingConstants.RIGHT);

            add(badge, BorderLayout.WEST);
            add(nameLbl, BorderLayout.CENTER);
            add(scoreLbl, BorderLayout.EAST);

            animTimer = rank <= 3 ? new Timer(16, e -> {
                breathePhase += 0.025f;
                repaint();
            }) : null;
            if (animTimer != null) animTimer.start();
        }

        private JLabel buildRankBadge(int rank) {
            String text = rank <= 3 ? medalEmoji(rank) : String.valueOf(rank);
            JLabel badge = new JLabel(text, SwingConstants.CENTER);
            badge.setPreferredSize(new Dimension(40, 40));
            badge.setFont(pickTechFont(rank <= 3 ? 18 : 15, Font.BOLD));
            badge.setForeground(rank <= 3 ? new Color(6, 16, 22) : C_CYAN_BRIGHT);
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

        /** Aksen medali (satu-satunya warna selain cyan), khusus 3 besar. */
        private static Color rankAccent(int rank) {
            switch (rank) {
                case 1: return GOLD;
                case 2: return SILVER;
                case 3: return BRONZE;
                default: return C_CYAN_BRIGHT;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            boolean top3 = rank <= 3;
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);

            float breathe = 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(breathePhase));

            // Glow ber-nafas untuk 3 besar, memakai aksen warna medalinya masing-masing
            if (top3) {
                Color glow = rankAccent(rank);
                g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), (int) (55 * breathe)));
                g2.fill(new RoundRectangle2D.Float(-3, -3, w + 6, h + 6, 18, 18));
            }

            g2.setColor(new Color(0, 0, 0, 70));
            g2.fill(new RoundRectangle2D.Float(1, 3, w, h, 14, 14));

            g2.setPaint(new GradientPaint(0, 0, GLASS_FILL_TOP, 0, h, GLASS_FILL_BOTTOM));
            g2.fill(shape);

            if (top3) {
                Color accent = rankAccent(rank);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clampAlpha(0.55f + 0.35f * breathe)));
                g2.setStroke(new BasicStroke(1.6f));
            } else {
                g2.setColor(new Color(160, 230, 255, 90));
                g2.setStroke(new BasicStroke(1f));
            }
            g2.draw(shape);

            // Lingkaran badge peringkat
            int badgeSize = 40;
            int bx = 14;
            int by = (h - badgeSize) / 2;
            if (top3) {
                Color accent = rankAccent(rank);
                g2.setPaint(new RadialGradientPaint(new Point2D.Float(bx + badgeSize / 2f, by + badgeSize / 2.6f), badgeSize,
                        new float[]{0f, 1f}, new Color[]{brighten(accent), accent}));
            } else {
                g2.setColor(new Color(140, 226, 255, 22));
            }
            g2.fillOval(bx, by, badgeSize, badgeSize);
            g2.setColor(top3 ? new Color(255, 255, 255, 160) : new Color(160, 230, 255, 130));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(bx, by, badgeSize, badgeSize);

            g2.dispose();
            super.paintComponent(g);
        }

        private static Color brighten(Color c) {
            return new Color(
                    Math.min(255, c.getRed() + 40),
                    Math.min(255, c.getGreen() + 40),
                    Math.min(255, c.getBlue() + 40)
            );
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Tombol CTA — glass + sweep + breathing glow cyan
    //  (identik gaya StartButton pada InputTournamentPanel).
    // =========================================================
    private static class GlowButton extends JButton {
        private boolean hover = false;
        private float pulsePhase = 0f;
        private float hoverAnim = 0f;
        private long streakStart = -1L;
        private final Timer fxTimer;

        GlowButton(String text) {
            super(text);
            setFont(pickTechFont(15, Font.BOLD));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setForeground(new Color(4, 14, 18));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    streakStart = System.currentTimeMillis();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                }
            });

            fxTimer = new Timer(16, e -> {
                pulsePhase += 0.045f;
                hoverAnim += ((hover ? 1f : 0f) - hoverAnim) * 0.18f;
                repaint();
            });
            fxTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float scale = 1f + hoverAnim * 0.02f;
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale, scale);
            g2.translate(-w / 2.0, -h / 2.0);

            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 12, 12);
            float breathe = 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(pulsePhase));
            int glowAlpha = (int) (70 * breathe + 80 * hoverAnim);
            g2.setColor(new Color(140, 226, 255, glowAlpha));
            g2.fill(new RoundRectangle2D.Float(-5, -5, w + 10, h + 10, 16, 16));

            g2.setPaint(hover
                    ? new GradientPaint(0, 0, new Color(200, 244, 255), 0, h, new Color(120, 210, 235))
                    : new GradientPaint(0, 0, new Color(170, 232, 255), 0, h, new Color(90, 185, 215)));
            g2.fill(shape);

            if (hover && streakStart >= 0) {
                long elapsed = System.currentTimeMillis() - streakStart;
                float t = Math.min(1f, elapsed / 260f);
                Shape oldClip = g2.getClip();
                g2.clip(shape);
                float sx = -h + t * (w + h * 2f);
                int alpha = (int) (110 * (1f - t));
                Polygon streak = new Polygon();
                streak.addPoint((int) sx, -5);
                streak.addPoint((int) (sx + h * 0.5f), -5);
                streak.addPoint((int) (sx + h * 0.5f - h), h + 5);
                streak.addPoint((int) (sx - h), h + 5);
                g2.setColor(new Color(255, 255, 255, Math.max(0, alpha)));
                g2.fill(streak);
                g2.setClip(oldClip);
            }

            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255, 255, 255, (int) (140 + 60 * hoverAnim)));
            g2.draw(shape);
            g2.dispose();

            setForeground(new Color(4, 14, 18));
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Tombol kembali — glass cyan, glow bernafas,
    //  light sweep saat hover (identik panel turnamen lain).
    // =========================================================
    private static class MinimalBackButton extends JComponent {
        private static final String ARROW = "\u2190";
        private final String label;
        private final Runnable action;
        private float hoverT = 0f;
        private float pressT = 0f;
        private float breathePhase = 0f;
        private boolean hovering = false;
        private boolean pressed = false;
        private long streakStart = -1L;
        private final Timer animTimer;

        MinimalBackButton(String label, Runnable action) {
            this.label = label;
            this.action = action;
            setOpaque(false);
            setFont(pickTechFont(13, Font.BOLD));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(150, 46));
            setMaximumSize(new Dimension(160, 46));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                    streakStart = System.currentTimeMillis();
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
                    if (wasPressed && contains(e.getPoint())) action.run();
                }
            });

            animTimer = new Timer(16, e -> {
                hoverT += ((hovering ? 1f : 0f) - hoverT) * 0.2f;
                pressT += ((pressed ? 1f : 0f) - pressT) * 0.35f;
                breathePhase += 0.03f;
                repaint();
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            float scale = 1f - pressT * 0.035f;
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale, scale);
            g2.translate(-w / 2.0, -h / 2.0);

            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
            float breathe = 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(breathePhase));

            g2.setColor(new Color(140, 226, 255, (int) (30 * breathe + 55 * hoverT)));
            g2.fill(new RoundRectangle2D.Float(-3, -3, w + 6, h + 6, 18, 18));

            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(new RoundRectangle2D.Float(1, 3, w, h, 14, 14));

            g2.setPaint(new GradientPaint(0, 0, GLASS_FILL_TOP, 0, h, GLASS_FILL_BOTTOM));
            g2.fill(shape);

            if (hovering && streakStart >= 0) {
                long elapsed = System.currentTimeMillis() - streakStart;
                float t = Math.min(1f, elapsed / 260f);
                Shape oldClip = g2.getClip();
                g2.clip(shape);
                float sx = -h + t * (w + h * 2f);
                int alpha = (int) (90 * (1f - t));
                Polygon streak = new Polygon();
                streak.addPoint((int) sx, -5);
                streak.addPoint((int) (sx + h * 0.5f), -5);
                streak.addPoint((int) (sx + h * 0.5f - h), h + 5);
                streak.addPoint((int) (sx - h), h + 5);
                g2.setColor(new Color(200, 240, 255, Math.max(0, alpha)));
                g2.fill(streak);
                g2.setClip(oldClip);
            }

            g2.setColor(new Color(160, 230, 255, (int) (90 + 110 * hoverT)));
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(shape);

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String full = ARROW + " " + label;
            int tx = (w - fm.stringWidth(full)) / 2;
            int ty = (h + fm.getAscent()) / 2 - 3;
            g2.setColor(new Color(0, 0, 0, 150));
            g2.drawString(full, tx + 1, ty + 1);
            g2.setColor(hovering ? C_CYAN_BRIGHT : C_WHITE);
            g2.drawString(full, tx, ty);

            g2.dispose();
        }
    }

    // =========================================================
    //  SCROLLBAR CUSTOM: tipis, rounded, semi-transparan, cyan
    // =========================================================
    private static class ThinFadeScrollBarUI extends BasicScrollBarUI {
        private float thumbAlpha = 0f;
        private Timer fadeTimer;

        @Override
        protected void configureScrollBarColors() {
            this.trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void installListeners() {
            super.installListeners();
            fadeTimer = new Timer(30, e -> {
                boolean hoveringThumb = getThumbBounds().contains(
                        scrollbar.getMousePosition() != null ? scrollbar.getMousePosition() : new Point(-1, -1));
                float target = hoveringThumb ? 0.9f : 0.35f;
                thumbAlpha += (target - thumbAlpha) * 0.15f;
                scrollbar.repaint();
            });
            fadeTimer.start();
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(140, 226, 255, (int) (thumbAlpha * 255)));
            g2.fillRoundRect(thumbBounds.x + 1, thumbBounds.y, thumbBounds.width - 2, thumbBounds.height, 6, 6);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // Track sengaja transparan penuh, konsisten dengan minimalist UI
        }
    }
}