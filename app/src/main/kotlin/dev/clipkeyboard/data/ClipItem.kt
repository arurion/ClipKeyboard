package dev.clipkeyboard.data

import org.json.JSONObject

data class ClipItem(
    val id: String,
    var text: String,
    var label: String? = null,   // ユーザーが付けられる任意のメモ/タイトル
    var pinned: Boolean = false,
    val createdAt: Long,
    var updatedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("label", label ?: JSONObject.NULL)
        put("pinned", pinned)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): ClipItem = ClipItem(
            id = o.getString("id"),
            text = o.getString("text"),
            label = if (o.isNull("label")) null else o.optString("label"),
            pinned = o.optBoolean("pinned", false),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
