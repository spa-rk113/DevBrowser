
package com.example.devbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import java.net.URLEncoder

class MainActivity : Activity() {

    private data class BrowserTab(
        val id: Int,
        val web: WebView,
        var title: String,
        var url: String,
        var inspect: Boolean = false,
        var pageGeneration: Int = 0
    )

    private data class PendingCommand(
        val tabId: Int,
        val generation: Int
    )

    private val tabs = mutableListOf<BrowserTab>()
    private val pendingCommands = mutableMapOf<Int, PendingCommand>()
    private val handler = Handler(Looper.getMainLooper())

    private var nextTabId = 1
    private var nextCommandId = 1
    private var activeTabId = -1

    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var devPanel: LinearLayout
    private lateinit var panelBody: LinearLayout
    private lateinit var panelParams: LinearLayout.LayoutParams

    private var panelHeight = 320
    private var panelVisible = false
    private var currentDevTab = "Elements"

    private var selectedHtml = ""
    private var selectedCss = ""
    private var selectedComputed = ""
    private var selectedSelector = ""
    private var selectedBox = JSONObject()
    private var domNodes = JSONArray()

    private var htmlDirty = false
    private var cssDirty = false

    private val consoleHistory = mutableListOf<String>()
    private var consoleOutput: TextView? = null

    private val prefs by lazy {
        getSharedPreferences("devbrowser_session", Context.MODE_PRIVATE)
    }

    private val bg = Color.rgb(15, 18, 25)
    private val surface = Color.rgb(25, 30, 40)
    private val surface2 = Color.rgb(36, 43, 56)
    private val accent = Color.rgb(139, 124, 255)
    private val foreground = Color.rgb(238, 241, 248)
    private val muted = Color.rgb(157, 169, 188)

    private val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    private val activeWeb: WebView?
        get() = activeTab?.web

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // --------------------------------------------------
    // ACTIVITY
    // --------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        panelHeight = prefs.getInt("panel_height", 320)
            .coerceIn(180, 650)

        currentDevTab = prefs.getString("dev_tab", "Elements")
            ?: "Elements"

        buildInterface()
        applyInsets()
        restoreSession()

        if (prefs.getBoolean("first_launch", true)) {
            root.postDelayed({
                showTutorial()
            }, 600)
        }
    }

    private fun applyInsets() {
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars()
                )
                view.setPadding(0, bars.top, 0, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    0,
                    insets.systemWindowInsetTop,
                    0,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }
        root.requestApplyInsets()
    }

    // --------------------------------------------------
    // UI HELPERS
    // --------------------------------------------------

    private fun rounded(
        color: Int,
        stroke: Int = Color.TRANSPARENT,
        radius: Int = 12
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != Color.TRANSPARENT) {
            setStroke(dp(1), stroke)
        }
    }

    private fun makeLabel(
        value: String,
        size: Float = 12f,
        color: Int = foreground
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun makeButton(
        value: String,
        action: () -> Unit
    ) = TextView(this).apply {
        text = value
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(foreground)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = rounded(surface2)
        isClickable = true
        isFocusable = true

        setOnClickListener {
            animate().scaleX(0.96f).scaleY(0.96f)
                .setDuration(65)
                .withEndAction {
                    animate().scaleX(1f).scaleY(1f)
                        .setDuration(85).start()
                    action()
                }.start()
        }
    }

    private fun makeEditor(
        initial: String,
        hintText: String,
        lines: Int = 4
    ) = EditText(this).apply {
        setText(initial)
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

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // --------------------------------------------------
    // MAIN INTERFACE
    // --------------------------------------------------

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
            setTextColor(foreground)
            setHintTextColor(muted)
            setPadding(dp(12), 0, dp(10), 0)
            background = rounded(surface2, accent, 22)

            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO ||
                    action == EditorInfo.IME_ACTION_DONE
                ) {
                    loadAddress()
                    true
                } else false
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

        panelParams = LinearLayout.LayoutParams(
            -1,
            dp(panelHeight)
        )

        root.addView(toolbar)
        root.addView(tabStrip, LinearLayout.LayoutParams(-1, dp(43)))
        root.addView(
            webContainer,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        root.addView(status)
        root.addView(devPanel, panelParams)

        setContentView(root)
    }

    // --------------------------------------------------
    // WEBVIEW
    // --------------------------------------------------

    private fun createWebView(tabId: Int): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.WHITE)

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
                    val tab = tabs.firstOrNull { it.id == tabId }
                        ?: return

                    tab.pageGeneration++
                    tab.url = url ?: tab.url

                    pendingCommands.entries.removeAll {
                        it.value.tabId == tabId
                    }

                    if (tabId == activeTabId) {
                        address.setText(tab.url)
                        status.text = "Loading website..."
                        clearSelection()
                    }
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    val tab = tabs.firstOrNull { it.id == tabId }
                        ?: return

                    tab.url = url ?: tab.url
                    tab.title = view?.title
                        ?.takeIf { it.isNotBlank() }
                        ?: tab.url

                    if (tabId == activeTabId) {
                        address.setText(tab.url)
                        status.text = "Page loaded"
                    }

                    renderTabs()
                    saveSession()

                    view?.postDelayed({
                        val current = tabs.firstOrNull {
                            it.id == tabId
                        } ?: return@postDelayed

                        if (current.web === view) {
                            installInspector(current)
                        }
                    }, 250)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
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
                    val tab = tabs.firstOrNull { it.id == tabId }
                        ?: return

                    if (!title.isNullOrBlank()) {
                        tab.title = title
                    }

                    if (tabId == activeTabId) renderTabs()
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
                    if (tabId == activeTabId) {
                        val level = when (message.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                            ConsoleMessage.MessageLevel.WARNING -> "WARN"
                            ConsoleMessage.MessageLevel.DEBUG -> "DEBUG"
                            else -> "LOG"
                        }

                        appendConsole(
                            "[$level] ${message.message()} " +
                                "(line ${message.lineNumber()})"
                        )
                    }
                    return true
                }
            }
        }
    }

    // --------------------------------------------------
    // TABS
    // --------------------------------------------------

    private fun createTab(
        url: String = "https://www.google.com"
    ) {
        if (tabs.size >= 8) {
            toast("Maximum 8 tabs allowed")
            return
        }

        val id = nextTabId++
        val web = createWebView(id)

        tabs.add(
            BrowserTab(id, web, "New tab", url)
        )

        switchTab(id)
        web.loadUrl(url)
    }

    private fun switchTab(id: Int) {
        val tab = tabs.firstOrNull { it.id == id } ?: return

        activeTabId = id
        webContainer.removeAllViews()

        (tab.web.parent as? ViewGroup)?.removeView(tab.web)

        webContainer.addView(
            tab.web,
            FrameLayout.LayoutParams(-1, -1)
        )

        address.setText(tab.web.url ?: tab.url)

        renderTabs()
        saveSession()
        tab.web.onResume()

        if (panelVisible) {
            tab.web.post {
                installInspector(tab)
            }
        }
    }

    private fun closeTab(id: Int) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return

        val wasActive = activeTabId == id
        val tab = tabs[index]

        if (wasActive) {
            webContainer.removeView(tab.web)
            activeTabId = -1
        }

        tabs.removeAt(index)
        pendingCommands.entries.removeAll {
            it.value.tabId == id
        }

        try {
            tab.web.stopLoading()
            tab.web.removeJavascriptInterface("DevBrowserBridge")
            tab.web.destroy()
        } catch (_: Exception) {
        }

        if (tabs.isEmpty()) {
            createTab()
        } else if (wasActive) {
            val next = (index - 1).coerceIn(0, tabs.lastIndex)
            switchTab(tabs[next].id)
        } else {
            renderTabs()
            saveSession()
        }
    }

    private fun renderTabs() {
        if (!::tabStrip.isInitialized) return

        tabStrip.removeAllViews()

        tabs.forEach { tab ->
            val selected = tab.id == activeTabId

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(4), dp(5), dp(4))
                background = rounded(
                    if (selected) Color.rgb(45, 55, 82) else surface2,
                    if (selected) accent else Color.TRANSPARENT
                )
            }

            val title = TextView(this).apply {
                text = tab.title.take(12).ifBlank { "New tab" }
                textSize = 11f
                maxWidth = dp(105)
                setTextColor(if (selected) Color.WHITE else muted)
                setOnClickListener {
                    switchTab(tab.id)
                }
            }

            val close = TextView(this).apply {
                text = "×"
                textSize = 16f
                setTextColor(muted)
                setPadding(dp(7), 0, dp(4), 0)
                setOnClickListener {
                    closeTab(tab.id)
                }
            }

            container.addView(title)
            container.addView(close)

            tabStrip.addView(
                container,
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

    private fun restoreSession() {
        val saved = prefs.getString("saved_tabs", null)
        var restored = false

        if (!saved.isNullOrBlank()) {
            try {
                val array = JSONArray(saved)

                for (i in 0 until array.length()) {
                    val url = array.optString(i)

                    if (url.startsWith("https://") ||
                        url.startsWith("http://")
                    ) {
                        createTab(url)
                        restored = true
                    }

                    if (tabs.size >= 8) break
                }
            } catch (_: Exception) {
                appendConsole("Session restore failed")
            }
        }

        if (!restored) {
            createTab()
            return
        }

        val index = prefs.getInt("active_tab_index", 0)
            .coerceIn(0, tabs.lastIndex)

        switchTab(tabs[index].id)
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
            .putString("dev_tab", currentDevTab)
            .putInt("panel_height", panelHeight)
            .apply()
    }

    // --------------------------------------------------
    // ADDRESS BAR
    // --------------------------------------------------

    private fun loadAddress() {
        val input = address.text.toString().trim()
        if (input.isBlank()) return

        val url = when {
            input.startsWith("https://") ||
                input.startsWith("http://") -> input

            input.contains(" ") || !input.contains(".") ->
                "https://www.google.com/search?q=" +
                    URLEncoder.encode(input, "UTF-8")

            else -> "https://$input"
        }

        hideKeyboard(address)
        activeWeb?.loadUrl(url)
    }

    // --------------------------------------------------
    // MENU
    // --------------------------------------------------

    private fun showMenu() {
        val options = arrayOf(
            "New tab",
            "Inspect Element",
            "Elements",
            "Styles",
            "Console",
            "Close DevTools",
            "Reload page",
            "Help / Tutorial"
        )

        AlertDialog.Builder(this)
            .setTitle("DevBrowser")
            .setItems(options) { _, index ->
                when (index) {
                    0 -> createTab()
                    1 -> {
                        showDevTools("Elements")
                        setInspectMode(true)
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

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    // --------------------------------------------------
    // DEVTOOLS PANEL
    // --------------------------------------------------

    private fun showDevTools(tab: String) {
        currentDevTab = tab
        panelVisible = true

        panelParams.height = dp(panelHeight)
        devPanel.layoutParams = panelParams
        devPanel.visibility = View.VISIBLE

        renderPanel()

        devPanel.alpha = 0f
        devPanel.translationY = dp(14).toFloat()

        devPanel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180)
            .start()

        activeTab?.let { installInspector(it) }
        saveSession()
    }

    private fun closeDevTools() {
        panelVisible = false
        setInspectMode(false)

        devPanel.animate()
            .alpha(0f)
            .translationY(dp(12).toFloat())
            .setDuration(140)
            .withEndAction {
                devPanel.visibility = View.GONE
                devPanel.alpha = 1f
                devPanel.translationY = 0f
            }
            .start()

        saveSession()
    }

    private fun buildDragHandle(): View {
        return View(this).apply {
            minimumHeight = dp(14)
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

                        val screenHeight = (
                            resources.displayMetrics.heightPixels /
                                resources.displayMetrics.density
                            ).toInt()

                        panelHeight = (startHeight + delta)
                            .coerceIn(180, (screenHeight - 180).coerceAtLeast(180))

                        panelParams.height = dp(panelHeight)
                        devPanel.layoutParams = panelParams
                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
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
                text = if (name == currentDevTab) "● $name" else name
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(
                    if (name == currentDevTab) accent else muted
                )
                setPadding(dp(12), dp(13), dp(12), dp(13))

                setOnClickListener {
                    currentDevTab = name
                    renderPanel()

                    if (name != "Console") {
                        activeTab?.let { installInspector(it) }
                    }

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

    // --------------------------------------------------
    // ELEMENTS / DOM TREE
    // --------------------------------------------------

    private fun renderElements() {
        panelBody.addView(
            makeLabel("ELEMENTS / DOM INSPECTOR", 11f, accent)
        )

        panelBody.addView(makeButton(
            if (activeTab?.inspect == true) {
                "Stop picking elements"
            } else {
                "◎ Pick element on webpage"
            }
        ) {
            setInspectMode(activeTab?.inspect != true)
        })

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
                    "DOM loading. Use Refresh DOM if needed.",
                    11f,
                    muted
                )
            )
        } else {
            for (i in 0 until domNodes.length()) {
                val node = domNodes.optJSONObject(i) ?: continue
                val id = node.optInt("id", -1)
                val depth = node.optInt("depth", 0)
                val selected = node.optBoolean("selected", false)

                val row = TextView(this).apply {
                    text = (if (selected) "◆ " else "  ") +
                        node.optString("label", "element")

                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(
                        if (selected) accent else foreground
                    )

                    setPadding(
                        dp(8 + depth * 13),
                        dp(7),
                        dp(5),
                        dp(7)
                    )

                    background = rounded(
                        if (selected) {
                            Color.rgb(43, 43, 68)
                        } else {
                            bg
                        }
                    )

                    setOnClickListener {
                        selectDomNode(id)
                    }
                }

                panelBody.addView(row)
            }
        }

        panelBody.addView(
            makeButton("↻ Refresh DOM") {
                activeTab?.let { installInspector(it) }
            }
        )

        panelBody.addView(
            makeLabel("HTML EDITOR", 11f, accent)
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

        htmlEditor.addTextChangedListener(
            SimpleTextWatcher {
                htmlDirty = true
            }
        )

        panelBody.addView(
            makeButton("Apply HTML") {
                applyHtml(htmlEditor.text.toString())
            }
        )
    }

    private fun selectDomNode(id: Int) {
        if (id < 0) return

        activeWeb?.evaluateJavascript(
            """
            (function(){
                const el = window.__dbNodeById &&
                    window.__dbNodeById.get($id);

                if (el && window.__dbSelect) {
                    window.__dbSelect(el);
                    return true;
                }

                return false;
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
            status.text = "HTML exceeds 50 KB limit"
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

                    const roots = Array.from(
                        template.content.children
                    );

                    if (roots.length !== 1) {
                        return "Enter exactly one root HTML element";
                    }

                    const replacement = roots[0];

                    old.replaceWith(replacement);

                    if (window.__dbSelect) {
                        window.__dbSelect(replacement);
                    }

                    return "HTML applied";
                } catch(e) {
                    return "HTML error: " + e.message;
                }
            })();
            """.trimIndent()
        ) { result ->
            status.text = decodeJsResult(result)
            htmlDirty = false
            activeTab?.let { installInspector(it) }
        }
    }

    // --------------------------------------------------
    // STYLES / BOX MODEL
    // --------------------------------------------------

    private fun renderStyles() {
        panelBody.addView(
            makeLabel("STYLES / CSS INSPECTOR", 11f, accent)
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

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(surface)
        }

        box.addView(
            boxLayer(
                "MARGIN",
                selectedBox.optString("margin", "—"),
                Color.rgb(104, 75, 38)
            )
        )

        box.addView(
            boxLayer(
                "BORDER",
                selectedBox.optString("border", "—"),
                Color.rgb(137, 89, 39)
            )
        )

        box.addView(
            boxLayer(
                "PADDING",
                selectedBox.optString("padding", "—"),
                Color.rgb(44, 91, 61)
            )
        )

        box.addView(
            boxLayer(
                "CONTENT",
                selectedBox.optString("content", "—"),
                Color.rgb(42, 75, 124)
            )
        )

        panelBody.addView(box)

        panelBody.addView(
            makeLabel("COMPUTED CSS", 11f, accent)
        )

        panelBody.addView(
            makeLabel(
                selectedComputed.ifBlank {
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
            selectedCss,
            "Select an element to load its CSS.",
            4
        )

        panelBody.addView(
            cssEditor,
            LinearLayout.LayoutParams(-1, dp(105))
        )

        cssEditor.addTextChangedListener(
            SimpleTextWatcher {
                cssDirty = true
            }
        )

        panelBody.addView(
            makeButton("Apply CSS") {
                applyCss(cssEditor.text.toString())
            }
        )

        panelBody.addView(
            makeButton("↻ Refresh styles") {
                activeTab?.let { installInspector(it) }
            }
        )
    }

    private fun boxLayer(
        title: String,
        value: String,
        color: Int
    ) = TextView(this).apply {
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

    private fun applyCss(css: String) {
        if (css.length > 20000) {
            status.text = "CSS exceeds 20 KB limit"
            return
        }

        val quoted = JSONObject.quote(css)

        activeWeb?.evaluateJavascript(
            """
            (function(){
                try {
                    const el = window.__dbSelected;
                    if (!el) return "No selected element";

                    el.style.cssText = $quoted;

                    if (window.__dbSend) {
                        window.__dbSend(el);
                    }

                    return "CSS applied";
                } catch(e) {
                    return "CSS error: " + e.message;
                }
            })();
            """.trimIndent()
        ) { result ->
            status.text = decodeJsResult(result)
            cssDirty = false
        }
    }

    // --------------------------------------------------
    // CONSOLE
    // --------------------------------------------------

    private fun renderConsole() {
        panelBody.addView(
            makeLabel("CONSOLE / JAVASCRIPT", 11f, accent)
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
            LinearLayout.LayoutParams(-1, dp(150))
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

        panelBody.addView(
            makeButton("▶ Run JavaScript") {
                val source = command.text.toString()

                if (source.isNotBlank()) {
                    runJavaScript(source)
                }
            }
        )

        panelBody.addView(
            makeButton("Clear console") {
                consoleHistory.clear()
                consoleOutput?.text = "Console cleared."
            }
        )
    }

    private fun runJavaScript(source: String) {
        val tab = activeTab ?: run {
            appendConsole("No active webpage.")
            return
        }

        if (source.length > 30000) {
            appendConsole("Command exceeds 30 KB limit.")
            return
        }

        val commandId = nextCommandId++
        val generation = tab.pageGeneration

        pendingCommands[commandId] = PendingCommand(
            tab.id,
            generation
        )

        appendConsole("> $source")

        val quoted = JSONObject.quote(source)

        val script = """
            (function(){
                const commandId = $commandId;

                function send(value, isError) {
                    let output;

                    try {
                        if (value === undefined) {
                            output = "undefined";
                        } else if (typeof value === "string") {
                            output = value;
                        } else {
                            output = JSON.stringify(value, null, 2);
                            if (output === undefined) {
                                output = String(value);
                            }
                        }
                    } catch(e) {
                        output = String(value);
                    }

                    try {
                        DevBrowserBridge.postConsoleResult(
                            commandId,
                            String(output).slice(0, 30000),
                            !!isError
                        );
                    } catch(e) {}
                }

                try {
                    const result = (0, eval)($quoted);

                    if (result &&
                        typeof result.then === "function") {

                        result.then(
                            value => send(value, false),
                            error => send(
                                error && (error.stack || error.message)
                                    || error,
                                true
                            )
                        );
                    } else {
                        send(result, false);
                    }
                } catch(e) {
                    send(e && (e.stack || e.message) || e, true);
                }
            })();
        """.trimIndent()

        tab.web.evaluateJavascript(script, null)

        handler.postDelayed({
            val pending = pendingCommands.remove(commandId)
            if (pending != null &&
                pending.tabId == activeTabId
            ) {
                appendConsole("[TIMEOUT] No result received.")
            }
        }, 15000)
    }

    private fun appendConsole(line: String) {
        runOnUiThread {
            consoleHistory.add(line.take(4000))

            while (consoleHistory.size > 200) {
                consoleHistory.removeAt(0)
            }

            consoleOutput?.text = consoleHistory.joinToString("\n")
        }
    }

    private fun decodeJsResult(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") {
            return "undefined"
        }

        return try {
            val value = org.json.JSONTokener(raw).nextValue()
            value?.toString() ?: "undefined"
        } catch (_: Exception) {
            raw
        }
    }

    // --------------------------------------------------
    // JAVASCRIPT BRIDGE
    // --------------------------------------------------

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
                    handleInspectorData(
                        JSONObject(payload)
                    )
                } catch (_: Exception) {
                    status.text = "Inspector data error"
                }
            }
        }

        @JavascriptInterface
        fun postConsoleResult(
            commandId: Int,
            result: String,
            isError: Boolean
        ) {
            if (result.length > 30000) return

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                val pending = pendingCommands.remove(commandId)
                    ?: return@runOnUiThread

                if (pending.tabId != tabId ||
                    pending.tabId != activeTabId
                ) return@runOnUiThread

                val tab = tabs.firstOrNull {
                    it.id == pending.tabId
                } ?: return@runOnUiThread

                if (tab.pageGeneration != pending.generation) {
                    return@runOnUiThread
                }

                appendConsole(
                    (if (isError) "ERROR: " else "") +
                        result.take(30000)
                )
            }
        }
    }

    // --------------------------------------------------
    // INSPECTOR JAVASCRIPT
    // --------------------------------------------------

    private fun installInspector(tab: BrowserTab) {
        if (!tabs.any { it.id == tab.id }) return

        val inspect = tab.inspect

        val script = """
        (function() {
            if (!document.body) return;

            if (window.__dbInstalled) {
                window.__dbInspect = $inspect;

                if (window.__dbSend) {
                    window.__dbSend(
                        window.__dbSelected || document.body
                    );
                }
                return;
            }

            window.__dbInstalled = true;
            window.__dbInspect = $inspect;
            window.__dbSelected = null;
            window.__dbNodeById = new Map();
            window.__dbWeakIds = new WeakMap();
            window.__dbNextNodeId = 1;
            window.__dbPrevious = null;
            window.__dbOldOutline = "";
            window.__dbOldOffset = "";

            function nodeId(el) {
                let id = window.__dbWeakIds.get(el);

                if (!id) {
                    id = window.__dbNextNodeId++;
                    window.__dbWeakIds.set(el, id);
                }

                window.__dbNodeById.set(id, el);
                return id;
            }

            function selector(el) {
                if (!el || !el.tagName) return "";

                let value = el.tagName.toLowerCase();

                if (el.id) value += "#" + el.id;

                if (el.classList && el.classList.length) {
                    value += "." +
                        Array.from(el.classList)
                            .slice(0, 3)
                            .join(".");
                }

                return value;
            }

            function getTree() {
                const output = [];
                let count = 0;

                window.__dbNodeById.clear();

                function walk(node, depth) {
                    if (!node ||
                        node.nodeType !== 1 ||
                        count >= 160 ||
                        depth > 6) return;

                    count++;

                    const id = nodeId(node);

                    output.push({
                        id: id,
                        label: selector(node),
                        depth: depth,
                        selected: node === window.__dbSelected
                    });

                    const children = Array.from(node.children);

                    for (const child of children) {
                        if (count >= 160) break;
                        walk(child, depth + 1);
                    }
                }

                walk(document.body, 0);
                return output;
            }

            window.__dbSend = function(el) {
                if (!el || el.nodeType !== 1) return;

                window.__dbSelected = el;

                const computed = getComputedStyle(el);

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

                const css = properties.map(name =>
                    name + ": " +
                    computed.getPropertyValue(name) + ";"
                ).join("\\n");

                const data = {
                    selector: selector(el),
                    html: (el.outerHTML || "").slice(0, 12000),
                    inlineCss: el.getAttribute("style") || "",
                    computedCss: css,
                    boxModel: {
                        margin: computed.margin,
                        border: computed.borderWidth,
                        padding: computed.padding,
                        content: computed.width + " × " +
                            computed.height
                    },
                    treeNodes: getTree()
                };

                try {
                    DevBrowserBridge.postData(
                        JSON.stringify(data)
                    );
                } catch(e) {}
            };

            window.__dbSelect = function(el) {
                if (!el || el.nodeType !== 1) return;

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

                let target = event.target;

                if (target && target.nodeType === 3) {
                    target = target.parentElement;
                }

                if (target && target.nodeType === 1) {
                    window.__dbSelect(target);
                }
            }, true);

            if (window.MutationObserver) {
                let queued = false;

                const observer = new MutationObserver(function() {
                    if (queued) return;
                    queued = true;

                    setTimeout(function() {
                        queued = false;

                        if (!document.body) return;

                        if (window.__dbSelected &&
                            !document.contains(window.__dbSelected)) {
                            window.__dbSelected = document.body;
                        }

                        window.__dbSend(
                            window.__dbSelected || document.body
                        );
                    }, 300);
                });

                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
            }

            window.__dbSend(document.body);
        })();
        """.trimIndent()

        tab.web.evaluateJavascript(script, null)
    }

    private fun handleInspectorData(data: JSONObject) {
        domNodes = data.optJSONArray("treeNodes")
            ?: JSONArray()

        selectedSelector = data.optString("selector", "")

        if (!htmlDirty) {
            selectedHtml = data.optString("html", "")
        }

        if (!cssDirty) {
            selectedCss = data.optString("inlineCss", "")
        }

        selectedComputed = data.optString("computedCss", "")

        selectedBox = try {
            data.optJSONObject("boxModel") ?: JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }

        status.text = if (selectedSelector.isNotBlank()) {
            "Selected: $selectedSelector"
        } else {
            "DOM loaded"
        }

        if (panelVisible && currentDevTab != "Console") {
            val focused = devPanel.findFocus() is EditText

            if (!focused) {
                renderPanel()
            }
        }
    }

    private fun clearSelection() {
        selectedHtml = ""
        selectedCss = ""
        selectedComputed = ""
        selectedSelector = ""
        selectedBox = JSONObject()
        domNodes = JSONArray()
        htmlDirty = false
        cssDirty = false
    }

    private fun setInspectMode(enabled: Boolean) {
        val tab = activeTab ?: return

        tab.inspect = enabled

        tab.web.evaluateJavascript(
            "window.__dbInspect = $enabled;",
            null
        )

        status.text = if (enabled) {
            "PICK MODE ON — tap a webpage element"
        } else {
            "Inspect mode OFF"
        }

        if (panelVisible && currentDevTab == "Elements") {
            renderPanel()
        }
    }

    // --------------------------------------------------
    // TUTORIAL
    // --------------------------------------------------

    private fun showTutorial() {
        val steps = arrayOf(
            "Welcome to DevBrowser!\n\nA browser with built-in developer tools.",
            "ADDRESS BAR\n\nEnter a website or search term in the address bar.",
            "BROWSER TABS\n\nUse + to open tabs. Tap a tab to switch between pages.",
            "INSPECT ELEMENT\n\nOpen the menu and choose Inspect Element. Then tap an element on the page.",
            "ELEMENTS\n\nExplore the DOM tree and edit selected HTML.",
            "STYLES\n\nInspect computed CSS and edit inline styles.",
            "CONSOLE\n\nEnter JavaScript and run it on the current webpage.",
            "RESIZE DEVTOOLS\n\nDrag the handle above the DevTools tabs to resize the panel."
        )

        var index = 0

        fun nextStep() {
            AlertDialog.Builder(this)
                .setTitle("DevBrowser · ${index + 1}/${steps.size}")
                .setMessage(steps[index])
                .setCancelable(false)
                .setNegativeButton(
                    if (index == 0) "Skip" else "Close"
                ) { _, _ ->
                    prefs.edit()
                        .putBoolean("first_launch", false)
                        .apply()
                }
                .setPositiveButton(
                    if (index == steps.lastIndex) "Finish" else "Next"
                ) { _, _ ->
                    if (index < steps.lastIndex) {
                        index++
                        nextStep()
                    } else {
                        prefs.edit()
                            .putBoolean("first_launch", false)
                            .apply()

                        showDevTools("Elements")
                    }
                }
                .show()
        }

        nextStep()
    }

    // --------------------------------------------------
    // LIFECYCLE
    // --------------------------------------------------

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
        handler.removeCallbacksAndMessages(null)
        pendingCommands.clear()

        tabs.forEach { tab ->
            try {
                (tab.web.parent as? ViewGroup)
                    ?.removeView(tab.web)

                tab.web.stopLoading()
                tab.web.removeJavascriptInterface(
                    "DevBrowserBridge"
                )
                tab.web.destroy()
            } catch (_: Exception) {
            }
        }

        tabs.clear()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        when {
            panelVisible -> closeDevTools()

            activeWeb?.canGoBack() == true ->
                activeWeb?.goBack()

            tabs.size > 1 ->
                closeTab(activeTabId)

            else ->
                super.onBackPressed()
        }
    }

    // --------------------------------------------------
    // TEXT WATCHER
    // --------------------------------------------------

    private class SimpleTextWatcher(
        private val changed: () -> Unit
    ) : android.text.TextWatcher {

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
            changed()
        }

        override fun afterTextChanged(
            s: android.text.Editable?
        ) = Unit
    }
}
