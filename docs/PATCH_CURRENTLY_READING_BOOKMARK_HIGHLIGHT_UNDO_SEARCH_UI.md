# Currently Reading / Bookmark / Highlight Undo + Search UI

## Changes

- Currently Reading swipe removal now shows “Removed from Currently Reading” with Undo; Undo restores the same book with `setCurrentlyReading(book.id)`.
- Book Details “Remove from Reading” now uses the same removal message and Undo behavior.
- Bookmark deletion is immediate and offers Undo to restore the exact bookmark entity.
- Highlight deletion is immediate and offers Undo to restore the exact highlight entity; when the deleted highlight belongs to the currently displayed chapter, it is re-injected into the WebView immediately.
- Existing book-library removal confirmation remains unchanged.
- SearchActivity now says “No matching books” for a non-empty query with no results.
- Search terminology is corrected so the in-book search hint is “Search this book”.
- Search keyboard is dismissed when leaving Search, selecting a book, or submitting the keyboard action.

## Scope

This is an isolated UX patch. Reader Search result navigation, TTS, selection toolbar, reader history/navigation behavior, TOC navigation, and bookmark/highlight navigation are otherwise unchanged.
