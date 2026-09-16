# C03 discovery-card
## 機能概要
discoveryの要約を表示する。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名   | 物理名            | 種別 | 繰り返し | 親 | データ元 | データ項目 | 対象端末    | 備考 |
|----------|-------------------|------|----------|----|----------|------------|-------------|------|
| テーマ   | discovery_theme   | text | -        | -  | API      | theme      | PC/モバイル |      |
| 写真     | discovery_picture | file | -        | -  | API      | picture    | PC/モバイル |      |
| タイトル | discovery_title   | text | -        | -  | API      | title      | PC/モバイル |      |
| 要約     | discovery_summary | text | -        | -  | API      | summary    | PC/モバイル |      |

## アクション
- 初期表示
  - 指定されたdiscovery idに基づいたDiscoveryを取得し表示する
- card押下
  - [S03 discovery詳細](../screens/S03-discovery-detail.md)へ遷移

## 検討事項
- ジャンルに何を表示するか、いくつ表示するかを今後検討
- 写真に複数の写真が登録されていた場合、どれを表示するか今後検討
- PC・モバイルでの可視領域の差から要約の表示、文字数はデザイン時点で検討する
- データ取得元は実装設計で確定する