package com.aduteriak;

import javax.swing.*;
import java.awt.*;

public class StartFrame extends JFrame {
    public StartFrame() {
        setTitle("Adu Teriak - Menu");
        setSize(400, 250);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(4, 1, 10, 10));

        JTextField p1Field = new JTextField("Pemain 1");
        JTextField p2Field = new JTextField("Pemain 2");
        JButton startBtn = new JButton("MULAI GAME");

        add(new JLabel("Masukkan Nama Pemain:", SwingConstants.CENTER));
        add(p1Field);
        add(p2Field);
        add(startBtn);

        startBtn.addActionListener(e -> {
            GameState.player1Name = p1Field.getText();
            GameState.player2Name = p2Field.getText();
            GameState.reset();
            new GameFrame().setVisible(true);
            dispose();
        });
    }
}