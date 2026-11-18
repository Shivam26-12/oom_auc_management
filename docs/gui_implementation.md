# GUI Implementation

## Architecture

The Swing user interface is in `com.auction.ui`. `LoginFrame` and `RegisterFrame` collect credentials and call the existing `AuthService`; neither frame accesses JDBC nor performs hashing. After authentication, `DashboardFrame` receives the returned `User` and the same service instance. The role panel classes are presentation-only and contain no auction, bid, or administration operations.

Authentication and registration database operations run through `SwingWorker`, keeping JDBC and password hashing off the Swing event-dispatch thread. UI updates and dialogs run in the worker's `done()` callback on the event-dispatch thread.

## Screens

- **LoginFrame:** Email and password inputs, sign-in action, and a link to public registration. Empty credentials and authentication/database failures produce clear messages.
- **RegisterFrame:** Full name, email, password, confirmation, and a role selector containing only `BUYER` and `SELLER`. The form performs user-facing validation and delegates authoritative validation, duplicate checks, hashing, and persistence to `AuthService`.
- **DashboardFrame:** Application header, account name and role, role-specific sidebar, main content area, and logout action. Sidebar entries are buttons that switch the central card view to the selected role-appropriate overview or placeholder page. It receives a `User`, chooses a panel using `getRole()`, and checks the corresponding concrete subtype before showing that panel.
- **BuyerPanel:** Welcome/account role plus placeholder sections for auction browsing, bids, and auction history.
- **SellerPanel:** Welcome/account role plus placeholder sections for auction creation, own auctions, and results.
- **AdminPanel:** Welcome/admin role plus placeholder sections for user management, auction monitoring, and listing moderation.

## Components and Styling

The screens use `JFrame`, `JPanel`, `JLabel`, `JTextField`, `JPasswordField`, `JComboBox`, `JButton`, `JOptionPane`, and `SwingWorker`. Layout uses `BorderLayout`, `GridBagLayout`, and `BoxLayout`; no absolute positioning or external GUI libraries are used. The shared package-private `UiStyle` helper provides the light surface colors, blue accent, consistent typography, fields, buttons, and informational section styling.

## Authentication and Navigation

1. `Main` initializes the SQLite schema, runs optional environment-based Admin provisioning, creates one `AuthService`, then creates `LoginFrame` using `SwingUtilities.invokeLater()`.
2. Login reads the email and password, clears the temporary password character array, and calls `AuthService.login()`. Invalid credentials stay on the login screen. Database errors are shown without exposing internal details.
3. On success, the frame passes the authenticated `User` to `DashboardFrame`. Dashboard selection switches on `User.getRole()` and requires the matching `Buyer`, `Seller`, or `Admin` instance, preventing role mismatch from opening another role's panel.
4. The registration button disposes the login frame and opens `RegisterFrame`. Returning or closing registration opens one login frame; successful registration returns to sign-in with the email prefilled. The role selector has no Admin option.
5. Logout opens a fresh login frame and disposes the dashboard. It does not modify the SQLite database.

## Scope

Auction, bid, winner-determination, and administrator-management controls are placeholders only. GUI behavior should be manually exercised in a graphical desktop session; automated Maven tests cover service and persistence behavior but do not claim interactive window verification.