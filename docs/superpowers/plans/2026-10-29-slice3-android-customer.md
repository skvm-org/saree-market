# Slice 3 — Android Customer App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The reseller side of the Android app becomes the customer side: browse, wishlist, add to cart, check out into a payment hold, pay the seller by UPI and submit the UTR, and follow the order through to delivery.

**Architecture:** Same single-activity Compose app (MVVM, Hilt, Retrofit + kotlinx.serialization, StateFlow ViewModels, repositories over `apiCall`). New repositories (`CartRepository`, `WishlistRepository`, `AddressRepository`, `OrderRepository`) sit on two new Retrofit interfaces. The reseller shell (`ui/reseller/`) is reused and renamed in behaviour only: its tab set and item page change. The booking classes stay compiled but unreferenced until slice 5 removes them.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Navigation Compose, Hilt, Retrofit/OkHttp, kotlinx.serialization, Coil, DataStore, JUnit4 + coroutines-test for unit tests, `createComposeRule` for UI tests.

**Spec:** `docs/superpowers/specs/2026-10-28-customer-orders-redesign-design.md` (sections 5, 6, 8). Backend routes come from slices 1 and 2 (`backend/src/main/java/com/sareemarket/{address,wishlist,cart,order}`).

## Rulings carried into this plan

1. **Tabs:** Catalog · Wishlist · Cart · Orders · Profile. The old "My sellers" and "Alerts" tabs become non-tab routes (`sellers`, `alerts`) opened by two icon buttons (people, bell with unread badge) in a new header row above the catalog search field. The bottom bar stays visible on those two routes with no tab selected.
2. **No QR image:** the hold screen shows the seller's UPI ID with a copy button and a **Pay with UPI app** button that fires `upi://pay?pa=<id>&pn=<shop>&am=<total>&cu=INR`. No new dependency. If no UPI app answers, a snackbar says to copy the ID.
3. **Stored sessions:** a saved role of `RESELLER` is read as `CUSTOMER` (users who updated the app stay logged in; their old token is replaced at the slice 5 deploy by rotating `JWT_SECRET`).
4. **Alerts for orders** (system notifications) are out of this slice; the Orders tab shows an in-app badge from `GET /orders/summary`, polled every 60 s with the existing shell loop. Seller-side order alerts arrive in slice 4.
5. **Test policy for this slice (user request):** during a task run only the tests that task adds or changes (`./gradlew testDebugUnitTest --tests <class>`) and `./gradlew compileDebugAndroidTestKotlin` when UI tests are added. The whole unit and instrumented suites run once, in Task 10.

## Global Constraints

- Branch `customer-orders`; never push to `main` until slice 5. Commit per task, no Co-Authored-By lines.
- Android conventions from `CLAUDE.md`: one ViewModel per screen exposing `StateFlow`, touch targets ≥ 48 dp (`MinTouchTarget`), DESIGN.md tokens via `SareeTheme`/`Spacing`/`SareeColors`, system back works, edge-to-edge insets handled, Photo Picker for the screenshot, no WebView.
- Money is shown as `₹` + grouped digits through the existing formatter in `ui/Format.kt`; whole rupees only.
- New enums decoded from the server must tolerate old/new values: `OrderStatus` is decoded strictly (the backend only sends the seven values in the spec), so a `decodes every status the backend can send` test pins them.
- Use `perl -pi -e` for bulk edits, not macOS `sed -i`.
- Paths below are under `android/app/src/main/java/com/sareemarket/app/` (main), `android/app/src/test/java/com/sareemarket/app/` (unit) and `android/app/src/androidTest/java/com/sareemarket/app/` (UI) unless stated.

## Review Focus

1. A rejected payment: the order detail shows the seller's reason and a corrected-UTR form until the deadline, and hides the form after it (Task 8).
2. Cart edge cases: a line that is out of stock, a quantity above stock (`maxQuantity`), a seller that is not accepting orders, and an empty cart all render without crashing or offering "Pay" (Task 6).
3. Checkout failures keep the cart intact and show the server's message: `HOLD_LIMIT`, `TOTAL_TOO_LARGE`, network failure (Task 6).
4. Hold screen: only 12-digit UTRs enable "I have paid"; the countdown reaching zero disables payment and offers "Back to cart"; `HOLD_EXPIRED` and `UTR_IN_USE` errors show their message (Task 7).
5. A customer with a stored `RESELLER` session or a seller token never sees a crash from the role enum (Task 1).

---

### Task 1: Role CUSTOMER everywhere in the app

**Files:**
- Modify: `data/AuthModels.kt`, `data/SessionStore.kt:32`, `ui/home/HomeScreen.kt:15`, `ui/auth/RegisterScreen.kt:82`, `ui/profile/AccountRoutes.kt:35`
- Modify tests: `data/Fakes.kt` (`priya`, `session()`), every unit/UI test using `Role.RESELLER` (`RegisterViewModelTest`, `AuthRepositoryTest`, `AccountRepositoryTest`, `RegisterScreenTest`, `ProfileScreensTest`)
- Test: `data/SessionStoreRoleTest.kt` (new, unit)

**Interfaces:** Produces `enum class Role { SELLER, CUSTOMER, ADMIN }`; role string on the wire `"CUSTOMER"`; register choice label "I'm a Customer"; `Role.CUSTOMER.label()` = "Customer". `SessionStore` maps a persisted `"RESELLER"` to `Role.CUSTOMER`.

- [ ] **Step 1: Write the failing test** `SessionStoreRoleTest` (name: `legacyResellerRoleIsReadAsCustomer`, `unknownRoleLogsOut`) against the DataStore-backed store the way `AccountRepositoryTest` builds its store (use whichever test double or temp-file DataStore the existing store tests use); assert a stored `"RESELLER"` yields `Session.role == Role.CUSTOMER` and an unknown string yields no session.
- [ ] **Step 2:** `cd android && ./gradlew testDebugUnitTest --tests '*SessionStoreRoleTest'` → FAIL.
- [ ] **Step 3: Implement** the enum rename, the legacy mapping (`"RESELLER" -> CUSTOMER`) in the role read at `SessionStore.kt:32`, the labels/routing, then `perl -pi -e 's/Role\.RESELLER/Role.CUSTOMER/g; s/role-RESELLER/role-CUSTOMER/g'` over main and test trees.
- [ ] **Step 4:** `./gradlew testDebugUnitTest --tests '*SessionStoreRoleTest' --tests '*RegisterViewModelTest' --tests '*AuthRepositoryTest' --tests '*AccountRepositoryTest'` → PASS; `./gradlew compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `Android: reseller role becomes customer`.

---

### Task 2: Data layer for cart, wishlist, addresses, holds and orders

**Files:**
- Create: `data/CartModels.kt`, `data/OrderModels.kt`, `data/ShoppingApi.kt`, `data/OrderApi.kt`, `data/CartRepository.kt`, `data/WishlistRepository.kt`, `data/AddressRepository.kt`, `data/OrderRepository.kt`
- Modify: `data/CatalogModels.kt` (`CatalogItem`, `ItemDetail`), `di/NetworkModule.kt`, `ui/AppViewModel.kt` (reset on logout)
- Test: `data/ShoppingRepositoryTest.kt`, `data/OrderRepositoryTest.kt`, `data/OrderModelsTest.kt`; fakes in `data/OrderFakes.kt` (unit test tree, modelled on `BookingFakes.kt`)

**Interfaces — models (all `@Serializable`, `ignoreUnknownKeys` already on):**
- `CatalogItem` gains `val quantity: Int = 1, val inStock: Boolean = true`; `ItemDetail` gains `val quantity: Int = 1, val inStock: Boolean = true, val inWishlist: Boolean = false, val inCart: Int = 0` (defaults keep existing tests compiling).
- `Address(id: Long, name, phone, line1, line2: String?, city, state, pin, isDefault: Boolean)`; `AddressForm(name, phone, line1, line2: String?, city, state, pin, makeDefault: Boolean?)`.
- `CartLine(itemId: Long, collectionTitle, colour: String?, unitPrice: Int, quantity: Int, maxQuantity: Int, inStock: Boolean, lineTotal: Int)`; `CartSeller(sellerId: Long, shopName, acceptsOrders: Boolean, shippingFee: Int, lines: List<CartLine>, itemsTotal: Int, grandTotal: Int)`; `Cart(sellers: List<CartSeller>, lineCount: Int)`; `QuantityBody(quantity: Int)`.
- `OrderLine(itemId: Long?, title, colour: String?, unitPrice: Int, quantity: Int)`; `AddressSnapshot(name, phone, line1, line2: String?, city, state, pin)`; `enum OrderStatus { PAYMENT_SUBMITTED, PAYMENT_REJECTED, CONFIRMED, PACKED, SHIPPED, DELIVERED, CANCELLED }` with `val label: String` ("Waiting for seller", "Payment not found", "Confirmed", "Packed", "Shipped", "Delivered", "Cancelled") and `isActive` (everything except `DELIVERED`, `CANCELLED`); `Hold(id, sellerId, shopName, sellerUpiId: String?, lines, itemsTotal, shippingFee, grandTotal, address: AddressSnapshot, note: String?, expiresAt: String)`; `CheckoutBody(addressId: Long, note: String?, sellerId: Long?)`; `LineAdjustment(itemId, title, requested, reserved)`; `SkippedSeller(sellerId, shopName, reason: String)`; `CheckoutResult(holds, adjustments, skipped)`; `StatusEntry(status: OrderStatus, at: String)`; `Order(id, status, sellerId, shopName, sellerUpiId: String?, lines, itemsTotal, shippingFee, grandTotal, address, note: String?, paymentReference: String?, hasProof: Boolean, rejectionReason: String?, paymentDeadline: String?, cancelReason: String?, cancelledBy: String?, courier: String?, trackingNumber: String?, unread: Boolean, createdAt: String, updatedAt: String, history: List<StatusEntry> = emptyList())`; `OrderPage(items: List<Order>, page, pageSize, totalItems: Long, totalPages)`; `OrderSummary(unread: Int = 0, activeHolds: Int = 0, toConfirm: Int = 0, toPack: Int = 0, toShip: Int = 0, shipped: Int = 0)`.

**Interfaces — APIs** (Retrofit, paths under `api/v1/`): `ShoppingApi`: `GET cart`, `PUT cart/items/{id}` (body `QuantityBody`, `Response<Unit>`), `DELETE cart/items/{id}` (`Response<Unit>`), `GET wishlist?page=` → `CatalogPage`, `PUT wishlist/{id}`, `DELETE wishlist/{id}` (both `Response<Unit>`), `GET addresses`, `POST addresses` (`AddressForm`) → `Address`, `PUT addresses/{id}`, `DELETE addresses/{id}` (`Response<Unit>`). `OrderApi`: `POST cart/checkout` (`CheckoutBody`) → `CheckoutResult`; `GET holds` → `List<Hold>`; `DELETE holds/{id}` (`Response<Unit>`); `@Multipart POST holds/{id}/payment` (`@Part("utr") RequestBody`, `@Part proof: MultipartBody.Part?`) → `Order`; `@Multipart POST orders/{id}/payment` (same) → `Order`; `GET orders/mine?state=&page=` → `OrderPage`; `GET orders/{id}` → `Order`; `POST orders/{id}/cancel` → `Order`; `GET orders/summary` → `OrderSummary`; `POST orders/seen` (`Response<Unit>`).

**Interfaces — repositories** (all `@Singleton`, `Result<T>` via `apiCall`, same style as `BookingRepository`):
- `CartRepository`: `val cart: StateFlow<Cart>` (starts empty), `suspend fun refresh(): Result<Cart>`, `suspend fun setQuantity(itemId: Long, quantity: Int): Result<Unit>`, `suspend fun remove(itemId: Long): Result<Unit>` (both refresh the cart on success), `fun reset()`.
- `WishlistRepository`: `suspend fun page(page: Int): Result<CatalogPage>`, `suspend fun add(itemId: Long): Result<Unit>`, `suspend fun remove(itemId: Long): Result<Unit>`.
- `AddressRepository`: `suspend fun list(): Result<List<Address>>`, `create(form): Result<Address>`, `update(id, form): Result<Address>`, `delete(id): Result<Unit>`.
- `OrderRepository`: `val summary: StateFlow<OrderSummary>`, `refreshSummary()`, `checkout(addressId: Long, note: String?, sellerId: Long?): Result<CheckoutResult>`, `holds(): Result<List<Hold>>`, `cancelHold(id): Result<Unit>`, `pay(holdId: Long, utr: String, proof: PhotoPart?): Result<Order>` and `resubmit(orderId: Long, utr: String, proof: PhotoPart?): Result<Order>` (build the multipart parts; `PhotoPart` is a small wrapper over the existing `PhotoSource`/`ProgressRequestBody` pieces — reuse whatever the upload code uses for a `MultipartBody.Part` rather than adding a new abstraction), `mine(state: OrderStatus?, page: Int)`, `order(id)`, `cancel(id)`, `markSeen()`, `reset()`. A successful `checkout`, `pay`, `resubmit`, `cancel`, `cancelHold` refreshes both the summary and, via a callback-free approach, leaves cart refresh to the caller (ViewModels call `CartRepository.refresh()` themselves).
- `NetworkModule` provides `ShoppingApi` and `OrderApi`; `AppViewModel.logout` also calls `CartRepository.reset()` and `OrderRepository.reset()`.

- [ ] **Step 1: Write failing tests.** `OrderModelsTest.decodesEveryStatusTheBackendCanSend` (all seven names, plus a full `Order` JSON sample copied from the slice 2 smoke output, with unknown extra keys), `ShoppingRepositoryTest`: `cartRefreshStoresCart`, `setQuantityRefreshesCart`, `removeRefreshesCart`, `failureKeepsPreviousCartAndReturnsMessage`, `wishlistAddAndRemoveCallTheApi`, `addressCreateReturnsAddress`; `OrderRepositoryTest`: `checkoutReturnsHoldsAndAdjustments`, `payBuildsMultipartWithUtrAndOptionalProof`, `serverErrorBecomesApiFailureWithCode` (`HOLD_LIMIT`, `UTR_IN_USE`), `markSeenZeroesUnreadInSummary`, `resetClearsSummary`. Fakes in `OrderFakes.kt` implement `ShoppingApi`/`OrderApi` with recorded calls like `BookingFakes`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests '*OrderModelsTest' --tests '*ShoppingRepositoryTest' --tests '*OrderRepositoryTest'` → FAIL (classes missing).
- [ ] **Step 3: Implement** models, APIs, repositories, DI and logout reset to the interfaces above.
- [ ] **Step 4:** same command → PASS; `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL. Existing tests that construct `CatalogItem`/`ItemDetail` positionally must still compile (new fields are last with defaults).
- [ ] **Step 5: Commit** `Android: cart, wishlist, address and order data layer`.

---

### Task 3: Item page — quantity, Add to cart, wishlist heart

**Files:**
- Create: `ui/reseller/PurchasePanel.kt`
- Modify: `ui/reseller/ItemDetailScreen.kt` (replace the `BookingPanel(...)` call at ~line 355, add heart overlay near the status pill ~line 269), `ui/reseller/DetailViewModels.kt` (`ItemPagerViewModel`), `ui/ShareText.kt` only if it mentions booking
- Test: `ui/ItemPagerViewModelTest.kt` (update), `androidTest/.../ui/PurchasePanelTest.kt` (new)

**Interfaces:**
- Consumes: `CartRepository.setQuantity/remove`, `WishlistRepository.add/remove` (Task 2).
- Produces: `ItemPagerViewModel(handle, catalog: CatalogRepository, cart: CartRepository, wishlist: WishlistRepository)` (the `BookingRepository` parameter and `bookingBusy/bookingError/book/cancelBooking` are removed); `PagerState` gains `shoppingBusy: Boolean = false, shoppingError: String? = null`; functions `setCartQuantity(id: Long, quantity: Int)`, `removeFromCart(id: Long)`, `toggleWishlist(id: Long)` — each calls the repository, then refetches that item's detail (like `booking()` did) and sets/clears `shoppingError`.
- `@Composable internal fun PurchasePanel(detail: ItemDetail, ui: ShoppingUi, onSetQuantity: (Int) -> Unit, onRemove: () -> Unit)` with `data class ShoppingUi(val busy: Boolean = false, val error: String? = null)`. Behaviour: when `!detail.inStock` show "Out of stock" text (testTag `out-of-stock`) and nothing else; when `detail.inCart == 0` show a stepper (1..`detail.quantity`, tags `qty-minus`, `qty-plus`, `qty-value`), "Only N left" when `detail.quantity <= 3` (tag `low-stock`), and **Add to cart** (tag `add-to-cart`); when `detail.inCart > 0` show "In your cart" with the stepper bound to the cart quantity, **Update** is implicit (every stepper change calls `onSetQuantity`), and a **Remove** text button (tag `remove-from-cart`). Heart: `IconButton` (tag `wishlist`, content description "Save to wishlist"/"Remove from wishlist") overlay top-start of the photo.

- [ ] **Step 1: Write failing tests.** Unit (`ItemPagerViewModelTest`, replacing booking tests): `addToCartCallsRepositoryAndReloadsDetail`, `failedAddShowsErrorAndKeepsDetail`, `removeFromCartReloadsDetail`, `wishlistToggleAddsWhenNotSavedAndRemovesWhenSaved`, `secondTapWhileBusyIsIgnored`. UI (`PurchasePanelTest`): `shows stepper and add button for in-stock item`, `hides everything but out-of-stock text when not in stock`, `plus is capped at stock`, `shows low stock warning at three or fewer`, `in-cart item shows remove and sends quantity changes`, `shows error text`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests '*ItemPagerViewModelTest'` → FAIL (compile).
- [ ] **Step 3: Implement** the ViewModel changes, `PurchasePanel`, and wire both into `ItemDetailScreen` (keep `ContactButtons` below the panel). Delete the `BookingPanel` usage but not the file.
- [ ] **Step 4:** `./gradlew testDebugUnitTest --tests '*ItemPagerViewModelTest'` → PASS; `./gradlew compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `Android: add to cart and wishlist on the saree page`.

---

### Task 4: Wishlist tab

**Files:**
- Create: `ui/reseller/WishlistViewModel.kt`, `ui/reseller/WishlistScreen.kt`
- Test: `ui/WishlistViewModelTest.kt`, `androidTest/.../ui/WishlistScreenTest.kt`

**Interfaces:** `WishlistViewModel(WishlistRepository)` exposing `StateFlow<WishlistState(items: List<CatalogItem>, nextPage, hasMore, loading, loadingMore, refreshing, error, busyId: Long?)>`; `reload()`, `pullRefresh()`, `loadMore()`, `remove(itemId)` (removes the row on success). `WishlistScreen(onOpenItem: (Long) -> Unit, viewModel)` — rows like `MatchRow` in `CatalogScreen.kt` (thumbnail via `SareeImage`, collection title, shop, price, `ItemStatusPill` or "Out of stock" when `!inStock`), a remove (heart-filled) icon button per row (tag `wishlist-remove-<id>`), empty state "Nothing saved yet" with a hint to tap the heart (tag `wishlist-empty`). Follow `BookingsViewModel`/`BookingsScreen` for paging, pull-to-refresh and error layout.

- [ ] **Step 1: Failing tests.** Unit: `loadsFirstPage`, `loadMoreAppendsAndDeduplicates`, `removeDropsTheRow`, `failedRemoveKeepsRowAndShowsError`, `emptyListIsNotAnError`. UI: `shows saved sarees with price and shop`, `out of stock row says so`, `empty state shows hint`, `remove button sends the item id`, `tapping a row opens the item`.
- [ ] **Step 2:** unit command for `*WishlistViewModelTest` → FAIL. **Step 3:** implement. **Step 4:** unit PASS + `compileDebugAndroidTestKotlin`.
- [ ] **Step 5: Commit** `Android: wishlist tab`.

---

### Task 5: Delivery addresses

**Files:**
- Create: `ui/reseller/AddressesViewModel.kt`, `ui/reseller/AddressesScreen.kt` (list + add/edit form dialog in one file)
- Test: `ui/AddressesViewModelTest.kt`, `androidTest/.../ui/AddressesScreenTest.kt`

**Interfaces:** `AddressesViewModel(AddressRepository)` with `AddressesState(items: List<Address>, loading, error, form: AddressFormState?, saving: Boolean, fieldErrors: Map<String,String>)`; `AddressFormState(id: Long?, name, phone, line1, line2, city, state, pin, makeDefault)` (all strings); functions `load()`, `startAdd()`, `startEdit(address)`, `onField(name: String, value: String)`, `setDefault(Boolean)`, `save()`, `cancelForm()`, `delete(id)`. Client validation mirrors the server: name/line1/city/state required, phone `[6-9]\d{9}`, PIN `\d{6}` (errors in `fieldErrors`, nothing sent); server `fields` map from `ApiFailure.fields` is merged into `fieldErrors`. `AddressesScreen(onBack, onPicked: ((Address) -> Unit)? = null, viewModel)` — list rows show name, phone, lines, "Default" chip, edit/delete buttons (tags `address-edit-<id>`, `address-delete-<id>`, delete asks to confirm), **Add address** button (tag `add-address`); when `onPicked != null` tapping a row picks it (checkout uses this). Route `addresses` is added in Task 9; here the screen is a standalone composable.

- [ ] **Step 1: Failing tests.** Unit: `loadShowsAddresses`, `invalidPhoneAndPinAreRejectedLocally`, `saveCreatesAndReloads`, `serverFieldErrorsAreShown`, `editPrefillsAndUpdates`, `deleteRemovesRow`, `eleventhAddressShowsServerMessage` (409 `ADDRESS_LIMIT` message surfaces). UI: `form shows field errors`, `list marks the default`, `picker mode returns tapped address`, `delete asks for confirmation`.
- [ ] **Step 2–4:** as Task 4 (`*AddressesViewModelTest`). **Step 5: Commit** `Android: delivery addresses`.

---

### Task 6: Cart tab and checkout

**Files:**
- Create: `ui/reseller/CartViewModel.kt`, `ui/reseller/CartScreen.kt`, `ui/reseller/CheckoutSheet.kt`
- Test: `ui/CartViewModelTest.kt`, `androidTest/.../ui/CartScreenTest.kt`

**Interfaces:**
- Consumes: `CartRepository`, `AddressRepository`, `OrderRepository.checkout`.
- Produces: `CartViewModel(cart: CartRepository, addresses: AddressRepository, orders: OrderRepository)` with `CartState(cart: Cart, loading, refreshing, error, busyItemId: Long?, checkout: CheckoutUi?)` and `CheckoutUi(seller: CartSeller, addresses: List<Address>, selectedAddressId: Long?, note: String, busy: Boolean, error: String?, result: CheckoutResult?)`; functions `reload()`, `pullRefresh()`, `setQuantity(itemId, quantity)`, `remove(itemId)`, `startCheckout(sellerId)` (loads addresses, preselects the default), `selectAddress(id)`, `onNote(String)`, `confirmCheckout()`, `dismissCheckout()`, and an events channel/flow `holdCreated: Flow<Long>` emitting the new hold id. `confirmCheckout` calls `orders.checkout(addressId, note, sellerId)`; on success refreshes the cart; if `holds` is non-empty emits the first hold id (the screen navigates to `hold/{id}`); otherwise shows `result.skipped`/`adjustments` as text in the sheet. `CartScreen(onOpenItem: (Long) -> Unit, onOpenHold: (Long) -> Unit, onAddAddress: () -> Unit, onDiscover: () -> Unit, viewModel)`.
- Layout: one card per `CartSeller` with the shop name, each line (thumbnail `SareeImage` for `itemId`, title, colour, `₹unitPrice`, stepper capped at `maxQuantity`, remove button tag `cart-remove-<itemId>`), "Shipping ₹fee", "Total ₹grandTotal", and **Pay <shop>** (tag `pay-<sellerId>`) enabled only when `acceptsOrders && grandTotal > 0`. A group that cannot be paid shows why: "Not accepting orders yet" (tag `not-accepting-<sellerId>`) or "Nothing in stock". Out-of-stock lines are dimmed with "Out of stock" and a remove button. A line with `quantity > maxQuantity` shows "Only N available". Empty cart: "Your cart is empty" + **Browse sarees** (tag `cart-empty`, calls `onDiscover`). `CheckoutSheet` is a Material 3 `ModalBottomSheet` with the address list (radio rows, tags `address-choice-<id>`), **Add address** (tag `checkout-add-address`, calls `onAddAddress`), an optional note field (≤300 chars), the total, and **Continue to payment** (tag `continue-payment`, disabled until an address is chosen or while busy).

- [ ] **Step 1: Failing tests.** Unit: `reloadShowsGroupedCart`, `setQuantityRefreshesTotals`, `removeLineUpdatesCart`, `startCheckoutPreselectsDefaultAddress`, `checkoutWithoutAddressIsBlocked`, `confirmCheckoutEmitsFirstHoldId`, `checkoutFailureKeepsCartAndShowsServerMessage` (`HOLD_LIMIT`), `adjustmentsAreShownWhenNoHoldWasMade`, `sellerNotAcceptingCannotStartCheckout`. UI: `shows seller groups with shipping and totals`, `pay button disabled when seller not accepting`, `out of stock line is dimmed and offers remove`, `quantity above stock shows only N available`, `empty cart shows browse button`, `checkout sheet lists addresses and enables continue after selecting one`.
- [ ] **Step 2–4:** as before (`*CartViewModelTest`). **Step 5: Commit** `Android: cart and checkout`.

---

### Task 7: Payment hold screen

**Files:**
- Create: `ui/reseller/HoldViewModel.kt`, `ui/reseller/HoldScreen.kt`
- Test: `ui/HoldViewModelTest.kt`, `androidTest/.../ui/HoldScreenTest.kt`

**Interfaces:**
- Consumes: `OrderRepository.holds()/pay/cancelHold`, `Format.timeLeft` (existing, in `ui/Format.kt`).
- Produces: `HoldViewModel(handle: SavedStateHandle("id"), orders: OrderRepository, cart: CartRepository)` with `HoldState(hold: Hold?, loading, error, utr: String, proof: Uri?, busy: Boolean, payError: String?, expired: Boolean)`; `onUtr(String)` keeps digits only, max 12; `onProof(Uri?)`; `canPay: Boolean` = 12 digits and not busy and not expired; `markExpired()` (called by the screen when the countdown hits zero); `pay()` → `orders.pay(...)`, then emits `paid: Flow<Long>` with the new order id; `cancel()` → `orders.cancelHold` then `cart.refresh()` and emits `closed: Flow<Unit>`. Loads the hold from `orders.holds()` by id (not found → "This payment window has ended" with a back button).
- `HoldScreen(onBack, onPaid: (Long) -> Unit, onClosed: () -> Unit, viewModel)`: header "Pay <shop>"; total in large type and the breakdown (items + shipping); a countdown line "Complete payment in 1 h 42 m" recomputed every 30 s (`LaunchedEffect` tick) tag `hold-countdown`; the seller's UPI ID with a **Copy** button (tag `copy-upi`, copies to the clipboard and shows a snackbar) and **Pay with UPI app** (tag `upi-intent`, `ACTION_VIEW` on the `upi://pay` URI built from UPI ID, shop name, total; on `ActivityNotFoundException` show the snackbar "No UPI app found. Copy the ID and pay from your app."); a UTR field (tag `utr`, numeric keyboard, helper text "12 digits, shown in your UPI app after paying"); an optional screenshot picker (Photo Picker, tag `pick-proof`, shows the chosen file name and a remove button); **I have paid** (tag `i-have-paid`, enabled by `canPay`); **Cancel payment** (tag `cancel-hold`, confirm dialog "Release these sarees?"). At expiry: the pay controls are replaced with "This payment window has ended" and **Back to cart** (tag `back-to-cart`).

- [ ] **Step 1: Failing tests.** Unit: `utrKeepsOnlyDigitsAndTwelveCharacters`, `cannotPayWithShortUtr`, `payEmitsOrderId`, `payFailureShowsServerMessage` (`UTR_IN_USE`, `HOLD_EXPIRED`), `expiredHoldDisablesPaying`, `cancelReleasesHoldAndRefreshesCart`, `missingHoldShowsEndedMessage`. UI: `shows total shipping and countdown`, `i have paid enables only with twelve digits`, `copy button shows the upi id`, `expired state hides payment form`, `cancel asks for confirmation`.
- [ ] **Step 2–4:** as before (`*HoldViewModelTest`). **Step 5: Commit** `Android: payment hold screen`.

---

### Task 8: Orders tab and order detail

**Files:**
- Create: `ui/reseller/OrdersViewModel.kt`, `ui/reseller/OrdersScreen.kt`, `ui/reseller/OrderDetailViewModel.kt`, `ui/reseller/OrderDetailScreen.kt`
- Modify: `ui/components/StatusPill.kt` (add `OrderStatusPill(status: OrderStatus)` using existing `SareeColors`: pending = amber `OnHold`/`OnHoldText`, confirmed/packed/shipped = neutral, delivered = `Available`/`AvailableText`, rejected/cancelled = error tone)
- Test: `ui/OrdersViewModelTest.kt`, `ui/OrderDetailViewModelTest.kt`, `androidTest/.../ui/OrdersScreenTest.kt`

**Interfaces:**
- Produces: `OrdersViewModel(OrderRepository)` with `OrdersState(holds: List<Hold>, items: List<Order>, nextPage, hasMore, loading, loadingMore, refreshing, error)`; `reload()`, `pullRefresh()`, `loadMore()`; opening the list calls `orders.markSeen()` after page 0 loads when any order is `unread` (same pattern as `BookingsViewModel`). `OrdersScreen(onOpenOrder: (Long) -> Unit, onOpenHold: (Long) -> Unit, viewModel)`: active holds first as cards ("Pay <shop> · ₹total · <countdown>", tag `hold-<id>`), then orders newest first, each row shows shop, item titles summary, `₹grandTotal`, `OrderStatusPill`, a dot when `unread` (tag `unread-<id>`); empty state "No orders yet" (tag `orders-empty`).
- `OrderDetailViewModel(handle("id"), OrderRepository)` with `OrderDetailState(order: Order?, loading, error, utr: String, proof: Uri?, busy: Boolean, actionError: String?)`; `reload()`, `onUtr`, `onProof`, `resubmit()` (needs 12 digits and `status == PAYMENT_REJECTED`), `cancel()` (only when `status` is `PAYMENT_SUBMITTED` or `PAYMENT_REJECTED`; confirm dialog in the screen). `OrderDetailScreen(onBack, viewModel)`: status pill and timeline from `history` (each entry label + `timeAgo`), lines, address, totals, seller name, `paymentReference`, courier and tracking when present; a banner for `PAYMENT_REJECTED` with `rejectionReason` and the deadline countdown and the corrected-UTR form (tags `resubmit-utr`, `resubmit`) shown only while the deadline is in the future; cancelled orders show `cancelReason` and who cancelled; **Cancel order** (tag `cancel-order`) when allowed.

- [ ] **Step 1: Failing tests.** Unit: `listsHoldsAndOrders`, `opensAndMarksSeenOnlyWhenUnread`, `loadMorePages`, `resubmitNeedsTwelveDigitsAndRejectedStatus`, `resubmitReplacesOrder`, `cancelOnlyForPaymentSubmittedOrRejected`, `cancelFailureShowsMessage`, `deadlineInThePastHidesResubmit`. UI: `order row shows status pill and total`, `active hold card opens the hold`, `rejected order shows reason and resubmit form`, `rejected order past deadline hides the form`, `shipped order shows courier and tracking`, `cancelled order shows who cancelled and why`, `empty state`.
- [ ] **Step 2–4:** as before (`*OrdersViewModelTest`, `*OrderDetailViewModelTest`). **Step 5: Commit** `Android: orders list and detail`.

---

### Task 9: Shell, navigation and badges

**Files:**
- Modify: `ui/reseller/ResellerBottomBar.kt`, `ui/reseller/ResellerNavHost.kt`, `ui/reseller/ResellerShellViewModel.kt`, `ui/reseller/CatalogScreen.kt` (header row), `ui/profile/AccountRoutes.kt` only if routes need registering
- Test: `ui/ResellerShellViewModelTest.kt` (new or updated), `androidTest/.../ui/ResellerBottomBarTest.kt` (update/new), `androidTest/.../ui/CatalogHeaderTest.kt` (new)

**Interfaces:**
- Consumes: all screens from Tasks 4–8 and `CartRepository.cart`, `OrderRepository.summary`.
- Produces: constants `TAB_CATALOG = "catalog"`, `TAB_WISHLIST = "wishlist"`, `TAB_CART = "cart"`, `TAB_ORDERS = "orders"`, `ROUTE_PROFILE`; non-tab routes `sellers`, `alerts`, `addresses`, `hold/{id}`, `order/{id}`, `item/{id}`, `seller/{id}`, `collection/{id}`. `TABS = {catalog, wishlist, cart, orders, profile}`; bar visible when `route in TABS + {sellers, alerts}`. `ResellerBottomBar(current: String?, cartCount: Long, orderBadge: Long, onSelect)` — five tabs with test tags `tab-<route>`; Cart badge shows `cart.lineCount`, Orders badge shows `summary.unread + summary.activeHolds` (tags `cart-badge`, `orders-badge`). `ResellerShellViewModel` exposes `unread` (alerts bell), `cartCount`, `orderBadge` StateFlows, refreshes cart and order summary in `refreshUnread()` (60 s loop already in the nav host) and no longer touches `BookingRepository`. `CatalogScreen` gains `onOpenSellers: () -> Unit`, `onOpenAlerts: () -> Unit`, `alertsUnread: Long` and a header row with the title "Saree Market" and two icon buttons (tags `open-sellers`, `open-alerts`, the alerts one badged).
- Navigation wiring: `HoldScreen.onPaid(orderId)` → `nav.navigate("order/$orderId") { popUpTo("hold/{id}") { inclusive = true } }`; `onClosed`/back-to-cart → `openTab(TAB_CART)`; cart `onOpenHold` → `hold/{id}`; cart `onAddAddress` → `addresses`; `addresses` route uses `AddressesScreen(onBack)`; the checkout address picker reloads addresses when the user returns (the cart ViewModel reloads in `onResume`/when `addresses` pops). Orders `onOpenHold`/`onOpenOrder` → the routes above.

- [ ] **Step 1: Failing tests.** Unit: `cartCountFollowsCart`, `orderBadgeAddsUnreadAndHolds`, `refreshUnreadRefreshesCartAndSummary`. UI: `bar has five tabs in order`, `cart and orders badges show counts and hide at zero`, `catalog header buttons call their callbacks and bell shows unread`.
- [ ] **Step 2–4:** unit (`*ResellerShellViewModelTest`) then `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin`.
- [ ] **Step 5: Commit** `Android: customer shell, tabs and navigation`.

---

### Task 10: Whole suites, emulator walk-through, notes

**Files:** none new; fix whatever the suites reveal.

- [ ] **Step 1:** `cd android && ./gradlew testDebugUnitTest` → all pass (record the count). Fix failures (existing tests that referenced `Role.RESELLER`, `ItemPagerViewModel(…, bookings)`, the five-tab bar or the booking panel are expected breakages: update them to the new behaviour, never delete coverage that still applies).
- [ ] **Step 2:** start the emulator (`Medium_Phone_API_37.0`, wait for boot), `./gradlew connectedDebugAndroidTest` → all pass (record the count). Note that this uninstalls the app and logs out.
- [ ] **Step 3: Walk-through on the emulator** against the local backend (start it if needed): register or log in as customer `9555500011` / `password1`; open a saree, add to cart, heart it; Cart tab → Pay → choose address → continue → hold screen shows total and countdown; enter a 12-digit UTR → I have paid → order detail shows "Waiting for seller". Log in as seller `9876500001` is not possible in the app yet (slice 4), so confirm and ship through the API (`curl` as in the slice 2 smoke test) and check the customer's Orders tab and badge update. Install: `./gradlew installDebug`, then `adb shell am start -n com.sareemarket.app/.MainActivity`.
- [ ] **Step 4:** Update memory notes (`customer-orders-redesign.md`) and this plan's checkboxes; commit `Slice 3 verified`. Stop for review.

---

## Hand-offs

- Slice 4 (Android seller): reuse `OrderApi`/`OrderModels` from Task 2 (seller routes `received`, `confirm-payment`, `reject-payment`, `pack`, `ship`, `deliver` are added to `OrderApi` then), `OrderStatusPill` from Task 8, and the polling loop pattern from Task 9.
- Slice 5 deletes `BookingApi/Repository/Models`, `BookingsScreen/ViewModel`, `BookingPanel`, `BookingAlertsWorker` and their tests.
