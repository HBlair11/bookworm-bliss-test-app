# Phase 7 — Feature Services

**Goal.** Extract each reader feature's domain logic out of the Phase 6 UI
controllers (and `ReaderActivity`) into dedicated, unit-testable service
classes in a new `features` package. UI controllers now render and wire
screens; services own logic and persistence coordination.

Blueprint rule being applied: *"If a piece of logic has an independent
responsibility, it should have an appropriate home outside the Activity.
If it's merely screen wiring, it can remain in the screen."*

---

## New package: `com.epubreader.app.features`

```
features/
├── annotation/
│   ├── BookmarkService.kt        create (whole-page / selected-text) with
│   │                              duplicate detection, delete, restore, observe
│   ├── HighlightService.kt        save / delete / restore / observe / per-chapter,
│   │                              plus the single source of truth for the
│   │                              highlight palette + rgba() CSS conversion
│   └── AnnotationRepository.kt    unified read model over bookmarks +
│                                  highlights via the core annotation contracts
├── search/
│   └── ReaderSearchService.kt     off-main EPUB search with latest-wins
│                                  cancellation and a generation-token guard
├── define/
│   └── DefinitionService.kt       dictionary lifecycle (language switching) +
│                                  vocabulary history persistence
└── tts/
    └── ReaderTtsCoordinator.kt    read-aloud engine lifecycle + foreground
                                   service attachment; event fan-out to a
                                   single Listener
```

### Design rules

- **Constructor injection only** — services receive DAOs / factories /
  dispatchers, never Activities or Views. `ReaderTtsCoordinator` takes a
  `Context` + `Listener`; it is deliberately the thinnest of the five and
  holds no UI logic.
- **Config/State/Callbacks preserved** — Phase 6 controller wiring is
  untouched. Services are added to controller `Config` objects; no service
  ever receives a controller or an Activity.
- **Behavior-preserving** — every moved code path (duplicate-detection order,
  undo flows, snackbar sequences, dictionary language switching, history
  refresh-vs-insert) is byte-for-byte equivalent to the pre-Phase 7 code.

---

## Service notes

### BookmarkService
- `AddResult` sealed type (`Created` / `Duplicate`) replaces the old
  boolean-ish "insert or show exists-snackbar" branching in the overlay
  controller.
- Duplicate detection is unchanged: semantic snippet first
  (`findWholePageBySnippet` / `findTextBySnippet`), then the
  page/ratio or page/anchor pair, so bookmarks survive reflow.
- `restore()` re-inserts under the original row id (DAO `REPLACE`), so undo
  is transparent to observers.

### HighlightService
- The highlight palette (`HIGHLIGHT_YELLOW/GREEN/BLUE/PURPLE`) and
  `highlightCssColor()` previously existed **twice** — duplicated in
  `ReaderActivity` and `ReaderOverlayController` companions. They now live in
  exactly one place; both call sites reference the service.
- `highlightCssColor()` uses pure bit math (no `android.graphics.Color`), so
  it is unit-testable on the JVM.

### AnnotationRepository
- Read model only: `observeForBook()` merges both stores via `Flow.combine()`
  into `List<ReaderAnnotation>` (newest first) and exposes `AnnotationCounts`.
  Writes stay on the typed services (type-specific columns would be lost
  behind a generic write API).
- Documented trade-offs: bookmarks carry a spine **index** (empty
  `spineHref`); highlights carry a spine **href** (`spineIndex = -1` unless a
  resolver is supplied — navigate by href, treat -1 as "unresolved").

### ReaderSearchService
- `EpubSearchEngine` is blocking, so cancellation has two layers: the
  in-flight coroutine is cancelled AND a generation token guards result
  delivery — a search that raced past cancellation can never overwrite a
  newer query's results.
- `cancel()` is also invoked when the search overlay is hidden
  (`hideOverlays()`), so dismissed searches never deliver stale results.

### DefinitionService
- Owns the `DictionaryLookup` lifecycle: reuses the active dictionary when
  its language matches, otherwise closes it and opens the new one (the
  original multi-language behavior, extracted verbatim).
- Lookup happens in `define()`; history is recorded only via
  `recordLookup()` when a definition card is actually shown — preserving the
  original "no card, no history row" behavior.
- New `epub.DictionarySource` interface (declared next to `DictionaryLookup`
  so the legacy layer never depends on feature code); `DictionaryLookup`
  implements it. Tests substitute an in-memory dictionary.

### ReaderTtsCoordinator
- Lifecycle + wiring only: `start()` creates the engine once and attaches it
  to the foreground `ReaderTtsService`; `applySettings()`, `stop()`,
  `close()`. The visible-offset JS walk and overlay UI stay in the reader
  controllers that own them.
- `ReaderActivity` keeps a `ttsController` computed property over the
  coordinator, so every existing call site (play/pause, skip, sleep timer,
  settings sliders) works unchanged.

---

## Wiring changes (no behavior changes)

| Site | Change |
| --- | --- |
| `ReaderOverlayController.Config` | + `bookmarkService`, `highlightService`, `searchService` |
| `ReaderOverlayController.Callbacks` | `db` and `applicationContext` removed (no longer needed) |
| `ReaderSelectionController.Config` | + `definitionService` |
| `ReaderSelectionController.State` | `dictionaryLookup` removed (service owns the dictionary) |
| `ReaderActivity` | constructs all services from `AppDatabase`; TTS creation block replaced by `ReaderTtsCoordinator` + Listener; `onDestroy` closes coordinator + definition service; duplicated highlight constants removed |
| `hideOverlays()` | cancels any in-flight search |
| `ReaderActivity.onDestroy()` | cancels any in-flight search before closing the coordinator/services |

---

## Tests

`app/src/test/java/com/epubreader/app/features/` — 30 tests, all passing:

- In-memory DAO fakes (`FakeBookmarkDao`, `FakeHighlightDao`,
  `FakeDictionaryHistoryDao`) that mimic the SQL semantics the services rely
  on (REPLACE inserts, `created_at DESC, id DESC` ordering, near-ratio
  matching).
- `BookmarkServiceTest` (9) — creation, snippet-first dedupe after reflow,
  page/ratio fallback, text-anchor dedupe, restore-with-original-id.
- `HighlightServiceTest` (8) — save/restore, per-chapter document ordering,
  note updates, palette + CSS format.
- `AnnotationRepositoryTest` (5) — mapping to the unified contract, spine
  resolution, merged newest-first stream, counts.
- `ReaderSearchServiceTest` (5) — real epub zip via `EpubSearchEngine`
  (`searchNow`), blank-query handling, latest-wins cancellation,
  generation-token invalidation on cancel.
- `DefinitionServiceTest` (8) — language reuse/switching (old dictionary
  closed), history insert vs refresh, word normalization.

`ReaderTtsCoordinator` intentionally has no unit test — it is thin glue over
an Android `TextToSpeech` engine and is exercised by the existing read-aloud
integration paths.

### Pre-existing test failures (NOT from this phase)

`HomeOrderingTest` (2) and `EpubNavigationParserTest` (2) fail identically
on the pre-Phase 7 baseline; verified by running them against the untouched
v1.0 source archive. They are unrelated to this work (home-screen sort
assertion and a kxml2 namespace parse issue) and remain to be triaged
separately.

### Build

```
JAVA_HOME=<jdk17> ./gradlew testDebugUnitTest assembleDebug
```

Unit tests: 133 completed, 4 pre-existing failures, 0 new failures.
`assembleDebug`: SUCCESS.
