package dev.clipkeyboard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.clipkeyboard.R
import dev.clipkeyboard.data.ClipItem
import dev.clipkeyboard.data.ClipStore
import dev.clipkeyboard.databinding.ActivityMainBinding
import dev.clipkeyboard.ime.ClipAdapter
import dev.clipkeyboard.theme.ThemeConfig
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_IMPORT = "action_import"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ClipAdapter

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFile(it) }
    }

    private val storeListener = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val theme = ThemeConfig.load(this)
        adapter = ClipAdapter(
            items = emptyList(),
            theme = theme,
            onTap = { /* 一覧管理画面ではタップ=編集を開く */ openEdit(it) },
            onLongPress = { openEdit(it) },
            onPinToggle = { ClipStore.togglePin(this, it.id) },
            onDelete = { ClipStore.delete(this, it.id) }
        )
        binding.recyclerClips.layoutManager = LinearLayoutManager(this)
        binding.recyclerClips.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.recyclerClips.adapter = adapter

        binding.btnEnableIme.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        binding.btnPickIme.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        binding.btnImport.setOnClickListener { importLauncher.launch("text/*") }
        binding.btnTheme.setOnClickListener {
            startActivity(Intent(this, ThemeSettingsActivity::class.java))
        }
        binding.btnClearAll.setOnClickListener {
            ClipStore.clearAllUnpinned(this)
            Toast.makeText(this, "ピン留め以外を削除しました", Toast.LENGTH_SHORT).show()
        }

        ClipStore.addListener(storeListener)
        refresh()

        if (intent?.getStringExtra(EXTRA_ACTION) == ACTION_IMPORT) {
            importLauncher.launch("text/*")
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        ClipStore.removeListener(storeListener)
        super.onDestroy()
    }

    private fun refresh() {
        adapter.submit(ClipStore.getAll(this))
    }

    private fun openEdit(item: ClipItem) {
        val intent = Intent(this, EditClipActivity::class.java)
        intent.putExtra(EditClipActivity.EXTRA_CLIP_ID, item.id)
        startActivity(intent)
    }

    /** テキストファイルの中身を丸ごと1件のクリップとして読み込む。行ごとに分けたい場合は「行単位」オプションも用意。 */
    private fun importFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val reader = BufferedReader(InputStreamReader(input))
                val content = reader.readText()
                if (content.isBlank()) {
                    Toast.makeText(this, "ファイルが空です", Toast.LENGTH_SHORT).show()
                    return
                }
                val fileName = queryDisplayName(uri) ?: "インポート"
                val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }

                if (lines.size > 1) {
                    // 複数行ある場合は「1行=1クリップ」と「全文まとめて1クリップ」を選ばせる
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("インポート方法")
                        .setMessage("${lines.size} 行のテキストが見つかりました。")
                        .setPositiveButton("1行ずつ別々のクリップにする") { _, _ ->
                            lines.forEach { ClipStore.addOrTouch(this, it, fileName) }
                            Toast.makeText(this, "${lines.size}件を追加しました", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("全文を1件のクリップにする") { _, _ ->
                            ClipStore.addOrTouch(this, content, fileName)
                            Toast.makeText(this, "1件を追加しました", Toast.LENGTH_SHORT).show()
                        }
                        .show()
                } else {
                    ClipStore.addOrTouch(this, content, fileName)
                    Toast.makeText(this, "追加しました", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "読み込みに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) return it.getString(idx)
        }
        return null
    }
}
