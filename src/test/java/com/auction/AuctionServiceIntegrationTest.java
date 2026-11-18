package com.auction;

import com.auction.dao.AuctionDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.dao.DatabaseInitializer;
import com.auction.dao.UserDAO;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Buyer;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.service.AuctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionServiceIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseConnection databaseConnection;
    private AuctionDAO auctionDAO;
    private AuctionService auctionService;
    private Seller seller;
    private Seller otherSeller;
    private Buyer buyer;

    @BeforeEach
    void setUp() throws SQLException {
        databaseConnection = new DatabaseConnection(temporaryDirectory.resolve("auction-test.db"));
        new DatabaseInitializer(databaseConnection).initializeSchema();
        UserDAO userDAO = new UserDAO(databaseConnection);
        seller = (Seller) userDAO.save(new Seller("Seller One", "seller-one@example.test"), "hash");
        otherSeller = (Seller) userDAO.save(new Seller("Seller Two", "seller-two@example.test"), "hash");
        buyer = (Buyer) userDAO.save(new Buyer("Buyer One", "buyer@example.test"), "hash");
        auctionDAO = new AuctionDAO(databaseConnection);
        auctionService = new AuctionService(auctionDAO);
    }

    @Test
    void createsAndPersistsActiveAuctionAcrossDaoInstances() throws SQLException {
        Auction saved = auctionService.createAuction(seller, auction(
                seller.getId(), "Vintage Camera", AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(2)));

        assertTrue(saved.getId() > 0);
        assertEquals(AuctionStatus.ACTIVE, saved.getStatus());
        Auction reloaded = new AuctionDAO(databaseConnection).findById(saved.getId()).orElseThrow();
        assertEquals("Vintage Camera", reloaded.getProductName());
        assertEquals(seller.getId(), reloaded.getSellerId());
    }

    @Test
    void rejectsPastEndTimeAndAuctionOwnedByAnotherSeller() throws SQLException {
        Auction expired = auction(seller.getId(), "Expired listing", AuctionStatus.ACTIVE,
                LocalDateTime.now().minusMinutes(1));
        Auction mismatchedOwner = auction(otherSeller.getId(), "Wrong owner", AuctionStatus.ACTIVE,
                LocalDateTime.now().plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> auctionService.createAuction(seller, expired));
        assertThrows(IllegalArgumentException.class, () -> auctionService.createAuction(seller, mismatchedOwner));
        assertThrows(IllegalArgumentException.class, () -> auctionService.createAuction(
                seller, auction(seller.getId(), "Not active", AuctionStatus.CANCELLED,
                        LocalDateTime.now().plusDays(1))));
    }

    @Test
    void sellerQueriesOnlyTheirOwnListings() throws SQLException {
        auctionService.createAuction(seller, auction(seller.getId(), "Seller One Item",
                AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1)));
        Auction otherListing = auctionService.createAuction(otherSeller, auction(otherSeller.getId(),
                "Seller Two Item", AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1)));

        List<Auction> sellerListings = auctionService.getAuctionsBySeller(seller.getId());
        List<Auction> otherListings = auctionService.getAuctionsBySeller(otherSeller.getId());
        assertEquals(1, sellerListings.size());
        assertEquals(seller.getId(), sellerListings.get(0).getSellerId());
        assertEquals(otherSeller.getId(), otherListings.get(0).getSellerId());
        assertFalse(sellerListings.stream().anyMatch(item -> item.getId() == otherListing.getId()));
    }

    @Test
    void buyersBrowseAndSearchOnlyActiveUnexpiredAuctions() throws SQLException {
        Auction camera = auctionService.createAuction(seller, auction(seller.getId(), "Camera lens",
                AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1)));
        auctionService.createAuction(otherSeller, auction(otherSeller.getId(), "Desk lamp",
                AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1)));
        Auction expired = auctionDAO.save(auction(seller.getId(), "Expired camera",
                AuctionStatus.ACTIVE, LocalDateTime.now().minusMinutes(1)));

        assertEquals(2, auctionService.getActiveAuctions().size());
        assertEquals(List.of(camera.getId()), auctionService.searchAuctions("CAMERA").stream()
                .map(Auction::getId).toList());
        assertEquals(AuctionStatus.UNSOLD, auctionDAO.findById(expired.getId()).orElseThrow().getStatus());
    }

    @Test
    void detailsAndCurrentHighestPriceUseStartingPriceAndMaximumBid() throws SQLException {
        Auction auction = auctionService.createAuction(seller, auction(seller.getId(), "Monitor",
                AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1)));
        assertEquals(0, auctionService.getCurrentHighestBid(auction.getId()).compareTo(BigDecimal.TEN));

        try (Connection connection = databaseConnection.openConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO bids (auction_id, buyer_id, amount, bid_time) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, auction.getId());
            statement.setInt(2, buyer.getId());
            statement.setDouble(3, 42.50);
            statement.setString(4, LocalDateTime.now().toString());
            statement.executeUpdate();
        }

        assertEquals(0, auctionService.getCurrentHighestBid(auction.getId()).compareTo(new BigDecimal("42.5")));
        assertEquals("Monitor", auctionService.getAuctionDetails(auction.getId()).orElseThrow().getProductName());
    }

    @Test
    void expiresAuctionWithoutBidsAsUnsoldWhenDetailsAreLoaded() throws SQLException {
        Auction expired = auctionDAO.save(auction(seller.getId(), "Old book", AuctionStatus.ACTIVE,
                LocalDateTime.now().minusMinutes(1)));

        Auction details = auctionService.getAuctionDetails(expired.getId()).orElseThrow();
        assertEquals(AuctionStatus.UNSOLD, details.getStatus());
    }

    @Test
    void expiresAuctionWithValidBidsAsSoldWhenSellerListingsLoad() throws SQLException {
        Auction expired = auctionDAO.save(auction(seller.getId(), "Old record", AuctionStatus.ACTIVE,
                LocalDateTime.now().minusMinutes(1)));
        try (Connection connection = databaseConnection.openConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO bids (auction_id, buyer_id, amount, bid_time) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, expired.getId());
            statement.setInt(2, buyer.getId());
            statement.setDouble(3, 11.0);
            statement.setString(4, LocalDateTime.now().minusDays(1).toString());
            statement.executeUpdate();
        }

        Auction listing = auctionService.getAuctionsBySeller(seller.getId()).get(0);
        assertEquals(AuctionStatus.SOLD, listing.getStatus());
    }

    @Test
    void rejectsInvalidSellerIdentityAndUnsupportedStartingPriceRange() {
        assertThrows(IllegalArgumentException.class, () -> auctionService.getAuctionsBySeller(0));
        Auction hugePrice = auction(seller.getId(), "Over-range item", AuctionStatus.ACTIVE,
                LocalDateTime.now().plusDays(1), new BigDecimal("1e1000"));
        assertThrows(IllegalArgumentException.class, () -> auctionService.createAuction(seller, hugePrice));
    }

    private static Auction auction(int sellerId, String name, AuctionStatus status, LocalDateTime endTime) {
        return auction(sellerId, name, status, endTime, BigDecimal.TEN);
    }

    private static Auction auction(int sellerId, String name, AuctionStatus status,
                                   LocalDateTime endTime, BigDecimal startingPrice) {
        return new Auction(0, sellerId, name, "Test description", "Other", startingPrice,
                endTime, status, LocalDateTime.now());
    }
}