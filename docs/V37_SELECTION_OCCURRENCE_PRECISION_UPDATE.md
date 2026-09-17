# v37 Selection Occurrence Precision Update

## Purpose

Fix ambiguous single-word and short-phrase selection resolution for persistent highlights and selected-text bookmarks.

## Changes

- Capture a text-node-specific DOM locator for new selections, including the exact text-node offset.
- Persistent highlights use the exact saved selection endpoints first, so repeated words/phrases are not resolved to an earlier occurrence in the same paragraph or chapter.
- Existing context/path fallback behavior remains available for older highlights.
- Selected-text bookmarks store a compact internal locator while continuing to display only the original selected text in the bookmark list.
- Selected-text bookmark navigation resolves the saved selection endpoints before falling back to the existing text-anchor search.
- No database schema migration was added.

## Files changed

- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `app/src/main/java/com/epubreader/app/ui/BookmarkAdapter.kt`

## Files added

- `docs/V37_SELECTION_OCCURRENCE_PRECISION_UPDATE.md`
