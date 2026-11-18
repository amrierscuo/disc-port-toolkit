# Disc Port Toolkit | クイックスタート

[English](../../README.md) · [Italiano](it.md) · [Français](fr.md) · [Deutsch](de.md) · [Español](es.md) · 日本語

お持ちの『BLiNX: The Time Sweeper』PAL 版ディスクを使って、Windows 用の Clone 1 または 2 種類の Android 版のいずれかを個人用にビルドできます。

| | 必要なファイル | 機能 |
| --- | --- | --- |
| Windows (Clone 1) | ご自身の完全な PAL 版ディスクを使用してください。 | 手元の PAL 版のゲームファイルを使って、Windows で Clone 1 を起動できます。 |
| Android Original | ご自身の完全な PAL 版ディスクを使用してください。 | PAL 版のゲームを直接起動し、画面上のタッチパッドで操作できます。 |
| Android Full | PAL 版と OXM12 体験版のディスク全体、画面や音楽などの非公開リソース 263 個、ARM64 ライブラリ 2 個、ローカルの署名鍵。 | メニューから「Original Game」と OXM12 体験版を選べます。 |

## ビルド手順

64 ビット版 Windows でツールキットのフォルダーを開き、`START_HERE.cmd` をダブルクリックします。Windows Clone 1 または Android Original を選びます。PAL 版のゲームパーティションのイメージを選びます。ディスクを展開済みの場合は、ツールキットの外でも別のドライブでも、そのままの場所で使えます。ファイル選択画面でディスクのフォルダーを開き、`media/` と同じ階層にある `default.xbe` を選びます。`media/` 内のすべてのファイルとサブフォルダーは元の構成のままにしてください。

時間のかかる処理には、それぞれ進捗バーが表示されます。~ が付いた割合は推定値です。ツールからの出力がない間も経過時間は更新されます。処理が正常に完了すると 100% になります。

Windows ではゲームが起動します。Android では生成された APK を端末にコピーして開きます。

ディスクの検証に失敗した場合は、ツールキットを更新して同じディスクでもう一度お試しください。インストール済みのツールは再利用されます。 [トラブルシューティング](../TROUBLESHOOTING.md#pal-disc-files-differ-from-the-reference)

| | |
| --- | --- |
| Windows | `START_HERE.cmd windows "D:\Discs\Blinx.iso"` |
| Android Original | `START_HERE.cmd android-original "D:\Discs\Blinx.iso"` |
| Android Full | `build_android_full.cmd` |

Android Original では、Pillow 12.0.0 が PAL 版ディスクのテクスチャ 2 枚からタッチパッド用の画像 20 枚を生成します。

[言語を選べるプレビュー](../../preview/index.html) · [技術資料（英語）](../../README.md) · [ビルドの詳細](../FINAL_AUDIT.md) · [ライセンス](../../LICENSES.md)
