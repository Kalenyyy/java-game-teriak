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
    private MicrophoneMeter mic;
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
    private long bigOverlayChangedAt = 0L;

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

    // ---- Ambient background particles (debu mengambang, monokrom, sangat halus) ----
    private static final int AMBIENT_PARTICLE_COUNT = 34;
    // tiap partikel: {x, y, vy, size, alphaBase, phase}
    private final List<float[]> ambientParticles = new ArrayList<>();

    // ---- Score pop-up feedback (saat giliran selesai dengan skor tinggi) ----
    private String scorePopupText = null;
    private float scorePopupProgress = 1f; // 0 = baru muncul, 1 = selesai/fade out
    private int scorePopupX, scorePopupY;
    private Timer scorePopupTimer;

    // ---- Jam animasi global (untuk breathing / pulse / light beam) ----
    private final long animClockStart = System.currentTimeMillis();

    private static final double THRESHOLD = 25.0;
    private static final double SHAKE_TRIGGER_PERCENT = 70.0; // 70% dari MAX
    private static final double MAX_REACH_SCORE = 98.0;
    private final Random rng = new Random();

    // ---- Layout konstan (dipakai bersama render & logic partikel) ----
    private static final int LANE_MARGIN_X = 70;
    private static final float BAR_TOP_RATIO = 0.40f;
    private static final float BAR_BOTTOM_RATIO = 0.66f;
    private static final float WAVE_AMPLITUDE_RATIO = 0.10f;

    // ---- Palet monokrom terpusat (tidak ada warna baru di luar ini) ----
    private static final Color C_BG_0        = new Color(5, 5, 6);
    private static final Color C_BG_1        = new Color(16, 16, 18);
    private static final Color C_BG_2        = new Color(24, 24, 27);
    private static final Color C_SOFT_WHITE  = new Color(240, 240, 242);
    private static final Color C_LIGHT_GRAY  = new Color(196, 196, 201);
    private static final Color C_MID_GRAY    = new Color(128, 128, 133);
    private static final Color C_DIM_GRAY    = new Color(80, 80, 85);
    private static final Color C_DARK_GRAY   = new Color(34, 34, 37);
    private static final Color C_HAIRLINE    = new Color(255, 255, 255, 40);

    // ---- Tombol interaktif ----
    private PremiumButton btnBack;
    private PremiumButton btnMicToggle;
    private PremiumButton btnStart;
    private JLabel lblSubHint;

    // ---- Timer render ----
    private Timer renderTimer;

    public GamePanel(MainFrame parent) {
        this.parent = parent;
        setBackground(C_BG_0);
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
        initAmbientParticles();

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
    //  AMBIENT PARTICLES (debu mengambang di background)
    // =========================================================
    private void initAmbientParticles() {
        ambientParticles.clear();
        for (int i = 0; i < AMBIENT_PARTICLE_COUNT; i++) {
            float x = rng.nextFloat();
            float y = rng.nextFloat();
            float vy = 0.00015f + rng.nextFloat() * 0.00035f;
            float size = 1f + rng.nextFloat() * 2.2f;
            float alphaBase = 0.04f + rng.nextFloat() * 0.10f;
            float phase = rng.nextFloat() * (float) (Math.PI * 2);
            ambientParticles.add(new float[]{x, y, vy, size, alphaBase, phase});
        }
    }

    private void updateAmbientParticles() {
        for (float[] p : ambientParticles) {
            p[1] -= p[2]; // naik pelan ke atas
            p[5] += 0.01f; // phase untuk shimmer halus
            if (p[1] < -0.02f) {
                p[1] = 1.02f;
                p[0] = rng.nextFloat();
            }
        }
    }

    // =========================================================
    //  HEADER  (GlassStrip premium, bukan JPanel transparan polos)
    // =========================================================
    private JPanel buildHeaderPanel() {
        GlassStrip panel = new GlassStrip(GlassStrip.Edge.BOTTOM);
        panel.setLayout(new BorderLayout());
        panel.setPreferredSize(new Dimension(10, 72));

        btnBack = new PremiumButton("\u2190 KEMBALI", 13, PremiumButton.Style.GHOST);
        btnBack.setMaximumSize(new Dimension(150, 34));
        btnBack.setPreferredSize(new Dimension(140, 34));
        btnBack.addActionListener(e -> goBackToMainMenu());

        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 18));
        wrap.setOpaque(false);
        wrap.add(btnBack);

        panel.add(wrap, BorderLayout.WEST);
        return panel;
    }

    /** Kembali ke Main Menu dengan bersih: hentikan semua timer & thread mikrofon/audio dulu. */
    private void goBackToMainMenu() {
        stopEverything();
        parent.showView("MENU_UTAMA");
        parent.requestFocus();
    }

    // =========================================================
    //  FOOTER  (GlassStrip premium)
    // =========================================================
    private JPanel buildFooterPanel() {
        GlassStrip panel = new GlassStrip(GlassStrip.Edge.TOP);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 0, 28, 0));

        btnMicToggle = new PremiumButton("AKTIFKAN MIKROFON", 14, PremiumButton.Style.OUTLINE);
        btnMicToggle.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnMicToggle.setMaximumSize(new Dimension(270, 44));
        btnMicToggle.setPreferredSize(new Dimension(270, 44));
        btnMicToggle.addActionListener(e -> toggleMicrophone());

        btnStart = new PremiumButton("TEKAN SPACE UNTUK MULAI TERIAK", 16, PremiumButton.Style.SOLID);
        btnStart.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStart.setMaximumSize(new Dimension(440, 58));
        btnStart.setPreferredSize(new Dimension(440, 58));
        btnStart.addActionListener(e -> attemptStartTurn());

        lblSubHint = new JLabel("ATAU TEKAN [SPACE]");
        lblSubHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblSubHint.setFont(new Font("Arial", Font.PLAIN, 11));
        lblSubHint.setForeground(C_MID_GRAY);
        lblSubHint.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        panel.add(btnMicToggle);
        panel.add(Box.createRigidArea(new Dimension(0, 14)));
        panel.add(btnStart);
        panel.add(lblSubHint);
        return panel;
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
        mic = new MicrophoneMeter();
    }

    private void toggleMicrophone() {
        micActive = !micActive;
        if (micActive) {
            mic.start();
            btnMicToggle.setText("MATIKAN MIKROFON");
            btnMicToggle.setActiveStyle(true);
        } else {
            mic.stop();
            btnMicToggle.setText("AKTIFKAN MIKROFON");
            btnMicToggle.setActiveStyle(false);
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
        bigOverlayChangedAt = System.currentTimeMillis();
        statusText = "BERSIAP, " + currentPlayer.getName().toUpperCase() + "...";
        sound.playTick(); // tick untuk angka pertama ("3") yang tampil langsung

        if (countdownTimer != null) countdownTimer.stop();
        countdownTimer = new Timer(COUNTDOWN_STEP_MS, e -> {
            countdownIndex++;
            if (countdownIndex < countdownStages.length) {
                bigOverlayText = countdownStages[countdownIndex];
                bigOverlayChangedAt = System.currentTimeMillis();
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
            double score = mic.getCurrentScore();
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

        // SIMPAN SKOR JIKA SOLO
        if (GameState.isSoloMode) {
            ScoreManager.saveScore(currentPlayer.getName(), (int) peakScore);
        }

        sound.stopScreamLoop();
        sound.restoreBGM();

        if (isTopPlayer(currentPlayer)) {
            targetPosTop = (float) Math.max(0, Math.min(1.0, peakScore / 100.0));
            frozenTop = true;
            triggerScorePopup(true);
        } else if (isBottomPlayer(currentPlayer)) {
            targetPosBottom = (float) Math.max(0, Math.min(1.0, peakScore / 100.0));
            frozenBottom = true;
            triggerScorePopup(false);
        }

        if (peakScore >= MAX_REACH_SCORE) triggerFlash();

        Timer pause = new Timer(1500, e -> prepareNextTurn());
        pause.setRepeats(false);
        pause.start();
    }

    // =========================================================
    //  SCORE POPUP (feedback saat giliran selesai dihitung)
    // =========================================================
    private boolean scorePopupIsTop = true;

    private void triggerScorePopup(boolean top) {
        scorePopupIsTop = top;
        scorePopupText = String.valueOf((int) peakScore);
        scorePopupProgress = 0f;
        if (scorePopupTimer != null) scorePopupTimer.stop();
        scorePopupTimer = new Timer(20, e -> {
            scorePopupProgress += 0.02f;
            if (scorePopupProgress >= 1f) {
                scorePopupProgress = 1f;
                scorePopupText = null;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        scorePopupTimer.start();
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
        if (scorePopupTimer != null) scorePopupTimer.stop();
        if (mic != null) mic.stop();
        if (sound != null) sound.stopAll();
        micActive = false;
        listening = false;
    }

    // =========================================================
    //  LOOP RENDER (60 FPS): posisi lerp, waveform, shake, partikel
    // =========================================================
    private void tickRender() {
        double activeScore = (micActive && listening) ? mic.getCurrentScore() : 0;

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
        updateAmbientParticles();

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

    /** Elapsed time (detik) sejak panel ini dibuat -- dipakai untuk animasi ambient/pulse. */
    private double animSeconds() {
        return (System.currentTimeMillis() - animClockStart) / 1000.0;
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
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int shakeAmt = (int) shakeMagnitude;
        int jx = shakeAmt > 0 ? rng.nextInt(shakeAmt * 2 + 1) - shakeAmt : 0;
        int jy = shakeAmt > 0 ? rng.nextInt(shakeAmt * 2 + 1) - shakeAmt : 0;
        g2.translate(jx, jy);

        drawAmbientBackground(g2, w, h);
        drawSpotlightBeams(g2, w, h);
        drawHoloGrid(g2, w, h);
        drawLightStreaks(g2, w, h);
        drawConsoleFrame(g2, w, h);
        drawFrequencyRadar(g2, w, h);
        drawOperatorSilhouette(g2, w, h);

        drawHeaderText(g2, w);
        drawStatus(g2, w);
        drawQueueInfo(g2, w);
        drawCornerHUDLabels(g2, w, h);
        drawLanes(g2, w, h);

        if (scorePopupText != null) {
            drawScorePopup(g2, w, h);
        }

        if (bigOverlayText != null && phase == Phase.COUNTDOWN) {
            drawBigOverlay(g2, w, h);
        }

        drawVignette(g2, w, h);

        g2.dispose();

        if (flashAlpha > 0f) {
            Graphics2D g3 = (Graphics2D) g.create();
            g3.setColor(new Color(255, 255, 255, (int) (flashAlpha * 255)));
            g3.fillRect(0, 0, w, h);
            g3.dispose();
        }
    }

    /**
     * Background premium: gradient dasar dua-lapis + light beam yang bergerak
     * pelan (grayscale) + partikel debu mengambang. Tidak pernah terasa kosong,
     * tapi tetap tenang agar tidak mengganggu gameplay.
     */
    private void drawAmbientBackground(Graphics2D g2, int w, int h) {
        // Lapisan dasar: vertical gradient gelap -> sedikit lebih terang di tengah
        GradientPaint base = new GradientPaint(0, -40, C_BG_0, 0, h * 0.65f, C_BG_1);
        g2.setPaint(base);
        g2.fillRect(-40, -40, w + 80, h + 80);
        g2.setPaint(null);

        // Light beam ambient yang bergeser pelan secara horizontal (radial, sangat redup)
        double t = animSeconds();
        float beamX = (float) (w * (0.5 + 0.35 * Math.sin(t * 0.15)));
        float beamY = h * 0.32f;
        RadialGradientPaint beam = new RadialGradientPaint(
                new Point2D.Float(beamX, beamY),
                Math.max(w, h) * 0.55f,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 16), new Color(255, 255, 255, 0)}
        );
        Paint oldPaint = g2.getPaint();
        g2.setPaint(beam);
        g2.fillRect(-40, -40, w + 80, h + 80);
        g2.setPaint(oldPaint);

        // Partikel debu mengambang (sangat redup, grayscale, shimmer halus)
        for (float[] p : ambientParticles) {
            float px = p[0] * w;
            float py = p[1] * h;
            float size = p[3];
            float shimmer = (float) (0.6 + 0.4 * Math.sin(p[5]));
            int alpha = (int) Math.max(0, Math.min(255, p[4] * shimmer * 255));
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.fillOval((int) px, (int) py, (int) size, (int) size);
        }
    }

    // =========================================================================================
    //  LAPISAN VISUAL BARU — "MILITARY AUDIO COMMAND CENTER" (murni presentasi/dekorasi).
    //  Semua method di bawah ini TIDAK menyentuh/READ state gameplay apapun selain yang sudah
    //  publicly dibaca oleh method render lain (animSeconds, shakeMagnitude, dsb bila perlu),
    //  dan TIDAK mengubah fungsi-fungsi yang sudah ada. Hanya dipanggil tambahan dari
    //  paintComponent() sebagai layer dekoratif baru, di atas/di bawah layer lama.
    // =========================================================================================

    /** Grid holografik tipis menutupi seluruh arena -- kesan lab audio militer/esports command center. */
    private void drawHoloGrid(Graphics2D g2, int w, int h) {
        double t = animSeconds();
        float drift = (float) (Math.sin(t * 0.06) * 6.0);

        g2.setStroke(new BasicStroke(1f));
        int step = 46;

        g2.setColor(new Color(255, 255, 255, 9));
        for (int x = (int) (-step + drift); x < w + step; x += step) {
            g2.drawLine(x, 0, x, h);
        }
        for (int y = (int) (-step + drift * 0.4f); y < h + step; y += step) {
            g2.drawLine(0, y, w, y);
        }

        // Garis penanda lebih terang setiap beberapa sel (kesan blueprint teknis, tetap monokrom)
        g2.setColor(new Color(255, 255, 255, 16));
        int bigStep = step * 4;
        for (int x = (int) (-bigStep + drift); x < w + bigStep; x += bigStep) {
            g2.drawLine(x, 0, x, h);
        }
    }

    /** Bingkai console holografik besar di belakang lajur -- panel kaca lebar dengan sudut siku teknis. */
    private void drawConsoleFrame(Graphics2D g2, int w, int h) {
        int margin = 34;
        int px = margin;
        int py = (int) (h * 0.20);
        int pw = w - margin * 2;
        int ph = (int) (h * 0.56);
        int arc = 26;

        GradientPaint glass = new GradientPaint(
                px, py, new Color(255, 255, 255, 8),
                px, py + ph, new Color(255, 255, 255, 1)
        );
        g2.setPaint(glass);
        g2.fillRoundRect(px, py, pw, ph, arc, arc);
        g2.setPaint(null);

        g2.setColor(new Color(255, 255, 255, 26));
        g2.setStroke(new BasicStroke(1.4f));
        g2.drawRoundRect(px, py, pw, ph, arc, arc);

        // Corner brackets teknis (gaya HUD militer) di keempat sudut console
        int bl = 22;
        g2.setColor(new Color(255, 255, 255, 90));
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        drawCornerBracket(g2, px, py, bl, true, true);
        drawCornerBracket(g2, px + pw, py, bl, false, true);
        drawCornerBracket(g2, px, py + ph, bl, true, false);
        drawCornerBracket(g2, px + pw, py + ph, bl, false, false);
    }

    private void drawCornerBracket(Graphics2D g2, int x, int y, int len, boolean right, boolean down) {
        int dx = right ? len : -len;
        int dy = down ? len : -len;
        g2.drawLine(x, y, x + dx, y);
        g2.drawLine(x, y, x, y + dy);
    }

    /** Label-label teks HUD ala sistem militer di pojok layar (ONLINE, SIGNAL DETECTED, dsb). */
    private void drawCornerHUDLabels(Graphics2D g2, int w, int h) {
        double t = animSeconds();
        boolean blink = ((int) (t * 2)) % 2 == 0;

        Font hudFont = new Font("Consolas", Font.PLAIN, 11);
        if (!java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()).contains("Consolas")) {
            hudFont = new Font("Monospaced", Font.PLAIN, 11);
        }
        g2.setFont(hudFont);

        // Kiri atas: status sistem
        drawHudLine(g2, 18, h * 0.24f, "GAME PANEL : ONLINE", C_MID_GRAY);
        drawHudLine(g2, 18, h * 0.24f + 15, "AUDIO SYSTEM READY", C_DIM_GRAY);
        drawHudLine(g2, 18, h * 0.24f + 30, micActive ? "MIC LINK : ESTABLISHED" : "MIC LINK : STANDBY", micActive ? C_LIGHT_GRAY : C_DIM_GRAY);

        // Kanan atas: status sinyal (berkedip halus saat listening)
        String sigText = listening ? "SIGNAL DETECTED" : "VOICE ANALYZER";
        Color sigColor = listening && blink ? C_SOFT_WHITE : C_MID_GRAY;
        drawHudLineRight(g2, w - 18, h * 0.24f, sigText, sigColor);
        drawHudLineRight(g2, w - 18, h * 0.24f + 15, "DECIBEL SCANNER", C_DIM_GRAY);
        drawHudLineRight(g2, w - 18, h * 0.24f + 30, listening ? "INPUT ACTIVE" : "INPUT IDLE", listening ? C_LIGHT_GRAY : C_DIM_GRAY);

        // Bawah kiri kecil: id unit / kode arena, murni dekoratif
        g2.setFont(hudFont.deriveFont(9f));
        g2.setColor(C_DIM_GRAY);
        g2.drawString("UNIT-ADT // ARENA-07", 18, h * 0.78f);
    }

    private void drawHudLine(Graphics2D g2, float x, float y, String text, Color color) {
        g2.setColor(new Color(0, 0, 0, 140));
        g2.drawString(text, x + 1, y + 1);
        g2.setColor(color);
        g2.drawString(text, x, y);
    }

    private void drawHudLineRight(Graphics2D g2, float xRight, float y, String text, Color color) {
        FontMetrics fm = g2.getFontMetrics();
        float x = xRight - fm.stringWidth(text);
        drawHudLine(g2, x, y, text, color);
    }

    /** Radar spektrum frekuensi kecil di pojok kanan atas -- dekorasi teknis, monokrom. */
    private void drawFrequencyRadar(Graphics2D g2, int w, int h) {
        int r = 34;
        int cx = w - 64;
        int cy = (int) (h * 0.24f) + 56;

        g2.setColor(new Color(255, 255, 255, 14));
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        g2.drawOval(cx - r / 2, cy - r / 2, r, r);
        g2.drawLine(cx - r, cy, cx + r, cy);
        g2.drawLine(cx, cy - r, cx, cy + r);

        // Garis sapuan radar berputar pelan
        double sweep = animSeconds() * 1.1;
        int sx = (int) (cx + Math.cos(sweep) * r);
        int sy = (int) (cy + Math.sin(sweep) * r);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(1.4f));
        g2.drawLine(cx, cy, sx, sy);

        // Titik-titik amplitudo kecil berdasarkan skor aktif (murni visual, tidak mengubah state)
        double activeNorm = listening ? Math.max(liveScoreTop, liveScoreBottom) / 100.0 : 0.0;
        int dots = 8;
        for (int i = 0; i < dots; i++) {
            double a = (Math.PI * 2 / dots) * i;
            double amp = r * (0.3 + 0.7 * activeNorm) * (0.6 + 0.4 * Math.sin(sweep * 2 + i));
            int dx = (int) (cx + Math.cos(a) * amp);
            int dy = (int) (cy + Math.sin(a) * amp);
            g2.setColor(new Color(255, 255, 255, 100));
            g2.fillOval(dx - 2, dy - 2, 4, 4);
        }
    }

    /** Sorotan spotlight silver dari atas -- pencahayaan sinematik AAA, tetap grayscale. */
    private void drawSpotlightBeams(Graphics2D g2, int w, int h) {
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

        double t = animSeconds();
        drawSingleBeam(g2, w * (0.28f + 0.02f * (float) Math.sin(t * 0.2)), w, h);
        drawSingleBeam(g2, w * (0.72f + 0.02f * (float) Math.cos(t * 0.17)), w, h);

        g2.setComposite(old);
    }

    private void drawSingleBeam(Graphics2D g2, float topX, int w, int h) {
        float bottomSpread = w * 0.16f;
        Path2D beam = new Path2D.Float();
        beam.moveTo(topX - 6, -10);
        beam.lineTo(topX + 6, -10);
        beam.lineTo(topX + bottomSpread, h * 0.55f);
        beam.lineTo(topX - bottomSpread, h * 0.55f);
        beam.closePath();

        Paint oldPaint = g2.getPaint();
        GradientPaint beamPaint = new GradientPaint(
                0, -10, new Color(255, 255, 255, 30),
                0, h * 0.55f, new Color(255, 255, 255, 0)
        );
        g2.setPaint(beamPaint);
        g2.fill(beam);
        g2.setPaint(oldPaint);
    }

    /** Garis-garis kilau metalik tipis melintas perlahan -- aksen "metallic light streaks". */
    private void drawLightStreaks(Graphics2D g2, int w, int h) {
        double t = animSeconds();
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 4; i++) {
            double phase = t * (0.09 + i * 0.015) + i * 1.7;
            float y = (float) (h * (0.15 + 0.6 * ((Math.sin(phase) + 1) / 2.0)));
            float len = w * (0.10f + i * 0.03f);
            float x = (float) ((Math.sin(phase * 1.3 + i) + 1) / 2.0) * w;
            int alpha = 12 + i * 4;
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.drawLine((int) (x - len / 2), (int) y, (int) (x + len / 2), (int) y);
        }
    }

    /** Siluet operator futuristik di sisi kiri layar (tampak samping, wajah tersamar bayangan). */
    private void drawOperatorSilhouette(Graphics2D g2, int w, int h) {
        int baseX = 40;
        int baseY = (int) (h * 0.88);
        float breathe = (float) Math.sin(animSeconds() * 1.6) * 1.6f;
        boolean gesture = listening;
        float armLift = gesture ? 14f : 4f + (float) Math.sin(animSeconds() * 1.2) * 2f;

        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));

        // Bayangan lantai
        g2.setColor(new Color(0, 0, 0, 100));
        g2.fillOval(baseX - 28, baseY + 2, 96, 14);

        // Kaki
        g2.setColor(C_DARK_GRAY);
        g2.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(baseX, baseY - 6, baseX - 6, baseY - 60);
        g2.drawLine(baseX + 18, baseY - 6, baseX + 14, baseY - 62);

        // Torso (mantel/armor tinggi, kerah tegak)
        GeneralPath torso = new GeneralPath();
        int ty = (int) (baseY - 60 + breathe);
        torso.moveTo(baseX - 20, ty);
        torso.curveTo(baseX - 26, ty - 40, baseX - 16, ty - 92, baseX - 2, ty - 108);
        torso.lineTo(baseX + 26, ty - 108);
        torso.curveTo(baseX + 40, ty - 90, baseX + 42, ty - 40, baseX + 34, ty);
        torso.closePath();

        GradientPaint torsoPaint = new GradientPaint(
                baseX - 26, ty - 108, new Color(70, 70, 74),
                baseX + 42, ty, new Color(18, 18, 20)
        );
        g2.setPaint(torsoPaint);
        g2.fill(torso);
        g2.setPaint(null);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(torso);

        // Kerah tinggi (high collar)
        g2.setColor(new Color(20, 20, 22));
        g2.fillRoundRect(baseX - 4, ty - 118, 30, 20, 8, 8);
        g2.setColor(new Color(255, 255, 255, 30));
        g2.drawRoundRect(baseX - 4, ty - 118, 30, 20, 8, 8);

        // Bahu mekanis (shoulder piece) kanan, sisi menghadap console
        g2.setColor(new Color(150, 150, 154));
        g2.fillRoundRect(baseX + 14, ty - 104, 30, 18, 10, 10);
        g2.setColor(new Color(60, 60, 64));
        g2.drawRoundRect(baseX + 14, ty - 104, 30, 18, 10, 10);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.fillOval(baseX + 34, ty - 100, 5, 5); // sendi kecil / implant cybernetic

        // Lengan terangkat mengoperasikan console (gesture dinamis)
        int shoulderX = baseX + 40;
        int shoulderY = (int) (ty - 96);
        int elbowX = (int) (shoulderX + 34);
        int elbowY = (int) (shoulderY - 6 - armLift * 0.4f);
        int handX = (int) (elbowX + 30);
        int handY = (int) (elbowY - 8 - armLift);

        g2.setColor(C_MID_GRAY);
        g2.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(shoulderX, shoulderY, elbowX, elbowY);
        g2.setColor(C_LIGHT_GRAY);
        g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(elbowX, elbowY, handX, handY);

        // Sarung tangan futuristik (telapak di console)
        g2.setColor(new Color(230, 230, 232));
        g2.fillOval(handX - 7, handY - 7, 15, 15);
        g2.setColor(new Color(40, 40, 42));
        g2.drawOval(handX - 7, handY - 7, 15, 15);

        // Kepala (profil samping, wajah tersamar bayangan penuh)
        int headCx = baseX + 8;
        int headCy = ty - 128;
        int headR = 15;
        GeneralPath headProfile = new GeneralPath();
        headProfile.moveTo(headCx - headR, headCy);
        headProfile.curveTo(headCx - headR, headCy - headR * 1.15, headCx + headR * 0.4, headCy - headR * 1.2, headCx + headR * 0.9, headCy - headR * 0.5);
        headProfile.curveTo(headCx + headR * 1.15, headCy - headR * 0.05, headCx + headR * 0.95, headCy + headR * 0.55, headCx + headR * 0.55, headCy + headR * 0.85);
        headProfile.curveTo(headCx + headR * 0.15, headCy + headR * 1.05, headCx - headR * 0.6, headCy + headR * 0.7, headCx - headR, headCy);
        headProfile.closePath();

        g2.setColor(new Color(10, 10, 11));
        g2.fill(headProfile);
        g2.setColor(new Color(255, 255, 255, 35));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(headProfile);

        // Rim-light tipis di garis rahang/kepala agar tetap terlihat sebagai siluet premium, bukan flat hitam
        Shape oldClip = g2.getClip();
        g2.clip(headProfile);
        g2.setColor(new Color(255, 255, 255, 50));
        g2.setStroke(new BasicStroke(1.6f));
        g2.drawArc(headCx - headR, headCy - headR, headR * 2, headR * 2, 250, 60);
        g2.setClip(oldClip);

        // Implant cybernetic kecil (garis tipis menyala redup di pelipis)
        g2.setColor(new Color(255, 255, 255, gesture ? 90 : 45));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawLine(headCx + 2, headCy - 2, headCx + 10, headCy - 4);

        g2.setComposite(oldComposite);
    }

    /** Vignette halus di tepi layar agar fokus tetap ke tengah panggung. */
    private void drawVignette(Graphics2D g2, int w, int h) {
        Paint oldPaint = g2.getPaint();
        float radius = Math.max(w, h) * 0.75f;
        RadialGradientPaint vignette = new RadialGradientPaint(
                new Point2D.Float(w / 2f, h / 2f),
                radius,
                new float[]{0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 110)}
        );
        g2.setPaint(vignette);
        g2.fillRect(0, 0, w, h);
        g2.setPaint(oldPaint);
    }

    private void drawHeaderText(Graphics2D g2, int w) {
        g2.setFont(new Font("Arial", Font.PLAIN, 13));
        g2.setColor(C_MID_GRAY);
        FontMetrics fmSmall = g2.getFontMetrics();
        String sub = GameState.isSoloMode ? "M O D E   S O L O" : "M O D E   D U E L";
        if (GameState.isTournamentMode) sub = "M O D E   T U R N A M E N";
        g2.drawString(sub, (w - fmSmall.stringWidth(sub)) / 2, 34);

        Font titleFont = pickTitleFont(46);
        g2.setFont(titleFont);
        FontMetrics fmTitle = g2.getFontMetrics();
        String title = "AAAAAAAAAAAA";
        int tx = (w - fmTitle.stringWidth(title)) / 2;
        int ty = 80;

        // Breathing scale halus untuk judul (tidak mengubah posisi elemen gameplay lain)
        double pulse = 1.0 + 0.012 * Math.sin(animSeconds() * 1.4);
        AffineTransform oldTx = g2.getTransform();
        g2.translate(tx + fmTitle.stringWidth(title) / 2.0, ty - fmTitle.getAscent() / 2.5);
        g2.scale(pulse, pulse);
        g2.translate(-(tx + fmTitle.stringWidth(title) / 2.0), -(ty - fmTitle.getAscent() / 2.5));

        // Glow belakang (beberapa lapisan blur-approx dengan offset kecil & alpha rendah)
        g2.setColor(new Color(255, 255, 255, 26));
        for (int r = 6; r >= 2; r -= 2) {
            g2.drawString(title, tx - r, ty);
            g2.drawString(title, tx + r, ty);
            g2.drawString(title, tx, ty - r);
            g2.drawString(title, tx, ty + r);
        }

        // Shadow tegas untuk kedalaman
        g2.setColor(new Color(0, 0, 0, 200));
        g2.drawString(title, tx + 2, ty + 3);

        // Isi metallic: gradient vertikal soft-white -> abu-abu terang -> soft-white
        GradientPaint metallic = new GradientPaint(
                0, ty - fmTitle.getAscent(), C_SOFT_WHITE,
                0, ty + 6, C_LIGHT_GRAY
        );
        g2.setPaint(metallic);
        g2.drawString(title, tx, ty);
        g2.setPaint(null);

        // Highlight tipis di bagian atas huruf untuk kesan metalik
        g2.setColor(new Color(255, 255, 255, 90));
        g2.drawString(title, tx, ty - 1);

        g2.setTransform(oldTx);
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
        FontMetrics fm = g2.getFontMetrics();
        int sx = (w - fm.stringWidth(statusText)) / 2;
        int sy = 112;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.drawString(statusText, sx + 1, sy + 2);
        g2.setColor(C_SOFT_WHITE);
        g2.drawString(statusText, sx, sy);
    }

    private void drawQueueInfo(Graphics2D g2, int w) {
        if (totalPlayers <= 0) return;
        String info = "GILIRAN " + turnIndex + " DARI " + totalPlayers
                + "   \u00B7   SISA ANTREAN: " + GameState.turnQueue.size();
        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        g2.setColor(C_MID_GRAY);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(info, (w - fm.stringWidth(info)) / 2, 132);
    }

    /** Countdown sinematik: scale-in dengan sedikit overshoot + glow pulse + impact khusus "MULAIIII!". */
    private void drawBigOverlay(Graphics2D g2, int w, int h) {
        boolean isGo = bigOverlayText.equals("MULAIIII!");
        Font bigFont = pickTitleFont(isGo ? 100 : 90);
        g2.setFont(bigFont);
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(bigOverlayText)) / 2;
        int ty = (int) (h * 0.48);

        long elapsed = System.currentTimeMillis() - bigOverlayChangedAt;
        double life = Math.min(1.0, elapsed / 260.0);
        // Ease-out-back sederhana untuk kesan "pop" premium
        double overshoot = 1.7;
        double eased = 1 + (overshoot + 1) * Math.pow(life - 1, 3) + overshoot * Math.pow(life - 1, 2);
        float scale = (float) Math.max(0.05, eased);
        float alpha = (float) Math.min(1.0, life * 1.4);

        int cx = tx + fm.stringWidth(bigOverlayText) / 2;
        int cy = ty - fm.getAscent() / 3;

        AffineTransform oldTx = g2.getTransform();
        g2.translate(cx, cy);
        g2.scale(scale, scale);
        g2.translate(-cx, -cy);

        Color glowColor = isGo ? new Color(255, 255, 255) : new Color(230, 230, 230);

        // Glow radial di belakang teks
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        RadialGradientPaint glow = new RadialGradientPaint(
                new Point2D.Float(cx, cy),
                fm.stringWidth(bigOverlayText) * (isGo ? 0.9f : 0.7f),
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, isGo ? 90 : 55), new Color(255, 255, 255, 0)}
        );
        g2.setPaint(glow);
        g2.fillOval(cx - 160, cy - 100, 320, 200);
        g2.setPaint(null);

        // Bayangan
        g2.setColor(new Color(0, 0, 0, (int) (200 * alpha)));
        g2.drawString(bigOverlayText, tx + 3, ty + 3);

        // Isi utama
        g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), (int) (255 * alpha)));
        g2.drawString(bigOverlayText, tx, ty);

        g2.setComposite(oldComposite);
        g2.setTransform(oldTx);
    }

    /** Feedback skor: teks besar muncul di dekat lajur pemain, membesar lalu memudar ke atas. */
    private void drawScorePopup(Graphics2D g2, int w, int h) {
        int left = LANE_MARGIN_X;
        int right = w - LANE_MARGIN_X;
        int barY = (int) (h * (scorePopupIsTop ? BAR_TOP_RATIO : BAR_BOTTOM_RATIO));
        float posNorm = scorePopupIsTop ? livePosTop : livePosBottom;
        int baseX = (int) (left + (right - left) * posNorm);
        int baseY = barY - 46;

        float p = scorePopupProgress;
        float scale = 0.9f + 0.5f * (float) Math.sin(Math.min(1f, p * 2.2f) * Math.PI / 2);
        float alpha = 1f - Math.max(0, (p - 0.55f) / 0.45f);
        alpha = Math.max(0f, Math.min(1f, alpha));
        int riseY = baseY - (int) (p * 26);

        Font f = new Font("Arial", Font.BOLD, 30);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        String txt = "+" + scorePopupText;
        int tw = fm.stringWidth(txt);

        AffineTransform oldTx = g2.getTransform();
        g2.translate(baseX, riseY);
        g2.scale(scale, scale);

        g2.setColor(new Color(255, 255, 255, (int) (50 * alpha)));
        g2.fillOval(-tw / 2 - 14, -26, tw + 28, 40);

        g2.setColor(new Color(0, 0, 0, (int) (180 * alpha)));
        g2.drawString(txt, -tw / 2 + 1, 6);
        g2.setColor(new Color(255, 255, 255, (int) (255 * alpha)));
        g2.drawString(txt, -tw / 2, 5);

        g2.setTransform(oldTx);
    }

    private void drawLanes(Graphics2D g2, int w, int h) {
        int left = LANE_MARGIN_X;
        int right = w - LANE_MARGIN_X;
        int waveAmplitude = (int) (h * WAVE_AMPLITUDE_RATIO);

        if (GameState.isSoloMode) {
            // Mode Solo: Satu baris di tengah
            int barCenterY = h / 2;
            boolean active = listening && isTopPlayer(currentPlayer);
            drawSingleLane(g2, left, right, barCenterY, waveAmplitude,
                    playerTop, waveTop, livePosTop, liveScoreTop, active,
                    trailTop, ringsTop, speedLinesTop);
        } else {
            // Mode Duel/Tournament: Atas & Bawah
            int barTopY = (int) (h * BAR_TOP_RATIO);
            int barBottomY = (int) (h * BAR_BOTTOM_RATIO);
            boolean topActive = listening && isTopPlayer(currentPlayer);
            boolean bottomActive = listening && isBottomPlayer(currentPlayer);

            drawSingleLane(g2, left, right, barTopY, waveAmplitude,
                    playerTop, waveTop, livePosTop, liveScoreTop, topActive,
                    trailTop, ringsTop, speedLinesTop);
            drawSingleLane(g2, left, right, barBottomY, waveAmplitude,
                    playerBottom, waveBottom, livePosBottom, liveScoreBottom, bottomActive,
                    trailBottom, ringsBottom, speedLinesBottom);
        }
    }

    private void drawSingleLane(Graphics2D g2, int left, int right, int barY, int waveAmplitude,
                                Player lanePlayer, LinkedList<Double> waveData,
                                float livePosNorm, double liveScore, boolean isActiveScreaming,
                                LinkedList<Float> trail, List<float[]> rings, List<float[]> speedLines) {

        // Track/panel lajur bergaya "glass" tipis di belakang bar agar terasa seperti
        // panel kompetisi audio profesional, tanpa mengubah posisi elemen apapun.
        drawLaneTrackPanel(g2, left, right, barY, waveAmplitude);

        g2.setFont(new Font("Arial", Font.BOLD, 15));
        String nameLabel = (lanePlayer != null ? lanePlayer.getName().toUpperCase() : "-");
        g2.setColor(new Color(0, 0, 0, 140));
        g2.drawString(nameLabel, left + 1, barY - waveAmplitude - 11);
        g2.setColor(C_SOFT_WHITE);
        g2.drawString(nameLabel, left, barY - waveAmplitude - 12);

        if (isActiveScreaming) {
            drawSpeedLines(g2, speedLines);
        }

        drawWaveLine(g2, waveData, left, right, barY, waveAmplitude);

        g2.setColor(C_HAIRLINE);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(left, barY, right, barY);
        // highlight tipis tepat di atas garis untuk kesan depth/bevel
        g2.setColor(new Color(255, 255, 255, 14));
        g2.drawLine(left, barY - 1, right, barY - 1);

        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        g2.setColor(C_MID_GRAY);
        g2.drawString("LOW", left, barY + 24);
        g2.drawString("MAX", right - 30, barY + 24);

        int[] markers = {60, 80, 100};
        for (int i = 0; i < markers.length; i++) {
            float t = (float) i / (markers.length - 1);
            int x = (int) (left + (right - left) * 0.65f + (right - left) * 0.30f * t);
            g2.drawString(String.valueOf(markers[i]), x, barY + 24);
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillOval(x - 2, barY + 11, 4, 4);
            g2.setColor(C_MID_GRAY);
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

    /** Panel kaca tipis di belakang setiap lajur -- glassmorphism halus, tidak mengubah layout. */
    private void drawLaneTrackPanel(Graphics2D g2, int left, int right, int barY, int waveAmplitude) {
        int padTop = waveAmplitude + 26;
        int padBottom = 34;
        int px = left - 16;
        int py = barY - padTop;
        int pw = (right - left) + 32;
        int ph = padTop + padBottom;

        GradientPaint glass = new GradientPaint(
                px, py, new Color(255, 255, 255, 10),
                px, py + ph, new Color(255, 255, 255, 2)
        );
        g2.setPaint(glass);
        g2.fillRoundRect(px, py, pw, ph, 22, 22);
        g2.setPaint(null);

        g2.setColor(new Color(255, 255, 255, 22));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(px, py, pw, ph, 22, 22);
    }

    private void drawSpeedLines(Graphics2D g2, List<float[]> lines) {
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (float[] p : lines) {
            float x = p[0], y = p[1], len = p[2], alpha = p[3];
            g2.setColor(new Color(255, 255, 255, (int) Math.max(0, Math.min(255, alpha * 255))));
            g2.drawLine((int) x, (int) y, (int) (x + len), (int) y);
        }
    }

    /** Shockwave dengan lapisan bloom (glow tebal redup di bawah, garis tajam di atas). */
    private void drawShockwaveRings(Graphics2D g2, List<float[]> rings) {
        for (float[] r : rings) {
            float x = r[0], y = r[1], radius = r[2], alpha = r[3];

            g2.setStroke(new BasicStroke(6f));
            g2.setColor(new Color(255, 255, 255, (int) Math.max(0, Math.min(255, alpha * 40))));
            g2.drawOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));

            g2.setStroke(new BasicStroke(1.6f));
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

    /** Waveform dengan bloom: beberapa lapisan stroke tebal-redup di bawah garis inti terang. */
    private void drawWaveLine(Graphics2D g2, LinkedList<Double> data, int left, int right, int baseY, int amplitude) {
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

        // Lapisan bloom (glow) -- lebar menurun, alpha meningkat
        g2.setColor(new Color(255, 255, 255, 18));
        g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);
        g2.setColor(new Color(255, 255, 255, 45));
        g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);

        // Garis inti tajam
        g2.setColor(new Color(255, 255, 255, 225));
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);
    }

    /**
     * MASCOT REDESIGN — bentuk & identitas karakter TIDAK diubah dari versi
     * sebelumnya (silhouette, ekspresi 3-tingkat, bandana, palet warna semua
     * identik). Yang ditambahkan hanyalah:
     *  - Soft ambient glow di belakang kepala saat berteriak (bloom halus).
     *  - Rim-light tipis di sisi kepala untuk kesan kedalaman/pencahayaan.
     *  - Idle breathing animation (bob vertikal sangat halus) saat tidak
     *    sedang aktif berteriak, supaya karakter tidak terasa statis.
     * Karakter tetap digambar TERAKHIR (paling atas) di drawSingleLane.
     */
    private void drawCharacterIcon(Graphics2D g2, int x, int barY, double score, boolean isActiveScreaming) {
        int radius = 21;
        boolean screaming = isActiveScreaming && score > THRESHOLD;
        double intensity = Math.max(0, Math.min(1.0, score / 100.0));

        // 3 tingkat ekspresi: CALM -> FOCUSED (menahan) -> INTENSE (puncak teriak)
        int tier = !screaming ? 0 : (intensity > 0.6 ? 2 : 1);

        int shakeRange = screaming ? (int) (score / 14.0) : 0;
        int jx = shakeRange > 0 ? rng.nextInt(shakeRange * 2 + 1) - shakeRange : 0;
        int jy = shakeRange > 0 ? rng.nextInt(shakeRange * 2 + 1) - shakeRange : 0;

        // Idle breathing: bob vertikal halus (~1.5px) saat tidak berteriak
        int breathe = 0;
        if (!screaming) {
            breathe = (int) Math.round(1.4 * Math.sin(animSeconds() * 2.1 + x * 0.01));
        }

        int cx = x + jx;
        int cy = barY + jy + breathe;

        // ---- Ambient glow di belakang kepala saat berteriak (bloom monokrom) ----
        if (screaming) {
            Paint oldPaint = g2.getPaint();
            RadialGradientPaint headGlow = new RadialGradientPaint(
                    new Point2D.Float(cx, cy),
                    radius * (1.6f + (float) intensity * 1.1f),
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, (int) (50 + intensity * 70)), new Color(255, 255, 255, 0)}
            );
            g2.setPaint(headGlow);
            g2.fillOval((int) (cx - radius * 2.6), (int) (cy - radius * 2.6), (int) (radius * 5.2), (int) (radius * 5.2));
            g2.setPaint(oldPaint);
        }

        // ---- Rays energi saat berteriak (posisi & warna sama seperti sebelumnya) ----
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

        // ---- Bayangan lembut (drop shadow sedikit lebih dalam untuk kesan depth) ----
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillOval(cx - radius + 4, cy + radius - 1, (radius * 2) - 8, 7);
        g2.setColor(new Color(0, 0, 0, 55));
        g2.fillOval(cx - radius + 2, cy + radius - 3, (radius * 2) - 4, 9);

        // ---- Bahu / badan kecil (tidak berubah dari versi sebelumnya) ----
        g2.setColor(new Color(235, 235, 235));
        g2.fillRoundRect(cx - (int) (radius * 0.85), cy + radius - 6, (int) (radius * 1.7), 12, 10, 10);
        g2.setColor(new Color(190, 190, 190));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(cx - (int) (radius * 0.85), cy + radius - 6, (int) (radius * 1.7), 12, 10, 10);

        // ---- Kepala: silhouette stylized (bukan lingkaran polos) ----
        GeneralPath head = buildHeadShape(cx, cy, radius);

        RadialGradientPaint headPaint = new RadialGradientPaint(
                new Point2D.Float(cx - radius * 0.3f, cy - radius * 0.3f),
                radius * 1.6f,
                new float[]{0f, 1f},
                new Color[]{Color.WHITE, new Color(225, 225, 225)}
        );
        g2.setPaint(headPaint);
        g2.fill(head);
        g2.setPaint(null);

        g2.setColor(new Color(0, 0, 0, 40));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(head);

        // ---- Rim-light tipis di sisi kanan kepala (kesan pencahayaan terarah) ----
        Shape oldClip = g2.getClip();
        g2.clip(head);
        g2.setColor(new Color(255, 255, 255, 70));
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc((int) (cx - radius * 0.98), (int) (cy - radius * 1.05), (int) (radius * 1.96), (int) (radius * 2.1), -70, 90);
        g2.setClip(oldClip);

        // ---- Rambut energik bergaya "spike" (mengganti setengah-lingkaran polos) ----
        drawEnergyHair(g2, cx, cy, radius);

        // ---- Bandana kecil di dahi -- aksesori tema kompetisi teriak (hitam, warna lama) ----
        drawHeadband(g2, cx, cy, radius);

        // ---- Pipi merona hanya di puncak teriakan (warna sama seperti sebelumnya) ----
        if (tier == 2) {
            g2.setColor(new Color(255, 120, 120, (int) (60 + intensity * 90)));
            g2.fillOval(cx - radius + 1, cy + 2, 7, 5);
            g2.fillOval(cx + radius - 8, cy + 2, 7, 5);
        }

        drawEyebrows(g2, cx, cy, radius, tier);
        drawEyes(g2, cx, cy, radius, tier);
        drawMouth(g2, cx, cy, tier, intensity);
    }

    /** Silhouette kepala non-lingkaran: dahi lebih lebar, dagu meruncing lembut. */
    private GeneralPath buildHeadShape(int cx, int cy, int radius) {
        GeneralPath path = new GeneralPath();
        path.moveTo(cx, cy - radius * 1.05);
        path.curveTo(cx + radius * 0.75, cy - radius * 1.0, cx + radius * 1.02, cy - radius * 0.35, cx + radius * 0.95, cy - radius * 0.05);
        path.curveTo(cx + radius * 0.88, cy + radius * 0.45, cx + radius * 0.62, cy + radius * 0.85, cx + radius * 0.22, cy + radius * 1.08);
        path.curveTo(cx + radius * 0.08, cy + radius * 1.16, cx - radius * 0.08, cy + radius * 1.16, cx - radius * 0.22, cy + radius * 1.08);
        path.curveTo(cx - radius * 0.62, cy + radius * 0.85, cx - radius * 0.88, cy + radius * 0.45, cx - radius * 0.95, cy - radius * 0.05);
        path.curveTo(cx - radius * 1.02, cy - radius * 0.35, cx - radius * 0.75, cy - radius * 1.0, cx, cy - radius * 1.05);
        path.closePath();
        return path;
    }

    /** Rambut spike energik (hitam, warna sama seperti hair lama) -- lebih berkarakter dari setengah-lingkaran polos. */
    private void drawEnergyHair(Graphics2D g2, int cx, int cy, int radius) {
        g2.setColor(Color.BLACK);
        GeneralPath hair = new GeneralPath();
        float topY = cy - radius * 1.05f;

        float[] spikeX = {-0.85f, -0.5f, -0.15f, 0.2f, 0.55f, 0.85f};
        float[] spikeH = {0.35f, 0.55f, 0.75f, 0.65f, 0.5f, 0.32f};

        hair.moveTo(cx + spikeX[0] * radius, topY + radius * 0.5f);
        for (int i = 0; i < spikeX.length; i++) {
            float baseX = cx + spikeX[i] * radius;
            float tipX = baseX + radius * 0.08f;
            float tipY = topY - spikeH[i] * radius;
            hair.lineTo(tipX, tipY);
            float nextX = (i + 1 < spikeX.length) ? cx + spikeX[i + 1] * radius : cx + 0.95f * radius;
            hair.lineTo((baseX + nextX) / 2f, topY + radius * 0.15f);
        }
        hair.lineTo(cx + 0.95f * radius, topY + radius * 0.55f);
        hair.curveTo(cx + radius * 0.4f, topY + radius * 0.15f, cx - radius * 0.4f, topY + radius * 0.15f, cx - 0.85f * radius, topY + radius * 0.5f);
        hair.closePath();
        g2.fill(hair);
    }

    /** Bandana/headband kecil -- aksesori tema kompetisi (hitam, tidak menambah warna baru). */
    private void drawHeadband(Graphics2D g2, int cx, int cy, int radius) {
        g2.setColor(Color.BLACK);
        int bandY = (int) (cy - radius * 0.42);
        g2.fillRect(cx - radius + 1, bandY, radius * 2 - 2, (int) (radius * 0.16));

        // Simpul kecil di sisi kanan biar terasa seperti ikat kepala petarung
        GeneralPath knot = new GeneralPath();
        knot.moveTo(cx + radius * 0.85f, bandY);
        knot.lineTo(cx + radius * 1.15f, bandY - radius * 0.05f);
        knot.lineTo(cx + radius * 1.05f, bandY + radius * 0.22f);
        knot.closePath();
        g2.fill(knot);
    }

    /** Alis dinamis: relaks (tenang) -> menukik fokus (menahan) -> tajam terangkat (puncak teriak). */
    private void drawEyebrows(Graphics2D g2, int cx, int cy, int radius, int tier) {
        g2.setColor(Color.BLACK);
        int browY = cy - (int) (radius * 0.28);

        if (tier == 0) {
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            QuadCurve2D leftBrow = new QuadCurve2D.Float(cx - 11, browY + 1, cx - 6, browY - 2, cx - 2, browY);
            QuadCurve2D rightBrow = new QuadCurve2D.Float(cx + 2, browY, cx + 6, browY - 2, cx + 11, browY + 1);
            g2.draw(leftBrow);
            g2.draw(rightBrow);
        } else if (tier == 1) {
            g2.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - 12, browY - 1, cx - 3, browY + 4);
            g2.drawLine(cx + 3, browY + 4, cx + 12, browY - 1);
        } else {
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - 13, browY + 3, cx - 3, browY - 5);
            g2.drawLine(cx + 3, browY - 5, cx + 13, browY + 3);
        }
    }

    /** Mata ekspresif: bulat tenang -> menyipit menahan -> membelalak besar di puncak teriak. */
    private void drawEyes(Graphics2D g2, int cx, int cy, int radius, int tier) {
        g2.setColor(Color.BLACK);
        int eyeY = cy - 2;

        if (tier == 0) {
            g2.fillOval(cx - 8, eyeY, 4, 4);
            g2.fillOval(cx + 4, eyeY, 4, 4);
        } else if (tier == 1) {
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - 9, eyeY + 2, cx - 3, eyeY + 2);
            g2.drawLine(cx + 3, eyeY + 2, cx + 9, eyeY + 2);
        } else {
            g2.setColor(Color.WHITE);
            g2.fillOval(cx - 10, eyeY - 3, 8, 8);
            g2.fillOval(cx + 2, eyeY - 3, 8, 8);
            g2.setColor(new Color(0, 0, 0, 60));
            g2.drawOval(cx - 10, eyeY - 3, 8, 8);
            g2.drawOval(cx + 2, eyeY - 3, 8, 8);
            g2.setColor(Color.BLACK);
            g2.fillOval(cx - 7, eyeY - 1, 4, 4);
            g2.fillOval(cx + 5, eyeY - 1, 4, 4);
        }
    }

    /** Mulut: garis tipis tenang -> gigit-menahan -> terbuka lebar dengan highlight gigi di puncak. */
    private void drawMouth(Graphics2D g2, int cx, int cy, int tier, double intensity) {
        if (tier == 0) {
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - 6, cy + 9, cx + 6, cy + 9);
        } else if (tier == 1) {
            g2.setColor(new Color(60, 20, 20));
            g2.fillRoundRect(cx - 7, cy + 7, 14, 3, 3, 3);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(cx - 7, cy + 7, 14, 3, 3, 3);
        } else {
            int mouthW = 15;
            int mouthH = (int) Math.min(16, 8 + intensity * 10);
            g2.setColor(new Color(60, 20, 20));
            g2.fillOval(cx - mouthW / 2, cy + 4, mouthW, mouthH);
            g2.setColor(Color.WHITE);
            g2.fillRect(cx - mouthW / 2 + 2, cy + 4, mouthW - 4, Math.max(2, mouthH / 4));
            g2.setColor(new Color(30, 8, 8));
            g2.fillOval(cx - 3, cy + 4 + mouthH - 5, 6, 5);
        }
    }

    // =========================================================================================
    //  GlassStrip — panel header/footer bergaya glassmorphism tipis (hairline glow + gradient).
    //  Murni komponen presentasi, tidak menyimpan/mempengaruhi state gameplay apapun.
    // =========================================================================================
    private static class GlassStrip extends JPanel {
        enum Edge { TOP, BOTTOM }
        private final Edge edge;

        GlassStrip(Edge edge) {
            this.edge = edge;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint glass = (edge == Edge.TOP)
                    ? new GradientPaint(0, 0, new Color(255, 255, 255, 10), 0, h, new Color(255, 255, 255, 0))
                    : new GradientPaint(0, 0, new Color(255, 255, 255, 0), 0, h, new Color(255, 255, 255, 10));
            g2.setPaint(glass);
            g2.fillRect(0, 0, w, h);

            g2.setColor(new Color(255, 255, 255, 30));
            g2.setStroke(new BasicStroke(1f));
            if (edge == Edge.TOP) {
                g2.drawLine(0, h - 1, w, h - 1);
            } else {
                g2.drawLine(0, 0, w, 0);
            }
            g2.dispose();
        }
    }

    // =========================================================================================
    //  PremiumButton — pengganti JButton polos: rounded corners, layered shadow, border glow
    //  saat hover/focus, animasi scale halus saat ditekan. Tidak membawa logic gameplay apapun;
    //  hanya memanggil ActionListener yang didaftarkan seperti JButton biasa.
    // =========================================================================================
    private static class PremiumButton extends JButton {
        enum Style { SOLID, OUTLINE, GHOST }

        private final Style style;
        private float hoverT = 0f;   // 0..1, dianimasikan menuju target saat hover berubah
        private float pressT = 0f;   // 0..1, untuk efek scale saat ditekan
        private boolean hovering = false;
        private boolean pressed = false;
        private boolean active = false; // dipakai btnMicToggle untuk menandakan "mic aktif"
        private final Timer animTimer;

        PremiumButton(String text, int fontSize, Style style) {
            super(text);
            this.style = style;
            setFont(new Font("Arial", Font.BOLD, fontSize));
            setForeground(C_SOFT_WHITE);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.CENTER_ALIGNMENT);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovering = true; }
                @Override public void mouseExited(MouseEvent e) { hovering = false; pressed = false; }
                @Override public void mousePressed(MouseEvent e) { pressed = true; }
                @Override public void mouseReleased(MouseEvent e) { pressed = false; }
            });

            animTimer = new Timer(16, e -> {
                float hoverTarget = hovering ? 1f : 0f;
                float pressTarget = pressed ? 1f : 0f;
                hoverT += (hoverTarget - hoverT) * 0.2f;
                pressT += (pressTarget - pressT) * 0.35f;
                repaint();
            });
            animTimer.start();
        }

        void setActiveStyle(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float scale = 1f - pressT * 0.035f;
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(scale, scale);
            g2.translate(-w / 2.0, -h / 2.0);

            int arc = Math.min(18, h / 2);
            RoundRectangle2D shape = new RoundRectangle2D.Float(2, 2, w - 4, h - 4, arc, arc);

            // Bayangan berlapis di bawah tombol (depth)
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fill(new RoundRectangle2D.Float(3, 5, w - 6, h - 4, arc, arc));

            switch (style) {
                case SOLID: {
                    GradientPaint bg = new GradientPaint(0, 2, new Color(60, 60, 64), 0, h - 2, new Color(20, 20, 22));
                    g2.setPaint(bg);
                    g2.fill(shape);
                    g2.setPaint(null);
                    break;
                }
                case OUTLINE: {
                    GradientPaint bg = new GradientPaint(0, 2, new Color(255, 255, 255, active ? 26 : 14), 0, h - 2, new Color(255, 255, 255, 4));
                    g2.setPaint(bg);
                    g2.fill(shape);
                    g2.setPaint(null);
                    break;
                }
                case GHOST:
                default: {
                    if (hoverT > 0.01f) {
                        g2.setColor(new Color(255, 255, 255, (int) (18 * hoverT)));
                        g2.fill(shape);
                    }
                    break;
                }
            }

            // Glow border saat hover/aktif
            float glowStrength = Math.max(hoverT, active ? 0.8f : 0f);
            if (glowStrength > 0.01f && style != Style.GHOST) {
                g2.setColor(new Color(255, 255, 255, (int) (70 * glowStrength)));
                g2.setStroke(new BasicStroke(2.4f));
                g2.draw(shape);
            }

            // Border dasar
            Color borderColor = style == Style.GHOST
                    ? new Color(255, 255, 255, (int) (60 + 80 * hoverT))
                    : new Color(255, 255, 255, (int) (110 + 90 * hoverT));
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(shape);

            // Highlight tipis di tepi atas (kesan glass/premium)
            g2.setColor(new Color(255, 255, 255, 40));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(6, 3, w - 6, 3);

            // Teks dengan sedikit glow saat hover
            FontMetrics fm = g2.getFontMetrics(getFont());
            String text = getText();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h + fm.getAscent()) / 2 - 3;

            if (hoverT > 0.02f) {
                g2.setColor(new Color(255, 255, 255, (int) (90 * hoverT)));
                g2.setFont(getFont());
                g2.drawString(text, tx - 1, ty);
                g2.drawString(text, tx + 1, ty);
            }

            g2.setColor(new Color(0, 0, 0, 160));
            g2.setFont(getFont());
            g2.drawString(text, tx + 1, ty + 1);
            g2.setColor(active ? C_SOFT_WHITE : getForeground());
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }
}