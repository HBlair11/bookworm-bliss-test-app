# Patch E — Seek-Bar History Behavior

## Scope

This is an isolated navigation-history fix for explicit whole-book seek-bar jumps.

## Change

- A seek-bar jump now records the current reader location as the Back destination.
- The history cursor is then anchored to the exact selected seek destination.
- Subsequent ordinary page swipes do not replace that explicit navigation destination in history.
- Same-location seek-bar moves do not create a new history entry.
- No changes were made to TOC, bookmark, highlight, reader search, selection toolbar, TTS, or other navigation behavior.

## Intended behavior

For example:

`Page 10 → seek to Page 40 → swipe to Page 41 → Back → Page 10 → Forward → Page 40`

The selected seek-bar destination is treated the same way as the other explicit navigation jumps covered by the reader history model.
