package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;


public class ResultPanel extends JPanel {

    private Timer particleTimer;
    private final List<float[]> confetti = new ArrayList<>(); // {x, y, vy, size, alpha}
    private final Random rng = new Random();
    private float glowPhase = 0f;

    public ResultPanel(MainFrame parent) {
        setLayout(new BorderLayout());
        setOpaque(true);

        boolean tournamentChampion = GameState.isTournamentMode
                && GameState.tournamentRoot != null
                && GameState.tournamentRoot.winner != null;

        initConfetti();

        add(buildHeaderPanel(tournamentChampion), BorderLayout.NORTH);
        add(buildCenterPanel(tournamentChampion), BorderLayout.CENTER);
        add(buildFooterPanel(parent), BorderLayout.SOUTH);

        particleTimer = new Timer(16, e -> tickParticles());
        particleTimer.start();

        addAncestorListenerCleanup();
    }

    /** Menghentikan timer confetti saat panel tidak lagi ditampilkan (mencegah leak sederhana). */
    private void addAncestorListenerCleanup() {
        addHierarchyListener(e -> {
            if (!isShowing() && particleTimer != null && particleTimer.isRunning()) {
                particleTimer.stop();
            }
        });
    }

    // =========================================================
    //  BACKGROUND: Gradient + Stickman + Confetti
    // =========================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(24, 24, 24),
                0, h, new Color(8, 8, 8)
        );
        g2.setPaint(gradient);
        g2.fillRect(0, 0, w, h);

        drawStickman(g2, (int) (w * 0.14f), (int) (h * 0.80f), Math.min(w, h) * 0.24f);
        drawStickman(g2, (int) (w * 0.86f), (int) (h * 0.22f), Math.min(w, h) * 0.22f);

        drawConfetti(g2);

        g2.dispose();
    }

    private void drawStickman(Graphics2D g2, int cx, int cy, float scale) {
        g2.setColor(new Color(255, 255, 255, 16));
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

    // =========================================================
    //  CONFETTI (particle celebratory, monochrome)
    // =========================================================
    private void initConfetti() {
        for (int i = 0; i < 40; i++) {
            float x = rng.nextFloat();
            float y = rng.nextFloat() * -1f; // mulai di atas layar, jatuh ke bawah
            float vy = 0.0012f + rng.nextFloat() * 0.0022f;
            float size = 3f + rng.nextFloat() * 5f;
            float alpha = 0.25f + rng.nextFloat() * 0.5f;
            confetti.add(new float[]{x, y, vy, size, alpha});
        }
    }

    private void tickParticles() {
        glowPhase += 0.03f;
        for (float[] c : confetti) {
            c[1] += c[2];
            if (c[1] > 1.05f) {
                c[1] = -0.05f;
                c[0] = rng.nextFloat();
            }
        }
        repaint();
    }

    private void drawConfetti(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        for (float[] c : confetti) {
            float x = c[0] * w;
            float y = c[1] * h;
            float size = c[3];
            int alpha = (int) Math.max(0, Math.min(255, c[4] * 255));
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.fillRect((int) x, (int) y, (int) size, (int) (size * 0.4f));
        }
    }

    // =========================================================
    //  HEADER
    // =========================================================
    private JPanel buildHeaderPanel(boolean tournamentChampion) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(46, 10, 10, 10));

        JLabel sub = new JLabel(tournamentChampion ? "TURNAMEN SELESAI" : "PERTANDINGAN SELESAI");
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setFont(new Font("Arial", Font.PLAIN, 14));
        sub.setForeground(new Color(150, 150, 150));

        JLabel title = new JLabel("HASIL AKHIR");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(pickTitleFont(46));
        title.setForeground(Color.WHITE);

        panel.add(sub);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
        panel.add(title);
        return panel;
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
    //  CENTER: Champion (turnamen) atau Perbandingan 2 Pemain (duel/solo)
    // =========================================================
    private JPanel buildCenterPanel(boolean tournamentChampion) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        if (tournamentChampion) {
            content.add(buildChampionBlock());
            content.add(Box.createRigidArea(new Dimension(0, 24)));
            content.add(buildLeaderboardBlock());
        } else {
            content.add(buildDuelResultBlock());
        }

        wrapper.add(content);
        return wrapper;
    }

    // ---- Blok: Juara Turnamen ----
    private JPanel buildChampionBlock() {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        Player champion = GameState.tournamentRoot.winner;

        JLabel star = new JLabel("\u2605 \u2605 \u2605");
        star.setAlignmentX(Component.CENTER_ALIGNMENT);
        star.setFont(new Font("Arial", Font.BOLD, 22));
        star.setForeground(new Color(230, 230, 230));

        JLabel lblLabel = new JLabel("JUARA TURNAMEN");
        lblLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblLabel.setFont(new Font("Arial", Font.BOLD, 15));
        lblLabel.setForeground(new Color(160, 160, 160));

        GlowLabel lblName = new GlowLabel(champion.getName().toUpperCase());
        lblName.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(star);
        box.add(Box.createRigidArea(new Dimension(0, 6)));
        box.add(lblLabel);
        box.add(Box.createRigidArea(new Dimension(0, 4)));
        box.add(lblName);
        return box;
    }

    /** Label nama juara dengan glow lembut berdenyut (dipakai hanya di layar hasil). */
    private class GlowLabel extends JComponent {
        private final String text;

        GlowLabel(String text) {
            this.text = text;
            setOpaque(false);
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setPreferredSize(new Dimension(10, 60));
            setMaximumSize(new Dimension(2000, 60));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Font font = pickTitleFont(38);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int cx = getWidth() / 2;
            int y = getHeight() / 2 + fm.getAscent() / 2 - 4;

            float pulse = 0.5f + 0.5f * (float) Math.sin(glowPhase);
            int[] offsets = {5, 3, 2};
            for (int r : offsets) {
                int a = (int) Math.max(0, (0.10f - r * 0.02f) * pulse * 255);
                g2.setColor(new Color(255, 255, 255, a));
                for (int dx = -r; dx <= r; dx += r) {
                    for (int dy = -r; dy <= r; dy += r) {
                        if (dx == 0 && dy == 0) continue;
                        g2.drawString(text, cx - textWidth / 2 + dx, y + dy);
                    }
                }
            }

            g2.setColor(Color.WHITE);
            g2.drawString(text, cx - textWidth / 2, y);
            g2.dispose();
        }
    }

    // ---- Blok: Papan skor seluruh peserta (turnamen) ----
    private JPanel buildLeaderboardBlock() {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(Component.CENTER_ALIGNMENT);

        List<Player> sorted = new ArrayList<>(GameState.allPlayers);
        sorted.sort(Comparator.comparingDouble(Player::getScore).reversed());

        int rank = 1;
        for (Player p : sorted) {
            JLabel row = new JLabel(rank + ". " + p.getName().toUpperCase() + "  \u2014  " + (int) p.getScore());
            row.setAlignmentX(Component.CENTER_ALIGNMENT);
            row.setFont(new Font("Arial", rank == 1 ? Font.BOLD : Font.PLAIN, rank == 1 ? 16 : 14));
            row.setForeground(rank == 1 ? Color.WHITE : new Color(150, 150, 150));
            row.setBorder(new EmptyBorder(3, 0, 3, 0));
            box.add(row);
            rank++;
        }
        return box;
    }

    // ---- Blok: Hasil Duel/Solo (perbandingan 2 pemain, seperti versi asli) ----
    private JPanel buildDuelResultBlock() {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        if (GameState.allPlayers.size() < 2) {
            JLabel empty = new JLabel("DATA PEMAIN TIDAK LENGKAP");
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setForeground(new Color(150, 150, 150));
            empty.setFont(new Font("Arial", Font.BOLD, 16));
            box.add(empty);
            return box;
        }

        Player p1 = GameState.allPlayers.get(0);
        Player p2 = GameState.allPlayers.get(1);

        boolean p1Wins = p1.getScore() > p2.getScore();
        boolean p2Wins = p2.getScore() > p1.getScore();
        boolean tie = p1.getScore() == p2.getScore();

        JPanel cardsRow = new JPanel();
        cardsRow.setOpaque(false);
        cardsRow.setLayout(new BoxLayout(cardsRow, BoxLayout.X_AXIS));
        cardsRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        cardsRow.add(buildScoreCard(p1, p1Wins && !tie));
        cardsRow.add(Box.createRigidArea(new Dimension(24, 0)));
        cardsRow.add(buildScoreCard(p2, p2Wins && !tie));

        String winText = tie ? "SERI!" : "PEMENANG: " + (p1Wins ? p1.getName() : p2.getName()).toUpperCase();

        JLabel lblWin = new JLabel(winText);
        lblWin.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblWin.setFont(new Font("Arial", Font.BOLD, 26));
        lblWin.setForeground(Color.WHITE);
        lblWin.setBorder(new EmptyBorder(26, 0, 0, 0));

        box.add(cardsRow);
        box.add(lblWin);
        return box;
    }

    private JPanel buildScoreCard(Player p, boolean winner) {
        JPanel card = new GlassCard(winner);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(220, 130));
        card.setMaximumSize(new Dimension(220, 130));
        card.setBorder(new EmptyBorder(20, 16, 16, 16));

        JLabel name = new JLabel(p.getName().toUpperCase());
        name.setAlignmentX(Component.CENTER_ALIGNMENT);
        name.setFont(new Font("Arial", Font.BOLD, 16));
        name.setForeground(winner ? Color.WHITE : new Color(190, 190, 190));

        JLabel score = new JLabel(String.valueOf((int) p.getScore()));
        score.setAlignmentX(Component.CENTER_ALIGNMENT);
        score.setFont(pickTitleFont(30));
        score.setForeground(winner ? Color.WHITE : new Color(170, 170, 170));
        score.setBorder(new EmptyBorder(6, 0, 0, 0));

        card.add(name);
        card.add(score);
        return card;
    }

    /** Kartu kaca sederhana; border lebih terang & sedikit berdenyut untuk pemenang. */
    private class GlassCard extends JPanel {
        private final boolean highlighted;

        GlassCard(boolean highlighted) {
            this.highlighted = highlighted;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(255, 255, 255, highlighted ? 22 : 12));
            g2.fillRoundRect(0, 0, w, h, 16, 16);

            if (highlighted) {
                float pulse = 0.6f + 0.4f * (float) Math.sin(glowPhase * 1.3f);
                g2.setColor(new Color(255, 255, 255, (int) (150 * pulse)));
                g2.setStroke(new BasicStroke(1.8f));
            } else {
                g2.setColor(new Color(255, 255, 255, 40));
                g2.setStroke(new BasicStroke(1f));
            }
            g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================
    //  FOOTER
    // =========================================================
    private JPanel buildFooterPanel(MainFrame parent) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 0, 34, 0));

        MenuStyleButton btnBack = new MenuStyleButton("KEMBALI KE MENU");
        btnBack.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnBack.setMaximumSize(new Dimension(280, 52));
        btnBack.addActionListener(e -> {
            if (particleTimer != null) particleTimer.stop();
            parent.showView("MENU_UTAMA"); // logika sama seperti versi asli
        });

        panel.add(btnBack);
        return panel;
    }

    /** Tombol bergaya sama seperti MenuButton di MainMenuPanel (invert hitam-putih saat hover). */
    private static class MenuStyleButton extends JButton {
        private boolean hover = false;

        MenuStyleButton(String text) {
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
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(hover ? new Color(15, 15, 15) : new Color(210, 210, 210, 130));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();

            setForeground(hover ? Color.WHITE : new Color(55, 55, 55));
            super.paintComponent(g);
        }
    }
}