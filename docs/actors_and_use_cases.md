# Actors and Use Cases

## Actors

- **Visitor:** An unauthenticated person who can register as a Buyer or Seller and attempt to log in.
- **Buyer:** An authenticated `BUYER` account that browses, searches, views details, bids, and reviews its own history and results.
- **Seller:** An authenticated `SELLER` account that creates auctions and views its own auction activity and results.
- **Administrator:** An authenticated, provisioned `ADMIN` account that views users and all auctions, monitors statuses, and cancels active listings.

## Use Cases

| ID | Use case | Primary actor | Description |
|---|---|---|---|
| UC-01 | Register account | Visitor | Create a Buyer or Seller account with unique credentials. Administrator registration is not public. |
| UC-02 | Log in | Visitor | Authenticate credentials and open the dashboard associated with the account's single role. |
| UC-03 | Browse auctions | Buyer | View available active auctions; resolve expired active auctions encountered during loading. |
| UC-04 | Search auctions | Buyer | Find available auctions by product name, applying the same expiration handling as browsing. |
| UC-05 | View auction details | Buyer | Inspect product details, ending time, status, and current highest bid or starting price. |
| UC-06 | Place bid | Buyer | Submit an amount for an auction. The system checks authentication, ownership, active status, expiration, and amount before saving. |
| UC-07 | View bidding history and results | Buyer | Review personal bids and whether each associated auction was won, lost, unsold, or cancelled. |
| UC-08 | Create auction | Seller | Create a listing with required product data, a positive starting price, and a future ending time. The new auction is `ACTIVE`. |
| UC-09 | View own auctions and activity | Seller | Review the Seller's listings, bid activity, statuses, and results. |
| UC-10 | View registered users | Administrator | Review persisted registered user accounts. |
| UC-11 | View and monitor all auctions | Administrator | Review all listings and their current statuses. Expired active listings encountered are finalized. |
| UC-12 | Cancel active listing | Administrator | Mark an inappropriate `ACTIVE` listing `CANCELLED` while retaining its records. |

## Use Case Diagram

See [`uml/use_case_diagram.puml`](uml/use_case_diagram.puml). The diagram keeps role-specific actions separate and shows the shared login capability without adding unnecessary include/extend relationships.
