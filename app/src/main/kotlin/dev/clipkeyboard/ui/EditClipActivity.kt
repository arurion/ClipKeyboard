package dev.clipkeyboard.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.clipkeyboard.data.ClipStore
import dev.clipkeyboard.databinding.ActivityEditClipBinding

class EditClipActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CLIP_ID = "extra_clip_id"
    }

    private lateinit var binding: ActivityEditClipBinding
    private var existingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditClipBinding.inflate(layoutInflater)
        setContentView(binding.root)

        existingId = intent.getStringExtra(EXTRA_CLIP_ID)
        val isCreateMode = existingId == null

        if (isCreateMode) {
            binding.toolbarTitle.text = "クリップを作成"
            binding.btnDelete.visibility = android.view.View.GONE
        } else {
            val item = ClipStore.getAll(this).firstOrNull { it.id == existingId }
            if (item == null) {
                Toast.makeText(this, "対象のクリップが見つかりません", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            binding.toolbarTitle.text = "クリップを編集"
            binding.editLabel.setText(item.label.orEmpty())
            binding.editBody.setText(item.text)
            binding.switchPinned.isChecked = item.pinned
            binding.btnDelete.visibility = android.view.View.VISIBLE
        }

        binding.rowPinned.setOnClickListener {
            binding.switchPinned.toggle()
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            val label = binding.editLabel.text?.toString()?.ifBlank { null }
            val body = binding.editBody.text?.toString().orEmpty()
            val pinned = binding.switchPinned.isChecked

            if (body.isBlank()) {
                Toast.makeText(this, "本文を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = existingId
            if (id == null) {
                ClipStore.createManual(this, body, label, pinned)
                Toast.makeText(this, "作成しました", Toast.LENGTH_SHORT).show()
            } else {
                val item = ClipStore.getAll(this).firstOrNull { it.id == id }
                if (item != null) {
                    item.label = label
                    item.text = body
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
}
