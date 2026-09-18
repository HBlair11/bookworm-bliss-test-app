# Phase 9 — Layout Migration

**Goal.** Migrate every screen layout to the semantic design tokens defined
in Phases 1–5, so that "one change has a predictable application-wide
effect" and Phase 10 (full runtime theme switching) has a complete
`?attr/` surface to work with.

Blueprint rule being applied: *"Migrate all layout files to use semantic
tokens (`?attr/`) and component styles."*

**Scope note:** the blueprint counted 28 layout files; the project
actually contains **35** screen layouts. All 35 were migrated, plus 6
supporting drawables and the legacy `styles.xml` value file.

**Migration discipline:** this phase is *value-preserving*. Every
substitution resolves to the same pixel value it replaced, so screens
render identically to v1.0. The only deliberate size snaps are three
sub-perceptual type steps (9→10sp, 17→18sp, 19→20sp — see below).

---

## What was migrated (4 passes, 41 files)

| Pass | Before | After | Count |
|------|--------|-------|-------|
| Colors — direct palette refs → theme attrs | `@color/reader_chrome_text`, `@color/light_border`, `@color/accent`, `@color/secondary`, `@color/pop`, `@color/dark_text`, `@color/progress_*`, `@color/reader_bg_light`, … | `?attr/livreColorReaderChrome*`, `?attr/livreColorDivider`, `?attr/colorPrimary`, `?attr/colorSecondary`, `?attr/livreColorAccent`, `?attr/livreColorSuccess`, `?attr/livreColorDisabled`, `?attr/livreColorReaderBackground` | 57 |
| Text sizes — raw `sp` → type-scale tokens | `android:textSize="15sp"` … | `@dimen/text_size_body_medium`, `…_caption`, `…_body`, `…_body_large`, `…_title`, `…_title_large`, `…_headline`, `…_hero`, `…_cover_label`, … | 117 |
| Legacy `app_*` dimens → design-system tokens | `@dimen/app_row_content_padding_h`, `@dimen/app_card_padding`, `@dimen/app_card_corner`, `@dimen/app_selection_toolbar_*`, … (the Patch 18 / v37 parallel dimension system, 200 layout refs) | `?attr/livreScreenPadding`, `?attr/livreCardPadding`, `?attr/livreRowSpacing`, `@dimen/row_content_padding_h`, `@dimen/card_corner_radius`, `@dimen/selection_toolbar_*`, … | 200 |
| Spacing — raw 4dp-grid paddings/margins → spacing scale | `android:paddingTop="16dp"`, `android:layout_margin="8dp"`, … | `@dimen/spacing_xs/sm/md/lg/xl/xxl` | 127 |

Post-migration audit across `layout/`:

- raw hex colors: **0** · direct legacy color refs: **0** · raw `textSize` literals: **0**
- legacy `app_*` dimen refs: **0** · raw on-scale paddings/margins: **0**
- token usage now in layouts: 43 × `?attr/livre*`, 117 × `@dimen/text_size_*`, 127 × `@dimen/spacing_*`

## Color mapping (layouts + drawables)

| Legacy reference | Semantic token | Notes |
|---|---|---|
| `@color/reader_chrome_text` / `_text_muted` | `?attr/livreColorReaderChromeText` / `…TextSecondary` | 14 uses |
| `@color/reader_chrome_bg` / `_surface` / `_accent` / `_scrim` | `?attr/livreColorReaderChrome*` | 7 uses |
| `@color/reader_bg_light` (WebView bed) | `?attr/livreColorReaderBackground` | becomes reading-theme switchable |
| `@color/light_border` | `?attr/livreColorDivider` | also `app:strokeColor` |
| `@color/accent` | `?attr/colorPrimary` | plum; incl. `chip_bg` drawable |
| `@color/secondary` | `?attr/colorSecondary` | slate |
| `@color/pop` | `?attr/livreColorAccent` | mint |
| `@color/dark_text`, `@color/progress_fill` | `?attr/livreColorSuccess` | mint (misleading legacy name `dark_text`) |
| `@color/progress_track` | `?attr/livreColorDisabled` | slate |
| `@color/white` (contextual) | `?attr/colorOnPrimary` (FAB icon, drawer header, count chip) / `?attr/livreColorReaderChromeText` (reader overlay) | |

`?attr/` theme references also now power `android:progressTint`,
`android:thumbTint`, `app:drawableTint`, `android:tint`, `app:boxStrokeColor`,
`app:titleTextColor` and `app:navigationIconTint` (minSdk 24, safe).

## New tokens in `design_system.xml`

| Token | Value | Purpose |
|---|---|---|
| `text_size_body_medium` | 15sp | the app's de-facto "value text" size (details/TOC/sort rows) — tokenized instead of snapping 15 existing screens |
| `text_size_hero` | 20sp | home greeting, Continue-Reading title, About title |
| `text_size_cover_label` | 10sp | micro "% read" label on cover thumbnails |
| `reader_icon_inset` | 10dp | icon-glyph keyline inset (was `app_reader_icon_inset`) |
| `seeker_button_size` | 40dp | reader seeker touch target (was `app_seeker_button_size`) |

Deliberate type snaps (imperceptible, normalized onto the scale):
9sp→10sp, 17sp→18sp, 19sp→20sp.

## Files changed

- `layout/` — all 35 layouts (activity_*, item_*, dialog_*, bottom_sheet_*,
  overlay_list, reader_selection_toolbar, view_home, view_definition_card)
- `drawable/` — chip_bg, page_indicator_bg, progress_line,
  reader_search_input_bg, reader_selection_toolbar_bg, toc_item_background
- `values/design_system.xml` — 5 new tokens (above)
- `values/styles.xml` — legacy reader-settings styles now reference tokens
- `values/dimens.xml` — the 27 legacy `app_*` definitions removed; the file
  now only documents the migration mapping (design_system.xml is the single
  source of truth for dimensions)
- `ui/reader/ReaderSelectionController.kt`, `ui/reader/ReaderOverlayController.kt`
  — 9 `R.dimen.app_*` code references updated to the design-system names

## Boundary decisions (what stayed raw, and why)

1. `@color/black` on the `DrawerLayout` root — structural backdrop behind
   the drawer pane, not a themed surface.
2. `@color/reader_history_text` (`#8A8A8A`) — fixed muted gray for the
   transparent reading-history overlay, documented as intentionally
   theme-independent in `colors.xml`.
3. Micro paddings < 4dp and off-scale values (1/2/3/5/6/7/10/14/18/20/26/28dp
   where not covered by a named component dim) — intentional micro-adjustments
   with no scale slot; tokenizing them would add noise, not control.
4. Component dimensions (`262dp` shelf card, `72/96dp` spacers, icon
   `layout_width/height`) — one-off structural sizes, not spacing.
5. A few deliberately-unmatched component values: reader chrome bars keep a
   structural `6dp` elevation (no chrome-elevation token exists);
   `item_book_list` keeps its `10dp` card radius (between `thumbnail` 6 and
   `card` 14, an intentional compact list-card look); `item_toc` keeps its
   `44dp` row min-height (row anatomy, not an icon button); flat cards keep
   `0dp`/`1dp` hairline elevations.
6. `Widget.Livre.*` component styles were **not** force-applied. Adopting
   them changes button heights, corner radii and elevations — visual
   changes that need emulator review. Layouts now consume the design system
   through tokens (colors, type, spacing), which is what Phase 10 needs;
   component-style adoption is deferred to a visually verified pass.
7. Inset behavior untouched: the theme-level `android:fitsSystemWindows`
   in `Theme.Livre` and the `SystemBarController` overlay views are
   preserved exactly as-is (flagged in the Phase 8 UI diagnosis for
   Phase 10 review).

## Verification

```
./gradlew assembleDebug            → SUCCESS (clean build)
./gradlew testDebugUnitTest        → 158 tests, 4 failures
                                     (HomeOrderingTest ×2, EpubNavigationParserTest ×2 —
                                      pre-existing, identical on the pristine baseline)
```

Resource linking (`processDebugResources`/`assembleDebug`) validates every
`?attr/` and token reference. Clean-build APK size 16.55 MB — unchanged
from Phase 8 (16.56 MB), confirming the token indirection adds no weight.

## What Phase 10 gains

Every screen color decision now flows through `?attr/` — flipping a theme
overlay at runtime (or adding a Pastel / High-Contrast variant) re-skins
every screen, list row, dialog, bottom sheet and reader overlay without
touching a single layout. Spacing/typography likewise flow from
`design_system.xml` alone.
