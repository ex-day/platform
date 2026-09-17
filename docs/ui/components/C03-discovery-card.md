# C03 discovery-card
## 機能概要
Discoveryの推薦された魅力を要約して表示する。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名   | 物理名            | 種別  | 繰り返し | 親 | データ元 | データ項目 | 対象端末    | 備考                                                                                               |
|----------|-------------------|-------|----------|----|----------|------------|-------------|----------------------------------------------------------------------------------------------------|
| subject  | discovery_subject | text  | -        | -  | API      | subject    | PC/モバイル |                                                                                                    |
| 価値     | discovery_value   | text  | -        | -  | API      | value      | PC/モバイル |                                                                                                    |
| 写真     | discovery_picture | image | -        | -  | API      | picture    | PC/モバイル |                                                                                                    |
| タイトル | discovery_title   | text  | -        | -  | API      | title      | PC/モバイル |                                                                                                    |
| 場所     | discovery_place   | map   | -        | -  | API      | place      | PC/モバイル |                                                                                                    |

## アクション
- 初期表示
  - 渡されたRecommendationに基づき、Discoveryおよび推薦対象となったValueを表示する
- card押下
  - [S03 discovery詳細](../screens/S03-discovery-detail.md)へ遷移

## 検討事項
- 写真に複数の写真が登録されていた場合、どれを表示するか今後検討
- PC・モバイルでの可視領域の差から要約の表示、文字数はデザイン時点で検討する
- データ取得元は実装設計で確定する