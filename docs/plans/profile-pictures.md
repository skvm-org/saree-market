# Profile pictures for customers and sellers

Status: Phases 1 and 2 are built (nothing committed yet); the one full test pass is next. Branch: `feature/profile-picture`. Date: 2026-11-06.

Progress notes (where the build differs from the plan below):
- The version is whole epoch milliseconds, both in Java (stamped with `truncatedTo(MILLIS)`) and in SQL (`floor(...)`), so the version a client gets from the upload equals the one other users' views compute. A first version of the SQL rounded and was 1 ms off; a test caught it.
- Phase 2 adds `sellerPhotoVersion` / `photoVersion` to: the catalog collection card, the photo page's seller, the shop page, followed sellers, notifications; and `id` + `photoVersion` to the customer on a seller's order. Each is a scalar subquery (`PhotoVersions.sql`), no join changes.
- Android: a shared `Avatar` (picture, falling back to initials while loading or on error) replaces the initials circles on the seller home header and cards, the item page, My sellers, notifications, the shop page (new 72 dp avatar) and the seller's order cards.
- `MigrationFromProductionTest` now counts the migration files instead of listing versions, so a new migration no longer breaks it.

## Why

Every account shows an initials circle today. Customers and sellers should be able to upload a picture, change it and remove it. A seller's picture is the shop's face for buyers.

## Decisions (agreed in chat)

| # | Decision |
| --- | --- |
| D1 | A seller's picture is visible to every logged-in user. A customer's picture is visible only to that customer, to admins, and to a seller on that seller's own orders with them. Anonymous users see none. |
| D2 | Upload from the gallery (Android Photo Picker) or the camera; remove is offered when a picture exists. |
| D3 | The server centre-crops to a square and stores a 512 px JPEG; the app shrinks a large photo to 1600 px before sending, as other uploads do. |
| D4 | Client-neutral API: no Android-specific endpoints or shapes. iOS is not part of this work. |

## Design

**Data.** `V18__profile_photo.sql` adds nullable `photo_key` and `photo_updated_at` to `app_user`.

**Storage.** Keys are `avatars/<userId>-<uuid>.jpg`. They land under the S3 folder the app already writes to, so no instance-role change. A replaced or removed picture's old file is deleted after the database change commits; a rolled-back upload deletes its new file.

**API.**

| Method | Path | Who | Notes |
| --- | --- | --- | --- |
| PUT | `/api/v1/auth/me/photo` (multipart `photo`) | Any logged-in role | Returns the updated user. 400 `INVALID_IMAGE` for a non JPG/PNG/WebP file, 400 `VALIDATION_ERROR` when no file |
| DELETE | `/api/v1/auth/me/photo` | Any logged-in role | 204; removing when there is none is fine |
| GET | `/api/v1/users/{id}/photo` | Logged in, by D1 | JPEG. 404 when there is no picture or the caller may not see it (so existence is not leaked) |

`UserDto` gains `photoVersion` (epoch milliseconds of the last change, or null). Clients ask for `/users/{id}/photo?v=<version>`: with `v` the response is cached hard (immutable), without it the client must revalidate. Phase 2 adds the same field next to the seller and customer ids that buyer-facing and seller-facing DTOs already carry.

**Android.** A shared `Avatar` composable shows the picture and falls back to initials. The Profile avatar becomes tappable with a camera badge and opens a sheet: Choose from gallery, Take a photo, Remove photo (only when there is one).

## Phases

1. **Backend and the owner's own screens.** Migration, `AppUser`, `UserDto.photoVersion`, an `ImageProcessor.avatar` crop, the upload/remove/view endpoints, and on Android the Profile screen (upload, change, remove) and the seller home header.
2. **Pictures on other screens.** A seller's avatar on catalog cards, the item page, the seller page and My sellers; a customer's avatar next to their name on the seller's order cards.

## Tests

Backend integration tests: upload, wrong type, no file, replace (old file deleted), remove, and the visibility matrix (self, admin, a seller with an order from the customer, a seller without one, another customer, anonymous). A unit test that the crop is square and the orientation is applied. Android view-model and UI tests. No regression run between phases; one full pass (backend `verify`, Android unit tests, the full UI suite, a walkthrough on the emulator) after both phases.

## Deployment

Additive: the migration only adds nullable columns, and old apps ignore the new field. Deploy the backend first, then publish the rebuilt APK, as in the order-flow release. Rolling the backend back leaves the new columns unused.

## Out of scope

Server-side moderation, an in-app cropper or zoom, admin screens, the iOS app, animated or video avatars.
