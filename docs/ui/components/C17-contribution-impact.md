# C17 contribution impact
## 機能概要
投稿者本人に、知識・疑問がDiscoveryやValueの形成・向上へどう利用されたかを示す。確定していない成果や人数は断定しない。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名 | 物理名 | 種別 | 繰り返し | 親 | データ元 | データ項目 | 対象端末 | 備考 |
|--------|--------|------|----------|----|----------|------------|----------|------|
| Discovery形成への利用 | discovery_formation_impact | text/link | 0..n | - | API | discoveryFormationImpact | PC/モバイル | 確定した場合のみ表示 |
| Value形成・向上への利用 | value_impact | text/link | 0..n | - | API | valueImpact | PC/モバイル | 確定した場合のみ表示 |
| Discoveryにつながった人数 | discovery_reach | text | - | - | API | discoveryReach | PC/モバイル | 算出対象・期間と人数が確定した場合のみ表示 |
| 成果の未確定案内 | impact_pending | text | - | - | - | - | PC/モバイル | 表示できる成果がない場合は「現在確認中」等。成果がないと断定しない |

## アクション
- 初期表示時
  - 確定した成果だけを個別に表示する。Value成立前や関係・人数が未確定なら確定表現を避ける。
  - DiscoveryまたはValueへのリンクがある場合は対象の詳細へ遷移する。

## 検討事項
- 人数の算出方法、集計期間、Valueへの利用判定は別途定義する。
