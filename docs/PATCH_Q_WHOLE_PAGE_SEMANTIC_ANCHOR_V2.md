# Patch Q v2 — Whole-Page Semantic Bookmark Anchor

## Changes
- Kept Patch J as the source baseline and left pagination initialization/rendering unchanged.
- Whole-page bookmarks now store a compact internal semantic anchor containing a normalized start window, bounded passage, and end window.
- Whole-page restoration searches the DOM for the start window, validates the passage/end window when available, and resolves the matched DOM position to the rendered page.
- Existing selected-text bookmark navigation continues using the original `pageForTextAnchor` path.
- Whole-page bookmark rows display stable `Bookmark 1`, `Bookmark 2`, etc. based on creation order; chapter/date remain in the subtitle.
- No database migration or Entity/schema changes.

## Files changed
- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `app/src/main/java/com/epubreader/app/ui/BookmarkAdapter.kt`
- `docs/PATCH_Q_WHOLE_PAGE_SEMANTIC_ANCHOR_V2.md`
