# Business Rules

1. Every registered account has exactly one role: `BUYER`, `SELLER`, or `ADMIN`. The Java `Buyer`, `Seller`, and `Admin` classes specialize the abstract `User`; each overrides `getRole()`.
2. Buyer and Seller registration is public. Admin accounts are provisioned outside registration; all account types use the same login flow.
3. A successfully created auction is immediately `ACTIVE`.
4. An auction's starting price must be greater than zero. Its end time must be in the future at creation.
5. Only an authenticated Buyer may place a bid.
6. A Buyer may not bid on an auction whose Seller is that Buyer. This ownership check is enforced by the service layer.
7. A bid amount must be strictly greater than the current highest valid bid; if there is no bid, it must be strictly greater than the starting price.
8. A bid may be placed only while the auction is `ACTIVE` and its end time is in the future. The boundary is exclusive: at or after the end time, a bid is rejected.
9. Bid eligibility checks and bid insertion use one SQLite transaction so the accepted amount is compared with the persisted current state.
10. Expiration is handled lazily when auction data is loaded or a bid is attempted. No scheduler or background service is part of the design.
11. When an expired `ACTIVE` auction is finalized, its highest valid bid identifies the winner and its status becomes `SOLD`. The winner is derived from the retained bid history rather than stored as duplicate auction data.
12. When an expired `ACTIVE` auction has no valid bids, its status becomes `UNSOLD`.
13. An Administrator may change an `ACTIVE` listing to `CANCELLED`. Cancellation is not a physical deletion and does not erase bids or history.
14. `SOLD`, `UNSOLD`, and `CANCELLED` are terminal statuses. No transition from one terminal status to another is defined.
15. Users, auctions, bids, and auction statuses are stored persistently in SQLite. Foreign-key relationships preserve the account, auction, and bid associations.

## Auction Status Transitions

| Current status | Event / condition | Result |
|---|---|---|
| `ACTIVE` | End time reached and at least one valid bid exists | `SOLD`; highest bidder is the winner |
| `ACTIVE` | End time reached and no valid bid exists | `UNSOLD` |
| `ACTIVE` | Administrator removes listing | `CANCELLED` |
| `SOLD`, `UNSOLD`, `CANCELLED` | Any later event | No transition |
