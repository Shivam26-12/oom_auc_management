# Bidding Implementation

## How Bidding Works

An authenticated `Buyer` selects an active auction and enters an amount. The Buyer panel delegates the request to `BidService`; the Buyer ID always comes from the authenticated model object, never from a GUI field. The service validates the amount, persisted Buyer role, auction state, expiration, seller ownership, and current price before `BidDAO` inserts the bid.

## Validation Rules

- The Buyer must have a positive ID that resolves to a persisted account whose role is `BUYER`.
- The auction must exist, remain `ACTIVE`, and have an end time later than the current local time.
- A Buyer cannot bid on an auction where their ID is the seller ID.
- The amount must be finite and greater than zero.
- The amount must be strictly greater than the highest valid bid; if there are no bids, it must be strictly greater than the starting price.
- `SOLD`, `UNSOLD`, and `CANCELLED` auctions reject bids.
- Rejected submissions are rolled back and do not add a row to `bids`.

## Service Responsibilities

`BidService` owns bid eligibility and the transaction that accepts or rejects a bid. It also exposes Buyer history/results, Seller auction summaries, Seller-owned bid details, winning bid lookup, and auction bid counts. Seller operations verify that the persisted account is a Seller; bid-detail lookup checks the auction's `seller_id` against that Seller before returning bids.

`AuctionService` retains auction creation, listing, search, details, and lazy expiration. Expiration refresh uses one transaction to find expired active auctions, inspect their highest bid, and update each status to `SOLD` or `UNSOLD`. The winner is derived from the highest bid only after an auction is `SOLD`; no winner table or duplicate winner field is stored.

## DAO Responsibilities

`BidDAO` uses prepared statements and try-with-resources for bid inserts, Buyer/Auction histories, highest bid lookup, bid counts, and winning bid retrieval. Connection-aware overloads let `BidService` share its transaction connection across `AuctionDAO`, `UserDAO`, and `BidDAO`.

The numeric highest-bid query is:

```sql
SELECT COALESCE(MAX(amount), 0)
FROM bids
WHERE auction_id = ? AND amount > 0
```

Display price remains `COALESCE(MAX(bids.amount), auctions.starting_price)`, so an auction with no bids displays its starting price. Winner selection orders valid bids by amount descending, then bid time and ID ascending for deterministic ties, and only returns a result for a `SOLD` auction.

## Transaction Handling

Bid placement opens one SQLite JDBC connection and disables auto-commit. It takes a write lock on the auction row, rechecks the persisted Buyer role, auction status, expiration, ownership, and latest highest bid on that connection, then inserts the bid only when the amount is valid. Valid bids commit. Validation or database failures roll back. If the attempt discovers an expired active auction, its `SOLD`/`UNSOLD` transition is committed and that attempted bid is rejected.

This uses SQLite's local transaction/locking behavior and adds no server, scheduler, or distributed-locking infrastructure. Money continues to use the existing SQLite `REAL` column and Java `BigDecimal` model; exact decimal currency storage remains limited by the Phase 2 schema choice.

## Buyer and Seller Dashboards

Buyer auction details show starting price, current highest bid, bid count, status, and (only after `SOLD`) winning bid and Buyer name. `My Bids` is filtered by the signed-in Buyer. Auction results show the Buyer’s highest personal bid and `ACTIVE`, `WON`, `LOST`, `UNSOLD`, or `CANCELLED`; an active auction never exposes a winner.

Seller summaries show each own listing's highest bid, number of bids, status, and winning Buyer only after `SOLD`. `View Bids` calls the service with the authenticated Seller and selected auction ID; the service enforces ownership before returning bid IDs, Buyer names, amounts, and times. All Swing database calls run through `SwingWorker`.

## Tests and Scope

The integration tests use temporary SQLite databases. They cover bid acceptance and rejection boundaries, multiple bids, invalid/own Buyers, auction statuses, expiration finalization, winning Buyer derivation, Buyer history scoping, Seller ownership checks, and starting-price display when no bids exist. Payments, notifications, and real-time bidding are not implemented.