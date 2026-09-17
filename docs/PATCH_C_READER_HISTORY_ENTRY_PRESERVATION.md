# Patch — Reader History Entry Preservation

## Summary

This isolated v37 patch fixes reader history cursor semantics so normal page turns do not mutate the destination associated with an existing history entry.

### Intended behavior

For an explicit navigation such as:

`Page 10 -> navigate to Page 40 -> swipe to Page 41 -> Back`

history preserves the explicit navigation pair:

- Back returns to Page 10.
- Forward returns to Page 40, not Page 41.
- Normal swipe/tap page turns do not rewrite either history entry.

## Implementation

- Added a reader history cursor location separate from the live rendered page position.
- Back/Forward now move the history cursor between stored locations instead of capturing the live page after arbitrary page turns.
- Explicit TOC/internal-link, bookmark, and highlight navigation update the history cursor to their resolved destination.
- Cross-chapter destinations update the cursor after their restored destination page is resolved.
- Existing history entries remain immutable during normal reader page turns.
- Selection toolbar, in-reader Search behavior, TTS, and unrelated reader features were not intentionally changed.

## Files changed

- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `docs/PATCH_C_READER_HISTORY_ENTRY_PRESERVATION.md`

## Validation target

1. Page 10.
2. Navigate to Page 40.
3. Swipe to Page 41.
4. Verify Back still records Page 10.
5. Press Back.
6. Verify Forward records Page 40.
7. Press Forward.
8. Verify Back records Page 10.

Version remains v37 / 1.36.
