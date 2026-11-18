package com.auction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Demonstrates Encapsulation & Domain Logic.
 * Invariant validation in constructor and domain expiration calculation.
 */
public final class Auction {
    private final int id;
    private final int sellerId;
    private final String productName;
    private final String description;
    private final String category;
    private final BigDecimal startingPrice;
    private final LocalDateTime endTime;
    private final AuctionStatus status;
    private final LocalDateTime createdAt;

    public Auction(int id, int sellerId, String productName, String description, String category,
                   BigDecimal startingPrice, LocalDateTime endTime, AuctionStatus status,
                   LocalDateTime createdAt) {
        if (id < 0 || sellerId <= 0) {
            throw new IllegalArgumentException("Auction and seller ids are invalid");
        }
        String normalizedProductName = Objects.requireNonNull(productName, "productName").trim();
        if (normalizedProductName.isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be blank");
        }
        BigDecimal validatedStartingPrice = Objects.requireNonNull(startingPrice, "startingPrice");
        if (validatedStartingPrice.signum() <= 0) {
            throw new IllegalArgumentException("Starting price must be greater than zero");
        }

        this.id = id;
        this.sellerId = sellerId;
        this.productName = normalizedProductName;
        this.description = description;
        this.category = category;
        this.startingPrice = validatedStartingPrice;
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getSellerId() { return sellerId; }
    public String getProductName() { return productName; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public LocalDateTime getEndTime() { return endTime; }
    public AuctionStatus getStatus() { return status; }

    // Domain behavior: Encapsulated lazy expiration check
    public boolean isExpired() {
        return isExpired(LocalDateTime.now());
    }

    public boolean isExpired(LocalDateTime now) {
        return !Objects.requireNonNull(now, "now").isBefore(endTime);
    }
}
