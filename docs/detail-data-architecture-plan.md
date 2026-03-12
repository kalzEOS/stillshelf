# Detail Data Architecture Plan

## Purpose

This document defines a clean, testable plan for book and series detail loading in StillShelf.

The goal is to make detail pages:

- Open instantly when we already know something locally.
- Refresh in the background when data is missing or stale.
- Stay correct for dynamic user state like progress and bookmarks.
- Avoid full-screen loading flashes on every navigation.
- Avoid repeated network calls while a screen is open.

This plan is intentionally phased so each step can be implemented, verified, and tested before moving to the next one.

## Current Problems

- Book detail is fetched with `forceRefresh = true` on first load.
- Book detail is polled every second while the page is visible.
- Series detail uses only in-memory cache, so it still starts from a loading state on every fresh view-model creation.
- Detail data is not persisted in Room, so process death loses all cached detail state.
- The detail payload mixes stable catalog data and dynamic user state, which drives too many refreshes.

## Architecture Principles

1. Room is the source of truth for detail screens.
2. UI reads local data only.
3. Network refresh writes into Room, then UI updates from Room flows.
4. Full-screen loading is only allowed when there is no local record at all.
5. Cached data may be shown while a background refresh is running.
6. User-driven refresh is explicit and forceful.
7. Dynamic user state is updated by events and targeted sync, not by screen polling.
8. Presentation choices such as series collapsing are not stored as source-of-truth cache.

## External Guidance

- Official Android guidance recommends an offline-first data layer with a local source of truth and repositories that update local storage from the network.
- The Audiobookshelf Android app is useful as a reference for scoped in-memory browse caches, but its main phone UI is Capacitor/WebView-driven and is not the right architectural pattern for StillShelf's native Compose detail screens.

References:

- https://developer.android.com/topic/architecture/data-layer/offline-first
- https://developer.android.com/topic/architecture/data-layer
- https://developer.android.com/topic/architecture/recommendations
- [MainActivity.kt](/home/kalzi/Git/audiobookshelf-app-master/android/app/src/main/java/com/audiobookshelf/app/MainActivity.kt)
- [MediaManager.kt](/home/kalzi/Git/audiobookshelf-app-master/android/app/src/main/java/com/audiobookshelf/app/media/MediaManager.kt)

## Data Model Direction

The current `BookDetail` model should not remain a purely network-only aggregate.

We should persist the detail screen as a small set of tables:

- `book_summaries`
  - Existing browse-level catalog data for a book.
- `book_details`
  - Stable metadata that only appears on the detail page.
  - Example: description, published year, size, last fetched timestamp.
- `book_chapters`
  - Normalized chapter rows by `bookId`.
- `book_bookmarks`
  - Bookmark rows by `bookId` and bookmark id.
- `series_summaries`
  - Series metadata used by list and detail pages.
- `series_memberships`
  - Raw series contents keyed by series id and book id, with ordering fields.
- `detail_sync_state`
  - Per-resource freshness metadata if needed.

Everything should be keyed so data is isolated by server and library where required.

## Resource Split

Detail pages currently behave as if everything is one resource. That is the wrong split.

We should treat detail data as three freshness classes:

1. Catalog metadata
- Title, cover, author, description, chapters, published year, size.
- Usually slow-changing.
- Safe to show immediately from cache.

2. User state
- Playback progress, current time, finished state, bookmarks.
- Fast-changing.
- Must update from local mutations and targeted server sync.

3. Membership data
- Series contents, subseries membership, ordering.
- Medium-changing.
- Should refresh more often than catalog metadata, but not every second.

This separation is what gives us both speed and correctness.

## Refresh Policy

Introduce a repository refresh policy instead of raw `forceRefresh: Boolean`.

Proposed policy:

- `IfMissing`
  - Only hit network when nothing exists locally.
- `IfStale`
  - Use local data immediately and refresh if older than freshness budget.
- `Force`
  - Ignore freshness and refresh now, but still keep showing existing local data while the refresh runs.

Recommended freshness budgets:

- Book catalog metadata: 6 hours
- Book bookmarks: 2 minutes on detail entry, immediate local writes after bookmark actions
- Playback progress and finished state: event-driven locally, server reconciliation on app foreground, playback stop, explicit refresh, or error recovery
- Series memberships: 15 minutes
- Series summary metadata: 1 hour

These values are intentionally conservative enough to avoid visible staleness while still preventing repeated server fetches on normal navigation.

## UI Behavior Rules

### Book Detail

On page entry:

- Subscribe to `observeBookDetail(bookId)`.
- Render cached data immediately if present.
- Trigger `refreshBookDetail(bookId, IfStale)`.
- Show a small inline progress indicator if background refresh is running.
- Show full-screen loading only if there is no cached row yet.

While page is open:

- Do not poll full detail.
- Progress comes from playback state and local mutation streams.
- Bookmark changes update local tables immediately, then sync.
- Explicit pull-to-refresh uses `Force`.

### Series Detail

On page entry:

- Subscribe to `observeSeriesDetail(seriesId)`.
- Render cached series contents immediately if present.
- Trigger `refreshSeriesDetail(seriesId, IfStale)`.
- Show full-screen loading only if there is no local series detail at all.

While page is open:

- No timer-based refresh loop.
- Collapse or expand subseries in the view model or use case, not in persistence.
- Explicit pull-to-refresh uses `Force`.

## Repository Contract Direction

The repository should stop returning detail screens as fetch-only calls.

Direction:

- `observeBookDetail(bookId): Flow<BookDetailAggregate?>`
- `refreshBookDetail(bookId, policy: RefreshPolicy): AppResult<Unit>`
- `observeSeriesDetail(seriesId): Flow<SeriesDetailAggregate?>`
- `refreshSeriesDetail(seriesId, policy: RefreshPolicy): AppResult<Unit>`

Optional helpers:

- `seedBookDetailFromSummary(bookId, summary)`
- `markBookRefreshRunning(bookId)`
- `observeBookRefreshState(bookId)`

The key rule is:

- screens observe local state
- refresh methods populate local state
- network results do not update screens directly

## Concurrency Rules

To avoid duplicate work and race conditions:

- Only one in-flight refresh per resource key.
- Multiple callers requesting the same refresh should join the same job.
- Writes from user actions should patch local state first where safe.
- Server responses must not overwrite newer local progress state with stale values.

## Invalidation Rules

Use targeted invalidation only.

Invalidate or patch:

- `book_summaries` and `book_details` for the affected book after:
  - mark finished
  - reset progress
  - bookmark create/update/delete
  - collection or playlist actions only if the current screen depends on that state
- `series_memberships` for the affected series after:
  - explicit series refresh
  - a library-wide content refresh that includes that series

Do not clear all caches just because one detail action occurred.

## Migration Plan

### Phase 1: Foundation

- [x] Add Room entities and DAOs for persistent detail data.
- [x] Add mappers between API DTOs and local entities.
- [x] Add refresh policy enum and in-flight refresh deduping.
- [x] Add repository methods that write detail data into Room.
- [x] Keep existing screen code untouched until local flows are available.

Acceptance criteria:

- A book detail record can be written and read locally without the UI.
- A series detail record can be written and read locally without the UI.
- Two simultaneous refreshes for the same book result in one network request.

### Phase 2: Book Detail Migration

- [x] Replace fetch-only book detail screen loading with Room observation.
- [x] Remove per-second full-detail polling from the book page.
- [x] Split progress and bookmarks handling from static catalog detail.
- [x] Keep cached detail visible during background refresh.
- [x] Restrict full-screen loading to cache-miss only.

Acceptance criteria:

- Re-opening a previously viewed book detail page does not show a blocking loading screen if local data exists.
- The book page does not call the detail endpoint every second.
- Progress stays current while playing without full-detail polling.

### Phase 3: Series Detail Migration

- [x] Persist raw series membership data.
- [x] Replace fetch-only series detail screen loading with Room observation.
- [x] Move collapse/expand behavior out of cache storage and into presentation logic.
- [x] Keep cached series contents visible during background refresh.

Acceptance criteria:

- Re-opening a previously viewed series page does not block on a full-screen loading state if local data exists.
- Series detail does not refetch on every navigation within freshness budget.
- Collapse/expand preference does not require recomputing the series from the network.

### Phase 4: Cleanup

- [x] Remove old in-memory detail caches that are no longer needed.
- [x] Remove screen-specific loading workarounds built around fetch-only detail calls.
- [x] Keep only memory caches that still have a clear role, such as tiny short-lived image or browse helpers.

Acceptance criteria:

- There is one clear detail-loading path per resource.
- No screen depends on fetch-returned detail as its primary state source.

## Test Plan

### Repository Tests

- [ ] `IfMissing` skips network when local data exists.
- [ ] `IfStale` refreshes only after freshness threshold.
- [ ] `Force` refreshes regardless of freshness.
- [ ] In-flight refresh dedupe prevents duplicate requests.
- [ ] Server response does not overwrite newer local progress with older state.

### UI/ViewModel Tests

- [ ] Book detail shows cached content immediately when available.
- [ ] Book detail shows full-screen loading only on true cache miss.
- [ ] Series detail shows cached content immediately when available.
- [ ] Pull-to-refresh keeps visible content on screen while refreshing.

### Manual Verification

- [ ] Open the same book detail twice in a row and confirm no blocking loading screen on second open.
- [ ] Open the same series detail twice in a row and confirm no blocking loading screen on second open.
- [ ] Play a book from detail page and confirm progress updates without repeated detail endpoint fetches.
- [ ] Kill and relaunch the app, then reopen a viewed detail page and confirm persistent local data is shown first.

## Non-Goals For This Work

- Rewriting the whole browse stack.
- Changing unrelated playback behavior.
- Introducing a general offline download sync system beyond what detail screens need.
- Migrating Android Auto in this branch.

## Recommended Execution Order

When we implement this, the safest order is:

1. Phase 1 foundation
2. Book detail migration
3. Test and stabilize
4. Series detail migration
5. Test and stabilize
6. Cleanup

This keeps the highest-churn screen, book detail, isolated first and avoids mixing two migrations at once.
