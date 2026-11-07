package com.example.devbrowser

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context.INPUT_METHOD_SERVICE
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class BrowserChromeView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onBack()
        fun onForward()
        fun onReload()
        fun onHome()
        fun onNavigate(url: String)
        fun onNewTab()
        fun onSelectTab(index: Int)
        fun onCloseTab(index: Int)
        fun onMenu()
    }

    data class TabItem(
        val title: String,
        val url: String,
        val active: Boolean = false
    )

    var listener: Listener? = null

    private val toolbar = LinearLayout(context)
    private val tabsContainer = LinearLayout(context)
    private val addressInput = EditText(context)
    private val progress = ProgressBar(
        context,
        null,
        android.R.attr.progressBarStyleHorizontal
    )

    private val tabScroll = HorizontalScrollView(context)

    private val backButton = makeButton("‹", 25)
    private val forwardButton = makeButton("›", 25)
    private val reloadButton = makeButton("↻", 22)
    private val homeButton = makeButton("⌂", 22)
    private val menuButton = makeButton("⋮", 23)
    private val newTabButton = makeButton("+", 24)

    private var currentTabs: List<TabItem> = emptyList()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(248, 249, 250))

        buildToolbar()
        buildTabStrip()
        buildProgressBar()

        backButton.setOnClickListener { listener?.onBack() }
        forwardButton.setOnClickListener { listener?.onForward() }
        reloadButton.setOnClickListener { listener?.onReload() }
        homeButton.setOnClickListener { listener?.onHome() }
        menuButton.setOnClickListener { listener?.onMenu() }
        newTabButton.setOnClickListener { listener?.onNewTab() }

        addressInput.setOnEditorActionListener { _, actionId, _ ->
            if (
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_SEARCH
            ) {
                val entered = addressInput.text.toString().trim()

                if (entered.isNotEmpty()) {
                    listener?.onNavigate(entered)
                    hideKeyboard()
                }

                true
            } else {
                false
            }
        }
    }

    private fun buildToolbar() {
        toolbar.orientation = HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        toolbar.setPadding(dp(5), dp(5), dp(5), dp(5))
        toolbar.setBackgroundColor(Color.rgb(248, 249, 250))

        addView(
            toolbar,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(54)
            )
        )

        addToolButton(backButton)
        addToolButton(forwardButton)
        addToolButton(reloadButton)
        addToolButton(homeButton)

        val addressBackground = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.rgb(218, 220, 224))
        }

        addressInput.apply {
            textSize = 14f
            singleLine = true
            hint = "Search or type URL"
            setTextColor(Color.rgb(32, 33, 36))
            setHintTextColor(Color.rgb(110, 115, 120))
            setPadding(dp(13), 0, dp(10), 0)
            background = addressBackground
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }

        toolbar.addView(
            addressInput,
            LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
        )

        addToolButton(menuButton)
    }

    private fun buildTabStrip() {
        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(3), dp(7), dp(3))
            setBackgroundColor(Color.rgb(241, 243, 244))
        }

        tabScroll.apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                tabsContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(39)
                )
            )
        }

        tabRow.addView(
            tabScroll,
            LayoutParams(0, dp(42), 1f)
        )

        tabRow.addView(
            newTabButton,
            LayoutParams(dp(38), dp(36)).apply {
                setMargins(dp(5), 0, 0, 0)
            }
        )

        addView(
            tabRow,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(44)
            )
        )
    }

    private fun buildProgressBar() {
        progress.max = 100
        progress.progress = 0
        progress.visibility = View.GONE
        progress.progressTintList =
            android.content.res.ColorStateList.valueOf(
                Color.rgb(26, 115, 232)
            )

        addView(
            progress,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(2)
            )
        )
    }

    private fun addToolButton(button: TextView) {
        toolbar.addView(
            button,
            LayoutParams(dp(34), dp(40))
        )
    }

    private fun makeButton(symbol: String, size: Int): TextView {
        return TextView(context).apply {
            text = symbol
            textSize = size.toFloat()
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(60, 64, 67))
            typeface = Typeface.DEFAULT
            isClickable = true
            isFocusable = true
            background = selectableBackground()
        }
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable {
        val out = android.util.TypedValue()
        context.theme.resolveAttribute(
            android.R.attr.selectableItemBackground,
            out,
            true
        )
        return if (out.resourceId != 0) {
            context.getDrawable(out.resourceId)
                ?: GradientDrawable()
        } else {
            GradientDrawable()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun hideKeyboard() {
        val manager = context.getSystemService(
            INPUT_METHOD_SERVICE
        ) as? InputMethodManager

        manager?.hideSoftInputFromWindow(
            addressInput.windowToken,
            0
        )

        addressInput.clearFocus()
    }

    fun setAddress(url: String) {
        addressInput.setText(url)
        addressInput.setSelection(addressInput.text.length)
    }

    fun getAddress(): String {
        return addressInput.text.toString()
    }

    fun focusAddress() {
        addressInput.requestFocus()
        addressInput.selectAll()

        val manager = context.getSystemService(
            INPUT_METHOD_SERVICE
        ) as? InputMethodManager

        manager?.showSoftInput(
            addressInput,
            InputMethodManager.SHOW_IMPLICIT
        )
    }

    fun setLoading(isLoading: Boolean, value: Int = 0) {
        if (isLoading) {
            progress.visibility = View.VISIBLE
            progress.progress = value.coerceIn(0, 100)
        } else {
            progress.progress = 100
            progress.postDelayed({
                progress.visibility = View.GONE
                progress.progress = 0
            }, 180)
        }
    }

    fun renderTabs(tabs: List<TabItem>) {
        currentTabs = tabs
        tabsContainer.removeAllViews()

        tabs.forEachIndexed { index, tab ->
            val tabView = createTabView(tab, index)

            tabsContainer.addView(
                tabView,
                LayoutParams(
                    dp(150),
                    dp(34)
                ).apply {
                    setMargins(0, 0, dp(5), 0)
                }
            )
        }
    }

    private fun createTabView(
        tab: TabItem,
        index: Int
    ): View {
        val container = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), 0, dp(5), 0)

            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()

                if (tab.active) {
                    setColor(Color.WHITE)
                } else {
                    setColor(Color.rgb(232, 234, 237))
                }

                setStroke(
                    dp(1),
                    if (tab.active) {
                        Color.rgb(218, 220, 224)
                    } else {
                        Color.TRANSPARENT
                    }
                )
            }

            isClickable = true
            isFocusable = true
            setOnClickListener {
                listener?.onSelectTab(index)
            }
        }

        val title = TextView(context).apply {
            text = tab.title.ifBlank { "New Tab" }
            textSize = 12f
            setTextColor(Color.rgb(60, 64, 67))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }

        container.addView(
            title,
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        )

        val close = TextView(context).apply {
            text = "×"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(95, 99, 104))
            background = selectableBackground()
            isClickable = true
            isFocusable = true

            setOnClickListener {
                listener?.onCloseTab(index)
            }
        }

        container.addView(
            close,
            LayoutParams(dp(25), LayoutParams.MATCH_PARENT)
        )

        return container
    }

    fun setNavigationEnabled(
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        backButton.alpha = if (canGoBack) 1f else 0.4f
        forwardButton.alpha = if (canGoForward) 1f else 0.4f

        backButton.isEnabled = canGoBack
        forwardButton.isEnabled = canGoForward
    }
}
