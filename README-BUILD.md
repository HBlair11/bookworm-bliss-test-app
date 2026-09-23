# The Bookworm Bliss — build guide

Native Android (Kotlin + Jetpack Compose) EPUB reader, reconstructed from
the Livre Magicae web app. No Android Studio required — everything here is
built by Gradle from the command line or by the included GitHub Actions
workflows.

## 1. One-time setup: the permanent debug keystore

`android-ci.yml` injects a debug keystore from the `EPUB_APP_KEYSTORE_BASE64`
repo secret into `~/.android/debug.keystore` before building. The Android
Gradle Plugin's *default* debug signing config already points at exactly
that file, with the standard alias/passwords below — so as long as you
generate the keystore with these exact values, `app/build.gradle.kts` needs
no custom debug `signingConfig` block (and doesn't have one).

Generate it once, locally:

```bash
keytool -genkeypair -v \
  -keystore debug.keystore \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=Android Debug,O=Android,C=US"
```

Then base64-encode it and store it as the `EPUB_APP_KEYSTORE_BASE64` GitHub
secret:

```bash
base64 -w0 debug.keystore > debug.keystore.b64   # macOS: base64 -i debug.keystore
```

Paste the contents of `debug.keystore.b64` into **Settings → Secrets and
variables → Actions → New repository secret**. Keep `debug.keystore` itself
somewhere safe outside the repo — it's what makes the debug key "permanent"
across CI runs instead of a fresh throwaway key each time.

## 2. Release signing (optional, only needed to run `release.yml`)

Generate a real release key and set these four repo secrets:
`EPUB_APP_RELEASE_KEYSTORE_BASE64`, `EPUB_APP_RELEASE_KEYSTORE_PASSWORD`,
`EPUB_APP_RELEASE_KEY_ALIAS`, `EPUB_APP_RELEASE_KEY_PASSWORD`.

```bash
keytool -genkeypair -v \
  -keystore bookworm-bliss-release.jks \
  -alias bookworm-release \
  -keyalg RSA -keysize 2048 -validity 10950
base64 -w0 bookworm-bliss-release.jks > release.b64
```

`release.yml` writes these into `keystore.properties` at build time (which
is git-ignored); `app/build.gradle.kts` only wires up the release
`signingConfig` when that file exists, so local `./gradlew assembleDebug`
keeps working with zero secrets.

## 3. Why there's no committed `gradle-wrapper.jar`

All three workflows provision Gradle 8.9 via `gradle/actions/setup-gradle@v4`
(`gradle-version: "8.9"`) and then run `gradle wrapper --gradle-version 8.9
--distribution-type bin` as their first step, generating `gradlew` /
`gradlew.bat` / `gradle-wrapper.jar` fresh every run. That keeps the repo
free of a binary blob. If you'd rather commit a wrapper for local use
without installing Gradle yourself, run that same command once locally
(with Gradle installed) and commit the result — CI doesn't need it either
way.

## 4. Activating the real typefaces (Cinzel / Plus Jakarta Sans)

`ui/theme/Type.kt` currently falls back to `FontFamily.Serif` /
`FontFamily.SansSerif` because no font binaries are bundled in this
scaffold. To switch on the real look:

1. Download the OFL `.ttf` files for Cinzel and Plus Jakarta Sans.
2. Drop them under `app/src/main/res/font/` (e.g. `cinzel_semibold.ttf`,
   `plus_jakarta_sans_regular.ttf`).
3. In `Type.kt`, change `LiteraryFontFamily` / `UiFontFamily` to build a
   `FontFamily(Font(R.font.cinzel_semibold, FontWeight.SemiBold), ...)`.

Nothing else needs to change — every screen already reads fonts through
those two vals.

## 5. What's implemented vs. stubbed

**Implemented:** design system (colors/type/shape/spacing — single source
of truth), Room data layer, DataStore preferences, native EPUB parser +
importer (SAF picker, no network/WebView), Home, Library (grid + import),
Book Details, Reader (chapters, theming, font/size/line-height, TTS via
on-device `TextToSpeech`, bookmarking, table of contents), Settings,
navigation drawer shell.

**Stubbed (styled "coming soon" placeholders, wired into navigation):**
Reading Stats, Vocabulary, Authors, Series, Reading Nook, Search, Favorites
filter. Highlights (data layer exists, no UI yet), book-journal entries,
reading-session logging, metadata editing UI, backup export/import.

## 6. Building locally

```bash
gradle wrapper --gradle-version 8.9 --distribution-type bin   # once
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/bookworm-bliss.apk`.

## 7. A note on verification

This project was written without a local Android SDK/network connection to
compile against, so treat the first CI run as the real first compile — if
`android-ci.yml` reports errors, paste them back and they can be fixed
directly against real compiler output rather than guesswork.
