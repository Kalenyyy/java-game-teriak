package com.aduteriak;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * GamePanel.java
 * Panel gameplay utama game "AAAAAAAAAAAA". Menangani giliran teriak
 * BERGANTIAN mengikuti antrian GameState.turnQueue -- dipakai baik untuk
 * mode "MAIN SENDIRI" (1 pemain di antrian) maupun "MODE DUEL" (2 pemain
 * di antrian, ditampilkan sebagai lajur atas & bawah).
 *
 * Mekanik:
 * - Sebelum mikrofon mulai dihitung, ditampilkan countdown "3, 2, 1, MULAIIII!".
 * - Karakter pemain yang sedang giliran mulai di posisi KIRI (LOW), lalu bergerak
 *   ke KANAN (menuju MAX) proporsional terhadap desibel real-time, dan meluncur
 *   balik ke kiri (lerp) saat suara mengecil.
 * - Waveform digambar di ATAS bar skala.
 * - Dua lajur (atas = pemain pertama, bawah = pemain kedua jika ada) selalu
 *   tampil; lajur milik pemain yang tidak sedang giliran "membeku" di posisi
 *   terakhirnya.
 *
 * Fitur audio & visual juice:
 * - BGM tegang yang loop sejak panel dimuat, dengan efek ducking saat teriakan mulai.
 * - SFX tick tiap angka countdown & impact saat "MULAIIII!".
 * - SFX distorsi (scream echo) yang volumenya mengikuti dB teriakan secara real-time.
 * - Screen shake seluruh panel saat dB mendekati MAX.
 * - Afterimage/motion blur di belakang karakter saat bergerak cepat.
 * - Shockwave ring yang memancar dari mulut karakter saat berteriak.
 * - Speed lines di sekitar bar saat karakter melesat maju.
 * - Flash putih singkat saat seorang pemain mencapai skor mendekati MAX.
 *
 * Catatan arsitektur:
 * - Akuisisi mikrofon didelegasikan ke class AudioCapture (file terpisah).
 * - Audio management (BGM/SFX) didelegasikan ke class SoundManager (file terpisah).
 * - Panel ini TIDAK memiliki inner-class bertumpuk; semua dependensi berat
 *   sudah dipecah jadi top-level class agar mudah diuji & dipakai ulang.
 */
public class GamePanel extends JPanel {

    private final MainFrame parent;

    // ---- Identitas pemain (untuk pemetaan lajur atas/bawah) ----
    private Player playerTop;    // Lajur atas (giliran pertama)
    private Player playerBottom; // Lajur bawah (giliran kedua, jika ada / Mode Duel)

    // ---- Antrian giliran aktif ----
    private Player currentPlayer;
    private int totalPlayers;
    private int turnIndex = 0;

    // ---- Akuisisi mikrofon (1 device, dipakai bergantian antar pemain) ----
    private AudioCapture mic;
    private boolean micActive = false;
    private boolean listening = false; // true hanya saat fase SCREAMING (setelah "MULAIIII!")

    // ---- Audio management (BGM & SFX) ----
    private SoundManager sound;

    // ---- State mesin giliran ----
    private enum Phase { WAITING_SPACE, COUNTDOWN, SCREAMING, TURN_DONE, ALL_DONE }
    private Phase phase = Phase.WAITING_SPACE;

    private final String[] countdownStages = {"3", "2", "1", "MULAIIII!"};
    private int countdownIndex = 0;
    private Timer countdownTimer;
    private static final int COUNTDOWN_STEP_MS = 700;
    private static final int SCREAM_DURATION_MS = 3000;

    private Timer screamTimer;
    private int screamTicks = 0;

    private double peakScore = 0;
    private String statusText = "MENYIAPKAN...";
    private String bigOverlayText = null;

    // ---- Posisi lajur (0.0 = kiri/LOW, 1.0 = kanan/MAX) ----
    private float livePosTop = 0f, targetPosTop = 0f, prevLivePosTop = 0f;
    private float livePosBottom = 0f, targetPosBottom = 0f, prevLivePosBottom = 0f;
    private boolean frozenTop = false, frozenBottom = false;
    private double liveScoreTop = 0, liveScoreBottom = 0;

    // ---- Riwayat waveform per lajur ----
    private static final int WAVE_POINTS = 120;
    private final LinkedList<Double> waveTop = new LinkedList<>();
    private final LinkedList<Double> waveBottom = new LinkedList<>();

    // ---- Motion blur / afterimage (riwayat posisi normalisasi) ----
    private static final int TRAIL_LENGTH = 6;
    private final LinkedList<Float> trailTop = new LinkedList<>();
    private final LinkedList<Float> trailBottom = new LinkedList<>();

    // ---- Shockwave ring {x, y, radius, alpha} ----
    private final List<float[]> ringsTop = new ArrayList<>();
    private final List<float[]> ringsBottom = new ArrayList<>();
    private int ringTimerTop = 0, ringTimerBottom = 0;

    // ---- Speed lines {x, y, length, alpha} ----
    private final List<float[]> speedLinesTop = new ArrayList<>();
    private final List<float[]> speedLinesBottom = new ArrayList<>();
    private int speedLineTimerTop = 0, speedLineTimerBottom = 0;

    // ---- Screen shake ----
    private float shakeMagnitude = 0f;

    // ---- Flash effect ----
    private float flashAlpha = 0f;
    private Timer flashTimer;

    private static final double THRESHOLD = 25.0;
    private static final double SHAKE_TRIGGER_PERCENT = 70.0; // 70% dari MAX
    private static final double MAX_REACH_SCORE = 98.0;
    private final Random rng = new Random();

    // ---- Layout konstan (dipakai bersama render & logic partikel) ----
    private static final int LANE_MARGIN_X = 70;
    private static final float BAR_TOP_RATIO = 0.40f;
    private static final float BAR_BOTTOM_RATIO = 0.66f;
    private static final float WAVE_AMPLITUDE_RATIO = 0.10f;

    // ---- Tombol interaktif ----
    private JButton btnBack;
    private JButton btnMicToggle;
    private JButton btnStart;
    private JLabel lblSubHint;

    // ---- Timer render ----
    private Timer renderTimer;

    public GamePanel(MainFrame parent) {
        this.parent = parent;
        setBackground(new Color(8, 8, 8));
        setLayout(new BorderLayout());
        setFocusable(true);

        for (int i = 0; i < WAVE_POINTS; i++) {
            waveTop.add(0.0);
            waveBottom.add(0.0);
        }
        for (int i = 0; i < TRAIL_LENGTH; i++) {
            trailTop.add(0f);
            trailBottom.add(0f);
        }

        // Intip 2 pemain pertama di antrian tanpa menghapusnya, untuk label lajur atas/bawah.
        // Jika hanya 1 pemain (mode solo), playerBottom akan null dan lajur bawah otomatis idle.
        List<Player> snapshot = new ArrayList<>(GameState.turnQueue);
        playerTop = snapshot.size() > 0 ? snapshot.get(0) : null;
        playerBottom = snapshot.size() > 1 ? snapshot.get(1) : null;
        totalPlayers = snapshot.size();

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildFooterPanel(), BorderLayout.SOUTH);

        setupKeyBindings();
        setupMicrophone();

        sound = new SoundManager();
        sound.startBGM(); // BGM mulai sejak panel dimuat

        renderTimer = new Timer(16, e -> tickRender());
        renderTimer.start();

        prepareNextTurn();
    }

    // =========================================================
    //  HEADER
    // =========================================================
    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(10, 70));

        btnBack = new JButton("\u2190 KEMBALI");
        btnBack.setFont(new Font("Arial", Font.PLAIN, 14));
        btnBack.setForeground(Color.WHITE);
        btnBack.setContentAreaFilled(false);
        btnBack.setBorderPainted(false);
        btnBack.setFocusPainted(false);
        btnBack.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBack.setBorder(BorderFactory.createEmptyBorder(14, 18, 0, 0));
        btnBack.addActionListener(e -> goBackToMainMenu());

        panel.add(btnBack, BorderLayout.WEST);
        return panel;
    }

    /** Kembali ke Main Menu dengan bersih: hentikan semua timer & thread mikrofon/audio dulu. */
    private void goBackToMainMenu() {
        stopEverything();
        parent.showView("MENU_UTAMA");
        parent.requestFocus();
    }

    // =========================================================
    //  FOOTER
    // =========================================================
    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));

        btnMicToggle = buildOutlineButton("AKTIFKAN MIKROFON", 15);
        btnMicToggle.setMaximumSize(new Dimension(260, 42));
        btnMicToggle.addActionListener(e -> toggleMicrophone());

        btnStart = buildOutlineButton("TEKAN SPACE UNTUK MULAI TERIAK", 17);
        btnStart.setMaximumSize(new Dimension(420, 54));
        btnStart.addActionListener(e -> attemptStartTurn());

        lblSubHint = new JLabel("ATAU TEKAN [SPACE]");
        lblSubHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblSubHint.setFont(new Font("Arial", Font.PLAIN, 12));
        lblSubHint.setForeground(new Color(150, 150, 150));
        lblSubHint.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        panel.add(btnMicToggle);
        panel.add(Box.createRigidArea(new Dimension(0, 12)));
        panel.add(btnStart);
        panel.add(lblSubHint);
        return panel;
    }

    private JButton buildOutlineButton(String text, int fontSize) {
        JButton b = new JButton(text);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setFont(new Font("Arial", Font.BOLD, fontSize));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(0, 0, 0, 0));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 160), 1));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    // =========================================================
    //  KEY BINDING
    // =========================================================
    private void setupKeyBindings() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "startTurn");
        am.put("startTurn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                attemptStartTurn();
            }
        });
    }

    // =========================================================
    //  MIKROFON (akuisisi didelegasikan ke class AudioCapture)
    // =========================================================
    private void setupMicrophone() {
        AudioFormat format = new AudioFormat(44100f, 16, 1, true, false);
        mic = new AudioCapture(format);
    }

    private void toggleMicrophone() {
        micActive = !micActive;
        if (micActive) {
            mic.start();
            btnMicToggle.setText("MATIKAN MIKROFON");
        } else {
            mic.stop();
            btnMicToggle.setText("AKTIFKAN MIKROFON");
        }
        requestFocusInWindow();
    }

    // =========================================================
    //  ANTRIAN GILIRAN
    // =========================================================
    private void prepareNextTurn() {
        currentPlayer = GameState.turnQueue.poll();

        if (currentPlayer == null) {
            phase = Phase.ALL_DONE;
            stopEverything();

            if (GameState.isTournamentMode) {
                TournamentManager.processWinner();
                statusText = "MATCH SELESAI!";
                if (GameState.tournamentRoot.winner != null) {
                    Timer t = new Timer(2000, e -> parent.showResult());
                    t.setRepeats(false);
                    t.start();
                } else {
                    Timer t = new Timer(2000, e -> parent.showBracket());
                    t.setRepeats(false);
                    t.start();
                }
            } else {
                parent.showResult();
            }
        } else {
            turnIndex++;
            phase = Phase.WAITING_SPACE;
            peakScore = 0;
            statusText = "GILIRAN: " + currentPlayer.getName().toUpperCase();

            if (isTopPlayer(currentPlayer)) {
                livePosTop = 0f;
                targetPosTop = 0f;
                prevLivePosTop = 0f;
                frozenTop = false;
                liveScoreTop = 0;
                waveTop.clear();
                for (int i = 0; i < WAVE_POINTS; i++) waveTop.add(0.0);
                trailTop.clear();
                for (int i = 0; i < TRAIL_LENGTH; i++) trailTop.add(0f);
                ringsTop.clear();
                speedLinesTop.clear();
            } else if (isBottomPlayer(currentPlayer)) {
                livePosBottom = 0f;
                targetPosBottom = 0f;
                prevLivePosBottom = 0f;
                frozenBottom = false;
                liveScoreBottom = 0;
                waveBottom.clear();
                for (int i = 0; i < WAVE_POINTS; i++) waveBottom.add(0.0);
                trailBottom.clear();
                for (int i = 0; i < TRAIL_LENGTH; i++) trailBottom.add(0f);
                ringsBottom.clear();
                speedLinesBottom.clear();
            }
        }
    }

    private boolean isTopPlayer(Player p) {
        return playerTop != null && p == playerTop;
    }

    private boolean isBottomPlayer(Player p) {
        return playerBottom != null && p == playerBottom;
    }

    // =========================================================
    //  MULAI GILIRAN -> COUNTDOWN -> SCREAMING
    // =========================================================
    private void attemptStartTurn() {
        if (phase != Phase.WAITING_SPACE || currentPlayer == null) return;
        if (!micActive) {
            statusText = "AKTIFKAN MIKROFON DULU!";
            return;
        }
        startCountdown();
    }

    private void startCountdown() {
        phase = Phase.COUNTDOWN;
        listening = false; // mic dikunci, belum dihitung
        countdownIndex = 0;
        bigOverlayText = countdownStages[0];
        statusText = "BERSIAP, " + currentPlayer.getName().toUpperCase() + "...";
        sound.playTick(); // tick untuk angka pertama ("3") yang tampil langsung

        if (countdownTimer != null) countdownTimer.stop();
        countdownTimer = new Timer(COUNTDOWN_STEP_MS, e -> {
            countdownIndex++;
            if (countdownIndex < countdownStages.length) {
                bigOverlayText = countdownStages[countdownIndex];
                if (bigOverlayText.equals("MULAIIII!")) {
                    sound.playImpact();
                    sound.duckBGM();
                    countdownTimer.stop();
                    beginScreamPhase();
                } else {
                    sound.playTick();
                }
            }
        });
        countdownTimer.start();
    }

    private void beginScreamPhase() {
        phase = Phase.SCREAMING;
        listening = true; // mic mulai dihitung TEPAT saat "MULAIIII!" muncul
        statusText = "TERIAKKKK, " + currentPlayer.getName().toUpperCase() + "!!!";
        screamTicks = 0;
        peakScore = 0;
        sound.startScreamLoop();

        if (screamTimer != null) screamTimer.stop();
        screamTimer = new Timer(100, e -> {
            screamTicks++;
            double score = mic.getScore();
            if (score > peakScore) peakScore = score;

            if (screamTicks * 100 >= SCREAM_DURATION_MS) {
                screamTimer.stop();
                finishTurn();
            }
        });
        screamTimer.start();
    }

    private void finishTurn() {
        listening = false;
        phase = Phase.TURN_DONE;
        bigOverlayText = null;
        currentPlayer.setScore(peakScore);
        statusText = currentPlayer.getName().toUpperCase() + " SELESAI! SKOR: " + (int) peakScore;

        sound.stopScreamLoop();
        sound.restoreBGM();

        float finalNorm = (float) Math.max(0, Math.min(1.0, peakScore / 100.0));
        if (isTopPlayer(currentPlayer)) {
            targetPosTop = finalNorm;
            frozenTop = true;
        } else if (isBottomPlayer(currentPlayer)) {
            targetPosBottom = finalNorm;
            frozenBottom = true;
        }

        if (peakScore >= MAX_REACH_SCORE) {
            triggerFlash();
        }

        Timer pause = new Timer(1500, e -> prepareNextTurn());
        pause.setRepeats(false);
        pause.start();
    }

    private void triggerFlash() {
        flashAlpha = 1f;
        if (flashTimer != null) flashTimer.stop();
        flashTimer = new Timer(30, e -> {
            flashAlpha -= 0.08f;
            if (flashAlpha <= 0f) {
                flashAlpha = 0f;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        flashTimer.start();
    }

    /** Menghentikan SEMUA timer & thread (render, countdown, scream, flash, mikrofon, audio). */
    private void stopEverything() {
        if (renderTimer != null) renderTimer.stop();
        if (countdownTimer != null) countdownTimer.stop();
        if (screamTimer != null) screamTimer.stop();
        if (flashTimer != null) flashTimer.stop();
        if (mic != null) mic.stop();
        if (sound != null) sound.stopAll();
        micActive = false;
        listening = false;
    }

    // =========================================================
    //  LOOP RENDER (60 FPS): posisi lerp, waveform, shake, partikel
    // =========================================================
    private void tickRender() {
        double activeScore = (micActive && listening) ? mic.getScore() : 0;

        if (currentPlayer != null && listening) {
            if (isTopPlayer(currentPlayer)) {
                liveScoreTop = activeScore;
                targetPosTop = (float) Math.max(0, Math.min(1.0, activeScore / 100.0));
            } else if (isBottomPlayer(currentPlayer)) {
                liveScoreBottom = activeScore;
                targetPosBottom = (float) Math.max(0, Math.min(1.0, activeScore / 100.0));
            }
        } else {
            if (!frozenTop && (currentPlayer == null || !isTopPlayer(currentPlayer))) {
                liveScoreTop = 0;
            }
            if (!frozenBottom && (currentPlayer == null || !isBottomPlayer(currentPlayer))) {
                liveScoreBottom = 0;
            }
            if (currentPlayer != null && isTopPlayer(currentPlayer) && !listening && phase != Phase.TURN_DONE) {
                targetPosTop = 0f;
                liveScoreTop = 0;
            }
            if (currentPlayer != null && isBottomPlayer(currentPlayer) && !listening && phase != Phase.TURN_DONE) {
                targetPosBottom = 0f;
                liveScoreBottom = 0;
            }
        }

        // Screen shake: makin dekat MAX (>=70%), makin menggila
        if (activeScore >= SHAKE_TRIGGER_PERCENT) {
            float t = (float) ((activeScore - SHAKE_TRIGGER_PERCENT) / (100.0 - SHAKE_TRIGGER_PERCENT));
            shakeMagnitude = 4f + t * 20f;
        } else {
            shakeMagnitude = 0f;
        }

        prevLivePosTop = livePosTop;
        prevLivePosBottom = livePosBottom;

        float lerpFactor = 0.15f;
        if (!frozenTop) livePosTop += (targetPosTop - livePosTop) * lerpFactor;
        if (!frozenBottom) livePosBottom += (targetPosBottom - livePosBottom) * lerpFactor;

        double sampleTop = (listening && isTopPlayer(currentPlayer)) ? liveScoreTop : 0;
        double sampleBottom = (listening && isBottomPlayer(currentPlayer)) ? liveScoreBottom : 0;
        waveTop.addLast(sampleTop);
        waveTop.removeFirst();
        waveBottom.addLast(sampleBottom);
        waveBottom.removeFirst();

        trailTop.addLast(livePosTop);
        trailTop.removeFirst();
        trailBottom.addLast(livePosBottom);
        trailBottom.removeFirst();

        int w = Math.max(getWidth(), LANE_MARGIN_X * 2 + 10);
        int h = Math.max(getHeight(), 400);
        int left = LANE_MARGIN_X;
        int right = w - LANE_MARGIN_X;
        int barTopY = (int) (h * BAR_TOP_RATIO);
        int barBottomY = (int) (h * BAR_BOTTOM_RATIO);
        int charXTop = (int) (left + (right - left) * livePosTop);
        int charXBottom = (int) (left + (right - left) * livePosBottom);

        boolean topScreamingActive = listening && isTopPlayer(currentPlayer) && liveScoreTop > THRESHOLD;
        boolean bottomScreamingActive = listening && isBottomPlayer(currentPlayer) && liveScoreBottom > THRESHOLD;

        ringTimerTop += 16;
        if (ringTimerTop >= 140) {
            ringTimerTop = 0;
            maybeSpawnRing(ringsTop, charXTop, barTopY, topScreamingActive);
        }
        ringTimerBottom += 16;
        if (ringTimerBottom >= 140) {
            ringTimerBottom = 0;
            maybeSpawnRing(ringsBottom, charXBottom, barBottomY, bottomScreamingActive);
        }

        float speedTop = Math.abs(livePosTop - prevLivePosTop);
        float speedBottom = Math.abs(livePosBottom - prevLivePosBottom);
        speedLineTimerTop += 16;
        if (speedLineTimerTop >= 70) {
            speedLineTimerTop = 0;
            maybeSpawnSpeedLine(speedLinesTop, charXTop, barTopY, topScreamingActive && speedTop > 0.01f);
        }
        speedLineTimerBottom += 16;
        if (speedLineTimerBottom >= 70) {
            speedLineTimerBottom = 0;
            maybeSpawnSpeedLine(speedLinesBottom, charXBottom, barBottomY, bottomScreamingActive && speedBottom > 0.01f);
        }

        updateRings(ringsTop);
        updateRings(ringsBottom);
        updateSpeedLines(speedLinesTop);
        updateSpeedLines(speedLinesBottom);

        if (listening) {
            sound.setScreamIntensity(activeScore / 100.0);
        } else {
            sound.setScreamIntensity(0);
        }

        repaint();
    }

    private void maybeSpawnRing(List<float[]> rings, int x, int y, boolean active) {
        if (!active) return;
        if (rings.size() < 25) {
            rings.add(new float[]{x, y, 14f, 0.9f});
        }
    }

    private void maybeSpawnSpeedLine(List<float[]> lines, int x, int y, boolean active) {
        if (!active) return;
        if (lines.size() < 40) {
            float len = 12 + rng.nextFloat() * 16;
            int yy = y + rng.nextInt(25) - 12;
            lines.add(new float[]{x, yy, len, 0.7f});
        }
    }

    private void updateRings(List<float[]> rings) {
        Iterator<float[]> it = rings.iterator();
        while (it.hasNext()) {
            float[] r = it.next();
            r[2] += 1.8f;
            r[3] -= 0.04f;
            if (r[3] <= 0) it.remove();
        }
    }

    private void updateSpeedLines(List<float[]> lines) {
        Iterator<float[]> it = lines.iterator();
        while (it.hasNext()) {
            float[] p = it.next();
            p[0] -= 6f;
            p[3] -= 0.05f;
            if (p[3] <= 0 || p[0] < 0) it.remove();
        }
    }

    // =========================================================
    //  PAINTING
    // =========================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int w = getWidth();
        int h = getHeight();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int shakeAmt = (int) shakeMagnitude;
        int jx = shakeAmt > 0 ? rng.nextInt(shakeAmt * 2 + 1) - shakeAmt : 0;
        int jy = shakeAmt > 0 ? rng.nextInt(shakeAmt * 2 + 1) - shakeAmt : 0;
        g2.translate(jx, jy);

        g2.setColor(new Color(8, 8, 8));
        g2.fillRect(-40, -40, w + 80, h + 80);

        drawHeaderText(g2, w);
        drawStatus(g2, w);
        drawQueueInfo(g2, w);
        drawLanes(g2, w, h);

        if (bigOverlayText != null && phase == Phase.COUNTDOWN) {
            drawBigOverlay(g2, w, h);
        }

        g2.dispose();

        if (flashAlpha > 0f) {
            Graphics2D g3 = (Graphics2D) g.create();
            g3.setColor(new Color(255, 255, 255, (int) (flashAlpha * 255)));
            g3.fillRect(0, 0, w, h);
            g3.dispose();
        }
    }

    private void drawHeaderText(Graphics2D g2, int w) {
        g2.setFont(new Font("Arial", Font.PLAIN, 14));
        g2.setColor(new Color(180, 180, 180));
        FontMetrics fmSmall = g2.getFontMetrics();
        String sub = "MODE DUEL";
        g2.drawString(sub, (w - fmSmall.stringWidth(sub)) / 2, 34);

        Font titleFont = pickTitleFont(46);
        g2.setFont(titleFont);
        g2.setColor(Color.WHITE);
        FontMetrics fmTitle = g2.getFontMetrics();
        String title = "AAAAAAAAAAAA";
        g2.drawString(title, (w - fmTitle.stringWidth(title)) / 2, 78);
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

    private void drawStatus(Graphics2D g2, int w) {
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.setColor(new Color(255, 210, 60));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(statusText, (w - fm.stringWidth(statusText)) / 2, 112);
    }

    private void drawQueueInfo(Graphics2D g2, int w) {
        if (totalPlayers <= 0) return;
        String info = "GILIRAN " + turnIndex + " DARI " + totalPlayers
                + "  .  SISA ANTREAN: " + GameState.turnQueue.size();
        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        g2.setColor(new Color(140, 140, 140));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(info, (w - fm.stringWidth(info)) / 2, 132);
    }

    private void drawBigOverlay(Graphics2D g2, int w, int h) {
        Font bigFont = pickTitleFont(90);
        g2.setFont(bigFont);
        boolean isGo = bigOverlayText.equals("MULAIIII!");
        g2.setColor(isGo ? new Color(255, 230, 80) : Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(bigOverlayText)) / 2;
        int ty = (int) (h * 0.48);

        g2.setColor(new Color(0, 0, 0, 180));
        g2.drawString(bigOverlayText, tx + 3, ty + 3);
        g2.setColor(isGo ? new Color(255, 230, 80) : Color.WHITE);
        g2.drawString(bigOverlayText, tx, ty);
    }

    private void drawLanes(Graphics2D g2, int w, int h) {
        int left = LANE_MARGIN_X;
        int right = w - LANE_MARGIN_X;

        int barTopY = (int) (h * BAR_TOP_RATIO);
        int barBottomY = (int) (h * BAR_BOTTOM_RATIO);
        int waveAmplitude = (int) (h * WAVE_AMPLITUDE_RATIO);

        boolean topActive = currentPlayer != null && isTopPlayer(currentPlayer) && listening;
        boolean bottomActive = currentPlayer != null && isBottomPlayer(currentPlayer) && listening;

        drawSingleLane(g2, left, right, barTopY, waveAmplitude,
                playerTop, waveTop, livePosTop, liveScoreTop, topActive,
                trailTop, ringsTop, speedLinesTop);

        drawSingleLane(g2, left, right, barBottomY, waveAmplitude,
                playerBottom, waveBottom, livePosBottom, liveScoreBottom, bottomActive,
                trailBottom, ringsBottom, speedLinesBottom);
    }

    private void drawSingleLane(Graphics2D g2, int left, int right, int barY, int waveAmplitude,
                                Player lanePlayer, LinkedList<Double> waveData,
                                float livePosNorm, double liveScore, boolean isActiveScreaming,
                                LinkedList<Float> trail, List<float[]> rings, List<float[]> speedLines) {

        g2.setFont(new Font("Arial", Font.BOLD, 15));
        g2.setColor(Color.WHITE);
        String nameLabel = (lanePlayer != null ? lanePlayer.getName().toUpperCase() : "-");
        g2.drawString(nameLabel, left, barY - waveAmplitude - 12);

        if (isActiveScreaming) {
            drawSpeedLines(g2, speedLines);
        }

        drawWaveLine(g2, waveData, left, right, barY, waveAmplitude);

        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(left, barY, right, barY);

        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        g2.setColor(new Color(160, 160, 160));
        g2.drawString("LOW", left, barY + 24);
        g2.drawString("MAX", right - 30, barY + 24);

        int[] markers = {60, 80, 100};
        for (int i = 0; i < markers.length; i++) {
            float t = (float) i / (markers.length - 1);
            int x = (int) (left + (right - left) * 0.65f + (right - left) * 0.30f * t);
            g2.drawString(String.valueOf(markers[i]), x, barY + 24);
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillOval(x - 2, barY + 11, 4, 4);
            g2.setColor(new Color(160, 160, 160));
        }

        if (isActiveScreaming) {
            drawShockwaveRings(g2, rings);
        }

        if (isActiveScreaming) {
            drawTrail(g2, trail, left, right, barY);
        }

        int charX = (int) (left + (right - left) * livePosNorm);
        drawCharacterIcon(g2, charX, barY, liveScore, isActiveScreaming);
    }

    private void drawSpeedLines(Graphics2D g2, List<float[]> lines) {
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (float[] p : lines) {
            float x = p[0], y = p[1], len = p[2], alpha = p[3];
            g2.setColor(new Color(255, 255, 255, (int) Math.max(0, Math.min(255, alpha * 255))));
            g2.drawLine((int) x, (int) y, (int) (x + len), (int) y);
        }
    }

    private void drawShockwaveRings(Graphics2D g2, List<float[]> rings) {
        g2.setStroke(new BasicStroke(1.6f));
        for (float[] r : rings) {
            float x = r[0], y = r[1], radius = r[2], alpha = r[3];
            g2.setColor(new Color(255, 255, 255, (int) Math.max(0, Math.min(255, alpha * 180))));
            g2.drawOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        }
    }

    private void drawTrail(Graphics2D g2, LinkedList<Float> trail, int left, int right, int barY) {
        int n = trail.size();
        for (int i = 0; i < n - 1; i++) {
            float posNorm = trail.get(i);
            float alpha = ((i + 1) / (float) n) * 0.35f;
            int gx = (int) (left + (right - left) * posNorm);
            g2.setColor(new Color(255, 255, 255, (int) Math.max(0, Math.min(255, alpha * 255))));
            g2.fillOval(gx - 14, barY - 14, 28, 28);
        }
    }

    private void drawWaveLine(Graphics2D g2, LinkedList<Double> data, int left, int right, int baseY, int amplitude) {
        g2.setColor(new Color(255, 255, 255, 210));
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D path = new Path2D.Float();
        int n = data.size();
        float stepX = (float) (right - left) / (n - 1);

        boolean first = true;
        int i = 0;
        for (double v : data) {
            float x = left + stepX * i;
            float norm = (float) (v / 100.0);
            float y = baseY - norm * amplitude;
            if (first) {
                path.moveTo(x, y);
                first = false;
            } else {
                path.lineTo(x, y);
            }
            i++;
        }
        g2.draw(path);
    }

    private void drawCharacterIcon(Graphics2D g2, int x, int barY, double score, boolean isActiveScreaming) {
        int radius = 20;
        boolean screaming = isActiveScreaming && score > THRESHOLD;
        double intensity = Math.max(0, Math.min(1.0, score / 100.0));

        int shakeRange = screaming ? (int) (score / 14.0) : 0;
        int jx = shakeRange > 0 ? rng.nextInt(shakeRange * 2 + 1) - shakeRange : 0;
        int jy = shakeRange > 0 ? rng.nextInt(shakeRange * 2 + 1) - shakeRange : 0;

        int cx = x + jx;
        int cy = barY + jy;

        if (screaming) {
            int rays = 10;
            for (int r = 0; r < rays; r++) {
                double angle = (2 * Math.PI / rays) * r + rng.nextDouble() * 0.15;
                float rayLen = (float) (6 + intensity * 16);
                int x1 = (int) (cx + Math.cos(angle) * (radius + 4));
                int y1 = (int) (cy + Math.sin(angle) * (radius + 4));
                int x2 = (int) (cx + Math.cos(angle) * (radius + 4 + rayLen));
                int y2 = (int) (cy + Math.sin(angle) * (radius + 4 + rayLen));
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 245, 200, (int) (60 + intensity * 100)));
                g2.drawLine(x1, y1, x2, y2);
            }
        }

        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillOval(cx - radius + 4, cy + radius - 2, (radius * 2) - 8, 8);

        g2.setColor(new Color(235, 235, 235));
        g2.fillRoundRect(cx - (int) (radius * 0.85), cy + radius - 6, (int) (radius * 1.7), 12, 10, 10);
        g2.setColor(new Color(190, 190, 190));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(cx - (int) (radius * 0.85), cy + radius - 6, (int) (radius * 1.7), 12, 10, 10);

        RadialGradientPaint headPaint = new RadialGradientPaint(
                new Point2D.Float(cx - radius * 0.3f, cy - radius * 0.3f),
                radius * 1.6f,
                new float[]{0f, 1f},
                new Color[]{Color.WHITE, new Color(225, 225, 225)}
        );
        g2.setPaint(headPaint);
        g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g2.setPaint(null);

        g2.setColor(new Color(0, 0, 0, 40));
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);

        g2.setColor(Color.BLACK);
        Arc2D hair = new Arc2D.Float(cx - radius, cy - radius - 2, radius * 2, radius * 2, 0, 180, Arc2D.CHORD);
        g2.fill(hair);
        Path2D tuft = new Path2D.Float();
        tuft.moveTo(cx - 3, cy - radius - 2);
        tuft.curveTo(cx - 1, cy - radius - 10, cx + 5, cy - radius - 9, cx + 4, cy - radius - 1);
        tuft.closePath();
        g2.fill(tuft);

        if (screaming && intensity > 0.35) {
            g2.setColor(new Color(255, 120, 120, (int) (60 + intensity * 90)));
            g2.fillOval(cx - radius + 2, cy + 1, 6, 4);
            g2.fillOval(cx + radius - 8, cy + 1, 6, 4);
        }

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (screaming) {
            g2.drawLine(cx - 9, cy - 6, cx - 3, cy - 8);
            g2.drawLine(cx + 3, cy - 8, cx + 9, cy - 6);
        } else {
            g2.drawLine(cx - 9, cy - 4, cx - 3, cy - 4);
            g2.drawLine(cx + 3, cy - 4, cx + 9, cy - 4);
        }

        g2.setColor(Color.BLACK);
        if (screaming && intensity > 0.6) {
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - 8, cy - 1, cx - 4, cy - 1);
            g2.drawLine(cx + 4, cy - 1, cx + 8, cy - 1);
        } else {
            g2.fillOval(cx - 8, cy - 2, 4, 4);
            g2.fillOval(cx + 4, cy - 2, 4, 4);
        }

        if (screaming) {
            int mouthW = 13;
            int mouthH = (int) Math.min(14, 5 + intensity * 10);
            g2.setColor(new Color(60, 20, 20));
            g2.fillOval(cx - mouthW / 2, cy + 5, mouthW, mouthH);
            g2.setColor(Color.WHITE);
            g2.fillRect(cx - mouthW / 2 + 2, cy + 5, mouthW - 4, Math.max(2, mouthH / 4));
        } else {
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - 6, cy + 9, cx + 6, cy + 9);
        }
    }
}