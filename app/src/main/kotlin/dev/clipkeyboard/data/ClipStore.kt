package dev.clipkeyboard.data

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID

object ClipStore {
    private const val PREFS = "clipkeyboard_store"
    private const val KEY_ITEMS = "items"
    const val MAX_ITEMS = 500

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyChanged() { listeners.toList().forEach { it() } }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<ClipItem> {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val list = ArrayList<ClipItem>(arr.length())
        for (i in 0 until arr.length()) {
            list.add(ClipItem.fromJson(arr.getJSONObject(i)))
        }
        return list.sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.updatedAt })
    }

    private fun saveAll(context: Context, items: List<ClipItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ITEMS, arr.toString()).apply()
        notifyChanged()
    }

    fun addOrTouchText(context: Context, text: String, label: String? = null): ClipItem {
        val now = System.currentTimeMillis()
        val current = getAll(context).toMutableList()
        val existing = current.firstOrNull { it.mimeType == "text/plain" && it.text == text }
        if (existing != null) {
            existing.updatedAt = now
            if (label != null) existing.label = label
            saveAll(context, current)
            return existing
        }
        val item = ClipItem(
            id = UUID.randomUUID().toString(),
            mimeType = "text/plain",
            text = text,
            label = label,
            createdAt = now,
            updatedAt = now
        )
        current.add(0, item)
        trimAndSave(context, current)
        return item
    }

    fun addImageOrFile(
        context: Context,
        mimeType: String,
        filePath: String,
        fileName: String?,
        fileSize: Long,
        label: String? = null
    ): ClipItem {
        val now = System.currentTimeMillis()
        val current = getAll(context).toMutableList()
        val item = ClipItem(
            id = UUID.randomUUID().toString(),
            mimeType = mimeType,
            filePath = filePath,
            fileName = fileName,
            fileSize = fileSize,
            label = label,
            createdAt = now,
            updatedAt = now
        )
        current.add(0, item)
        trimAndSave(context, current)
        return item
    }

    fun createManual(context: Context, text: String, label: String? = null, pinned: Boolean = false): ClipItem {
        val now = System.currentTimeMillis()
        val item = ClipItem(
            id = UUID.randomUUID().toString(),
            mimeType = "text/plain",
            text = text,
            label = label,
            pinned = pinned,
            createdAt = now,
            updatedAt = now
        )
        val current = getAll(context).toMutableList()
        current.add(0, item)
        saveAll(context, current)
        return item
    }

    fun update(context: Context, item: ClipItem) {
        val current = getAll(context).toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            item.updatedAt = System.currentTimeMillis()
            current[idx] = item
            saveAll(context, current)
        }
    }

    fun delete(context: Context, id: String) {
        val current = getAll(context).toMutableList()
        val item = current.firstOrNull { it.id == id }
        if (item?.filePath != null) {
            try { File(item.filePath!!).delete() } catch (_: Exception) {}
        }
        current.removeAll { it.id == id }
        saveAll(context, current)
    }

    fun togglePin(context: Context, id: String) {
        val current = getAll(context).toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            current[idx].pinned = !current[idx].pinned
            saveAll(context, current)
        }
    }

    fun clearAllUnpinned(context: Context) {
        val current = getAll(context)
        val unpinned = current.filter { !it.pinned }
        unpinned.forEach { item ->
            if (item.filePath != null) {
                try { File(item.filePath!!).delete() } catch (_: Exception) {}
            }
        }
        saveAll(context, current.filter { it.pinned })
    }

    private fun trimAndSave(context: Context, list: MutableList<ClipItem>) {
        val trimmed = if (list.size > MAX_ITEMS) {
            val pinned = list.filter { it.pinned }
            val unpinned = list.filter { !it.pinned }.sortedByDescending { it.updatedAt }
            val toRemove = unpinned.drop(MAX_ITEMS - pinned.size)
            toRemove.forEach { item ->
                if (item.filePath != null) {
                    try { File(item.filePath!!).delete() } catch (_: Exception) {}
                }
            }
            pinned + unpinned.take(MAX_ITEMS - pinned.size)
        } else list
        saveAll(context, trimmed)
    }
}
