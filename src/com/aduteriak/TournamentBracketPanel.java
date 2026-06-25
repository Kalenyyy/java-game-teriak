package com.aduteriak;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

public class TournamentBracketPanel extends JPanel {
    private MainFrame parent;
    private JButton btnPlayMatch;
    private JButton btnShuffle; // Tombol baru

    public TournamentBracketPanel(MainFrame parent) {
        this.parent = parent;
        setBackground(new Color(25, 25, 25));
        setLayout(new BorderLayout());

        // HEADER
        JLabel lblTitle = new JLabel("TOURNAMENT BRACKET", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 40));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setBorder(BorderFactory.createEmptyBorder(20,0,20,0));
        add(lblTitle, BorderLayout.NORTH);

        // FOOTER (Tempat tombol-tombol)
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        footer.setOpaque(false);

        // Tombol Shuffle
        btnShuffle = new JButton("ACAK POSISI (SHUFFLE)");
        btnShuffle.setFont(new Font("Arial", Font.BOLD, 18));
        btnShuffle.setPreferredSize(new Dimension(280, 60));
        btnShuffle.setBackground(new Color(150, 70, 0)); // Warna Oranye/Coklat
        btnShuffle.setForeground(Color.WHITE);
        btnShuffle.addActionListener(e -> {
            TournamentManager.shuffleAndRebuild();
            repaint(); // Gambar ulang bagan dengan posisi baru
        });

        // Tombol Main
        btnPlayMatch = new JButton("MULAI PERTANDINGAN BERIKUTNYA");
        btnPlayMatch.setFont(new Font("Arial", Font.BOLD, 18));
        btnPlayMatch.setPreferredSize(new Dimension(380, 60));
        btnPlayMatch.addActionListener(e -> {
            if (TournamentManager.isTournamentOver()) {
                parent.showResult();
            } else {
                TournamentManager.updateQueueFromTree();
                parent.startGame();
            }
        });

        footer.add(btnShuffle);
        footer.add(btnPlayMatch);
        add(footer, BorderLayout.SOUTH);
    }

    public void refreshBracket() {
        // Logika Sembunyikan Tombol Shuffle:
        // Jika sudah ada minimal satu match yang punya pemenang, tombol Shuffle hilang
        boolean hasStarted = false;
        for (MatchNode m : GameState.allMatches) {
            if (m.winner != null) {
                hasStarted = true;
                break;
            }
        }

        if (hasStarted) {
            btnShuffle.setVisible(false); // Tidak bisa acak lagi kalau sudah ada yang tanding
        } else {
            btnShuffle.setVisible(true);
        }

        // Update teks tombol tanding
        if (TournamentManager.isTournamentOver()) {
            btnPlayMatch.setText("LIHAT JUARA AKHIR");
        } else {
            btnPlayMatch.setText("MULAI PERTANDINGAN BERIKUTNYA");
        }

        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (GameState.tournamentRoot != null) {
            // Gambar Tree: Root (Final) di kanan, Leaf (Awal) di kiri
            drawNode(g2, GameState.tournamentRoot, getWidth() - 250, getHeight() / 2, 200);
        }
    }

    private void drawNode(Graphics2D g, MatchNode node, int x, int y, int yOffset) {
        if (node == null) return;

        int w = 180, h = 80;
        g.setColor(new Color(45, 45, 45));
        g.fillRoundRect(x - w / 2, y - h / 2, w, h, 15, 15);
        g.setColor(Color.GRAY);
        g.drawRoundRect(x - w / 2, y - h / 2, w, h, 15, 15);

        g.setFont(new Font("Arial", Font.BOLD, 12));

        // Info Pemain 1 (Atas)
        drawPlayerInfo(g, node.p1, node.winner, x - w/2 + 10, y - 15);
        // Info Pemain 2 (Bawah)
        drawPlayerInfo(g, node.p2, node.winner, x - w/2 + 10, y + 25);

        if (node.left != null) {
            int childX = x - 250;
            int childYLeft = y - yOffset;
            int childYRight = y + yOffset;

            g.setColor(new Color(100, 100, 100));
            g.drawLine(x - w / 2, y, childX + w / 2, childYLeft);
            g.drawLine(x - w / 2, y, childX + w / 2, childYRight);

            drawNode(g, node.left, childX, childYLeft, yOffset / 2);
            drawNode(g, node.right, childX, childYRight, yOffset / 2);
        }
    }

    private void drawPlayerInfo(Graphics2D g, Player p, Player winner, int x, int y) {
        if (p == null) {
            g.setColor(Color.DARK_GRAY);
            g.drawString("MENUNGGU...", x, y);
        } else {
            if (winner != null && winner != p) {
                // Kalah: Merah + Coret
                g.setColor(Color.RED);
                String name = p.getName().toUpperCase() + " (OUT)";
                g.drawString(name, x, y);
                int stringWidth = g.getFontMetrics().stringWidth(name);
                g.drawLine(x, y - 5, x + stringWidth, y - 5);
            } else if (winner == p) {
                // Menang: Hijau
                g.setColor(Color.GREEN);
                g.drawString(p.getName().toUpperCase() + " (WIN)", x, y);
            } else {
                // Belum tanding: Putih
                g.setColor(Color.WHITE);
                g.drawString(p.getName().toUpperCase(), x, y);
            }
        }
    }
}