package com.auction.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

final class UiStyle {
    static final Color BACKGROUND = new Color(244, 247, 251);
    static final Color SURFACE = Color.WHITE;
    static final Color PRIMARY = new Color(31, 99, 183);
    static final Color PRIMARY_DARK = new Color(23, 76, 144);
    static final Color TEXT = new Color(35, 48, 65);
    static final Color MUTED = new Color(100, 116, 135);
    static final Color BORDER = new Color(220, 227, 236);
    static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    private UiStyle() {
    }

    static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 24));
        label.setForeground(TEXT);
        return label;
    }

    static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        label.setForeground(MUTED);
        return label;
    }

    static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(PRIMARY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY), new EmptyBorder(9, 18, 9, 18)));
        button.setOpaque(true);
        return button;
    }

    static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(PRIMARY_DARK);
        button.setBackground(SURFACE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(8, 17, 8, 17)));
        button.setOpaque(true);
        return button;
    }

    static JButton navigationButton(String text, boolean selected) {
        JButton button = new JButton(text);
        button.setFont(BODY_FONT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(selected ? PRIMARY_DARK : TEXT);
        button.setBackground(selected ? new Color(222, 235, 250) : new Color(238, 243, 249));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        button.setOpaque(true);
        return button;
    }

    static void styleField(JTextField field) {
        field.setFont(BODY_FONT);
        field.setForeground(TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(9, 10, 9, 10)));
        field.setBackground(SURFACE);
    }

    static JPanel section(String heading, String description) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(18, 20, 18, 20)));

        JLabel title = new JLabel(heading);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(TEXT);
        JLabel details = new JLabel("<html>" + description + "</html>");
        details.setFont(BODY_FONT);
        details.setForeground(MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(details, BorderLayout.CENTER);
        return panel;
    }
}