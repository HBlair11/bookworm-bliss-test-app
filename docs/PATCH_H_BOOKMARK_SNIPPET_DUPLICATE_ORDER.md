# Patch H — Bookmark snippet, duplicate handling, and ordering

- Whole-page bookmarks capture text from the rendered page without character-by-character extraction.
- Whole-page and selected-text bookmarks have distinct internal types.
- Duplicate bookmark attempts show `Bookmark exists` with an explicit Delete action.
- Delete from that snackbar uses the existing deletion + Undo behavior.
- Bookmark list is ordered newest-created first.
- Percentage remains hidden from the bookmark UI.
- Reader Settings/reflow, Search, selection toolbar, TTS, navigation/history, and highlights are unchanged.
