package com.aduteriak;

import javax.sound.sampled.*;

/**
 * AudioCapture.java
 * Menangani pembacaan input mikrofon secara real-time menggunakan
 * javax.sound.sampled (TargetDataLine), berjalan di thread terpisah
 * agar GUI (EDT) tidak pernah freeze/blocking menunggu data audio.
 *
 * Class ini murni bertanggung jawab atas AKUISISI & PERHITUNGAN skor
 * kenyaringan (dB -> skala 0-100). Tidak ada logika gameplay di sini,
 * supaya bisa dipakai ulang oleh panel manapun (GamePanel, dsb).
 */
public class AudioCapture {

    private final AudioFormat format;
    private TargetDataLine line;
    private volatile boolean running = false;
    private volatile double currentScore = 0;
    private Thread thread;

    public AudioCapture(AudioFormat format) {
        this.format = format;
    }

    /** Membuka line mikrofon default sistem dan mulai membaca di thread terpisah. */
    public void start() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
        } catch (Exception ex) {
            System.err.println("Gagal membuka mikrofon: " + ex.getMessage());
            return;
        }

        running = true;
        thread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (running) {
                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) {
                    currentScore = computeScore(buffer, count);
                }
            }
        }, "AudioCapture-Thread");
        thread.setDaemon(true);
        thread.start();
    }

    /** Menghitung RMS -> dB -> dinormalisasi ke skala 0-100. */
    private double computeScore(byte[] buffer, int count) {
        long sum = 0;
        int samples = count / 2;
        for (int i = 0; i + 1 < count; i += 2) {
            int low = buffer[i] & 0xff;
            int high = buffer[i + 1];
            short sample = (short) ((high << 8) | low);
            sum += (long) sample * sample;
        }
        double rms = samples > 0 ? Math.sqrt(sum / (double) samples) : 0;
        double db = 20 * Math.log10(rms + 1);
        double score = (db / 90.0) * 100.0;
        return Math.max(0, Math.min(100, score));
    }

    /** Skor kenyaringan real-time terkini (0-100), aman dibaca dari thread manapun (volatile). */
    public double getScore() {
        return currentScore;
    }

    /** Menghentikan thread & menutup line mikrofon secara aman. */
    public void stop() {
        running = false;
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception ignored) {
            }
        }
    }
}