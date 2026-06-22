package com.aduteriak;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class GamePanel extends JPanel {
    private MainFrame parent;
    private JLabel lblStatus, lblTimer, lblInstruction;
    private JProgressBar progressBar;
    private JLabel lblScore; // Untuk debug: tampilkan score real-time
    private MicrophoneMeter mic;
    private Timer gameTimer;
    private int timeLeft = 3, ticks = 0;
    private double peakScore = 0; // UBAH: peakVolume -> peakScore
    private Player currentPlayer;
    private boolean isWaitingForSpace = true;

    public GamePanel(MainFrame parent) {
        this.parent = parent;
        setBackground(new Color(20, 20, 20));
        setLayout(new BorderLayout(30, 30));

        lblStatus = new JLabel("SIAP?", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Arial", Font.BOLD, 50));
        lblStatus.setForeground(Color.WHITE);

        lblTimer = new JLabel("READY", SwingConstants.CENTER);
        lblTimer.setFont(new Font("Arial", Font.BOLD, 150));
        lblTimer.setForeground(Color.YELLOW);

        lblInstruction = new JLabel("TEKAN SPACE", SwingConstants.CENTER);
        lblInstruction.setForeground(Color.CYAN);
        lblInstruction.setFont(new Font("Arial", Font.PLAIN, 25));

        // UBAH: Progress bar dari 0-10000 menjadi 0-100
        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(800, 100));
        progressBar.setStringPainted(true);

        // Debug label untuk lihat score real-time
        lblScore = new JLabel("Score: 0/100", SwingConstants.CENTER);
        lblScore.setForeground(Color.GREEN);
        lblScore.setFont(new Font("Arial", Font.PLAIN, 15));

        add(lblStatus, BorderLayout.NORTH);
        add(lblTimer, BorderLayout.CENTER);

        JPanel bot = new JPanel(new BorderLayout());
        bot.setOpaque(false);
        bot.add(lblInstruction, BorderLayout.NORTH);
        bot.add(progressBar, BorderLayout.SOUTH);

        JPanel botBot = new JPanel(new BorderLayout());
        botBot.setOpaque(false);
        botBot.add(lblScore, BorderLayout.SOUTH);
        bot.add(botBot, BorderLayout.CENTER);

        add(bot, BorderLayout.SOUTH);

        // Key Bindings SPACE
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "start");
        am.put("start", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(isWaitingForSpace) {
                    isWaitingForSpace = false;
                    startTurn();
                }
            }
        });

        mic = new MicrophoneMeter();
        mic.start();
        prepareNext();
    }

    private void prepareNext() {
        currentPlayer = GameState.turnQueue.poll();
        if(currentPlayer == null) {
            mic.stop();
            parent.showResult();
        } else {
            isWaitingForSpace = true;
            peakScore = 0; // RESET peak score
            lblStatus.setText("GILIRAN: " + currentPlayer.getName().toUpperCase());
            lblTimer.setText("READY");
            lblInstruction.setText("TEKAN [SPACE] UNTUK MULAI");
            progressBar.setValue(0);
            lblScore.setText("Score: 0/100");
        }
    }

    private void startTurn() {
        timeLeft = 3;
        ticks = 0;
        peakScore = 0;
        lblInstruction.setText("TERIAKKKK!!!");
        gameTimer = new Timer(100, e -> {
            ticks++;

            // UBAH: Ambil score 0-100 bukan RMS mentah
            double score = mic.getCurrentScore();
            progressBar.setValue((int)score);
            lblScore.setText(String.format("Score: %.1f/100", score));

            // Track peak score
            if(score > peakScore) {
                peakScore = score;
            }

            if(ticks % 10 == 0) {
                timeLeft--;
                lblTimer.setText(String.valueOf(timeLeft));
                if(timeLeft < 0) {
                    gameTimer.stop();

                    // UBAH: Simpan peakScore bukan peakVolume
                    currentPlayer.setScore(peakScore);
                    System.out.println(currentPlayer.getName() + " score: " + peakScore);

                    lblTimer.setText("STOP");
                    Timer pause = new Timer(1000, ev -> prepareNext());
                    pause.setRepeats(false);
                    pause.start();
                }
            }
        });
        gameTimer.start();
    }
}