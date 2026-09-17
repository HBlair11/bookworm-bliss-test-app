# Highlight Tab History Fix

## Changes
- Highlight clicks that move to a different chapter now record the reader location being left before the chapter load begins.
- This makes cross-chapter Highlight navigation reliably participate in the existing back/forward reader history.
- Same-chapter Highlight navigation keeps the existing page-change check so history is added only when the highlighted text is on a different rendered page.
- Removed the asynchronous cross-chapter history push because the history entry is now captured synchronously before navigation.

## Files updated
- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `docs/HIGHLIGHT_TAB_HISTORY_FIX.md`
