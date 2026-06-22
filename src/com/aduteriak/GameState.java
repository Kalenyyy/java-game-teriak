package com.aduteriak;

import java.util.LinkedList;
import java.util.Queue;

public class GameState {
    // Syarat ADT & List: Menyimpan semua pemain yang ikut
    public static LinkedList<Player> allPlayers = new LinkedList<>();

    // Syarat Queue: Antrean untuk giliran teriak
    public static Queue<Player> turnQueue = new LinkedList<>();

    public static void reset() {
        allPlayers.clear();
        turnQueue.clear();
    }
}