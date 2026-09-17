package com.bookwormbliss.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bookwormbliss.app.core.theme.ThemeController
import com.bookwormbliss.app.data.AppDatabase
import com.bookwormbliss.app.data.BookEntity
import com.bookwormbliss.app.data.HighlightEntity
import com.bookwormbliss.app.data.BookmarkEntity
import com.bookwormbliss.app.data.ReadingSessionEntity
import com.bookwormbliss.app.databinding.ActivityReadingNookBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reading Nook — a per-book workspace for journaling, reading stamps,
 * highlights management, and reading status tracking.
 *
 * Translated from the web app's ReadingNookView.tsx. The visual styling
 * reflects the selected app theme (Original or Pastel) via ThemeController.
 *
 * Tabs:
 * - Overview: Book hero, status selector, progress bar, reading stats
 * - Journal: Add/edit/delete journal entries (title + content)
 * - Stamps: Reading milestones (started, resumed, favorite moment, etc.)
 * - Highlights: Saved text highlights with color management
 */
class ReadingNookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadingNookBinding
    private var book: BookEntity? = null
    private var highlights: List<HighlightEntity> = emptyList()
    private var bookmarks: List<BookmarkEntity> = emptyList()
    private var sessions: List<ReadingSessionEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeController.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityReadingNookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId < 0) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.reading_nook_workspace)
        }

        loadBook(bookId)
    }

    private fun loadBook(bookId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext)
            val loadedBook = db.bookDao().getById(bookId)
            highlights = db.highlightDao().getForBook(bookId)
            bookmarks = db.bookmarkDao().getForBook(bookId)
            sessions = db.readingSessionDao().getForBook(bookId)

            withContext(Dispatchers.Main) {
                book = loadedBook
                if (loadedBook != null) {
                    displayOverview(loadedBook)
                } else {
                    finish()
                }
            }
        }
    }

    private fun displayOverview(b: BookEntity) {
        binding.bookTitle.text = b.title
        binding.bookAuthor.text = "by ${b.author}"
        if (!b.series.isNullOrEmpty()) {
            val seriesText = b.series + (if (b.seriesIndex != null && b.seriesIndex > 0) " #${b.seriesIndex.toInt()}" else "")
            binding.bookSeries.text = seriesText
            binding.bookSeries.visibility = android.view.View.VISIBLE
        } else {
            binding.bookSeries.visibility = android.view.View.GONE
        }

        val percent = (b.progress * 100).toInt()
        binding.progressPercent.text = "$percent%"
        binding.progressBar.progress = percent
        if (!b.currentLocation.isNullOrEmpty()) {
            binding.currentLocation.text = getString(R.string.reading_nook_current_location, b.currentLocation)
            binding.currentLocation.visibility = android.view.View.VISIBLE
        } else {
            binding.currentLocation.visibility = android.view.View.GONE
        }

        // Status buttons
        binding.btnUnread.setOnClickListener { updateStatus(b, "unread") }
        binding.btnReading.setOnClickListener { updateStatus(b, "reading") }
        binding.btnFinished.setOnClickListener { updateStatus(b, "finished") }

        // Resume Reading button
        binding.btnResume.setOnClickListener {
            startActivity(
                Intent(this, ReaderActivity::class.java)
                    .putExtra(ReaderActivity.EXTRA_BOOK_ID, b.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }

        // Tab selection
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> { binding.overviewContent.visibility = android.view.View.VISIBLE; binding.journalContent.visibility = android.view.View.GONE; binding.stampsContent.visibility = android.view.View.GONE; binding.highlightsContent.visibility = android.view.View.GONE }
                    1 -> { binding.overviewContent.visibility = android.view.View.GONE; binding.journalContent.visibility = android.view.View.VISIBLE; binding.stampsContent.visibility = android.view.View.GONE; binding.highlightsContent.visibility = android.view.View.GONE }
                    2 -> { binding.overviewContent.visibility = android.view.View.GONE; binding.journalContent.visibility = android.view.View.GONE; binding.stampsContent.visibility = android.view.View.VISIBLE; binding.highlightsContent.visibility = android.view.View.GONE }
                    3 -> { binding.overviewContent.visibility = android.view.View.GONE; binding.journalContent.visibility = android.view.View.GONE; binding.stampsContent.visibility = android.view.View.GONE; binding.highlightsContent.visibility = android.view.View.VISIBLE }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Display highlights count
        if (highlights.isEmpty()) {
            binding.highlightsEmpty.text = getString(R.string.reading_nook_no_highlights)
            binding.highlightsEmpty.visibility = android.view.View.VISIBLE
        }
    }

    private fun updateStatus(b: BookEntity, status: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(applicationContext)
            val updated = when (status) {
                "unread" -> b.copy(progress = 0f, isCurrentlyReading = false)
                "reading" -> b.copy(isCurrentlyReading = true)
                "finished" -> b.copy(progress = 1f, isCurrentlyReading = false)
                else -> b
            }
            db.bookDao().update(updated)
            withContext(Dispatchers.Main) {
                book = updated
                displayOverview(updated)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }
}
