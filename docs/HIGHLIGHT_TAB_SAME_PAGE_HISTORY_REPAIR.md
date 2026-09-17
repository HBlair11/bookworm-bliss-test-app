# Highlight Tab Same-Chapter History Repair

## Changes

- Highlight navigation now compares the target highlight page with the WebView's actual current rendered page.
- Same-chapter Highlight clicks record the exact page being left when the highlight moves to a different page.
- Cross-chapter Highlight clicks also capture the WebView's actual current page before loading the target chapter.
- This avoids relying on the activity's potentially stale `currentPageInChapter` value during direct JavaScript navigation.

## Files updated

- `app/src/main/java/com/epubreader/app/ReaderActivity.kt`

## Version

- App version remains v37 / 1.36.
