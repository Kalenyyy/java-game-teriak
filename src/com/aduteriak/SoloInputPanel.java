package com.aduteriak;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SoloInputPanel extends JPanel {
    public SoloInputPanel(MainFrame parent) {
        setLayout(new GridBagLayout());
        setBackground(new Color(15, 15, 15));

        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JLabel label = new JLabel("MASUKKAN NAMAMU");
        label.setFont(new Font("Arial", Font.BOLD, 20));
        label.setForeground(Color.WHITE);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField txtName = new JTextField(15);
        txtName.setMaximumSize(new Dimension(300, 40));
        txtName.setFont(new Font("Arial", Font.PLAIN, 18));
        txtName.setBackground(new Color(40, 40, 40));
        txtName.setForeground(Color.WHITE);
        txtName.setCaretColor(Color.WHITE);
        txtName.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100,100,100)),
                BorderFactory.createEmptyBorder(5,10,5,10)
        ));

        JButton btnStart = new JButton("MULAI TERIAK");
        btnStart.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStart.setPreferredSize(new Dimension(300, 50));
        btnStart.addActionListener(e -> {
            String name = txtName.getText().trim();
            if (!name.isEmpty()) {
                GameState.reset();
                GameState.isSoloMode = true;
                Player p = new Player(name);
                GameState.allPlayers.add(p);
                GameState.turnQueue.add(p);
                parent.startGame();
            }
        });

        container.add(label);
        container.add(Box.createRigidArea(new Dimension(0, 20)));
        container.add(txtName);
        container.add(Box.createRigidArea(new Dimension(0, 20)));
        container.add(btnStart);

        add(container);
    }
}