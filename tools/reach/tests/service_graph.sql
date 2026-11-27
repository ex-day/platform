-- tools/reach/tests/service_graph.sql
-- build_service.sql（探索用の路線）を確かめるための、小さな N02 の形のグラフ（PR #173 のレビュー）。
-- apps/api の SampleDataIntegrationTest が、このファイル → 場合ごとの定義（service_case_*.sql）→ build_service.sql →
-- 確かめ（service_case_*_assert.sql）をつないで、1つのトランザクションで流す（最後に ROLLBACK）。
--
-- 駅 T1〜T7（東西に約1km ごと）。路線（事業者「テスト鉄道」）：
--   A線 T1–T2–T3（1km ずつ）、C線 T1–T5–T3（1.5km ずつ。A線の別の道）、B線 T3–T4（1km）、D線 T4–T1（3km。環状をつくる）、
--   F線 T2–T7（1km。T2 の別の路線）、G線 T1–T2–T3（0.9km ずつ。A線と並ぶ別の路線で、A線より短い）、
--   N線 T6–Q1 と Q2–T8（500m ずつ。Q1 と Q2 は約 9m 離れた駅でない地点で、N02 の線路の切れ目のように、同じ路線の中でつながっていない）、
--   J線 T5–P と L線 P–T7（1km ずつ。P は T7 から約 370m の駅でない地点で、J線と L線の点が同じ位置にある。つなぎ目として書いたときだけ通れる）
-- 事業者「テスト地下鉄」：M線 T4–T6（1km。事業者をまたぐ直通）
-- 表定速度はどれも 40km/h（1km ＝ 1.5分）。同じ駅の場所の路線の違う点どうしは乗換10分（build_rail.sql と同じ）
BEGIN;

INSERT INTO reach.build (version, status) VALUES ('test.service', 'BUILDING');
SELECT set_config('reach.build_id', (SELECT id FROM reach.build WHERE version = 'test.service')::text, true);
SELECT set_config('reach.transfer_minutes', '10', true);

INSERT INTO reach.line (operator, name, service_type, speed_kmh) VALUES
  ('テスト鉄道', 'A線', 'conventional', 40), ('テスト鉄道', 'B線', 'conventional', 40),
  ('テスト鉄道', 'C線', 'conventional', 40), ('テスト鉄道', 'D線', 'conventional', 40),
  ('テスト鉄道', 'F線', 'conventional', 40), ('テスト鉄道', 'G線', 'conventional', 40),
  ('テスト鉄道', 'J線', 'conventional', 40), ('テスト鉄道', 'L線', 'conventional', 40),
  ('テスト鉄道', 'N線', 'conventional', 40),
  ('テスト地下鉄', 'M線', 'subway', 40);

CREATE TEMP TABLE t_station (name text PRIMARY KEY, x double precision, y double precision);
INSERT INTO t_station VALUES ('T1', 139.00, 35.00), ('T2', 139.01, 35.00), ('T3', 139.02, 35.00), ('T4', 139.03, 35.00),
                             ('T5', 139.01, 35.01), ('T6', 139.04, 35.00), ('T7', 139.01, 34.99),
                             ('T8', 139.05, 35.00);
INSERT INTO place (name, spatial_type, geom, origin)
SELECT name || '駅', 'POINT', ST_SetSRID(ST_MakePoint(x, y), 4326)::geography, 'OPERATOR' FROM t_station;
INSERT INTO place_kind (place_id, kind) SELECT p.id, '駅' FROM place p JOIN t_station t ON p.name = t.name || '駅';

-- 路線ごとの駅の並び
CREATE TEMP TABLE t_line_station (operator text, line text, seq int, station text);
INSERT INTO t_line_station VALUES
  ('テスト鉄道', 'A線', 1, 'T1'), ('テスト鉄道', 'A線', 2, 'T2'), ('テスト鉄道', 'A線', 3, 'T3'),
  ('テスト鉄道', 'C線', 1, 'T1'), ('テスト鉄道', 'C線', 2, 'T5'), ('テスト鉄道', 'C線', 3, 'T3'),
  ('テスト鉄道', 'B線', 1, 'T3'), ('テスト鉄道', 'B線', 2, 'T4'),
  ('テスト鉄道', 'D線', 1, 'T4'), ('テスト鉄道', 'D線', 2, 'T1'),
  ('テスト鉄道', 'F線', 1, 'T2'), ('テスト鉄道', 'F線', 2, 'T7'),
  ('テスト鉄道', 'G線', 1, 'T1'), ('テスト鉄道', 'G線', 2, 'T2'), ('テスト鉄道', 'G線', 3, 'T3'),
  ('テスト鉄道', 'J線', 1, 'T5'), ('テスト鉄道', 'L線', 1, 'T7'), ('テスト鉄道', 'N線', 1, 'T6'), ('テスト鉄道', 'N線', 2, 'T8'),
  ('テスト地下鉄', 'M線', 1, 'T4'), ('テスト地下鉄', 'M線', 2, 'T6');
INSERT INTO reach.station (place_id, line_id, name, geom)
SELECT p.id, l.id, ls.station, p.geom
  FROM t_line_station ls JOIN reach.line l ON l.operator = ls.operator AND l.name = ls.line
  JOIN place p ON p.name = ls.station || '駅';

-- 点：路線 × 駅に1つ。駅の点にもする
INSERT INTO reach.node (build_id, line_id, geom)
SELECT current_setting('reach.build_id')::bigint, s.line_id, s.geom FROM reach.station s JOIN place p ON p.id = s.place_id
 WHERE p.name LIKE 'T_駅';
INSERT INTO reach.station_node (build_id, station_id, node_id)
SELECT n.build_id, s.id, n.id FROM reach.station s JOIN reach.node n ON n.line_id = s.line_id AND ST_Equals(n.geom::geometry, s.geom::geometry)
 WHERE n.build_id = current_setting('reach.build_id')::bigint;

-- 駅でない地点 P の、J線と L線の点（同じ位置）
INSERT INTO reach.node (build_id, line_id, geom)
SELECT current_setting('reach.build_id')::bigint, l.id, ST_SetSRID(ST_MakePoint(139.014, 34.99), 4326)::geography
  FROM reach.line l WHERE l.name IN ('J線', 'L線');
INSERT INTO reach.edge (build_id, kind, line_id, source, target, length_m, cost, reverse_cost)
SELECT current_setting('reach.build_id')::bigint, 'RIDE', l.id, sn.node_id, p.id, 1000, 1.5, 1.5
  FROM reach.line l
  JOIN reach.station s ON s.line_id = l.id JOIN reach.station_node sn ON sn.station_id = s.id
  JOIN reach.node p ON p.line_id = l.id AND ST_Equals(p.geom::geometry, ST_SetSRID(ST_MakePoint(139.014, 34.99), 4326))
 WHERE l.name IN ('J線', 'L線');

-- N線の線路の切れ目：T6–Q1 と Q2–T8。Q1 と Q2 は同じ路線の点で、約 9m 離れている
INSERT INTO reach.node (build_id, line_id, geom)
SELECT current_setting('reach.build_id')::bigint, l.id, ST_SetSRID(ST_MakePoint(x, 35.00), 4326)::geography
  FROM reach.line l, (VALUES (139.045), (139.0451)) v(x) WHERE l.name = 'N線';
INSERT INTO reach.edge (build_id, kind, line_id, source, target, length_m, cost, reverse_cost)
SELECT current_setting('reach.build_id')::bigint, 'RIDE', l.id, sn.node_id, q.id, 500, 0.75, 0.75
  FROM reach.line l
  JOIN reach.station s ON s.line_id = l.id JOIN reach.station_node sn ON sn.station_id = s.id
  JOIN (VALUES ('T6', 139.045), ('T8', 139.0451)) v(st, x) ON v.st = s.name
  JOIN reach.node q ON q.line_id = l.id AND ST_Equals(q.geom::geometry, ST_SetSRID(ST_MakePoint(v.x, 35.00), 4326))
 WHERE l.name = 'N線';

-- 乗車の辺：路線の隣の駅どうし。長さは指定の値（km）
CREATE TEMP TABLE t_len (line text, a text, b text, km double precision);
INSERT INTO t_len VALUES ('A線', 'T1', 'T2', 1), ('A線', 'T2', 'T3', 1), ('C線', 'T1', 'T5', 1.5), ('C線', 'T5', 'T3', 1.5),
                         ('B線', 'T3', 'T4', 1), ('D線', 'T4', 'T1', 3), ('F線', 'T2', 'T7', 1), ('M線', 'T4', 'T6', 1),
                         ('G線', 'T1', 'T2', 0.9), ('G線', 'T2', 'T3', 0.9);
INSERT INTO reach.edge (build_id, kind, line_id, source, target, length_m, cost, reverse_cost)
SELECT current_setting('reach.build_id')::bigint, 'RIDE', l.id, na.node_id, nb.node_id, t.km * 1000, t.km * 1.5, t.km * 1.5
  FROM t_len t JOIN reach.line l ON l.name = t.line
  JOIN (SELECT sn.node_id, s.line_id, s.name FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id) na
    ON na.line_id = l.id AND na.name = t.a
  JOIN (SELECT sn.node_id, s.line_id, s.name FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id) nb
    ON nb.line_id = l.id AND nb.name = t.b;

-- 乗換：同じ駅の場所の、路線の違う点どうし
INSERT INTO reach.edge (build_id, kind, source, target, cost, reverse_cost)
SELECT current_setting('reach.build_id')::bigint, 'TRANSFER', a.node_id, b.node_id, 10, 10
  FROM (SELECT sn.node_id, s.place_id, s.line_id FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id
         WHERE sn.build_id = current_setting('reach.build_id')::bigint) a
  JOIN (SELECT sn.node_id, s.place_id, s.line_id FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id
         WHERE sn.build_id = current_setting('reach.build_id')::bigint) b
    ON a.place_id = b.place_id AND a.line_id < b.line_id;

-- build_service.sql が読む一時の表（reach_build.py の load_services と同じ形）
CREATE TEMP TABLE svc_def (key text PRIMARY KEY, operator text NOT NULL, name text NOT NULL, kind text NOT NULL,
                           speed_kmh double precision NOT NULL, is_loop boolean NOT NULL,
                           name_by_line boolean NOT NULL DEFAULT false);
CREATE TEMP TABLE svc_def_line (key text NOT NULL, operator text NOT NULL, line_name text NOT NULL);
CREATE TEMP TABLE svc_def_point (key text NOT NULL, seq integer NOT NULL, station_name text NOT NULL,
                                 is_stop boolean NOT NULL, display_name text);
CREATE TEMP TABLE svc_replace (operator text NOT NULL, line_name text NOT NULL);
CREATE TEMP TABLE svc_junction (ord integer PRIMARY KEY, operator_a text NOT NULL, line_a text NOT NULL,
                                operator_b text NOT NULL, line_b text NOT NULL, near text NOT NULL,
                                gap_m double precision NOT NULL DEFAULT 1);

-- 2つの駅の場所の間の、いちばん短い時間と乗換の回数（確かめに使う）
CREATE FUNCTION pg_temp.t_route(a text, z text, OUT minutes double precision, OUT transfers bigint) LANGUAGE sql AS $$
  WITH nodes AS (
    SELECT p.name, array_agg(DISTINCT sn.node_id) AS ids
      FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id JOIN place p ON p.id = s.place_id
     WHERE sn.build_id = current_setting('reach.build_id')::bigint AND p.name IN (a || '駅', z || '駅')
     GROUP BY p.name),
  d AS (SELECT * FROM pgr_dijkstra(
          format('SELECT id, source, target, cost, reverse_cost FROM reach.edge WHERE build_id = %s', current_setting('reach.build_id')),
          (SELECT ids FROM nodes WHERE name = a || '駅'), (SELECT ids FROM nodes WHERE name = z || '駅'), directed := false)),
  best AS (SELECT start_vid, end_vid FROM d WHERE edge = -1 ORDER BY agg_cost LIMIT 1)
  SELECT max(d.agg_cost), count(*) FILTER (WHERE e.kind = 'TRANSFER')
    FROM d JOIN best USING (start_vid, end_vid) LEFT JOIN reach.edge e ON e.id = d.edge
$$;

CREATE FUNCTION pg_temp.t_expect(label text, ok boolean) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF ok IS NOT TRUE THEN RAISE EXCEPTION 'NG: %', label; END IF;
    RAISE NOTICE 'OK: %', label;
END $$;
