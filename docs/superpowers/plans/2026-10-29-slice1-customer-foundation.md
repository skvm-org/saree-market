# Slice 1 — Customer Foundation (backend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the `RESELLER` role to `CUSTOMER`, give every saree a seller-set quantity, let sellers store a UPI ID and flat shipping fee, and add customer addresses, wishlist and cart to the backend.

**Architecture:** Same Spring Boot monolith. New feature packages `address`, `wishlist`, `cart` follow the `booking` package style (controller, service with `NamedParameterJdbcTemplate` plain SQL, `dto/` records). Each table lands in its own Flyway migration so no applied migration is ever edited. Booking code stays and keeps working until slice 5; it only gets the role rename.

**Tech Stack:** Java 21, Spring Boot 4.1, Flyway, PostgreSQL, JUnit 5 + Testcontainers (`IntegrationTestBase`), Jackson 3 (`tools.jackson`).

**Spec:** `docs/superpowers/specs/2026-10-28-customer-orders-redesign-design.md` (sections 2, 4, 6 and 9 slice 1). Migration numbering differs from the spec because each table gets its own file: V6 role, V7 quantity, V8 seller payment settings, V9 address, V10 wishlist, V11 cart. Orders (slice 2) start at V12 and the booking drop (slice 5) is V13. Task 8 updates the spec to match.

## Global Constraints

- Never edit an applied Flyway migration; add `V<n>__description.sql` only (CLAUDE.md).
- Work on branch `customer-orders`. **Do not push to `main` until slice 5**: a push to `main` auto-deploys, and the `RESELLER`→`CUSTOMER` rename breaks the APK friends have installed. Commit locally per task; push only the branch if a backup is wanted.
- Roles after this slice: `SELLER`, `CUSTOMER`, `ADMIN`. `RESELLER` must no longer be accepted anywhere.
- Prices and money are whole-rupee `INTEGER`s. Quantity is a whole number 0–10000.
- Controllers return DTO records, never entities. Errors use `ApiException(HttpStatus, code, message[, fields])`. All endpoints under `/api/v1`.
- Customer-only endpoints use `@PreAuthorize("hasRole('CUSTOMER')")`; another customer's address or cart data is answered `404 NOT_FOUND`, never `403`.
- Do not add Co-Authored-By or Claude attribution lines to commits (user rule).
- `./mvnw verify` from `backend/` must pass at the end of every task. Containers need OrbStack running (`open -a OrbStack`); use an empty `DOCKER_CONFIG` dir if the Docker credential helper prompts.
- Use `perl -pi -e` for bulk edits; macOS `sed -i` needs a suffix argument and has failed here before.

## Review Focus

Most likely to bite first:
1. PATCH `quantity` with `-1`, `10001`, `2.5`, `"3"`, `null` → `400 VALIDATION_ERROR` and the stored quantity is unchanged (Task 2).
2. Two requests setting the same cart line at once → one row, no 500 (Task 7).
3. Customer A reading, changing or deleting customer B's address or cart line → `404`, B's data untouched; sellers `403`, anonymous `401` (Tasks 4, 6, 7).
4. A cart line whose saree later sells out, goes on hold, is deleted, or whose seller is disabled → `GET /cart` still answers 200, shows `inStock=false`, and leaves that line out of totals (Task 7).
5. Existing sellers have no UPI ID: profile `GET` returns `upiId: null`, a `PUT` that omits `upiId` keeps it null, and a malformed ID such as `abc` is a 400 (Task 3).

---

### Task 1: Rename the role to CUSTOMER

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__customer_role.sql`
- Modify: `auth/Role.java`, `auth/AuthService.java:118`, `seller/SellerController.java:19,35`, `collection/CollectionController.java:50`, `notification/NotificationController.java:21`, `follow/FollowController.java:25`, `catalog/CatalogController.java:34,41,49,57`, `catalog/CatalogService.java:267,283`, `booking/BookingController.java` (all `hasRole('RESELLER')` / `hasAnyRole(... 'RESELLER')`), under `backend/src/main/java/com/sareemarket/`
- Modify tests: `IntegrationTestBase.java` (rename `registerReseller()` → `registerCustomer()` and send `"role":"CUSTOMER"`), plus every test file containing `RESELLER` or `registerReseller` (auth, admin, booking, follow, catalog, notification, seller tests; `RoleProbeController`)
- Test: `auth/AuthIntegrationTest.java`

**Interfaces:**
- Produces: `Role` enum `{SELLER, CUSTOMER, ADMIN}`; JWT claim `role` is `"CUSTOMER"` for customers; test helper `String registerCustomer()` (returns a JWT, same as the old `registerReseller()`).
- `BookingService.Viewer.RESELLER` and the DB column names `reseller_id` stay as they are (internal; deleted with bookings in slice 5).

- [ ] **Step 1: Write the failing tests** in `AuthIntegrationTest`: `registerAsCustomerReturnsCustomerRole` (POST `/api/v1/auth/register` with `"role":"CUSTOMER"` → 201 and `user.role == "CUSTOMER"`), and `registerAsResellerIsRejected` (`"role":"RESELLER"` → 400).
- [ ] **Step 2: Run** `./mvnw test -Dtest=AuthIntegrationTest` → both FAIL (enum has no `CUSTOMER`).
- [ ] **Step 3: Create `V6__customer_role.sql`:** drop the `app_user.role` check constraint (look its name up in `pg_constraint`, as V5 did for `item_status_check`; do not guess), run `UPDATE app_user SET role = 'CUSTOMER' WHERE role = 'RESELLER'`, then add `CHECK (role IN ('SELLER','CUSTOMER','ADMIN'))`. `VARCHAR(10)` already fits `CUSTOMER`.
- [ ] **Step 4: Rename in code:** `Role.RESELLER` → `Role.CUSTOMER`; `perl -pi -e "s/'RESELLER'/'CUSTOMER'/g; s/\"RESELLER\"/\"CUSTOMER\"/g"` over the listed main files, and fix the `AuthService` message to "must be SELLER or CUSTOMER". Apply the same string and helper rename to the test tree (`registerReseller` → `registerCustomer`, `"role":"RESELLER"` → `"role":"CUSTOMER"`). Leave `Viewer.RESELLER` and `MAX_OPEN_PER_RESELLER` alone.
- [ ] **Step 5: Run** `grep -rn "RESELLER" backend/src` → only `BookingService` internals and V1/V5 migrations remain. Then `./mvnw verify` → all pass.
- [ ] **Step 6: Verify the data migration on the local DB:** `docker compose up -d`, `./mvnw spring-boot:run`, then `docker compose exec postgres psql -U <user> -d <db> -c "select role, count(*) from app_user group by role"` (credentials in `docker-compose.yml`) → no `RESELLER` rows. Stop the app.
- [ ] **Step 7: Commit** `git add -A && git commit -m "Rename RESELLER role to CUSTOMER"`.

---

### Task 2: Seller-set quantity on every saree

**Files:**
- Create: `db/migration/V7__item_quantity.sql`
- Modify: `item/Item.java`, `item/ItemService.java` (`update`, ~line 127), `item/dto/ItemDto.java`, `catalog/dto/CatalogItemDto.java`, `catalog/dto/ItemDetailDto.java`, `catalog/CatalogService.java` (the SELECTs that build `CatalogItemDto` and `ItemDetailDto`)
- Test: `item/ItemQuantityIntegrationTest.java` (new), update `catalog/CatalogIntegrationTest.java` for new DTO fields

**Interfaces:**
- Produces: `Item.getQuantity(): int` / `setQuantity(int)`; `ItemDto` gains `int quantity` as its **last** component (after `openRequests`); `CatalogItemDto` gains `int quantity, boolean inStock` as last components; `ItemDetailDto` gains `int quantity, boolean inStock` after `status`. `inStock` means `status == AVAILABLE && quantity > 0`.
- PATCH `/api/v1/items/{id}` accepts `quantity` (integral JSON number 0–10000).

- [ ] **Step 1: Write failing tests** in `ItemQuantityIntegrationTest` (seller uploads one photo via the existing upload helper used in `CollectionIntegrationTest`): `newItemHasQuantityOne`; `sellerCanSetQuantity` (PATCH `{"quantity":5}` → `quantity == 5`); `badQuantitiesAreRejected` (`-1`, `10001`, `2.5`, `"3"`, `null` each → 400 `VALIDATION_ERROR` with a `quantity` field error, stored value still 5); `zeroQuantityIsNotInStock` (catalog item detail shows `inStock == false` for `quantity 0`, and `true` for 5 with status `AVAILABLE`); `soldOutStatusIsNotInStock` (qty 5 but status `SOLD_OUT` → `inStock == false`).
- [ ] **Step 2: Run** `./mvnw test -Dtest=ItemQuantityIntegrationTest` → FAIL.
- [ ] **Step 3: `V7__item_quantity.sql`:** `ALTER TABLE item ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0 AND quantity <= 10000)`; backfill `UPDATE item SET quantity = 0 WHERE status IN ('SOLD_OUT','ON_HOLD')`.
- [ ] **Step 4: Implement:** field + accessors on `Item` (default 1); in `ItemService.update` handle `quantity` like `priceOverride` (reject anything but an integral int in range with `errors.put("quantity", "must be a whole number from 0 to 10000")`); thread `quantity`/`inStock` through the three DTOs and the catalog SELECTs. Do not touch `status` logic; status and quantity are independent until slice 5.
- [ ] **Step 5: Run** `./mvnw test -Dtest=ItemQuantityIntegrationTest,CatalogIntegrationTest` → PASS; fix any record-constructor call sites the compiler flags in other tests.
- [ ] **Step 6: Run** `./mvnw verify` → PASS. **Commit** `Add seller-set quantity to items`.

---

### Task 3: Seller UPI ID and shipping fee

**Files:**
- Create: `db/migration/V8__seller_payment_settings.sql`
- Modify: `seller/SellerProfile.java`, `seller/dto/SellerProfileDto.java`, `seller/dto/SellerProfileRequest.java`, `seller/SellerService.java` (profile get/update)
- Test: `seller/SellerPaymentSettingsIntegrationTest.java` (new)

**Interfaces:**
- Produces: `SellerProfileDto(String shopName, String marketLocation, String contactPhone, String whatsappNumber, String upiId, int shippingFee)`; `SellerProfile.getUpiId(): String|null`, `getShippingFee(): int`. Cart (Task 7) reads both columns from `seller_profile` directly via SQL.
- `PUT /api/v1/seller/profile` request gains optional `upiId` (null or blank clears it; otherwise matches `^[A-Za-z0-9._-]{2,64}@[A-Za-z]{2,32}$`) and optional `shippingFee` (integer 0–10000, omitted = 0).

- [ ] **Step 1: Write failing tests:** `newSellerHasNoUpiAndZeroShipping`; `sellerSavesUpiAndShippingFee` (PUT then GET shows both); `omittingUpiKeepsItNull`; `blankUpiClearsIt`; `malformedUpiIsRejected` (`"abc"`, `"a@b"`, `"x y@okaxis"` → 400 with `upiId` field error); `negativeOrHugeShippingFeeIsRejected` (`-1`, `10001`); `customerCannotUpdateSellerProfile` (403).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: `V8__seller_payment_settings.sql`:** `ALTER TABLE seller_profile ADD COLUMN upi_id VARCHAR(100), ADD COLUMN shipping_fee INTEGER NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0 AND shipping_fee <= 10000)`.
- [ ] **Step 4: Implement** the entity fields, add `@Pattern`/`@Min`/`@Max` on the request record (blank is normalised to null in the service), return both in the DTO. Keep the existing `SellerProfile` constructor valid for `AuthService`.
- [ ] **Step 5: Run** the new test and then `./mvnw verify` → PASS. **Commit** `Seller UPI ID and flat shipping fee`.

---

### Task 4: Customer addresses

**Files:**
- Create: `db/migration/V9__address.sql`; `address/AddressController.java`, `address/AddressService.java`, `address/dto/AddressDto.java`, `address/dto/AddressRequest.java` (under `com/sareemarket/`)
- Test: `address/AddressIntegrationTest.java`, `address/AddressServiceTest.java`

**Interfaces:**
- Produces: `AddressDto(Long id, String name, String phone, String line1, String line2, String city, String state, String pin, boolean isDefault)`; `AddressRequest` with `@NotBlank name(≤100)`, `phone` (`^[6-9]\d{9}$`), `@NotBlank line1(≤200)`, optional `line2(≤200)`, `@NotBlank city(≤100)`, `@NotBlank state(≤100)`, `pin` (`^\d{6}$`), optional `Boolean makeDefault`.
- `AddressService`: `List<AddressDto> list(long customerId)`, `AddressDto create(long customerId, AddressRequest r)`, `AddressDto update(long customerId, long id, AddressRequest r)`, `void delete(long customerId, long id)`, and `AddressDto get(long customerId, long id)` (throws 404; slice 2 checkout uses it).
- Endpoints (`CUSTOMER` only): `GET /addresses`, `POST /addresses` (201), `PUT /addresses/{id}`, `DELETE /addresses/{id}` (204).
- Rules: max 10 addresses per customer (11th → 409 `ADDRESS_LIMIT`); the first address is default; `makeDefault: true` clears the old default in the same transaction; deleting the default promotes the most recently created remaining address; one default per customer enforced by a partial unique index on `(customer_id) WHERE is_default`.

- [ ] **Step 1: Write failing tests** (integration unless noted): `firstAddressBecomesDefault`; `makingAnotherDefaultMovesTheFlag` (exactly one default in `GET`); `deletingDefaultPromotesNewest`; `eleventhAddressIsRejected`; `invalidPinPhoneOrBlankNameIs400` (field errors named); `otherCustomersAddressIs404ForGetUpdateDelete` (customer B's address untouched afterwards); `sellerGets403AndAnonymousGets401`; unit `AddressServiceTest.deleteLastAddressLeavesNoDefault`.
- [ ] **Step 2: Run** `./mvnw test -Dtest='Address*'` → FAIL.
- [ ] **Step 3: `V9__address.sql`:** table `address(id identity PK, customer_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE, name, phone, line1, line2, city, state, pin VARCHAR(6), is_default BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT now())`, index on `customer_id`, partial unique index `uq_address_default ON address (customer_id) WHERE is_default`.
- [ ] **Step 4: Implement** controller/service/DTOs in the `booking` style (plain SQL; the limit check, default switch and delete-promote each run in one `@Transactional` method; clear the old default before setting the new one so the unique index never trips).
- [ ] **Step 5: Run** the address tests then `./mvnw verify` → PASS. **Commit** `Customer delivery addresses`.

---

### Task 5: Wishlist (and the shared card lookup)

**Files:**
- Create: `db/migration/V10__wishlist.sql`; `wishlist/WishlistController.java`, `wishlist/WishlistService.java`
- Modify: `catalog/CatalogService.java` (add `cardsFor`), `catalog/dto/ItemDetailDto.java` (add `boolean inWishlist`), item-detail SELECT in `CatalogService`
- Test: `wishlist/WishlistIntegrationTest.java`

**Interfaces:**
- Produces: `CatalogService.cardsFor(List<Long> itemIds): List<CatalogItemDto>` (same shape as catalog cards, returned **in the order of `itemIds`**, silently skipping missing ids; `distance` null). Task 7 reuses it. `ItemDetailDto` gains `boolean inWishlist` as its last component (false for non-customers).
- Endpoints (`CUSTOMER` only): `PUT /wishlist/{itemId}` (204, idempotent; 404 if the saree does not exist), `DELETE /wishlist/{itemId}` (204, idempotent), `GET /wishlist?page=` → `PageDto<CatalogItemDto>` newest-added first, page size 20 (use the `PageDto` shape the catalog already returns).
- Wishlist keeps sold-out sarees (the card shows `inStock=false`); no cap.

- [ ] **Step 1: Write failing tests:** `addingTwiceKeepsOneEntry`; `listIsNewestFirstAndPaged` (21 items → page 0 has 20, page 1 has 1); `removedAndMissingIdsAreHarmless`; `unknownItemIs404`; `soldOutItemStaysListedAsNotInStock`; `itemDetailShowsInWishlist` (true after add, false after remove); `listIsPerCustomer` (B's wishlist does not show A's items); `sellerGets403AndAnonymousGets401`; catalog `cardsForKeepsRequestedOrderAndSkipsMissing`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: `V10__wishlist.sql`:** `wishlist_entry(customer_id REFERENCES app_user ON DELETE CASCADE, item_id REFERENCES item ON DELETE CASCADE, added_at TIMESTAMPTZ DEFAULT now(), PRIMARY KEY (customer_id, item_id))` plus index `(customer_id, added_at DESC)`.
- [ ] **Step 4: Implement** `cardsFor` by reusing the SELECT/mapper that builds catalog cards (filter by `i.id = ANY(:ids)`, then reorder in Java); the service adds with `INSERT ... ON CONFLICT DO NOTHING`; fill `inWishlist` in the detail query for customers only.
- [ ] **Step 5: Run** wishlist and catalog tests, then `./mvnw verify` → PASS. **Commit** `Customer wishlist`.

---

### Task 6: Cart table, add/set/remove lines

**Files:**
- Create: `db/migration/V11__cart.sql`; `cart/CartController.java`, `cart/CartService.java`
- Modify: `catalog/dto/ItemDetailDto.java` (add `int inCart`), item-detail SELECT in `CatalogService`
- Test: `cart/CartLineIntegrationTest.java`, `cart/CartServiceTest.java`

**Interfaces:**
- Produces: `CartService.setLine(long customerId, long itemId, int quantity)` (upsert), `CartService.removeLine(long customerId, long itemId)`; `ItemDetailDto` gains `int inCart` as its last component (quantity of this saree in the viewer's cart, 0 if none or not a customer).
- Endpoints (`CUSTOMER` only): `PUT /cart/items/{itemId}` body `{"quantity": n}` → 204; `DELETE /cart/items/{itemId}` → 204 (idempotent).
- Rules for `PUT`: `quantity` integer 1–`item.quantity` else `409 NOT_ENOUGH_STOCK` ("Only N left"); the saree must be `status == AVAILABLE` and `quantity > 0` and its seller enabled, else `409 NOT_AVAILABLE`; saree missing → 404; max 50 lines per customer (`409 CART_FULL`); upsert is `INSERT ... ON CONFLICT (customer_id, item_id) DO UPDATE` so concurrent calls leave one row. The cart never reserves stock.

- [ ] **Step 1: Write failing tests:** `settingALineStoresIt` (via `inCart` on item detail); `settingAgainReplacesQuantity`; `quantityAboveStockIs409NotEnoughStock`; `zeroNegativeOrNonIntegerQuantityIs400`; `soldOutOrHeldOrZeroStockItemIs409NotAvailable`; `disabledSellersItemIs409NotAvailable`; `unknownItemIs404`; `fiftyFirstLineIs409CartFull`; `concurrentSetsLeaveOneRow` (two threads, same customer and item, assert one row in `cart_line`); `removeIsIdempotent`; `sellerGets403AndAnonymousGets401`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: `V11__cart.sql`:** `cart_line(customer_id REFERENCES app_user ON DELETE CASCADE, item_id REFERENCES item ON DELETE CASCADE, quantity INTEGER NOT NULL CHECK (quantity > 0), added_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now(), PRIMARY KEY (customer_id, item_id))`.
- [ ] **Step 4: Implement** controller and service (plain SQL, one transaction per call, availability check reads the item row and `app_user.enabled` of its seller); fill `inCart` in the detail query.
- [ ] **Step 5: Run** the cart tests then `./mvnw verify` → PASS. **Commit** `Cart lines: add, set and remove`.

---

### Task 7: View the cart grouped by seller

**Files:**
- Create: `cart/dto/CartDto.java`, `cart/dto/CartSellerDto.java`, `cart/dto/CartLineDto.java`
- Modify: `cart/CartController.java`, `cart/CartService.java`
- Test: `cart/CartViewIntegrationTest.java`

**Interfaces:**
- Consumes: Task 6's `cart_line`; Task 3's `seller_profile.upi_id` and `shipping_fee`.
- Produces: `CartLineDto(long itemId, String collectionTitle, String colour, int unitPrice, int quantity, int maxQuantity, boolean inStock, int lineTotal)`; `CartSellerDto(long sellerId, String shopName, boolean acceptsOrders, int shippingFee, List<CartLineDto> lines, int itemsTotal, int grandTotal)`; `CartDto(List<CartSellerDto> sellers, int lineCount)`. Slice 2 checkout consumes the same grouping rule.
- `GET /api/v1/cart` (`CUSTOMER`): lines grouped by seller, sellers ordered by earliest `added_at` of their lines, lines newest first. `unitPrice` is the saree's own price if set else the collection's price (same rule as `ItemDto.effectivePrice`). `maxQuantity` = the saree's current quantity. Out-of-stock lines (`inStock=false`: not `AVAILABLE`, quantity 0, or seller disabled) stay listed but are excluded from `itemsTotal`; in-stock lines count `min(quantity, maxQuantity)` units. `acceptsOrders` = the seller has a UPI ID and is enabled. `grandTotal = itemsTotal + shippingFee`, or `0` when the group has no in-stock lines. Read-only: `GET` never changes the cart.

- [ ] **Step 1: Write failing tests:** `emptyCartIsEmptyList`; `linesGroupBySellerWithShippingAndTotals` (two sellers; seller 1 fee 50, two lines 2×800 and 1×1200 → `itemsTotal 2800`, `grandTotal 2850`); `priceOverrideBeatsCollectionPrice`; `acceptsOrdersFalseWithoutUpi`; `soldOutLineIsListedButNotCounted`; `deletedItemDisappears`; `disabledSellersLinesAreNotInStock`; `quantityLargerThanStockIsClampedInTotals`; `viewDoesNotChangeStock`; `otherCustomersCartIsNeverShown`; `sellerGets403AndAnonymousGets401`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** `CartService.view(long customerId): CartDto` with one query joining `cart_line`, `item`, `collection`, `seller_profile`, `app_user`, grouped in Java; compute totals in Java with integer arithmetic.
- [ ] **Step 4: Run** `./mvnw test -Dtest='Cart*'` then `./mvnw verify` → PASS. **Commit** `Cart view grouped by seller`.

---

### Task 8: Docs and final check

**Files:**
- Modify: `docs/superpowers/specs/2026-10-28-customer-orders-redesign-design.md` (section 4 migration numbers and slice 1 note), memory file `booking-requests-feature.md` is not touched.

- [ ] **Step 1:** In the spec, change section 4's headings to V6 customer role, V7 item quantity, V8 seller payment settings, V9 address, V10 wishlist, V11 cart, V12 orders (holds and orders), V13 drop bookings; note in section 9 that slice 1 is done on branch `customer-orders` and unpushed.
- [ ] **Step 2:** Run `./mvnw clean verify` from `backend/` → all tests pass; record the count.
- [ ] **Step 3: Smoke the API by hand:** start the backend, register a customer, add an address, wishlist one saree, put it in the cart, `GET /cart` → expected JSON groups by seller. (Local test sellers: `9876500001` / `password1`.)
- [ ] **Step 4: Commit** `Slice 1 docs`; stop for review.

---

## Hand-offs and later reminders

- **Deploy in slice 5, not before.** Tokens issued before the rename carry `role=RESELLER` and would get 403 for up to 24 h after deploy (the app only logs out on 401). Rotate the `JWT_SECRET` GitHub secret on the deploy so every old token is a 401 and everyone logs in again cleanly.
- Slice 3 (Android customer) must send `"role":"CUSTOMER"` and accept both role strings when decoding stored sessions.
