package com.aduteriak;

public class MatchNode {
    public Player p1, p2;
    public Player winner;
    public MatchNode left, right; // Anak (babak sebelumnya)
    public MatchNode parent;      // Orang tua (babak selanjutnya)

    public MatchNode() {
        this.p1 = null;
        this.p2 = null;
        this.winner = null;
    }

    // Cek apakah pertandingan ini siap dimulai (dua pemain sudah ada)
    public boolean isReady() {
        return p1 != null && p2 != null && winner == null;
    }
}