package com.aduteriak;

import javax.swing.*;
import java.awt.*;

public class Input2PPanel extends JPanel {
    public Input2PPanel(MainFrame parent) {
        setBackground(new Color(30, 30, 30));
        setLayout(new GridBagLayout());

        JPanel form = new JPanel(new GridLayout(5, 1, 10, 10));
        form.setOpaque(false);

        JLabel lbl = new JLabel("MASUKKAN NAMA", SwingConstants.CENTER);
        lbl.setFont(new Font("Arial", Font.BOLD, 30));
        lbl.setForeground(Color.WHITE);

        JTextField f1 = new JTextField("Pemain 1");
        JTextField f2 = new JTextField("Pemain 2");
        JButton btnStart = new JButton("MULAI");
        JButton btnBack = new JButton("KEMBALI");

        btnStart.addActionListener(e -> {
            GameState.reset();

            // BUAT OBJEK SEKALI SAJA (Ini kunci agar skor tidak 0)
            Player p1 = new Player(f1.getText());
            Player p2 = new Player(f2.getText());

            // Masukkan objek YANG SAMA ke List dan Queue
            GameState.allPlayers.add(p1);
            GameState.allPlayers.add(p2);
            GameState.turnQueue.add(p1);
            GameState.turnQueue.add(p2);

            parent.startGame();
        });

        btnBack.addActionListener(e -> parent.showView("MENU_UTAMA"));

        form.add(lbl); form.add(f1); form.add(f2); form.add(btnStart); form.add(btnBack);
        add(form);
    }
}