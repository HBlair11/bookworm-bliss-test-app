# v37 Selection Column Auto-Scroll Suppression Update

## Purpose

Prevent Chromium's horizontal CSS-column auto-scroll from exposing the reader's internal column pagination while the user is selecting text.

## Change

- The selection gesture locks horizontal `body.scrollLeft` to the reader page where the gesture began.
- The lock is released when selection ends or the selection is explicitly cleared.
- The lock is horizontal only; normal selection movement within the current page remains unchanged.
- Existing custom selection toolbar actions, highlight/bookmark behavior, definition behavior, and selection occurrence/anchor logic are unchanged.
- No EPUB content is modified.

## Files changed

- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `docs/V37_SELECTION_COLUMN_AUTO_SCROLL_SUPPRESSION_UPDATE.md`
