package com.example.devbrowser

import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class BrowserDownloadsView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onBack()
        fun onRefresh()
        fun onCancel(id: Long)
        fun onOpen(download: BrowserDownload)
        fun onRemove(id: Long)
        fun onClearCompleted()
    }

    var listener: Listener? = null

    private val items = LinearLayout(context)
    private val countLabel = TextView(context)
    private val emptyLabel = TextView(context)

    private val backgroundColor = Color.rgb(248, 249, 250)
    private val textColor = Color.rgb(32, 33, 36)
    private val mutedColor = Color.rgb(95, 99, 104)
    private val blueColor = Color.rgb(26, 115, 232)

    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)
        buildHeader()
        buildContent()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun button(
        label: String,
        action: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.rgb(241, 243, 244))
            isClickable = true
            setOnClickListener { action() }
        }
    }

    private fun buildHeader() {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(12), dp(5))
            setBackgroundColor(Color.WHITE)
        }

        header.addView(
            button("‹") { listener?.onBack() },
            LayoutParams(dp(44), dp(44))
        )

        val title = TextView(context).apply {
            text = "Downloads"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setPadding(dp(12), 0, 0, 0)
        }

        header.addView(
            title,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )

        header.addView(
            button("↻") { listener?.onRefresh() }
        )

        addView(
            header,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )
    }

    private fun buildContent() {
        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setBackgroundColor(Color.WHITE)
        }

        countLabel.apply {
            text = "Your downloads"
            textSize = 13f
            setTextColor(mutedColor)
        }

        actions.addView(
            countLabel,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )

        actions.addView(
            button("Clear completed") {
                listener?.onClearCompleted()
            }
        )

        addView(
            actions,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
        }

        items.orientation = VERTICAL
        items.setPadding(dp(12), dp(10), dp(12), dp(16))

        emptyLabel.apply {
            text = "No downloads yet"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(mutedColor)
            setPadding(dp(20), dp(45), dp(20), dp(45))
        }

        scroll.addView(items)

        addView(
            scroll,
            LayoutParams(0, 0, 1f)
        )
    }

    fun setDownloads(downloads: List<BrowserDownload>) {
        items.removeAllViews()

        countLabel.text = "${downloads.size} downloads"

        if (downloads.isEmpty()) {
            items.addView(emptyLabel)
            return
        }

        downloads.forEach { download ->
            items.addView(createDownloadCard(download))
        }
    }

    private fun createDownloadCard(
        download: BrowserDownload
    ): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(Color.WHITE)
        }

        val heading = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fileName = TextView(context).apply {
            text = download.fileName.ifBlank { "Download" }
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            maxLines = 2
        }

        heading.addView(
            fileName,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )

        val size = TextView(context).apply {
            text = formatBytes(download.totalBytes)
            textSize = 11f
            setTextColor(mutedColor)
        }

        heading.addView(size)
        card.addView(heading)

        val status = TextView(context).apply {
            text = statusText(download)
            textSize = 12f
            setTextColor(
                if (download.isFailed) {
                    Color.rgb(197, 34, 31)
                } else {
                    mutedColor
                }
            )
            setPadding(0, dp(7), 0, dp(5))
        }

        card.addView(status)

        if (download.isRunning) {
            val progress = ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 100
                this.progress = download.progress
            }

            card.addView(
                progress,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    dp(5)
                )
            )

            val percentage = TextView(context).apply {
                text = "${download.progress}%"
                textSize = 11f
                setTextColor(mutedColor)
                gravity = Gravity.END
                setPadding(0, dp(4), 0, 0)
            }

            card.addView(percentage)
        }

        val url = TextView(context).apply {
            text = download.url
            textSize = 10f
            setTextColor(mutedColor)
            maxLines = 2
            setPadding(0, dp(7), 0, dp(7))
        }

        card.addView(url)

        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        if (download.isRunning) {
            actions.addView(
                button("Cancel") {
                    listener?.onCancel(download.id)
                }
            )
        }

        if (download.isComplete) {
            actions.addView(
                button("Open") {
                    listener?.onOpen(download)
                }
            )
        }

        actions.addView(
            button("Remove") {
                listener?.onRemove(download.id)
            },
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(7)
            }
        )

        card.addView(actions)

        return card.apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun statusText(download: BrowserDownload): String {
        return when (download.status) {
            DownloadManager.STATUS_PENDING -> "Waiting"
            DownloadManager.STATUS_RUNNING -> "Downloading"
            DownloadManager.STATUS_PAUSED -> "Paused"
            DownloadManager.STATUS_SUCCESSFUL -> "Completed"
            DownloadManager.STATUS_FAILED -> "Failed · ${download.reason}"
            else -> "Unknown status"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "Size unknown"

        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0

        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }

        return String.format(
            Locale.US,
            "%.1f %s",
            value,
            units[index]
        )
    }
}
