# Customer orders redesign — design spec

Status: built in slices 1–5 on branch `customer-orders` (bookings removed by migration V13); release pending. Originally drafted 2026-10-28. Supersedes the "Add-on: booking requests" section of `docs/MVP_BRIEF.md` and reverses its "no payments, no orders" scope line.

## 1. Goal

Today a reseller finds a saree, then calls the seller or asks for a hold. The seller sees a request, not a sale. The redesign turns the app into a small marketplace: anyone can sign up as a **Customer** (resellers included), browse sellers' collections, wishlist, add to a cart, check out, pay the seller by UPI, and track the order. The **Seller** keeps uploading daily collections, and now receives orders to confirm, pack and ship.

Success: a customer can pay for and place orders with two sellers; each seller sees only their order, verifies payment, ships it and marks it delivered; stock never goes below zero; a payment hold that lapses releases its stock.

## 2. Decisions (from the user)

| Topic | Decision |
|---|---|
| Roles | `SELLER`, `CUSTOMER` (+ seeded `ADMIN`, backend only). `RESELLER` is renamed to `CUSTOMER`. |
| Payment | Seller's UPI ID/QR. Customer pays outside the app and submits the 12-digit UTR (required, unique across orders) and optionally a screenshot. Seller checks their own bank/UPI app and confirms. No gateway in v1; a Razorpay gateway is planned as slice 6. |
| Stock | Seller sets a **quantity** per saree. |
| Reservation | No order exists until the customer has paid. Checkout starts a **payment hold** that reserves the quantity for **2 hours**; the order is created when the customer submits payment proof. A lapsed hold returns the stock. The cart itself does not reserve. |
| Delivery | Customer saves addresses. Each seller sets one flat shipping fee. Seller marks Packed → Shipped (optional courier and tracking number) → Delivered. No courier integration. |
| Reviews | None in this version. Customers view details only. |
| Migration | Replace bookings. Reseller accounts convert in place to Customer. |
| Approach | Evolve the current app in place (Spring Boot monolith + native Android). |

Kept as is: follow sellers, new-collection notifications, search and filters, image search, seller uploads, call and WhatsApp (secondary actions on the product page).

## 3. Assumptions (flag if wrong)

- A cart can hold sarees from several sellers; checkout creates **one order per seller**, each paid separately to that seller's UPI.
- Prices are whole rupees (`INTEGER`), as today. Order totals = sum of line prices + that seller's shipping fee. No tax lines, discounts or coupons.
- A seller cannot buy their own sarees and cannot hold a Customer role on the same account. A seller who wants to buy registers a second account.
- Orders are for India, one currency (₹), addresses are free-text lines plus PIN.
- Customers see the seller's phone on the product page (as today for resellers); sellers see the customer's name and phone only on orders addressed to them.

## 4. Data model (Flyway `V6`..)

Never edit applied migrations. New migrations only:

- **V6 `customer_role`**: drop the `app_user.role` CHECK (look up its name in `pg_constraint`, as V5 did for item status) and re-add `('SELLER','CUSTOMER','ADMIN')`; `UPDATE app_user SET role='CUSTOMER' WHERE role='RESELLER'`.
- **V7 `item_quantity`**: add `item.quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0 AND quantity <= 10000)`; backfill `quantity = 0` where `status` is `SOLD_OUT` or `ON_HOLD`, else 1. Keep `item.status` column for now (see slice 5).
- **V8 `seller_payment_settings`**: `seller_profile` gains `upi_id VARCHAR(100)`, `shipping_fee INTEGER NOT NULL DEFAULT 0 CHECK (>= 0)`. A seller without a `upi_id` cannot be ordered from (the product shows "Not accepting orders yet").
- **V9 `address`, V10 `wishlist`, V11 `cart`** (one migration each):
  - `cart_line(customer_id, item_id, quantity CHECK > 0, added_at, PRIMARY KEY (customer_id, item_id))`
  - `wishlist_entry(customer_id, item_id, added_at, PRIMARY KEY (customer_id, item_id))`
  - `address(id, customer_id, name, phone, line1, line2, city, state, pin, is_default, created_at)`
- **V12 `orders`**:
  - `payment_hold(id, customer_id, seller_id, items_total, shipping_fee, grand_total, address JSONB, customer_note, created_at, expires_at)` (unique per customer and seller) and `hold_line(hold_id FK cascade, item_id FK set null, title, colour, unit_price, quantity)`. A hold is the reservation; the quantities are already subtracted from the items. Not visible to sellers. Deleted when it becomes an order or lapses.
  - `customer_order(id, customer_id, seller_id, status, items_total, shipping_fee, grand_total, address_snapshot JSONB, customer_note, payment_reference, payment_proof_key, rejection_reason, rejection_count, payment_deadline, cancel_reason, cancelled_by, courier, tracking_number, customer_unread, seller_unread, created_at, updated_at)`
  - `order_line(id, order_id FK cascade, item_id (nullable ON DELETE SET NULL), title_snapshot, unit_price, quantity)`
  - `order_status_history(order_id, status, at)` for the timeline shown to both sides.
  - Indexes: `(seller_id, status, created_at)`, `(customer_id, created_at)`, `payment_hold (expires_at)` for the expiry job, `payment_hold (customer_id)` for the 3-hold cap.
- **V13 `drop_bookings`** (last slice): migrate then drop `booking_request`. Open `PENDING`/`ACCEPTED` requests are closed with a notification text ("Booking was replaced by orders, add the saree to your cart") before the table goes.

Lines snapshot title and price so later edits or deletes of a saree do not change a placed order.

## 5. Payment hold and order lifecycle

No order exists before the customer has paid. Checkout creates a **payment hold** (not visible to the seller) per seller and reserves the stock; submitting the UTR turns the hold into an order.

```
cart --checkout--> HOLD (2 h, stock reserved) --submit UTR--> order PAYMENT_SUBMITTED --seller confirms--> CONFIRMED --> PACKED --> SHIPPED --> DELIVERED
                     |                                              |
                     |- customer cancels or 2 h lapse: stock returns |- seller rejects (first time): PAYMENT_REJECTED, 2 h to resubmit a correct UTR
                                                                     |- seller rejects again, or 2 h lapse, or either side cancels before SHIPPED: CANCELLED, stock returns
```

Order statuses: `PAYMENT_SUBMITTED`, `PAYMENT_REJECTED`, `CONFIRMED`, `PACKED`, `SHIPPED`, `DELIVERED`, `CANCELLED`. A lapsed hold simply disappears (the cart is unchanged).

| Action | Who | From | To | Stock |
|---|---|---|---|---|
| Checkout (address, note, optional sellerId) | customer | cart lines | hold per seller, `expires_at = now+2h` | reserved (min of wanted and available) |
| Cancel hold / lapse (job every 5 min, also released at the customer's next checkout) | customer / system | hold | removed | returned |
| Submit proof (UTR required, screenshot optional) | customer | hold | order `PAYMENT_SUBMITTED`; the order's cart lines are removed | unchanged |
| Confirm payment | seller | `PAYMENT_SUBMITTED` | `CONFIRMED` | unchanged |
| Reject (reason required) | seller | `PAYMENT_SUBMITTED` | first time `PAYMENT_REJECTED` with `payment_deadline = now+2h`; second time `CANCELLED` | first: unchanged; second: returned |
| Resubmit (new UTR, optional screenshot) | customer | `PAYMENT_REJECTED` before the deadline | `PAYMENT_SUBMITTED` | unchanged |
| Deadline passes (job) | system | `PAYMENT_REJECTED` | `CANCELLED` (`cancelled_by = SYSTEM`) | returned |
| Pack / Ship (courier and tracking optional) / Deliver | seller | in order | next state | unchanged |
| Cancel | customer | `PAYMENT_SUBMITTED`, `PAYMENT_REJECTED` | `CANCELLED` | returned |
| Cancel (reason required) | seller | before `SHIPPED` | `CANCELLED` | returned |

Rules:
- Checkout locks the customer's user row, then the sarees' rows in id order. A short line is reserved at the available quantity and reported as an adjustment; lines that cannot be ordered stay in the cart and are reported. A seller is skipped when it has no UPI ID or is disabled (`NOT_ACCEPTING_ORDERS`), nothing is in stock (`NOTHING_IN_STOCK`) or the customer already has a hold with it (`HOLD_EXISTS`).
- At most 3 active holds per customer (`409 HOLD_LIMIT`, nothing changes) and one per (customer, seller). An order total above Rs 10,000,000 is refused (`TOTAL_TOO_LARGE`).
- The UTR is exactly 12 digits and unique among non-cancelled orders (`UTR_IN_USE`); the screenshot is optional, must be a real image, and is visible only to the order's customer and seller.
- Returning stock on cancel or lapse does not publish `ItemsRestocked`.
- Orders, holds and proofs of other users answer 404. Illegal transitions answer `409 BAD_STATE`.
- Unread flags (`customer_unread`, `seller_unread`) and `GET /orders/summary` replace notification-table entries; the Android workers poll the summary.

## 6. API (all `/api/v1`)

Customer: `GET/PUT/DELETE /cart` (lines), `POST /cart/checkout {addressId, note?, sellerId?}` → `{holds, adjustments, skipped}`, `GET /holds`, `DELETE /holds/{id}`, `POST /holds/{id}/payment` (multipart: utr, proof?) → the order, `GET/POST/DELETE /wishlist`, `GET/POST/PUT/DELETE /addresses`, `GET /orders/mine?state=&page=`, `GET /orders/{id}`, `GET /orders/{id}/proof`, `POST /orders/{id}/payment` (resubmit after a rejection), `POST /orders/{id}/cancel`, `GET /orders/summary`, `POST /orders/seen`.

Seller: `GET /orders/received?state=&page=`, `GET /orders/summary` (to confirm, to pack, to ship), `POST /orders/{id}/confirm-payment | reject-payment | pack | ship | deliver | cancel`, `PUT /seller/profile` gains `upiId`, `shippingFee`.

Catalog: `ItemDto`/`CatalogItemDto` gain `quantity`, `inStock`, and (customers) `inCart`, `inWishlist`. The `state`/`status` filter values `ON_HOLD` and `SOLD_OUT` become `IN_STOCK` / `OUT_OF_STOCK`; `AVAILABLE`/`SOLD_OUT` accepted for one release as aliases so the older APK keeps working.

Client-neutral: no Android-specific shapes (CLAUDE.md rule).

## 7. Backend structure

New packages: `cart`, `wishlist` (or one `cart` package for both), `address`, `order` (service, controller, `OrderStatus`, dtos, `HoldExpiryJob`), `payment` is folded into `order` (proof is a field set, not a subsystem). Cross-feature effects stay on application events: `OrderPaymentSubmitted`, `OrderStatusChanged` → notification listener, as `BookingItemListener` did. `item.ItemService` loses the ON_HOLD rules; the seller edits the free quantity directly; reserved units are already subtracted, so no extra check is needed.

The `notification` table gains types `PAYMENT_SUBMITTED` (to the seller: a new order to confirm), `ORDER_CONFIRMED`, `ORDER_SHIPPED`, `ORDER_DELIVERED`, `ORDER_CANCELLED` via a CHECK widening in the orders migration, and a nullable `order_id`. The existing worker polling pattern is reused (order unread counts feed `orders/summary`), still 15-minute local notifications; FCM stays on the backlog and becomes more valuable now that customers wait on the seller to confirm payment.

## 8. Android structure

- **Customer shell** (replaces reseller shell): bottom bar Catalog · Wishlist · Cart · Orders · Following. The Following list stays; the Bookings tab and `BookingPanel` are removed.
- Product detail: price, quantity picker (bounded by stock), **Add to cart**, wishlist heart, secondary Call/WhatsApp, "Out of stock" / "Not accepting orders yet" states.
- Checkout: address picker/add, per-seller groups with shipping fee, summary, **Pay seller** (one hold per seller). Hold screen: 2 h countdown, seller's UPI ID with copy button and QR, total, payment form (UTR, optional screenshot via photo picker), **I have paid** (submits proof and creates the order), cancel. Order detail: status timeline, cancel, tracking info. Active holds show at the top of the Orders tab.
- **Seller shell**: the Requests tab becomes **Orders** with the same filterable count tiles (To confirm, To pack, Shipped, All). Settings gain UPI ID and shipping fee. Upload and edit screens gain a quantity field; the collection screen shows stock instead of an availability toggle.
- Role-aware startup: persisted role `RESELLER` from older sessions is mapped to `CUSTOMER` on decode so a logged-in friend is not crashed or logged out. kotlinx.serialization does not coerce unknown enum values, so the new APK must accept both strings, and the server keeps accepting the old status aliases (section 6).

## 9. Delivery slices (one at a time, stop for review after each)

Slices 1–5 are built on branch `customer-orders` and are not pushed (a push to `main` deploys). It exposes `GET /cart` and `PUT/DELETE /cart/items/{id}` instead of the single `/cart` resource listed in section 6.

1. **Backend: roles, quantity, cart, wishlist, addresses.** V6–V8, role rename across `AuthService`, controllers, `SecurityConfig`, quantity in item DTOs and catalog filters, tests.
2. **Backend: holds and orders.** V9, checkout with locking, payment holds, proof submission, seller actions, hold expiry job, events and notifications, `orders/summary`, tests including concurrent checkout of the last piece.
3. **Android customer side.** Shell, cart, checkout, wishlist, orders, product-page changes.
4. **Android seller side.** Orders tab, settings, quantity on upload, collection screen.
5. **Retire bookings and ship.** V10, delete booking code (backend and Android), rewrite `docs/MVP_BRIEF.md`, update CLAUDE.md scope lines, deploy backend, then push APK, then tell friends to update.

6. **Later: payment gateway (Razorpay).** Replaces proof and manual confirmation with in-app payment and webhooks, and adds seller payouts and refunds. Separate spec.

Release order: slice 1–2 are additive and keep the booking endpoints, so the current APK keeps working until slice 5. The status aliases and the dual role decoding exist only to protect the installed APK during the switch.

## 10. Testing

Per CLAUDE.md: unit tests for every service method with logic, an integration test per controller on Testcontainers, `./mvnw verify` before each backend slice is done, Android unit tests with fakes and Compose UI tests before each Android slice is done. Must-haves: role access (customer cannot read another customer's order, seller sees only their orders, anonymous gets 401), two customers racing for the last piece (one wins), checkout split across two sellers, partial-stock failure, hold lapse returning stock, the 3-hold cap, reject-then-resubmit, cancel after confirm by seller, proof image access control, money totals. Existing booking tests are deleted in slice 5, not earlier.

## 11. Open edge cases to settle during planning

- Item sells out between cart and checkout: spec says trim and tell the customer; confirm that is acceptable versus failing the whole checkout.
- Seller cancels after confirming payment: no refund handling exists (payment is outside the app). The order shows "Refund to be handled directly with the seller". Acceptable for v1?
- Seller editing price or quantity of a saree that is in carts: the cart always shows live price; the order snapshots at checkout.
- A customer who pays after their 2 h hold lapsed: the stock may be gone. The hold screen blocks "I have paid" after expiry and says to contact the seller; any money already sent is settled directly with the seller.
- Disabled seller or customer: existing `enabled` check applies; a disabled seller's open orders are left for admin handling.
- Return and exchange: out of scope.

## 12. Out of scope

Payment gateway (slice 6, separate spec), courier integration, ratings and reviews, coupons, tax invoices, refunds, seller-as-buyer on one account, multiple images per cart line variants, FCM push (backlog), the Angular web app.
