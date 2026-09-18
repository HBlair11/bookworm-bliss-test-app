# Phase 8 — MainActivity Decomposition

**Goal.** Break the 1,833-line `MainActivity` into the seven components the
blueprint names, so the Activity is reduced to pure screen wiring: layout
inflation, launcher/observer registration, lifecycle forwarding, the options
menu and its dialogs, and the reader/details intents.

Blueprint rule being applied: *"If a piece of logic has an independent
responsibility, it should have an appropriate home outside the Activity.
If it's merely screen wiring, it can remain in the screen."*

MainActivity is now 624 lines, ~66% smaller, with every behavior moved
verbatim — including the patch notes that explain each historic fix.

---

## New package: `com.epubreader.app.ui.shelf`

```
ui/shelf/
├── ShelfStateStore.kt           pure shelf-navigation state machine:
│                                  Recently-Added enter/exit, parent-list
│                                  rules, top-reset flags, save/restore
├── ScrollStateStore.kt          pure scroll-restore state machine: per-view
│                                  anchor map + pending-restore key + the
│                                  restore decision (exact first-visible vs.
│                                  re-locate clicked book by id)
│                                  + ScrollAnchor/RestoreTarget types
├── EmptyStateRows.kt            shared dynamic-row builder for the Folders
│                                  and Settings screens (settings rows, the
│                                  Screen On toggle, inline buttons)
├── HomeScreenController.kt      the curated Home screen (Continue Reading
│                                  hero + 8 horizontal shelves + cached
│                                  HomeContent)
├── LibraryScreenController.kt   the main shelf RecyclerView: book/row
│                                  adapters, grid/list config, empty states,
│                                  scroll capture/restore, swipe helper
├── FolderImportController.kt    Folders screen + folder scan, metadata
│                                  refresh, multi-file import, scan results
├── SettingsScreenController.kt  Settings screen rows
└── AppNavigationController.kt   drawer, titles, up-affordance, FAB,
                                  applyView() fan-out, back transitions
```

### Boundary decisions

- **Stores are pure Kotlin.** `ShelfStateStore` and `ScrollStateStore` hold
  no View, Adapter, RecyclerView, or Context references. The Activity /
  controllers feed them raw values (first-visible position/offset, clicked
  book id/index) and apply the decisions they get back. That makes the
  trickiest logic in the old MainActivity — the Patch 10/11/12 scroll and
  reset contracts — unit-testable on the JVM.
- **The ViewModel stays the single source of truth for the current view.**
  The stores hold only the transient navigation context the ViewModel
  deliberately does not persist (the Recently-Added pre-entry shelf, the
  pending-restore key, the top-reset flags). Every transition the stores
  compute is applied through `setView` / `clearDetail` / `openDetail`.
- **applyView is split, not moved wholesale.** `ShelfStateStore` computes
  transitions; `AppNavigationController.applyView` performs the UI
  orchestration (drawer selection, title, FAB, menu invalidation, dispatch
  to the screen controllers).
- **Observers stay in the Activity** — they cross controllers by nature.
  The content observer routes: Settings → `settingsController.show()`,
  Folders → `folderController.showFoldersView()`, Collections placeholder →
  Activity, everything else → `libraryController.renderItems()`.
- **ActivityResult launchers stay in the Activity** (they must register
  before onCreate completes); results are delegated straight into
  `FolderImportController`.
- **Adapters now live in their controller.** `bookAdapter`/`rowAdapter`/
  `currentBooks` moved from Activity fields into `LibraryScreenController`
  (single owner); the navigation controller reads `bookAdapter` for the
  Currently-Reading swipe wiring via `attachTouchHelper`/`detachTouchHelper`.

### What stayed in the Activity

`onCreate` ordering (unchanged: setContentView → SystemBarController →
setSupportActionBar → controller construction → restoreViewFromSavedState →
setupDrawer → home setup → refresh listener → setupObservers → FAB/empty
listeners → updateFab → handleViewIntent → drawerToggle creation → back
callback), all lifecycle overrides, the back-press double-tap exit, the
options menu + sort/view-mode/book-options dialogs, `handleViewIntent`,
`openBook` / `openDetails`, and `confirmDeleteBook`.

Note: LiveData observers registered during `onCreate` are inactive until
`onStart`, so `applyView` never runs before `drawerToggle` is initialized —
the original (and preserved) ordering is safe.

---

## Verification

- `./gradlew compileDebugKotlin` — clean.
- `./gradlew testDebugUnitTest` — 158 tests, 4 failures, **all four
  pre-existing** (`HomeOrderingTest` ×2 ordering, `EpubNavigationParserTest`
  ×2 kxml2) — identical failures on the pristine pre-transformation source.
  All 25 new Phase 8 store tests (`ShelfStateStoreTest` ×11,
  `ScrollStateStoreTest` ×14) pass.
- `./gradlew assembleDebug` — run separately after the test task; success.
  APK size unchanged (~16.7 MB).

### New unit tests

- `ShelfStateStoreTest` — Recently-Added enter/exit with Library fallback,
  drawer-navigation flag cancellation, save/restore round-trips for every
  view (incl. detail names), Recently-Added never persisted, view
  classification (isBookView / parentOf / placeholder / folders).
- `ScrollStateStoreTest` — view-key uniqueness per detail name, restore
  eligibility, pending-key gating (wrong view / empty adapter / missing
  anchor), cancel path, exact first-visible restoration for row lists,
  no-move vs. moved vs. vanished clicked-book relocation for book lists,
  position clamping, per-view anchor isolation.

---

## UI-risk notes

Phase 8 did not touch `fitsSystemWindows`, `SystemBarController`, layout
XML, or theme tokens. The earlier static UI diagnosis stands: the shell-path
risk patterns (theme-level inset double-application, black overlay views on
API 35+) are deferred to Phases 9–10 (layout migration to semantic tokens,
then full runtime theme switching).
