# V37 Highlight Delete Sync Update

## Patch Q6

This is an isolated fix based directly on the validated Q5 baseline.

### Fix
- When a highlight is deleted from the Highlights tab, remove every rendered `mark.livre-highlight` fragment for that highlight ID from the current WebView.
- When a highlight is deleted from the highlight note sheet, use the same cleanup helper.
- Unwrap decoration nodes without modifying the underlying EPUB text or DOM content structure beyond removing the annotation wrapper.

### Preserved
- Q5 highlight anchoring and occurrence precision.
- Selected-text bookmark locator/storage behavior.
- Whole-page bookmark behavior.
- Selection toolbar.
- TTS behavior.
- Existing bookmark deletion and undo behavior.
- Existing highlight database/list observer behavior.

### Files changed
- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`
- `docs/V37_HIGHLIGHT_DELETE_SYNC_UPDATE.md`
