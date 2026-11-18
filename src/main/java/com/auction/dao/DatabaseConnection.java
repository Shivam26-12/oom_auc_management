package com.auction.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class DatabaseConnection {
    private final Path databaseFile;

    public DatabaseConnection() {
        this(resolveDefaultDatabasePath());
    }

    private static Path resolveDefaultDatabasePath() {
        Path relative = Path.of("database", "oams.db");
        if (Files.exists(relative)) {
            return relative;
        }
        try {
            Path codeSource = Path.of(DatabaseConnection.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path parent = codeSource.getParent();
            if (parent != null && parent.getParent() != null) {
                Path candidate = parent.getParent().resolve("database").resolve("oams.db");
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
        }
        return relative;
    }

    public DatabaseConnection(Path databaseFile) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile")
                .toAbsolutePath()
                .normalize();
    }

    public Connection openConnection() throws SQLException {
        Path parent = databaseFile.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new SQLException("Could not create database directory: " + parent, exception);
        }

        String databasePath = databaseFile.toString().replace('\\', '/');
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            try (var result = statement.executeQuery("PRAGMA foreign_keys")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new SQLException("SQLite foreign key enforcement could not be enabled");
                }
            }
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public Path getDatabaseFile() {
        return databaseFile;
    }
}