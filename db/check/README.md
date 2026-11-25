# db/check

DB V2〜V4 の制約・ビューが、設計（[DB 設計 v0.2](../../docs/ex-day_db_design.md) 7節・7.1節）のとおりに働くかを確かめる SQL と手順。Flyway では流さない。

## v2_checks.sql

サンプルデータを入れた DB で流す。全体を1つのトランザクションで行い、最後に ROLLBACK するので、データは変わらない。失敗したところで `NG: ...` のエラーで止まる。

```bash
docker compose up -d db flyway
docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v2_checks.sql
```

最後に `v2_checks: すべて OK` と出れば通っている。確かめること：

1. 場所の採用・採用し直し・再検討中・取り消し（問いの採用中は1件まで、取り消した行も残る、取り消すと場所未定に戻る）
2. 複合の外部キーと NULL（候補と違う対象・別の会話の候補の採用を拒む、対象を2つ持つ採用を拒む）
3. 企画そのものの会話は企画ごとに1つ
4. 公開のビューの境界（非公開の Post・会話・企画、成立を取り消した Discovery、入口が非公開でも会話は出る、見えない Discovery を指す企画の項目）
5. 企画の変更の履歴（出来事から今の並びを再現できる、「作った」は1つ、中身を写す項目名を拒む、順番の入れ替え）
6. 統合の自己参照
7. そのほかの制約（Reaction の対象と種類、場所の共有の誘いは1件、判断の記録の主体、由来の種類と対象）

## v3_checks.sql

DB V3（駅・行き方・到達点。[F06](../../docs/ui/functions/F06-detour-reach.md)「データの形」）の制約を確かめる。v2_checks.sql と同じく、サンプルデータを入れた DB で流し、最後に ROLLBACK する。

```bash
docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v3_checks.sql
```

最後に `v3_checks: すべて OK` と出れば通っている。確かめること：

1. 駅：サンプルの駅の場所はすべて計算の中の駅を持つ、同じ路線・同じ駅の場所は1行、年度ごとの駅コードは一意、駅の位置は点か線
2. 到達点は点だけ
3. 行き方：着く先は同じ場所の到達点だけ（複合の外部キー）、人が確かめたものは確かめた時点を持つ、駅と行き方の先が同じ場所は拒む、判断の記録を持つ、分数は0以上（駅直結・駅構内は0分）
4. 駅を指す列（`reach.station.place_id`・`place_access.station_place_id`）は種類が「駅」の場所だけ。駅として指されている場所から「駅」の種類を外す・書き換えるのも拒む。制約のトリガーは commit の時点で確かめるので、同じトランザクションで駅を先に足しても「駅」の種類を足せば通る（スクリプトでは `SET CONSTRAINTS ALL IMMEDIATE` で文ごとに確かめる）

## v4_checks.sql

DB V4（到達圏の版・鉄道のグラフ・駅から場所への歩き。F06「データの形」5・6）の制約を確かめる。流し方は v3_checks.sql と同じ。

```bash
docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v4_checks.sql
```

1. 版：ACTIVE は1つだけ、ACTIVE は使い始めた時点を持つ、版の名前は英数字と `._-`
2. グラフ：乗車の辺は路線を持ち乗換の辺は持たない、所要時間は0以上
3. 駅から場所への歩き：片道15分まで、同じ版・駅・場所・到達点は1行（到達点のない行も1行）
4. 版を消すと、その版のグラフ・駅の点・歩きも消える

## 同時の更新（2つの接続で確かめる）

### 企画そのものの会話を2人が同時に作る

1つ目の接続で作り、commit する前に待つ。その間に2つ目の接続で同じ企画に作ると、2つ目は1つ目の commit を待ってから一意の制約（`conversation_project_own_uniq`）で失敗する。アプリは、このとき、できている会話に Post を足し直す。

```bash
docker compose exec -T db psql -U exday exday -c "BEGIN; INSERT INTO conversation (entry_project_id, is_project_own, starter_user_id) SELECT id, true, creator_user_id FROM project WHERE title = '港の見える散歩コース'; SELECT pg_sleep(3); COMMIT;" &
sleep 1
docker compose exec -T db psql -U exday exday -c "INSERT INTO conversation (entry_project_id, is_project_own, starter_user_id) SELECT id, true, creator_user_id FROM project WHERE title = '港の見える散歩コース';"
wait
```

2つ目が `duplicate key value violates unique constraint "conversation_project_own_uniq"` で失敗すれば通っている。確かめたら `docker compose up flyway` でサンプルを入れ直す（R__ の内容が変わっていないと入れ直されないので、`docker compose down -v` から作り直す）。

### 公開の Post の数を同時に足す

Post を足すのと同じトランザクションで `public_post_count = public_post_count + 1` にする。会話の行の更新は行ロックで順番になるので、同時に足しても数がずれない。20 の接続から同時に足し、数が `visible_post` の件数と合うことを確かめる。

```bash
for i in $(seq 1 20); do
  docker compose exec -T db psql -U exday exday -q -c "BEGIN; INSERT INTO post (conversation_id, user_id, body) SELECT id, starter_user_id, 'check $i' FROM conversation WHERE id = 2; UPDATE conversation SET public_post_count = public_post_count + 1, last_public_posted_at = greatest(last_public_posted_at, now()) WHERE id = 2; COMMIT;" &
done; wait
docker compose exec -T db psql -U exday exday -c "SELECT c.public_post_count, (SELECT count(*) FROM visible_post vp WHERE vp.conversation_id = c.id) FROM conversation c WHERE id = 2;"
```

2つの数が同じなら通っている。
