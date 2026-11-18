package com.auction;

import com.auction.dao.DatabaseConnection;
import com.auction.dao.DatabaseInitializer;
import com.auction.dao.UserDAO;
import com.auction.model.Admin;
import com.auction.security.PasswordUtil;
import com.auction.service.AuthService;
import com.auction.ui.LoginFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        DatabaseConnection databaseConnection = new DatabaseConnection();
        try {
            new DatabaseInitializer(databaseConnection).initializeSchema();
            provisionInitialAdmin(new UserDAO(databaseConnection));
            try (Connection connection = databaseConnection.openConnection()) {
                if (!connection.isValid(2)) {
                    throw new SQLException("SQLite connection validation failed");
                }
            }
            AuthService authService = new AuthService(new UserDAO(databaseConnection));
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println("OAMS database is ready, but a graphical environment is required to show the login window.");
                return;
            }
            SwingUtilities.invokeLater(() -> new LoginFrame(authService).setVisible(true));
        } catch (SQLException | IllegalArgumentException exception) {
            String message = "Could not initialize OAMS: " + exception.getMessage();
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println(message);
            } else {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        null, message, "OAMS startup error", JOptionPane.ERROR_MESSAGE));
            }
        }
    }

    private static void provisionInitialAdmin(UserDAO userDAO) throws SQLException {
        String name = System.getenv("OAMS_ADMIN_NAME");
        String email = System.getenv("OAMS_ADMIN_EMAIL");
        String password = System.getenv("OAMS_ADMIN_PASSWORD");
        if (isBlank(name) && isBlank(email) && isBlank(password)) {
            return;
        }
        if (isBlank(name) || isBlank(email) || isBlank(password)) {
            System.err.println("Initial admin setup skipped: configure all three OAMS_ADMIN_* variables.");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userDAO.emailExists(normalizedEmail)) {
            return;
        }
        if (password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("OAMS_ADMIN_PASSWORD must contain 8 to 128 characters");
        }

        char[] passwordCharacters = password.toCharArray();
        try {
            String passwordHash = PasswordUtil.hash(passwordCharacters);
            userDAO.save(new Admin(0, name, normalizedEmail, LocalDateTime.now()), passwordHash);
            System.out.println("Initial administrator account created.");
        } finally {
            Arrays.fill(passwordCharacters, '\0');
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}