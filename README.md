# ClipKeyboard

Gboard 等の標準キーボードは「クリップボード履歴を全部保存/管理する」用途には
不向き(件数上限や編集不可など)なので、**クリップボード管理専用のAndroidキーボード(IME)** として
一から実装したものです。

## できること

- システムのコピー操作を自動で監視し、履歴として保存(ピン留め・検索・削除)
- キーボード上からタップでそのままペースト(`InputConnection#commitText`)
- 長押しでピン留め切替・編集・削除を選べるクイックメニュー(ボトムシート)
- 保存済みクリップのテキスト編集・ラベル付け(ホストアプリの編集画面を開いて編集)
- メイン画面のFAB・キーボード上の「＋」から手動で定型文を新規作成
- テキストファイルのインポート(SAFのファイル選択 → 1行ずつ複数クリップ化 / 全文1件、を選択可)
- 配色・角丸・文字サイズなどテーマの編集(ライブプレビュー・カラーチップ付き、プリセット3種)
- 直前のキーボードへ確実に戻れる3段フォールバック付きのIME切替ボタン

UI/UXは「Tinted Glass UI Philosophy」という設計方針に基づいており、詳細は
[CHANGELOG.md](./CHANGELOG.md) の Unreleased 節を参照してください。

## プロジェクト構成

- `app/` — 単一モジュール。IMEサービス (`ime/ClipboardIMEService`)、データ層 (`data/`)、
  設定・編集用Activity (`ui/`)、テーマ (`theme/`) に分割。
- Room 等の追加ライブラリを使わず、`SharedPreferences` に JSON 配列として履歴を保存する
  軽量な自前ストア (`data/ClipStore.kt`) にしています。数百件規模のクリップ管理であれば
  十分な性能です。

## ビルド方法

```bash
./gradlew :app:assembleDebug
```

Android Studio で開いて実行するのが簡単です。`gradlew` は最小限のスタブなので、
初回は Android Studio の "Sync" 時に Gradle Wrapper を生成させるか、
`gradle wrapper --gradle-version 8.7` を実行してください。

## リリース署名について

`app/build.gradle.kts` は以下の優先順位でリリース鍵を探します。

1. Gradle プロジェクトプロパティ (`-PRELEASE_STORE_FILE=...` や `gradle.properties`)
2. 環境変数
3. 上記が無ければ **デバッグ鍵に自動フォールバック**し、ビルド自体は失敗させません
   (ログに警告を出します)。

参照するキー名は、**ローカルでビルドする場合**と**GitHub Actions上でビルドする場合**で
1つだけ異なる点に注意してください(ローカルは鍵ファイルのパスをそのまま渡すのに対し、
GitHub Actions はリポジトリシークレットに鍵ファイルを直接置けないため base64 文字列で
渡し、ワークフロー側が一時ファイルに復号してからパスを渡す、という違いがあります)。

**ローカルでビルドする場合**(4つとも環境変数 or Gradleプロパティとして指定):

| 変数名                     | 内容                                  |
|----------------------------|---------------------------------------|
| `RELEASE_STORE_FILE`       | `.jks`/`.keystore` ファイルへの**パス** |
| `RELEASE_STORE_PASSWORD`   | キーストアのパスワード                 |
| `RELEASE_KEY_ALIAS`        | キーのエイリアス                       |
| `RELEASE_KEY_PASSWORD`     | キーのパスワード                       |

**GitHub Actions で自動ビルドする場合**(以下4つをリポジトリシークレットとして登録。
`RELEASE_STORE_FILE` は登録不要 — ワークフローが `RELEASE_STORE_BASE64` を復号した
一時ファイルのパスを自動的に `RELEASE_STORE_FILE` として渡します):

| Secret name                | 内容                                    |
|-----------------------------|------------------------------------------|
| `RELEASE_STORE_BASE64`      | `.jks`/`.keystore` を **base64 エンコードした文字列**(改行なし。`base64 -w0 release.keystore` 等で生成) |
| `RELEASE_STORE_PASSWORD`    | キーストアのパスワード                   |
| `RELEASE_KEY_ALIAS`         | キーのエイリアス                         |
| `RELEASE_KEY_PASSWORD`      | キーのパスワード                         |

GitHub Actions (`.github/workflows/release.yml`) は `main` ブランチへの push、
`v*.*.*` タグの push、Pull Request(main向け)、Release作成(`release: created`)の
いずれでも自動的に `assembleRelease` を実行します。ただし GitHub Release への添付は
タグ push または Release作成イベントの場合のみです(通常pushではビルド確認のみ行い、
Actionsの Artifacts からAPKをダウンロードできます)。
リポジトリのデフォルトブランチ名が `main` 以外(例: `master`)の場合は
`.github/workflows/release.yml` の `branches: [ main ]` を書き換えてください。
上記4つのシークレットが揃っていれば本番署名で、揃っていなければデバッグ署名でAPKを
ビルドします。

## ライセンス

Apache License 2.0。


## 既知の制約 / 今後の課題

- `mipmap-*` のアプリアイコンPNGは同梱していません(ベクター版の適応アイコンのみ)。
  Android Studio の Image Asset 機能で好きなアイコンを生成してください。
- クリップボードの自動監視は、Android のバックグラウンド制限により
  「キーボードのプロセスが生きている間」に限られます(OSの一般的な制約です)。
- カラーピッカーは16進コード直接入力方式です。UIをより親切にしたい場合は
  `androidx.core:core` のカラーピッカー等を追加してください。
