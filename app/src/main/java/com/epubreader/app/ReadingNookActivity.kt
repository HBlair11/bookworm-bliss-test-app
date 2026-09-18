package com.epubreader.app

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookJournalEntryEntity
import com.epubreader.app.data.BookRepository
import com.epubreader.app.data.HighlightEntity
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.data.ReadingStampEntity
import com.epubreader.app.databinding.ActivityReadingNookBinding
import com.epubreader.app.util.AppThemeController
import com.epubreader.app.util.KeepScreenOnController
import com.epubreader.app.util.SystemBarController
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ReadingNookActivity — Phase 10.
 *
 * Translated from the web app's ReadingNookView.tsx. A dedicated "reading
 * workspace" for a single book: status selector (Unread/Reading/Finished),
 * progress display, and four tabs — Overview, Journal, Stamps, Highlights.
 *
 * Per the Phase 10 spec, Recent Reading Activity is NOT included here.
 *
 * The activity uses the theme-aware AppThemeController + SystemBarController so
 * it re-skins correctly under Original or Pastel. Data access goes through
 * BookRepository; the stamp/journal/highlight DAOs built in the earlier
 * Phase 10 slice provide the backing store.
 */
class ReadingNookActivity : AppCompatActivity() {
    // Phase 10: theme-aware text color resolution
    private fun textPrimary(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        return if (tv.resourceId != 0) resources.getColor(tv.resourceId, theme) else tv.data
    }
    private fun textSecondary(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
        return if (tv.resourceId != 0) resources.getColor(tv.resourceId, theme) else tv.data
    }


    private lateinit var binding: ActivityReadingNookBinding
    private lateinit var repo: BookRepository
    private var bookId: Long = -1L
    private var book: BookEntity? = null

    private lateinit var keepScreenOnController: KeepScreenOnController

    private val stamps = mutableListOf<ReadingStampEntity>()
    private val journals = mutableListOf<BookJournalEntryEntity>()
    private val highlights = mutableListOf<HighlightEntity>()

    /** Maps stamp type string → display label resource. */
    private val stampLabels = mapOf(
        ReadingStampEntity.Type.STARTED to R.string.stamp_started,
        ReadingStampEntity.Type.RESUMED to R.string.stamp_resumed,
        ReadingStampEntity.Type.FINISHED to R.string.stamp_finished,
        ReadingStampEntity.Type.REVISITED to R.string.stamp_revisited,
        ReadingStampEntity.Type.FAVORITE_MOMENT to R.string.stamp_favorite_moment,
        ReadingStampEntity.Type.MEMORABLE to R.string.stamp_memorable,
        ReadingStampEntity.Type.REREAD to R.string.stamp_reread,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeController.apply(this)
        super.onCreate(savedInstanceState)

        keepScreenOnController = KeepScreenOnController(
            this,
            PrefsManager(applicationContext),
        )

        binding = ActivityReadingNookBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)

        repo = BookRepository(applicationContext)

        setSupportActionBar(binding.nookToolbar)
        binding.nookToolbar.setNavigationOnClickListener { finish() }

        bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId < 0) {
            finish()
            return
        }

        setupTabs()
        loadData()
    }

    private fun setupTabs() {
        binding.nookTabs.apply {
            addTab(newTab().setText(R.string.nook_tab_overview))
            addTab(newTab().setText(R.string.nook_tab_journal))
            addTab(newTab().setText(R.string.nook_tab_stamps))
            addTab(newTab().setText(R.string.nook_tab_highlights))
        }
        binding.nookTabs.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                showTab(tab.position)
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val loaded = repo.getBook(bookId) ?: run {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }
            book = loaded
            stamps.clear()
            stamps.addAll(repo.getStamps(bookId))
            journals.clear()
            journals.addAll(repo.getJournal(bookId))
            highlights.clear()
            highlights.addAll(repo.observeHighlights(bookId).first())

            withContext(Dispatchers.Main) {
                renderHero()
                showTab(0)
            }
        }
    }

    private fun renderHero() {
        val b = book ?: return
        val ctx = binding.root.context

        binding.nookTitle.text = b.title
        binding.nookAuthor.text = b.author.ifBlank { ctx.getString(R.string.unknown_author) }

        val seriesText = b.series?.trim()?.takeIf { it.isNotEmpty() }?.let { series ->
            val index = b.seriesIndex
            if (index != null) {
                val formatted = if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
                "$series #$formatted"
            } else {
                series
            }
        }
        binding.nookSeries.text = seriesText
        binding.nookSeries.visibility = if (seriesText.isNullOrBlank()) View.GONE else View.VISIBLE

        // Cover
        if (b.coverPath != null) {
            Glide.with(this).load(File(b.coverPath)).centerCrop()
                .placeholder(R.drawable.cover_frame).into(binding.nookCover)
        } else {
            binding.nookCover.setImageResource(R.drawable.cover_frame)
        }

        // Progress
        val percent = (b.progress * 100).toInt()
        binding.nookProgressPercent.text =
            if (b.progress >= 0.98f) ctx.getString(R.string.progress_completed)
            else "${percent}%"
        binding.nookProgressBar.progress = percent

        val location = b.currentLocation?.trim().orEmpty()
        binding.nookCurrentLocation.text =
            if (location.isBlank()) "" else ctx.getString(R.string.home_continue_section, location)
        binding.nookCurrentLocation.visibility =
            if (location.isBlank()) View.GONE else View.VISIBLE

        // Status selector
        val isUnread = b.progress == 0f && !b.isCurrentlyReading
        val isReading = b.isCurrentlyReading && b.progress < 0.98f
        val isFinished = b.progress >= 0.98f

        binding.nookStatusGroup.check(
            when {
                isFinished -> R.id.nookStatusFinished
                isReading -> R.id.nookStatusReading
                else -> R.id.nookStatusUnread
            }
        )

        binding.nookStatusUnread.setOnClickListener {
            if (!isUnread) setStatus("unread")
        }
        binding.nookStatusReading.setOnClickListener {
            if (!isReading) setStatus("reading")
        }
        binding.nookStatusFinished.setOnClickListener {
            if (!isFinished) setStatus("finished")
        }

        // Analytics counts
        binding.nookStampsCount.text = ctx.getString(R.string.nook_stamps_count, stamps.size)
        binding.nookJournalsCount.text = ctx.getString(R.string.nook_journals_count, journals.size)
        binding.nookHighlightsCount.text = ctx.getString(R.string.nook_highlights_count, highlights.size)
    }

    private fun setStatus(status: String) {
        val b = book ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            when (status) {
                "unread" -> {
                    repo.clearCurrentlyReading(b.id)
                    repo.updateProgress(b.id, 0f, 0, 0f)
                    book = b.copy(progress = 0f, isCurrentlyReading = false)
                }
                "reading" -> {
                    repo.setCurrentlyReading(b.id)
                    val newProgress = if (b.progress >= 0.98f) 0.5f else b.progress
                    repo.updateProgress(b.id, newProgress, b.spineIndex, b.scrollRatio)
                    book = b.copy(isCurrentlyReading = true, progress = newProgress)
                }
                "finished" -> {
                    repo.clearCurrentlyReading(b.id)
                    repo.updateProgress(b.id, 1f, b.spineCount - 1, 1f)
                    book = b.copy(progress = 1f, isCurrentlyReading = false)
                }
            }
            withContext(Dispatchers.Main) {
                renderHero()
                Snackbar.make(binding.root, R.string.nook_status_set, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTab(position: Int) {
        val container = binding.nookTabContent
        container.removeAllViews()
        when (position) {
            0 -> showOverview(container)
            1 -> showJournal(container)
            2 -> showStamps(container)
            3 -> showHighlights(container)
        }
    }

    // ---- Overview tab ----
    private fun showOverview(container: ViewGroup) {
        val b = book ?: return
        val ctx = container.context
        val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        val view = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        fun addRow(label: String, value: String?) {
            if (value.isNullOrBlank()) return
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
            }
            row.addView(TextView(ctx).apply {
                text = label
                setTextColor(textSecondary())
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(ctx).apply {
                text = value
                setTextColor(textPrimary())
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            })
            view.addView(row)
        }

        addRow("Author", b.author)
        addRow("Series", b.series)
        addRow("Publisher", b.publisher)
        addRow("Published", b.publishYear?.toString())
        addRow("Language", b.language?.uppercase())
        addRow("File size", Formatter.formatFileSize(ctx, b.fileSize))
        addRow("Added", df.format(Date(b.addedDate)))
        b.lastOpenedDate?.let { addRow("Last opened", df.format(Date(it))) }

        if (b.description.isNullOrBlank()) {
            view.addView(TextView(ctx).apply {
                text = "No synopsis available."
                setTextColor(textSecondary())
                textSize = 12f
                setPadding(0, 12, 0, 0)
            })
        } else {
            view.addView(TextView(ctx).apply {
                text = "Synopsis"
                setTextColor(textSecondary())
                textSize = 12f
                setPadding(0, 12, 0, 4)
            })
            view.addView(TextView(ctx).apply {
                text = b.description
                setTextColor(textPrimary())
                textSize = 12f
                setPadding(0, 0, 0, 0)
            })
        }

        container.addView(view)
    }

    // ---- Journal tab ----
    private fun showJournal(container: ViewGroup) {
        val ctx = container.context
        val view = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Add button
        val addBtn = com.google.android.material.button.MaterialButton(ctx).apply {
            text = ctx.getString(R.string.nook_add_journal)
            setOnClickListener { showJournalDialog(null) }
        }
        view.addView(addBtn)

        if (journals.isEmpty()) {
            view.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.nook_no_journals)
                setTextColor(textSecondary())
                textSize = 12f
                setPadding(0, 24, 0, 0)
                gravity = android.view.Gravity.CENTER
            })
        } else {
            val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            journals.sortedByDescending { it.createdAt }.forEach { entry ->
                val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                    cardElevation = 1f
                    useCompatPadding = true
                    radius = 12f
                    setCardBackgroundColor(ctx.getColor(android.R.color.white))
                }
                val inner = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 12, 16, 12)
                }
                inner.addView(TextView(ctx).apply {
                    text = entry.title
                    setTextColor(textPrimary())
                    textSize = 14f
                    setPadding(0, 0, 0, 2)
                })
                inner.addView(TextView(ctx).apply {
                    text = df.format(Date(entry.createdAt))
                    setTextColor(textSecondary())
                    textSize = 10f
                    setPadding(0, 0, 0, 6)
                })
                if (entry.content.isNotBlank()) {
                    inner.addView(TextView(ctx).apply {
                        text = entry.content
                        setTextColor(textSecondary())
                        textSize = 12f
                        maxLines = 4
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
                val btnRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 0)
                }
                btnRow.addView(com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = ctx.getString(R.string.nook_edit)
                    setOnClickListener { showJournalDialog(entry) }
                })
                btnRow.addView(com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = ctx.getString(R.string.nook_delete)
                    setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            repo.deleteJournalEntry(entry)
                            journals.remove(entry)
                            withContext(Dispatchers.Main) { showJournal(container) }
                        }
                    }
                })
                inner.addView(btnRow)
                card.addView(inner)
                view.addView(card)
            }
        }

        container.addView(view)
    }

    private fun showJournalDialog(existing: BookJournalEntryEntity?) {
        val ctx = binding.root.context
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_journal_entry, null)
        val titleField = dialogView.findViewById<android.widget.EditText>(R.id.journalTitleField)
        val contentField = dialogView.findViewById<android.widget.EditText>(R.id.journalContentField)

        existing?.let {
            titleField.setText(it.title)
            contentField.setText(it.content)
        }

        AlertDialog.Builder(ctx)
            .setTitle(if (existing != null) R.string.nook_edit else R.string.nook_add_journal)
            .setView(dialogView)
            .setPositiveButton(R.string.nook_save) { _, _ ->
                val title = titleField.text.toString().trim()
                    .ifBlank { ctx.getString(R.string.nook_journal_default_title) }
                val content = contentField.text.toString().trim()
                if (title.isBlank() && content.isBlank()) return@setPositiveButton

                lifecycleScope.launch(Dispatchers.IO) {
                    if (existing != null) {
                        repo.updateJournalEntry(
                            existing.copy(title = title, content = content, updatedAt = System.currentTimeMillis())
                        )
                    } else {
                        val id = repo.addJournalEntry(
                            BookJournalEntryEntity(bookId = bookId, title = title, content = content)
                        )
                        journals.add(
                            BookJournalEntryEntity(
                                id = id, bookId = bookId, title = title, content = content
                            )
                        )
                    }
                    // Reload journals
                    journals.clear()
                    journals.addAll(repo.getJournal(bookId))
                    withContext(Dispatchers.Main) {
                        binding.nookJournalsCount.text =
                            ctx.getString(R.string.nook_journals_count, journals.size)
                        showTab(1)
                    }
                }
            }
            .setNegativeButton(R.string.nook_cancel, null)
            .show()
    }

    // ---- Stamps tab ----
    private fun showStamps(container: ViewGroup) {
        val ctx = container.context
        val view = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Add button
        val addBtn = com.google.android.material.button.MaterialButton(ctx).apply {
            text = ctx.getString(R.string.nook_add_stamp)
            setOnClickListener { showStampDialog() }
        }
        view.addView(addBtn)

        if (stamps.isEmpty()) {
            view.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.nook_no_stamps)
                setTextColor(textSecondary())
                textSize = 12f
                setPadding(0, 24, 0, 0)
                gravity = android.view.Gravity.CENTER
            })
        } else {
            val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            stamps.sortedByDescending { it.timestamp }.forEach { stamp ->
                val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                    cardElevation = 1f
                    useCompatPadding = true
                    radius = 12f
                    setCardBackgroundColor(ctx.getColor(android.R.color.white))
                }
                val inner = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 12, 16, 12)
                }
                val labelRes = stampLabels[stamp.type] ?: R.string.stamp_memorable
                inner.addView(TextView(ctx).apply {
                    text = ctx.getString(labelRes)
                    setTextColor(textPrimary())
                    textSize = 14f
                })
                inner.addView(TextView(ctx).apply {
                    text = df.format(Date(stamp.timestamp))
                    setTextColor(textSecondary())
                    textSize = 10f
                    setPadding(0, 0, 0, 4)
                })
                stamp.note?.takeIf { it.isNotBlank() }?.let { note ->
                    inner.addView(TextView(ctx).apply {
                        text = note
                        setTextColor(textSecondary())
                        textSize = 12f
                    })
                }
                inner.addView(com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = ctx.getString(R.string.nook_delete)
                    setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            repo.deleteStamp(stamp)
                            stamps.remove(stamp)
                            withContext(Dispatchers.Main) { showStamps(container) }
                        }
                    }
                })
                card.addView(inner)
                view.addView(card)
            }
        }

        container.addView(view)
    }

    private fun showStampDialog() {
        val ctx = binding.root.context
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_stamp_entry, null)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.stampTypeSpinner)
        val noteField = dialogView.findViewById<android.widget.EditText>(R.id.stampNoteField)

        val types = ReadingStampEntity.Type.ALL
        val labels = types.map { ctx.getString(stampLabels[it] ?: R.string.stamp_memorable) }
        typeSpinner.adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, labels)

        AlertDialog.Builder(ctx)
            .setTitle(R.string.nook_add_stamp)
            .setView(dialogView)
            .setPositiveButton(R.string.nook_save) { _, _ ->
                val type = types[typeSpinner.selectedItemPosition]
                val note = noteField.text.toString().trim().ifBlank { null }
                val label = ctx.getString(stampLabels[type] ?: R.string.stamp_memorable)

                lifecycleScope.launch(Dispatchers.IO) {
                    val id = repo.addStamp(
                        ReadingStampEntity(
                            bookId = bookId, type = type, title = label,
                            note = note, timestamp = System.currentTimeMillis()
                        )
                    )
                    stamps.add(
                        ReadingStampEntity(
                            id = id, bookId = bookId, type = type, title = label,
                            note = note, timestamp = System.currentTimeMillis()
                        )
                    )
                    withContext(Dispatchers.Main) {
                        binding.nookStampsCount.text =
                            ctx.getString(R.string.nook_stamps_count, stamps.size)
                        showTab(2)
                    }
                }
            }
            .setNegativeButton(R.string.nook_cancel, null)
            .show()
    }

    // ---- Highlights tab ----
    private fun showHighlights(container: ViewGroup) {
        val ctx = container.context
        val view = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (highlights.isEmpty()) {
            view.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.nook_no_highlights)
                setTextColor(textSecondary())
                textSize = 12f
                setPadding(0, 24, 0, 0)
                gravity = android.view.Gravity.CENTER
            })
        } else {
            val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            highlights.sortedByDescending { it.createdAt }.forEach { hl ->
                val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                    cardElevation = 1f
                    useCompatPadding = true
                    radius = 12f
                    setCardBackgroundColor(ctx.getColor(android.R.color.white))
                }
                val inner = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 12, 16, 12)
                }
                inner.addView(TextView(ctx).apply {
                    text = "\"${hl.text}\""
                    setTextColor(textPrimary())
                    textSize = 13f
                    setPadding(0, 0, 0, 4)
                })
                inner.addView(TextView(ctx).apply {
                    text = df.format(Date(hl.createdAt))
                    setTextColor(textSecondary())
                    textSize = 10f
                })
                hl.note?.takeIf { it.isNotBlank() }?.let { note ->
                    inner.addView(TextView(ctx).apply {
                        text = note
                        setTextColor(textSecondary())
                        textSize = 12f
                        setPadding(0, 4, 0, 0)
                    })
                }
                card.addView(inner)
                view.addView(card)
            }
        }

        container.addView(view)
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }
}
