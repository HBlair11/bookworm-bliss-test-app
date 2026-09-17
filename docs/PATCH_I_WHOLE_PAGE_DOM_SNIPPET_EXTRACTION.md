# Patch I — Whole-Page Bookmark DOM Snippet Extraction

## Update

Replaced only the whole-page bookmark snippet extraction mechanism.

The previous whole-page extraction sampled rendered caret positions and rebuilt text from those visual positions, which could produce missing letters in otherwise correct snippets. This patch instead walks the document's actual text nodes, identifies text nodes with rendered fragments on the current page, and reads their `textContent` directly.

## Files changed

- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
  - Replaced only `pageSnippet(page)`.
  - Removed caret-position sampling from whole-page snippet extraction.
  - Uses `TreeWalker` over DOM text nodes and `Range.getClientRects()` only to determine whether a text node belongs to the currently rendered page.
  - Uses the text node's `textContent` as the snippet source.
  - Keeps the existing whitespace normalization and 180-character display limit.

## Explicitly not changed

- Selected-text bookmark extraction
- Bookmark duplicate / Delete / Undo behavior
- Whole-page vs selected-text bookmark distinction
- Bookmark ordering
- Percentage UI removal
- Reader Settings / reflow
- TTS
- Selection toolbar
- Navigation / history
- Highlights
- Reader Search
- App version

App version remains v37 / 1.36.
