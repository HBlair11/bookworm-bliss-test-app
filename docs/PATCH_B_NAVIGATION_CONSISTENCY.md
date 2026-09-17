# Patch B — Reader Navigation Consistency

## Summary

Unified internal reader navigation so TOC links, bookmarks, and highlights follow the same history rule:

- navigating to the same rendered page does not create a history entry;
- navigating to a different page in the same chapter creates one history entry for the page being left;
- navigating to a different chapter creates one history entry for the exact visible location being left.

Search behavior inside the EPUB reader is intentionally unchanged for this patch.

## Changes

- Added a page-only resolver for EPUB fragment targets in the WebView pagination helper.
- Updated internal URL navigation so same-chapter fragment and chapter-start destinations are compared with the actual WebView page before history is pushed.
- Updated bookmark navigation to resolve the bookmark's target rendered page and avoid history entries when the bookmark is already on the visible page.
- Updated cross-chapter bookmark navigation to record the actual visible WebView page before loading the destination chapter.
- Removed duplicate cross-chapter history recording from highlight navigation while preserving its exact-highlight destination behavior.

## Files updated

- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `docs/PATCH_B_NAVIGATION_CONSISTENCY.md`
