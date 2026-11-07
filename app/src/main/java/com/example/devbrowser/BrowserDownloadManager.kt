package com.example.devbrowser

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BrowserDownload(
    val id: Long,
    val url: String,
    val fileName: String,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int,
    val localUri: String?
) {
    val progress: Int
        get() = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }

    val isComplete: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL

    val isFailed: Boolean
        get() = status == DownloadManager.STATUS_FAILED

    val isRunning: Boolean
        get() = status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PENDING
}

class BrowserDownloadManager(
    context: Context
) {

    private val appContext = context.applicationContext

    private val manager =
        appContext.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

    private val preferences =
        appContext.getSharedPreferences(
            "devbrowser_downloads",
            Context.MODE_PRIVATE
        )

    companion object {
        private const val KEY_DOWNLOADS = "download_records"
        private const val MAX_RECORDS = 500
    }

    @Synchronized
    fun enqueue(
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null
    ): Long {
        val cleanUrl = url.trim()

        require(
            cleanUrl.startsWith("https://", true) ||
                cleanUrl.startsWith("http://", true)
        ) {
            "Only HTTP and HTTPS downloads are supported"
        }

        val guessedName = URLUtil.guessFileName(
            cleanUrl,
            contentDisposition,
            mimeType
        ).ifBlank {
            "download_${System.currentTimeMillis()}"
        }

        val request = DownloadManager.Request(
            Uri.parse(cleanUrl)
        ).apply {
            setTitle(guessedName)
            setDescription("Downloading with DevBrowser")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )

            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                guessedName
            )

            if (!mimeType.isNullOrBlank()) {
                setMimeType(mimeType)
            }

            if (!userAgent.isNullOrBlank()) {
                addRequestHeader("User-Agent", userAgent)
            }

            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }

        val downloadId = manager.enqueue(request)

        saveDownloadId(downloadId, cleanUrl, guessedName)

        return downloadId
    }

    @Synchronized
    private fun saveDownloadId(
        id: Long,
        url: String,
        fileName: String
    ) {
        val records = readRecords()

        records.put(
            JSONObject()
                .put("id", id)
                .put("url", url)
                .put("fileName", fileName)
                .put("createdAt", System.currentTimeMillis())
        )

        while (records.length() > MAX_RECORDS) {
            records.remove(0)
        }

        preferences.edit()
            .putString(KEY_DOWNLOADS, records.toString())
            .apply()
    }

    @Synchronized
    private fun readRecords(): JSONArray {
        val raw = preferences.getString(
            KEY_DOWNLOADS,
            "[]"
        ) ?: "[]"

        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    fun getDownloads(): List<BrowserDownload> {
        val records = readRecords()
        val result = ArrayList<BrowserDownload>()

        for (index in records.length() - 1 downTo 0) {
            val record = records.optJSONObject(index)
                ?: continue

            val id = record.optLong("id", -1L)

            if (id <= 0L) continue

            val download = getDownload(id)

            if (download != null) {
                result.add(download)
            }
        }

        return result
    }

    fun getDownload(id: Long): BrowserDownload? {
        val query = DownloadManager.Query()
            .setFilterById(id)

        var cursor: Cursor? = null

        return try {
            cursor = manager.query(query)

            if (cursor == null || !cursor.moveToFirst()) {
                return null
            }

            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_STATUS
                )
            )

            val reason = cursor.getInt(
                cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_REASON
                )
            )

            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                )
            )

            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                )
            )

            val localUri = cursor.getString(
                cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_LOCAL_URI
                )
            )

            val record = findRecord(id)

            BrowserDownload(
                id = id,
                url = record?.optString("url", "") ?: "",
                fileName = record?.optString("fileName", "")
                    ?: "",
                status = status,
                downloadedBytes = downloaded,
                totalBytes = total,
                reason = reason,
                localUri = localUri
            )
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun findRecord(id: Long): JSONObject? {
        val records = readRecords()

        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index)
                ?: continue

            if (record.optLong("id") == id) {
                return record
            }
        }

        return null
    }

    fun cancel(id: Long): Boolean {
        return try {
            manager.remove(id) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun cancelAll(): Int {
        return try {
            manager.remove()
        } catch (_: Exception) {
            0
        }
    }

    fun removeRecord(id: Long): Boolean {
        val records = readRecords()
        val updated = JSONArray()
        var removed = false

        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index)
                ?: continue

            if (record.optLong("id") == id) {
                removed = true
            } else {
                updated.put(record)
            }
        }

        if (removed) {
            preferences.edit()
                .putString(KEY_DOWNLOADS, updated.toString())
                .apply()
        }

        return removed
    }

    fun clearCompletedRecords(): Int {
        val records = readRecords()
        val updated = JSONArray()
        var removed = 0

        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index)
                ?: continue

            val id = record.optLong("id", -1L)
            val download = if (id > 0L) getDownload(id) else null

            if (download == null || download.isComplete || download.isFailed) {
                removed++
            } else {
                updated.put(record)
            }
        }

        preferences.edit()
            .putString(KEY_DOWNLOADS, updated.toString())
            .apply()

        return removed
    }

    fun getReasonText(download: BrowserDownload): String {
        if (!download.isFailed) return ""

        return when (download.reason) {
            DownloadManager.ERROR_CANNOT_RESUME ->
                "Download cannot resume"

            DownloadManager.ERROR_DEVICE_NOT_FOUND ->
                "Storage device not found"

            DownloadManager.ERROR_FILE_ALREADY_EXISTS ->
                "File already exists"

            DownloadManager.ERROR_FILE_ERROR ->
                "File storage error"

            DownloadManager.ERROR_HTTP_DATA_ERROR ->
                "Network data error"

            DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                "Not enough storage space"

            DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
                "Too many redirects"

            DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
                "Unsupported HTTP response"

            DownloadManager.ERROR_UNKNOWN ->
                "Unknown download error"

            DownloadManager.PAUSED_QUEUED_FOR_WIFI ->
                "Waiting for Wi-Fi"

            DownloadManager.PAUSED_WAITING_FOR_NETWORK ->
                "Waiting for network"

            DownloadManager.PAUSED_WAITING_TO_RETRY ->
                "Waiting to retry"

            DownloadManager.PAUSED_UNKNOWN ->
                "Download paused"

            else -> "Download failed (code ${download.reason})"
        }
    }

    fun getCompletedFile(download: BrowserDownload): File? {
        if (!download.isComplete) return null

        val uri = download.localUri ?: return null

        return try {
            val parsed = Uri.parse(uri)

            if (parsed.scheme == "file") {
                parsed.path?.let { File(it) }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
