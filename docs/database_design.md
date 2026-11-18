# Database Design

## Architecture

OAMS uses a local SQLite database file at `database/oams.db`, relative to the application's working directory. `DatabaseConnection` creates the parent directory when opening a connection, opens it through JDBC, and enables SQLite foreign-key enforcement on every connection. `DatabaseInitializer` creates the schema and indexes with `IF NOT EXISTS`; initialization is safe to repeat and does not delete existing rows. The `Main` entry point initializes the schema, verifies a connection, and reports success without launching a GUI.

Java source uses `com.auction.dao` for database infrastructure and user persistence, `com.auction.model` for domain types, `com.auction.service` for authentication, `com.auction.security` for password hashing, and `com.auction.ui` for future presentation. These packages retain the Phase 1 layer responsibilities. `DatabaseInitializer` owns schema creation while `DatabaseConnection` owns connection setup.

## Tables

### `users`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | INTEGER | Primary key, auto-increment |
| `name` | TEXT | Not null |
| `email` | TEXT | Unique, not null |
| `password_hash` | TEXT | Not null |
| `role` | TEXT | Not null; limited to `BUYER`, `SELLER`, `ADMIN` |
| `created_at` | TEXT | Creation timestamp; nullable as specified |

### `auctions`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | INTEGER | Primary key, auto-increment |
| `seller_id` | INTEGER | Not null; foreign key to `users.id` |
| `product_name` | TEXT | Not null; indexed for searches |
| `description` | TEXT | Optional product description |
| `category` | TEXT | Optional category |
| `starting_price` | REAL | Not null; must be greater than zero |
| `end_time` | TEXT | Not null; ISO-8601 local date-time representation planned |
| `status` | TEXT | Not null; limited to `ACTIVE`, `SOLD`, `UNSOLD`, `CANCELLED` |
| `created_at` | TEXT | Creation timestamp; nullable as specified |

### `bids`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | INTEGER | Primary key, auto-increment |
| `auction_id` | INTEGER | Not null; foreign key to `auctions.id` |
| `buyer_id` | INTEGER | Not null; foreign key to `users.id` |
| `amount` | REAL | Not null; must be greater than zero |
| `bid_time` | TEXT | Not null; ISO-8601 local date-time representation planned |

## Relationships and Indexes

- One user acting as a Seller may own many auctions; every auction has one seller (`auctions.seller_id` references `users.id`).
- One auction may have many bids; every bid belongs to one auction (`bids.auction_id` references `auctions.id`).
- One user acting as a Buyer may place many bids; every bid has one buyer (`bids.buyer_id` references `users.id`).
- SQLite foreign-key checks are enabled per JDBC connection. The schema also checks allowed role/status values and positive starting prices/bid amounts.
- Indexes support seller auction lists, active/ending auction scans, product-name search, and bid queries by auction, amount, or buyer. The unique email constraint creates its own SQLite index.

## Java Connection Flow

`Main` constructs `DatabaseConnection` with the default relative file path, calls `DatabaseInitializer.initializeSchema()`, optionally provisions an initial administrator from environment variables, then opens and validates a connection. `DatabaseConnection.openConnection()` creates `database/` if necessary, opens a JDBC connection using the SQLite driver, enables `PRAGMA foreign_keys = ON`, and verifies that the pragma took effect. `UserDAO` uses prepared statements and closes each connection and statement with try-with-resources. Tests can pass a temporary database path to the same connection class.

## User Password Storage

The `password_hash` column contains a PBKDF2WithHmacSHA256 encoding: algorithm identifier, iteration count, per-password random salt, and derived hash. `PasswordUtil` creates and verifies this format; user model objects do not contain the stored hash. Authentication looks up the hash separately through `UserDAO.findPasswordHashByEmail`.

## Phase 1 Alignment Notes

The Phase 1 UML used a `username` attribute, while the Phase 2 database schema specifies `name`; Phase 3 uses the database's `name` field for the `User` model. The email address is the normalized unique login identifier. Phase 1 specifies decimal monetary values, while the database schema uses SQLite `REAL`; exact currency handling may require a schema revision (for example, integer minor units) before auction and bidding persistence is implemented. These are documented handoff decisions; only the class diagram's user/auth portion was updated for Phase 3.