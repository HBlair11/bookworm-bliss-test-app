package com.epubreader.app.ui.shelf

import android.view.View
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
 * HomeScreenController — extracted from MainActivity (Phase 8).
 *
 * Renders the dedicated Home screen: the Continue Reading hero, the
 * Recently Added and Favorites shelves, and the top-author / top-series
 * horizontal shelves. Owns the eight HomeBookAdapters and the cached
 * [HomeContent] so Home can be re-rendered instantly when the user navigates
 * back to it, even when the LiveData emitted while a different view was on
 * screen (e.g. the user opened a book from Library and Continue Reading
 * updated while Home was hidden).
 *
 * The controller is view-only: it does not navigate or mutate data — book
 * taps and long-presses are forwarded through [Callbacks] so the Activity
 * keeps the single navigation path (openBook/showBookOptions).
 */
class HomeScreenController(
    private val binding: ViewHomeBinding,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onOpenBook(book: BookEntity)
        fun onBookLongPressed(book: BookEntity): Boolean
    }

    /** Cached Home content so the screen can be re-rendered instantly on
     * return. Updated on every emission even while Home is hidden. */
    private var lastContent: HomeContent? = null

    /** Set by AppNavigationController.applyView: true while the Home content
     * is on screen. Emissions received while hidden only refresh the cache. */
    var visible: Boolean = false

    private val adapters = mutableMapOf<Int, HomeBookAdapter>()

    /** Creates the eight horizontal shelf adapters + layout managers. Called
     * once from onCreate, in the original setupHomeShelves() position. */
    fun setup() {
        val specs = listOf(
            R.id.homeRecentlyAdded to binding.homeRecentlyAdded,
            R.id.homeFavorites to binding.homeFavorites,
            R.id.homeAuthorBooks1 to binding.homeAuthorBooks1,
            R.id.homeAuthorBooks2 to binding.homeAuthorBooks2,
            R.id.homeAuthorBooks3 to binding.homeAuthorBooks3,
            R.id.homeSeriesBooks1 to binding.homeSeriesBooks1,
            R.id.homeSeriesBooks2 to binding.homeSeriesBooks2,
            R.id.homeSeriesBooks3 to binding.homeSeriesBooks3,
        )
        specs.forEach { (key, recycler) ->
            val adapter = HomeBookAdapter(callbacks::onOpenBook, callbacks::onBookLongPressed)
            recycler.layoutManager =
                LinearLayoutManager(recycler.context, LinearLayoutManager.HORIZONTAL, false)
            recycler.adapter = adapter
            adapters[key] = adapter
        }
    }

    /** Renders [content] when Home is on screen; always refreshes the cache
     * so a later onShown() picks up the freshest data. */
    fun render(content: HomeContent) {
        lastContent = content
        if (!visible) return

        binding.homeEmpty.visibility = if (content.hasBooks) View.GONE else View.VISIBLE
        binding.homeRecentlyAddedSection.visibility =
            if (content.recentlyAdded.isEmpty()) View.GONE else View.VISIBLE
        val hasContinueReading = content.continueReading != null
        binding.homeContinueSection.visibility = if (hasContinueReading) View.VISIBLE else View.GONE
        binding.homeContinueCard.visibility = if (hasContinueReading) View.VISIBLE else View.GONE

        content.continueReading?.let { book ->
            binding.homeContinueTitle.text = book.title
            binding.homeContinueAuthor.text =
                book.author.ifBlank { binding.root.context.getString(R.string.unknown_author) }

            val seriesText = book.series
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { series ->
                    val index = book.seriesIndex
                    if (index != null) {
                        val formattedIndex =
                            if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
                        binding.root.context.getString(
                            R.string.home_continue_series, "$series #$formattedIndex"
                        )
                    } else {
                        binding.root.context.getString(R.string.home_continue_series, series)
                    }
                }
            binding.homeContinueSeries.text = seriesText
            binding.homeContinueSeries.visibility =
                if (seriesText.isNullOrBlank()) View.GONE else View.VISIBLE

            val location = book.currentLocation?.trim().orEmpty()
            binding.homeContinueLocation.text = if (location.isBlank()) {
                ""
            } else {
                binding.root.context.getString(R.string.home_continue_location, location)
            }
            binding.homeContinueLocation.visibility =
                if (location.isBlank()) View.GONE else View.VISIBLE
            binding.homeContinueProgress.text = if (book.progress >= 0.90f) {
                binding.root.context.getString(R.string.progress_completed)
            } else {
                binding.root.context.getString(
                    R.string.home_progress_percent, (book.progress * 100).toInt()
                )
            }
            loadCover(book, binding.homeContinueCover)
            binding.homeContinueButton.setOnClickListener { callbacks.onOpenBook(book) }
            binding.homeContinueCard.setOnClickListener { callbacks.onOpenBook(book) }
        }

        submitShelf(R.id.homeRecentlyAdded, content.recentlyAdded)
        binding.homeFavoritesSection.visibility =
            if (content.favorites.isEmpty()) View.GONE else View.VISIBLE
        submitShelf(R.id.homeFavorites, content.favorites)
        bindGroups(
            listOf(
                Triple(binding.homeAuthorGroup1, binding.homeAuthorTitle1, binding.homeAuthorBooks1),
                Triple(binding.homeAuthorGroup2, binding.homeAuthorTitle2, binding.homeAuthorBooks2),
                Triple(binding.homeAuthorGroup3, binding.homeAuthorTitle3, binding.homeAuthorBooks3),
            ),
            content.topAuthors,
        )
        binding.homeAuthorsSection.visibility =
            if (content.topAuthors.isEmpty()) View.GONE else View.VISIBLE
        bindGroups(
            listOf(
                Triple(binding.homeSeriesGroup1, binding.homeSeriesTitle1, binding.homeSeriesBooks1),
                Triple(binding.homeSeriesGroup2, binding.homeSeriesTitle2, binding.homeSeriesBooks2),
                Triple(binding.homeSeriesGroup3, binding.homeSeriesTitle3, binding.homeSeriesBooks3),
            ),
            content.topSeries,
        )
        binding.homeSeriesSection.visibility =
            if (content.topSeries.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Called when Home becomes the active view: re-renders the cached
     * content (so Continue Reading reflects the latest DB state even if the
     * LiveData emitted while Home was hidden) and always scrolls Home to the
     * top so Continue Reading is visible. */
    fun onShown() {
        lastContent?.let { render(it) }
        binding.homeScroll.post { binding.homeScroll.scrollTo(0, 0) }
    }

    /** Scrolls the Home content to the top (a Home drawer tap while already
     * on Home). */
    fun scrollToTop() {
        binding.homeScroll.post { binding.homeScroll.scrollTo(0, 0) }
    }

    private fun bindGroups(
        slots: List<Triple<View, TextView, RecyclerView>>,
        groups: List<HomeGroup>,
    ) {
        slots.forEachIndexed { index, (container, title, recycler) ->
            val group = groups.getOrNull(index)
            container.visibility = if (group == null) View.GONE else View.VISIBLE
            if (group != null) {
                title.text =
                    binding.root.context.getString(R.string.home_group_title, group.name, group.count)
                adapters[recycler.id]?.submitList(group.books)
            }
        }
    }

    private fun submitShelf(id: Int, books: List<BookEntity>) {
        adapters[id]?.submitList(books)
    }

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
