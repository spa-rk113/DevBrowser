package com.example.devbrowser

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONObject

class MainActivity : Activity() {

private lateinit var webView: WebView
private lateinit var address: EditText
private lateinit var status: TextView
private var inspecting = false
private var selectedElement = ""

private val purple = Color.rgb(103, 80, 164)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
    }

    val toolbar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(6, 6, 6, 6)
        setBackgroundColor(Color.rgb(245, 245, 250))
    }

    address = EditText(this).apply {
        hint = "Search or enter website"
        isSingleLine = true
        textSize = 14f
        setPadding(10, 0, 10, 0)
        setBackgroundColor(Color.WHITE)
    }

    val go = button("GO") { loadAddress() }
    val menu = button("⋮") { showMenu() }

    toolbar.addView(address, LinearLayout.LayoutParams(0, 48, 1f))
    toolbar.addView(go)
    toolbar.addView(menu)

    val navigation = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.WHITE)
    }

    navigation.addView(button("←") {
        if (webView.canGoBack()) webView.goBack()
    })

    navigation.addView(button("→") {
        if (webView.canGoForward()) webView.goForward()
    })

    navigation.addView(button("↻") {
        webView.reload()
    })

    navigation.addView(button("Home") {
        webView.loadUrl("https://www.google.com")
    })

    status = TextView(this).apply {
        text = "Ready"
        textSize = 11f
        setPadding(8, 4, 8, 4)
        setBackgroundColor(Color.rgb(240, 240, 240))
    }

    webView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                address.setText(url ?: "")
                status.text = "Loaded"
                installInspector()
            }
        }
        webChromeClient = WebChromeClient()
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

    setContentView(root)
    webView.loadUrl("https://www.google.com")
}

private fun button(label: String, action: () -> Unit): Button {
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

    if (!url.startsWith("http://") && !url.startsWith("https://")) {
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
        "Inspect Element",
        "HTML Editor",
        "CSS Editor",
        "Console",
        "Disable Inspector",
        "Reload Page"
    )

    AlertDialog.Builder(this)
        .setTitle("DevBrowser Tools")
        .setItems(options) { _, which ->
            when (which) {
                0 -> toggleInspect(true)
                1 -> editHTML()
                2 -> editCSS()
                3 -> openConsole()
                4 -> toggleInspect(false)
                5 -> webView.reload()
            }
        }
        .show()
}

private fun installInspector() {
    val script = """
        (function() {
            if (window.__devBrowserInstalled) return;
            window.__devBrowserInstalled = true;
            window.__devInspect = false;

            document.addEventListener('click', function(e) {
                if (!window.__devInspect) return;

                e.preventDefault();
                e.stopPropagation();

                let el = e.target;
                window.__devSelected = el;

                el.style.outline = '2px solid #ff5722';

                let info = {
                    tag: el.tagName,
                    html: el.outerHTML.substring(0, 12000),
                    style: el.getAttribute('style') || ''
                };

                document.title = '__DEV_ELEMENT__' + JSON.stringify(info);
            }, true);
        })();
    """.trimIndent()

    webView.evaluateJavascript(script, null)
}

private fun toggleInspect(enabled: Boolean) {
    inspecting = enabled

    webView.evaluateJavascript(
        "window.__devInspect = $enabled;",
        null
    )

    status.text = if (enabled) {
        "Inspect mode ON — tap an element"
    } else {
        "Inspect mode OFF"
    }
}

private fun editHTML() {
    val script = """
        (function() {
            let el = window.__devSelected;
            if (!el) return '';
            return el.outerHTML;
        })();
    """.trimIndent()

    webView.evaluateJavascript(script) { result ->
        val html = try {
            JSONObject("{\"value\":$result}").getString("value")
        } catch (_: Exception) {
            ""
        }

        val input = EditText(this).apply {
            setText(html)
            minLines = 6
            gravity = Gravity.TOP
            textSize = 12f
        }

        AlertDialog.Builder(this)
            .setTitle("HTML Editor")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val value = JSONObject.quote(input.text.toString())
                webView.evaluateJavascript(
                    """
                    (function() {
                        let el = window.__devSelected;
                        if (el) {
                            let temp = document.createElement('div');
                            temp.innerHTML = $value;
                            let replacement = temp.firstElementChild;
                            if (replacement) {
                                el.replaceWith(replacement);
                                window.__devSelected = replacement;
                            }
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private fun editCSS() {
    val input = EditText(this).apply {
        hint = "Example: color:red; background:yellow;"
        minLines = 4
        gravity = Gravity.TOP
    }

    AlertDialog.Builder(this)
        .setTitle("CSS Editor")
        .setView(input)
        .setPositiveButton("Apply") { _, _ ->
            val css = JSONObject.quote(input.text.toString())

            webView.evaluateJavascript(
                """
                (function() {
                    if (window.__devSelected) {
                        window.__devSelected.style.cssText = $css;
                    }
                })();
                """.trimIndent(),
                null
            )
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun openConsole() {
    val input = EditText(this).apply {
        hint = "Enter JavaScript"
        minLines = 3
        gravity = Gravity.TOP
    }

    AlertDialog.Builder(this)
        .setTitle("JavaScript Console")
        .setView(input)
        .setPositiveButton("Run") { _, _ ->
            val js = JSONObject.quote(input.text.toString())

            webView.evaluateJavascript(
                "try { String(eval($js)); } catch(e) { String(e); }"
            ) { result ->
                AlertDialog.Builder(this)
                    .setTitle("Console Result")
                    .setMessage(result ?: "No result")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
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
