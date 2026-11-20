-- ex-day：DB V3 の制約の確認（駅・行き方・到達点。F06「データの形」、PR #165）
--
-- サンプルデータ（db/sample/R__sample_data.sql）を入れた DB で流す。全体を1つのトランザクションで行い、
-- 最後に ROLLBACK するので、データは変わらない。失敗したところで止まる（ERROR: NG: ...）。
--
--   docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v3_checks.sql

\set ON_ERROR_STOP 1
BEGIN;
-- 駅を指す列の制約のトリガーは commit の時点で確かめる（DEFERRABLE INITIALLY DEFERRED）。ここでは文ごとに確かめる
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

-- 23505 unique_violation、23503 foreign_key_violation、23514 check_violation

-- 1. 駅：同じ路線の同じ駅の場所は1行。年度ごとの駅コードは一意
SELECT pg_temp.expect('サンプルの駅の場所はすべて計算の中の駅を持つ', NOT EXISTS (
    SELECT 1 FROM place p JOIN place_kind k ON k.place_id = p.id AND k.kind = '駅'
     WHERE NOT EXISTS (SELECT 1 FROM reach.station s WHERE s.place_id = p.id)));
SELECT pg_temp.expect_error('同じ路線・同じ駅の場所の2行目', $q$
    INSERT INTO reach.station (place_id, line_id, name, geom)
    SELECT place_id, line_id, name, geom FROM reach.station LIMIT 1
$q$, '23505');
SELECT pg_temp.expect_error('同じ年度の同じ駅コード', $q$
    INSERT INTO reach.station_source (station_id, dataset_year, station_code, group_code)
    SELECT (SELECT max(id) FROM reach.station), dataset_year, station_code, group_code
      FROM reach.station_source WHERE station_id = (SELECT min(id) FROM reach.station)
$q$, '23505');
SELECT pg_temp.expect_error('駅の位置が面', $q$
    INSERT INTO reach.station (place_id, line_id, name, geom)
    SELECT pg_temp.plid('kozukue-st'), (SELECT id FROM reach.line WHERE name = '東横線'), '小机',
           ST_GeogFromText('SRID=4326;POLYGON((139.59 35.50, 139.60 35.50, 139.60 35.51, 139.59 35.50))')
$q$, '23514');

-- 2. 到達点：点だけ
SELECT pg_temp.expect_error('到達点が線', $q$
    INSERT INTO place_arrival_point (place_id, geom, kind, source)
    VALUES (pg_temp.plid('yamashita'), ST_GeogFromText('SRID=4326;LINESTRING(139.65 35.44, 139.66 35.45)'), 'ESTIMATED', 'OPERATOR')
$q$, '23514');

-- 3. 行き方：着く先は同じ場所の到達点だけ。人が確かめたものは確かめた時点を持つ。駅と場所が同じものは拒む
SELECT pg_temp.expect_error('別の場所の到達点を着く先にする', $q$
    INSERT INTO place_access (place_id, station_place_id, mode, minutes, arrival_point_id, source, verification, created_decision_id)
    SELECT pg_temp.plid('yamashita'), pg_temp.plid('ishikawacho'), 'WALK', 14, a.id, 'OPERATOR', 'UNVERIFIED',
           (SELECT created_decision_id FROM place_access LIMIT 1)
      FROM place_arrival_point a WHERE a.place_id = pg_temp.plid('kozukue-castle')
$q$, '23503');
SELECT pg_temp.expect_error('人が確かめたのに確かめた時点がない', $q$
    INSERT INTO place_access (place_id, station_place_id, mode, minutes, source, verification, created_decision_id)
    SELECT pg_temp.plid('yamashita'), pg_temp.plid('ishikawacho'), 'WALK', 14, 'OPERATOR', 'HUMAN_VERIFIED',
           (SELECT created_decision_id FROM place_access LIMIT 1)
$q$, '23514');
SELECT pg_temp.expect_error('駅と行き方の先が同じ場所', $q$
    INSERT INTO place_access (place_id, station_place_id, mode, minutes, source, verification, created_decision_id)
    SELECT pg_temp.plid('ishikawacho'), pg_temp.plid('ishikawacho'), 'WALK', 1, 'OPERATOR', 'UNVERIFIED',
           (SELECT created_decision_id FROM place_access LIMIT 1)
$q$, '23514');
SELECT pg_temp.expect_error('判断の記録のない行き方', $q$
    INSERT INTO place_access (place_id, station_place_id, mode, minutes, source, verification)
    VALUES (pg_temp.plid('yamashita'), pg_temp.plid('ishikawacho'), 'WALK', 14, 'OPERATOR', 'UNVERIFIED')
$q$, '23502');
SELECT pg_temp.expect_error('分数が負', $q$
    INSERT INTO place_access (place_id, station_place_id, mode, minutes, source, verification, created_decision_id)
    SELECT pg_temp.plid('yamashita'), pg_temp.plid('ishikawacho'), 'WALK', -1, 'OPERATOR', 'UNVERIFIED',
           (SELECT created_decision_id FROM place_access LIMIT 1)
$q$, '23514');
-- 駅直結・駅構内は 0 分で持てる（OpenAPI の DetourAccess.minutes と同じ）
INSERT INTO place_access (place_id, station_place_id, mode, minutes, source, verification, created_decision_id)
SELECT pg_temp.plid('minatomirai'), pg_temp.plid('sakuragicho'), 'WALK', 0, 'OPERATOR', 'UNVERIFIED',
       (SELECT created_decision_id FROM place_access LIMIT 1);
SELECT pg_temp.expect('徒歩0分の行き方を持てる', EXISTS (
    SELECT 1 FROM place_access WHERE place_id = pg_temp.plid('minatomirai') AND minutes = 0));

-- 4. 駅を指す列は、種類が「駅」の場所だけ（制約のトリガー）
SELECT pg_temp.expect_error('行き方の駅に駅でない場所', $q$
    INSERT INTO place_access (place_id, station_place_id, mode, minutes, source, verification, created_decision_id)
    SELECT pg_temp.plid('yamashita'), pg_temp.plid('kishamichi'), 'WALK', 5, 'OPERATOR', 'UNVERIFIED',
           (SELECT created_decision_id FROM place_access LIMIT 1)
$q$, '23514');
SELECT pg_temp.expect_error('行き方の駅を駅でない場所に変える', $q$
    UPDATE place_access SET station_place_id = pg_temp.plid('kishamichi') WHERE place_id = pg_temp.plid('kozukue-castle')
$q$, '23514');
SELECT pg_temp.expect_error('計算の中の駅に駅でない場所', $q$
    INSERT INTO reach.station (place_id, line_id, name, geom)
    SELECT pg_temp.plid('yamashita'), (SELECT id FROM reach.line WHERE name = '東横線'), '山下公園',
           ST_GeogFromText('SRID=4326;POINT(139.65 35.44)')
$q$, '23514');
SELECT pg_temp.expect_error('駅として指されている場所から「駅」の種類を外す', $q$
    DELETE FROM place_kind WHERE place_id = pg_temp.plid('kozukue-st') AND kind = '駅'
$q$, '23514');
SELECT pg_temp.expect_error('駅として指されている場所の「駅」の種類を書き換える', $q$
    UPDATE place_kind SET kind = '公園' WHERE place_id = pg_temp.plid('ishikawacho') AND kind = '駅'
$q$, '23514');
-- 同じトランザクションで場所・種類・駅を足すとき、駅を先に足しても commit の時点で「駅」があれば通る
SET CONSTRAINTS ALL DEFERRED;
INSERT INTO place (name, spatial_type, geom, origin, source_dataset, source_record_id)
VALUES ('確認用の駅', 'POINT', ST_GeogFromText('SRID=4326;POINT(139.70 35.60)'), 'OPEN_DATA', 'CHECK', 'check-station');
INSERT INTO reach.station (place_id, line_id, name, geom)
SELECT p.id, (SELECT id FROM reach.line WHERE name = '東横線'), '確認用', p.geom FROM place p WHERE p.source_record_id = 'check-station';
INSERT INTO place_kind (place_id, kind) SELECT id, '駅' FROM place WHERE source_record_id = 'check-station';
SET CONSTRAINTS ALL IMMEDIATE;
SELECT pg_temp.expect('駅を先に足しても、同じトランザクションで「駅」の種類を足せば通る',
    EXISTS (SELECT 1 FROM reach.station s JOIN place p ON p.id = s.place_id WHERE p.source_record_id = 'check-station'));

ROLLBACK;
\echo 'v3_checks: すべて OK（変更は ROLLBACK した）'
