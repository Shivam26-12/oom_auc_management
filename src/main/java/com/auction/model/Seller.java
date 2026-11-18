package com.auction.model;

import java.time.LocalDateTime;

public final class Seller extends User {
    public Seller(String name, String email) {
        this(0, name, email, LocalDateTime.now());
    }

    public Seller(int id, String name, String email, LocalDateTime createdAt) {
        super(id, name, email, createdAt);
    }

    @Override
    public UserRole getRole() {
        return UserRole.SELLER;
    }
}