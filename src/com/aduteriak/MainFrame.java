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

        // Register Layar
        mainPanel.add(new MainMenuPanel(this), "MENU_UTAMA");
        mainPanel.add(new Input2PPanel(this), "INPUT_2P");
        mainPanel.add(new InputTournamentPanel(this), "INPUT_TOURNAMENT");
        mainPanel.add(new TournamentBracketPanel(this), "BRACKET_SCREEN");

        add(mainPanel);

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        gd.setFullScreenWindow(this);
    }

    public void showBracket() {
        // Refresh isi bracket sebelum tampil
        Component[] components = mainPanel.getComponents();
        for (Component c : components) {
            if (c instanceof TournamentBracketPanel) {
                ((TournamentBracketPanel) c).refreshBracket();
            }
        }
        showView("BRACKET_SCREEN");
    }

    public void showView(String name) {
        cardLayout.show(mainPanel, name);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    public void startGame() {
        // Hapus GamePanel lama agar bersih (clean memory & focus)
        Component[] components = mainPanel.getComponents();
        for (Component c : components) {
            if (c instanceof GamePanel) mainPanel.remove(c);
        }

        mainPanel.add(new GamePanel(this), "GAME_SCREEN");
        showView("GAME_SCREEN");

        // Pastikan focus kembali ke game
        this.requestFocus();
    }

    public void startDuel() {
        // Hapus DuelPanel lama jika ada (agar mic & timer lama tidak menumpuk)
        Component[] components = mainPanel.getComponents();
        for (Component c : components) {
            if (c instanceof GamePanel) mainPanel.remove(c);
        }

        mainPanel.add(new GamePanel(this), "DUEL_SCREEN");
        showView("DUEL_SCREEN");

        // Pastikan focus kembali ke panel duel (agar key binding SPACE aktif)
        this.requestFocus();
    }

    public void showResult() {
        // Hapus ResultPanel lama jika ada
        Component[] components = mainPanel.getComponents();
        for (Component c : components) {
            if (c instanceof ResultPanel) mainPanel.remove(c);
        }

        mainPanel.add(new ResultPanel(this), "RESULT_SCREEN");
        showView("RESULT_SCREEN");
    }
}