# Update v1.1 — Gradle Kotlin DSL signing-script fix

## Fixed
- Fixed `app/build.gradle.kts` release signing configuration script compilation.
- Replaced the ambiguous `java.util.Properties()` reference with an explicit `java.util.Properties` import.
- Made the `Properties.load(...)` call explicit inside the `use` block.

## Files changed
- `app/build.gradle.kts`
- `docs/update-v1-1.md`

## Notes
- App identity remains **The Bookworm Bliss**, version `1` / versionCode `1`.
- No architecture or application behavior was changed.
- No keystore or signing secret is included in the source ZIP.
