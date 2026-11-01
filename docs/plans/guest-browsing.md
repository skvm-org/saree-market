> **Status: proposed, not started.** Reviewed and approved as a plan on 2026-11-01; implementation deliberately paused. Start from the merged `ui-redesign-v3` work.

# Guest browsing: log in only when it matters

## Context
Today the app shows a login screen first, and the backend returns 401 for everything except login, register and health. You asked whether customers need to log in before they can look around. They don't: browsing costs the seller nothing and gives a new customer a reason to sign up. The plan is to open straight into the catalog and ask for an account at the moment someone follows a seller, wishlists, adds to the cart, orders, or tries to contact a seller.

Decisions confirmed:
1. Seller phone and WhatsApp numbers are hidden from guests and shown only after login. Call/WhatsApp stay on screen and open the login sheet.
2. No guest cart. Add to cart and Buy now need an account; the pending action finishes right after sign-up.
3. A customer who logs out lands on the guest catalog. A seller or admin lands on the login screen.

Verdict on the idea: sound. It matches the MVP brief's "sellers' phones are protected" rule, removes the biggest drop-off point, and is easier for App Store review. The cost is that the catalog becomes public, so scraping and spam registration need light protection (slice 3).

## Corrections found while checking the code
- Behind Caddy the client IP is already correct (`deploy/docker-compose.yml:15`, `SERVER_FORWARD_HEADERS_STRATEGY: native`), so a new rate limiter can use the same key as `LoginAttemptLimiter`.
- Sellers get 403 on `/catalog` today and keep that. A guest will see more of the catalog than a logged-in seller does. That is fine for the MVP.
- `/sellers/me/collections` has three path segments, so `GET /sellers/*` doesn't match it and it stays seller-only.
- Photo bytes never change for an id (no replace path in `ItemService`), so public `Cache-Control` is safe. `ItemService.image` doesn't check the seller is enabled; add that check, since images become public.
- `CatalogService.itemDetail` hides phones only from the SELLER role, so a null role would expose them. This must be fixed before the endpoint is opened.

## Slice 1: backend, then a guest catalog end to end
**Backend (`backend/src/main/java/com/sareemarket`)**
1. `auth/SecurityConfig.java`: add method-specific `permitAll` before `anyRequest()`: `GET /api/v1/catalog`, `/catalog/collections`, `POST /catalog/image-search`, `GET /items/*`, `GET /sellers`, `/sellers/*`, `GET /collections/*`, `GET /images/*/full` and `/thumb`, `GET /reference/**`. PATCH/DELETE on items and collections stay authenticated.
2. `@PreAuthorize` on those browse methods becomes `isAnonymous() or hasAnyRole(...)` with today's role list: `catalog/CatalogController`, `seller/SellerController`, `collection/CollectionController` (GET only).
3. Add `auth/Viewer` (`Long id`, `String role`, `Viewer.of(@Nullable Jwt)`) and pass it to `CatalogService.itemDetail`, `SellerService`, catalog search and `CollectionService.get`, replacing the primitive `long userId, String role`.
4. Contact rule: `contact = viewer != null && !SELLER`, in both `CatalogService.itemDetail` and `SellerService` (null phone and WhatsApp for guests).
5. `CatalogFilter.following` with an anonymous viewer returns the existing 401 `{code:"UNAUTHORIZED",message:"Login required"}`.
6. `item/ItemController.java:62`: `cachePrivate()` becomes `cachePublic()` (30 days, immutable); `ItemService.image` returns 404 for items of a disabled seller.
7. Docs: `docs/MVP_BRIEF.md` roles row (line 23), contact sentence (line 25), the API "Who" column (about lines 177-208) and Screens table (about 230-232); `CLAUDE.md` auth lines (69-71). The must-have test "seller phone is hidden from anonymous users" now runs against a public endpoint.

**Android (`android/app/src/main/java/com/sareemarket/app`)**
1. `ui/AppViewModel.kt`, `ui/AppRoot.kt`: `AppState` becomes `Loading | SignedOut(showLogin) | LoggedIn`. One branch renders `ResellerNavHost(session: Session?)` for both guests and logged-in customers, so the NavController survives sign-in. Sellers and admins go to `HomeScreen` as today. `AuthNavHost` is removed; login and register become routes `auth/login` and `auth/register` in `ResellerNavHost`.
2. Logout and expiry: save `lastRole` in `data/SessionStore.kt`. A customer lands on the guest catalog (expiry shows a "Your session ended" snackbar with a Log in action, via `SessionNotice`). A seller or admin lands on `auth/login` with the existing notice.
3. `ui/reseller/ResellerShellViewModel.kt`: run `refreshUnread` and `startAlerts()` only while a session exists; reset when it ends.
4. `ui/reseller/CatalogViewModel.kt`: guests skip `/follows` and `refreshFollowCount` and start on All sellers. The Following tab shows "Log in to follow sellers".
5. Catalog header (`CatalogScreen.kt`): guests see a "Log in" text button instead of My sellers and Notifications.
6. `data/AuthInterceptor.kt`: when a request that carried a token gets a 401, clear the session (as today) and retry GET requests once without the token. A stale token then degrades to a guest catalog.

## Slice 2: ask for an account at the moment of intent (Android)
- `GatedAction` sealed interface: `Follow(sellerId)`, `Wishlist(itemId)`, `AddToCart(itemId, qty)`, `BuyNow(itemId, qty)`, `Contact`.
- `AuthGateViewModel` scoped to `ResellerNavHost`, exposed as `LocalAuthGate`. `require(action)` returns true when logged in; otherwise it stores the action (in `SavedStateHandle`, so it survives process death) and opens the login sheet. When the session becomes a CUSTOMER it runs the action through the existing repositories (`FollowRepository`, wishlist, cart), then refreshes; `BuyNow` also opens the cart. For a seller or admin, or when the sheet is dismissed, the action is dropped.
- Wire it at: `CatalogViewModel.toggleFollow`, `FollowPill` (`FollowButton.kt`, item page and `SellerPageScreen.kt`), the wishlist heart and `PurchasePanel.kt` buttons (`ItemDetailScreen.kt`), `rememberContactActions()` (item and seller page), the catalog header buttons, and the signed-out tab states.
- Tabs for guests: keep all five. Wishlist, Cart and Orders show an empty state with "Log in" and "Create account" (reuse `components/Kit.kt` `EmptyState`). Profile shows Log in, Create account, "Sell on Saree Market" (full-screen register with role SELLER) and "Seller or admin? Log in".
- Hold, addresses and order routes open the sheet instead of loading.
- Login sheet: a `ModalBottomSheet` (pattern in `ui/reseller/FilterSheet.kt`) with Log in and Create account panes. Extract `LoginForm` and `RegisterForm` from `LoginScreen.kt`/`RegisterScreen.kt` without the `Surface`/`BrandMark` wrapper; `RegisterViewModel` gets a `fixedRole` (CUSTOMER in the sheet; the full-screen register keeps the role picker).
- The item, seller-page and catalog view models reload when `session.userId` changes, so cart, wishlist, follow state and phone numbers refresh after sign-in.

## Slice 3: hardening (backend)
- `common/RateLimiter` (generalise `auth/LoginAttemptLimiter`, in-memory fixed window): `POST /catalog/image-search` 10/min per IP for guests and 30/min logged in; `POST /auth/register` 5/hour per IP; anonymous browse GETs about 300/min per IP via a `OncePerRequestFilter`. Over the limit returns 429 `{code:"RATE_LIMITED"}`; the Android error mapper shows "Too many requests, try again shortly".
- Image search accepts one file of 10MB or less, so the 320MB request limit can't be abused.
- Count limited requests in the logs.

## Tests
**Backend** (`./mvnw verify` must pass)
- Flip to 200 or the new behaviour: `CatalogIntegrationTest` (69-71, 370-373, 400-402), `ImageSearchIntegrationTest` (207-209), `SellerPageIntegrationTest` (45-51), `CollectionIntegrationTest` (206, 317).
- New `GuestBrowsingIntegrationTest`: guest 200 on every public endpoint; contact fields null for a guest and present for a customer; guest `following=true` gets 401; guest 401 on `/cart`, `/wishlist`, `/follows`, `/orders/mine`, `/sellers/me/collections`, PATCH `/items/{id}`, PUT `/collections/{id}`; an expired token on `/catalog` gets 401; images carry `Cache-Control: public`; a seller still gets 403 on `/catalog`; an item of a disabled seller's image is 404.
- Slice 3: `RateLimiterTest` and 429 checks on image search and register.

**Android**
- New: `AppViewModelTest`, `AuthGateViewModelTest` (runs after customer login; dropped for a seller; cleared on dismiss; BuyNow opens the cart), `ResellerShellViewModelTest` (no polling for a guest), `LoginSheetTest`.
- Extend: `AuthInterceptorTest` (GET retried without a token, POST not), `CatalogViewModelTest` (guest skips follows), `CatalogHeaderTest`, `ItemDetailScreenTest`, `PurchasePanelTest`, `LoginScreenTest`, `RegisterScreenTest`, `ShellNavigationTest`.
- Run unit tests per slice; run the one full `connectedDebugAndroidTest` suite at the end.

## Verification
1. `cd backend && ./mvnw verify`.
2. `cd android && ./gradlew testDebugUnitTest`, then one full `connectedDebugAndroidTest` at the end.
3. Emulator walkthrough against the local backend: a fresh install opens the catalog; search, filters and image search work; an item shows no phone numbers; Follow opens the sheet and Create account returns already following; Buy now opens the sheet and ends in the cart with the same quantity; logout lands on the guest catalog; a seller logs in from Profile and gets the seller shell, and seller logout shows the login screen; clearing the token server-side gives a guest catalog plus the "session ended" snackbar.

## Risks to watch
- Scraping of catalogs and photos: the catalog was already open to anyone willing to register, and phone numbers stay behind login. Tell sellers their shop and photos are public.
- Spam registrations: the register limit now, OTP later.
- Stale state after sign-in: handled by reloading on user change.
- Gated routes reached by deep link or Back: they open the sheet instead of crashing.
- Store review: confirm the Play Data Safety form still holds.
- Measure afterwards: guest sessions, which action opened the sheet, sheet-to-account conversion, registrations per day, 429 counts, anonymous requests per IP.

## Not in this change
- iOS (`RootDestination.swift` needs a guest destination, a sign-in sheet and the expiry change; update `RootRoutingTests` and `AuthFlowUITests`) and the Angular app: same rules, later.
- Seller screens, the stock stepper and the `ui-redesign-v3` branch work are unaffected, but this should start from the merged redesign branch.
