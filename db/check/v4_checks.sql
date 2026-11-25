-- ex-day：DB V4 の制約の確認（到達圏の版・鉄道のグラフ・駅から場所への歩き。F06「データの形」5・6、PR #165）
--
-- サンプルデータ（db/sample/R__sample_data.sql）を入れた DB で流す。全体を1つのトランザクションで行い、
-- 最後に ROLLBACK するので、データは変わらない。失敗したところで止まる（ERROR: NG: ...）。
--
--   docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v4_checks.sql

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

-- 23505 unique_violation、23514 check_violation

-- 1. 版：ACTIVE は1つだけ。ACTIVE は使い始めた時点を持つ
SELECT pg_temp.expect('サンプルの ACTIVE の版は1つ', (SELECT count(*) FROM reach.build WHERE status = 'ACTIVE') = 1);
SELECT pg_temp.expect_error('2つ目の ACTIVE の版', $q$
    INSERT INTO reach.build (version, status, activated_at) VALUES ('check.2', 'ACTIVE', now())
$q$, '23505');
SELECT pg_temp.expect_error('使い始めた時点のない ACTIVE', $q$
    UPDATE reach.build SET activated_at = NULL WHERE status = 'ACTIVE'
$q$, '23514');
SELECT pg_temp.expect_error('版の名前に使えない文字', $q$
    INSERT INTO reach.build (version, status) VALUES ('版 1', 'BUILDING')
$q$, '23514');

-- 2. グラフ：乗車の辺は路線を持ち、乗換の辺は持たない
SELECT pg_temp.expect_error('路線のない乗車の辺', $q$
    INSERT INTO reach.edge (build_id, kind, source, target, cost, reverse_cost)
    SELECT build_id, 'RIDE', source, target, 1, 1 FROM reach.edge LIMIT 1
$q$, '23514');
SELECT pg_temp.expect_error('負の所要時間', $q$
    INSERT INTO reach.edge (build_id, kind, line_id, source, target, cost, reverse_cost)
    SELECT build_id, 'RIDE', line_id, source, target, -1, 1 FROM reach.edge WHERE kind = 'RIDE' LIMIT 1
$q$, '23514');

-- 3. 駅から場所への歩き：0分以上（徒歩の行き方と比べるため、上限の15分を超える行も持てる）、同じ版・駅・場所・到達点は1行
SELECT pg_temp.expect_error('負の歩き', $q$
    INSERT INTO reach.place_walk (build_id, station_place_id, place_id, walk_min, method)
    SELECT build_id, station_place_id, place_id, -1, 'SAMPLE' FROM reach.place_walk LIMIT 1
$q$, '23514');
SELECT pg_temp.expect_error('同じ版・駅・場所の、到達点のない2行目', $q$
    INSERT INTO reach.place_walk (build_id, station_place_id, place_id, arrival_point_id, walk_min, method)
    SELECT build_id, station_place_id, place_id, NULL, 5, 'SAMPLE' FROM reach.place_walk WHERE arrival_point_id IS NULL LIMIT 1
$q$, '23505');

SELECT pg_temp.expect('サンプルの版は、読んだ徒歩の行き方を記録している',
    EXISTS (SELECT 1 FROM reach.build_access ba JOIN reach.build b ON b.id = ba.build_id WHERE b.status = 'ACTIVE'));

-- 4. 版を消すと、その版のグラフ・駅の点・歩きも消える
DELETE FROM reach.build WHERE status = 'ACTIVE';
SELECT pg_temp.expect('版を消すとグラフ・駅の点・歩き・読んだ行き方の記録も消える',
    NOT EXISTS (SELECT 1 FROM reach.edge) AND NOT EXISTS (SELECT 1 FROM reach.node)
    AND NOT EXISTS (SELECT 1 FROM reach.station_node) AND NOT EXISTS (SELECT 1 FROM reach.place_walk)
    AND NOT EXISTS (SELECT 1 FROM reach.build_access));

ROLLBACK;
\echo 'v4_checks: すべて OK（変更は ROLLBACK した）'
