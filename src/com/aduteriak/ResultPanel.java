package com.aduteriak;

import javax.swing.*;
import java.awt.*;

public class ResultPanel extends JPanel {
    public ResultPanel(MainFrame parent) {
        setBackground(new Color(25, 25, 25));
        setLayout(new GridBagLayout());

        JPanel content = new JPanel(new GridLayout(6, 1, 10, 10));
        content.setOpaque(false);

        Player p1 = GameState.allPlayers.get(0);
        Player p2 = GameState.allPlayers.get(1);

        JLabel title = new JLabel("HASIL AKHIR", SwingConstants.CENTER);
        title.setForeground(Color.WHITE); title.setFont(new Font("Arial", Font.BOLD, 40));

        JLabel s1 = new JLabel(p1.getName() + " : " + (int)p1.getScore(), SwingConstants.CENTER);
        s1.setForeground(Color.WHITE); s1.setFont(new Font("Arial", Font.PLAIN, 25));

        JLabel s2 = new JLabel(p2.getName() + " : " + (int)p2.getScore(), SwingConstants.CENTER);
        s2.setForeground(Color.WHITE); s2.setFont(new Font("Arial", Font.PLAIN, 25));

        String win = p1.getScore() > p2.getScore() ? p1.getName() : p2.getName();
        if(p1.getScore() == p2.getScore()) win = "SERI";
        else win = "PEMENANG: " + win.toUpperCase();

        JLabel lblWin = new JLabel(win, SwingConstants.CENTER);
        lblWin.setForeground(Color.YELLOW); lblWin.setFont(new Font("Arial", Font.BOLD, 35));

        JButton btnBack = new JButton("KEMBALI KE MENU");
        btnBack.addActionListener(e -> parent.showView("MENU_UTAMA"));

        content.add(title); content.add(s1); content.add(s2);
        content.add(new JLabel("")); content.add(lblWin); content.add(btnBack);
        add(content);
    }
}