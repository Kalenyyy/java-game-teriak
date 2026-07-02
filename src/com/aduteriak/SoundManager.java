package com.aduteriak;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.util.Random;

/**
 * SoundManager.java
 * Mengelola BGM & SFX untuk Mode Duel menggunakan javax.sound.sampled (Clip).
 *
 * CATATAN PENTING:
 * Semua audio di sini disintesis secara PROSEDURAL langsung di memori (bukan
 * memuat file .wav eksternal berlisensi), supaya kelas ini langsung bisa
 * dipakai tanpa perlu menyertakan aset musik. Kalau nanti kamu punya file
 * .wav BGM/SFX sendiri, cukup ganti isi method generateX() di bagian bawah
 * agar membaca file itu lewat AudioSystem.getAudioInputStream(new File(...))
 * alih-alih men-generate gelombang secara manual.
 *
 * Semua Clip di-preload secara ASYNCHRONOUS pada thread terpisah saat
 * SoundManager dibuat, supaya tidak ada delay/stutter saat SFX pertama kali
 * dipicu di tengah permainan.
 */
public class SoundManager {

    private static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 1, true, false);

    private volatile boolean ready = false;

    // ---- Background Music ----
    private Clip bgmClip;
    private FloatControl bgmGain;
    private float bgmBaseGainDb = -10f;
    private float bgmDuckGainDb = -28f;

    // ---- Scream echo / distortion loop (volume mengikuti dB teriakan) ----
    private Clip screamClip;
    private FloatControl screamGain;
    private float screamMinGainDb = -80f;
    private float screamMaxGainDb = -4f;

    // ---- SFX one-shot ----
    private byte[] tickBytes;
    private byte[] impactBytes;

    public SoundManager() {
        Thread loader = new Thread(this::preload, "SoundManager-Preload");
        loader.setDaemon(true);
        loader.start();
    }

    private void preload() {
        try {
            byte[] bgmData = generateBgmLoop(2000);
            bgmClip = AudioSystem.getClip();
            bgmClip.open(new AudioInputStream(new ByteArrayInputStream(bgmData), FORMAT, bgmData.length / 2));
            if (bgmClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                bgmGain = (FloatControl) bgmClip.getControl(FloatControl.Type.MASTER_GAIN);
                bgmBaseGainDb = clampGain(bgmGain, -10f);
                bgmDuckGainDb = clampGain(bgmGain, -28f);
            }

            byte[] screamData = generateScreamTexture(1500);
            screamClip = AudioSystem.getClip();
            screamClip.open(new AudioInputStream(new ByteArrayInputStream(screamData), FORMAT, screamData.length / 2));
            if (screamClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                screamGain = (FloatControl) screamClip.getControl(FloatControl.Type.MASTER_GAIN);
                screamMinGainDb = clampGain(screamGain, -80f);
                screamMaxGainDb = clampGain(screamGain, -4f);
            }

            tickBytes = generateTone(1400, 55, 0.5, true);
            impactBytes = generateImpact(260);

            ready = true;
        } catch (Exception ex) {
            System.err.println("SoundManager: gagal menyiapkan audio (" + ex.getMessage()
                    + "). Game tetap berjalan tanpa suara.");
        }
    }

    private float clampGain(FloatControl control, float wanted) {
        return Math.max(control.getMinimum(), Math.min(control.getMaximum(), wanted));
    }

    // =========================================================
    //  KONTROL BGM
    // =========================================================
    public void startBGM() {
        runWhenReady(() -> {
            if (bgmClip == null) return;
            bgmClip.setFramePosition(0);
            bgmClip.setLoopPoints(0, -1);
            if (bgmGain != null) bgmGain.setValue(bgmBaseGainDb);
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
        });
    }

    /** Volume BGM mengecil (ducking) saat countdown selesai / pemain mulai berteriak. */
    public void duckBGM() {
        runWhenReady(() -> {
            if (bgmGain != null) bgmGain.setValue(bgmDuckGainDb);
        });
    }

    public void restoreBGM() {
        runWhenReady(() -> {
            if (bgmGain != null) bgmGain.setValue(bgmBaseGainDb);
        });
    }

    // =========================================================
    //  SFX COUNTDOWN (tick untuk 3/2/1, impact untuk MULAIIII!)
    // =========================================================
    public void playTick() {
        runWhenReady(() -> playOneShot(tickBytes));
    }

    public void playImpact() {
        runWhenReady(() -> playOneShot(impactBytes));
    }

    private void playOneShot(byte[] data) {
        if (data == null) return;
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(new AudioInputStream(new ByteArrayInputStream(data), FORMAT, data.length / 2));
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (Exception ignored) {
        }
    }

    // =========================================================
    //  SFX SCREAM ECHO (loop kontinu, gain mengikuti dB real-time)
    // =========================================================
    public void startScreamLoop() {
        runWhenReady(() -> {
            if (screamClip == null) return;
            screamClip.setFramePosition(0);
            screamClip.setLoopPoints(0, -1);
            if (screamGain != null) screamGain.setValue(screamMinGainDb);
            screamClip.loop(Clip.LOOP_CONTINUOUSLY);
        });
    }

    /** intensity01 = 0.0 (diam) sampai 1.0 (teriakan maksimal). */
    public void setScreamIntensity(double intensity01) {
        runWhenReady(() -> {
            if (screamGain == null) return;
            double clamped = Math.max(0, Math.min(1, intensity01));
            float target = (float) (screamMinGainDb + (screamMaxGainDb - screamMinGainDb) * clamped);
            screamGain.setValue(target);
        });
    }

    public void stopScreamLoop() {
        runWhenReady(() -> {
            if (screamClip == null) return;
            if (screamGain != null) screamGain.setValue(screamMinGainDb);
            screamClip.stop();
        });
    }

    public void stopAll() {
        runWhenReady(() -> {
            if (bgmClip != null) bgmClip.stop();
            if (screamClip != null) screamClip.stop();
        });
    }

    private void runWhenReady(Runnable r) {
        if (!ready) return; // preload sangat cepat (prosedural), jadi kondisi ini jarang kena
        try {
            r.run();
        } catch (Exception ignored) {
        }
    }

    // =========================================================
    //  SINTESIS AUDIO PROSEDURAL (tanpa file eksternal)
    // =========================================================

    private byte[] generateTone(double freqHz, int durationMs, double amplitude, boolean fadeOut) {
        int sampleRate = 44100;
        int totalSamples = (int) (sampleRate * durationMs / 1000.0);
        byte[] data = new byte[totalSamples * 2];
        for (int i = 0; i < totalSamples; i++) {
            double t = i / (double) sampleRate;
            double env = fadeOut ? Math.max(0, 1.0 - (double) i / totalSamples) : 1.0;
            double sample = Math.sin(2 * Math.PI * freqHz * t) * amplitude * env;
            writeSample(data, i, sample);
        }
        return data;
    }

    private byte[] generateImpact(int durationMs) {
        int sampleRate = 44100;
        int totalSamples = (int) (sampleRate * durationMs / 1000.0);
        byte[] data = new byte[totalSamples * 2];
        Random r = new Random();
        for (int i = 0; i < totalSamples; i++) {
            double frac = i / (double) totalSamples;
            double env = Math.exp(-frac * 6.0); // decay cepat seperti gong/ledakan pecah
            double tone = Math.sin(2 * Math.PI * 90 * (i / (double) sampleRate));
            double noise = (r.nextDouble() * 2 - 1);
            double sample = (tone * 0.6 + noise * 0.4) * env * 0.9;
            writeSample(data, i, sample);
        }
        return data;
    }

    /** Loop BGM: drone tegang + tremolo + denyut metalik bertema industrial minimalis. */
    private byte[] generateBgmLoop(int durationMs) {
        int sampleRate = 44100;
        int totalSamples = (int) (sampleRate * durationMs / 1000.0);
        byte[] data = new byte[totalSamples * 2];
        for (int i = 0; i < totalSamples; i++) {
            double t = i / (double) sampleRate;
            double drone = Math.sin(2 * Math.PI * 55 * t) * 0.5;
            double tremolo = 0.6 + 0.4 * Math.sin(2 * Math.PI * 2.0 * t); // denyut tegang ~2Hz
            double pulseWindow = i % (sampleRate / 2);
            double pulseEnv = (pulseWindow < 400) ? Math.exp(-pulseWindow / 60.0) : 0;
            double metallic = Math.sin(2 * Math.PI * 880 * t) * pulseEnv * 0.35;
            double sample = (drone * tremolo + metallic) * 0.7;
            writeSample(data, i, sample);
        }
        return data;
    }

    /** Tekstur distorsi bernada tinggi (dua nada berdekatan -> beating, terasa seperti feedback). */
    private byte[] generateScreamTexture(int durationMs) {
        int sampleRate = 44100;
        int totalSamples = (int) (sampleRate * durationMs / 1000.0);
        byte[] data = new byte[totalSamples * 2];
        for (int i = 0; i < totalSamples; i++) {
            double t = i / (double) sampleRate;
            double a = Math.sin(2 * Math.PI * 1400 * t);
            double b = Math.sin(2 * Math.PI * 1460 * t);
            double sawEdge = ((t * 3000) % 1.0) * 2 - 1; // sedikit gerigi untuk kesan distorsi
            double sample = (a * 0.5 + b * 0.5) * 0.7 + sawEdge * 0.15;
            writeSample(data, i, sample);
        }
        return data;
    }

    private void writeSample(byte[] data, int index, double sampleValue) {
        double clamped = Math.max(-1.0, Math.min(1.0, sampleValue));
        short val = (short) (clamped * Short.MAX_VALUE);
        data[index * 2] = (byte) (val & 0xff);
        data[index * 2 + 1] = (byte) ((val >> 8) & 0xff);
    }
}