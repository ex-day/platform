-- tools/reach/sql/build_rail.sql
-- N02（鉄道）から、駅の場所・計算の中の駅・路線と、版の鉄道のグラフを作る（F06「データの形」1・5、#168）。
-- reach_build.py が、N02 を一時の表（n02_station・n02_section。座標は JGD2011）に入れてから、同じ接続で流す。
-- 設定（reach_build.py が SET する）：reach.build_id（版）、reach.n02_year（N02 の年度）、reach.transfer_minutes（乗換の分）
-- グラフの作り方は ex-day/poc reach/sql/02_build_graph.sql と同じ（点は路線ごと、区間1つが辺1本、乗換は同じ駅の場所の路線の違う点どうし）。

-- ---------------------------------------------------------------------------
-- 路線：ないものだけ足す（表定速度は人が直すので、あるものは変えない）。誤差を「行けない」側に寄せるため遅めに置く
-- ---------------------------------------------------------------------------
INSERT INTO reach.line (operator, name, service_type, speed_kmh)
SELECT operator, line_name, service_type,
       CASE service_type
         WHEN 'shinkansen'   THEN 150
         WHEN 'conventional' THEN 40
         WHEN 'subway'       THEN 30
         WHEN 'monorail'     THEN 27
         WHEN 'agt'          THEN 25
         WHEN 'tram'         THEN 13
         WHEN 'cable'        THEN 5
         ELSE 20
       END
FROM (
  SELECT DISTINCT ON (operator, line_name)
         operator, line_name,
         CASE
           WHEN operator_type = '1' THEN 'shinkansen'
           WHEN operator IN ('東京地下鉄', '大阪市高速電気軌道', '北大阪急行電鉄') AND rail_class IN ('12', '21') THEN 'subway'
           WHEN operator_type = '3' AND rail_class = '12' THEN 'subway'
           WHEN operator = '札幌市' AND rail_class = '16' THEN 'subway'
           WHEN rail_class = '21' THEN 'tram'
           WHEN rail_class = '13' THEN 'cable'
           WHEN rail_class IN ('14', '15', '22', '23') THEN 'monorail'
           WHEN rail_class IN ('16', '24', '25') THEN 'agt'
           WHEN rail_class IN ('11', '12') THEN 'conventional'
           ELSE 'other'
         END AS service_type
  FROM n02_section
  ORDER BY operator, line_name, rail_class
) s
ON CONFLICT (operator, name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 駅：N02 の駅1行（路線ごとの駅）を、すでにある計算の中の駅に結ぶ。同じ路線・同じ駅名で 1km 以内なら同じ駅とみなす
-- （年度で駅コードが変わっても、station_id と駅の場所は変えない。名寄せの運用は #163）
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE n02_match AS
SELECT n.id AS n02_id, n.station_code, n.group_code, n.station_name, l.id AS line_id,
       ST_Transform(n.geom, 4326) AS geom4326,
       (SELECT s.id FROM reach.station s
         WHERE s.line_id = l.id AND s.name = n.station_name
           AND ST_DWithin(s.geom, ST_Transform(n.geom, 4326)::geography, 1000)
         ORDER BY ST_Distance(s.geom, ST_Transform(n.geom, 4326)::geography) LIMIT 1) AS station_id
FROM n02_station n
JOIN reach.line l ON l.operator = n.operator AND l.name = n.line_name;

-- 駅のまとまり（N02 のグループ）と、その駅の場所。すでにある駅に結べたものはその駅の場所
CREATE TEMP TABLE n02_group AS
SELECT m.group_code,
       mode() WITHIN GROUP (ORDER BY m.station_name) AS name,
       ST_Centroid(ST_Collect(m.geom4326)) AS center,
       (SELECT s.place_id FROM n02_match m2 JOIN reach.station s ON s.id = m2.station_id
         WHERE m2.group_code = m.group_code LIMIT 1) AS place_id
FROM n02_match m
GROUP BY m.group_code;

-- 駅に結べないまとまりは、同じ名前（「駅」を除く）で 500m 以内の、種類が駅の ACTIVE の場所に結ぶ
UPDATE n02_group g
SET place_id = (
  SELECT p.id FROM place p
   WHERE p.status = 'ACTIVE'
     AND EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = p.id AND k.kind = '駅')
     AND regexp_replace(p.name, '駅$', '') = g.name
     AND ST_DWithin(p.geom, g.center::geography, 500)
   ORDER BY ST_Distance(p.geom, g.center::geography) LIMIT 1)
WHERE g.place_id IS NULL;

-- それでもなければ、駅の場所を新しく作る（オープンデータから。種類は駅）
WITH created AS (
  INSERT INTO place (name, spatial_type, geom, origin, source_dataset, source_record_id, imported_at)
  SELECT g.name || '駅', 'POINT', g.center::geography, 'OPEN_DATA', 'N02',
         'N02-' || current_setting('reach.n02_year') || ':' || g.group_code, now()
    FROM n02_group g WHERE g.place_id IS NULL
  ON CONFLICT (source_dataset, source_record_id) DO NOTHING
  RETURNING id, source_record_id
)
UPDATE n02_group g SET place_id = c.id
  FROM created c
 WHERE c.source_record_id = 'N02-' || current_setting('reach.n02_year') || ':' || g.group_code;
UPDATE n02_group g SET place_id = p.id
  FROM place p
 WHERE g.place_id IS NULL AND p.source_dataset = 'N02'
   AND p.source_record_id = 'N02-' || current_setting('reach.n02_year') || ':' || g.group_code;
INSERT INTO place_kind (place_id, kind)
SELECT place_id, '駅' FROM n02_group ON CONFLICT DO NOTHING;

-- 結べなかった N02 の駅は、計算の中の駅として足す
WITH created AS (
  INSERT INTO reach.station (place_id, line_id, name, geom)
  SELECT g.place_id, m.line_id, m.station_name, m.geom4326::geography
    FROM n02_match m JOIN n02_group g USING (group_code)
   WHERE m.station_id IS NULL
  ON CONFLICT (line_id, place_id) DO NOTHING
  RETURNING id, line_id, place_id
)
UPDATE n02_match m SET station_id = c.id
  FROM created c JOIN n02_group g ON g.place_id = c.place_id
 WHERE m.station_id IS NULL AND m.line_id = c.line_id AND m.group_code = g.group_code;
UPDATE n02_match m SET station_id = s.id
  FROM n02_group g JOIN reach.station s ON s.place_id = g.place_id
 WHERE m.station_id IS NULL AND m.group_code = g.group_code AND s.line_id = m.line_id;

INSERT INTO reach.station_source (station_id, dataset_year, station_code, group_code)
SELECT DISTINCT ON (station_id) station_id, current_setting('reach.n02_year')::smallint, station_code, group_code
  FROM n02_match WHERE station_id IS NOT NULL
 ORDER BY station_id, station_code
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- グラフの点：路線×区間の端点。細かい揺れを丸めてから同じ点とみなす
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE section_end AS
SELECT s.id AS section_id, l.id AS line_id, s.geom,
       ST_SnapToGrid(ST_StartPoint(s.geom), 0.0000001) AS p_start,
       ST_SnapToGrid(ST_EndPoint(s.geom), 0.0000001) AS p_end
FROM n02_section s
JOIN reach.line l ON l.operator = s.operator AND l.name = s.line_name;

CREATE TEMP TABLE node_map AS
SELECT DISTINCT line_id, p FROM (
  SELECT line_id, p_start AS p FROM section_end
  UNION ALL SELECT line_id, p_end FROM section_end
) x;
ALTER TABLE node_map ADD COLUMN node_id bigint;
WITH ins AS (
  INSERT INTO reach.node (build_id, line_id, geom)
  SELECT current_setting('reach.build_id')::bigint, line_id, ST_Transform(p, 4326)::geography FROM node_map
  RETURNING id, line_id, geom
)
UPDATE node_map nm SET node_id = ins.id
  FROM ins WHERE ins.line_id = nm.line_id AND ST_Equals(ins.geom::geometry, ST_Transform(nm.p, 4326));
CREATE INDEX ON node_map (line_id);

-- 乗車の辺：区間1つが辺1本。所要時間 ＝ 距離 ÷ 表定速度。向きは区別しない
CREATE TEMP TABLE section_edge AS
SELECT e.section_id, e.line_id, ns.node_id AS source, nt.node_id AS target,
       ST_Length(ST_Transform(e.geom, 4326)::geography) AS length_m, e.geom
FROM section_end e
JOIN node_map ns ON ns.line_id = e.line_id AND ns.p = e.p_start
JOIN node_map nt ON nt.line_id = e.line_id AND nt.p = e.p_end;

INSERT INTO reach.edge (build_id, kind, line_id, source, target, length_m, cost, reverse_cost)
SELECT current_setting('reach.build_id')::bigint, 'RIDE', se.line_id, se.source, se.target, se.length_m,
       se.length_m / 1000.0 / l.speed_kmh * 60, se.length_m / 1000.0 / l.speed_kmh * 60
FROM section_edge se JOIN reach.line l ON l.id = se.line_id;

-- 駅のホームの点：N02 の駅の線は、同じ路線の区間の1つと同じ形をしている。その区間の両端
INSERT INTO reach.station_node (build_id, station_id, node_id)
SELECT DISTINCT current_setting('reach.build_id')::bigint, m.station_id, x.node_id
FROM n02_match m
JOIN n02_station n ON n.id = m.n02_id
JOIN section_edge se ON se.line_id = m.line_id AND ST_Equals(se.geom, n.geom)
CROSS JOIN LATERAL (VALUES (se.source), (se.target)) AS x (node_id)
WHERE m.station_id IS NOT NULL;

-- 乗換の辺：同じ駅の場所の、路線の違う駅のホームの点どうし（駅をまたいで歩く乗換はしない。PoC #5）
INSERT INTO reach.edge (build_id, kind, source, target, cost, reverse_cost)
SELECT DISTINCT ON (a.node_id, b.node_id)
       current_setting('reach.build_id')::bigint, 'TRANSFER', a.node_id, b.node_id,
       current_setting('reach.transfer_minutes')::double precision, current_setting('reach.transfer_minutes')::double precision
FROM (SELECT sn.node_id, s.place_id, s.line_id FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id
       WHERE sn.build_id = current_setting('reach.build_id')::bigint) a
JOIN (SELECT sn.node_id, s.place_id, s.line_id FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id
       WHERE sn.build_id = current_setting('reach.build_id')::bigint) b
  ON a.place_id = b.place_id AND a.line_id < b.line_id;

ANALYZE reach.node;
ANALYZE reach.edge;
ANALYZE reach.station_node;
