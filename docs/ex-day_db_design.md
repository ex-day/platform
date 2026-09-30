# ex-day DB 設計（閲覧用・S01・S03）v0.1

- 対象：[Issue #89](https://github.com/ex-day/platform/issues/89)（Docker で閲覧用に動かせるローカル環境を用意する）の「進め方 3. DB の設計とサンプルデータ」
- 入力：[論理エンティティ設計書](ex-day_logical_entity_design.md)、取得用 API の約束事（[docs/api/openapi.yaml](api/openapi.yaml)）
- テーブル定義：[db/migration/V1__create_view_tables.sql](../db/migration/V1__create_view_tables.sql)
- サンプルデータ：[db/sample/R__sample_data.sql](../db/sample/R__sample_data.sql)

S01（Discovery提案（サービスTOP））と S03（Discovery詳細）の取得用 API が読むものだけを、物理テーブルにする。書き込み（投稿・リアクション・ログイン）とベクトル検索は扱わない。

## 1. 方針

| 項目 | 決めたこと | 理由 |
|---|---|---|
| DB | PostgreSQL 18 ＋ PostGIS（＋ pgvector。今は使わない） | ex-day/poc と同じ構成。場所は PostGIS、ベクトル検索は MVP に入れると決めたら pgvector |
| テーブル定義の置き場所と流し方 | リポジトリ直下の `db/`。Flyway のコンテナで流す（#89 で人が決定） | API の技術に依存させない |
| 識別子 | 主キーは内部の連番（bigint）。API・URL には `public_id`（英数字12文字のランダムな文字列）を出す（#89 で人が決定） | 件数や作った順が外から分からないようにする。User の方針（#74）と同じ考え方 |
| 区分値 | PostgreSQL の enum ではなく、text と CHECK 制約 | 値を足しやすい |
| 日時 | timestamptz | ― |
| 場所 | `geography`（WGS84）。Spatial Type（POINT・AREA・ROUTE）と図形の種類が合うことを CHECK で守る | 「この近く」の絞り込み（距離）に使う。論理設計 7.1・DEC-0007 |
| サンプルデータ | Flyway の繰り返し実行のマイグレーション（`R__`）。変えると全部消して入れ直す | サンプルを直しながら試しやすい。本番では `db/sample` を読み込ませない |

## 2. 論理 Entity との対応

| 論理 Entity（節） | テーブル | 備考 |
|---|---|---|
| User・UserProfile（4.1） | `app_user`・`user_profile` | 声の表示名は、表示のたびに `user_profile.nickname` を引く（#74）。UserIdentity は認証（#75）で足す |
| Discovery（4.2） | `discovery` | 成立状態・公開状態だけを状態として持つ（DEC-0006） |
| 運営が付けたタグ（6A.4・#57） | `discovery_operator_tag` | Discovery のタグ ＝ これ ＋ 属する声のタグ |
| Value（4.2.1） | `discovery_value` | `subject_label` は「何についての価値か」の表示名。Subject との対応は後続設計 |
| Condition（4.2.2） | `value_condition` | 今は季節の軸だけ（DEC-0002）。行がない軸は「その軸に依存しない」 |
| Hero のメッセージ（C19） | `value_presentation` | 価値の組み合わせと会話の文脈を AI で解析した結果の文言を想定する見立て（#89 で人が決定）。サンプルは `generated_by = 'SAMPLE'` |
| Post（4.3） | `post` | 投稿先（器）の Discovery は1つ |
| PostReference（4.3.1） | `post_reference` | 今は返信だけ |
| PostTag（4.3.2） | `post_tag` | 本文中の #タグを書いたまま |
| Media（8.1） | `media` | 提供先は Discovery。ともに提供された声は任意 |
| Finding（6A.3） | `finding`・`finding_source_post` | 下記「3. 省いたもの・簡略にしたもの」を参照 |
| DiscoveryRelation（6.3） | `discovery_relation` | `DERIVED`（派生）と `RELATED`（関連）。派生の目印の位置を `origin_post_id` に持つ |
| 派生の続きの見立て（C26） | `post_continuation` | 「『〇〇』で続いています」。F02 の見立てのため付け直してよい |
| Reaction（4.4） | `discovery_reaction`・`post_reaction`・`finding_reaction` | 対象ごとにテーブルを分け、外部キーで対象を守る |
| Place（7.1） | `place`・`discovery_place` | 代表の場所（`is_primary`）をカード・S03 に表示する |

## 3. 省いたもの・簡略にしたもの

閲覧用の範囲で必要がないため、今回は持たない。必要になった工程で足す。

- **話題（Topic）・発見の仮説（DiscoveryHypothesis）・提案（Proposal）・判断記録（DiscoveryDecision）**：F02（投稿後の会話の解析）を入れるときに足す。そのため、今回は Finding を Discovery に直接つなげている（論理設計では話題・発見の仮説を介す）。
- **Finding の版の履歴、Reaction の対象の版**：版を使う更新・統合・分割がまだないため。
- **Subject・Theme・UserInterest**：興味を使う軸は、未ログインのため扱わない（#89）。
- **TimeExpression（季節以外）・SearchContext**：季節だけを Condition として持つ。SearchContext は API の中の一時的な値。
- **Source・Evidence・ContentReport・UserIdentity**：資料の出典は `media` の列（出典状態・種類・名前）で表す。

## 4. 推薦との関係

推薦の評価（推薦度・並び順）は API で行い、DB には評価の結果を持たない（Recommendation は非永続。論理設計 4.2.3）。DB が用意するのは、評価の材料となる次の情報である。

- 場所：`place.geom`（「この近く」の絞り込み。GiST インデックス）
- 季節：`value_condition`（季節の一致・通年・条件なし）
- 成立状態・公開状態：`discovery.establishment_status`・`visibility`

## 5. サンプルデータ

| 種類 | Discovery | 見るためのもの |
|---|---|---|
| 会話つき | 桜木町から山下公園への海沿い散歩 | ex-day/poc#2 のシナリオ P1〜P20。返信、#タグ、写真、派生の目印、続きの注記 |
| 派生 | 山下臨港線の跡をたどる | 派生元（海沿いの散歩）と、元の会話の冒頭の経緯。裏付けのある説 |
| 会話つき | 大岡川の桜と風景の変化 | organization の README の漫画の流れ（疑問 → 経験が集まる → 写真・資料が集まる）。春の Value |
| 関連 | 氷川丸、赤い靴はいてた女の子像、汽車道 | 関連する Discovery の枠 |
| 季節違い | 山下公園のバラ（春・秋）、山下公園通りの銀杏並木（秋）、赤レンガ倉庫の冬の催し（冬）、横浜港の夏の花火（夏）、野毛山動物園（通年） | 「今」を変えると S01 の提案が変わること。1つの Discovery に「今おすすめ」の Value が複数ある場合 |

- 会話や Discovery の内容は利用イメージであり、史実と異なる場合がある。写真は仮の画像（`/images/sample/…`。画面側で用意する）、ニックネームは架空のもの。
- Hero のメッセージは、季節の一致だけで「今が見頃」「開催中」のような実状を言い切らない書き方にしている（論理設計 2章 原則14）。

## 6. 後続への引き継ぎ

- API（進め方 4）：推薦度の評価、「周辺」の範囲、「今」の季節の決め方（月から）、開発用の設定ファイル（「今」と「いる場所」）の読み込み。
- 画面（進め方 5）：仮の画像（`/images/sample/…`）を用意する。
- 物理設計として後で決めること：`public_id` の作り方（アプリで作るか DB で作るか）、Finding の版、Reaction の対象の版、話題・発見の仮説のテーブル。
