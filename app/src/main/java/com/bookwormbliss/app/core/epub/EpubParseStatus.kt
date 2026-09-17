package com.bookwormbliss.app.core.epub

/**
 * Classification of EPUB parse results.
 *
 * Instead of silent fallback behavior, every parse attempt produces
 * one of these statuses, making it substantially easier to diagnose
 * weird EPUBs later.
 *
 * @property VALID             The EPUB parsed cleanly with no issues.
 * @property VALID_WITH_WARNINGS The EPUB parsed but has minor issues
 *                               (missing optional metadata, non-standard
 *                               elements, etc.). The document is usable.
 * @property RECOVERED          The EPUB had structural problems that were
 *                               automatically recovered from (missing
 *                               container.xml, fallback TOC, etc.).
 *                               The document is usable but may have quirks.
 * @property UNSUPPORTED        The EPUB uses features this parser doesn't
 *                               support (e.g. fixed-layout, media overlays).
 *                               The document may render partially.
 * @property INVALID            The EPUB is structurally broken and cannot
 *                               be loaded. Diagnostics contain the reasons.
 */
enum class EpubParseStatus {
    VALID,
    VALID_WITH_WARNINGS,
    RECOVERED,
    UNSUPPORTED,
    INVALID,
    ;

    /** True if the document can be loaded despite issues. */
    val isLoadable: Boolean
        get() = this != INVALID

    /** True if the parse had any issues at all. */
    val hasIssues: Boolean
        get() = this != VALID
}
