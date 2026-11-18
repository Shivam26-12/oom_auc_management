package com.auction.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseConnection databaseConnection;
    private DatabaseInitializer databaseInitializer;

    @BeforeEach
    void setUp() {
        databaseConnection = new DatabaseConnection(temporaryDirectory.resolve("nested").resolve("test.db"));
        databaseInitializer = new DatabaseInitializer(databaseConnection);
    }

    @Test
    void initializesTablesIndexesAndForeignKeyEnforcement() throws SQLException {
        databaseInitializer.initializeSchema();

        assertTrue(databaseConnection.getDatabaseFile().toFile().isFile());
        try (Connection connection = databaseConnection.openConnection();
             var statement = connection.createStatement();
             var tables = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table'")) {
            Set<String> tableNames = new HashSet<>();
            while (tables.next()) {
                tableNames.add(tables.getString("name"));
            }
            assertTrue(tableNames.containsAll(Set.of("users", "auctions", "bids")));

            try (var foreignKeys = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(foreignKeys.next());
                assertEquals(1, foreignKeys.getInt(1));
            }

            try (var indexes = statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%'")) {
                assertTrue(indexes.next());
                assertTrue(indexes.getInt(1) >= 6);
            }
        }
    }

    @Test
    void reinitializationPreservesExistingRows() throws SQLException {
        databaseInitializer.initializeSchema();
        try (Connection connection = databaseConnection.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (name, email, password_hash, role) "
                    + "VALUES ('Test Buyer', 'buyer@example.test', 'hash', 'BUYER')");
        }

        databaseInitializer.initializeSchema();

        try (Connection connection = databaseConnection.openConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM users")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    @Test
    void rejectsRowsThatViolateForeignKeys() throws SQLException {
        databaseInitializer.initializeSchema();

        try (Connection connection = databaseConnection.openConnection();
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO auctions (seller_id, product_name, starting_price, end_time, status) "
                            + "VALUES (999, 'Invalid owner', 1.0, '2099-01-01T00:00:00', 'ACTIVE')"));
        }
    }
}