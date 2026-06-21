package com.aduteriak;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainPanel;

    public MainFrame() {
        setUndecorated(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // Daftarkan layar-layar utama
        mainPanel.add(new MainMenuPanel(this), "MENU_UTAMA");
        mainPanel.add(new Input2PPanel(this), "INPUT_2P");

        add(mainPanel);

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        gd.setFullScreenWindow(this);
    }

    public void showView(String name) {
        cardLayout.show(mainPanel, name);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    public void startGame() {
        mainPanel.add(new GamePanel(this), "GAME_SCREEN");
        showView("GAME_SCREEN");
    }

    public void showResult() {
        mainPanel.add(new ResultPanel(this), "RESULT_SCREEN");
        showView("RESULT_SCREEN");
    }
}