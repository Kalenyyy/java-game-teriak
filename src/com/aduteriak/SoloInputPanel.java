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

public class SoloInputPanel extends JPanel {

    // Taruh file foto asset di path ini (relatif terhadap classpath / resources root):
    // src/main/resources/assets/images/solo_input_background.png
    private static final String BACKGROUND_RESOURCE_PATH = "/assets/images/alex.jpeg";

    // ---- Warna teks (dipasangkan dengan outline gelap agar tidak pernah "menyatu" ----
    // ---- dengan bagian gambar yang kebetulan terang di titik yang sama)             ----
    private static final Color TITLE_COLOR = new Color(255, 255, 255);
    private static final Color SUBTITLE_COLOR = new Color(225, 225, 225);
    private static final Color FIELD_LABEL_COLOR = new Color(230, 230, 230);
    private static final Color TEXT_OUTLINE_COLOR = new Color(0, 0, 0, 235);

    // ---- Scrim: penggelap tipis di zona header & footer supaya teks & tombol ----
    // ---- selalu punya latar yang cukup gelap, terlepas dari isi foto di titik itu ----
    private static final Color SCRIM_COLOR = new Color(0, 0, 0);
    private static final float SCRIM_TOP_HEIGHT_RATIO = 0.30f;
    private static final float SCRIM_BOTTOM_HEIGHT_RATIO = 0.26f;
    private static final int SCRIM_TOP_ALPHA = 165;
    private static final int SCRIM_BOTTOM_ALPHA = 170;

    private BufferedImage backgroundImage;

    private float entranceAlpha = 1f; // 1 = gelap penuh, 0 = transparan (reveal selesai)
    private Timer entranceTimer;

    public SoloInputPanel(MainFrame parent) {
        setLayout(new BorderLayout());
        setOpaque(true);

        loadBackgroundImage();

        add(buildHeaderPanel(parent), BorderLayout.NORTH);
        add(buildCenterPanel(parent), BorderLayout.CENTER);
        add(buildFooterPanel(parent), BorderLayout.SOUTH);

        startEntranceAnimation();
    }

    // =========================================================
    //  LATAR BELAKANG (Gambar, cover-fit, senada Main Menu)
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
        drawReadabilityScrim(g2, w, h);

        g2.dispose();

        // Overlay fade-in dari hitam pekat -> transparan saat panel baru dibuka
        if (entranceAlpha > 0f) {
            Graphics2D gf = (Graphics2D) g.create();
            gf.setColor(new Color(0, 0, 0, (int) (entranceAlpha * 255)));
            gf.fillRect(0, 0, w, h);
            gf.dispose();
        }
    }

    /**
     * Menggambar backgroundImage agar selalu memenuhi seluruh panel tanpa
     * distorsi (perilaku "cover" seperti CSS background-size: cover).
     * Kelebihan area akan di-crop, bukan di-stretch.
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
     * Menggelapkan tipis zona atas (judul) dan bawah (tombol) dengan gradient,
     * supaya teks & kontrol selalu punya latar yang cukup gelap tanpa
     * menutupi keseluruhan artwork di bagian tengah panel.
     */
    private void drawReadabilityScrim(Graphics2D g2, int w, int h) {
        int topHeight = Math.round(h * SCRIM_TOP_HEIGHT_RATIO);
        Paint topScrim = new GradientPaint(
                0, 0, withAlpha(SCRIM_COLOR, SCRIM_TOP_ALPHA),
                0, topHeight, withAlpha(SCRIM_COLOR, 0)
        );
        g2.setPaint(topScrim);
        g2.fillRect(0, 0, w, topHeight);

        int bottomHeight = Math.round(h * SCRIM_BOTTOM_HEIGHT_RATIO);
        int bottomStart = h - bottomHeight;
        Paint bottomScrim = new GradientPaint(
                0, bottomStart, withAlpha(SCRIM_COLOR, 0),
                0, h, withAlpha(SCRIM_COLOR, SCRIM_BOTTOM_ALPHA)
        );
        g2.setPaint(bottomScrim);
        g2.fillRect(0, bottomStart, w, bottomHeight);
    }

    private static Color withAlpha(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
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



        JLabel title = new OutlinedLabel("MASUKKAN NAMAMU", TITLE_COLOR, TEXT_OUTLINE_COLOR, 2);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(pickTitleFont(38));

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
        JLabel lbl = new OutlinedLabel(text, FIELD_LABEL_COLOR, TEXT_OUTLINE_COLOR, 1);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
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
    //  LABEL DENGAN OUTLINE (agar teks tidak pernah menyatu dengan gambar)
    //  Isi terang menjamin kontras di area gelap, outline gelap menjamin
    //  kontras di area terang (mis. wisp energi putih pada foto).
    // =========================================================
    private static class OutlinedLabel extends JLabel {
        private final Color outlineColor;
        private final int outlineThickness;

        OutlinedLabel(String text, Color fillColor, Color outlineColor, int outlineThickness) {
            super(text);
            this.outlineColor = outlineColor;
            this.outlineThickness = outlineThickness;
            setForeground(fillColor);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());

            String text = getText();
            FontMetrics fm = g2.getFontMetrics();
            int textX = (getWidth() - fm.stringWidth(text)) / 2;
            int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

            g2.setColor(outlineColor);
            for (int dx = -outlineThickness; dx <= outlineThickness; dx++) {
                for (int dy = -outlineThickness; dy <= outlineThickness; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    g2.drawString(text, textX + dx, textY + dy);
                }
            }

            g2.setColor(getForeground());
            g2.drawString(text, textX, textY);

            g2.dispose();
        }
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