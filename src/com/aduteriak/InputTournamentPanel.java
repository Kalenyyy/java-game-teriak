package com.aduteriak;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class InputTournamentPanel extends JPanel {
    private JPanel dynamicForm;
    private ArrayList<JTextField> nameFields;
    private MainFrame parent;
    private int playerCount = 4; // Default

    public InputTournamentPanel(MainFrame parent) {
        this.parent = parent;
        this.nameFields = new ArrayList<>();

        setBackground(new Color(30, 30, 30));
        setLayout(new BorderLayout(20, 20));

        // --- ATAS: Header & Pemilih Jumlah ---
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setOpaque(false);

        JLabel lblTitle = new JLabel("MODE TOURNAMENT", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 30));
        lblTitle.setForeground(Color.WHITE);
        header.add(lblTitle);

        JPanel selectionPanel = new JPanel();
        selectionPanel.setOpaque(false);
        JLabel lblChoice = new JLabel("Jumlah Pemain: ");
        lblChoice.setForeground(Color.WHITE);

        // Pilihan jumlah pemain dinamis
        Integer[] options = {4, 8, 16};
        JComboBox<Integer> combo = new JComboBox<>(options);
        combo.addActionListener(e -> {
            playerCount = (int) combo.getSelectedItem();
            generateFields(playerCount); // Generate ulang field
        });

        selectionPanel.add(lblChoice);
        selectionPanel.add(combo);
        header.add(selectionPanel);
        add(header, BorderLayout.NORTH);

        // --- TENGAH: Form Input Dinamis (Scrollable) ---
        dynamicForm = new JPanel();
        dynamicForm.setOpaque(false);
        // ScrollPane supaya kalau 16 pemain tidak meluber keluar layar
        JScrollPane scrollPane = new JScrollPane(dynamicForm);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // --- BAWAH: Tombol Aksi ---
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        JButton btnBack = new JButton("KEMBALI");
        JButton btnStart = new JButton("GENERATE BRACKET & MULAI");

        btnBack.addActionListener(e -> parent.showView("MENU_UTAMA"));

        btnStart.addActionListener(e -> {
            startTournament();
        });

        footer.add(btnBack);
        footer.add(btnStart);
        add(footer, BorderLayout.SOUTH);

        // Inisialisasi awal dengan 4 field
        generateFields(4);
    }

    // FUNGSI INI YANG BIKIN DINAMIS COY
    private void generateFields(int count) {
        dynamicForm.removeAll();
        nameFields.clear();

        // Setting layout grid: misal 2 kolom supaya hemat tempat
        dynamicForm.setLayout(new GridLayout(0, 2, 10, 10));

        for (int i = 1; i <= count; i++) {
            JPanel p = new JPanel(new BorderLayout(5, 5));
            p.setOpaque(false);
            JLabel l = new JLabel("Pemain " + i + ": ");
            l.setForeground(Color.WHITE);
            JTextField f = new JTextField("Player " + i);

            nameFields.add(f);
            p.add(l, BorderLayout.WEST);
            p.add(f, BorderLayout.CENTER);
            dynamicForm.add(p);
        }

        dynamicForm.revalidate();
        dynamicForm.repaint();
    }

    private void startTournament() {
        GameState.reset();
        GameState.isTournamentMode = true;

        for (JTextField f : nameFields) {
            GameState.allPlayers.add(new Player(f.getText()));
        }

        // Build Tree
        GameState.tournamentRoot = TournamentManager.buildTree(new ArrayList<>(GameState.allPlayers));

        // PINDAH KE BRACKET DULU COY, JANGAN LANGSUNG TANDING
        parent.showBracket();
    }
}