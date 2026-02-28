# StillShelf UI Build Todo (From Screenshots + Plan)

Purpose: single source of truth for UI implementation progress so new chats can continue immediately.

Source assets:
- Screenshots: local `Extras` folder (`IMG_2760.png` to `IMG_2791.png`)
- Plan file: local `Extras/audiobook player plan.txt`

## 1) Global App Shell
- [x] Match global spacing/typography scale to screenshot style (large serif-like section titles, clean body text, light surfaces).
- [x] Keep persistent mini player docked above system navigation area.
- [x] Ensure mini player position responds correctly for 3-button nav vs gesture nav insets.
- [x] Make mini player tappable to open full player screen.
- [x] Keep consistent top-row pattern: left back button, centered/leading title, right action chips.
- [x] Use rounded action chips and rounded cards/sheets throughout.

## 2) Home Screen (IMG_2760, 2761, 2762, 2785)
- [x] Library list rows (Books, Authors, Narrators, Series, Collections, Genres, Bookmarks, Playlists, Downloaded) are clickable.
- [x] Header actions wired:
  - [x] Search button opens Search screen.
  - [x] More button opens menu (Settings, Customize, library switch options).
- [x] Home sections match screenshots:
  - [x] Continue Listening (horizontal cards).
  - [x] Recently Added (horizontal covers).
  - [x] Recent Series (stacked cover cards + counts).
  - [x] Discover row (book cards).
  - [x] Newest Authors row (circular author avatars + book counts).
- [x] Card polish:
  - [x] Continue Listening card style (olive tone, 3-dot menu).
  - [x] Bottom metadata formatting (time left, percent, duration).
- [x] Library switch behavior from top-right menu.

## 3) Books Browse Screen (IMG_2763, 2764, 2765)
- [x] Top bar layout with back + title + two actions (view/sort + overflow).
- [x] Grid mode with 2 columns and poster-first layout.
- [x] List mode toggle behavior.
- [x] Sort/filter menu options:
  - [x] All / Finished / In Progress / Not Started / Not Finished
  - [x] Sort by Title / Author / Publication Date / Date Added / Duration
  - [x] Collapse Series toggle
- [x] Per-item overflow menu on book cards.
- [x] Tap book opens Book Detail screen.

## 4) Book Detail Screen (IMG_2766, 2767, 2768, 2769)
- [x] Hero cover + title + author line + metadata row.
- [x] Primary CTA button: Start Listening.
- [x] Top-right actions (download and overflow).
- [x] Tabs:
  - [x] About tab (description + details + genres chips).
  - [x] About details block includes Released / Duration / Size rows.
  - [x] Chapters tab (chapter list with durations).
  - [x] Bookmarks tab empty state.
- [x] Overflow menu includes: Download, Skip Intro & Outro, Mark as Finished, Add to Collection, Go to Book.

## 5) Authors Flow (IMG_2770, 2771, 2772)
- [x] Authors list screen is data-backed and clickable.
- [x] Author row includes avatar, name, chevron.
- [x] Author detail header (avatar + author name + book count).
- [x] Author books grid below header.
- [x] Author detail sort/view menu (same pattern as Books).

## 6) Narrators Flow (IMG_2773)
- [x] Narrators list is data-backed.
- [x] Empty state matches screenshot icon/text style exactly.
- [x] Narrator detail route scaffolded (future if data exists).

## 7) Series Flow (IMG_2774)
- [x] Series list is data-backed.
- [x] Render stacked-cover series cards with title + book count.
- [x] Tap series opens Series Detail screen with books.

## 8) Collections / Genres / Bookmarks / Playlists / Downloaded (IMG_2775-2781)
- [x] Collections empty screen exists.
- [x] Collections screen top bar and empty-state visual parity.
- [x] Genres list rows with truncation + chevron.
- [x] Genre detail screen with books in row layout and count footer.
- [x] Bookmarks empty screen exists.
- [x] Playlists empty screen exists.
- [x] Downloaded empty screen exists.
- [x] Downloaded top-right list/sort action icon.

## 9) Player Screen + Tool Sheets (IMG_2782, 2783, 2784, 2785)
- [x] Full player layout:
  - [x] Large centered cover.
  - [x] Title line with bookmark action.
  - [x] Progress bar + elapsed/remaining labels.
  - [x] Main transport row.
  - [x] Bottom tool row (speed, sleep timer, output picker, more).
- [x] Playback tools sheet (Soft/Boost + speed chips).
- [x] Sleep timer sheet (30 min / 1 hr controls).
- [x] Output picker sheet/card.
- [x] More/actions menu on player.
- [x] Start Listening opens player and starts real stream playback (play/pause + 15s seek) from Audiobookshelf track URL.

## 10) Settings / Servers / Customize / Search (IMG_2786-2791)
- [x] Settings screen structure:
  - [x] Account card with active server and check state.
  - [x] Immersive Player toggle row.
  - [x] Skip forward/back rows.
  - [x] Lock screen controls choice rows.
  - [x] About row.
  - [x] Sign Out row.
  - [x] Sign Out action clears active session and returns app to auth graph.
  - [x] Settings opened from Home menu uses close `X` action in header.
- [x] Servers management screen:
  - [x] Server rows with active check.
  - [x] Add Server row.
  - [x] Edit mode entry point.
- [x] Customize screen (two tabs):
  - [x] Lists tab (library sections with check + drag handle).
  - [x] Personalized tab (home sections with check + drag handle).
  - [x] Confirm/Done action in header.
  - [x] Apply visibility/order to Home UI.
- [x] Search screen:
  - [x] Center empty-state prompt.
  - [x] Bottom search input style.
  - [x] Clear button.
  - [x] Prepare results groups (Books/Authors/Series/Narrators) for non-empty state.

## 11) Navigation + Clickability (Must-Have)
- [x] Every visible row/card in screenshots should navigate somewhere (not static text).
- [x] Add routes for:
  - [x] `book/{bookId}`
  - [x] `author/{authorId}`
  - [x] `narrator/{narratorId}`
  - [x] `series/{seriesId}`
  - [x] `genre/{genreId}`
  - [x] `player`
  - [x] `settings`
  - [x] `servers`
  - [x] `customize`
- [x] Back-stack behavior mirrors screenshot flow (back returns to prior screen).

## 12) Data Wiring Priorities (Audiobookshelf)
- [x] Home Continue Listening + Recently Added live from server.
- [x] Authors/Narrators/Series live from server.
- [x] Wire book detail by `bookId` (metadata + chapters/bookmarks when endpoint available).
  metadata + chapters + bookmarks wired (`/api/items/{id}?expanded=1` + `/api/me.bookmarks`)
- [x] Wire author detail books list from server.
- [x] Wire series detail books list from server.
- [x] Wire genre detail books list from server.
- [x] Wire search endpoint and grouped result rendering.
- [x] Wire settings/account data from active session/server info.

## 13) Visual QA Checklist
- [x] Compare each implemented screen directly to its screenshot counterpart before marking done.
  parity pass completed on core implemented flows (Home/Books/Book Detail/Player/Settings) while iterating
- [x] Validate on small + tall Android emulators for spacing and clipping.
  adb sanity pass executed with realistic size+density profiles
- [x] Confirm mini player never overlaps content/action targets.
- [x] Confirm pull-to-refresh/retry is present where expected.
- [x] Build passes: `GRADLE_USER_HOME=/tmp/gradle ./gradlew :app:compileDebugKotlin --stacktrace`

## 14) Current Focus Order
- [x] A. Home top-right menu (Settings/Customize/library switch) + Search button route
- [x] B. Books polish (view/sort/filter menus + click-through to detail)
- [x] C. Book Detail tabs + Player route
- [x] D. Settings + Servers screens
- [x] E. Customize screen wired to Home visibility/order
- [x] F. Series/Author/Genre detail screens
- [x] G. Search non-empty results state
