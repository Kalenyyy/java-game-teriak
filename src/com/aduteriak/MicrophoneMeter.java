package com.aduteriak;

import javax.sound.sampled.*;

public class MicrophoneMeter {
    private TargetDataLine line;
    private boolean running = false;
    private double currentRMS = 0;

    public void start() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            running = true;

            Thread thread = new Thread(() -> {
                byte[] buffer = new byte[2048];
                while (running) {
                    int read = line.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        currentRMS = calculateRMS(buffer, read);
                    }
                }
            });
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

    public double getCurrentRMS() {
        return currentRMS;
    }

    private double calculateRMS(byte[] buffer, int read) {
        long sum = 0;
        for (int i = 0; i < read - 1; i += 2) {
            // Gabungkan 2 byte menjadi 1 sample (16-bit)
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += sample * sample;
        }
        double avg = sum / (read / 2.0);
        return Math.sqrt(avg);
    }
}