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

The initial release intentionally uses framework Views rather than introducing Compose or a large enterprise Clean Architecture hierarchy. Production persistence, richer domain/repository layers, dedicated details/reader screens, and advanced reader features will be introduced incrementally in later phases.
