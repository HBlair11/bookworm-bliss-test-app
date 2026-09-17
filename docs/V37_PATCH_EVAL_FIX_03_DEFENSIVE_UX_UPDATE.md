# V37 Patch Eval Fix #3 — Defensive UX Safety

## Changes

### BookAdapter NO_POSITION protection
- Added `bindingAdapterPosition` guards for grid and list click, long-click, and details handlers.
- Valid positions keep the existing callbacks and behavior unchanged.
- Invalid/recycled positions are ignored safely instead of being used as adapter indices.

### Book Details favorite toggle state
- Favorite toggling now uses the current in-screen favorite state rather than repeatedly reading the originally loaded `BookEntity.isFavorite` value.
- The favorite icon updates immediately on each tap.
- A pending database update is canceled when a newer tap supersedes it, so rapid taps persist the latest requested state instead of an older captured state.

## Scope

Only `app/src/main/java/com/epubreader/app/ui/BookAdapter.kt`, `app/src/main/java/com/epubreader/app/BookDetailsActivity.kt`, and this update document are changed for this patch. No database schema, migrations, reader, TTS, selection, highlight, bookmark, TOC, search, navigation, or architecture changes are included.
