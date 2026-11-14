# S06 知識・疑問一覧（廃止）

> **廃止（2026-11-14）**。IDは欠番とし、再利用しない。本ファイルは過去の参照のために残す。以下の「廃止前の定義」は当時の記録であり、現在の仕様ではない。

## 廃止の理由と代わり
- Discovery に紐付く知識・疑問の一覧という前提がなくなった（DEC-0020 決定4）。会話の一覧のページは持たない。
- 代わり：S13（場所のページ）・S15（企画のページ）・S09（自分の話）・S02（探す）
- 関係するDEC／Issue：[DEC-0020](../../decisions/DEC-0020-place-conversation-discovery.md)、[DEC-0011](../../decisions/DEC-0011-id-numbering-rules.md)、[Issue #135](https://github.com/ex-day/platform/issues/135)

## 廃止前の定義

（元の見出し：S06 contribution list）

### 画面概要
S03で表示中のDiscoveryに紐付く知識・疑問の一覧を表示する。自分が登録した知識・疑問全体の履歴や確認が必要な項目は、[S09 自分の投稿一覧](./S09-user-contribution-list.md)で扱う。

### 対象端末
- PC
- モバイル

### 表示項目
| 論理名         | 物理名                                       | 種別      | 繰り返し | 親           | データ元 | データ項目    | 対象端末    | 備考 |
|----------------|----------------------------------------------|-----------|----------|--------------|----------|---------------|-------------|------|
| 共通ヘッダー   | → [C01](../components/C01-header.md)         | component | -        | -            | -        | -             | PC/モバイル |      |
| discovery card | → [C03](../components/C03-discovery-card.md) | component | -        | -            | -        | -             | PC/モバイル |      |
| 知識・疑問行   | contribution_list                            | -         | 1..n     | -            | API      | contributions | PC/モバイル |      |
| サムネイル画像 | contribution_thumbnail                       | image     | -        | 知識・疑問行 | API      | thumbnail     | PC/モバイル |      |
| 本文           | contribution_body                            | text      | -        | 知識・疑問行 | API      | body          | PC/モバイル |      |
| 共通フッター   | → [C02](../components/C02-footer.md)         | component | -        | -            | -        | -             | PC/モバイル |      |

### アクション
- 初期表示時
  - 本文が存在した場合、対象端末によって本文の一部を表示する
- 行押下
  - S05 知識・疑問詳細画面へ遷移する

### 検討事項
- S06はDiscovery配下の一覧として維持する。Discoveryに未紐付けの知識・疑問を含む本人の一覧はS09で扱う。
