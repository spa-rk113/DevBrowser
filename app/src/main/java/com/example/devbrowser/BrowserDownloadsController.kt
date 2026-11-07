package com.example.devbrowser

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast

class BrowserDownloadsController(
    private val activity: Activity
) {

    private val downloadManager =
        BrowserDownloadManager(activity)

    private var downloadsView: BrowserDownloadsView? = null
    private var overlay: FrameLayout? = null
    private var container: FrameLayout? = null

    private val handler = Handler(Looper.getMainLooper())

    private var visible = false

    private val refreshTask = object : Runnable {
        override fun run() {
            if (!visible) return

            refresh()

            handler.postDelayed(this, 1000)
        }
    }

    fun isVisible(): Boolean = visible

    fun show(parent: FrameLayout) {
        if (visible) {
            refresh()
            return
        }

        container = parent

        val screen = BrowserDownloadsView(activity)

        screen.listener = object : BrowserDownloadsView.Listener {

            override fun onBack() {
                hide()
            }

            override fun onRefresh() {
                refresh()
            }

            override fun onCancel(id: Long) {
                val cancelled = downloadManager.cancel(id)

                if (cancelled) {
                    showToast("Download cancelled")
                } else {
                    showToast("Unable to cancel download")
                }

                refresh()
            }

            override fun onOpen(download: BrowserDownload) {
                openDownload(download)
            }

            override fun onRemove(id: Long) {
                downloadManager.removeRecord(id)
                refresh()
            }

            override fun onClearCompleted() {
                val count =
                    downloadManager.clearCompletedRecords()

                showToast("$count completed downloads cleared")
                refresh()
            }
        }

        val screenOverlay = FrameLayout(activity).apply {
            setBackgroundColor(Color.rgb(248, 249, 250))
            alpha = 0f
            translationY = dp(18).toFloat()

            addView(
                screen,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        parent.addView(
            screenOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        downloadsView = screen
        overlay = screenOverlay
        visible = true

        refresh()

        screenOverlay.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180)
            .start()

        handler.removeCallbacks(refreshTask)
        handler.postDelayed(refreshTask, 1000)
    }

    fun hide() {
        if (!visible) return

        visible = false
        handler.removeCallbacks(refreshTask)

        val oldOverlay = overlay

        oldOverlay?.animate()
            ?.alpha(0f)
            ?.translationY(dp(12).toFloat())
            ?.setDuration(140)
            ?.withEndAction {
                (oldOverlay.parent as? FrameLayout)
                    ?.removeView(oldOverlay)
            }
            ?.start()
            ?: run {
                (oldOverlay?.parent as? FrameLayout)
                    ?.removeView(oldOverlay)
            }

        downloadsView?.listener = null

        downloadsView = null
        overlay = null
        container = null
    }

    fun refresh() {
        if (!visible) return

        downloadsView?.setDownloads(
            downloadManager.getDownloads()
        )
    }

    fun startDownload(
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null
    ): Boolean {

        return try {
            downloadManager.enqueue(
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType
            )

            showToast("Download started")
            refresh()

            true
        } catch (error: Exception) {
            showToast(
                error.message ?: "Unable to start download"
            )

            false
        }
    }

    private fun openDownload(download: BrowserDownload) {
        if (!download.isComplete) {
            showToast("Download is not complete")
            return
        }

        try {
            val systemManager =
                activity.getSystemService(
                    Activity.DOWNLOAD_SERVICE
                ) as android.app.DownloadManager

            val uri: Uri =
                systemManager.getUriForDownloadedFile(download.id)
                    ?: throw IllegalStateException(
                        "Downloaded file is unavailable"
                    )

            val mimeType =
                systemManager.getMimeTypeForDownloadedFile(
                    download.id
                ) ?: "*/*"

            val intent = Intent(
                Intent.ACTION_VIEW
            ).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivity(intent)

        } catch (_: Exception) {
            showToast(
                "No app found to open this file"
            )
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(
            activity,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dp(value: Int): Int {
        return (
            value * activity.resources.displayMetrics.density
        ).toInt()
    }

    fun destroy() {
        hide()
        handler.removeCallbacksAndMessages(null)
    }
}
