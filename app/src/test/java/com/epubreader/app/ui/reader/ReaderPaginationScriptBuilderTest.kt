package com.epubreader.app.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for the Phase 6 extracted [ReaderPaginationScriptBuilder].
 *
 * Verifies that the pagination JS generation produces correct output with
 * the expected structure and injected guard values.
 */
class ReaderPaginationScriptBuilderTest {

    @Test
    fun `build produces valid script tag`() {
        val js = ReaderPaginationScriptBuilder.build(56, 56)
        assertTrue("JS should start with <script>", js.startsWith("<script>"))
        assertTrue("JS should end with </script>", js.endsWith("</script>"))
    }

    @Test
    fun `build injects guard values`() {
        val js = ReaderPaginationScriptBuilder.build(56, 56)
        assertTrue("JS should contain GUARD value", js.contains("var GUARD = 56;"))
        assertTrue("JS should contain TOP_GUARD value", js.contains("var TOP_GUARD = 56;"))
    }

    @Test
    fun `build injects custom guard values`() {
        val js = ReaderPaginationScriptBuilder.build(0, 0)
        assertTrue("JS should contain GUARD=0", js.contains("var GUARD = 0;"))
        assertTrue("JS should contain TOP_GUARD=0", js.contains("var TOP_GUARD = 0;"))
    }

    @Test
    fun `build contains Caesura object with all methods`() {
        val js = ReaderPaginationScriptBuilder.build(56, 56)
        val methods = listOf(
            "apply", "pageCount", "currentPage", "gotoPage",
            "nextPage", "prevPage", "ratio",
            "gotoElementById", "pageForElementById",
            "pageForTextAnchor", "pageForWholePageAnchor",
            "pageSnippet", "setSelectionPageLock",
            "highlightPageById", "gotoHighlightById"
        )
        for (method in methods) {
            assertTrue("JS should expose Caesura.$method", js.contains("$method:"))
        }
    }

    @Test
    fun `build contains selection page lock logic`() {
        val js = ReaderPaginationScriptBuilder.build(56, 56)
        assertTrue("JS should contain selectionPageLock", js.contains("selectionPageLock"))
        assertTrue("JS should contain setSelectionPageLock", js.contains("setSelectionPageLock"))
    }

    @Test
    fun `build contains pagination functions`() {
        val js = ReaderPaginationScriptBuilder.build(56, 56)
        assertTrue("JS should contain pageCount function", js.contains("function pageCount()"))
        assertTrue("JS should contain currentPage function", js.contains("function currentPage()"))
        assertTrue("JS should contain gotoPage function", js.contains("function gotoPage("))
        assertTrue("JS should contain nextPage function", js.contains("function nextPage("))
        assertTrue("JS should contain prevPage function", js.contains("function prevPage("))
    }

    @Test
    fun `build contains DOMContentLoaded listener`() {
        val js = ReaderPaginationScriptBuilder.build(56, 56)
        assertTrue("JS should contain DOMContentLoaded listener", js.contains("DOMContentLoaded"))
    }

    @Test
    fun `build contains resize listener`() {
        val js = ReaderPaginationScriptBuilder.build(56, 56)
        assertTrue("JS should contain resize listener", js.contains("addEventListener('resize'"))
    }
}
