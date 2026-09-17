# V37 Patch Eval Fix #1 — Completion Indicator Consistency

## Update

Aligned completion handling with the existing `BookAdapter.kt` behavior: a book is treated as completed at **90% progress** across the Library, Home, Book Details, and Finished-book queries.

## Files changed

- `app/src/main/java/com/epubreader/app/data/BookDao.kt`
  - Finished-book list and finished-count threshold changed from 99.5% to 90%.
- `app/src/main/java/com/epubreader/app/BookDetailsActivity.kt`
  - Completed progress display threshold changed from 99.5% to 90%.
- `app/src/main/java/com/epubreader/app/MainActivity.kt`
  - Home Continue progress display threshold changed from 99.5% to 90%.
- `app/src/main/java/com/epubreader/app/ui/HomeBookAdapter.kt`
  - Home shelf completion badge threshold changed from 99.5% to 90%.

No other application behavior, architecture, database schema, migrations, selection behavior, reader behavior, or existing feature code was changed. `BookAdapter.kt` remains unchanged because it already used the desired 90% completion threshold.
