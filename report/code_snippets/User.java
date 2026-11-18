package com.auction.model;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Demonstrates Abstraction & Encapsulation.
 * Abstract base class providing immutable core identity state and strict invariant checks.
 */
public abstract class User {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    // Encapsulated state: private final attributes prevent unauthorized modification
    private final int id;
    private final String name;
    private final String email;
    private final LocalDateTime createdAt;

    protected User(int id, String name, String email, LocalDateTime createdAt) {
        if (id < 0) {
            throw new IllegalArgumentException("User id cannot be negative");
        }
        String normalizedName = Objects.requireNonNull(name, "name").trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 100) {
            throw new IllegalArgumentException("Name must contain 1 to 100 characters");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.length() > 254 || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("A valid email address is required");
        }

        this.id = id;
        this.name = normalizedName;
        this.email = normalizedEmail;
        this.createdAt = createdAt;
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    // Public read-only accessors ensure information hiding
    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Abstraction: forces specialized subclasses to provide their own role classification
    public abstract UserRole getRole();
}
