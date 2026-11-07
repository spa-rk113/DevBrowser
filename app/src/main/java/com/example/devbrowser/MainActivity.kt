
package com.example.devbrowser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var devPanel: LinearLayout
    private lateinit var panelBody: LinearLayout

    private var selectedHtml = ""
    private var selectedInlineCss = ""
    private var selectedComputedCss = ""
    private var domTree = "Load a webpage to inspect its DOM."
    private var domNodes = JSONArray()
    private var currentTab = "Elements"

    private val consoleHistory = mutableListOf<String>()
    private var consoleOutput: TextView? = null

    private val bg = Color.rgb(25, 27, 31)
    private val panelBg = Color.rgb(32, 34, 39)
    private val textColor = Color.rgb(230, 230, 235)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 5, 6, 5)
            setBackgroundColor(Color.rgb(245, 245, 250))
        }

        address = EditText(this).apply {
            hint = "Search or enter website"
            isSingleLine = true
            textSize = 14f
            setPadding(10, 0, 10, 0)
            setBackgroundColor(Color.WHITE)
        }

        toolbar.addView(address, LinearLayout.LayoutParams(0, dp(46), 1f))
        toolbar.addView(button("GO") { loadAddress() })
        toolbar.addView(button("⋮") { showMenu() })

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
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
            setPadding(8, 5, 8, 5)
            setBackgroundColor(Color.rgb(240, 240, 240))
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
                    status.text = "Loading..."
                    selectedHtml = ""
                    selectedInlineCss = ""
                    selectedComputedCss = ""
                    domNodes = JSONArray()
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    address.setText(url ?: "")
                    status.text = "Loaded"
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
                dp(340)
            )
        )

        setContentView(root)
        webView.loadUrl("https://www.google.com")
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun button(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setOnClickListener { action() }
        }
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

        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)

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
            "Reload Page"
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
                    5 -> {
                        devPanel.visibility = View.GONE
                        toggleInspect(false)
                    }
                    6 -> webView.reload()
                }
            }
            .show()
    }

    private fun showDevTools(tab: String) {
        currentTab = tab
        devPanel.visibility = View.VISIBLE
        renderPanel()
    }

    private fun buildTabs(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(39, 41, 47))

            listOf("Elements", "Styles", "Console").forEach { tab ->
                val item = TextView(this@MainActivity).apply {
                    text = if (tab == currentTab) "● $tab" else tab
                    textSize = 13f
                    setTextColor(
                        if (tab == currentTab) Color.WHITE else Color.LTGRAY
                    )
                    setPadding(dp(14), dp(12), dp(14), dp(12))

                    setOnClickListener {
                        currentTab = tab
                        renderPanel()
                    }
                }
                addView(item)
            }

            val close = TextView(this@MainActivity).apply {
                text = "✕"
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setOnClickListener {
                    devPanel.visibility = View.GONE
                    toggleInspect(false)
                }
            }

            addView(
                close,
                LinearLayout.LayoutParams(0, -1, 1f)
            )
        }
    }

    private fun renderPanel() {
        devPanel.removeAllViews()
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

    private fun label(value: String, size: Float = 12f): TextView {
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
            setBackgroundColor(Color.rgb(43, 46, 53))
            minLines = lines
        }
    }

    private fun renderElements() {
        panelBody.addView(label("DOM TREE · Click any element"))

        val treeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(27, 29, 34))
        }

        if (domNodes.length() == 0) {
            treeContainer.addView(label(domTree, 11f))
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
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(
                        if (selected) Color.rgb(255, 183, 77)
                        else textColor
                    )
                    setPadding(dp(6 + depth * 14), dp(5), dp(6), dp(5))
                    isClickable = true

                    setOnClickListener {
                        webView.evaluateJavascript(
                            """
                            (function(){
                                let el = document.body;
                                const path = $path;
                                for (const index of path) {
                                    if (!el || !el.children[index]) return;
                                    el = el.children[index];
                                }
                                if (el) {
                                    if (window.__devPreviousOutline) {
                                        window.__devPreviousOutline.style.outline =
                                            window.__devPreviousOutlineOutline || "";
                                    }
                                    window.__devPreviousOutline = el;
                                    window.__devPreviousOutlineOutline =
                                        el.style.outline || "";
                                    el.style.outline = "2px solid #ff5722";
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
            LinearLayout.LayoutParams(-1, dp(150))
        )

        panelBody.addView(label("SELECTED ELEMENT · HTML"))

        val htmlInput = editor(
            selectedHtml,
            "Pick an element to inspect its HTML.",
            5
        )

        panelBody.addView(
            htmlInput,
            LinearLayout.LayoutParams(-1, dp(130))
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        actions.addView(button("Apply HTML") {
            if (selectedHtml.isEmpty()) {
                status.text = "Select an element first"
            } else {
                val quoted = JSONObject.quote(htmlInput.text.toString())

                webView.evaluateJavascript(
                    """
                    (function(){
                        const old = window.__devSelected;
                        if (!old) return;

                        const box = document.createElement('div');
                        box.innerHTML = $quoted;
                        const next = box.firstElementChild;

                        if (next) {
                            old.replaceWith(next);
                            window.__devSendSelection(next);
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        })

        actions.addView(button("Pick element") {
            toggleInspect(true)
        })

        panelBody.addView(actions)
    }

    private fun renderStyles() {
        panelBody.addView(label("COMPUTED STYLES"))

        val computed = label(
            selectedComputedCss.ifEmpty {
                "Select an element to inspect its CSS."
            },
            11f
        ).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(27, 29, 34))
        }

        panelBody.addView(
            computed,
            LinearLayout.LayoutParams(-1, dp(130))
        )

        panelBody.addView(label("INLINE CSS · Editable"))

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
            if (selectedHtml.isEmpty()) {
                status.text = "Select an element first"
            } else {
                val css = JSONObject.quote(cssInput.text.toString())

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

    private fun renderConsole() {
        panelBody.addView(label("CONSOLE · Logs, warnings and errors"))

        consoleOutput = label(
            consoleHistory.joinToString("\n")
                .ifEmpty { "DevBrowser console ready." },
            11f
        ).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(18, 20, 24))
        }

        panelBody.addView(
            consoleOutput,
            LinearLayout.LayoutParams(-1, dp(150))
        )

        val command = editor("", "Enter JavaScript and tap Run...", 2)

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
                        JSONObject("{\"value\":$result}")
                            .optString("value", "undefined")
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
            consoleOutput?.text = consoleHistory.joinToString("\n")
        }
    }

    private fun handleInspectorData(data: JSONObject) {
        runOnUiThread {
            domTree = data.optString("tree", domTree)
            domNodes = data.optJSONArray("treeNodes") ?: JSONArray()
            selectedHtml = data.optString("html", "")
            selectedInlineCss = data.optString("inlineCss", "")
            selectedComputedCss = data.optString("computedCss", "")

            status.text = "Selected: " +
                data.optString("selector", "element")

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
                let lines = [];
                let count = 0;

                function walk(node, depth, path) {
                    if (!node || count >= 220 || depth > 6) return;
                    if (node.nodeType !== 1) return;

                    count++;

                    const label = selector(node);
                    const selected = node === window.__devSelected;

                    nodes.push({
                        label: label,
                        depth: depth,
                        path: path,
                        selected: selected
                    });

                    lines.push(
                        "  ".repeat(depth) +
                        (selected ? "▶ " : "  ") + label
                    );

                    Array.from(node.children).forEach(function(child, index) {
                        walk(child, depth + 1, path.concat(index));
                    });
                }

                walk(document.body, 0, []);

                return {
                    nodes: nodes,
                    text: lines.join("\\n") || "(empty document)"
                };
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

                const treeResult = treeData();

                const data = {
                    selector: selector(el),
                    html: (el.outerHTML || "").slice(0, 18000),
                    inlineCss: el.getAttribute("style") || "",
                    computedCss: css,
                    tree: treeResult.text,
                    treeNodes: treeResult.nodes
                };

                console.log("__DEVBROWSER__" + JSON.stringify(data));
            };

            document.addEventListener("click", function(e) {
                if (!window.__devInspect) return;

                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();

                const el = e.target;

                if (window.__devPreviousOutline) {
                    window.__devPreviousOutline.style.outline =
                        window.__devPreviousOutlineOutline || "";
                }

                window.__devPreviousOutline = el;
                window.__devPreviousOutlineOutline = el.style.outline || "";
                el.style.outline = "2px solid #ff5722";

                window.__devSendSelection(el);
            }, true);

            window.__devSendSelection(document.body);

        })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun toggleInspect(enabled: Boolean) {
        webView.evaluateJavascript(
            "window.__devInspect = $enabled;",
            null
        )

        status.text = if (enabled) {
            "Inspect ON — tap an element on the page"
        } else {
            "Inspect OFF"
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
