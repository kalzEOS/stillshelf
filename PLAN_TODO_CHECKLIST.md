# StillShelf Plan TODO Checklist

Source: `/home/kalzi/Desktop/audiobooks app plan`

## Residual Risk / Testing Gap
- [ ] Add UI/instrumentation tests for Home-button navigation and saved view-mode restore.

## Major Functional Issue
- [x] Fix add-to-collection so it actually adds books.

## Current Batch TODO
- [x] In Settings > Lock Screen Controls, remove right chevrons from `Next/Previous` and `Skip Forward/Back`.
- [x] In Settings > Lock Screen Controls, show only checkmark for selected option and nothing for unselected option.
- [x] In Settings > Skip Buttons, add selectable durations for both forward/backward: `10`, `15`, `30`, `45`, `60` seconds.
- [x] Wire selected skip durations into actual player behavior (mini player + fullscreen player).
- [x] Verify all 3-dot UI options persist across screen exits/re-entry, not only in Books.
- [ ] Keep Home button in its own circle next to mini player and ensure spacing/sizing is consistent. Make mini player narrower and decrease its hight and its text to work with the new home button
- [x] Auto-advance to next chapter when current chapter ends (do not stop playback).
- [x] In Chapters tab, show correct per-chapter times.
- [x] In fullscreen player, show progress/time metadata for the current chapter, not mismatched values.
- [x] Preserve last selected book tab (`About`/`Chapters`/`Bookmarks`) when opening/minimizing fullscreen player.
- [x] Implement Books/Series/Discover 3-dot menu actions: `Add to collection`, `Mark as finished`, `Download`/`Remove download`.
- [x] Implement Home > Continue Listening 3-dot menu actions: `Go to book`, `Add to collection`, `Mark as finished`, `Remove from Continue Listening`, `Download`/`Remove download`.
- [x] Implement Home > Recently Added 3-dot menu actions: `Add to collection`, `Mark as unfinished`, `Download`/`Remove download`.
- [x] Show more entries in Home > Recent Series.
- [x] Make Home > Recent Series cover size/style match other home shelves.
- [x] Make the player show on the phone's lockscreen and wherever else the phone shows playing things. Like on my oneplus 12, whatever I play, a song or a video, the player and its controls will show in the lockscreen so I can control things while the phone is locked, and it also shows the player in the drop down notification area (when you swipe down from the top of the phone screen and get that android notification menu if you you know what I am talking about), this will be phone dependent (I think) because different android phones do different things with whatever is playing (figure that out)

## Hotfix Batch (Current)
- [ ] Collections list entries open to a collection-books detail screen so added books are visible in-app. `Reported still inconsistent.`
- [x] Downloaded tab renders downloaded books from saved download state without disappearing/requiring re-entry. `Rewired to DownloadManager-backed state + completed-file validation.`
- [x] Series detail single-book 3-dot menu shows `Add to collection`, `Mark as finished`, and `Download`/`Remove download` on every item. `Also respects active in-progress downloads for label state.`
- [x] Download state labels/menu + Downloaded tab visibility stay in sync across all surfaces. `Completed-only = downloaded; in-progress = progress state.`
- [x] Series detail ordering maps correctly (`#1 -> first item`) and does not invert on some series. `Removed misleading chapter-title sequence inference.`
- [x] Media notification/lockscreen metadata now publishes large artwork for current book (when cover is available). `Pixel confirmed; OnePlus lockscreen behavior appears OEM-limited.`

## New Device-Test Regressions
- [ ] Fix cold-start Books placeholders that stay blank until leaving/re-entering tab.
- [ ] Remove page-entry hitch on book-heavy screens (Books, series details, similar routes).
- [ ] Keep mini player floating style consistent on all screens (no extra background layer behind it).
- [ ] Keep mini player fully above system nav bar on all screens/devices.
- [ ] Align fullscreen player bottom action row consistently (Speed/Timer/History/Download/More baseline and spacing).

## Latest Requests Batch
- [x] Implement real offline download (actual file download) so downloaded books remain available without Wi‑Fi/cellular. `Playback now prefers completed local file if present.`
- [x] Add visible per-book download progress indicator (queued/downloading/completed). `Extended to Home shelves + Books/Series stack cards + Book detail/Series detail cards.`
- [x] Replace ambiguous download icon state with explicit circular downloader UI on each book:
  - [x] When download starts, show a clearly visible circle on the book cover/card.
  - [x] Show real progress as a ring around the circle + numeric `%` in the center.
  - [x] When download completes, replace progress circle with downloaded-state down-arrow icon.
  - [x] On remove download, delete local file(s), remove downloaded-state icon, and ensure offline play no longer works for that removed book.
  - [x] Verify tapping `Start/Continue Listening` on a downloaded book opens and plays that exact book offline (no network required, no fallback to currently-loaded book). `Implemented in controller; device validation pending.`
- [x] Clarify and improve `Mark as finished` behavior (clear user feedback and expected state change). `Success feedback now explicitly states 100% completion / reset.`
- [ ] Redesign Collections flow: user-created collections + picker when adding books + create/rename/delete management. `In progress: create/rename/delete + collection-item removal implemented; ABS no-books fallback now seeds create-from-book then removes seed item; add-to-collection picker still pending.`
- [x] Make 3-dot actions in Collections tab functional (not just opening book page). `Browse now has rename/delete actions; collection detail has remove-from-collection action.`
- [x] Clarify/implement `Skip intro & outro` behavior in 3-dot menus. `Now explicitly labeled as coming soon and no longer ambiguous.`
- [x] Fix fullscreen player progress-time row jitter/rocking during ticking updates. `Stabilized with weighted layout + monospace side timers.`
- [x] Add List/Grid toggle on Series main page (browse list of series).
- [x] Disable landscape mode app-wide.
- [x] Fix Search field position so it does not sit behind Android navigation bar.

## Data / Performance
- [x] Investigate and reduce excessive refreshes/server hits on content pages. `Done: longer repository cache age + removed automatic background home refetch when fresh cache is available.`
- [x] Implement stronger caching + sync strategy so pages open from cache first, then refresh intelligently. `Done: cache-first path kept, user-initiated pull refresh remains force-refresh path.`

## Notes
- The mini-player + home-button experiment should be done in its own branch.
- Keep this checklist as the running tracker; check items off as they are completed.
