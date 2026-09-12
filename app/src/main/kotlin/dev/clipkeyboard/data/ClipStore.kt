package dev.clipkeyboard.data

import android.content.Context
import org.json.JSONArray
import java.util.UUID

/**
 * クリップ履歴の永続化レイヤ。
 * Room 等を使わず SharedPreferences 内に JSON 配列としてまとめて保存する軽量実装。
 * (キーボード用途では件数が数百程度に収まる想定なので十分実用的)
 */
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
        // ピン留めを先頭に、その後は新しい順
        return list.sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.updatedAt })
    }

    private fun saveAll(context: Context, items: List<ClipItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ITEMS, arr.toString()).apply()
        notifyChanged()
    }

    /** システムクリップボード等から新規追加。同一テキストが既にあれば更新日時だけ上げて重複を避ける。 */
    fun addOrTouch(context: Context, text: String, label: String? = null): ClipItem {
        val now = System.currentTimeMillis()
        val current = getAll(context).toMutableList()
        val existing = current.firstOrNull { it.text == text }
        if (existing != null) {
            existing.updatedAt = now
            if (label != null) existing.label = label
            saveAll(context, current)
            return existing
        }
        val item = ClipItem(
            id = UUID.randomUUID().toString(),
            text = text,
            label = label,
            createdAt = now,
            updatedAt = now
        )
        current.add(0, item)
        // 上限を超えたらピン留めされていない古いものから削除
        val trimmed = if (current.size > MAX_ITEMS) {
            val pinned = current.filter { it.pinned }
            val unpinned = current.filter { !it.pinned }.sortedByDescending { it.updatedAt }
            pinned + unpinned.take(MAX_ITEMS - pinned.size)
        } else current
        saveAll(context, trimmed)
        return item
    }

    /**
     * メイン画面の「＋」やキーボードの「定型文を作成」から呼ばれる明示的な新規作成。
     * addOrTouch と異なり同一テキストとの統合(重複排除)を行わない
     * — ユーザーが意図して複数の定型文を作る操作のため。
     */
    fun createManual(context: Context, text: String, label: String? = null, pinned: Boolean = false): ClipItem {
        val now = System.currentTimeMillis()
        val item = ClipItem(
            id = UUID.randomUUID().toString(),
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
        val current = getAll(context).filterNot { it.id == id }
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
        val current = getAll(context).filter { it.pinned }
        saveAll(context, current)
    }
}
