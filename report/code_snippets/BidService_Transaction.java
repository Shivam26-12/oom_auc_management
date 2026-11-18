package com.auction.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Snippet: ACID Transaction Management and Concurrency Control in BidService
public Bid placeBid(Buyer buyer, int auctionId, double amount) throws SQLException {
    if (buyer == null || buyer.getId() <= 0) {
        throw new IllegalArgumentException("Sign in with a Buyer account before placing a bid");
    }
    BigDecimal bidAmount = BigDecimal.valueOf(amount);
    Bid savedBid = null;

    try (Connection connection = databaseConnection.openConnection()) {
        connection.setAutoCommit(false); // Begin ACID transaction
        try {
            lockAuctionForUpdate(connection, auctionId); // Row-level concurrency lock
            
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
                AuctionStatus expiredStatus = bidDAO.findHighestBid(connection, auctionId).isPresent()
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
            connection.commit(); // Commit on success
            return savedBid;
        } catch (SQLException | RuntimeException exception) {
            rollback(connection, exception); // Rollback on invariant failure
            throw exception;
        }
    }
}
