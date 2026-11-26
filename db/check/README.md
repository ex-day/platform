# db/check

DB V2〜V6 の制約・ビューが、設計（[DB 設計 v0.2](../../docs/ex-day_db_design.md) 7節・7.1節）のとおりに働くかを確かめる SQL と手順。Flyway では流さない。

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
3. 駅から場所への歩き：0分以上（徒歩の行き方と比べるため上限の15分を超える行も持てる）、同じ版・駅・場所・到達点は1行（到達点のない行も1行）
4. 版を消すと、その版のグラフ・駅の点・歩きも消える

## v5_checks.sql

DB V5（版の歩きのグラフ・場所ごとの作り直し・事業者と路線の呼び名。F06「データの形」6・8）を確かめる。流し方は v3_checks.sql と同じ。中で、東西に 80m ごとの点を並べた小さな歩きのグラフを持つ版（`check.walk`）を作る（サンプルの版は歩きのグラフを持たないため）。

```bash
docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v5_checks.sql
```

1. 呼び名：東日本旅客鉄道は JR東日本、横浜市の3号線はブルーライン。同じ事業者・路線の呼び名は1行、空の呼び名は拒む
2. 歩きのグラフ：辺の所要時間は0以上
3. 場所を足すと、版ごとの待ち行列に積まれる。作り直すと、駅から場所の代表点までの歩き（2分）を持つ
4. 到達点（徒歩）を足すと待ち行列に積まれ、作り直すと到達点ごとの行もできる（3分）
5. 徒歩の行き方がある組は、上限（15分）を超えても道のり（25分）を持ち、版で使う行き方に記録する。到達点を消すと、その到達点への歩きも消える。行き方を無効にして作り直すと、上限を超える行と記録が消える
6. 無くなった場所（ABOLISHED）は、作り直すと行が消える
7. 歩きのグラフの無い版（サンプルの版）では何もしない（手で置いた行を消さない）
8. 版を消すと、歩きのグラフ・駅の点・待ち行列も消える

同じ文の中で作り直しと行の確かめをすると、副問い合わせが関数より先に読まれることがあるので、スクリプトでは文を分けている。

## v6_checks.sql

DB V6（探索用の路線（運行系統）。F06「データの形」9）を確かめる。流し方は v3_checks.sql と同じ。

```bash
docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v6_checks.sql
```

1. サンプルの京浜東北線（鶴見〜横浜〜桜木町〜石川町）は、停車駅4・乗車の辺3・点4（系統の点は駅の点でもある）
2. 点・辺は N02 の路線か系統のどちらか1つに属する。乗換の辺はどちらにも属さない
3. 同じ版で同じ識別子の系統は1つ、識別子は英小文字・数字・ハイフン、速さは正。停車の順は系統の中で1つ。同じ駅は並びに2回置ける
4. 置き換えた路線は版ごとに1つ。版を消すと、系統・停車駅・系統の点と辺・置き換えた路線も消える

系統を版に足す計算（停車駅の探し方・線路のたどり方・置き換えの確かめ）は `tools/reach/sql/build_service.sql` にあり、ここでは確かめない（`tools/reach/README.md`「探索用の路線」）。

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
