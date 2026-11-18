package com.auction.ui;

import com.auction.model.User;
import com.auction.service.AuthService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public final class LoginFrame extends JFrame {
    private final AuthService authService;
    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JButton loginButton = UiStyle.primaryButton("Log in");

    public LoginFrame(AuthService authService) {
        this(authService, "");
    }

    LoginFrame(AuthService authService, String initialEmail) {
        super("Online Auction Management System");
        this.authService = authService;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(440, 500));
        setSize(480, 540);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UiStyle.BACKGROUND);
        buildInterface(initialEmail);
    }

    private void buildInterface(String initialEmail) {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(36, 36, 36, 36));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiStyle.SURFACE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyle.BORDER),
                BorderFactory.createEmptyBorder(28, 30, 28, 30)));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 8, 0);
        form.add(UiStyle.title("Welcome back"), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 24, 0);
        form.add(UiStyle.subtitle("Sign in to your OAMS account."), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 7, 0);
        form.add(fieldLabel("Email address"), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 18, 0);
        UiStyle.styleField(emailField);
        emailField.setText(initialEmail);
        emailField.setPreferredSize(new Dimension(300, 42));
        form.add(emailField, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 7, 0);
        form.add(fieldLabel("Password"), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 22, 0);
        UiStyle.styleField(passwordField);
        passwordField.setPreferredSize(new Dimension(300, 42));
        passwordField.addActionListener(event -> submitLogin());
        form.add(passwordField, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 12, 0);
        loginButton.setPreferredSize(new Dimension(300, 44));
        loginButton.addActionListener(event -> submitLogin());
        form.add(loginButton, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        JButton registerButton = UiStyle.secondaryButton("Create an account");
        registerButton.setPreferredSize(new Dimension(300, 42));
        registerButton.addActionListener(event -> openRegistration());
        form.add(registerButton, constraints);

        GridBagConstraints outer = new GridBagConstraints();
        outer.fill = GridBagConstraints.HORIZONTAL;
        outer.weightx = 1;
        content.add(form, outer);
        add(content, BorderLayout.CENTER);
    }

    private void submitLogin() {
        if (!loginButton.isEnabled()) {
            return;
        }
        String email = emailField.getText().trim();
        char[] password = passwordField.getPassword();
        if (email.isEmpty() || password.length == 0) {
            Arrays.fill(password, '\0');
            showError("Enter both your email address and password.");
            return;
        }

        String passwordValue = new String(password);
        Arrays.fill(password, '\0');
        loginButton.setEnabled(false);
        new SwingWorker<Optional<User>, Void>() {
            @Override
            protected Optional<User> doInBackground() throws Exception {
                return authService.login(email, passwordValue);
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                try {
                    Optional<User> result = get();
                    if (result.isEmpty()) {
                        passwordField.setText("");
                        showError("The email address or password is incorrect.");
                        passwordField.requestFocusInWindow();
                        return;
                    }
                    new DashboardFrame(result.orElseThrow(), authService).setVisible(true);
                    dispose();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Sign in was interrupted. Please try again.");
                } catch (ExecutionException exception) {
                    showError("Unable to sign in because the account service is unavailable.");
                    System.err.println("Login failed: " + exception.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void openRegistration() {
        dispose();
        RegisterFrame registerFrame = new RegisterFrame(authService, email -> {
            LoginFrame loginFrame = new LoginFrame(authService, email == null ? "" : email);
            loginFrame.setVisible(true);
        });
        registerFrame.setVisible(true);
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiStyle.BODY_FONT);
        label.setForeground(UiStyle.TEXT);
        return label;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Sign in", JOptionPane.ERROR_MESSAGE);
    }
}