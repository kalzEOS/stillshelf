# Android Auto Implementation Checklist

## Goal

Add Android Auto support for both Audiobookshelf and Navidrome without disturbing existing phone playback, sign-in flows, or non-car UI behavior.

This plan is intentionally incremental. Each step should be implemented and verified before moving to the next one.

## Constraints

- Keep changes tightly scoped to Android Auto only.
- Do not change existing phone playback UX unless required for Android Auto compatibility.
- Do not break ABS-only or NV-only user setups.
- Prefer one shared Android Auto media service for the whole app, with separate browsable roots for ABS and Navidrome.

## Official References

- Android Auto media app support:
  - https://developer.android.com/training/cars/media/auto
- Build a media browser service:
  - https://developer.android.com/media/legacy/audio/mediabrowserservice
- Media3 controllers and browsers:
  - https://developer.android.com/media/media3/session/connect-to-media-app
- Car app testing guidance:
  - https://developer.android.com/training/cars/testing

## Phase 1: Platform Wiring

- [x] Add Android Auto app metadata to the manifest.
- [x] Add `res/xml/automotive_app_desc.xml` with `<uses name="media"/>`.
- [x] Decide the service base:
  - Chosen: shared `MediaBrowserServiceCompat`
- [x] Confirm whether a Media3 session dependency is needed for the chosen path.
  - Not needed for the MVP compat-service implementation.

Notes:
- Android Auto discovery requires manifest metadata plus a browsable media service.
- Playback notification and `MediaSessionCompat` alone are not enough.

## Phase 2: Shared Service Skeleton

- [x] Create one shared Android Auto media service for the app.
- [x] Expose a session token from the service.
- [x] Keep service startup safe when no activity is open.
- [x] Make the service safe when the user is not signed in.
- [x] Return a valid root for supported clients.

Notes:
- Android docs explicitly call out startup scenarios where the media service runs before any activity and while no UI can be shown.

## Phase 3: Content Hierarchy

- [x] Define stable media IDs and browsable roots.
- [x] Add top-level root entries:
  - [x] `Audiobookshelf`
  - [x] `Navidrome`
- [ ] Add a minimal ABS hierarchy:
  - [x] Continue Listening
  - [x] Recent / Home
  - [ ] Authors or Series
- [ ] Add a minimal Navidrome hierarchy:
  - [x] Replace early placeholder browse tree with a car-friendly music hierarchy
  - [x] Artists
  - [x] Albums
  - [x] Songs
  - [x] Playlists

Notes:
- Start small. We do not need every phone surface on day one.
- The first version only needs a sane, browseable structure that plays correctly.

## Phase 4: Playback Handoff

- [x] Wire ABS playable items into `PlaybackController`.
- [x] Wire Navidrome playable items into `NavidromePlayerController`.
- [x] Support play from browsed item.
- [ ] Support play from playlist/album/artist container where applicable.
- [x] Keep media session state correct enough for Android Auto to switch between ABS and Navidrome now-playing sessions.
- [x] Fix Android Auto ABS transport state on first playback so a freshly started book can be paused/resumed without first starting Navidrome content.
- [x] Support queue / up-next display for Navidrome playback.
- [x] Support direct play and shuffle from Navidrome artist / album / playlist containers.
- [x] Add first Android Auto player custom actions:
  - [x] ABS playback speed
  - [x] ABS sleep timer
  - [x] ABS bookmark
  - [x] ABS chapter jump
  - [x] Navidrome shuffle
  - [x] Navidrome repeat
  - [x] Navidrome favorite

Notes:
- The app already has playback controllers and media sessions. Android Auto needs a browsable entry point and reliable play commands on top of that.

## Phase 5: Search and Voice

- [x] Add Android Auto search support.
- [x] Route search to ABS browse/search sources.
- [x] Route search to Navidrome search sources.
- [x] Fix Android Auto search relevance so non-English and Navidrome-first queries do not fall back to unrelated ABS books.
- [ ] Support voice-triggered play where practical.

Notes:
- This is important, but it should come after basic browse and play are working.

## Phase 6: Hardening

- [ ] Handle signed-out state gracefully in the service.
- [ ] Handle empty-library state gracefully.
- [ ] Handle no-network / server-unavailable state gracefully.
- [ ] Make sure the service does not crash when launched cold by Android Auto.
- [ ] Verify the service does not disturb normal phone playback notifications.

## Testing Checklist

- [x] Android Auto can discover the app.
- [x] App opens in Android Auto media list.
- [x] ABS root appears and is browseable.
- [x] Navidrome root appears and is browseable.
- [ ] Playing ABS content starts audio correctly in car mode.
- [ ] Playing Navidrome content starts audio correctly in car mode.
- [ ] Transport controls work from Android Auto.
- [ ] App survives cold start from Android Auto.
- [ ] App behaves correctly when user is signed into only ABS.
- [ ] App behaves correctly when user is signed into only Navidrome.
- [ ] App behaves correctly when user is signed into both.

## Current Status

- [x] Initial codebase audit complete.
- [x] Official Android Auto docs reviewed for discovery and service requirements.
- [x] Implementation started.

## Deployment

- [ ] Keep Android Auto work isolated on a dedicated integration branch named `beta-android-auto`.
- [ ] Do not release from `beta-android-auto`; merge it into `beta` only when the Android Auto slice is stable enough for beta testing.
- [ ] Keep `beta` as the only beta release branch and `main` as the only stable release branch.
- [ ] Continue using normal feature branches for non-Android-Auto work, based on `beta` or `main` as appropriate.

## Change Log

- 2026-03-20: Created implementation checklist and confirmed the main gap: the app has media sessions and playback notifications, but no Android Auto-discoverable browsable media service yet.
- 2026-03-20: Added Android Auto manifest metadata, `automotive_app_desc.xml`, and a shared `MediaBrowserServiceCompat` skeleton with safe signed-out handling.
- 2026-03-20: Added the first browse tree: Audiobookshelf continue/recent, and Navidrome recent albums, songs, and playlists.
- 2026-03-20: Wired browsed book and track items into the existing ABS and Navidrome playback controllers, and added a mirrored Android Auto media session for transport state.
- 2026-03-20: Local verification passed with `:app:compileGithubDebugKotlin` and `:app:testGithubDebugUnitTest`. Android Auto emulator testing is still required.
- 2026-03-21: Confirmed the Desktop Head Unit package is already installed in the local Android SDK at `extras;google;auto` and added a CachyOS-friendly launcher helper at `scripts/run_dhu.sh`.
- 2026-03-21: Fixed cold-start Android Auto crashes caused by main-thread lifecycle-bound Navidrome singletons being initialized off the main thread inside the car service.
- 2026-03-21: Fixed shared car-session ownership so starting Navidrome content from Android Auto can take over the now-playing session instead of staying stuck on ABS.
- 2026-03-21: Expanded the Navidrome Android Auto browse tree to a more standard car music hierarchy: Artists, Albums, Songs, and Playlists.
- 2026-03-21: Added Android Auto search support through the shared media browser service, but search relevance still needs hardening for Navidrome-heavy and non-English queries.
- 2026-03-21: Fixed Android Auto search relevance by returning one relevance-ranked list across ABS authors/books and Navidrome artists/albums/songs instead of falling back to unrelated ABS books.
- 2026-03-21: Fixed first-run ABS Android Auto pause/resume routing by making the car service target the backend that is actually playing or loading rather than the phone's last-selected backend.
- 2026-03-21: Added Android Auto queue support for Navidrome plus direct Play/Shuffle browse items for artist, album, and playlist containers.
- 2026-03-21: Added first Android Auto player custom actions for ABS (speed, sleep timer, bookmark, chapter jump) and Navidrome (shuffle, repeat, favorite).
