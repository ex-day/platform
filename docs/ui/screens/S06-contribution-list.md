# S06 contribution list
## 画面概要
S03で表示中のDiscoveryに紐付く知識・疑問の一覧を表示する。自分が登録した知識・疑問全体の履歴や確認が必要な項目は、[S09 自分の投稿一覧](./S09-user-contribution-list.md)で扱う。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名         | 物理名                                       | 種別      | 繰り返し | 親           | データ元 | データ項目    | 対象端末    | 備考 |
|----------------|----------------------------------------------|-----------|----------|--------------|----------|---------------|-------------|------|
| 共通ヘッダー   | → [C01](../components/C01-header.md)         | component | -        | -            | -        | -             | PC/モバイル |      |
| discovery card | → [C03](../components/C03-discovery-card.md) | component | -        | -            | -        | -             | PC/モバイル |      |
| 知識・疑問行   | contribution_list                            | -         | 1..n     | -            | API      | contributions | PC/モバイル |      |
| サムネイル画像 | contribution_thumbnail                       | image     | -        | 知識・疑問行 | API      | thumbnail     | PC/モバイル |      |
| 本文           | contribution_body                            | text      | -        | 知識・疑問行 | API      | body          | PC/モバイル |      |
| 共通フッター   | → [C02](../components/C02-footer.md)         | component | -        | -            | -        | -             | PC/モバイル |      |

## アクション
- 初期表示時
  - 本文が存在した場合、対象端末によって本文の一部を表示する
- 行押下
  - S05 知識・疑問詳細画面へ遷移する

## 検討事項
- S06はDiscovery配下の一覧として維持する。Discoveryに未紐付けの知識・疑問を含む本人の一覧はS09で扱う。
