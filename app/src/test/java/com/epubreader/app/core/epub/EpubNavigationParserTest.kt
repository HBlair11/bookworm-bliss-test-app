package com.epubreader.app.core.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubNavigationParserTest {

    @Test
    fun `parseNavXml extracts EPUB 3 nav entries with correct levels`() {
        val navXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <body>
            <nav epub:type="toc">
              <ol>
                <li><a href="ch1.xhtml">Chapter 1</a>
                  <ol>
                    <li><a href="ch1.xhtml#sec1">Section 1.1</a></li>
                    <li><a href="ch1.xhtml#sec2">Section 1.2</a></li>
                  </ol>
                </li>
                <li><a href="ch2.xhtml">Chapter 2</a></li>
                <li><a href="ch3.xhtml">Chapter 3</a></li>
              </ol>
            </nav>
            </body>
            </html>
        """.trimIndent()

        val entries = EpubNavigationParser.parseNavXml(navXml, "OEBPS")

        assertEquals(5, entries.size)
        assertEquals("Chapter 1", entries[0].label)
        assertEquals(0, entries[0].level)
        assertEquals("OEBPS/ch1.xhtml", entries[0].href)
        assertEquals("Section 1.1", entries[1].label)
        assertEquals(1, entries[1].level)
        assertEquals("OEBPS/ch1.xhtml#sec1", entries[1].href)
        assertEquals("Chapter 2", entries[2].label)
        assertEquals(0, entries[2].level)
    }

    @Test
    fun `parseNcxXml extracts NCX entries with correct levels`() {
        val ncxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
            <navMap>
              <navPoint id="nav1">
                <navLabel><text>Chapter 1</text></navLabel>
                <content src="ch1.xhtml"/>
                <navPoint id="nav1-1">
                  <navLabel><text>Section 1.1</text></navLabel>
                  <content src="ch1.xhtml#sec1"/>
                </navPoint>
              </navPoint>
              <navPoint id="nav2">
                <navLabel><text>Chapter 2</text></navLabel>
                <content src="ch2.xhtml"/>
              </navPoint>
            </navMap>
            </ncx>
        """.trimIndent()

        val entries = EpubNavigationParser.parseNcxXml(ncxXml, "OEBPS")

        assertEquals(3, entries.size)
        assertEquals("Chapter 1", entries[0].label)
        assertEquals(0, entries[0].level)
        assertEquals("OEBPS/ch1.xhtml", entries[0].href)
        assertEquals("Section 1.1", entries[1].label)
        assertEquals(1, entries[1].level)
        assertEquals("OEBPS/ch1.xhtml#sec1", entries[1].href)
        assertEquals("Chapter 2", entries[2].label)
        assertEquals(0, entries[2].level)
    }

    @Test
    fun `parseNavXml returns empty list for malformed nav`() {
        val xml = "<html><body>No nav here</body></html>"
        val entries = EpubNavigationParser.parseNavXml(xml, "OEBPS")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `parseNavXml handles nested ol correctly`() {
        val navXml = """
            <html><body><nav>
              <ol>
                <li><a href="a.xhtml">A</a>
                  <ol>
                    <li><a href="a1.xhtml">A1</a>
                      <ol>
                        <li><a href="a1a.xhtml">A1a</a></li>
                      </ol>
                    </li>
                  </ol>
                </li>
              </ol>
            </nav></body></html>
        """.trimIndent()

        val entries = EpubNavigationParser.parseNavXml(navXml, "")

        assertEquals(3, entries.size)
        assertEquals(0, entries[0].level)
        assertEquals("A", entries[0].label)
        assertEquals(1, entries[1].level)
        assertEquals("A1", entries[1].label)
        assertEquals(2, entries[2].level)
        assertEquals("A1a", entries[2].label)
    }
}
