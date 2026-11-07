package com.example.devbrowser

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.text.DecimalFormat

class BrowserDownloadsView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onBack()
        fun onRefresh()
        fun onCancel(downloadId: Long)
        fun onOpen(downloadId: Long)
        fun onRemove(downloadId: Long)
        fun onClearCompleted()
    }

    var listener: Listener? = null

    private val header = LinearLayout(context)
    private val content = LinearLayout(context)
    private val scrollView = ScrollView(context)
    private val emptyState = TextView(context)

    private val countLabel = TextView(context)
    private val refreshButton = Button(context)
    private val clearButton = Button(context)

    private val formatter = DecimalFormat("#,##0.0")

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(248, 249, 250))

        buildHeader()
        buildToolbar()
        buildContent()
    }

    private fun buildHeader() {
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(10), dp(12), dp(10))
        header.setBackgroundColor(Color.WHITE)

        val back = TextView(context).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(60, 64, 67))
            setOnClickListener {
                listener?.onBack()
            }
        }

        header.addView(
            back,
            LayoutParams(dp(42), dp(44))
        )

        val title = TextView(context).apply {
            text = "Downloads"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(32, 33, 36))
        }

        header.addView(
            title,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )

        addView(
            header,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )
    }

    private fun buildToolbar() {
        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(5), dp(14), dp(5))
            setBackgroundColor(Color.WHITE)
        }

        countLabel.apply {
            text = "0 downloads"
            textSize = 13f
            setTextColor(Color.rgb(95, 99, 104))
        }

        toolbar.addView(
            countLabel,
            LayoutParams(0, dp(40), 1f)
        )

        refreshButton.apply {
            text = "Refresh"
            textSize = 12f
            isAllCaps = false
            setOnClickListener {
                listener?.onRefresh()
            }
        }

        clearButton.apply {
            text = "Clear completed"
            textSize = 12f
            isAllCaps = false
            setOnClickListener {
                listener?.onClearCompleted()
            }
        }

        toolbar.addView(
            refreshButton,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dp(42)
            )
        )

        toolbar.addView(
            clearButton,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dp(42)
            )
        )

        addView(
            toolbar,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        val divider = View(context).apply {
            setBackgroundColor(Color.rgb(232, 234, 237))
        }

        addView(
            divider,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(1)
            )
        )
    }

    private fun buildContent() {
        content.orientation = VERTICAL
        content.setPadding(dp(12), dp(12), dp(12), dp(16))

        scrollView.apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            addView(
                content,
                ScrollView.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
            )
        }

        emptyState.apply {
            text = "No downloads yet\n\nYour downloaded files will appear here."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(95, 99, 104))
            setPadding(dp(25), dp(35), dp(25), dp(35))
        }

        addView(
            scrollView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    fun render(downloads: List<BrowserDownload>) {
        content.removeAllViews()

        countLabel.text = when (downloads.size) {
            1 -> "1 download"
            else -> "${downloads.size} downloads"
        }

        if (downloads.isEmpty()) {
            content.addView(
                emptyState,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                )
            )
            return
        }

        downloads.forEach { download ->
            content.addView(createDownloadCard(download))
        }
    }

    private fun createDownloadCard(
        download: BrowserDownload
    ): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(12))
            background = roundedBackground(
                Color.WHITE,
                Color.rgb(232, 234, 237)
            )
        }

        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fileIcon = TextView(context).apply {
            text = "↓"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(26, 115, 232))
            background = roundedBackground(
                Color.rgb(232, 240, 254),
                Color.TRANSPARENT
            )
        }

        titleRow.addView(
            fileIcon,
            LayoutParams(dp(42), dp(42))
        )

        val details = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }

        val fileName = TextView(context).apply {
            text = download.fileName.ifBlank {
                "Unnamed download"
            }
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(32, 33, 36))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val url = TextView(context).apply {
            text = download.url
            textSize = 11f
            setTextColor(Color.rgb(95, 99, 104))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }

        details.addView(fileName)
        details.addView(url)

        titleRow.addView(
            details,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )

        card.addView(
            titleRow,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val status = TextView(context).apply {
            text = statusText(download)
            textSize = 12f
            setTextColor(statusColor(download))
            setPadding(0, dp(10), 0, dp(5))
        }

        card.addView(status)

        if (download.isRunning) {
            val progressBar = ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 100
                progress = download.progress
                progressTintList =
                    android.content.res.ColorStateList.valueOf(
                        Color.rgb(26, 115, 232)
                    )
            }

            card.addView(
                progressBar,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    dp(5)
                )
            )
        }

        val size = TextView(context).apply {
            text = "${formatBytes(download.downloadedBytes)} / " +
                formatBytes(download.totalBytes)
            textSize = 11f
            setTextColor(Color.rgb(95, 99, 104))
            setPadding(0, dp(7), 0, dp(4))
        }

        card.addView(size)

        val buttons = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        if (download.isRunning) {
            buttons.addView(
                makeActionButton("Cancel") {
                    listener?.onCancel(download.id)
                }
            )
        }

        if (download.isComplete) {
            buttons.addView(
                makeActionButton("Open") {
                    listener?.onOpen(download.id)
                }
            )
        }

        buttons.addView(
            makeActionButton("Remove") {
                listener?.onRemove(download.id)
            }
        )

        card.addView(
            buttons,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(43)
            )
        )

        return card.apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun makeActionButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(9), 0, dp(9), 0)

            setOnClickListener {
                action()
            }
        }
    }

    private fun statusText(
        download: BrowserDownload
    ): String {
        return when {
            download.isComplete -> "✓ Completed"

            download.isFailed ->
                "Failed — error ${download.reason}"

            download.status ==
                android.app.DownloadManager.STATUS_PAUSED ->
                "Paused"

            download.status ==
                android.app.DownloadManager.STATUS_PENDING ->
                "Waiting to start"

            download.status ==
                android.app.DownloadManager.STATUS_RUNNING ->
                "Downloading • ${download.progress}%"

            else -> "Unknown status"
        }
    }

    private fun statusColor(
        download: BrowserDownload
    ): Int {
        return when {
            download.isComplete -> Color.rgb(24, 128, 56)
            download.isFailed -> Color.rgb(197, 34, 31)
            else -> Color.rgb(26, 115, 232)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Unknown"

        if (bytes < 1024L) {
            return "$bytes B"
        }

        val kb = bytes / 1024.0

        if (kb < 1024.0) {
            return "${formatter.format(kb)} KB"
        }

        val mb = kb / 1024.0

        if (mb < 1024.0) {
            return "${formatter.format(mb)} MB"
        }

        val gb = mb / 1024.0

        return "${formatter.format(gb)} GB"
    }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(13).toFloat()

            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }
}
