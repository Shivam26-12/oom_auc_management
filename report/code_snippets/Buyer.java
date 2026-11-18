package com.auction.model;

import java.time.LocalDateTime;

/**
 * Demonstrates Inheritance & Polymorphism.
 * Extends the abstract User class and overrides getRole() polymorphically.
 */
public final class Buyer extends User {

    public Buyer(String name, String email) {
        this(0, name, email, LocalDateTime.now());
    }

    public Buyer(int id, String name, String email, LocalDateTime createdAt) {
        super(id, name, email, createdAt);
    }

    /**
     * Polymorphic implementation of the abstract getRole() method.
     */
    @Override
    public UserRole getRole() {
        return UserRole.BUYER;
    }
}
