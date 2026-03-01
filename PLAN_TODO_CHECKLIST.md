# StillShelf Plan TODO Checklist

Source: `/home/kalzi/AndroidStudioProjects/Extras/plan.md`

## Auth / Server Flow
- [x] Center `Add Server` and `Login` screen components; shift layout up when keyboard opens.
- [x] Redesign first-launch `Servers` empty state for reachability on tall phones (move CTA/content lower and improve visual hierarchy).
- [x] Show friendly, specific login errors (wrong username/password vs connection/server issues).
- [x] Move login error message/snackbar placement to a top-visible region so it stays visible when keyboard is open.
- [x] On sign out, navigate to `Add Server` screen (not into an empty in-app state). 
- [x] Validate server name input; block continue when invalid and show red error text.
- [x] Disable autocorrect on login fields.

## Server Management / Settings
- [x] Make `Manage Servers` actually useful (edit/manage existing server entries).
- [x] Hide mini player on `Manage Servers` / `Add Server` screens from settings flow.
- [x] Change top server row behavior in settings:
  - [x] Either make it non-clickable, or
- [ ] Route to a dedicated server detail screen (server info / headers / advanced options). `Deferred` (current behavior is intentionally non-clickable row).
- [ ] Fix login -> Choose Library shows no libraries / crash. `Deferred for now by request`.
- [x] make the page that shows the libraries I have after I have logged in to be in the center, too, so it can be reachable, it is now at the very top - still showing the libraries at the top

## Player UX / Gestures
- [x] Fix fullscreen player layout differences across devices (6.8" vs 6.3" behavior).
- [ ] Make fullscreen player drag one-to-one with finger (interactive swipe down/up). `Deferred by request`.
- [ ] Make mini player open via swipe-up gesture (one-to-one interactive drag). `Deferred by request`.
- [x] Experiment: move Home action next to mini player and resize mini player to fit.
- [x] Bottom row icons in the full screen player. Make sure the text for those icons is a bit smaller than it is now, that way some of them don't get truncated like the word "Download" now, it is showing as "Downloa".

## Data / Performance
- [ ] Investigate and reduce excessive refreshes/server hits on content pages. `In progress: repository-level content caches + stale fallback added.`
- [ ] Implement stronger caching + sync strategy so pages open from cache first, then refresh intelligently. `In progress: cache-first for books/authors/narrators/series/collections/book detail + force-refresh on pull paths.`

## Notes
- The mini-player + home-button experiment should be done in its own branch.
- Keep this checklist as the running tracker; check items off as they are completed.
