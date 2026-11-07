
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
import kotlin.math.min

class MainActivity : Activity() {

    private data class BrowserTab(
        val id: Int,
        val web: WebView,
        var title: String,
        var url: String
    )

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId = 0
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

    private var currentTab = "Elements"
    private var panelHeight = 310
    private var inspectEnabled = false

    private var selectedHtml = ""
    private var selectedInlineCss = ""
    private var selectedComputedCss = ""
    private var selectedSelector = ""
    private var boxModelJson = "{}"

    private var domNodes = JSONArray()
    private val consoleHistory = mutableListOf<String>()
    private var consoleOutput: TextView? = null

    private val bg = Color.rgb(17, 20, 27)
    private val surface = Color.rgb(27, 32, 42)
    private val surface2 = Color.rgb(36, 42, 54)
    private val accent = Color.rgb(69, 153, 255)
    private val orange = Color.rgb(255, 167, 65)
    private val textColor = Color.rgb(235, 239, 247)
    private val muted = Color.rgb(155, 166, 184)

    private val activeWeb: WebView?
        get() = tabs.firstOrNull { it.id == activeTabId }?.web

    private val activeBrowserTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        panelHeight = prefs.getInt("panel_height", 310)
        currentTab = prefs.getString("last_dev_tab", "Elements") ?: "Elements"

        buildInterface()

        val saved = prefs.getString("saved_tabs", null)
        var restoredAny = false

        if (saved != null) {
            try {
                val array = JSONArray(saved)
                for (i in 0 until array.length()) {
                    val url = array.optString(i)
                    if (url.isNotBlank()) {
                        createTab(url, true)
                        restoredAny = true
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (!restoredAny) {
            createTab("https://www.google.com", false)
        }

        val savedIndex = prefs.getInt("active_tab_index", 0)
        if (tabs.isNotEmpty()) {
            val index = savedIndex.coerceIn(0, tabs.lastIndex)
            switchTab(tabs[index].id)
        }

        if (prefs.getBoolean("show_tutorial", true)) {
            root.postDelayed({ showTutorial() }, 900)
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

    private fun makeButton(
        title: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = title
            textSize = 12f
            isAllCaps = false
            minWidth = 0
            minHeight = dp(40)
            setPadding(dp(9), 0, dp(9), 0)
            setTextColor(textColor)
            background = rounded(surface2)
            stateListAnimator = null
            setOnClickListener { action() }
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
        }
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(bg)
        }

        address = EditText(this).apply {
            hint = "Search or enter website"
            textSize = 13f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(textColor)
            setHintTextColor(muted)
            setPadding(dp(13), 0, dp(10), 0)
            background = rounded(surface2, Color.rgb(62, 75, 96), 22)

            setOnEditorActionListener { _, _, _ ->
                loadAddress()
                true
            }
        }

        toolbar.addView(
            address,
            LinearLayout.LayoutParams(0, dp(43), 1f)
        )

        toolbar.addView(makeButton("GO") { loadAddress() })
        toolbar.addView(makeButton("⋮") { showMenu() })

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

    private fun createTab(
        url: String,
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

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.loadsImagesAutomatically = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false

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
                renderBrowserTabs()
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
                    renderBrowserTabs()
                }
            }

            override fun onConsoleMessage(
                message: ConsoleMessage
            ): Boolean {
                val line = message.message()

                if (line.startsWith("__DEVBROWSER__")) {
                    try {
                        handleInspectorData(
                            JSONObject(
                                line.removePrefix("__DEVBROWSER__")
                            )
                        )
                    } catch (_: Exception) {
                        appendConsole("Inspector data error")
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
        status.text = "Ready"

        renderBrowserTabs()
        saveSession()
    }

    private fun closeTab(id: Int) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index == -1) return

        val item = tabs[index]

        webContainer.removeView(item.web)
        item.web.stopLoading()
        item.web.destroy()
        tabs.removeAt(index)

        if (tabs.isEmpty()) {
            createTab("https://www.google.com")
        } else {
            val nextIndex = (index - 1).coerceAtLeast(0)
            switchTab(tabs[nextIndex].id)
        }

        saveSession()
    }

    private fun renderBrowserTabs() {
        tabStrip.removeAllViews()

        tabs.forEach { item ->
            val selected = item.id == activeTabId

            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(5), dp(5), dp(5))
                background = rounded(
                    if (selected) Color.rgb(48, 65, 91) else surface2,
                    if (selected) accent else Color.TRANSPARENT,
                    10
                )
            }

            val title = TextView(this).apply {
                text = item.title.take(13)
                textSize = 11f
                setTextColor(if (selected) Color.WHITE else muted)
                maxWidth = dp(110)
                setOnClickListener { switchTab(item.id) }
            }

            val close = TextView(this).apply {
                text = " × "
                textSize = 15f
                setTextColor(muted)
                setPadding(dp(5), 0, dp(3), 0)
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

        tabStrip.addView(makeButton("+") {
            createTab("https://www.google.com")
        })
    }

    private fun saveSession() {
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
            .putString("last_dev_tab", currentTab)
            .putInt("panel_height", panelHeight)
            .apply()
    }

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

        val keyboard = getSystemService(INPUT_METHOD_SERVICE)
            as InputMethodManager

        keyboard.hideSoftInputFromWindow(address.windowToken, 0)

        activeWeb?.loadUrl(url)
    }

    private fun showMenu() {
        val options = arrayOf(
            "New tab",
            "Open DevTools",
            "Inspect Element",
            "Elements",
            "Styles",
            "Console",
            "Close DevTools",
            "Reload",
            "Tutorial"
        )

        AlertDialog.Builder(this)
            .setTitle("DevBrowser")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> createTab("https://www.google.com")
                    1 -> showDevTools("Elements")
                    2 -> {
                        showDevTools("Elements")
                        toggleInspect(true)
                    }
                    3 -> showDevTools("Elements")
                    4 -> showDevTools("Styles")
                    5 -> showDevTools("Console")
                    6 -> closeDevTools()
                    7 -> activeWeb?.reload()
                    8 -> showTutorial()
                }
            }
            .show()
    }

    private fun showDevTools(tab: String) {
        currentTab = tab
        devPanel.visibility = View.VISIBLE
        renderPanel()
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
            setBackgroundColor(Color.rgb(73, 83, 101))
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
                            .coerceIn(190, max(190, screenDp - 180))

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

        val tabsBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(surface)
        }

        listOf("Elements", "Styles", "Console").forEach { tab ->
            val item = TextView(this).apply {
                text = if (tab == currentTab) "● $tab" else tab
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (tab == currentTab) accent else muted)
                setPadding(dp(13), dp(13), dp(13), dp(13))

                setOnClickListener {
                    currentTab = tab
                    renderPanel()
                    saveSession()
                }
            }

            tabsBar.addView(item)
        }

        val close = TextView(this).apply {
            text = "✕"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(10), dp(8), dp(12), dp(8))
            setOnClickListener { closeDevTools() }
        }

        tabsBar.addView(
            close,
            LinearLayout.LayoutParams(0, -1, 1f)
        )

        devPanel.addView(tabsBar)

        panelBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(9), dp(11), dp(12))
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        scroll.addView(panelBody)

        devPanel.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        when (currentTab) {
            "Elements" -> renderElements()
            "Styles" -> renderStyles()
            "Console" -> renderConsole()
        }
    }

    private fun renderElements() {
        panelBody.addView(
            makeLabel("ELEMENTS  /  DOM INSPECTOR", 11f, accent)
        )

        panelBody.addView(makeButton(
            if (inspectEnabled) "Stop picking" else "⌖  Pick Element"
        ) {
            toggleInspect(!inspectEnabled)
            renderPanel()
        })

        panelBody.addView(
            makeLabel(
                if (inspectEnabled) {
                    "Pick mode active — tap an element on the webpage."
                } else {
                    "Select a DOM row or use Pick Element."
                },
                10f,
                muted
            )
        )

        val tree = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = rounded(surface)
        }

        if (domNodes.length() == 0) {
            tree.addView(makeLabel("No DOM data yet. Load a website.", 11f))
        } else {
            for (i in 0 until domNodes.length()) {
                val node = domNodes.optJSONObject(i) ?: continue
                val path = node.optJSONArray("path") ?: JSONArray()
                val depth = node.optInt("depth", 0)
                val selected = node.optBoolean("selected", false)

                val row = TextView(this).apply {
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
                        background = rounded(
                            Color.rgb(68, 48, 29)
                        )
                    }

                    setOnClickListener {
                        val pathJson = path.toString()

                        activeWeb?.evaluateJavascript(
                            """
                            (function(){
                                let el = document.body;
                                const path = $pathJson;

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
                }

                tree.addView(row)
            }
        }

        val treeScroll = ScrollView(this)
        treeScroll.addView(tree)

        panelBody.addView(
            treeScroll,
            LinearLayout.LayoutParams(-1, dp(150))
        )

        panelBody.addView(makeLabel("SELECTED HTML", 11f, accent))

        val htmlInput = makeEditor(
            selectedHtml,
            "Select an element to load its HTML.",
            5
        )

        panelBody.addView(
            htmlInput,
            LinearLayout.LayoutParams(-1, dp(115))
        )

        panelBody.addView(makeButton("Apply HTML") {
            if (selectedSelector.isBlank()) {
                status.text = "Select an element first"
            } else {
                val html = JSONObject.quote(
                    htmlInput.text.toString()
                )

                activeWeb?.evaluateJavascript(
                    """
                    (function(){
                        const el = window.__devSelected;
                        if (!el) return;

                        el.innerHTML = $html;
                        window.__devHighlight(el);
                        window.__devSendSelection(el);
                    })();
                    """.trimIndent(),
                    null
                )
            }
        })

        panelBody.addView(makeButton("Refresh DOM") {
            activeWeb?.evaluateJavascript(
                "window.__devSendSelection(window.__devSelected || document.body);",
                null
            )
        })
    }

    private fun renderStyles() {
        panelBody.addView(
            makeLabel("STYLES  /  CSS INSPECTOR", 11f, accent)
        )

        panelBody.addView(
            makeLabel(
                selectedSelector.ifBlank {
                    "Select an element to inspect its styles."
                },
                12f
            )
        )

        panelBody.addView(makeLabel("BOX MODEL", 11f, accent))

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
                Color.rgb(107, 77, 37))
        )

        boxView.addView(
            boxLayer("BORDER", box.optString("border", "—"),
                Color.rgb(137, 89, 39))
        )

        boxView.addView(
            boxLayer("PADDING", box.optString("padding", "—"),
                Color.rgb(48, 91, 64))
        )

        boxView.addView(
            boxLayer("CONTENT", box.optString("content", "—"),
                Color.rgb(42, 75, 124))
        )

        panelBody.addView(boxView)

        panelBody.addView(makeLabel("COMPUTED CSS", 11f, accent))

        val computed = makeLabel(
            selectedComputedCss.ifBlank { "No element selected." },
            11f
        ).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = rounded(surface)
        }

        panelBody.addView(
            computed,
            LinearLayout.LayoutParams(-1, dp(125))
        )

        panelBody.addView(makeLabel("INLINE CSS · EDITABLE", 11f, accent))

        val cssInput = makeEditor(
            selectedInlineCss,
            "Select an element to load its CSS.",
            4
        )

        panelBody.addView(
            cssInput,
            LinearLayout.LayoutParams(-1, dp(100))
        )

        panelBody.addView(makeButton("Apply CSS") {
            if (selectedSelector.isBlank()) {
                status.text = "Select an element first"
            } else {
                val css = JSONObject.quote(cssInput.text.toString())

                activeWeb?.evaluateJavascript(
                    """
                    (function(){
                        const el = window.__devSelected;
                        if (!el) return;

                        el.style.cssText = $css;
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
            background = rounded(Color.rgb(12, 15, 20))
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

        panelBody.addView(makeButton("Clear console") {
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
                "Page loaded"
            }

            if (devPanel.visibility == View.VISIBLE &&
                (currentTab == "Elements" || currentTab == "Styles")) {
                renderPanel()
            }
        }
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
            "PICK MODE ON — tap an element"
        } else {
            "Inspect mode OFF"
        }
    }

    private fun showTutorial() {
        val steps = arrayOf(
            "Welcome to DevBrowser!\n\nA browser with built-in webpage developer tools.",
            "ADDRESS BAR\n\nEnter a website or search term in the top address bar.",
            "BROWSER TABS\n\nUse + to open a new tab. Tap a tab to switch or × to close it.",
            "INSPECT ELEMENT\n\nOpen the menu and choose Inspect Element. Tap an element on the webpage.",
            "ELEMENTS\n\nExplore the DOM tree and edit selected HTML.",
            "STYLES\n\nInspect computed CSS, view the Box Model and edit inline CSS.",
            "CONSOLE\n\nEnter JavaScript and run it on the current webpage.",
            "RESIZE DEVTOOLS\n\nDrag the handle above the DevTools tabs to resize the panel.",
            "SESSION\n\nYour open tab URLs are saved and restored when the app starts again."
        )

        var index = 0

        fun showStep() {
            val builder = AlertDialog.Builder(this)
                .setTitle("DevBrowser Tutorial · ${index + 1}/${steps.size}")
                .setMessage(steps[index])
                .setCancelable(false)

            if (index == 3) {
                builder.setNeutralButton("Try Inspect") { _, _ ->
                    showDevTools("Elements")
                    toggleInspect(true)
                }
            }

            builder.setNegativeButton(
                if (index == 0) "Skip" else "Close"
            ) { _, _ ->
                prefs.edit()
                    .putBoolean("show_tutorial", false)
                    .apply()
            }

            builder.setPositiveButton(
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

            builder.show()
        }

        showStep()
    }

    override fun onPause() {
        saveSession()
        super.onPause()
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

        if (devPanel.visibility == View.VISIBLE) {
            closeDevTools()
        } else if (web?.canGoBack() == true) {
            web.goBack()
        } else if (tabs.size > 1) {
            closeTab(activeTabId)
        } else {
            super.onBackPressed()
        }
    }
}
