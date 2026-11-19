# ex-day DB 設計 v0.2（MVP）

- 作成日：2026-10-31（v0.1：閲覧用・S01・S03）、v0.2：2026-11-17（[Issue #135](https://github.com/ex-day/platform/issues/135)）
- 状態：案。テーブル定義は `db/migration/V2__*`（本書と食い違うときは、どちらかを直す）。表と列の骨組み、「いずれか1種類を指す」関係の持ち方、判断の記録・写し・公開制御・データの保持の方針を決める。テーブル定義とサンプルデータは、本書のレビュー（PR #150）のあとの PR で作った（2026-11-17 の人の判断）
- 入力：[画面とデータの対応表](ex-day_screen_data_map.md)（2〜8節）、[論理エンティティ設計 v0.2](ex-day_logical_entity_design.md)、[MVP スコープ v0.4](ex-day_mvp_scope.md)、各画面の定義（`docs/ui/screens/`）
- v0.1（v0.1 の論理設計に基づく閲覧用の DB。Discovery を器とする形）の本文は Git の履歴にある
- **対象外**：
  - 寄り道の到達圏のデータ（駅・路線・表定速度・事前計算）と、場所の行き方（何件でも足せる形）・到達点・到達圏の計算の中の駅の番号（DEC-0013 決定2・4・5）。F06 の設計（#161）で足す（2026-11-17 の人の判断。2026-11-19 に、PR ではなく Issue で追う形にした）
  - 会話の解析の運用の記録（解析の実行・バッチ・予算の台帳等。DEC-0018、論理設計 14節）。F02 の実装で足す。本書の表は「どの解析の版で作られたか」を `analysis_version`（text）で持つだけにする
  - MVP で使わない Entity（資料、提案、タグ、出典・根拠、Theme、システムの興味の推定、歴史的な時間。4.2節）
  - ベクトル（pgvector）。検索（F07）の設計で入れると決めたら足す

## 1. 方針

| 項目 | 決めたこと | 理由 |
|---|---|---|
| DB | PostgreSQL 18 ＋ PostGIS。検索に pg_trgm（**案**。F07 で確かめる）。pgRouting は F06、pgvector は F07 で足す | v0.1・DEC-0017 を引き継ぐ。pg_trgm は日本語の部分一致に使え、RDS で使える |
| 置き場所と流し方 | `db/migration/`。Flyway のコンテナで流す | v0.1 を引き継ぐ |
| V1 からの移り方 | `V2__` のマイグレーションで V1 の表をすべて消し、本書の表を作る。API・Web は、後続の PR で直すまで動かない | 本番はまだない。V1 は書き換えず、履歴を残す（2026-11-17 の人の判断） |
| 主キー | 内部の連番（bigint、`GENERATED ALWAYS AS IDENTITY`）。画面・URL・API に出さない | v0.1 を引き継ぐ |
| 画面・URL・API に出す識別子 | `public_id`（英数字12文字のランダムな文字列）。DB の既定値で作る（2.1節） | 件数・作った順を外から分からなくする。座標を URL に出さない（論理設計 原則22） |
| 区分値 | PostgreSQL の enum ではなく、text と CHECK 制約 | 値を足しやすい（v0.1 を引き継ぐ）。値を足すときはマイグレーションで CHECK を差し替える |
| 足せる形にする区分 | DiscoveryPlace の役割、場所の種類、PlaceRelation の種類以外の細かい種類は、CHECK を付けない text にする | 論理設計で「例が出るたびに足せる形にする」としたもの（4.2・4.5.1節） |
| 日時 | timestamptz | ― |
| 場所の形 | `geography`（WGS84）。形の種類（地点・エリア・経路）と図形の種類が合うことを CHECK で守る | v0.1 を引き継ぐ |
| 行を消さない | 判断の対象・採用した構造・判断の記録の行は消さない。状態と判断の記録で表す。外部キーに `ON DELETE CASCADE` を付けない | 論理設計 6.2・6.5節、MVP スコープ 7・12節 |
| 判断の記録が要る変更 | データの変更と判断の記録の追加を1つのトランザクションで行う（2.3節） | 対応表 8節 |

### 1.1 データの保持

2026-11-17 の人の判断（PR #150 のレビュー）。

- データを持つかは、サービスの上の利用目的、整合性の維持、セキュリティ、監査、法令の遵守などの、はっきりした必要に基づいて決める
- 将来使うかもしれないこと、保存の費用が小さいことだけを理由に持たない
- 特に、利用者が書いた内容の写しは、公開制御・削除・保持期間・アクセスの権限を考えたうえで、持つ必要があるかを決める

企画の変更の履歴（3.5節）は、この原則の例外ではなく、利用目的（企画の会話のタイムラインに出す）がはっきりしたので持つ。

## 2. 共通の決まり

### 2.1 画面・URL・API に出す識別子

- `public_id` を持つ表：API で一つずつ指して開く・操作するもの。`place`、`conversation`、`post`、`discovery`、`discovery_value`、`project`、`project_item`、`project_change`、`finding`、`open_question`、`place_candidate`、`place_adoption`、`question_answer`、`decision`、`content_report`
- 持たない表：関係・履歴・設定の行（`discovery_place`、`place_relation`、`reaction` 等）。API では親の `public_id` と中身で指す
- 作り方：DB の関数 `exday_public_id()` を既定値にする。`gen_random_uuid()`（PostgreSQL の標準。拡張は要らない）のランダムな値から12文字を取る。重なりは一意の制約で拒む（起きる見込みはごく小さい）
- User は持たない（MVP では他人のプロフィールページがない。論理設計 4.1節）

### 2.2 「いずれか1種類を指す」関係

論理設計 12節・対応表 8節の指示に従い、3つの持ち方を比べた。

| 持ち方 | 参照の整合 | 種類ごとの制約・index | 種類を足すとき | 読むとき |
|---|---|---|---|---|
| **汎用の ID**（`target_type` ＋ `target_id`） | 外部キーで守れない。消えた・違う表の行を指せる | 種類ごとの部分 index は作れる | 区分の値を足すだけ | 種類ごとに結合を書き分ける |
| **種類ごとの表**（`post_reaction`・`discovery_reaction`…。v0.1 の Reaction） | 外部キーで守れる | 表ごとに書ける | 表を足す | 横断して数える・並べるときに UNION が要る |
| **種類ごとの列**（指す先の種類ごとに外部キーの列を持ち、`CHECK (num_nonnulls(...) = 1)`） | 外部キーで守れる | 部分 index で種類ごとに書ける | 列を足し、CHECK を差し替える | 1つの表のまま |

**決めたこと：種類ごとの列にする。** 外部キーで参照を守れ、1つの表のまま横断して読める。指す先の種類は多くても十数（判断の記録の対象）で、空の列の費用は小さい。資料（二次開発）を足すときは、各表に `media_id` を足して CHECK を差し替える。

| 関係 | 表 | 列 | 「ちょうど1つ」か |
|---|---|---|---|
| 会話の入口 | `conversation` | `entry_place_id`・`entry_discovery_id`・`entry_project_id` | 0か1（入口なしを許す） |
| 企画の項目の指す先 | `project_item` | `place_id`・`discovery_id` | 1 |
| 企画の変更で動いた項目 | `project_change_item` | `place_id`・`discovery_id` | 1 |
| 場所の候補の対象 | `place_candidate` | `conversation_id`・`open_question_id` | 1 |
| 場所の候補の候補の場所 | `place_candidate` | `place_id` ／ 新しい場所の案の列（`proposed_*`） | どちらか一方 |
| 場所の採用の対象 | `place_adoption` | `conversation_id`・`open_question_id`（候補と同じ値。複合の外部キーで候補と食い違わせない。3.8節） | 1 |
| 場所以外の回答 | `question_answer` | `answer_finding_id`（版つき）・`answer_post_id` | 1 |
| 由来の対象 | `discovery_origin` | `conversation_id`・`post_id`・`project_id`・`source_discovery_id` | 由来の種類ごとに1（運営の登録は0） |
| Discovery の候補の出どころ | `discovery_candidate` | `conversation_id`・`post_id`・`project_id` | 1 |
| 関係の候補の一方 | `relation_candidate` | `discovery_candidate_id`・`from_discovery_id` | 1 |
| Subject の付く先 | `subject_assignment` | `discovery_id`・`value_id` | 1 |
| 興味の対象 | `user_interest` | `place_id`・`subject_id` | 1 |
| 検索のための情報の対象 | `search_profile` | `place_id`・`discovery_id`・`project_id` | 1 |
| Reaction の対象 | `reaction` | `post_id`・`discovery_id`・`value_id`・`finding_id`（版つき）・`open_question_id` | 1 |
| 通報の対象 | `content_report` | `conversation_id`・`post_id`・`discovery_id`・`project_id` | 1 |
| 場所の候補の根拠 | `place_candidate_basis` | `post_id`・`claim_id`・`finding_id` | 1以上 |
| 判断の記録の対象・結果 | `decision_target` | 判断の対象になる表ごとの列（3.9節） | 1 |
| 判断の記録に固めた見立て | `decision_basis_snapshot` | `claim_id`・`finding_id`・`open_question_id` | 1 |

### 2.3 判断の記録とデータの変更

- 判断の記録が要る操作（対応表 3節）は、1つのトランザクションの中で、`decision` を1行足し、変えた行を `decision_target` に書き（対象・結果・比べた既存の Discovery の別と、変える前・後の状態）、データを変える
- 判断で作った行は、作った判断の記録を `created_decision_id` に持つ。取り消し・外した行は `revoked_decision_id`（または `removed_decision_id`）を持つ。その間の状態の変化（採用中 → 再検討中 → 採用中 など）は `decision_target` の変える前・後の状態でたどる
- 判断の記録は書き換えない。直すときは新しい判断の記録を足す（論理設計 6.5節）
- 運営が SQL で補う場合も、同じ形で判断の記録を足す（S18）。データだけを書き換えない
- 判断の主体：システムは `actor_kind = 'SYSTEM'`（User を持たない）。運営・ユーザーは User を持つ。運営かどうかは Cognito のグループで判定し（S18）、DB には持たない。判断の記録には、そのときの立場（運営／問いを立てた人／会話を始めた人／企画を作った人）を `actor_kind`・`actor_role` に写す

### 2.4 導くものと写し

対応表 4節・8節の「写しを持つか」は、次のようにする。**MVP では写しを最小にし、ビューと部分 index で導く**。遅い・重いと分かったものから写しにする（写しにするときも正本は変えない）。

| 導くもの | 持ち方 | 整合 |
|---|---|---|
| 会話と場所の採用した紐付き（ConversationPlace） | ビュー `conversation_place`（採用中・再検討中の `place_adoption` のうち、対象がその会話か、その会話の問いのもの）。`place_adoption` に部分 index | 即時（写しがないので食い違わない） |
| 会話の「場所未定」 | 上のビューに行がないこと（`NOT EXISTS`） | 即時 |
| 候補の表示の「採用」「取り消し」、問いの回答の状態 | `place_adoption`・`question_answer` の状態から導く | 即時 |
| Discovery の出す位置の場所 | `discovery_place` の役割（体験できる場所・始まり）から導く。役割の値はアプリの定数で持つ | 即時 |
| 会話の始めた人 | `conversation.starter_user_id` に持つ（最初の Post の書き手。変わらないので写しても食い違わない） | 即時 |
| 返信の数・最終の発言の時点 | `conversation.public_post_count`・`last_public_posted_at` に写す。Post を足す・公開状態を変えるのと同じトランザクションで更新する | 遅れを許してよいが、同じ処理で更新する（公開状態による表示の可否を遅らせないため。2.5節） |
| 「探しています」・このあたりの数字・企画の新しい声・Discovery の現在状態 | 写さず、問い合わせで導く（`place_candidate` の状態・位置、Post の時点に index） | 遅れを許す。TOP で重ければ、まとめて作る写しを後で足す |
| SearchProfile | `search_profile` に持つ（見立て。作り直せる） | 遅れを許す。非公開になった対象は検索のときに公開状態で外す（2.5節） |
| Recommendation、TOP の並びの種 | 保存しない | ― |

### 2.5 公開制御

- 公開状態 `visibility`（`PUBLIC`／`HIDDEN`）を持つ表：`conversation`、`post`、`discovery`、`project`。場所は公開状態を持たない（MVP では場所を非公開にしない。統合・廃止は状態で表す。S13）
- **一般のユーザー向けの読み取りは、公開のものだけを返すビューを通す**（2026-11-17 の人の判断、PR #150 のレビュー）。公開の条件を個々の SQL に散らさず、何を見て公開と判断しているかを1か所で分かるようにするため
- ビューだけでアクセスの制御が終わるわけではない。運営の権限・作った人本人の読み取り（非公開の自分の Post・企画、運営が開く非公開・統合済み・取り消された対象）は、表を直接読み、権限をサーバー側で確かめる（S18、画面一覧「運営の権限」）
- 1つの応答を複数の SQL で読むときは、REPEATABLE READ の読み取り専用トランザクションの中で読み、応答はその時点（スナップショット）の公開状態を表す（PR #152 のレビュー。apps/api/AGENTS.md「読み取りの一貫性と公開制御」）
- ビューの公開の条件（**案**。マイグレーションの PR で書く）：

| ビュー | 公開とする条件 |
|---|---|
| `visible_conversation` | 会話が公開 |
| `visible_post` | Post が公開、かつ属する会話が公開 |
| `visible_discovery` | Discovery が公開、かつ成立を取り消していない（`withdrawn_at` が空。3.4節） |
| `visible_project` | 企画が公開 |
| `visible_project_item` | 企画が公開、かつ指す先が見えるもの（場所、または `visible_discovery` の Discovery）。見えない Discovery を指す項目の出し方は S15 で決めた：S15 の並びは位置を残して「表示できません」と出すので、このビューは使わず、`visible_project` で企画の公開を確かめてから `project_item` を読み、指す先だけを公開の判定にかける（2026-11-18、PR #155）。指す先が見える項目だけを集める読み取り（S03・S13 の企画カード）は、このビューを使う |
| `visible_finding`・`visible_open_question`・`visible_place_candidate` | 属する会話（場所の候補は対象の会話・問いの会話）が公開 |
| `visible_project_change` | 企画が公開（3.5節） |

- 親の公開状態を見る：Post・わかってきたこと・問い・場所の候補は、属する会話が非公開なら出さない
- **入口が非公開になっても、会話は非公開にしない**：入口の Discovery・企画が非公開・成立の取り消しになっても、その入口から始まった会話は場所の会話として残す（独立した会話まで一律に非公開にしない）。入口へのリンクだけを出さない
- **公開状態は対象ごとに独立して扱い、機械的に連動させない**（2026-11-17 の人の判断、PR #154 のレビュー）。退会、Post の公開状態、わかってきたこと・問い・場所の候補・Discovery 等の公開状態は、それぞれ別に決める
  - 退会しただけで、その人の Post を自動で非公開にしない（退会後の表示は規約で定める。MVP スコープ 12節）
  - Post を非公開にしただけで、その Post を根拠にしたわかってきたこと・問い・場所の候補・成立した Discovery を自動で非公開にしない。一般のユーザーには、非公開の Post の本文と書いた人を出さず、識別子・時点・返信先だけを残す（S14）
  - ただし、非公開の理由が個人情報・権利侵害等で、派生した情報にも問題のある内容が残るときは、**その派生した情報自身の公開の可否を別に判断する**（F10 の運営の判断。判断の記録を残す）。再解析と公開制御の手順は F02・F10 で決め、**一般のユーザーから投稿を受け付ける外部公開の前に**、漏えいを防ぐ扱いを確定する（7.1節）
  - 上の「親の公開状態を見る」（会話が非公開なら中のものを出さない）は、親が子を含む関係なので連動させる。根拠・由来として指すだけの関係（Post → わかってきたこと等）は連動させない
- 関連の情報からの漏えいを防ぐ：企画の項目・Discovery の関係・由来・数・地図など、ほかの対象を指して出すものは、指す先も公開のビューで引く
- SearchProfile：検索の結果は、対象を公開のビューで引き直して出す（非公開・取り消しの対象を結果に出さない）。非公開になった Post・会話を材料にした SearchProfile・わかってきたこと・要約の作り直しは、F02・F07・F10 の設計で決める（対応表 7節）。DB は、見立てから根拠の Post を版ごとにたどれるように持つ（`finding_version_claim` → `claim.post_id`。3.7節）

### 2.6 暫定公開

- 投稿・返信・企画は、確定時のチェックを通ると暫定公開になり、事後チェックの対象になる（F10、DEC-0021 決定5）。**案**：`post`・`project` に `moderation_status`（`PROVISIONAL` 暫定公開／`CHECKED` 確認済み）を持つ。S18 の事後チェックの一覧はこれを読む。値の種類と、確認済みにする手順は F10 で決める
- 公開状態（`visibility`）と事後チェックの状態は別の列にする。暫定公開のものも一般に見える

## 3. 表

列は主なものだけを書く。すべての表に `created_at`（必要な表は `updated_at`）を持つ。型・長さ・index の細部はマイグレーションの PR で決める。

### 3.1 User（論理設計 4.1節）

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `app_user` | `status`（ACTIVE／WITHDRAWN） | v0.1 を引き継ぐ。`user` は予約語のため |
| `user_identity` | `user_id`、`issuer`、`subject`、`login_method`（GOOGLE／EMAIL 等）、`upstream_subject`、`linked_at` | 一意（`issuer`, `subject`）。メールアドレス・名前・顔写真は持たない（DEC-0016） |
| `user_profile` | `user_id`（主キー）、`nickname` | v0.1 を引き継ぐ。表示のたびに今のニックネームを引く（#74） |
| `user_nickname_change` | `user_id`、`before_nickname`、`after_nickname`、`changed_at` | 運営だけが読む（#74） |
| `user_interest` | `user_id`、`place_id`・`subject_id`（2.2節）、`origin`（MVP は SELF だけ）、`intent`（INTERESTED／NOT_INTERESTED） | 一意（`user_id`, 対象）。MVP の興味のある地域は `place_id`。興味（#73）は決まったら `subject_id` で持つ |

### 3.2 場所（4.2節）

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `place` | `public_id`、`name`（表示名）、`spatial_type`（POINT／AREA／ROUTE）、`geom`、`status`（ACTIVE／MERGED／ABOLISHED）、`merged_into_place_id`、`origin`（OPEN_DATA／CONVERSATION／OPERATOR）、`source_dataset`・`source_record_id`・`source_license`・`imported_at`、`existed_from`・`existed_to`、`access_station_place_id`（行き方の代表の駅）、`created_decision_id` | `geom` に GiST。`status = 'MERGED'` と `merged_into_place_id` の有無を CHECK でそろえ、統合先が自分自身でないことも CHECK で守る（統合先の持ち方。5節。循環は 3.12節）。一意（`source_dataset`, `source_record_id`）。会話から育った場所は `created_decision_id`（採用の判断）を持つ |
| `place_name` | `place_id`、`name`、`kind`（DISPLAY／ALIAS／FORMER）、`valid_from`・`valid_to` | 別名・旧称（時代つき）。検索（F07）の照合にも使う |
| `place_kind` | `place_id`、`kind` | 主キー（`place_id`, `kind`）。種類は CHECK を付けない text（城跡・公園・駅・坂等） |
| `place_estimate` | `place_id`、`metric`（勾配等）、`value`、`unit`、`method`・`method_version`、`computed_at` | 番付の最初の並び（F09）の根拠。計算の出どころと版を持つ |
| `place_relation` | `from_place_id`、`to_place_id`、`kind`（ADMINISTRATIVE／CONTAINS／AREA／FACILITY／ROUTE_PART）、`seq`、`valid_from`・`valid_to`、`origin` | 1本の木にしない。位置から計算で導ける包含は全部は保存しない（論理設計 4.2.1節） |

- 採用されていない場所（新しい場所の案）は `place` に入れない。案は `place_candidate` の列に持つ（3.8節）
- 場所の重複の統合・廃止は、MVP では運営が SQL で行い、判断の記録を足す（S13、MVP スコープ 5節）

### 3.3 会話と Post（4.3・4.4節）

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `conversation` | `public_id`、`entry_place_id`・`entry_discovery_id`・`entry_project_id`（2.2節）、`is_project_own`、`starter_user_id`、`visibility`、`public_post_count`、`last_public_posted_at` | `is_project_own` なら `entry_project_id` が要る（CHECK）。**部分一意（`entry_project_id`）WHERE `is_project_own`**：企画そのものの会話を1つに保つ（5節） |
| `post` | `public_id`、`conversation_id`、`user_id`、`body`、`visibility`、`moderation_status`（2.6節）、`posted_at` | index（`conversation_id`, `posted_at`）、（`user_id`, `posted_at`）（S09） |
| `post_reference` | `from_post_id`、`to_post_id`、`kind`（REPLY／MENTION） | 参照元と参照先は同じ会話（アプリで確かめる） |
| `location_sharing_prompt` | `post_id`（主キー）、`status`（SHOWN／ANSWERED／DECLINED／LATER）、`shown_at`、`answered_at`、`place_adoption_id` | 主キーで「1つの投稿に1件まで」を守る。`ANSWERED` と `place_adoption_id` の有無を CHECK でそろえる |

- 会話と最初の Post は同じトランザクションで作る。企画そのものの会話を2人が同時に作ろうとしたときは、部分一意の制約で後の方が失敗するので、アプリはできている会話に Post を足し直す
- 入口から場所が決まる会話は、作るのと同じトランザクションで、場所の候補（出され方＝入口）・場所の採用・判断の記録を作る（論理設計 4.3節）

### 3.4 Discovery（4.5節）

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `discovery` | `public_id`、`title`、`summary`、`visibility`、`established_at`、`withdrawn_at` | 成立状態を持たない（DEC-0020 決定9）。`withdrawn_at` は成立の取り消し（下記） |
| `discovery_place` | `discovery_id`、`place_id`、`role`、`seq`、`valid_from`・`valid_to`、`description`、`status`（ACTIVE／REMOVED）、`created_decision_id`、`removed_decision_id` | `role` は CHECK を付けない text。付け替えでも行を消さず REMOVED にする。場所が0件にならないことはアプリで守る（足してから外す）。index（`place_id`）WHERE ACTIVE |
| `discovery_value` | `public_id`、`discovery_id`、`description`、`status`（ACTIVE／REMOVED）、`created_decision_id`、`removed_decision_id` | Value。Reaction の対象になるので `public_id` を持つ |
| `value_condition` | `value_id`、`axis`（MVP は SEASON）、`season`（SPRING／SUMMER／AUTUMN／WINTER／ALL_YEAR）、`description` | 軸の行がないことは「その軸に依存しない」（v0.1 を引き継ぐ）。通年は ALL_YEAR で表し、条件なしと区別する |
| `discovery_origin` | `discovery_id`、`kind`（CONVERSATION／POST／PROJECT／DERIVED／OPERATOR）、種類ごとの対象の列（2.2節）、`decision_id` | 種類と対象の列を CHECK でそろえる。成立の判断の記録を必ず持つ |
| `discovery_relation` | `from_discovery_id`、`to_discovery_id`、`kind`（DERIVED／RELATED／MERGED）、`description`、`status`（ACTIVE／REVOKED）、`created_decision_id`、`revoked_decision_id` | 統合（MERGED）は from が統合された側。部分一意（`from_discovery_id`）WHERE `kind = 'MERGED'` AND ACTIVE（統合先は1つ）。自分自身を指さない（CHECK）。統合の循環は統合の処理で確かめる（3.12節） |

- **成立の取り消し**（2026-11-17 の人の判断、PR #150 のレビュー）：`discovery.withdrawn_at` に持ち、公開状態とは分ける。正本は成立の取り消しの判断の記録で、`withdrawn_at` はその写しとして同じトランザクションで入れる。取り消しを戻すときは空にし、判断の記録を足す。非公開は安全・規約の理由、取り消しは「Discovery ではなかった」という判断で、理由も戻し方も違うため。一般の読み取り（`visible_discovery`）・数・検索から外す。論理設計 原則6・4.5節を併せて直した
- v0.1 の `value_presentation`（Hero の文言）と `post_continuation`（派生の続きの注記）は持たない。Hero は Value の説明と Recommendation で出す（S03）。文言を別に作ることにしたら、そのときに足す
- 運営が付けたタグ（v0.1 の `discovery_operator_tag`）は持たない（タグは MVP の対象外）

### 3.5 企画（4.6節）

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `project` | `public_id`、`creator_kind`（OPERATOR／USER）、`creator_user_id`、`type`（COURSE／RANKING／COLLECTION／MEMORY）、`title`、`call_text`（呼びかけ）、`description`、`visibility`、`moderation_status` | 運営が作った企画も、作ったアカウントの User を持つ（編集できるのは作った人だけ。判定は `creator_user_id`） |
| `project_item` | `public_id`、`project_id`、`place_id`・`discovery_id`（2.2節）、`position`、`note` | 一意（`project_id`, `position`）を遅延の制約にする（並べ替えを1つのトランザクションで行うため） |
| `project_change` | `public_id`、`project_id`、`kind`（CREATED／UPDATED。CREATED は企画ごとに1つ）、`changed_by_user_id`、`changed_at`、`fields_changed`（text[]。TITLE／CALL_TEXT／DESCRIPTION 等。中身は写さない） | 企画の変更の履歴（下記）。保存のうち、項目が動いたか表示する項目が変わったものだけを1行にする |
| `project_change_item` | `change_id`、`place_id`・`discovery_id`（2.2節）、`kind`（ADDED／REMOVED／MOVED）、`before_position`、`after_position` | 動いた項目ごとに1行。指す先は企画の項目の行ではなく場所・Discovery で持つ（項目を外すと項目の行はなくなるため） |

- 一定期間内に1人が新しく作れる企画の数の上限は、期間と件数が決まったらアプリで確かめる（累計の上限ではない。MVP スコープ 8節、2026-11-18 の人の判断、PR #155）

**企画の変更の履歴**（2026-11-17 の人の判断、PR #150 のレビュー）：

- **目的**：会話を通して企画が育つ過程を見せ、参加した人が自分の声が反映されたことを感じられるようにする。企画そのものの会話のタイムラインに、変更の出来事（「坂Bを足しました」「坂Aが3位から2位に」）を出す。保存の費用が小さいから持つのではない（1.1節）
- **対象**：順位・順番の変更だけを特別に扱わない。4つの型（コース・番付・まとめ・記憶）の項目の追加・外す・順番の変更を持つ。表題・呼びかけ・説明の変更は「変えた」という事実だけを持ち、中身は写さない（表示するかは S15 で決める）。項目の説明（作った人の文）の変更は持たない
- **持ち方の比較**：

| 持ち方 | タイムラインに出す | 過去の並びを再現する | 利用者が書いた文の写し |
|---|---|---|---|
| 保存のたびに企画全体を写す（jsonb） | 前後の写しを比べて差分を作る | そのまま読める | 表題・説明・項目の説明が写しの数だけ残る |
| 保存のたびに並び（項目と順番）だけを写す | 前後を比べて差分を作る | そのまま読める | 項目の説明を含めるかによる |
| **変更の出来事と、動いた項目の差分だけを持つ（推す）** | そのまま出来事として読める | 作った時点（全部 ADDED）から順に重ねれば再現できる | 持たない（場所・Discovery への参照と順番だけ） |

- **決めたこと（案）**：差分の出来事で持つ（`project_change`・`project_change_item`）。タイムラインに出す単位がそのまま出来事になり、利用者が書いた文を写さないので、守る範囲が企画そのものの公開制御に収まる。過去の並びも差分を重ねれば再現でき、専用の画面を後で作るときに困らない
- **作った時点**：企画を作ったときも1件の出来事にする（全項目が ADDED）。これで「最初の並び」もたどれる
- **公開制御・削除**：出来事の見え方は企画の公開状態に従う（`visible_project_change`）。動いた項目が指す Discovery が非公開・成立の取り消し・統合になったときは、今の状態で出す（非公開・取り消しは伏せる。統合は統合先へ案内する）。出来事は利用者の文を写さないが、変更した人の User と、過去の項目（場所・Discovery）への参照は持つ。そのため、履歴そのものの削除・匿名化の要否、保持期間、退会後の表示、本人・運営・一般のアクセスの権限は、退会・削除・公開制御の設計（規約を含む）で決める（PR #150 の再レビュー。7.1節）
- **タイムラインの出し方と会話の関係**：出来事は企画に属し、会話には属さない。S15 は、企画そのものの会話の Post と企画の変更の出来事を、時点の順に混ぜて出す。**企画そのものの会話がまだないときも出来事は出せる**ので、履歴を出すためだけに空の会話を作らない（会話は最初の発言のときに作る、という決定を変えない）
- **継続検討**：過去の並びを再現して見る専用の画面を MVP に設けるか。提案のもとの Post と変更を明示的に結ぶか（結ぶときは `project_change` に Post への参照を足す）。運営の企画でもタイムラインに出すか（データは同じ形で持つ）
- **運営の事後チェックとは目的を分ける**：非公開の企画を作った人が直したことを運営が分かるための更新の検知は、`project.updated_at` で足りるかを F10 で決める。本表はユーザー向けの履歴であり、事後チェックのための記録ではない

### 3.6 Subject（7.2・7.3節）

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `subject` | `label`（代表表記）、`aliases`（text[]。同義の表現）、`description`、`kind`（TARGET／ASPECT）、`granularity`、`status`（CANDIDATE／ADOPTED） | 対象と観点 |
| `subject_assignment` | `subject_id`、`discovery_id`・`value_id`（2.2節）、`role`、`confidence`、`assigned_by`（SYSTEM／OPERATOR）、`created_decision_id` | 対象と Subject の関連。成立の判断で採用したもの |

### 3.7 見立て（5節）

見立ては作り直してよい。どれも `analysis_version` を持つ。

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `claim` | `post_id`、`content`、`exclusion_reason`（除外したときの理由。除外しても消さない） | ― |
| `finding` | `public_id`、`conversation_id`、`kind`（THEORY／VALUE）、`current_version`、`status`（ACTIVE／MERGED／SPLIT／EXPIRED）、`merged_into_finding_id` | 中身は版に持つ。`current_version` は `finding_version` への遅延の外部キー（Finding と最初の版を同じトランザクションで作る）。統合先が自分自身でないことを CHECK で守る |
| `finding_version` | `finding_id`、`version`、`content`、`backed`（裏付けの有無）、`unconfirmed` | 主キー（`finding_id`, `version`）。Reaction と判断の記録はこの版を指す |
| `finding_version_claim` | `finding_id`、`version`、`claim_id` | 根拠の主張を**版ごとに**持つ。根拠の Post が非公開になったとき、`claim.post_id` から、影響を受ける Finding の版（と今の版か）を引ける |
| `open_question` | `public_id`、`conversation_id`、`content`、`kind`（WHERE／WHY／WHAT／OTHER）、`asker_user_id`、`status`（ACTIVE／MERGED／EXPIRED）、`merged_into_question_id` | 回答の状態は持たない（3.8節から導く）。統合先が自分自身でないことを CHECK で守る |
| `open_question_post` | `question_id`、`post_id` | 問いの Post（人が書いたもの） |
| `open_question_setting` | `question_id`（主キー）、`keep_open`、`updated_by_user_id` | 「引き続き募集するか」。問いを立てた人の意思なので、見立ての `open_question` と分ける（論理設計 5.3節の案A）。統合のときに引き継ぐ |
| `discovery_candidate` | 出どころの列（2.2節）、`proposal`（jsonb。場所・Subject・価値・時間の候補）、`confidence`、`status`（ACTIVE／ESTABLISHED／EXPIRED）、`discovery_id` | 候補の中身は見立てなので jsonb にまとめる |
| `discovery_candidate_finding` | `candidate_id`、`finding_id` | 束ねた Finding |
| `relation_candidate` | `discovery_candidate_id`・`from_discovery_id`（2.2節）、`to_discovery_id`、`kind`（SAME／RELATED／DERIVED／COMPLEMENT／NEW_VALUE）、`reason`、`confidence`、`status`（ACTIVE／DECIDED／EXPIRED） | 却下は判断の記録に残し、同じ組を繰り返し出さない関門にする |
| `search_profile` | 対象の列（2.2節）、`search_text`、`subjects`（text[]）、`seasons`（text[]）、`area`（geography） | 対象ごとに1行（作り直すときは置き換える）。`search_text` に pg_trgm の GIN（**案**）。中身と照合の仕方は F07 で決める |

### 3.8 場所の候補と採用、回答の採用（6.1・6.2・6.4節）

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `place_candidate` | `public_id`、`conversation_id`・`open_question_id`（対象。2.2節）、`place_id` ／ `proposed_name`・`proposed_spatial_type`・`proposed_geom`・`proposed_kinds`・`proposed_existed_from`・`proposed_existed_to`（新しい場所の案）、`created_place_id`、`origin`（AI／HUMAN／ENTRY）、`status`（CONSIDERING／REJECTED／EXPIRED） | 部分一意（対象, `place_id`）で「対象 × 既存の場所」に1件。新しい場所の案どうしが同じかは名称と位置でアプリが判断する。`proposed_geom` に GiST（「このあたりで探している人がいます」で、案を位置を含む場所に寄せる）。採用・取り消しは持たない |
| `place_candidate_basis` | `candidate_id`、`post_id`・`claim_id`・`finding_id`（1つ以上）、`origin`、`added_at` | 根拠 |
| `place_candidate_voice` | `candidate_id`、`user_id`、`stance`（SUPPORT／OPPOSE）、`post_id` | 一意（`candidate_id`, `user_id`）。向きを変えたら更新する |
| `place_candidate_state_change` | `candidate_id`、`from_status`、`to_status`、`moved_from_candidate_id`（問いの統合で移したとき）、`actor_kind`・`actor_user_id`、`reason`、`decision_id` | 状態の履歴。判断の記録は 0..1 |
| `place_adoption` | `public_id`、`candidate_id`、`conversation_id`・`open_question_id`（候補と同じ対象）、`place_id`、`role`・`seq`（対象が会話のとき）、`actor_kind`（QUESTION_OWNER／CONVERSATION_STARTER／RULE_OR_OPERATOR）、`status`（ADOPTED／RECONSIDERING／REVOKED）、`revoke_reason`（READOPTED／WITHDRAWN／INHERITED_BY_MERGE）、`inherited_from_adoption_id`、`owner_agreement`（AGREED／UNCONFIRMED）、`created_decision_id`、`revoked_decision_id`、`adopted_at`、`revoked_at` | 場所の採用の唯一の正本。下の不変条件を制約で守る |
| `question_answer` | `public_id`、`question_id`、`answer_finding_id`＋`answer_finding_version` ／ `answer_post_id`、`actor_kind`（QUESTION_OWNER／RULE_OR_OPERATOR）、`status`・`revoke_reason`・`inherited_from_answer_id`・`owner_agreement`、`created_decision_id`、`revoked_decision_id` | 部分一意（`question_id`）WHERE 採用中・再検討中。回答の Finding は版の外部キー（`finding_version`）で指す |

**場所の採用の不変条件と制約**：

- 対象を候補と食い違わせない：`place_candidate` に一意（`id`, `conversation_id`）・（`id`, `open_question_id`）を置き、`place_adoption` から複合の外部キーで指す。対象の列が空の側は検査されず、値のある側は候補と同じでなければ拒まれる
- 対象が問いのとき、採用中・再検討中は1件まで：部分一意（`open_question_id`）WHERE `status IN ('ADOPTED', 'RECONSIDERING')`
- 対象が会話のときは複数を持ってよいが、同じ候補には1件まで：部分一意（`candidate_id`）WHERE 採用中・再検討中
- 取り消しの理由・時点・判断の記録の有無を、`status = 'REVOKED'` と CHECK でそろえる
- 採用した場所が候補の場所と同じであること（既存の場所なら `place_id`、新しい場所の案なら `created_place_id`）は、採用を作る処理で確かめる（新しい場所の案は、採用の判断の中で Place を作ってから `created_place_id` と採用に入れるため、外部キーだけでは表せない）
- 採用し直しは、1つのトランザクションの中で、前の採用を REVOKED（READOPTED）にしてから新しい採用を足す（部分一意の制約に触れない順番）
- 問いを対象にした採用の「回答した問い」は、対象の `open_question_id` で表す。資料の撮影場所を探す問い（資料を対象にし、問いに答える採用）は資料と一緒に二次開発で足す

### 3.9 判断の記録（6.5節）

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `decision` | `public_id`、`kind`（下記）、`workflow`（判断を行った業務の流れ。下記）、`method`（AUTO／HUMAN）、`actor_kind`（SYSTEM／OPERATOR／USER）、`actor_user_id`、`actor_role`（QUESTION_OWNER／CONVERSATION_STARTER／PROJECT_CREATOR）、`reason_code`、`reason_text`、`rule_version`、`analysis_version`、`decided_at` | `SYSTEM` なら User を持たず `AUTO`。運営・ユーザーなら User を持つ（CHECK） |
| `decision_target` | `decision_id`、`role`（SUBJECT 対象／RESULT 結果／COMPARED 比べた既存）、指す先の列（下記）、`before_state`、`after_state` | 指す先はちょうど1つ（2.2節） |
| `decision_basis_post` | `decision_id`、`post_id` | 固めた根拠の Post（参照で固める） |
| `decision_basis_snapshot` | `decision_id`、`claim_id`・`finding_id`・`open_question_id`、`version`、`content` | 見立ては版の中身を写して固める（論理設計 6.5節） |

- **判断の種類**（`kind`。値はマイグレーションの PR で並べる）：成立、成立の取り消し、統合・統合の取り消し、派生、関連、Discovery の場所・価値の付け替え、場所の採用・採用し直し・再検討中にする・保つ・取り消し、場所の候補の不採用・失効、回答の採用（同じ並び）、問いの統合、関係の候補の却下、公開状態の変更、通報の対応の状態の変更、**見直し（変更なし）**、場所の統合・廃止、運営の登録
- **`decision_target` の指す先の列**：`conversation`、`post`、`project`、`discovery`、`discovery_place`、`discovery_value`、`discovery_relation`、`discovery_candidate`、`relation_candidate`、`place`、`place_candidate`、`place_adoption`、`open_question`、`question_answer`、`content_report`
- 非公開の理由の分類（作った人に出す）は、公開状態の変更の判断の記録の `reason_code` に持つ。分類の一覧は F10 で決める
- **業務の流れ（`workflow`）**：判断の記録が、どの業務の中で行われたかを持つ（**案**の値：`DISCOVERY_REVIEW`（F12 の見直し）、`REPORT_HANDLING`（F10 の通報・事後チェックの対応）、`ANALYSIS`（F02）、`USER_ACTION`（利用者の採用など）、`OPERATOR_SQL`（運営が SQL で補ったもの））。値は F10・F12 で確定する
- S18 の自動で成立した Discovery の「見直しの状態」は、成立の判断より後に、その Discovery を対象にした `workflow = 'DISCOVERY_REVIEW'` の判断の記録（見直し（変更なし）、成立の取り消し、付け替え、統合）があるかで導く。通報の対応などほかの業務の判断では、見直し済みにしない（2026-11-17、PR #150 のレビュー）

### 3.10 Reaction・通報（4.7・8.3節）

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `reaction` | `user_id`、`post_id`・`discovery_id`・`value_id`・`finding_id`・`open_question_id`（2.2節）、`finding_version`、`kind` | 部分一意（`user_id`, 対象, `kind`）を対象の種類ごとに。対象と種類の組み合わせを CHECK で守る（例：裏付けのない説への「そうかも」）。取り消したら行を消す（人が作ったもので、判断の材料ではないため） |
| `content_report` | `public_id`、`reporter_user_id`、`conversation_id`・`post_id`・`discovery_id`・`project_id`（2.2節）、`reason_code`、`description`、`status`、`received_at`、`resolved_at` | `reason_code`・`status` の値は F10 で決める。**案**：理由は RULE_VIOLATION／RIGHTS／SAFETY／OTHER（内容への異論は含めない）、状態は RECEIVED／IN_REVIEW／RESOLVED／DISMISSED |

Reaction の種類（**案**。C10・S03・S14）：

| 対象 | 種類 |
|---|---|
| Post | EMPATHY（共感） |
| Discovery・Value・Finding（価値） | AGREE（わかる）、WANT_TO_GO（行ってみたい） |
| Finding（裏付けのない説） | MAYBE（そうかも） |
| 問い | CURIOUS（自分も気になる） |

### 3.11 運用・画面の都合のデータ

| 表 | 主な列 | 制約・備考 |
|---|---|---|
| `user_conversation_read` | `user_id`、`conversation_id`、`read_until` | 既読（S09 の新しい動き）。論理 Entity にしない。詳細の画面から戻っただけでは消さない（S09） |

### 3.12 統合の自己参照と循環

- 統合先を持つ列（`place.merged_into_place_id`、`discovery_relation`（MERGED）、`finding.merged_into_finding_id`、`open_question.merged_into_question_id`）は、自分自身を指さないことを CHECK で守る
- 循環（A → B → A）は、統合の処理の中で、統合先から先をたどって元に戻らないことを確かめる。DB のトリガーは使わない（PR #150 のレビュー）。運営が SQL で統合するときも同じ確認を行う

## 4. 論理 Entity との対応

### 4.1 MVP で表にするもの

| 論理 Entity（論理設計の節） | 表 |
|---|---|
| User・UserIdentity・UserProfile・ニックネームの変更履歴（4.1） | `app_user`・`user_identity`・`user_profile`・`user_nickname_change` |
| UserInterest（自己申告。7.4） | `user_interest` |
| Place・PlaceRelation（4.2） | `place`・`place_name`・`place_kind`・`place_estimate`・`place_relation` |
| Conversation（4.3） | `conversation` |
| ConversationPlace（4.3.1。導出） | ビュー `conversation_place` |
| Post・PostReference・LocationSharingPrompt（4.4） | `post`・`post_reference`・`location_sharing_prompt` |
| Discovery・DiscoveryPlace・Value・Condition（4.5） | `discovery`・`discovery_place`・`discovery_value`・`value_condition` |
| Project・ProjectItem・企画の変更の履歴（4.6） | `project`・`project_item`・`project_change`・`project_change_item` |
| Reaction（4.7） | `reaction` |
| Claim・Finding・OpenQuestion（5.1〜5.3） | `claim`・`finding`・`finding_version`・`finding_version_claim`・`open_question`・`open_question_post`・`open_question_setting` |
| DiscoveryCandidate・RelationCandidate（5.4・5.5） | `discovery_candidate`・`discovery_candidate_finding`・`relation_candidate` |
| SearchProfile（5.7） | `search_profile` |
| 公開のものだけを返すビュー（2.5節） | `visible_*` |
| PlaceCandidate（＋根拠・声・状態の履歴）・PlaceAdoption・QuestionAnswer（6.1・6.2・6.4） | `place_candidate`・`place_candidate_basis`・`place_candidate_voice`・`place_candidate_state_change`・`place_adoption`・`question_answer` |
| Decision（6.5） | `decision`・`decision_target`・`decision_basis_post`・`decision_basis_snapshot` |
| DiscoveryOrigin・DiscoveryRelation（6.6・6.7） | `discovery_origin`・`discovery_relation` |
| Subject・対象と Subject の関連（7.2・7.3） | `subject`・`subject_assignment` |
| ContentReport（8.3） | `content_report` |

### 4.2 MVP では表にしないもの

| 論理 Entity | 理由 | 足すとき |
|---|---|---|
| Media・PostMedia・DiscoveryMedia、資料を対象にする場所の候補・採用、会話の入口＝資料 | 資料は二次開発（MVP スコープ 14節） | 表を足し、会話の入口・場所の候補・採用・Reaction・通報・判断の記録に `media_id` の列を足す（2.2節） |
| PostTag・TagMapping | タグは MVP の対象外 | ― |
| Proposal | 提案を取り込む専用の流れは二次開発 | 判断の記録の指す先にも列を足す |
| UserInterest（システムの推定）、Theme | MVP の対象外 | `user_interest.origin` の値と `theme_id` の列を足す |
| TimeExpression の歴史的な時間、出来事 | MVP の画面で出さない（対応表 2.2節）、出来事は MVP の対象外 | ― |
| Source・Evidence | MVP では資料を扱わず、画面で出典・根拠を出さない | 資料と一緒に検討する |
| Recommendation・SearchContext・DetourQuery | 一時モデル（保存しない） | ― |

## 5. 対応表 5節の項目のうち、本書で決めたもの

対応表 6節で「DB V2 で、必要になった時点で決める」としたもの。

| 項目 | 決めたこと（案） |
|---|---|
| 「見直し（変更なし）」の判断の種類 | 判断の種類に足す（3.9節）。S18 の見直しの状態はここから導く |
| Place の統合先の持ち方 | `place.status = 'MERGED'` と `merged_into_place_id`。CHECK でそろえる。論理設計 4.2節の「状態：統合済み」に統合先を足す（本 PR で論理設計を直す） |
| 通報の対応の状態・理由の分類 | 列と **案** の値を置く（3.10節）。値は F10 で確定し、マイグレーションの CHECK に反映する |
| 非公開の理由の分類 | 公開状態の変更の判断の記録の `reason_code`（3.9節）。一覧は F10 |
| 新しい動きの既読 | `user_conversation_read`（3.11節） |
| 解析の状態（反映が遅れていること） | 本書では持たない。F02 の運用の記録（DEC-0018）から出す |
| 起点の既定のエリア | DB に持たない。始める地域はアプリの設定（場所の `public_id`）、ログインしている人は `user_interest` の興味のある地域 |
| TOP の並びを保つための種 | 保存しない |
| 寄り道の到達圏の事前計算 | F06 の設計（#161）で足す |
| 企画そのものの会話の同時投稿の重複防止 | `conversation` の部分一意（`entry_project_id`）WHERE `is_project_own`（3.3節） |

## 6. レビューで決めたこと（2026-11-17、PR #150）

本書の初版で判断待ちにした3つは、次のように決めた。

| 項目 | 決めたこと |
|---|---|
| 成立の取り消しの持ち方 | `discovery.withdrawn_at` に持ち、公開状態とは分ける（3.4節）。一般の読み取りでは成立の取り消しも見る（`visible_discovery`） |
| 一般向けの読み取り | 公開のものだけを返すビューを通す（2.5節）。運営・本人向けの読み取りの権限は別に確かめる |
| 並びの履歴 | 「並びの履歴を MVP で残すか」ではなく、**企画の変更の履歴を MVP で持ち、企画そのものの会話のタイムラインに出す**（3.5節）。持ち方（差分の出来事）は本書の案。専用の画面と、提案の Post との結び付けは継続検討 |

あわせて、データの保持の原則（1.1節）を足した。

## 7. 後続

- **テーブル定義**：[db/migration/V2__recreate_tables_for_mvp.sql](../db/migration/V2__recreate_tables_for_mvp.sql)（V1 の表を消し、本書の表・ビュー・関数を作る）。サンプルデータ：[db/sample/R__sample_data.sql](../db/sample/R__sample_data.sql)（論理設計 10節・MVP スコープ 16節のシナリオから、横浜の始める地域で）。制約・ビューの確認：[db/check/](../db/check/README.md)
- API（`apps/api`）・画面（`apps/web`）を新しい表に合わせる（V2 を入れると、直すまで動かない）
- F06（到達圏の表）、F02（解析の運用の記録）、F07（検索の中身）、F10（通報・事後チェックの値）で、本書の表に足す・値を決める
- 本書で論理設計に足りなかったことを決めたときは、論理設計を併せて直す（対応表 6節）。本 PR では Place の統合先、成立の取り消し、企画の変更の履歴、Finding の根拠を版ごとに持つこと、統合の自己参照を直した
- マイグレーションの PR で、PostgreSQL 18 ＋ PostGIS ＋ Flyway の上で次を確かめる（PR #150 のレビュー）：採用・取り消し・採用し直しの整合、同時投稿での企画そのものの会話の重複防止、公開の Post の数の同時更新、複合の外部キーと NULL の制約、公開のビューの境界

### 7.1 後続の工程への申し送り（PR #150 の再レビュー）

本書のマージを止めるものではないが、後続の工程で漏れなく扱う確認事項。どれも、その工程の設計・実装の中で扱い、独立した再設計の工程は設けない。決定済みの事項と継続検討の事項を分けて進める。

| 工程 | 確認すること |
|---|---|
| DDL・企画の更新の API | 企画・項目の更新と `project_change`・`project_change_item` の登録を同じトランザクションで行う。失敗時のロールバック、同時更新での差分の整合、項目の追加・外す・順番の変更と、作った時点からの並びの再現を確かめる |
| 退会・削除・公開制御の設計（規約を含む） | 企画の変更の履歴そのものの削除・匿名化の要否、保持期間、退会後の表示、参照先（場所・Discovery）の削除・非公開・成立の取り消し・統合のときの扱い、本人・運営・一般のアクセスの権限（3.5節）。「削除の求めには企画と Post の側で応じればよい」とは言い切らない |
| S15・タイムラインの具体化 | 変更の出来事の文言、1回の保存で多くの項目が変わったときのまとめ方、表題・呼びかけ・説明を変えた事実を出すか、運営の企画でも出すか。Post と出来事の時点の順の並べ方、企画そのものの会話がまだないときの出し方。**継続検討のまま**扱うもの（採用を決めたものとして扱わない）：過去の並びを再現して見る専用の画面を MVP に設けるか、提案のもとの Post との明示的な結び付け |
| F02・F07・F10 | 非公開の Post・会話を根拠にした Finding・問い・場所の候補・要約・SearchProfile 等の扱いと作り直し、公開の読み取りと関連・派生の情報からの漏えい防止。公開状態は連動させない（2.5節）ので、非公開の理由が個人情報・権利侵害等のときに、派生した情報の公開の可否を判断する手順を決める。**一般のユーザーから投稿を受け付ける外部公開の前に確定する**（PR #154 のレビュー）。F10 では、企画の事後チェックに要る更新の検知が `updated_at` で足りるか |
| F10・F12 | 判断の記録の `workflow` などの値を確定し、通報の対応と Discovery の見直しを区別する |
| 統合の処理の設計・実装 | 自己参照・循環の防止を、同時の更新と、運営が SQL で行う操作も含めて確かめる |
| マイグレーションの PR | 上の7節の確認（PostgreSQL 18 ＋ PostGIS ＋ Flyway）。企画の変更の履歴の整合は、DDL・API の実装の段階で確かめる |
