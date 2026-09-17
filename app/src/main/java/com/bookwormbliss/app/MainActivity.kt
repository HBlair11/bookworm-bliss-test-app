package com.bookwormbliss.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.*
import com.bookwormbliss.app.core.epub.EpubParser
import com.bookwormbliss.app.core.library.Book
import com.bookwormbliss.app.core.library.BookStore
import com.bookwormbliss.app.core.library.EpubLibraryImporter
import com.bookwormbliss.app.core.reader.ReaderSettings
import com.bookwormbliss.app.core.reader.ReaderWebRenderer
import com.bookwormbliss.app.core.location.ReaderPosition
import com.bookwormbliss.app.core.system.SystemBars
import com.bookwormbliss.app.ui.common.Ui
import java.io.File
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var store: BookStore
    private lateinit var importer: EpubLibraryImporter
    private lateinit var root: FrameLayout
    private lateinit var prefs: SharedPreferences
    private var screen = "home"
    private var selectedBook: Book? = null
    private var drawer: PopupWindow? = null
    private var renderer: ReaderWebRenderer? = null
    private var readerLabel: TextView? = null
    private var readerSettings = ReaderSettings()
    private val scrollPositions = mutableMapOf<String, Int>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressOverlay: View? = null
    private var snackbar: View? = null
    private var lastAddedIds: List<String> = emptyList()
    private val importCode = 4101
    private val folderCode = 4102

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = BookStore(this)
        importer = EpubLibraryImporter(this)
        prefs = getSharedPreferences("bookworm_app", MODE_PRIVATE)
        root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.app_surface_soft)) }
        setContentView(root)
        SystemBars.install(this, root, getColor(R.color.app_background))
        show("home")
    }

    override fun onDestroy() {
        importer.shutdown()
        super.onDestroy()
    }

    private fun show(name: String) {
        saveScrollPosition()
        root.setBackgroundColor(getColor(R.color.app_surface_soft))
        screen = name
        root.removeAllViews()
        snackbar = null
        if (name == "reader" && selectedBook != null) { showReader(selectedBook!!); return }

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.app_background))
        }
        shell.addView(header(name), LinearLayout.LayoutParams(-1, Ui.dp(this, 58)))
        val body = FrameLayout(this)
        shell.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        SystemBars.updateBackground(this, getColor(R.color.app_background))

        when (name) {
            "home" -> home(body)
            "library", "reading", "finished", "unread", "favorites" -> books(body, name)
            "authors", "series" -> authorsSeries(body, name)
            "stats" -> stats(body)
            "folders" -> folders(body)
            "settings" -> settings(body)
            "vocabulary" -> placeholder(body, "Vocabulary Builder", "Build your personal vocabulary from words you encounter while reading.")
            "recent" -> recentAdded(body)
        }
        progressOverlay?.let { spinner ->
            if (spinner.parent == null) root.addView(spinner, spinner.layoutParams ?: FrameLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32), Gravity.TOP or Gravity.END))
            spinner.bringToFront()
        }
        restoreScrollPosition()
    }

    private fun titleFor(s: String) = when (s) {
        "home" -> "The Bookworm Bliss"
        "library" -> "Library"
        "reading" -> "Currently Reading"
        "finished" -> "Finished Books"
        "unread" -> "Unread Books"
        "favorites" -> "Favorites"
        "authors" -> "Authors"
        "series" -> "Series"
        "folders" -> "Folders & Import"
        "stats" -> "Reading Stats"
        "vocabulary" -> "Vocabulary Builder"
        "settings" -> "Settings"
        "recent" -> "Recently Added"
        else -> "The Bookworm Bliss"
    }

    private fun header(name: String): View {
        val bar = Ui.row(this).apply {
            setPadding(Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 8), 0)
            setBackgroundColor(getColor(R.color.app_surface_soft))
            elevation = Ui.dp(this@MainActivity, 1).toFloat()
        }
        val menu = iconButton("☰", "Open navigation") { openDrawer() }
        bar.addView(menu, LinearLayout.LayoutParams(Ui.dp(this, 48), -1))
        val title = Ui.text(this, titleFor(name), 17f, true).apply { typeface = Typeface.create("serif", Typeface.BOLD) }
        bar.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
        if (name != "recent") {
            bar.addView(iconButton("⌕", "Search library") { showSearch() }, LinearLayout.LayoutParams(Ui.dp(this, 44), -1))
        }
        if (name in listOf("library", "reading", "unread", "finished", "favorites")) {
            bar.addView(iconButton("▦", "Grid view") { setGridPreference(true); show(name) }, LinearLayout.LayoutParams(Ui.dp(this, 44), -1))
            bar.addView(iconButton("☷", "List view") { setGridPreference(false); show(name) }, LinearLayout.LayoutParams(Ui.dp(this, 44), -1))
            bar.addView(iconButton("⇅", "Sort books") { showSortMenu(itAnchor = bar) }, LinearLayout.LayoutParams(Ui.dp(this, 44), -1))
        }
        bar.addView(iconButton("＋", "Add EPUB") { startImport() }, LinearLayout.LayoutParams(Ui.dp(this, 48), -1))
        return bar
    }

    private fun iconButton(symbol: String, description: String, action: (View) -> Unit): TextView = TextView(this).apply {
        text = symbol; textSize = 22f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.app_text_primary))
        contentDescription = description; setOnClickListener { action(this) }
        setBackgroundColor(Color.TRANSPARENT); isClickable = true; isFocusable = true
    }

    private fun openDrawer() {
        if (drawer?.isShowing == true) return
        val width = minOf(Ui.dp(this, 340), (resources.displayMetrics.widthPixels * .82f).toInt())
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 22), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(getColor(R.color.app_surface_soft), getColor(R.color.app_light_accent)))
        }
        val brand = Ui.row(this)
        brand.addView(Ui.text(this, "BB", 15f, true).apply { gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = roundBg(getColor(R.color.app_primary), 10f) }, LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)))
        brand.addView(Ui.text(this, "The Bookworm Bliss", 18f, true).apply { setPadding(Ui.dp(this, 10), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(brand)
        head.addView(Ui.text(this, "Offline EPUB Bookshelf & Reader", 11f).apply { setTextColor(getColor(R.color.app_text_secondary)); setPadding(Ui.dp(this, 48), 0, 0, 0) })
        panel.addView(head)

        val items = listOf(
            "home" to "✦  Home", "library" to "▤  All Books", "reading" to "▣  Currently Reading",
            "unread" to "▱  Unread Books", "finished" to "✓  Finished Books", "favorites" to "♥  Favorites",
            "authors" to "♙  Authors", "series" to "▥  Series", "folders" to "▰  Folders & Import",
            "stats" to "▥  Reading Stats", "vocabulary" to "A  Vocabulary Builder", "settings" to "⚙  Settings"
        )
        val scroll = ScrollView(this)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 10)) }
        items.forEach { (id, label) ->
            val active = id == screen
            val row = TextView(this).apply {
                text = label; textSize = 14f; gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(this@MainActivity, 14), 0, Ui.dp(this@MainActivity, 10), 0)
                setTextColor(getColor(if (active) R.color.app_primary else R.color.app_text_primary))
                background = roundBg(getColor(if (active) R.color.app_surface_soft else android.R.color.transparent), 12f)
                setOnClickListener { drawer?.dismiss(); show(id) }
            }
            nav.addView(row, LinearLayout.LayoutParams(-1, Ui.dp(this, 48)).apply { bottomMargin = Ui.dp(this@MainActivity, 3) })
        }
        scroll.addView(nav); panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val foot = Ui.text(this, "v1  •  100% Offline  •  Private", 10f).apply { setTextColor(getColor(R.color.app_text_secondary)); setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 14)) }
        panel.addView(foot)

        val popup = PopupWindow(panel, width, -1, true).apply { elevation = Ui.dp(this@MainActivity, 18).toFloat(); setBackgroundDrawable(roundBg(Color.WHITE, 0f)); isOutsideTouchable = true }
        drawer = popup
        popup.showAtLocation(root, Gravity.START or Gravity.TOP, 0, 0)
    }

    private fun home(body: ViewGroup) {
        val books = store.all()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 22), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 28)) }
        if (books.isEmpty()) {
            emptyState(col, "Your library is waiting for its first story.", "Import EPUB files or select a folder to begin building your bookshelf.")
        } else {
            sectionTitle(col, "Continue Reading", "reading")
            val reading = books.filter { it.isCurrentlyReading && it.progress < .98f }.sortedByDescending { it.lastOpened }.take(1)
            if (reading.isNotEmpty()) renderBooks(col, reading, true) else renderBooks(col, books.take(4), true)
            sectionTitle(col, "Recently Added", null)
            renderBooks(col, books.sortedByDescending { it.addedDate }.take(6), getGridPreference())
            sectionTitle(col, "Favorites", "favorites")
            val favorites = books.filter { it.isFavorite }.take(4)
            if (favorites.isNotEmpty()) renderBooks(col, favorites, true) else emptyMini(col, "No favorites yet.")
        }
        body.addView(scroll(col))
    }

    private fun books(body: ViewGroup, kind: String) {
        val all = store.all()
        val list = when (kind) {
            "reading" -> all.filter { it.isCurrentlyReading && it.progress < .98f }
            "finished" -> all.filter { it.progress >= .98f }
            "unread" -> all.filter { it.progress == 0f && !it.isCurrentlyReading }
            "favorites" -> all.filter { it.isFavorite }
            else -> all
        }.let { sortBooks(it) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 26)) }
        sectionTitle(col, when(kind) { "reading"->"Currently Reading"; "finished"->"Finished Books"; "unread"->"Unread Books"; "favorites"->"Favorites"; else->"Library" }, null, "${list.size} ${if(list.size==1) "book" else "books"}")
        if (list.isEmpty()) emptyState(col, emptyMessage(kind), if (kind == "library") "Add books from your device or select a folder to scan." else emptyMessage(kind)) else renderBooks(col, list, getGridPreference())
        body.addView(scroll(col))
    }

    private fun emptyMessage(kind: String) = when(kind) {
        "reading" -> "No books are currently in progress. Start reading any book from your library!"
        "unread" -> "No unread books found in your library."
        "finished" -> "No finished books yet. As you complete reading your books, they will appear here."
        "favorites" -> "No favorite books yet. Tap the heart icon on any book to add it here."
        else -> "Your library is empty. Import EPUB files or select a folder to begin."
    }

    private fun emptyState(col: LinearLayout, title: String, message: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 60), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 60)) }
        val icon = Ui.text(this, "▤", 36f, true).apply { gravity = Gravity.CENTER; setTextColor(getColor(R.color.app_primary)); background = roundBg(getColor(R.color.app_surface_soft), 40f) }
        box.addView(icon, LinearLayout.LayoutParams(Ui.dp(this, 78), Ui.dp(this, 78)))
        box.addView(Ui.text(this, "No Books Found", 17f, true).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(this@MainActivity, 16), 0, Ui.dp(this@MainActivity, 4)) })
        box.addView(Ui.text(this, "$title\n\n$message", 12f).apply { gravity = Gravity.CENTER; setTextColor(getColor(R.color.app_text_secondary)); setPadding(Ui.dp(this@MainActivity, 10), 0, Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 18)) })
        box.addView(primaryButton("Import EPUB Files") { startImport() }, LinearLayout.LayoutParams(-1, Ui.dp(this, 46)).apply { leftMargin = Ui.dp(this@MainActivity, 22); rightMargin = Ui.dp(this@MainActivity, 22) })
        box.addView(outlineButton("Select Folder") { chooseFolder() }, LinearLayout.LayoutParams(-1, Ui.dp(this, 46)).apply { leftMargin = Ui.dp(this@MainActivity, 22); rightMargin = Ui.dp(this@MainActivity, 22); topMargin = Ui.dp(this@MainActivity, 8) })
        col.addView(box)
    }

    private fun emptyMini(col: LinearLayout, text: String) = col.addView(Ui.text(this, text, 12f).apply { setTextColor(getColor(R.color.app_text_secondary)); setPadding(0, Ui.dp(this@MainActivity, 10), 0, Ui.dp(this@MainActivity, 10)) })

    private fun sectionTitle(col: LinearLayout, title: String, destination: String?, count: String? = null) {
        val row = Ui.row(this).apply { setPadding(0, Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 8)) }
        row.addView(Ui.text(this, title, 15f, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (count != null) row.addView(Ui.text(this, count, 10f).apply { setTextColor(getColor(R.color.app_text_secondary)) })
        if (destination != null) row.addView(Ui.text(this, "View All  ›", 11f, true).apply { setTextColor(getColor(R.color.app_primary)); setOnClickListener { show(destination) } })
        col.addView(row)
    }

    private fun renderBooks(col: LinearLayout, list: List<Book>, grid: Boolean) {
        if (grid) {
            val gridBox = GridLayout(this).apply { columnCount = if (resources.displayMetrics.widthPixels > Ui.dp(this@MainActivity, 600)) 4 else 2; useDefaultMargins = false }
            list.forEach { b ->
                val card = bookCard(b, true)
                val lp = GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(Ui.dp(this@MainActivity, 4), Ui.dp(this@MainActivity, 4), Ui.dp(this@MainActivity, 4), Ui.dp(this@MainActivity, 8)) }
                gridBox.addView(card, lp)
            }
            col.addView(gridBox)
        } else list.forEach { b -> col.addView(bookCard(b, false), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@MainActivity, 10) }) }
    }

    private fun bookCard(b: Book, grid: Boolean): View {
        val card = Ui.card(this).apply { setOnClickListener { openBook(b) }; setOnLongClickListener { bookActions(b); true } }
        if (grid) {
            card.addView(coverView(b, 128, 192), LinearLayout.LayoutParams(-1, Ui.dp(this, 192)))
            card.addView(Ui.text(this, b.title, 13f, true).apply { maxLines = 2 }, LinearLayout.LayoutParams(-1, Ui.dp(this, 42)))
            card.addView(Ui.text(this, b.author, 10f).apply { setTextColor(getColor(R.color.app_text_secondary)); maxLines = 1 })
            card.addView(progressBar(b), LinearLayout.LayoutParams(-1, Ui.dp(this, 5)).apply { topMargin = Ui.dp(this@MainActivity, 8) })
        } else {
            val row = Ui.row(this)
            row.addView(coverView(b, 62, 92), LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 92)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 12), 0, 0, 0) }
            info.addView(Ui.text(this, b.title, 15f, true).apply { maxLines = 2 })
            info.addView(Ui.text(this, b.author + (b.year?.let { "  •  $it" } ?: ""), 11f).apply { setTextColor(getColor(R.color.app_text_secondary)); maxLines = 1 })
            info.addView(progressBar(b), LinearLayout.LayoutParams(-1, Ui.dp(this, 5)).apply { topMargin = Ui.dp(this@MainActivity, 10) })
            info.addView(Ui.text(this, if (b.progress >= .98f) "Finished" else "${(b.progress * 100).toInt()}%", 10f).apply { setTextColor(getColor(R.color.app_text_secondary)); setPadding(0, Ui.dp(this@MainActivity, 3), 0, 0) })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(row)
        }
        return card
    }

    private fun coverView(b: Book, w: Int, h: Int): ImageView = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(getColor(R.color.app_surface_soft))
        if (b.coverPath != null) setImageURI(Uri.fromFile(File(b.coverPath!!))) else setImageResource(android.R.drawable.ic_menu_gallery)
    }

    private fun progressBar(b: Book): ProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = (b.progress * 100).toInt() }

    private fun authorsSeries(body: ViewGroup, kind: String) {
        val map = linkedMapOf<String, MutableList<Book>>()
        store.all().forEach { b -> val key = if (kind == "authors") b.author.trim() else "${b.publisher ?: "Unspecified Series"}"; if (key.isNotBlank()) map.getOrPut(key) { mutableListOf() }.add(b) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 24)) }
        sectionTitle(col, if(kind=="authors") "Authors" else "Series", null, "${map.size} ${if(kind=="authors") "authors" else "collections"}")
        if (map.isEmpty()) emptyMini(col, "No ${if(kind=="authors") "authors" else "series"} found yet.")
        map.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (name, books) ->
            val row = Ui.row(this).apply { background = roundBg(Color.WHITE, 16f); setPadding(Ui.dp(this@MainActivity, 14), 0, Ui.dp(this@MainActivity, 12), 0) }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(Ui.text(this, name, 14f, true)); info.addView(Ui.text(this, "${books.size} ${if(books.size==1)"book" else "books"}", 10f).apply { setTextColor(getColor(R.color.app_text_secondary)) })
            row.addView(info, LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f)); row.addView(Ui.text(this, "${books.size}  ›", 12f, true).apply { setTextColor(getColor(R.color.app_primary)) })
            col.addView(row, LinearLayout.LayoutParams(-1, Ui.dp(this, 64)).apply { bottomMargin = Ui.dp(this@MainActivity, 8) })
        }
        body.addView(scroll(col))
    }

    private fun folders(body: ViewGroup) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 24)) }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; background = roundBg(Color.WHITE, 24f); setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 24)) }
        hero.addView(Ui.text(this, "Folders & Import", 19f, true)); hero.addView(Ui.text(this, "Select a folder once, then scan it anytime for new EPUB books. Scanning is private and runs in the background.", 11f).apply { gravity = Gravity.CENTER; setTextColor(getColor(R.color.app_text_secondary)); setPadding(0, Ui.dp(this@MainActivity, 6), 0, Ui.dp(this@MainActivity, 18)) })
        hero.addView(primaryButton("Select Folder") { chooseFolder() }, LinearLayout.LayoutParams(-1, Ui.dp(this, 46)))
        hero.addView(outlineButton("Scan Selected Folder") { scanSelectedFolder() }, LinearLayout.LayoutParams(-1, Ui.dp(this, 46)).apply { topMargin = Ui.dp(this@MainActivity, 8) })
        hero.addView(outlineButton("Import EPUB Files") { startImport() }, LinearLayout.LayoutParams(-1, Ui.dp(this, 46)).apply { topMargin = Ui.dp(this@MainActivity, 8) })
        val saved = prefs.getString("folder_uri", null)
        hero.addView(Ui.text(this, if (saved == null) "No folder selected yet." else "Folder selected. Scan again whenever new books are added.", 10f).apply { gravity = Gravity.CENTER; setTextColor(getColor(R.color.app_text_secondary)); setPadding(0, Ui.dp(this@MainActivity, 14), 0, 0) })
        col.addView(hero)
        sectionTitle(col, "Stored EPUBs", null, "${store.all().size} books")
        if (store.all().isEmpty()) emptyMini(col, "No stored EPUBs yet.") else renderBooks(col, store.all().sortedByDescending { it.addedDate }, getGridPreference())
        body.addView(scroll(col))
    }

    private fun recentAdded(body: ViewGroup) {
        val ids = lastAddedIds.toSet(); val list = store.all().filter { it.id in ids }.sortedByDescending { it.addedDate }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 24)) }
        val back = outlineButton("‹  Back") { show("library") }; col.addView(back, LinearLayout.LayoutParams(-1, Ui.dp(this, 42)).apply { bottomMargin = Ui.dp(this@MainActivity, 14) })
        sectionTitle(col, "Recently Added", null, "${list.size} books")
        renderBooks(col, list, getGridPreference())
        body.addView(scroll(col))
    }

    private fun stats(body: ViewGroup) {
        val books = store.all(); val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 24)) }
        sectionTitle(col, "Reading Stats", null)
        listOf("Books in library" to books.size.toString(), "Currently reading" to books.count { it.isCurrentlyReading && it.progress < .98f }.toString(), "Finished books" to books.count { it.progress >= .98f }.toString()).forEach { (a,b) -> val card = Ui.card(this); card.addView(Ui.text(this,a,11f).apply{setTextColor(getColor(R.color.app_text_secondary))}); card.addView(Ui.text(this,b,26f,true)); col.addView(card,LinearLayout.LayoutParams(-1,Ui.dp(this,80)).apply{bottomMargin=Ui.dp(this@MainActivity,8)}) }
        col.addView(Ui.text(this, "Reading statistics visual foundation is ready for future session analytics.", 11f).apply { setTextColor(getColor(R.color.app_text_secondary)); setPadding(0, Ui.dp(this@MainActivity, 10), 0, 0) })
        body.addView(scroll(col))
    }

    private fun settings(body: ViewGroup) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 24)) }
        sectionTitle(col, "Settings", null)
        col.addView(Ui.text(this, "Appearance", 15f, true)); col.addView(Ui.text(this, "Reader preferences are applied from the reader settings panel. Application colors and spacing use centralized resources.", 11f).apply { setTextColor(getColor(R.color.app_text_secondary)); setPadding(0, Ui.dp(this@MainActivity, 5), 0, Ui.dp(this@MainActivity, 14)) })
        col.addView(outlineButton("Choose Default Reader Theme") { readerSettingsDialog(null) })
        col.addView(Ui.text(this, "Library", 15f, true).apply { setPadding(0, Ui.dp(this@MainActivity, 24), 0, Ui.dp(this@MainActivity, 8)) })
        col.addView(outlineButton("Select EPUB Folder") { chooseFolder() })
        body.addView(scroll(col))
    }

    private fun placeholder(body: ViewGroup, title: String, message: String) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(Ui.dp(this@MainActivity, 30), Ui.dp(this@MainActivity, 30), Ui.dp(this@MainActivity, 30), Ui.dp(this@MainActivity, 30)) }
        col.addView(Ui.text(this, title, 21f, true).apply { gravity = Gravity.CENTER }); col.addView(Ui.text(this, message, 12f).apply { gravity = Gravity.CENTER; setTextColor(getColor(R.color.app_text_secondary)); setPadding(0, Ui.dp(this@MainActivity, 8), 0, 0) }); body.addView(col)
    }

    private fun bookActions(b: Book) {
        val labels = arrayOf("Open", "Book Details", "Reading Nook", if (b.isFavorite) "Remove Favorite" else "Add Favorite", "Remove Book")
        AlertDialog.Builder(this).setTitle(b.title).setItems(labels) { _, which -> when(which) { 0 -> openBook(b); 1 -> details(b); 2 -> nook(b); 3 -> { b.isFavorite=!b.isFavorite; store.save(b); show(screen) }; 4 -> { store.delete(b.id); show(screen) } } }.show()
    }

    private fun details(b: Book) {
        AlertDialog.Builder(this).setTitle("Book Details").setMessage("${b.title}\n\nAuthor: ${b.author}\nPublisher: ${b.publisher ?: "Unknown"}\nLanguage: ${b.language ?: "Unknown"}\nYear: ${b.year ?: "Unknown"}\nIdentifier: ${b.identifier ?: "Unknown"}\n\n${b.description ?: "No description."}").setPositiveButton("Edit Metadata") { _, _ -> editMetadata(b) }.setNeutralButton("Reading Nook") { _, _ -> nook(b) }.setNegativeButton("Close", null).show()
    }

    private fun editMetadata(b: Book) {
        val fields = LinkedHashMap<String, EditText>(); val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity,20),0,Ui.dp(this@MainActivity,20),0) }
        listOf("Title" to b.title, "Author" to b.author, "Publisher" to (b.publisher ?: ""), "Language" to (b.language ?: ""), "Year" to (b.year?.toString() ?: ""), "Description" to (b.description ?: "")).forEach { (label,value) -> box.addView(Ui.text(this,label,10f,true)); val e=EditText(this).apply{setText(value);textSize=14f}; fields[label]=e; box.addView(e) }
        AlertDialog.Builder(this).setTitle("Edit Metadata").setView(box).setPositiveButton("Save") { _, _ -> b.title=fields["Title"]!!.text.toString().ifBlank{"Untitled"}; b.author=fields["Author"]!!.text.toString().ifBlank{"Unknown Author"}; b.publisher=fields["Publisher"]!!.text.toString().ifBlank{null}; b.language=fields["Language"]!!.text.toString().ifBlank{null}; b.year=fields["Year"]!!.text.toString().toIntOrNull(); b.description=fields["Description"]!!.text.toString().ifBlank{null}; store.save(b); show(screen) }.setNegativeButton("Cancel",null).show()
    }

    private fun nook(b: Book) { AlertDialog.Builder(this).setTitle("Reading Nook").setMessage("A dedicated workspace for reading history, journal entries, stamps and highlights.\n\nThe reader core is kept separate so the full Nook can be expanded later without changing EPUB rendering contracts.").setPositiveButton("Resume Reading") { _, _ -> openBook(b) }.setNegativeButton("Close",null).show() }

    private fun startImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/epub+zip"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true) }
        startActivityForResult(intent, importCode)
    }

    private fun chooseFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) }, folderCode)
    }

    private fun scanSelectedFolder() {
        val raw = prefs.getString("folder_uri", null)
        if (raw == null) { chooseFolder(); return }
        beginBackgroundWork()
        val started = System.currentTimeMillis()
        importer.scanTree(Uri.parse(raw)) { outcome -> finishBackgroundWork(started, outcome, "Scan complete") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == importCode) {
            val uris = mutableListOf<Uri>(); data.data?.let { uris += it }; data.clipData?.let { clip -> for(i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri }
            if (uris.isNotEmpty()) { beginBackgroundWork(); val started=System.currentTimeMillis(); importer.importUris(uris) { outcome -> finishBackgroundWork(started,outcome,"Import complete") } }
        } else if (requestCode == folderCode) {
            val uri = data.data ?: return
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
            prefs.edit().putString("folder_uri", uri.toString()).apply()
            beginBackgroundWork(); val started=System.currentTimeMillis(); importer.scanTree(uri) { outcome -> finishBackgroundWork(started,outcome,"Folder scan complete") }
        }
    }

    private fun beginBackgroundWork() {
        val spinner = ProgressBar(this)
        spinner.isClickable = false
        spinner.isFocusable = false
        spinner.contentDescription = "Library import in progress"
        root.addView(spinner, FrameLayout.LayoutParams(Ui.dp(this,32), Ui.dp(this,32), Gravity.TOP or Gravity.END).apply {
            topMargin = Ui.dp(this@MainActivity, 12)
            rightMargin = Ui.dp(this@MainActivity, 12)
        })
        progressOverlay = spinner
    }

    private fun finishBackgroundWork(started: Long, outcome: EpubLibraryImporter.Outcome, label: String) {
        val delay = (2200L - (System.currentTimeMillis() - started)).coerceAtLeast(0L)
        mainHandler.postDelayed({
            progressOverlay?.let { root.removeView(it) }; progressOverlay=null
            lastAddedIds=outcome.added.map{it.id}
            if (outcome.added.isNotEmpty()) show(screen)
            val text = when { outcome.added.isNotEmpty() -> "${outcome.added.size} new ${if(outcome.added.size==1)"book" else "books"} added"; outcome.duplicates>0 -> "No new books added • ${outcome.duplicates} duplicate${if(outcome.duplicates==1)"" else "s"}"; else -> "No new books added" }
            showSnackbar(text, outcome.added.isNotEmpty())
            if (outcome.failures.isNotEmpty()) mainHandler.postDelayed({ AlertDialog.Builder(this).setTitle("${label}: some files skipped").setMessage(outcome.failures.joinToString("\n\n")).setPositiveButton("OK",null).show() }, 500)
        }, delay)
    }

    private fun showSnackbar(message: String, showButton: Boolean) {
        snackbar?.let { root.removeView(it) }
        val bar=Ui.row(this).apply{setPadding(Ui.dp(this@MainActivity,14),0,Ui.dp(this@MainActivity,8),0);background=roundBg(getColor(R.color.app_text_primary),18f);elevation=Ui.dp(this@MainActivity,8).toFloat()}
        bar.addView(Ui.text(this,message,11f,true).apply{setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,Ui.dp(this,52),1f))
        if(showButton) bar.addView(TextView(this).apply{text="Show";textSize=11f;gravity=Gravity.CENTER;setTextColor(getColor(R.color.app_surface_soft));setPadding(Ui.dp(this@MainActivity,12),0,Ui.dp(this@MainActivity,12),0);setOnClickListener{show("recent")}},LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,52)))
        root.addView(bar,FrameLayout.LayoutParams(-1,Ui.dp(this,52),Gravity.BOTTOM).apply{leftMargin=Ui.dp(this@MainActivity,12);rightMargin=Ui.dp(this@MainActivity,12);bottomMargin=Ui.dp(this@MainActivity,12)})
        snackbar=bar; mainHandler.postDelayed({snackbar?.let{root.removeView(it)};snackbar=null},5000)
    }

    private fun showReader(b: Book) {
        b.isCurrentlyReading=true; b.lastOpened=System.currentTimeMillis(); store.save(b)
        val outer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.parseColor(themeBackground(readerSettings.theme)))}
        val top=Ui.row(this).apply{setBackgroundColor(Color.parseColor(themeBackground(readerSettings.theme)));setPadding(Ui.dp(this@MainActivity,6),0,Ui.dp(this@MainActivity,6),0)}
        top.addView(iconButton("‹","Back to library"){ saveReaderPosition(b); show("library") },LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,58)))
        top.addView(Ui.text(this,"${b.title}\n${b.author}",12f,true).apply{maxLines=2},LinearLayout.LayoutParams(0,Ui.dp(this,58),1f))
        top.addView(iconButton("☰","Table of contents"){readerToc(b)},LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,58)))
        top.addView(iconButton("Aa","Reader settings"){readerSettingsDialog(b)},LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,58)))
        outer.addView(top)
        val web=WebView(this).apply{setBackgroundColor(Color.parseColor(themeBackground(readerSettings.theme)));overScrollMode=View.OVER_SCROLL_NEVER}
        outer.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        val bottom=Ui.row(this).apply{setBackgroundColor(Color.parseColor(themeBackground(readerSettings.theme)));setPadding(Ui.dp(this@MainActivity,8),0,Ui.dp(this@MainActivity,8),0)}
        bottom.addView(iconButton("‹","Previous page"){renderer?.previousPage();updateReaderLabel(b)},LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,52)))
        readerLabel=Ui.text(this,"Page 1 / 1",11f,true).apply{gravity=Gravity.CENTER}; bottom.addView(readerLabel,LinearLayout.LayoutParams(0,Ui.dp(this,52),1f))
        bottom.addView(iconButton("›","Next page"){renderer?.nextPage();updateReaderLabel(b)},LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,52)))
        outer.addView(bottom)
        root.removeAllViews();root.setBackgroundColor(Color.parseColor(themeBackground(readerSettings.theme)));root.addView(outer,FrameLayout.LayoutParams(-1,-1)); SystemBars.updateBackground(this,Color.parseColor(themeBackground(readerSettings.theme)))
        renderer=ReaderWebRenderer(web)
        val doc=try{val file=File(filesDir,"imports/${b.sourceFilename}");EpubParser(this).parseFile(file).document}catch(_:Exception){null}
        if(doc==null){AlertDialog.Builder(this).setTitle("Unable to open book").setMessage("The stored EPUB could not be opened. It may have been removed or damaged.").setPositiveButton("Back"){_,_->show("library")}.show();return}
        renderer!!.applySettings(readerSettings); renderer!!.load(doc)
        web.setOnTouchListener(object:View.OnTouchListener{var downX=0f;override fun onTouch(v:View,e:MotionEvent):Boolean{when(e.action){MotionEvent.ACTION_DOWN->downX=e.x;MotionEvent.ACTION_UP->{val dx=e.x-downX;if(abs(dx)>80){if(dx<0)renderer?.nextPage() else renderer?.previousPage();updateReaderLabel(b);return true}}};return false}})
        web.postDelayed({renderer?.restore(ReaderPosition(b.spineIndex,b.pageIndex,0));updateReaderLabel(b)},700)
    }

    private fun saveReaderPosition(b: Book) { renderer?.currentPosition()?.let { p -> b.spineIndex=p.spineIndex;b.pageIndex=p.pageIndex;b.pageCount=renderer?.pageCount()?:1;b.progress=if((b.spineIndex+1)>0) ((p.spineIndex.toFloat() + (p.pageIndex.toFloat()/maxOf(1,renderer?.pageCount()?:1)))/maxOf(1,renderer?.chapterCount()?:1)).coerceIn(0f,.999f) else 0f;store.save(b) } }
    private fun updateReaderLabel(b:Book){saveReaderPosition(b);readerLabel?.text="Page ${renderer?.currentPage()?:1} / ${renderer?.pageCount()?:1}"}

    private fun readerToc(b:Book){val rr=renderer?:return;val toc=rr.toc();if(toc.isEmpty()){Toast.makeText(this,"No table of contents found",Toast.LENGTH_SHORT).show();return};val labels=toc.map{it.label}.toTypedArray();AlertDialog.Builder(this).setTitle("Table of Contents").setItems(labels){_,which->rr.loadChapter(toc[which].spineIndex);b.spineIndex=toc[which].spineIndex;b.pageIndex=0;store.save(b);updateReaderLabel(b)}.setNegativeButton("Close",null).show()}

    private fun readerSettingsDialog(b:Book?){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(Ui.dp(this@MainActivity,18),Ui.dp(this@MainActivity,6),Ui.dp(this@MainActivity,18),Ui.dp(this@MainActivity,6))};val themes=arrayOf("alabaster","ivory","nordic_eco","candlelight","onyx","midnight_slate");val ts=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,themes);setSelection(themes.indexOf(readerSettings.theme).coerceAtLeast(0))};val fs=SeekBar(this).apply{max=36;progress=(readerSettings.fontSize-20).coerceIn(0,36)};val mode=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("horizontal","vertical"));setSelection(if(readerSettings.readingMode=="vertical")1 else 0)};box.addView(Ui.text(this,"Theme",11f,true));box.addView(ts);box.addView(Ui.text(this,"Font size",11f,true));box.addView(fs);box.addView(Ui.text(this,"Reading mode",11f,true));box.addView(mode);AlertDialog.Builder(this).setTitle("Reader Settings").setView(box).setPositiveButton("Apply"){_,_->readerSettings=readerSettings.copy(theme=ts.selectedItem.toString(),fontSize=20+fs.progress,readingMode=mode.selectedItem.toString());if(b!=null){saveReaderPosition(b);showReader(b)} }.setNegativeButton("Cancel",null).show()}

    private fun showSortMenu(itAnchor: View) {
        val popup=PopupWindow(this); val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,8));background=roundBg(Color.WHITE,16f);elevation=Ui.dp(this@MainActivity,12).toFloat()}
        box.addView(Ui.text(this,"Sort Books By",11f,true).apply{setTextColor(getColor(R.color.app_text_secondary));setPadding(Ui.dp(this@MainActivity,10),Ui.dp(this@MainActivity,6),0,Ui.dp(this@MainActivity,8))})
        listOf("Recently Read" to "recently_read","Recently Added" to "recently_added","Title" to "title","Author" to "author","Progress" to "progress").forEach{(label,key)->val row=TextView(this).apply{text=label;textSize=12f;gravity=Gravity.CENTER_VERTICAL;setTextColor(getColor(R.color.app_text_primary));setPadding(Ui.dp(this@MainActivity,10),0,Ui.dp(this@MainActivity,10),0);setOnClickListener{prefs.edit().putString("sort",key).apply();popup.dismiss();show(screen)}};box.addView(row,LinearLayout.LayoutParams(Ui.dp(this,190),Ui.dp(this,42)))}
        popup.contentView=box;popup.width=Ui.dp(this,210);popup.height=LinearLayout.LayoutParams.WRAP_CONTENT;popup.isFocusable=true;popup.setBackgroundDrawable(roundBg(Color.WHITE,16f));popup.showAsDropDown(itAnchor, -Ui.dp(this,165), Ui.dp(this,4))
    }

    private fun sortBooks(list:List<Book>):List<Book>{return when(prefs.getString("sort","recently_added")){"recently_read"->list.sortedByDescending{it.lastOpened};"title"->list.sortedBy{it.title.lowercase()};"author"->list.sortedBy{it.author.lowercase()};"progress"->list.sortedByDescending{it.progress};else->list.sortedByDescending{it.addedDate}}}
    private fun setGridPreference(grid:Boolean){prefs.edit().putBoolean("grid",grid).apply()}; private fun getGridPreference()=prefs.getBoolean("grid",true)
    private fun showSearch(){val input=EditText(this).apply{hint="Search title or author"};AlertDialog.Builder(this).setTitle("Search Library").setView(input).setPositiveButton("Search"){_,_->searchResults(input.text.toString())}.setNegativeButton("Cancel",null).show()}
    private fun searchResults(query:String){val q=query.trim();if(q.isEmpty())return;val matches=store.all().filter{it.title.contains(q,true)||it.author.contains(q,true)};val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(Ui.dp(this@MainActivity,16),Ui.dp(this@MainActivity,18),Ui.dp(this@MainActivity,16),Ui.dp(this@MainActivity,24))};sectionTitle(col,"Search Results",null,"${matches.size} found");renderBooks(col,matches,getGridPreference());root.removeAllViews();val shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};shell.addView(header("search"),LinearLayout.LayoutParams(-1,Ui.dp(this,58)));shell.addView(scroll(col),LinearLayout.LayoutParams(-1,0,1f));root.addView(shell,FrameLayout.LayoutParams(-1,-1));SystemBars.updateBackground(this,getColor(R.color.app_background))}

    private fun openBook(b:Book){selectedBook=b;saveScrollPosition();show("reader")}
    private fun scroll(v:View):ScrollView=ScrollView(this).apply{addView(v);setBackgroundColor(getColor(R.color.app_background));isFillViewport=true}
    private fun saveScrollPosition(){
        fun findScroll(view: View): ScrollView? {
            if (view is ScrollView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) findScroll(view.getChildAt(i))?.let { return it }
            return null
        }
        findScroll(root)?.let { scrollPositions[screen] = it.scrollY }
    }
    private fun restoreScrollPosition(){root.post{val shell=root.getChildAt(0);if(shell is ViewGroup){val body=shell.getChildAt(1);if(body is ScrollView)body.scrollTo(0,scrollPositions[screen]?:0)}}}
    private fun primaryButton(text:String,onClick:()->Unit)=Ui.button(this,text,onClick)
    private fun outlineButton(text:String,onClick:()->Unit)=Button(this).apply{text=text;textSize=12f;setTextColor(getColor(R.color.app_text_primary));background=roundBg(Color.WHITE,14f);setOnClickListener{onClick()};minHeight=Ui.dp(this@MainActivity,44)}
    private fun roundBg(color:Int,radius:Float)=GradientDrawable().apply{setColor(color);cornerRadius=Ui.dp(this@MainActivity,radius.toInt()).toFloat();if(color==Color.WHITE)setStroke(Ui.dp(this@MainActivity,1),getColor(R.color.app_divider))}
    private fun themeBackground(theme:String)=when(theme){"ivory"->"#FFFFFF";"nordic_eco"->"#E8EFE9";"candlelight"->"#E8D3A7";"onyx"->"#000000";"midnight_slate"->"#1A1B1E";else->"#F1E3D3"}
}
