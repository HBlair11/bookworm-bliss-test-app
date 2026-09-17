# Compile fix iteration (v1)

Fixed the four Kotlin compile errors reported by CI in MainActivity.kt:
- app_surface_soft/app_accent resource names now match colors.xml.
- Ui.dp calls inside TextView.apply use MainActivity context explicitly.
- outlineButton assigns the Button.text property with this.text.

Version remains 1; this is a build-fix iteration, not a version bump.
