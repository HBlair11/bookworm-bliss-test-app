# Patch F — Bookmark Precision and Settings Reflow Anchor

This isolated v37 patch addresses two reader-location quality issues.

## Bookmark precision

- Bookmark records now store the rendered `pageInChapter` captured at creation time.
- Existing bookmarks are preserved through real Room migration 14 → 15 and receive `-1` as the legacy sentinel.
- Bookmark navigation uses the stored rendered page when available and falls back to the existing scroll ratio for legacy bookmarks.
- The existing bookmark ratio remains intact for compatibility and display.

## Settings reflow

- Before reader settings are applied, the current chapter captures a short semantic text anchor near the current viewport plus the current rendered page as a fallback.
- After the chapter is reloaded with the new typography/layout, the reader searches the rendered DOM for that anchor and navigates to its new rendered page.
- If the anchor cannot be recovered, the previous rendered page is used as the fallback.
- No reader search, selection toolbar, highlight navigation, TTS, or history behavior was intentionally changed.

## Database

- Room version advances from 14 to 15.
- Migration 14 → 15 adds `bookmarks.page_in_chapter` with a default of `-1`.
- Existing bookmark rows are not deleted or rewritten.

## Validation

- Version remains v37 / 1.36.
- Complete baseline inventory is preserved.
- All text/source/config/document files end with exactly a normal newline; no trailing literal backslash or `\\n` characters were introduced.
