# Highlight Tab Navigation Fix

## Changes

- Fixed Highlights-tab row navigation so it targets the actual rendered page containing the selected highlight instead of treating the highlight as a chapter URL.
- Added a Caesura helper that resolves a highlight mark by its saved highlight ID and navigates to the corresponding rendered page.
- Cross-chapter highlight navigation now waits for the target chapter highlights to be injected before jumping.
- Reader history is updated only when the highlight navigation actually moves the reader, avoiding history entries caused by a failed/no-op highlight jump.

## Files updated

- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `docs/HIGHLIGHT_TAB_NAVIGATION_FIX.md`

No database schema, selection toolbar, highlight anchoring, or TTS behavior was changed.
