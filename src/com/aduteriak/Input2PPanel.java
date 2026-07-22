package com.aduteriak;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Input2PPanel.java
 *
 * Didesain ulang agar menjadi perpanjangan visual langsung dari
 * {@link SoloInputPanel}: kartu kaca hologram mengambang di atas
 * background (gambar atau gradient fallback), lengkap dengan bracket
 * sudut ala viewfinder, breathing border, scanline yang menyapu, dan
 * wipe-reveal saat kartu pertama muncul.
 *
 * Perbedaan dari Solo hanyalah pada ISI kartu: dua input nama pemain
 * (identik persis dalam ukuran, styling, spacing, animasi, efek glass,
 * border, tipografi, dan placeholder) dipisahkan oleh badge "VS", alih-alih
 * satu input saja. Palet warna TETAP monokrom (tidak ada cyan/magenta
 * seperti pada draft awal Solo) mengikuti tema utama game — namun kini
 * dengan gradasi & lapisan cahaya yang lebih kaya agar terasa lebih premium.
 *
 * Ganti path {@link #BACKGROUND_RESOURCE_PATH} untuk mengganti gambar
 * latar tanpa menyentuh kode lain.
 */
public class Input2PPanel extends JPanel {

    private static final String BACKGROUND_RESOURCE_PATH = "/assets/images/solo_input.jpeg";

    // ---- Palet monokrom terpusat (senada dengan seluruh game, tidak ada warna baru) ----
    private static final Color C_SOFT_WHITE   = new Color(240, 240, 242);
    private static final Color C_LIGHT_GRAY   = new Color(196, 196, 201);
    private static final Color C_MID_GRAY     = new Color(128, 128, 133);
    private static final Color C_DIM_GRAY     = new Color(80, 80, 85);
    private static final Color C_DARK_GRAY    = new Color(34, 34, 37);
    private static final Color C_HAIRLINE     = new Color(255, 255, 255, 90);
    private static final Color GLASS_FILL_TOP    = new Color(30, 34, 40, 150);
    private static final Color GLASS_FILL_BOTTOM = new Color(10, 11, 14, 190);
    private static final Color GLASS_FILL_FOCUS_TOP    = new Color(40, 40, 44, 225);
    private static final Color GLASS_FILL_FOCUS_BOTTOM = new Color(14, 14, 16, 235);
    private static final Color TEXT_TITLE = new Color(245, 245, 247);
    private static final Color TEXT_LABEL = new Color(200, 200, 204);
    private static final Color TEXT_FIELD = new Color(238, 238, 240);

    // ---- Scrim tipis hanya untuk area pojok kiri-atas (tombol kembali) ----
    private static final float SCRIM_TOP_HEIGHT_RATIO = 0.16f;
    private static final int SCRIM_TOP_ALPHA = 140;

    // ---- Margin kanan agar kartu menempel di sisi kanan panel ----
    private static final int CARD_RIGHT_MARGIN = 110;
    // ---- Margin atas agar kartu berada lebih ke atas panel ----
    private static final int CARD_TOP_MARGIN = 140;

    private BufferedImage backgroundImage;

    private float entranceAlpha = 1f;
    private Timer entranceTimer;

    private MinimalBackButton backButton;
    private GlassCard glassCard;

    public Input2PPanel(MainFrame parent) {
        setLayout(null);
        setOpaque(true);

        loadBackgroundImage();

        backButton = new MinimalBackButton("KEMBALI", () -> parent.showView("MENU_UTAMA"));
        glassCard = buildGlassCard(parent);

        add(backButton);
        add(glassCard);

        startEntranceAnimation();
    }

    // =========================================================
    //  LAYOUT PROPORSIONAL — kartu diposisikan mengikuti persentase
    //  ukuran panel, sama seperti SoloInputPanel, agar tetap presisi
    //  di berbagai ukuran window. Duel butuh ruang vertikal lebih besar
    //  (dua input + badge VS) sehingga kartu ditempatkan di tengah,
    //  lalu digeser lebih ke kanan lewat CARD_EXTRA_RIGHT_OFFSET.
    // =========================================================
    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Dimension backPref = backButton.getPreferredSize();
        backButton.setBounds(16, 16, backPref.width, backPref.height);

        int cardW = Math.max(360, Math.round(w * 0.38f));
        int cardH = Math.max(520, Math.round(h * 0.45f));
        int cardX = w - cardW - CARD_RIGHT_MARGIN;
        int cardY = CARD_TOP_MARGIN;

        cardX = Math.max(12, Math.min(cardX, w - cardW - 12));
        cardY = Math.max(12, Math.min(cardY, h - cardH - 12));

        glassCard.setBounds(cardX, cardY, cardW, cardH);
    }

    // =========================================================
    //  LATAR BELAKANG (Gambar cover-fit, fallback gradient + stickman)
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

        // Overlay fade-in dari hitam pekat -> transparan saat panel baru dibuka
        if (entranceAlpha > 0f) {
            Graphics2D gf = (Graphics2D) g.create();
            gf.setColor(new Color(0, 0, 0, (int) (entranceAlpha * 255)));
            gf.fillRect(0, 0, w, h);
            gf.dispose();
        }
    }

    /** Cover-fit: gambar mengisi seluruh panel, aspect ratio terjaga, tanpa stretching/pixelation. */
    private void drawBackgroundCover(Graphics2D g2, int panelWidth, int panelHeight) {
        if (backgroundImage == null || panelWidth <= 0 || panelHeight <= 0) {
            drawFallbackBackground(g2, panelWidth, panelHeight);
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

    /** Fallback saat gambar belum tersedia: gradient monokrom + siluet stickman (desain lama tetap ada). */
    private void drawFallbackBackground(Graphics2D g2, int w, int h) {
        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(200, 200, 200),
                0, h, new Color(15, 15, 15)
        );
        g2.setPaint(gradient);
        g2.fillRect(0, 0, w, h);
        g2.setPaint(null);
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
    //  FONT — teknis / futuristik, dengan fallback aman (sama seperti Solo)
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

    private static Font pickTitleFont(int size) {
        String[] candidates = {"Impact", "Arial Black", "Haettenschweiler", "Arial"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        List<String> availableList = Arrays.asList(available);
        for (String name : candidates) {
            if (availableList.contains(name)) {
                int style = name.equals("Arial") ? Font.BOLD : Font.PLAIN;
                return new Font(name, style, size);
            }
        }
        return new Font("SansSerif", Font.BOLD, size);
    }

    // =========================================================
    //  KARTU HOLOGRAM — berisi judul, dua input pemain identik
    //  dipisah badge VS, dan tombol MULAI. Struktur & efek sama
    //  persis dengan HoloCard milik SoloInputPanel.
    // =========================================================
    private GlassCard buildGlassCard(MainFrame parent) {
        GlassCard card = new GlassCard();
        card.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        int row = 0;

        gbc.gridy = row++;
        gbc.insets = new Insets(30, 26, 4, 26);
        MonoLabel title = new MonoLabel("MASUKKAN NAMA", TEXT_TITLE, C_HAIRLINE, true, 3.5f);
        title.setFont(pickTitleFont(26));
        card.add(title, gbc);

        gbc.gridy = row++;
        gbc.insets = new Insets(6, 26, 22, 26);
        MonoLabel sub = new MonoLabel("PERSIAPAN MODE DUEL", C_MID_GRAY, C_MID_GRAY, false, 2.2f);
        sub.setFont(pickTechFont(12, Font.PLAIN));
        card.add(sub, gbc);

        // ---- Input Pemain 1 & Pemain 2: dibangun lewat helper yang sama persis,
        //      sehingga ukuran, styling, spacing, animasi, glass, border, tipografi,
        //      dan placeholder-nya identik antara kedua field. ----
        DuelField field1 = addPlayerRow(card, gbc, row, "PLAYER 1", "Pemain 1");
        row += 2;
        this.putClientProperty("field1", field1);

        gbc.gridy = row++;
        gbc.insets = new Insets(4, 26, 4, 26);
        VsBadge vsBadge = new VsBadge();
        card.add(vsBadge, gbc);

        DuelField field2 = addPlayerRow(card, gbc, row, "PLAYER 2", "Pemain 2");
        row += 2;
        this.putClientProperty("field2", field2);

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
        DuelButton btnStart = new DuelButton("MULAI TERIAK");
        btnStart.setPreferredSize(new Dimension(100, 54));
        btnStart.addActionListener(e -> {
            DuelField f1 = (DuelField) getClientProperty("field1");
            DuelField f2 = (DuelField) getClientProperty("field2");

            String name1 = f1.getText().trim();
            String name2 = f2.getText().trim();
            if (name1.isEmpty() || name1.equals(f1.getPlaceholder())) name1 = "Pemain 1";
            if (name2.isEmpty() || name2.equals(f2.getPlaceholder())) name2 = "Pemain 2";

            GameState.reset();

            Player p1 = new Player(name1);
            Player p2 = new Player(name2);

            GameState.allPlayers.add(p1);
            GameState.allPlayers.add(p2);
            GameState.turnQueue.add(p1);
            GameState.turnQueue.add(p2);

            parent.startGame();
        });
        card.add(btnStart, gbc);

        return card;
    }

    /**
     * Helper terpusat untuk membangun satu baris input pemain (label + field).
     * Dipanggil dua kali (Pemain 1 & Pemain 2) agar kedua field dijamin identik
     * persis tanpa duplikasi kode: sama lebar, sama styling, sama spacing, sama
     * animasi, sama efek glass, sama border, sama tipografi, sama placeholder style.
     */
    private DuelField addPlayerRow(JPanel card, GridBagConstraints gbc, int startRow, String labelText, String placeholder) {
        int row = startRow;

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 26, 6, 26);
        MonoLabel fieldLabel = new MonoLabel(labelText, TEXT_LABEL, TEXT_LABEL, false, 2.5f);
        fieldLabel.setFont(pickTechFont(12, Font.BOLD));
        card.add(fieldLabel, gbc);

        gbc.gridy = row;
        gbc.insets = new Insets(0, 26, 18, 26);
        DuelField field = new DuelField(placeholder);
        field.setFont(pickTechFont(16, Font.BOLD));
        card.add(field, gbc);

        return field;
    }

    // =========================================================
    //  KARTU KACA HOLOGRAM (monokrom) — identik strukturnya dengan
    //  HoloCard milik SoloInputPanel: glass fill gradien gelap
    //  translusen, border ber-"nafas" dua-lapis, header chevron +
    //  garis gradien, bracket 4 sudut ala viewfinder yang ikut
    //  berdenyut, scanline dua-lapis yang menyapu turun, serta
    //  wipe-reveal + scale-in halus saat kartu pertama muncul.
    // =========================================================
    private static class GlassCard extends JPanel {
        private static final int FRAME_MS = 16;

        private float scanPhase = 0f;
        private float breathePhase = 0f;
        private float wipeProgress = 0f;
        private final Timer fxTimer;

        GlassCard() {
            setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 0, 0));

            fxTimer = new Timer(FRAME_MS, e -> {
                scanPhase += 0.0045f;
                if (scanPhase > 1.15f) scanPhase = -0.15f;
                breathePhase += 0.028f;
                if (wipeProgress < 1f) {
                    // ease-out agar reveal terasa lebih halus dibanding linear
                    wipeProgress = Math.min(1f, wipeProgress + (1f - wipeProgress) * 0.12f + 0.01f);
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

            // scale-in tipis dibarengi wipe agar kemunculan kartu terasa lebih "hidup"
            float scale = 0.965f + 0.035f * wipeProgress;
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale, scale);
            g2.translate(-w / 2.0, -h / 2.0);

            // one-shot wipe reveal: kartu "terbuka" dari kiri ke kanan saat muncul
            if (wipeProgress < 0.999f) {
                g2.setClip(0, 0, Math.round(w * wipeProgress), h);
            }

            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 16, 16);

            // bayangan lembut, sedikit lebih dalam untuk kesan melayang
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fill(new RoundRectangle2D.Float(4, 8, w, h, 16, 16));

            // isi kaca dengan gradien vertikal halus, bukan warna solid datar
            GradientPaint glassGradient = new GradientPaint(
                    0, 0, GLASS_FILL_TOP,
                    0, h, GLASS_FILL_BOTTOM
            );
            g2.setPaint(glassGradient);
            g2.fill(shape);

            // sapuan cahaya lembut di sisi kiri-atas, kesan permukaan kaca
            Shape oldClipGlass = g2.getClip();
            g2.clip(shape);
            GradientPaint sheen = new GradientPaint(
                    0, 0, new Color(255, 255, 255, 30),
                    w * 0.55f, h * 0.35f, new Color(255, 255, 255, 0)
            );
            g2.setPaint(sheen);
            g2.fill(shape);
            g2.setClip(oldClipGlass);

            // scanline yang menyapu turun terus-menerus (monokrom, dua-lapis)
            paintScanline(g2, shape, w, h);

            // border ber-nafas ganda: lapisan luar tipis + lapisan dalam terang
            float breathe = 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(breathePhase));
            g2.setStroke(new BasicStroke(2.6f));
            g2.setColor(new Color(255, 255, 255, (int) (55 * breathe)));
            g2.draw(shape);
            g2.setStroke(new BasicStroke(1.3f));
            g2.setColor(new Color(255, 255, 255, (int) (150 * breathe)));
            g2.draw(shape);

            // header: chevron + garis gradien
            paintHeaderDecor(g2, w, breathe);

            // aksen sudut ala viewfinder, ikut berdenyut mengikuti breathe
            paintCornerBrackets(g2, w, h, breathe);

            g2.dispose();

            super.paintComponent(g);
        }

        private void paintScanline(Graphics2D g2, Shape clipShape, int w, int h) {
            Shape oldClip = g2.getClip();
            g2.clip(clipShape);

            float y = h * scanPhase;
            float bandH = Math.max(12f, h * 0.07f);

            GradientPaint band = new GradientPaint(
                    0, y - bandH / 2f, new Color(255, 255, 255, 0),
                    0, y, new Color(255, 255, 255, 55)
            );
            g2.setPaint(band);
            g2.fillRect(0, (int) (y - bandH / 2f), w, (int) (bandH / 2f));

            GradientPaint band2 = new GradientPaint(
                    0, y, new Color(255, 255, 255, 55),
                    0, y + bandH / 2f, new Color(255, 255, 255, 0)
            );
            g2.setPaint(band2);
            g2.fillRect(0, (int) y, w, (int) (bandH / 2f));

            // garis inti scanline yang lebih tajam di tengah band, kesan HUD
            g2.setColor(new Color(255, 255, 255, 90));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(0, (int) y, w, (int) y);

            g2.setClip(oldClip);
        }

        private void paintHeaderDecor(Graphics2D g2, int w, float breathe) {
            g2.setColor(new Color(255, 255, 255, (int) (110 + 60 * breathe)));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawString("\u00BB\u00BB\u00BB", 14, 20);

            g2.setStroke(new BasicStroke(1f));
            GradientPaint headerLine = new GradientPaint(
                    70, 14, new Color(255, 255, 255, 90),
                    w - 24, 14, new Color(255, 255, 255, 10)
            );
            g2.setPaint(headerLine);
            g2.drawLine(70, 14, w - 24, 14);
        }

        private void paintCornerBrackets(Graphics2D g2, int w, int h, float breathe) {
            int len = 16;
            int pad = 6;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(255, 255, 255, (int) (150 + 90 * breathe)));

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
    //  LABEL TEKNIS — letter-spacing manual + opsi bracket "[ ]",
    //  identik strukturnya dengan TechLabel milik SoloInputPanel,
    //  hanya direkolorisasi ke monokrom.
    // =========================================================
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
    //  INPUT FIELD HOLOGRAM (monokrom) — dipakai identik untuk
    //  Pemain 1 & Pemain 2 lewat addPlayerRow(). Struktur sama
    //  persis dengan HoloTextField milik SoloInputPanel (glass fill
    //  gradien, breathing glow saat fokus, aksen bar kiri, scanline
    //  halus, border), hanya warnanya monokrom.
    // =========================================================
    private static class DuelField extends JTextField {
        private final String placeholder;
        private boolean focused = false;
        private float glowPhase = 0f;
        private final Timer glowTimer;

        DuelField(String placeholder) {
            super(placeholder);
            this.placeholder = placeholder;
            setForeground(TEXT_FIELD);
            setCaretColor(C_SOFT_WHITE);
            setSelectionColor(new Color(255, 255, 255, 60));
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

        String getPlaceholder() {
            return placeholder;
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
                    g2.setColor(new Color(255, 255, 255, alpha));
                    g2.fillRoundRect(-i * 2, -i * 2, w + i * 4, h + i * 4, 10 + i * 2, 10 + i * 2);
                }
            }

            GradientPaint fieldGradient = focused
                    ? new GradientPaint(0, 0, GLASS_FILL_FOCUS_TOP, 0, h, GLASS_FILL_FOCUS_BOTTOM)
                    : new GradientPaint(0, 0, GLASS_FILL_TOP, 0, h, GLASS_FILL_BOTTOM);
            g2.setPaint(fieldGradient);
            g2.fillRoundRect(0, 0, w, h, 8, 8);

            // tekstur scanline halus di dalam field (kesan hologram)
            g2.setColor(new Color(255, 255, 255, 16));
            for (int ly = 6; ly < h; ly += 6) {
                g2.drawLine(4, ly, w - 4, ly);
            }

            // aksen bar tegak di kiri, ciri khas input HUD
            g2.setColor(focused ? C_SOFT_WHITE : C_DIM_GRAY);
            g2.fillRect(0, 4, 3, h - 8);

            g2.setStroke(new BasicStroke(focused ? 1.8f : 1.2f));
            g2.setColor(focused ? new Color(255, 255, 255, 230) : new Color(255, 255, 255, 100));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  BADGE "VS" BERDENYUT — dipertahankan dari desain awal Duel,
    //  ditempatkan di antara dua input agar tetap terasa seperti
    //  panel duel, bukan sekadar salinan Solo. Kini dengan cincin
    //  ganda ber-gradien dan glow yang lebih dramatis.
    // =========================================================
    private static class VsBadge extends JComponent {
        private float pulse = 0f;
        private float rotation = 0f;
        private final Timer pulseTimer;

        VsBadge() {
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setPreferredSize(new Dimension(48, 48));
            setOpaque(false);

            pulseTimer = new Timer(30, e -> {
                pulse += 0.06f;
                rotation += 0.9f;
                if (rotation > 360f) rotation -= 360f;
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
            int baseRadius = Math.min(w, h) / 2 - 8;

            float glowT = (float) (0.5 + 0.5 * Math.sin(pulse));
            int glowRadius = baseRadius + (int) (glowT * 6);

            // glow luar
            g2.setColor(new Color(255, 255, 255, (int) (26 + glowT * 40)));
            g2.fillOval(cx - glowRadius - 5, cy - glowRadius - 5, (glowRadius + 5) * 2, (glowRadius + 5) * 2);

            // isi bulatan dengan gradien radial agar terasa berdimensi
            RadialGradientPaint fill = new RadialGradientPaint(
                    new Point(cx, cy - baseRadius / 3), baseRadius * 1.4f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(38, 38, 42), new Color(10, 10, 11)}
            );
            g2.setPaint(fill);
            g2.fill(new Ellipse2D.Float(cx - baseRadius, cy - baseRadius, baseRadius * 2, baseRadius * 2));

            // cincin luar berputar putus-putus, aksen dinamis
            Graphics2D gRing = (Graphics2D) g2.create();
            gRing.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{3f, 5f}, 0));
            gRing.setColor(new Color(255, 255, 255, (int) (70 + 60 * glowT)));
            gRing.rotate(Math.toRadians(rotation), cx, cy);
            gRing.drawOval(cx - baseRadius - 4, cy - baseRadius - 4, (baseRadius + 4) * 2, (baseRadius + 4) * 2);
            gRing.dispose();

            // cincin dalam tegas
            g2.setColor(new Color(255, 255, 255, 120));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(cx - baseRadius, cy - baseRadius, baseRadius * 2, baseRadius * 2);

            g2.setColor(C_SOFT_WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            String text = "VS";
            g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2 - 2);

            g2.dispose();
        }
    }

    // =========================================================
    //  TOMBOL AKSI HOLOGRAM (monokrom) — glass + energy sweep +
    //  breathing glow, identik strukturnya dengan HoloButton milik
    //  SoloInputPanel, kini dengan gradien isi & glow lebih hidup.
    // =========================================================
    private static class DuelButton extends JButton {
        private boolean hover = false;
        private float pulsePhase = 0f;
        private float hoverAnim = 0f;
        private long streakStart = -1L;
        private final Timer fxTimer;

        DuelButton(String text) {
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
            int glowAlpha = (int) (55 * breathe + 65 * hoverAnim);
            g2.setColor(new Color(255, 255, 255, glowAlpha));
            g2.fill(new RoundRectangle2D.Float(-4, -4, w + 8, h + 8, 14, 14));

            GradientPaint fill = hover
                    ? new GradientPaint(0, 0, new Color(40, 40, 44, 235), 0, h, new Color(18, 18, 20, 235))
                    : new GradientPaint(0, 0, new Color(28, 28, 30, 210), 0, h, new Color(14, 14, 16, 210));
            g2.setPaint(fill);
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
                g2.setColor(new Color(245, 245, 247, Math.max(0, alpha)));
                g2.fill(streak);
                g2.setClip(oldClip);
            }

            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255, 255, 255, (int) (150 + 70 * hoverAnim)));
            g2.draw(shape);

            g2.dispose();

            setForeground(TEXT_TITLE);
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  TOMBOL KEMBALI "\u2190 KEMBALI" — konsisten di semua halaman
    //  (identik dengan versi di SoloInputPanel & GamePanel).
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