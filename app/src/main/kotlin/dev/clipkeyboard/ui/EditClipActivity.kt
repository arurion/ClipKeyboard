package dev.clipkeyboard.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.clipkeyboard.data.ClipStore
import dev.clipkeyboard.databinding.ActivityEditClipBinding

class EditClipActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CLIP_ID = "extra_clip_id"
    }

    private lateinit var binding: ActivityEditClipBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditClipBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val clipId = intent.getStringExtra(EXTRA_CLIP_ID)
        val item = ClipStore.getAll(this).firstOrNull { it.id == clipId }
        if (item == null) {
            Toast.makeText(this, "対象のクリップが見つかりません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.editLabel.setText(item.label.orEmpty())
        binding.editBody.setText(item.text)
        binding.switchPinned.isChecked = item.pinned

        binding.btnSave.setOnClickListener {
            item.label = binding.editLabel.text?.toString()?.ifBlank { null }
            item.text = binding.editBody.text?.toString().orEmpty()
            item.pinned = binding.switchPinned.isChecked
            ClipStore.update(this, item)
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
            finish()
        }
        binding.btnDelete.setOnClickListener {
            ClipStore.delete(this, item.id)
            finish()
        }
    }
}
