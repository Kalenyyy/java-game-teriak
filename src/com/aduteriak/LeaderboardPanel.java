package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class LeaderboardPanel extends JPanel {
    private JPanel listWrapper;

    public LeaderboardPanel(MainFrame parent) {
        setBackground(new Color(10, 10, 12));
        setLayout(new BorderLayout());

        // Header
        JLabel title = new JLabel("PAPAN PERINGKAT TERIAK", SwingConstants.CENTER);
        title.setFont(new Font("Impact", Font.PLAIN, 40));
        title.setForeground(Color.WHITE);
        title.setBorder(new EmptyBorder(40, 0, 30, 0));
        add(title, BorderLayout.NORTH);

        listWrapper = new JPanel();
        listWrapper.setOpaque(false);
        listWrapper.setLayout(new BoxLayout(listWrapper, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(listWrapper);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        JButton back = new JButton("KEMBALI KE MENU");
        back.addActionListener(e -> parent.showView("MENU_UTAMA"));
        add(back, BorderLayout.SOUTH);

        this.addHierarchyListener(e -> {
            // Jika status tampilan berubah dan sekarang sedang tampil (showing)
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    refreshData(); // Tarik data terbaru dari file .txt
                }
            }
        });
        // ============================================================

        refreshData();
    }

    public void refreshData() {
        listWrapper.removeAll();

        List<Player> scores = ScoreManager.getTopScores();

        if (scores.isEmpty()) {
            JLabel empty = new JLabel("BELUM ADA DATA SKOR");
            empty.setForeground(Color.GRAY);
            empty.setFont(new Font("Arial", Font.ITALIC, 18));
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(50, 0, 0, 0));
            listWrapper.add(empty);
        } else {
            for (int i = 0; i < scores.size(); i++) {
                Player p = scores.get(i);
                JLabel row = new JLabel((i + 1) + ". " + p.getName().toUpperCase() + "  -  " + (int)p.getScore() + " PTS");
                row.setFont(new Font("Arial", Font.BOLD, 22));
                row.setForeground(i == 0 ? Color.YELLOW : Color.WHITE);
                row.setAlignmentX(Component.CENTER_ALIGNMENT);
                row.setBorder(new EmptyBorder(10, 0, 10, 0));
                listWrapper.add(row);
            }
        }

        listWrapper.revalidate();
        listWrapper.repaint();
    }
}