# MVP Brief — Saree Market Catalog

## Goal

Replace scrolling through WhatsApp groups with one searchable app. Sellers list their sarees in collections and mark each one available or sold out. Resellers find sarees across all sellers by keyword, filters or a photo, see whether each is available, and contact the seller directly by phone or WhatsApp link.

Nothing is bought or paid for in the app. It is a live catalog with availability.

## Frontends

- **Phase 1 (MVP): native Android app** (`android/`): Kotlin, Jetpack Compose, Material 3
- **Phase 2 (after the MVP): Angular web app** (`frontend/`) on the same backend; it adds the admin screens
- One REST API serves both. The backend is client-neutral (no Android-specific endpoints or response shapes) and has a configurable CORS allow-list (empty by default) so the web app can be added without backend changes
- `docs/design/DESIGN.md` and the screens in `docs/design/stitch/` are shared by both

## Roles

| Role | Can do |
| --- | --- |
| Seller | Register, manage shop profile, create collections, upload photos, edit items, mark available / sold out |
| Reseller | Register, browse and search the catalog (all sellers), search by image, see seller name and contact number, follow chosen sellers and get notified of their new sarees |
| Admin | Seeded by migration; manage saree types and fabrics; disable a user |
| Anonymous | Can only see the login and register pages |

Seller contact details are visible only to logged-in resellers and admins.

## Core concepts

- **Seller** — a shop in the market (shop name, market location, contact phone, optional WhatsApp number)
- **Collection** — a group of sarees a seller uploads together, e.g. "Diwali soft silks". Holds shared defaults: title, saree type, fabric, price, description
- **Item** — one saree = one photo. Inherits collection defaults; can override colour and price. Status: `AVAILABLE` or `SOLD_OUT`
- **Follow** — a reseller subscribes to a seller, like joining that seller's WhatsApp group. Sellers are publishers, resellers are subscribers. Following controls notifications only; search always covers every seller
- **Notification** — an in-app message to a follower when a followed seller publishes new sarees
- **Reference data** — saree types (Kanjivaram, Banarasi, Soft silk, Cotton, Chiffon, Georgette, Linen, Organza, Tussar...) and fabrics, seeded by Flyway and editable by admin

## Data model

```
app_user        id, phone (unique), password_hash, name, role, enabled, created_at
seller_profile  user_id (PK/FK), shop_name, market_location, contact_phone, whatsapp_number
saree_type      id, name (unique), active
fabric          id, name (unique), active
collection      id, seller_id, title, saree_type_id, fabric_id, price, description, created_at, updated_at
item            id, collection_id, seller_id, colour, price_override, status,
                image_path, thumb_path, image_hash (BIGINT), created_at, updated_at
seller_follow   reseller_id, seller_id, notify_mode (INSTANT | MUTED), created_at   PK (reseller_id, seller_id)
notification    id, reseller_id, seller_id, collection_id, type, item_count, read_at, created_at, updated_at
```

- Notification types: `NEW_COLLECTION`, `ITEMS_ADDED` (new photos added to an existing collection), `BACK_IN_STOCK` (items switched from sold out to available)
- Index: `seller_follow(seller_id)` for fan-out, `notification(reseller_id, read_at, created_at)` for the bell

- Effective item price = `price_override` if set, else the collection price
- Prices in whole rupees (integer), > 0
- Index: `item(status)`, `item(seller_id)`, `collection(saree_type_id)`, `collection(fabric_id)`, and a full-text index over collection title + description + item colour

## Features

### 1. Auth
- Register with phone, name, password and role (SELLER or RESELLER)
- Seller registration also captures shop name, market location, contact phone, WhatsApp number
- Login returns a JWT (24 h expiry); no refresh tokens in MVP. The app stores it in DataStore, sends it on every call, and logout clears it
- The app picks the Seller or Reseller flow from the role at login
- No OTP, no admin approval in MVP

### 2. Seller: collections and items
- Create a collection: title, saree type, fabric, price, description, and 1–30 photos in one multi-file upload (Android Photo Picker, multi-select)
- Each photo becomes an item with status `AVAILABLE`
- On upload: validate JPG/PNG/WebP, max 10 MB each, resize the stored original to max 1600 px, create a 400 px thumbnail, compute the image hash
- Seller dashboard: list of own collections with item counts (available / sold out)
- Collection page: photo grid with a one-tap toggle Available ↔ Sold out per item, plus "Mark all sold out"
- Edit collection fields; edit item colour and price override; delete an item or a whole collection (deletes image files too)

### 3. Reseller: catalog search
- Results are whole collections, as the seller posted them: a card per collection with title, price, saree type, fabric, seller shop name, a strip of its first photos and how many are available. A collection is listed when at least one of its photos matches the filters; tapping it opens the collection page. Photo (image) search still lists single photos, closest first
- Filters (combinable):
  - Keyword (full-text over title, description, colour)
  - Saree type (multi-select)
  - Fabric (multi-select)
  - Colour (text match)
  - Price category: Under ₹1,000 · ₹1,000–3,000 · ₹3,000–7,000 · ₹7,000–15,000 · Above ₹15,000, plus custom min/max
  - Seller (select)
  - Availability: Available only (default) / include sold out
  - Uploaded: last 7 days / last 30 days / any time
- Sort: newest (default), price low–high, price high–low (by the collection price)
- Paginated, 24 per page
- Item detail: large photo, all fields, collection name, seller shop name, market location, **Call** (`tel:`) and **WhatsApp** (`https://wa.me/<number>`) buttons. These are plain links, not an integration.
- Seller page: shop details and all their collections
- Collection page: all items in that collection

### 4. Image search
Resellers often receive a screenshot or forwarded photo of a saree and need to find who has it.
- Reseller picks a photo from the gallery or takes one with the camera, from the catalog screen
- MVP approach: **perceptual hash (dHash, 64-bit)** computed for every item on upload and for the query image
- Match by Hamming distance between hashes; return items with distance ≤ 12, closest first, then apply any active filters
- Works well for the same photo resized, compressed, cropped slightly or screenshotted. It does not find "similar-looking" sarees from different photos; that is a later add-on (image embeddings + pgvector)
- Hashing in a dedicated `ImageHasher` class with unit tests (identical image → 0, resized copy → small distance, unrelated image → large distance)

### 5. Follow sellers and notifications (pub/sub)
With ~100 sellers posting daily and ~15 resellers, notifying everyone of everything would be a flood. Resellers choose whose updates they get.

**Following**
- Follow / Unfollow button on every seller page and item detail
- "My sellers" page: list of followed sellers with mute toggle and unfollow
- Catalog has a **Following** toggle that limits results to followed sellers; resellers land on "New from sellers you follow" by default, and can switch to All sellers anytime

**What triggers a notification** (only to followers with notify_mode INSTANT)
- Seller publishes a new collection → `NEW_COLLECTION`: "Lakshmi Silks added 18 sarees · Diwali soft silks"
- Seller adds photos to an existing collection → `ITEMS_ADDED`
- Items go from sold out back to available → `BACK_IN_STOCK`
- Marking items sold out does **not** notify anyone; the catalog simply stops showing them as available

**Anti-flood rules**
- One notification per collection event, never one per photo
- Coalescing: if the reseller already has an **unread** notification of the same type for the same collection created in the last 2 hours, update its `item_count` and `updated_at` instead of creating a new row
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
- Must-have tests: a reseller following seller A but not B is notified only of A; muted gets nothing; a second upload to the same collection within 2 hours coalesces into one unread notification; sold-out changes notify no one; unfollow stops notifications

### 6. Admin (minimal, backend only in the MVP)
- No admin screens in the Android app. Admin is the seeded user plus the endpoints below, covered by integration tests; admin screens come with the Angular app
- Seeded admin user via Flyway with a dev password from config
- Add, rename, deactivate saree types and fabrics
- Disable or enable a user

## API outline (`/api/v1`)

| Method | Path | Who |
| --- | --- | --- |
| POST | /auth/register, /auth/login | Anyone |
| GET/PUT | /sellers/me | Seller |
| GET | /sellers, /sellers/{id} | Reseller, Admin |
| POST | /collections (multipart: fields + files) | Seller |
| GET/PUT/DELETE | /collections/{id} | Seller (own); GET also Reseller |
| GET | /sellers/me/collections | Seller |
| POST | /collections/{id}/items (multipart, add more photos) | Seller (own) |
| PATCH | /items/{id} (colour, priceOverride, status) | Seller (own) |
| PATCH | /collections/{id}/items/status (bulk status) | Seller (own) |
| DELETE | /items/{id} | Seller (own) |
| GET | /catalog?q=&typeIds=&fabricIds=&colour=&minPrice=&maxPrice=&sellerId=&status=&since=&sort=&page= (single photos) | Reseller, Admin |
| GET | /catalog/collections (same filters; whole collections) | Reseller, Admin |
| POST | /catalog/image-search (multipart image + same filter params) | Reseller, Admin |
| GET | /items/{id} | Reseller, Admin, owning Seller |
| GET | /images/{itemId}/full, /images/{itemId}/thumb | Logged-in users |
| GET | /reference/saree-types, /reference/fabrics | Logged-in users |
| PUT/DELETE | /follows/{sellerId} (PUT body: notifyMode) | Reseller |
| GET | /follows | Reseller |
| GET | /notifications?page=, /notifications/unread-count | Reseller |
| POST | /notifications/{id}/read, /notifications/read-all | Reseller |
| POST/PUT | /admin/saree-types, /admin/fabrics | Admin |
| GET | /admin/users?q=&role=&page=, /admin/saree-types, /admin/fabrics (all, including deactivated) | Admin |
| PATCH | /admin/users/{id}/enabled | Admin |
| PATCH | /auth/me (name) | Logged-in users |
| PUT | /auth/me/password | Logged-in users |
| GET/PUT | /seller/profile | Seller (own) |

## Screens

Layouts come from `docs/design/stitch/v2/code.html` (one `screen-*` section each). The Android app builds each natively in Compose. The route column is the Angular route in phase 2.

| Screen (Stitch id) | Route (phase 2) | Who | Purpose |
| --- | --- | --- | --- |
| Login, Register (`screen-login`, `screen-register`) | /login, /register | Anyone | Auth; register form switches fields by role |
| Seller home (`screen-seller-dashboard`) | /seller | Seller | Dashboard: collections with available / sold-out counts |
| New upload (`screen-new-collection`) | /seller/collections/new | Seller | Create collection with multi-photo upload and progress |
| Batch detail (`screen-collection-detail`) | /seller/collections/:id | Seller | Grid with availability toggles, edit, add photos |
| Catalog, Filters, Lens search (`screen-catalog`, `screen-filters`, `screen-img-search`) | /catalog | Reseller | Keyword box, filters, image search, collection cards |
| Saree detail (`screen-detail`) | /items/:id | Reseller | Item detail with Call and WhatsApp buttons |
| Seller profile (`screen-seller-page`) | /sellers/:id | Reseller | Seller page with collections and Follow button |
| My sellers (`screen-my-sellers`) | /my-sellers | Reseller | Followed sellers, mute, unfollow |
| Notifications (`screen-notifications`) | /notifications | Reseller | Notification list; bell icon in the top bar on every screen |
| Desktop catalog (`screen-desktop-catalog`) | /catalog (wide) | Reseller | Web layout only; not built in Android |
| Admin (`screen-admin`) | /admin | Admin | Phase 2 only; reference data and users |

On phones the filters panel is a bottom sheet.

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

After the MVP: Angular web app (`frontend/`) with the admin screens.

## Out of scope (later add-ons)

Orders, payments, UPI, stock quantities and reservations, customer-facing storefronts, reseller markup, WhatsApp API, SMS / OTP, push / WhatsApp / email notification channels, daily digest mode, message broker, AWS / S3, AI quick-fill, similarity search with embeddings, ratings, disputes, admin approval of sellers.

## Done means

- A seller can register in the Android app, upload a collection of 20 photos from the phone, and mark items sold out
- A reseller who follows a seller sees one notification when that seller uploads a collection; a reseller who doesn't follow them sees none, but can still find those sarees in search
- A reseller can find an item by keyword, by any filter combination, and by uploading a screenshot of one of the uploaded photos, then tap Call or WhatsApp
- `./mvnw verify` and the Android unit and UI tests pass
- Runs locally with the three commands in CLAUDE.md (PostgreSQL, backend, Android app on an emulator)
