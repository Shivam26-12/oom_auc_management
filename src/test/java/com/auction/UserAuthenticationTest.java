package com.auction;

import com.auction.dao.DatabaseConnection;
import com.auction.dao.DatabaseInitializer;
import com.auction.dao.UserDAO;
import com.auction.model.Admin;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Buyer;
import com.auction.model.Bid;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.model.UserRole;
import com.auction.security.PasswordUtil;
import com.auction.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAuthenticationTest {
    @TempDir
    private java.nio.file.Path temporaryDirectory;

    private UserDAO userDAO;
    private AuthService authService;

    @BeforeEach
    void setUp() throws SQLException {
        DatabaseConnection databaseConnection = new DatabaseConnection(
                temporaryDirectory.resolve("auth-test.db"));
        new DatabaseInitializer(databaseConnection).initializeSchema();
        userDAO = new UserDAO(databaseConnection);
        authService = new AuthService(userDAO);
    }

    @Test
    void createsAllConcreteRolesAndDemonstratesInheritanceAndPolymorphism() {
        LocalDateTime createdAt = LocalDateTime.now();
        User buyer = new Buyer(1, "Buyer One", "buyer@example.test", createdAt);
        User seller = new Seller(2, "Seller One", "seller@example.test", createdAt);
        User admin = new Admin(3, "Admin One", "admin@example.test", createdAt);

        assertInstanceOf(Buyer.class, buyer);
        assertInstanceOf(Seller.class, seller);
        assertInstanceOf(Admin.class, admin);
        assertEquals(List.of(UserRole.BUYER, UserRole.SELLER, UserRole.ADMIN),
                List.of(buyer.getRole(), seller.getRole(), admin.getRole()));
    }

        @Test
        void auctionAndBidValidateTheirRequiredAmounts() {
        LocalDateTime now = LocalDateTime.now();
        Auction auction = new Auction(0, 1, "Camera", null, null, BigDecimal.TEN,
            now.plusDays(1), AuctionStatus.ACTIVE, now);
        Bid bid = new Bid(0, 1, 2, BigDecimal.ONE, now);

        assertFalse(auction.isExpired(now));
        assertTrue(auction.isExpired(now.plusDays(1)));
        assertEquals(BigDecimal.TEN, auction.getStartingPrice());
        assertEquals(BigDecimal.ONE, bid.getAmount());
        assertThrows(IllegalArgumentException.class, () -> new Auction(0, 1, "Camera", null,
            null, BigDecimal.ZERO, now.plusDays(1), AuctionStatus.ACTIVE, now));
        assertThrows(IllegalArgumentException.class,
            () -> new Bid(0, 1, 2, BigDecimal.ZERO, now));
        }

    @Test
    void hashesPasswordsWithUniqueSaltsAndVerifiesCorrectly() {
        char[] firstPassword = "correct horse battery".toCharArray();
        char[] secondPassword = "correct horse battery".toCharArray();
        String firstHash = PasswordUtil.hash(firstPassword);
        String secondHash = PasswordUtil.hash(secondPassword);

        assertNotEquals(firstHash, secondHash);
        assertArrayEquals(new char[firstPassword.length], firstPassword);
        assertTrue(PasswordUtil.verify("correct horse battery".toCharArray(), firstHash));
        assertFalse(PasswordUtil.verify("incorrect password".toCharArray(), firstHash));
        assertFalse(PasswordUtil.verify("correct horse battery".toCharArray(), "not-a-hash"));
    }

    @Test
    void registersBuyerAndNormalizesEmail() throws SQLException {
        Buyer buyer = authService.registerBuyer("  Buyer One  ", "  BUYER@Example.test ", "buyer-pass-1");

        assertEquals("Buyer One", buyer.getName());
        assertEquals("buyer@example.test", buyer.getEmail());
        assertEquals(UserRole.BUYER, buyer.getRole());
        assertTrue(userDAO.emailExists("BUYER@example.test"));
        assertTrue(userDAO.findPasswordHashByEmail(buyer.getEmail()).isPresent());
    }

    @Test
    void registersSellerAndRejectsDuplicateEmail() throws SQLException {
        Seller seller = authService.registerSeller("Seller One", "seller@example.test", "seller-pass-1");
        assertEquals(UserRole.SELLER, seller.getRole());

        assertThrows(IllegalArgumentException.class, () -> authService.registerBuyer(
                "Other Name", " SELLER@example.test ", "another-pass-1"));
    }

        @Test
        void rejectsMalformedEmailAndOutOfPolicyPasswords() {
        assertThrows(IllegalArgumentException.class,
            () -> authService.registerBuyer("Buyer", "not-an-email", "buyer-pass-1"));
        assertThrows(IllegalArgumentException.class,
            () -> authService.registerSeller("Seller", "seller@example.test", "short"));
        }

    @Test
    void loginReturnsCorrectSubtypeOnlyForValidCredentials() throws SQLException {
        Buyer registeredBuyer = authService.registerBuyer(
                "Buyer One", "buyer@example.test", "buyer-pass-1");

        Optional<User> loginResult = authService.login(" BUYER@example.test ", "buyer-pass-1");
        assertTrue(loginResult.isPresent());
        assertInstanceOf(Buyer.class, loginResult.orElseThrow());
        assertEquals(registeredBuyer.getId(), loginResult.orElseThrow().getId());
        assertTrue(authService.login("buyer@example.test", "wrong-password").isEmpty());
        assertTrue(authService.login("missing@example.test", "buyer-pass-1").isEmpty());
    }

    @Test
    void retrievesBuyerSellerAndAdminAsTheirConcreteTypes() throws SQLException {
        Buyer buyer = authService.registerBuyer("Buyer One", "buyer@example.test", "buyer-pass-1");
        Seller seller = authService.registerSeller("Seller One", "seller@example.test", "seller-pass-1");
        String adminHash = PasswordUtil.hash("admin-pass-1".toCharArray());
        Admin admin = (Admin) userDAO.save(
                new Admin("Admin One", "admin@example.test"), adminHash);

        assertInstanceOf(Buyer.class, userDAO.findById(buyer.getId()).orElseThrow());
        assertInstanceOf(Seller.class, userDAO.findByEmail(seller.getEmail()).orElseThrow());
        assertInstanceOf(Admin.class, userDAO.findById(admin.getId()).orElseThrow());
        assertEquals(List.of(UserRole.BUYER, UserRole.SELLER, UserRole.ADMIN),
                userDAO.findAll().stream().map(User::getRole).toList());
    }
}