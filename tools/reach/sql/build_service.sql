-- tools/reach/sql/build_service.sql
-- 探索用の路線（運行系統）を、版の鉄道のグラフに足す（F06「データの形」9、DB V6、#170）。
-- reach_build.py が、build_rail.sql のあとに、同じ接続で流す。系統の定義（tools/reach/services/*.toml）は、reach_build.py が
-- 一時の表 svc_def・svc_def_line・svc_def_stop・svc_replace に入れておく。
-- 設定（reach_build.py が SET する）：reach.build_id、reach.transfer_minutes
--
-- 1. 系統と停車駅を版に写す。停車駅は、系統の事業者の、通る路線（lines）の駅から、駅の名前で探す（版のグラフにある駅だけ）
-- 2. 系統の点（系統 × 駅の場所に1つ）を作る。隣の停車駅の間は、通る路線の線路だけをたどって道のりを測り（正式な路線の境は
--    通り抜ける）、系統の表定速度で所要時間にした乗車の辺を足す
-- 3. 乗換：系統の点と、同じ駅の場所のほかの点（N02 の路線の点・ほかの系統の点）を結ぶ（同じ系統の中は乗換にしない）
-- 4. 置き換えてよい路線（replace_lines）だけ、系統の走る区間の N02 の乗車の辺を外す。外したことで乗れる点がなくなる駅の場所が
--    あれば失敗にする（各駅停車だけの駅へ行けなくならないように）
-- 見つからない停車駅・同じ名前で場所が2つ以上ある停車駅・線路のつながらない隣の停車駅も、失敗にする

-- ---------------------------------------------------------------------------
-- 1. 系統と停車駅を版に写す
-- ---------------------------------------------------------------------------
INSERT INTO reach.build_service (build_id, service_key, operator, name, kind, speed_kmh, is_loop)
SELECT current_setting('reach.build_id')::bigint, key, operator, name, kind, speed_kmh, is_loop FROM svc_def;

CREATE TEMP TABLE svc AS
SELECT bs.id AS service_id, bs.service_key, bs.operator, bs.speed_kmh, bs.is_loop,
       (SELECT array_agg(l.id) FROM svc_def_line dl JOIN reach.line l ON l.operator = bs.operator AND l.name = dl.line_name
         WHERE dl.key = bs.service_key) AS line_ids
  FROM reach.build_service bs
 WHERE bs.build_id = current_setting('reach.build_id')::bigint;

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(dl.key || ':' || dl.line_name, '、') AS missing INTO r
    FROM svc_def_line dl JOIN svc_def d USING (key)
   WHERE NOT EXISTS (SELECT 1 FROM reach.line l WHERE l.operator = d.operator AND l.name = dl.line_name);
  IF r.missing IS NOT NULL THEN
    RAISE EXCEPTION '系統の通る路線が N02 にない：%', r.missing;
  END IF;
END $$;

-- 停車駅の候補：系統の事業者・通る路線の駅で、版のグラフに点がある駅
CREATE TEMP TABLE svc_stop_candidate AS
SELECT ds.key, ds.seq, ds.station_name, ds.display_name, s.id AS station_id, s.place_id
  FROM svc_def_stop ds
  JOIN svc ON svc.service_key = ds.key
  JOIN reach.station s ON s.name = ds.station_name AND s.line_id = ANY (svc.line_ids)
 WHERE EXISTS (SELECT 1 FROM reach.station_node sn
                WHERE sn.station_id = s.id AND sn.build_id = current_setting('reach.build_id')::bigint);

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(ds.key || ':' || ds.station_name, '、' ORDER BY ds.key, ds.seq) AS bad INTO r
    FROM svc_def_stop ds
   WHERE NOT EXISTS (SELECT 1 FROM svc_stop_candidate c WHERE c.key = ds.key AND c.seq = ds.seq);
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '系統の停車駅が、通る路線の駅（この版のグラフ）にない：%', r.bad;
  END IF;
  SELECT string_agg(key || ':' || station_name, '、') AS bad INTO r
    FROM (SELECT key, seq, station_name FROM svc_stop_candidate GROUP BY key, seq, station_name
          HAVING count(DISTINCT place_id) > 1) x;
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '系統の停車駅の名前で、駅の場所が2つ以上ある：%', r.bad;
  END IF;
END $$;

INSERT INTO reach.build_service_stop (build_service_id, seq, station_id, display_name)
SELECT DISTINCT ON (svc.service_id, c.seq) svc.service_id, c.seq, c.station_id, c.display_name
  FROM svc_stop_candidate c JOIN svc ON svc.service_key = c.key
 ORDER BY svc.service_id, c.seq, c.station_id;

CREATE TEMP TABLE svc_stop AS
SELECT st.build_service_id AS service_id, st.seq, st.station_id, s.place_id
  FROM reach.build_service_stop st JOIN svc ON svc.service_id = st.build_service_id
  JOIN reach.station s ON s.id = st.station_id;

-- ---------------------------------------------------------------------------
-- 2. 系統の点と乗車の辺
-- ---------------------------------------------------------------------------
-- 点：系統 × 駅の場所に1つ（同じ駅が並びに2回出ても1つ）。位置は、その駅の場所の停車駅のホームの重心
CREATE TEMP TABLE svc_node AS
SELECT st.service_id, st.place_id, min(st.station_id) AS station_id,
       ST_Centroid(ST_Collect(s.geom::geometry)) AS geom
  FROM svc_stop st JOIN reach.station s ON s.place_id = st.place_id
  JOIN reach.line l ON l.id = s.line_id
  JOIN svc ON svc.service_id = st.service_id AND l.id = ANY (svc.line_ids)
 GROUP BY st.service_id, st.place_id;
ALTER TABLE svc_node ADD COLUMN node_id bigint;
WITH ins AS (
  INSERT INTO reach.node (build_id, service_id, geom)
  SELECT current_setting('reach.build_id')::bigint, service_id, ST_PointOnSurface(geom)::geography FROM svc_node
  RETURNING id, service_id, geom
)
UPDATE svc_node n SET node_id = ins.id
  FROM ins WHERE ins.service_id = n.service_id AND ST_Equals(ins.geom::geometry, ST_PointOnSurface(n.geom));
-- 系統の点も駅の点にする（API の駅の時間は、駅の場所の点から探す。版の停車駅の1つにひも付ける）
INSERT INTO reach.station_node (build_id, station_id, node_id)
SELECT current_setting('reach.build_id')::bigint, station_id, node_id FROM svc_node;

-- 隣の停車駅（環状なら最後 → 最初も）
CREATE TEMP TABLE svc_hop AS
SELECT a.service_id, a.seq, a.place_id AS from_place, b.place_id AS to_place
  FROM svc_stop a JOIN svc_stop b ON b.service_id = a.service_id AND b.seq = a.seq + 1
UNION ALL
SELECT a.service_id, a.seq, a.place_id, b.place_id
  FROM svc_stop a JOIN svc ON svc.service_id = a.service_id AND svc.is_loop
  JOIN svc_stop b ON b.service_id = a.service_id AND b.seq = 1
 WHERE a.seq = (SELECT max(seq) FROM svc_stop x WHERE x.service_id = a.service_id);

-- 道のりを測るグラフ：N02 の乗車の辺（長さ）と、同じ駅の場所の点どうし（0m。正式な路線の境を通り抜ける）
CREATE TEMP TABLE len_edge AS
SELECT e.id, e.source, e.target, e.length_m AS cost, e.length_m AS reverse_cost, e.line_id
  FROM reach.edge e
 WHERE e.build_id = current_setting('reach.build_id')::bigint AND e.kind = 'RIDE' AND e.line_id IS NOT NULL;
CREATE TEMP TABLE len_transfer AS
SELECT e.id, e.source, e.target, na.line_id AS line_a, nb.line_id AS line_b
  FROM reach.edge e JOIN reach.node na ON na.id = e.source JOIN reach.node nb ON nb.id = e.target
 WHERE e.build_id = current_setting('reach.build_id')::bigint AND e.kind = 'TRANSFER'
   AND na.line_id IS NOT NULL AND nb.line_id IS NOT NULL;

-- 駅の場所の、通る路線の点（N02）
CREATE TEMP TABLE svc_place_nodes AS
SELECT svc.service_id, s.place_id, array_agg(DISTINCT sn.node_id) AS nodes
  FROM svc JOIN reach.station s ON s.line_id = ANY (svc.line_ids)
  JOIN reach.station_node sn ON sn.station_id = s.id AND sn.build_id = current_setting('reach.build_id')::bigint
  JOIN reach.node n ON n.id = sn.node_id AND n.line_id IS NOT NULL
 WHERE s.place_id IN (SELECT place_id FROM svc_stop x WHERE x.service_id = svc.service_id)
 GROUP BY svc.service_id, s.place_id;

CREATE TEMP TABLE svc_hop_path AS
SELECT h.service_id, h.seq, h.from_place, h.to_place, p.length_m, p.edges
  FROM svc_hop h
  JOIN svc ON svc.service_id = h.service_id
  LEFT JOIN LATERAL (
    WITH d AS (
      SELECT * FROM pgr_dijkstra(
        format('SELECT id, source, target, cost, reverse_cost FROM len_edge WHERE line_id = ANY (%L::bigint[])
                UNION ALL
                SELECT -id, source, target, 0, 0 FROM len_transfer WHERE line_a = ANY (%L::bigint[]) AND line_b = ANY (%L::bigint[])',
               svc.line_ids, svc.line_ids, svc.line_ids),
        (SELECT nodes FROM svc_place_nodes x WHERE x.service_id = h.service_id AND x.place_id = h.from_place),
        (SELECT nodes FROM svc_place_nodes x WHERE x.service_id = h.service_id AND x.place_id = h.to_place),
        directed := false)),
    best AS (SELECT start_vid, end_vid, agg_cost FROM d WHERE edge = -1 ORDER BY agg_cost LIMIT 1)
    SELECT best.agg_cost AS length_m, array_agg(d.edge) FILTER (WHERE d.edge > 0) AS edges
      FROM d JOIN best USING (start_vid, end_vid) GROUP BY best.agg_cost
  ) p ON true;

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(svc.service_key || ':' || pa.name || '→' || pb.name, '、') AS bad INTO r
    FROM svc_hop_path h JOIN svc USING (service_id)
    JOIN place pa ON pa.id = h.from_place JOIN place pb ON pb.id = h.to_place
   WHERE h.length_m IS NULL;
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '系統の隣の停車駅の間が、通る路線の線路でつながらない：%', r.bad;
  END IF;
END $$;

INSERT INTO reach.edge (build_id, kind, service_id, source, target, length_m, cost, reverse_cost)
SELECT current_setting('reach.build_id')::bigint, 'RIDE', h.service_id, na.node_id, nb.node_id, h.length_m,
       h.length_m / 1000.0 / svc.speed_kmh * 60, h.length_m / 1000.0 / svc.speed_kmh * 60
  FROM svc_hop_path h JOIN svc USING (service_id)
  JOIN svc_node na ON na.service_id = h.service_id AND na.place_id = h.from_place
  JOIN svc_node nb ON nb.service_id = h.service_id AND nb.place_id = h.to_place
 WHERE na.node_id <> nb.node_id;

-- ---------------------------------------------------------------------------
-- 3. 乗換：系統の点と、同じ駅の場所のほかの点（N02 の路線の点・ほかの系統の点）
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE place_node AS
SELECT DISTINCT s.place_id, sn.node_id, n.service_id
  FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id JOIN reach.node n ON n.id = sn.node_id
 WHERE sn.build_id = current_setting('reach.build_id')::bigint;
INSERT INTO reach.edge (build_id, kind, source, target, cost, reverse_cost)
SELECT current_setting('reach.build_id')::bigint, 'TRANSFER', a.node_id, b.node_id,
       current_setting('reach.transfer_minutes')::double precision, current_setting('reach.transfer_minutes')::double precision
  FROM place_node a JOIN place_node b ON b.place_id = a.place_id AND b.node_id <> a.node_id
 WHERE a.service_id IS NOT NULL
   AND (b.service_id IS NULL OR (b.service_id <> a.service_id AND a.node_id < b.node_id));

-- ---------------------------------------------------------------------------
-- 4. 置き換えてよい路線の、系統の走る区間の N02 の乗車の辺を外す
-- ---------------------------------------------------------------------------
INSERT INTO reach.build_replaced_line (build_id, line_id)
SELECT DISTINCT current_setting('reach.build_id')::bigint, l.id
  FROM svc_replace x JOIN reach.line l ON l.operator = x.operator AND l.name = x.line_name;

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(x.operator || ':' || x.line_name, '、') AS bad INTO r
    FROM svc_replace x WHERE NOT EXISTS (SELECT 1 FROM reach.line l WHERE l.operator = x.operator AND l.name = x.line_name);
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '置き換えてよい路線が N02 にない：%', r.bad;
  END IF;
END $$;

-- 乗れる駅の場所（乗車の辺の端の点を持つ）。外す前
CREATE TEMP TABLE ridable_before AS
SELECT DISTINCT pn.place_id FROM place_node pn
 WHERE EXISTS (SELECT 1 FROM reach.edge e WHERE e.build_id = current_setting('reach.build_id')::bigint
                AND e.kind = 'RIDE' AND (e.source = pn.node_id OR e.target = pn.node_id));

DELETE FROM reach.edge e
 USING (SELECT DISTINCT unnest(edges) AS edge_id FROM svc_hop_path) c
 WHERE e.id = c.edge_id
   AND e.line_id IN (SELECT line_id FROM reach.build_replaced_line WHERE build_id = current_setting('reach.build_id')::bigint);

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(p.name, '・' ORDER BY p.name) AS bad INTO r
    FROM ridable_before b JOIN place p ON p.id = b.place_id
   WHERE NOT EXISTS (SELECT 1 FROM place_node pn JOIN reach.edge e
                       ON e.build_id = current_setting('reach.build_id')::bigint AND e.kind = 'RIDE'
                      AND (e.source = pn.node_id OR e.target = pn.node_id)
                      WHERE pn.place_id = b.place_id);
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '置き換えると乗れなくなる駅がある（その区間を走る系統を足すか、replace_lines から外す）：%', r.bad;
  END IF;
END $$;

ANALYZE reach.node;
ANALYZE reach.edge;
ANALYZE reach.station_node;
