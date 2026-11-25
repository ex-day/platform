-- tools/reach/sql/build_walk.sql
-- OSM の歩ける道（一時の表 walk_node・walk_edge）から、版の「駅から場所への歩き」（reach.place_walk）を作る（F06「データの形」6、#168）。
-- reach_build.py が、OSM の歩ける道を一時の表に入れてから、同じ接続で流す。歩きのグラフそのものは DB に残さない。
-- 設定（reach_build.py が SET する）：reach.build_id、reach.walk_cap_minutes（片道の上限）、reach.walk_meters_per_minute、
-- reach.min_island_nodes（これより点の少ないつながりは除く）、reach.snap_meters（道の点に結ぶ距離の上限）
-- 作り方は ex-day/poc reach/osmwalk（#46）と同じ。駅の代表点はホームの重心（DEC-0014 決定1）。

-- ---------------------------------------------------------------------------
-- 小さな島を除く：ほかの道とつながっていない短い道の集まり（駅の中の通路など）。結ぶと駅や場所がどこへも歩けなくなる。
-- 閾値はメッシュ 5339 での暫定の値（poc #46）。確定は #163
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE walk_component AS
SELECT node AS node_id, component
FROM pgr_connectedComponents('SELECT id, source, target, cost, reverse_cost FROM walk_edge');
CREATE TEMP TABLE small_island AS
SELECT c.node_id FROM walk_component c
JOIN (SELECT component FROM walk_component GROUP BY component
       HAVING count(*) < current_setting('reach.min_island_nodes')::int) s USING (component);
CREATE UNIQUE INDEX ON small_island (node_id);
ANALYZE small_island;
DELETE FROM walk_edge e USING small_island i WHERE e.source = i.node_id;
DELETE FROM walk_node n USING small_island i WHERE n.node_id = i.node_id;
CREATE INDEX ON walk_node USING gist (geom);
CREATE INDEX ON walk_edge USING gist (geom);
ANALYZE walk_node;
ANALYZE walk_edge;

-- ---------------------------------------------------------------------------
-- 駅の場所：この版のグラフにある駅の、ホームの重心から、いちばん近い道の点
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE station_snap AS
SELECT g.place_id, n.node_id, n.d AS access_m
FROM (
  SELECT s.place_id, ST_Centroid(ST_Collect(s.geom::geometry)) AS p
    FROM reach.station s
   WHERE EXISTS (SELECT 1 FROM reach.station_node sn
                  WHERE sn.station_id = s.id AND sn.build_id = current_setting('reach.build_id')::bigint)
   GROUP BY s.place_id
) g
CROSS JOIN LATERAL (
  SELECT w.node_id, ST_Distance(w.geom::geography, g.p::geography) AS d
    FROM walk_node w ORDER BY w.geom <-> g.p LIMIT 1
) n
WHERE n.d <= current_setting('reach.snap_meters')::double precision;

-- ---------------------------------------------------------------------------
-- 場所の着く先：到達点（徒歩）と、場所の代表点（到達点の行は arrival_point_id、代表点は NULL）。
-- 寄り道先にならない場所（都道府県・市区町村・町丁・字・駅）は除く
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE target_snap AS
WITH target AS (
  SELECT p.id AS place_id, NULL::bigint AS arrival_point_id,
         CASE p.spatial_type WHEN 'ROUTE' THEN ST_StartPoint(ST_GeometryN(p.geom::geometry, 1))
                             ELSE ST_PointOnSurface(p.geom::geometry) END AS p
    FROM place p
   WHERE p.status = 'ACTIVE'
     AND NOT EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = p.id
                      AND k.kind IN ('都道府県', '市区町村', '町丁・字', '駅'))
  UNION ALL
  SELECT a.place_id, a.id, a.geom::geometry
    FROM place_arrival_point a
    JOIN place p ON p.id = a.place_id AND p.status = 'ACTIVE'
   WHERE a.mode = 'WALK'
     AND NOT EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = p.id
                      AND k.kind IN ('都道府県', '市区町村', '町丁・字', '駅'))
)
SELECT t.place_id, t.arrival_point_id, n.node_id, n.d AS access_m
FROM target t
CROSS JOIN LATERAL (
  SELECT w.node_id, ST_Distance(w.geom::geography, t.p::geography) AS d
    FROM walk_node w ORDER BY w.geom <-> t.p LIMIT 1
) n
WHERE n.d <= current_setting('reach.snap_meters')::double precision;
CREATE INDEX ON target_snap (node_id);

-- ---------------------------------------------------------------------------
-- 駅ごとに、片道の上限までの歩きをたどる（道に結べない到達点は外す。直線で代わりに測らない）
-- ---------------------------------------------------------------------------
INSERT INTO reach.place_walk (build_id, station_place_id, place_id, arrival_point_id, walk_min, method)
SELECT current_setting('reach.build_id')::bigint, s.place_id, t.place_id, t.arrival_point_id,
       min(d.agg_cost + s.access_m / current_setting('reach.walk_meters_per_minute')::double precision
                      + t.access_m / current_setting('reach.walk_meters_per_minute')::double precision),
       'OSM_WALK'
FROM station_snap s
CROSS JOIN LATERAL pgr_drivingDistance(
  format('SELECT id, source, target, cost, reverse_cost FROM walk_edge WHERE geom && ST_Expand(%L::geometry, %s)',
         (SELECT w.geom FROM walk_node w WHERE w.node_id = s.node_id),
         current_setting('reach.walk_cap_minutes')::double precision
           * current_setting('reach.walk_meters_per_minute')::double precision / 80000.0),
  s.node_id,
  greatest(current_setting('reach.walk_cap_minutes')::double precision
           - s.access_m / current_setting('reach.walk_meters_per_minute')::double precision, 0),
  directed := false) d
JOIN target_snap t ON t.node_id = d.node
WHERE t.place_id <> s.place_id
GROUP BY s.place_id, t.place_id, t.arrival_point_id
HAVING min(d.agg_cost + s.access_m / current_setting('reach.walk_meters_per_minute')::double precision
                      + t.access_m / current_setting('reach.walk_meters_per_minute')::double precision)
       <= current_setting('reach.walk_cap_minutes')::double precision;

ANALYZE reach.place_walk;
