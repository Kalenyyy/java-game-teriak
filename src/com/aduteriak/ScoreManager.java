package com.aduteriak;

import java.io.*;
import java.util.*;

public class ScoreManager {
    private static final String FILE_NAME = "leaderboard.txt";

    public static void saveScore(String name, int score) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(FILE_NAME, true)))) {
            out.println(name + "," + score);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Player> getTopScores() {
        List<Player> scores = new ArrayList<>();
        File file = new File(FILE_NAME);
        if (!file.exists()) return scores;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    Player p = new Player(parts[0]);
                    p.setScore(Double.parseDouble(parts[1]));
                    scores.add(p);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        scores.sort((p1, p2) -> Double.compare(p2.getScore(), p1.getScore()));
        return scores.size() > 10 ? scores.subList(0, 10) : scores;
    }
}