package com.example.devbrowser

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

data class BrowserChromeTab(
    val id: Int,
    val title: String,
    val active: Boolean
)

class BrowserChromeView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onBack()
        fun onForward()
        fun onReload()
        fun onHome()
        fun onNavigate(text: String)
        fun onNewTab()
        fun onSelectTab(id: Int)
        fun onCloseTab(id: Int)
        fun onMenu()
    }

    var listener: Listener? = null

    private val backgroundColor = Color.rgb(32, 33, 36)
    private val toolbarColor = Color.rgb(41, 42, 45)
    private val tabActiveColor = Color.rgb(65, 67, 72)
    private val textColor = Color.rgb(241, 243, 244)
    private val mutedColor = Color.rgb(189, 193, 198)
    private val accentColor = Color.rgb(138, 180, 248)

    private val addressBar: EditText
    private val tabsContainer: LinearLayout
    private val progressTrack: TextView

    private var currentProgress = 0
    private var isLoading = false

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(
        color: Int,
        radius: Int
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)

        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(4), dp(5), dp(4))
            setBackgroundColor(backgroundColor)
        }

        toolbar.addView(navButton("‹", "Go back") {
            listener?.onBack()
        })

        toolbar.addView(navButton("›", "Go forward") {
            listener?.onForward()
        })

        toolbar.addView(navButton("↻", "Reload") {
            listener?.onReload()
        })

        toolbar.addView(navButton("⌂", "Home") {
            listener?.onHome()
        })

        addressBar = EditText(context).apply {
            hint = "Search or enter address"
            textSize = 14f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(textColor)
            setHintTextColor(mutedColor)
            setPadding(dp(13), 0, dp(10), 0)
            background = shape(toolbarColor, 24)
            selectAllOnFocus = true

            setOnEditorActionListener { _, actionId, _ ->
                if (
                    actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE
                ) {
                    listener?.onNavigate(text.toString())
                    true
                } else {
                    false
                }
            }
        }

        toolbar.addView(
            addressBar,
            LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(5), 0, dp(5), 0)
            }
        )

        toolbar.addView(navButton("⋮", "Browser menu") {
            listener?.onMenu()
        })

        addView(
            toolbar,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(3), dp(5), dp(3))
            setBackgroundColor(backgroundColor)
        }

        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    tabsContainer = this
                }
            )
        }

        tabRow.addView(
            tabScroll,
            LayoutParams(0, dp(39), 1f)
        )

        tabRow.addView(
            navButton("+", "New tab") {
                listener?.onNewTab()
            }
        )

        addView(
            tabRow,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(43)
            )
        )

        progressTrack = TextView(context).apply {
            setBackgroundColor(accentColor)
            visibility = GONE
        }

        addView(
            progressTrack,
            LayoutParams(0, dp(2))
        )
    }

    private fun navButton(
        symbol: String,
        description: String,
        action: () -> Unit
    ) = TextView(context).apply {
        text = symbol
        contentDescription = description
        gravity = Gravity.CENTER
        textSize = 20f
        setTextColor(textColor)
        background = shape(toolbarColor, 12)
        isClickable = true
        isFocusable = true

        setOnClickListener {
            animate()
                .alpha(0.65f)
                .setDuration(55)
                .withEndAction {
                    alpha = 1f
                    action()
                }
                .start()
        }

        layoutParams = LayoutParams(
            dp(35),
            dp(37)
        ).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
    }

    fun setAddress(value: String?) {
        val safeValue = value.orEmpty()

        if (addressBar.text.toString() != safeValue) {
            addressBar.setText(safeValue)
        }
    }

    fun getAddress(): String =
        addressBar.text.toString()

    fun focusAddress() {
        addressBar.requestFocus()
        addressBar.selectAll()
    }

    fun setLoading(progress: Int) {
        currentProgress = progress.coerceIn(0, 100)
        isLoading = currentProgress < 100

        progressTrack.visibility =
            if (isLoading) VISIBLE else GONE

        progressTrack.layoutParams = progressTrack.layoutParams.apply {
            width = (resources.displayMetrics.widthPixels *
                currentProgress / 100)
        }

        progressTrack.requestLayout()
    }

    fun renderTabs(items: List<BrowserChromeTab>) {
        tabsContainer.removeAllViews()

        items.forEach { item ->
            val tab = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(5), 0)

                background = shape(
                    if (item.active) tabActiveColor else backgroundColor,
                    12
                )

                isClickable = true
                isFocusable = true
                contentDescription = item.title
            }

            val title = TextView(context).apply {
                text = item.title
                    .ifBlank { "New tab" }
                    .take(19)

                textSize = 12f
                maxWidth = dp(145)
                setTextColor(
                    if (item.active) textColor else mutedColor
                )
                typeface = if (item.active) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                }

                setOnClickListener {
                    listener?.onSelectTab(item.id)
                }
            }

            val close = TextView(context).apply {
                text = "×"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(mutedColor)
                setPadding(dp(9), 0, dp(5), 0)
                contentDescription = "Close tab"

                setOnClickListener {
                    listener?.onCloseTab(item.id)
                }
            }

            tab.addView(title)
            tab.addView(close)

            tab.setOnClickListener {
                listener?.onSelectTab(item.id)
            }

            tabsContainer.addView(
                tab,
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    dp(34)
                ).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            )
        }
    }
}
