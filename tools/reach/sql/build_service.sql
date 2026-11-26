-- tools/reach/sql/build_service.sql
-- 探索用の路線（運行系統）を、版の鉄道のグラフに足す（F06「データの形」9、DB V6、#170）。
-- reach_build.py が、build_rail.sql のあとに、同じ接続で流す。系統の定義（tools/reach/services/*.toml）は、reach_build.py が
-- 一時の表 svc_def・svc_def_line・svc_def_point・svc_replace に入れておく（apps/api の BuildServiceSqlTest も同じ形で入れて流す）。
-- svc_def_point は停車駅と通過地点の並び（is_stop）。通過地点は経路を決めるだけで、乗り降り・乗換はできない（PR #173 のレビュー）
-- 設定（reach_build.py が SET する）：reach.build_id、reach.transfer_minutes
--
-- 1. 系統と停車駅を版に写す。停車駅・通過地点は、通る路線（lines。事業者をまたいでよい）の駅から、駅の名前で探す
--    （版のグラフにある駅だけ）
-- 2. 系統の点（系統 × 駅の場所に1つ。停車駅だけ）を作る。隣の停車駅の間は、間の通過地点を順にたどり、通る路線の線路だけを
--    たどって道のりを測り（正式な路線の境は通り抜ける）、系統の表定速度で所要時間にした乗車の辺を足す
-- 3. 乗換：系統の点と、同じ駅の場所のほかの点（N02 の路線の点・ほかの系統の点）を結ぶ（同じ系統の中は乗換にしない）
-- 4. 置き換えてよい路線（replace_lines）だけ、系統の走る区間の N02 の乗車の辺を外す。外した区間ごとに、その区間の駅が、
--    残ったその路線の辺と、その路線を通る系統（と同じ駅の乗換）で、互いに行き来できるかを確かめ、できなければ失敗にする
--    （各駅停車だけの駅へ行けなくならないように。別の路線だけが残って、その区間への接続を失うことも見つける。PR #173 のレビュー）
-- 見つからない停車駅・同じ名前で場所が2つ以上ある停車駅・線路のつながらない隣の停車駅も、失敗にする

-- ---------------------------------------------------------------------------
-- 1. 系統と停車駅を版に写す
-- ---------------------------------------------------------------------------
INSERT INTO reach.build_service (build_id, service_key, operator, name, kind, speed_kmh, is_loop)
SELECT current_setting('reach.build_id')::bigint, key, operator, name, kind, speed_kmh, is_loop FROM svc_def;

CREATE TEMP TABLE svc AS
SELECT bs.id AS service_id, bs.service_key, bs.operator, bs.speed_kmh, bs.is_loop,
       (SELECT array_agg(l.id) FROM svc_def_line dl JOIN reach.line l ON l.operator = dl.operator AND l.name = dl.line_name
         WHERE dl.key = bs.service_key) AS line_ids
  FROM reach.build_service bs
 WHERE bs.build_id = current_setting('reach.build_id')::bigint;

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(dl.key || ':' || dl.operator || ' ' || dl.line_name, '、') AS missing INTO r
    FROM svc_def_line dl
   WHERE NOT EXISTS (SELECT 1 FROM reach.line l WHERE l.operator = dl.operator AND l.name = dl.line_name);
  IF r.missing IS NOT NULL THEN
    RAISE EXCEPTION '系統の通る路線が N02 にない：%', r.missing;
  END IF;
END $$;

-- 停車駅・通過地点の候補：通る路線の駅で、版のグラフに点がある駅
CREATE TEMP TABLE svc_point_candidate AS
SELECT dp.key, dp.seq, dp.station_name, dp.is_stop, dp.display_name, s.id AS station_id, s.place_id
  FROM svc_def_point dp
  JOIN svc ON svc.service_key = dp.key
  JOIN reach.station s ON s.name = dp.station_name AND s.line_id = ANY (svc.line_ids)
 WHERE EXISTS (SELECT 1 FROM reach.station_node sn
                WHERE sn.station_id = s.id AND sn.build_id = current_setting('reach.build_id')::bigint);

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(dp.key || ':' || dp.station_name, '、' ORDER BY dp.key, dp.seq) AS bad INTO r
    FROM svc_def_point dp
   WHERE NOT EXISTS (SELECT 1 FROM svc_point_candidate c WHERE c.key = dp.key AND c.seq = dp.seq);
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '系統の停車駅・通過地点が、通る路線の駅（この版のグラフ）にない：%', r.bad;
  END IF;
  SELECT string_agg(key || ':' || station_name, '、') AS bad INTO r
    FROM (SELECT key, seq, station_name FROM svc_point_candidate GROUP BY key, seq, station_name
          HAVING count(DISTINCT place_id) > 1) x;
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '系統の停車駅・通過地点の名前で、駅の場所が2つ以上ある：%', r.bad;
  END IF;
END $$;

-- 並び（停車駅と通過地点）。stop_no はその点までの停車駅の数（通過地点は直前の停車駅の番号）
CREATE TEMP TABLE svc_point AS
SELECT DISTINCT ON (svc.service_id, c.seq) svc.service_id, c.seq, c.station_id, c.place_id, c.is_stop, c.display_name
  FROM svc_point_candidate c JOIN svc ON svc.service_key = c.key
 ORDER BY svc.service_id, c.seq, c.station_id;
ALTER TABLE svc_point ADD COLUMN stop_no integer;
UPDATE svc_point p SET stop_no = x.n
  FROM (SELECT service_id, seq, count(*) FILTER (WHERE is_stop) OVER (PARTITION BY service_id ORDER BY seq) AS n FROM svc_point) x
 WHERE x.service_id = p.service_id AND x.seq = p.seq;

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(svc.service_key, '、') AS bad INTO r
    FROM svc WHERE (SELECT count(*) FROM svc_point p WHERE p.service_id = svc.service_id AND p.is_stop) < 2
                OR NOT (SELECT is_stop FROM svc_point p WHERE p.service_id = svc.service_id ORDER BY seq LIMIT 1)
                OR (NOT svc.is_loop AND NOT (SELECT is_stop FROM svc_point p WHERE p.service_id = svc.service_id ORDER BY seq DESC LIMIT 1));
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '系統の停車駅が2つ未満、または並びが停車駅で始まらない・（環状でないのに）停車駅で終わらない：%', r.bad;
  END IF;
END $$;

INSERT INTO reach.build_service_stop (build_service_id, seq, station_id, display_name)
SELECT service_id, stop_no, station_id, display_name FROM svc_point WHERE is_stop;

CREATE TEMP TABLE svc_stop AS
SELECT service_id, stop_no AS seq, station_id, place_id FROM svc_point WHERE is_stop;

-- ---------------------------------------------------------------------------
-- 2. 系統の点と乗車の辺
-- ---------------------------------------------------------------------------
-- 点：系統 × 停車駅の場所に1つ（同じ駅が並びに2回出ても1つ。通過地点には作らない）。位置は、その駅の場所の駅のホームの重心
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
 WHERE s.place_id IN (SELECT place_id FROM svc_point x WHERE x.service_id = svc.service_id)
 GROUP BY svc.service_id, s.place_id;

-- 区間：並びの隣の点どうし（環状なら最後 → 最初も）。どの停車駅の間（hop）に入るかは、区間の始まりの点の stop_no
CREATE TEMP TABLE svc_segment AS
SELECT a.service_id, a.stop_no AS hop, a.seq, a.place_id AS from_place, b.place_id AS to_place
  FROM svc_point a JOIN svc_point b ON b.service_id = a.service_id
       AND b.seq = (SELECT min(seq) FROM svc_point x WHERE x.service_id = a.service_id AND x.seq > a.seq)
UNION ALL
SELECT a.service_id, a.stop_no, a.seq, a.place_id, b.place_id
  FROM svc_point a JOIN svc ON svc.service_id = a.service_id AND svc.is_loop
  JOIN svc_point b ON b.service_id = a.service_id AND b.seq = (SELECT min(seq) FROM svc_point x WHERE x.service_id = a.service_id)
 WHERE a.seq = (SELECT max(seq) FROM svc_point x WHERE x.service_id = a.service_id);

CREATE TEMP TABLE svc_segment_path AS
SELECT g.service_id, g.hop, g.seq, g.from_place, g.to_place, p.length_m, p.edges
  FROM svc_segment g
  JOIN svc ON svc.service_id = g.service_id
  LEFT JOIN LATERAL (
    WITH d AS (
      SELECT * FROM pgr_dijkstra(
        format('SELECT id, source, target, cost, reverse_cost FROM len_edge WHERE line_id = ANY (%L::bigint[])
                UNION ALL
                SELECT -id, source, target, 0, 0 FROM len_transfer WHERE line_a = ANY (%L::bigint[]) AND line_b = ANY (%L::bigint[])',
               svc.line_ids, svc.line_ids, svc.line_ids),
        (SELECT nodes FROM svc_place_nodes x WHERE x.service_id = g.service_id AND x.place_id = g.from_place),
        (SELECT nodes FROM svc_place_nodes x WHERE x.service_id = g.service_id AND x.place_id = g.to_place),
        directed := false)),
    best AS (SELECT start_vid, end_vid, agg_cost FROM d WHERE edge = -1 ORDER BY agg_cost LIMIT 1)
    SELECT best.agg_cost AS length_m, array_agg(d.edge) FILTER (WHERE d.edge > 0) AS edges
      FROM d JOIN best USING (start_vid, end_vid) GROUP BY best.agg_cost
  ) p ON true;

-- 隣の停車駅の間（hop）：区間の道のりの合計。始まりは hop の停車駅、終わりは次の停車駅（環状の最後は最初の停車駅）
CREATE TEMP TABLE svc_hop_path AS
SELECT g.service_id, g.hop AS seq,
       (SELECT place_id FROM svc_stop s WHERE s.service_id = g.service_id AND s.seq = g.hop) AS from_place,
       coalesce((SELECT place_id FROM svc_stop s WHERE s.service_id = g.service_id AND s.seq = g.hop + 1),
                (SELECT place_id FROM svc_stop s WHERE s.service_id = g.service_id AND s.seq = 1)) AS to_place,
       CASE WHEN bool_and(g.length_m IS NOT NULL) THEN sum(g.length_m) END AS length_m,
       array_agg(e ORDER BY e) FILTER (WHERE e IS NOT NULL) AS edges
  FROM svc_segment_path g LEFT JOIN LATERAL unnest(g.edges) AS e ON true
 GROUP BY g.service_id, g.hop;

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(svc.service_key || ':' || pa.name || '→' || pb.name, '、') AS bad INTO r
    FROM svc_segment_path g JOIN svc USING (service_id)
    JOIN place pa ON pa.id = g.from_place JOIN place pb ON pb.id = g.to_place
   WHERE g.length_m IS NULL;
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '系統の隣の停車駅・通過地点の間が、通る路線の線路でつながらない：%', r.bad;
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

-- 外す辺：置き換えてよい路線の、系統の走る区間（どれかの系統の隣の停車駅の間の道に入る辺）
CREATE TEMP TABLE removed_edge AS
SELECT e.id, e.line_id, e.source, e.target
  FROM reach.edge e
  JOIN (SELECT DISTINCT unnest(edges) AS edge_id FROM svc_hop_path) c ON c.edge_id = e.id
 WHERE e.line_id IN (SELECT line_id FROM reach.build_replaced_line WHERE build_id = current_setting('reach.build_id')::bigint);
DELETE FROM reach.edge e USING removed_edge r WHERE e.id = r.id;

-- 外したあとに残った置き換えた路線の辺のうち、つながった辺のまとまりが駅の場所を1つしか通らないもの（停車駅のホームの区間など。
-- 隣の停車駅の間の道は近いほうのホームの端から測るので、端の駅のホームの区間は道に入らないことがある）も外す。
-- 残すと、ほかの駅へ行けない辺のために、駅の候補に置き換えた路線の名前が出る
CREATE TEMP TABLE replaced_rest AS
SELECT e.id, e.line_id, e.source, e.target
  FROM reach.edge e
 WHERE e.build_id = current_setting('reach.build_id')::bigint AND e.kind = 'RIDE'
   AND e.line_id IN (SELECT line_id FROM reach.build_replaced_line WHERE build_id = current_setting('reach.build_id')::bigint);
CREATE TEMP TABLE replaced_rest_component AS
SELECT node, component FROM pgr_connectedComponents('SELECT id, source, target, 1 AS cost, 1 AS reverse_cost FROM replaced_rest');
INSERT INTO removed_edge (id, line_id, source, target)
SELECT r.id, r.line_id, r.source, r.target
  FROM replaced_rest r JOIN replaced_rest_component c ON c.node = r.source
 WHERE (SELECT count(DISTINCT pn.place_id) FROM replaced_rest_component c2 JOIN place_node pn ON pn.node_id = c2.node
         WHERE c2.component = c.component) <= 1;
DELETE FROM reach.edge e USING removed_edge r WHERE e.id = r.id;

-- 外した区間（外した辺のつながったまとまり）ごとに、その区間の駅の場所が、互いに行き来できるかを確かめる。
-- 使ってよいのは、その路線の残った乗車の辺・その路線を通る系統の乗車の辺と、それらの点どうしの同じ駅の乗換だけ
-- （ほかの路線だけで行き来できても、その区間の駅への接続は失われている。PR #173 のレビュー）
CREATE TEMP TABLE replace_problem (line_id bigint, places text);
DO $$
DECLARE
  b bigint := current_setting('reach.build_id')::bigint;
  l record;
BEGIN
  FOR l IN SELECT DISTINCT line_id FROM removed_edge LOOP
    DROP TABLE IF EXISTS pg_temp.keep_ride, pg_temp.keep_node, pg_temp.keep_edge, pg_temp.keep_component,
                         pg_temp.removed_component, pg_temp.section_place;
    CREATE TEMP TABLE keep_ride AS
    SELECT e.id, e.source, e.target FROM reach.edge e
     WHERE e.build_id = b AND e.kind = 'RIDE'
       AND (e.line_id = l.line_id OR e.service_id IN (SELECT service_id FROM svc WHERE l.line_id = ANY (line_ids)));
    -- 点：残った辺の端と、この路線の駅の点
    CREATE TEMP TABLE keep_node AS
    SELECT source AS node_id FROM keep_ride UNION SELECT target FROM keep_ride
    UNION
    SELECT sn.node_id FROM reach.station_node sn JOIN reach.node n ON n.id = sn.node_id AND n.line_id = l.line_id
     WHERE sn.build_id = b;
    CREATE TEMP TABLE keep_edge AS
    SELECT id, source, target FROM keep_ride
    UNION ALL
    SELECT e.id, e.source, e.target FROM reach.edge e
     WHERE e.build_id = b AND e.kind = 'TRANSFER'
       AND e.source IN (SELECT node_id FROM keep_node) AND e.target IN (SELECT node_id FROM keep_node);
    CREATE TEMP TABLE keep_component AS
    SELECT node, component FROM pgr_connectedComponents('SELECT id, source, target, 1 AS cost, 1 AS reverse_cost FROM keep_edge');
    CREATE TEMP TABLE removed_component AS
    SELECT node, component FROM pgr_connectedComponents(
      format('SELECT id, source, target, 1 AS cost, 1 AS reverse_cost FROM removed_edge WHERE line_id = %s', l.line_id));
    -- 外した区間ごとの駅の場所
    CREATE TEMP TABLE section_place AS
    SELECT DISTINCT rc.component AS section, pn.place_id
      FROM removed_component rc JOIN place_node pn ON pn.node_id = rc.node;
    -- 区間の駅の場所を全部含むつながり（keep_component）が無い区間を記録する
    INSERT INTO replace_problem (line_id, places)
    SELECT l.line_id, string_agg(DISTINCT p.name, '・')
      FROM section_place sp JOIN place p ON p.id = sp.place_id
     WHERE NOT EXISTS (
         SELECT 1 FROM keep_component kc
          WHERE (SELECT count(DISTINCT sp2.place_id) FROM section_place sp2
                  WHERE sp2.section = sp.section
                    AND EXISTS (SELECT 1 FROM keep_component kc2 JOIN place_node pn2 ON pn2.node_id = kc2.node
                                 WHERE kc2.component = kc.component AND pn2.place_id = sp2.place_id))
              = (SELECT count(DISTINCT sp3.place_id) FROM section_place sp3 WHERE sp3.section = sp.section))
     GROUP BY sp.section;
  END LOOP;
END $$;

DO $$
DECLARE r record;
BEGIN
  SELECT string_agg(l.name || '（' || x.places || '）', '、') AS bad INTO r
    FROM replace_problem x JOIN reach.line l ON l.id = x.line_id;
  IF r.bad IS NOT NULL THEN
    RAISE EXCEPTION '置き換えると、外した区間の駅どうしを、その路線を通る系統と残った辺で行き来できない（その区間を走る系統を足すか、replace_lines から外す）：%', r.bad;
  END IF;
END $$;

ANALYZE reach.node;
ANALYZE reach.edge;
ANALYZE reach.station_node;
