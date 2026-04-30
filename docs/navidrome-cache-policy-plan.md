# Navidrome Cache Policy Plan

## Purpose

Build a user-controlled Navidrome playback cache that feels smooth like a media app should, without making cached tracks look like permanent downloads.

This plan is intentionally incremental. Work top to bottom and check each item off as it lands.

## Scope

- Navidrome only.
- Playback cache only, not library-wide caching.
- Keep explicit downloads separate from cache.
- Preserve the playback stability fixes that already shipped.

## Goals

- Cache songs that are likely to play soon.
- Keep the cache bounded by size.
- Let users turn caching off.
- Let users clear cached music manually.
- Show a cached indicator in song and queue rows.
- Keep cached music out of the Downloaded tab.

## Non-Goals

- Do not cache the entire Songs library automatically.
- Do not merge cache state with normal downloads.
- Do not change ABS playback behavior.
- Do not build a cross-backend cache system.

## Phase 1: Cache Model

- [x] Add a dedicated Navidrome playback cache record separate from explicit downloads.
- [x] Store cache files in app-private cache storage.
- [x] Track last access time for each cached track.
- [x] Track cached byte size per account or active server context.
- [x] Keep the cache state resilient across app restarts.
- [x] Make it impossible for cached items to be mistaken for permanent user downloads.

## Phase 2: Cache Policy

- [x] Add a user-facing cache size setting.
- [x] Support `Off` as a cache-size option.
- [x] Support a small set of practical cache size choices in gigabytes or megabytes.
- [x] Enforce the size limit with LRU eviction.
- [x] Prefer evicting the least recently used cached tracks first.
- [x] Remove stale cached files when the active queue changes.
- [x] Remove stale cached files when caching is disabled.
- [x] Keep explicit downloads out of the cache eviction path.

## Phase 3: Playback Integration

- [x] Continue preferring local cached files when they exist.
- [x] Fall back to streaming when cache is missing or disabled.
- [x] Cache the current track and the near-future queue window.
- [x] Keep warmup bounded so it does not flood storage or download slots.
- [x] Preserve recovery behavior after source errors.
- [x] Make queue additions and mid-play inserts eligible for cache warmup.

## Phase 4: UI and Settings

- [x] Add a Navidrome cache section to settings.
- [x] Add a cache-size picker with `Off` and several size options.
- [x] Add a `Clear cached music` action.
- [x] Show the current cache usage and selected limit.
- [x] Add a cache badge or icon to songs that are cached.
- [x] Show the badge in queue and album/song list rows where it makes sense.
- [x] Make the cache badge visually distinct from the normal download state.

## Phase 5: Download Separation

- [x] Keep the Downloaded tab showing only explicit downloads.
- [x] Keep cache items out of the standard download state flow.
- [x] Promote a cached track to a real download if the user explicitly downloads it.
- [x] Ensure cache cleanup does not delete explicit downloads.
- [x] Ensure explicit download removal does not accidentally clear unrelated cache.

## Phase 6: Tests

- [x] Test cache-size setting parsing and `Off`.
- [x] Test LRU eviction when cache exceeds the limit.
- [x] Test that cached items are not counted as normal downloads.
- [x] Test that the Downloaded tab ignores cache-only items.
- [ ] Test that the cache badge appears for cached rows. (UI/Compose — skipped; pure logic covered above)
- [x] Test local-cache-first playback resolution.
- [x] Test stream fallback when cache is unavailable.
- [ ] Test that explicit downloads still work normally. (covered by existing NavidromeDownloadReconciliationTest)
- [x] Test that cache pruning does not remove permanent downloads.

## Implementation Order

1. Cache model and metadata.
2. Cache size setting and eviction policy.
3. Playback integration.
4. UI badge and settings screen.
5. Download separation cleanup.
6. Tests.

## Done Criteria

- Navidrome cache can be turned on or off by the user.
- Cache stays bounded and self-prunes.
- Cached songs are visible as cached, not as downloaded.
- Explicit downloads still behave exactly like downloads.
- Playback still feels smooth and recovers cleanly after failures.

