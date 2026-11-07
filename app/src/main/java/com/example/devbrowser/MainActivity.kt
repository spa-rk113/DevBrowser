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
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
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

    private val bg = Color.rgb(15, 18, 25)
    private val surface = Color.rgb(25, 30, 40)
    private val surface2 = Color.rgb(36, 43, 56)
    private val accent = Color.rgb(139, 124, 255)
    private val textColor = Color.rgb(238, 241, 248)
    private val muted = Color.rgb(157, 169, 188)

    private var panelHeight = 310
    private var currentDevTab = "Elements"
    private var inspectEnabled = false

    private var selectedHtml = ""
    private var selectedInlineCss = ""
    private var selectedComputedCss = ""
    private var selectedSelector = ""
    private var boxModelJson = "{}"
    private var domNodes = JSONArray()

    private val consoleHistory = mutableListOf<String>()
    private var consoleOutput: TextView? = null

    private val activeWeb: WebView?
        get() = tabs.firstOrNull { it.id == activeTabId }?.web

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        panelHeight = prefs.getInt("panel_height", 310)
            .coerceIn(180, 650)

        currentDevTab = prefs.getString("last_dev_tab", "Elements")
            ?: "Elements"

        buildInterface()
        restoreTabs()

        if (prefs.getBoolean("show_tutorial", true)) {
            root.postDelayed({ showTutorial() }, 700)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

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

    private fun makeLabel(
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

    private fun makeButton(
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
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun makeEditor(
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
            setHorizontallyScrolling(true)
        }
    }

    // ---------------------------------------------------------
    // MAIN INTERFACE
    // ---------------------------------------------------------

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            clipToPadding = false
        }

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setBackgroundColor(bg)
        }

        toolbar.addView(makeButton("‹") {
            activeWeb?.let {
                if (it.canGoBack()) it.goBack()
            }
        })

        toolbar.addView(makeButton("›") {
            activeWeb?.let {
                if (it.canGoForward()) it.goForward()
            }
        })

        toolbar.addView(makeButton("↻") {
            activeWeb?.reload()
        })

        toolbar.addView(makeButton("⌂") {
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
                    action == EditorInfo.IME_ACTION_DONE
                ) {
                    loadAddress()
                    true
                } else {
                    false
                }
            }
        }

        toolbar.addView(
            address,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        toolbar.addView(makeButton("⋮") {
            showMenu()
        })

        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(4), dp(5), dp(4))
            setBackgroundColor(surface)
        }

        webContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        status = makeLabel("Ready", 10f, muted).apply {
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
    // WEBVIEW AND TABS
    // ---------------------------------------------------------

    private fun createWebView(tabId: Int): WebView {
        return WebView(this).apply {
            settings.apply {
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

            addJavascriptInterface(
                InspectorBridge(tabId),
                "DevBrowserBridge"
            )

            webViewClient = object : WebViewClient() {

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    val item = tabs.firstOrNull { it.id == tabId }
                        ?: return

                    item.url = url ?: item.url

                    if (tabId == activeTabId) {
                        status.text = "Loading website..."
                        address.setText(item.url)
                        clearSelectedElement()
                    }
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    val item = tabs.firstOrNull { it.id == tabId }
                        ?: return

                    item.url = url ?: item.url
                    item.title = view?.title
                        ?.takeIf { it.isNotBlank() }
                        ?: item.url

                    if (tabId == activeTabId) {
                        address.setText(item.url)
                        status.text = "Page loaded"
                    }

                    renderTabs()
                    saveSession()

                    view?.postDelayed({
                        if (tabs.any { it.id == tabId }) {
                            installInspector(view)
                        }
                    }, 250)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true &&
                        tabId == activeTabId
                    ) {
                        status.text = "Page failed to load"
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {

                override fun onReceivedTitle(
                    view: WebView?,
                    title: String?
                ) {
                    val item = tabs.firstOrNull { it.id == tabId }
                        ?: return

                    if (!title.isNullOrBlank()) {
                        item.title = title
                    }

                    if (tabId == activeTabId) {
                        renderTabs()
                    }
                }

                override fun onProgressChanged(
                    view: WebView?,
                    newProgress: Int
                ) {
                    if (tabId == activeTabId) {
                        status.text = if (newProgress < 100) {
                            "Loading $newProgress%"
                        } else {
                            "Page loaded"
                        }
                    }
                }

                override fun onConsoleMessage(
                    message: ConsoleMessage
                ): Boolean {
                    val level = when (message.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                        ConsoleMessage.MessageLevel.WARNING -> "WARN"
                        ConsoleMessage.MessageLevel.DEBUG -> "DEBUG"
                        else -> "LOG"
                    }

                    appendConsole(
                        "[$level] ${message.message()}"
                    )

                    return true
                }
            }
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

        val id = nextTabId++
        val web = createWebView(id)

        val item = BrowserTab(
            id = id,
            web = web,
            title = "New tab",
            url = url
        )

        tabs.add(item)
        switchTab(id)

        if (load) {
            web.loadUrl(url)
        }
    }

    private fun restoreTabs() {
        val saved = prefs.getString("saved_tabs", null)
        var restored = false

        if (!saved.isNullOrBlank()) {
            try {
                val array = JSONArray(saved)

                for (i in 0 until array.length()) {
                    val url = array.optString(i)

                    if (url.startsWith("http://") ||
                        url.startsWith("https://")
                    ) {
                        createTab(url, true)
                        restored = true
                    }

                    if (tabs.size >= 8) break
                }
            } catch (_: Exception) {
                appendConsole("Session restore failed")
            }
        }

        if (!restored) {
            createTab("https://www.google.com")
            return
        }

        val index = prefs.getInt("active_tab_index", 0)
            .coerceIn(0, tabs.lastIndex)

        switchTab(tabs[index].id)
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

        item.web.onResume()
    }

    private fun closeTab(id: Int) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return

        val wasActive = id == activeTabId
        val item = tabs[index]

        if (wasActive) {
            webContainer.removeView(item.web)
            activeTabId = -1
        }

        tabs.removeAt(index)

        try {
            item.web.stopLoading()
            item.web.removeJavascriptInterface("DevBrowserBridge")
            item.web.destroy()
        } catch (_: Exception) {
            // Ignore cleanup errors from an already-destroyed WebView.
        }

        if (tabs.isEmpty()) {
            createTab()
        } else if (wasActive) {
            val nextIndex = (index - 1).coerceAtLeast(0)
                .coerceAtMost(tabs.lastIndex)

            switchTab(tabs[nextIndex].id)
        } else {
            renderTabs()
            saveSession()
        }
    }

    private fun renderTabs() {
        if (!::tabStrip.isInitialized) return

        tabStrip.removeAllViews()

        tabs.forEach { item ->
            val selected = item.id == activeTabId

            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(4), dp(5), dp(4))
                background = rounded(
                    if (selected) Color.rgb(45, 55, 82)
                    else surface2,
                    if (selected) accent else Color.TRANSPARENT
                )
            }

            val title = TextView(this).apply {
                text = item.title.take(12).ifBlank { "New tab" }
                textSize = 11f
                setTextColor(if (selected) Color.WHITE else muted)
                maxWidth = dp(105)
                setOnClickListener {
                    switchTab(item.id)
                }
            }

            val close = TextView(this).apply {
                text = "×"
                textSize = 16f
                setTextColor(muted)
                setPadding(dp(6), 0, dp(4), 0)
                setOnClickListener {
                    closeTab(item.id)
                }
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

        tabStrip.addView(makeButton("+") {
            createTab()
        })
    }

    private fun saveSession() {
        if (!::root.isInitialized) return

        val array = JSONArray()

        tabs.forEach { item ->
            array.put(item.web.url ?: item.url)
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
        val input = address.text.toString().trim()
        if (input.isBlank()) return

        val url = when {
            input.startsWith("https://") ||
                input.startsWith("http://") -> input

            input.contains(" ") ||
                !input.contains(".") -> {
                "https://www.google.com/search?q=" +
                    URLEncoder.encode(input, "UTF-8")
            }

            else -> "https://$input"
        }

        val manager = getSystemService(INPUT_METHOD_SERVICE)
            as InputMethodManager

        manager.hideSoftInputFromWindow(address.windowToken, 0)

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

        devPanel.alpha = 0f
        devPanel.animate()
            .alpha(1f)
            .setDuration(160)
            .start()

        requestDom()
        saveSession()
    }

    private fun closeDevTools() {
        toggleInspect(false)

        devPanel.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                devPanel.visibility = View.GONE
                devPanel.alpha = 1f
            }
            .start()

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
                            .coerceIn(180, max(180, screenDp - 190))

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
            val tab = TextView(this).apply {
                text = if (name == currentDevTab) {
                    "● $name"
                } else {
                    name
                }

                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(
                    if (name == currentDevTab) accent else muted
                )
                setPadding(dp(12), dp(13), dp(12), dp(13))
                setOnClickListener {
                    currentDevTab = name
                    renderPanel()
                    if (name != "Console") requestDom()
                    saveSession()
                }
            }

            bar.addView(tab)
        }

        bar.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        bar.addView(makeButton("✕") {
            closeDevTools()
        })

        devPanel.addView(bar)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
        }

        panelBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(14))
        }

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
            makeLabel("ELEMENTS  /  DOM INSPECTOR", 11f, accent)
        )

        panelBody.addView(
            makeButton(
                if (inspectEnabled) "Stop picking elements"
                else "◎  Pick element on webpage"
            ) {
                toggleInspect(!inspectEnabled)
            }
        )

        panelBody.addView(
            makeLabel(
                selectedSelector.ifBlank {
                    "Select an element or wait for the page DOM."
                },
                12f
            )
        )

        panelBody.addView(
            makeLabel("DOCUMENT TREE", 11f, accent)
        )

        if (domNodes.length() == 0) {
            panelBody.addView(
                makeLabel(
                    "DOM is loading. Tap Refresh DOM if it stays empty.",
                    11f,
                    muted
                )
            )
        } else {
            for (i in 0 until domNodes.length()) {
                val node = domNodes.optJSONObject(i) ?: continue
                val depth = node.optInt("depth", 0)
                val path = node.optJSONArray("path") ?: JSONArray()
                val selected = node.optBoolean("selected", false)
                val labelText = node.optString("label", "element")

                val row = TextView(this).apply {
                    text = (if (selected) "◆ " else "  ") +
                        labelText

                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(
                        if (selected) accent else textColor
                    )
                    setPadding(
                        dp(8 + depth * 13),
                        dp(7),
                        dp(5),
                        dp(7)
                    )
                    background = if (selected) {
                        rounded(Color.rgb(43, 43, 68))
                    } else {
                        rounded(bg)
                    }

                    setOnClickListener {
                        selectDomPath(path)
                    }
                }

                panelBody.addView(
                    row,
                    LinearLayout.LayoutParams(-1, -2)
                )
            }
        }

        panelBody.addView(makeButton("↻  Refresh DOM") {
            requestDom()
        })

        panelBody.addView(
            makeLabel("HTML EDITOR · SELECTED ELEMENT", 11f, accent)
        )

        val htmlEditor = makeEditor(
            selectedHtml,
            "Select an element to load its HTML.",
            5
        )

        panelBody.addView(
            htmlEditor,
            LinearLayout.LayoutParams(-1, dp(135))
        )

        panelBody.addView(makeButton("Apply HTML") {
            if (selectedHtml.isBlank()) {
                status.text = "Select an element first"
            } else {
                applyHtml(htmlEditor.text.toString())
            }
        })
    }

    private fun selectDomPath(path: JSONArray) {
        val values = mutableListOf<Int>()

        for (i in 0 until path.length()) {
            values.add(path.optInt(i, -1))
        }

        val jsonPath = JSONArray(values).toString()
        val quotedPath = JSONObject.quote(jsonPath)

        activeWeb?.evaluateJavascript(
            """
            (function(){
                try {
                    const path = JSON.parse($quotedPath);
                    let node = document.body;

                    for (const index of path) {
                        if (!node || !node.children[index]) return;
                        node = node.children[index];
                    }

                    if (node && window.__dbSelect) {
                        window.__dbSelect(node);
                    }
                } catch(e) {
                    console.error(e);
                }
            })();
            """.trimIndent(),
            null
        )
    }

    private fun applyHtml(html: String) {
        if (html.isBlank()) {
            status.text = "HTML cannot be empty"
            return
        }

        if (html.length > 50000) {
            status.text = "HTML is too large (50 KB limit)"
            return
        }

        val quoted = JSONObject.quote(html)

        activeWeb?.evaluateJavascript(
            """
            (function(){
                try {
                    const old = window.__dbSelected;
                    if (!old || !old.parentNode) {
                        return "No selected element";
                    }

                    const template = document.createElement("template");
                    template.innerHTML = $quoted;

                    const first = template.content.firstElementChild;
                    if (!first) return "Invalid HTML";

                    old.replaceWith(template.content);
                    window.__dbSelected = first;
                    window.__dbSend(first);

                    return "HTML applied";
                } catch(e) {
                    return "HTML error: " + e.message;
                }
            })();
            """.trimIndent()
        ) { result ->
            status.text = decodeJsResult(result)
        }
    }

    // ---------------------------------------------------------
    // STYLES
    // ---------------------------------------------------------

    private fun renderStyles() {
        panelBody.addView(
            makeLabel("STYLES  /  CSS INSPECTOR", 11f, accent)
        )

        panelBody.addView(
            makeLabel(
                selectedSelector.ifBlank {
                    "Select an element to inspect CSS."
                },
                12f
            )
        )

        panelBody.addView(
            makeLabel("BOX MODEL", 11f, accent)
        )

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
            boxLayer(
                "MARGIN",
                box.optString("margin", "—"),
                Color.rgb(104, 75, 38)
            )
        )

        boxView.addView(
            boxLayer(
                "BORDER",
                box.optString("border", "—"),
                Color.rgb(137, 89, 39)
            )
        )

        boxView.addView(
            boxLayer(
                "PADDING",
                box.optString("padding", "—"),
                Color.rgb(44, 91, 61)
            )
        )

        boxView.addView(
            boxLayer(
                "CONTENT",
                box.optString("content", "—"),
                Color.rgb(42, 75, 124)
            )
        )

        panelBody.addView(boxView)

        panelBody.addView(
            makeLabel("COMPUTED CSS", 11f, accent)
        )

        panelBody.addView(
            makeLabel(
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
            makeLabel("INLINE CSS · EDITABLE", 11f, accent)
        )

        val cssEditor = makeEditor(
            selectedInlineCss,
            "Select an element to load its CSS.",
            4
        )

        panelBody.addView(
            cssEditor,
            LinearLayout.LayoutParams(-1, dp(100))
        )

        panelBody.addView(makeButton("Apply CSS") {
            if (selectedSelector.isBlank()) {
                status.text = "Select an element first"
            } else {
                val quoted = JSONObject.quote(
                    cssEditor.text.toString()
                )

                activeWeb?.evaluateJavascript(
                    """
                    (function(){
                        try {
                            const el = window.__dbSelected;
                            if (!el) return "No selected element";

                            el.style.cssText = $quoted;
                            window.__dbSend(el);

                            return "CSS applied";
                        } catch(e) {
                            return "CSS error: " + e.message;
                        }
                    })();
                    """.trimIndent()
                ) { result ->
                    status.text = decodeJsResult(result)
                }
            }
        })

        panelBody.addView(makeButton("↻  Refresh styles") {
            requestDom()
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
    // JAVASCRIPT CONSOLE
    // ---------------------------------------------------------

    private fun renderConsole() {
        panelBody.addView(
            makeLabel("CONSOLE  /  JAVASCRIPT", 11f, accent)
        )

        consoleOutput = makeLabel(
            consoleHistory.joinToString("\n")
                .ifBlank { "DevBrowser console ready." },
            11f
        ).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = rounded(Color.rgb(10, 13, 18))
            setTextIsSelectable(true)
        }

        panelBody.addView(
            consoleOutput,
            LinearLayout.LayoutParams(-1, dp(145))
        )

        val command = makeEditor(
            "",
            "Enter JavaScript here...",
            2
        )

        panelBody.addView(
            command,
            LinearLayout.LayoutParams(-1, dp(75))
        )

        panelBody.addView(makeButton("▶  Run JavaScript") {
            val source = command.text.toString()

            if (source.isNotBlank()) {
                runJavaScript(source)
            }
        })

        panelBody.addView(makeButton("Clear console") {
            consoleHistory.clear()
            consoleOutput?.text = "Console cleared."
        })
    }

    private fun runJavaScript(source: String) {
        appendConsole("> $source")

        val quoted = JSONObject.quote(source)

        activeWeb?.evaluateJavascript(
            """
            (function(){
                try {
                    const result = (0, eval)($quoted);

                    return Promise.resolve(result).then(function(value) {
                        if (value === undefined) return "undefined";

                        if (typeof value === "string") return value;

                        try {
                            return JSON.stringify(value, null, 2);
                        } catch(e) {
                            return String(value);
                        }
                    });
                } catch(e) {
                    return "Error: " + (e.stack || e.message || e);
                }
            })();
            """.trimIndent()
        ) { result ->
            appendConsole(decodeJsResult(result))
        } ?: appendConsole("No active webpage.")
    }

    private fun decodeJsResult(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") {
            return "undefined"
        }

        return try {
            val value = JSONTokener(raw).nextValue()
            value?.toString() ?: "undefined"
        } catch (_: Exception) {
            raw
        }
    }

    private fun appendConsole(line: String) {
        runOnUiThread {
            consoleHistory.add(line.take(4000))

            while (consoleHistory.size > 200) {
                consoleHistory.removeAt(0)
            }

            consoleOutput?.text =
                consoleHistory.joinToString("\n")
        }
    }

    // ---------------------------------------------------------
    // INSPECTOR JAVASCRIPT BRIDGE
    // ---------------------------------------------------------

    private inner class InspectorBridge(
        private val tabId: Int
    ) {
        @JavascriptInterface
        fun postData(payload: String) {
            if (payload.length > 250000) return

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (tabId != activeTabId) return@runOnUiThread

                try {
                    val data = JSONObject(payload)
                    handleInspectorData(data)
                } catch (_: Exception) {
                    status.text = "Inspector data error"
                }
            }
        }
    }

    private fun installInspector(view: WebView?) {
        if (view == null) return

        val script = """
        (function() {
            if (window.__dbInstalled) {
                window.__dbSend(window.__dbSelected || document.body);
                return;
            }

            window.__dbInstalled = true;
            window.__dbInspect = false;
            window.__dbSelected = null;
            window.__dbPrevious = null;
            window.__dbOldOutline = "";
            window.__dbOldOffset = "";

            function makeSelector(el) {
                if (!el || !el.tagName) return "";

                let value = el.tagName.toLowerCase();

                if (el.id) value += "#" + el.id;

                if (el.classList && el.classList.length) {
                    value += "." + Array.from(el.classList)
                        .slice(0, 3)
                        .join(".");
                }

                return value;
            }

            function getPath(el) {
                const path = [];
                let node = el;

                while (node && node !== document.body) {
                    const parent = node.parentElement;
                    if (!parent) break;

                    path.unshift(
                        Array.prototype.indexOf.call(
                            parent.children,
                            node
                        )
                    );

                    node = parent;
                }

                return path;
            }

            function getTree() {
                const output = [];
                let count = 0;

                function walk(node, depth, path) {
                    if (!node || count >= 160 || depth > 6) return;
                    if (node.nodeType !== 1) return;

                    count++;

                    output.push({
                        label: makeSelector(node),
                        depth: depth,
                        path: path,
                        selected: node === window.__dbSelected
                    });

                    const children = Array.from(node.children);

                    for (let i = 0; i < children.length; i++) {
                        walk(
                            children[i],
                            depth + 1,
                            path.concat(i)
                        );

                        if (count >= 160) break;
                    }
                }

                if (document.body) {
                    walk(document.body, 0, []);
                }

                return output;
            }

            window.__dbSend = function(el) {
                if (!el || el.nodeType !== 1) return;

                window.__dbSelected = el;

                const computed = window.getComputedStyle(el);

                const properties = [
                    "display",
                    "position",
                    "width",
                    "height",
                    "margin",
                    "padding",
                    "color",
                    "background-color",
                    "font-size",
                    "font-family",
                    "border",
                    "opacity"
                ];

                const css = properties.map(function(name) {
                    return name + ": " +
                        computed.getPropertyValue(name) + ";";
                }).join("\\n");

                const data = {
                    selector: makeSelector(el),
                    html: (el.outerHTML || "").slice(0, 12000),
                    inlineCss: el.getAttribute("style") || "",
                    computedCss: css,
                    boxModel: JSON.stringify({
                        margin: computed.margin,
                        border: computed.borderWidth,
                        padding: computed.padding,
                        content: computed.width + " × " + computed.height
                    }),
                    treeNodes: getTree()
                };

                try {
                    window.DevBrowserBridge.postData(
                        JSON.stringify(data)
                    );
                } catch(e) {
                    console.error("DevBrowser bridge:", e);
                }
            };

            window.__dbSelect = function(el) {
                if (!el) return;

                if (window.__dbPrevious) {
                    window.__dbPrevious.style.outline =
                        window.__dbOldOutline;
                    window.__dbPrevious.style.outlineOffset =
                        window.__dbOldOffset;
                }

                window.__dbPrevious = el;
                window.__dbOldOutline = el.style.outline || "";
                window.__dbOldOffset =
                    el.style.outlineOffset || "";

                el.style.outline = "2px solid #8B7CFF";
                el.style.outlineOffset = "2px";

                window.__dbSend(el);
            };

            document.addEventListener("click", function(event) {
                if (!window.__dbInspect) return;

                event.preventDefault();
                event.stopPropagation();
                event.stopImmediatePropagation();

                const target = event.target;

                if (target && target.nodeType === 1) {
                    window.__dbSelect(target);
                }
            }, true);

            window.__dbSend(document.body);
        })();
        """.trimIndent()

        view.evaluateJavascript(script, null)
    }

    private fun requestDom() {
        activeWeb?.evaluateJavascript(
            """
            (function(){
                if (window.__dbSend) {
                    window.__dbSend(
                        window.__dbSelected || document.body
                    );
                } else {
                    console.warn("Inspector is not ready yet.");
                }
            })();
            """.trimIndent(),
            null
        )
    }

    private fun handleInspectorData(data: JSONObject) {
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
            currentDevTab != "Console"
        ) {
            renderPanel()
        }
    }

    private fun clearSelectedElement() {
        selectedHtml = ""
        selectedInlineCss = ""
        selectedComputedCss = ""
        selectedSelector = ""
        boxModelJson = "{}"
        domNodes = JSONArray()
    }

    private fun toggleInspect(enabled: Boolean) {
        inspectEnabled = enabled

        activeWeb?.evaluateJavascript(
            "window.__dbInspect = $enabled;",
            null
        )

        status.text = if (enabled) {
            "PICK MODE ON — tap a webpage element"
        } else {
            "Inspect mode OFF"
        }

        if (devPanel.visibility == View.VISIBLE &&
            currentDevTab == "Elements"
        ) {
            renderPanel()
        }
    }

    // ---------------------------------------------------------
    // TUTORIAL
    // ---------------------------------------------------------

    private fun showTutorial() {
        val steps = arrayOf(
            "Welcome to DevBrowser!\n\nA browser with built-in developer tools.",
            "ADDRESS BAR\n\nEnter a website or search term in the address bar.",
            "BROWSER TABS\n\nUse + to open a tab. Tap a tab to switch or × to close it.",
            "INSPECT ELEMENT\n\nOpen the menu and choose Inspect Element. Then tap an element on the webpage.",
            "ELEMENTS\n\nExplore the DOM tree and edit selected HTML.",
            "STYLES\n\nInspect CSS and edit inline styles.",
            "CONSOLE\n\nEnter JavaScript and run it on the current webpage.",
            "RESIZE DEVTOOLS\n\nDrag the handle above the DevTools tabs to resize the panel."
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
                    if (index == steps.lastIndex) "Finish"
                    else "Next"
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
    // LIFECYCLE AND BACK
    // ---------------------------------------------------------

    override fun onPause() {
        saveSession()

        tabs.forEach {
            try {
                it.web.onPause()
            } catch (_: Exception) {
            }
        }

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        activeWeb?.onResume()
    }

    override fun onDestroy() {
        tabs.forEach { item ->
            try {
                (item.web.parent as? ViewGroup)
                    ?.removeView(item.web)

                item.web.stopLoading()
                item.web.removeJavascriptInterface(
                    "DevBrowserBridge"
                )
                item.web.destroy()
            } catch (_: Exception) {
            }
        }

        tabs.clear()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        when {
            devPanel.visibility == View.VISIBLE -> closeDevTools()

            activeWeb?.canGoBack() == true -> activeWeb?.goBack()

            tabs.size > 1 -> closeTab(activeTabId)

            else -> super.onBackPressed()
        }
    }
}
