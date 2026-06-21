package com.aduteriak;

import javax.sound.sampled.*;

/**
 * Deteksi suara untuk game "Adu Teriak" - versi perbaikan.
 * Return score 0-100 yang siap pakai untuk progress bar.
 */
public class MicrophoneMeter {
    private TargetDataLine line;
    private boolean running = false;
    private double currentRMS = 0;
    private double currentScore = 0;
    private double smoothedScore = 0;

    // Parameter tuning
    private static final double NOISE_GATE_THRESHOLD = 300.0; // Abaikan suara kipas/noise kecil
    private static final double VOLUME_GAIN = 0.00005;        // Menyesuaikan angka RMS 16-bit yang besar
    private static final double POWER_FACTOR = 1.2;           // Memberi efek progresif
    private static final double SMOOTHING_FACTOR = 0.3;
    private static final double WEIGHT_RMS = 0.5;             // 50% dari volume suara
    private static final double WEIGHT_FREQUENCY = 0.5;       // 50% dari tinggi nada (teriakan)
    private static final double MIN_SHRIEK_FREQ = 400.0;
    private static final double MAX_SHRIEK_FREQ = 4000.0;


//    private static final double NOISE_GATE_THRESHOLD = 500.0;
//    private static final double VOLUME_GAIN = 0.002;
//    private static final double POWER_FACTOR = 1.3;
//    private static final double SMOOTHING_FACTOR = 0.3;
//    private static final double WEIGHT_RMS = 0.4;
//    private static final double WEIGHT_FREQUENCY = 0.6;
//    private static final double MIN_SHRIEK_FREQ = 500.0;
//    private static final double MAX_SHRIEK_FREQ = 4000.0;
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 2048;

    private byte[] audioBuffer;
    private double[] fftBuffer;
    private FFT fft;

    public MicrophoneMeter() {
        this.audioBuffer = new byte[BUFFER_SIZE * 2];
        this.fftBuffer = new double[BUFFER_SIZE * 2]; // Ditambah * 2
        this.fft = new FFT(BUFFER_SIZE);
    }

    public void start() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            running = true;

            Thread thread = new Thread(() -> {
                while (running) {
                    int read = line.read(audioBuffer, 0, audioBuffer.length);
                    if (read > 0) {
                        currentRMS = calculateRMS(audioBuffer, read);
                        System.out.println("RMS: " + currentRMS + " | Score: " + smoothedScore);
                        convertBytesToSamples(audioBuffer, read);
                        double frequencyEnergy = calculateFrequencyEnergy();
                        currentScore = calculateScore(currentRMS, frequencyEnergy);
                        smoothedScore = smoothedScore + SMOOTHING_FACTOR * (currentScore - smoothedScore);
                        smoothedScore = Math.max(0, Math.min(100, smoothedScore));
                    }
                }
            });
            thread.setName("AudioProcessing");
            thread.setDaemon(true);
            thread.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    /**
     * Return score 0-100 (sudah smooth dan normalized)
     * Ganti dari kode lama yang return RMS mentah
     */
    public double getCurrentRMS() {
        return smoothedScore; // Return score 0-100, bukan RMS mentah
    }

    /**
     * Method baru: dapatkan score final
     */
    public double getCurrentScore() {
        return smoothedScore;
    }

    /**
     * Hitung RMS (amplitude signal)
     */
    private double calculateRMS(byte[] buffer, int read) {
        long sum = 0;
        for (int i = 0; i < read - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += sample * sample;
        }
        double avg = sum / (read / 2.0);
        return Math.sqrt(avg);
    }

    /**
     * Konversi byte array ke double samples untuk FFT
     */
    private void convertBytesToSamples(byte[] buffer, int bytesRead) {
        int sampleCount = bytesRead / 2;
        for (int i = 0; i < sampleCount && i < BUFFER_SIZE; i++) {
            int idx = i * 2;
            short sample = (short) ((buffer[idx + 1] << 8) | (buffer[idx] & 0xFF));

            // Simpan di indeks genap (Real Part)
            fftBuffer[i * 2] = sample / 32768.0;
            // Kosongkan indeks ganjil (Imaginary Part)
            fftBuffer[i * 2 + 1] = 0;
        }

        // Sisa buffer dikosongkan
        for (int i = sampleCount; i < BUFFER_SIZE; i++) {
            fftBuffer[i * 2] = 0;
            fftBuffer[i * 2 + 1] = 0;
        }
    }

    /**
     * Hitung energy pada frekuensi teriakan menggunakan FFT
     */
    private double calculateFrequencyEnergy() {
        applyHannWindow();
        fft.transform(fftBuffer);

        double[] magnitude = new double[BUFFER_SIZE / 2];
        for (int i = 0; i < magnitude.length; i++) {
            double real = fftBuffer[2 * i];
            double imag = fftBuffer[2 * i + 1];
            magnitude[i] = Math.sqrt(real * real + imag * imag);
        }

        double energy = 0;
        double count = 0;

        for (int i = 0; i < magnitude.length; i++) {
            double freq = (i * SAMPLE_RATE) / (double) BUFFER_SIZE;
            if (freq >= MIN_SHRIEK_FREQ && freq <= MAX_SHRIEK_FREQ) {
                energy += magnitude[i] * magnitude[i];
                count++;
            }
        }

        if (count > 0) {
            energy = energy / count;
        }

        return energy;
    }

    /**
     * Apply Hann window
     */
    private void applyHannWindow() {
        for (int i = 0; i < fftBuffer.length; i++) {
            double window = 0.5 * (1 - Math.cos(2 * Math.PI * i / (fftBuffer.length - 1)));
            fftBuffer[i] = fftBuffer[i] * window;
        }
    }

    /**
     * Hitung final score (0-100)
     */
    private double calculateScore(double rawRMS, double frequencyEnergy) {
        // 1. Cek Noise Gate
        if (rawRMS < NOISE_GATE_THRESHOLD) {
            return 0;
        }

        // 2. Hitung Komponen Volume (RMS)
        // Kita gunakan VOLUME_GAIN untuk mengatur sensitivitas
        double scaledVolume = (rawRMS - NOISE_GATE_THRESHOLD) * VOLUME_GAIN;
        double rmsComponent = Math.pow(Math.max(0, Math.min(1, scaledVolume)), POWER_FACTOR);

        // 3. Hitung Komponen Frekuensi
        // Kita asumsikan 0.1 adalah energy frekuensi yang cukup tinggi
        double frequencyComponent = frequencyEnergy * 10.0;
        frequencyComponent = Math.max(0, Math.min(1, frequencyComponent));

        // 4. Gabungkan menggunakan WEIGHT (Bobot)
        // Di sini variabel WEIGHT_RMS dan WEIGHT_FREQUENCY digunakan
        double combinedScore = (rmsComponent * WEIGHT_RMS) + (frequencyComponent * WEIGHT_FREQUENCY);

        // 5. Kembalikan skor dalam skala 0-100
        return Math.min(100, combinedScore * 100);
    }
}