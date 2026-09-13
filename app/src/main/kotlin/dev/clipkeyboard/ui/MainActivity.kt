package dev.clipkeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import dev.clipkeyboard.data.ClipItem
import dev.clipkeyboard.data.ClipStore
import dev.clipkeyboard.data.ClipboardWatcher
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

    private val storeListener = { refreshList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ClipboardWatcher.register(this)

        val theme = ThemeConfig.load(this)
        adapter = ClipAdapter(
            items = emptyList(),
            theme = theme,
            onTap = { item -> copyToClipboard(item) },
            onLongPress = { item -> openEdit(item) }
        )
        binding.recyclerClips.layoutManager = LinearLayoutManager(this)
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
        binding.btnOverflowMenu.setOnClickListener { showOverflowMenu() }
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, EditClipActivity::class.java))
        }

        ClipStore.addListener(storeListener)

        if (intent?.getStringExtra(EXTRA_ACTION) == ACTION_IMPORT) {
            importLauncher.launch("text/*")
        }
    }

    override fun onResume() {
        super.onResume()
        val currentTheme = ThemeConfig.load(this)
        adapter.updateThemeAndItems(currentTheme, ClipStore.getAll(this))
        updateImeStatusUi()
    }

    override fun onDestroy() {
        ClipStore.removeListener(storeListener)
        super.onDestroy()
    }

    private fun refreshList() {
        val items = ClipStore.getAll(this)
        adapter.submit(items)
        binding.textListHeader.text = "保存されたクリップ (${items.size}件)"
    }

    private fun copyToClipboard(item: ClipItem) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(item.label ?: "Clip", item.text)
        cm.setPrimaryClip(clip)
        val preview = if (item.text.length > 20) item.text.take(20) + "…" else item.text
        Toast.makeText(this, "コピーしました: $preview", Toast.LENGTH_SHORT).show()
    }

    private fun updateImeStatusUi() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val isEnabled = imm.enabledInputMethodList.any { it.packageName == packageName }

        if (isEnabled) {
            binding.btnEnableIme.text = "✓ 有効化済み (設定)"
            binding.btnEnableIme.alpha = 0.8f
        } else {
            binding.btnEnableIme.text = "① キーボード有効化"
            binding.btnEnableIme.alpha = 1.0f
        }
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(this, binding.btnOverflowMenu)
        popup.menu.add("ピン留め以外の履歴をすべて消去")
        popup.setOnMenuItemClickListener {
            confirmClearAll()
            true
        }
        popup.show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("履歴の全消去")
            .setMessage("ピン留めされていないクリップをすべて削除しますか？\nこの操作は取り消せません。")
            .setPositiveButton("消去する") { _, _ ->
                ClipStore.clearAllUnpinned(this)
                Toast.makeText(this, "ピン留め以外を消去しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun openEdit(item: ClipItem) {
        val intent = Intent(this, EditClipActivity::class.java)
        intent.putExtra(EditClipActivity.EXTRA_CLIP_ID, item.id)
        startActivity(intent)
    }

    private fun importFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val content = BufferedReader(InputStreamReader(input)).readText()
                if (content.isBlank()) {
                    Toast.makeText(this, "ファイルが空です", Toast.LENGTH_SHORT).show()
                    return
                }
                val fileName = queryDisplayName(uri) ?: "インポート"
                val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }

                if (lines.size > 1) {
                    AlertDialog.Builder(this)
                        .setTitle("インポート方法")
                        .setMessage("${lines.size} 行のテキストが見つかりました。")
                        .setPositiveButton("1行ずつ別々のクリップにする") { _, _ ->
                            lines.forEach { ClipStore.createManual(this, it, fileName) }
                            Toast.makeText(this, "${lines.size}件を追加しました", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("全文を1件のクリップにする") { _, _ ->
                            ClipStore.createManual(this, content, fileName)
                            Toast.makeText(this, "1件を追加しました", Toast.LENGTH_SHORT).show()
                        }
                        .show()
                } else {
                    ClipStore.createManual(this, content, fileName)
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
