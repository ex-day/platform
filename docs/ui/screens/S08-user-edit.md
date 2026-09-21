# S08 user edit
## 画面概要
ユーザー情報の更新を行う

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名             | 物理名                               | 種別       | 繰り返し | 親 | データ元 | データ項目     | 対象端末    | 備考 |
|--------------------|--------------------------------------|------------|----------|----|----------|----------------|-------------|------|
| 共通ヘッダー       | → [C01](../components/C01-header.md) | component  | -        | -  | -        | -              | PC/モバイル |      |
| ニックネーム       | user_nickname                        | text       | -        | -  | API      | nickname       | PC/モバイル |      |
| よく知っている地域 | user_area                            | text       | -        | -  | API      | area           | PC/モバイル |      |
| 興味のある地域     | user_interest_areas                  | text       | -        | -  | API      | interest_area  | PC/モバイル |      |
| 年代               | user_generation                      | text       | -        | -  | API      | generation     | PC/モバイル |      |
| 性別               | user_sex                             | text       | -        | -  | API      | sex            | PC/モバイル |      |
| 興味のあるジャンル | user_interest_genre                  | text       | -        | -  | API      | interest_genre | PC/モバイル |      |
| 更新               | -                                    | label/link | -        | -  | -        | -              | PC/モバイル |      |
| 削除               | -                                    | label/link | -        | -  | -        | -              | PC/モバイル |      |
| 共通フッター       | → [C02](../components/C02-footer.md) | component  | -        | -  | -        | -              | PC/モバイル |      |

## アクション
- 更新押下時
  - ニックネームが入力されていない場合エラーとする
  - 更新処理を実施
  - 更新後、更新したユーザー情報を表示する
- アカウント削除押下時
  - 警告を表示する
  - アカウント削除処理を実施
  - 処理端末にかかわらず S01 Discovery提案（サービスTOP）画面へ遷移
## 検討事項
- 投稿済み知識・疑問を削除するか等アカウント削除の仕様については検討課題とする
