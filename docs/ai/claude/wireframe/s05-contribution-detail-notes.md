# S05 知識・疑問詳細 Wireframe 作成メモ (Issue #17 用)

担当: Claude (Maker) / Reviewer: 未実施(別AIレビュー待ち) / 人間レビュー: 未実施

## 成果物

正式成果物(`docs/ui/wireframe/`配下)

- `screens/S05/PC-Main.png` 通常閲覧(知識)。C14 + C10 + C15
- `screens/S05/PC-OwnCheck.png` 自分の確認(疑問, C18あり)。C14 + C15 + C16 + C17 + C18
- `screens/S05/Mobile-Main.png` / `Mobile-OwnCheck.png` 上記のMobile版
- `screens/S05/PC-StateVariants.png` / `Mobile-StateVariants.png` 状態差・配置の比較案
- `components/C10-reaction-button-s05-variants.png` (既存の`C10-reaction-button-variants.png`とは別ファイル)
- `components/C14-contribution-content-variants.png`
- `components/C15-related-discovery-variants.png`
- `components/C16-contribution-status-states.png`
- `components/C17-contribution-impact-states.png`
- `components/C18-contribution-confirmation-states.png`

Main採用案は「案1」表記。他案の採否は人間レビューで決定する。

## 設計書確認結果

**Blocking事項: なし。** 以下はすべてNon-blocking。

1. 「自分の確認」経路で、投稿者以外が開いた場合のC10表示が未定義。Wireframeでは通常閲覧のみC10を配置し、C16/C17/C18は投稿者のみ表示とした。
2. 未ログイン投稿(未公開)のS05表示が未定義。S11保存後にS05へ戻る際、通常閲覧か自分の確認かも未定義。
3. 疑問への回答・議論のUI/仕様が設計書にない(C10のみ)。知識/疑問の差は種別ラベルとC16のステータス文言以外に記載がなく、Wireframeも同程度の差にとどめた。
4. C15〜C18のセクション見出し文言が未定義(プレースホルダー使用)。
5. C14の場所表示(テキストか地図か)が未確定。テキスト+ピンで表現。
6. 自分の確認のレイアウト(1カラム/2カラム、Mobile: C18上部固定/下部固定)は比較案として提示。人間選択待ち。
7. 繰り越し: S02概要の「mobile = TOP」の不整合(S01/C11と食い違い)。

## S04(登録・編集)への引き継ぎ事項

- C14に表示する項目(種別, 本文, 写真, 添付, 場所, 時期, 季節)はS04の入力項目と対応させる必要がある。「投稿者が指定した内容」と「後から変わる解釈」は分けて表示している。
- C16 → S04(編集)、C18 → S04 → S11 の導線で、確認対象(場所/関連Discovery/情報補足)をS04側へ渡す仕様が必要。
- 未ログイン投稿の紐付け・公開フロー(S11/C08)とS05の表示状態の整合。
- 疑問投稿の入力項目のうち、知識との差(回答受付など)の有無をS04で決める必要がある。

## 作成時の仮定

- 設計書にない独自仕様は追加していない。
- 未採番コンポーネントは作成していない。
