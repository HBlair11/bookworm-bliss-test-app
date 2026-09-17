# Highlight Tab Same-Chapter History Repair

## Change
Fixed Highlights-tab navigation history for same-chapter page changes.

The previous implementation changed the WebView page while simultaneously asking for the current page in one JavaScript expression. The returned page comparison was timing-sensitive. The reader now resolves the Highlight's target page first, records the actual page being left when the target differs, and only then performs the page navigation.

## Files updated
- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `docs/HIGHLIGHT_TAB_SAME_CHAPTER_HISTORY_REPAIR.md`

## Preserved
- Different-chapter Highlight navigation and history behavior.
- Highlight anchoring.
- Selection toolbar.
- TTS.
- Room schema.
- App version v37 / 1.36.
