package com.auction.service;

import com.auction.dao.AuctionDAO;
import com.auction.dao.BidDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.Seller;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AuctionService {
    private final AuctionDAO auctionDAO;
    private final BidDAO bidDAO;
    private final DatabaseConnection databaseConnection;

    public AuctionService() {
        this(new DatabaseConnection());
    }

    public AuctionService(DatabaseConnection databaseConnection) {
        this(new AuctionDAO(databaseConnection), new BidDAO(databaseConnection));
    }

    public AuctionService(AuctionDAO auctionDAO) {
        this(auctionDAO, new BidDAO(auctionDAO.getDatabaseConnection()));
    }

    public AuctionService(AuctionDAO auctionDAO, BidDAO bidDAO) {
        this.auctionDAO = Objects.requireNonNull(auctionDAO, "auctionDAO");
        this.bidDAO = Objects.requireNonNull(bidDAO, "bidDAO");
        this.databaseConnection = auctionDAO.getDatabaseConnection();
    }

    public Auction createAuction(Seller seller, Auction auction) throws SQLException {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(auction, "auction");
        if (seller.getId() <= 0) {
            throw new IllegalArgumentException("An authenticated seller account is required");
        }
        if (auction.getSellerId() != seller.getId()) {
            throw new IllegalArgumentException("Auction seller must match the authenticated seller");
        }
        if (auction.getStartingPrice() == null || auction.getStartingPrice().signum() <= 0) {
            throw new IllegalArgumentException("Starting price must be greater than zero");
        }
        if (auction.getEndTime() == null || !auction.getEndTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Auction end time must be in the future");
        }
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new IllegalArgumentException("A new auction must have ACTIVE status");
        }
        double storedStartingPrice = auction.getStartingPrice().doubleValue();
        if (!Double.isFinite(storedStartingPrice) || storedStartingPrice <= 0) {
            throw new IllegalArgumentException("Starting price is outside the supported SQLite range");
        }
        return auctionDAO.save(auction);
    }

    public List<Auction> getActiveAuctions() throws SQLException {
        refreshExpiredAuctions();
        return auctionDAO.findActiveAuctions();
    }

    public List<Auction> searchAuctions(String keyword) throws SQLException {
        refreshExpiredAuctions();
        return auctionDAO.searchAuctions(keyword);
    }

    public List<Auction> getAuctionsBySeller(int sellerId) throws SQLException {
        if (sellerId <= 0) {
            throw new IllegalArgumentException("A valid seller id is required");
        }
        refreshExpiredAuctions();
        return auctionDAO.findAuctionsBySeller(sellerId);
    }

    public Optional<Auction> getAuctionDetails(int auctionId) throws SQLException {
        refreshExpiredAuctions();
        return auctionDAO.findById(auctionId);
    }

    public BigDecimal getCurrentHighestBid(int auctionId) throws SQLException {
        return auctionDAO.findCurrentHighestBidAmount(auctionId);
    }

    public void refreshExpiredAuctions() throws SQLException {
        try (Connection connection = databaseConnection.openConnection()) {
            connection.setAutoCommit(false);
            try {
                List<Auction> expiredAuctions = auctionDAO.findExpiredActiveAuctions(connection, LocalDateTime.now());
                for (Auction auction : expiredAuctions) {
                    AuctionStatus finalStatus = bidDAO.findHighestBid(connection, auction.getId()).isPresent()
                            ? AuctionStatus.SOLD : AuctionStatus.UNSOLD;
                    auctionDAO.updateStatus(connection, auction.getId(), finalStatus);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }
}