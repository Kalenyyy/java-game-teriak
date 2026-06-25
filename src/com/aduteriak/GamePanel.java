package com.aduteriak;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class GamePanel extends JPanel {
    private MainFrame parent;
    private JLabel lblStatus, lblTimer, lblInstruction;
    private JProgressBar progressBar;
    private JLabel lblScore;
    private MicrophoneMeter mic;
    private Timer gameTimer;
    private int timeLeft = 3, ticks = 0;
    private double peakScore = 0;
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

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(800, 100));
        progressBar.setStringPainted(true);

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

        // Binding SPACE
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

            if (GameState.isTournamentMode) {
                // 1. Tentukan siapa pemenang dari skor yang baru dicatat
                TournamentManager.processWinner();

                lblStatus.setText("MATCH SELESAI!");
                lblTimer.setText("OK");

                // 2. Cek apakah ini sudah pertandingan FINAL (Root sudah ada pemenang)
                if (GameState.tournamentRoot.winner != null) {
                    // Jika sudah ada juara umum, tunggu 2 detik lalu ke layar juara
                    Timer t = new Timer(2000, e -> parent.showResult());
                    t.setRepeats(false);
                    t.start();
                } else {
                    // Jika belum final, balik ke layar BRACKET agar user bisa lihat bagan
                    // User nanti klik tombol di BracketPanel untuk lanjut ke match berikutnya
                    Timer t = new Timer(2000, e -> parent.showBracket());
                    t.setRepeats(false);
                    t.start();
                }
            } else {
                // Mode 2 Player Biasa (langsung ke result)
                parent.showResult();
            }
        } else {
            // RESET UI UNTUK PEMAIN KEDUA DALAM SATU MATCH
            isWaitingForSpace = true;
            peakScore = 0;
            lblStatus.setText("GILIRAN: " + currentPlayer.getName().toUpperCase());
            lblTimer.setText("READY");
            lblInstruction.setText("TEKAN [SPACE] UNTUK MULAI");
            progressBar.setValue(0);
            lblScore.setText("Score: 0/100");
        }
    }

    private void startTurn() {
        timeLeft = 3; ticks = 0; peakScore = 0;
        lblInstruction.setText("TERIAKKKK!!!");
        gameTimer = new Timer(100, e -> {
            ticks++;
            double score = mic.getCurrentScore();
            progressBar.setValue((int)score);
            lblScore.setText(String.format("Score: %.1f/100", score));

            if(score > peakScore) peakScore = score;

            if(ticks % 10 == 0) {
                timeLeft--;
                lblTimer.setText(String.valueOf(timeLeft));
                if(timeLeft < 0) {
                    gameTimer.stop();
                    currentPlayer.setScore(peakScore);
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