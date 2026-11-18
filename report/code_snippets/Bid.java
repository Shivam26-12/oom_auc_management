package com.auction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable Domain Entity: Bid
 * Guarantees mathematical and temporal validity upon construction.
 */
public final class Bid {
    private final int id;
    private final int auctionId;
    private final int buyerId;
    private final BigDecimal amount;
    private final LocalDateTime bidTime;

    public Bid(int id, int auctionId, int buyerId, BigDecimal amount, LocalDateTime bidTime) {
        if (id < 0 || auctionId <= 0 || buyerId <= 0) {
            throw new IllegalArgumentException("Bid, auction, or buyer id is invalid");
        }
        BigDecimal validatedAmount = Objects.requireNonNull(amount, "amount");
        if (validatedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Bid amount must be greater than zero");
        }

        this.id = id;
        this.auctionId = auctionId;
        this.buyerId = buyerId;
        this.amount = validatedAmount;
        this.bidTime = Objects.requireNonNull(bidTime, "bidTime");
    }

    public int getId() { return id; }
    public int getAuctionId() { return auctionId; }
    public int getBuyerId() { return buyerId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getBidTime() { return bidTime; }
}
