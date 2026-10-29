# MVP Brief — Saree Market Catalog

## Goal

Replace scrolling through WhatsApp groups with one app where buying a saree is a few taps. Sellers list their sarees in collections and say how many pieces they have. Customers (shopkeepers who resell, and anyone else) find sarees across all sellers by keyword, filters or a photo, add them to a cart, and check out. Each seller gets their own order, is paid directly by UPI, confirms the payment, and packs and ships it.

The app moves the order and the paperwork, not the money: customers pay the seller's UPI ID outside the app and enter the UTR (the transaction reference) as proof, and the seller checks their own bank or UPI app before confirming. There is no payment gateway.

## Frontends

- **Phase 1 (MVP): native Android app** (`android/`): Kotlin, Jetpack Compose, Material 3
- **Phase 2 (after the MVP): Angular web app** (`frontend/`) on the same backend; it adds the admin screens
- One REST API serves both. The backend is client-neutral (no Android-specific endpoints or response shapes) and has a configurable CORS allow-list (empty by default) so the web app can be added without backend changes
- `docs/design/DESIGN.md` and the screens in `docs/design/stitch/` are shared by both

## Roles

| Role | Can do |
| --- | --- |
| Seller | Register, manage shop profile (UPI ID, shipping fee), create collections, upload photos with a quantity, edit items, mark available / sold out, receive and process orders |
| Customer | Register (a reseller is just a customer), browse and search the catalog (all sellers), search by image, see seller name and contact number, follow chosen sellers and get notified of their new sarees, save to a wishlist, add to a cart, check out, pay and track orders |
| Admin | Seeded by migration; manage saree types and fabrics; disable a user |
| Anonymous | Can only see the login and register pages |

Seller contact details are visible only to logged-in customers and admins. A seller sees a customer's name and phone only on orders placed with them. A seller cannot buy on the same account; a seller who wants to buy registers a second account.

## Core concepts

- **Seller** — a shop in the market (shop name, market location, contact phone, optional WhatsApp number)
- **Collection** — a group of sarees a seller uploads together, e.g. "Diwali soft silks". Holds shared defaults: title, saree type, fabric, price, description
- **Item** — one saree design = one photo. Inherits collection defaults; can override colour and price. The seller sets a **quantity** (pieces in stock, 0 to 10,000) and a mark, `AVAILABLE` or `SOLD_OUT`. A saree can be ordered only when it is marked available and has at least one piece
- **Cart** — sarees a customer means to buy, grouped by seller. Nothing is reserved while it sits in the cart
- **Payment hold** — checkout reserves the pieces for 2 hours, one hold per seller, while the customer pays that seller's UPI ID. No order exists until the customer submits the UTR
- **Order** — made when the customer submits the UTR; the seller confirms the payment, packs, ships and delivers it. Prices, address and shipping fee are copied into the order so later edits do not change it
- **Follow** — a customer subscribes to a seller, like joining that seller's WhatsApp group. Sellers are publishers, customers are subscribers. Following controls notifications only; search always covers every seller
- **Notification** — an in-app message to a follower when a followed seller publishes new sarees
- **Reference data** — saree types (Kanjivaram, Banarasi, Soft silk, Cotton, Chiffon, Georgette, Linen, Organza, Tussar...) and fabrics, seeded by Flyway and editable by admin

## Data model

```
app_user        id, phone (unique), password_hash, name, role (SELLER | CUSTOMER | ADMIN), enabled, created_at
seller_profile  user_id (PK/FK), shop_name, market_location, contact_phone, whatsapp_number, upi_id, shipping_fee
saree_type      id, name (unique), active
fabric          id, name (unique), active
collection      id, seller_id, title, saree_type_id, fabric_id, price, description, created_at, updated_at
item            id, collection_id, seller_id, colour, price_override, status (AVAILABLE | SOLD_OUT), quantity,
                image_path, thumb_path, image_hash (BIGINT), created_at, updated_at
seller_follow   reseller_id (the following customer), seller_id, notify_mode (INSTANT | MUTED), created_at   PK (reseller_id, seller_id)
notification    id, reseller_id (the notified customer), seller_id, collection_id, type, item_count, read_at, created_at, updated_at
address         id, customer_id, name, phone, line1, line2, city, state, pin, is_default, created_at
wishlist_entry  customer_id, item_id, added_at   PK (customer_id, item_id)
cart_line       customer_id, item_id, quantity, added_at, updated_at   PK (customer_id, item_id)
payment_hold    id, customer_id, seller_id, items_total, shipping_fee, grand_total, address (JSONB), customer_note, created_at, expires_at   unique (customer_id, seller_id)
hold_line       id, hold_id, item_id, title, colour, unit_price, quantity
customer_order  id, customer_id, seller_id, status, items_total, shipping_fee, grand_total, address (JSONB), customer_note,
                payment_reference (the 12-digit UTR), payment_proof_key, rejection_reason, rejection_count, payment_deadline,
                cancel_reason, cancelled_by, courier, tracking_number, customer_unread, seller_unread, created_at, updated_at
order_line      id, order_id, item_id, title, colour, unit_price, quantity
order_status_history  id, order_id, status, at
```

- Notification types: `NEW_COLLECTION`, `ITEMS_ADDED` (new photos added to an existing collection), `BACK_IN_STOCK` (items switched from sold out to available)
- Order statuses: `PAYMENT_SUBMITTED`, `PAYMENT_REJECTED`, `CONFIRMED`, `PACKED`, `SHIPPED`, `DELIVERED`, `CANCELLED`
- The follow and notification tables keep the column name `reseller_id` from when the role was called reseller
- Index: `seller_follow(seller_id)` for fan-out, `notification(reseller_id, read_at, created_at)` for the bell; a unique index on `customer_order(payment_reference)` for live orders, so one payment cannot back two orders

- Effective item price = `price_override` if set, else the collection price
- Prices in whole rupees (integer), > 0; an order from one seller is at most ₹1,00,00,000
- Index: `item(status)`, `item(seller_id)`, `collection(saree_type_id)`, `collection(fabric_id)`, and a full-text index over collection title + description + item colour

## Features

### 1. Auth
- Register with phone, name, password and role (SELLER or CUSTOMER). A login attempt limiter slows password guessing
- Seller registration also captures shop name, market location, contact phone, WhatsApp number
- Login returns a JWT (24 h expiry); no refresh tokens in MVP. The app stores it in DataStore, sends it on every call, and logout clears it
- The app picks the Seller or Customer flow from the role at login
- No OTP, no admin approval in MVP

### 2. Seller: collections and items
- Create a collection: title, saree type, fabric, price, description, and 1–30 photos in one multi-file upload (Android Photo Picker, multi-select)
- Each photo becomes an item marked `AVAILABLE`, with the quantity the seller entered for the upload (default 1; each saree's count can be changed later)
- On upload: validate JPG/PNG/WebP, max 10 MB each, resize the stored original to max 1600 px, create a 400 px thumbnail, compute the image hash
- Seller dashboard: list of own collections with item counts (available / sold out)
- Shop settings: UPI ID (where customers pay) and a flat shipping fee added to each order. Without a UPI ID nobody can order from the shop, and the app says so
- Collection page: photo grid showing "N in stock", a one-tap toggle Available ↔ Sold out per item, plus "Mark all sold out". A saree with no pieces left looks sold out whatever its mark says
- Edit collection fields; edit item colour, price override and quantity; delete an item or a whole collection (deletes image files too)

### 3. Customer: catalog search
- Results are whole collections, as the seller posted them: a card per collection with title, price, saree type, fabric, seller shop name, a strip of its first photos and how many are available. A collection is listed when at least one of its photos matches the filters; tapping it opens the collection page. Photo (image) search still lists single photos, closest first
- Filters (combinable):
  - Keyword (full-text over title, description, colour)
  - Saree type (multi-select)
  - Fabric (multi-select)
  - Colour (text match)
  - Price category: Under ₹1,000 · ₹1,000–3,000 · ₹3,000–7,000 · ₹7,000–15,000 · Above ₹15,000, plus custom min/max
  - Seller (select)
  - Availability: Available only (default) / include sold out (a saree with no pieces left counts as sold out)
  - Uploaded: last 7 days / last 30 days / any time
- Sort: newest (default), price low–high, price high–low (by the collection price)
- Paginated, 24 per page
- Item detail: large photo, all fields, collection name, seller shop name, market location, a quantity stepper with **Add to cart**, a wishlist heart, and **Call** (`tel:`) and **WhatsApp** (`https://wa.me/<number>`) buttons. Call and WhatsApp are plain links, not an integration. "Only N left" shows at three or fewer; "Out of stock" replaces the buying controls when none can be ordered
- Seller page: shop details and all their collections
- Collection page: all items in that collection

### 4. Image search
Customers often receive a screenshot or forwarded photo of a saree and need to find who has it.
- Customer picks a photo from the gallery or takes one with the camera, from the catalog screen
- MVP approach: **perceptual hash (dHash, 64-bit)** computed for every item on upload and for the query image
- Match by Hamming distance between hashes; return items with distance ≤ 12, closest first, then apply any active filters
- Works well for the same photo resized, compressed, cropped slightly or screenshotted. It does not find "similar-looking" sarees from different photos; that is a later add-on (image embeddings + pgvector)
- Hashing in a dedicated `ImageHasher` class with unit tests (identical image → 0, resized copy → small distance, unrelated image → large distance)

### 5. Follow sellers and notifications (pub/sub)
With ~100 sellers posting daily and ~15 customers, notifying everyone of everything would be a flood. Customers choose whose updates they get.

**Following**
- Follow / Unfollow button on every seller page and item detail
- "My sellers" page: list of followed sellers with mute toggle and unfollow
- Catalog has a **Following** toggle that limits results to followed sellers; customers land on "New from sellers you follow" by default, and can switch to All sellers anytime

**What triggers a notification** (only to followers with notify_mode INSTANT)
- Seller publishes a new collection → `NEW_COLLECTION`: "Lakshmi Silks added 18 sarees · Diwali soft silks"
- Seller adds photos to an existing collection → `ITEMS_ADDED`
- Items go from sold out back to available → `BACK_IN_STOCK`
- Marking items sold out does **not** notify anyone; the catalog simply stops showing them as available

**Anti-flood rules**
- One notification per collection event, never one per photo
- Coalescing: if the customer already has an **unread** notification of the same type for the same collection created in the last 2 hours, update its `item_count` and `updated_at` instead of creating a new row
- Muted follows get nothing but still appear under the Following filter
- Unfollowing stops notifications immediately; existing notifications stay

**Delivery (MVP: in-app only)**
- Bell icon in the header with unread count, refreshed by polling every 60 seconds while the app is open
- Notifications screen, newest first; tapping one marks it read and opens the collection
- "Mark all read"
- Android only: a WorkManager periodic job (every 15 minutes) checks the unread count in the background and shows a local Android notification when there are new collections from followed sellers. No Firebase or push service in the MVP
- Notifications older than 30 days are deleted by a nightly `@Scheduled` job

**How it's built**
- Seller actions publish Spring application events: `CollectionPublished`, `ItemsAdded`, `ItemsRestocked`
- A `NotificationService` listens with `@TransactionalEventListener(phase = AFTER_COMMIT)` and `@Async`, looks up followers of that seller, and writes or coalesces notification rows (fan-out on write; at this scale it's a few dozen rows per event)
- Delivery goes through a `NotificationChannel` interface; MVP has `InAppChannel` only. Push, WhatsApp or email channels plug in later, and a real broker (Kafka, RabbitMQ) can replace in-process events only if scale demands it
- Must-have tests: a customer following seller A but not B is notified only of A; muted gets nothing; a second upload to the same collection within 2 hours coalesces into one unread notification; sold-out changes notify no one; unfollow stops notifications

### 6. Customer: wishlist, cart and addresses
- Wishlist: a heart on every saree page; a Wishlist tab lists saved sarees, newest first, and keeps sold-out ones (marked out of stock)
- Cart: set a quantity on the saree page (never more than the seller has); the Cart tab groups lines by seller with that seller's shipping fee and total. A line that is out of stock stays listed but is not counted. A seller without a UPI ID shows "Not accepting orders yet". At most 50 lines
- Addresses: up to 10 saved addresses with one default; edit and delete

### 7. Checkout, payment hold and order
- **Pay <shop>** in the cart starts checkout for that seller: choose an address, add a note, continue. Checkout reserves what is available (a short line is reserved at the available count and the customer is told) and creates one payment hold, valid for 2 hours. A customer has at most 3 holds, one per seller. A hold the customer cancels or lets lapse gives the pieces back
- The hold screen shows the total, a countdown, the seller's UPI ID with Copy and **Pay with UPI app** (a `upi://pay` link), and asks for the 12-digit **UTR** (required) and an optional screenshot. **I have paid** turns the hold into an order awaiting the seller and takes the order's lines out of the cart
- The UTR must be exactly 12 digits and not already used by a live order; the screenshot must be a real image and is seen only by that customer and seller
- A customer can cancel an order until the seller has confirmed it; stock is returned. If the seller says the payment was not found, the customer gets 2 hours to send a correct UTR (once); a second rejection, or the time running out, cancels the order
- Order screen: a timeline, the lines, the address, courier and tracking when shipped, and the seller's reason when rejected or cancelled
- Alerts: the Orders tab shows a badge (unread updates plus payments in progress); the 15-minute background check shows an Android notification for order updates

### 8. Seller: orders
- Orders tab with five filters: To confirm, To pack, To ship, Shipped, All (counts from the server). Each order shows the customer's name and phone with Call and WhatsApp, the lines, the city and PIN, the total, the UTR and a **View screenshot** link
- **Payment received** asks "Did ₹X arrive with UTR …? Check your bank or UPI app first" before confirming. **Not found** and **Cancel order** need a reason. **Mark packed**, **Ship** (courier and tracking number optional) and **Mark delivered** move the order along; a shipped order can no longer be cancelled. The Orders tab badge counts orders waiting for confirmation
- The 15-minute background check tells a seller about new orders to confirm

### 9. Admin (minimal, backend only in the MVP)
- No admin screens in the Android app. Admin is the seeded user plus the endpoints below, covered by integration tests; admin screens come with the Angular app
- Seeded admin user via Flyway with a dev password from config
- Add, rename, deactivate saree types and fabrics
- Disable or enable a user

## API outline (`/api/v1`)

| Method | Path | Who |
| --- | --- | --- |
| POST | /auth/register, /auth/login | Anyone |
| GET | /sellers, /sellers/{id} | Customer, Admin |
| POST | /collections (multipart: fields, optional quantity, files) | Seller |
| GET/PUT/DELETE | /collections/{id} | Seller (own); GET also Customer |
| GET | /sellers/me/collections | Seller |
| POST | /collections/{id}/items (multipart, add more photos, optional quantity) | Seller (own) |
| PATCH | /items/{id} (colour, priceOverride, quantity, status) | Seller (own) |
| PATCH | /collections/{id}/items/status (bulk status) | Seller (own) |
| DELETE | /items/{id} | Seller (own) |
| GET | /catalog?q=&typeIds=&fabricIds=&colour=&minPrice=&maxPrice=&sellerId=&status=&since=&sort=&page= (single photos) | Customer, Admin |
| GET | /catalog/collections (same filters; whole collections) | Customer, Admin |
| POST | /catalog/image-search (multipart image + same filter params) | Customer, Admin |
| GET | /items/{id} | Customer, Admin, owning Seller |
| GET | /cart (grouped by seller) | Customer |
| PUT/DELETE | /cart/items/{itemId} (PUT body: quantity) | Customer |
| GET | /wishlist?page= | Customer |
| PUT/DELETE | /wishlist/{itemId} | Customer |
| GET/POST | /addresses | Customer |
| PUT/DELETE | /addresses/{id} | Customer |
| POST | /cart/checkout (addressId, note, optional sellerId) → holds, adjustments, skipped | Customer |
| GET | /holds | Customer |
| DELETE | /holds/{id} | Customer |
| POST | /holds/{id}/payment (multipart: utr, optional proof) → the order | Customer |
| GET | /orders/mine?state=&page= | Customer |
| GET | /orders/received?state=&page= | Seller |
| GET | /orders/{id}, /orders/{id}/proof | The order's customer or seller |
| POST | /orders/{id}/payment (multipart; after a rejection) | Customer |
| POST | /orders/{id}/confirm-payment, /reject-payment (reason), /pack, /ship (courier, trackingNumber), /deliver | Seller (own) |
| POST | /orders/{id}/cancel (the seller gives a reason) | Customer, Seller |
| GET | /orders/summary | Customer, Seller |
| POST | /orders/seen | Customer, Seller |
| GET | /images/{itemId}/full, /images/{itemId}/thumb | Logged-in users |
| GET | /reference/saree-types, /reference/fabrics | Logged-in users |
| PUT/DELETE | /follows/{sellerId} (PUT body: notifyMode) | Customer |
| GET | /follows | Customer |
| GET | /notifications?page=, /notifications/unread-count | Customer |
| POST | /notifications/{id}/read, /notifications/read-all | Customer |
| POST/PUT | /admin/saree-types, /admin/fabrics | Admin |
| GET | /admin/users?q=&role=&page=, /admin/saree-types, /admin/fabrics (all, including deactivated) | Admin |
| PATCH | /admin/users/{id}/enabled | Admin |
| GET/PATCH | /auth/me (PATCH: name) | Logged-in users |
| PUT | /auth/me/password | Logged-in users |
| GET/PUT | /seller/profile (shopName, marketLocation, contactPhone, whatsappNumber, upiId, shippingFee) | Seller (own) |

## Screens

Layouts come from `docs/design/stitch/v2/code.html` (one `screen-*` section each). The Android app builds each natively in Compose. The route column is the Angular route in phase 2.

| Screen (Stitch id) | Route (phase 2) | Who | Purpose |
| --- | --- | --- | --- |
| Login, Register (`screen-login`, `screen-register`) | /login, /register | Anyone | Auth; register form switches fields by role |
| Seller home (`screen-seller-dashboard`) | /seller | Seller | Dashboard: collections with available / sold-out counts |
| New upload (`screen-new-collection`) | /seller/collections/new | Seller | Create collection with multi-photo upload and progress |
| Batch detail (`screen-collection-detail`) | /seller/collections/:id | Seller | Grid with availability toggles, edit, add photos |
| Catalog, Filters, Lens search (`screen-catalog`, `screen-filters`, `screen-img-search`) | /catalog | Customer | Keyword box, filters, image search, collection cards |
| Saree detail (`screen-detail`) | /items/:id | Customer | Item detail with quantity, Add to cart, wishlist heart, Call and WhatsApp |
| Seller profile (`screen-seller-page`) | /sellers/:id | Customer | Seller page with collections and Follow button |
| My sellers (`screen-my-sellers`) | /my-sellers | Customer | Followed sellers, mute, unfollow |
| Notifications (`screen-notifications`) | /notifications | Customer | Notification list; bell icon in the top bar on every screen |
| Wishlist, Cart, Checkout sheet (customer app, no Stitch screen) | /wishlist, /cart | Customer | Saved sarees; cart grouped by seller with Pay buttons; address choice before paying |
| Payment hold, Orders, Order detail (customer app, no Stitch screen) | /holds/:id, /orders, /orders/:id | Customer | Pay by UPI and enter the UTR; list and follow orders; resend a corrected UTR; cancel |
| Orders, Shop settings (seller app, no Stitch screen) | /seller/orders, /seller/shop | Seller | Confirm, reject, pack, ship, deliver; UPI ID and shipping fee |
| Desktop catalog (`screen-desktop-catalog`) | /catalog (wide) | Customer | Web layout only; not built in Android |
| Admin (`screen-admin`) | /admin | Admin | Phase 2 only; reference data and users |

On phones the filters panel is a bottom sheet. The customer's bottom bar is Catalog, Wishlist, Cart, Orders, Profile; "My sellers" and the notification bell sit in the catalog header. The seller's bottom bar is Collections, Orders, Add, Shop, Profile.

## Build order (one slice at a time)

1. **Skeleton** — Maven project with health endpoint and configurable CORS, docker-compose PostgreSQL, Flyway V1 with all tables, seed data and the admin user, `./mvnw verify` passing; Android project that builds, applies the DESIGN.md theme and shows a placeholder screen calling the health endpoint
2. **Auth** — register, login, JWT filter, role-based access (backend); login and register screens, DataStore token, role-based flows (Android); tests
3. **Seller collections** — create with photo upload, local storage, thumbnails, image hash, dashboard, edit, delete, tests
4. **Availability** — per-item and bulk status toggles, tests
5. **Catalog search** — filters, keyword, sort, paging, item detail, seller page, tests for each filter
6. **Image search** — dHash, Hamming match, gallery and camera capture in the app, tests with sample images
7. **Follow and notifications** — follows, events, fan-out with coalescing, bell, notifications screen, WorkManager local notifications, Following filter, tests
8. **Admin (backend only)** — reference data and user enable/disable endpoints with integration tests
9. **Side menu and profile** — shared drawer for all roles (profile, change password, log out; sellers also edit shop details), with the endpoints above

Each slice: plan → build → unit + integration tests → summary and how to try it → stop for review. The ponytail review (`/ponytail-review`) runs once on the whole backend after slice 9, not per slice.

After the MVP, the product became a small marketplace (the booking-request feature that came first was replaced). The orders work was built in slices on the same backend:

10. **Customer foundation** — the `CUSTOMER` role, item quantity, the seller's UPI ID and shipping fee, addresses, wishlist, cart
11. **Holds and orders** — checkout, 2-hour payment holds, UTR proof, seller confirm / reject / pack / ship / deliver / cancel, the expiry job
12. **Android customer app** — wishlist, cart, checkout, hold screen, orders
13. **Android seller app** — Orders tab, shop payment settings, quantity on upload and per saree, order alerts
14. **Retire bookings and release** — remove the booking feature (migration V13), rewrite this brief, rotate the JWT secret, deploy

Next: Angular web app (`frontend/`) with the admin screens.

## Out of scope (later add-ons)

A payment gateway and automatic payment verification (the seller checks the UTR in their own bank app), refunds, courier integration and live shipping rates, returns and exchanges, ratings and reviews, coupons, tax invoices, a seller buying on the same account, WhatsApp API, SMS / OTP, push notifications (Firebase) and WhatsApp / email channels, daily digest mode, message broker, AWS / S3, AI quick-fill, similarity search with embeddings, disputes, admin approval of sellers, bulk quantity editing.

## Done means

- A seller can register in the Android app, set a UPI ID and shipping fee, upload a collection of 20 photos with a quantity, and mark items sold out
- A customer who follows a seller sees one notification when that seller uploads a collection; a customer who doesn't follow them sees none, but can still find those sarees in search
- A customer can find an item by keyword, by any filter combination, and by uploading a screenshot of one of the uploaded photos, then add it to the cart or tap Call or WhatsApp
- A customer with sarees from two sellers in the cart can pay each seller separately, and each seller sees only their own order, confirms the payment, packs it, ships it with tracking, and marks it delivered; the customer follows each step
- Pieces are reserved at checkout and given back when a hold lapses or an order is cancelled; the same piece is never sold twice
- `./mvnw verify` and the Android unit and UI tests pass
- Runs locally with the three commands in CLAUDE.md (PostgreSQL, backend, Android app on an emulator)
