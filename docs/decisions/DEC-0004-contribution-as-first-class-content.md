# DEC-0004: Contributionを単独でも価値を持つコンテンツとして扱う

## Status

Accepted

## Context

Contributionは、既存Discoveryへ情報を加えたり、複数のContribution・疑問・会話の蓄積から新しいDiscoveryを形成したりする材料となる。

一方、Contributionには、場所・体験時期等が不明な資料、まだ関係するDiscoveryを特定できない疑問や知識、既存Discoveryとの関係を投稿時点では判断できない情報も含まれる。これらをDiscoveryの成立・更新または関連付けが完了するまで投稿できないものとすると、不完全な資料や疑問をコンテンツ形成の入口として受け入れるex-dayの方針と矛盾する。

また、ContributionがDiscoveryの材料になることだけを重視すると、投稿者が提供した原文・資料・疑問そのものの価値が見えにくくなり、Discoveryへ変換できないContributionを価値のないものとして扱う設計へ戻るおそれがある。

このため、ContributionとDiscoveryの位置付け、および投稿時の関連付けの必須性を本Decisionで定める。

## Decision

1. **Contributionは、Discoveryの成立・更新を必須とせず、Contribution単独でも成立し価値を持つコンテンツとして扱う。** 設計上の短い表現として、この方針を**「Contributionも主役」**と呼ぶ。
2. ex-dayは、Contributionが既存Discoveryの更新または新しいDiscoveryの成立へ還元されることを促す。ただし、Discoveryへの関連付けをContribution投稿の成立条件にはしない。
3. 投稿時点で関連するDiscoveryが存在しない、見つからない、または判断できない場合も、Contributionを関連先未指定のまま投稿できるようにする。
4. ContributionとDiscoveryの関係は投稿時点だけで確定するものとせず、Contributionの蓄積、他のユーザーによる情報追加、調査、運営上の確認等を経て、後からDiscoveryの成立・更新へ還元できるものとする。

## Reason

- 疑問、不完全な資料、地域の記憶・体験は、それ単独でも他のユーザーが情報を追加し、調査や議論へ参加する入口になる。
- Contributionを先に受け入れることで、投稿時点では不足していた情報が後から補われ、複数のContributionからDiscoveryが形成・更新される循環を維持できる。
- Discoveryへの変換や関連付けを投稿成立条件にすると、投稿者へDiscovery形成のための情報補完や判断を強制し、気軽な投稿と情報提供の動機を損なう。
- Contributionの原文・資料・疑問を独立したコンテンツとして保持することで、後から解釈や関連先が変わっても、投稿者が提供した内容そのものを失わずに扱える。

## Alternatives

- **投稿時に既存Discoveryへの関連付けを必須とする案**：関連先がまだ存在しない、見つからない、または判断できないContributionを受け入れられず、不採用。
- **関連するDiscoveryがない場合に新規Discoveryの成立を必須とする案**：一つのContributionだけでDiscoveryを機械的に成立させ、類似Discoveryの乱立や情報不足のままの一般化を招くため、不採用。
- **Discoveryへの還元を行わず、Contributionを完全に独立して扱う案**：Contributionの蓄積からDiscoveryが成立・更新され、別のユーザーの発見へつながるex-dayの循環を弱めるため、不採用。

## Consequences

- 類似Discoveryの検索結果は`0..n`件とし、`0件`も正常系として扱う。
- 類似Discoveryが存在しない、または見つからない場合も、Contributionを投稿できる。
- 場所、対象年代・時期、体験時期等の情報が不足していても、Discoveryを成立させるために入力や選択を強制しない。
- AIは、不足情報を推測・創作して補い、ContributionをDiscoveryの成立へ寄せない。候補を提示する場合も、ユーザーが提供した情報と推定を区別する。
- 類似Discoveryがある場合は、関連付けやDiscoveryへの還元をUX上で促す。ただし、候補の選択や関連付けを必須にはしない。
- ContributionからDiscoveryの成立・更新への還元は、投稿時だけでなく投稿後にも行えるようにする。
- S05やS09等では、Discoveryに未紐付けのContributionも、原文・資料・疑問とその現在状態を確認できる対象として扱う。
- S11の定義を整合する際は、関連Discovery候補が`0件`の場合と、候補があっても関連付けずに投稿する場合を正常なフローとして扱う。

## Deferred / Open Questions

- S04からS11へ進む際にAI解析を呼び出すタイミングと画面間の責務境界は、DEC-0003および後続の画面・Function設計で扱い、本Decisionでは変更しない。
- Contributionの自然文解析、類似Discovery検索、候補の確度判定等の具体的な実装方式は、本Decisionでは確定しない。
- Mediaの安全性Validation、モデレーションおよび投稿可否判定を実行するタイミングは、本Decisionでは確定しない。
- 投稿後にContributionをDiscoveryの成立・更新へ還元する主体、判断手順、権限および通知方法は、後続設計で定める。
- Discoveryへ未紐付けのContributionを、投稿者本人以外へどの範囲で表示・探索可能にするかは、後続設計で定める。

## Revisit Conditions

- Contribution単独では投稿者または閲覧者へ価値を提供できず、投稿体験や情報品質に重大な問題が生じることが検証で明らかになった場合
- Contributionを後からDiscoveryへ還元する運用または仕組みが成立せず、未紐付けContributionが継続的に活用されないことが明らかになった場合
- S11 Wireframe（Issue #40）またはContribution／Discoveryの変換設計で、本Decisionの前提と矛盾する制約が見つかった場合

## Related Issues / PRs

- Issue #40（S11確認項目を最新のS04入力定義へ整合する）
- DEC-0002（Contributionの時間情報を解釈前のContextとして取得する）
- DEC-0003（S04/S11とContribution AI解析の責務境界）
