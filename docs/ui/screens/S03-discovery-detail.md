# S03 discovery detail
## 画面概要
指定されたDiscoveryの表示を行う

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名               | 物理名                                        | 種別        | 繰り返し | 親 | データ元 | データ項目 | 対象端末    | 備考               |
|----------------------|-----------------------------------------------|-------------|----------|----|----------|------------|-------------|--------------------|
| 共通ヘッダー         | → [C01](../components/C01-header.md)          | component   | -        | -  | -        | -          | PC/モバイル |                    |
| テーマ               | discovery_theme                               | text        | -        | -  | API      | theme      | PC/モバイル |                    |
| subject              | discovery_subject                             | text        | -        | -  | API      | subject    | PC/モバイル |                    |
| 時間・季節           | discovery_time                                | text        | -        | -  | API      | time       | PC/モバイル | 朝昼夕・春夏秋冬等 |
| 画像                 | discovery_images                              | image       | 0..n     | -  | API      | images     | PC/モバイル |                    |
| 場所                 | discovery_place                               | map         | -        | -  | API      | place      | PC/モバイル |                    |
| 本文                 | discovery_body                                | text        | -        | -  | API      | body       | PC/モバイル |                    |
| 知識・疑問一覧を見る | show_contribution                             | label/ link | -        | -  | 固定     | -          | PC/モバイル |                    |
| リアクションボタン   | → [C10](../components/C10-reaction-button.md) | component   | -        | -  | -        | -          | PC/モバイル |                    |
| 関連Discovery        | → [C03](../components/C03-discovery-card.md)  | component   | -        | -  | -        | -          | PC/モバイル |                    |
| 共通フッター         | → [C02](../components/C02-footer.md)          | component   | -        | -  | -        | -          | PC/モバイル |                    |

## アクション
- 知識・疑問一覧を見るを押下
    - S06: 知識・疑問一覧へ遷移

## 検討事項
- 場所をどのように表示するか(point/area)