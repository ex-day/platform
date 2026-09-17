# C13 discovery recommendation
## 機能概要
discoveryに含まれる現在の魅力を紹介するコンポーネント。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名       | 物理名                            | 種別      | 繰り返し | 親 | データ元 | データ項目           | 対象端末    | 備考 |
|--------------|-----------------------------------|-----------|----------|----|----------|----------------------|-------------|------|
| 魅力         | → [C12](./C12-discovery-value.md) | component | -        | -  | -        | -                    | PC/モバイル |      |
| おすすめ理由 | recommendation_reason             | text      | -        | -  | API      | recommendationReason | PC/モバイル |      |

## アクション
- 初期表示
  - 渡されたRecommendationを表示する
  - Recommendationに紐付くValueをC12で表示する
  - Recommendationのおすすめ理由を表示する

## 検討事項
なし