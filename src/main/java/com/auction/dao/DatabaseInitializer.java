package com.auction.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class DatabaseInitializer {
    private static final String[] CREATE_STATEMENTS = {
            "CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "email TEXT UNIQUE NOT NULL, "
                    + "password_hash TEXT NOT NULL, "
                    + "role TEXT NOT NULL CHECK (role IN ('BUYER', 'SELLER', 'ADMIN')), "
                    + "created_at TEXT"
                    + ")",
            "CREATE TABLE IF NOT EXISTS auctions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "seller_id INTEGER NOT NULL, "
                    + "product_name TEXT NOT NULL, "
                    + "description TEXT, "
                    + "category TEXT, "
                    + "starting_price REAL NOT NULL CHECK (starting_price > 0), "
                    + "end_time TEXT NOT NULL, "
                    + "status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SOLD', 'UNSOLD', 'CANCELLED')), "
                    + "created_at TEXT, "
                    + "FOREIGN KEY (seller_id) REFERENCES users(id)"
                    + ")",
            "CREATE TABLE IF NOT EXISTS bids ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "auction_id INTEGER NOT NULL, "
                    + "buyer_id INTEGER NOT NULL, "
                    + "amount REAL NOT NULL CHECK (amount > 0), "
                    + "bid_time TEXT NOT NULL, "
                    + "FOREIGN KEY (auction_id) REFERENCES auctions(id), "
                    + "FOREIGN KEY (buyer_id) REFERENCES users(id)"
                    + ")",
            "CREATE INDEX IF NOT EXISTS idx_auctions_seller_id ON auctions(seller_id)",
            "CREATE INDEX IF NOT EXISTS idx_auctions_status_end_time ON auctions(status, end_time)",
            "CREATE INDEX IF NOT EXISTS idx_auctions_product_name ON auctions(product_name)",
            "CREATE INDEX IF NOT EXISTS idx_bids_auction_id ON bids(auction_id)",
            "CREATE INDEX IF NOT EXISTS idx_bids_buyer_id ON bids(buyer_id)",
            "CREATE INDEX IF NOT EXISTS idx_bids_auction_amount ON bids(auction_id, amount DESC)"
    };

    private final DatabaseConnection databaseConnection;

    public DatabaseInitializer(DatabaseConnection databaseConnection) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection, "databaseConnection");
    }

    public void initializeSchema() throws SQLException {
        try (Connection connection = databaseConnection.openConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                for (String createStatement : CREATE_STATEMENTS) {
                    statement.execute(createStatement);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}