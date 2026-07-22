package com.aduteriak;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * SoloInputPanel.java
 *
 * Didesain ulang mengikuti komposisi foto referensi: sebuah panel kaca
 * hologram "SOLO MODE REGISTRATION" yang mengambang di sisi KANAN layar,
 * lengkap dengan bar input bergaya HUD, bracket judul "[ ... ]", aksen
 * sudut viewfinder, dan tombol aksi ber-glow.
 *
 * Area "USERNAME" pada foto sengaja DITUTUP oleh kartu kaca kode ini, lalu
 * input field asli (HoloTextField) diletakkan tepat di posisi tersebut —
 * sehingga yang tampil ke pengguna adalah kontrol sungguhan, bukan gambar
 * placeholder pada foto.
 *
 * Posisi kartu dihitung berbasis PERSENTASE ukuran panel (bukan pixel
 * tetap) via override doLayout(), supaya tetap presisi menempel di zona
 * yang sama pada foto di berbagai ukuran window.
 */
public class SoloInputPanel extends JPanel {

    private static final String BACKGROUND_RESOURCE_PATH = "/assets/images/solo_input.jpeg";

    // ---- Palet warna hologram (cyan dingin + aksen magenta lembut, senada foto) ----
    private static final Color HOLO_CYAN = new Color(150, 215, 255);
    private static final Color HOLO_CYAN_SOFT = new Color(150, 215, 255, 90);
    private static final Color HOLO_CYAN_DIM = new Color(120, 170, 200, 130);
    private static final Color HOLO_MAGENTA = new Color(210, 150, 230);
    private static final Color GLASS_FILL = new Color(8, 14, 22, 150);
    private static final Color GLASS_FILL_FOCUS = new Color(10, 22, 34, 205);
    private static final Color TEXT_TITLE = new Color(235, 245, 255);
    private static final Color TEXT_LABEL = new Color(190, 210, 225);
    private static final Color TEXT_FIELD = new Color(230, 245, 255);

    // ---- Scrim tipis hanya untuk area pojok kiri-atas (tombol kembali) ----
    private static final float SCRIM_TOP_HEIGHT_RATIO = 0.16f;
    private static final int SCRIM_TOP_ALPHA = 140;

    private BufferedImage backgroundImage;

    private float entranceAlpha = 1f;
    private Timer entranceTimer;

    private MinimalBackButton backButton;
    private HoloCard holoCard;

    public SoloInputPanel(MainFrame parent) {
        setLayout(null);
        setOpaque(true);

        loadBackgroundImage();

        backButton = new MinimalBackButton("KEMBALI", () -> parent.showView("MENU_UTAMA"));
        holoCard = buildHoloCard(parent);

        add(backButton);
        add(holoCard);

        startEntranceAnimation();
    }

    // =========================================================
    //  LAYOUT PROPORSIONAL — kartu ditempatkan mengikuti zona
    //  panel hologram pada foto (sisi kanan layar)
    // =========================================================
    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Dimension backPref = backButton.getPreferredSize();
        backButton.setBounds(16, 16, backPref.width, backPref.height);

        int cardW = Math.max(340, Math.round(w * 0.38f));
        int cardH = Math.max(380, Math.round(h * 0.45f));
        int cardX = Math.round(w * 0.50f);
        int cardY = Math.round(h * 0.15f);

        if (cardX + cardW > w - 12) {
            cardX = Math.max(12, w - cardW - 12);
        }
        if (cardY + cardH > h - 12) {
            cardY = Math.max(12, h - cardH - 12);
        }

        holoCard.setBounds(cardX, cardY, cardW, cardH);
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

        int w = getWidth();
        int h = getHeight();

        drawBackgroundCover(g2, w, h);
        drawTopScrim(g2, w, h);

        g2.dispose();

        if (entranceAlpha > 0f) {
            Graphics2D gf = (Graphics2D) g.create();
            gf.setColor(new Color(0, 0, 0, (int) (entranceAlpha * 255)));
            gf.fillRect(0, 0, w, h);
            gf.dispose();
        }
    }

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

    private void drawTopScrim(Graphics2D g2, int w, int h) {
        int topHeight = Math.round(h * SCRIM_TOP_HEIGHT_RATIO);
        Paint topScrim = new GradientPaint(
                0, 0, new Color(0, 0, 0, SCRIM_TOP_ALPHA),
                0, topHeight, new Color(0, 0, 0, 0)
        );
        g2.setPaint(topScrim);
        g2.fillRect(0, 0, w, topHeight);
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
    //  FONT — teknis / futuristik, dengan fallback aman
    // =========================================================
    private static Font pickTechFont(int size, int style) {
        String[] candidates = {"Orbitron", "Rajdhani", "Exo 2", "Eurostile", "Consolas", "Segoe UI Semibold", "Arial"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        List<String> availableList = Arrays.asList(available);
        for (String name : candidates) {
            if (availableList.contains(name)) {
                return new Font(name, style, size);
            }
        }
        return new Font(Font.MONOSPACED, style, size);
    }

    // =========================================================
    //  KARTU HOLOGRAM — menggantikan zona "USERNAME" pada foto
    // =========================================================
    private HoloCard buildHoloCard(MainFrame parent) {
        HoloCard card = new HoloCard();
        card.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        int row = 0;

        gbc.gridy = row++;
        gbc.insets = new Insets(30, 26, 4, 26);
        TechLabel title = new TechLabel("MASUKKAN NAMAMU", TEXT_TITLE, HOLO_CYAN, true, 3.5f);
        title.setFont(pickTechFont(22, Font.BOLD));
        card.add(title, gbc);

        gbc.gridy = row++;
        gbc.insets = new Insets(6, 26, 22, 26);
        TechLabel sub = new TechLabel("SOLO MODE REGISTRATION", HOLO_CYAN_DIM, HOLO_CYAN_DIM, false, 2.2f);
        sub.setFont(pickTechFont(12, Font.PLAIN));
        card.add(sub, gbc);

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 26, 6, 26);
        TechLabel fieldLabel = new TechLabel("NAMA PEMAIN", TEXT_LABEL, TEXT_LABEL, false, 2.5f);
        fieldLabel.setFont(pickTechFont(12, Font.BOLD));
        card.add(fieldLabel, gbc);

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 26, 22, 26);
        HoloTextField field = new HoloTextField("Pemain");
        field.setFont(pickTechFont(16, Font.BOLD));
        card.add(field, gbc);
        this.putClientProperty("field", field);

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 26, 26, 26);
        TechLabel modeRow = new TechLabel("SELECT MODE : SOLO", HOLO_CYAN_DIM, HOLO_CYAN_DIM, false, 2.2f);
        modeRow.setFont(pickTechFont(12, Font.PLAIN));
        card.add(modeRow, gbc);

        gbc.gridy = row++;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel flexSpacer = new JPanel();
        flexSpacer.setOpaque(false);
        card.add(flexSpacer, gbc);

        gbc.gridy = row;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 26, 30, 26);
        HoloButton btnStart = new HoloButton("MULAI TERIAK");
        btnStart.setPreferredSize(new Dimension(100, 54));
        btnStart.addActionListener(e -> {
            HoloTextField f = (HoloTextField) getClientProperty("field");
            String name = f.getText().trim();
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
        card.add(btnStart, gbc);

        return card;
    }

    // =========================================================
    //  KARTU KACA HOLOGRAM
    //  - background glass gelap translusen
    //  - border ber-"nafas" (breathing glow)
    //  - garis header + chevron ala HUD
    //  - aksen bracket di 4 sudut (viewfinder style)
    //  - scanline yang menyapu turun terus-menerus
    //  - one-shot wipe-reveal saat kartu pertama muncul
    // =========================================================
    private static class HoloCard extends JPanel {
        private static final int FRAME_MS = 16;

        private float scanPhase = 0f;
        private float breathePhase = 0f;
        private float wipeProgress = 0f;
        private final Timer fxTimer;

        HoloCard() {
            setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 0, 0));

            fxTimer = new Timer(FRAME_MS, e -> {
                scanPhase += 0.0045f;
                if (scanPhase > 1.15f) scanPhase = -0.15f;
                breathePhase += 0.03f;
                if (wipeProgress < 1f) {
                    wipeProgress = Math.min(1f, wipeProgress + 0.045f);
                }
                repaint();
            });
            fxTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // one-shot wipe reveal: kartu "terbuka" dari kiri ke kanan saat muncul
            if (wipeProgress < 1f) {
                g2.setClip(0, 0, Math.round(w * wipeProgress), h);
            }

            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);

            // bayangan lembut
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fill(new RoundRectangle2D.Float(3, 6, w, h, 14, 14));

            // isi kaca gelap
            g2.setColor(GLASS_FILL);
            g2.fill(shape);

            // scanline yang menyapu turun terus-menerus
            paintScanline(g2, shape, w, h);

            // border ber-nafas
            float breathe = 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(breathePhase));
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(),
                    (int) (140 * breathe)));
            g2.draw(shape);

            // header: chevron + garis tipis
            paintHeaderDecor(g2, w);

            // aksen sudut ala viewfinder
            paintCornerBrackets(g2, w, h);

            g2.dispose();

            super.paintComponent(g);
        }

        private void paintScanline(Graphics2D g2, Shape clipShape, int w, int h) {
            Shape oldClip = g2.getClip();
            g2.clip(clipShape);

            float y = h * scanPhase;
            float bandH = Math.max(10f, h * 0.10f);
            GradientPaint band = new GradientPaint(
                    0, y - bandH / 2f, new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 0),
                    0, y, new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 55)
            );
            g2.setPaint(band);
            g2.fillRect(0, (int) (y - bandH / 2f), w, (int) (bandH / 2f));

            GradientPaint band2 = new GradientPaint(
                    0, y, new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 55),
                    0, y + bandH / 2f, new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 0)
            );
            g2.setPaint(band2);
            g2.fillRect(0, (int) y, w, (int) (bandH / 2f));

            g2.setClip(oldClip);
        }

        private void paintHeaderDecor(Graphics2D g2, int w) {
            g2.setColor(HOLO_CYAN_SOFT);
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawString("\u00BB\u00BB\u00BB", 14, 20);

            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 90));
            g2.drawLine(70, 14, w - 24, 14);
        }

        private void paintCornerBrackets(Graphics2D g2, int w, int h) {
            int len = 16;
            int pad = 6;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 200));

            // kiri-atas
            g2.drawLine(pad, pad, pad + len, pad);
            g2.drawLine(pad, pad, pad, pad + len);
            // kanan-atas
            g2.drawLine(w - pad, pad, w - pad - len, pad);
            g2.drawLine(w - pad, pad, w - pad, pad + len);
            // kiri-bawah
            g2.drawLine(pad, h - pad, pad + len, h - pad);
            g2.drawLine(pad, h - pad, pad, h - pad - len);
            // kanan-bawah
            g2.drawLine(w - pad, h - pad, w - pad - len, h - pad);
            g2.drawLine(w - pad, h - pad, w - pad, h - pad - len);
        }
    }

    // =========================================================
    //  LABEL TEKNIS — letter-spacing manual + opsi bracket "[ ]"
    //  agar terasa senada dengan tipografi HUD pada foto
    // =========================================================
    private static class TechLabel extends JLabel {
        private final Color glowColor;
        private final boolean brackets;
        private final float letterSpacing;

        TechLabel(String text, Color fg, Color glowColor, boolean brackets, float letterSpacing) {
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
            g2.setFont(getFont());

            FontMetrics fm = g2.getFontMetrics();
            String text = getText();

            String left = brackets ? "[  " : "";
            String right = brackets ? "  ]" : "";
            float bracketW = brackets ? fm.stringWidth(left) + fm.stringWidth(right) : 0f;
            float textW = trackedWidth(fm, text, letterSpacing);
            float totalW = textW + bracketW;

            float x = (getWidth() - totalW) / 2f;
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
                x += fm.stringWidth(ch) + letterSpacing;
            }

            if (brackets) {
                g2.setColor(glowColor);
                g2.drawString(right, x - letterSpacing, y);
            }

            g2.dispose();
        }

        private static float trackedWidth(FontMetrics fm, String text, float spacing) {
            float w = 0f;
            for (int i = 0; i < text.length(); i++) {
                w += fm.stringWidth(String.valueOf(text.charAt(i))) + spacing;
            }
            return Math.max(0f, w - spacing);
        }
    }

    // =========================================================
    //  INPUT FIELD HOLOGRAM — menggantikan bar "USERNAME" pada foto
    // =========================================================
    private static class HoloTextField extends JTextField {
        private boolean focused = false;
        private float glowPhase = 0f;
        private final Timer glowTimer;

        HoloTextField(String defaultText) {
            super(defaultText);
            setForeground(TEXT_FIELD);
            setCaretColor(HOLO_CYAN);
            setSelectionColor(new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 70));
            setHorizontalAlignment(JTextField.CENTER);
            setOpaque(false);
            setBorder(new EmptyBorder(12, 16, 12, 16));
            setPreferredSize(new Dimension(10, 50));

            glowTimer = new Timer(16, e -> {
                glowPhase += 0.04f;
                repaint();
            });
            glowTimer.start();

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

            if (focused) {
                float breathe = 0.65f + 0.35f * (float) (0.5 + 0.5 * Math.sin(glowPhase));
                for (int i = 3; i >= 1; i--) {
                    int alpha = (int) (34 * breathe / i);
                    g2.setColor(new Color(HOLO_MAGENTA.getRed(), HOLO_MAGENTA.getGreen(), HOLO_MAGENTA.getBlue(), alpha));
                    g2.fillRoundRect(-i * 2, -i * 2, w + i * 4, h + i * 4, 10 + i * 2, 10 + i * 2);
                }
            }

            g2.setColor(focused ? GLASS_FILL_FOCUS : GLASS_FILL);
            g2.fillRoundRect(0, 0, w, h, 8, 8);

            // tekstur scanline halus di dalam field (kesan hologram)
            g2.setColor(new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 18));
            for (int ly = 6; ly < h; ly += 6) {
                g2.drawLine(4, ly, w - 4, ly);
            }

            // aksen bar tegak di kiri, ciri khas input HUD
            g2.setColor(focused ? HOLO_MAGENTA : HOLO_CYAN_DIM);
            g2.fillRect(0, 4, 3, h - 8);

            g2.setStroke(new BasicStroke(focused ? 1.8f : 1.2f));
            g2.setColor(focused
                    ? new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 230)
                    : new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), 110));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  TOMBOL AKSI HOLOGRAM — glass + energy sweep + breathing glow
    // =========================================================
    private static class HoloButton extends JButton {
        private static final Random RANDOM = new Random();

        private boolean hover = false;
        private float pulsePhase = 0f;
        private float hoverAnim = 0f;
        private long streakStart = -1L;
        private final Timer fxTimer;

        HoloButton(String text) {
            super("\u25B8  " + text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setForeground(TEXT_TITLE);

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
                float target = hover ? 1f : 0f;
                hoverAnim += (target - hoverAnim) * 0.18f;
                repaint();
            });
            fxTimer.start();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(260, 52);
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 10, 10);

            float breathe = 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(pulsePhase));
            int glowAlpha = (int) (60 * breathe + 60 * hoverAnim);
            g2.setColor(new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(), glowAlpha));
            g2.fill(new RoundRectangle2D.Float(-4, -4, w + 8, h + 8, 14, 14));

            g2.setColor(hover ? new Color(10, 26, 36, 235) : new Color(8, 16, 24, 210));
            g2.fill(shape);

            if (hover && streakStart >= 0) {
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
                g2.setColor(new Color(235, 245, 255, Math.max(0, alpha)));
                g2.fill(streak);
                g2.setClip(oldClip);
            }

            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(HOLO_CYAN.getRed(), HOLO_CYAN.getGreen(), HOLO_CYAN.getBlue(),
                    (int) (160 + 60 * hoverAnim)));
            g2.draw(shape);

            g2.dispose();

            setForeground(TEXT_TITLE);
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  TOMBOL KEMBALI "\u2190 KEMBALI" — konsisten di semua halaman
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

            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(new RoundRectangle2D.Float(cardX + 1, cardY + 3, cardW, cardH, arc, arc));

            g2.setColor(new Color(22, 22, 24, (int) (215 + 20 * hoverT)));
            g2.fill(shape);

            g2.setColor(new Color(255, 255, 255, (int) (90 + 90 * hoverT)));
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(shape);

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