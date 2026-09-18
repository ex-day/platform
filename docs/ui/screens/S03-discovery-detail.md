# S03 discovery detail
## 画面概要
指定されたDiscoveryの表示を行う

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名               | 物理名                                                 | 種別        | 繰り返し | 親 | データ元 | データ項目 | 対象端末    | 備考                                                          |
|----------------------|--------------------------------------------------------|-------------|----------|----|----------|------------|-------------|---------------------------------------------------------------|
| 共通ヘッダー         | → [C01](../components/C01-header.md)                   | component   | -        | -  | -        | -          | PC/モバイル |                                                               |
| タイトル             | discovery_title                                        | text        | -        | -  | API      | title      | PC/モバイル |                                                               |
| 本文                 | discovery_body                                         | text        | -        | -  | API      | body       | PC/モバイル |                                                               |
| リアクションボタン   | → [C10](../components/C10-reaction-button.md)          | component   | -        | -  | -        | -          | PC/モバイル |                                                               |
| 今のおすすめ         | → [C13](../components/C13-discovery-recommendation.md) | component   | 0..n     | -  | -        | -          | PC/モバイル |                                                               |
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
  - 現在のDiscovery Contextと価値の条件を評価する
    - 推薦条件に一致した価値：今のおすすめに表示する
    - 今回推薦対象とならなかった価値：その他の魅力に表示する
  - 派生元Discovery、派生したDiscovery、関連Discoveryを関係の意味に従って別々の枠に表示する。各枠は0..n件とし、該当がなければ表示しない
    - 派生元・派生先は、現在のDiscoveryを基準に明示的な派生関係の方向で判定する
    - 関連Discoveryには、派生と判定されない明示的な関連およびSubjectを介した意味的関連を表示する。共通Subjectだけから派生とみなさない
    - 各Discoveryの表示にはC03を再利用し、関係種別は枠の見出しで表す
- 知識・疑問一覧を見るを押下
    - S06: 知識・疑問一覧へ遷移

## 検討事項
- 場所をどのように表示するか(point/area)
- モバイルでも当面は3枠を維持し、ワイヤーフレームで見づらさを確認した場合に2枠化・折りたたみ・横スクロール等を検討する
- 見出しのユーザー向け文言はワイヤーフレームで検討する（例：「この発見のもとになったDiscovery」「この発見から広がったDiscovery」「関連するDiscovery」）
