# v37 Patch Eval Fix #2 — Modified / Recent Ordering

## Update
- Renamed the Home “Recently Added” section to **Recent**.
- Renamed the bookshelf sort option from **Recently Added** to **Modified**.
- For the Modified sort, **sourceLastModified** is the only recency key. `addedDate` is not used.
- Newer first sorts by `sourceLastModified` descending, with book `id` descending only as a stable tie-breaker when modification times match.
- Older first sorts by `sourceLastModified` ascending, with book `id` ascending only as a stable tie-breaker when modification times match.
- The Modified sort uses the existing internal `RECENTLY_ADDED` sort key so persisted/session state and the existing architecture remain unchanged.
- Other sort options continue using their existing Ascending / Descending labels and behavior.
- The transient Recently Added import screen continues to use the same Modified ordering through the existing ViewModel sort path.

## Files changed
- `app/src/main/java/com/epubreader/app/ui/BookshelfViewModel.kt`
- `app/src/main/java/com/epubreader/app/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/epubreader/app/HomeOrderingTest.kt`
- `docs/V37_PATCH_EVAL_FIX_02_MODIFIED_RECENT_ORDER_UPDATE.md`

## Preservation
- No database schema or migration changes.
- No reader, TTS, selection, highlight, bookmark, or in-book Search changes.
- Existing `sourceLastModified` importer/update logic is retained; no importer changes were necessary.
- App version remains v37.
## Correction — Modified direction labels
- Corrected the direction mapping so **Newer first** sorts `sourceLastModified` descending (newest timestamp first), with `id` descending as the tie-breaker.
- **Older first** sorts `sourceLastModified` ascending (oldest timestamp first), with `id` ascending as the tie-breaker.
- No other Recent/Modified behavior was changed.

