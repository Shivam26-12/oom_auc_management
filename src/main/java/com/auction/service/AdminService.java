package com.auction.service;

import com.auction.dao.AuctionDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.dao.UserDAO;
import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.User;
import com.auction.model.UserRole;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AdminService {
    private final DatabaseConnection databaseConnection;
    private final UserDAO userDAO;
    private final AuctionDAO auctionDAO;

    public AdminService() {
        this(new DatabaseConnection(), new UserDAO(), new AuctionDAO());
    }

    public AdminService(DatabaseConnection databaseConnection, UserDAO userDAO, AuctionDAO auctionDAO) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection, "databaseConnection");
        this.userDAO = Objects.requireNonNull(userDAO, "userDAO");
        this.auctionDAO = Objects.requireNonNull(auctionDAO, "auctionDAO");
    }

    public List<User> getAllUsers() throws SQLException {
        return userDAO.findAll();
    }

    public List<Auction> getAllAuctions() throws SQLException {
        return auctionDAO.findAllAuctions();
    }

    public List<User> searchUsers(String keyword) throws SQLException {
        String term = keyword == null ? "" : keyword.trim();
        List<User> matches = new ArrayList<>();
        for (User user : userDAO.findAll()) {
            if (term.isEmpty() || user.getName().toLowerCase().contains(term.toLowerCase())
                    || user.getEmail().toLowerCase().contains(term.toLowerCase())) {
                matches.add(user);
            }
        }
        return List.copyOf(matches);
    }

    public List<Auction> searchAuctions(String keyword) throws SQLException {
        String term = keyword == null ? "" : keyword.trim();
        List<Auction> matches = new ArrayList<>();
        for (Auction auction : auctionDAO.findAllAuctions()) {
            if (term.isEmpty() || auction.getProductName().toLowerCase().contains(term.toLowerCase())
                    || auction.getDescription().toLowerCase().contains(term.toLowerCase())
                    || auction.getCategory().toLowerCase().contains(term.toLowerCase())) {
                matches.add(auction);
            }
        }
        return List.copyOf(matches);
    }

    public DashboardStatistics getDashboardStatistics() throws SQLException {
        List<User> users = userDAO.findAll();
        List<Auction> auctions = auctionDAO.findAllAuctions();
        long totalUsers = users.size();
        long totalBuyers = users.stream().filter(user -> user.getRole() == UserRole.BUYER).count();
        long totalSellers = users.stream().filter(user -> user.getRole() == UserRole.SELLER).count();
        long totalAdmins = users.stream().filter(user -> user.getRole() == UserRole.ADMIN).count();
        long totalAuctions = auctions.size();
        long activeAuctions = auctions.stream().filter(auction -> auction.getStatus() == AuctionStatus.ACTIVE).count();
        long soldAuctions = auctions.stream().filter(auction -> auction.getStatus() == AuctionStatus.SOLD).count();
        long unsoldAuctions = auctions.stream().filter(auction -> auction.getStatus() == AuctionStatus.UNSOLD).count();
        long cancelledAuctions = auctions.stream().filter(auction -> auction.getStatus() == AuctionStatus.CANCELLED).count();
        return new DashboardStatistics(totalUsers, totalBuyers, totalSellers, totalAdmins, totalAuctions,
                activeAuctions, soldAuctions, unsoldAuctions, cancelledAuctions);
    }

    public void cancelAuction(int auctionId, Admin admin) throws SQLException {
        cancelAuction(auctionId, (User) admin);
    }

    public void cancelAuction(int auctionId, User actor) throws SQLException {
        if (actor == null || actor.getId() <= 0) {
            throw new IllegalArgumentException("An authenticated Admin is required");
        }
        Optional<User> persistedActor = userDAO.findById(actor.getId());
        if (persistedActor.isEmpty() || persistedActor.orElseThrow().getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("Only an authenticated Admin can cancel auctions");
        }
        Auction auction = auctionDAO.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Auction not found"));
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only ACTIVE auctions can be cancelled");
        }
        auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELLED);
    }

    public record DashboardStatistics(long totalUsers, long totalBuyers, long totalSellers, long totalAdmins,
                                     long totalAuctions, long activeAuctions, long soldAuctions,
                                     long unsoldAuctions, long cancelledAuctions) {
    }
}
