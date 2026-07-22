package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TournamentBracketPanel.java
 *
 * Direstyle agar satu bahasa visual dengan InputTournamentPanel: bagan
 * hologram cyan di atas background prosedural (gradient + grid HUD, TANPA
 * aset gambar), dengan bracket sudut viewfinder, breathing border, scanline
 * yang menyapu, dan tombol bergaya glass cyan yang identik.
 *
 * Header & footer diberi jatah tinggi tetap; bagan turnamen dipusatkan HANYA
 * pada area di antara keduanya (bukan seluruh tinggi panel), sehingga node
 * bagan tidak pernah menabrak tombol atau judul. Ruang kosong di kiri/kanan
 * bagan diisi panel info HUD ringkas (jumlah pemain, ronde, status) agar
 * tetap fungsional, bukan sekadar dekorasi kosong.
 *
 * Logika inti (bagan, refresh, aksi tombol) TIDAK DIUBAH — murni perubahan
 * tampilan. Efek sinematik lama (fog, debu, noise, siluet stickman) dan
 * background gambar dibuang karena tidak lagi relevan/diminta.
 */
public class TournamentBracketPanel extends JPanel {

    // =========================================================
    //  PALET WARNA — sama persis dengan InputTournamentPanel
    // =========================================================
    private static final Color C_WHITE        = new Color(240, 248, 252);
    private static final Color C_SOFT_BLUE    = new Color(176, 208, 226);
    private static final Color C_MUTED_BLUE   = new Color(120, 148, 168);
    private static final Color C_CYAN         = new Color(140, 226, 255);
    private static final Color C_CYAN_BRIGHT  = new Color(200, 244, 255);
    private static final Color C_CYAN_DIM     = new Color(90, 160, 190);

    private static final Color GLASS_FILL_TOP    = new Color(10, 24, 34, 150);
    private static final Color GLASS_FILL_BOTTOM = new Color(3, 9, 14, 205);

    private final MainFrame parent;
    private JButton btnPlayMatch;
    private JButton btnShuffle;
    private JPanel headerPanel;
    private JPanel footerPanel;

    // ---- Animasi ----
    private Timer masterTimer;
    private float entranceAlpha = 1f;
    private float titleBobPhase = 0f;
    private float glowBreathPhase = 0f;
    private float scanPhase = -0.2f;

    public TournamentBracketPanel(MainFrame parent) {
        this.parent = parent;
        setOpaque(true);
        setLayout(new BorderLayout());

        headerPanel = buildHeaderPanel();
        footerPanel = buildFooterPanel();
        add(headerPanel, BorderLayout.NORTH);
        add(footerPanel, BorderLayout.SOUTH);

        masterTimer = new Timer(16, e -> tickAnimation());
        masterTimer.start();
    }

    // =========================================================
    //  LATAR BELAKANG — prosedural (gradient + grid HUD), TANPA aset gambar
    // =========================================================
    private void drawProceduralBackground(Graphics2D g2, int w, int h) {
        g2.setPaint(new GradientPaint(0, 0, new Color(14, 22, 30), 0, h, new Color(4, 7, 10)));
        g2.fillRect(0, 0, w, h);

        // sorotan radial cyan sangat lembut di area atas-tengah, kesan sorotan panggung/HUD
        RadialGradientPaint spotlight = new RadialGradientPaint(
                new Point2D.Float(w / 2f, h * 0.18f), w * 0.6f,
                new float[]{0f, 1f},
                new Color[]{new Color(140, 226, 255, 20), new Color(140, 226, 255, 0)}
        );
        g2.setPaint(spotlight);
        g2.fillRect(0, 0, w, h);

        paintHudDotGrid(g2, w, h);
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

    // =========================================================
    //  ANIMASI
    // =========================================================
    private void tickAnimation() {
        if (entranceAlpha > 0f) {
            entranceAlpha = Math.max(0f, entranceAlpha - 0.045f);
        }
        titleBobPhase += 0.02f;
        glowBreathPhase += 0.02f;
        scanPhase += 0.0035f;
        if (scanPhase > 1.2f) scanPhase = -0.2f;
        repaint();
    }

    // =========================================================
    //  PAINT: latar + scanline HUD + bagan turnamen
    // =========================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = getWidth();
        int h = getHeight();

        drawProceduralBackground(g2, w, h);
        paintHudScanline(g2, w, h);
        paintCornerBrackets(g2, w, h);

        // ---- Area aman bagan: hanya di antara header & footer, TIDAK PERNAH
        //      menabrak tombol/judul, berapa pun dalam pohon turnamennya. ----
        int topSafe = headerPanel.getHeight();
        int bottomSafe = footerPanel.getHeight();
        int safeCenterY = topSafe + Math.max(0, (h - topSafe - bottomSafe) / 2);

        if (GameState.tournamentRoot != null) {
            int depth = computeTreeDepth(GameState.tournamentRoot);
            int rootX = (w / 2) + (125 * depth); // supaya seluruh bagan simetris di tengah
            drawNode(g2, GameState.tournamentRoot, rootX, safeCenterY, 200);
            paintSideInfoPanels(g2, w, h, topSafe, bottomSafe, depth);
        }

        g2.dispose();

        if (entranceAlpha > 0f) {
            Graphics2D gf = (Graphics2D) g.create();
            gf.setColor(new Color(0, 0, 0, (int) (entranceAlpha * 255)));
            gf.fillRect(0, 0, w, h);
            gf.dispose();
        }
    }

    /**
     * Panel info HUD ringkas di ruang kosong kiri & kanan bagan (jumlah
     * peserta, jumlah ronde, status turnamen). Hanya tampil kalau lebar
     * layar cukup lega, supaya TIDAK PERNAH menabrak node bagan di tengah.
     */
    private void paintSideInfoPanels(Graphics2D g2, int w, int h, int topSafe, int bottomSafe, int depth) {
        int boxW = 168, boxH = 92;
        int estimatedBracketHalfWidth = 95 + 250 * depth; // perkiraan lebar bagan dari tengah
        int marginNeeded = w / 2 - estimatedBracketHalfWidth;
        if (marginNeeded < boxW + 60) return; // ruang tidak cukup, jangan dipaksakan

        int centerY = topSafe + (h - topSafe - bottomSafe) / 2;
        int boxY = centerY - boxH / 2;

        int rounds = depth + 1;
        int totalPlayers = GameState.allPlayers != null ? GameState.allPlayers.size() : 0;
        boolean over = TournamentManager.isTournamentOver();

        drawHudInfoBox(g2, 34, boxY, boxW, boxH, "TOURNAMENT INFO",
                new String[]{"PESERTA", "RONDE"}, new String[]{String.valueOf(totalPlayers), String.valueOf(rounds)});

        drawHudInfoBox(g2, w - 34 - boxW, boxY, boxW, boxH, "STATUS",
                new String[]{"KEADAAN", "FORMAT"}, new String[]{over ? "SELESAI" : "BERLANGSUNG", "GUGUR TUNGGAL"});
    }

    private void drawHudInfoBox(Graphics2D g2, int x, int y, int w, int h, String title, String[] labels, String[] values) {
        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, 12, 12);
        g2.setPaint(new GradientPaint(x, y, GLASS_FILL_TOP, x, y + h, GLASS_FILL_BOTTOM));
        g2.fill(shape);
        g2.setColor(new Color(160, 230, 255, 70));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(shape);

        g2.setColor(C_CYAN);
        g2.fillRect(x + 12, y + 12, 3, 12);
        g2.setFont(pickTechFont(11, Font.BOLD));
        g2.setColor(C_WHITE);
        g2.drawString(title, x + 22, y + 22);

        g2.setColor(new Color(160, 230, 255, 45));
        g2.drawLine(x + 12, y + 30, x + w - 12, y + 30);

        int rowY = y + 50;
        g2.setFont(pickTechFont(10, Font.BOLD));
        for (int i = 0; i < labels.length; i++) {
            g2.setColor(C_MUTED_BLUE);
            g2.drawString(labels[i], x + 12, rowY);
            g2.setColor(C_CYAN_BRIGHT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(values[i], x + w - 12 - fm.stringWidth(values[i]), rowY);
            rowY += 22;
        }
    }

    /** Scanline HUD tipis yang menyapu turun terus-menerus, senada kartu hologram lain. */
    private void paintHudScanline(Graphics2D g2, int w, int h) {
        float y = h * scanPhase;
        float bandH = Math.max(14f, h * 0.05f);
        g2.setPaint(new GradientPaint(0, y - bandH / 2f, new Color(160, 230, 255, 0), 0, y, new Color(160, 230, 255, 22)));
        g2.fillRect(0, (int) (y - bandH / 2f), w, (int) (bandH / 2f));
        g2.setPaint(new GradientPaint(0, y, new Color(160, 230, 255, 22), 0, y + bandH / 2f, new Color(160, 230, 255, 0)));
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
    //  HEADER: back button cyan + judul + subjudul + pill "LIVE" + divider HUD
    // =========================================================
    private JPanel buildHeaderPanel() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(new EmptyBorder(26, 34, 6, 34));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(new MinimalBackButton("KEMBALI", () -> parent.showView("MENU_UTAMA")), BorderLayout.WEST);
        topRow.add(new LivePill(), BorderLayout.EAST);
        wrapper.add(topRow);
        wrapper.add(Box.createRigidArea(new Dimension(0, 4)));

        MonoLabel title = new MonoLabel("TOURNAMENT BRACKET", C_WHITE, C_CYAN, true, 3f);
        title.setFont(pickTitleFont(30));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setPreferredSize(new Dimension(10, 40));
        wrapper.add(title);

        MonoLabel sub = new MonoLabel("LIVE MATCH PROGRESSION", C_CYAN_DIM, C_CYAN_DIM, false, 2.4f);
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

    /** Pil kecil "LIVE" berkedip cyan, pengganti status ONLINE pada panel input. */
    private static class LivePill extends JComponent {
        private float blinkPhase = 0f;
        private final Timer timer;

        LivePill() {
            setOpaque(false);
            setPreferredSize(new Dimension(66, 22));
            timer = new Timer(30, e -> {
                blinkPhase += 0.05f;
                repaint();
            });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float pulse = 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(blinkPhase));

            int h = getHeight();
            g2.setColor(new Color(140, 226, 255, (int) (30 * pulse)));
            g2.fillRoundRect(0, h / 2 - 11, getWidth(), 22, 9, 9);
            g2.setColor(new Color(160, 230, 255, 70));
            g2.drawRoundRect(0, h / 2 - 11, getWidth() - 1, 21, 9, 9);

            g2.setColor(new Color(140, 226, 255, (int) (130 * pulse)));
            g2.fillOval(8, h / 2 - 5, 12, 12);
            g2.setColor(C_CYAN_BRIGHT);
            g2.fillOval(10, h / 2 - 3, 8, 8);

            g2.setFont(pickTechFont(10, Font.BOLD));
            g2.setColor(C_SOFT_BLUE);
            g2.drawString("LIVE", 24, h / 2 + 4);
            g2.dispose();
        }
    }

    /** Garis dekorasi HUD tipis dengan node kecil di tengah, aksen cyan (identik InputTournamentPanel). */
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
    //  FOOTER: Tombol Shuffle (outline cyan) & Mulai Pertandingan (CTA cyan)
    // =========================================================
    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 22, 10));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(6, 0, 28, 0));

        btnShuffle = new OutlineButton("\u21BB  ACAK POSISI (SHUFFLE)");
        btnShuffle.setPreferredSize(new Dimension(280, 54));
        btnShuffle.addActionListener(e -> {
            TournamentManager.shuffleAndRebuild(); // logika sama seperti versi asli
            repaint();
        });

        btnPlayMatch = new PrimaryButton("\u25B8  MULAI PERTANDINGAN BERIKUTNYA");
        btnPlayMatch.setPreferredSize(new Dimension(400, 54));
        btnPlayMatch.addActionListener(e -> {
            // logika sama seperti versi asli
            if (TournamentManager.isTournamentOver()) {
                parent.showResult();
            } else {
                TournamentManager.updateQueueFromTree();
                parent.startGame();
            }
        });

        footer.add(btnShuffle);
        footer.add(btnPlayMatch);
        return footer;
    }

    // =========================================================
    //  LOGIKA REFRESH -- TIDAK DIUBAH DARI VERSI ASLI
    // =========================================================
    public void refreshBracket() {
        boolean hasStarted = false;
        for (MatchNode m : GameState.allMatches) {
            if (m.winner != null) {
                hasStarted = true;
                break;
            }
        }

        btnShuffle.setVisible(!hasStarted);

        if (TournamentManager.isTournamentOver()) {
            btnPlayMatch.setText("\u25B8  LIHAT JUARA AKHIR");
        } else {
            btnPlayMatch.setText("\u25B8  MULAI PERTANDINGAN BERIKUTNYA");
        }

        revalidate();
        repaint();
    }

    // =========================================================
    //  GAMBAR BAGAN (logika rekursi sama persis, tampilan direstyle cyan)
    // =========================================================
    private int computeTreeDepth(MatchNode node) {
        if (node == null || node.left == null) return 0;
        return 1 + Math.max(computeTreeDepth(node.left), computeTreeDepth(node.right));
    }

    private void drawNode(Graphics2D g, MatchNode node, int x, int y, int yOffset) {
        if (node == null) return;

        int w = 190, h = 82;
        boolean decided = node.winner != null;
        RoundRectangle2D shape = new RoundRectangle2D.Float(x - w / 2f, y - h / 2f, w, h, 14, 14);

        // Kartu match: glass gradien gelap, border cyan (lebih terang & berdenyut kalau sudah ada pemenang)
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Float(x - w / 2f + 2, y - h / 2f + 4, w, h, 14, 14));

        g.setPaint(new GradientPaint(x, y - h / 2f, GLASS_FILL_TOP, x, y + h / 2f, GLASS_FILL_BOTTOM));
        g.fill(shape);

        float breathe = 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(glowBreathPhase * 1.4f));
        if (decided) {
            g.setColor(new Color(140, 226, 255, (int) (60 * breathe)));
            g.setStroke(new BasicStroke(3f));
            g.draw(new RoundRectangle2D.Float(x - w / 2f - 2, y - h / 2f - 2, w + 4, h + 4, 16, 16));
            g.setColor(new Color(200, 244, 255, clampAlpha(0.75f * breathe + 0.25f)));
            g.setStroke(new BasicStroke(1.6f));
        } else {
            g.setColor(new Color(160, 230, 255, 90));
            g.setStroke(new BasicStroke(1.1f));
        }
        g.draw(shape);

        g.setFont(pickTechFont(12, Font.BOLD));
        drawPlayerInfo(g, node.p1, node.winner, x - w / 2 + 14, y - 14);
        drawPlayerInfo(g, node.p2, node.winner, x - w / 2 + 14, y + 26);

        if (node.left != null) {
            int childX = x - 250;
            int childYLeft = y - yOffset;
            int childYRight = y + yOffset;

            GradientPaint linePaint = new GradientPaint(childX, y, new Color(140, 226, 255, 90), x, y, new Color(140, 226, 255, 20));
            g.setPaint(linePaint);
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new Line2D.Float(x - w / 2f, y, childX + w / 2f, childYLeft));
            g.draw(new Line2D.Float(x - w / 2f, y, childX + w / 2f, childYRight));

            drawNode(g, node.left, childX, childYLeft, yOffset / 2);
            drawNode(g, node.right, childX, childYRight, yOffset / 2);
        }
    }

    private void drawPlayerInfo(Graphics2D g, Player p, Player winner, int x, int y) {
        if (p == null) {
            g.setColor(C_MUTED_BLUE);
            g.drawString("MENUNGGU...", x, y);
            return;
        }

        if (winner != null && winner != p) {
            // Kalah: biru redup + coret
            g.setColor(C_MUTED_BLUE);
            String name = p.getName().toUpperCase();
            g.drawString(name, x, y);
            int strW = g.getFontMetrics().stringWidth(name);
            g.drawLine(x, y - 4, x + strW, y - 4);
        } else if (winner == p) {
            // Menang: cyan terang + bold + glow tipis + bintang
            String name = p.getName().toUpperCase() + " \u2605";
            Font bold = g.getFont().deriveFont(Font.BOLD);
            g.setFont(bold);
            g.setColor(new Color(140, 226, 255, 90));
            g.drawString(name, x + 1, y + 1);
            g.setColor(C_CYAN_BRIGHT);
            g.drawString(name, x, y);
        } else {
            g.setColor(C_WHITE);
            g.drawString(p.getName().toUpperCase(), x, y);
        }
    }

    // =========================================================
    //  TOMBOL KEMBALI — glass cyan, glow bernafas, light sweep saat hover
    //  (identik strukturnya dengan InputTournamentPanel).
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
    //  TOMBOL OUTLINE CYAN (aksi sekunder — Shuffle)
    // =========================================================
    private static class OutlineButton extends JButton {
        private boolean hover = false;
        private float scale = 1f;
        private float breathePhase = 0f;
        private final Timer animTimer;

        OutlineButton(String text) {
            super(text);
            setFont(pickTechFont(14, Font.BOLD));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setForeground(C_WHITE);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    scale = 0.96f;
                }
            });

            animTimer = new Timer(16, e -> {
                scale += (1f - scale) * 0.25f;
                breathePhase += 0.03f;
                repaint();
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            AffineTransform old = g2.getTransform();
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale, scale);
            g2.translate(-w / 2.0, -h / 2.0);

            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 12, 12);
            float breathe = 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(breathePhase));

            g2.setColor(new Color(140, 226, 255, hover ? 34 : 16));
            g2.fill(shape);
            g2.setColor(new Color(160, 230, 255, (int) ((hover ? 210 : 130) * breathe + (hover ? 40 : 0))));
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(shape);

            g2.setTransform(old);
            g2.dispose();

            setForeground(hover ? C_CYAN_BRIGHT : C_WHITE);
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  TOMBOL CTA UTAMA — glass + energy sweep + breathing glow + partikel
    //  (identik gaya StartButton pada InputTournamentPanel).
    // =========================================================
    private static class PrimaryButton extends JButton {
        private boolean hover = false;
        private float pulsePhase = 0f;
        private float hoverAnim = 0f;
        private long streakStart = -1L;
        private final List<float[]> particles = new ArrayList<>(); // {x, y, vy, life}
        private final java.util.Random rng = new java.util.Random();
        private final Timer fxTimer;

        PrimaryButton(String text) {
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

                if (hover && getWidth() > 0 && rng.nextFloat() < 0.35f) {
                    particles.add(new float[]{rng.nextFloat() * getWidth(), getHeight() + 2, 0.6f + rng.nextFloat() * 0.8f, 1f});
                }
                java.util.Iterator<float[]> it = particles.iterator();
                while (it.hasNext()) {
                    float[] p = it.next();
                    p[1] -= p[2];
                    p[3] -= 0.03f;
                    if (p[3] <= 0f) it.remove();
                }
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

                for (float[] p : particles) {
                    g2.setColor(new Color(255, 255, 255, (int) (200 * p[3])));
                    g2.fillOval((int) p[0], (int) p[1], 3, 3);
                }
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
}