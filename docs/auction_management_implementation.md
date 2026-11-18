# Auction Management Implementation

## Architecture

Auction features use the existing `com.auction.model`, `com.auction.service`, `com.auction.dao`, and `com.auction.ui` layers. `AuctionService` owns creation validation and lazy expiration. `AuctionDAO` owns SQLite queries and maps rows to the existing `Auction` model. Buyer and Seller panels call the service asynchronously with `SwingWorker`; they do not issue SQL themselves.

## Seller Auction Creation

The authenticated `SellerPanel` receives the `Seller` returned by login. Its **Create New Auction** dialog accepts product name, description, category, starting price, and an end date in `yyyy-MM-dd HH` format. It rejects missing fields, invalid categories, non-numeric/NaN/infinite/non-positive prices, malformed dates, and past end dates. The seller ID is taken only from the authenticated `Seller` object.

After UI validation, the panel constructs an `ACTIVE` `Auction` and calls `AuctionService.createAuction(seller, auction)`. The service independently verifies a persisted seller ID, matching ownership, positive finite SQLite-compatible starting price, future end time, and `ACTIVE` status before delegating to `AuctionDAO.save`. Successful saves display a confirmation and reload the seller's table.

## Seller Auction Management

The seller table calls `getAuctionsBySeller(seller.getId())`, which scopes results by the current authenticated seller ID and includes all statuses. It shows ID, product, category, starting price, current highest bid, end time, and status. An empty listing produces a clear empty-state message. It does not expose a user-entered seller ID or operations for editing/removing auctions.

## Buyer Browsing and Search

`BuyerPanel` loads available records through `getActiveAuctions()` and searches through `searchAuctions(keyword)`. Both service operations perform expiration refresh before returning results. The DAO additionally filters to `ACTIVE` and end times later than the current time. Search uses a prepared statement with a product-name `LIKE` parameter. Search, clear, and refresh update the same table; a selected row opens read-only details, and double-click is also supported.

Both tables use the specified display aggregate:

```sql
COALESCE(MAX(bids.amount), auctions.starting_price)
```

The aggregate is display-only. No bid submission controls or bid-writing operations were added. Prices use locale currency formatting; dates use a consistent local date-time display.

## Expiration

On auction list loads, the service fetches expired `ACTIVE` auctions. It checks the existing `bids` table for a positive bid and updates each listing to `SOLD` when a bid exists or `UNSOLD` otherwise. Loading details performs the same check for that auction before returning it. No scheduler, background thread, winner declaration, or notification mechanism is included. `SOLD`, `UNSOLD`, and `CANCELLED` statuses are preserved when seller listings are read.

## Persistence and Testing

`AuctionDAO` reuses `DatabaseConnection`, uses prepared statements and try-with-resources, and stores records in the existing `auctions` table. There is no schema change and no data reset. Integration tests use temporary SQLite files and cover persistence, seller filtering, browsing/search, display price, validation, and expired auctions with and without bids. Swing database operations use `SwingWorker` so the event-dispatch thread remains responsive.

The existing Phase 1 bid sequence diagram remains a future Phase 6 scenario; this phase does not implement bid placement.