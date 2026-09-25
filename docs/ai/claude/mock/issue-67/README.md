# Issue #67 モック確認のスクリーンショット

Claude Code（Maker）が、Issue #67のモック反映（`apps/web`）をローカルで確認した記録。`npm run build`のあと`next start`で起動し、Playwright（Chromium）で撮影した。PCは1280×860、モバイルは390×844。

| ファイル | 内容 |
|---|---|
| 01-pc-s01-header.png | C01（共通ヘッダー）の入口「話す」（ペンのアイコン＋ラベル） |
| 02-pc-modal-tag-suggest.png | S01の上に開いたC27（新しい話を始める）のモーダル。「#」のあとのタグの補完（F04） |
| 03-pc-modal-tag-highlight.png | 入力欄の中で本文中の#タグが強調されている状態。チップは出ない（Issue #67「判断：タグの入力と表示」で廃止） |
| 04-pc-modal-login.png | 未ログインで「送る」→ C08（ログイン認証ダイアログ）。入力内容は保持 |
| 05-pc-modal-after-post.png | ログイン後に投稿を確定し、投稿直後の案内（似たDiscoveryとわかってきたこと。F03のモック）。投稿した本文の#タグは入力欄と同じ見た目 |
| 06-pc-c07-menu.png | C07（ドロップダウンメニュー）。新規投稿の導線を外した |
| 07-pc-s03-top.png | S03（Discovery詳細）。C25（わかってきたこと）の価値・説と種類ごとのリアクション |
| 08-pc-s03-full.png | S03全体。Discoveryのタグ、C26（みんなの声）の返信先の引用・派生の目印・続きの投稿への注記 |
| 09-pc-s03-reply-suggestion.png | 派生した話題の声に返信しようとしたときの提案「続きは『〇〇』で話しませんか？」 |
| 10-pc-s03-findings-theories.png | 対立する説を並べた表示（sample-2） |
| 11-pc-s03-findings-backed.png | 裏付けのある説（リアクションなし、裏付けを表示）（exploring） |
| 12-pc-s03-derived-origin.png | 派生先の冒頭の経緯（sample-3） |
| 13-pc-s02-zero.png | S02（Discovery探索一覧）で範囲を拡大しても0件のときの「話す」の案内 |
| 14-pc-s02-zero-modal.png | 0件時の「話す」からモーダルを開いた状態 |
| 15-sp-s01-header.png | モバイルのヘッダーの「話す」 |
| 16-sp-modal-sheet.png | モバイルの全画面のシート（本文中の#タグの強調） |
| 17-sp-s03-full.png | モバイルのS03全体 |
| 18-pc-posts-new-direct.png | `/posts/new`を直接開いた場合（背景の画面なし） |
| 19-sp-modal-long-scrolled.png | スマホ幅で長文（長いURLを含む）を入力し、入力欄をスクロールした状態。強調の位置がずれない |
| 20-pc-s03-posts-and-input.png | S03の声の一覧の#タグと、みんなの声の入力欄の#タグが同じ見た目 |
| 21-pc-input-focus.png | 入力欄のフォーカス時の枠 |
| 22-pc-input-dark.png | ダークモード（`html.dark`）での強調 |
| 23-pc-input-ime-composing.png | 日本語の変換中（CDPの`Input.imeSetComposition`で再現）。変換中は強調の層を隠し、textareaの文字をそのまま表示するため、二重に見えない |
| 24-sp-input-placeholder.png | 空のときのプレースホルダー |
| 25-sp-input-long-url.png | スマホ幅で空白のない長いURLが折り返された状態 |

2026-09-25追記：タグのチップを廃止し、本文中の#タグを入力欄と投稿後で同じ見た目で強調する修正（Issue #67「判断：タグの入力と表示」）に合わせ、02・03・05・08・09・16・17を撮り直し、19〜25を追加した。

確認した動作：他の画面から「話す」で開くとURLは`/posts/new`になりモーダルで表示される／モーダル内のリンクで移動するとモーダルが閉じる／Escで開く前の画面へ戻る／旧URL（`/contributions/new`・`/contributions/new/confirm`・`/contributions/[id]/edit`）はリダイレクトされる／ブラウザのコンソールエラーなし。
