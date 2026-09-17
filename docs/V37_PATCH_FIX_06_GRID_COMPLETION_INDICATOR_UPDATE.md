# v37 Patch Fix #6 — Grid Completion Indicator

## Update
- In Grid 2/3/4 views, completed books (90% or higher) now show the completion checkmark without the progress bar.
- In-progress books below 90% continue to show the percentage badge and progress bar.
- List view behavior remains unchanged for in-progress books and completed books.
- The List view `Completed` badge is aligned with the left edge of the progress area by removing its extra leading margin.

## Files changed
- `app/src/main/java/com/epubreader/app/ui/BookAdapter.kt`
- `app/src/main/res/layout/item_book_list.xml`
- `docs/V37_PATCH_FIX_06_GRID_COMPLETION_INDICATOR_UPDATE.md`

No other application files or project files were changed.
