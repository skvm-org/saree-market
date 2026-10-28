# Slice 2 — Payment Holds and Orders (backend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A customer checks out their cart into a 2-hour payment hold per seller, submits payment proof (a UTR, optionally a screenshot) to turn the hold into an order, and the seller confirms payment, packs, ships and delivers it, with stock reserved and returned correctly throughout.

**Architecture:** New `order` package in the same Spring Boot monolith, plain SQL with `NamedParameterJdbcTemplate` like `booking` and `cart`. `HoldService` owns checkout and holds, `OrderService` owns everything from proof onward, one `OrderExpiryJob` releases lapsed holds and unanswered rejected-payment orders. Stock is reserved by decrementing `item.quantity` at checkout and returned by incrementing it on cancel or lapse. Unread flags plus a summary endpoint replace the notification table (as bookings did).

**Tech Stack:** Java 21, Spring Boot 4.1, Flyway, PostgreSQL JSONB (address snapshot), `ImageProcessor` and `ImageStorage` for proof images, JUnit 5 + Testcontainers (`IntegrationTestBase`).

**Spec:** `docs/superpowers/specs/2026-10-28-customer-orders-redesign-design.md` (sections 4, 5, 6, 7). Rulings below deviate from it in four places; Task 6 updates the spec.

## Rulings carried into this plan

1. **Rejected proof keeps the order.** Instead of turning an order back into a hold, add order status `PAYMENT_REJECTED` with a 2-hour `payment_deadline`. First rejection → `PAYMENT_REJECTED` (customer may resubmit once the new UTR, → `PAYMENT_SUBMITTED`); second rejection, or the deadline passing → `CANCELLED` with stock returned. Same customer experience, no lines moved between tables.
2. **No notification table change.** Orders carry `customer_unread` / `seller_unread` flags and `GET /orders/summary` feeds the existing polling workers, exactly as bookings do. No new events.
3. **Checkout reserves what is available.** Short lines are reserved at the available quantity and reported as adjustments; lines that cannot be ordered are left in the cart and reported. The cart is emptied of a seller's lines only when that seller's hold becomes an order.
4. **Checkout can target one seller** (`sellerId` optional) because a customer may have at most 3 active holds and one hold per seller.
5. **Expiry job runs every 5 minutes**, not hourly, so a 2-hour window lapses on time.

## Global Constraints

- Branch `customer-orders`; **do not push to `main`** (it deploys and breaks the installed APK). Commit per task, no Co-Authored-By lines.
- Next migration is `V12__orders.sql`; never edit applied migrations.
- Money is whole-rupee `INTEGER`. Compute totals in `long` and refuse with `409 TOTAL_TOO_LARGE` when an order total exceeds 10,000,000.
- Customer endpoints `@PreAuthorize("hasRole('CUSTOMER')")`, seller endpoints `hasRole('SELLER')`. Another user's hold, order or proof is `404 NOT_FOUND`, never `403`.
- Illegal state transitions are `409` with code `BAD_STATE`. Errors use `ApiException(HttpStatus, code, message[, fields])`.
- UTR: exactly 12 digits, unique among non-cancelled orders. Proof image optional, must pass `ImageProcessor.process(MultipartFile)` (non-images are 400); stored with `ImageStorage.save("proofs/<orderId>-<uuid>.jpg", full bytes)`; the old file is deleted when replaced.
- Hold window and rejected-payment window: 2 hours. Max 3 active holds per customer, 1 per (customer, seller). Order and hold status changes never publish `ItemsRestocked`.
- `./mvnw verify` from `backend/` passes at the end of every task (OrbStack must be running).
- Use `perl -pi -e` for bulk edits, not macOS `sed -i`.

## Review Focus

1. Double-tapped or concurrent checkout (same customer, two requests) → at most one hold per seller, stock decremented once (Task 1).
2. Two customers racing for the last piece → exactly one hold, other gets the item reported as unavailable, quantity never negative (Task 1).
3. Stock conservation: after cancel, lapse, second rejection, and seller cancel the item quantity equals its original value; lines whose saree was deleted do not error (Tasks 2, 4, 5).
4. Cross-user access: another customer's hold/order/proof, a seller reading another seller's order or proof, → 404 (Tasks 1, 3, 4, 5).
5. Wrong-state actions (confirm a cancelled order, ship before pack, pay after the hold lapsed, resubmit a confirmed order) → 409 and no change; bad UTRs and non-image proofs → 400 with no stock or status change (Tasks 3, 5).

---

### Task 1: Checkout creates payment holds

**Files:**
- Create: `db/migration/V12__orders.sql`; `order/HoldService.java`, `order/HoldController.java`, `order/dto/CheckoutRequest.java`, `order/dto/CheckoutResultDto.java`, `order/dto/HoldDto.java`, `order/dto/OrderLineDto.java`, `order/dto/AddressSnapshotDto.java`, `order/dto/LineAdjustmentDto.java`, `order/dto/SkippedSellerDto.java`
- Test: `order/CheckoutIntegrationTest.java`

**Interfaces:**
- Consumes: `AddressService.get(long customerId, long id): AddressDto` (404 if not the customer's); cart tables from slice 1; `seller_profile.upi_id`/`shipping_fee`.
- Produces:
  - `V12`: tables `payment_hold(id, customer_id, seller_id, items_total, shipping_fee, grand_total, address JSONB, customer_note VARCHAR(300), created_at, expires_at)` with `UNIQUE (customer_id, seller_id)` and index on `expires_at`; `hold_line(id, hold_id FK cascade, item_id FK item ON DELETE SET NULL, title, colour, unit_price, quantity CHECK > 0)`; `customer_order(id, customer_id, seller_id, status VARCHAR(20) CHECK IN ('PAYMENT_SUBMITTED','PAYMENT_REJECTED','CONFIRMED','PACKED','SHIPPED','DELIVERED','CANCELLED'), items_total, shipping_fee, grand_total, address JSONB, customer_note, payment_reference VARCHAR(12), payment_proof_key VARCHAR(300), rejection_reason VARCHAR(200), rejection_count INT DEFAULT 0, payment_deadline TIMESTAMPTZ, cancel_reason VARCHAR(200), cancelled_by VARCHAR(10), courier VARCHAR(60), tracking_number VARCHAR(60), customer_unread BOOLEAN DEFAULT FALSE, seller_unread BOOLEAN DEFAULT FALSE, created_at, updated_at)` with partial unique index `uq_order_utr ON customer_order (payment_reference) WHERE payment_reference IS NOT NULL AND status <> 'CANCELLED'`, index `(seller_id, status, created_at)`, `(customer_id, created_at)`, and `(payment_deadline) WHERE status = 'PAYMENT_REJECTED'`; `order_line` (same columns as `hold_line` with `order_id` FK cascade); `order_status_history(id, order_id FK cascade, status, at DEFAULT now())`.
  - `OrderLineDto(Long itemId, String title, String colour, int unitPrice, int quantity)`; `AddressSnapshotDto(String name, String phone, String line1, String line2, String city, String state, String pin)`.
  - `HoldDto(long id, long sellerId, String shopName, String sellerUpiId, List<OrderLineDto> lines, int itemsTotal, int shippingFee, int grandTotal, AddressSnapshotDto address, String note, Instant expiresAt)`.
  - `CheckoutRequest(@NotNull Long addressId, @Size(max=300) String note, Long sellerId)`; `LineAdjustmentDto(long itemId, String title, int requested, int reserved)`; `SkippedSellerDto(long sellerId, String shopName, String reason)` with reasons `NOT_ACCEPTING_ORDERS`, `NOTHING_IN_STOCK`, `HOLD_EXISTS`; `CheckoutResultDto(List<HoldDto> holds, List<LineAdjustmentDto> adjustments, List<SkippedSellerDto> skipped)`.
  - `HoldService.checkout(long customerId, CheckoutRequest r): CheckoutResultDto`, `list(long customerId): List<HoldDto>` (expires_at in the future only, soonest first). Cancelling a hold arrives in Task 2.
  - `POST /api/v1/cart/checkout` → 200 `CheckoutResultDto`; `GET /api/v1/holds` → `List<HoldDto>`.
- Rules: lock the customer's `app_user` row, then the `item` rows of all lines in ascending id order (`FOR UPDATE`). Per seller group in the cart (or only `r.sellerId()`; unknown seller in cart → 404): skip with `NOT_ACCEPTING_ORDERS` (no UPI or disabled), `HOLD_EXISTS` (a hold already exists), or `NOTHING_IN_STOCK` (no line can be reserved). Otherwise reserve `min(wanted, stock)` of each AVAILABLE in-stock line (decrement `item.quantity`, record an adjustment when `reserved < wanted`; lines with nothing reserved stay in the cart and are listed as adjustments with `reserved = 0`), snapshot title, colour, `unit_price` (override else collection price), shipping fee and the address JSON, `expires_at = now() + 2 hours`. If creating the holds would push the customer past 3 active holds → `409 HOLD_LIMIT` before changing anything. Totals use `long` → `409 TOTAL_TOO_LARGE`. Empty or fully-skipped checkout returns 200 with empty `holds`. The cart is not modified.

- [ ] **Step 1: Write failing tests** in `CheckoutIntegrationTest` (helpers: shop with UPI+fee, saree with quantity, customer with address, as in `CartViewIntegrationTest`): `checkoutCreatesOneHoldPerSellerWithTotalsAndAddressSnapshot` (two sellers, fee 50, lines 2×800 + 1×1200 → `itemsTotal 2800`, `grandTotal 2850`, `expiresAt` about 2 h ahead, `address.pin` equals the saved PIN even after the address is edited), `checkoutReservesStock` (`item.quantity` drops by the reserved amount), `shortLineIsReservedAtAvailableAndReportedAsAdjustment` (wanted 4, stock 2 → reserved 2, `adjustments[0].requested == 4`), `outOfStockLineStaysInCartAndIsReported`, `cartIsNotModifiedByCheckout`, `sellerWithoutUpiIsSkipped`, `sellerFilterCheckoutsOnlyThatSeller`, `secondCheckoutForSameSellerIsSkippedAsHoldExists`, `fourthHoldIsRejectedWithHoldLimit` (nothing reserved), `totalAboveTenMillionIsRejected`, `otherCustomersAddressIs404`, `emptyCartReturnsEmptyResult`, `holdsListShowsOnlyMyActiveHolds`, `concurrentCheckoutsOfSameCartMakeOneHoldAndReserveOnce` (8 threads), `twoCustomersRaceForTheLastPiece` (stock 1, two carts → exactly one hold with the piece, the other reports it unavailable, `item.quantity == 0` never negative), `sellerGets403AndAnonymousGets401`.
- [ ] **Step 2: Run** `./mvnw test -Dtest=CheckoutIntegrationTest` → FAIL (404 / missing table).
- [ ] **Step 3:** Write `V12__orders.sql` and the DTOs, `HoldService.checkout/list`, `HoldController` (`POST /api/v1/cart/checkout`, `GET /api/v1/holds`). Build seller groups with the same SQL shape as `CartService.view`, but lock rows first. Store the address with Jackson (`tools.jackson.databind.ObjectMapper`) into a `?::jsonb` parameter and read it back the same way.
- [ ] **Step 4: Run** the test class → PASS, then `./mvnw verify` → PASS.
- [ ] **Step 5: Commit** `Checkout creates payment holds`.

---

### Task 2: Cancel a hold, and holds lapse after 2 hours

**Files:**
- Modify: `order/HoldService.java`, `order/HoldController.java`
- Create: `order/OrderExpiryJob.java`
- Test: `order/HoldExpiryIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 tables and `HoldService`.
- Produces: `HoldService.cancel(long customerId, long holdId): void` (`DELETE /api/v1/holds/{id}` → 204; another customer's or missing hold → 404); `HoldService.expireOverdue(): int` (number released); package-private `StockReturn` helper `static void returnStock(NamedParameterJdbcTemplate jdbc, String lineTable, String ownerColumn, long ownerId)` in `order/StockReturn.java` that adds each line's quantity back to `item.quantity` for lines whose `item_id` is not null (Task 4 and 5 reuse it for orders); `OrderExpiryJob` (`@Scheduled(initialDelay = 60_000, fixedDelay = 300_000)`, logs and swallows `RuntimeException` like `BookingExpiryJob`) calling `HoldService.expireOverdue()` now and `OrderService.expireRejected()` once Task 5 exists.
- Cancelling or lapsing deletes the hold (lines cascade) and returns the reserved quantity; the cart is untouched so it is "unchanged" for the customer.

- [ ] **Step 1: Write failing tests:** `cancelReturnsStockAndDeletesHold`; `cancelTwiceIs404`; `otherCustomersHoldCannotBeCancelled` (404, still reserved); `expireOverdueReleasesOnlyLapsedHolds` (set `expires_at` in the past for one of two holds; stock of that hold returns, the other stays reserved; method returns 1); `lapsedHoldIsNotListed`; `deletedSareeLineDoesNotBreakRelease` (delete the item row, then expire → no error); `stockIsConservedAcrossCheckoutAndCancel` (quantity equals the original after checkout + cancel).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** `cancel`, `expireOverdue` (one transaction per hold so one bad row cannot block the rest), `StockReturn.returnStock`, the controller route and `OrderExpiryJob`.
- [ ] **Step 4: Run** the class then `./mvnw verify` → PASS. **Commit** `Cancel and expire payment holds`.

---

### Task 3: Submit payment proof, hold becomes an order

**Files:**
- Create: `order/OrderService.java`, `order/OrderController.java`, `order/OrderStatus.java`, `order/dto/OrderDto.java`
- Modify: `order/HoldController.java` (route only)
- Test: `order/PaymentProofIntegrationTest.java`

**Interfaces:**
- Consumes: `ImageProcessor.process(MultipartFile): ProcessedImage` (use `.full()` bytes), `ImageStorage`, Task 1 tables.
- Produces:
  - `OrderStatus` enum `PAYMENT_SUBMITTED, PAYMENT_REJECTED, CONFIRMED, PACKED, SHIPPED, DELIVERED, CANCELLED`.
  - `OrderDto(long id, OrderStatus status, long sellerId, String shopName, String sellerUpiId, OrderPartyDto customer, List<OrderLineDto> lines, int itemsTotal, int shippingFee, int grandTotal, AddressSnapshotDto address, String note, String paymentReference, boolean hasProof, String rejectionReason, Instant paymentDeadline, String cancelReason, String cancelledBy, String courier, String trackingNumber, boolean unread, Instant createdAt, Instant updatedAt, List<StatusEntryDto> history)` with nested `OrderPartyDto(String name, String phone)` (non-null only in the seller's view) and `StatusEntryDto(OrderStatus status, Instant at)`. `history` is empty in lists and filled in `GET /orders/{id}`.
  - `OrderService.submitPayment(long customerId, long holdId, String utr, MultipartFile proof): OrderDto`.
  - `POST /api/v1/holds/{id}/payment` multipart (`utr` text, `proof` optional file) → 201 `OrderDto`; `GET /api/v1/orders/{id}/proof` → `image/jpeg` for the order's customer or seller (404 for anyone else or when no proof).
- Rules: `utr` must match `\d{12}` else `400 VALIDATION_ERROR` with `fields.utr`; a non-image proof is 400 (`fields.proof`) before anything changes; hold must belong to the customer (404), have `expires_at > now()` (else `409 HOLD_EXPIRED`, nothing changes; the job releases the stock); a UTR already on a non-cancelled order → `409 UTR_IN_USE`. In one transaction: insert `customer_order` (status `PAYMENT_SUBMITTED`, `seller_unread = TRUE`) with the hold's snapshot, copy lines, write a history row, delete the hold, and delete this customer's cart lines for exactly the saree ids in the order (the cart lines for the seller's other, un-reserved items stay). Stock stays reserved. Store the proof after validation; if the transaction later fails, delete the stored file.

- [ ] **Step 1: Write failing tests:** `proofTurnsHoldIntoPaymentSubmittedOrder` (order has the lines, totals, address, `paymentReference`, `hasProof`, history `[PAYMENT_SUBMITTED]`; hold is gone from `GET /holds`; stock unchanged); `cartLinesOfTheOrderAreRemoved`; `utrOnlyWithoutImageIsAccepted`; `badUtrIs400` (`"123"`, `"12345678901a"`, 13 digits, blank, missing) with the hold intact; `nonImageProofIs400AndHoldIntact`; `reusedUtrIs409` (second customer, other hold); `cancelledOrdersUtrCanBeReused` (set an order `CANCELLED` via SQL, then the same UTR succeeds); `payingAfterHoldLapsedIs409HoldExpired` (stock stays reserved until the job); `otherCustomersHoldIs404`; `proofIsServedToCustomerAndSellerOnly` (other customer and other seller → 404, anonymous 401); `sellerGets403` on the payment route.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** the DTO/enum/service/controller (multipart with `@RequestParam("utr")`, `@RequestParam(value="proof", required=false) MultipartFile`). Order DTO building is shared with Tasks 4 and 5 (`OrderService.one(long id, Viewer viewer, long userId)` internal `Viewer {CUSTOMER, SELLER}` mapping like `BookingService`).
- [ ] **Step 4: Run** the class then `./mvnw verify` → PASS. **Commit** `Payment proof turns a hold into an order`.

---

### Task 4: Customer reads, cancels and gets a summary of orders

**Files:**
- Modify: `order/OrderService.java`, `order/OrderController.java`
- Create: `order/dto/OrderSummaryDto.java`
- Test: `order/CustomerOrdersIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3's `OrderService`, `StockReturn` from Task 2.
- Produces: `OrderService.mine(long customerId, OrderStatus state, int page): PageDto<OrderDto>` (newest first, page size 20, `state` optional filter); `OrderService.get(long userId, boolean seller, long orderId): OrderDto` (with history; 404 unless the viewer is the order's customer or seller); `OrderService.cancelByCustomer(long customerId, long orderId): OrderDto`; `OrderService.markSeenByCustomer(long customerId): void`; `OrderSummaryDto(int unread, int activeHolds, int toConfirm, int toPack, int toShip, int shipped)` — customers get `unread` and `activeHolds` (others 0); sellers get the counts (Task 5).
- Endpoints: `GET /orders/mine?state=&page=`, `GET /orders/{id}`, `POST /orders/{id}/cancel` (customer: allowed from `PAYMENT_SUBMITTED` and `PAYMENT_REJECTED` only, else `409 BAD_STATE`; stock returned; history row; `seller_unread = TRUE`; `cancelled_by = 'CUSTOMER'`), `POST /orders/seen` (204, clears `customer_unread` for all the customer's orders), `GET /orders/summary`.

- [ ] **Step 1: Write failing tests:** `mineListsMyOrdersNewestFirstAndFilters`; `getShowsHistoryAndHidesCustomerPartyFromTheCustomerView` (`customer` null for the customer; `shopName` and `sellerUpiId` present); `otherCustomersOrderIs404`; `customerCancelReturnsStockAndRecordsHistory` (quantity restored, `status CANCELLED`, `cancelledBy CUSTOMER`); `customerCannotCancelConfirmedOrShipped` (409 `BAD_STATE`, stock unchanged); `cancelTwiceIs409`; `seenClearsUnread` (set `customer_unread` via SQL); `summaryCountsUnreadAndActiveHolds`; `stockIsConservedAfterCheckoutPayAndCancel`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** the methods and routes; `cancelByCustomer` locks the order row, re-checks status, then calls `StockReturn.returnStock(jdbc, "order_line", "order_id", id)`.
- [ ] **Step 4: Run** the class then `./mvnw verify` → PASS. **Commit** `Customer order list, detail, cancel and summary`.

---

### Task 5: Seller processes orders

**Files:**
- Modify: `order/OrderService.java`, `order/OrderController.java`, `order/OrderExpiryJob.java`
- Create: `order/dto/RejectRequest.java`, `order/dto/ShipRequest.java`, `order/dto/CancelRequest.java`
- Test: `order/SellerOrdersIntegrationTest.java`

**Interfaces:**
- Consumes: Tasks 3 and 4.
- Produces: `RejectRequest(@NotBlank @Size(max=200) String reason)`, `ShipRequest(@Size(max=60) String courier, @Size(max=60) String trackingNumber)`, `CancelRequest(@Size(max=200) String reason)` (reason required for sellers, ignored for customers).
- `OrderService`: `received(long sellerId, OrderStatus state, int page): PageDto<OrderDto>` (seller view includes `customer` name and phone; default ordering: `PAYMENT_SUBMITTED` first, then newest), `confirmPayment`, `rejectPayment(sellerId, id, reason)`, `pack`, `ship(sellerId, id, courier, trackingNumber)`, `deliver`, `cancelBySeller(sellerId, id, reason)`, `resubmitPayment(long customerId, long orderId, String utr, MultipartFile proof): OrderDto`, `expireRejected(): int`, and the seller branch of `summary`.
- Endpoints: `GET /orders/received?state=&page=` (seller), `POST /orders/{id}/confirm-payment | reject-payment | pack | ship | deliver` (seller), `POST /orders/{id}/cancel` (now role-aware: a seller needs `reason` and may cancel from `PAYMENT_SUBMITTED`, `PAYMENT_REJECTED`, `CONFIRMED`, `PACKED`), `POST /orders/{id}/payment` (customer resubmit, same `utr`/`proof` rules as Task 3).
- Transitions (each locks the order row, re-checks status, writes a history row, sets the other party's unread flag, and clears the acting seller's `seller_unread`; wrong state → `409 BAD_STATE`; another seller's order → 404): confirm `PAYMENT_SUBMITTED→CONFIRMED`; reject `PAYMENT_SUBMITTED→PAYMENT_REJECTED` the first time (`rejection_count` 0→1, `rejection_reason`, `payment_deadline = now()+2h`, keep stock) and `→CANCELLED` the second time (`cancelled_by='SELLER'`, stock returned); pack `CONFIRMED→PACKED`; ship `PACKED→SHIPPED` (courier, tracking saved); deliver `SHIPPED→DELIVERED`; resubmit `PAYMENT_REJECTED→PAYMENT_SUBMITTED` (needs `payment_deadline > now()`, else `409 HOLD_EXPIRED`; replaces UTR and proof, deletes the old proof file, `seller_unread = TRUE`); `expireRejected` cancels `PAYMENT_REJECTED` orders past their deadline with `cancelled_by='SYSTEM'`, returns stock, and `OrderExpiryJob` calls it after `HoldService.expireOverdue()`.
- Seller summary: `toConfirm` = `PAYMENT_SUBMITTED`, `toPack` = `CONFIRMED`, `toShip` = `PACKED`, `shipped` = `SHIPPED`, `unread` = count with `seller_unread`; a request `POST /orders/seen` from a seller clears `seller_unread` for all their orders.

- [ ] **Step 1: Write failing tests:** `happyPathConfirmPackShipDeliver` (history `[PAYMENT_SUBMITTED, CONFIRMED, PACKED, SHIPPED, DELIVERED]`, courier and tracking visible to the customer, customer unread set at each step); `sellerSeesCustomerNameAndPhoneOnlyOnOwnOrders`; `receivedFiltersByStateAndOrdersPaymentSubmittedFirst`; `firstRejectionKeepsStockAndAllowsResubmit` (status `PAYMENT_REJECTED`, `rejectionReason` shown to the customer, then resubmit with a new UTR → `PAYMENT_SUBMITTED`, old UTR free); `secondRejectionCancelsAndReturnsStock`; `rejectedPaymentLapsesAfterDeadline` (past `payment_deadline`, call `expireRejected()` → `CANCELLED`, stock returned, `cancelledBy SYSTEM`); `resubmitAfterDeadlineIs409`; `rejectNeedsAReason` (400); `cannotSkipStates` (ship from `CONFIRMED`, pack from `PAYMENT_SUBMITTED`, confirm twice, deliver before ship → 409 `BAD_STATE`, status unchanged); `sellerCancelNeedsReasonReturnsStockAndIsBlockedAfterShipped`; `anotherSellersOrderIs404ForEveryAction`; `customerCannotUseSellerActions` (403); `sellerSummaryCounts`; `stockIsConservedAcrossAllCancelPaths` (cancel by customer, seller, second rejection, lapse each restore the original quantity).
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** Reuse one private `transition(long orderId, EnumSet<OrderStatus> from, OrderStatus to, ...)` helper for the lock/check/history/unread steps.
- [ ] **Step 4: Run** the class then `./mvnw verify` → PASS. **Commit** `Seller order actions and rejected-payment lapse`.

---

### Task 6: Spec update and end-to-end check

**Files:**
- Modify: `docs/superpowers/specs/2026-10-28-customer-orders-redesign-design.md`

- [ ] **Step 1:** Update the spec: section 4 `V12 orders` (add `PAYMENT_REJECTED`, `payment_deadline`, `rejection_count`, remove `payment_hold.proof_attempts`/`rejection_reason`, add unique `(customer_id, seller_id)`), section 5 (rulings 1, 3, 4), section 6 (`POST /cart/checkout` body, `GET /holds`, `POST /orders/{id}/payment` for resubmit, cancel is role-aware), section 7 (unread flags and summary instead of notification types), expiry job every 5 minutes, and mark slice 2 built on branch `customer-orders`.
- [ ] **Step 2:** `./mvnw clean verify` from `backend/` → all pass; record the count.
- [ ] **Step 3: Smoke by hand** against the local backend: with the customer from slice 1's smoke test (`9555500011` / `password1`) put a saree in the cart, `POST /cart/checkout`, `POST /holds/{id}/payment` with a 12-digit `utr`, then log in as the saree's seller and run `confirm-payment`, `pack`, `ship`, `deliver`, checking `GET /orders/mine` after each. (The seller must have a UPI ID: `PUT /seller/profile` first.)
- [ ] **Step 4: Commit** `Slice 2 docs`; stop for review.

---

## Hand-offs

- Slice 3 (Android customer) consumes: `POST /cart/checkout`, `GET /holds`, `DELETE /holds/{id}`, `POST /holds/{id}/payment`, `GET /orders/mine`, `GET /orders/{id}`, `POST /orders/{id}/payment`, `POST /orders/{id}/cancel`, `GET /orders/summary`, `POST /orders/seen`, `GET /orders/{id}/proof`.
- Slice 4 (Android seller) consumes: `GET /orders/received`, the five seller action routes, `GET /orders/summary`, `PUT /seller/profile` (UPI, fee).
- Deploy only in slice 5 (see slice 1 plan's hand-off about rotating `JWT_SECRET`).
