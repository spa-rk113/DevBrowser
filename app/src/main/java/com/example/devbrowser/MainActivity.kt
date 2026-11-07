
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

    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var devPanel: LinearLayout
    private lateinit var panelBody: LinearLayout
    private lateinit var root: LinearLayout

    private val prefs by lazy {
        getSharedPreferences("devbrowser_settings", Context.MODE_PRIVATE)
    }

    private var selectedHtml = ""
    private var selectedInlineCss = ""
    private var selectedComputedCss = ""
    private var selectedSelector = ""
    private var domNodes = JSONArray()
    private var currentTab = "Elements"
    private var panelHeight = 340
    private var inspectEnabled = false
    private var consoleOutput: TextView? = null

    private val consoleHistory = mutableListOf<String>()

    private val bg = Color.rgb(24, 26, 31)
    private val panelBg = Color.rgb(32, 35, 41)
    private val textColor = Color.rgb(235, 237, 242)
    private val accent = Color.rgb(255, 167, 38)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildInterface()

        val restored = savedInstanceState?.let {
            webView.restoreState(it) != null
        } ?: false

        if (!restored) {
            val lastUrl = prefs.getString("last_url", null)
            webView.loadUrl(lastUrl ?: "https://www.google.com")
        }

        currentTab = prefs.getString("last_tab", "Elements") ?: "Elements"
        panelHeight = prefs.getInt("panel_height", 340)

        if (prefs.getBoolean("show_tutorial", true)) {
            webView.postDelayed({ showTutorial() }, 900)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun button(
        title: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = title
            textSize = 12f
            isAllCaps = false
            minWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { action() }
        }
    }

    private fun roundedBackground(
        color: Int,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(8).toFloat()
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setBackgroundColor(Color.rgb(242, 244, 248))
        }

        address = EditText(this).apply {
            hint = "Search or enter website"
            isSingleLine = true
            textSize = 14f
            setPadding(dp(10), 0, dp(10), 0)
            setBackground(roundedBackground(Color.WHITE))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ ->
                loadAddress()
                true
            }
        }

        toolbar.addView(
            address,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        toolbar.addView(button("GO") { loadAddress() })
        toolbar.addView(button("⋮") { showMenu() })

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(248, 248, 250))
        }

        navigation.addView(button("←") {
            if (webView.canGoBack()) webView.goBack()
        })

        navigation.addView(button("→") {
            if (webView.canGoForward()) webView.goForward()
        })

        navigation.addView(button("↻") { webView.reload() })

        navigation.addView(button("Home") {
            webView.loadUrl("https://www.google.com")
        })

        status = TextView(this).apply {
            text = "Ready"
            textSize = 11f
            setTextColor(Color.DKGRAY)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            setBackgroundColor(Color.rgb(235, 237, 241))
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    status.text = "Loading website..."
                    selectedHtml = ""
                    selectedInlineCss = ""
                    selectedComputedCss = ""
                    selectedSelector = ""
                    domNodes = JSONArray()
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    address.setText(url ?: "")
                    status.text = "Website loaded"
                    prefs.edit().putString("last_url", url).apply()
                    installInspector()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(
                    message: ConsoleMessage
                ): Boolean {
                    val line = message.message()

                    if (line.startsWith("__DEVBROWSER__")) {
                        try {
                            val data = JSONObject(
                                line.removePrefix("__DEVBROWSER__")
                            )
                            handleInspectorData(data)
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
        }

        devPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            visibility = View.GONE
        }

        root.addView(toolbar)
        root.addView(navigation)
        root.addView(status)

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(
            devPanel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(panelHeight)
            )
        )

        setContentView(root)
    }

    private fun loadAddress() {
        var url = address.text.toString().trim()
        if (url.isEmpty()) return

        if (!url.startsWith("http://") &&
            !url.startsWith("https://")) {
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

        webView.loadUrl(url)
    }

    private fun showMenu() {
        val options = arrayOf(
            "Open DevTools",
            "Inspect Element",
            "Elements",
            "Styles",
            "Console",
            "Close DevTools",
            "Reload Page",
            "Tutorial"
        )

        AlertDialog.Builder(this)
            .setTitle("DevBrowser")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDevTools("Elements")
                    1 -> {
                        showDevTools("Elements")
                        toggleInspect(true)
                    }
                    2 -> showDevTools("Elements")
                    3 -> showDevTools("Styles")
                    4 -> showDevTools("Console")
                    5 -> closeDevTools()
                    6 -> webView.reload()
                    7 -> showTutorial()
                }
            }
            .show()
    }

    private fun showDevTools(tab: String) {
        currentTab = tab
        devPanel.visibility = View.VISIBLE
        renderPanel()
        saveSettings()
    }

    private fun closeDevTools() {
        devPanel.visibility = View.GONE
        toggleInspect(false)
        saveSettings()
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("last_tab", currentTab)
            .putInt("panel_height", panelHeight)
            .apply()
    }

    private fun buildTabs(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(39, 42, 49))

            listOf("Elements", "Styles", "Console").forEach { tab ->
                val item = TextView(this@MainActivity).apply {
                    text = if (tab == currentTab) "● $tab" else tab
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(
                        if (tab == currentTab) accent else Color.LTGRAY
                    )
                    setPadding(dp(13), dp(13), dp(13), dp(13))
                    setOnClickListener {
                        currentTab = tab
                        renderPanel()
                        saveSettings()
                    }
                }
                addView(item)
            }

            val close = TextView(this@MainActivity).apply {
                text = "✕"
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setOnClickListener { closeDevTools() }
            }

            addView(
                close,
                LinearLayout.LayoutParams(0, -1, 1f)
            )
        }
    }

    private fun buildDragHandle(): View {
        return View(this).apply {
            setBackgroundColor(Color.rgb(70, 74, 84))
            minimumHeight = dp(12)

            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> true

                    MotionEvent.ACTION_MOVE -> {
                        val params = devPanel.layoutParams
                            as LinearLayout.LayoutParams

                        val screenDp =
                            (resources.displayMetrics.heightPixels /
                                resources.displayMetrics.density).toInt()

                        val newHeight = min(
                            max(
                                panelHeight - (event.rawY /
                                    resources.displayMetrics.density).toInt() +
                                    (view.tag as? Int ?: 0),
                                190
                            ),
                            max(220, screenDp - 190)
                        )

                        params.height = dp(newHeight)
                        devPanel.layoutParams = params
                        panelHeight = newHeight
                        view.tag = event.rawY.toInt()
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        view.tag = null
                        saveSettings()
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
        devPanel.addView(buildTabs())

        panelBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(panelBg)
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

    private fun label(
        value: String,
        size: Float = 12f
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(textColor)
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun editor(
        value: String,
        hintText: String,
        lines: Int = 5
    ): EditText {
        return EditText(this).apply {
            setText(value)
            hint = hintText
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 12f
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackground(roundedBackground(Color.rgb(43, 46, 53)))
            minLines = lines
        }
    }

    private fun renderElements() {
        panelBody.addView(label("ELEMENTS · DOM TREE"))

        val pickButton = button(
            if (inspectEnabled) "Stop picking" else "Pick Element"
        ) {
            toggleInspect(!inspectEnabled)
            renderPanel()
        }

        panelBody.addView(pickButton)

        panelBody.addView(
            label(
                if (inspectEnabled) {
                    "PICK MODE ON · Tap an element on the webpage"
                } else {
                    "Tap a DOM row or Pick Element on the webpage"
                },
                11f
            )
        )

        val treeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            setBackground(roundedBackground(Color.rgb(25, 27, 32)))
        }

        if (domNodes.length() == 0) {
            treeContainer.addView(
                label("Load a website to inspect its DOM.", 11f)
            )
        } else {
            for (i in 0 until domNodes.length()) {
                val node = domNodes.optJSONObject(i) ?: continue
                val nodeLabel = node.optString("label", "element")
                val path = node.optJSONArray("path") ?: JSONArray()
                val depth = node.optInt("depth", 0)
                val selected = node.optBoolean("selected", false)

                val row = TextView(this).apply {
                    text = (if (selected) "▶ " else "  ") + nodeLabel
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(
                        if (selected) accent else textColor
                    )
                    setPadding(
                        dp(6 + depth * 12),
                        dp(6),
                        dp(6),
                        dp(6)
                    )
                    isClickable = true

                    if (selected) {
                        setBackground(
                            roundedBackground(Color.rgb(66, 49, 30))
                        )
                    }

                    setOnClickListener {
                        val pathJson = path.toString()

                        webView.evaluateJavascript(
                            """
                            (function(){
                                let el = document.body;
                                const path = $pathJson;

                                for (const index of path) {
                                    if (!el || !el.children[index]) return;
                                    el = el.children[index];
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

                treeContainer.addView(row)
            }
        }

        val treeScroll = ScrollView(this)
        treeScroll.addView(treeContainer)

        panelBody.addView(
            treeScroll,
            LinearLayout.LayoutParams(-1, dp(155))
        )

        panelBody.addView(label("SELECTED ELEMENT · HTML"))

        val htmlInput = editor(
            selectedHtml,
            "Pick an element to load its HTML.",
            5
        )

        panelBody.addView(
            htmlInput,
            LinearLayout.LayoutParams(-1, dp(115))
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        actions.addView(button("Apply HTML") {
            if (selectedHtml.isEmpty()) {
                status.text = "Select an element first"
            } else {
                val quoted = JSONObject.quote(
                    htmlInput.text.toString()
                )

                webView.evaluateJavascript(
                    """
                    (function(){
                        const old = window.__devSelected;
                        if (!old) return;

                        const box = document.createElement('div');
                        box.innerHTML = $quoted;

                        if (box.children.length === 1) {
                            const next = box.firstElementChild;
                            old.replaceWith(next);
                            window.__devHighlight(next);
                            window.__devSendSelection(next);
                        } else {
                            old.innerHTML = box.innerHTML;
                            window.__devSendSelection(old);
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        })

        actions.addView(button("Refresh DOM") {
            webView.evaluateJavascript(
                "window.__devSendSelection(window.__devSelected || document.body);",
                null
            )
        })

        panelBody.addView(actions)
    }

    private fun renderStyles() {
        panelBody.addView(label("STYLES · CSS INSPECTOR"))

        panelBody.addView(
            label(
                selectedSelector.ifEmpty {
                    "Select an element to inspect its styles."
                },
                12f
            )
        )

        panelBody.addView(label("CSS BOX MODEL"))

        val boxModel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackground(roundedBackground(Color.rgb(25, 27, 32)))
        }

        val boxData = try {
            JSONObject(
                webViewBoxModel.ifEmpty { "{}" }
            )
        } catch (_: Exception) {
            JSONObject()
        }

        val margin = boxData.optString("margin", "—")
        val border = boxData.optString("border", "—")
        val padding = boxData.optString("padding", "—")
        val content = boxData.optString("content", "—")

        boxModel.addView(
            boxLayer("MARGIN", margin, Color.rgb(100, 75, 32))
        )
        boxModel.addView(
            boxLayer("BORDER", border, Color.rgb(130, 85, 35))
        )
        boxModel.addView(
            boxLayer("PADDING", padding, Color.rgb(55, 95, 65))
        )
        boxModel.addView(
            boxLayer("CONTENT", content, Color.rgb(45, 75, 125))
        )

        panelBody.addView(boxModel)

        panelBody.addView(label("COMPUTED CSS"))

        val computed = label(
            selectedComputedCss.ifEmpty {
                "No element selected."
            },
            11f
        ).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackground(roundedBackground(Color.rgb(25, 27, 32)))
        }

        panelBody.addView(
            computed,
            LinearLayout.LayoutParams(-1, dp(125))
        )

        panelBody.addView(label("INLINE CSS · EDITABLE"))

        val cssInput = editor(
            selectedInlineCss,
            "Select an element to load its CSS.",
            4
        )

        panelBody.addView(
            cssInput,
            LinearLayout.LayoutParams(-1, dp(100))
        )

        panelBody.addView(button("Apply CSS") {
            if (selectedSelector.isEmpty()) {
                status.text = "Select an element first"
            } else {
                val css = JSONObject.quote(
                    cssInput.text.toString()
                )

                webView.evaluateJavascript(
                    """
                    (function(){
                        if (window.__devSelected) {
                            window.__devSelected.style.cssText = $css;
                            window.__devSendSelection(window.__devSelected);
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        })
    }

    private var webViewBoxModel = ""

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
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setBackground(roundedBackground(color))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(3), 0, dp(3))
            }
        }
    }

    private fun renderConsole() {
        panelBody.addView(label("CONSOLE · JavaScript"))

        consoleOutput = label(
            consoleHistory.joinToString("\n")
                .ifEmpty { "DevBrowser console ready." },
            11f
        ).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackground(roundedBackground(Color.rgb(18, 20, 24)))
        }

        panelBody.addView(
            consoleOutput,
            LinearLayout.LayoutParams(-1, dp(145))
        )

        val command = editor(
            "",
            "Enter JavaScript and tap Run...",
            2
        )

        panelBody.addView(
            command,
            LinearLayout.LayoutParams(-1, dp(75))
        )

        panelBody.addView(button("▶ Run JavaScript") {
            val source = command.text.toString()

            if (source.isNotBlank()) {
                appendConsole("> $source")

                val quoted = JSONObject.quote(source)

                webView.evaluateJavascript(
                    """
                    (function(){
                        try {
                            let value = (0, eval)($quoted);

                            if (value === undefined) return "undefined";

                            if (typeof value === "object" && value !== null) {
                                return JSON.stringify(value, null, 2);
                            }

                            return String(value);
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

            if (consoleHistory.size > 150) {
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
            webViewBoxModel = data.optString("boxModel", "")

            status.text = if (selectedSelector.isNotEmpty()) {
                "Selected: $selectedSelector"
            } else {
                "Website loaded"
            }

            if (devPanel.visibility == View.VISIBLE &&
                (currentTab == "Elements" || currentTab == "Styles")) {
                renderPanel()
            }
        }
    }

    private fun installInspector() {
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
            window.__devPreviousOutlineOutline = "";

            window.__devHighlight = function(el) {
                if (!el) return;

                if (window.__devPreviousOutline) {
                    window.__devPreviousOutline.style.outline =
                        window.__devPreviousOutlineOutline || "";
                }

                window.__devPreviousOutline = el;
                window.__devPreviousOutlineOutline =
                    el.style.outline || "";

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
                let nodes = [];
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

        webView.evaluateJavascript(script, null)
    }

    private fun toggleInspect(enabled: Boolean) {
        inspectEnabled = enabled

        webView.evaluateJavascript(
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
            "Welcome to DevBrowser!\n\nThis browser includes developer tools for inspecting and testing webpages.",
            "1. Open a website\n\nUse the address bar at the top to open a website or search the web.",
            "2. Inspect Element\n\nOpen the ⋮ menu, choose Inspect Element, then tap an element on the webpage. It will be outlined in orange.",
            "3. Elements tab\n\nThe DOM tree shows webpage elements. Tap a row to select it. You can edit the selected HTML.",
            "4. Styles tab\n\nSee computed CSS, the visual Box Model, and edit inline CSS.",
            "5. Console tab\n\nEnter JavaScript and press Run JavaScript to test code on the currently open page.",
            "6. Resize DevTools\n\nDrag the small handle above the DevTools tabs to change the panel height.\n\nYour last opened page is saved for the next launch."
        )

        var index = 0

        fun showStep() {
            val builder = AlertDialog.Builder(this)
                .setTitle("DevBrowser Tutorial")
                .setMessage(steps[index])
                .setCancelable(false)

            if (index == 2) {
                builder.setNeutralButton("Try Inspect") { _, _ ->
                    showDevTools("Elements")
                    toggleInspect(true)
                }
            }

            builder.setNegativeButton(
                if (index == 0) "Skip" else "Close"
            ) { _, _ ->
                prefs.edit().putBoolean("show_tutorial", false).apply()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (::webView.isInitialized) {
            webView.saveState(outState)
        }

        saveSettings()
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            val currentUrl = webView.url
            if (!currentUrl.isNullOrBlank()) {
                prefs.edit().putString("last_url", currentUrl).apply()
            }
        }

        saveSettings()
        super.onPause()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }

        super.onDestroy()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
