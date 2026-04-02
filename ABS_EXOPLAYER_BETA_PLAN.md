# ABS ExoPlayer Beta Plan

Working branch: `beta-abs-exoplayer-progress-parity`

Purpose:
- migrate ABS playback from `MediaPlayer` to ExoPlayer/Media3 on beta only
- restore the preferred in-app ABS output switcher after routing is stable enough
- preserve existing ABS behavior so the migration does not silently break user-facing features

How to use this file:
- treat each phase as a gate
- do not move to the next phase until the current phase is functionally stable
- check items off as they are completed
- if a phase exposes major regressions, stop and fix those before expanding scope

## Phase 0: Ground Rules

- [x] Keep this work on `beta-abs-exoplayer-migration`
- [x] Keep `main` out of the beta migration flow
- [x] Keep Navidrome behavior isolated unless shared playback infrastructure must change
- [x] Keep ABS UI contracts stable unless a controller-level change is unavoidable
- [x] Keep changes narrowly tied to the ABS ExoPlayer migration and related routing parity

## Phase 1: Discovery And Invariants

Goal:
- inventory the current ABS `MediaPlayer` behavior before replacing it

- [x] Map every `MediaPlayer`-specific usage in `PlaybackController`
- [x] Map every ABS playback entry point: play, pause, resume, seek, stop, completion, error
- [x] Map every place playback state is exposed to UI, notifications, lock screen, and Android Auto
- [x] Map every persistence path tied to playback: checkpoints, continue listening, cached state, resume state
- [x] Map every output-switching path and route-selection assumption on ABS
- [x] Map every ABS audio-effects dependency that assumes a `MediaPlayer` session ID
- [x] Write down the behavior contracts that must remain unchanged after the swap

Phase 1 findings:
- `PlaybackController` owns the ABS engine boundary directly today: `MediaPlayer` instance lifecycle, prepare/start/pause/resume/seek/release, and completion/error callbacks all live there.
- The ABS play entry path is `playBook(...)` -> `resolvePlaybackStart(...)` -> `prepareAndPlay(...)`. Re-entry within the same book uses `resume()` or `seekToPosition(...)` if the target is still inside the current track.
- Multi-track ABS behavior is not delegated to the player engine. The controller manually resolves track offsets, recreates playback when a seek crosses track boundaries, and auto-advances by replaying the same book from the next absolute position.
- Progress state is controller-owned, not player-owned: periodic updates, local checkpoint persistence, continue-listening cache updates, server sync throttling, background retry scheduling, and startup checkpoint replay all depend on controller timing.
- Bookmark behavior is position-based and controller-facing: the player engine only needs to keep `positionMs` accurate enough for bookmark creation and restore/jump flows.
- Sleep timer, chapter navigation, rewind/forward, restart-from-beginning, and stop-and-restore-progress all depend on accurate absolute position and duration reporting from the engine.
- Audio effects are tightly coupled to the engine audio session. ABS boost and soft tone are recreated from the active player session ID and must be revalidated when ExoPlayer owns the session instead.
- Output switching is also engine-coupled today. `PlaybackController` applies preferred devices directly to the active player and falls back to `AudioManager` route changes when needed.
- UI and external surfaces are relatively stable. `PlayerViewModel`, `MiniPlayerViewModel`, notifications, lock screen metadata, and Android Auto mostly consume `PlaybackController.uiState` plus its existing public commands, which means the migration should stay inside the controller first.
- Android Auto depends on the same ABS controller contract as the phone UI: current metadata, position, speed, play/pause state, rewind/forward actions, and `playBook(...)` entry behavior must remain stable.

Phase 1 invariants:
- Keep `PlaybackController` as the ABS facade during the migration. Do not rewrite the player UI or Android Auto contract first.
- Preserve absolute-position semantics across multi-track books. A user-facing ABS position must continue to mean the whole book timeline, not the current file timeline.
- Preserve checkpoint and sync behavior even if ExoPlayer emits state changes more often than `MediaPlayer`.
- Preserve audio session driven features: boost, soft tone, notification/media session behavior, and output selection cannot become second-pass cleanup items.
- Treat downloads/local playback and remote playback as equal first-class cases from the start of the migration.
- Do not let the ABS migration change Navidrome behavior unless shared infrastructure truly requires it.

## Phase 2: ExoPlayer Foundation

Goal:
- get ABS playback running on ExoPlayer without trying to solve every edge case at once

- [x] Add ABS ExoPlayer creation and release lifecycle inside `PlaybackController`
- [x] Replace ABS media source setup from `MediaPlayer` data sources to ExoPlayer media items
- [x] Support both remote ABS playback and local downloaded playback with ExoPlayer
- [x] Replace prepare/start flow with ExoPlayer prepare/play flow
- [x] Replace completion callbacks with ExoPlayer listener-based completion handling
- [x] Replace error callbacks with ExoPlayer listener-based error handling
- [x] Keep controller public methods stable from the app’s point of view

Phase 2 notes:
- ABS still uses the same `PlaybackController` facade and `uiState`; `PlayerViewModel`, mini-player, notifications, and Android Auto callers were not rewritten.
- The first ExoPlayer pass is inside the existing controller only. It swaps player creation, media item loading, prepare/play, completion, error, seek, speed, audio session effect hookup, and preferred-device routing over to Media3.
- Remote ABS playback now resolves authenticated stream URLs into an ExoPlayer HTTP data source with the same `Authorization` header behavior. Local downloads still go through `file:` / `content:` URIs.
- This is foundation work, not parity sign-off yet. Manual device validation is still needed for real playback, track-boundary seeks, output switching, bookmarks, and Android Auto.

Exit criteria:
- [ ] ABS basic playback starts
- [ ] ABS pause/resume works
- [ ] ABS seek works
- [ ] ABS stop works
- [ ] ABS playback survives basic background/foreground transitions

## Phase 3: Playback State Parity

Goal:
- make sure ExoPlayer-backed ABS reports the same useful state to the app

- [ ] Preserve `isLoading` behavior
- [ ] Preserve `isPlaying` behavior
- [ ] Preserve `positionMs` updates
- [ ] Preserve `durationMs` updates
- [ ] Preserve progress update cadence
- [ ] Preserve resume-after-pause behavior
- [ ] Preserve stop/reset behavior
- [ ] Preserve app background/foreground state handling

Exit criteria:
- [x] Player screen still behaves normally
- [x] Fullscreen bottom controls still behave normally
- [x] Mini-player state still matches real playback state

## Phase 4: Progress, Checkpoints, Resume, Bookmarks

Goal:
- prevent silent regressions in stateful ABS features

- [x] Preserve local checkpoint saving
- [x] Preserve server progress sync timing and throttling
- [x] Preserve resume-position accuracy after pause/background/relaunch
- [x] Preserve finished-state detection
- [x] Preserve continue-listening updates
- [x] Preserve bookmark creation at current position
- [x] Preserve bookmark restore/jump behavior
- [x] Preserve stop-and-restore-progress behavior
- [x] Preserve restart-from-beginning behavior

Phase 4 progress:
- Added direct unit coverage for local-vs-server progress preference, checkpoint replay matching, restart-from-beginning thresholds, finished-state detection, continue-listening summary math, shared bookmark/chapter jump decisions, stop-and-restore progress clamping, checkpoint/background-sync save policy, and local-vs-remote ABS source resolution.

Exit criteria:
- [x] Checkpoints still save correctly
- [x] Resume position is stable after app restart
- [x] Bookmarks still land on the expected time
- [x] Server sync still reflects real playback position

## Phase 5: Chapter And Track Parity

Goal:
- keep ABS audiobook navigation behavior intact

- [x] Preserve chapter boundary detection
- [x] Preserve previous/next chapter behavior
- [x] Preserve chapter auto-advance
- [x] Preserve multi-track audiobook handling
- [x] Preserve track-boundary seeking at the correct absolute position
- [ ] Preserve end-of-book completion behavior

Phase 5 progress:
- Added direct unit coverage for chapter-index tolerance around boundaries and for track selection when seeking across or past track edges.

Exit criteria:
- [x] Chapter navigation feels unchanged
- [x] Crossing track boundaries does not corrupt progress or playback

## Phase 6: Audio Controls And Interruptions

Goal:
- make sure all non-routing playback controls still work correctly on ExoPlayer

- [ ] Preserve play/pause
- [ ] Preserve rewind/forward skip behavior
- [ ] Preserve seek bar drag and commit behavior
- [x] Preserve playback speed control
- [x] Preserve sleep timer behavior
- [ ] Preserve audio focus handling
- [ ] Preserve noisy-audio handling like unplug/disconnect pause behavior

Phase 6 progress:
- Added direct unit coverage for playback-speed stepping and the end-of-chapter boundary selection math used by the sleep timer. Timer countdown and interruption behavior still need real playback validation.

Exit criteria:
- [x] Common player controls feel unchanged
- [ ] Interruptions do not leave playback in a broken state

## Phase 7: Audio Effects

Goal:
- move ABS sound shaping safely onto ExoPlayer-backed sessions

- [ ] Map audio session handling to ExoPlayer
- [x] Preserve boost behavior
- [x] Preserve soft tone behavior
- [ ] Verify effect enable/disable behavior across player recreation
- [ ] Verify no timing bug breaks effect initialization

Exit criteria:
- [x] Boost works
- [x] Soft tone works
- [ ] Effects remain stable after pause/resume and source changes

## Phase 8: Notifications, Lock Screen, Android Auto

Goal:
- preserve all external playback surfaces

- [ ] Preserve notification appearance and controls
- [x] Preserve lock screen metadata updates
- [x] Preserve lock screen previous/next/play/pause actions
- [ ] Preserve media session active/inactive behavior
- [x] Preserve paused-state background behavior
- [x] Preserve Android Auto browse/playback behavior for ABS
- [x] Preserve Android Auto playback controls and metadata

Exit criteria:
- [ ] Notification controls work
- [x] Lock screen controls work
- [x] Android Auto behavior is verified on DHU

Phase 8 progress:
- Added direct unit coverage for lock-screen control mode normalization and the double-press previous-after-restart decision path. Android Auto and lock-screen behavior still need real device or DHU validation.

## Phase 9: Output Switching

Goal:
- restore the preferred in-app ABS output switcher on top of a more stable player foundation

- [ ] Reintroduce the ABS in-app output switcher once ExoPlayer route control is stable
- [x] Reintroduce the ABS in-app output switcher once ExoPlayer route control is stable
- [x] Verify Bluetooth -> speaker switching while playing
- [x] Verify speaker -> Bluetooth switching while playing
- [x] Verify switching while paused
- [x] Verify switching after app background/foreground transitions
- [x] Verify switching after backend switches
- [x] Prevent stale route state after switching outputs
- [x] Prevent stale playback leaking between ABS and NV

Exit criteria:
- [x] ABS in-app output switcher behaves reliably enough for beta
- [x] Switching does not cause repeated audio, broken pauses, or stuck routes

## Phase 10: Regression Sweep

Goal:
- catch migration fallout before beta shipping

- [x] Run compile validation
- [x] Run focused unit tests
- [ ] Add or update tests for migrated controller behavior where practical
- [x] Manual ABS playback pass on device
- [x] Manual ABS + NV switching pass on device
- [ ] Manual downloads/local playback pass
- [x] Manual Bluetooth/speaker switching pass
- [x] Manual bookmarks/checkpoints/resume pass
- [x] Manual Android Auto pass on DHU

Phase 10 progress:
- Compile validation and the focused ABS parity unit suite are passing on the current beta branch.

## Phase 11: Beta Readiness

Goal:
- decide whether the migration is safe enough to ship to beta users

- [ ] Confirm no playback-dead issues remain
- [ ] Confirm no routing-dead issues remain
- [ ] Confirm no progress-loss or checkpoint-loss issues remain
- [ ] Confirm no misleading UI state remains
- [ ] Document known rough edges clearly
- [ ] Prepare beta release notes in user-friendly language

## Must-Not-Break Checklist

These are the features most likely to break when changing player engines:

- [ ] play/pause/resume
- [ ] seek/rewind/forward
- [ ] playback speed
- [ ] sleep timer
- [ ] bookmarks
- [ ] local checkpoints
- [ ] continue listening
- [ ] server progress sync
- [ ] backgrounded paused-state behavior
- [ ] resume after relaunch
- [ ] chapter navigation
- [ ] multi-track audiobook boundaries
- [ ] downloads/local playback
- [ ] notification controls
- [ ] lock screen controls
- [ ] Android Auto
- [ ] audio focus / interruptions
- [ ] audio effects
- [ ] output switching
- [ ] ABS <-> NV backend switching isolation

## Current Next Step

- [ ] Validate the first ExoPlayer-backed ABS controller pass on device, then fix parity gaps in progress, chapter boundaries, and output switching before expanding scope
