package com.auction;

import com.auction.dao.AuctionDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.dao.DatabaseInitializer;
import com.auction.dao.UserDAO;
import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Buyer;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminServiceIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseConnection connection;
    private UserDAO userDAO;
    private AuctionDAO auctionDAO;
    private AdminService adminService;
    private Admin admin;
    private Buyer buyer;
    private Seller seller;

    @BeforeEach
    void setUp() throws SQLException {
        connection = new DatabaseConnection(temporaryDirectory.resolve("admin-test.db"));
        new DatabaseInitializer(connection).initializeSchema();
        userDAO = new UserDAO(connection);
        auctionDAO = new AuctionDAO(connection);
        admin = (Admin) userDAO.save(new Admin("Admin One", "admin@example.test"), "hash");
        buyer = (Buyer) userDAO.save(new Buyer("Buyer One", "buyer@example.test"), "hash");
        seller = (Seller) userDAO.save(new Seller("Seller One", "seller@example.test"), "hash");
        adminService = new AdminService(connection, userDAO, auctionDAO);
    }

    @Test
    void adminCanListUsersAndAuctionStatistics() throws SQLException {
        assertEquals(3, adminService.getAllUsers().size());
        assertEquals(0, adminService.getAllAuctions().size());
        var stats = adminService.getDashboardStatistics();
        assertEquals(3, stats.totalUsers());
        assertEquals(1, stats.totalBuyers());
        assertEquals(1, stats.totalSellers());
        assertEquals(1, stats.totalAdmins());
        assertEquals(0, stats.totalAuctions());
        assertEquals(0, stats.activeAuctions());
        assertEquals(0, stats.soldAuctions());
        assertEquals(0, stats.unsoldAuctions());
        assertEquals(0, stats.cancelledAuctions());
    }

    @Test
    void adminCanSearchUsersAndAuctions() throws SQLException {
        Auction auction = auctionDAO.save(new Auction(0, seller.getId(), "Laptop", "Details", "Electronics",
                BigDecimal.TEN, LocalDateTime.now().plusDays(2), AuctionStatus.ACTIVE, LocalDateTime.now()));

        assertTrue(adminService.searchUsers("buyer").stream().anyMatch(user -> user.getEmail().equals("buyer@example.test")));
        assertTrue(adminService.searchAuctions("laptop").stream().anyMatch(item -> item.getId() == auction.getId()));
    }

    @Test
    void adminCanCancelOnlyActiveAuctions() throws SQLException {
        Auction active = auctionDAO.save(new Auction(0, seller.getId(), "Active item", "desc", "Other",
                BigDecimal.TEN, LocalDateTime.now().plusDays(2), AuctionStatus.ACTIVE, LocalDateTime.now()));
        Auction sold = auctionDAO.save(new Auction(0, seller.getId(), "Sold item", "desc", "Other",
                BigDecimal.TEN, LocalDateTime.now().plusDays(2), AuctionStatus.SOLD, LocalDateTime.now()));

        adminService.cancelAuction(active.getId(), admin);
        assertEquals(AuctionStatus.CANCELLED, auctionDAO.findById(active.getId()).orElseThrow().getStatus());
        assertThrows(IllegalArgumentException.class, () -> adminService.cancelAuction(sold.getId(), admin));
        assertEquals(AuctionStatus.SOLD, auctionDAO.findById(sold.getId()).orElseThrow().getStatus());
    }

    @Test
    void nonAdminCannotCancelAuctions() {
        Auction active = new Auction(0, seller.getId(), "Auction", "desc", "Other",
                BigDecimal.TEN, LocalDateTime.now().plusDays(2), AuctionStatus.ACTIVE, LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> adminService.cancelAuction(999, buyer));
        assertThrows(IllegalArgumentException.class, () -> adminService.cancelAuction(0, buyer));
        assertFalse(active.getStatus() == AuctionStatus.CANCELLED);
    }

    @Test
    void adminCanSearchUsersWithRoleFilterAndId() throws SQLException {
        // Search by role filter
        List<User> buyers = adminService.searchUsers("", "BUYER");
        assertEquals(1, buyers.size());
        assertEquals(buyer.getId(), buyers.get(0).getId());

        List<User> sellers = adminService.searchUsers("", "SELLER");
        assertEquals(1, sellers.size());
        assertEquals(seller.getId(), sellers.get(0).getId());

        // Search by user ID
        List<User> byId = adminService.searchUsers(String.valueOf(admin.getId()), "ALL");
        assertEquals(1, byId.size());
        assertEquals("admin@example.test", byId.get(0).getEmail());
    }

    @Test
    void adminCanGetEnrichedAuctionsAndDetails() throws SQLException {
        Auction auction = auctionDAO.save(new Auction(0, seller.getId(), "Smartphone", "64GB", "Electronics",
                BigDecimal.valueOf(100), LocalDateTime.now().plusDays(3), AuctionStatus.ACTIVE, LocalDateTime.now()));

        var enriched = adminService.getEnrichedAuctions("Smartphone", "ACTIVE");
        assertEquals(1, enriched.size());
        assertEquals("Seller One", enriched.get(0).sellerName());
        assertEquals(BigDecimal.valueOf(100.0), enriched.get(0).currentHighestBid());

        var fullDetails = adminService.getAuctionFullDetails(auction.getId());
        assertEquals("Smartphone", fullDetails.auction().getProductName());
        assertEquals("Seller One", fullDetails.seller().getName());
        assertEquals(0, fullDetails.bids().size());

        var userSummary = adminService.getUserSummary(seller.getId());
        assertEquals(1, userSummary.auctionCount());
    }
}