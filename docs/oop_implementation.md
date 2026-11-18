# OOP and User Management Implementation

## Four OOP Pillars

- **Encapsulation:** `User`, `Auction`, and `Bid` keep their fields private and final. Public getters expose the data needed by callers, while constructors validate identity and required values. There are no public setters for account or auction state.
- **Inheritance:** `Buyer`, `Seller`, and `Admin` extend the abstract `User` class and inherit its common identity fields and getters.
- **Abstraction:** `User` is abstract and declares `getRole()`. Its role-specific behavior is left to concrete account classes.
- **Polymorphism:** A `User` reference can refer to any of the three concrete types. Calling `getRole()` dispatches to the subtype implementation at runtime; `UserAuthenticationTest` verifies the three results through `User` references.

## Inheritance Hierarchy

```text
             abstract User
             /     |     \
          Buyer  Seller  Admin
```

`UserRole` contains exactly `BUYER`, `SELLER`, and `ADMIN`. `AuctionStatus` contains `ACTIVE`, `SOLD`, `UNSOLD`, and `CANCELLED`. `Auction` and `Bid` are independent immutable model classes; they do not introduce additional inheritance.

## Registration and Login

`AuthService.registerBuyer` and `registerSeller` validate the name, email, and password, trim and lowercase the email, reject an existing email, hash the password, then pass the user and hash to `UserDAO`. The database's unique email constraint remains the final duplicate guard. No public Admin registration method exists.

`AuthService.login` returns `Optional<User>`. It retrieves the stored hash by normalized email, verifies the supplied password, and returns the matching concrete User subtype only on success. Incorrect credentials return `Optional.empty()`; JDBC failures are propagated as `SQLException` for the caller to handle.

`UserDAO` uses prepared statements and maps each stored role into a `Buyer`, `Seller`, or `Admin`. Password hashes are selected only by `findPasswordHashByEmail`; they are not fields on `User` and are never returned by its getters.

## Password Security

`com.auction.security.PasswordUtil` uses Java's `PBKDF2WithHmacSHA256`, a fresh 16-byte `SecureRandom` salt per hash, 600,000 derivation iterations, and a 256-bit result. The `password_hash` column stores the algorithm identifier, iteration count, salt, and derived hash in a delimited Base64 representation. Verification derives a candidate hash and compares it with `MessageDigest.isEqual`.

Password arrays passed to `PasswordUtil.hash` and `verify` are cleared in `finally` blocks; `PBEKeySpec` is also cleared. Java `String` inputs cannot be zeroed, so AuthService limits their lifetime to the operation and converts them to temporary character arrays. No plaintext password is persisted.

## Initial Administrator

On startup, `Main` creates an initial Admin only when all three environment variables `OAMS_ADMIN_NAME`, `OAMS_ADMIN_EMAIL`, and `OAMS_ADMIN_PASSWORD` are present and nonblank. Existing email
 addresses are left untouched. If the variables are absent, database startup continues normally. The initial password is hashed using the same utility before it is saved.
