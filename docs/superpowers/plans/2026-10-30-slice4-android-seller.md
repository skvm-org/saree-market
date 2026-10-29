# Slice 4 — Android Seller App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A seller can set a UPI ID and shipping fee, give each saree a quantity, and work orders from the app: see who paid, confirm or reject the payment, pack, ship with tracking, mark delivered, or cancel, and hear about new orders in the background.

**Architecture:** Same single-activity Compose app. The seller shell's **Requests** tab becomes an **Orders** tab backed by the existing `OrderRepository` (seller routes added to `OrderApi`). The shop-details screen gains UPI and shipping fields. Quantity joins the upload form and the photo editor. One background worker (`OrderAlertsWorker`) replaces `BookingAlertsWorker` for both roles. One small backend change lets a collection upload set the starting quantity.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Navigation Compose, Hilt, Retrofit/kotlinx.serialization, WorkManager, DataStore, JUnit4 + coroutines-test, `createComposeRule`; backend Spring Boot + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-28-customer-orders-redesign-design.md` (sections 5, 6, 8). Backend routes were built in slices 1 and 2 (`backend/.../order`, `seller`); Task 1 adds the one missing field.

## Rulings carried into this plan

1. **Orders replace Requests.** `RequestsScreen`, `RequestsViewModel` and their tests are deleted in Task 6; the seller bottom bar keeps five tabs (Collections · Orders · Add · Shop · Profile). The held-card "See Requests" action in the collection screen is removed; `ON_HOLD` pills stay (passive) until slice 5 deletes bookings.
2. **Filters:** five selectable tiles (To confirm · To pack · To ship · Shipped · All) in a horizontally scrolling row, counts from `GET /orders/summary`. `PAYMENT_REJECTED` and finished orders appear under All only.
3. **Confirming payment is deliberate:** the button opens a dialog showing the exact amount, the customer's UTR and "Check your bank or UPI app first"; the screenshot is a hint, never proof.
4. **Upload quantity:** a "Pieces of each saree" field (default 1) on the new-collection form is applied to every new photo; adding photos later uses 1. Editing a single photo's quantity is in the photo editor. No bulk quantity editor in this slice.
5. **Alerts:** one `OrderAlertsWorker` for both roles. Sellers hear "N new orders to confirm"; customers and sellers hear "An order was updated" when their unread count rises. `BookingAlertsWorker`, `AlertPlanner.bookingText` and the booking marks are deleted, and the old unique work `booking-alerts` is cancelled on start so updated installs never try to run a class that no longer exists.
6. **Test policy (user request):** per task, run only the tests that task adds or changes (`./gradlew testDebugUnitTest --tests <class>`, `./mvnw test -Dtest=<class>` for the backend task, and `./gradlew compileDebugAndroidTestKotlin` when UI tests are added); always read the `BUILD` verdict, never grep only for `e:` lines. Whole backend and Android suites run once, in Task 8.

## Global Constraints

- Branch `customer-orders`; never push to `main` until slice 5. Commit per task; no Co-Authored-By lines.
- Android conventions from `CLAUDE.md`: one ViewModel per screen exposing `StateFlow`, touch targets ≥ 48 dp (`MinTouchTarget`), DESIGN.md tokens, system back works, Photo Picker for photos, no WebView.
- Money is shown through `formatRupees`. UPI ID rule (matches the server): empty, or `[A-Za-z0-9._-]{2,64}@[A-Za-z]{2,32}`. Shipping fee: whole rupees 0–10000. Quantity: whole number 0–10000. Seller cancel reason: required, ≤ 200 characters. Courier and tracking: optional, ≤ 60 characters each.
- Server contract: `PUT /seller/profile` leaves `upiId`/`shippingFee` unchanged when absent and clears the UPI ID when it is `""`; the app always sends both.
- Paths are under `android/app/src/main/java/com/sareemarket/app/` (main), `android/app/src/test/java/com/sareemarket/app/` (unit), `android/app/src/androidTest/java/com/sareemarket/app/` (UI) unless stated. Backend paths are under `backend/src/`.
- Use `perl -pi -e` for bulk edits, not macOS `sed -i`.

## Review Focus

1. A double tap or a stale list on Confirm, Reject, Pack, Ship, Deliver, Cancel: one request only, and a `409 BAD_STATE` from the server shows its message and reloads the list (Task 5).
2. Reject and cancel need a reason; ship works with no courier details; over-long input is trimmed or refused before sending (Task 5).
3. The seller sees the customer's name and phone only on their own orders, with working Call and WhatsApp buttons; a customer's order never shows seller-only fields (Tasks 2, 5).
4. UPI and shipping validation matches the server, clearing the UPI ID works, and a seller without a UPI ID is told customers can't order yet (Tasks 3, 5).
5. Quantity edits at 0, 1, 10000 and 10001; an old `ON_HOLD` card still renders without the Requests link; an updated install does not crash on the removed booking worker (Tasks 4, 6, 7).

---

### Task 1: Backend — starting quantity on upload

**Files:**
- Modify: `main/java/com/sareemarket/collection/dto/CreateCollectionForm.java`, `collection/CollectionController.java` (`addPhotos`), `collection/CollectionService.java` (`create`, `addPhotos`, `saveWithPhotos`), `item/ItemService.java` (`store`)
- Test: `test/java/com/sareemarket/collection/CollectionQuantityIntegrationTest.java` (new)

**Interfaces:**
- Produces: optional multipart field `quantity` (whole number 0–10000, default 1) on `POST /collections` and `POST /collections/{id}/items`; `ItemService.store(Long collectionId, Long sellerId, List<ProcessedImage> images, int quantity, List<String> storedKeys)`; `CreateCollectionForm` gains `@Min(0) @Max(10000) Integer quantity` as its **last** component (null means 1).

- [ ] **Step 1: Write failing tests** (`CollectionQuantityIntegrationTest`, using the upload helpers from `CollectionIntegrationTest`): `newCollectionPhotosGetTheGivenQuantity` (upload 2 photos with `quantity=7` → every item's `quantity == 7` in the detail response), `quantityDefaultsToOne`, `addedPhotosGetTheGivenQuantity`, `zeroQuantityIsAllowed`, `badQuantityIs400` (`-1`, `10001`, `abc` → 400 and no collection or photo stored).
- [ ] **Step 2:** `cd backend && ./mvnw test -Dtest=CollectionQuantityIntegrationTest` → FAIL.
- [ ] **Step 3: Implement** the field, validation and plumbing to `Item.setQuantity` (set before `saveAll`). `addPhotos` reads `@RequestParam(name = "quantity", required = false) Integer quantity` and rejects out-of-range with the same `VALIDATION_ERROR` shape as the item PATCH (`fields.quantity`).
- [ ] **Step 4:** the new test class, plus `CollectionIntegrationTest` and `CollectionServiceTest` (they call the changed signatures) → PASS.
- [ ] **Step 5: Commit** `Starting quantity when photos are uploaded`.

---

### Task 2: Android data layer for the seller side

**Files:**
- Modify: `data/OrderModels.kt` (`Order.customer`), `data/OrderApi.kt`, `data/OrderRepository.kt`, `data/AuthModels.kt` (`ShopDetails`), `data/CollectionModels.kt` (`Item.quantity`), `data/CollectionRepository.kt` (`create`, `addPhotos`, `updateItem`), `data/CollectionsApi.kt` (`create`, `addPhotos` get a `quantity` part), `ui/components/SareeImage.kt` (add `orderProofUrl(orderId: Long): String`)
- Test: `data/OrderRepositoryTest.kt` (extend), `data/OrderModelsTest.kt` (extend), `data/CollectionRepositoryTest.kt` (extend), `data/OrderFakes.kt` / `data/CollectionFakes.kt` (extend fakes)

**Interfaces:**
- Models: `OrderParty(name: String, phone: String)`; `Order` gains `val customer: OrderParty? = null` (set only in the seller's view); `ShopDetails` gains `val upiId: String? = null, val shippingFee: Int = 0`; `Item` gains `val quantity: Int = 1`; `RejectBody(reason: String)`, `ShipBody(courier: String?, trackingNumber: String?)`, `CancelBody(reason: String?)`.
- `OrderApi` additions: `GET api/v1/orders/received?state=&page=` → `OrderPage`; `POST api/v1/orders/{id}/confirm-payment`, `pack`, `deliver` → `Order`; `POST .../reject-payment` (`RejectBody`) → `Order`; `POST .../ship` (`ShipBody`) → `Order`; `POST .../cancel` with a `CancelBody` → `Order` as `cancelWithReason(@Path id, @Body body)`. The existing body-less `cancel(id)` stays for customers.
- `OrderRepository` additions (all `Result<…>`, all refresh the summary on success): `received(state: OrderStatus?, page: Int): Result<OrderPage>` (no refresh), `confirmPayment(id)`, `rejectPayment(id, reason)`, `pack(id)`, `ship(id, courier: String?, trackingNumber: String?)` (blank values sent as null, trimmed), `deliver(id)`, `cancelAsSeller(id, reason)`.
- `CollectionRepository.create(form, photoUris, quantity: Int, onProgress)` sends `quantity` as a text part; `addPhotos` sends `quantity = 1`; `updateItem(id, colour, priceOverride, quantity: Int)` always sends `quantity` too. Existing callers are updated in Task 4.
- `orderProofUrl(id)` = `BuildConfig.API_BASE_URL + "api/v1/orders/$id/proof"` built the way `imageUrl` builds its URL.

- [ ] **Step 1: Write failing tests.** `OrderRepositoryTest`: `receivedSendsTheStateAndPage`, `confirmPackAndDeliverCallTheirRoutesAndRefreshTheSummary`, `rejectSendsTheReason`, `shipTrimsAndSendsNullForBlankFields`, `sellerCancelSendsTheReason`, `aBadStateAnswerKeepsTheServersMessage` (`409 BAD_STATE`). `OrderModelsTest`: `decodesTheCustomerPartyInTheSellersView`. `CollectionRepositoryTest`: `createSendsTheQuantityPart`, `updateItemSendsTheQuantity`. Fakes record the new calls like the existing ones.
- [ ] **Step 2:** `cd android && ./gradlew testDebugUnitTest --tests '*OrderRepositoryTest' --tests '*OrderModelsTest' --tests '*CollectionRepositoryTest'` → FAIL (compile).
- [ ] **Step 3: Implement** to the interfaces above.
- [ ] **Step 4:** same command → `BUILD SUCCESSFUL`; `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` → `BUILD SUCCESSFUL` (callers of `create`/`updateItem` in the ViewModels need the new parameters: pass `1` / the item's current quantity until Task 4 changes them).
- [ ] **Step 5: Commit** `Android: seller order data layer and quantity fields`.

---

### Task 3: UPI ID and shipping fee in the shop details

**Files:**
- Modify: `ui/profile/ProfileViewModels.kt` (`ShopDetailsUiState`, `ShopDetailsViewModel`), `ui/profile/ProfileScreens.kt` (`ShopDetailsContent`)
- Test: `ui/ShopDetailsViewModelTest.kt` (extend or create beside the existing profile ViewModel tests), `androidTest/.../ui/ProfileScreensTest.kt` (extend)

**Interfaces:**
- Consumes: `ShopDetails.upiId/shippingFee` (Task 2).
- Produces: `ShopDetailsUiState` gains `upiId: String`, `shippingFee: String` (digits), included in `current`/`dirty`; field keys `FIELD_UPI = "upiId"`, `FIELD_FEE = "shippingFee"`; `ShopDetailsViewModel.onUpiId(String)` (trimmed, ≤ 100 chars, lower-cased not required) and `onShippingFee(String)` (digits only, ≤ 5). Validation: UPI empty or matching the Global Constraints regex (message "Enter your UPI ID, like name@bank, or leave empty"); fee empty (means 0) or 0–10000 (message "Enter a fee from 0 to 10,000"). Save sends `ShopDetails(..., upiId = upi.ifEmpty { "" } as given, shippingFee = fee.toIntOrNull() ?: 0)`: an empty UPI is sent as `""` so the server clears it.
- UI: two fields under the contact details, tags `shop-upi` and `shop-fee`, helper text "Customers pay this ID. Without it they can't order from you." and "Added to each order from you. ₹0 means free shipping."

- [ ] **Step 1: Write failing tests.** Unit: `loadsUpiAndFee`, `savingSendsUpiAndFee`, `emptyUpiIsSentEmptyToClearIt`, `badUpiIsRejectedLocally` (`abc`, `a@b`, `x y@okaxis`), `feeAbove10000IsRejected`, `emptyFeeMeansZero`, `changingOnlyTheUpiMakesTheFormDirty`. UI: `shows the upi and fee fields with their helper text`, `shows field errors under them`.
- [ ] **Step 2–4:** unit command for `*ShopDetails*` classes → FAIL, implement, → `BUILD SUCCESSFUL`; `compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** `Android: UPI ID and shipping fee in shop details`.

---

### Task 4: Quantity on upload and on each photo

**Files:**
- Modify: `ui/seller/CollectionFormState.kt` (`CollectionFields.quantity`), `ui/seller/CollectionFieldsForm.kt`, `ui/seller/NewCollectionViewModel.kt` / `NewCollectionScreen.kt`, `ui/seller/CollectionDetailViewModel.kt` (`saveItem`), `ui/seller/CollectionDetailScreen.kt` (`EditItemSheet`, item card, remove "See Requests"), `ui/seller/SellerHomeScreen.kt` only if it references requests
- Test: `ui/NewCollectionViewModelTest.kt`, `ui/CollectionDetailViewModelTest.kt` (extend), `androidTest/.../ui/NewCollectionScreenTest.kt`, `androidTest/.../ui/AvailabilityControlsTest.kt` (extend)

**Interfaces:**
- `CollectionFields` gains `val quantity: String = "1"`; `validate()` adds `quantity` error "Enter 0 to 10,000" unless it is digits 0–10000; `toForm()` unchanged; the create path passes `quantity.toInt()` to `CollectionRepository.create`. The quantity field is shown only on the **new** collection form (tag `quantity`, label "Pieces of each saree", helper "You can change each saree's count later"); the edit-collection sheet does not show it.
- `CollectionDetailViewModel.saveItem(itemId, colour, priceOverride, quantity: String, onSaved)` validates quantity 0–10000 (error key `quantity`) and passes it to `updateItem`.
- `EditItemSheet` gains a quantity field (tag `item-quantity`, prefilled from `item.quantity`). The item card in the collection grid shows "N in stock" (tag `stock-<id>`) or "Out of stock" when `quantity == 0` or status is `SOLD_OUT`.
- The held-item card's "See Requests" action and its navigation callback are removed; the amber `ON_HOLD` pill stays.

- [ ] **Step 1: Write failing tests.** Unit: `newCollectionSendsTheQuantity`, `quantityDefaultsToOne`, `badQuantityBlocksTheCreateForm` (`""`, `10001`), `saveItemSendsTheQuantity`, `saveItemRejectsBadQuantity`. UI: `new collection form shows pieces of each saree`, `edit sheet shows and sends the quantity`, `item card shows stock count or out of stock`, `held card has no requests link but still shows on hold`.
- [ ] **Step 2–4:** as before (`*NewCollectionViewModelTest`, `*CollectionDetailViewModelTest`), then `compileDebugKotlin compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** `Android: quantity on upload and on each saree`.

---

### Task 5: Seller Orders screen

**Files:**
- Create: `ui/seller/SellerOrdersViewModel.kt`, `ui/seller/SellerOrdersScreen.kt`, `ui/seller/OrderActionDialogs.kt`
- Test: `ui/SellerOrdersViewModelTest.kt`, `androidTest/.../ui/SellerOrdersScreenTest.kt`

**Interfaces:**
- Consumes: `OrderRepository` seller methods and `summary` (Task 2), `OrderStatusPill` (slice 3), `ContactButtons`, `PhotoViewer`, `orderProofUrl`, `timeAgo`, `timeRemaining`.
- Produces: `enum SellerOrderFilter(val state: OrderStatus?) { TO_CONFIRM(PAYMENT_SUBMITTED), TO_PACK(CONFIRMED), TO_SHIP(PACKED), SHIPPED(SHIPPED), ALL(null) }`; `SellerOrdersState(filter = TO_CONFIRM, items, nextPage, hasMore, loading, loadingMore, refreshing, error, busyId: Long?, summary: OrderSummary)`; `SellerOrdersViewModel(OrderRepository)` with `selectFilter`, `reload`, `pullRefresh`, `loadMore`, `confirmPayment(id)`, `rejectPayment(id, reason)`, `pack(id)`, `ship(id, courier, tracking)`, `deliver(id)`, `cancel(id, reason)`. Every action ignores a second call while `busyId != null`, sets `error` to the server's message on failure, and reloads page 0 and the summary afterwards (the order usually moves to another tile). Opening the list calls `markSeen()` after page 0 loads when the summary or a row is unread.
- `SellerOrdersScreen(onOpenSettings: () -> Unit, viewModel)`: a title "Orders"; a banner (tag `no-upi-banner`) "Add your UPI ID so customers can pay you" with button **Add UPI ID** (tag `add-upi`, calls `onOpenSettings`) when the shop has no UPI ID (the screen reads it from `AuthRepository.shopDetails()` through the shell ViewModel's `shop` state, passed in as `hasUpiId: Boolean`); the five filter tiles (tags `tile-to-confirm`, `tile-to-pack`, `tile-to-ship`, `tile-shipped`, `tile-all`, counts from `summary`); the list. Each order card (tag `order-<id>`): customer name and phone with Call/WhatsApp (`ContactButtons`), item titles summary, address (city and PIN), `₹grandTotal`, status pill, time ago, courier/tracking when set, UTR line "UTR 412345678901" and a **View screenshot** link (tag `proof-<id>`, only when `hasProof`) opening `PhotoViewer(orderProofUrl(id))`.
- Actions by status (tags `act-<action>-<id>`): `PAYMENT_SUBMITTED` → **Payment received** (opens `ConfirmPaymentDialog`: "Did ₹<grandTotal> arrive from UTR <utr>? Check your bank or UPI app first."), **Not found** (opens `ReasonDialog`, reason required ≤ 200, tag `reason`), **Cancel order** (`ReasonDialog`); `PAYMENT_REJECTED` → text "Waiting for the customer's corrected UTR, <timeRemaining>" and **Cancel order**; `CONFIRMED` → **Mark packed**, **Cancel order**; `PACKED` → **Ship** (opens `ShipDialog` with optional Courier and Tracking number, tags `courier`, `tracking`, each ≤ 60), **Cancel order**; `SHIPPED` → **Mark delivered**; `DELIVERED`/`CANCELLED` → no actions (cancelled shows who and why).
- Empty states per filter ("Nothing waiting for payment confirmation", …, "No orders yet"), tag `orders-empty`.

- [ ] **Step 1: Write failing tests.** Unit: `opensOnToConfirmAndLoadsThatState`, `selectingATileLoadsItsState`, `allLoadsEveryState`, `confirmCallsTheRepositoryThenReloads`, `aSecondActionWhileBusyIsIgnored`, `badStateShowsTheServersMessageAndReloads`, `rejectNeedsAReason` (blank → not sent), `shipSendsOptionalFieldsTrimmed`, `cancelNeedsAReason`, `opensAndMarksSeenWhenUnread`, `loadMorePages`, `failureShowsTheMessage`. UI: `tiles show the counts and the selected one is marked`, `to confirm card shows customer utr and the three actions`, `confirm dialog states amount utr and the bank warning`, `reject dialog needs a reason`, `ship dialog accepts empty details`, `packed card offers ship`, `shipped card offers delivered`, `rejected card shows waiting text and only cancel`, `delivered card has no actions`, `call and whatsapp buttons are shown for the customer`, `no upi banner shows and calls the settings callback`, `empty state per tile`.
- [ ] **Step 2–4:** `*SellerOrdersViewModelTest` → FAIL → implement → `BUILD SUCCESSFUL`; `compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** `Android: seller orders screen`.

---

### Task 6: Seller shell — Orders tab replaces Requests

**Files:**
- Modify: `ui/seller/SellerBottomBar.kt`, `ui/seller/SellerNavHost.kt`, `ui/seller/SellerShellViewModel.kt`
- Delete: `ui/seller/RequestsScreen.kt`, `ui/seller/RequestsViewModel.kt`, `test/.../ui/RequestsViewModelTest.kt`, `androidTest/.../ui/RequestsScreenTest.kt`
- Test: `ui/SellerShellBadgeTest.kt` (new, pure function), `androidTest/.../ui/FollowAndAlertsScreensTest.kt` (replace the seller-bar test), `androidTest/.../ui/SellerBottomBarTest.kt` (new)

**Interfaces:**
- `SELLER_ORDERS = "orders"` replaces `SELLER_REQUESTS`; tab label "Orders" (icon `Icons.Filled.Inbox`), tag `seller-tab-orders`, badge tag `orders-badge` showing `pending` (99+ cap). `SellerBottomBar(current, pending: Long = 0, onSelect)` keeps its signature.
- `internal fun sellerBadge(summary: OrderSummary): Long = summary.toConfirm.toLong()`: the badge counts only orders that need the seller's action (not unread updates).
- `SellerShellViewModel(AuthRepository, OrderRepository, NewCollectionAlerts)`: `pending` from `orders.summary`; `refreshPending()` calls `orders.refreshSummary()`; `startAlerts()` unchanged name. `SellerNavHost` routes `SELLER_ORDERS` to `SellerOrdersScreen(onOpenSettings = { nav.navigate(SHOP) }, hasUpiId = shop?.upiId?.isNotBlank() == true)`; the bar and `showBar` use the new route; `BookingRepository` is no longer injected anywhere on the seller side.

- [ ] **Step 1: Write failing tests.** Unit: `theSellerBadgeCountsOrdersWaitingForConfirmation` (toConfirm 3, unread 9 → 3), `nothingToConfirmMeansNoBadge`. UI: `orders tab shows the waiting count and caps at 99+`, `tabs report where to go` (`home`, `orders`, `new`, `shop`, `profile`), `the old requests tab is gone`.
- [ ] **Step 2–4:** unit test → FAIL → implement and delete the Requests files → `./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`; then `grep -rn "Requests\(Screen\|ViewModel\)\|SELLER_REQUESTS" android/app/src` → no matches.
- [ ] **Step 5: Commit** `Android: seller Orders tab replaces Requests`.

---

### Task 7: Order alerts replace booking alerts

**Files:**
- Create: `alerts/OrderAlertsWorker.kt`
- Modify: `alerts/AlertPlanner.kt` (`orderText`), `alerts/AlertPrefs.kt` (`orderMarks`/`setOrderMarks`), `alerts/NewCollectionAlerts.kt` (`schedule`, `scheduleForSeller`, `showOrder`, `cancel`)
- Delete: `alerts/BookingAlertsWorker.kt`, the booking cases in `test/.../alerts/AlertPlannerTest.kt` (replaced by order cases)
- Test: `alerts/AlertPlannerTest.kt` (unit)

**Interfaces:**
- `AlertPlanner.orderText(lastToConfirm: Int, lastUnread: Int, now: OrderSummary): String?` — `now.toConfirm > lastToConfirm` → "1 new order to confirm" / "N new orders to confirm" (N = difference); else `now.unread > lastUnread` → "An order was updated"; else `null`.
- `AlertPrefs.orderMarks(): Pair<Int, Int>` (toConfirm, unread) and `setOrderMarks(toConfirm: Int, unread: Int)`; the old booking marks are removed from the class and from `clear()`'s keys.
- `OrderAlertsWorker` (`@HiltWorker`, `CoroutineWorker`): `orders.refreshSummary()` (failure → `Result.retry()`), `orderText` against `prefs.orderMarks()`, `alerts.showOrder(text)`, then `setOrderMarks` with the new counts (also when they fell).
- `NewCollectionAlerts`: `schedule()` (customer) enqueues `NewCollectionsWorker` and `OrderAlertsWorker` under unique names `new-collection-alerts` and `order-alerts`; `scheduleForSeller()` enqueues `OrderAlertsWorker` only; both first cancel the legacy unique work `booking-alerts`; `showOrder(text)` uses channel `orders` ("Orders", id 3) distinct from the collections and legacy booking channels; `cancel()` cancels `new-collection-alerts` and `order-alerts` (and the legacy name) and clears prefs.

- [ ] **Step 1: Write failing tests** (`AlertPlannerTest`): `announcesNewOrdersToConfirm` (one and many), `announcesAnUpdateWhenUnreadRises`, `staysQuietWhenNothingRose`, `staysQuietWhenCountsFell`, `newOrdersWinOverUpdates`.
- [ ] **Step 2–4:** `*AlertPlannerTest` → FAIL → implement → PASS; `./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`; `grep -rn "BookingAlertsWorker\|bookingText\|bookingMarks" android/app/src` → no matches.
- [ ] **Step 5: Commit** `Android: order alerts replace booking alerts`.

---

### Task 8: Whole suites, emulator walk-through, review, notes

**Files:** none new; fix what the suites reveal.

- [ ] **Step 1:** `cd backend && ./mvnw clean verify` → all pass; record the count (expect 329 + the Task 1 tests).
- [ ] **Step 2:** `cd android && ./gradlew testDebugUnitTest` → all pass (read `BUILD`; count the XML results). Update tests broken by the removed Requests and booking-alert classes; never delete coverage that still applies.
- [ ] **Step 3:** emulator up (`Medium_Phone_API_37.0`), `./gradlew connectedDebugAndroidTest` → all pass; if only the first few alphabetical classes fail with "No compose hierarchies found", rerun those classes one by one (known start-up flake) and record it.
- [ ] **Step 4: Walk through on the emulator** against the local backend: log in as seller `9876500001` / `password1`, open **Shop**, set UPI `lakshmi@okaxis` and fee `60`; open a collection, edit a saree's quantity; as customer `9555500011` (second login or API) put it in the cart and pay with a fresh 12-digit UTR; back as the seller open **Orders**: the card shows UTR and customer, confirm the payment, pack, ship with a courier and tracking, mark delivered; check the customer's order detail updates. Install with `./gradlew installDebug`.
- [ ] **Step 5:** dispatch the whole-slice review (fresh reviewer, most capable model) using the plan's Review Focus; fix Critical and Important findings with a failing test first; ledger minors.
- [ ] **Step 6:** update memory notes (`customer-orders-redesign.md`), commit `Slice 4 verified`, stop for review.

---

## Hand-offs

- Slice 5 deletes: `BookingApi/Repository/Models`, `BookingsScreen/ViewModel` (reseller), `ON_HOLD` handling and the passive amber pill in the seller screens, the backend `booking` package and V13 migration, then rewrites `docs/MVP_BRIEF.md`, rotates `JWT_SECRET`, deploys, and ships the APK.
- Not in this slice: bulk quantity editing, QR image for the UPI ID, order search, FCM push (still the backlog item that would make seller alerts instant).
