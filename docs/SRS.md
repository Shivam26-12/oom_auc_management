# Software Requirements Specification

## 1. Purpose

This specification defines the requirements for the Online Auction Management System (OAMS), a small Java desktop application for buyers, sellers, and administrators. It is the agreed design baseline for later implementation; this phase does not implement the application.

## 2. Scope

OAMS uses Java Swing for its desktop interface and SQLite through JDBC for persistent storage. Buyers browse auctions, submit bids, and review their bidding and results. Sellers create auctions and monitor their listings. Administrators review users and listings and cancel inappropriate active auctions. The application resolves expiration when auction data is loaded or a bid is attempted; it does not require a background server or scheduler.

Out of scope are online payments, shipping, messaging, user self-service administration, and remote or cloud hosting. Administrator accounts are provisioned outside public registration.

## 3. Actors

- **Buyer:** A registered, authenticated user allowed to browse, search, inspect, bid, and review personal bidding results.
- **Seller:** A registered, authenticated user allowed to create auctions and inspect their own listings and bidding activity.
- **Administrator:** A provisioned, authenticated user allowed to view registered users and all auctions, monitor statuses, and cancel active listings.

## 4. Functional Requirements

- **FR-01 User registration:** The system shall allow a visitor to register as a Buyer or Seller with a unique username and email address and a password. Public registration shall not create administrator accounts.
- **FR-02 Account role:** Each account shall have exactly one role: `BUYER`, `SELLER`, or `ADMIN`. The Java model shall represent the role through the account subtype and its `getRole()` implementation.
- **FR-03 Login and authorization:** The system shall authenticate registered users and provide only the operations permitted for their role. Failed authentication shall not open an authenticated dashboard.
- **FR-04 Seller auction creation:** An authenticated Seller shall be able to create an auction with product name, description, category, starting price, and ending date and time.
- **FR-05 Auction creation validation:** The system shall reject a starting price less than or equal to zero and an ending time that is not in the future. A successfully created auction shall immediately have status `ACTIVE`.
- **FR-06 Auction browsing:** A Buyer shall be able to browse available active auctions. Before presenting auction data, the system shall resolve any expired active auctions encountered.
- **FR-07 Auction search:** A Buyer shall be able to search available auctions by product name. Search results shall follow the same availability and expiration rules as browsing.
- **FR-08 Auction details:** A Buyer shall be able to view a product's details, starting price, ending time, current highest bid (or starting price when no bid exists), and status.
- **FR-09 Bid submission:** Only an authenticated Buyer shall be allowed to submit a bid on an auction.
- **FR-10 Bid eligibility:** The system shall reject a bid when the auction does not exist, is not `ACTIVE`, has expired, or belongs to the bidding Buyer.
- **FR-11 Bid amount validation:** A bid shall be accepted only when its amount is strictly greater than the current highest valid bid, or the starting price when no valid bid exists.
- **FR-12 Bid persistence and feedback:** The system shall persist each accepted bid and return clear success or failure feedback to the Buyer. Validation and saving shall use a transaction so a bid is checked against the current persisted auction and bid state.
- **FR-13 Bidding history:** A Buyer shall be able to view their own bid history, including auction, amount, and time, and the result of each associated auction.
- **FR-14 Seller monitoring:** A Seller shall be able to view only their own auctions, their bidding activity, current status, and final result.
- **FR-15 Expiration handling:** When auction data is loaded or a bid is attempted, the system shall detect an `ACTIVE` auction whose ending time has passed and finalize it. No background expiration process is required.
- **FR-16 Winner determination:** At expiration, the highest valid bidder shall be identified as the winner and the auction status shall become `SOLD`. If there are no valid bids, the status shall become `UNSOLD`.
- **FR-17 Administrator oversight:** An Administrator shall be able to view registered users, all auctions, and their statuses.
- **FR-18 Listing removal:** An Administrator shall be able to remove an inappropriate active listing by changing its status to `CANCELLED`. The system shall retain the auction and its bid records rather than hard-delete them.
- **FR-19 Persistent storage:** The system shall persist users, auctions, bids, their relationships, and auction statuses in a local SQLite database using JDBC.

## 5. Non-Functional Requirements

- **NFR-01 Usability:** Swing screens shall use role-appropriate navigation, clearly label required fields, and display understandable validation and operation feedback.
- **NFR-02 Performance:** For a typical academic demonstration dataset of up to 10,000 auctions and 100,000 bids on a supported desktop, common list, search, detail, and history actions should complete within 2 seconds, excluding initial application startup and unusually slow storage.
- **NFR-03 Reliability:** Bid validation and insertion, and auction finalization, shall use SQLite transactions. A failed operation shall not leave a partially updated auction or bid state.
- **NFR-04 Security:** Passwords shall not be stored in plaintext; the application shall store a salted, one-way password hash. Service methods shall enforce role and ownership checks rather than relying only on hiding GUI controls.
- **NFR-05 Maintainability:** The application shall separate Swing presentation, business services, domain models, and JDBC DAOs into the packages described in `architecture_overview.md`.
- **NFR-06 Data persistence:** Committed user, auction, bid, and status data shall remain available after the application closes and reopens, subject to the integrity of the local database file.
- **NFR-07 Portability:** The project shall run on a desktop with a compatible Java Runtime Environment and the SQLite JDBC driver; it shall not require a network connection or server.
- **NFR-08 Data integrity:** The database shall enforce primary keys, foreign keys, required fields, and uniqueness of usernames and emails. Foreign-key enforcement shall be enabled for each SQLite connection.

## 6. Constraints and Assumptions

- The application is a single-user-at-a-time desktop program with a local SQLite file; multi-client server coordination is not required.
- Auction end times are interpreted using the desktop's local date and time. The implementation should consistently convert and compare them.
- Expiration is lazy: an auction can remain stored as `ACTIVE` after its ending time until it is loaded or a bid is attempted. Such a load or attempt must finalize it before it is treated as available.
- An auction's status is one of `ACTIVE`, `SOLD`, `UNSOLD`, or `CANCELLED`. `SOLD`, `UNSOLD`, and `CANCELLED` are terminal statuses.
- Bid amounts use a decimal monetary representation, not binary floating point.
- Buyer and Seller accounts are created through registration. Admin accounts are provisioned separately.
