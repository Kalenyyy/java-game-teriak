package com.aduteriak;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GameFrame extends JFrame {
    private JLabel lblStatus, lblTimer;
    private JProgressBar progressBar;
    private MicrophoneMeter mic;
    private Timer gameTimer;
    private int timeLeft = 5;
    private boolean isPlayer1Turn = true;
    private double peakVolume = 0;

    public GameFrame() {
        setTitle("Adu Teriak - Bertanding");
        setSize(500, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(20, 20));

        lblStatus = new JLabel("Siap-siap " + GameState.player1Name + "!", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Arial", Font.BOLD, 20));

        lblTimer = new JLabel("5", SwingConstants.CENTER);
        lblTimer.setFont(new Font("Arial", Font.BOLD, 50));

        progressBar = new JProgressBar(0, 10000); // Skala RMS
        progressBar.setPreferredSize(new Dimension(400, 50));

        add(lblStatus, BorderLayout.NORTH);
        add(lblTimer, BorderLayout.CENTER);
        add(progressBar, BorderLayout.SOUTH);

        mic = new MicrophoneMeter();
        mic.start();

        startTurn();
    }

    private void startTurn() {
        timeLeft = 5;
        peakVolume = 0;
        String currentPlayer = isPlayer1Turn ? GameState.player1Name : GameState.player2Name;
        lblStatus.setText("AYO TERIAK, " + currentPlayer.toUpperCase() + "!");

        gameTimer = new Timer(100, new ActionListener() {
            int ticks = 0;
            @Override
            public void actionPerformed(ActionEvent e) {
                ticks++;
                double currentVol = mic.getCurrentRMS();
                progressBar.setValue((int) currentVol);

                if (currentVol > peakVolume) peakVolume = currentVol;

                if (ticks % 10 == 0) {
                    timeLeft--;
                    lblTimer.setText(String.valueOf(timeLeft));
                }

                if (timeLeft <= 0) {
                    gameTimer.stop();
                    endTurn();
                }
            }
        });
        gameTimer.start();
    }

    private void endTurn() {
        if (isPlayer1Turn) {
            GameState.player1Score = peakVolume;
            isPlayer1Turn = false;
            JOptionPane.showMessageDialog(this, "Waktu habis! Sekarang giliran " + GameState.player2Name);
            startTurn();
        } else {
            GameState.player2Score = peakVolume;
            mic.stop();
            new ResultDialog(this).setVisible(true);
            dispose();
        }
    }
}