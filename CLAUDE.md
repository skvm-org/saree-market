# Saree Market Catalog — MVP

A catalog app where saree sellers list their collections and resellers search them by keyword, filters or image, then contact the seller directly. No payments, no orders, no external services.

Full scope: `docs/MVP_BRIEF.md`. Read it before starting any feature. If it conflicts with the longer product spec or functional flow docs, **MVP_BRIEF.md wins**.

## Frontends

- **Phase 1 (MVP, now): native Android app** in `android/`
- **Phase 2 (after the MVP): Angular web app** in `frontend/`, built against the same backend. Not started; do not scaffold `frontend/` until told to
- Both frontends use the same REST API. The backend stays **client-neutral**: no Android-specific endpoints, response shapes or assumptions
- `docs/design/DESIGN.md` is the shared design source for both apps: same colours, type scale, radii, spacing. The screens in `docs/design/stitch/code.html` (one HTML file, one `screen-*` section per screen) are the visual reference for both

## Stack

- Backend: Java (latest LTS), Spring Boot (latest stable), Maven
- Database: PostgreSQL in Docker, schema managed only by Flyway
- Auth: Spring Security + JWT (stateless), BCrypt passwords
- Android: Kotlin, Jetpack Compose, Material 3, single activity, Navigation Compose, Hilt, Retrofit + OkHttp + kotlinx.serialization, Coil, DataStore, WorkManager
- Tests: JUnit 5 + Mockito (backend unit), Spring Boot Test + Testcontainers PostgreSQL (backend integration); Android tests below
- Images: stored on local disk, thumbnails via Thumbnailator

Check start.spring.io, the Android developer site and library release notes for current stable versions (AGP, Kotlin, Compose BOM, libraries) when scaffolding; don't guess version numbers.

## Repo layout

```
backend/        Spring Boot app (Maven)
android/        Android app (Gradle, Kotlin) — phase 1
frontend/       Angular app — phase 2, not yet created
docs/           MVP_BRIEF.md, design/DESIGN.md, design/stitch/ (screens), longer product docs
docker-compose.yml   PostgreSQL only
data/uploads/   local image storage (git-ignored)
```

## Running locally

```
docker compose up -d                      # PostgreSQL on host port 5433
cd backend && ./mvnw spring-boot:run      # API on 8080
cd android && ./gradlew installDebug      # or run from Android Studio on an emulator
```

Seeded admin for local testing: phone `9999999999`, password `admin123` (local profile only; other profiles must set `ADMIN_PASSWORD_HASH` and `JWT_SECRET`, 32+ characters).

Containers run on OrbStack (it provides the `docker` CLI and `docker compose`; Testcontainers uses it too).

The emulator reaches the backend at `http://10.0.2.2:8080`. For a physical device on the LAN, override the base URL (see Android conventions).

## Backend conventions

- Package by feature: `auth`, `seller`, `collection`, `item`, `catalog`, `imagesearch`, `follow`, `notification`, `reference`, `common`
- Cross-feature side effects (e.g. notifying followers) go through Spring application events, not direct service calls
- Inside each feature: `Controller`, `Service`, `Repository`, `dto/`, `entity`
- Controllers never return entities; use DTO records
- All endpoints under `/api/v1`
- Validation with Jakarta Bean Validation on request DTOs
- One `@RestControllerAdvice` returns errors as `{ "code", "message", "fields" }`
- Never edit an applied Flyway migration; always add a new `V<n>__description.sql`
- Configuration in `application.yml`, with a `local` profile; no secrets committed (JWT secret from env var with a dev default in the `local` profile only)
- CORS allowed origins come from config (`app.cors.allowed-origins`, empty by default). The native Android app needs no CORS; the setting exists so the Angular app can be added later without backend changes
- Image storage behind an `ImageStorage` interface (`LocalDiskImageStorage` now; S3 later)
- Client-neutral: responses and endpoints must not assume which frontend calls them

## Android conventions

- Kotlin, Jetpack Compose, Material 3, single activity, Navigation Compose; min SDK 26, target the latest stable SDK
- MVVM: one `ViewModel` per screen exposing `StateFlow` UI state, a repository layer over Retrofit, Hilt for DI
- Retrofit + OkHttp + kotlinx.serialization; an OkHttp interceptor adds the JWT and logs out on a 401
- Debug builds request the Android 17 `ACCESS_LOCAL_NETWORK` permission at startup (needed to reach `10.0.2.2` and LAN IPs); release builds do not declare it
- JWT stored in DataStore; logout clears it. Seller and Reseller flows are chosen at login by role
- Base URL from `BuildConfig`: `http://10.0.2.2:8080` by default (emulator), overridable for a physical device on the LAN. Cleartext HTTP is allowed in the debug build only
- Coil for images; Android Photo Picker for multi-select upload (up to 30); camera capture for image search
- `DESIGN.md` tokens become the Compose theme (colour scheme, typography, shapes, spacing). Stitch HTML is the layout and content reference for each screen, rebuilt natively in Compose; never a WebView
- Notifications: in-app bell with unread count and a notifications screen, plus a WorkManager periodic job (every 15 min) that checks the unread count and shows a local Android notification for new collections from followed sellers. No Firebase in the MVP
- Admin stays backend-only in the MVP (seeded admin user, admin endpoints covered by integration tests). Admin screens come with the Angular app

## Testing rules

- Every backend service method with logic gets unit tests
- Every controller gets at least one integration test against Testcontainers PostgreSQL
- Must-have integration tests: role-based access (a reseller cannot edit a seller's items; seller phone is hidden from anonymous users), catalog filters, image search finds an uploaded image, admin endpoints
- `./mvnw verify` must pass before a backend slice is called done
- Android: unit tests for ViewModels and repositories using a fake API; Compose UI tests for the login and catalog screens
- Android tests must pass before an Android slice is called done

## Skills to use

Required skills must be installed before work starts. If one is missing when needed, stop and say so instead of continuing without it.

| Skill | Use for | When |
| --- | --- | --- |
| **Android design** (Material 3 + Android conventions) | Navigation, back behaviour, touch targets, system feedback | While writing any Android UI: Material 3 components, predictable back stack and system back, touch targets ≥ 48 dp, bottom sheets for filters, edge-to-edge with insets handled |
| **Ponytail** (`/ponytail-review`, `/ponytail-audit`) | Remove boilerplate and over-engineering in backend code | `/ponytail-review` on the diff at the end of every backend slice; apply its delete-list unless it removes validation, security or a required test. `/ponytail-audit` on the whole backend after slices 4 and 8 |

**Precedence when they disagree:** `DESIGN.md` wins on tokens, colours and type; Material 3 / Android conventions win on navigation, back behaviour and touch targets (48 dp, not DESIGN.md's 44 px); the Stitch screens decide layout and content.

**Ponytail limits:** it reviews only for over-engineering. It does not check correctness, security or performance, so the tests in "Testing rules" and a normal review still apply. Never remove the `ImageStorage` or `NotificationChannel` interfaces or the event-based notification flow; they are deliberate extension points for planned add-ons.

## Web frontend (phase 2, not started)

Applies only once the Angular app begins, after the MVP. Do not scaffold `frontend/` before then.

- Angular (latest stable), standalone components, Angular Material
- Standalone components, signals for local state, lazy-loaded routes per feature
- Visual design comes from the design skills below and `DESIGN.md`, not from Material defaults: use Angular Material / CDK only for complex widgets (dialogs, menus, autocomplete), restyled to the design system
- One `ApiService` per feature; an HTTP interceptor adds the JWT
- Route guards by role (SELLER, RESELLER, ADMIN)
- Mobile-first layouts: most users are on phones
- Admin screens (reference data, users) are built here
- Dev server on 4200, proxying `/api` to 8080; add its origin to `app.cors.allowed-origins` if it calls the API directly

| Skill | Use for | When |
| --- | --- | --- |
| **Impeccable** (`/impeccable`) | Main UI/UX workflow and design system | Once before the web UI: `/impeccable init` (or `teach` on older versions) to write `PRODUCT.md` and refine `DESIGN.md`. Each UI slice: `/impeccable shape` before coding, `/impeccable critique` after, `/impeccable polish` before calling it done |
| **Taste skill** (`design-taste-frontend`) | Visual taste: layout, type, spacing, motion | While writing any web UI code. Dials: low design variance, low motion, medium density (a working catalog tool, not a landing page) |
| **Apple design skill** (HIG) | Interaction and mobile conventions | While writing web UI: touch targets ≥ 44 px, bottom sheets for filters on phones, clear hierarchy, system-like feedback on taps and toggles |

Web precedence: `DESIGN.md` wins on tokens, colours and type; Apple HIG wins on touch and mobile interaction; Taste guides everything else visual.

## Working style

- Build one slice at a time in the order in MVP_BRIEF.md
- Per slice: plan → build → tests → (backend: `/ponytail-review`) → `./mvnw verify` and Android tests → summarise what was built and how to try it → stop for review
- Do not start the next slice until told to
- Do not add features listed under "Out of scope" in the brief
- Keep it simple: no microservices, no message queues, no caching layers
