# C17 contribution impact
## 機能概要
投稿者本人に、知識・疑問が複数のDiscoveryへ与えたImpactをまとめて示すセクション。DiscoveryごとのImpactはC20で表現し、対象Discoveryと、そのDiscoveryへ提供したValueおよび利用結果の対応を崩さない。確定していない成果や人数は断定しない。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名 | 物理名 | 種別 | 繰り返し | 親 | データ元 | データ項目 | 対象端末 | 備考 |
|--------|--------|------|----------|----|----------|------------|----------|------|
| DiscoveryごとのImpact | → [C20](C20-discovery-impact.md) | component | 0..n | - | API | discoveryImpacts | PC/モバイル | このContributionがImpactを与えたDiscoveryごとに1件。確定した関係・成果のみを渡す |
| 成果の未確定案内 | impact_pending | text | - | - | - | - | PC/モバイル | 表示できる成果がない場合は「現在確認中」等。成果がないと断定しない |

## アクション
- 初期表示時
  - 確定したDiscoveryとの関係をDiscovery単位にまとめ、C20を0..n件表示する。同じDiscoveryの複数Valueへ利用された場合は、一つのC20内でValueごとの対応を保持する。
  - Value成立前・未特定またはDiscovery全体への寄与で対象Valueがない場合は、全Valueへ寄与したように見せない。表示できる確定成果がない場合は成果の未確定案内を表示する。
  - C20が複数ある場合の並び順は、確定していない順位やImpactの大小を示すものとして扱わない。

## 検討事項
- C20が複数ある場合の並び順とセクション内の要約方法はWireframeで検証する。
- Discovery単位のImpactを取得する具体的なAPI契約は、ContributionDiscoveryとの対応を保ったままAPI設計で定める。
