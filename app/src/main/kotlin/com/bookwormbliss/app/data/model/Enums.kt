package com.bookwormbliss.app.data.model

/** Mirrors ThemeId from the web app's types.ts; see ReaderThemeId in ui/theme/Color.kt for swatches. */
enum class ThemeId { IVORY, NORDIC_ECO, ALABASTER, CANDLELIGHT, ONYX, MIDNIGHT_SLATE }

/** Mirrors FontId. */
enum class FontId { SERIF, SANS, MONO, BOOK, HUMANIST }

/** Mirrors AlignOption. */
enum class AlignOption { LEFT, JUSTIFY, CENTER, RIGHT, ORIGINAL }

/** Mirrors ReadingMode. */
enum class ReadingMode { HORIZONTAL, VERTICAL }

/** Mirrors HighlightEntity.color. */
enum class HighlightColor { ROSE, AMBER, SAGE, LAVENDER, SLATE, YELLOW, GREEN, BLUE, PURPLE }

/** Mirrors ReadingStampType. */
enum class ReadingStampType { STARTED, RESUMED, FINISHED, REVISITED, FAVORITE_MOMENT, MEMORABLE, REREAD }

/** Mirrors ReaderPreferences.sortOption. */
enum class SortOption { RECENTLY_READ, RECENTLY_ADDED, TITLE, AUTHOR, SERIES, PROGRESS }
