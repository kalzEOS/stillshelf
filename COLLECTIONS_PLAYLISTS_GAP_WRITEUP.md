# Collections/Playlists Flow Gap Write-Up

## Purpose
Document why "Add to Collection" is currently failing, what behavior we actually want, and what needs to be implemented to match the expected UX (based on the 9 reference screenshots from Still).

## Current Problem Summary
- Creating a collection from our app often fails with "Unable to create collection."
- When adding a book to collection, our app auto-creates/uses a default collection (`StillShelf Collection`) instead of letting the user choose.
- Users cannot reliably choose an existing collection before adding a book.
- Playlist support is missing in this flow (user should be able to add to playlists too).
- Result: user intent is ignored, and collection management feels broken/opaque.

## What We Are Trying To Achieve
From any book's 3-dot menu, tapping `Add to Collection` should open a picker sheet/dialog that allows:
- Add book to an existing collection (if any exist).
- Create a new collection and add this book to it immediately.
- Add book to an existing playlist (if any exist).
- Create a new playlist and add this book to it immediately.

No silent auto-creation of a default collection should happen.

## What We Are Getting Instead
- App auto-routes add actions into a default collection (`StillShelf Collection`) without user selection.
- Creating a collection from UI can fail and returns generic error.
- User cannot reproduce Still-style workflow shown in screenshots.

## Screenshot-Based Expected Behavior (9-step reference)
- **1**: User taps `Add to Collection` from book menu.
- **2**: Add sheet opens with editable row for creating a new collection.
- **3**: Existing collection list is visible; user can pick one.
- **4**: Playlist section mirrors collection behavior (new + existing).
- **5**: Collections tab then shows created collection cards.
- **6**: Opening a collection shows collection header + contained books.
- **7**: Collection detail supports grid/list layout toggle.
- **8**: Collection detail supports delete action.
- **9**: Inside collection item menu, user can remove book from collection and run other actions.

## Functional Gap Analysis
- Missing explicit "choose destination" flow for collection/playlist add.
- Incorrect fallback behavior (forced default collection) masks backend failures and user intent.
- Collection create endpoint usage appears incompatible for some ABS server states (possible "must include at least one item" rule, depending on ABS version/config).
- Playlist create/add flow is not wired in the same unified picker.

## Required Implementation Changes

### 1) Replace Default-Collection Shortcut
- Stop calling "add to default collection" from book menus.
- Route all add actions through a unified `AddToList` sheet/dialog.

### 2) Unified Add Sheet (Collection + Playlist)
- Show two sections:
  - `Collections`
  - `Playlists`
- In each section:
  - `+ New ...` row
  - existing items list with counts
- Selecting an existing item adds the current book immediately.

### 3) Create-Then-Add Behavior
- For new collection/playlist:
  - create target
  - add current book to the newly created target in same transaction flow
- If create-only API fails but create-with-book API exists/works, use create-with-book path.

### 4) Server Compatibility Strategy (ABS Variants)
- Handle both cases:
  - server allows empty collection creation
  - server requires first book during creation
- Fallback order:
  1. try create empty
  2. if rejected with known validation error, retry create-with-book

### 5) UI Feedback and State Updates
- Success: toast/snackbar with specific action result:
  - `Added to "<collection name>"`
  - `Created "<collection name>" and added book`
- Failure: preserve actionable error text (no generic-only message).
- Optimistically update relevant screens:
  - Collections list
  - Collection detail
  - Book menu state if needed

### 6) Collection Detail Actions
- Keep per-collection page with:
  - grid/list toggle
  - delete collection
  - book item menu including `Remove from Collection`

### 7) Playlist Parity
- Apply same UX structure and create/add behavior to playlists.

## Acceptance Criteria
- User can create a named collection from add sheet and the chosen book appears in it.
- User can add to any existing collection from the same sheet.
- No automatic forced use of `StillShelf Collection`.
- User can create playlist + add same book in one flow.
- Book can be removed from collection from collection detail screen.
- Errors are specific and explain what failed (create vs add).

## Suggested Technical Approach
- Replace existing `addBookToDefaultCollection(bookId)` calls in UI with:
  - open `CollectionPicker`/`AddToList` UI
  - submit explicit target selection or new-name creation
- Add/confirm repository methods:
  - `createCollection(name)`
  - `createCollectionWithBook(name, bookId)` (fallback)
  - `addBookToCollection(collectionId, bookId)`
  - equivalent playlist methods
- Keep one source of truth in ViewModel state for:
  - available collections/playlists
  - create/add loading state
  - last action result

## Why This Matters
Collections/playlists are organizational core features. If users cannot trust where a book is added, this breaks a primary reason to use the app over basic library browsing.
