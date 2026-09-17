# V37 Patch Fix #5 — View Mode Toggle Icon Update

## Purpose

Fix the Books/Currently Reading top-bar view-mode toggle icon so it always displays the opposite view mode currently available to the user.

## Behavior

- List view displays the **grid** icon, offering the grid view option.
- Grid 2, Grid 3, and Grid 4 views display the **list** icon, offering the list view option.
- Switching between List and any Grid column count refreshes the menu icon immediately.
- Grid column changes do not alter the existing view-mode behavior.

## Implementation

- `MainActivity.kt`: invalidate the options menu when the persisted grid/list mode changes so `onPrepareOptionsMenu()` reapplies the existing opposite-mode icon.
- No new state, database fields, migrations, reader changes, or architecture changes were introduced.

## Scope

This patch intentionally changes only the view-mode menu icon refresh behavior. All existing reader, TTS, selection, highlight, bookmark, TOC, search, sorting, navigation, and bookshelf behavior is preserved.
