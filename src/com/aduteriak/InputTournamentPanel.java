package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class InputTournamentPanel extends JPanel {

    // =========================================================
    //  PALET WARNA (murni grayscale, tanpa aksen warna)
    // =========================================================
    private static final Color BG_TOP = new Color(20, 20, 20);
    private static final Color BG_BOTTOM = new Color(4, 4, 4);
    private static final Color TEXT_PRIMARY = new Color(245, 245, 245);
    private static final Color TEXT_SECONDARY = new Color(165, 165, 165);
    private static final Color TEXT_MUTED = new Color(105, 105, 105);
    private static final Color CARD_BG = new Color(255, 255, 255, 16);
    private static final Color CARD_BORDER = new Color(255, 255, 255, 40);
    private static final Color GLOW_WHITE = new Color(255, 255, 255);

    // =========================================================
    //  STATE LOGIKA ASLI (tidak diubah)
    // =========================================================
    private JPanel dynamicForm;
    private ArrayList<JTextField> nameFields;
    private MainFrame parent;
    private int playerCount = 4; // Default

    // =========================================================
    //  STATE VISUAL / ANIMASI
    // =========================================================
    private Timer masterTimer;
    private float entranceProgress = 0f; // 0 = tertutup, 1 = selesai reveal
    private float titleBobPhase = 0f;
    private float glowBreathPhase = 0f;

    private BufferedImage ambientLayer;   // vignette + spotlight, di-cache
    private BufferedImage noiseLayer;     // grain + scanline, di-cache
    private final Random rng = new Random();

    private final List<float[]> dustParticles = new ArrayList<>(); // {x,y,vy,size,alphaBase,phase}
    private final List<float[]> fogBlobs = new ArrayList<>();      // {x,y,vx,radius,alphaBase,phase}

    private SegmentedControl segmentedControl;

    public InputTournamentPanel(MainFrame parent) {
        this.parent = parent;
        this.nameFields = new ArrayList<>();

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(BG_BOTTOM);

        initParticles();

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildFooterPanel(), BorderLayout.SOUTH);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                regenerateCachedLayers();
            }
        });

        masterTimer = new Timer(16, e -> tickAnimation());
        masterTimer.start();

        // Inisialisasi awal dengan 4 field (logika sama seperti versi asli)
        generateFields(4);
    }

    // =========================================================
    //  PARTIKEL: debu mengambang & kabut tipis
    // =========================================================
    private void initParticles() {
        for (int i = 0; i < 34; i++) {
            float x = rng.nextFloat();
            float y = rng.nextFloat();
            float vy = 0.00012f + rng.nextFloat() * 0.00025f;
            float size = 1f + rng.nextFloat() * 2.2f;
            float alphaBase = 0.08f + rng.nextFloat() * 0.16f;
            float phase = rng.nextFloat() * 6.28f;
            dustParticles.add(new float[]{x, y, vy, size, alphaBase, phase});
        }
        for (int i = 0; i < 4; i++) {
            float x = rng.nextFloat();
            float y = 0.55f + rng.nextFloat() * 0.4f;
            float vx = (0.00006f + rng.nextFloat() * 0.00010f) * (rng.nextBoolean() ? 1 : -1);
            float radius = 140f + rng.nextFloat() * 120f;
            float alphaBase = 0.02f + rng.nextFloat() * 0.025f;
            float phase = rng.nextFloat() * 6.28f;
            fogBlobs.add(new float[]{x, y, vx, radius, alphaBase, phase});
        }
    }

    private void tickAnimation() {
        if (entranceProgress < 1f) {
            entranceProgress = Math.min(1f, entranceProgress + 0.035f);
        }
        titleBobPhase += 0.02f;
        glowBreathPhase += 0.015f;

        int w = Math.max(getWidth(), 1);
        int h = Math.max(getHeight(), 1);

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

    // =========================================================
    //  CACHE LAYER: vignette + spotlight, dan noise + scanline
    // =========================================================
    private void regenerateCachedLayers() {
        int w = Math.max(getWidth(), 1);
        int h = Math.max(getHeight(), 1);

        // ---- Ambient layer: vignette gelap di tepi + spotlight lembut di atas ----
        ambientLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ga = ambientLayer.createGraphics();
        ga.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RadialGradientPaint vignette = new RadialGradientPaint(
                new Point2D.Float(w / 2f, h / 2f),
                Math.max(w, h) * 0.75f,
                new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 60), new Color(0, 0, 0, 170)}
        );
        ga.setPaint(vignette);
        ga.fillRect(0, 0, w, h);

        RadialGradientPaint spotlight = new RadialGradientPaint(
                new Point2D.Float(w / 2f, h * 0.18f),
                w * 0.55f,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 26), new Color(255, 255, 255, 0)}
        );
        ga.setPaint(spotlight);
        ga.fillRect(0, 0, w, h);
        ga.dispose();

        // ---- Noise layer: grain halus + scanline tipis (dihitung sekali, dipakai ulang) ----
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
        for (int y = 0; y < h; y += 3) {
            gn.drawLine(0, y, w, y);
        }
        gn.dispose();

        repaint();
    }

    // =========================================================
    //  PAINT: latar sinematik penuh
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
        drawStickmen(g2, w, h);
        drawDust(g2, w, h);

        if (ambientLayer != null) g2.drawImage(ambientLayer, 0, 0, null);
        if (noiseLayer != null) g2.drawImage(noiseLayer, 0, 0, null);

        g2.dispose();
    }

    /** Entrance reveal digambar di atas SEMUA (termasuk child component) lewat override paint(). */
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
                    new Point2D.Float(cx, cy), radius,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, clampAlpha(alpha)), new Color(255, 255, 255, 0)}
            );
            g2.setPaint(fogPaint);
            g2.fillOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));
        }
    }

    private void drawDust(Graphics2D g2, int w, int h) {
        g2.setColor(Color.WHITE);
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
    //  STICKMAN REDESIGN: 3 pose, faux-blur, glow, bayangan
    // =========================================================
    private void drawStickmen(Graphics2D g2, int w, int h) {
        drawStickman(g2, w * 0.16f, h * 0.62f, Math.min(w, h) * 0.30f, StickPose.STANDING, 0.05f);
        drawStickman(g2, w * 0.86f, h * 0.30f, Math.min(w, h) * 0.24f, StickPose.HANDS_UP, 0.045f);
        drawStickman(g2, w * 0.50f, h * 0.85f, Math.min(w, h) * 0.34f, StickPose.SCREAMING, 0.04f);
    }

    private enum StickPose { STANDING, HANDS_UP, SCREAMING }

    private void drawStickman(Graphics2D g2, float cx, float cy, float scale, StickPose pose, float baseAlpha) {
        GeneralPath body = buildStickPath(cx, cy, scale, pose);

        // Bayangan transparan lembut di belakang
        AffineTransform shadowTx = AffineTransform.getTranslateInstance(scale * 0.03f, scale * 0.05f);
        Shape shadowShape = shadowTx.createTransformedShape(body);
        g2.setColor(new Color(0, 0, 0, clampAlpha(baseAlpha * 1.4f)));
        g2.setStroke(new BasicStroke(scale * 0.045f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(shadowShape);

        // Glow tipis (faux-blur: gambar beberapa lapis lebih tebal & transparan)
        g2.setColor(new Color(255, 255, 255, clampAlpha(baseAlpha * 0.5f)));
        g2.setStroke(new BasicStroke(scale * 0.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(body);
        g2.setColor(new Color(255, 255, 255, clampAlpha(baseAlpha * 0.8f)));
        g2.setStroke(new BasicStroke(scale * 0.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(body);

        // Outline utama (lebih halus & proporsional)
        g2.setColor(new Color(255, 255, 255, clampAlpha(baseAlpha * 1.6f)));
        g2.setStroke(new BasicStroke(scale * 0.03f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(body);
    }

    private GeneralPath buildStickPath(float cx, float cy, float scale, StickPose pose) {
        GeneralPath path = new GeneralPath();

        float headR = scale * 0.12f;
        float neckY = cy - scale * 0.34f;
        float hipY = cy + scale * 0.10f;

        // Kepala (proporsional, sedikit lebih kecil dari versi lama)
        path.append(new Ellipse2D.Float(cx - headR, neckY - headR * 2.1f, headR * 2, headR * 2), false);

        // Badan lebih panjang & sedikit melengkung (bukan garis lurus kaku)
        path.moveTo(cx, neckY);
        path.curveTo(cx - scale * 0.01f, neckY + scale * 0.15f, cx + scale * 0.01f, hipY - scale * 0.1f, cx, hipY);

        switch (pose) {
            case HANDS_UP:
                path.moveTo(cx, neckY + scale * 0.06f);
                path.curveTo(cx - scale * 0.05f, neckY - scale * 0.05f, cx - scale * 0.12f, neckY - scale * 0.22f, cx - scale * 0.16f, neckY - scale * 0.38f);
                path.moveTo(cx, neckY + scale * 0.06f);
                path.curveTo(cx + scale * 0.05f, neckY - scale * 0.05f, cx + scale * 0.12f, neckY - scale * 0.22f, cx + scale * 0.16f, neckY - scale * 0.38f);
                break;
            case SCREAMING:
                path.moveTo(cx, neckY + scale * 0.08f);
                path.curveTo(cx - scale * 0.10f, neckY + scale * 0.02f, cx - scale * 0.20f, neckY, cx - scale * 0.26f, neckY - scale * 0.10f);
                path.moveTo(cx, neckY + scale * 0.08f);
                path.curveTo(cx + scale * 0.10f, neckY + scale * 0.02f, cx + scale * 0.20f, neckY, cx + scale * 0.26f, neckY - scale * 0.10f);
                break;
            case STANDING:
            default:
                path.moveTo(cx, neckY + scale * 0.06f);
                path.curveTo(cx - scale * 0.06f, neckY + scale * 0.14f, cx - scale * 0.14f, neckY + scale * 0.20f, cx - scale * 0.18f, neckY + scale * 0.30f);
                path.moveTo(cx, neckY + scale * 0.06f);
                path.curveTo(cx + scale * 0.06f, neckY + scale * 0.14f, cx + scale * 0.14f, neckY + scale * 0.20f, cx + scale * 0.18f, neckY + scale * 0.30f);
                break;
        }

        // Kaki (sedikit melebar, lebih natural)
        path.moveTo(cx, hipY);
        path.curveTo(cx - scale * 0.05f, hipY + scale * 0.20f, cx - scale * 0.14f, hipY + scale * 0.32f, cx - scale * 0.20f, hipY + scale * 0.46f);
        path.moveTo(cx, hipY);
        path.curveTo(cx + scale * 0.05f, hipY + scale * 0.20f, cx + scale * 0.14f, hipY + scale * 0.32f, cx + scale * 0.20f, hipY + scale * 0.46f);

        return path;
    }

    // =========================================================
    //  HEADER: Judul bertingkat + tracking + glow + garis tipis
    // =========================================================
    private JPanel buildHeaderPanel() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(new MinimalBackButton("\u2190 KEMBALI", () -> parent.showView("MENU_UTAMA")), BorderLayout.WEST);

        TitleHeader titleHeader = new TitleHeader();
        titleHeader.setPreferredSize(new Dimension(10, 140));

        JPanel selectorRow = buildCountSelector();

        wrapper.add(topRow);
        wrapper.add(titleHeader);
        wrapper.add(Box.createRigidArea(new Dimension(0, 18)));
        wrapper.add(selectorRow);
        wrapper.add(Box.createRigidArea(new Dimension(0, 16)));
        return wrapper;
    }

    /** Komponen judul kustom: kontrol penuh atas posisi piksel untuk tracking huruf, glow, dan floating bob. */
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
            float bob = (float) Math.sin(titleBobPhase) * 2.5f;

            // Baris 1: "MODE TURNAMEN" kecil, tracking lebar
            Font smallFont = new Font("Arial", Font.BOLD, 13);
            g2.setFont(smallFont);
            g2.setColor(TEXT_MUTED);
            drawTracked(g2, "MODE TURNAMEN", w / 2f, 22, 4.5f);

            // Baris 2: judul besar dengan fake bloom di belakangnya
            Font titleFont = pickTitleFont(44);
            g2.setFont(titleFont);
            float titleY = 74 + bob;
            float glowAlpha = 0.5f + 0.5f * (float) Math.sin(glowBreathPhase);
            drawBloomText(g2, "AAAAAAAAAAAA", w / 2f, titleY, titleFont, glowAlpha);

            // Garis tipis di bawah judul
            g2.setColor(new Color(255, 255, 255, 60));
            g2.setStroke(new BasicStroke(1f));
            int lineW = (int) (w * 0.22f);
            g2.drawLine(w / 2 - lineW / 2, (int) titleY + 18, w / 2 + lineW / 2, (int) titleY + 18);

            // Subjudul kecil
            Font subFont = new Font("Arial", Font.PLAIN, 14);
            g2.setFont(subFont);
            g2.setColor(TEXT_SECONDARY);
            drawTracked(g2, "SIAPKAN PARA TUKANG TERIAK", w / 2f, (int) titleY + 42, 2.2f);

            g2.dispose();
        }
    }

    /** Menggambar teks dengan tracking manual (jarak antar huruf lebih lebar), rata tengah. */
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

    /** Fake bloom: beberapa lapis teks blur-semu di belakang teks utama yang tajam. */
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
    //  SEGMENTED CONTROL: 4 / 8 / 16 (satu kesatuan, modern)
    // =========================================================
    private JPanel buildCountSelector() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);

        JLabel lbl = new JLabel("JUMLAH PEMAIN");
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setForeground(TEXT_MUTED);
        lbl.setBorder(new EmptyBorder(0, 0, 0, 14));

        segmentedControl = new SegmentedControl(new int[]{4, 8, 16}, playerCount);
        segmentedControl.setOnSelect(value -> {
            playerCount = value;
            generateFields(playerCount); // logika sama seperti versi asli
        });

        JPanel inline = new JPanel();
        inline.setOpaque(false);
        inline.setLayout(new BoxLayout(inline, BoxLayout.X_AXIS));
        inline.add(lbl);
        inline.add(segmentedControl);

        row.add(inline);
        return row;
    }

    // =========================================================
    //  CENTER: Kartu kaca berisi form nama peserta (scrollable)
    // =========================================================
    private JPanel buildCenterPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(false);

        GlassCard card = new GlassCard();
        card.setLayout(new BorderLayout());
        card.setPreferredSize(new Dimension(760, 360));

        dynamicForm = new JPanel();
        dynamicForm.setOpaque(false);
        dynamicForm.setBorder(new EmptyBorder(26, 34, 26, 34));

        JScrollPane scrollPane = new JScrollPane(dynamicForm);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUI(new ThinFadeScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));

        card.add(scrollPane, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        outer.add(card, gbc);
        return outer;
    }

    // FUNGSI INI YANG BIKIN DINAMIS -- LOGIKA SAMA PERSIS SEPERTI VERSI ASLI
    private void generateFields(int count) {
        dynamicForm.removeAll();
        nameFields.clear();

        dynamicForm.setLayout(new GridLayout(0, 2, 20, 16));

        for (int i = 1; i <= count; i++) {
            JPanel cell = new JPanel();
            cell.setOpaque(false);
            cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));

            JLabel l = new JLabel("PEMAIN " + i);
            l.setFont(new Font("Arial", Font.BOLD, 12));
            l.setForeground(TEXT_MUTED);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);

            RoundedNameField f = new RoundedNameField("Player " + i);
            f.setAlignmentX(Component.LEFT_ALIGNMENT);

            nameFields.add(f);
            cell.add(l);
            cell.add(Box.createRigidArea(new Dimension(0, 6)));
            cell.add(f);
            dynamicForm.add(cell);
        }

        dynamicForm.revalidate();
        dynamicForm.repaint();
    }

    // =========================================================
    //  FOOTER: Tombol Generate (utama, besar, ripple)
    // =========================================================
    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 0, 30, 0));

        PrimaryButton btnStart = new PrimaryButton("GENERATE BRACKET & MULAI");
        btnStart.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStart.setMaximumSize(new Dimension(420, 58));
        btnStart.setPreferredSize(new Dimension(420, 58));
        btnStart.addActionListener(e -> startTournament()); // logika sama seperti versi asli

        panel.add(btnStart);
        return panel;
    }

    // Logika sama persis seperti versi asli, tidak diubah sedikit pun
    private void startTournament() {
        GameState.reset();
        GameState.isTournamentMode = true;

        for (JTextField f : nameFields) {
            GameState.allPlayers.add(new Player(f.getText()));
        }

        GameState.tournamentRoot = TournamentManager.buildTree(new ArrayList<>(GameState.allPlayers));

        parent.showBracket();
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
    //  KOMPONEN KUSTOM: Segmented control (satu kesatuan)
    // =========================================================
    private interface OnSegmentSelect {
        void onSelect(int value);
    }

    private static class SegmentedControl extends JComponent {
        private final int[] values;
        private int selectedIndex;
        private int hoverIndex = -1;
        private OnSegmentSelect callback;
        private final float[] fillAnim;
        private Timer animTimer;

        SegmentedControl(int[] values, int selectedValue) {
            this.values = values;
            this.fillAnim = new float[values.length];
            int idx = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == selectedValue) idx = i;
            }
            this.selectedIndex = idx;
            fillAnim[selectedIndex] = 1f;

            setPreferredSize(new Dimension(56 * values.length, 38));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setOpaque(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverIndex = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int idx = indexAt(e.getX());
                    if (idx >= 0) {
                        selectedIndex = idx;
                        if (callback != null) callback.onSelect(values[idx]);
                        repaint();
                    }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = indexAt(e.getX());
                    if (idx != hoverIndex) {
                        hoverIndex = idx;
                        repaint();
                    }
                }
            });

            animTimer = new Timer(16, e -> {
                boolean stillAnimating = false;
                for (int i = 0; i < values.length; i++) {
                    float target = (i == selectedIndex) ? 1f : 0f;
                    fillAnim[i] += (target - fillAnim[i]) * 0.22f;
                    if (Math.abs(target - fillAnim[i]) > 0.01f) stillAnimating = true;
                }
                repaint();
            });
            animTimer.start();
        }

        void setOnSelect(OnSegmentSelect cb) {
            this.callback = cb;
        }

        private int indexAt(int x) {
            int segW = getWidth() / values.length;
            int idx = x / Math.max(1, segW);
            return (idx >= 0 && idx < values.length) ? idx : -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int segW = w / values.length;

            g2.setColor(new Color(255, 255, 255, 18));
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(new Color(255, 255, 255, 55));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

            for (int i = 0; i < values.length; i++) {
                int x = i * segW;
                int hoverLift = (i == hoverIndex && i != selectedIndex) ? -2 : 0;

                float fill = fillAnim[i];
                if (fill > 0.01f) {
                    g2.setColor(new Color(255, 255, 255, clampAlpha(fill)));
                    g2.fillRoundRect(x + 2, 2 + hoverLift, segW - 4, h - 4, 8, 8);
                    g2.setColor(new Color(0, 0, 0, clampAlpha(0.15f * fill)));
                    g2.fillRoundRect(x + 2, h - 6 + hoverLift, segW - 4, 3, 8, 8);
                } else if (i == hoverIndex) {
                    g2.setColor(new Color(255, 255, 255, 26));
                    g2.fillRoundRect(x + 2, 2 + hoverLift, segW - 4, h - 4, 8, 8);
                }

                if (i > 0) {
                    g2.setColor(new Color(255, 255, 255, 30));
                    g2.drawLine(x, 6, x, h - 6);
                }

                g2.setFont(new Font("Arial", Font.BOLD, 14));
                g2.setColor(fill > 0.5f ? new Color(10, 10, 10) : TEXT_SECONDARY);
                String text = String.valueOf(values[i]);
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (segW - fm.stringWidth(text)) / 2;
                int ty = h / 2 + fm.getAscent() / 2 - 2 + hoverLift;
                g2.drawString(text, tx, ty);
            }

            g2.dispose();
        }

        private int clampAlpha(float a) {
            return Math.max(0, Math.min(255, (int) (a * 255)));
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Kartu kaca (glass card) untuk form
    // =========================================================
    private static class GlassCard extends JPanel {
        GlassCard() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(CARD_BG);
            g2.fillRoundRect(0, 0, w, h, 18, 18);

            // Inner shadow tipis di tepi atas (kesan kedalaman)
            GradientPaint innerShadow = new GradientPaint(0, 0, new Color(0, 0, 0, 60), 0, 20, new Color(0, 0, 0, 0));
            g2.setPaint(innerShadow);
            g2.fillRoundRect(0, 0, w, 20, 18, 18);

            g2.setColor(CARD_BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Input nama rounded, transparan, glow fokus
    // =========================================================
    private static class RoundedNameField extends JTextField {
        private boolean focused = false;
        private boolean hover = false;

        RoundedNameField(String defaultText) {
            super(defaultText);
            setFont(new Font("Arial", Font.PLAIN, 15));
            setForeground(TEXT_PRIMARY);
            setCaretColor(Color.WHITE);
            setSelectionColor(new Color(255, 255, 255, 60));
            setOpaque(false);
            setBorder(new EmptyBorder(9, 14, 9, 14));
            setPreferredSize(new Dimension(180, 42));
            setMaximumSize(new Dimension(2000, 42));

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

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Glow lembut saat fokus
            if (focused) {
                for (int i = 3; i >= 1; i--) {
                    g2.setColor(new Color(255, 255, 255, 24 / i));
                    g2.fillRoundRect(-i * 2, -i * 2, w + i * 4, h + i * 4, 14 + i * 2, 14 + i * 2);
                }
            }

            int bgAlpha = focused ? 30 : (hover ? 22 : 14);
            g2.setColor(new Color(255, 255, 255, bgAlpha));
            g2.fillRoundRect(0, 0, w, h, 12, 12);

            // Inner shadow tipis di tepi atas
            g2.setColor(new Color(0, 0, 0, 35));
            g2.drawLine(2, 1, w - 3, 1);

            g2.setColor(focused ? new Color(255, 255, 255, 210) : new Color(255, 255, 255, 65));
            g2.setStroke(new BasicStroke(focused ? 1.6f : 1f));
            g2.drawRoundRect(1, 1, w - 2, h - 2, 12, 12);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  KOMPONEN KUSTOM: Tombol utama (Generate) + ripple
    // =========================================================
    private static class PrimaryButton extends JButton {
        private boolean hover = false;
        private float scale = 1f;
        private float breath = 0f;
        private final List<float[]> ripples = new ArrayList<>(); // {x, y, radius, alpha}
        private Timer animTimer;

        PrimaryButton(String text) {
            super(text);
            setFont(new Font("Arial", Font.BOLD, 18));
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

            // Shadow ambient di bawah tombol
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fillRoundRect(0, 6, w, h, 14, 14);

            // Glow napas + boost saat hover
            int glowAlpha = (int) (18 + pulseGlow * 14 + hoverBoost * 26);
            g2.setColor(new Color(255, 255, 255, Math.min(255, glowAlpha)));
            g2.fillRoundRect(-6, -6, w + 12, h + 12, 18, 18);

            AffineTransform old = g2.getTransform();
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale * (1f + hoverBoost * 0.02f), scale * (1f + hoverBoost * 0.02f));
            g2.translate(-w / 2.0, -h / 2.0);

            g2.setColor(hover ? Color.WHITE : new Color(235, 235, 235));
            g2.fillRoundRect(0, 0, w, h, 14, 14);

            // Ripple effect (dipotong biar tidak keluar dari tombol)
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

    // =========================================================
    //  SCROLLBAR CUSTOM: tipis, rounded, semi-transparan, fade
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
            g2.setColor(new Color(255, 255, 255, (int) (thumbAlpha * 255)));
            g2.fillRoundRect(thumbBounds.x + 1, thumbBounds.y, thumbBounds.width - 2, thumbBounds.height, 6, 6);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // Track sengaja transparan penuh (tanpa jalur terlihat, konsisten dengan minimalist UI)
        }
    }
}