package com.bookwormbliss.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReaderDocumentPositionTest {
    @Test
    fun `position round trips through stable encoding`() {
        val original = ReaderDocumentPosition(4, 0.375f)
        val decoded = ReaderDocumentPosition.decode(original.encode())
        assertNotNull(decoded)
        assertEquals(original.spineIndex, decoded!!.spineIndex)
        assertEquals(original.offsetRatio, decoded.offsetRatio, 0.00001f)
    }

    @Test
    fun `position normalization clamps invalid values`() {
        val normalized = ReaderDocumentPosition(-2, 4f).normalized()
        assertEquals(0, normalized.spineIndex)
        assertEquals(1f, normalized.offsetRatio, 0f)
    }

    @Test
    fun `invalid position text is rejected`() {
        assertEquals(null, ReaderDocumentPosition.decode("chapter:page"))
        assertEquals(null, ReaderDocumentPosition.decode("1"))
    }
}
