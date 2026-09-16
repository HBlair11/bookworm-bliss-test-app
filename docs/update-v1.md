# v1 — Bookworm Bliss Android foundation

## Included

- The Bookworm Bliss Android application starts at version 1 (`versionCode = 1`, `versionName = "1"`).
- The supplied web application is used as the visual/product reference; its reader implementation is not copied.
- Centralized app identity, strings, colors, dimensions, themes, and reader-theme tokens are established.
- Library navigation foundation covers Home, Library, Currently Reading, Finished, Read, Favorites, Authors, Series, Reading Stats, Folders, Recently Added, and Settings.
- Long-press book actions provide Open, Book Details, Reading Nook, Favorite, and Remove actions.
- Editable book metadata and local library persistence are included as a foundation.
- EPUB parsing foundation covers ZIP extraction/safety checks, `META-INF/container.xml`, OPF discovery, metadata, manifest, spine, navigation, cover discovery, resource extraction, and structured parse diagnostics.
- Reader foundation provides a format-neutral reader contract plus an EPUB WebView renderer with horizontal column pagination, vertical flow mode, TOC, reader themes, font controls, alignment, margins, line spacing, and chapter/page position restoration.
- GitHub Actions use the supplied old-project Gradle wrapper (Gradle 8.9 distribution), JDK 17, Android API 35, and the repository's permanent debug keystore secret without embedding the keystore in source.

## Wrapper/toolchain files

The following are copied from the supplied old-project Gradle wrapper set and kept together:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle.properties`

The CI workflows invoke `./gradlew`, so the repository does not depend on a separately installed Gradle version.

## Intentionally deferred

This v1 is the foundation, not the complete production reader. Larger persistence and feature layers such as Room-based `AppDatabase`/DAOs, a dedicated `BookDetailsActivity`, richer repositories, full production EPUB rendering, bookmarks, highlights, search, selection, TTS, and advanced reading statistics are intended to be added on top of this foundation in later phases rather than being simulated prematurely in v1.
