package com.example.devbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.Locale

data class BrowserHistoryEntry(
    val id: String,
    val title: String,
    val url: String,
    val visitedAt: Long,
    val visitCount: Int
)

data class BrowserBookmark(
    val id: String,
    val title: String,
    val url: String,
    val folder: String,
    val createdAt: Long
)

data class SavedPageMetadata(
    val url: String,
    val title: String,
    val savedAt: Long,
    val description: String
)

class BrowserDataStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "devbrowser_data_store"

        private const val KEY_HISTORY = "browser_history_v1"
        private const val KEY_BOOKMARKS = "browser_bookmarks_v1"
        private const val KEY_SAVED_PAGES = "browser_saved_pages_v1"

        private const val MAX_HISTORY = 2000
        private const val MAX_BOOKMARKS = 1000
        private const val MAX_SAVED_PAGES = 500
    }

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val lock = Any()

    // --------------------------------------------------
    // URL NORMALIZATION
    // --------------------------------------------------

    private fun normalizeUrl(raw: String): String? {
        val input = raw.trim()

        if (input.isEmpty() || input.length > 8192) {
            return null
        }

        return try {
            val parsed = URL(input)

            if (
                parsed.protocol != "http" &&
                parsed.protocol != "https"
            ) {
                return null
            }

            if (parsed.host.isNullOrBlank()) {
                return null
            }

            input
        } catch (_: Exception) {
            null
        }
    }

    private fun createId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    // --------------------------------------------------
    // HISTORY
    // --------------------------------------------------

    fun recordVisit(
        title: String?,
        rawUrl: String
    ) {
        val url = normalizeUrl(rawUrl) ?: return

        synchronized(lock) {
            val history = readHistory()
            val now = System.currentTimeMillis()

            val existingIndex = history.indexOfFirst {
                it.url.equals(url, ignoreCase = true)
            }

            val entry = if (existingIndex >= 0) {
                val old = history.removeAt(existingIndex)

                old.copy(
                    title = title
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.take(300)
                        ?: old.title,
                    visitedAt = now,
                    visitCount = old.visitCount + 1
                )
            } else {
                BrowserHistoryEntry(
                    id = createId(),
                    title = title
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.take(300)
                        ?: url,
                    url = url,
                    visitedAt = now,
                    visitCount = 1
                )
            }

            history.add(0, entry)

            writeHistory(
                history.take(MAX_HISTORY)
            )
        }
    }

    fun getHistory(
        limit: Int = 100
    ): List<BrowserHistoryEntry> {
        synchronized(lock) {
            return readHistory()
                .sortedByDescending { it.visitedAt }
                .take(limit.coerceIn(1, MAX_HISTORY))
        }
    }

    fun searchHistory(
        query: String,
        limit: Int = 100
    ): List<BrowserHistoryEntry> {
        val term = query.trim().lowercase(Locale.ROOT)

        if (term.isEmpty()) {
            return getHistory(limit)
        }

        synchronized(lock) {
            return readHistory()
                .filter {
                    it.title.lowercase(Locale.ROOT).contains(term) ||
                    it.url.lowercase(Locale.ROOT).contains(term)
                }
                .sortedByDescending { it.visitedAt }
                .take(limit.coerceIn(1, MAX_HISTORY))
        }
    }

    fun deleteHistoryEntry(id: String): Boolean {
        synchronized(lock) {
            val history = readHistory()
            val oldSize = history.size

            history.removeAll { it.id == id }

            if (history.size == oldSize) {
                return false
            }

            writeHistory(history)
            return true
        }
    }

    fun deleteHistoryForUrl(rawUrl: String): Boolean {
        val url = normalizeUrl(rawUrl) ?: return false

        synchronized(lock) {
            val history = readHistory()
            val oldSize = history.size

            history.removeAll {
                it.url.equals(url, ignoreCase = true)
            }

            if (history.size == oldSize) {
                return false
            }

            writeHistory(history)
            return true
        }
    }

    fun clearHistory() {
        synchronized(lock) {
            prefs.edit()
                .remove(KEY_HISTORY)
                .apply()
        }
    }

    // --------------------------------------------------
    // BOOKMARKS
    // --------------------------------------------------

    fun addBookmark(
        title: String?,
        rawUrl: String,
        folder: String = "Bookmarks"
    ): Boolean {
        val url = normalizeUrl(rawUrl) ?: return false

        synchronized(lock) {
            val bookmarks = readBookmarks()

            if (
                bookmarks.any {
                    it.url.equals(url, ignoreCase = true)
                }
            ) {
                return false
            }

            if (bookmarks.size >= MAX_BOOKMARKS) {
                return false
            }

            bookmarks.add(
                BrowserBookmark(
                    id = createId(),
                    title = title
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.take(300)
                        ?: url,
                    url = url,
                    folder = folder
                        .trim()
                        .take(80)
                        .ifBlank { "Bookmarks" },
                    createdAt = System.currentTimeMillis()
                )
            )

            writeBookmarks(bookmarks)
            return true
        }
    }

    fun getBookmarks(): List<BrowserBookmark> {
        synchronized(lock) {
            return readBookmarks()
                .sortedByDescending { it.createdAt }
        }
    }

    fun getBookmarks(
        folder: String
    ): List<BrowserBookmark> {
        synchronized(lock) {
            return readBookmarks()
                .filter {
                    it.folder.equals(folder, ignoreCase = true)
                }
                .sortedBy { it.title.lowercase(Locale.ROOT) }
        }
    }

    fun searchBookmarks(
        query: String
    ): List<BrowserBookmark> {
        val term = query.trim().lowercase(Locale.ROOT)

        synchronized(lock) {
            return readBookmarks()
                .filter {
                    it.title.lowercase(Locale.ROOT).contains(term) ||
                    it.url.lowercase(Locale.ROOT).contains(term) ||
                    it.folder.lowercase(Locale.ROOT).contains(term)
                }
        }
    }

    fun isBookmarked(rawUrl: String): Boolean {
        val url = normalizeUrl(rawUrl) ?: return false

        synchronized(lock) {
            return readBookmarks().any {
                it.url.equals(url, ignoreCase = true)
            }
        }
    }

    fun removeBookmark(id: String): Boolean {
        synchronized(lock) {
            val bookmarks = readBookmarks()
            val oldSize = bookmarks.size

            bookmarks.removeAll { it.id == id }

            if (bookmarks.size == oldSize) {
                return false
            }

            writeBookmarks(bookmarks)
            return true
        }
    }

    fun removeBookmarkForUrl(rawUrl: String): Boolean {
        val url = normalizeUrl(rawUrl) ?: return false

        synchronized(lock) {
            val bookmarks = readBookmarks()
            val oldSize = bookmarks.size

            bookmarks.removeAll {
                it.url.equals(url, ignoreCase = true)
            }

            if (bookmarks.size == oldSize) {
                return false
            }

            writeBookmarks(bookmarks)
            return true
        }
    }

    fun updateBookmark(
        id: String,
        title: String,
        folder: String
    ): Boolean {
        synchronized(lock) {
            val bookmarks = readBookmarks()
            val index = bookmarks.indexOfFirst { it.id == id }

            if (index < 0) {
                return false
            }

            val old = bookmarks[index]

            bookmarks[index] = old.copy(
                title = title.trim().take(300)
                    .ifBlank { old.url },
                folder = folder.trim().take(80)
                    .ifBlank { "Bookmarks" }
            )

            writeBookmarks(bookmarks)
            return true
        }
    }

    // --------------------------------------------------
    // SAVED PAGE METADATA
    // --------------------------------------------------

    fun savePageMetadata(
        title: String?,
        rawUrl: String,
        description: String = ""
    ): Boolean {
        val url = normalizeUrl(rawUrl) ?: return false

        synchronized(lock) {
            val pages = readSavedPages()

            val existingIndex = pages.indexOfFirst {
                it.url.equals(url, ignoreCase = true)
            }

            val page = SavedPageMetadata(
                url = url,
                title = title
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.take(300)
                    ?: url,
                savedAt = System.currentTimeMillis(),
                description = description.trim().take(1000)
            )

            if (existingIndex >= 0) {
                pages.removeAt(existingIndex)
            } else if (pages.size >= MAX_SAVED_PAGES) {
                return false
            }

            pages.add(0, page)

            writeSavedPages(
                pages.take(MAX_SAVED_PAGES)
            )

            return true
        }
    }

    fun getSavedPages(): List<SavedPageMetadata> {
        synchronized(lock) {
            return readSavedPages()
                .sortedByDescending { it.savedAt }
        }
    }

    fun removeSavedPage(rawUrl: String): Boolean {
        val url = normalizeUrl(rawUrl) ?: return false

        synchronized(lock) {
            val pages = readSavedPages()
            val oldSize = pages.size

            pages.removeAll {
                it.url.equals(url, ignoreCase = true)
            }

            if (pages.size == oldSize) {
                return false
            }

            writeSavedPages(pages)
            return true
        }
    }

    // --------------------------------------------------
    // COUNTS
    // --------------------------------------------------

    fun getHistoryCount(): Int {
        synchronized(lock) {
            return readHistory().size
        }
    }

    fun getBookmarkCount(): Int {
        synchronized(lock) {
            return readBookmarks().size
        }
    }

    // --------------------------------------------------
    // INTERNAL JSON READERS
    // --------------------------------------------------

    private fun readHistory(): MutableList<BrowserHistoryEntry> {
        val result = mutableListOf<BrowserHistoryEntry>()

        val raw = prefs.getString(KEY_HISTORY, null)
            ?: return result

        try {
            val array = JSONArray(raw)

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = normalizeUrl(item.optString("url")) ?: continue

                result.add(
                    BrowserHistoryEntry(
                        id = item.optString("id").ifBlank { createId() },
                        title = item.optString("title").ifBlank { url },
                        url = url,
                        visitedAt = item.optLong("visitedAt"),
                        visitCount = item.optInt("visitCount", 1)
                            .coerceAtLeast(1)
                    )
                )
            }
        } catch (_: Exception) {
            return mutableListOf()
        }

        return result
    }

    private fun readBookmarks(): MutableList<BrowserBookmark> {
        val result = mutableListOf<BrowserBookmark>()

        val raw = prefs.getString(KEY_BOOKMARKS, null)
            ?: return result

        try {
            val array = JSONArray(raw)

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = normalizeUrl(item.optString("url")) ?: continue

                result.add(
                    BrowserBookmark(
                        id = item.optString("id").ifBlank { createId() },
                        title = item.optString("title").ifBlank { url },
                        url = url,
                        folder = item.optString("folder")
                            .ifBlank { "Bookmarks" },
                        createdAt = item.optLong("createdAt")
                    )
                )
            }
        } catch (_: Exception) {
            return mutableListOf()
        }

        return result
    }

    private fun readSavedPages(): MutableList<SavedPageMetadata> {
        val result = mutableListOf<SavedPageMetadata>()

        val raw = prefs.getString(KEY_SAVED_PAGES, null)
            ?: return result

        try {
            val array = JSONArray(raw)

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = normalizeUrl(item.optString("url")) ?: continue

                result.add(
                    SavedPageMetadata(
                        url = url,
                        title = item.optString("title").ifBlank { url },
                        savedAt = item.optLong("savedAt"),
                        description = item.optString("description")
                    )
                )
            }
        } catch (_: Exception) {
            return mutableListOf()
        }

        return result
    }

    // --------------------------------------------------
    // INTERNAL JSON WRITERS
    // --------------------------------------------------

    private fun writeHistory(
        items: List<BrowserHistoryEntry>
    ) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                    put("visitedAt", item.visitedAt)
                    put("visitCount", item.visitCount)
                }
            )
        }

        prefs.edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    private fun writeBookmarks(
        items: List<BrowserBookmark>
    ) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                    put("folder", item.folder)
                    put("createdAt", item.createdAt)
                }
            )
        }

        prefs.edit()
            .putString(KEY_BOOKMARKS, array.toString())
            .apply()
    }

    private fun writeSavedPages(
        items: List<SavedPageMetadata>
    ) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("url", item.url)
                    put("title", item.title)
                    put("savedAt", item.savedAt)
                    put("description", item.description)
                }
            )
        }

        prefs.edit()
            .putString(KEY_SAVED_PAGES, array.toString())
            .apply()
    }
}
