package com.epubreader.app

import com.epubreader.app.util.SystemBarController

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.epubreader.app.data.BookEntity
import com.epubreader.app.data.BookRepository
import com.epubreader.app.databinding.ActivitySearchBinding
import com.epubreader.app.ui.BookAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var repo: BookRepository
    private val adapter =
        BookAdapter(grid = false, onClick = { openBook(it) }, onLongClick = { false }, onDetails = { openDetails(it) })

    private val query = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        repo = BookRepository(applicationContext)
        // Phase 10: apply the selected app theme before inflating any views.
        com.epubreader.app.util.AppThemeController.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)
        setSupportActionBar(binding.searchToolbar)
        binding.searchToolbar.setNavigationOnClickListener {
            dismissSearchKeyboard()
            finish()
        }

        binding.searchRecycler.layoutManager = LinearLayoutManager(this)
        binding.searchRecycler.adapter = adapter

        binding.searchEdit.requestFocus()
        binding.searchEdit.setOnEditorActionListener { _, _, _ ->
            // Phase 10: a submitted (non-blank) query is persisted to search
            // history so it can be surfaced as a recent suggestion later.
            val q = query.value
            if (q.isNotBlank()) {
                lifecycleScope.launch { repo.recordSearch(q) }
            }
            dismissSearchKeyboard()
            false
        }
        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query.value = s?.toString()?.trim() ?: ""
            }
        })

        lifecycleScope.launch {
            query
                .debounce(180)
                .distinctUntilChanged()
                .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.search(q) }
                .collectLatest { results -> showResults(results) }
        }

        // Phase 10: surface recent searches as tappable suggestions whenever the
        // query box is empty.
        lifecycleScope.launch {
            query.collectLatest { q ->
                if (q.isBlank()) showRecentSearches() else hideRecentSearches()
            }
        }
    }

    private fun showResults(books: List<BookEntity>) {
        adapter.submitList(books)
        // When the query is blank, the recent-searches flow owns the empty
        // state — don't fight it here.
        if (query.value.isBlank()) return
        if (books.isEmpty()) {
            binding.searchEmpty.visibility = View.VISIBLE
            binding.searchEmptyText.text = getString(R.string.search_no_matches)
        } else {
            binding.searchEmpty.visibility = View.GONE
        }
    }

    // ---- Phase 10: recent search suggestions ----

    private var recentContainer: android.widget.LinearLayout? = null

    private fun showRecentSearches() {
        lifecycleScope.launch {
            val recent = repo.getRecentSearches(10)
            if (query.value.isNotBlank()) return@launch
            val container = recentContainer ?: android.widget.LinearLayout(this@SearchActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
                binding.searchEmpty.addView(this)
                recentContainer = this
            }
            container.visibility = View.VISIBLE
            container.removeAllViews()
            if (recent.isEmpty()) {
                // No recent searches: fall back to the standard empty hint so the
                // screen is never just blank.
                binding.searchEmptyText.text = getString(R.string.search_hint)
                binding.searchEmpty.visibility = View.VISIBLE
                return@launch
            }
            binding.searchEmpty.visibility = View.VISIBLE
            binding.searchEmptyText.text = getString(R.string.search_recent)
            recent.forEach { entry ->
                val chip = com.google.android.material.chip.Chip(this@SearchActivity).apply {
                    text = entry.query
                    isClickable = true
                    setOnClickListener {
                        binding.searchEdit.setText(entry.query)
                        binding.searchEdit.setSelection(entry.query.length)
                    }
                }
                container.addView(chip)
            }
        }
    }

    private fun hideRecentSearches() {
        recentContainer?.visibility = View.GONE
    }

    private fun dismissSearchKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEdit.windowToken, 0)
        binding.searchEdit.clearFocus()
    }

    private fun openBook(book: BookEntity) {
        dismissSearchKeyboard()
        lifecycleScope.launch { repo.markOpened(book.id) }
        startActivity(Intent(this, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id))
    }

    private fun openDetails(book: BookEntity) {
        startActivity(
            Intent(this, BookDetailsActivity::class.java).putExtra(
                BookDetailsActivity.EXTRA_BOOK_ID,
                book.id
            )
        )
    }
}
