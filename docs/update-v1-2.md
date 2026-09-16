# Update v1.2 — Kotlin compile fixes

## Fixed
- Fixed the `Ui.text()` size type mismatch reported by Kotlin 2.0.21.
- Fixed the missing `grid` argument in the Home book renderer call.
- Fixed the Author sort call to use `sortedBy`.
- Fixed the nullable Year `toString()` invocation in metadata editing.
- Fixed the reader JavaScript callback so the generated `go(page)` function is invoked through WebView JavaScript instead of being resolved as a Kotlin function.

## Files changed
- `app/src/main/java/com/bookwormbliss/app/ui/common/Ui.kt`
- `app/src/main/java/com/bookwormbliss/app/MainActivity.kt`
- `app/src/main/java/com/bookwormbliss/app/core/reader/ReaderWebRenderer.kt`
- `docs/update-v1-2.md`

## Notes
- This is a compile-fix patch based directly on the GitHub Actions `compileDebugKotlin` errors.
- No app architecture was rewritten.
- App identity remains **The Bookworm Bliss**, version `1` / versionCode `1`.
- No keystore or signing secret is included.
