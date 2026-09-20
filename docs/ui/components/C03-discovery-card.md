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
| 場所     | discovery_place   | text  | -        | -  | API      | place      | PC/モバイル | 地図を埋め込まずテキストで表示                                                                     |

## アクション
- 初期表示
  - 渡されたRecommendationに基づき、Discoveryおよび推薦対象となったValueを表示する
- card押下
  - [S03 discovery詳細](../screens/S03-discovery-detail.md)へ遷移

## 検討事項
- 写真に複数の写真が登録されていた場合、どれを表示するか今後検討
- PC・モバイルでの可視領域の差から要約の表示、文字数はデザイン時点で検討する
- データ取得元は実装設計で確定する
- [S03 Discovery詳細](../screens/S03-discovery-detail.md)のモバイルの関係Discovery 3枠では、横スクロールを採用する。そこでのカード幅・次カードの見せ方は、Next.jsモックおよびS02との整合確認後に決定する。件数に応じた表示と末尾の一覧導線の方針はS03を参照する。この方針をPCや他画面のカード表示へ一律に適用しない。
