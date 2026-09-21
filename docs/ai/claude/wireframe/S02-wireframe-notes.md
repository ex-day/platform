# S02 Discovery探索一覧 Wireframe 作成メモ (Issue #28 用ドラフト)

担当: Claude (Maker) / Reviewer: 未実施(別AIレビュー待ち) / 参照基準: main @ 914cef0 + PR #29 (2aa5f0c) のS01/S02責務境界

## 改訂履歴

- v1: S02画面概要(旧)に基づき、モバイルを「TOP初期表示」として作成。
- v2 (本版): PR #29 で「TOPはS01(モバイル/PCとも)、S02はPC/Mobile共通の探索一覧」と確定(人間判断: 案A、Issue #28に記録済み)。旧「PC条件付き一覧 vs MobileのTOP」の役割差は置き換わったため、Wireframeを新定義へ全面更新した。

## 成果物 (PNG) — `docs/ui/wireframe/screens/S02/`

| ファイル | 内容 |
|---|---|
| PC-Main.png | PC版(1440px)。S01のC05「もっと見る」から条件を引き継いだ探索一覧。C01/C06、C04 4列グリッド、C02。card→S03の導線と、S02への3つの入口(S01「もっと見る」/他画面のC06検索/S02上のC06)を注記 |
| Mobile-Main.png | Mobile版(375px)。S01「もっと見る」から遷移した探索一覧。C06主要条件1行、引き継ぎ条件チップ(表示有無は仮定)、C04縦型1列 |
| PC-StateVariants.png | 少数件 / 0件 / 取得中(C09) / 取得失敗 / 条件なし直接アクセス(未定義) |
| Mobile-StateVariants.png | S01から引き継いだ条件 / C06で指定した条件 / 少数件 / 0件 / 取得中 / 取得失敗 / 条件なし直接アクセス(未定義) |
| PC-LayoutVariants.png | C03/C04: 4列 / 3列 / 横型 の比較 |
| Mobile-LayoutVariants.png | C03/C04: 縦型1列 / 横型 / 2列コンパクト の比較 |
| S03-RelationsEntryVariants.png | S03関係Discovery「さらに見る」の遷移先: S02条件付き再利用 vs 専用一覧 の比較材料 |

Artifact(共有用): https://claude.ai/artifact/6Qm7C6U8CzVG9Z6SRnAZJ1
生成元HTML: `docs/ai/claude/wireframe/S02-wireframe-source.html`(Claude固有の補助成果物)

Main採用案は S01 PC-Main と同じ 4列・縦型(PC) / 縦型1列(Mobile)。他案は比較用で、採否は人間レビューで決定する。

## 設計書確認結果

S02は「C01 / C04 / C02」のみで構成。S02の入口は、①S01のC05「もっと見る」 ②他画面のC06検索 ③S02上のC06実行(同画面更新)。

### Blocking

なし。v1で挙げたBlocking候補(モバイルのTOPがS01かS02か)は PR #29 の人間判断(案A)で解消済み。

### Non-blocking

1. 引き継いだ条件(グループ名・件数・並び順)をS02上にどう表示するか未定義。Mobile-Main / StateVariants に条件チップを仮置き。
2. 続きの取得方法(件数上限・ページング・追加読み込み)が未定義。C04は0..n件のみ。
3. 0件・取得失敗の表示が未定義(C09はloadingのみ)。仮置き。独立コンポーネント化する場合は定義追加とC番号の採番(人間)が必要。
4. `/discoveries` を条件なしで直接開いた場合の表示が未定義(PC/Mobile-StateVariants の「条件なし直接アクセス」)。
5. S01で位置だけを指定する導線が未定義(C06検索を実行するとS02へ遷移する定義のため)。MVPスコープ「優先して決める事項」で扱う。
6. S01モバイルの「縦方向の継続閲覧」の範囲(件数超過後はS01内継続かS02遷移か)が曖昧。C05/C11は「もっと見る」→S02。
7. C06の対象端末が空欄・PC/Mobileの配置未定義。「現在地」利用不可時の扱い、自然文から生成した条件の確認表示も未定義。
8. C03のカード幅・表示方向・情報量、C04の一覧レイアウトは PC/Mobile-LayoutVariants に比較を置いた。S01との整合はPC案A・Mobile案A。

### S03 関係Discovery一覧導線(S02再利用可否)

S03-RelationsEntryVariants に比較材料を整理した。決定はしていない。PR #29 の責務境界との関係の行を追加(案A: 「さらに見る」を探索意思の表れとして関係条件を引き継ぐ探索とみなせるか要整理 / 案B: S02の責務を広げずに済む)。

## 作成時の仮定(設計書にない独自仕様は確定していない)

- 橙の点線タグ「仮定」「仮置き」「設計書に定義なし」は設計書に記載がない箇所。文言・ボタン(条件をリセット/再読み込み)・条件チップは比較用の例。
- C06の値(現在地 / 3km / 夕方)は引き継ぎ・指定条件の表示例。
- カードにC10(リアクション)は置いていない(C03に含まれないため)。

## Issue #28 コメント案 (未投稿)

> S02 Discovery探索一覧 Wireframe(PC/Mobile)を PR #29 のS01/S02責務境界に合わせて更新しました。成果物: `docs/ui/wireframe/screens/S02/` の7枚(PC-Main / Mobile-Main / PC-StateVariants / Mobile-StateVariants / PC-LayoutVariants / Mobile-LayoutVariants / S03-RelationsEntryVariants)。Artifact: https://claude.ai/artifact/6Qm7C6U8CzVG9Z6SRnAZJ1
> Maker: Claude。別AIレビューは未実施です。
> Blockingなし。Non-blocking 8件(上記メモ 1〜8)。S03の関係Discovery一覧導線は比較材料のみで未決定です。
