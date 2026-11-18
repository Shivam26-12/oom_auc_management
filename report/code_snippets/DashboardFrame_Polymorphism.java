package com.auction.ui;

// Snippet: Polymorphic UI Dispatch & Java 17 Pattern Matching in DashboardFrame
private JPanel createDashboardPage(String page) {
    return switch (user.getRole()) {
        case BUYER -> createBuyerPage(page);
        case SELLER -> createSellerPage(page);
        case ADMIN -> createAdminPage(page);
    };
}

private JPanel createBuyerPage(String page) {
    if (user instanceof Buyer buyer) {
        return switch (page) {
            case "Overview" -> new BuyerPanel(buyer, auctionService, bidService);
            case "Browse auctions" -> new BuyerPanel(buyer, auctionService, bidService);
            case "My bids" -> buildBuyerTablePage("My Bids", ...);
            case "Auction history" -> buildBuyerTablePage("Auction Results", ...);
            default -> throw new IllegalArgumentException("Unknown Buyer dashboard page: " + page);
        };
    }
    throw new IllegalStateException("BUYER role does not match the account type");
}
