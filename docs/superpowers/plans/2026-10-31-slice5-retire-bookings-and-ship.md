# Slice 5 — Retire Bookings, Document, and Ship Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the booking feature from the backend and the app, rewrite the product brief and `CLAUDE.md` for the customer-orders product, then put the branch live: merge, deploy to the shared RDS and EC2, rotate `JWT_SECRET`, and hand friends a new APK.

**Architecture:** Tasks 1–4 are local and reversible (code, docs, rehearsal). Task 5 is the release and touches shared systems (GitHub, the production database, the live server, friends' phones); **each of its steps that changes something outside this machine stops and asks first.** The booking removal is one forward-only Flyway migration (`V13`) rehearsed against a database built the way production was (V1–V12 with realistic booking data) before anything is merged.

**Tech Stack:** Spring Boot 4.1 + Flyway + PostgreSQL (Testcontainers), Kotlin/Compose app, GitHub Actions (Backend CI → Deploy over SSH to EC2), Docker Hub, RDS.

**Spec:** `docs/superpowers/specs/2026-10-28-customer-orders-redesign-design.md` (sections 4, 9). Release facts come from `.github/workflows/{backend,deploy}.yml`, `deploy/docker-compose.yml` and the project memory notes.

## Rulings carried into this plan

1. **`item.status` stays, `ON_HOLD` goes.** The seller's own "in stock / sold out" mark (`AVAILABLE`, `SOLD_OUT`) is still used by the seller screens, the catalog filters and "Mark all". Removing the column would be a second redesign of the catalog and the seller UI; it is a follow-up, and `inStock` already combines mark and quantity. Cost if wrong: two ways to say "not for sale" remain.
2. **Held sarees come back on sale.** `V13` turns every `ON_HOLD` item into `AVAILABLE` with `quantity = GREATEST(quantity, 1)` (a held saree was a real piece), then drops `booking_request` and tightens the status check to `('AVAILABLE','SOLD_OUT')`. Open requests are discarded with the table: nothing in the new app can show them, and the old app cannot run against the new server anyway.
3. **API fields removed outright:** `ItemDto.openRequests`, `ItemDetailDto.myBooking`, `CollectionSummaryDto.onHoldCount`, `CatalogCollectionDto.onHoldCount`, `StatusFilter.ON_HOLD`, and the `/bookings/*` endpoints. The new app ignores unknown keys, so removing is safe for it; the old app is already incompatible (role rename).
4. **Rollback is a database snapshot, not a redeploy.** `V6` renames a role and `V13` drops a table; the previous image cannot run against the migrated schema. So an RDS snapshot before the deploy is a hard requirement.
5. **Release order:** secrets first, then deploy, then the APK, in one sitting, with friends told beforehand (their installed app stops working at the moment of the deploy). The APK commit goes to `main` after the deploy and does not trigger a deploy (path filters).
6. **Test policy (user request):** per task run only the tests it adds or changes; whole suites run once in Task 4. Always read the `BUILD` verdict.

## Global Constraints

- Branch `customer-orders` until the merge in Task 5; no Co-Authored-By or "Generated with Claude" lines in commits or the PR body (user rule).
- Never edit an applied migration; `V13` is new. `V5` stays in the tree (it created the table that `V13` drops).
- No outward action without the user's explicit yes at that step: creating the PR, setting a GitHub secret, merging, creating accounts on production, pushing to `main`.
- The live URL is `https://sareemarket.duckdns.org`; the production database is the shared RDS `saree` database (credentials only in GitHub secrets, never in files or logs).
- Use `perl -pi -e` for bulk edits, not macOS `sed -i`. Paths: backend under `backend/src/`, Android under `android/app/src/`.

## Review Focus

1. `V13` on production-shaped data: pending and accepted bookings on several sarees, `ON_HOLD` items with quantity 0 and above 0, `SOLD_OUT` items, customers who were resellers: after migrating, no `ON_HOLD`, formerly held sarees have `quantity ≥ 1` and `AVAILABLE`, no `booking_request`, other rows untouched, Flyway history shows V13 (Task 1).
2. The new app against the trimmed API: catalog, collection and item responses decode with the removed fields gone, and no screen or worker still calls `/bookings/*` (Tasks 1, 2).
3. Nothing scheduled or injected still points at the dropped table: the application starts on a migrated database with no bean errors (Task 1).
4. The release order and what happens if a step fails midway: secrets set but deploy failed; deploy done but smoke test failed; APK pushed before the deploy (Task 5).
5. The rewritten brief and `CLAUDE.md` describe what was actually built (UPI proof, 2-hour hold, quantity, roles, packages) and no longer promise "no orders, no payments" (Task 3).

---

### Task 1: Backend — remove bookings, migration V13

**Files:**
- Create: `main/resources/db/migration/V13__drop_bookings.sql`; `test/java/com/sareemarket/MigrationV13Test.java`
- Delete: `main/java/com/sareemarket/booking/` (all), `main/java/com/sareemarket/item/event/ItemSoldOut.java`, `.../item/event/CollectionSoldOut.java` (only the booking package listened to them), `test/java/com/sareemarket/booking/` (all)
- Modify: `item/ItemStatus.java` (drop `ON_HOLD`), `item/ItemService.java` (drop the ON_HOLD rules, the `openRequestCounts` query, the sold-out event publishing), `item/ItemRepository.java` (the bulk update no longer excludes `ON_HOLD`), `item/dto/ItemDto.java` (drop `openRequests` and `withOpenRequests`), `collection/CollectionService.java` (drop the open-request fill-in for the owner), `collection/dto/CollectionSummaryDto.java` and `collection/SareeCollectionRepository.java` (drop `onHoldCount`), `catalog/CatalogService.java` (drop `myBooking`, `onHoldCount` and the `ON_HOLD` filter branch), `catalog/dto/ItemDetailDto.java` (drop `myBooking` and `MyBookingDto`), `catalog/dto/CatalogCollectionDto.java` (drop `onHoldCount`), `catalog/StatusFilter.java` (drop `ON_HOLD`); tests that mention bookings or `ON_HOLD` (`CatalogIntegrationTest`, `CollectionIntegrationTest`, `ItemQuantityIntegrationTest`, `IntegrationTestBase` helpers) updated to the new shapes.

**Interfaces:**
- `V13`: `UPDATE item SET status = 'AVAILABLE', quantity = GREATEST(quantity, 1) WHERE status = 'ON_HOLD'`; `DROP TABLE booking_request`; drop the item status check (look its name up in `pg_constraint`, as `V6` did, do not guess) and add `item_status_check CHECK (status IN ('AVAILABLE','SOLD_OUT'))`.
- `MigrationV13Test` builds a **separate** PostgreSQL Testcontainers database, runs Flyway to target `12` (`Flyway.configure().dataSource(..).target("12")`), inserts production-shaped rows (a seller, two customers (role `CUSTOMER`; V6 has already run at version 12), a collection; items `AVAILABLE`, `SOLD_OUT`, `ON_HOLD` with quantity 0 and with quantity 3; `booking_request` rows `PENDING`, `ACCEPTED`, `SOLD`), then migrates to latest and asserts.

- [ ] **Step 1: Write the failing migration test** `MigrationV13Test`: `heldSareesComeBackOnSaleWithAtLeastOnePiece` (the quantity-0 held item ends `AVAILABLE` with quantity 1; the quantity-3 one keeps 3), `soldOutAndAvailableItemsAreUntouched`, `bookingTableIsGone` (`to_regclass('booking_request')` is null), `itemStatusNowRejectsOnHold` (an `UPDATE ... SET status='ON_HOLD'` fails with a check violation), `otherTablesKeepTheirRows` (users, collections, cart, orders row counts unchanged), `flywayHistoryEndsAtV13`.
- [ ] **Step 2:** `cd backend && ./mvnw test -Dtest=MigrationV13Test` → FAIL (no V13).
- [ ] **Step 3: Write `V13__drop_bookings.sql`** exactly as in Interfaces.
- [ ] **Step 4:** run `MigrationV13Test` → PASS.
- [ ] **Step 5: Delete the booking package, events and tests; trim the files in the list.** Order: delete → `./mvnw compile` and fix each compile error by removing the dead branch (do not leave stubs) → `./mvnw test-compile`.
- [ ] **Step 6:** `./mvnw test -Dtest='MigrationV13Test,CatalogIntegrationTest,CollectionIntegrationTest,ItemQuantityIntegrationTest,ItemUpdateKeepsQuantityTest,SmokeTest'` → PASS; then start the app (`./mvnw spring-boot:run`, local profile) against the **local** database, which holds real test data through V12, and `curl` `GET /api/v1/health` → 200 and `GET /api/v1/bookings/summary` with a customer token → 404. Stop the app.
- [ ] **Step 7: Commit** `Backend: remove bookings (V13)`.

---

### Task 2: Android — remove bookings and ON_HOLD

**Files:**
- Delete: `data/BookingApi.kt`, `data/BookingModels.kt`, `data/BookingRepository.kt`, `ui/reseller/BookingsScreen.kt`, `ui/reseller/BookingsViewModel.kt`; tests `test/.../data/BookingFakes.kt`, `BookingRepositoryTest.kt`, `test/.../ui/BookingsViewModelTest.kt`, `androidTest/.../ui/BookingsScreenTest.kt`
- Modify: `di/NetworkModule.kt` (the `bookingApi` provider), `ui/AppViewModel.kt` (the `bookings.reset()` on log out), `data/CollectionModels.kt` (`ItemStatus` loses `ON_HOLD`; `Item.openRequests` and `CollectionSummary.onHoldCount` go), `data/CatalogModels.kt` (`ItemDetail.myBooking`, `MyBooking`), `ui/seller/CollectionDetailViewModel.kt` and `CollectionDetailScreen.kt` (the `ON_HOLD` filter chip, held pill/label, "Booking in progress" text, the "N requests" chip), `ui/components/StatusPill.kt` (`ItemStatusPill` loses its `ON_HOLD` case), `ui/seller/SellerHomeScreen.kt` (the "N on hold" line on collection cards), `alerts/NewCollectionAlerts.kt` (keep only the `LEGACY_BOOKING_WORK_NAME` cancel; fix comments), `ui/theme/Color.kt` (keep `OnHold*`: `OrderStatusPill` uses it; rename nothing), tests that build `Item(...)` or `CollectionSummary(...)` positionally or mention `ON_HOLD` (`AvailabilityControlsTest`, `CollectionDetailViewModelTest`, `CollectionRepositoryTest`, `CatalogRepositoryTest`, `SellerHomeScreenTest`, `SellerQuantityUiTest`, `FollowAndAlertsScreensTest`, fakes).

**Interfaces:** `enum class ItemStatus { AVAILABLE, SOLD_OUT }`. After this task `grep -rin "booking\|ON_HOLD\|openRequests\|onHoldCount\|myBooking" android/app/src` finds only the legacy work-name constant, the `Color.kt` token name `OnHold`, and test names that say so on purpose.

- [ ] **Step 1: Write failing tests where behaviour changes:** `CollectionDetailViewModelTest.filterChipsAreAllAvailableAndSoldOut` (no `ON_HOLD` filter exists; `ItemFilter` has exactly three values), `CatalogRepositoryTest.anItemDetailWithoutBookingFieldsDecodes`, `CollectionRepositoryTest.aSummaryWithoutOnHoldCountDecodes`; UI: `AvailabilityControlsTest` loses the held-card tests and gains `noOnHoldChipAnywhere`.
- [ ] **Step 2:** `cd android && ./gradlew testDebugUnitTest --tests '*CollectionDetailViewModelTest' --tests '*CatalogRepositoryTest' --tests '*CollectionRepositoryTest'` → FAIL (compile or assertion).
- [ ] **Step 3: Delete and trim** the files above; fix each compile error by removing the dead branch, not by stubbing.
- [ ] **Step 4:** `./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`; the three unit test classes → PASS; run the `grep` above and fix leftovers.
- [ ] **Step 5: Commit** `Android: remove bookings and ON_HOLD`.

---

### Task 3: Rewrite the brief and CLAUDE.md

**Files:**
- Modify: `docs/MVP_BRIEF.md`, `CLAUDE.md`
- Modify (light): `docs/superpowers/specs/2026-10-28-customer-orders-redesign-design.md` (status line: "built and shipped on <date>"; slice 5 notes)

**Interfaces / content decisions:**
- `docs/MVP_BRIEF.md`: Goal and Roles now **Seller** and **Customer** (resellers are customers), plus the seeded admin; Core concepts add quantity, payment hold (2 h), order, UTR, address; Data model lists the real tables (`app_user`, `seller_profile` with `upi_id`/`shipping_fee`, `collection`, `item` with `quantity`, `seller_follow`, `notification`, `address`, `wishlist_entry`, `cart_line`, `payment_hold`/`hold_line`, `customer_order`/`order_line`/`order_status_history`); Features: auth, seller collections and quantity, customer catalog and search (unchanged), image search (unchanged), follow and notifications (unchanged), **wishlist and cart**, **checkout, payment hold and UPI payment with UTR**, **orders: seller confirms, packs, ships, delivers; customer cancels or resubmits**, admin (unchanged); API outline gets the cart/holds/orders/addresses/wishlist routes as built; Screens get the customer tabs (Catalog, Wishlist, Cart, Orders, Profile) and seller tabs (Collections, Orders, Add, Shop, Profile); delete the "Add-on: booking requests" section; **Out of scope** becomes: payment gateway and automatic payment verification (UTR is checked by the seller), refunds, courier integration, returns and exchanges, ratings and reviews, coupons and tax invoices, seller buying on the same account, push notifications (FCM), WhatsApp API, SMS/OTP, AWS/S3, message broker, AI features, admin approval of sellers; **Done means** is rewritten around the order flow (a customer pays two sellers in one cart and each seller ships their own order).
- `CLAUDE.md`: first paragraph (no longer "no payments, no orders, no external services": say orders with manual UPI payment, no payment gateway); Roles wording (Seller / Customer); backend package list gains `address`, `wishlist`, `cart`, `order` and loses nothing (`booking` was never listed); Android conventions: "Seller and Customer flows chosen at login by role", notification line mentions order alerts; the "Out of scope" sentence in Working style stays; admin credentials line unchanged.

- [ ] **Step 1:** write the new brief and CLAUDE.md edits to the content decisions above (read both files first; keep their existing structure and tone; delete, don't comment out).
- [ ] **Step 2: Check them against the code:** `grep -n "reseller\|Reseller\|booking\|Booking\|ON_HOLD" docs/MVP_BRIEF.md CLAUDE.md` finds nothing except deliberate historical notes (say so in a one-line comment where kept); every route named in the API outline exists (`grep -rn "Mapping(\"" backend/src/main/java/com/sareemarket/{cart,wishlist,address,order}`), every table named exists in `backend/src/main/resources/db/migration`.
- [ ] **Step 3: Commit** `Docs: brief and CLAUDE.md for customer orders`.

---

### Task 4: Whole suites and a rehearsal on realistic data

**Files:** none new; fix what the runs show.

- [ ] **Step 1:** `cd backend && ./mvnw clean verify` → all pass; record the count (expect 338 − removed booking tests + the V13 tests).
- [ ] **Step 2:** `cd android && ./gradlew testDebugUnitTest --rerun` → all pass (count the XML results; read `BUILD`).
- [ ] **Step 3:** emulator up, `./gradlew connectedDebugAndroidTest` → all pass; if only the first alphabetical classes fail with "No compose hierarchies found", rerun those classes one by one (known flake) and record it.
- [ ] **Step 4: Rehearse the migration on the local database** that has real test data through V12: take a dump first (`docker compose exec -T postgres pg_dump -U saree saree > $SCRATCH/pre-v13.sql`), start the backend so Flyway applies V13, then check by SQL: no `ON_HOLD`, `booking_request` gone, counts of `app_user`, `item`, `customer_order`, `cart_line` unchanged. Keep the dump until the release is done.
- [ ] **Step 5: Walk through on the emulator** against that backend: log in as seller `9876500001`, open Collections (no on-hold chip), Orders, Shop; log in as customer `9555500011`, open Catalog, an item, Cart, Orders; confirm nothing mentions bookings and nothing crashes. Install with `./gradlew installDebug`.
- [ ] **Step 6:** dispatch the whole-slice review (fresh reviewer, most capable model) with the Review Focus; fix Critical and Important findings with a failing test first; ledger the minors. Commit `Slice 5 verified`.

---

### Task 5: Release (each outward step waits for the user's yes)

**Files:** `releases/saree-market-debug.apk` (replaced after the deploy); nothing else in the repo.

**Before anything:** show the user this checklist and wait for their confirmation of the two things only they can do or decide:
1. **RDS snapshot** of the production database (AWS console or `aws rds create-db-snapshot`), taken and visible as `available`. This is the rollback.
2. **Friends warned**: their current app stops working the moment the deploy finishes; they will need the new APK.

- [ ] **Step 1 (ask first): open the PR** `customer-orders` → `main` with `gh pr create`; body: what changed in five lines, the migration warning (V6 renames the role, V13 drops bookings), the rollback note; no attribution lines. Wait for the PR's **Backend CI** run to pass (`gh pr checks --watch`).
- [ ] **Step 2 (ask first): rotate `JWT_SECRET`.** Generate 48 random bytes (`openssl rand -base64 48`), set it with `gh secret set JWT_SECRET` (read from stdin; never print or store it). Effect: every existing login becomes a 401 and the app returns to the login screen.
- [ ] **Step 3 (ask first): merge** (`gh pr merge --merge`, keep history). Watch the chain: `gh run list`, then `gh run watch` on Backend CI and Deploy. Expect: image pushed, `docker compose up` over SSH, health check `https://sareemarket.duckdns.org/api/v1/health` green. If the health check fails, read the deploy log's tail of the backend container; **do not redeploy the old image**; decide with the user between a forward fix and restoring the snapshot.
- [ ] **Step 4 (ask first): smoke test production** with two throwaway accounts named `ZZ Smoke Seller` / `ZZ Smoke Customer` (phones in a reserved range, e.g. `9000099901/2`): register both, seller sets UPI and fee and uploads one photo with quantity 2, customer follows, adds to cart, checks out, pays with a fresh UTR, seller confirms, packs, ships, delivers; read each response. Afterwards disable both accounts with the admin endpoints and note their ids in the ledger (accounts cannot be deleted).
- [ ] **Step 5: Build the release APK:** `cd android && ./gradlew assembleDebug -PapiBaseUrl=https://sareemarket.duckdns.org`; verify the URL is inside the dex (`unzip -p … 'classes*.dex' | strings | grep sareemarket.duckdns.org`) and that no `10.0.2.2` base URL is; copy to `releases/saree-market-debug.apk`.
- [ ] **Step 6 (ask first): commit and push the APK to `main`** (`Debug APK for customer orders`); confirm the push did not start a deploy (`gh run list -L 3`: only path-filtered runs). Give the user the link to the file and the message to send friends: install the new APK over the old one, log in again, sellers add a UPI ID first (Shop tab).
- [ ] **Step 7:** update memory (`customer-orders-redesign.md`, `production-deploy-and-backlog.md`: live since the date, what changed, snapshot id, the throwaway accounts, FCM and payment-gateway follow-ups), delete the local branch only if the user asks.

---

## Hand-offs

- Follow-ups outside this plan: payment gateway (Razorpay) and automatic verification; FCM so sellers hear about orders at once; removing the `item.status` mark in favour of quantity alone; open-ended minors listed in the slice ledgers.
- If Step 3 fails after the migration ran, the database is already on V13: restore the snapshot rather than patching by hand, and keep the failed run's logs.
