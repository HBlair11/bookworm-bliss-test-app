# Build and Validation

## Toolchain

- JDK 17
- Gradle 8.9 via the checked-in Gradle wrapper
- Android Gradle Plugin 8.7.2
- Kotlin 2.0.20
- KSP 2.0.20-1.0.25
- compileSdk / targetSdk 35
- minSdk 24

## Local validation

From the project root:

```bash
./gradlew testDebugUnitTest --console=plain --no-daemon
./gradlew compileDebugKotlin --console=plain --no-daemon
./gradlew mergeDebugResources --console=plain --no-daemon
./gradlew assembleDebug --console=plain --no-daemon
```

The debug APK is:

```text
app/build/outputs/apk/debug/bookworm-bliss.apk
```

The project is debug-only during the foundation restart. A production release workflow remains in the repository for later use, but release signing is not part of Phase 1 validation.

## Static checks before delivery

Before delivering a source ZIP:

1. Confirm the complete ZIP inventory.
2. Confirm the README is present.
3. Confirm GitHub Actions workflows are present.
4. Confirm no removed-feature source files or manifest components remain.
5. Confirm every Kotlin, Gradle/KTS, XML, YAML, properties, Markdown, and script file ends with a newline.
6. Scan for unresolved references to deleted resources/classes.
7. Run the Gradle compile/resource/unit-test checks when the required SDK/dependency cache is available.
8. Only describe an APK as built when `assembleDebug` actually succeeds.

## CI

`.github/workflows/android-ci.yml` performs the debug compile/resource/build validation.

`.github/workflows/android-tests.yml` can run JVM tests and, when requested, Android instrumentation tests on an API 35 emulator.

## Signing

The existing permanent debug-key mechanism was inspected during the Phase 1 reset:

- The source ZIP does not contain the keystore material.
- `app/build.gradle.kts` explicitly uses `~/.android/debug.keystore` with the standard Android debug keystore credentials.
- CI validates the repository's existing `EPUB_APP_KEYSTORE_BASE64` secret before installing it as `~/.android/debug.keystore`; if the secret is missing, malformed, or incompatible, CI removes it and allows Gradle to generate a standard debug keystore instead.

The key material itself cannot be verified from the source ZIP because it is intentionally supplied by the local machine/GitHub secret. The configuration is preserved so the same debug certificate can be reused. Because Bookworm Bliss uses a different application ID, it can be installed beside The Livre Magicae.
