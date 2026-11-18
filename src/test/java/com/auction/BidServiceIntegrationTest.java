package com.auction;

import com.auction.dao.AuctionDAO;
import com.auction.dao.BidDAO;
import com.auction.dao.DatabaseConnection;
import com.auction.dao.DatabaseInitializer;
import com.auction.dao.UserDAO;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.Buyer;
import com.auction.model.Seller;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
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

class BidServiceIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseConnection databaseConnection;
    private AuctionDAO auctionDAO;
    private BidDAO bidDAO;
    private AuctionService auctionService;
    private BidService bidService;
    private Seller seller;
    private Seller otherSeller;
    private Buyer buyer;
    private Buyer otherBuyer;

    @BeforeEach
    void setUp() throws SQLException {
        databaseConnection = new DatabaseConnection(temporaryDirectory.resolve("bid-test.db"));
        new DatabaseInitializer(databaseConnection).initializeSchema();
        UserDAO userDAO = new UserDAO(databaseConnection);
        seller = (Seller) userDAO.save(new Seller("Seller One", "seller-one@example.test"), "hash");
        otherSeller = (Seller) userDAO.save(new Seller("Seller Two", "seller-two@example.test"), "hash");
        buyer = (Buyer) userDAO.save(new Buyer("Buyer One", "buyer-one@example.test"), "hash");
        otherBuyer = (Buyer) userDAO.save(new Buyer("Buyer Two", "buyer-two@example.test"), "hash");
        auctionDAO = new AuctionDAO(databaseConnection);
        bidDAO = new BidDAO(databaseConnection);
        auctionService = new AuctionService(auctionDAO, bidDAO);
        bidService = new BidService(databaseConnection, auctionDAO, bidDAO, userDAO);
    }

    @Test
    void acceptsValidBidAndCalculatesHighestPrice() throws SQLException {
        Auction auction = auction(seller, "Camera", AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1));

        Bid savedBid = bidService.placeBid(buyer, auction.getId(), 15.0);

        assertTrue(savedBid.getId() > 0);
        assertEquals(0, bidDAO.findHighestBid(auction.getId()).orElseThrow().getAmount()
                .compareTo(new BigDecimal("15.0")));
        assertEquals(0, bidDAO.findHighestBidAmount(auction.getId()).compareTo(new BigDecimal("15.0")));
        assertEquals(1, bidDAO.countBidsByAuction(auction.getId()));
    }

    @Test
    void requiresBidToExceedStartingAndCurrentHighestPrices() throws SQLException {
        Auction auction = auction(seller, "Desk", AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(buyer, auction.getId(), 10.0));
        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(buyer, auction.getId(), 9.0));
        bidService.placeBid(buyer, auction.getId(), 12.0);
        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(otherBuyer, auction.getId(), 12.0));
        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(otherBuyer, auction.getId(), 11.0));
        assertEquals(1, bidDAO.countBidsByAuction(auction.getId()));
    }

    @Test
    void permitsMultipleIncreasingBidsAndIdentifiesHighestBuyer() throws SQLException {
        Auction auction = auction(seller, "Record player", AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1));
        bidService.placeBid(buyer, auction.getId(), 12.0);
        Bid secondBid = bidService.placeBid(otherBuyer, auction.getId(), 20.0);

        assertEquals(2, bidDAO.countBidsByAuction(auction.getId()));
        assertEquals(otherBuyer.getId(), bidDAO.findHighestBid(auction.getId()).orElseThrow().getBuyerId());
        assertEquals(secondBid.getId(), bidDAO.findHighestBid(auction.getId()).orElseThrow().getId());
        assertTrue(bidService.getWinningBid(auction.getId()).isEmpty());
    }

    @Test
    void rejectsInvalidBuyerSellerAndMissingAuction() throws SQLException {
        Auction ownAuction = auctionDAO.save(new Auction(0, buyer.getId(), "Own auction", "Test details", "Other",
            BigDecimal.TEN, LocalDateTime.now().plusDays(1), AuctionStatus.ACTIVE, LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(buyer, ownAuction.getId(), 11.0));
        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(
                new Buyer(999_999, "Unknown", "unknown@example.test", LocalDateTime.now()),
                ownAuction.getId(), 11.0));
        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(buyer, 999_999, 11.0));
        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(buyer, ownAuction.getId(), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(buyer, ownAuction.getId(), Double.POSITIVE_INFINITY));
        assertEquals(0, bidDAO.countBidsByAuction(ownAuction.getId()));
    }

    @Test
    void rejectsExpiredAuctionAndCommitsUnsoldFinalization() throws SQLException {
        Auction expired = auction(seller, "Old vase", AuctionStatus.ACTIVE, LocalDateTime.now().minusMinutes(1));

        assertThrows(IllegalArgumentException.class, () -> bidService.placeBid(buyer, expired.getId(), 11.0));

        assertEquals(AuctionStatus.UNSOLD, auctionDAO.findById(expired.getId()).orElseThrow().getStatus());
        assertEquals(0, bidDAO.countBidsByAuction(expired.getId()));
    }

    @Test
    void rejectsClosedAuctionStatuses() throws SQLException {
        for (AuctionStatus status : List.of(AuctionStatus.SOLD, AuctionStatus.UNSOLD, AuctionStatus.CANCELLED)) {
            Auction auction = auction(seller, "Closed " + status, status, LocalDateTime.now().plusDays(1));
            assertThrows(IllegalArgumentException.class,
                    () -> bidService.placeBid(buyer, auction.getId(), 11.0), status.name());
            assertEquals(0, bidDAO.countBidsByAuction(auction.getId()));
        }
    }

    @Test
    void expiredAuctionWithBidsBecomesSoldAndWinnerIsDerivedAfterClose() throws SQLException {
        Auction expired = auction(seller, "Old radio", AuctionStatus.ACTIVE, LocalDateTime.now().minusMinutes(1));
        saveBid(buyer, expired, 15.0);
        saveBid(otherBuyer, expired, 25.0);

        assertTrue(bidService.getWinningBid(expired.getId()).isEmpty());
        auctionService.refreshExpiredAuctions();

        assertEquals(AuctionStatus.SOLD, auctionDAO.findById(expired.getId()).orElseThrow().getStatus());
        assertEquals(otherBuyer.getId(), bidService.getWinningBid(expired.getId()).orElseThrow().getBuyerId());
        assertEquals(AuctionStatus.SOLD, auctionDAO.findById(expired.getId()).orElseThrow().getStatus());
    }

    @Test
    void buyerHistoryAndResultsAreScopedToBuyerAndHideActiveWinner() throws SQLException {
        Auction active = auction(seller, "Active item", AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1));
        Auction won = auction(otherSeller, "Won item", AuctionStatus.ACTIVE, LocalDateTime.now().minusMinutes(1));
        Bid activeBid = saveBid(buyer, active, 12.0);
        saveBid(buyer, won, 15.0);
        saveBid(otherBuyer, won, 25.0);
        saveBid(otherBuyer, active, 20.0);
        auctionService.refreshExpiredAuctions();

        List<Bid> ownHistory = bidService.getBuyerHistory(buyer);
        assertEquals(2, ownHistory.size());
        assertTrue(ownHistory.stream().allMatch(item -> item.getBuyerId() == buyer.getId()));
        assertTrue(bidService.getBuyerHistory(otherBuyer).stream()
                .allMatch(item -> item.getBuyerId() == otherBuyer.getId()));
        List<BidService.BuyerAuctionResult> results = bidService.getBuyerAuctionResults(buyer);
        assertEquals(2, results.size());
        assertTrue(results.stream().filter(result -> result.auction().getId() == active.getId())
                .findFirst().orElseThrow().winningBid().isEmpty());
        assertEquals(otherBuyer.getId(), results.stream()
                .filter(result -> result.auction().getId() == won.getId())
                .findFirst().orElseThrow().winningBid().orElseThrow().getBuyerId());
        assertEquals(buyer.getId(), activeBid.getBuyerId());
    }

    @Test
    void sellerSummaryAndBidDetailsAreLimitedToTheirOwnAuctions() throws SQLException {
        Auction sellersAuction = auction(seller, "Seller's item", AuctionStatus.ACTIVE,
                LocalDateTime.now().plusDays(1));
        Auction soldAuction = auction(seller, "Sold item", AuctionStatus.ACTIVE,
            LocalDateTime.now().minusMinutes(1));
        Auction otherAuction = auction(otherSeller, "Other item", AuctionStatus.ACTIVE,
                LocalDateTime.now().plusDays(1));
        saveBid(buyer, sellersAuction, 16.0);
        saveBid(buyer, soldAuction, 18.0);
        saveBid(otherBuyer, soldAuction, 24.0);
        saveBid(otherBuyer, otherAuction, 18.0);
        auctionService.refreshExpiredAuctions();

        List<BidService.SellerAuctionSummary> summaries = bidService.getSellerAuctionSummaries(seller);
        assertEquals(2, summaries.size());
        BidService.SellerAuctionSummary activeSummary = summaries.stream()
            .filter(summary -> summary.auction().getId() == sellersAuction.getId()).findFirst().orElseThrow();
        BidService.SellerAuctionSummary soldSummary = summaries.stream()
            .filter(summary -> summary.auction().getId() == soldAuction.getId()).findFirst().orElseThrow();
        assertEquals(1, activeSummary.bidCount());
        assertEquals(AuctionStatus.SOLD, soldSummary.auction().getStatus());
        assertEquals(otherBuyer.getName(), soldSummary.winningBuyerName());
        assertEquals(buyer.getName(), bidService.getBidsForSellerAuction(seller, sellersAuction.getId())
                .get(0).buyerName());
        assertThrows(IllegalArgumentException.class,
                () -> bidService.getBidsForSellerAuction(seller, otherAuction.getId()));
        assertFalse(bidService.getSellerAuctionSummaries(otherSeller).isEmpty());
    }

    @Test
    void noBidsProduceZeroMaximumAndStartingPriceAsCurrentDisplay() throws SQLException {
        Auction auction = auction(seller, "No bids", AuctionStatus.ACTIVE, LocalDateTime.now().plusDays(1));

        assertEquals(0, bidDAO.findHighestBidAmount(auction.getId()).compareTo(BigDecimal.ZERO));
        assertEquals(0, auctionService.getCurrentHighestBid(auction.getId()).compareTo(auction.getStartingPrice()));
        assertTrue(bidDAO.findHighestBid(auction.getId()).isEmpty());
    }

    private Auction auction(Seller owner, String name, AuctionStatus status, LocalDateTime endTime)
            throws SQLException {
        return auctionDAO.save(new Auction(0, owner.getId(), name, "Test details", "Other", BigDecimal.TEN,
                endTime, status, LocalDateTime.now()));
    }

    private Bid saveBid(Buyer bidder, Auction auction, double amount) throws SQLException {
        return bidDAO.saveBid(new Bid(0, auction.getId(), bidder.getId(), BigDecimal.valueOf(amount),
                LocalDateTime.now()));
    }
}