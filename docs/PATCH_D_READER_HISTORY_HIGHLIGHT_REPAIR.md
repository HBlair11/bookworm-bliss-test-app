# Reader History Entry Preservation — Highlight History Repair

## Summary

Repairs the interaction between preserved reader history cursors and explicit Highlight navigation.

## Changes

- Establishes the history cursor after cross-chapter explicit navigation that has no fragment/exact page.
- Resolves the history cursor to the exact rendered page after a cross-chapter highlight is injected and positioned.
- Prevents Back/Forward from falling back to an older cursor and recording the wrong location.
- Leaves normal swipe/tap page turns, TOC behavior, bookmarks, in-reader Search, selection toolbar, and TTS behavior otherwise unchanged.

## Expected behavior

For an explicit navigation from Page 10 to Page 40, then a normal swipe to Page 41:

- Back remains associated with Page 10.
- Back returns to Page 10.
- Forward returns to Page 40, not Page 10 or Page 41.

For Highlight navigation, the history cursor is updated only after the highlight's exact rendered destination page is known.
