# v37 Patch Fix #4 — Currently Reading Book Details Scroll Restore

## Update

When Book Details is opened from the Currently Reading view, the bookshelf now preserves and restores the user's last scroll location when returning.

The existing reader behavior is intentionally unchanged: opening a book from Currently Reading still uses the existing top-reset-on-return behavior.

## Files changed

- `app/src/main/java/com/epubreader/app/MainActivity.kt`
- `docs/V37_PATCH_FIX_04_CURRENTLY_READING_DETAILS_SCROLL_RESTORE_UPDATE.md`

## Scope

This is an isolated scroll-restoration change. Reader, TTS, selection, highlights, bookmarks, TOC, in-book Search, sorting, favorite behavior, and database schema/migrations are not changed.
