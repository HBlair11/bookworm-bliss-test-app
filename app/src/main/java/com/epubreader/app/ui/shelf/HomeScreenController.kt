package com.epubreader.app.ui.shelf

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.epubreader.app.R
import com.epubreader.app.data.BookEntity
import com.epubreader.app.ui.HomeBookAdapter
import com.epubreader.app.ui.HomeContent
import com.epubreader.app.ui.HomeGroup
import com.epubreader.app.databinding.ViewHomeBinding

/**
 * HomeScreenController — extracted from MainActivity (Phase 8), redesigned
 * for Phase 10.
 *
 * Renders the dedicated Home screen:
 *   - Continue Reading hero (cover, title, author, series, current section,
 *     progress bar, Resume Reading + Book Details buttons)
 *   - Recently Added and Favorites horizontal shelves (compact)
 *   - Author/Series navigation cards (tappable rows, not book shelves) —
 *     clicking a row navigates to that Author/Series detail bookshelf with
 *     fromHome=true so back returns to Home; "All Authors"/"All Series"
 *     navigates to the full list; "View All" on Favorites navigates to the
 *     Favorites shelf.
 *
 * The controller is view-only: book taps and long-presses are forwarded
 * through [Callbacks] so the Activity keeps the single navigation path.
 */
class HomeScreenController(
    private val binding: ViewHomeBinding,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onOpenBook(book: BookEntity)
        fun onBookLongPressed(book: BookEntity): Boolean
        /** Phase 10: Open book details from the Continue Reading hero. */
        fun onOpenDetails(book: BookEntity)
        /** Phase 10: Open an author's bookshelf from the Home navigation card. */
        fun onOpenAuthor(name: String)
        /** Phase 10: Open a series' bookshelf from the Home navigation card. */
        fun onOpenSeries(name: String)
        /** Phase 10: Navigate to the full Authors list. */
        fun onViewAllAuthors()
        /** Phase 10: Navigate to the full Series list. */
        fun onViewAllSeries()
        /** Phase 10: Navigate to the full Favorites shelf. */
        fun onViewAllFavorites()
    }

    private var lastContent: HomeContent? = null
    var visible: Boolean = false

    private val recentlyAddedAdapter =
        HomeBookAdapter(callbacks::onOpenBook, callbacks::onBookLongPressed)
    private val favoritesAdapter =
        HomeBookAdapter(callbacks::onOpenBook, callbacks::onBookLongPressed)

    /** Sets up the two horizontal shelf adapters. Called once from onCreate. */
    fun setup() {
        binding.homeRecentlyAdded.layoutManager =
            LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
        binding.homeRecentlyAdded.adapter = recentlyAddedAdapter

        binding.homeFavorites.layoutManager =
            LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
        binding.homeFavorites.adapter = favoritesAdapter

        // Wire the View All / All Authors / All Series buttons.
        binding.homeFavoritesViewAll.setOnClickListener { callbacks.onViewAllFavorites() }
        binding.homeAllAuthors.setOnClickListener { callbacks.onViewAllAuthors() }
        binding.homeAllSeries.setOnClickListener { callbacks.onViewAllSeries() }
    }

    fun render(content: HomeContent) {
        lastContent = content
        if (!visible) return

        val ctx = binding.root.context

        binding.homeEmpty.visibility = if (content.hasBooks) View.GONE else View.VISIBLE

        // ---- Continue Reading hero ----
        val hasContinueReading = content.continueReading != null
        binding.homeContinueSection.visibility = if (hasContinueReading) View.VISIBLE else View.GONE
        binding.homeContinueCard.visibility = if (hasContinueReading) View.VISIBLE else View.GONE

        content.continueReading?.let { book ->
            binding.homeContinueTitle.text = book.title
            binding.homeContinueAuthor.text =
                book.author.ifBlank { ctx.getString(R.string.unknown_author) }

            val seriesText = book.series?.trim()?.takeIf { it.isNotEmpty() }?.let { series ->
                val index = book.seriesIndex
                if (index != null) {
                    val formattedIndex =
                        if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
                    ctx.getString(R.string.home_continue_series, "$series #$formattedIndex")
                } else {
                    ctx.getString(R.string.home_continue_series, series)
                }
            }
            binding.homeContinueSeries.text = seriesText
            binding.homeContinueSeries.visibility =
                if (seriesText.isNullOrBlank()) View.GONE else View.VISIBLE

            // Phase 10: "Current section" instead of "Location"
            val section = book.currentLocation?.trim().orEmpty()
            binding.homeContinueLocation.text = if (section.isBlank()) {
                ""
            } else {
                ctx.getString(R.string.home_continue_section, section)
            }
            binding.homeContinueLocation.visibility =
                if (section.isBlank()) View.GONE else View.VISIBLE

            val percent = (book.progress * 100).toInt()
            binding.homeContinueProgress.text = if (book.progress >= 0.90f) {
                ctx.getString(R.string.progress_completed)
            } else {
                ctx.getString(R.string.home_progress_percent, percent)
            }
            binding.homeContinueProgressBar.progress = percent
            loadCover(book, binding.homeContinueCover)

            // Phase 10: Resume Reading + Book Details buttons
            binding.homeContinueResume.setOnClickListener { callbacks.onOpenBook(book) }
            binding.homeContinueDetails.setOnClickListener { callbacks.onOpenDetails(book) }
            binding.homeContinueButton.setOnClickListener { callbacks.onOpenBook(book) }
            binding.homeContinueCard.setOnClickListener { callbacks.onOpenBook(book) }
        }

        // ---- Recently Added shelf ----
        binding.homeRecentlyAddedSection.visibility =
            if (content.recentlyAdded.isEmpty()) View.GONE else View.VISIBLE
        recentlyAddedAdapter.submitList(content.recentlyAdded)

        // ---- Favorites shelf ----
        binding.homeFavoritesSection.visibility =
            if (content.favorites.isEmpty()) View.GONE else View.VISIBLE
        favoritesAdapter.submitList(content.favorites)

        // ---- Author/Series navigation cards ----
        binding.homeAuthorsCard.visibility =
            if (content.topAuthors.isEmpty()) View.GONE else View.VISIBLE
        buildNavCards(binding.homeAuthorsNavContainer, content.topAuthors, isSeries = false)

        binding.homeSeriesCard.visibility =
            if (content.topSeries.isEmpty()) View.GONE else View.VISIBLE
        buildNavCards(binding.homeSeriesNavContainer, content.topSeries, isSeries = true)
    }

    /**
     * Builds tappable navigation rows for the given author/series groups.
     * Each row shows the name and book count; tapping navigates to the
     * detail bookshelf with fromHome=true.
     */
    private fun buildNavCards(
        container: LinearLayout,
        groups: List<HomeGroup>,
        isSeries: Boolean,
    ) {
        container.removeAllViews()
        val ctx = container.context
        groups.forEach { group ->
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(
                    (12 * resources.displayMetrics.density).toInt(),
                    (10 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(),
                    (10 * resources.displayMetrics.density).toInt(),
                )
                isClickable = true
                background = ctx.obtainStyledAttributes(
                    intArrayOf(android.R.attr.selectableItemBackground)
                ).use { it.getDrawable(0) }
                setOnClickListener {
                    if (isSeries) callbacks.onOpenSeries(group.name)
                    else callbacks.onOpenAuthor(group.name)
                }
            }

            val nameText = TextView(ctx).apply {
                text = group.name
                setTextColor(
                    ctx.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
                        .use { it.getColor(0, 0) }
                )
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val countText = TextView(ctx).apply {
                text = if (group.count == 1) {
                    if (isSeries) ctx.getString(R.string.home_vol_count_single)
                    else ctx.getString(R.string.home_book_count_single)
                } else {
                    if (isSeries) ctx.getString(R.string.home_vol_count, group.count)
                    else ctx.getString(R.string.home_book_count, group.count)
                }
                setTextColor(
                    ctx.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
                        .use { it.getColor(0, 0) }
                )
                textSize = 11f
            }

            row.addView(nameText)
            row.addView(countText)
            container.addView(row)
        }
    }

    /** Called when Home becomes the active view: re-renders the cached
     * content so Continue Reading reflects the latest DB state even if the
     * LiveData emitted while Home was hidden, and always scrolls Home to the
     * top so Continue Reading is visible. */
    fun onShown() {
        lastContent?.let { render(it) }
        binding.homeScroll.post { binding.homeScroll.scrollTo(0, 0) }
    }

    fun scrollToTop() {
        binding.homeScroll.post { binding.homeScroll.scrollTo(0, 0) }
    }

    private val resources get() = binding.root.context.resources

    private fun loadCover(book: BookEntity, view: android.widget.ImageView) {
        if (book.coverPath != null) {
            com.bumptech.glide.Glide.with(view)
                .load(java.io.File(book.coverPath))
                .centerCrop()
                .placeholder(R.drawable.cover_frame)
                .into(view)
        } else {
            com.bumptech.glide.Glide.with(view).clear(view)
            view.setImageResource(R.drawable.cover_frame)
        }
    }
}
