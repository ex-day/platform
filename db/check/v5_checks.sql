-- ex-day：DB V5 の確認（版の歩きのグラフ・場所ごとの作り直し・事業者と路線の呼び名。F06「データの形」6・8、#168）
--
-- サンプルデータ（db/sample/R__sample_data.sql）を入れた DB で流す。全体を1つのトランザクションで行い、
-- 最後に ROLLBACK するので、データは変わらない。失敗したところで止まる（ERROR: NG: ...）。
--
--   docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v5_checks.sql

\set ON_ERROR_STOP 1
BEGIN;
SET CONSTRAINTS ALL IMMEDIATE;

CREATE FUNCTION pg_temp.expect_error(label text, stmt text, expected text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE stmt;
    EXCEPTION WHEN OTHERS THEN
        IF SQLSTATE = expected THEN
            RAISE NOTICE 'OK: % (%)', label, SQLSTATE;
            RETURN;
        END IF;
        RAISE EXCEPTION 'NG: % は % で拒まれるはずが % (%)', label, expected, SQLSTATE, SQLERRM;
    END;
    RAISE EXCEPTION 'NG: % が拒まれなかった', label;
END $$;

CREATE FUNCTION pg_temp.expect(label text, ok boolean) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF ok IS NOT TRUE THEN RAISE EXCEPTION 'NG: %', label; END IF;
    RAISE NOTICE 'OK: %', label;
END $$;

CREATE FUNCTION pg_temp.plid(k text) RETURNS bigint LANGUAGE sql
    AS $$ SELECT id FROM place WHERE public_id = substr(md5('ex-day-sample:place:' || k), 1, 12) $$;

-- 確かめる版の歩きのグラフ：東西に 80m ごとの点 1〜6（辺は1分ずつ）と、点6から20分の点 21。駅（桜木町）は点1に結ぶ
CREATE FUNCTION pg_temp.pt(n int) RETURNS geometry LANGUAGE sql IMMUTABLE AS $$
    SELECT ST_SetSRID(ST_MakePoint(139.6400 + 0.000882 * CASE WHEN n = 21 THEN 25 ELSE n - 1 END, 35.4400), 4326)
$$;
CREATE FUNCTION pg_temp.check_build() RETURNS bigint LANGUAGE sql AS $$ SELECT id FROM reach.build WHERE version = 'check.walk' $$;
CREATE FUNCTION pg_temp.job(p bigint) RETURNS boolean LANGUAGE sql AS $$
    SELECT EXISTS (SELECT 1 FROM reach.place_walk_job WHERE build_id = pg_temp.check_build() AND place_id = p)
$$;

-- 23505 unique_violation、23514 check_violation

-- 1. 事業者・路線の呼び名：表にあれば呼び名、無ければ N02 の名前。同じ名前は1行
SELECT pg_temp.expect('東日本旅客鉄道は JR東日本、横浜市の3号線はブルーライン',
    (SELECT display_name FROM reach.operator_display WHERE operator = '東日本旅客鉄道') = 'JR東日本'
    AND (SELECT display_name FROM reach.line_display WHERE operator = '横浜市' AND name = '3号線') = 'ブルーライン');
SELECT pg_temp.expect_error('同じ事業者の2つ目の呼び名', $q$
    INSERT INTO reach.operator_display (operator, display_name) VALUES ('東日本旅客鉄道', 'JR')
$q$, '23505');
SELECT pg_temp.expect_error('空の呼び名', $q$
    INSERT INTO reach.line_display (operator, name, display_name) VALUES ('東日本旅客鉄道', '根岸線', '')
$q$, '23514');

-- 2. 歩きのグラフを持つ版を作る（READY）。版の行は場所の変更と同じ鍵を取ってから足す（tools/reach と同じ）
SELECT pg_advisory_xact_lock(reach.build_lock_key());
INSERT INTO reach.build (version, status) VALUES ('check.walk', 'READY');
CREATE TEMP TABLE check_build_id AS SELECT pg_temp.check_build() AS id;
INSERT INTO reach.walk_graph (build_id, walk_cap_minutes, walk_meters_per_minute, snap_meters, min_island_nodes)
VALUES (pg_temp.check_build(), 15, 80, 300, 100);
INSERT INTO reach.walk_node (build_id, node_id, geom)
SELECT pg_temp.check_build(), n, pg_temp.pt(n) FROM (SELECT generate_series(1, 6) UNION ALL SELECT 21) AS s (n);
INSERT INTO reach.walk_edge (build_id, source, target, cost, reverse_cost, geom)
SELECT pg_temp.check_build(), a, b, c, c, ST_MakeLine(pg_temp.pt(a), pg_temp.pt(b))
  FROM (VALUES (1, 2, 1.0), (2, 3, 1.0), (3, 4, 1.0), (4, 5, 1.0), (5, 6, 1.0), (6, 21, 20.0)) AS e (a, b, c);
INSERT INTO reach.walk_station (build_id, station_place_id, node_id, access_m)
VALUES (pg_temp.check_build(), pg_temp.plid('sakuragicho'), 1, 0);
SELECT pg_temp.expect_error('負の歩きの辺', $q$
    INSERT INTO reach.walk_edge (build_id, source, target, cost, reverse_cost, geom)
    SELECT pg_temp.check_build(), 1, 2, -1, 1, ST_MakeLine(pg_temp.pt(1), pg_temp.pt(2))
$q$, '23514');

-- 3. 場所を足すと、版ごとの待ち行列に積まれる。作り直すと、点3（駅から2分）への歩きを持つ
INSERT INTO place (public_id, name, spatial_type, geom, origin)
VALUES ('chk5park0001', '確かめる公園', 'POINT', pg_temp.pt(3)::geography, 'OPERATOR');
SELECT pg_temp.expect('場所を足すと、作り直しの待ち行列に積まれる（確かめる版・サンプルの版）',
    pg_temp.job((SELECT id FROM place WHERE public_id = 'chk5park0001'))
    AND EXISTS (SELECT 1 FROM reach.place_walk_job j JOIN reach.build b ON b.id = j.build_id
                 WHERE b.version = 'sample.1' AND j.place_id = (SELECT id FROM place WHERE public_id = 'chk5park0001')));
INSERT INTO place_kind (place_id, kind) SELECT id, '公園' FROM place WHERE public_id = 'chk5park0001';
SELECT pg_temp.expect('作り直すと駅から場所の代表点への歩きを1行作る',
    reach.refresh_place_walk(pg_temp.check_build(), (SELECT id FROM place WHERE public_id = 'chk5park0001')) = 1);
SELECT pg_temp.expect('駅（点1）から場所（点3）は2分',
    (SELECT abs(walk_min - 2) < 1e-6 FROM reach.place_walk
      WHERE build_id = pg_temp.check_build() AND place_id = (SELECT id FROM place WHERE public_id = 'chk5park0001')
        AND station_place_id = pg_temp.plid('sakuragicho') AND arrival_point_id IS NULL));

-- 4. 到達点（徒歩）を足すと、到達点ごとの行もできる。消すと、その到達点の行も消える
DELETE FROM reach.place_walk_job;
INSERT INTO place_arrival_point (place_id, geom, kind, source)
SELECT id, pg_temp.pt(4)::geography, 'CONFIRMED_ENTRANCE', 'OPERATOR' FROM place WHERE public_id = 'chk5park0001';
SELECT pg_temp.expect('到達点を足すと待ち行列に積まれる', pg_temp.job((SELECT id FROM place WHERE public_id = 'chk5park0001')));
-- （作り直しと行の確かめは別の文にする。同じ文の副問い合わせは、関数より先に読まれることがある）
SELECT pg_temp.expect('作り直すと、代表点と到達点の2行',
    reach.refresh_place_walk(pg_temp.check_build(), (SELECT id FROM place WHERE public_id = 'chk5park0001')) = 2);
SELECT pg_temp.expect('駅（点1）から到達点（点4）は3分',
    (SELECT abs(walk_min - 3) < 1e-6 FROM reach.place_walk
          WHERE build_id = pg_temp.check_build() AND arrival_point_id IS NOT NULL
            AND place_id = (SELECT id FROM place WHERE public_id = 'chk5park0001')));

-- 5. 徒歩の行き方がある組は、上限（15分）を超えても道のりを持ち、版で使う行き方に記録する
INSERT INTO place_arrival_point (place_id, geom, kind, source)
SELECT id, pg_temp.pt(21)::geography, 'ESTIMATED', 'OSM_ESTIMATED' FROM place WHERE public_id = 'chk5park0001';
INSERT INTO place_access (place_id, station_place_id, mode, minutes, arrival_point_id, source, verification, created_decision_id)
SELECT p.id, pg_temp.plid('sakuragicho'), 'WALK', 20, a.id, 'OPERATOR', 'UNVERIFIED',
       (SELECT created_decision_id FROM place_access LIMIT 1)
  FROM place p JOIN place_arrival_point a ON a.place_id = p.id AND ST_Equals(a.geom::geometry, pg_temp.pt(21))
 WHERE p.public_id = 'chk5park0001';
SELECT reach.refresh_place_walk(pg_temp.check_build(), (SELECT id FROM place WHERE public_id = 'chk5park0001'));
SELECT pg_temp.expect('行き方の着く先（点21、25分）は上限を超えても行を持つ',
    (SELECT abs(walk_min - 25) < 1e-6 FROM reach.place_walk w JOIN place_arrival_point a ON a.id = w.arrival_point_id
      WHERE w.build_id = pg_temp.check_build() AND ST_Equals(a.geom::geometry, pg_temp.pt(21))));
SELECT pg_temp.expect('行き方を版で使う行き方に記録する',
    EXISTS (SELECT 1 FROM reach.build_access ba JOIN place_access pa ON pa.id = ba.place_access_id
             WHERE ba.build_id = pg_temp.check_build()
               AND pa.place_id = (SELECT id FROM place WHERE public_id = 'chk5park0001')));
DELETE FROM place_arrival_point a USING place p
 WHERE p.id = a.place_id AND p.public_id = 'chk5park0001' AND ST_Equals(a.geom::geometry, pg_temp.pt(4));
SELECT pg_temp.expect('到達点を消すと、その到達点への歩きも消える（代表点と点21の2行が残る）',
    (SELECT count(*) FROM reach.place_walk
      WHERE build_id = pg_temp.check_build() AND place_id = (SELECT id FROM place WHERE public_id = 'chk5park0001')) = 2);
UPDATE place_access SET status = 'INVALID' WHERE place_id = (SELECT id FROM place WHERE public_id = 'chk5park0001');
SELECT reach.refresh_place_walk(pg_temp.check_build(), (SELECT id FROM place WHERE public_id = 'chk5park0001'));
SELECT pg_temp.expect('行き方を無効にして作り直すと、上限を超える行と版で使う行き方の記録が消える',
    (SELECT count(*) FROM reach.place_walk
      WHERE build_id = pg_temp.check_build() AND place_id = (SELECT id FROM place WHERE public_id = 'chk5park0001')) = 1
    AND NOT EXISTS (SELECT 1 FROM reach.build_access ba JOIN place_access pa ON pa.id = ba.place_access_id
                     WHERE ba.build_id = pg_temp.check_build()
                       AND pa.place_id = (SELECT id FROM place WHERE public_id = 'chk5park0001')));

-- 6. 寄り道先にならない場所（ACTIVE でない・駅）は、作り直すと行が消える
UPDATE place SET status = 'ABOLISHED' WHERE public_id = 'chk5park0001';
SELECT pg_temp.expect('無くなった場所は、作り直すと0行',
    reach.refresh_place_walk(pg_temp.check_build(), (SELECT id FROM place WHERE public_id = 'chk5park0001')) = 0);
SELECT pg_temp.expect('無くなった場所の行は消える',
    NOT EXISTS (SELECT 1 FROM reach.place_walk
                     WHERE build_id = pg_temp.check_build()
                       AND place_id = (SELECT id FROM place WHERE public_id = 'chk5park0001')));

-- 7. 歩きのグラフの無い版（サンプルの手で作った版）では何もしない（手で置いた行を消さない）
SELECT pg_temp.expect('サンプルの版では NULL を返す',
    reach.refresh_place_walk((SELECT id FROM reach.build WHERE version = 'sample.1'), pg_temp.plid('kozukue-castle')) IS NULL);
SELECT pg_temp.expect('サンプルの版の手で置いた行が残る',
    EXISTS (SELECT 1 FROM reach.place_walk w JOIN reach.build b ON b.id = w.build_id
                 WHERE b.version = 'sample.1' AND w.place_id = pg_temp.plid('kozukue-castle')));

-- 8. 版を消すと、歩きのグラフ・駅の点・待ち行列も消える
INSERT INTO reach.place_walk_job (build_id, place_id) SELECT pg_temp.check_build(), pg_temp.plid('kozukue-castle');
DELETE FROM reach.build WHERE version = 'check.walk';
SELECT pg_temp.expect('版を消すと、歩きのグラフ・駅の点・待ち行列も消える',
    NOT EXISTS (SELECT 1 FROM reach.walk_graph WHERE build_id = (SELECT id FROM check_build_id))
    AND NOT EXISTS (SELECT 1 FROM reach.walk_node WHERE build_id = (SELECT id FROM check_build_id))
    AND NOT EXISTS (SELECT 1 FROM reach.walk_edge WHERE build_id = (SELECT id FROM check_build_id))
    AND NOT EXISTS (SELECT 1 FROM reach.walk_station WHERE build_id = (SELECT id FROM check_build_id))
    AND NOT EXISTS (SELECT 1 FROM reach.place_walk_job WHERE build_id = (SELECT id FROM check_build_id)));

ROLLBACK;
\echo 'v5_checks: すべて OK（変更は ROLLBACK した）'
