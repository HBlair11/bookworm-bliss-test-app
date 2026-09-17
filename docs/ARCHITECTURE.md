# The Livre Magicae — Architecture Transformation Blueprint

## Overview

This document describes the target architecture for transforming The Livre Magicae
from its current state (monolithic activities, scattered UI, no design system) into
a production-grade Android EPUB reader with a semantic design system, clean
separation of concerns, and a format-agnostic reader engine.

## Current State (Problems)

| File | Lines | Problem |
|------|-------|---------|
| ReaderActivity.kt | 5,118 | God activity: WebView, selection, highlights, bookmarks, TOC, search, TTS, progress, chrome, animations, history, definition, settings, CSS, pagination JS |
| MainActivity.kt | 1,832 | Shell + library + shelves + import + scroll state + settings UI + sort dialogs + book options |
| EpubParser.kt | 1,056 | Monolithic: container + OPF + metadata + manifest + spine + TOC all in one file |
| Layouts | 28 files | Hardcoded dp/sp values, no semantic color tokens, no consistent component styles |

## Target Architecture

```
com.epubreader.app/
│
├── core/
│   ├── reader/              ← Format-agnostic reader contracts
│   │   ├── ReaderDocument       (interface — any readable document)
│   │   ├── ReaderPosition       (canonical location: spine + ratio + DOM + offset)
│   │   ├── ReaderProgress       (0..1 fraction across the whole book)
│   │   ├── ReaderSettings       (font, size, margins, alignment, theme)
│   │   ├── ReaderViewport       (dimensions, density, guards)
│   │   ├── ReaderPage           (visual page: spine + pageInChapter + absolute)
│   │   └── ReaderRenderer       (interface: load, navigate, position, settings)
│   │
│   ├── epub/                ← EPUB-specific implementation
│   │   ├── EpubContainer        (container.xml layer)
│   │   ├── EpubContainerParser  (parse META-INF/container.xml)
│   │   ├── EpubPackageParser    (parse OPF: metadata + manifest + spine)
│   │   ├── EpubNavigationParser (parse nav.xhtml / NCX)
│   │   ├── EpubMetadata         (bibliographic info)
│   │   ├── EpubManifest         (all resources)
│   │   ├── EpubSpine            (reading order)
│   │   ├── EpubNavigation       (table of contents)
│   │   ├── EpubDocument         (implements ReaderDocument)
│   │   ├── EpubParseResult      (document + status + diagnostics)
│   │   ├── EpubParseStatus      (VALID / WARNINGS / RECOVERED / UNSUPPORTED / INVALID)
│   │   ├── EpubDiagnostic       (structured error/warning/info)
│   │   └── EpubParsePipeline    (orchestrates container → package → nav)
│   │
│   ├── annotation/          ← Annotation contracts
│   │   ├── AnnotationLocation  (position + text + note + color)
│   │   ├── AnnotationType      (BOOKMARK / HIGHLIGHT / NOTE)
│   │   └── ReaderAnnotation    (unified annotation model)
│   │
│   ├── navigation/          ← App navigation
│   │   ├── NavDestination      (sealed class: Home, Library, Reader, etc.)
│   │   └── NavAction           (OPEN / RETURN / DEEP_LINK)
│   │
│   └── theme/               ← Runtime theme system
│       └── AppThemePalette      (Default, Night, Sepia, High Contrast)
│
├── ui/
│   ├── reader/              ← Reader UI components (Phase 5)
│   │   ├── ReaderActivity       (coordinator only)
│   │   ├── ReaderScreen          (main content host)
│   │   ├── ReaderToolbar         (top chrome bar)
│   │   ├── ReaderSettingsPanel   (font, margins, theme)
│   │   ├── ReaderTocPanel        (table of contents overlay)
│   │   ├── ReaderProgressBar     (bottom seeker + page indicator)
│   │   └── ReaderPageControls    (page turn, snapshot)
│   │
│   ├── library/             ← Library screens (Phase 7)
│   ├── details/             ← Book details (Phase 7)
│   ├── settings/            ← App settings (Phase 7)
│   └── common/              ← Shared UI components (Phase 7)
│
├── data/                    ← Room database layer (existing)
│   ├── AppDatabase, BookEntity, BookmarkEntity, HighlightEntity, ...
│   └── PrefsManager, BookRepository
│
├── epub/                    ← Legacy EPUB layer (existing, migrating to core/epub/)
│   ├── EpubParser, EpubResourceResolver, EpubImporter, EpubSearchEngine
│   ├── EpubPageMap, ReaderPageMapping, ReaderProgressMath
│   ├── ReaderSelectionBridge, ReaderSelectionLocator
│   └── ReaderTtsController, ReaderTtsDocument, DictionaryLookup
│
└── (existing activities, adapters, view models)
```

## Semantic Design System

### Color Tokens (design_system.xml)

| Token | Light | Dark | Purpose |
|-------|-------|------|---------|
| colorPrimary | plum #503A65 | plum | FAB, chips, drawer header |
| colorPrimaryVariant | purple #574F7D | purple | Primary variant |
| colorBackground | mint #E0F0EA | eggplant #3C2A4D | App background |
| colorSurface | white | plum | Cards, surfaces |
| colorSurfaceVariant | mint | purple | Alternative surfaces |
| colorTextPrimary | eggplant | mint | Primary text |
| colorTextSecondary | purple | slate | Secondary text |
| colorTextTertiary | slate | slate | Faint text |
| colorDivider | slate | purple | Borders, dividers |
| colorAccent | mint | mint | Accent highlights |
| colorReaderBackground | white | white | EPUB content bg (theme-controlled) |
| colorReaderText | black | black | EPUB content text (theme-controlled) |
| colorReaderChromeBackground | eggplant | eggplant | Reader chrome (always dark) |
| colorReaderChromeText | white | white | Reader chrome text |

### Spacing Scale (4dp-based)

| Token | Value | Usage |
|-------|-------|-------|
| spacing_xs | 4dp | Minimal gaps |
| spacing_sm | 8dp | Row spacing, small padding |
| spacing_md | 12dp | Row padding, card spacing |
| spacing_lg | 16dp | Screen padding, card padding |
| spacing_xl | 24dp | Section spacing |
| spacing_xxl | 32dp | Large gaps |

### Type Scale

| Style | Size | Weight | Color |
|-------|------|--------|-------|
| Livre.Overline | 12sp | bold | TextSecondary |
| Livre.Caption | 11sp | normal | TextTertiary |
| Livre.BodySmall | 13sp | normal | TextSecondary |
| Livre.Body | 14sp | normal | TextPrimary |
| Livre.BodyLarge | 16sp | normal | TextPrimary |
| Livre.Title | 18sp | bold | TextPrimary |
| Livre.TitleLarge | 22sp | bold | TextPrimary |
| Livre.Headline | 26sp | bold | TextPrimary |

### Shape System

| Token | Radius | Usage |
|-------|--------|-------|
| card_corner_radius | 14dp | All Material cards |
| button_corner_radius | 12dp | All buttons |
| dialog_corner_radius | 16dp | Dialogs, bottom sheets |
| chip_corner_radius | 8dp | Chips, tags |
| thumbnail_corner_radius | 6dp | Book covers, thumbnails |

### Elevation System

| Token | Value | Usage |
|-------|-------|-------|
| card_elevation | 2dp | Standard cards |
| card_elevation_raised | 6dp | Hovered/active cards |
| dialog_elevation | 8dp | Dialogs, popups |
| toolbar_elevation | 4dp | App bars |
| fab_elevation | 6dp | FABs |
| popup_elevation | 8dp | Popups, selection toolbar |

## Theme System

### Architecture
```
User selects theme
      ↓
AppThemePalette (core/theme/)
      ↓
Theme (themes.xml — ?attr/ assignments)
      ↓
Design System Tokens (design_system.xml)
      ↓
Component Styles (component_styles.xml)
      ↓
Screens & Layouts
```

### Available Themes
1. **Original (Default)** — mint background, eggplant text, plum accents
2. **Night** — eggplant background, mint text, plum surfaces
3. **Sepia** — warm cream tones
4. **High Contrast** — maximum readability (black on white)

### Reader Themes (separate from app themes)
Controlled by `ReaderThemes.kt` — 6 reading themes (Ivory, Nordic Eco, Alabaster, Candlelight, Onyx, Midnight Slate). Only the EPUB content area changes; the reader chrome stays dark.

## EPUB Parse Pipeline

```
EPUB file
    ↓
EpubContainerParser    (META-INF/container.xml → opfPath)
    ↓
EpubPackageParser      (OPF → metadata + manifest + spine)
    ↓
EpubNavigationParser   (nav.xhtml / NCX → TOC entries)
    ↓
EpubParsePipeline      (orchestrates → EpubParseResult)
    ↓
EpubDocument           (implements ReaderDocument)
```

### Error Classification
```
EpubParseResult
├── document: EpubDocument?
├── status: EpubParseStatus (VALID / VALID_WITH_WARNINGS / RECOVERED / UNSUPPORTED / INVALID)
└── diagnostics: List<EpubDiagnostic>
    ├── severity: INFO / WARNING / ERROR
    ├── code: String (e.g. "MISSING_CONTAINER")
    ├── message: String
    └── path: String?
```

## Reader Position Model

```
ReaderPosition
├── spineIndex        (which chapter)
├── scrollRatio       (0..1 within chapter)
├── pageInChapter     (renderer-specific page)
├── domAnchor         (XPath for precise restore)
├── charOffset        (for TTS/highlight alignment)
└── fragment          (#id deep-link)
```

Consumed by:
- Bookmark storage
- Highlight storage
- Reading progress persistence
- TTS word alignment
- Search result navigation
- Future annotations

## Implementation Phases

### Phase 1: Design System Foundation ✅
- Semantic color tokens (design_system.xml)
- Typography styles (typography.xml)
- Shape appearances (shapes.xml)
- Component styles (component_styles.xml)
- Custom theme attributes (attrs.xml)
- Updated themes.xml with semantic attribute assignments
- Night theme override (values-night/themes.xml)
- Backward-compatible theme aliases

### Phase 2: Core Reader Contracts ✅
- ReaderDocument (format-agnostic interface)
- ReaderPosition (canonical location)
- ReaderProgress (0..1 fraction)
- ReaderSettings (appearance preferences)
- ReaderViewport (dimensions)
- ReaderPage (visual page)
- ReaderRenderer (renderer contract)
- Unit tests for all contracts

### Phase 3: EPUB Layer ✅
- EpubContainerParser
- EpubPackageParser
- EpubNavigationParser
- EpubMetadata, EpubManifest, EpubSpine, EpubNavigation
- EpubDocument (implements ReaderDocument)
- EpubParseResult + EpubParseStatus + EpubDiagnostic
- EpubParsePipeline (orchestrates parse stages)
- Unit tests for contracts

### Phase 4: Theme System ✅
- AppThemePalette (Default, Night, Sepia, High Contrast)
- Theme registry

### Phase 5: Annotation & Navigation Contracts ✅
- AnnotationLocation, AnnotationType, ReaderAnnotation
- NavDestination (sealed class), NavAction

### Phase 6: ReaderActivity Decomposition (NEXT)
Extract controllers from the 5,118-line ReaderActivity:
- ReaderWebViewController (WebView setup, resource interception)
- ReaderCssBuilder (CSS generation)
- ReaderPaginationScriptBuilder (pagination JS)
- ReaderProgressController (polling, parsing, persistence)
- ReaderNavigationController (next/prev, seek, history)
- ReaderChromeController (top/bottom chrome, page indicator)
- ReaderOverlayController (TOC/bookmarks/highlights/search)
- ReaderSelectionController (action mode, toolbar, define/search/share)
- ReaderTtsUiController (TTS overlay/settings UI)

### Phase 7: Feature Services
- AnnotationRepository / HighlightService
- BookmarkService
- ReaderSearchService
- DefinitionService
- ReaderTtsCoordinator

### Phase 8: MainActivity Decomposition
- AppNavigationController
- ShelfStateStore
- ScrollStateStore
- HomeScreenController
- LibraryScreenController
- FolderImportController
- SettingsScreenController

### Phase 9: Layout Migration
Migrate all 28 layout files to use semantic tokens (?attr/) and component styles.

### Phase 10: Full Runtime Theme Switching
- Theme preferences layer
- Dynamic theme application
- Custom user colors
