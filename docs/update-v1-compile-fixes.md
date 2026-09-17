# v1 Compile Fixes — UI foundation

## Fixed from GitHub Actions compile log
- Replaced the missing `app_light_accent` resource reference with the existing accent resource.
- Corrected affected `AlertDialog.Builder` calls to receive the Activity `Context` rather than a `TextView`.
- Corrected the local variable reported as reassigned from `val` to `var`.

## Files changed
- `app/src/main/java/com/bookwormbliss/app/MainActivity.kt`
- `docs/update-v1-compile-fixes.md`

## Versioning
This remains **The Bookworm Bliss v1**. This is a compile-fix iteration inside the current phase; it is not a new app version.
