package com.aduteriak;

import javax.swing.*;
import java.awt.*;

public class ResultDialog extends JDialog {
    public ResultDialog(Frame parent) {
        super(parent, "Hasil Pertandingan", true);
        setSize(400, 300);
        setLocationRelativeTo(parent);
        setLayout(new GridLayout(5, 1));

        String winner;
        if (GameState.player1Score > GameState.player2Score) {
            winner = "PEMENANG: " + GameState.player1Name;
        } else if (GameState.player2Score > GameState.player1Score) {
            winner = "PEMENANG: " + GameState.player2Name;
        } else {
            winner = "HASIL SERI!";
        }

        add(new JLabel("SKOR AKHIR", SwingConstants.CENTER));
        add(new JLabel(GameState.player1Name + ": " + (int)GameState.player1Score, SwingConstants.CENTER));
        add(new JLabel(GameState.player2Name + ": " + (int)GameState.player2Score, SwingConstants.CENTER));

        JLabel lblWinner = new JLabel(winner, SwingConstants.CENTER);
        lblWinner.setFont(new Font("Arial", Font.BOLD, 18));
        lblWinner.setForeground(Color.RED);
        add(lblWinner);

        JButton btnAgain = new JButton("Main Lagi");
        btnAgain.addActionListener(e -> {
            new StartFrame().setVisible(true);
            dispose();
        });
        add(btnAgain);
    }
}