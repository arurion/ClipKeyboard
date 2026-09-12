package dev.clipkeyboard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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

    private val storeListener = { refreshList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val theme = ThemeConfig.load(this)
        adapter = ClipAdapter(
            items = emptyList(),
            theme = theme,
            onTap = { openEdit(it) },
            onLongPress = { openEdit(it) }
        )
        binding.recyclerClips.layoutManager = LinearLayoutManager(this)
        binding.recyclerClips.adapter = adapter

        binding.btnImport.setOnClickListener { importLauncher.launch("text/*") }
        binding.btnTheme.setOnClickListener {
            startActivity(Intent(this, ThemeSettingsActivity::class.java))
        }
        binding.btnOverflowMenu.setOnClickListener { showOverflowMenu() }
        binding.bannerAction.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
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
        // テーマとデータを同時に再読込し、テーマ設定画面から戻った際の見た目未更新を防ぐ。
        val currentTheme = ThemeConfig.load(this)
        adapter.updateThemeAndItems(currentTheme, ClipStore.getAll(this))
        evaluateSetupStatus()
    }

    override fun onDestroy() {
        ClipStore.removeListener(storeListener)
        super.onDestroy()
    }

    private fun refreshList() {
        adapter.submit(ClipStore.getAll(this))
    }

    /** 本IMEが未有効・未選択の場合のみバナーを表示し、済んでいれば隠す。 */
    private fun evaluateSetupStatus() {
        val enabledImes = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS).orEmpty()
        val isEnabled = enabledImes.contains(packageName)

        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        val isSelectedIme = defaultIme.contains(packageName)

        binding.setupBanner.visibility = if (!isEnabled) android.view.View.VISIBLE else android.view.View.GONE
        if (isEnabled && !isSelectedIme) {
            binding.bannerTitle.text = "キーボードを切り替えましょう"
            binding.bannerBody.text = "入力欄を長押しし「入力方法を選択」からClipKeyboardを選んでください"
        }
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(this, binding.btnOverflowMenu)
        popup.menu.add("すべての履歴を消去(ピン留めを除く)")
        popup.setOnMenuItemClickListener {
            confirmClearAll()
            true
        }
        popup.show()
    }

    /** 破壊的操作なので二重確認を必須化する。 */
    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("ピン留め以外の履歴を消去しますか？")
            .setMessage("この操作は取り消せません。ピン留め済みのクリップは残ります。")
            .setPositiveButton("消去する") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("本当によろしいですか？")
                    .setMessage("最終確認です。ピン留め以外のすべての履歴が削除されます。")
                    .setPositiveButton("完全に消去する") { _, _ ->
                        ClipStore.clearAllUnpinned(this)
                        Toast.makeText(this, "ピン留め以外を削除しました", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
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
