package com.aduteriak;

// Ini adalah ADT (Abstract Data Type)
public class Player {
    private String name;
    private double score;

    public Player(String name) {
        this.name = name;
        this.score = 0;
    }

    // Getter dan Setter
    public String getName() { return name; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}