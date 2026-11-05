# Order flow simplification: auto-confirm, tracking number, customer "Received"

Status: Phases 1 to 6 are done (nothing committed yet); Phase 7 (deploy) is not started and waits for the user's go-ahead. Phase 6 result: backend `./mvnw verify` green (one stale hard-coded version list in `MigrationFromProductionTest` fixed), Android unit tests green, Android UI suite 203 tests green (one stale banner-text test in `SellerHomeScreenTest` fixed and rerun), and the API walkthrough plus the seller Orders screen checked on the emulator. Branch: `feature/enhancements`. Date: 2026-11-05.

Progress notes (where the build differs from the plan below):
- Phases 1 and 2 were built together, because removing the service methods broke the controller until it was updated. Backend `./mvnw -q test-compile` passes; no tests have been run.
- Phases 3 and 4 were built together for the same reason (the Android repository is shared). `compileDebugKotlin`, `compileDebugUnitTestKotlin` and `compileDebugAndroidTestKotlin` pass; no tests have been run.
- The customer's cancel dialog has no Call/WhatsApp shortcut: the order response carries no seller phone. The dialog tells the customer to ask the seller for the refund.
- Notification wording for `CONFIRMED`, `PACKED` and `PAYMENT_REJECTED` was kept so notifications already inside their 30 days still read correctly.
- Android decodes an unknown order status as `UNKNOWN` (`coerceInputValues` is now on in the network module); history rows of the earlier flow are hidden by the server.
- The iOS spec lives on the `ios-app` branch, so the note for it (Phase 5) is not added here; carry it over when that branch is reconciled.
Per `CLAUDE.md`, `docs/MVP_BRIEF.md` wins over other docs; this plan changes sections 7 and 8 of the brief and Phase 5 updates them.

## 1. Why

Sellers see too many order stages (To confirm, To pack, To ship, Shipped, All) and each one is a tap. The order flow should ask the seller for the one thing only they can supply: the courier tracking number. The customer should be told when it arrives and be able to copy it.

## 2. Decisions (agreed in chat)

| # | Decision |
| --- | --- |
| D1 | Submitting a valid 12-digit UTR creates the order directly as `CONFIRMED`. The seller no longer confirms payment. |
| D2 | The seller can still cancel with a reason (payment never arrived) until the order ships. Stock returns as today. |
| D3 | `PACKED` is removed. The seller adds courier and tracking number when the parcel is with the courier; that moves the order to `SHIPPED`. |
| D4 | The customer taps **Received** to move `SHIPPED` to `DELIVERED`. The seller has no deliver step. |
| D5 | Seller filters: **To ship / Shipped / All**. |
| D6 | The customer gets a notification when the tracking number is added, with the number in the text, and can copy it in the order screen. |
| D7 | The customer can cancel their own order while it is `CONFIRMED` (before it ships). The seller is notified at once. Stock returns. The app does not refund: the confirmation dialog says the refund is arranged with the seller. A reason is optional for the customer. |
| D8 | The seller can correct a mistyped tracking number while the order is `SHIPPED`; the customer is notified again. |
| D9 | Courier name and tracking number are both required to ship (for now). |

## 3. Target state

```
(hold, 2 h) --UTR submitted--> CONFIRMED --seller adds tracking--> SHIPPED --customer taps Received--> DELIVERED
                                   |
                                   +--seller (reason) or customer cancels--> CANCELLED   (also: hold lapses, never an order)
```

Removed statuses: `PAYMENT_SUBMITTED`, `PAYMENT_REJECTED`, `PACKED`.
Removed behaviour: seller payment confirm/reject, the 2-hour UTR correction window, rejected-order expiry, seller deliver. Customer cancel stays, but only from `CONFIRMED` (D7).

## 4. Test policy for this feature (explicit instruction)

- **No regression runs between phases.** Each phase writes or rewrites the tests for what it changes, and ends with a compile check only (`./mvnw -q test-compile` for backend, `./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileDebugAndroidTestKotlin` for Android). No `./mvnw verify`, no Android test runs mid-way.
- **One full test pass in Phase 6**, after all phases are built: backend `verify`, Android unit tests, the full Android UI suite once, the migration rehearsal, and a manual walkthrough.
- This overrides the per-slice test gate in `CLAUDE.md` for this feature only.
- Cost of the policy: failures from early phases surface together in Phase 6. Phase 6 therefore allows fix-and-rerun until green before anything is deployed.

## 5. Phases

### Phase 1: Backend data and domain

Goal: the database and order logic follow the new lifecycle.

Changes
- `V17__simplify_order_flow.sql` (new; never edit V12). Data only, no constraint change (see section 6, expand/contract):
  - `PAYMENT_SUBMITTED` and `PACKED` orders become `CONFIRMED`, with an `order_status_history` row for the new status.
  - `PAYMENT_REJECTED` orders become `CANCELLED` (`cancelled_by = 'SYSTEM'`, reason "Order flow changed; payment could not be matched"). They still hold stock, so the migration puts it back with the same capped add-back as `StockReturn` (cap 10000 per saree).
- `OrderStatus`: keep the enum values the database may still hold during rollout; mark the three removed ones `@Deprecated` and stop producing them.
- `OrderService`:
  - `submitPayment` inserts `CONFIRMED`, writes `CONFIRMED` history, sets `seller_unread = TRUE` and `customer_unread = FALSE`.
  - Delete `confirmPayment`, `pack`, `rejectPayment`, `resubmitPayment`, `expireRejected`, `deliver` (seller version).
  - `cancelByCustomer`: allowed only from `CONFIRMED` (was `PAYMENT_SUBMITTED`/`PAYMENT_REJECTED`); optional reason; marks the order unread for the seller and notifies the seller; stock returns through the existing cancel path.
  - `ship(sellerId, orderId, courier, trackingNumber)`: from `CONFIRMED` only, courier and tracking both required (D9).
  - New `updateTracking` (D8): only while `SHIPPED`, courier and tracking both required; publishes the update event again.
  - New `receive(customerId, orderId)`: `SHIPPED` to `DELIVERED`, customer's own order only.
  - `cancelBySeller`: allowed only from `CONFIRMED`.
  - `summary`: counts become to-ship (`CONFIRMED`) and shipped; unread logic unchanged.
- `OrderExpiryJob`: drop the rejected-order step; holds still expire.

Tests (written, not run)
- Unit: transitions allowed and refused for each of `ship`, `updateTracking`, `receive`, `cancelBySeller`, `cancelByCustomer` (refused once shipped); summary counts.
- `MigrationV17Test` in the style of `MigrationV13Test`: build the database up to V16 with orders in every old status, run V17, assert statuses, history rows and returned stock.

Exit: `./mvnw -q test-compile` passes.

### Phase 2: Backend API and notifications

Goal: the HTTP surface and notification text match the new flow. Responses stay client-neutral.

Changes
- `OrderController`:
  - Remove `/confirm-payment`, `/reject-payment`, `/pack`, `/deliver`, and `POST /orders/{id}/payment` (resubmit).
  - `/ship` requires `courier` and `trackingNumber` (`ShipRequest` gets `@NotBlank` on both).
  - Add `POST /orders/{id}/receive` (customer, own order) and `PUT /orders/{id}/tracking` (seller, own order).
  - `/cancel` stays for both roles. A customer can cancel only their own `CONFIRMED` order (reason optional); a seller needs a reason, as today. Either gets 409 `BAD_STATE` once the order has shipped.
  - `GET /orders/received?state=` accepts `CONFIRMED`, `SHIPPED` (and no value for All).
- DTOs: drop `rejectionReason` and `paymentDeadline` from `OrderDto`; drop the confirm/pack counts from `OrderSummaryDto`; delete `RejectRequest`.
- `NotificationService.orderMessage`:
  - `SHIPPED`: "<shop> shipped your order #N. Tracking: <courier> <number>", reading courier and number from the order.
  - Remove `PACKED` and `PAYMENT_REJECTED` wording; keep `DELIVERED` and `CANCELLED`.
  - A customer cancel tells the seller: "<customer> cancelled order #N" (with the reason if given). The seller's cancel wording to the customer is unchanged.
  - A tracking edit (D8) reads "Tracking updated for order #N: ...".
  - The seller's "new order" alert keeps using the unread flag.
- Update the Javadoc on changed methods.

Tests (written, not run)
- Controller integration tests rewritten: seller ships (courier and tracking required, 400 otherwise), edit tracking, customer receives, customer cancels a confirmed order and the seller is notified, customer cannot cancel a shipped order, customer cannot read another customer's order, seller cannot act on another seller's order, role-based access, and the shipped notification text containing the number.
- Delete tests for removed endpoints (`PaymentProofIntegrationTest` keeps only proof upload and viewing).

Exit: `./mvnw -q test-compile` passes.

### Phase 3: Android data layer and seller orders

Goal: sellers see To ship / Shipped / All and add tracking in one step.

Changes (`android/app/src/main/java/com/sareemarket/app/`)
- `data/OrderModels.kt`, `data/OrderApi.kt`: new status set, new `receive` and `updateTracking` calls, removed calls and fields. Decode unknown statuses to an `UNKNOWN` fallback so a future backend value cannot fail a whole page.
- `ui/seller/SellerOrdersViewModel.kt`, `SellerOrdersScreen.kt`, `OrderActionDialogs.kt`:
  - Filters: To ship / Shipped / All, counts from `summary`; tab badge = to-ship count.
  - To-ship card: customer name and phone with Call and WhatsApp, lines, city and PIN, total, UTR, **View screenshot**, and two actions: **Add tracking** and **Cancel order**.
  - Add-tracking dialog: courier and tracking number, both required. Shipped card shows **Edit tracking**.
  - A cancelled order shows who cancelled it and the reason.
  - Remove the Payment received, Not found, Mark packed and Mark delivered actions and their dialogs.
- `ui/components/StatusPill.kt`: pills for Confirmed, Shipped, Delivered, Cancelled.
- Follow `docs/design/DESIGN.md` tokens, 48 dp touch targets, back stack unchanged.

Tests (written, not run): `SellerOrdersViewModelTest`, `OrderModelsTest`, `OrderFakes.kt`, `SellerOrdersScreenTest` updated for the new filters and dialog.

Exit: Android compile of main, unit-test and androidTest sources passes.

### Phase 4: Android customer orders and notifications

Goal: customers see tracking, copy it, and confirm receipt.

Changes
- `ui/reseller/OrderDetailScreen.kt`, `OrderDetailViewModel.kt`:
  - Timeline: Confirmed, Shipped, Delivered (Cancelled with the seller's reason).
  - Shipped state shows courier and tracking number with a **Copy** button (system clipboard, a short confirmation, no extra permission) and a **Received** button with a confirmation prompt.
  - **Cancel order** stays on `CONFIRMED` orders, with a dialog: "The seller already has your payment. Cancelling returns the sarees to the shop; arrange your refund with the seller." It offers an optional reason and a Call/WhatsApp shortcut to the seller. It is hidden once shipped.
  - Remove the resend-UTR flow and the 2-hour countdown for rejected orders.
- `ui/reseller/OrdersScreen.kt`: filters and badge follow the new statuses; the Orders badge counts unread updates plus open holds, as now.
- Notifications: no worker change expected, because the text comes from the server. Verify the shipped notification opens the order.

Tests (written, not run): `OrderDetailViewModelTest` (receive, copy state, cancel only while confirmed), `OrdersScreenTest`.

Exit: Android compile of main, unit-test and androidTest sources passes.

### Phase 5: Documentation

- `docs/MVP_BRIEF.md`: sections 7 and 8, the status list, the API table, the data model line for `order_status_history`, and the "Orders tab" description.
- `CLAUDE.md`: the opening paragraph says the seller confirms the UTR; change it to say the customer enters the UTR and the order is confirmed automatically.
- Note in the iOS spec (`docs/superpowers/specs/2026-10-30-ios-customer-app-design.md`) that the `ios-app` branch must adopt the same lifecycle when I4 (orders) is built.

Exit: docs read consistently; no code runs.

### Phase 6: Full verification (the one test pass)

Run once, after Phases 1 to 5 are complete. Fix and rerun until green.

1. Backend: `cd backend && ./mvnw verify` (unit, integration on Testcontainers, `MigrationV17Test`).
2. Android: `./gradlew testDebugUnitTest`, then the full `connectedDebugAndroidTest` suite once (this uninstalls the app and logs the user out; reinstall after).
3. Manual walkthrough on the emulator against a local backend (`docker compose up -d`, `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`), seller `9876500001` and customer `9876500003` (password `password1`):
   - Customer checks out, pays, enters a UTR: order shows Confirmed immediately; seller's Orders badge and To-ship list show it.
   - Seller adds tracking: customer gets the notification with the number; the order shows it; Copy works.
   - Seller edits the tracking number: customer is notified again.
   - Customer taps Received: Delivered.
   - Seller cancels a confirmed order with a reason: customer sees it, stock returns.
   - Customer cancels a confirmed order (refund warning shown): the seller is notified and stock returns.
   - A shipped order has no cancel action for either side.
4. Migration rehearsal on a copy of production data (section 6, step 2).

Exit: all green and the walkthrough passes. Only then does Phase 7 start.

### Phase 7: Deployment

See section 6.

## 6. Deployment strategy

Facts: pushes to `main` that touch `backend/**` run Backend CI then Deploy (image tagged with the git sha, `docker compose up` over SSH, RDS Postgres, Caddy). The debug APK in `releases/saree-market-debug.apk` is rebuilt against `https://sareemarket.duckdns.org` and force-added. Installed apps decode statuses strictly, so an old APK will not understand the new flow.

Approach: expand now, contract later.
- **Expand (this release):** V17 migrates data only and leaves the status `CHECK` constraint and the rejected-deadline index alone. Old backend code can still run on migrated data (it only meets statuses it knows), so rolling the image back is safe.
- **Contract (a later release, after a stable week):** V18 narrows the `CHECK`, drops `idx_order_rejected_deadline`, and the deprecated enum values are deleted. Not part of this plan's phases.

Steps
1. **Before merge:** query production for orders in `PAYMENT_SUBMITTED` and `PAYMENT_REJECTED`. Customers with a rejected order still inside their 2-hour window will be cancelled by V17; if there are any, tell them or let the window run out first. Take an RDS snapshot (the migration changes data and cannot be reversed by redeploying).
2. **Rehearse V17** against a restore of that snapshot or a dump loaded into a throwaway local database, and check the counts match the pre-query. (`MigrationV17Test` covers synthetic rows; this covers real ones.)
3. **Build the new APK** against the live URL from the Phase 6 commit, with the new flow.
4. **Merge the PR to `main`.** Backend CI runs `verify` again, then Deploy ships the image and Flyway applies V17 on startup.
5. **Immediately after the deploy is healthy:** commit the rebuilt APK to `releases/` on `main` (a change outside `backend/**` does not redeploy) and share it with testers. Between steps 4 and 5 an old APK talks to the new backend; the window is minutes and the user base is small, so I accept it rather than keeping the old endpoints alive.
6. **Smoke test on production** with a throwaway seller and customer account (and disable the throwaway accounts afterwards; user id 16 is still waiting to be disabled): place an order, add tracking, receive.
7. **Rollback:** redeploy the previous image tag (the previous git sha). Because V17 is data-only and old code understands `CONFIRMED`, `SHIPPED`, `DELIVERED` and `CANCELLED`, nothing needs to be undone, except that orders V17 cancelled stay cancelled.

## 7. Risks

| Risk | Mitigation |
| --- | --- |
| A fake UTR is auto-confirmed and the seller only notices later (D1). | Seller keeps UTR, screenshot and Cancel on every to-ship order. This is the accepted trade-off. |
| Old APK breaks against the new backend. | Ship the APK straight after the deploy (step 5); unknown-status fallback in the new app; contract migration waits. |
| V17 changes real orders. | Pre-query, snapshot, rehearsal on a production copy. |
| Failures from early phases pile up because there are no mid-way runs. | Compile checks per phase; Phase 6 loops until green before deploying. |
| Customer cancels after paying and expects a refund, or after the seller already packed (D7). | The dialog states the refund is arranged with the seller; the seller is notified immediately with the reason; cancelling is blocked once shipped. |
| `ios-app` branch drifts further. | Recorded in Phase 5; handled when iOS I4 starts. |

## 8. Out of scope

Courier API integration or live tracking status, tracking links, delivery auto-completion, partial shipments, payment verification of any kind, admin screens, the iOS changes, FCM push.
