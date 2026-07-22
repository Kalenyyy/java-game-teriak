package com.aduteriak;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MainMenuPanel.java
 * Halaman Main Menu game "AAAAAAAAAAAA" (voice-powered scream game).
 *
 * Latar belakang artwork sudah memuat judul besar "AAAAAAAAAAAA", pola
 * soundwave simetris yang mengerucut ke satu titik fokus di tengah, serta
 * podium/console futuristik di bagian bawah. Supaya tidak dobel dan lebih
 * "menyatu" dengan gambar, judul TIDAK lagi digambar lewat kode — layout
 * sepenuhnya disusun proporsional (berbasis persentase tinggi panel, via
 * GridBagLayout weighty) agar elemen UI jatuh tepat di titik-titik fokus
 * gambar:
 *   - subtitle kecil  -> tepat di bawah judul besar pada foto
 *   - menu tombol     -> di titik pertemuan/pusat gelombang (fokus visual)
 *   - footer          -> menempel dekat area podium/console di bawah
 *
 * Ditambahkan cinematic vignette + radial darken di belakang area menu agar
 * teks & tombol tetap kontras terbaca meski di atas background yang ramai
 * detail (gelombang, partikel, truss lighting).
 */
public class MainMenuPanel extends JPanel {

    private static final String BACKGROUND_RESOURCE_PATH = "/assets/images/main_menu.jpeg" +
            "";

    private static final Color SUBTITLE_COLOR = new Color(200, 215, 235);
    private static final Color FOOTER_COLOR = new Color(210, 220, 235);

    private static final int BUTTON_SPACING = 12;

    private BufferedImage backgroundImage;

    public MainMenuPanel(MainFrame parent) {
        setLayout(new GridBagLayout());
        setOpaque(true);

        loadBackgroundImage();
        buildProportionalLayout(parent);
    }

    // =========================================================
    //  LATAR BELAKANG (Gambar, cover-fit)
    // =========================================================
    private void loadBackgroundImage() {
        try (InputStream in = openBackgroundResourceStream()) {
            if (in != null) {
                backgroundImage = ImageIO.read(in);
            }
        } catch (IOException e) {
            backgroundImage = null;
        }
    }

    private InputStream openBackgroundResourceStream() {
        URL url = getClass().getResource(BACKGROUND_RESOURCE_PATH);
        if (url == null) {
            return null;
        }
        return getClass().getResourceAsStream(BACKGROUND_RESOURCE_PATH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        drawBackgroundCover(g2, getWidth(), getHeight());
        paintCinematicOverlay(g2, getWidth(), getHeight());

        g2.dispose();
    }

    /**
     * Menggambar backgroundImage agar selalu memenuhi seluruh panel tanpa
     * distorsi (perilaku "cover" seperti CSS background-size: cover).
     * Kelebihan area akan di-crop dari sisi kiri/kanan secara simetris,
     * sehingga elemen yang sudah center di gambar (judul, podium) akan
     * tetap center di panel pada rasio layar berapa pun.
     */
    private void drawBackgroundCover(Graphics2D g2, int panelWidth, int panelHeight) {
        if (backgroundImage == null || panelWidth <= 0 || panelHeight <= 0) {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, panelWidth, panelHeight);
            return;
        }

        int imgWidth = backgroundImage.getWidth();
        int imgHeight = backgroundImage.getHeight();

        double scale = Math.max(
                (double) panelWidth / imgWidth,
                (double) panelHeight / imgHeight
        );

        int scaledWidth = (int) Math.ceil(imgWidth * scale);
        int scaledHeight = (int) Math.ceil(imgHeight * scale);

        int drawX = (panelWidth - scaledWidth) / 2;
        int drawY = (panelHeight - scaledHeight) / 2;

        g2.drawImage(backgroundImage, drawX, drawY, scaledWidth, scaledHeight, null);
    }

    /**
     * Vignette sinematik: sedikit menggelapkan tepi atas/bawah (efek
     * letterbox halus) dan memberi radial-darken lembut tepat di belakang
     * zona menu (titik pertemuan gelombang) supaya tombol & teks tetap
     * kontras tanpa menutupi keindahan artwork.
     */
    private void paintCinematicOverlay(Graphics2D g2, int w, int h) {
        if (w <= 0 || h <= 0) return;

        g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 110), 0, h * 0.16f, new Color(0, 0, 0, 0)));
        g2.fillRect(0, 0, w, (int) (h * 0.16f));

        g2.setPaint(new GradientPaint(0, h * 0.86f, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 150)));
        g2.fillRect(0, (int) (h * 0.86f), w, (int) (h * 0.14f) + 1);

        float cx = w / 2f;
        float cy = h * 0.60f; // titik pertemuan gelombang pada artwork
        float radius = Math.max(w, h) * 0.42f;
        try {
            RadialGradientPaint radial = new RadialGradientPaint(
                    new Point2D.Float(cx, cy), radius,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0, 0, 0, 95), new Color(0, 0, 0, 0)}
            );
            g2.setPaint(radial);
            g2.fillRect(0, 0, w, h);
        } catch (IllegalArgumentException ignored) {
            // radius 0 pada kondisi ekstrem (panel belum berukuran) - abaikan frame ini
        }
    }

    // =========================================================
    //  LAYOUT PROPORSIONAL — mengikuti komposisi foto
    //  (judul besar & subjudul dari foto -> tidak digambar lagi)
    // =========================================================
    private void buildProportionalLayout(MainFrame parent) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        int row = 0;

        // Row A: ruang kosong -> menyisakan area judul besar pada foto
        addSpacer(gbc, row++, 0.34f);

        // Row B: subtitle kecil, jatuh tepat di bawah judul besar pada foto
        gbc.gridy = row++;
        gbc.weighty = 0f;
        gbc.anchor = GridBagConstraints.NORTH;
        // Row C: jarak turun mengikuti alur gelombang
        addSpacer(gbc, row++, 0.10f);

        // Row D: MENU — diposisikan tepat di titik pertemuan/fokus gelombang
        gbc.gridy = row++;
        gbc.weighty = 0.34f;
        gbc.anchor = GridBagConstraints.CENTER;
        add(buildMenuBox(parent), gbc);

        // Row E: jarak sebelum area podium/console
        addSpacer(gbc, row++, 0.10f);

        // Row F: footer, menempel dekat podium/console di bawah
        gbc.gridy = row++;
        gbc.weighty = 0f;
        gbc.anchor = GridBagConstraints.SOUTH;
        JLabel footer = new JLabel("AKTIFKAN MIKROFON  .  TERIAK SEKENCANG MUNGKIN", SwingConstants.CENTER);
        footer.setFont(new Font("Arial", Font.BOLD, 13));
        footer.setForeground(FOOTER_COLOR);
        add(footer, gbc);

        // Row G: margin bawah, menyisakan platform lingkaran pada foto
        addSpacer(gbc, row, 0.06f);
    }

    private void addSpacer(GridBagConstraints gbc, int row, float weight) {
        gbc.gridy = row;
        gbc.weighty = weight;
        gbc.anchor = GridBagConstraints.CENTER;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        add(spacer, gbc);
    }

    // =========================================================
    //  KOTAK MENU (tombol-tombol)
    // =========================================================
    private JPanel buildMenuBox(MainFrame parent) {
        JPanel menuBox = new JPanel();
        menuBox.setOpaque(false);
        menuBox.setLayout(new BoxLayout(menuBox, BoxLayout.Y_AXIS));

        List<MenuButton> buttons = new ArrayList<>();

        MenuButton btnMainSendiri = new MenuButton("MAIN SENDIRI", false);
        MenuButton btnDuel = new MenuButton("MODE DUEL", false);
        MenuButton btnTurnamen = new MenuButton("MODE TURNAMEN", false);
        MenuButton btnPeringkat = new MenuButton("PAPAN PERINGKAT", false);

        buttons.add(btnMainSendiri);
        buttons.add(btnDuel);
        buttons.add(btnTurnamen);
        buttons.add(btnPeringkat);

        for (MenuButton b : buttons) {
            b.addActionListener(e -> {
                for (MenuButton other : buttons) other.setActiveState(other == b);
            });
        }

        btnMainSendiri.addActionListener(e -> parent.showView("SOLO_INPUT"));
        btnDuel.addActionListener(e -> parent.showView("INPUT_2P"));
        btnTurnamen.addActionListener(e -> parent.showView("INPUT_TOURNAMENT"));
        btnPeringkat.addActionListener(e -> parent.showView("LEADERBOARD_SCREEN"));

        for (MenuButton b : buttons) {
            menuBox.add(b);
            menuBox.add(Box.createRigidArea(new Dimension(0, BUTTON_SPACING)));
        }

        MenuButton btnExit = new MenuButton("KELUAR", false);
        btnExit.addActionListener(e -> System.exit(0));
        menuBox.add(btnExit);

        return menuBox;
    }

    // =========================================================
    //  TOMBOL MENU KUSTOM
    //  Blade-panel cinematic hover: energy wipe, layered shadow,
    //  breathing glow, one-shot light streak, ember particles,
    //  heat distortion dan tactile click compression.
    //  Palet disetel ke nuansa icy blue-white agar senada dengan
    //  pencahayaan dingin pada artwork latar.
    //  Semua diimplementasikan murni Java2D + Swing Timer.
    // =========================================================
    static class MenuButton extends JButton {

        // ---- Palet warna (monokrom dingin, senada dengan artwork) ----
        private static final Color BG_NORMAL = new Color(15, 17, 22, 150);
        private static final Color BG_ACTIVE = new Color(238, 242, 248, 235);
        private static final Color TEXT_NORMAL = new Color(222, 228, 238);
        private static final Color TEXT_ACTIVE = new Color(12, 14, 18);
        private static final Color BORDER_SMOKE = new Color(135, 145, 160);
        private static final Color BORDER_GLOW = new Color(205, 225, 255);
        private static final Color SHADOW_CHARCOAL = new Color(6, 7, 10);
        private static final Color ENERGY_TINT = new Color(220, 235, 255);

        // ---- Durasi animasi ----
        private static final int HOVER_IN_MS = 200;
        private static final int HOVER_OUT_MS = 160;
        private static final int STREAK_MS = 240;
        private static final int CLICK_MS = 120;
        private static final int PULSE_PERIOD_MS = 1400;
        private static final int FRAME_MS = 16; // ~60 FPS
        private static final int MAX_PARTICLES = 15;
        private static final int PARTICLE_SPAWN_INTERVAL_MS = 20;

        private static final Random RANDOM = new Random();

        // ---- Cache geometri (dibangun ulang hanya saat ukuran berubah) ----
        private Shape bladeShape;
        private int cachedWidth = -1;
        private int cachedHeight = -1;

        // ---- State dasar ----
        private boolean hover = false;
        private boolean selected;

        // ---- State animasi hover (ease-out cubic, 0 = idle, 1 = full energi) ----
        private float hoverAnim = 0f;
        private float hoverAnimFrom = 0f;
        private long hoverAnimStart = 0L;
        private int hoverAnimDuration = HOVER_IN_MS;
        private boolean animatingToHover = false;
        private long hoverEnterTime = 0L;

        // ---- Light streak (satu kali per hover) ----
        private boolean streakActive = false;
        private long streakStart = 0L;

        // ---- Tactile click compression ----
        private boolean clicking = false;
        private long clickStart = 0L;
        private float clickScale = 1f;

        // ---- Particle pool (tidak ada alokasi baru saat runtime) ----
        private final Particle[] particles = new Particle[MAX_PARTICLES];
        private long lastParticleSpawn = 0L;

        private final Timer animTimer;

        MenuButton(String text, boolean selectedDefault) {
            super(text);
            this.selected = selectedDefault;

            for (int i = 0; i < particles.length; i++) {
                particles[i] = new Particle();
            }

            setFont(new Font("Arial", Font.BOLD, 18));
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setMaximumSize(new Dimension(320, 50));
            setPreferredSize(new Dimension(320, 50));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            animTimer = new Timer(FRAME_MS, e -> onTick());
            animTimer.setCoalesce(true);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    hoverEnterTime = System.currentTimeMillis();
                    beginHoverAnimation(true);
                    beginStreak();
                    ensureTimerRunning();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    beginHoverAnimation(false);
                    ensureTimerRunning();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    clicking = true;
                    clickStart = System.currentTimeMillis();
                    ensureTimerRunning();
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

        // =====================================================
        //  ANIMATION DRIVER
        // =====================================================
        private void ensureTimerRunning() {
            if (!animTimer.isRunning()) {
                animTimer.start();
            }
        }

        private void beginHoverAnimation(boolean toHovered) {
            hoverAnimFrom = hoverAnim;
            hoverAnimStart = System.currentTimeMillis();
            hoverAnimDuration = toHovered ? HOVER_IN_MS : HOVER_OUT_MS;
            animatingToHover = toHovered;
        }

        private void beginStreak() {
            streakActive = true;
            streakStart = System.currentTimeMillis();
        }

        private void onTick() {
            long now = System.currentTimeMillis();

            updateHoverProgress(now);
            updateStreak(now);
            updateClick(now);
            updateParticles(now);

            if (isIdle()) {
                animTimer.stop();
            }
            repaint();
        }

        private void updateHoverProgress(long now) {
            float target = animatingToHover ? 1f : 0f;
            float elapsed = now - hoverAnimStart;
            float t = clamp01(elapsed / (float) hoverAnimDuration);
            float eased = easeOutCubic(t);
            hoverAnim = lerp(hoverAnimFrom, target, eased);
        }

        private void updateStreak(long now) {
            if (!streakActive) return;
            float t = clamp01((now - streakStart) / (float) STREAK_MS);
            if (t >= 1f) {
                streakActive = false;
            }
        }

        private void updateClick(long now) {
            if (!clicking) {
                clickScale = 1f;
                return;
            }
            float t = clamp01((now - clickStart) / (float) CLICK_MS);
            clickScale = 1f - 0.03f * (float) Math.sin(Math.PI * t);
            if (t >= 1f) {
                clicking = false;
                clickScale = 1f;
            }
        }

        private void updateParticles(long now) {
            boolean spawning = hover && hoverAnim < 1f;
            if (spawning && now - lastParticleSpawn > PARTICLE_SPAWN_INTERVAL_MS) {
                lastParticleSpawn = now;
                spawnParticle();
            }
            for (Particle p : particles) {
                if (p.active) {
                    p.update(now);
                }
            }
        }

        private void spawnParticle() {
            float frontX = getWidth() * hoverAnim;
            float y = getHeight() * (0.25f + 0.5f * RANDOM.nextFloat());
            for (Particle p : particles) {
                if (!p.active) {
                    p.reset(frontX, y);
                    return;
                }
            }
        }

        private boolean isIdle() {
            if (hover || clicking || streakActive) return false;
            if (hoverAnim > 0.0005f) return false;
            for (Particle p : particles) {
                if (p.active) return false;
            }
            return true;
        }

        private static float clamp01(float v) {
            return v < 0f ? 0f : (v > 1f ? 1f : v);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private static float easeOutCubic(float t) {
            float f = t - 1f;
            return f * f * f + 1f;
        }

        private static int clampByte(int v) {
            return v < 0 ? 0 : (v > 255 ? 255 : v);
        }

        private static Color lerpColor(Color a, Color b, float t) {
            int r = Math.round(lerp(a.getRed(), b.getRed(), t));
            int g = Math.round(lerp(a.getGreen(), b.getGreen(), t));
            int bl = Math.round(lerp(a.getBlue(), b.getBlue(), t));
            int al = Math.round(lerp(a.getAlpha(), b.getAlpha(), t));
            return new Color(clampByte(r), clampByte(g), clampByte(bl), clampByte(al));
        }

        // =====================================================
        //  GEOMETRY — blade-like panel (bukan rounded rect biasa)
        // =====================================================
        private Shape getBladeShape() {
            int w = getWidth();
            int h = getHeight();
            if (bladeShape == null || w != cachedWidth || h != cachedHeight) {
                bladeShape = buildBladeShape(w, h);
                cachedWidth = w;
                cachedHeight = h;
            }
            return bladeShape;
        }

        private static Shape buildBladeShape(int w, int h) {
            float cut = h * 0.42f;
            Path2D.Float path = new Path2D.Float();
            path.moveTo(cut, 0);
            path.lineTo(w - cut, 0);
            path.lineTo(w, h / 2f);
            path.lineTo(w - cut, h);
            path.lineTo(cut, h);
            path.lineTo(0, h / 2f);
            path.closePath();
            return path;
        }

        // =====================================================
        //  PAINT
        // =====================================================
        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            applyClickScale(g2, w, h);

            Shape shape = getBladeShape();

            paintLayeredShadow(g2, shape);
            paintBase(g2, shape);
            paintEnergyWipe(g2, shape, w, h);
            paintHeatDistortion(g2, shape, w, h);
            paintParticles(g2);
            paintLightStreak(g2, shape, w, h);
            paintPulseGlow(g2, shape);
            paintBorder(g2, shape);
            paintTextGlow(g2, w, h);

            setForeground(lerpColor(TEXT_NORMAL, TEXT_ACTIVE, hoverAnim));
            super.paintComponent(g2);

            g2.dispose();
        }

        private void applyClickScale(Graphics2D g2, int w, int h) {
            if (clickScale != 1f) {
                g2.translate(w / 2f, h / 2f);
                g2.scale(clickScale, clickScale);
                g2.translate(-w / 2f, -h / 2f);
            }
        }

        /** Bayangan berlapis yang sedikit "bergetar" saat energi aktif, mirip depth ala Unreal UI. */
        private void paintLayeredShadow(Graphics2D g2, Shape shape) {
            if (hoverAnim <= 0.01f) {
                return;
            }
            float jitter = (float) Math.sin(System.currentTimeMillis() * 0.02) * 0.6f * hoverAnim;
            for (int i = 3; i >= 1; i--) {
                float spread = (2f + i * 2.5f) * hoverAnim;
                int alpha = clampByte((int) (26 * hoverAnim / i));
                g2.setColor(new Color(SHADOW_CHARCOAL.getRed(), SHADOW_CHARCOAL.getGreen(),
                        SHADOW_CHARCOAL.getBlue(), alpha));
                g2.translate(jitter, spread);
                g2.fill(shape);
                g2.translate(-jitter, -spread);
            }
        }

        private void paintBase(Graphics2D g2, Shape shape) {
            g2.setColor(lerpColor(BG_NORMAL, BG_ACTIVE, hoverAnim));
            g2.fill(shape);
        }

        /** Gelombang energi putih-kebiruan yang menyapu dari kiri ke kanan saat kursor masuk. */
        private void paintEnergyWipe(Graphics2D g2, Shape shape, int w, int h) {
            if (hoverAnim <= 0f || hoverAnim >= 1f) {
                return;
            }

            Shape oldClip = g2.getClip();
            g2.clip(shape);

            float frontX = w * hoverAnim;
            float bandWidth = Math.max(1f, w * 0.18f);
            int steps = 6;
            float stepWidth = bandWidth / steps + 1f;

            for (int i = 0; i < steps; i++) {
                float f = i / (float) (steps - 1);
                float x = frontX - bandWidth * f;
                int alpha = clampByte((int) (150 * (1f - f)));
                g2.setColor(new Color(ENERGY_TINT.getRed(), ENERGY_TINT.getGreen(), ENERGY_TINT.getBlue(), alpha));
                g2.fill(new Rectangle2D.Float(x, 0, stepWidth, h));
            }

            g2.setClip(oldClip);
        }

        /** Distorsi panas halus di sekitar tepi energi yang bergerak (heat-wave illusion). */
        private void paintHeatDistortion(Graphics2D g2, Shape shape, int w, int h) {
            if (hoverAnim <= 0f || hoverAnim >= 1f) {
                return;
            }

            Shape oldClip = g2.getClip();
            g2.clip(shape);
            g2.setStroke(new BasicStroke(1.2f));

            float frontX = w * hoverAnim;
            long t = System.currentTimeMillis();

            for (int i = 0; i < 3; i++) {
                float offset = i * 6f;
                float amplitude = 3f - i * 0.7f;
                Path2D.Float wave = new Path2D.Float();
                wave.moveTo(frontX - offset, 0);
                for (int y = 0; y <= h; y += 4) {
                    float phase = (t * 0.01f) + y * 0.15f + i;
                    float x = frontX - offset + (float) Math.sin(phase) * amplitude;
                    wave.lineTo(x, y);
                }
                int alpha = clampByte((int) (28 * (1f - Math.abs(hoverAnim - 0.5f) * 1.4f)));
                g2.setColor(new Color(215, 230, 255, alpha));
                g2.draw(wave);
            }

            g2.setClip(oldClip);
        }

        private void paintParticles(Graphics2D g2) {
            for (Particle p : particles) {
                if (!p.active) continue;
                float ratio = p.lifeRatio();
                int alpha = clampByte((int) (200 * ratio));
                float s = p.size * ratio;
                g2.setColor(new Color(230, 240, 255, alpha));
                g2.fill(new Ellipse2D.Float(p.x - s / 2f, p.y - s / 2f, s, s));
            }
        }

        /** Satu sapuan cahaya diagonal cepat, seperti pantulan logam, dipicu sekali per hover. */
        private void paintLightStreak(Graphics2D g2, Shape shape, int w, int h) {
            if (!streakActive) {
                return;
            }

            float t = clamp01((System.currentTimeMillis() - streakStart) / (float) STREAK_MS);
            float progress = easeOutCubic(t);

            Shape oldClip = g2.getClip();
            g2.clip(shape);

            float streakX = -h + progress * (w + h * 2f);
            int alpha = clampByte((int) (90 * (1f - t)));

            Polygon streak = new Polygon();
            streak.addPoint((int) streakX, -5);
            streak.addPoint((int) (streakX + h * 0.5f), -5);
            streak.addPoint((int) (streakX + h * 0.5f - h), h + 5);
            streak.addPoint((int) (streakX - h), h + 5);

            g2.setColor(new Color(235, 244, 255, alpha));
            g2.fill(streak);

            g2.setClip(oldClip);
        }

        /** Glow tepi yang "bernapas" pelan selama tombol tetap di-hover penuh. */
        private void paintPulseGlow(Graphics2D g2, Shape shape) {
            if (hoverAnim < 0.98f) {
                return;
            }

            long elapsed = System.currentTimeMillis() - hoverEnterTime;
            float phase = (elapsed % PULSE_PERIOD_MS) / (float) PULSE_PERIOD_MS;
            float breathe = 0.85f + 0.15f * (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));

            g2.setStroke(new BasicStroke(3.5f));
            int alpha = clampByte((int) (120 * breathe));
            g2.setColor(new Color(BORDER_GLOW.getRed(), BORDER_GLOW.getGreen(), BORDER_GLOW.getBlue(), alpha));
            g2.draw(shape);
        }

        private void paintBorder(Graphics2D g2, Shape shape) {
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(lerpColor(BORDER_SMOKE, BORDER_GLOW, hoverAnim));
            g2.draw(shape);
        }

        /** Glow halus di belakang teks agar kontras & bobot visual meningkat saat energi aktif. */
        private void paintTextGlow(Graphics2D g2, int w, int h) {
            if (hoverAnim <= 0.05f) {
                return;
            }

            String text = getText();
            if (text == null || text.isEmpty()) {
                return;
            }

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(text)) / 2;
            int textY = (h + fm.getAscent() - fm.getDescent()) / 2;

            int alpha = clampByte((int) (70 * hoverAnim));
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.drawString(text, textX - 1, textY);
            g2.drawString(text, textX + 1, textY);
            g2.drawString(text, textX, textY - 1);
            g2.drawString(text, textX, textY + 1);
        }

        // =====================================================
        //  PARTICLE POOL (reused, no per-frame allocation)
        // =====================================================
        private static class Particle {
            boolean active;
            float x, y, vx, vy, size;
            long bornAt;
            long lifeMs;

            void reset(float startX, float startY) {
                this.x = startX;
                this.y = startY;
                this.vx = 1.2f + RANDOM.nextFloat() * 1.8f;
                this.vy = (RANDOM.nextFloat() - 0.5f) * 1.2f;
                this.size = 2f + RANDOM.nextFloat() * 2.5f;
                this.bornAt = System.currentTimeMillis();
                this.lifeMs = 180 + RANDOM.nextInt(120);
                this.active = true;
            }

            void update(long now) {
                x += vx;
                y += vy;
                if (now - bornAt > lifeMs) {
                    active = false;
                }
            }

            float lifeRatio() {
                float t = (System.currentTimeMillis() - bornAt) / (float) lifeMs;
                return clamp01(1f - t);
            }
        }
    }
}