-- ex-day：DB V6 の確認（探索用の路線（運行系統）。F06「データの形」9、#170）
--
-- サンプルデータ（db/sample/R__sample_data.sql）を入れた DB で流す。全体を1つのトランザクションで行い、
-- 最後に ROLLBACK するので、データは変わらない。失敗したところで止まる（ERROR: NG: ...）。
--
--   docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v6_checks.sql

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

CREATE FUNCTION pg_temp.svc() RETURNS bigint LANGUAGE sql AS $$
    SELECT id FROM reach.build_service WHERE service_key = 'jr-keihin-tohoku'
$$;

-- 23514 check_violation、23505 unique_violation

-- 1. サンプルの系統：京浜東北線は4駅に止まり、隣の駅の間に乗車の辺が3本。系統の点は駅の点でもある
SELECT pg_temp.expect('サンプルの京浜東北線は停車駅4・乗車の辺3・点4',
    (SELECT count(*) FROM reach.build_service_stop WHERE build_service_id = pg_temp.svc()) = 4
    AND (SELECT count(*) FROM reach.edge WHERE service_id = pg_temp.svc() AND kind = 'RIDE') = 3
    AND (SELECT count(*) FROM reach.node n JOIN reach.station_node sn ON sn.node_id = n.id WHERE n.service_id = pg_temp.svc()) = 4);

-- 2. 点・辺は N02 の路線か系統のどちらか1つに属する。乗換の辺はどちらにも属さない
SELECT pg_temp.expect_error('路線も系統も持たない点', $q$
    INSERT INTO reach.node (build_id, geom) SELECT build_id, geom FROM reach.node LIMIT 1
$q$, '23514');
SELECT pg_temp.expect_error('路線と系統の両方を持つ点', $q$
    INSERT INTO reach.node (build_id, line_id, service_id, geom)
    SELECT build_id, (SELECT id FROM reach.line LIMIT 1), pg_temp.svc(), geom FROM reach.node LIMIT 1
$q$, '23514');
SELECT pg_temp.expect_error('系統を持つ乗換の辺', $q$
    INSERT INTO reach.edge (build_id, kind, service_id, source, target, cost, reverse_cost)
    SELECT build_id, 'TRANSFER', pg_temp.svc(), source, target, 10, 10 FROM reach.edge WHERE kind = 'TRANSFER' LIMIT 1
$q$, '23514');
SELECT pg_temp.expect_error('路線も系統も持たない乗車の辺', $q$
    INSERT INTO reach.edge (build_id, kind, source, target, cost, reverse_cost)
    SELECT build_id, 'RIDE', source, target, 1, 1 FROM reach.edge WHERE kind = 'RIDE' LIMIT 1
$q$, '23514');

-- 3. 系統の決まり：同じ版で同じ識別子は1つ、識別子は英小文字・数字・ハイフン、速さは正。停車の順は系統の中で1つ
SELECT pg_temp.expect_error('同じ版の同じ識別子の系統', $q$
    INSERT INTO reach.build_service (build_id, service_key, operator, name, kind, speed_kmh)
    SELECT build_id, service_key, operator, name, kind, speed_kmh FROM reach.build_service WHERE id = pg_temp.svc()
$q$, '23505');
SELECT pg_temp.expect_error('大文字の識別子', $q$
    INSERT INTO reach.build_service (build_id, service_key, operator, name, kind, speed_kmh)
    SELECT build_id, 'JR', operator, name, kind, speed_kmh FROM reach.build_service WHERE id = pg_temp.svc()
$q$, '23514');
SELECT pg_temp.expect_error('速さが0の系統', $q$
    INSERT INTO reach.build_service (build_id, service_key, operator, name, kind, speed_kmh)
    SELECT build_id, 'zero', operator, name, kind, 0 FROM reach.build_service WHERE id = pg_temp.svc()
$q$, '23514');
SELECT pg_temp.expect_error('同じ停車の順', $q$
    INSERT INTO reach.build_service_stop (build_service_id, seq, station_id)
    SELECT build_service_id, seq, station_id FROM reach.build_service_stop WHERE build_service_id = pg_temp.svc() LIMIT 1
$q$, '23505');
-- 同じ駅が並びに2回出るのはよい（一意にするのは順番だけ）
INSERT INTO reach.build_service_stop (build_service_id, seq, station_id)
SELECT build_service_id, 99, station_id FROM reach.build_service_stop WHERE build_service_id = pg_temp.svc() AND seq = 1;
SELECT pg_temp.expect('同じ駅を並びに2回置ける',
    (SELECT count(*) FROM reach.build_service_stop WHERE build_service_id = pg_temp.svc()) = 5);

-- 4. 置き換えた路線は版ごと。版を消すと、系統・停車駅・系統の点と辺・置き換えた路線も消える
INSERT INTO reach.build_replaced_line (build_id, line_id)
SELECT b.id, l.id FROM reach.build b, reach.line l WHERE b.version = 'sample.1' AND l.name = '根岸線';
SELECT pg_temp.expect_error('同じ版の同じ置き換えた路線', $q$
    INSERT INTO reach.build_replaced_line (build_id, line_id)
    SELECT b.id, l.id FROM reach.build b, reach.line l WHERE b.version = 'sample.1' AND l.name = '根岸線'
$q$, '23505');
DELETE FROM reach.build WHERE version = 'sample.1';
SELECT pg_temp.expect('版を消すと系統・停車駅・点・辺・置き換えた路線も消える',
    NOT EXISTS (SELECT 1 FROM reach.build_service) AND NOT EXISTS (SELECT 1 FROM reach.build_service_stop)
    AND NOT EXISTS (SELECT 1 FROM reach.node WHERE service_id IS NOT NULL)
    AND NOT EXISTS (SELECT 1 FROM reach.edge WHERE service_id IS NOT NULL)
    AND NOT EXISTS (SELECT 1 FROM reach.build_replaced_line));

ROLLBACK;
\echo 'v6_checks: すべて OK（変更は ROLLBACK した）'
