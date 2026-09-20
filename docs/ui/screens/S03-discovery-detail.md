# S03 discovery detail
## 画面概要
指定されたDiscoveryを、評価済みRecommendationに基づき「今ユーザーに最も伝えたい価値」を直感的に提示するHeroを中心に表示する。Heroは画像・映像がなくてもテキスト中心で成立する。Recommendationが0件の場合のHeroの扱いは未確定とする。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名               | 物理名                                                 | 種別        | 繰り返し | 親 | データ元 | データ項目 | 対象端末    | 備考                                                          |
|----------------------|--------------------------------------------------------|-------------|----------|----|----------|------------|-------------|---------------------------------------------------------------|
| 共通ヘッダー         | → [C01](../components/C01-header.md)                   | component   | -        | -  | -        | -          | PC/モバイル |                                                               |
| タイトル             | discovery_title                                        | text        | -        | -  | API      | title      | PC/モバイル |                                                               |
| Hero                 | → [Discovery詳細 Hero](../components/C19-discovery-detail-hero.md) | component | - | - | - | - | PC/モバイル | C19。今伝えたい価値をメッセージと任意の画像・映像で提示 |
| 本文                 | discovery_body                                         | text        | -        | -  | API      | body       | PC/モバイル |                                                               |
| リアクションボタン   | → [C10](../components/C10-reaction-button.md)          | component   | 3        | -  | -        | -          | PC/モバイル | 👍／😮／♥の複数リアクションを表示                             |
| その他の魅力         | → [C12](../components/C12-discovery-value.md)          | component   | 0..n     | -  | -        | -          | PC/モバイル |                                                               |
| 知識・疑問一覧を見る | show_contribution                                      | label/ link | -        | -  | 固定     | -          | PC/モバイル |                                                               |
| 派生元Discovery      | → [C03](../components/C03-discovery-card.md)           | component   | 0..n     | -  | -        | -          | PC/モバイル | 現在のDiscoveryの主たる起点。明示的な派生関係の派生元を表示   |
| 派生したDiscovery    | → [C03](../components/C03-discovery-card.md)           | component   | 0..n     | -  | -        | -          | PC/モバイル | 現在のDiscoveryから派生したDiscoveryを表示                    |
| 関連Discovery        | → [C03](../components/C03-discovery-card.md)           | component   | 0..n     | -  | -        | -          | PC/モバイル | 派生元・派生先以外の意味的関連を表示。Subject由来の関連を含む |
| 共通フッター         | → [C02](../components/C02-footer.md)                   | component   | -        | -  | -        | -          | PC/モバイル |                                                               |

## アクション
- 初期表示
  - 指定されたIDに紐付くDiscoveryを表示する
  - Discoveryに紐付く価値を取得する
  - 現在のDiscovery ContextとValueのConditionを、必要なDiscovery状態も参照して評価した結果を取得し、表示先へ渡す。評価ロジックはC19に持たせず、評価の実装配置は後続設計で定める
    - 現在成立するValue：評価済みRecommendationと表示に必要な情報をHeroに渡し、価値を直感的なメッセージで提示する
    - 現在成立しないValue：「その他の魅力」としてC12で表示する。順位が低いだけの成立Valueをこちらへ移さない
    - 評価に必要なContext・状態等が不足する場合：成立／不成立を推測しない。評価不能時の表示は検討事項とする
  - 画像・映像がある場合はHeroの主要な視覚情報として利用し、ない場合もValueを伝えるテキストを中心に構成する。Subject・Condition・Discovery状態は表示内容を決める入力情報として扱い、Heroに属性一覧として表示しない
  - 疑問状態では疑問から定義したValueを提示し、Contribution添付画像等とともに「一緒に考えませんか？」等の表現を可能とする
  - 複数RecommendationはHero内のサムネイルを選択して切替可能とする
  - Recommendationが0件の場合のHeroの表示有無・内容は検討事項とする。Discovery概要・状態による補完を確定せず、その他の魅力から推薦を補充しない
  - 派生元Discovery、派生したDiscovery、関連Discoveryを関係の意味に従って別々の枠に表示する。各枠は0..n件とし、該当がなければ表示しない
    - 派生元・派生先は、現在のDiscoveryを基準に明示的な派生関係の方向で判定する
    - 関連Discoveryには、派生と判定されない明示的な関連およびSubjectを介した意味的関連を表示する。共通Subjectだけから派生とみなさない
    - 各Discoveryの表示にはC03を再利用し、関係種別は枠の見出しで表す
- 知識・疑問一覧を見るを押下
    - S06: 知識・疑問一覧へ遷移

## 構成方針
- タイトルとHeroでDiscoveryの入口と現在の価値を示し、本文・リアクション・その他の魅力・知識／疑問への導線・関係するDiscoveryへつなぐ。詳細な配置はWireframeで検討する。
- 場所は地図を埋め込まず、テキストで表示する。
- リアクションは👍／😮／♥の3種類を併記し、ユーザーが複数の種類を付与できる構成とする。
- 「今のおすすめ」の独立枠は設けず、C13の責務をHeroへ統合する。C13は廃止記録として残し、IDを再利用しない。
- 「その他の魅力」は現在成立しないValueを対象とし、将来・別Contextで成立し得る条件を説明できるようにする。該当がなければ表示しない。
- 公開・推薦上の制約があるValueや評価不能のValueを、一律に「その他の魅力」へ流さない。表示可否・表示先は後続設計で定める。

### 関係するDiscoveryのモバイル表示（ワイヤーフレームレビュー結果）

#### 今回合意した方針
- モバイルでも「派生元Discovery」「派生したDiscovery」「関連Discovery」の3枠を維持する。該当がない枠は表示しない既存方針を継続する。
- 3枠のDiscoveryカード表示は、横スクロールとする。
- 一定件数までは横スクロール内にカードを表示し、表示対象が上限を超える場合は末尾に「さらに見る」に相当する導線を置き、該当関係のDiscovery一覧へ遷移できる構成を検討する。
- 1件など少数件の場合は、無理に横スクロールらしい表現にせず、件数に応じた自然な表示を許容する。
- このレビュー結果はモバイルを対象とし、PC側の表示方式は変更しない。

#### 未確定事項
以下はNext.jsモックおよび[S02 Discovery一覧](./S02-discovery-list.md)との整合確認後に決定する。横スクロールを採用する方針自体は変更しない。
- 横スクロール内に表示するカードの上限n件
- [C03 Discoveryカード](../components/C03-discovery-card.md)の幅・次カードの見せ方
- 末尾の「さらに見る」に相当する導線の具体表現
- 遷移先をS02の条件付き表示として再利用するか、関係Discovery専用の一覧とするか

## 検討事項
- HeroのRecommendation初期選択、表示順、画像・映像なしの場合と疑問状態の構成
- 現在Contextの基準と再評価契機、Recommendation 0件・評価不能・取得失敗時の表示
- 評価済みRecommendationからHeroの文言・Mediaを用意する担当と入力契約、公開・推薦上の制約と表示の優先関係
- 関係するDiscoveryの3分類の概念は維持する。一方、見出し「この発見のもとになったDiscovery」「この発見から広がったDiscovery」「関連するDiscovery」が一般ユーザーに分かりやすいかは、実データに近いMock等で後続検討する。Non-blocking事項とし、現工程および次工程への引き渡しを止めない。
