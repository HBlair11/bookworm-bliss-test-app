package com.epubreader.app.core.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubParseStatusTest {

    @Test
    fun `VALID is loadable and has no issues`() {
        assertTrue(EpubParseStatus.VALID.isLoadable)
        assertFalse(EpubParseStatus.VALID.hasIssues)
    }

    @Test
    fun `VALID_WITH_WARNINGS is loadable with issues`() {
        assertTrue(EpubParseStatus.VALID_WITH_WARNINGS.isLoadable)
        assertTrue(EpubParseStatus.VALID_WITH_WARNINGS.hasIssues)
    }

    @Test
    fun `RECOVERED is loadable with issues`() {
        assertTrue(EpubParseStatus.RECOVERED.isLoadable)
        assertTrue(EpubParseStatus.RECOVERED.hasIssues)
    }

    @Test
    fun `UNSUPPORTED is loadable with issues`() {
        assertTrue(EpubParseStatus.UNSUPPORTED.isLoadable)
        assertTrue(EpubParseStatus.UNSUPPORTED.hasIssues)
    }

    @Test
    fun `INVALID is not loadable with issues`() {
        assertFalse(EpubParseStatus.INVALID.isLoadable)
        assertTrue(EpubParseStatus.INVALID.hasIssues)
    }
}

class EpubDiagnosticTest {

    @Test
    fun `error diagnostic is error`() {
        val d = EpubDiagnostic(
            severity = EpubDiagnosticSeverity.ERROR,
            code = "TEST",
            message = "test error",
        )
        assertTrue(d.isError)
        assertFalse(d.isWarning)
    }

    @Test
    fun `warning diagnostic is warning`() {
        val d = EpubDiagnostic(
            severity = EpubDiagnosticSeverity.WARNING,
            code = "TEST",
            message = "test warning",
        )
        assertFalse(d.isError)
        assertTrue(d.isWarning)
    }

    @Test
    fun `info diagnostic is neither error nor warning`() {
        val d = EpubDiagnostic(
            severity = EpubDiagnosticSeverity.INFO,
            code = "TEST",
            message = "test info",
        )
        assertFalse(d.isError)
        assertFalse(d.isWarning)
    }
}

class EpubParseResultTest {

    @Test
    fun `valid result is loadable`() {
        val doc = createMinimalDoc()
        val result = EpubParseResult.valid(doc)
        assertTrue(result.isLoadable)
        assertEquals(EpubParseStatus.VALID, result.status)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `valid with warnings has diagnostics`() {
        val doc = createMinimalDoc()
        val diag = EpubDiagnostic(EpubDiagnosticSeverity.WARNING, "TEST", "warning")
        val result = EpubParseResult.validWithWarnings(doc, listOf(diag))
        assertTrue(result.isLoadable)
        assertEquals(EpubParseStatus.VALID_WITH_WARNINGS, result.status)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `invalid result is not loadable`() {
        val diag = EpubDiagnostic(EpubDiagnosticSeverity.ERROR, "ERR", "error")
        val result = EpubParseResult.invalid(listOf(diag))
        assertFalse(result.isLoadable)
        assertEquals(EpubParseStatus.INVALID, result.status)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `requireDocument throws for invalid result`() {
        val diag = EpubDiagnostic(EpubDiagnosticSeverity.ERROR, "ERR", "error")
        val result = EpubParseResult.invalid(listOf(diag))
        try {
            result.requireDocument()
            assert(false) { "Should have thrown" }
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `requireDocument returns document for valid result`() {
        val doc = createMinimalDoc()
        val result = EpubParseResult.valid(doc)
        assertNotNull(result.requireDocument())
    }

    private fun createMinimalDoc(): EpubDocument {
        val file = java.io.File("/tmp/test.epub")
        return EpubDocument(
            file = file,
            container = EpubContainer(file, "OEBPS/content.opf", "OEBPS"),
            metadata = EpubMetadata(title = "Test"),
            manifest = EpubManifest(emptyMap()),
            spine = EpubSpine(emptyList()),
            navigation = EpubNavigation(emptyList()),
            coverHref = null,
        )
    }
}

class EpubSpineTest {

    @Test
    fun `indexForHref finds matching spine item`() {
        val spine = EpubSpine(listOf(
            EpubSpineItem("ch1", "OEBPS/ch1.xhtml", "application/xhtml+xml"),
            EpubSpineItem("ch2", "OEBPS/ch2.xhtml", "application/xhtml+xml"),
            EpubSpineItem("ch3", "OEBPS/ch3.xhtml", "application/xhtml+xml"),
        ))
        assertEquals(0, spine.indexForHref("OEBPS/ch1.xhtml"))
        assertEquals(1, spine.indexForHref("OEBPS/ch2.xhtml"))
        assertEquals(2, spine.indexForHref("OEBPS/ch3.xhtml"))
    }

    @Test
    fun `indexForHref strips fragment`() {
        val spine = EpubSpine(listOf(
            EpubSpineItem("ch1", "OEBPS/ch1.xhtml", "application/xhtml+xml"),
        ))
        assertEquals(0, spine.indexForHref("OEBPS/ch1.xhtml#section1"))
    }

    @Test
    fun `indexForHref returns -1 for missing href`() {
        val spine = EpubSpine(listOf(
            EpubSpineItem("ch1", "OEBPS/ch1.xhtml", "application/xhtml+xml"),
        ))
        assertEquals(-1, spine.indexForHref("OEBPS/nonexistent.xhtml"))
    }

    @Test
    fun `empty spine has size 0`() {
        val spine = EpubSpine(emptyList())
        assertEquals(0, spine.size)
        assertNull(spine.itemAt(0))
    }
}
