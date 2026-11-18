package com.auction.dao;

import com.auction.model.Admin;
import com.auction.model.Buyer;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class UserDAO {
    private static final String SELECT_COLUMNS = "id, name, email, role, created_at";

    private final DatabaseConnection databaseConnection;

    public UserDAO() {
        this(new DatabaseConnection());
    }

    public UserDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection, "databaseConnection");
    }

    public User save(User user, String passwordHash) throws SQLException {
        Objects.requireNonNull(user, "user");
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("A password hash is required");
        }

        String sql = "INSERT INTO users (name, email, password_hash, role, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getName());
            statement.setString(2, user.getEmail());
            statement.setString(3, passwordHash);
            statement.setString(4, user.getRole().name());
            statement.setString(5, formatDateTime(user.getCreatedAt()));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return withId(user, generatedKeys.getInt(1));
                }
            }
            try (Statement idStatement = connection.createStatement();
                 ResultSet generatedId = idStatement.executeQuery("SELECT last_insert_rowid()")) {
                if (generatedId.next()) {
                    return withId(user, generatedId.getInt(1));
                }
            }
            throw new SQLException("SQLite did not return the new user id");
        }
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE email = ?";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapUser(result)) : Optional.empty();
            }
        }
    }

    public Optional<User> findById(int id) throws SQLException {
        if (id <= 0) {
            return Optional.empty();
        }
        try (Connection connection = databaseConnection.openConnection()) {
            return findById(connection, id);
        }
    }

    public Optional<User> findById(Connection connection, int id) throws SQLException {
        if (id <= 0) {
            return Optional.empty();
        }
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapUser(result)) : Optional.empty();
            }
        }
    }

    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE email = ? LIMIT 1";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public Optional<String> findPasswordHashByEmail(String email) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE email = ?";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeEmail(email));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString("password_hash")) : Optional.empty();
            }
        }
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users ORDER BY id";
        List<User> users = new ArrayList<>();
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                users.add(mapUser(result));
            }
        }
        return List.copyOf(users);
    }

    private static User mapUser(ResultSet result) throws SQLException {
        int id = result.getInt("id");
        String name = result.getString("name");
        String email = result.getString("email");
        UserRole role;
        try {
            role = UserRole.valueOf(result.getString("role"));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Database contains an unknown user role", exception);
        }
        LocalDateTime createdAt = parseDateTime(result.getString("created_at"));
        return switch (role) {
            case BUYER -> new Buyer(id, name, email, createdAt);
            case SELLER -> new Seller(id, name, email, createdAt);
            case ADMIN -> new Admin(id, name, email, createdAt);
        };
    }

    private static User withId(User user, int id) {
        return switch (user.getRole()) {
            case BUYER -> new Buyer(id, user.getName(), user.getEmail(), user.getCreatedAt());
            case SELLER -> new Seller(id, user.getName(), user.getEmail(), user.getCreatedAt());
            case ADMIN -> new Admin(id, user.getName(), user.getEmail(), user.getCreatedAt());
        };
    }

    private static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static LocalDateTime parseDateTime(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException exception) {
            throw new SQLException("Database contains an invalid user creation time", exception);
        }
    }
}