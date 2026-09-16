# S05 contribution detail
## 画面概要
知識・疑問の詳細を表示する。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名             | 物理名                                        | 種別      | 繰り返し | 親 | データ元 | データ項目 | 対象端末    | 備考 |
|--------------------|-----------------------------------------------|-----------|----------|----|----------|------------|-------------|------|
| 共通ヘッダー       | → [C01](../components/C01-header.md)          | component | -        | -  | -        | -          | PC/モバイル |      |
| 投稿種別           | contribution_type                             | -         | -        | -  | API      | type       | PC/モバイル |      |
| 画像               | contribution_picture                          | image     | -        | -  | API      | picture    | PC/モバイル |      |
| 場所               | contribution_place                            | map       | -        | -  | API      | place      | PC/モバイル |      |
| 時間               | contribution_time                             | text      | -        | -  | API      | time       | PC/モバイル |      |
| 季節               | contribution_season                           | text      | -        | -  | API      | season     | PC/モバイル |      |
| 本文               | contribution_body                             | text      | -        | -  | API      | body       | PC/モバイル |      |
| リアクションボタン | → [C10](../components/C10-reaction-button.md) | component | -        | -  | -        | -          | PC/モバイル |      |
| 編集               | -                                             | button    | -        | -  | -        | -          | PC/モバイル |      |
| 共通フッター       | → [C02](../components/C02-footer.md)          | component | -        | -  | -        | -          | PC/モバイル |      |

## アクション
- 初期表示時
  - 認証状態であり、本人投稿のcontributionだった場合
    - 編集を表示する
- 編集押下
  - S04 知識・疑問登録／編集へ編集モードで遷移する

## 検討事項
なし
