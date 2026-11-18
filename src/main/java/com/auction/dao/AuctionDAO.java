package com.auction.dao;

import com.auction.model.Auction;
import com.auction.model.AuctionStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AuctionDAO {
    private static final DateTimeFormatter DATABASE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS");
    private static final String SELECT_COLUMNS = "id, seller_id, product_name, description, category, "
            + "starting_price, end_time, status, created_at";

    private final DatabaseConnection databaseConnection;

    public AuctionDAO() {
        this(new DatabaseConnection());
    }

    public AuctionDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection, "databaseConnection");
    }

    public DatabaseConnection getDatabaseConnection() {
        return databaseConnection;
    }

    public Auction save(Auction auction) throws SQLException {
        Objects.requireNonNull(auction, "auction");
        String sql = "INSERT INTO auctions (seller_id, product_name, description, category, starting_price, "
                + "end_time, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindAuction(statement, auction);
            statement.executeUpdate();
            int generatedId = generatedId(statement, connection);
            return withId(auction, generatedId);
        }
    }

    public Optional<Auction> findById(int auctionId) throws SQLException {
        if (auctionId <= 0) {
            return Optional.empty();
        }
        try (Connection connection = databaseConnection.openConnection()) {
            return findById(connection, auctionId);
        }
    }

    public Optional<Auction> findById(Connection connection, int auctionId) throws SQLException {
        if (auctionId <= 0) {
            return Optional.empty();
        }
        String sql = "SELECT " + SELECT_COLUMNS + " FROM auctions WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapAuction(result)) : Optional.empty();
            }
        }
    }

    public List<Auction> findActiveAuctions() throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM auctions "
                + "WHERE status = ? AND end_time > ? ORDER BY end_time, id";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, AuctionStatus.ACTIVE.name());
            statement.setString(2, formatDateTime(LocalDateTime.now()));
            return readAuctions(statement);
        }
    }

    public List<Auction> findAuctionsBySeller(int sellerId) throws SQLException {
        if (sellerId <= 0) {
            return List.of();
        }
        String sql = "SELECT " + SELECT_COLUMNS + " FROM auctions WHERE seller_id = ? ORDER BY created_at DESC, id DESC";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, sellerId);
            return readAuctions(statement);
        }
    }

    public List<Auction> findAllAuctions() throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM auctions ORDER BY created_at DESC, id DESC";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return readAuctions(statement);
        }
    }

    public List<Auction> searchAuctions(String keyword) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM auctions "
                + "WHERE status = ? AND end_time > ? AND product_name LIKE ? COLLATE NOCASE "
                + "ORDER BY end_time, id";
        String searchTerm = keyword == null ? "" : keyword.trim();
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, AuctionStatus.ACTIVE.name());
            statement.setString(2, formatDateTime(LocalDateTime.now()));
            statement.setString(3, "%" + searchTerm + "%");
            return readAuctions(statement);
        }
    }

    public void updateStatus(int auctionId, AuctionStatus status) throws SQLException {
        try (Connection connection = databaseConnection.openConnection()) {
            updateStatus(connection, auctionId, status);
        }
    }

    public void updateStatus(Connection connection, int auctionId, AuctionStatus status) throws SQLException {
        Objects.requireNonNull(status, "status");
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, auctionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Auction was not found for status update: " + auctionId);
            }
        }
    }

    public List<Auction> findExpiredActiveAuctions(LocalDateTime now) throws SQLException {
        try (Connection connection = databaseConnection.openConnection()) {
            return findExpiredActiveAuctions(connection, now);
        }
    }

    public List<Auction> findExpiredActiveAuctions(Connection connection, LocalDateTime now) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM auctions WHERE status = ? AND end_time <= ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, AuctionStatus.ACTIVE.name());
            statement.setString(2, formatDateTime(Objects.requireNonNull(now, "now")));
            return readAuctions(statement);
        }
    }

    public boolean hasValidBids(int auctionId) throws SQLException {
        String sql = "SELECT 1 FROM bids WHERE auction_id = ? AND amount > 0 LIMIT 1";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public BigDecimal findCurrentHighestBidAmount(int auctionId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(bids.amount), auctions.starting_price) AS current_highest_bid "
                + "FROM auctions LEFT JOIN bids ON bids.auction_id = auctions.id WHERE auctions.id = ?";
        try (Connection connection = databaseConnection.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getObject("current_highest_bid") == null) {
                    throw new SQLException("Auction was not found for current-price lookup: " + auctionId);
                }
                return BigDecimal.valueOf(result.getDouble("current_highest_bid"));
            }
        }
    }

    private static List<Auction> readAuctions(PreparedStatement statement) throws SQLException {
        List<Auction> auctions = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                auctions.add(mapAuction(result));
            }
        }
        return List.copyOf(auctions);
    }

    private static void bindAuction(PreparedStatement statement, Auction auction) throws SQLException {
        statement.setInt(1, auction.getSellerId());
        statement.setString(2, auction.getProductName());
        statement.setString(3, auction.getDescription());
        statement.setString(4, auction.getCategory());
        statement.setDouble(5, auction.getStartingPrice().doubleValue());
        statement.setString(6, formatDateTime(auction.getEndTime()));
        statement.setString(7, auction.getStatus().name());
        statement.setString(8, formatDateTime(auction.getCreatedAt()));
    }

    private static int generatedId(PreparedStatement statement, Connection connection) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        try (Statement idStatement = connection.createStatement();
             ResultSet result = idStatement.executeQuery("SELECT last_insert_rowid()")) {
            if (result.next()) {
                return result.getInt(1);
            }
        }
        throw new SQLException("SQLite did not return the new auction id");
    }

    private static Auction mapAuction(ResultSet result) throws SQLException {
        AuctionStatus status;
        try {
            status = AuctionStatus.valueOf(result.getString("status"));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Database contains an unknown auction status", exception);
        }
        return new Auction(
                result.getInt("id"),
                result.getInt("seller_id"),
                result.getString("product_name"),
                result.getString("description"),
                result.getString("category"),
                BigDecimal.valueOf(result.getDouble("starting_price")),
                parseDateTime(result.getString("end_time")),
                status,
                parseNullableDateTime(result.getString("created_at")));
    }

    private static Auction withId(Auction auction, int id) {
        return new Auction(id, auction.getSellerId(), auction.getProductName(), auction.getDescription(),
                auction.getCategory(), auction.getStartingPrice(), auction.getEndTime(), auction.getStatus(),
                auction.getCreatedAt());
    }

    private static String formatDateTime(LocalDateTime value) {
        return Objects.requireNonNull(value, "dateTime").format(DATABASE_DATE_FORMAT);
    }

    private static LocalDateTime parseDateTime(String value) throws SQLException {
        if (value == null) {
            throw new SQLException("Database contains a missing required auction date");
        }
        return parseNullableDateTime(value);
    }

    private static LocalDateTime parseNullableDateTime(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATABASE_DATE_FORMAT);
        } catch (RuntimeException exception) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (RuntimeException fallbackException) {
                throw new SQLException("Database contains an invalid auction date", fallbackException);
            }
        }
    }
}