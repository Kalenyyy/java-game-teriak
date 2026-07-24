package com.aduteriak;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * InputTournamentPanel.java
 *
 * Tournament Input Panel (4 / 8 / 16 pemain) — mengikuti bahasa visual
 * SoloInputPanel & Input2PPanel: kartu kaca hologram mengambang di atas
 * background artwork (BUKAN digambar oleh Java), dengan bracket sudut ala
 * viewfinder, breathing border, scanline yang menyapu, wipe-reveal, dan
 * kini bertema "cyberpunk command terminal" ber-aksen cyan.
 *
 * Kartu ditempatkan di sisi KANAN layar (~65-70% lebar), sehingga sisi
 * kiri (tempat karakter utama pada artwork) tetap kosong / tidak tertutup UI.
 *
 * Struktur konten kartu (dashboard, dipisah HUD divider tipis):
 *   HEADER   -> status ONLINE, versi, judul, subjudul, scanline
 *   SECTION 01 -> pilih jumlah peserta (4 / 8 / 16) via segmented control
 *   DASHBOARD  -> kiri: Player Registration (grid kartu pemain ber-badge)
 *                 kanan: Tournament Info panel + System Status panel
 *   FOOTER   -> tombol INITIALIZE TOURNAMENT
 *
 * Catatan: live bracket preview SENGAJA tidak ditampilkan di panel ini
 * karena bracket sudah punya halaman tersendiri (parent.showBracket()).
 *
 * Ganti path {@link #BACKGROUND_RESOURCE_PATH} untuk mengganti gambar
 * latar tanpa menyentuh kode lain.
 */
public class InputTournamentPanel extends JPanel {

    private static final String BACKGROUND_RESOURCE_PATH = "/assets/images/solo_input.jpeg";

    // =========================================================
    //  PALET WARNA — hologram cyan, sesuai brief (tanpa hijau/oranye/ungu/merah)
    // =========================================================
    private static final Color C_WHITE        = new Color(240, 248, 252);
    private static final Color C_SOFT_BLUE    = new Color(176, 208, 226);
    private static final Color C_MUTED_BLUE   = new Color(120, 148, 168);
    private static final Color C_CYAN         = new Color(140, 226, 255);
    private static final Color C_CYAN_BRIGHT  = new Color(200, 244, 255);
    private static final Color C_CYAN_DIM     = new Color(90, 160, 190);

    private static final Color GLASS_FILL_TOP      = new Color(10, 24, 34, 150);
    private static final Color GLASS_FILL_BOTTOM   = new Color(3, 9, 14, 205);
    private static final Color GLASS_FILL_FOCUS_TOP    = new Color(16, 40, 52, 225);
    private static final Color GLASS_FILL_FOCUS_BOTTOM = new Color(4, 12, 18, 235);

    private static final int SCRIM_TOP_ALPHA = 130;
    private static final float SCRIM_TOP_HEIGHT_RATIO = 0.14f;

    // ---- Posisi kartu: sisi kanan, ~68% lebar layar ----
    private static final float CARD_WIDTH_RATIO = 0.68f;
    private static final int CARD_MIN_WIDTH = 700;
    private static final int CARD_RIGHT_MARGIN = 42;
    private static final int CARD_TOP_MARGIN = 84;
    private static final int CARD_BOTTOM_MARGIN = 36;

    private BufferedImage backgroundImage;

    private float entranceAlpha = 1f;
    private Timer entranceTimer;

    // =========================================================
    //  STATE LOGIKA ASLI (tidak diubah)
    // =========================================================
    private JPanel dynamicForm;
    private ArrayList<JTextField> nameFields;
    private MainFrame parent;
    private int playerCount = 4; // Default

    private SegmentedControl segmentedControl;
    private MinimalBackButton backButton;
    private GlassCard glassCard;
    private JLabel infoPlayersValue;
    private JLabel infoRoundsValue;

    public InputTournamentPanel(MainFrame parent) {
        this.parent = parent;
        this.nameFields = new ArrayList<>();

        setLayout(null);
        setOpaque(true);

        loadBackgroundImage();

        backButton = new MinimalBackButton("KEMBALI", () -> parent.showView("MENU_UTAMA"));
        glassCard = buildGlassCard();

        add(backButton);
        add(glassCard);

        startEntranceAnimation();

        // Inisialisasi awal dengan 4 field (logika sama seperti versi asli)
        generateFields(4);
    }

    // =========================================================
    //  LAYOUT PROPORSIONAL — kartu selalu di sisi KANAN, sisi kiri
    //  dibiarkan kosong agar karakter pada artwork tetap terlihat.
    // =========================================================
    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Dimension backPref = backButton.getPreferredSize();
        backButton.setBounds(16, 16, backPref.width, backPref.height);

        int cardW = Math.max(CARD_MIN_WIDTH, Math.round(w * CARD_WIDTH_RATIO));
        cardW = Math.min(cardW, w - 24);
        int cardX = w - cardW - CARD_RIGHT_MARGIN;
        int cardY = CARD_TOP_MARGIN;
        int cardH = h - CARD_TOP_MARGIN - CARD_BOTTOM_MARGIN;

        cardX = Math.max(12, cardX);
        cardH = Math.max(360, cardH);

        glassCard.setBounds(cardX, cardY, cardW, cardH);
    }

    // =========================================================
    //  LATAR BELAKANG — HANYA gambar artwork (tanpa gradient/stickman
    //  buatan Java). Jika gambar belum tersedia, fallback minimal
    //  berupa warna gelap solid agar UI tetap terbaca saat development.
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

    /** Cover-fit: gambar mengisi seluruh panel, aspect ratio terjaga, tanpa stretching/pixelation. */
    private void drawBackgroundCover(Graphics2D g2, int panelWidth, int panelHeight) {
        if (backgroundImage == null || panelWidth <= 0 || panelHeight <= 0) {
            // Fallback minimal, hanya untuk development — bukan bagian dari desain final.
            g2.setColor(new Color(6, 9, 12));
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
    //  FONT
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

    /** Utility bersama: konversi alpha 0f-1f ke 0-255, dipakai semua komponen kustom (menghindari duplikasi). */
    private static int clampAlpha(float a) {
        return Math.max(0, Math.min(255, (int) (a * 255)));
    }

    // =========================================================
    //  KARTU HOLOGRAM UTAMA
    // =========================================================
    private GlassCard buildGlassCard() {
        GlassCard card = new GlassCard();
        card.setLayout(new BorderLayout());

        card.add(buildHeaderDecor(), BorderLayout.NORTH);
        card.add(buildScrollableBody(), BorderLayout.CENTER);
        card.add(buildFooter(), BorderLayout.SOUTH);

        return card;
    }

    // ---- Header: meta row (status + versi), judul + subjudul + garis HUD ----
    private JComponent buildHeaderDecor() {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setBorder(new EmptyBorder(20, 30, 6, 30));

        JPanel metaRow = new JPanel(new BorderLayout());
        metaRow.setOpaque(false);
        metaRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        metaRow.setMaximumSize(new Dimension(4000, 20));

        OnlineStatusPill onlinePill = new OnlineStatusPill();
        metaRow.add(onlinePill, BorderLayout.WEST);

        JLabel version = new JLabel("v1.0.0");
        version.setFont(pickTechFont(10, Font.PLAIN));
        version.setForeground(C_MUTED_BLUE);
        metaRow.add(version, BorderLayout.EAST);

        MonoLabel title = new MonoLabel("TOURNAMENT REGISTRATION", C_WHITE, C_CYAN, true, 3f);
        title.setFont(pickTitleFont(25));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        MonoLabel sub = new MonoLabel("CYBERSTRIKE TOURNAMENT SYSTEM", C_CYAN_DIM, C_CYAN_DIM, false, 2.4f);
        sub.setFont(pickTechFont(11, Font.PLAIN));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        HudDivider divider = new HudDivider();
        divider.setAlignmentX(Component.CENTER_ALIGNMENT);
        divider.setMaximumSize(new Dimension(4000, 18));

        wrap.add(metaRow);
        wrap.add(Box.createRigidArea(new Dimension(0, 12)));
        wrap.add(title);
        wrap.add(Box.createRigidArea(new Dimension(0, 5)));
        wrap.add(sub);
        wrap.add(Box.createRigidArea(new Dimension(0, 14)));
        wrap.add(divider);

        return wrap;
    }

    /** Pil kecil "ONLINE" dengan titik cyan yang berkedip pelan — indikator status sistem di header. */
    private static class OnlineStatusPill extends JComponent {
        private float blinkPhase = 0f;
        private final Timer timer;

        OnlineStatusPill() {
            setOpaque(false);
            setPreferredSize(new Dimension(78, 18));
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
            g2.setColor(new Color(140, 226, 255, (int) (30 * pulse)));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
            g2.setColor(new Color(160, 230, 255, 70));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);

            int dotR = 5;
            int dotX = 8;
            int dotY = getHeight() / 2 - dotR / 2;
            g2.setColor(new Color(140, 226, 255, (int) (120 * pulse)));
            g2.fillOval(dotX - 2, dotY - 2, dotR + 4, dotR + 4);
            g2.setColor(C_CYAN_BRIGHT);
            g2.fillOval(dotX, dotY, dotR, dotR);

            g2.setFont(pickTechFont(10, Font.BOLD));
            g2.setColor(C_SOFT_BLUE);
            g2.drawString("ONLINE", dotX + dotR + 6, getHeight() / 2 + 4);

            g2.dispose();
        }
    }

    /** Garis dekorasi HUD tipis dengan node kecil di tengah, aksen cyan. */
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

            GradientPaint left = new GradientPaint(0, 0, new Color(140, 226, 255, 0), w / 2f, 0, new Color(140, 226, 255, 130));
            g2.setPaint(left);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(0, cy, w / 2 - 8, cy);

            GradientPaint right = new GradientPaint(w / 2f, 0, new Color(140, 226, 255, 130), w, 0, new Color(140, 226, 255, 0));
            g2.setPaint(right);
            g2.drawLine(w / 2 + 8, cy, w, cy);

            g2.setColor(C_CYAN);
            g2.fillOval(w / 2 - 3, cy - 3, 6, 6);
            g2.setColor(new Color(140, 226, 255, 70));
            g2.fillOval(w / 2 - 6, cy - 6, 12, 12);

            g2.dispose();
        }
    }

    // ---- Body dashboard: [Tournament Size] -> divider -> [Player Registration | Info + Status] ----
    private JScrollPane buildScrollableBody() {
        JPanel content = new ScrollableFillPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(12, 30, 10, 30));

        content.add(buildSectionLabel("01", "TOURNAMENT SIZE"));
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(buildCountSelectorRow());
        content.add(Box.createRigidArea(new Dimension(0, 16)));

        HudDivider dividerTop = new HudDivider();
        dividerTop.setAlignmentX(Component.LEFT_ALIGNMENT);
        dividerTop.setMaximumSize(new Dimension(4000, 14));
        content.add(dividerTop);
        content.add(Box.createRigidArea(new Dimension(0, 16)));

        content.add(buildDashboardRow());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUI(new ThinFadeScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        return scrollPane;
    }

    /**
     * Panel konten yang otomatis melebar mengisi TINGGI PENUH viewport
     * ketika kartu punya ruang lebih (mis. 4 pemain), sehingga tidak ada
     * strip kosong di bawah konten — tapi tetap scroll normal begitu
     * konten memang lebih tinggi dari viewport (mis. 16 pemain).
     */
    private static class ScrollableFillPanel extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 80;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            Container viewport = getParent();
            return viewport instanceof JViewport && viewport.getHeight() > getPreferredSize().height;
        }
    }

    /** Baris dashboard: kolom kiri fleksibel (Player Registration), pemisah vertikal, kolom kanan tetap (Info + Status). */
    private JComponent buildDashboardRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(4000, 4000));

        GridBagConstraints gbcLeft = new GridBagConstraints();
        gbcLeft.gridx = 0;
        gbcLeft.gridy = 0;
        gbcLeft.weightx = 1.0;
        gbcLeft.weighty = 1.0;
        gbcLeft.fill = GridBagConstraints.BOTH;
        gbcLeft.anchor = GridBagConstraints.NORTH;
        gbcLeft.insets = new Insets(0, 0, 0, 20);
        row.add(buildPlayerRegistrationModule(), gbcLeft);

        GridBagConstraints gbcDivider = new GridBagConstraints();
        gbcDivider.gridx = 1;
        gbcDivider.gridy = 0;
        gbcDivider.weightx = 0;
        gbcDivider.weighty = 0;
        gbcDivider.fill = GridBagConstraints.VERTICAL;
        gbcDivider.insets = new Insets(2, 0, 2, 20);
        row.add(new VerticalHudDivider(), gbcDivider);

        GridBagConstraints gbcRight = new GridBagConstraints();
        gbcRight.gridx = 2;
        gbcRight.gridy = 0;
        gbcRight.weightx = 0;
        gbcRight.weighty = 0;
        gbcRight.fill = GridBagConstraints.HORIZONTAL;
        gbcRight.anchor = GridBagConstraints.NORTH;

        JPanel rightCol = new JPanel();
        rightCol.setOpaque(false);
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setPreferredSize(new Dimension(258, 10));
        rightCol.setMaximumSize(new Dimension(258, 4000));
        rightCol.add(buildTournamentInfoPanel());
        rightCol.add(Box.createRigidArea(new Dimension(0, 18)));
        rightCol.add(buildSystemStatusPanel());
        row.add(rightCol, gbcRight);

        return row;
    }

    /** Garis pemisah vertikal tipis cyan antar kolom dashboard, sama bahasanya dengan HudDivider horizontal. */
    private static class VerticalHudDivider extends JComponent {
        VerticalHudDivider() {
            setOpaque(false);
            setPreferredSize(new Dimension(14, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            int cx = getWidth() / 2;

            GradientPaint top = new GradientPaint(0, 0, new Color(140, 226, 255, 0), 0, h / 2f, new Color(140, 226, 255, 90));
            g2.setPaint(top);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(cx, 0, cx, h / 2 - 6);

            GradientPaint bottom = new GradientPaint(0, h / 2f, new Color(140, 226, 255, 90), 0, h, new Color(140, 226, 255, 0));
            g2.setPaint(bottom);
            g2.drawLine(cx, h / 2 + 6, cx, h);

            g2.setColor(new Color(140, 226, 255, 130));
            g2.fillOval(cx - 2, h / 2 - 2, 4, 4);

            g2.dispose();
        }
    }

    private JComponent buildSectionLabel(String tag, String title) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(4000, 26));

        JLabel tagLbl = new JLabel(tag);
        tagLbl.setFont(pickTechFont(11, Font.BOLD));
        tagLbl.setForeground(new Color(4, 12, 16));
        tagLbl.setOpaque(true);
        tagLbl.setBackground(C_CYAN);
        tagLbl.setBorder(new EmptyBorder(3, 8, 3, 8));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(pickTechFont(13, Font.BOLD));
        titleLbl.setForeground(C_WHITE);
        titleLbl.setBorder(new EmptyBorder(0, 10, 0, 0));

        row.add(tagLbl);
        row.add(titleLbl);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    // ---- Tournament Size: segmented control 4 / 8 / 16 ----
    private JComponent buildCountSelectorRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(4000, 60));

        segmentedControl = new SegmentedControl(new int[]{4, 8, 16}, playerCount);
        segmentedControl.setOnSelect(value -> {
            playerCount = value;
            generateFields(playerCount); // logika sama seperti versi asli
            updateInfoPanel();
        });

        row.add(segmentedControl);
        return row;
    }

    // ---- Player Registration module: judul + subjudul + grid kartu pemain ----
    private JComponent buildPlayerRegistrationModule() {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));

        MonoLabel moduleTitle = new MonoLabel("PLAYER REGISTRATION", C_WHITE, C_CYAN, false, 2f);
        moduleTitle.setFont(pickTechFont(15, Font.BOLD));
        moduleTitle.setHorizontalAlignment(SwingConstants.LEFT);
        moduleTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        moduleTitle.setPreferredSize(new Dimension(10, 20));
        moduleTitle.setMaximumSize(new Dimension(4000, 20));

        MonoLabel moduleSub = new MonoLabel("REGISTER ALL PARTICIPANTS", C_MUTED_BLUE, C_MUTED_BLUE, false, 1.8f);
        moduleSub.setFont(pickTechFont(10, Font.PLAIN));
        moduleSub.setHorizontalAlignment(SwingConstants.LEFT);
        moduleSub.setAlignmentX(Component.LEFT_ALIGNMENT);
        moduleSub.setPreferredSize(new Dimension(10, 16));
        moduleSub.setMaximumSize(new Dimension(4000, 16));

        wrap.add(moduleTitle);
        wrap.add(Box.createRigidArea(new Dimension(0, 2)));
        wrap.add(moduleSub);
        wrap.add(Box.createRigidArea(new Dimension(0, 12)));

        dynamicForm = new JPanel();
        dynamicForm.setMaximumSize(new Dimension(4000, 4000));
        dynamicForm.setOpaque(false);
        dynamicForm.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(dynamicForm);

        return wrap;
    }

    // FUNGSI INI YANG BIKIN DINAMIS -- LOGIKA INTI SAMA PERSIS SEPERTI VERSI ASLI
    // (hanya tampilan tiap slot yang diperkaya jadi "kartu pemain" ber-badge)
    private void generateFields(int count) {
        dynamicForm.removeAll();
        nameFields.clear();

        dynamicForm.setLayout(new GridLayout(0, 2, 16, 16));

        for (int i = 1; i <= count; i++) {
            PlayerCard card = new PlayerCard(i, "Pemain " + i);
            nameFields.add(card.getField());
            dynamicForm.add(card);
        }

        dynamicForm.revalidate();
        dynamicForm.repaint();
    }

    // ---- Tournament Info Panel: PLAYERS / ROUNDS / FORMAT / STATUS ----
    private JComponent buildTournamentInfoPanel() {
        MiniPanel panel = new MiniPanel("TOURNAMENT INFO");

        JPanel rows = new JPanel(new GridLayout(4, 1, 0, 10));
        rows.setOpaque(false);

        infoPlayersValue = new JLabel(String.valueOf(playerCount));
        infoRoundsValue = new JLabel(String.valueOf(roundsFor(playerCount)));

        rows.add(buildInfoRow("PLAYERS", infoPlayersValue));
        rows.add(buildInfoRow("ROUNDS", infoRoundsValue));
        rows.add(buildInfoRow("FORMAT", new JLabel("SINGLE ELIM")));
        rows.add(buildInfoRow("STATUS", new JLabel("READY")));

        panel.add(rows, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildInfoRow(String label, JLabel value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JLabel l = new JLabel(label);
        l.setFont(pickTechFont(10, Font.BOLD));
        l.setForeground(C_MUTED_BLUE);

        value.setFont(pickTechFont(12, Font.BOLD));
        value.setForeground(C_CYAN_BRIGHT);
        value.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(l, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void updateInfoPanel() {
        if (infoPlayersValue != null) infoPlayersValue.setText(String.valueOf(playerCount));
        if (infoRoundsValue != null) infoRoundsValue.setText(String.valueOf(roundsFor(playerCount)));
    }

    private int roundsFor(int count) {
        int rounds = 0;
        int n = count;
        while (n > 1) {
            n /= 2;
            rounds++;
        }
        return rounds;
    }

    // ---- System Status Panel: diagnostik kecil, tiap baris ber-indikator cyan ----
    private JComponent buildSystemStatusPanel() {
        MiniPanel panel = new MiniPanel("SYSTEM STATUS");

        JPanel rows = new JPanel(new GridLayout(4, 1, 0, 10));
        rows.setOpaque(false);
        rows.add(new StatusRow("SYSTEM", "ONLINE"));
        rows.add(new StatusRow("MEMORY", "READY"));
        rows.add(new StatusRow("MATCH ENGINE", "READY"));
        rows.add(new StatusRow("INPUT VALIDATION", "READY"));

        panel.add(rows, BorderLayout.CENTER);
        return panel;
    }

    /** Baris status kecil dengan titik indikator cyan yang berkedip halus. */
    private static class StatusRow extends JComponent {
        private final String label;
        private final String status;
        private float phase;
        private final Timer timer;

        StatusRow(String label, String status) {
            this.label = label;
            this.status = status;
            this.phase = (float) (Math.random() * 6.28);
            setOpaque(false);
            setPreferredSize(new Dimension(10, 16));
            timer = new Timer(30, e -> {
                phase += 0.05f;
                repaint();
            });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int h = getHeight();
            float pulse = 0.5f + 0.5f * (float) (0.5 + 0.5 * Math.sin(phase));

            g2.setColor(new Color(140, 226, 255, (int) (120 * pulse)));
            g2.fillOval(-1, h / 2 - 5, 8, 8);
            g2.setColor(C_CYAN_BRIGHT);
            g2.fillOval(1, h / 2 - 3, 4, 4);

            g2.setFont(pickTechFont(10, Font.BOLD));
            g2.setColor(C_MUTED_BLUE);
            g2.drawString(label, 14, h / 2 + 4);

            g2.setFont(pickTechFont(10, Font.PLAIN));
            g2.setColor(C_SOFT_BLUE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(status, getWidth() - fm.stringWidth(status), h / 2 + 4);

            g2.dispose();
        }
    }

    /** Panel kaca kecil generik dengan judul ber-aksen bar cyan — dipakai untuk Info & Status. */
    private static class MiniPanel extends JPanel {
        private final String title;

        MiniPanel(String title) {
            this.title = title;
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(34, 14, 14, 14));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 12, 12);

            GradientPaint fill = new GradientPaint(0, 0, GLASS_FILL_TOP, 0, h, GLASS_FILL_BOTTOM);
            g2.setPaint(fill);
            g2.fill(shape);

            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(160, 230, 255, 80));
            g2.draw(shape);

            g2.setColor(C_CYAN);
            g2.fillRect(14, 14, 3, 12);

            g2.setFont(pickTechFont(11, Font.BOLD));
            g2.setColor(C_WHITE);
            g2.drawString(title, 24, 24);

            g2.setColor(new Color(160, 230, 255, 45));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(14, 32, w - 14, 32);

            g2.dispose();
        }
    }

    // =========================================================
    //  PLAYER CARD — badge nomor, ikon kecil, input, garis penghubung,
    //  border HUD, dan hover glow. Membungkus HoloNameField (logika
    //  nameFields tetap memakai JTextField apa adanya).
    // =========================================================
    private static class PlayerCard extends JPanel {
        private final HoloNameField field;
        private boolean hover = false;
        private float hoverAnim = 0f;
        private final Timer animTimer;

        PlayerCard(int number, String placeholder) {
            setOpaque(false);
            setLayout(new BorderLayout(0, 8));
            setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel headerRow = new JPanel(new BorderLayout());
            headerRow.setOpaque(false);

            JPanel badgeAndLabel = new JPanel();
            badgeAndLabel.setOpaque(false);
            badgeAndLabel.setLayout(new BoxLayout(badgeAndLabel, BoxLayout.X_AXIS));
            badgeAndLabel.add(new BadgeIcon(number));
            badgeAndLabel.add(Box.createRigidArea(new Dimension(8, 0)));

            MonoLabel label = new MonoLabel("PLAYER " + number, C_SOFT_BLUE, C_SOFT_BLUE, false, 1.8f);
            label.setFont(pickTechFont(11, Font.BOLD));
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setPreferredSize(new Dimension(80, 18));
            badgeAndLabel.add(label);

            headerRow.add(badgeAndLabel, BorderLayout.WEST);

            field = new HoloNameField(placeholder);
            field.setFont(pickTechFont(14, Font.BOLD));

            add(headerRow, BorderLayout.NORTH);
            add(field, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                }
            });

            animTimer = new Timer(16, e -> {
                float target = hover ? 1f : 0f;
                hoverAnim += (target - hoverAnim) * 0.18f;
                repaint();
            });
            animTimer.start();
        }

        JTextField getField() {
            return field;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 10, 10);

            if (hoverAnim > 0.01f) {
                g2.setColor(new Color(140, 226, 255, (int) (28 * hoverAnim)));
                g2.fill(new RoundRectangle2D.Float(-3, -3, w + 6, h + 6, 13, 13));
            }

            g2.setColor(new Color(140, 226, 255, 10));
            g2.fill(shape);

            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(160, 230, 255, (int) (40 + 90 * hoverAnim)));
            g2.draw(shape);

            // garis penghubung tipis di bawah header, kesan HUD terminal
            g2.setColor(new Color(160, 230, 255, 55));
            g2.drawLine(38, 30, w - 12, 30);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Badge lingkaran kecil berisi nomor pemain, dengan cincin tipis cyan. */
    private static class BadgeIcon extends JComponent {
        private final int number;

        BadgeIcon(int number) {
            this.number = number;
            setPreferredSize(new Dimension(26, 26));
            setMaximumSize(new Dimension(26, 26));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int d = 24;
            RadialGradientPaint fill = new RadialGradientPaint(
                    new Point2D.Float(d / 2f, d / 2.6f), d,
                    new float[]{0f, 1f},
                    new Color[]{new Color(28, 58, 72), new Color(6, 16, 22)}
            );
            g2.setPaint(fill);
            g2.fill(new Ellipse2D.Float(0, 0, d, d));

            g2.setColor(new Color(160, 230, 255, 150));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Float(0, 0, d, d));

            g2.setFont(pickTechFont(11, Font.BOLD));
            g2.setColor(C_CYAN_BRIGHT);
            FontMetrics fm = g2.getFontMetrics();
            String txt = String.valueOf(number);
            g2.drawString(txt, (d - fm.stringWidth(txt)) / 2f, d / 2f + fm.getAscent() / 2f - 2);

            g2.dispose();
        }
    }

    // ---- Footer: tombol INITIALIZE TOURNAMENT ----
    private JComponent buildFooter() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(0, 30, 24, 30));

        HudDivider divider = new HudDivider();
        divider.setAlignmentX(Component.CENTER_ALIGNMENT);
        divider.setMaximumSize(new Dimension(4000, 18));
        panel.add(divider);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));

        // --- TARO DI SINI: LABEL ERROR UNTUK TURNAMEN ---
        MonoLabel errorLabel = new MonoLabel("", new Color(255, 100, 100), new Color(255, 100, 100), false, 1.2f);
        errorLabel.setFont(pickTechFont(11, Font.ITALIC));
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        this.putClientProperty("errorLabel", errorLabel); // Simpan agar bisa diakses di startTournament
        panel.add(errorLabel);
        // -----------------------------------------------

        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        StartButton btnStart = new StartButton("\u25B8  INITIALIZE TOURNAMENT");
        btnStart.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStart.setMaximumSize(new Dimension(4000, 56));
        btnStart.setPreferredSize(new Dimension(100, 56));
        btnStart.addActionListener(e -> startTournament());

        panel.add(btnStart);
        return panel;
    }

    // Logika sama persis seperti versi asli, tidak diubah sedikit pun
    private void startTournament() {
        MonoLabel errorLabel = (MonoLabel) getClientProperty("errorLabel");

        // 1. Validasi: Cek apakah ada yang kosong atau masih nama default
        for (int i = 0; i < nameFields.size(); i++) {
            String name = nameFields.get(i).getText().trim();
            // Cek kosong atau masih "Pemain 1", "Pemain 2", dst
            if (name.isEmpty() || name.equalsIgnoreCase("Pemain " + (i + 1))) {
                errorLabel.setText("! ERROR: SEMUA NAMA PEMAIN HARUS DIISI");
                return; // Berhenti
            }
        }

        // 2. Validasi: Cek apakah ada nama yang kembar (duplikat)
        for (int i = 0; i < nameFields.size(); i++) {
            for (int j = i + 1; j < nameFields.size(); j++) {
                if (nameFields.get(i).getText().trim().equalsIgnoreCase(nameFields.get(j).getText().trim())) {
                    errorLabel.setText("! ERROR: TERDETEKSI NAMA GANDA (PEMAIN " + (i+1) + " & " + (j+1) + ")");
                    return; // Berhenti
                }
            }
        }

        // Jika semua OK, lanjut ke Bracket
        errorLabel.setText("");
        GameState.reset();
        GameState.isTournamentMode = true;

        for (JTextField f : nameFields) {
            GameState.allPlayers.add(new Player(f.getText().trim()));
        }

        GameState.tournamentRoot = TournamentManager.buildTree(new ArrayList<>(GameState.allPlayers));
        parent.showBracket();
    }

    // =========================================================
    //  KARTU KACA HOLOGRAM (cyan) — glass fill gradien, border
    //  ber-nafas dua-lapis, header chevron, bracket sudut viewfinder,
    //  scanline yang menyapu turun, wipe-reveal + scale-in halus.
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
                scanPhase += 0.0042f;
                if (scanPhase > 1.15f) scanPhase = -0.15f;
                breathePhase += 0.026f;
                if (wipeProgress < 1f) {
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

            float scale = 0.972f + 0.028f * wipeProgress;
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale, scale);
            g2.translate(-w / 2.0, -h / 2.0);

            if (wipeProgress < 0.999f) {
                g2.setClip(0, 0, Math.round(w * wipeProgress), h);
            }

            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 18, 18);

            g2.setColor(new Color(0, 0, 0, 150));
            g2.fill(new RoundRectangle2D.Float(4, 8, w, h, 18, 18));

            GradientPaint glassGradient = new GradientPaint(
                    0, 0, GLASS_FILL_TOP,
                    0, h, GLASS_FILL_BOTTOM
            );
            g2.setPaint(glassGradient);
            g2.fill(shape);

            Shape oldClipGlass = g2.getClip();
            g2.clip(shape);
            GradientPaint sheen = new GradientPaint(
                    0, 0, new Color(160, 230, 255, 26),
                    w * 0.55f, h * 0.35f, new Color(160, 230, 255, 0)
            );
            g2.setPaint(sheen);
            g2.fill(shape);

            paintHudGrid(g2, w, h);
            g2.setClip(oldClipGlass);

            paintScanline(g2, shape, w, h);

            float breathe = 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(breathePhase));
            g2.setStroke(new BasicStroke(2.6f));
            g2.setColor(new Color(140, 226, 255, (int) (45 * breathe)));
            g2.draw(shape);
            g2.setStroke(new BasicStroke(1.3f));
            g2.setColor(new Color(180, 236, 255, (int) (170 * breathe)));
            g2.draw(shape);

            paintHeaderChevron(g2, breathe);
            paintCornerBrackets(g2, w, h, breathe);

            g2.dispose();

            super.paintComponent(g);
        }

        private void paintHudGrid(Graphics2D g2, int w, int h) {
            g2.setColor(new Color(160, 230, 255, 9));
            int step = 26;
            for (int x = step; x < w; x += step) {
                for (int y = step; y < h; y += step) {
                    g2.fillOval(x, y, 1, 1);
                }
            }
        }

        private void paintScanline(Graphics2D g2, Shape clipShape, int w, int h) {
            Shape oldClip = g2.getClip();
            g2.clip(clipShape);

            float y = h * scanPhase;
            float bandH = Math.max(12f, h * 0.05f);

            GradientPaint band = new GradientPaint(
                    0, y - bandH / 2f, new Color(160, 230, 255, 0),
                    0, y, new Color(160, 230, 255, 45)
            );
            g2.setPaint(band);
            g2.fillRect(0, (int) (y - bandH / 2f), w, (int) (bandH / 2f));

            GradientPaint band2 = new GradientPaint(
                    0, y, new Color(160, 230, 255, 45),
                    0, y + bandH / 2f, new Color(160, 230, 255, 0)
            );
            g2.setPaint(band2);
            g2.fillRect(0, (int) y, w, (int) (bandH / 2f));

            g2.setColor(new Color(200, 240, 255, 75));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(0, (int) y, w, (int) y);

            g2.setClip(oldClip);
        }

        private void paintHeaderChevron(Graphics2D g2, float breathe) {
            g2.setColor(new Color(140, 226, 255, (int) (120 + 60 * breathe)));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawString("\u00BB\u00BB\u00BB", 14, 20);
        }

        private void paintCornerBrackets(Graphics2D g2, int w, int h, float breathe) {
            int len = 18;
            int pad = 7;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(160, 230, 255, (int) (150 + 90 * breathe)));

            g2.drawLine(pad, pad, pad + len, pad);
            g2.drawLine(pad, pad, pad, pad + len);
            g2.drawLine(w - pad, pad, w - pad - len, pad);
            g2.drawLine(w - pad, pad, w - pad, pad + len);
            g2.drawLine(pad, h - pad, pad + len, h - pad);
            g2.drawLine(pad, h - pad, pad, h - pad - len);
            g2.drawLine(w - pad, h - pad, w - pad - len, h - pad);
            g2.drawLine(w - pad, h - pad, w - pad, h - pad - len);
        }
    }

    // =========================================================
    //  LABEL TEKNIS — letter-spacing manual + opsi bracket "[ ]"
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

            String text = getText();
            String left = brackets ? "[  " : "";
            String right = brackets ? "  ]" : "";

            // ---- Auto-fit: kecilkan font bertahap sampai SELURUH huruf muat,
            //      supaya judul/teks tidak pernah terpotong meski container sempit. ----
            Font fitFont = getFont();
            FontMetrics fm = g2.getFontMetrics(fitFont);
            float spacing = letterSpacing;
            float availableW = getWidth() - 6f; // sedikit padding kiri-kanan
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
            for (int i = 0; i < text.length(); i++) {
                w += fm.stringWidth(String.valueOf(text.charAt(i))) + spacing;
            }
            return Math.max(0f, w - spacing);
        }
    }

    // =========================================================
    //  SEGMENTED CONTROL: 4 / 8 / 16 — cyan accent saat aktif
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
        private final Timer animTimer;

        SegmentedControl(int[] values, int selectedValue) {
            this.values = values;
            this.fillAnim = new float[values.length];
            int idx = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == selectedValue) idx = i;
            }
            this.selectedIndex = idx;
            fillAnim[selectedIndex] = 1f;

            setPreferredSize(new Dimension(78 * values.length, 44));
            setMaximumSize(new Dimension(78 * values.length, 44));
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
                for (int i = 0; i < values.length; i++) {
                    float target = (i == selectedIndex) ? 1f : 0f;
                    fillAnim[i] += (target - fillAnim[i]) * 0.22f;
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

            g2.setColor(new Color(140, 226, 255, 18));
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(new Color(160, 230, 255, 70));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

            for (int i = 0; i < values.length; i++) {
                int x = i * segW;
                int hoverLift = (i == hoverIndex && i != selectedIndex) ? -2 : 0;

                float fill = fillAnim[i];
                if (fill > 0.01f) {
                    GradientPaint activeFill = new GradientPaint(
                            x, 2, new Color(190, 240, 255, clampAlpha(fill)),
                            x, h - 2, new Color(110, 200, 230, clampAlpha(fill))
                    );
                    g2.setPaint(activeFill);
                    g2.fillRoundRect(x + 2, 2 + hoverLift, segW - 4, h - 4, 8, 8);

                    g2.setColor(new Color(200, 244, 255, clampAlpha(0.55f * fill)));
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.drawRoundRect(x + 2, 2 + hoverLift, segW - 5, h - 5, 8, 8);
                } else if (i == hoverIndex) {
                    g2.setColor(new Color(160, 230, 255, 30));
                    g2.fillRoundRect(x + 2, 2 + hoverLift, segW - 4, h - 4, 8, 8);
                }

                if (i > 0) {
                    g2.setColor(new Color(160, 230, 255, 35));
                    g2.drawLine(x, 7, x, h - 7);
                }

                g2.setFont(pickTechFont(15, Font.BOLD));
                g2.setColor(fill > 0.5f ? new Color(4, 14, 18) : C_SOFT_BLUE);
                String text = values[i] + "P";
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (segW - fm.stringWidth(text)) / 2;
                int ty = h / 2 + fm.getAscent() / 2 - 2 + hoverLift;
                g2.drawString(text, tx, ty);
            }

            g2.dispose();
        }
    }

    // =========================================================
    //  INPUT FIELD HOLOGRAM (cyan) — dipakai untuk semua slot pemain,
    //  identik strukturnya dengan HoloTextField pada Solo/Duel panel.
    // =========================================================
    private static class HoloNameField extends JTextField {
        private final String placeholder;
        private boolean focused = false;
        private float glowPhase = 0f;
        private final Timer glowTimer;

        HoloNameField(String placeholder) {
            super(placeholder);
            this.placeholder = placeholder;
            setForeground(C_WHITE);
            setCaretColor(C_CYAN_BRIGHT);
            setSelectionColor(new Color(140, 226, 255, 70));
            setHorizontalAlignment(JTextField.LEFT);
            setOpaque(false);
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setPreferredSize(new Dimension(10, 46));
            setMaximumSize(new Dimension(2000, 4000));

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
                    int alpha = (int) (36 * breathe / i);
                    g2.setColor(new Color(140, 226, 255, alpha));
                    g2.fillRoundRect(-i * 2, -i * 2, w + i * 4, h + i * 4, 10 + i * 2, 10 + i * 2);
                }
            }

            GradientPaint fieldGradient = focused
                    ? new GradientPaint(0, 0, GLASS_FILL_FOCUS_TOP, 0, h, GLASS_FILL_FOCUS_BOTTOM)
                    : new GradientPaint(0, 0, GLASS_FILL_TOP, 0, h, GLASS_FILL_BOTTOM);
            g2.setPaint(fieldGradient);
            g2.fillRoundRect(0, 0, w, h, 8, 8);

            g2.setColor(new Color(160, 230, 255, 14));
            for (int ly = 6; ly < h; ly += 6) {
                g2.drawLine(4, ly, w - 4, ly);
            }

            g2.setColor(focused ? C_CYAN_BRIGHT : C_CYAN_DIM);
            g2.fillRect(0, 4, 3, h - 8);

            g2.setStroke(new BasicStroke(focused ? 1.8f : 1.2f));
            g2.setColor(focused ? new Color(200, 240, 255, 230) : new Color(160, 230, 255, 90));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  TOMBOL START TOURNAMENT — glass + energy sweep + breathing glow cyan
    // =========================================================
    private static class StartButton extends JButton {
        private boolean hover = false;
        private float pulsePhase = 0f;
        private float hoverAnim = 0f;
        private long streakStart = -1L;
        private final java.util.List<float[]> particles = new ArrayList<>(); // {x, y, vy, life}
        private final java.util.Random rng = new java.util.Random();
        private final Timer fxTimer;

        StartButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setForeground(new Color(4, 14, 18));
            setFont(pickTechFont(16, Font.BOLD));

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
        public Dimension getPreferredSize() {
            return new Dimension(320, 56);
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
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

            GradientPaint fill = hover
                    ? new GradientPaint(0, 0, new Color(200, 244, 255), 0, h, new Color(120, 210, 235))
                    : new GradientPaint(0, 0, new Color(170, 232, 255), 0, h, new Color(90, 185, 215));
            g2.setPaint(fill);
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

                // partikel kecil melayang naik saat hover, dipotong di dalam bentuk tombol
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

    // =========================================================
    //  TOMBOL KEMBALI "\u2190 KEMBALI" — glass cyan, glow bernafas,
    //  light sweep saat hover, konsisten dengan tema kartu.
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
            setBorder(new EmptyBorder(16, 16, 8, 8));
            setPreferredSize(new Dimension(164, 58));
            setMaximumSize(new Dimension(174, 58));

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

            float breathe = 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(breathePhase));

            // glow cyan bernafas, lebih kuat saat hover
            g2.setColor(new Color(140, 226, 255, (int) (30 * breathe + 55 * hoverT)));
            g2.fill(new RoundRectangle2D.Float(cardX - 3, cardY - 3, cardW + 6, cardH + 6, arc + 4, arc + 4));

            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(new RoundRectangle2D.Float(cardX + 1, cardY + 3, cardW, cardH, arc, arc));

            GradientPaint fill = new GradientPaint(
                    cardX, cardY, GLASS_FILL_TOP,
                    cardX, cardY + cardH, GLASS_FILL_BOTTOM
            );
            g2.setPaint(fill);
            g2.fill(shape);

            // light sweep saat hover
            if (hovering && streakStart >= 0) {
                long elapsed = System.currentTimeMillis() - streakStart;
                float t = Math.min(1f, elapsed / 260f);
                Shape oldClip = g2.getClip();
                g2.clip(shape);
                float sx = cardX - cardH + t * (cardW + cardH * 2f);
                int alpha = (int) (90 * (1f - t));
                Polygon streak = new Polygon();
                streak.addPoint((int) sx, cardY - 5);
                streak.addPoint((int) (sx + cardH * 0.5f), cardY - 5);
                streak.addPoint((int) (sx + cardH * 0.5f - cardH), cardY + cardH + 5);
                streak.addPoint((int) (sx - cardH), cardY + cardH + 5);
                g2.setColor(new Color(200, 240, 255, Math.max(0, alpha)));
                g2.fill(streak);
                g2.setClip(oldClip);
            }

            g2.setColor(new Color(160, 230, 255, (int) (90 + 110 * hoverT)));
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(shape);

            g2.setColor(new Color(160, 230, 255, 45));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(cardX + 6, cardY + 2, cardX + cardW - 6, cardY + 2);

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String full = ARROW + " " + label;
            int tx = cardX + (cardW - fm.stringWidth(full)) / 2;
            int ty = cardY + (cardH + fm.getAscent()) / 2 - 3;

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