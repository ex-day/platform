# F01: discovery-sections

## 関連概要
表示対象となるC05 discovery-sectionを決定する。

> 2026-11-19（Issue #135、PR #158）：TOP の枠の決め方は API の `GET /top`（OpenAPI の `getTop`）で行う。中身のある枠だけを、優先の順（お知らせ → いま・この時期 → 近くの発見 → 企画 → 番付 → 見本 → 探しています → 声を待っている）で返し、全体で 50件までにする。決まりは [S01](../screens/S01-top.md) の「取得用 API で返すもの（案）」を正とする。v0.1 の `/discovery-sections`（セクションの一覧とセクションごとの中身）は消した。

## アクション
- section一覧を取得する
- 表示条件に基づき表示対象のsectionを決定する
- 決定したsectionごとにC05 discovery-sectionを表示する