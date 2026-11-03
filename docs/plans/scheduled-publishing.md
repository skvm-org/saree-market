# Scheduled publishing for collections (seller-configurable slots)

## Context
Sellers' collections go live the moment they are created. Sellers want to define their own daily publish slots (different per weekday, e.g. Mon 11:00 / 13:00 / 16:00 / 18:00, Sun 10:00), create collections, assign each to a slot, and have each go live by itself; "publish now" stays. Constraints: no new infrastructure, no slowdown of other transactions as data grows, backend client-neutral, safe if a second instance ever runs.

**Branch:** no new branch. Work continues on `feature/home-all-sellers-first-follow-button` (the UI refactor branch), as separate commits per slice. Backend first, then the Android slice.

## Design

### Data model (`V16__collection_publishing.sql`)
- `collection`: add `status` (DRAFT | SCHEDULED | PUBLISHED, default PUBLISHED so existing rows are live), `publish_at` timestamptz, `published_at` timestamptz (backfilled = `created_at`). CHECKs: SCHEDULED needs `publish_at`; PUBLISHED iff `published_at`.
- Partial index `idx_collection_due ON collection (publish_at) WHERE status = 'SCHEDULED'`.
- New table `seller_publish_slot (id, seller_id FK ON DELETE CASCADE, day_of_week 1-7, slot_time TIME, UNIQUE(seller_id, day_of_week, slot_time))`. Times are in IST (`Asia/Kolkata`, one constant for now; a `timezone` column on `seller_profile` is the later scale-out step).

### Seller-defined slots
- `GET /sellers/me/publish-slots` returns the weekly set; `PUT` replaces it atomically (max 10 slots per day, 50 total, times on the minute).
- `GET /sellers/me/publish-slots/upcoming?days=7` returns the concrete next instants (`publishAt` + label) so a client never does timezone math: it just shows chips.
- Scheduling a collection takes an absolute `publishAt`; the server checks it is an occurrence of one of the seller's slots, at least 2 minutes ahead and at most 30 days ahead. `publish_at` is stored absolute, so later editing slots never moves collections already scheduled.

### One visibility rule
`CollectionVisibility.LIVE` (`c.status = 'PUBLISHED'`) added to every public read:
- `CatalogService.where()` (search, image search, cards, collection search) and item detail (404 unless live, owner or admin)
- Seller public page: new `listPublic` (today `SellerService` reuses the owner's `listMine`, which would leak drafts)
- `CollectionService.get`; `CartService.add/view` (add has no collection join today); `HoldService.cartRows`; wishlist add
- Image endpoints `/images/{id}/thumb|full` (ids are guessable)
- `FollowService` last-upload uses `max(published_at)` of live collections
"Newest", cards and time-ago use `published_at`. On publish, item `created_at` is re-stamped to the publish time so `idx_item_available_newest` and item sorting keep working (document on `Item`).

### Publisher and its database cost
Concern: a job that runs every 30 s must not slow other transactions as data grows. Assessment:
- The poll reads only `idx_collection_due`, a **partial index that contains only SCHEDULED rows**. Its size follows pending schedules (tens to a few thousand), never the total collection count, so the probe stays microseconds at any scale. It is a plain MVCC read: it takes no locks and blocks nobody.
- Row locks (`FOR UPDATE SKIP LOCKED`) are taken only on rows that are actually due, for milliseconds.
- Real costs are bursts (many sellers sharing 11:00) and index churn, so we bound them:
  1. **Cheap pre-check outside any write transaction:** `SELECT 1 FROM collection WHERE status='SCHEDULED' AND publish_at <= now() LIMIT 1`. Only when it returns a row does the job open a write transaction. Idle ticks write nothing, so no WAL, no dead tuples, no autovacuum pressure.
  2. **Adaptive wake-up instead of a fixed 30 s cadence:** the job reads `min(publish_at)` and sleeps until then (capped by a 2-minute safety tick that also covers restarts and a second instance). Schedule / reschedule calls nudge the wake-up earlier when needed. Typical idle load is a few probes per hour, with a worst case of 30 per hour at the safety tick.
  3. **Batches of 100** per transaction with `SKIP LOCKED`, looped until empty, so no long locks; follower notifications stay async after commit (existing `notificationExecutor`).
  4. Item re-stamp is one `UPDATE … WHERE collection_id = ANY(:ids)` per batch; counts come from one grouped query.
- Missed times after downtime publish on the first tick; a collection goes live within seconds of its slot (target under 15 s with the adaptive wake-up).
- **Proof, not assumption:** a verification step seeds 1M collections plus 20k scheduled rows and compares `EXPLAIN (ANALYZE, BUFFERS)` and catalog p95 with and without the job running.
- Rejected: in-memory timer per row (lost on restart, duplicated across instances), pg_cron (RDS extension setup), Quartz/db-scheduler (heavy for one query).

### REST API (`/api/v1`)
- `POST /collections`: optional `publishAt` or `draft=true` (both together is 400); default unchanged (publish now)
- `PUT /collections/{id}/schedule {publishAt}`, `DELETE …/schedule` (back to draft), `POST …/publish` (now)
- `GET /sellers/me/collections?status=`, ordered by `coalesce(published_at, publish_at, created_at) desc`
- DTOs gain `status`, `publishAt`, `publishedAt`; 409 `ALREADY_PUBLISHED` when rescheduling a live collection; 400 `INVALID_SLOT` for a time that is not one of the seller's slots
- Edits and deletes work in every state; `addPhotos` on an unpublished collection fires no event. Events: every move to PUBLISHED fires one `CollectionPublished`.

### Android slice (after backend; design pass first)
UX shaped with the Android design skill (Material 3, 48 dp targets, bottom sheets) and the Stitch/DESIGN.md tokens:
- Profile or seller home → "Publish slots": weekday tabs, add/remove time chips, copy one day to others
- Create collection: segmented "Publish now / Schedule / Save draft"; Schedule shows upcoming slot chips ("Today 4:00 PM", "Tomorrow 11:00 AM"); empty state links to slot setup
- Dashboard: status badge ("Goes live 4:00 PM") and a status filter; detail: Publish now / Reschedule / Unschedule

## Files
Create: `V16__collection_publishing.sql`, `CollectionStatus`, `CollectionVisibility`, `CollectionPublishJob`, slot entity/repository/service/controller/dtos (in `collection` or a new `schedule` feature package).
Modify: `SareeCollection`, `SareeCollectionRepository`, `CollectionService`, `CollectionController`, `collection/dto/*`, `CatalogService`, `CatalogSort`, `SellerService`, `CartService`, `HoldService`, `FollowService`, wishlist service, `ItemService`.

## Slices (each ends with `./mvnw verify`; Android slice also Android tests)
1. Migration, entity, migration test
2. Visibility everywhere, with broad leak tests (anonymous, customer and other seller never see or edit drafts or scheduled)
3. Slot configuration endpoints and validation
4. Create / schedule / publish / unschedule endpoints, state-machine unit tests
5. Publisher job (pre-check, adaptive wake-up, batches): due rows publish, future ones don't, overdue catch up, event fires once, two concurrent runs publish each row once
6. Performance check on seeded data (see above)
7. Android slot setup and schedule UI

## Verification
`./mvnw verify`; run locally, set slots, schedule a collection two minutes ahead, confirm it is hidden from catalog, seller page and image URLs, then appears at its time and a follower gets exactly one notification; EXPLAIN/p95 comparison from slice 6.

## Open questions
- Can a live collection go back to draft? (recommend no)
- Notify followers for publishes that run late after downtime? (recommend yes)
- Is a 30-day horizon right, and should scheduling strictly require a configured slot (recommended) or also allow any time?
