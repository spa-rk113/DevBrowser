package com.example.devbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

class MainActivity : Activity() {

    private data class BrowserTab(
        val id: Int,
        val web: WebView,
        var title: String,
        var url: String
    )

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId = -1
    private var nextTabId = 1

    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var devPanel: LinearLayout
    private lateinit var panelBody: LinearLayout

    private val prefs by lazy {
        getSharedPreferences("devbrowser_session", Context.MODE_PRIVATE)
    }

    private var currentDevTab = "Elements"
    private var panelHeight = 300
    private var inspectEnabled = false

    private var selectedHtml = ""
    private var selectedInlineCss = ""
    private var selectedComputedCss = ""
    private var selectedSelector = ""
    private var boxModelJson = "{}"

    private var domNodes = JSONArray()
    private val consoleHistory = mutableListOf<String>()
    private var consoleOutput: TextView? = null

    private val bg = Color.rgb(15, 18, 25)
    private val surface = Color.rgb(25, 30, 40)
    private val surface2 = Color.rgb(36, 43, 56)
    private val accent = Color.rgb(76, 156, 255)
    private val orange = Color.rgb(255, 174, 82)
    private val textColor = Color.rgb(238, 241, 248)
    private val muted = Color.rgb(157, 169, 188)

    private val activeWeb: WebView?
        get() = tabs.firstOrNull { it.id == activeTabId }?.web

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        panelHeight = prefs.getInt("panel_height", 300)
        currentDevTab = prefs.getString("last_dev_tab", "Elements")
            ?: "Elements"

        buildInterface()

        restoreTabs()

        if (prefs.getBoolean("show_tutorial", true)) {
            root.postDelayed({ showTutorial() }, 800)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        stroke: Int = Color.TRANSPARENT,
        radius: Int = 12
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != Color.TRANSPARENT) {
                setStroke(dp(1), stroke)
            }
        }
    }

    private fun label(
        value: String,
        size: Float = 12f,
        color: Int = textColor
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun button(
        value: String,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(surface2, Color.TRANSPARENT, 10)
            setOnClickListener { action() }
        }
    }

    private fun editor(
        value: String,
        hintText: String,
        lines: Int = 4
    ): EditText {
        return EditText(this).apply {
            setText(value)
            hint = hintText
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            textSize = 12f
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(surface2)
            minLines = lines
            typeface = Typeface.MONOSPACE
            isSingleLine = false
        }
    }

    // ---------------------------------------------------------
    // BROWSER INTERFACE
    // ---------------------------------------------------------

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setBackgroundColor(bg)
        }

        toolbar.addView(button("‹") {
            activeWeb?.let {
                if (it.canGoBack()) it.goBack()
            }
        })

        toolbar.addView(button("›") {
            activeWeb?.let {
                if (it.canGoForward()) it.goForward()
            }
        })

        toolbar.addView(button("↻") {
            activeWeb?.reload()
        })

        toolbar.addView(button("⌂") {
            activeWeb?.loadUrl("https://www.google.com")
        })

        address = EditText(this).apply {
            hint = "Search or enter website"
            textSize = 13f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(textColor)
            setHintTextColor(muted)
            setPadding(dp(12), 0, dp(10), 0)
            background = rounded(surface2, accent, 22)

            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO ||
                    action == EditorInfo.IME_ACTION_DONE) {
                    loadAddress()
                    true
                } else false
            }
        }

        toolbar.addView(
            address,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        toolbar.addView(button("⋮") { showMenu() })

        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(4), dp(5), dp(4))
            setBackgroundColor(surface)
        }

        webContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        status = label("Ready", 10f, muted).apply {
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setBackgroundColor(surface)
        }

        devPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            visibility = View.GONE
        }

        root.addView(toolbar)

        root.addView(
            tabStrip,
            LinearLayout.LayoutParams(-1, dp(43))
        )

        root.addView(
            webContainer,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(status)

        root.addView(
            devPanel,
            LinearLayout.LayoutParams(-1, dp(panelHeight))
        )

        setContentView(root)
    }

    // ---------------------------------------------------------
    // TAB MANAGEMENT
    // ---------------------------------------------------------

    private fun restoreTabs() {
        val saved = prefs.getString("saved_tabs", null)
        var restored = false

        if (saved != null) {
            try {
                val array = JSONArray(saved)

                for (i in 0 until array.length()) {
                    val url = array.optString(i)
                    if (url.isNotBlank()) {
                        createTab(url, false)
                        restored = true
                    }
                }
            } catch (_: Exception) {
                appendConsole("Session restore failed")
            }
        }

        if (!restored) {
            createTab("https://www.google.com", true)
        }

        if (tabs.isNotEmpty()) {
            val index = prefs.getInt("active_tab_index", 0)
                .coerceIn(0, tabs.lastIndex)

            switchTab(tabs[index].id)
        }
    }

    private fun createTab(
        url: String = "https://www.google.com",
        load: Boolean = true
    ) {
        if (tabs.size >= 8) {
            Toast.makeText(
                this,
                "Maximum 8 tabs allowed",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val web = WebView(this)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        web.webViewClient = object : WebViewClient() {

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                if (view === activeWeb) {
                    status.text = "Loading website..."
                }
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                val item = tabs.firstOrNull { it.web === view }
                    ?: return

                item.url = url ?: item.url
                item.title = view?.title ?: item.title

                if (item.id == activeTabId) {
                    address.setText(item.url)
                    status.text = "Page loaded"
                }

                saveSession()
                renderTabs()

                installInspector(view)
            }
        }

        web.webChromeClient = object : WebChromeClient() {

            override fun onReceivedTitle(
                view: WebView?,
                title: String?
            ) {
                val item = tabs.firstOrNull { it.web === view }
                    ?: return

                item.title = title ?: item.title

                if (item.id == activeTabId) {
                    renderTabs()
                }
            }

            override fun onConsoleMessage(
                message: ConsoleMessage
            ): Boolean {
                val line = message.message()

                if (line.startsWith("__DEVBROWSER__")) {
                    try {
                        val json = JSONObject(
                            line.removePrefix("__DEVBROWSER__")
                        )
                        handleInspectorData(json)
                    } catch (_: Exception) {
                        appendConsole("Inspector response could not be read")
                    }
                } else {
                    val level = when (message.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                        ConsoleMessage.MessageLevel.WARNING -> "WARN"
                        else -> "LOG"
                    }

                    appendConsole("[$level] $line")
                }

                return true
            }
        }

        val item = BrowserTab(
            id = nextTabId++,
            web = web,
            title = "New tab",
            url = url
        )

        tabs.add(item)
        activeTabId = item.id

        switchTab(item.id)

        if (load) web.loadUrl(url)

        saveSession()
    }

    private fun switchTab(id: Int) {
        val item = tabs.firstOrNull { it.id == id } ?: return

        activeTabId = id
        webContainer.removeAllViews()

        (item.web.parent as? ViewGroup)?.removeView(item.web)

        webContainer.addView(
            item.web,
            FrameLayout.LayoutParams(-1, -1)
        )

        address.setText(item.web.url ?: item.url)

        renderTabs()
        saveSession()

        if (devPanel.visibility == View.VISIBLE) {
            requestDom()
        }
    }

    private fun closeTab(id: Int) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return

        val item = tabs[index]

        webContainer.removeView(item.web)
        item.web.stopLoading()
        item.web.destroy()
        tabs.removeAt(index)

        if (tabs.isEmpty()) {
            createTab("https://www.google.com")
        } else {
            switchTab(tabs[index.coerceAtMost(tabs.lastIndex)].id)
        }

        saveSession()
    }

    private fun renderTabs() {
        tabStrip.removeAllViews()

        tabs.forEach { item ->
            val selected = item.id == activeTabId

            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(4), dp(5), dp(4))
                background = rounded(
                    if (selected) Color.rgb(45, 65, 94) else surface2,
                    if (selected) accent else Color.TRANSPARENT
                )
            }

            val title = TextView(this).apply {
                text = item.title.take(12).ifBlank { "New tab" }
                textSize = 11f
                setTextColor(if (selected) Color.WHITE else muted)
                maxWidth = dp(105)
                setOnClickListener { switchTab(item.id) }
            }

            val close = TextView(this).apply {
                text = "×"
                textSize = 16f
                setTextColor(muted)
                setPadding(dp(6), 0, dp(4), 0)
                setOnClickListener { closeTab(item.id) }
            }

            tab.addView(title)
            tab.addView(close)

            tabStrip.addView(
                tab,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(34)
                ).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            )
        }

        tabStrip.addView(button("+") {
            createTab()
        })
    }

    private fun saveSession() {
        if (!::root.isInitialized) return

        val array = JSONArray()

        tabs.forEach {
            array.put(it.web.url ?: it.url)
        }

        prefs.edit()
            .putString("saved_tabs", array.toString())
            .putInt(
                "active_tab_index",
                tabs.indexOfFirst { it.id == activeTabId }
                    .coerceAtLeast(0)
            )
            .putString("last_dev_tab", currentDevTab)
            .putInt("panel_height", panelHeight)
            .apply()
    }

    // ---------------------------------------------------------
    // ADDRESS BAR AND MENU
    // ---------------------------------------------------------

    private fun loadAddress() {
        var url = address.text.toString().trim()
        if (url.isBlank()) return

        if (!url.startsWith("https://") &&
            !url.startsWith("http://")) {

            url = if (url.contains(".") && !url.contains(" ")) {
                "https://$url"
            } else {
                "https://www.google.com/search?q=" +
                    java.net.URLEncoder.encode(url, "UTF-8")
            }
        }

        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)

        activeWeb?.loadUrl(url)
    }

    private fun showMenu() {
        val options = arrayOf(
            "New tab",
            "Inspect Element",
            "Open Elements",
            "Open Styles",
            "Open Console",
            "Close DevTools",
            "Reload page",
            "Replay tutorial"
        )

        AlertDialog.Builder(this)
            .setTitle("DevBrowser")
            .setItems(options) { _, index ->
                when (index) {
                    0 -> createTab()
                    1 -> {
                        showDevTools("Elements")
                        toggleInspect(true)
                    }
                    2 -> showDevTools("Elements")
                    3 -> showDevTools("Styles")
                    4 -> showDevTools("Console")
                    5 -> closeDevTools()
                    6 -> activeWeb?.reload()
                    7 -> showTutorial()
                }
            }
            .show()
    }

    // ---------------------------------------------------------
    // DEVTOOLS PANEL
    // ---------------------------------------------------------

    private fun showDevTools(tab: String) {
        currentDevTab = tab
        devPanel.visibility = View.VISIBLE
        renderPanel()
        requestDom()
        saveSession()
    }

    private fun closeDevTools() {
        devPanel.visibility = View.GONE
        toggleInspect(false)
        saveSession()
    }

    private fun buildDragHandle(): View {
        return View(this).apply {
            minimumHeight = dp(13)
            setBackgroundColor(Color.rgb(75, 87, 108))
            contentDescription = "Drag to resize DevTools"

            var startY = 0f
            var startHeight = panelHeight

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        startHeight = panelHeight
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val delta = (
                            (startY - event.rawY) /
                                resources.displayMetrics.density
                        ).toInt()

                        val screenDp = (
                            resources.displayMetrics.heightPixels /
                                resources.displayMetrics.density
                        ).toInt()

                        panelHeight = (startHeight + delta)
                            .coerceIn(180, max(180, screenDp - 200))

                        devPanel.layoutParams =
                            devPanel.layoutParams.apply {
                                height = dp(panelHeight)
                            }

                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        saveSession()
                        true
                    }

                    else -> true
                }
            }
        }
    }

    private fun renderPanel() {
        devPanel.removeAllViews()
        devPanel.addView(buildDragHandle())

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(surface)
        }

        listOf("Elements", "Styles", "Console").forEach { name ->
            bar.addView(TextView(this).apply {
                text = if (name == currentDevTab) "● $name" else name
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (name == currentDevTab) accent else muted)
                setPadding(dp(12), dp(13), dp(12), dp(13))

                setOnClickListener {
                    currentDevTab = name
                    renderPanel()
                    requestDom()
                    saveSession()
                }
            })
        }

        bar.addView(TextView(this).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { closeDevTools() }
        })

        devPanel.addView(bar)

        panelBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(12))
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        scroll.addView(panelBody)

        devPanel.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        when (currentDevTab) {
            "Elements" -> renderElements()
            "Styles" -> renderStyles()
            "Console" -> renderConsole()
        }
    }

    // ---------------------------------------------------------
    // ELEMENTS
    // ---------------------------------------------------------

    private fun renderElements() {
        panelBody.addView(
            label("ELEMENTS  /  DOM INSPECTOR", 11f, accent)
        )

        panelBody.addView(button(
            if (inspectEnabled) "Stop picking" else "⌖  Pick Element"
        ) {
            toggleInspect(!inspectEnabled)
            renderPanel()
        })

        panelBody.addView(
            label(
                if (inspectEnabled) {
                    "Pick mode ON · Tap an element on the webpage"
                } else {
                    "Select a DOM row or use Pick Element"
                },
                10f,
                muted
            )
        )

        val tree = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(surface)
        }

        if (domNodes.length() == 0) {
            tree.addView(label(
                "Waiting for webpage DOM...",
                11f,
                muted
            ))
        } else {
            for (i in 0 until domNodes.length()) {
                val node = domNodes.optJSONObject(i) ?: continue
                val path = node.optJSONArray("path") ?: JSONArray()
                val depth = node.optInt("depth", 0)
                val selected = node.optBoolean("selected", false)

                tree.addView(TextView(this).apply {
                    text = (if (selected) "▶ " else "  ") +
                        node.optString("label", "element")
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(if (selected) orange else textColor)
                    setPadding(
                        dp(5 + depth * 12),
                        dp(7),
                        dp(5),
                        dp(7)
                    )

                    if (selected) {
                        background = rounded(Color.rgb(66, 48, 30))
                    }

                    setOnClickListener {
                        selectDomPath(path)
                    }
                })
            }
        }

        val treeScroll = ScrollView(this)
        treeScroll.addView(tree)

        panelBody.addView(
            treeScroll,
            LinearLayout.LayoutParams(-1, dp(145))
        )

        panelBody.addView(
            label("SELECTED HTML", 11f, accent)
        )

        val html = editor(
            selectedHtml,
            "Select an element to load its HTML.",
            5
        )

        panelBody.addView(
            html,
            LinearLayout.LayoutParams(-1, dp(110))
        )

        panelBody.addView(button("Apply HTML") {
            if (selectedSelector.isBlank()) {
                status.text = "Select an element first"
            } else {
                val quoted = JSONObject.quote(html.text.toString())

                activeWeb?.evaluateJavascript(
                    """
                    (function(){
                        const el = window.__devSelected;
                        if (!el) return;
                        el.innerHTML = $quoted;
                        window.__devSendSelection(el);
                    })();
                    """.trimIndent(),
                    null
                )
            }
        })

        panelBody.addView(button("Refresh DOM") {
            requestDom()
        })
    }

    private fun selectDomPath(path: JSONArray) {
        activeWeb?.evaluateJavascript(
            """
            (function(){
                let el = document.body;
                const path = $path;

                for (const i of path) {
                    if (!el || !el.children[i]) return;
                    el = el.children[i];
                }

                if (el) {
                    window.__devHighlight(el);
                    window.__devSendSelection(el);
                }
            })();
            """.trimIndent(),
            null
        )
    }

    // ---------------------------------------------------------
    // STYLES
    // ---------------------------------------------------------

    private fun renderStyles() {
        panelBody.addView(
            label("STYLES  /  CSS INSPECTOR", 11f, accent)
        )

        panelBody.addView(
            label(
                selectedSelector.ifBlank {
                    "Select an element to inspect CSS"
                },
                12f
            )
        )

        panelBody.addView(label("BOX MODEL", 11f, accent))

        val box = try {
            JSONObject(boxModelJson)
        } catch (_: Exception) {
            JSONObject()
        }

        val boxView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(surface)
        }

        boxView.addView(
            boxLayer("MARGIN", box.optString("margin", "—"),
                Color.rgb(104, 75, 38))
        )

        boxView.addView(
            boxLayer("BORDER", box.optString("border", "—"),
                Color.rgb(137, 89, 39))
        )

        boxView.addView(
            boxLayer("PADDING", box.optString("padding", "—"),
                Color.rgb(44, 91, 61))
        )

        boxView.addView(
            boxLayer("CONTENT", box.optString("content", "—"),
                Color.rgb(42, 75, 124))
        )

        panelBody.addView(boxView)

        panelBody.addView(
            label("COMPUTED CSS", 11f, accent)
        )

        panelBody.addView(
            label(
                selectedComputedCss.ifBlank {
                    "No element selected."
                },
                11f
            ).apply {
                typeface = Typeface.MONOSPACE
                setPadding(dp(9), dp(9), dp(9), dp(9))
                background = rounded(surface)
            }
        )

        panelBody.addView(
            label("INLINE CSS · EDITABLE", 11f, accent)
        )

        val css = editor(
            selectedInlineCss,
            "Select an element to load its CSS.",
            4
        )

        panelBody.addView(
            css,
            LinearLayout.LayoutParams(-1, dp(100))
        )

        panelBody.addView(button("Apply CSS") {
            if (selectedSelector.isBlank()) {
                status.text = "Select an element first"
            } else {
                val quoted = JSONObject.quote(css.text.toString())

                activeWeb?.evaluateJavascript(
                    """
                    (function(){
                        const el = window.__devSelected;
                        if (!el) return;
                        el.style.cssText = $quoted;
                        window.__devSendSelection(el);
                    })();
                    """.trimIndent(),
                    null
                )
            }
        })
    }

    private fun boxLayer(
        title: String,
        value: String,
        color: Int
    ): TextView {
        return TextView(this).apply {
            text = "$title\n$value"
            gravity = Gravity.CENTER
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(color)

            layoutParams = LinearLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(3), 0, dp(3))
            }
        }
    }

    // ---------------------------------------------------------
    // CONSOLE
    // ---------------------------------------------------------

    private fun renderConsole() {
        panelBody.addView(
            label("CONSOLE  /  JAVASCRIPT", 11f, accent)
        )

        consoleOutput = label(
            consoleHistory.joinToString("\n")
                .ifBlank { "DevBrowser console ready." },
            11f
        ).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = rounded(Color.rgb(10, 13, 18))
        }

        panelBody.addView(
            consoleOutput,
            LinearLayout.LayoutParams(-1, dp(145))
        )

        val command = editor(
            "",
            "Enter JavaScript here...",
            2
        )

        panelBody.addView(
            command,
            LinearLayout.LayoutParams(-1, dp(75))
        )

        panelBody.addView(button("▶  Run JavaScript") {
            val source = command.text.toString()

            if (source.isNotBlank()) {
                appendConsole("> $source")

                val quoted = JSONObject.quote(source)

                activeWeb?.evaluateJavascript(
                    """
                    (function(){
                        try {
                            const result = (0, eval)($quoted);

                            if (result === undefined) return "undefined";

                            if (typeof result === "object" && result !== null) {
                                return JSON.stringify(result, null, 2);
                            }

                            return String(result);
                        } catch(e) {
                            return "Error: " + (e.stack || e);
                        }
                    })();
                    """.trimIndent()
                ) { result ->
                    val output = try {
                        JSONArray("[$result]").optString(0, "undefined")
                    } catch (_: Exception) {
                        result ?: "undefined"
                    }

                    appendConsole(output)
                }
            }
        })

        panelBody.addView(button("Clear console") {
            consoleHistory.clear()
            consoleOutput?.text = "Console cleared."
        })
    }

    private fun appendConsole(line: String) {
        runOnUiThread {
            consoleHistory.add(line)

            if (consoleHistory.size > 200) {
                consoleHistory.removeAt(0)
            }

            consoleOutput?.text =
                consoleHistory.joinToString("\n")
        }
    }

    // ---------------------------------------------------------
    // INSPECTOR DATA
    // ---------------------------------------------------------

    private fun handleInspectorData(data: JSONObject) {
        runOnUiThread {
            domNodes = data.optJSONArray("treeNodes") ?: JSONArray()
            selectedHtml = data.optString("html", "")
            selectedInlineCss = data.optString("inlineCss", "")
            selectedComputedCss = data.optString("computedCss", "")
            selectedSelector = data.optString("selector", "")
            boxModelJson = data.optString("boxModel", "{}")

            status.text = if (selectedSelector.isNotBlank()) {
                "Selected: $selectedSelector"
            } else {
                "DOM loaded"
            }

            if (devPanel.visibility == View.VISIBLE &&
                (currentDevTab == "Elements" ||
                    currentDevTab == "Styles")) {
                renderPanel()
            }
        }
    }

    private fun requestDom() {
        activeWeb?.evaluateJavascript(
            """
            (function(){
                if (window.__devSendSelection) {
                    window.__devSendSelection(
                        window.__devSelected || document.body
                    );
                }
            })();
            """.trimIndent(),
            null
        )
    }

    private fun installInspector(view: WebView?) {
        if (view == null) return

        val script = """
        (function(){
            if (window.__devBrowserInstalled) {
                window.__devSendSelection(
                    window.__devSelected || document.body
                );
                return;
            }

            window.__devBrowserInstalled = true;
            window.__devInspect = false;
            window.__devSelected = null;
            window.__devPreviousOutline = null;
            window.__devOldOutline = "";
            window.__devOldOffset = "";

            window.__devHighlight = function(el) {
                if (!el) return;

                if (window.__devPreviousOutline) {
                    window.__devPreviousOutline.style.outline =
                        window.__devOldOutline;
                    window.__devPreviousOutline.style.outlineOffset =
                        window.__devOldOffset;
                }

                window.__devPreviousOutline = el;
                window.__devOldOutline = el.style.outline || "";
                window.__devOldOffset = el.style.outlineOffset || "";

                el.style.outline = "2px solid #ff5722";
                el.style.outlineOffset = "2px";

                window.__devSelected = el;
            };

            function selector(el) {
                if (!el || !el.tagName) return "";

                let s = el.tagName.toLowerCase();

                if (el.id) s += "#" + el.id;

                if (el.classList && el.classList.length) {
                    s += "." + Array.from(el.classList)
                        .slice(0, 3).join(".");
                }

                return s;
            }

            function treeData() {
                const nodes = [];
                let count = 0;

                function walk(node, depth, path) {
                    if (!node || count >= 220 || depth > 6) return;
                    if (node.nodeType !== 1) return;

                    count++;

                    nodes.push({
                        label: selector(node),
                        depth: depth,
                        path: path,
                        selected: node === window.__devSelected
                    });

                    Array.from(node.children).forEach(
                        function(child, index) {
                            walk(child, depth + 1, path.concat(index));
                        }
                    );
                }

                walk(document.body, 0, []);
                return nodes;
            }

            window.__devSendSelection = function(el) {
                if (!el) return;

                window.__devSelected = el;

                const computed = getComputedStyle(el);

                const props = [
                    "display", "position", "width", "height",
                    "margin", "padding", "color",
                    "background-color", "font-size",
                    "font-family", "border", "opacity"
                ];

                const css = props.map(function(p) {
                    return p + ": " + computed.getPropertyValue(p) + ";";
                }).join("\\n");

                const box = {
                    margin: computed.margin,
                    border: computed.borderWidth,
                    padding: computed.padding,
                    content: computed.width + " × " + computed.height
                };

                const data = {
                    selector: selector(el),
                    html: (el.outerHTML || "").slice(0, 18000),
                    inlineCss: el.getAttribute("style") || "",
                    computedCss: css,
                    boxModel: JSON.stringify(box),
                    treeNodes: treeData()
                };

                console.log("__DEVBROWSER__" + JSON.stringify(data));
            };

            document.addEventListener("click", function(e) {
                if (!window.__devInspect) return;

                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();

                const el = e.target;

                window.__devHighlight(el);
                window.__devSendSelection(el);
            }, true);

            window.__devSendSelection(document.body);
        })();
        """.trimIndent()

        view.evaluateJavascript(script, null)
    }

    private fun toggleInspect(enabled: Boolean) {
        inspectEnabled = enabled

        activeWeb?.evaluateJavascript(
            "window.__devInspect = $enabled;",
            null
        )

        status.text = if (enabled) {
            "PICK MODE ON — tap a webpage element"
        } else {
            "Inspect mode OFF"
        }
    }

    // ---------------------------------------------------------
    // FIRST-RUN TUTORIAL
    // ---------------------------------------------------------

    private fun showTutorial() {
        val steps = arrayOf(
            "Welcome to DevBrowser!\n\nA browser with built-in webpage developer tools.",
            "ADDRESS BAR\n\nEnter a website or search term in the address bar.",
            "BROWSER TABS\n\nUse + to open a tab. Tap a tab to switch or × to close it.",
            "INSPECT ELEMENT\n\nOpen the menu and choose Inspect Element. Then tap an element on the webpage.",
            "ELEMENTS\n\nExplore the DOM tree and edit selected HTML.",
            "STYLES\n\nInspect CSS and edit inline styles.",
            "CONSOLE\n\nEnter JavaScript and run it on the current webpage.",
            "RESIZE DEVTOOLS\n\nDrag the handle above the DevTools tabs to resize the panel.",
            "SESSION\n\nYour open tab URLs are saved and restored when the app starts."
        )

        var index = 0

        fun showStep() {
            AlertDialog.Builder(this)
                .setTitle("DevBrowser · ${index + 1}/${steps.size}")
                .setMessage(steps[index])
                .setCancelable(false)
                .setNegativeButton(
                    if (index == 0) "Skip" else "Close"
                ) { _, _ ->
                    prefs.edit()
                        .putBoolean("show_tutorial", false)
                        .apply()
                }
                .setPositiveButton(
                    if (index == steps.lastIndex) "Finish" else "Next"
                ) { _, _ ->
                    if (index < steps.lastIndex) {
                        index++
                        showStep()
                    } else {
                        prefs.edit()
                            .putBoolean("show_tutorial", false)
                            .apply()

                        showDevTools("Elements")
                    }
                }
                .show()
        }

        showStep()
    }

    // ---------------------------------------------------------
    // ACTIVITY LIFECYCLE
    // ---------------------------------------------------------

    override fun onPause() {
        saveSession()
        activeWeb?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        activeWeb?.onResume()
    }

    override fun onDestroy() {
        tabs.forEach {
            try {
                (it.web.parent as? ViewGroup)?.removeView(it.web)
                it.web.stopLoading()
                it.web.destroy()
            } catch (_: Exception) {
            }
        }

        tabs.clear()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        val web = activeWeb

        when {
            devPanel.visibility == View.VISIBLE -> closeDevTools()
            web?.canGoBack() == true -> web.goBack()
            tabs.size > 1 -> closeTab(activeTabId)
            else -> super.onBackPressed()
        }
    }
}
