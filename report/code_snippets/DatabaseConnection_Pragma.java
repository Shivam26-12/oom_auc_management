package com.auction.dao;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// Snippet: SQLite Foreign Key Integrity Enforcement
public Connection openConnection() throws SQLException {
    ensureDirectoryExists();
    Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
    try (Statement statement = connection.createStatement()) {
        statement.execute("PRAGMA foreign_keys = ON;");
        try (ResultSet rs = statement.executeQuery("PRAGMA foreign_keys;")) {
            if (rs.next() && rs.getInt(1) != 1) {
                connection.close();
                throw new SQLException("Failed to enable foreign keys in SQLite database");
            }
        }
    }
    return connection;
}
