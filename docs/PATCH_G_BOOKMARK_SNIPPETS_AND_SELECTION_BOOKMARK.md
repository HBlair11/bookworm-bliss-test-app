# Patch G — Bookmark snippets and selection bookmarks

## Changes

- Whole-page bookmarks now capture a snippet from the actual rendered page instead of the chapter-opening text.
- Selected text can be saved as a bookmark through the selection toolbar More menu.
- Selected-text bookmarks store the exact selected text in the existing `snippet` field and retain the exact rendered `pageInChapter`.
- The Bookmarks tab no longer displays the layout-dependent percentage. Chapter title and date remain unchanged.
- Existing `scrollRatio` data is retained in Room for compatibility and legacy fallback; it is no longer shown in bookmark presentation.
- Selected-text duplicate handling uses chapter, rendered page, and exact snippet, so a text bookmark does not accidentally toggle a whole-page bookmark at the same position.
- Reader Settings/reflow behavior is intentionally unchanged in this patch.

## Files changed

- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `app/src/main/java/com/epubreader/app/data/BookmarkDao.kt`
- `app/src/main/java/com/epubreader/app/ui/BookmarkAdapter.kt`
- `docs/PATCH_G_BOOKMARK_SNIPPETS_AND_SELECTION_BOOKMARK.md`

Version remains v37 / 1.36.
