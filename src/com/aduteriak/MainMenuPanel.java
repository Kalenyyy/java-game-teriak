package com.aduteriak;

import javax.swing.*;
import java.awt.*;

public class MainMenuPanel extends JPanel {
    public MainMenuPanel(MainFrame parent) {
        setBackground(new Color(30, 30, 30));
        setLayout(new GridBagLayout());

        JLabel title = new JLabel("ADU TERIAK", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 60));
        title.setForeground(Color.WHITE);

        JButton btn2P = new JButton("2 PLAYER MODE");
        btn2P.setFont(new Font("Arial", Font.BOLD, 25));
        btn2P.addActionListener(e -> parent.showView("INPUT_2P"));

        JButton btnExit = new JButton("KELUAR");
        btnExit.addActionListener(e -> System.exit(0));

        JPanel box = new JPanel(new GridLayout(3, 1, 10, 10));
        box.setOpaque(false);
        box.add(title); box.add(btn2P); box.add(btnExit);
        add(box);
    }
}