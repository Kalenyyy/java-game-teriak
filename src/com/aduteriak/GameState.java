package com.aduteriak;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class GameState {
    public static LinkedList<Player> allPlayers = new LinkedList<>();
    public static Queue<Player> turnQueue = new LinkedList<>();
    public static boolean isTournamentMode = false;
    public static MatchNode tournamentRoot;
    public static ArrayList<MatchNode> allMatches = new ArrayList<>();

    public static void reset() {
        allPlayers.clear();
        turnQueue.clear();
        isTournamentMode = false;
        tournamentRoot = null;
        allMatches.clear();
    }
}