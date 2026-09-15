package dev.clipkeyboard.data

import org.json.JSONObject

data class ClipItem(
    val id: String,
    val mimeType: String = "text/plain",
    var text: String? = null,
    var filePath: String? = null,
    var fileName: String? = null,
    var fileSize: Long = 0L,
    var label: String? = null,
    var pinned: Boolean = false,
    val createdAt: Long,
    var updatedAt: Long
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isFile: Boolean get() = !isImage && mimeType != "text/plain" && !filePath.isNullOrBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("mimeType", mimeType)
        put("text", text ?: JSONObject.NULL)
        put("filePath", filePath ?: JSONObject.NULL)
        put("fileName", fileName ?: JSONObject.NULL)
        put("fileSize", fileSize)
        put("label", label ?: JSONObject.NULL)
        put("pinned", pinned)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): ClipItem = ClipItem(
            id = o.getString("id"),
            mimeType = o.optString("mimeType", "text/plain"),
            text = if (o.isNull("text")) null else o.optString("text"),
            filePath = if (o.isNull("filePath")) null else o.optString("filePath"),
            fileName = if (o.isNull("fileName")) null else o.optString("fileName"),
            fileSize = o.optLong("fileSize", 0L),
            label = if (o.isNull("label")) null else o.optString("label"),
            pinned = o.optBoolean("pinned", false),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
