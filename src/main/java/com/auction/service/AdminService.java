package com.auction.service;

import com.auction.dao.AuctionDAO;
import com.auction.dao.BidDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.dao.UserDAO;
import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.User;
import com.auction.model.UserRole;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AdminService {
    private final DatabaseConnection databaseConnection;
    private final UserDAO userDAO;
    private final AuctionDAO auctionDAO;
    private final BidDAO bidDAO;

    public AdminService() {
        this(new DatabaseConnection(), new UserDAO(), new AuctionDAO());
    }

    public AdminService(DatabaseConnection databaseConnection, UserDAO userDAO, AuctionDAO auctionDAO) {
        this(databaseConnection, userDAO, auctionDAO, new BidDAO(databaseConnection));
    }

    public AdminService(DatabaseConnection databaseConnection, UserDAO userDAO, AuctionDAO auctionDAO, BidDAO bidDAO) {
        this.databaseConnection = Objects.requireNonNull(databaseConnection, "databaseConnection");
        this.userDAO = Objects.requireNonNull(userDAO, "userDAO");
        this.auctionDAO = Objects.requireNonNull(auctionDAO, "auctionDAO");
        this.bidDAO = Objects.requireNonNull(bidDAO, "bidDAO");
    }

    public DatabaseConnection getDatabaseConnection() {
        return databaseConnection;
    }

    public List<User> getAllUsers() throws SQLException {
        return userDAO.findAll();
    }

    public List<Auction> getAllAuctions() throws SQLException {
        return auctionDAO.findAllAuctions();
    }

    public List<User> searchUsers(String keyword) throws SQLException {
        return searchUsers(keyword, null);
    }

    public List<User> searchUsers(String keyword, String roleFilter) throws SQLException {
        String term = keyword == null ? "" : keyword.trim().toLowerCase();
        List<User> matches = new ArrayList<>();
        for (User user : userDAO.findAll()) {
            if (roleFilter != null && !roleFilter.isBlank() && !"ALL".equalsIgnoreCase(roleFilter.trim())) {
                if (!user.getRole().name().equalsIgnoreCase(roleFilter.trim())) {
                    continue;
                }
            }
            if (term.isEmpty()) {
                matches.add(user);
            } else {
                String idStr = String.valueOf(user.getId());
                if (idStr.equals(term)
                        || user.getName().toLowerCase().contains(term)
                        || user.getEmail().toLowerCase().contains(term)) {
                    matches.add(user);
                }
            }
        }
        return List.copyOf(matches);
    }

    public UserSummary getUserSummary(int userId) throws SQLException {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        int auctionCount = 0;
        int bidCount = 0;
        if (user.getRole() == UserRole.SELLER) {
            auctionCount = auctionDAO.findAuctionsBySeller(userId).size();
        } else if (user.getRole() == UserRole.BUYER) {
            bidCount = bidDAO.findBidsByBuyer(userId).size();
        }
        return new UserSummary(user, auctionCount, bidCount);
    }

    public List<Auction> searchAuctions(String keyword) throws SQLException {
        return searchAuctions(keyword, null);
    }

    public List<Auction> searchAuctions(String keyword, String statusFilter) throws SQLException {
        String term = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Auction> matches = new ArrayList<>();
        for (Auction auction : auctionDAO.findAllAuctions()) {
            if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                if (!auction.getStatus().name().equalsIgnoreCase(statusFilter.trim())) {
                    continue;
                }
            }
            if (term.isEmpty()) {
                matches.add(auction);
            } else {
                String idStr = String.valueOf(auction.getId());
                String sellerIdStr = String.valueOf(auction.getSellerId());
                if (idStr.equals(term)
                        || sellerIdStr.equals(term)
                        || auction.getProductName().toLowerCase().contains(term)
                        || (auction.getDescription() != null && auction.getDescription().toLowerCase().contains(term))
                        || (auction.getCategory() != null && auction.getCategory().toLowerCase().contains(term))) {
                    matches.add(auction);
                }
            }
        }
        return List.copyOf(matches);
    }

    public List<AdminAuctionRow> getEnrichedAuctions(String keyword, String statusFilter) throws SQLException {
        String term = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Auction> allAuctions = auctionDAO.findAllAuctions();
        List<User> allUsers = userDAO.findAll();
        Map<Integer, User> userMap = new HashMap<>();
        for (User u : allUsers) {
            userMap.put(u.getId(), u);
        }

        List<AdminAuctionRow> results = new ArrayList<>();
        for (Auction auction : allAuctions) {
            if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                if (!auction.getStatus().name().equalsIgnoreCase(statusFilter.trim())) {
                    continue;
                }
            }

            User seller = userMap.get(auction.getSellerId());
            String sellerName = seller != null ? seller.getName() : "Seller #" + auction.getSellerId();

            if (!term.isEmpty()) {
                String idStr = String.valueOf(auction.getId());
                String sellerIdStr = String.valueOf(auction.getSellerId());
                boolean matches = idStr.equals(term)
                        || sellerIdStr.equals(term)
                        || auction.getProductName().toLowerCase().contains(term)
                        || sellerName.toLowerCase().contains(term)
                        || (auction.getCategory() != null && auction.getCategory().toLowerCase().contains(term))
                        || (auction.getDescription() != null && auction.getDescription().toLowerCase().contains(term));
                if (!matches) {
                    continue;
                }
            }

            BigDecimal highestBid = auctionDAO.findCurrentHighestBidAmount(auction.getId());
            int bidCount = bidDAO.countBidsByAuction(auction.getId());

            results.add(new AdminAuctionRow(
                    auction.getId(),
                    auction.getProductName(),
                    auction.getSellerId(),
                    sellerName,
                    auction.getCategory(),
                    auction.getStartingPrice(),
                    highestBid,
                    auction.getEndTime(),
                    auction.getCreatedAt(),
                    auction.getStatus(),
                    bidCount
            ));
        }
        return List.copyOf(results);
    }

    public AdminAuctionDetail getAuctionFullDetails(int auctionId) throws SQLException {
        Auction auction = auctionDAO.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Auction not found: " + auctionId));
        User seller = userDAO.findById(auction.getSellerId()).orElse(null);
        BigDecimal highestBid = auctionDAO.findCurrentHighestBidAmount(auctionId);
        int bidCount = bidDAO.countBidsByAuction(auctionId);
        List<Bid> bids = bidDAO.findBidsByAuction(auctionId);
        List<BidDetail> bidDetails = new ArrayList<>();
        for (Bid b : bids) {
            User buyer = userDAO.findById(b.getBuyerId()).orElse(null);
            String buyerName = buyer != null ? buyer.getName() : "Buyer #" + b.getBuyerId();
            String buyerEmail = buyer != null ? buyer.getEmail() : "-";
            bidDetails.add(new BidDetail(b.getId(), b.getBuyerId(), buyerName, buyerEmail, b.getAmount(), b.getBidTime()));
        }
        return new AdminAuctionDetail(auction, seller, highestBid, bidCount, List.copyOf(bidDetails));
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

    public record UserSummary(User user, int auctionCount, int bidCount) {}

    public record AdminAuctionRow(
            int id,
            String productName,
            int sellerId,
            String sellerName,
            String category,
            BigDecimal startingPrice,
            BigDecimal currentHighestBid,
            LocalDateTime endTime,
            LocalDateTime createdAt,
            AuctionStatus status,
            int bidCount
    ) {}

    public record AdminAuctionDetail(
            Auction auction,
            User seller,
            BigDecimal highestBid,
            int bidCount,
            List<BidDetail> bids
    ) {}

    public record BidDetail(
            int bidId,
            int buyerId,
            String buyerName,
            String buyerEmail,
            BigDecimal amount,
            LocalDateTime bidTime
    ) {}

    public record DashboardStatistics(long totalUsers, long totalBuyers, long totalSellers, long totalAdmins,
                                     long totalAuctions, long activeAuctions, long soldAuctions,
                                     long unsoldAuctions, long cancelledAuctions) {
    }
}
