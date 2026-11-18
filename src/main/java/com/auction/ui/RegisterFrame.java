package com.auction.ui;

import com.auction.model.UserRole;
import com.auction.service.AuthService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class RegisterFrame extends JFrame {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AuthService authService;
    private final Consumer<String> returnToLogin;
    private final JTextField nameField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmationField = new JPasswordField();
    private final JComboBox<UserRole> roleSelector = new JComboBox<>(new UserRole[]{
            UserRole.BUYER, UserRole.SELLER
    });
    private final JButton registerButton = UiStyle.primaryButton("Create account");
    private boolean returnedToLogin;

    public RegisterFrame(AuthService authService, Consumer<String> returnToLogin) {
        super("Create an OAMS account");
        this.authService = authService;
        this.returnToLogin = returnToLogin;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(470, 620));
        setSize(500, 660);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UiStyle.BACKGROUND);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                showLoginAgain(null);
            }
        });
        buildInterface();
    }

    private void buildInterface() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiStyle.SURFACE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyle.BORDER),
                BorderFactory.createEmptyBorder(24, 28, 24, 28)));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 7, 0);
        form.add(UiStyle.title("Create your account"), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 18, 0);
        form.add(UiStyle.subtitle("Choose a Buyer or Seller account."), constraints);

        addFormField(form, constraints, "Full name", nameField, 16);
        addFormField(form, constraints, "Email address", emailField, 16);
        addFormField(form, constraints, "Password", passwordField, 16);
        addFormField(form, constraints, "Confirm password", confirmationField, 16);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 7, 0);
        form.add(fieldLabel("Account type"), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 20, 0);
        roleSelector.setFont(UiStyle.BODY_FONT);
        roleSelector.setBackground(UiStyle.SURFACE);
        roleSelector.setPreferredSize(new Dimension(300, 40));
        form.add(roleSelector, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 10, 0);
        registerButton.setPreferredSize(new Dimension(300, 44));
        registerButton.addActionListener(event -> submitRegistration());
        form.add(registerButton, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        JButton backButton = UiStyle.secondaryButton("Back to sign in");
        backButton.setPreferredSize(new Dimension(300, 42));
        backButton.addActionListener(event -> showLoginAgain(null));
        form.add(backButton, constraints);

        GridBagConstraints outer = new GridBagConstraints();
        outer.fill = GridBagConstraints.HORIZONTAL;
        outer.weightx = 1;
        content.add(form, outer);
        add(content, BorderLayout.CENTER);
    }

    private void submitRegistration() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim().toLowerCase(Locale.ROOT);
        char[] password = passwordField.getPassword();
        char[] confirmation = confirmationField.getPassword();
        if (name.isEmpty() || email.isEmpty() || password.length == 0 || confirmation.length == 0) {
            clearPasswords(password, confirmation);
            showError("Complete all fields before creating your account.");
            return;
        }
        if (name.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
            clearPasswords(password, confirmation);
            showError("Enter a valid name and email address.");
            return;
        }
        if (password.length < 8 || password.length > 128) {
            clearPasswords(password, confirmation);
            showError("Password must contain 8 to 128 characters.");
            return;
        }
        if (!Arrays.equals(password, confirmation)) {
            clearPasswords(password, confirmation);
            showError("The password and confirmation do not match.");
            return;
        }

        String passwordValue = new String(password);
        clearPasswords(password, confirmation);
        UserRole selectedRole = (UserRole) roleSelector.getSelectedItem();
        registerButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (selectedRole == UserRole.BUYER) {
                    authService.registerBuyer(name, email, passwordValue);
                } else if (selectedRole == UserRole.SELLER) {
                    authService.registerSeller(name, email, passwordValue);
                } else {
                    throw new IllegalStateException("Only Buyer and Seller registration is available");
                }
                return null;
            }

            @Override
            protected void done() {
                registerButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(RegisterFrame.this,
                            "Your account has been created. You can now sign in.",
                            "Registration complete", JOptionPane.INFORMATION_MESSAGE);
                    showLoginAgain(email);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Registration was interrupted. Please try again.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        showError(cause.getMessage());
                    } else {
                        showError("Unable to create the account because the account service is unavailable.");
                        System.err.println("Registration failed: " + cause.getMessage());
                    }
                }
            }
        }.execute();
    }

    private void showLoginAgain(String email) {
        if (returnedToLogin) {
            return;
        }
        returnedToLogin = true;
        dispose();
        returnToLogin.accept(email);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Registration", JOptionPane.ERROR_MESSAGE);
    }

    private static void clearPasswords(char[] password, char[] confirmation) {
        Arrays.fill(password, '\0');
        Arrays.fill(confirmation, '\0');
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiStyle.BODY_FONT);
        label.setForeground(UiStyle.TEXT);
        return label;
    }

    private static void addFormField(JPanel form, GridBagConstraints constraints, String label,
                                     JTextField field, int bottomSpacing) {
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 7, 0);
        form.add(fieldLabel(label), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, bottomSpacing, 0);
        UiStyle.styleField(field);
        field.setPreferredSize(new Dimension(300, 40));
        form.add(field, constraints);
    }
}