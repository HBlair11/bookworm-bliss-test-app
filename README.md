# The Bookworm Bliss — Android v1

Android foundation generated from the supplied web application source. The web app is used as the visual/product reference; its reader implementation is not copied. The Android reader uses a dedicated EPUB parsing, document, location, and Chromium/WebView rendering core.

## Build

GitHub Actions is the supported build path. The project uses the supplied old-project Gradle wrapper (Gradle 8.9), JDK 17, and Android API 35. No Android Studio project configuration is required.

The CI workflows call `./gradlew` directly and restore execute permission with `chmod +x gradlew`, so the wrapper is the source of truth for the Gradle version.

## Version and identity

- App name: **The Bookworm Bliss**
- Version code: **1**
- Version name: **1**
- Application ID / namespace: `com.bookwormbliss.app`

Keep the application ID and signing certificate stable for future patch updates intended to install over an existing Bookworm Bliss APK. The permanent debug keystore is supplied through the GitHub Actions secret `EPUB_APP_KEYSTORE_BASE64`; the secret itself is never stored in this repository.

## Architecture

- `core/epub`: EPUB container/package/manifest/spine/navigation/resource parsing.
- `core/document`: format-neutral reader document contract.
- `core/location`: canonical reader positions.
- `core/reader`: renderer contract and WebView EPUB renderer.
- `core/library`: initial local library/book persistence foundation.
- `ui/common`: reusable design-system UI primitives.

The initial release uses framework Views rather than introducing Compose or a large enterprise Clean Architecture hierarchy. Library importing/scanning, UI shell, system-bar policy, and reader foundations are being expanded incrementally while keeping clear ownership in `core/` and `ui/`. Advanced reader features will follow later phases.

## Current v1 foundation behavior

- Multiple EPUB files can be selected at once.
- A persisted device folder can be selected from Folders, or from an empty Library, and rescanned later. Scans/imports run on a background worker while the user can continue navigating.
- SHA-256 checksums plus EPUB identity metadata are used to avoid duplicate library entries. Invalid or partially recoverable EPUBs are reported after the background operation rather than blocking the library screen.
- The Android application shell follows the supplied web app reference for navigation concepts, headers, library cards, empty states, and sort/view controls. There is no main-navigation Recently Added item; the post-import Snackbar `Show` action opens Recently Added temporarily.
- System bars are transparent and edge-to-edge. The root background supplies the same visual color beneath the status/navigation areas, while content receives system-bar insets so controls do not sit under the bars. The same rule is applied to the EPUB reader.
- Debug and release workflow outputs are named `the-bookworm-bliss.apk`.

Version remains **1** until a planned development phase is actually completed; bug fixes and CI fixes do not create version numbers.
