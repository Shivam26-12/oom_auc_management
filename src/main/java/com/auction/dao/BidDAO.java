package com.auction.dao;

import com.auction.model.Bid;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BidDAO {
    private static final String BID_COLUMNS = "id, auction_id, buyer_id, amount, bid_time";

    private final DatabaseConnection databaseConnection;

    public BidDAO() {
        this(new DatabaseConnection());
    }

    public BidDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection, "databaseConnection");
    }

    public Bid saveBid(Bid bid) throws SQLException {
        try (Connection connection = databaseConnection.openConnection()) {
            return saveBid(connection, bid);
        }
    }

    public Bid saveBid(Connection connection, Bid bid) throws SQLException {
        Objects.requireNonNull(bid, "bid");
        String sql = "INSERT INTO bids (auction_id, buyer_id, amount, bid_time) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, bid.getAuctionId());
            statement.setInt(2, bid.getBuyerId());
            statement.setDouble(3, bid.getAmount().doubleValue());
            statement.setString(4, bid.getBidTime().toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return withId(bid, keys.getInt(1));
                }
            }
            try (Statement idStatement = connection.createStatement();
                 ResultSet generatedId = idStatement.executeQuery("SELECT last_insert_rowid()")) {
                if (generatedId.next()) {
                    return withId(bid, generatedId.getInt(1));
                }
            }
            throw new SQLException("SQLite did not return the new bid id");
        }
    }

    public List<Bid> findBidsByAuction(int auctionId) throws SQLException {
        try (Connection connection = databaseConnection.openConnection()) {
            return findBidsByAuction(connection, auctionId);
        }
    }

    public List<Bid> findBidsByAuction(Connection connection, int auctionId) throws SQLException {
        String sql = "SELECT " + BID_COLUMNS + " FROM bids WHERE auction_id = ? ORDER BY amount DESC, bid_time, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, auctionId);
            return readBids(statement);
        }
    }

    public List<Bid> findBidsByBuyer(int buyerId) throws SQLException {
        try (Connection connection = databaseConnection.openConnection()) {
            return findBidsByBuyer(connection, buyerId);
        }
    }

    public List<Bid> findBidsByBuyer(Connection connection, int buyerId) throws SQLException {
        String sql = "SELECT " + BID_COLUMNS + " FROM bids WHERE buyer_id = ? ORDER BY bid_time DESC, id DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, buyerId);
            return readBids(statement);
        }
    }

    public Optional<Bid> findHighestBid(int auctionId) throws SQLException {
        try (Connection connection = databaseConnection.openConnection()) {
            return findHighestBid(connection, auctionId);
        }
    }

    public Optional<Bid> findHighestBid(Connection connection, int auctionId) throws SQLException {
        String sql = "SELECT " + BID_COLUMNS + " FROM bids WHERE auction_id = ? "
                + "AND amount > 0 ORDER BY amount DESC, bid_time ASC, id ASC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapBid(result)) : Optional.empty();
            }
        }
    }

    public BigDecimal findHighestBidAmount(int auctionId) throws SQLException {
        try (Connection connection = databaseConnection.openConnection()) {
            return findHighestBidAmount(connection, auctionId);
        }
    }

    public BigDecimal findHighestBidAmount(Connection connection, int auctionId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(amount), 0) AS highest_amount FROM bids "
                + "WHERE auction_id = ? AND amount > 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Could not read the highest bid amount");
                }
                return BigDecimal.valueOf(result.getDouble("highest_amount"));
            }
        }
    }

    public int countBidsByAuction(int auctionId) throws SQLException {
        try (Connection connection = databaseConnection.openConnection()) {
            return countBidsByAuction(connection, auctionId);
        }
    }

    public int countBidsByAuction(Connection connection, int auctionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM bids WHERE auction_id = ? AND amount > 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Could not count auction bids");
                }
                return result.getInt(1);
            }
        }
    }

    public Optional<Bid> findWinningBid(int auctionId) throws SQLException {
        String sql = "SELECT " + qualifiedBidColumns() + " FROM bids "
                + "JOIN auctions ON auctions.id = bids.auction_id "
                + "WHERE bids.auction_id = ? AND auctions.status = 'SOLD' AND bids.amount > 0 "
                + "ORDER BY bids.amount DESC, bids.bid_time ASC, bids.id ASC LIMIT 1";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapBid(result)) : Optional.empty();
            }
        }
    }

    private static List<Bid> readBids(PreparedStatement statement) throws SQLException {
        List<Bid> bids = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                bids.add(mapBid(result));
            }
        }
        return List.copyOf(bids);
    }

    private static Bid mapBid(ResultSet result) throws SQLException {
        try {
            return new Bid(result.getInt("id"), result.getInt("auction_id"), result.getInt("buyer_id"),
                    BigDecimal.valueOf(result.getDouble("amount")), LocalDateTime.parse(result.getString("bid_time")));
        } catch (RuntimeException exception) {
            throw new SQLException("Database contains an invalid bid record", exception);
        }
    }

    private static String qualifiedBidColumns() {
        return "bids.id, bids.auction_id, bids.buyer_id, bids.amount, bids.bid_time";
    }

    private static Bid withId(Bid bid, int id) {
        return new Bid(id, bid.getAuctionId(), bid.getBuyerId(), bid.getAmount(), bid.getBidTime());
    }
}