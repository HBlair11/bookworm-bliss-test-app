# v1 Foundation Phase Update — Library, UI, Import, and Reader Foundations

## Added / improved
- Web-app-inspired Android navigation drawer, screen headers, library controls, cards, empty states, and application shell.
- Multiple EPUB file selection.
- Persistent folder selection and recursive EPUB scanning from Folders and an empty Library.
- Background import/metadata extraction with a non-blocking spinner, duplicate detection, failure reporting, and Snackbar result with a temporary Recently Added `Show` action.
- Silent repeat folder scans for discovering books added later.
- Transparent edge-to-edge system bars with centralized inset handling across application screens and the EPUB reader.
- Reader page navigation, horizontal/vertical modes, TOC access, reader settings, and position persistence foundation.
- Debug/release APK naming standardized to `the-bookworm-bliss.apk`.

## Files changed / added
- `app/src/main/java/com/bookwormbliss/app/MainActivity.kt`
- `app/src/main/java/com/bookwormbliss/app/core/library/BookStore.kt`
- `app/src/main/java/com/bookwormbliss/app/core/library/EpubLibraryImporter.kt`
- `app/src/main/java/com/bookwormbliss/app/core/reader/ReaderWebRenderer.kt`
- `app/src/main/java/com/bookwormbliss/app/core/system/SystemBars.kt`
- `README.md`
- `.github/workflows/android-ci.yml`
- `.github/workflows/release.yml`
- `docs/update-v1-library-import-ui-reader-foundation.md`

## Version policy
This remains **version 1**. This update is an implementation phase within v1; compiler/CI corrections and intermediate patches do not increment the app version.
