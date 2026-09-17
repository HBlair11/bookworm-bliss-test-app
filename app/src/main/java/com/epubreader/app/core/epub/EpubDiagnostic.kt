package com.epubreader.app.core.epub

/**
 * Severity level for a parse diagnostic.
 */
enum class EpubDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

/**
 * A structured diagnostic message from EPUB parsing.
 *
 * Instead of silent fallback behavior, every non-trivial parse decision
 * produces a diagnostic that can be inspected later. This makes it
 * substantially easier to diagnose weird EPUBs.
 *
 * @property severity   How serious this issue is.
 * @property code       A short machine-readable code (e.g. "MISSING_CONTAINER").
 * @property message    A human-readable description.
 * @property path       Optional path to the affected resource (e.g. "META-INF/container.xml").
 */
data class EpubDiagnostic(
    val severity: EpubDiagnosticSeverity,
    val code: String,
    val message: String,
    val path: String? = null,
) {
    val isError: Boolean get() = severity == EpubDiagnosticSeverity.ERROR
    val isWarning: Boolean get() = severity == EpubDiagnosticSeverity.WARNING
}
