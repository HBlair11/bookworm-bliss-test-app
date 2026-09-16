# The Bookworm Bliss

**The Bookworm Bliss** is a private/debug-only Android EPUB reader restart. It is intentionally being rebuilt from a clean foundation instead of continuing the old **The Livre Magicae** architecture.

- **Application ID:** `com.bookwormbliss.app`
- **Version:** `1.0` / version code `1`
- **Renderer:** Chromium Android `WebView`
- **Formats in this stage:** EPUB only
- **Network:** no `INTERNET` permission
- **Baseline:** audited from The Livre Magicae v37 Patch Fix #6 — Grid Completion Indicator

The old The Livre Magicae application remains independent. Bookworm Bliss has a different application ID and database name, so the two applications can coexist on the same Android device.

---

## Current development stage

### Phase 1 — Foundation Reset

**Status: foundation reset in progress / core baseline established**

This phase is about making the EPUB engine, renderer lifecycle, persistence boundary, and application shell stable before rebuilding advanced reader features.

The reset deliberately keeps useful library and book-details functionality. It does **not** remove unrelated shell functionality merely because the original project contained it.

### Current reader foundation

The intended flow is:

```text
EPUB file
   ↓
EPUB parser / document model
   ↓
ReaderDocument
   ↓
ReaderWebRenderer
   ↓
Chromium WebView + CSS pagination
   ↓
Canonical ReaderDocumentPosition
   ↓
UI + persistence
```

The reader does not use independent feature code to interrogate the WebView.

---

## Available features

### Library / app shell

- EPUB import from Android storage providers
- EPUB file association / Open With registration
- Folder/library scanning
- EPUB validation during parsing
- Cover extraction/generation
- Library grid/list presentation
- Sorting
- Currently Reading shelf
- Favorites
- Authors
- Series
- Collections
- Book details
- Metadata refresh
- Remove book
- Offline operation
- Keep-screen-on setting
- Centralized system-bar handling
- About/privacy screen

### Reader

- EPUB package/container parsing
- OPF/package parsing
- Metadata parsing
- Manifest and spine parsing
- EPUB navigation/TOC parsing
- EPUB resource resolution from the local ZIP container
- CSS/reflow rendering through Chromium Android WebView
- Horizontal pagination
- Previous/next page
- Previous/next spine item at chapter boundaries
- Table of contents navigation
- Reader settings
- Reader themes
- Font selection
- Font size
- Line height
- Margins
- Text alignment
- Hyphenation
- Page-turn animation setting
- Orientation/viewport configuration
- Reading-position restoration
- Renderer cleanup/recovery boundary
- Reader progress persistence
- Basic reader error reporting
- Consistent black system bars

---

## Temporarily removed features

These features are intentionally outside the Phase 1 foundation. They are **not permanently abandoned**.

- Search
- Highlights
- Bookmarks
- Define / dictionary lookup
- TTS / Read Aloud
- Selection toolbar
- Annotation infrastructure
- Complicated reader history
- Measurement WebView
- Reading Stats
- Vocabulary Builder
- Feature-specific privileged JavaScript bridges

They will be rebuilt later as consumers of the stable document/location foundation.

The key rule is:

> Advanced features may depend on the reader foundation; the reader foundation must not depend on advanced features.

---

## Screens currently retained

The reset preserves the useful application shell:

1. **Home / Bookshelf**
2. **Library and shelf views**
3. **Book Details**
4. **Metadata Refresh**
5. **Reader**
6. **Reader Settings**
7. **About / Privacy**

The Reader Settings screen remains because reader appearance and rendering behavior are core foundation concerns.

---

## Architecture

### EPUB layer

`app/src/main/java/com/bookwormbliss/app/epub/`

Important components:

- `EpubParser.kt` — EPUB container/package parsing and document construction
- `EpubModels.kt` — format-specific EPUB model
- `EpubImporter.kt` — SAF/file import, deduplication, caching, metadata refresh
- `EpubResourceResolver.kt` — local EPUB resource serving to Chromium
- `EpubPaths.kt` — EPUB path normalization
- `BookFileTypes.kt` — supported import types
- `CoverExtractor.kt` / `CoverGenerator.kt` — library cover handling
- `BookImportIdentity.kt` / `RescanDecision.kt` — conservative import identity and rescan decisions

### Reader layer

`app/src/main/java/com/bookwormbliss/app/reader/`

- `ReaderDocument.kt` — reader-facing wrapper around the EPUB document
- `ReaderDocumentPosition.kt` — canonical reflow-safe reader position
- `ReaderWebRenderer.kt` — Chromium WebView configuration, XHTML loading, EPUB resource interception, pagination and renderer lifecycle

A document position is represented as:

```text
spine index + normalized horizontal offset
```

A screen number is deliberately **not** the persisted document address because EPUB content is reflowable.

### UI layer

`app/src/main/java/com/bookwormbliss/app/ui/`

Contains the retained library adapters/view model, reader settings, TOC adapter, reader theme registry, and other shell UI.

### Data layer

`app/src/main/java/com/bookwormbliss/app/data/`

The Phase 1 database contains only foundation/library concepts:

- `BookEntity`
- `BookDao`
- `CollectionEntity`
- `CollectionDao`
- `BookCollectionRef`
- `AppDatabase`
- `BookRepository`
- `PrefsManager`

The new application ID means the project intentionally starts with a fresh Room schema instead of carrying the old app's feature-heavy migration chain forward.

---

## Source-of-truth files

### Application identity

`gradle.properties`

Contains:

- `BOOKWORMBLISS_APPLICATION_ID`
- `BOOKWORMBLISS_VERSION_CODE`
- `BOOKWORMBLISS_VERSION_NAME`

`app/build.gradle.kts` consumes those values and generates the runtime `BuildConfig` identity.

`app/src/main/java/com/bookwormbliss/app/AppIdentity.kt` exposes the build identity to runtime code.

### App name

`app/src/main/res/values/strings.xml`

The application label is:

```text
The Bookworm Bliss
```

### Reader themes

`app/src/main/java/com/bookwormbliss/app/ui/ReaderThemes.kt`

This remains the reader-theme registry and is the single source for reader theme IDs, display names, page colors, and ink colors.

### Reader settings

`app/src/main/java/com/bookwormbliss/app/data/PrefsManager.kt`

Owns persisted reader appearance/settings values.

### Reader renderer

`app/src/main/java/com/bookwormbliss/app/reader/ReaderWebRenderer.kt`

Owns WebView configuration and the rendering/pagination boundary.

### Canonical reader location

`app/src/main/java/com/bookwormbliss/app/reader/ReaderDocumentPosition.kt`

Owns the logical reader-position representation.

### System bars

`app/src/main/java/com/bookwormbliss/app/util/SystemBarController.kt`

Centralizes system-bar behavior.

### Build and CI

- `app/build.gradle.kts`
- `gradle.properties`
- `.github/workflows/android-ci.yml`
- `.github/workflows/android-tests.yml`
- `.github/workflows/release.yml`

---

## UI standardization status

UI standardization is intentionally **not the main Phase 1 task**.

The existing useful shell and reader-settings UI are retained while the EPUB engine and reader lifecycle are stabilized.

Later UI work will establish centralized sources of truth for:

- colors
- typography
- dimensions
- spacing
- corner radii
- button sizes
- icon sizes
- touch targets
- menus
- dialogs
- toolbars
- themes

Do not redesign unrelated UI merely for aesthetics during foundation stabilization.

---

## Renderer lifecycle

The renderer is deliberately isolated from the Activity.

`ReaderActivity` owns:

- screen state
- user navigation
- settings navigation
- TOC navigation
- persistence

`ReaderWebRenderer` owns:

- WebView configuration
- EPUB XHTML loading
- EPUB resource interception
- CSS/reflow setup
- horizontal pagination
- renderer cleanup

`EpubResourceResolver` owns:

- EPUB ZIP access
- MIME resolution
- encrypted/obfuscated resource handling where supported
- virtual EPUB resource URLs

This separation is intended to make future reader features consumers of a stable location/rendering contract rather than WebView-specific implementations.

---

## Import and EPUB handling

Imported EPUBs are copied into the application's private `files/epubs` directory using checksum-based deduplication.

Covers are stored separately under `files/covers`.

SAF source identity is retained where available so rescans can avoid reparsing unchanged documents.

The EPUB parser remains format-specific. No universal `Document` abstraction for PDF/CBZ/MOBI/etc. is introduced at this stage.

---

## Signing and coexistence

The original project's permanent debug-key configuration was inspected before changing the application identity.

The source archive does **not** contain the private keystore material. Its configuration shows:

- debug builds use `~/.android/debug.keystore`
- standard Android debug keystore credentials are configured
- GitHub Actions injects the existing `EPUB_APP_KEYSTORE_BASE64` secret into that location

The same debug signing key can therefore continue to be supplied for local/CI debug builds.

Because Bookworm Bliss uses:

```text
com.bookwormbliss.app
```

instead of:

```text
com.epubreader.app
```

the two applications are independently installable.

No keystore material is committed to this repository.

---

## Network / privacy baseline

The Phase 1 manifest declares no `INTERNET` permission.

EPUB resources are served locally through the application-owned virtual EPUB host and `WebViewClient` interception. The renderer does not require network access to read imported books.

---

## Build information

Toolchain:

- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.2
- Kotlin 2.0.20
- KSP 2.0.20-1.0.25
- compileSdk 35
- targetSdk 35
- minSdk 24

Primary libraries retained from the audited foundation include AndroidX, Material Components, Room, Coroutines, DocumentFile, and Glide.

See `docs/BUILD_AND_VALIDATION.md` for validation commands.

---

## Full-project delivery rule

Every project update is delivered as a **full updated ZIP**, not as a partial patch.

Before delivery, the project should be checked for:

- complete file inventory
- required README
- GitHub Actions workflows
- required Gradle wrapper files
- source/config/document newline termination
- accidental literal escape/trailing-character corruption
- references to deleted classes/resources
- unresolved compile/resource issues as far as the available environment permits

An APK is only described as successfully built when an actual APK build has completed successfully.

---

## Foundation audit summary

The original v37 Patch Fix #6 project was substantially feature-rich and had accumulated a long Room migration chain, a very large `ReaderActivity`, a measurement WebView, WebView-specific selection/annotation bridges, TTS document/controller infrastructure, search/dictionary layers, reader history, statistics, and vocabulary functionality.

The reset preserves the strongest reusable EPUB infrastructure while removing those feature layers from the foundation.

The most important architectural change is the new explicit boundary:

```text
EPUB format model
       ↓
ReaderDocument
       ↓
ReaderWebRenderer
       ↓
Canonical ReaderDocumentPosition
       ↓
Reader UI / persistence
```

This is the foundation on which the later advanced features will be rebuilt.

---

## Roadmap

### Phase 1 — Foundation Reset
- [x] New application identity
- [x] Fresh database boundary
- [x] Remove obsolete feature infrastructure
- [x] Preserve useful library/details shell
- [x] Retain Chromium WebView EPUB rendering
- [x] Establish reader document wrapper
- [x] Establish canonical reader position
- [x] Isolate renderer lifecycle
- [x] Retain EPUB import/parser/resource infrastructure
- [ ] Full local/CI build and device validation

### Phase 2 — Foundation hardening
- EPUB edge-case validation
- renderer recovery testing
- viewport/orientation testing
- position restoration testing across reflow/settings changes
- additional parser/resource tests
- performance profiling on large EPUBs

### Later phases

Reintroduce advanced features only after the foundation is stable:

- Search
- Bookmarks
- Highlights / annotations
- Selection / Define
- TTS
- Reader history
- Reading statistics
- Vocabulary Builder

Each later feature should consume the stable document/location/rendering contracts rather than recreate WebView interrogation logic.

---

## Important baseline note

The old **The Livre Magicae v37 Patch Fix #6 — Grid Completion Indicator** ZIP is the historical/reference snapshot used for this reset.

It is **not** the Bookworm Bliss architecture baseline.

Bookworm Bliss starts from the audited useful portions of that snapshot and intentionally establishes a new foundation.
