package com.aduteriak;

import javax.sound.sampled.*;

/**
 * MicrophoneMeter yang sudah improved dengan perbaikan:
 * 1. RMS di-scale 0-1 (proper normalisasi)
 * 2. Frequency energy dari total, bukan peak saja
 * 3. Proper component scaling dan balancing
 * 4. Calibrated dengan typical FFT magnitude values
 */
public class MicrophoneMeter {
    private TargetDataLine line;
    private boolean running = false;
    private double currentScore = 0;
    private double smoothedScore = 0;

    // DEBUG: Store raw values untuk testing
    private double lastRMS = 0;
    private double lastFreqEnergy = 0;

    // ===== PARAMETER TUNING =====
    // --- SETTING SEDIKIT BERAT (RESPONSIF) ---
//    private static final double NOISE_GATE_THRESHOLD = 0.05; // Lebih sensitif (suara pelan masuk)
//    private static final double VOLUME_GAIN = 1.0;
//    private static final double POWER_FACTOR = 1.8;          // Tidak terlalu berat di awal
//    private static final double SMOOTHING_FACTOR = 0.4;      // Jauh lebih responsif (bar naik turun cepat)
//    private static final double WEIGHT_RMS = 0.4;            // Volume lebih berpengaruh
//    private static final double WEIGHT_FREQUENCY = 0.6;
//    private static final double MIN_SHRIEK_FREQ = 600.0;     // Range lebih luas (suara cowok masuk)
//    private static final double MAX_SHRIEK_FREQ = 5000.0;
//    private static final double TYPICAL_SCREAM_MAGNITUDE = 0.15; // Lebih mudah mencapai energi maksimal

    // --- SETTING BERAT BANGET (HARDCORE) ---
//    private static final double NOISE_GATE_THRESHOLD = 0.12; // Harus kencang dulu baru bar mau gerak
//    private static final double VOLUME_GAIN = 1.0;
//    private static final double POWER_FACTOR = 3.5;          // Sangat berat, butuh tenaga ekstra di akhir
//    private static final double SMOOTHING_FACTOR = 0.2;      // Agak "berat" gerakannya, harus ditahan teriaknya
//    private static final double WEIGHT_RMS = 0.2;            // Suara ngebass/volume doang nggak guna
//    private static final double WEIGHT_FREQUENCY = 0.8;      // WAJIB melengking (High pitch)
//    private static final double MIN_SHRIEK_FREQ = 1000.0;    // Fokus ke frekuensi tinggi saja
//    private static final double MAX_SHRIEK_FREQ = 6000.0;
//    private static final double TYPICAL_SCREAM_MAGNITUDE = 0.35; // Butuh energi FFT yang sangat besar

    // --- SETTING BERAT BANGET (ULTRA HARDCORE) ---
    private static final double NOISE_GATE_THRESHOLD = 0.20;      // Naikkan! Suara bicara keras tidak akan menggerakkan bar
    private static final double MAX_EXPECTED_RMS = 0.70;          // Naikkan drastis! (Dulu cuma 0.35)
    private static final double POWER_FACTOR = 4.5;               // Lebih berat lagi kurvanya
    private static final double SMOOTHING_FACTOR = 0.25;          // Responsif tapi berat
    private static final double WEIGHT_RMS = 0.4;                 // Naikkan bobot volume agar tenaga lebih terasa
    private static final double WEIGHT_FREQUENCY = 0.6;
    private static final double MIN_SHRIEK_FREQ = 1000.0;
    private static final double MAX_SHRIEK_FREQ = 6000.0;
    private static final double TYPICAL_SCREAM_MAGNITUDE = 0.60;  // Naikkan drastis! (Dulu cuma 0.35)

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 2048;

    private byte[] audioBuffer;
    private double[] fftBuffer;
    private FFT fft;

    public MicrophoneMeter() {
        this.audioBuffer = new byte[BUFFER_SIZE * 2];
        this.fftBuffer = new double[BUFFER_SIZE * 2];
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
                        // Calculate metrics
                        double normalizedRMS = calculateRMS(audioBuffer, read);
                        convertBytesToSamples(audioBuffer, read);
                        double frequencyEnergy = calculateFrequencyEnergy();

                        // Store untuk debugging
                        lastRMS = normalizedRMS;
                        lastFreqEnergy = frequencyEnergy;

                        // Calculate score
                        currentScore = calculateScore(normalizedRMS, frequencyEnergy);

                        // Apply exponential smoothing
                        smoothedScore = smoothedScore + SMOOTHING_FACTOR * (currentScore - smoothedScore);
                        smoothedScore = Math.max(0, Math.min(100, smoothedScore));
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        } catch (LineUnavailableException e) {
            System.err.println("Microphone not found: " + e.getMessage());
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
     * Get final smoothed score 0-100
     */
    public double getCurrentScore() {
        return smoothedScore;
    }

    /**
     * Debug: Get raw RMS (0-1)
     */
    public double getRawRMS() {
        return lastRMS;
    }

    /**
     * Debug: Get frequency energy
     */
    public double getFreqEnergy() {
        return lastFreqEnergy;
    }

    /**
     * Calculate RMS dan normalize ke 0-1 scale
     * 16-bit audio range: -32768 to 32767
     */
    private double calculateRMS(byte[] buffer, int read) {
        long sum = 0;
        for (int i = 0; i < read - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += (long) sample * sample;
        }
        double rms = Math.sqrt(sum / (read / 2.0));

        // PERBAIKAN: Scale ke 0-1 (16-bit max = 32768)
        return rms / 32768.0;
    }

    /**
     * Convert bytes to samples dan apply Hann window
     */
    private void convertBytesToSamples(byte[] buffer, int bytesRead) {
        int sampleCount = bytesRead / 2;
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (i < sampleCount) {
                int idx = i * 2;
                short sample = (short) ((buffer[idx + 1] << 8) | (buffer[idx] & 0xFF));

                // Apply Hann window
                double window = 0.5 * (1 - Math.cos(2 * Math.PI * i / (BUFFER_SIZE - 1)));
                fftBuffer[i * 2] = (sample / 32768.0) * window;
                fftBuffer[i * 2 + 1] = 0;  // Imaginary part = 0 initially
            } else {
                fftBuffer[i * 2] = 0;
                fftBuffer[i * 2 + 1] = 0;
            }
        }
    }

    /**
     * Calculate total frequency energy di range teriakan
     * PERBAIKAN: Total energy (sum), bukan peak saja
     */
    private double calculateFrequencyEnergy() {
        fft.transform(fftBuffer);
        double totalEnergy = 0;
        int binCount = 0;

        for (int i = 0; i < BUFFER_SIZE / 2; i++) {
            double real = fftBuffer[2 * i];
            double imag = fftBuffer[2 * i + 1];
            double magnitude = Math.sqrt(real * real + imag * imag);

            double freq = (i * SAMPLE_RATE) / (double) BUFFER_SIZE;

            // Cari energy di range 800-5000Hz (teriakan)
            if (freq >= MIN_SHRIEK_FREQ && freq <= MAX_SHRIEK_FREQ) {
                // PERBAIKAN: Accumulate energy, bukan hanya ambil peak
                totalEnergy += magnitude * magnitude;  // Power = magnitude^2
                binCount++;
            }
        }

        // Average power di range ini, baru ambil RMS
        if (binCount > 0) {
            totalEnergy = totalEnergy / binCount;
            totalEnergy = Math.sqrt(totalEnergy);  // RMS dari power
        }

        return totalEnergy;
    }

    /**
     * Calculate final score 0-100
     * Kombinasi 30% volume + 70% frequency energy (kualitas teriakan)
     */
    private double calculateScore(double normalizedRMS, double freqEnergy) {
        // 1. Noise Gate diperketat
        if (normalizedRMS < NOISE_GATE_THRESHOLD) {
            return 0;
        }

        // 2. Volume Component
        // Menggunakan MAX_EXPECTED_RMS yang lebih tinggi agar pembaginya besar
        double volumePart = (normalizedRMS - NOISE_GATE_THRESHOLD) / (MAX_EXPECTED_RMS - NOISE_GATE_THRESHOLD);
        volumePart = Math.max(0, Math.min(1, volumePart));

        // 3. Frequency Component
        // Menggunakan TYPICAL_SCREAM_MAGNITUDE yang lebih besar agar tidak gampang penuh
        double freqPart = freqEnergy / TYPICAL_SCREAM_MAGNITUDE;
        freqPart = Math.max(0, Math.min(1, freqPart));

        // 4. Gabungkan Dulu
        double combined = (volumePart * WEIGHT_RMS) + (freqPart * WEIGHT_FREQUENCY);

        // 5. BARU TERAPKAN POWER FACTOR ke hasil gabungan
        // Ini rahasianya agar bar terasa "sangat berat" di awal dan tengah
        combined = Math.pow(combined, POWER_FACTOR);

        return combined * 100;
    }
}