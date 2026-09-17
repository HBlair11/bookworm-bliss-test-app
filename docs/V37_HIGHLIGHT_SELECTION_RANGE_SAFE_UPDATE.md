# v37 Highlight Selection Range Safe Update

## Purpose

Fix persistent user-created highlights for arbitrary text selections without using DOM range operations that extract or reparent EPUB content.

## Changes

- Removed `Range.surroundContents()` and `Range.extractContents()` from persistent highlight injection.
- Persistent highlights now decorate individual text-node fragments by splitting only the affected text node and placing an inline `<mark>` around the selected fragment.
- Multi-sentence, partial-sentence, partial-paragraph, multi-paragraph, and inline-span selections can therefore be represented by multiple highlight fragments without moving EPUB content between its original DOM boundaries.
- Kept the existing text/prefix/suffix occurrence-resolution strategy and element-path preference for persisted highlights.
- Removed column-break avoidance styling from persistent highlight marks and explicitly keep the decoration inline with inherited font/line-height and zero padding/margin/border.
- Highlight deletion now removes all fragments belonging to the same highlight ID.

## Scope

This patch changes only persistent user-created highlight rendering/styling and its fragment removal. It does not change TTS highlighting, selected-text bookmark storage/navigation, reader search, pagination initialization, database schema, or app version.

## Acceptance criteria

A highlight must remain correct for selections that are words, phrases, partial words, partial or complete sentences, multiple sentences, partial or complete paragraphs, multiple paragraphs, selections crossing inline/nested formatting elements, punctuation boundaries, line wraps, and legitimate page/column boundaries. Applying or removing a highlight must not rearrange, cut off, duplicate, or otherwise rewrite EPUB text.
