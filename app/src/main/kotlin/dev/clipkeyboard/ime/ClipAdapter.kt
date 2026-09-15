package dev.clipkeyboard.ime

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.clipkeyboard.R
import dev.clipkeyboard.data.ClipItem
import dev.clipkeyboard.theme.ThemeConfig
import dev.clipkeyboard.theme.ThemeUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipAdapter(
    private var items: List<ClipItem>,
    private var theme: ThemeConfig,
    private val onTap: (ClipItem) -> Unit,
    private val onLongPress: (ClipItem) -> Unit
) : RecyclerView.Adapter<ClipAdapter.VH>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submit(newItems: List<ClipItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun updateThemeAndItems(newTheme: ThemeConfig, newItems: List<ClipItem>) {
        theme = newTheme
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val thumbnail: ImageView = root.findViewById(R.id.img_thumbnail)
        val fileBadge: TextView = root.findViewById(R.id.badge_file_ext)
        val pinIcon: ImageView = root.findViewById(R.id.img_pin_indicator)
        val label: TextView = root.findViewById(R.id.text_label)
        val body: TextView = root.findViewById(R.id.text_body)
        val charCount: TextView = root.findViewById(R.id.text_char_count)
        val time: TextView = root.findViewById(R.id.text_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clip_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // ① 画像クリップの表示
        if (item.isImage && item.filePath != null) {
            holder.thumbnail.visibility = View.VISIBLE
            holder.fileBadge.visibility = View.GONE
            holder.charCount.visibility = View.GONE
            loadThumbnail(holder.thumbnail, item.filePath!!)
            holder.body.text = item.fileName ?: "画像 (${formatSize(item.fileSize)})"
        }
        // ② ファイル・独自形式の表示
        else if (item.isFile) {
            holder.thumbnail.visibility = View.GONE
            holder.fileBadge.visibility = View.VISIBLE
            holder.charCount.visibility = View.GONE
            val ext = item.fileName?.substringAfterLast(".", "FILE")?.uppercase(Locale.getDefault()) ?: "FILE"
            holder.fileBadge.text = ext.take(4)
            holder.body.text = "${item.fileName}\n${formatSize(item.fileSize)}"
        }
        // ③ 通常テキストの表示（先頭200文字トリミング＆文字数バッジ）
        else {
            holder.thumbnail.visibility = View.GONE
            holder.fileBadge.visibility = View.GONE
            val fullText = item.text.orEmpty()
            val textLength = fullText.length
            holder.body.text = if (textLength > 200) fullText.take(200) + "…" else fullText

            if (textLength >= 500) {
                holder.charCount.visibility = View.VISIBLE
                holder.charCount.text = formatCharCount(textLength)
                holder.charCount.setTextColor(theme.accentColor)
            } else {
                holder.charCount.visibility = View.GONE
            }
        }

        holder.body.setTextColor(theme.textColor)
        holder.body.textSize = 14f * theme.fontScale

        holder.label.text = item.label
        holder.label.visibility = if (item.label.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.label.setTextColor(theme.accentColor)

        holder.pinIcon.visibility = if (item.pinned) View.VISIBLE else View.GONE
        holder.pinIcon.setColorFilter(theme.accentColor)

        holder.time.text = timeFormat.format(Date(item.updatedAt))
        holder.time.setTextColor(theme.subTextColor)

        ThemeUtils.applyGlassRowBackground(holder.root, theme)

        holder.root.setOnClickListener {
            if (theme.hapticFeedbackEnabled) {
                holder.root.performHapticFeedback(
                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
            onTap(item)
        }
        holder.root.setOnLongClickListener {
            onLongPress(item)
            true
        }
    }

    private fun loadThumbnail(imageView: ImageView, path: String) {
        val file = File(path)
        if (!file.exists()) return
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            val reqSize = 96
            var inSample = 1
            while (options.outWidth / inSample / 2 >= reqSize && options.outHeight / inSample / 2 >= reqSize) {
                inSample *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = inSample }
            val bitmap = BitmapFactory.decodeFile(path, decodeOptions)
            imageView.setImageBitmap(bitmap)
        } catch (_: Exception) {
            imageView.setImageDrawable(null)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }

    private fun formatCharCount(count: Int): String {
        return if (count >= 1000) String.format(Locale.getDefault(), "📄 %.1fk字", count / 1000f) else "📄 ${count}字"
    }

    override fun getItemCount(): Int = items.size
}
