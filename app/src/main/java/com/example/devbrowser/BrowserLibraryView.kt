package com.example.devbrowser

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Date
import java.util.Locale

class BrowserLibraryView(context: Context) : LinearLayout(context) {

    enum class Section {
        BOOKMARKS,
        HISTORY,
        SAVED_PAGES
    }

    interface Listener {
        fun onBack()
        fun onOpenUrl(url: String)
        fun onDeleteHistory(id: String)
        fun onClearHistory()
        fun onRemoveBookmark(id: String)
        fun onRemoveSavedPage(url: String)
    }

    var listener: Listener? = null

    private var currentSection = Section.BOOKMARKS

    private var bookmarks = emptyList<BrowserBookmark>()
    private var history = emptyList<BrowserHistoryEntry>()
    private var savedPages = emptyList<SavedPageMetadata>()

    private val searchInput = EditText(context)
    private val content = LinearLayout(context)
    private val scrollView = ScrollView(context)

    private val tabs = LinearLayout(context)
    private val countLabel = TextView(context)
    private val clearButton = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(248, 249, 250))

        buildHeader()
        buildSearch()
        buildTabs()
        buildContent()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun buildHeader() {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(16), dp(7))
            setBackgroundColor(Color.WHITE)
        }

        val back = TextView(context).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(60, 64, 67))
            setOnClickListener {
                listener?.onBack()
            }
        }

        header.addView(
            back,
            LayoutParams(dp(42), dp(44))
        )

        val title = TextView(context).apply {
            text = "Your Library"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(32, 33, 36))
        }

        header.addView(
            title,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )

        addView(
            header,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )
    }

    private fun buildSearch() {
        val wrapper = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
        }

        searchInput.apply {
            hint = "Search library"
            textSize = 14f
            singleLine = true
            setTextColor(Color.rgb(32, 33, 36))
            setHintTextColor(Color.rgb(120, 124, 128))
            setPadding(dp(14), 0, dp(14), 0)

            background = rounded(
                Color.rgb(241, 243, 244),
                Color.TRANSPARENT
            )
        }

        searchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    renderCurrentSection()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )

        wrapper.addView(
            searchInput,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )

        addView(
            wrapper,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )
    }

    private fun buildTabs() {
        tabs.orientation = HORIZONTAL
        tabs.gravity = Gravity.CENTER_VERTICAL
        tabs.setPadding(dp(8), dp(4), dp(8), dp(4))
        tabs.setBackgroundColor(Color.WHITE)

        addView(
            tabs,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        renderTabs()
    }

    private fun renderTabs() {
        tabs.removeAllViews()

        addTab("Bookmarks", Section.BOOKMARKS)
        addTab("History", Section.HISTORY)
        addTab("Saved", Section.SAVED_PAGES)
    }

    private fun addTab(
        label: String,
        section: Section
    ) {
        val selected = currentSection == section

        val button = TextView(context).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = if (selected) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }

            setTextColor(
                if (selected) {
                    Color.rgb(26, 115, 232)
                } else {
                    Color.rgb(95, 99, 104)
                }
            )

            background = if (selected) {
                rounded(
                    Color.rgb(232, 240, 254),
                    Color.TRANSPARENT
                )
            } else {
                rounded(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT
                )
            }

            setOnClickListener {
                currentSection = section
                renderTabs()
                renderCurrentSection()
            }
        }

        tabs.addView(
            button,
            LayoutParams(0, dp(38), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
        )
    }

    private fun buildContent() {
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(12), dp(5))
        }

        countLabel.apply {
            textSize = 12f
            setTextColor(Color.rgb(95, 99, 104))
        }

        topRow.addView(
            countLabel,
            LayoutParams(0, dp(34), 1f)
        )

        clearButton.apply {
            text = "Clear history"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(26, 115, 232))
            visibility = View.GONE
            setPadding(dp(8), 0, dp(8), 0)

            setOnClickListener {
                listener?.onClearHistory()
            }
        }

        topRow.addView(
            clearButton,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dp(34)
            )
        )

        addView(
            topRow,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(44)
            )
        )

        content.orientation = VERTICAL
        content.setPadding(
            dp(12),
            dp(5),
            dp(12),
            dp(16)
        )

        scrollView.apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true

            addView(
                content,
                ScrollView.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
            )
        }

        addView(
            scrollView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    fun setData(
        bookmarks: List<BrowserBookmark>,
        history: List<BrowserHistoryEntry>,
        savedPages: List<SavedPageMetadata>
    ) {
        this.bookmarks = bookmarks
        this.history = history
        this.savedPages = savedPages

        renderCurrentSection()
    }

    fun showSection(section: Section) {
        currentSection = section
        searchInput.setText("")
        renderTabs()
        renderCurrentSection()
    }

    private fun renderCurrentSection() {
        content.removeAllViews()

        val query = searchInput.text
            .toString()
            .trim()
            .lowercase(Locale.ROOT)

        when (currentSection) {
            Section.BOOKMARKS -> {
                val filtered = bookmarks.filter {
                    it.title.lowercase(Locale.ROOT).contains(query) ||
                    it.url.lowercase(Locale.ROOT).contains(query) ||
                    it.folder.lowercase(Locale.ROOT).contains(query)
                }

                countLabel.text = "${filtered.size} bookmarks"
                clearButton.visibility = View.GONE

                if (filtered.isEmpty()) {
                    showEmpty("No bookmarks found")
                } else {
                    renderBookmarks(filtered)
                }
            }

            Section.HISTORY -> {
                val filtered = history.filter {
                    it.title.lowercase(Locale.ROOT).contains(query) ||
                    it.url.lowercase(Locale.ROOT).contains(query)
                }

                countLabel.text = "${filtered.size} history entries"
                clearButton.visibility = View.VISIBLE

                if (filtered.isEmpty()) {
                    showEmpty("No browsing history found")
                } else {
                    renderHistory(filtered)
                }
            }

            Section.SAVED_PAGES -> {
                val filtered = savedPages.filter {
                    it.title.lowercase(Locale.ROOT).contains(query) ||
                    it.url.lowercase(Locale.ROOT).contains(query) ||
                    it.description.lowercase(Locale.ROOT).contains(query)
                }

                countLabel.text = "${filtered.size} saved pages"
                clearButton.visibility = View.GONE

                if (filtered.isEmpty()) {
                    showEmpty("No saved pages found")
                } else {
                    renderSavedPages(filtered)
                }
            }
        }
    }

    private fun renderBookmarks(
        items: List<BrowserBookmark>
    ) {
        var lastFolder = ""

        items.forEach { bookmark ->
            if (bookmark.folder != lastFolder) {
                lastFolder = bookmark.folder

                content.addView(
                    sectionHeading(bookmark.folder)
                )
            }

            content.addView(
                createEntry(
                    title = bookmark.title,
                    subtitle = bookmark.url,
                    metadata = "Saved ${formatDate(bookmark.createdAt)}",
                    onOpen = {
                        listener?.onOpenUrl(bookmark.url)
                    },
                    onRemove = {
                        listener?.onRemoveBookmark(bookmark.id)
                    }
                )
            )
        }
    }

    private fun renderHistory(
        items: List<BrowserHistoryEntry>
    ) {
        items.forEach { entry ->
            content.addView(
                createEntry(
                    title = entry.title,
                    subtitle = entry.url,
                    metadata = "${formatDate(entry.visitedAt)} • " +
                        "${entry.visitCount} visits",
                    onOpen = {
                        listener?.onOpenUrl(entry.url)
                    },
                    onRemove = {
                        listener?.onDeleteHistory(entry.id)
                    }
                )
            )
        }
    }

    private fun renderSavedPages(
        items: List<SavedPageMetadata>
    ) {
        items.forEach { page ->
            content.addView(
                createEntry(
                    title = page.title,
                    subtitle = page.description.ifBlank {
                        page.url
                    },
                    metadata = "Saved ${formatDate(page.savedAt)}",
                    onOpen = {
                        listener?.onOpenUrl(page.url)
                    },
                    onRemove = {
                        listener?.onRemoveSavedPage(page.url)
                    }
                )
            )
        }
    }

    private fun sectionHeading(
        label: String
    ): View {
        return TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(95, 99, 104))
            setPadding(dp(5), dp(12), dp(5), dp(7))
        }
    }

    private fun createEntry(
        title: String,
        subtitle: String,
        metadata: String,
        onOpen: () -> Unit,
        onRemove: () -> Unit
    ): View {
        val card = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(7), dp(11))

            background = rounded(
                Color.WHITE,
                Color.rgb(232, 234, 237)
            )
        }

        val details = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, dp(8), 0)
            setOnClickListener {
                onOpen()
            }
        }

        val titleView = TextView(context).apply {
            text = title.ifBlank { "Untitled page" }
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(32, 33, 36))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val subtitleView = TextView(context).apply {
            text = subtitle
            textSize = 11f
            setTextColor(Color.rgb(95, 99, 104))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(0, dp(3), 0, dp(3))
        }

        val metadataView = TextView(context).apply {
            text = metadata
            textSize = 10f
            setTextColor(Color.rgb(120, 124, 128))
        }

        details.addView(titleView)
        details.addView(subtitleView)
        details.addView(metadataView)

        card.addView(
            details,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )

        val openButton = makeSmallButton("Open") {
            onOpen()
        }

        card.addView(
            openButton,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dp(38)
            )
        )

        val removeButton = makeSmallButton("×") {
            onRemove()
        }

        card.addView(
            removeButton,
            LayoutParams(dp(38), dp(38))
        )

        card.setOnClickListener {
            onOpen()
        }

        return card.apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun makeSmallButton(
        label: String,
        action: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(26, 115, 232))
            background = rounded(
                Color.rgb(241, 243, 244),
                Color.TRANSPARENT
            )

            setOnClickListener {
                action()
            }
        }
    }

    private fun showEmpty(message: String) {
        val empty = TextView(context).apply {
            text = message
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(95, 99, 104))
            setPadding(dp(15), dp(50), dp(15), dp(50))
        }

        content.addView(
            empty,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return "Unknown date"

        return DateFormat.format(
            "dd MMM yyyy, hh:mm a",
            Date(timestamp)
        ).toString()
    }

    private fun rounded(
        fill: Int,
        stroke: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(12).toFloat()

            if (stroke != Color.TRANSPARENT) {
                setStroke(dp(1), stroke)
            }
        }
    }
}
