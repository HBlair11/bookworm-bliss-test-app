package com.bookwormbliss.app.core.reader

/**
 * Reader progress — a normalized 0..1 fraction representing how far
 * through the entire book the reader is.
 *
 * This is computed from [ReaderPosition] and per-spine page counts.
 * When page counts are available (measured by the renderer), the
 * progress is page-accurate. When they're not, it falls back to a
 * chapter-level estimate.
 *
 * This model is pure (no Android dependencies) so it can be unit-tested.
 */
data class ReaderProgress(
    val fraction: Float,
    val spineIndex: Int,
    val pageInChapter: Int,
    val totalPages: Int,
) {
    /** Clamp to 0..1. */
    val clampedFraction: Float get() = fraction.coerceIn(0f, 1f)

    /** Percentage as an integer (0..100). */
    val percent: Int get() = (clampedFraction * 100f).toInt()

    /** Human-readable label, e.g. "37%". */
    val label: String get() = "$percent%"

    companion object {
        val ZERO = ReaderProgress(0f, 0, 0, 0)
        val COMPLETE = ReaderProgress(1f, 0, 0, 0)

        /**
         * Compute overall progress from a position and page counts.
         * Uses page-accurate math when page counts are available;
         * falls back to chapter-level estimation otherwise.
         */
        fun compute(
            position: ReaderPosition,
            spineSize: Int,
            pageCounts: IntArray?,
        ): ReaderProgress {
            if (spineSize <= 0) return ZERO

            val index = position.spineIndex.coerceIn(0, spineSize - 1)
            val ratio = position.scrollRatio.coerceIn(0f, 1f)

            if (pageCounts != null && pageCounts.size == spineSize && pageCounts.none { it < 0 }) {
                val total = pageCounts.sumOf { it.coerceAtLeast(1) }
                if (total <= 0) return ZERO

                val prefix = IntArray(pageCounts.size)
                var acc = 0
                for (i in pageCounts.indices) {
                    prefix[i] = acc
                    acc += pageCounts[i].coerceAtLeast(1)
                }

                val inSpine = pageCounts[index].coerceAtLeast(1)
                val absolute = (prefix[index] + ratio * inSpine).coerceAtMost(total.toFloat())
                val fraction = (absolute / total.toFloat()).coerceIn(0f, 1f)
                return ReaderProgress(
                    fraction = fraction,
                    spineIndex = index,
                    pageInChapter = position.pageInChapter,
                    totalPages = total,
                )
            }

            // Fallback: chapter-level progress
            val fraction = ((index + ratio) / spineSize.toFloat()).coerceIn(0f, 1f)
            return ReaderProgress(
                fraction = fraction,
                spineIndex = index,
                pageInChapter = position.pageInChapter,
                totalPages = spineSize,
            )
        }
    }
}
