package dev.clipkeyboard.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.clipkeyboard.data.ClipStore
import dev.clipkeyboard.databinding.ActivityEditClipBinding

class EditClipActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CLIP_ID = "extra_clip_id"
        private const val PAGE_SIZE = 3000
    }

    private lateinit var binding: ActivityEditClipBinding
    private var existingId: String? = null

    private val pageList = mutableListOf<String>()
    private var currentPageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditClipBinding.inflate(layoutInflater)
        setContentView(binding.root)

        existingId = intent.getStringExtra(EXTRA_CLIP_ID)
        val isCreateMode = existingId == null

        if (isCreateMode) {
            binding.toolbarTitle.text = "クリップを作成"
            binding.btnDelete.visibility = View.GONE
            setupPagination("")
        } else {
            val item = ClipStore.getAll(this).firstOrNull { it.id == existingId }
            if (item == null) {
                Toast.makeText(this, "対象のクリップが見つかりません", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            binding.toolbarTitle.text = "クリップを編集"
            binding.editLabel.setText(item.label.orEmpty())
            binding.switchPinned.isChecked = item.pinned
            binding.btnDelete.visibility = View.VISIBLE
            setupPagination(item.text.orEmpty())
        }

        binding.rowPinned.setOnClickListener {
            binding.switchPinned.toggle()
        }

        binding.btnPrevPage.setOnClickListener {
            if (currentPageIndex > 0) {
                saveCurrentPageToBuffer()
                currentPageIndex--
                loadCurrentPage()
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPageIndex < pageList.size - 1) {
                saveCurrentPageToBuffer()
                currentPageIndex++
                loadCurrentPage()
            }
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            saveCurrentPageToBuffer()
            val label = binding.editLabel.text?.toString()?.ifBlank { null }
            val fullBody = getFullTextFromPages()
            val pinned = binding.switchPinned.isChecked

            if (fullBody.isBlank()) {
                Toast.makeText(this, "本文を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = existingId
            if (id == null) {
                ClipStore.createManual(this, fullBody, label, pinned)
                Toast.makeText(this, "作成しました", Toast.LENGTH_SHORT).show()
            } else {
                val item = ClipStore.getAll(this).firstOrNull { it.id == id }
                if (item != null) {
                    item.label = label
                    item.text = fullBody
                    item.pinned = pinned
                    ClipStore.update(this, item)
                    Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
                }
            }
            finish()
        }

        binding.btnDelete.setOnClickListener {
            val id = existingId ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("クリップの削除")
                .setMessage("このクリップを削除しますか？\nこの操作は取り消せません。")
                .setPositiveButton("削除する") { _, _ ->
                    ClipStore.delete(this, id)
                    finish()
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }
    }

    private fun setupPagination(fullText: String) {
        pageList.clear()
        if (fullText.length <= PAGE_SIZE) {
            pageList.add(fullText)
            currentPageIndex = 0
            binding.layoutPagination.visibility = View.GONE
            binding.editBody.setText(fullText)
        } else {
            var start = 0
            while (start < fullText.length) {
                val end = (start + PAGE_SIZE).coerceAtMost(fullText.length)
                pageList.add(fullText.substring(start, end))
                start = end
            }
            currentPageIndex = 0
            binding.layoutPagination.visibility = View.VISIBLE
            loadCurrentPage()
        }
    }

    private fun saveCurrentPageToBuffer() {
        if (currentPageIndex in pageList.indices) {
            pageList[currentPageIndex] = binding.editBody.text?.toString().orEmpty()
        }
    }

    private fun loadCurrentPage() {
        if (currentPageIndex in pageList.indices) {
            binding.editBody.setText(pageList[currentPageIndex])
            binding.editBody.setSelection(0)
        }
        updatePaginationBar()
    }

    private fun updatePaginationBar() {
        val totalPages = pageList.size
        val totalChars = getFullTextFromPages().length
        binding.textPageInfo.text = "ページ ${currentPageIndex + 1} / $totalPages (全 ${totalChars}字)"

        val hasPrev = currentPageIndex > 0
        binding.btnPrevPage.isEnabled = hasPrev
        binding.btnPrevPage.alpha = if (hasPrev) 1.0f else 0.3f

        val hasNext = currentPageIndex < totalPages - 1
        binding.btnNextPage.isEnabled = hasNext
        binding.btnNextPage.alpha = if (hasNext) 1.0f else 0.3f
    }

    private fun getFullTextFromPages(): String {
        return pageList.joinToString("")
    }
}
