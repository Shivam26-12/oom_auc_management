package com.auction.ui;

import com.auction.dao.AuctionDAO;
import com.auction.dao.BidDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.dao.DatabaseInitializer;
import com.auction.dao.UserDAO;
import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.Buyer;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.service.AdminService;
import com.auction.service.AuctionService;
import com.auction.service.AuthService;
import com.auction.service.BidService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPanelVerificationTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseConnection connection;
    private UserDAO userDAO;
    private AuctionDAO auctionDAO;
    private BidDAO bidDAO;
    private AdminService adminService;
    private AuctionService auctionService;
    private BidService bidService;
    private AuthService authService;

    private Admin admin;
    private Buyer buyer;
    private Seller seller;

    @BeforeEach
    void setUp() throws SQLException {
        connection = new DatabaseConnection(temporaryDirectory.resolve("admin-verification.db"));
        new DatabaseInitializer(connection).initializeSchema();
        userDAO = new UserDAO(connection);
        auctionDAO = new AuctionDAO(connection);
        bidDAO = new BidDAO(connection);

        adminService = new AdminService(connection, userDAO, auctionDAO, bidDAO);
        auctionService = new AuctionService(auctionDAO, bidDAO);
        bidService = new BidService(connection, auctionDAO, bidDAO, userDAO);
        authService = new AuthService(userDAO);

        String adminHash = com.auction.security.PasswordUtil.hash("AdminPassword123!".toCharArray());
        admin = (Admin) userDAO.save(new Admin(0, "Admin Master", "admin@verify.test", LocalDateTime.now()), adminHash);
        buyer = authService.registerBuyer("Buyer Bob", "buyer@verify.test", "BuyerPassword123!");
        seller = authService.registerSeller("Seller Sally", "seller@verify.test", "SellerPassword123!");
    }

    @Test
    void testAdminLoginAndRoleSecurity() throws SQLException {
        // Admin login succeeds
        Optional<User> loggedInAdmin = authService.login("admin@verify.test", "AdminPassword123!");
        assertTrue(loggedInAdmin.isPresent());
        assertEquals("Admin Master", loggedInAdmin.get().getName());
        assertTrue(loggedInAdmin.get() instanceof Admin);

        // Buyer/Seller role security — non-admin cannot cancel auctions
        Auction activeAuction = auctionDAO.save(new Auction(0, seller.getId(), "Antique Vase", "Old vase", "Antiques",
                BigDecimal.valueOf(50), LocalDateTime.now().plusDays(5), AuctionStatus.ACTIVE, LocalDateTime.now()));

        assertThrows(IllegalArgumentException.class, () -> adminService.cancelAuction(activeAuction.getId(), buyer));
        assertThrows(IllegalArgumentException.class, () -> adminService.cancelAuction(activeAuction.getId(), seller));
    }

    @Test
    void testDashboardStatisticsFromDatabase() throws SQLException {
        auctionDAO.save(new Auction(0, seller.getId(), "Item 1", "Desc", "Cat",
                BigDecimal.valueOf(10), LocalDateTime.now().plusDays(1), AuctionStatus.ACTIVE, LocalDateTime.now()));
        auctionDAO.save(new Auction(0, seller.getId(), "Item 2", "Desc", "Cat",
                BigDecimal.valueOf(20), LocalDateTime.now().minusDays(1), AuctionStatus.SOLD, LocalDateTime.now().minusDays(2)));
        auctionDAO.save(new Auction(0, seller.getId(), "Item 3", "Desc", "Cat",
                BigDecimal.valueOf(30), LocalDateTime.now().minusDays(1), AuctionStatus.UNSOLD, LocalDateTime.now().minusDays(2)));

        var stats = adminService.getDashboardStatistics();
        assertEquals(3, stats.totalUsers());
        assertEquals(1, stats.totalBuyers());
        assertEquals(1, stats.totalSellers());
        assertEquals(1, stats.totalAdmins());
        assertEquals(3, stats.totalAuctions());
        assertEquals(1, stats.activeAuctions());
        assertEquals(1, stats.soldAuctions());
        assertEquals(1, stats.unsoldAuctions());
        assertEquals(0, stats.cancelledAuctions());
    }

    @Test
    void testUserManagementSearchFilterAndDetails() throws SQLException {
        // View all users
        List<User> allUsers = adminService.getAllUsers();
        assertEquals(3, allUsers.size());

        // Search by name
        List<User> byName = adminService.searchUsers("Sally");
        assertEquals(1, byName.size());
        assertEquals("seller@verify.test", byName.get(0).getEmail());

        // Search by email
        List<User> byEmail = adminService.searchUsers("buyer@verify.test");
        assertEquals(1, byEmail.size());
        assertEquals("Buyer Bob", byEmail.get(0).getName());

        // Search by ID
        List<User> byId = adminService.searchUsers(String.valueOf(admin.getId()));
        assertEquals(1, byId.size());
        assertEquals("Admin Master", byId.get(0).getName());

        // Filter by role
        List<User> buyersOnly = adminService.searchUsers("", "BUYER");
        assertEquals(1, buyersOnly.size());
        assertEquals("buyer@verify.test", buyersOnly.get(0).getEmail());

        List<User> sellersOnly = adminService.searchUsers("", "SELLER");
        assertEquals(1, sellersOnly.size());
        assertEquals("seller@verify.test", sellersOnly.get(0).getEmail());

        // User summary details
        var sellerSummary = adminService.getUserSummary(seller.getId());
        assertEquals(0, sellerSummary.auctionCount());

        auctionDAO.save(new Auction(0, seller.getId(), "Painting", "Oil painting", "Art",
                BigDecimal.valueOf(200), LocalDateTime.now().plusDays(2), AuctionStatus.ACTIVE, LocalDateTime.now()));

        sellerSummary = adminService.getUserSummary(seller.getId());
        assertEquals(1, sellerSummary.auctionCount());
    }

    @Test
    void testAuctionManagementSearchFilterAndEnrichment() throws SQLException {
        Auction a1 = auctionDAO.save(new Auction(0, seller.getId(), "Vintage Watch", "Gold watch", "Jewelry",
                BigDecimal.valueOf(150), LocalDateTime.now().plusDays(4), AuctionStatus.ACTIVE, LocalDateTime.now()));
        Auction a2 = auctionDAO.save(new Auction(0, seller.getId(), "Gaming Console", "PS5", "Electronics",
                BigDecimal.valueOf(500), LocalDateTime.now().plusDays(2), AuctionStatus.ACTIVE, LocalDateTime.now()));

        // Place a bid on a1
        bidDAO.saveBid(new Bid(0, a1.getId(), buyer.getId(), BigDecimal.valueOf(175), LocalDateTime.now()));

        // Enriched auctions table data
        var rows = adminService.getEnrichedAuctions("", "ALL");
        assertEquals(2, rows.size());

        var row1 = rows.stream().filter(r -> r.id() == a1.getId()).findFirst().orElseThrow();
        assertEquals("Vintage Watch", row1.productName());
        assertEquals("Seller Sally", row1.sellerName());
        assertEquals(BigDecimal.valueOf(175.0), row1.currentHighestBid());
        assertEquals(1, row1.bidCount());

        // Search auctions by product name
        var searchByName = adminService.getEnrichedAuctions("Watch", "ALL");
        assertEquals(1, searchByName.size());

        // Search auctions by seller name
        var searchBySeller = adminService.getEnrichedAuctions("Sally", "ALL");
        assertEquals(2, searchBySeller.size());

        // Search auctions by ID
        var searchById = adminService.getEnrichedAuctions(String.valueOf(a2.getId()), "ALL");
        assertEquals(1, searchById.size());
        assertEquals("Gaming Console", searchById.get(0).productName());

        // Filter by status
        var activeAuctions = adminService.getEnrichedAuctions("", "ACTIVE");
        assertEquals(2, activeAuctions.size());

        var soldAuctions = adminService.getEnrichedAuctions("", "SOLD");
        assertEquals(0, soldAuctions.size());

        // Auction full details
        var fullDetails = adminService.getAuctionFullDetails(a1.getId());
        assertEquals("Vintage Watch", fullDetails.auction().getProductName());
        assertEquals("Seller Sally", fullDetails.seller().getName());
        assertEquals(1, fullDetails.bids().size());
        assertEquals("Buyer Bob", fullDetails.bids().get(0).buyerName());
        assertEquals(BigDecimal.valueOf(175.0), fullDetails.bids().get(0).amount());
    }

    @Test
    void testCancelActiveAuctionBusinessRules() throws SQLException {
        Auction activeAuction = auctionDAO.save(new Auction(0, seller.getId(), "Camera", "DSLR", "Photography",
                BigDecimal.valueOf(300), LocalDateTime.now().plusDays(5), AuctionStatus.ACTIVE, LocalDateTime.now()));

        // Place a bid
        Bid bid = bidDAO.saveBid(new Bid(0, activeAuction.getId(), buyer.getId(), BigDecimal.valueOf(320), LocalDateTime.now()));

        // Admin cancels active auction
        adminService.cancelAuction(activeAuction.getId(), admin);

        // Status changed to CANCELLED in DB
        Auction updated = auctionDAO.findById(activeAuction.getId()).orElseThrow();
        assertEquals(AuctionStatus.CANCELLED, updated.getStatus());

        // Bids are NOT deleted
        assertEquals(1, bidDAO.findBidsByAuction(activeAuction.getId()).size());

        // Cancelling already cancelled auction throws exception
        assertThrows(IllegalArgumentException.class, () -> adminService.cancelAuction(activeAuction.getId(), admin));

        // New bids are rejected on cancelled auction
        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(buyer, activeAuction.getId(), 350.0));
    }

    @Test
    void testCancelSoldAuctionRejected() throws SQLException {
        Auction soldAuction = auctionDAO.save(new Auction(0, seller.getId(), "Car", "Sedan", "Vehicles",
                BigDecimal.valueOf(5000), LocalDateTime.now().minusDays(1), AuctionStatus.SOLD, LocalDateTime.now().minusDays(5)));

        assertThrows(IllegalArgumentException.class, () -> adminService.cancelAuction(soldAuction.getId(), admin));
        assertEquals(AuctionStatus.SOLD, auctionDAO.findById(soldAuction.getId()).orElseThrow().getStatus());
    }

    @Test
    void testAdminPanelComponentRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AdminPanel panel = new AdminPanel(admin, adminService, auctionService);
            assertNotNull(panel);
        });
        Thread.sleep(1500); // Allow background SwingWorkers to complete and release SQLite locks
    }
}
