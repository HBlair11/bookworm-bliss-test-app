# Currently Reading Undo + Cross-Source Navigation History

## Changes

- Currently Reading swipe removal now uses an application-scoped undo state so the Snackbar can be restored after Activity navigation while its original visibility time remains.
- Book Details -> Remove from Reading uses the same persistent undo behavior.
- Undo restores the book with `setCurrentlyReading(book.id)`.
- Reader navigation history treats Highlight, TOC, Bookmark, and seek-bar destinations by rendered page, so a second explicit jump to the same page does not create a duplicate history event.
- No reader Search behavior or selection-toolbar behavior was changed.

## Files changed

- `app/src/main/java/com/epubreader/app/util/CurrentlyReadingUndoSnackbar.kt`
- `app/src/main/java/com/epubreader/app/MainActivity.kt`
- `app/src/main/java/com/epubreader/app/BookDetailsActivity.kt`
- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `docs/PATCH_CURRENTLY_READING_UNDO_AND_NAVIGATION_HISTORY.md`
