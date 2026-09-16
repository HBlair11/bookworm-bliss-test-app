package com.bookwormbliss.app.reader

/**
 * Canonical position inside an EPUB document.
 *
 * EPUB content is reflowable, so a screen/page number is not a stable document
 * address. The foundation therefore stores an EPUB spine index plus a normalized
 * horizontal offset within that spine item. The renderer converts this logical
 * position to the current viewport after each load.
 */
data class ReaderDocumentPosition(
    val spineIndex: Int,
    val offsetRatio: Float,
) {
    fun normalized(): ReaderDocumentPosition =
        copy(spineIndex = spineIndex.coerceAtLeast(0), offsetRatio = offsetRatio.coerceIn(0f, 1f))

    fun encode(): String {
        val p = normalized()
        return "${p.spineIndex}:${"%.6f".format(java.util.Locale.US, p.offsetRatio)}"
    }

    companion object {
        fun decode(value: String?): ReaderDocumentPosition? {
            val parts = value?.trim()?.split(':') ?: return null
            if (parts.size != 2) return null
            val spine = parts[0].toIntOrNull() ?: return null
            val ratio = parts[1].toFloatOrNull() ?: return null
            return ReaderDocumentPosition(spine, ratio).normalized()
        }
    }
}
