# C14 contribution content
## 機能概要
知識・疑問そのものを表示する共通Component。投稿原文と添付資料を保持して表示し、後から変わる解釈・分類・Discoveryとの関係とは分ける。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名 | 物理名 | 種別 | 繰り返し | 親 | データ元 | データ項目 | 対象端末 | 備考 |
|--------|--------|------|----------|----|----------|------------|----------|------|
| 投稿種別 | contribution_type | text | - | - | API | type | PC/モバイル | 知識／疑問 |
| 本文 | contribution_body | text | - | - | API | body | PC/モバイル | 投稿原文を表示 |
| 資料 | contribution_media | file | 0..n | - | API | media | PC/モバイル | 画像・動画・PDF等。添付されている場合に種別に応じて表示 |
| 投稿時に指定した場所 | contribution_place | text/map | - | - | API | place | PC/モバイル | 投稿者が指定した場合のみ。後から変わる解釈とは区別 |
| 投稿時に指定した対象時期 | contribution_time | text | - | - | API | time | PC/モバイル | 投稿者が指定した場合のみ。投稿日時とは区別 |
| 投稿時に指定した季節 | contribution_season | text | - | - | API | season | PC/モバイル | 投稿者が指定した場合のみ |
| 投稿日時 | contribution_posted_at | text | - | - | API | postedAt | PC/モバイル | 出来事の対象時点と区別する |
| 投稿者 | contribution_author | text/link | - | - | API | author | PC/モバイル | 公開範囲に従って表示 |

## アクション
- 初期表示時
  - 投稿原文・添付資料・投稿情報を表示する。後から得た解釈で原文を置き換えない。

## 検討事項
- 資料の種別ごとの表示・再生・ダウンロード方法と投稿者プロフィールへの遷移はデザイン・実装設計で決める。許可するMedia Type等は[Issue #35](https://github.com/ex-day/platform/issues/35)に従う。
- `contribution_media`は既存ドメイン／論理Entity設計の仮定義Mediaとの対応を示すUI上の名称であり、Mediaの属性・関係やAPI／物理データ構造の最終形は本Componentでは確定しない。
