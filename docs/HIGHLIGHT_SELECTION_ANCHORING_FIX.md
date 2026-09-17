# Highlight Selection Anchoring Fix

## Purpose

Fixes a v37 highlight-placement edge case where selecting a repeated word or phrase could highlight an earlier occurrence in the same EPUB block instead of the occurrence actually selected.

## Changes

- Kept the existing `ReaderSelectionLocator` and Room highlight data unchanged.
- Kept the custom selection toolbar unchanged.
- Updated `ReaderActivity.kt` highlight injection to evaluate every matching occurrence and choose the occurrence whose surrounding saved prefix/suffix context best matches the original selection.
- Applied the same context-aware matching to the chapter-wide fallback.
- No database migration or unrelated reader behavior was changed.

## Validation intent

The fix specifically targets repeated text in the first few rendered lines of a page while preserving the existing DOM-path-first highlight flow.
