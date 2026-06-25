package com.aduteriak;

import java.util.*;

public class TournamentManager {

    // 1. Membangun Bracket (Tree) dari list pemain
    public static MatchNode buildTree(List<Player> players) {
        Collections.shuffle(players);
        List<MatchNode> currentLevel = new ArrayList<>();

        // Level awal
        for (int i = 0; i < players.size(); i += 2) {
            MatchNode node = new MatchNode();
            node.p1 = players.get(i);
            node.p2 = players.get(i + 1);
            currentLevel.add(node);
            GameState.allMatches.add(node);
        }

        // Membangun ke atas sampai Root (Puncak Tree)
        while (currentLevel.size() > 1) {
            List<MatchNode> nextLevel = new ArrayList<>();
            for (int i = 0; i < currentLevel.size(); i += 2) {
                MatchNode p = new MatchNode();
                MatchNode l = currentLevel.get(i);
                MatchNode r = currentLevel.get(i + 1);
                p.left = l; p.right = r;
                l.parent = p; r.parent = p;
                nextLevel.add(p);
                GameState.allMatches.add(p);
            }
            currentLevel = nextLevel;
        }
        return currentLevel.get(0);
    }

    // 2. Mengambil match berikutnya (Ganti nama dari setupNextMatch ke updateQueueFromTree agar sinkron)
    public static void updateQueueFromTree() {
        GameState.turnQueue.clear();
        for (MatchNode m : GameState.allMatches) {
            if (m.isReady()) {
                GameState.turnQueue.add(m.p1);
                GameState.turnQueue.add(m.p2);
                System.out.println("Match Siap: " + m.p1.getName() + " vs " + m.p2.getName());
                return;
            }
        }
    }

    // 3. Menentukan pemenang match terakhir dan menaikkannya di Tree
    public static void processWinner() {
        for (MatchNode m : GameState.allMatches) {
            if (m.p1 != null && m.p2 != null && m.winner == null) {
                // Bandingkan skor
                m.winner = (m.p1.getScore() >= m.p2.getScore()) ? m.p1 : m.p2;

                // Naikkan pemenang ke babak selanjutnya (Parent)
                if (m.parent != null) {
                    if (m.parent.left == m) m.parent.p1 = m.winner;
                    else m.parent.p2 = m.winner;
                }
                return;
            }
        }
    }

    public static void shuffleAndRebuild() {
        // 1. Ambil list pemain asli
        List<Player> players = new ArrayList<>(GameState.allPlayers);

        // 2. Reset turnamen saat ini
        GameState.tournamentRoot = null;
        GameState.allMatches.clear();
        GameState.turnQueue.clear();

        // 3. Bangun ulang pohon dengan urutan baru
        GameState.tournamentRoot = buildTree(players);
    }

    // 4. Mengecek apakah turnamen sudah selesai (Ini yang tadi hilang)
    public static boolean isTournamentOver() {
        // Turnamen beres jika Root (Final) sudah punya pemenang
        return GameState.tournamentRoot != null && GameState.tournamentRoot.winner != null;
    }
}