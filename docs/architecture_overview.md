# Architecture Overview

## Architecture

OAMS is a local, layered Java desktop application. Swing screens call business services; services enforce role, ownership, bid, and lifecycle rules; DAOs issue JDBC operations against a local SQLite database. GUI classes do not access DAOs directly.

```text
gui (Swing)
  LoginFrame, RegisterFrame, DashboardFrame
  BuyerPanel, SellerPanel, AdminPanel
          |
          v
service (business rules)
  AuthService, AuctionService, BidService, AdminService
          |
          v
dao (JDBC)
  UserDAO, AuctionDAO, BidDAO, DatabaseConnection
          |
          v
SQLite database file
```

The planned Java package names are `oams.model`, `oams.service`, `oams.dao`, and `oams.gui`. The proposed class relationships are detailed in [`uml/class_diagram.puml`](uml/class_diagram.puml).

## Responsibilities

- **Model:** `User` is abstract; `Buyer`, `Seller`, and `Admin` provide the concrete roles. `Auction` and `Bid` represent the domain and expose controlled public operations over private state.
- **Service:** `AuthService` handles registration and authentication. `AuctionService` creates, queries, and lazily finalizes auctions. `BidService` validates and saves bids. `AdminService` provides administrative views and cancellation.
- **DAO:** `UserDAO`, `AuctionDAO`, and `BidDAO` map persisted rows to models and perform SQL operations. DAO methods receive the JDBC `Connection` used by the calling service so related operations can share a transaction.
- **Database connection:** `DatabaseConnection` opens connections to the local SQLite file, enables foreign keys, and initializes the schema when necessary.
- **GUI:** Login and registration frames collect credentials; `DashboardFrame` routes to the panel matching the authenticated user's role. Panels call services and display returned data or validation feedback.

## Persistence Outline

- **users:** `id`, `username` (unique), `email` (unique), `password_hash`, `role`, and `created_at`.
- **auctions:** `id`, `seller_id` (foreign key to users), product name, description, category, starting price, end time, status, and creation time.
- **bids:** `id`, `auction_id` (foreign key to auctions), `buyer_id` (foreign key to users), amount, and placement time.

Money values use a decimal representation and a SQLite representation chosen consistently by implementation (for example, integer minor units). User role values correspond to the concrete `User` subtype. Foreign keys are enabled. Indexes on product name, auction status/end time, and bid auction/buyer identifiers are reasonable for the stated desktop workload.

## Transaction and Expiration Policy

`BidService` performs auction lookup, active/expiry/ownership checks, highest-bid lookup, amount comparison, and bid insertion in one SQLite transaction. If an attempted bid discovers an expired active auction, it determines `SOLD` versus `UNSOLD`, updates the status, commits that finalization, and rejects the attempted bid. `AuctionService` applies the same finalization when browsing, searching, or loading details. No background worker is required.

For `SOLD`, the winner is derived from the highest persisted valid bid; bid history is retained. `CANCELLED` is a status change, not deletion. All data remains in the local database after application exit.

## Runtime and Deployment

The application runs on the user's computer in a compatible Java Runtime Environment. The SQLite JDBC driver is included with the application, and JDBC accesses a local database file. No network service is required. See [`uml/component_diagram.puml`](uml/component_diagram.puml) and [`uml/deployment_diagram.puml`](uml/deployment_diagram.puml).
