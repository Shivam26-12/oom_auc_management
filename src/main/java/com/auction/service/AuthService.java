package com.auction.service;

import com.auction.dao.UserDAO;
import com.auction.model.Buyer;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.model.UserRole;
import com.auction.security.PasswordUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AuthService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final UserDAO userDAO;

    public AuthService(UserDAO userDAO) {
        this.userDAO = Objects.requireNonNull(userDAO, "userDAO");
    }

    public Buyer registerBuyer(String name, String email, String password) throws SQLException {
        return (Buyer) register(name, email, password, UserRole.BUYER);
    }

    public Seller registerSeller(String name, String email, String password) throws SQLException {
        return (Seller) register(name, email, password, UserRole.SELLER);
    }

    public Optional<User> login(String email, String password) throws SQLException {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            return Optional.empty();
        }

        String normalizedEmail;
        try {
            normalizedEmail = validateEmail(email);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        Optional<String> passwordHash = userDAO.findPasswordHashByEmail(normalizedEmail);
        if (passwordHash.isEmpty()) {
            return Optional.empty();
        }

        char[] passwordCharacters = password.toCharArray();
        try {
            if (!PasswordUtil.verify(passwordCharacters, passwordHash.get())) {
                return Optional.empty();
            }
        } finally {
            Arrays.fill(passwordCharacters, '\0');
        }
        return userDAO.findByEmail(normalizedEmail);
    }

    private User register(String name, String email, String password, UserRole role) throws SQLException {
        String validatedName = validateName(name);
        String normalizedEmail = validateEmail(email);
        validatePassword(password);
        if (userDAO.emailExists(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        char[] passwordCharacters = password.toCharArray();
        try {
            String passwordHash = PasswordUtil.hash(passwordCharacters);
            User newUser = switch (role) {
                case BUYER -> new Buyer(0, validatedName, normalizedEmail, LocalDateTime.now());
                case SELLER -> new Seller(0, validatedName, normalizedEmail, LocalDateTime.now());
                case ADMIN -> throw new IllegalArgumentException("Admin registration is not available");
            };
            return userDAO.save(newUser, passwordHash);
        } finally {
            Arrays.fill(passwordCharacters, '\0');
        }
    }

    private static String validateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name is required");
        }
        String normalizedName = name.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 100) {
            throw new IllegalArgumentException("Name must contain 1 to 100 characters");
        }
        return normalizedName;
    }

    private static String validateEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > 254 || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        return normalizedEmail;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must contain 8 to 128 characters");
        }
    }
}