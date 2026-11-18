package com.auction.service;

import com.auction.dao.AuctionDAO;
import com.auction.dao.BidDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.dao.UserDAO;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.Buyer;
import com.auction.model.Seller;
import com.auction.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BidService {
    private final DatabaseConnection databaseConnection;
    private final AuctionDAO auctionDAO;
    private final BidDAO bidDAO;
    private final UserDAO userDAO;
    private final AuctionService auctionService;

    public BidService() {
        this(new DatabaseConnection());
    }

    public BidService(DatabaseConnection databaseConnection) {
        this(databaseConnection, new AuctionDAO(databaseConnection), new BidDAO(databaseConnection),
                new UserDAO(databaseConnection));
    }

    public BidService(DatabaseConnection databaseConnection, AuctionDAO auctionDAO,
                      BidDAO bidDAO, UserDAO userDAO) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection, "databaseConnection");
        this.auctionDAO = Objects.requireNonNull(auctionDAO, "auctionDAO");
        this.bidDAO = Objects.requireNonNull(bidDAO, "bidDAO");
        this.userDAO = Objects.requireNonNull(userDAO, "userDAO");
        this.auctionService = new AuctionService(auctionDAO, bidDAO);
    }

    public Bid placeBid(Buyer buyer, int auctionId, double amount) throws SQLException {
        if (buyer == null || buyer.getId() <= 0) {
            throw new IllegalArgumentException("Sign in with a Buyer account before placing a bid");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Bid amount must be a finite number greater than zero");
        }
        if (auctionId <= 0) {
            throw new IllegalArgumentException("Select a valid auction");
        }

        BigDecimal bidAmount = BigDecimal.valueOf(amount);
        Bid savedBid = null;
        AuctionStatus expiredStatus = null;
        try (Connection connection = databaseConnection.openConnection()) {
            connection.setAutoCommit(false);
            try {
                lockAuctionForUpdate(connection, auctionId);
                User persistedBuyer = userDAO.findById(connection, buyer.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Buyer account is not valid"));
                if (!(persistedBuyer instanceof Buyer)) {
                    throw new IllegalArgumentException("Only a registered Buyer can place bids");
                }

                Auction auction = auctionDAO.findById(connection, auctionId)
                        .orElseThrow(() -> new IllegalArgumentException("Auction was not found"));
                if (auction.getStatus() != AuctionStatus.ACTIVE) {
                    throw new IllegalArgumentException("This auction is not accepting bids");
                }
                if (auction.getSellerId() == buyer.getId()) {
                    throw new IllegalArgumentException("You cannot bid on your own auction");
                }

                LocalDateTime now = LocalDateTime.now();
                if (auction.isExpired(now)) {
                    expiredStatus = bidDAO.findHighestBid(connection, auctionId).isPresent()
                            ? AuctionStatus.SOLD : AuctionStatus.UNSOLD;
                    auctionDAO.updateStatus(connection, auctionId, expiredStatus);
                } else {
                    BigDecimal currentPrice = bidDAO.findHighestBid(connection, auctionId)
                            .map(Bid::getAmount)
                            .orElse(auction.getStartingPrice());
                    if (bidAmount.compareTo(currentPrice) <= 0) {
                        throw new IllegalArgumentException("Your bid must be greater than " + currentPrice);
                    }
                    savedBid = bidDAO.saveBid(connection,
                            new Bid(0, auctionId, buyer.getId(), bidAmount, now));
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }

        if (expiredStatus != null) {
            throw new IllegalArgumentException("This auction has expired and is now " + expiredStatus);
        }
        return Objects.requireNonNull(savedBid, "savedBid");
    }

    public List<Bid> getBuyerHistory(Buyer buyer) throws SQLException {
        verifyBuyer(buyer);
        return bidDAO.findBidsByBuyer(buyer.getId());
    }

    public List<BuyerAuctionResult> getBuyerAuctionResults(Buyer buyer) throws SQLException {
        verifyBuyer(buyer);
        auctionService.refreshExpiredAuctions();
        List<Bid> bids = bidDAO.findBidsByBuyer(buyer.getId());
        LinkedHashMap<Integer, BigDecimal> highestPersonalBidByAuction = new LinkedHashMap<>();
        for (Bid bid : bids) {
            highestPersonalBidByAuction.merge(bid.getAuctionId(), bid.getAmount(), BigDecimal::max);
        }

        List<BuyerAuctionResult> results = new ArrayList<>();
        for (var entry : highestPersonalBidByAuction.entrySet()) {
            Optional<Auction> auction = auctionDAO.findById(entry.getKey());
            if (auction.isPresent()) {
                Auction currentAuction = auction.orElseThrow();
                Optional<Bid> winningBid = currentAuction.getStatus() == AuctionStatus.SOLD
                        ? bidDAO.findWinningBid(currentAuction.getId()) : Optional.empty();
                results.add(new BuyerAuctionResult(currentAuction, entry.getValue(), winningBid));
            }
        }
        return List.copyOf(results);
    }

    public List<SellerAuctionSummary> getSellerAuctionSummaries(Seller seller) throws SQLException {
        verifySeller(seller);
        auctionService.refreshExpiredAuctions();
        List<SellerAuctionSummary> summaries = new ArrayList<>();
        for (Auction auction : auctionDAO.findAuctionsBySeller(seller.getId())) {
            String winningBuyerName = null;
            if (auction.getStatus() == AuctionStatus.SOLD) {
                Optional<Bid> winningBid = bidDAO.findWinningBid(auction.getId());
                if (winningBid.isPresent()) {
                    winningBuyerName = userDAO.findById(winningBid.orElseThrow().getBuyerId())
                            .map(User::getName).orElse(null);
                }
            }
            summaries.add(new SellerAuctionSummary(auction,
                    auctionDAO.findCurrentHighestBidAmount(auction.getId()),
                    bidDAO.countBidsByAuction(auction.getId()), winningBuyerName));
        }
        return List.copyOf(summaries);
    }

    public List<SellerBidDetails> getBidsForSellerAuction(Seller seller, int auctionId) throws SQLException {
        verifySeller(seller);
        Auction auction = auctionDAO.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Auction was not found"));
        if (auction.getSellerId() != seller.getId()) {
            throw new IllegalArgumentException("You can only view bids on your own auctions");
        }

        List<SellerBidDetails> details = new ArrayList<>();
        for (Bid bid : bidDAO.findBidsByAuction(auctionId)) {
            String buyerName = userDAO.findById(bid.getBuyerId()).map(User::getName)
                    .orElse("Unknown buyer");
            details.add(new SellerBidDetails(bid, buyerName));
        }
        return List.copyOf(details);
    }

    public Optional<Bid> getWinningBid(int auctionId) throws SQLException {
        Optional<Auction> auction = auctionDAO.findById(auctionId);
        if (auction.isEmpty() || auction.orElseThrow().getStatus() != AuctionStatus.SOLD) {
            return Optional.empty();
        }
        return bidDAO.findWinningBid(auctionId);
    }

    public Optional<WinningBidDetails> getWinningBidDetails(int auctionId) throws SQLException {
        Optional<Bid> winningBid = getWinningBid(auctionId);
        if (winningBid.isEmpty()) {
            return Optional.empty();
        }
        Bid bid = winningBid.orElseThrow();
        String buyerName = userDAO.findById(bid.getBuyerId()).map(User::getName).orElse("Unknown buyer");
        return Optional.of(new WinningBidDetails(bid, buyerName));
    }

    public int countBidsByAuction(int auctionId) throws SQLException {
        return bidDAO.countBidsByAuction(auctionId);
    }

    private void verifyBuyer(Buyer buyer) throws SQLException {
        if (buyer == null || buyer.getId() <= 0
                || !(userDAO.findById(buyer.getId()).orElse(null) instanceof Buyer)) {
            throw new IllegalArgumentException("A valid signed-in Buyer account is required");
        }
    }

    private void verifySeller(Seller seller) throws SQLException {
        if (seller == null || seller.getId() <= 0
                || !(userDAO.findById(seller.getId()).orElse(null) instanceof Seller)) {
            throw new IllegalArgumentException("A valid signed-in Seller account is required");
        }
    }

    private static void lockAuctionForUpdate(Connection connection, int auctionId) throws SQLException {
        String sql = "UPDATE auctions SET status = status WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, auctionId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Auction was not found");
            }
        }
    }

    private static void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    public record BuyerAuctionResult(Auction auction, BigDecimal highestPersonalBid,
                                     Optional<Bid> winningBid) {
    }

    public record SellerAuctionSummary(Auction auction, BigDecimal currentHighestBid,
                                       int bidCount, String winningBuyerName) {
    }

    public record SellerBidDetails(Bid bid, String buyerName) {
    }

    public record WinningBidDetails(Bid bid, String buyerName) {
    }
}