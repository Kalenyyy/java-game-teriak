package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TournamentBracketPanel.java
 * Halaman bagan turnamen -- REDESIGN VISUAL TOTAL, senada InputTournamentPanel.
 *
 * Tema: monochrome brutalist-cinematic, murni hitam/putih/abu-abu tanpa
 * warna mencolok sama sekali (termasuk status "menang" -- diselaraskan jadi
 * putih terang + bold + glow, bukan lagi emas/hijau/merah).
 *
 * PENTING: Seluruh LOGIKA program (drawNode, refreshBracket, shuffle, cek
 * turnamen selesai, updateQueueFromTree, navigasi ke GamePanel/ResultPanel)
 * dipertahankan 100% sama seperti versi asli. Yang berubah HANYA layout,
 * warna, font, animasi, dan painting.
 */
public class TournamentBracketPanel extends JPanel {

    // =========================================================
    //  PALET WARNA (murni grayscale)
    // =========================================================
    private static final Color BG_TOP = new Color(20, 20, 20);
    private static final Color BG_BOTTOM = new Color(4, 4, 4);
    private static final Color TEXT_PRIMARY = new Color(245, 245, 245);
    private static final Color TEXT_SECONDARY = new Color(165, 165, 165);
    private static final Color TEXT_MUTED = new Color(105, 105, 105);
    private static final Color COLOR_ELIMINATED = new Color(120, 120, 120);
    private static final Color COLOR_PENDING = new Color(90, 90, 90);
    private static final Color COLOR_ACTIVE = new Color(230, 230, 230);
    private static final Color CARD_BG = new Color(255, 255, 255, 16);
    private static final Color CARD_BORDER = new Color(255, 255, 255, 45);
    private static final Color CARD_BORDER_WIN = new Color(255, 255, 255, 150);

    private final MainFrame parent;
    private JButton btnPlayMatch;
    private JButton btnShuffle;

    // ---- Animasi & efek sinematik ----
    private Timer masterTimer;
    private float entranceProgress = 0f;
    private float titleBobPhase = 0f;
    private float glowBreathPhase = 0f;

    private BufferedImage ambientLayer;
    private BufferedImage noiseLayer;
    private final Random rng = new Random();
    private final List<float[]> dustParticles = new ArrayList<>();
    private final List<float[]> fogBlobs = new ArrayList<>();

    public TournamentBracketPanel(MainFrame parent) {
        this.parent = parent;
        setOpaque(true);
        setBackground(BG_BOTTOM);
        setLayout(new BorderLayout());

        initParticles();

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildFooterPanel(), BorderLayout.SOUTH);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                regenerateCachedLayers();
            }
        });

        masterTimer = new Timer(16, e -> tickAnimation());
        masterTimer.start();
    }

    // =========================================================
    //  PARTIKEL & ANIMASI
    // =========================================================
    private void initParticles() {
        for (int i = 0; i < 30; i++) {
            float x = rng.nextFloat();
            float y = rng.nextFloat();
            float vy = 0.00012f + rng.nextFloat() * 0.00025f;
            float size = 1f + rng.nextFloat() * 2.2f;
            float alphaBase = 0.07f + rng.nextFloat() * 0.15f;
            float phase = rng.nextFloat() * 6.28f;
            dustParticles.add(new float[]{x, y, vy, size, alphaBase, phase});
        }
        for (int i = 0; i < 3; i++) {
            float x = rng.nextFloat();
            float y = 0.5f + rng.nextFloat() * 0.4f;
            float vx = (0.00006f + rng.nextFloat() * 0.00010f) * (rng.nextBoolean() ? 1 : -1);
            float radius = 150f + rng.nextFloat() * 130f;
            float alphaBase = 0.02f + rng.nextFloat() * 0.02f;
            float phase = rng.nextFloat() * 6.28f;
            fogBlobs.add(new float[]{x, y, vx, radius, alphaBase, phase});
        }
    }

    private void tickAnimation() {
        if (entranceProgress < 1f) entranceProgress = Math.min(1f, entranceProgress + 0.035f);
        titleBobPhase += 0.02f;
        glowBreathPhase += 0.015f;

        for (float[] p : dustParticles) {
            p[1] -= p[2];
            if (p[1] < -0.02f) p[1] = 1.02f;
        }
        for (float[] f : fogBlobs) {
            f[0] += f[2];
            if (f[0] < -0.3f) f[0] = 1.3f;
            if (f[0] > 1.3f) f[0] = -0.3f;
        }
        repaint();
    }

    private void regenerateCachedLayers() {
        int w = Math.max(getWidth(), 1);
        int h = Math.max(getHeight(), 1);

        ambientLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ga = ambientLayer.createGraphics();
        ga.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RadialGradientPaint vignette = new RadialGradientPaint(
                new Point2D.Float(w / 2f, h / 2f), Math.max(w, h) * 0.78f,
                new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 55), new Color(0, 0, 0, 165)}
        );
        ga.setPaint(vignette);
        ga.fillRect(0, 0, w, h);

        RadialGradientPaint spotlight = new RadialGradientPaint(
                new Point2D.Float(w * 0.75f, h * 0.15f), w * 0.5f,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 22), new Color(255, 255, 255, 0)}
        );
        ga.setPaint(spotlight);
        ga.fillRect(0, 0, w, h);
        ga.dispose();

        noiseLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gn = noiseLayer.createGraphics();
        gn.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int i = 0; i < (w * h) / 900; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);
            int shade = rng.nextBoolean() ? 255 : 0;
            int alpha = 6 + rng.nextInt(10);
            gn.setColor(new Color(shade, shade, shade, alpha));
            gn.fillRect(x, y, 1, 1);
        }
        gn.setColor(new Color(255, 255, 255, 5));
        for (int y = 0; y < h; y += 3) gn.drawLine(0, y, w, y);
        gn.dispose();

        repaint();
    }

    // =========================================================
    //  PAINT: latar sinematik + bagan turnamen
    // =========================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (ambientLayer == null || ambientLayer.getWidth() != w || ambientLayer.getHeight() != h) {
            regenerateCachedLayers();
        }

        GradientPaint base = new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM);
        g2.setPaint(base);
        g2.fillRect(0, 0, w, h);

        drawFog(g2, w, h);
        drawStickman(g2, w * 0.10f, h * 0.85f, Math.min(w, h) * 0.20f, 0.045f);
        drawStickman(g2, w * 0.90f, h * 0.14f, Math.min(w, h) * 0.18f, 0.04f);
        drawDust(g2, w, h);

        if (ambientLayer != null) g2.drawImage(ambientLayer, 0, 0, null);
        if (noiseLayer != null) g2.drawImage(noiseLayer, 0, 0, null);

        // Bagan turnamen digambar di atas latar, di bawah komponen UI (header/footer)
        if (GameState.tournamentRoot != null) {
            int depth = computeTreeDepth(GameState.tournamentRoot);
            int rootX = (w / 2) + (125 * depth); // supaya seluruh bagan (bukan cuma root) simetris di tengah
            drawNode(g2, GameState.tournamentRoot, rootX, h / 2, 200);
        }

        g2.dispose();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (entranceProgress < 1f) {
            Graphics2D g2 = (Graphics2D) g.create();
            int h = getHeight();
            int curtainH = (int) (h * (1f - smoothStep(entranceProgress)));
            g2.setColor(new Color(2, 2, 2, (int) (255 * (1f - entranceProgress) * 0.9f + 20)));
            g2.fillRect(0, 0, getWidth(), curtainH);
            g2.dispose();
        }
    }

    private float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    private void drawFog(Graphics2D g2, int w, int h) {
        for (float[] f : fogBlobs) {
            float cx = f[0] * w;
            float cy = f[1] * h + (float) Math.sin(glowBreathPhase + f[5]) * 8f;
            float radius = f[3];
            float alpha = f[4] * (0.7f + 0.3f * (float) Math.sin(glowBreathPhase * 0.6f + f[5]));
            RadialGradientPaint fogPaint = new RadialGradientPaint(
                    new Point2D.Float(cx, cy), radius, new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, clampAlpha(alpha)), new Color(255, 255, 255, 0)}
            );
            g2.setPaint(fogPaint);
            g2.fillOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));
        }
    }

    private void drawDust(Graphics2D g2, int w, int h) {
        for (float[] p : dustParticles) {
            float x = p[0] * w;
            float y = p[1] * h;
            float size = p[3];
            float flicker = 0.6f + 0.4f * (float) Math.sin(glowBreathPhase * 2f + p[5]);
            int alpha = clampAlpha(p[4] * flicker);
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.fillOval((int) x, (int) y, (int) size, (int) size);
        }
    }

    private int clampAlpha(float a) {
        return Math.max(0, Math.min(255, (int) (a * 255)));
    }

    // =========================================================
    //  STICKMAN REDESIGN (senada InputTournamentPanel)
    // =========================================================
    private void drawStickman(Graphics2D g2, float cx, float cy, float scale, float baseAlpha) {
        GeneralPath body = new GeneralPath();
        float headR = scale * 0.12f;
        float neckY = cy - scale * 0.34f;
        float hipY = cy + scale * 0.10f;

        body.append(new Ellipse2D.Float(cx - headR, neckY - headR * 2.1f, headR * 2, headR * 2), false);
        body.moveTo(cx, neckY);
        body.curveTo(cx - scale * 0.01f, neckY + scale * 0.15f, cx + scale * 0.01f, hipY - scale * 0.1f, cx, hipY);

        body.moveTo(cx, neckY + scale * 0.06f);
        body.curveTo(cx - scale * 0.06f, neckY + scale * 0.14f, cx - scale * 0.14f, neckY + scale * 0.20f, cx - scale * 0.18f, neckY + scale * 0.30f);
        body.moveTo(cx, neckY + scale * 0.06f);
        body.curveTo(cx + scale * 0.06f, neckY + scale * 0.14f, cx + scale * 0.14f, neckY + scale * 0.20f, cx + scale * 0.18f, neckY + scale * 0.30f);

        body.moveTo(cx, hipY);
        body.curveTo(cx - scale * 0.05f, hipY + scale * 0.20f, cx - scale * 0.14f, hipY + scale * 0.32f, cx - scale * 0.20f, hipY + scale * 0.46f);
        body.moveTo(cx, hipY);
        body.curveTo(cx + scale * 0.05f, hipY + scale * 0.20f, cx + scale * 0.14f, hipY + scale * 0.32f, cx + scale * 0.20f, hipY + scale * 0.46f);

        AffineTransform shadowTx = AffineTransform.getTranslateInstance(scale * 0.03f, scale * 0.05f);
        Shape shadowShape = shadowTx.createTransformedShape(body);
        g2.setColor(new Color(0, 0, 0, clampAlpha(baseAlpha * 1.4f)));
        g2.setStroke(new BasicStroke(scale * 0.045f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(shadowShape);

        g2.setColor(new Color(255, 255, 255, clampAlpha(baseAlpha * 0.5f)));
        g2.setStroke(new BasicStroke(scale * 0.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(body);
        g2.setColor(new Color(255, 255, 255, clampAlpha(baseAlpha * 0.8f)));
        g2.setStroke(new BasicStroke(scale * 0.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(body);

        g2.setColor(new Color(255, 255, 255, clampAlpha(baseAlpha * 1.6f)));
        g2.setStroke(new BasicStroke(scale * 0.03f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(body);
    }

    // =========================================================
    //  HEADER
    // =========================================================
    private JPanel buildHeaderPanel() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(new MinimalBackButton("\u2190 KEMBALI", () -> parent.showView("MENU_UTAMA")), BorderLayout.WEST);
        wrapper.add(topRow);

        TitleHeader titleHeader = new TitleHeader();
        titleHeader.setPreferredSize(new Dimension(10, 100));
        wrapper.add(titleHeader);
        wrapper.add(Box.createRigidArea(new Dimension(0, 6)));

        return wrapper;
    }

    private class TitleHeader extends JComponent {
        TitleHeader() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            float bob = (float) Math.sin(titleBobPhase) * 2f;

            Font smallFont = new Font("Arial", Font.BOLD, 13);
            g2.setFont(smallFont);
            g2.setColor(TEXT_MUTED);
            drawTracked(g2, "BAGAN PERTANDINGAN", w / 2f, 20, 4f);

            Font titleFont = pickTitleFont(34);
            g2.setFont(titleFont);
            float titleY = 60 + bob;
            float glowAlpha = 0.5f + 0.5f * (float) Math.sin(glowBreathPhase);
            drawBloomText(g2, "TOURNAMENT BRACKET", w / 2f, titleY, titleFont, glowAlpha);

            g2.setColor(new Color(255, 255, 255, 55));
            g2.setStroke(new BasicStroke(1f));
            int lineW = (int) (w * 0.20f);
            g2.drawLine(w / 2 - lineW / 2, (int) titleY + 14, w / 2 + lineW / 2, (int) titleY + 14);

            g2.dispose();
        }
    }

    private void drawTracked(Graphics2D g2, String text, float centerX, float y, float extraSpacing) {
        FontMetrics fm = g2.getFontMetrics();
        float totalWidth = 0;
        for (char c : text.toCharArray()) totalWidth += fm.charWidth(c) + extraSpacing;
        totalWidth -= extraSpacing;
        float x = centerX - totalWidth / 2f;
        for (char c : text.toCharArray()) {
            g2.drawString(String.valueOf(c), x, y);
            x += fm.charWidth(c) + extraSpacing;
        }
    }

    private void drawBloomText(Graphics2D g2, String text, float centerX, float y, Font font, float glowAlpha) {
        FontMetrics fm = g2.getFontMetrics(font);
        int textWidth = fm.stringWidth(text);
        float x = centerX - textWidth / 2f;

        int[] offsets = {5, 3, 2};
        for (int r : offsets) {
            float a = glowAlpha * (0.10f - r * 0.02f);
            g2.setColor(new Color(255, 255, 255, clampAlpha(Math.max(0, a))));
            for (int dx = -r; dx <= r; dx += r) {
                for (int dy = -r; dy <= r; dy += r) {
                    if (dx == 0 && dy == 0) continue;
                    g2.drawString(text, x + dx, y + dy);
                }
            }
        }
        g2.setColor(TEXT_PRIMARY);
        g2.drawString(text, x, y);
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
    //  FOOTER: Tombol Shuffle (outline) & Mulai Pertandingan (solid)
    // =========================================================
    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 22, 10));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(6, 0, 28, 0));

        btnShuffle = new OutlineButton("ACAK POSISI (SHUFFLE)");
        btnShuffle.setPreferredSize(new Dimension(280, 54));
        btnShuffle.addActionListener(e -> {
            TournamentManager.shuffleAndRebuild(); // logika sama seperti versi asli
            repaint();
        });

        btnPlayMatch = new PrimaryButton("MULAI PERTANDINGAN BERIKUTNYA");
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

        if (hasStarted) {
            btnShuffle.setVisible(false);
        } else {
            btnShuffle.setVisible(true);
        }

        if (TournamentManager.isTournamentOver()) {
            btnPlayMatch.setText("LIHAT JUARA AKHIR");
        } else {
            btnPlayMatch.setText("MULAI PERTANDINGAN BERIKUTNYA");
        }

        revalidate();
        repaint();
    }

    // =========================================================
    //  GAMBAR BAGAN (logika rekursi sama persis, tampilan direstyle)
    // =========================================================
    private int computeTreeDepth(MatchNode node) {
        if (node == null || node.left == null) return 0;
        return 1 + Math.max(computeTreeDepth(node.left), computeTreeDepth(node.right));
    }

    private void drawNode(Graphics2D g, MatchNode node, int x, int y, int yOffset) {
        if (node == null) return;

        int w = 190, h = 82;
        boolean decided = node.winner != null;

        // Kartu match: kaca semi-transparan, border lebih terang kalau sudah ada pemenang
        g.setColor(CARD_BG);
        g.fillRoundRect(x - w / 2, y - h / 2, w, h, 14, 14);

        if (decided) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(glowBreathPhase * 1.4f);
            g.setColor(new Color(255, 255, 255, clampAlpha((CARD_BORDER_WIN.getAlpha() / 255f) * pulse)));
        } else {
            g.setColor(CARD_BORDER);
        }
        g.setStroke(new BasicStroke(decided ? 1.6f : 1.2f));
        g.drawRoundRect(x - w / 2, y - h / 2, w, h, 14, 14);

        g.setFont(new Font("Arial", Font.BOLD, 12));

        drawPlayerInfo(g, node.p1, node.winner, x - w / 2 + 12, y - 14);
        drawPlayerInfo(g, node.p2, node.winner, x - w / 2 + 12, y + 26);

        if (node.left != null) {
            int childX = x - 250;
            int childYLeft = y - yOffset;
            int childYRight = y + yOffset;

            g.setColor(new Color(255, 255, 255, 40));
            g.setStroke(new BasicStroke(1.3f));
            g.drawLine(x - w / 2, y, childX + w / 2, childYLeft);
            g.drawLine(x - w / 2, y, childX + w / 2, childYRight);

            drawNode(g, node.left, childX, childYLeft, yOffset / 2);
            drawNode(g, node.right, childX, childYRight, yOffset / 2);
        }
    }

    private void drawPlayerInfo(Graphics2D g, Player p, Player winner, int x, int y) {
        if (p == null) {
            g.setColor(COLOR_PENDING);
            g.drawString("MENUNGGU...", x, y);
            return;
        }

        if (winner != null && winner != p) {
            // Kalah: abu-abu redup + coret (monochrome, bukan merah)
            g.setColor(COLOR_ELIMINATED);
            String name = p.getName().toUpperCase();
            g.drawString(name, x, y);
            int stringWidth = g.getFontMetrics().stringWidth(name);
            g.drawLine(x, y - 4, x + stringWidth, y - 4);
        } else if (winner == p) {
            // Menang: putih terang + bold + glow tipis (monochrome, bukan emas)
            String name = p.getName().toUpperCase() + " \u2605";
            g.setFont(g.getFont().deriveFont(Font.BOLD));
            g.setColor(new Color(255, 255, 255, 40));
            g.drawString(name, x + 1, y + 1);
            g.setColor(TEXT_PRIMARY);
            g.drawString(name, x, y);
        } else {
            g.setColor(COLOR_ACTIVE);
            g.drawString(p.getName().toUpperCase(), x, y);
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Tombol kembali minimalis
    // =========================================================
    private static class MinimalBackButton extends JComponent {
        private final String text;
        private final Runnable action;
        private boolean hover = false;
        private float pressScale = 1f;
        private Timer pressTimer;

        MinimalBackButton(String text, Runnable action) {
            this.text = text;
            this.action = action;
            setOpaque(false);
            setFont(new Font("Arial", Font.PLAIN, 14));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(120, 44));
            setBorder(new EmptyBorder(16, 18, 0, 0));

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

                @Override
                public void mousePressed(MouseEvent e) {
                    animatePress();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        private void animatePress() {
            pressScale = 0.9f;
            repaint();
            if (pressTimer != null) pressTimer.stop();
            pressTimer = new Timer(30, e -> {
                pressScale += (1f - pressScale) * 0.4f;
                if (Math.abs(1f - pressScale) < 0.01f) {
                    pressScale = 1f;
                    ((Timer) e.getSource()).stop();
                }
                repaint();
            });
            pressTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int shiftX = hover ? 5 : 0;
            g2.translate(14 + shiftX, 26);
            g2.scale(pressScale, pressScale);

            g2.setFont(getFont());
            g2.setColor(hover ? TEXT_PRIMARY : TEXT_SECONDARY);
            g2.drawString(text, 0, 0);

            if (hover) {
                int textWidth = g2.getFontMetrics().stringWidth(text);
                g2.setColor(new Color(255, 255, 255, 160));
                g2.drawLine(0, 5, textWidth, 5);
            }

            g2.dispose();
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Tombol outline (aksi sekunder -- Shuffle)
    // =========================================================
    private static class OutlineButton extends JButton {
        private boolean hover = false;
        private float scale = 1f;
        private Timer animTimer;

        OutlineButton(String text) {
            super(text);
            setFont(new Font("Arial", Font.BOLD, 15));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setForeground(TEXT_PRIMARY);

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

                @Override
                public void mousePressed(MouseEvent e) {
                    scale = 0.96f;
                    repaint();
                }
            });

            animTimer = new Timer(16, e -> {
                scale += (1f - scale) * 0.25f;
                repaint();
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            AffineTransform old = g2.getTransform();
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale, scale);
            g2.translate(-w / 2.0, -h / 2.0);

            g2.setColor(new Color(255, 255, 255, hover ? 26 : 12));
            g2.fillRoundRect(0, 0, w, h, 12, 12);
            g2.setColor(new Color(255, 255, 255, hover ? 190 : 130));
            g2.setStroke(new BasicStroke(1.3f));
            g2.drawRoundRect(1, 1, w - 2, h - 2, 12, 12);

            g2.setTransform(old);
            g2.dispose();

            setForeground(TEXT_PRIMARY);
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Tombol utama (aksi primer -- Mulai Pertandingan)
    // =========================================================
    private static class PrimaryButton extends JButton {
        private boolean hover = false;
        private float scale = 1f;
        private float breath = 0f;
        private final List<float[]> ripples = new ArrayList<>();
        private Timer animTimer;

        PrimaryButton(String text) {
            super(text);
            setFont(new Font("Arial", Font.BOLD, 16));
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

                @Override
                public void mousePressed(MouseEvent e) {
                    scale = 0.95f;
                    ripples.add(new float[]{e.getX(), e.getY(), 4f, 0.5f});
                    repaint();
                }
            });

            animTimer = new Timer(16, e -> {
                breath += 0.03f;
                scale += (1f - scale) * 0.25f;

                java.util.Iterator<float[]> it = ripples.iterator();
                while (it.hasNext()) {
                    float[] r = it.next();
                    r[2] += 9f;
                    r[3] -= 0.02f;
                    if (r[3] <= 0f) it.remove();
                }
                repaint();
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            float pulseGlow = 0.5f + 0.5f * (float) Math.sin(breath);
            float hoverBoost = hover ? 1f : 0f;

            g2.setColor(new Color(0, 0, 0, 90));
            g2.fillRoundRect(0, 6, w, h, 14, 14);

            int glowAlpha = (int) (16 + pulseGlow * 12 + hoverBoost * 24);
            g2.setColor(new Color(255, 255, 255, Math.min(255, glowAlpha)));
            g2.fillRoundRect(-6, -6, w + 12, h + 12, 18, 18);

            AffineTransform old = g2.getTransform();
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale * (1f + hoverBoost * 0.02f), scale * (1f + hoverBoost * 0.02f));
            g2.translate(-w / 2.0, -h / 2.0);

            g2.setColor(hover ? Color.WHITE : new Color(232, 232, 232));
            g2.fillRoundRect(0, 0, w, h, 14, 14);

            Shape oldClip = g2.getClip();
            g2.clip(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            for (float[] r : ripples) {
                g2.setColor(new Color(0, 0, 0, clampAlpha(r[3])));
                g2.fillOval((int) (r[0] - r[2]), (int) (r[1] - r[2]), (int) (r[2] * 2), (int) (r[2] * 2));
            }
            g2.setClip(oldClip);
            g2.setTransform(old);

            setForeground(new Color(10, 10, 10));
            int textShift = hover ? 2 : 0;
            Graphics textG = g2.create(textShift, 0, w, h);
            super.paintComponent(textG);
            textG.dispose();

            g2.dispose();
        }

        private int clampAlpha(float a) {
            return Math.max(0, Math.min(255, (int) (a * 255)));
        }
    }
}