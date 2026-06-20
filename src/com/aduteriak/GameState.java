package com.aduteriak;

public class GameState {
    public static String player1Name = "";
    public static String player2Name = "";
    public static double player1Score = 0;
    public static double player2Score = 0;

    public static void reset() {
        player1Score = 0;
        player2Score = 0;
    }
}