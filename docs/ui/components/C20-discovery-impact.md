# C20 discovery impact
## 機能概要
投稿者本人に、このContributionが一つのDiscoveryへ与えたImpactを1単位として示す、意味のあるComponent。単なるレイアウト枠ではなく、対象Discovery、そのDiscoveryへ提供したValue、Discovery形成またはValue形成・向上への利用結果、当該Contribution由来のValueを通じてそのDiscoveryにつながった人数を対応付けて表現する。確定していない成果や人数は断定しない。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名 | 物理名 | 種別 | 繰り返し | 親 | データ元 | データ項目 | 対象端末 | 備考 |
|--------|--------|------|----------|----|----------|------------|----------|------|
| 対象Discovery | target_discovery | link | - | - | API | discovery | PC/モバイル | このImpactの対象となる確定済みDiscovery。各C20の識別情報／見出しとして扱う。具体的な表示形式は固定せず、S03へのリンクであることを維持する |
| Discovery形成への利用結果 | discovery_formation_impact | text | 0..n | - | API | discoveryFormationImpacts | PC/モバイル | 当該Discoveryの形成に利用されたことが確定した場合のみ表示 |
| 提供したValue | provided_value | text/link | 0..n | - | API | providedValues | PC/モバイル | 当該ContributionがこのDiscoveryで形成・向上に利用されたValue。Valueごとの利用結果と対応付ける |
| Value形成・向上への利用結果 | value_impact | text | 0..n | 提供したValue | API | valueImpacts | PC/モバイル | 対象Valueへの利用内容が確定した場合のみ表示。同じValueに複数の確定した利用結果がある場合に対応 |
| Discoveryにつながった人数 | discovery_reach | text | 0..1 | - | API | discoveryReach | PC/モバイル | 当該Contribution由来のValueを通してこのDiscoveryを紹介できた人数。算出対象・集計期間・人数が確定した場合のみ表示 |
| Value未成立・未特定の案内 | value_pending | text | 0..1 | - | API | valueAssignmentStatus | PC/モバイル | Discoveryとの関係は確定しているが対象Valueが未成立・未特定の場合に表示。Valueがない、または全Valueへの寄与とは表現しない |

## 表示単位・制約
- 一つのC20は一つのContributionと一つのDiscoveryの組み合わせを表す。同じContributionが複数DiscoveryへImpactを与えた場合は、C17配下にDiscoveryごとのC20を表示する。
- C17のユーザー向け見出しは「この投稿から見つかった価値」とする。各C20には「DiscoveryごとのImpact」「Discovery Impact」等の内部構造名を固定見出しとして表示せず、対象Discoveryを各表示単位の識別情報／見出しとして扱う。
- ContributionDiscoveryに対象Valueがある場合、そのValueは対象Discoveryに属するものだけを表示する。同じDiscoveryの複数Valueへ利用された場合は、提供したValueを0..n件表示し、それぞれの利用結果との対応を保つ。
- 対象Valueなしは、Value未成立・未特定またはDiscovery全体への寄与を含み得る。全Valueへの寄与と解釈せず、確定した状態の範囲だけを表示する。
- Discoveryにつながった人数を、Contribution全体や別Discoveryの人数と合算した単独指標として表示しない。
- UIの具体的なカード形状や、対象Discoveryを単純なテキストリンク、簡易サマリー等のどの形式で表すかは定義しない。Discovery・Value・利用結果・人数のまとまりと対応関係をWireframeで検証できる構造に留める。対象DiscoveryをDiscovery Cardへ変更することも本定義では行わない。

## アクション
- 初期表示時
  - 対象Discoveryと、確定した利用結果を表示する。未確定のValue、利用結果、人数は確定表現を避ける。
  - Discoveryにつながった人数を表示する場合は、算出対象と集計期間がユーザーに誤解なく伝わる情報を併記する。
- 対象Discovery押下時
  - [S03 Discovery詳細](../screens/S03-discovery-detail.md)へ遷移する。
- 提供したValueに遷移先が定義されている場合
  - 対象Discovery内の該当Valueを確認できる表示へ遷移する。具体的なリンク方法は実装設計で定める。

## 検討事項
- Discovery形成・Value形成・向上に関する利用結果の表示文言と、未成立・未特定・Discovery全体への寄与を区別する文言。
- Discoveryにつながった人数の算出方法、対象となるValue経由の識別方法、集計期間、重複ユーザーの扱い。
- 複数Valueと各利用結果の見せ方、Valueへのリンク方法はWireframeおよび実装設計で検証する。
- 対象Discoveryの具体的な表示形式と、C15「Discovery関連情報」で同一Discoveryが重複表示される場合の見せ方は、次回のS05 Wireframeで検証する。C15の統合・削除は本定義では行わない。
