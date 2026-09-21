# S02 Discovery探索一覧 Wireframe 作成メモ (Issue #28 用ドラフト)

担当: Claude (Maker) / Reviewer: 未実施(別AIレビュー待ち) / 参照基準: main @ 914cef0 + PR #29 (マージ済み) のS01/S02責務境界 + Issue #28「人間判断の記録」

## 改訂履歴

- v1: S02画面概要(旧)に基づき、モバイルを「TOP初期表示」として作成。
- v2: PR #29 で「TOPはS01、S02はPC/Mobile共通の探索一覧」と確定。Wireframeを新定義へ更新。
- v3: Issue #28「人間判断の記録」を反映。条件なし直接アクセス→既定条件、既定条件0件→範囲拡大表示、CTA Cardの比較案を追加。「条件をリセット」の仮置きは廃止。
- v3.1 (本版): PR #30 の C21 Contribution CTA 定義に合わせ、`draft-contribution-cta-card` を C21 に改称(画像名・図中ラベル・注記)。

## 成果物 (PNG)

`docs/ui/wireframe/screens/S02/`

| ファイル | 内容 |
|---|---|
| PC-Main.png | PC版(1440px)。S01のC05「もっと見る」から条件を引き継いだ探索一覧。S02への3つの入口(S01「もっと見る」/他画面のC06検索/S02上のC06)を注記 |
| Mobile-Main.png | Mobile版(375px)。S01「もっと見る」から遷移した探索一覧 |
| PC-StateVariants.png | 少数件 / 拡大しても0件(CTA案B) / 取得中 / 取得失敗 / 既定条件で表示 / 既定条件0件→範囲拡大(CTA案A) |
| Mobile-StateVariants.png | S01から引き継いだ条件 / C06で指定した条件 / 少数件 / 拡大しても0件 / 取得中 / 取得失敗 / 既定条件 / 範囲拡大表示 |
| PC-LayoutVariants.png | C03/C04: 4列 / 3列 / 横型 の比較 |
| Mobile-LayoutVariants.png | C03/C04: 縦型1列 / 横型 / 2列コンパクト の比較 |
| S03-RelationsEntryVariants.png | S03関係Discovery「さらに見る」の遷移先: S02条件付き再利用 vs 専用一覧 の比較材料 |

`docs/ui/wireframe/components/`

| ファイル | 内容 |
|---|---|
| C21-contribution-cta-variants.png | 「周辺に今のおすすめが見つかりません、周辺情報を投稿してみませんか？」(遷移先S04)の表示位置3案: 案A 一覧内カード / 案B 一覧の代わり(0件専用) / 案C 一覧上部バナー。文言は仮 |

Artifact(共有用): https://claude.ai/artifact/6Qm7C6U8CzVG9Z6SRnAZJ1
生成元HTML: `docs/ai/claude/wireframe/S02-wireframe-source.html`(Claude固有の補助成果物)

## C21 Contribution CTA (PR #30 で定義)

- 定義書: `docs/ui/components/C21-contribution-cta.md`(PR #30)。旧 `draft-contribution-cta-card` から改称済み。
- 対象・理由: Discoveryが見つからない/少ないときに、投稿(S04)へ誘導するCard。Discoveryではないため C03 の再利用ではない。MVPスコープの「情報不足時専用のCTA CardはMVPの必須要件としない」とは、必須でなければ採用可能という関係。
- 定義書との照合: S02のWireframeは、C03を再利用せず、S04へ遷移し、表示条件・場所・文言を未確定として3案比較する点で定義書と一致。S01・S03のWireframeでのC21利用は定義書上「未確定」のため追加していない。
- 未確定: 表示条件(0件のみ / 少数件も / 拡大後も不足時)、表示場所(案A/B/C)、S01のタブが0件のときに同じ部品を使うか、文言(最後に決定)。

## 設計書確認結果

S02は「C01 / C04 / C02」のみで構成。S02の入口は、①S01のC05「もっと見る」 ②他画面のC06検索 ③S02上のC06実行(同画面更新)。

### Blocking

なし。

### 決定済み(Issue #28「人間判断の記録」)

- C06の検索はどの画面からでもS02へ遷移する(S01のまま位置だけ変える導線は設けない)。
- 条件なしでS02を開いた場合は既定条件(現在地または既定エリア＋既定半径)で表示。既定条件で0件なら範囲を拡大して表示し、拡大したことを示す。既定値は実装時に決定。
- 0件・情報不足時はCTA Card(遷移先S04)。新規コンポーネント候補。
- S03から戻ったときのスクロール位置は復元する方針(順位変動・鮮度の扱いは実装時)。

### Non-blocking / 要検討

1. 引き継いだ条件(グループ名・件数・並び順)をS02上にどう表示するか未定義(条件チップを仮置き)。
2. 続きの取得方法(件数上限・ページング・追加読み込み)が未定義。
3. 取得失敗の表示が未定義(仮置き)。
4. S01モバイルの縦方向の継続閲覧の範囲(最大件数/無限スクロール/仮想化)は要検討。Mockで確認。
5. MVPスコープに残る単独の「一覧」表記(146・397・408・436行)は保留(人間が検討中)。
6. C06の対象端末が空欄・PC/Mobileの配置未定義。「現在地」利用不可時の扱い、自然文から生成した条件の確認表示も未定義。
7. C03のカード幅・表示方向・情報量、C04の一覧レイアウトは PC/Mobile-LayoutVariants に比較を置いた。S01との整合はPC案A・Mobile案A。

### S03 関係Discovery一覧導線(S02再利用可否)

S03-RelationsEntryVariants に比較材料を整理した。決定はしていない。PR #29 の責務境界との関係の行を追加(案A: 「さらに見る」を探索意思の表れとして関係条件を引き継ぐ探索とみなせるか要整理 / 案B: S02の責務を広げずに済む)。

## 作成時の仮定(設計書にない独自仕様は確定していない)

- 橙の点線タグ「仮定」「仮置き」は設計書に記載がない箇所。「決定」タグはIssue #28の判断記録に基づく表現。文言・ボタン・条件チップは比較用の例。
- C06の値(現在地 / 3km / 夕方)は引き継ぎ・指定条件の表示例。
- カードにC10(リアクション)は置いていない(C03に含まれないため)。

## Issue #28 コメント案 (未投稿)

> 「人間判断の記録」に基づき、S02 Wireframeを更新しました(既定条件、範囲拡大表示、CTA Cardの C21 比較案を追加)。成果物: `docs/ui/wireframe/screens/S02/` の7枚と `docs/ui/wireframe/components/C21-contribution-cta-variants.png`。Artifact: https://claude.ai/artifact/6Qm7C6U8CzVG9Z6SRnAZJ1
> Maker: Claude。別AIレビューは未実施です。
> C21 Contribution CTA: `draft-` から改称済み。Blockingなし。Non-blocking 7件(上記)。
