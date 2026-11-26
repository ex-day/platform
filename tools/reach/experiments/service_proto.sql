-- tools/reach/experiments/service_proto.sql
-- 試作：探索用の路線（運行系統）で、N02 の正式路線のグラフの一部を置き換える（PR #169 のレビュー、#170 の設計の確かめ）。
-- 版の鉄道のグラフを一時の表に写し、山手線・京浜東北線（根岸線）・上野東京ラインを足して、走る区間の N02 の乗車の辺を外し、
-- いくつかの区間の所要時間と乗換の回数を出す。DB は変えない（最後に ROLLBACK）。本番の作りは #170。
--   psql "$EXDAY_DSN" -v b=<版の id> -f tools/reach/experiments/service_proto.sql
-- 表定速度・停車駅は試作のための目安の値
\set ON_ERROR_STOP 1

BEGIN;
SELECT set_config('svc.b', :'b', true);  -- 関数の中では psql の変数が使えないので、設定で渡す

CREATE TEMP TABLE svc (id int PRIMARY KEY, operator text, name text, speed_kmh float8, loop boolean);
INSERT INTO svc VALUES
  (1, '東日本旅客鉄道', '山手線', 35, true),
  (2, '東日本旅客鉄道', '京浜東北線・根岸線', 40, false),
  (3, '東日本旅客鉄道', '上野東京ライン', 50, false);

CREATE TEMP TABLE svc_stop (service_id int, seq int, station text);
INSERT INTO svc_stop SELECT 1, o, n FROM unnest(string_to_array(
  '東京 有楽町 新橋 浜松町 田町 高輪ゲートウェイ 品川 大崎 五反田 目黒 恵比寿 渋谷 原宿 代々木 新宿 新大久保 高田馬場 目白 池袋 大塚 巣鴨 駒込 田端 西日暮里 日暮里 鶯谷 上野 御徒町 秋葉原 神田 東京', ' ')) WITH ORDINALITY AS t(n, o);
INSERT INTO svc_stop SELECT 2, o, n FROM unnest(string_to_array(
  '大宮 さいたま新都心 与野 北浦和 浦和 南浦和 蕨 西川口 川口 赤羽 東十条 王子 上中里 田端 西日暮里 日暮里 鶯谷 上野 御徒町 秋葉原 神田 東京 有楽町 新橋 浜松町 田町 高輪ゲートウェイ 品川 大井町 大森 蒲田 川崎 鶴見 新子安 東神奈川 横浜 桜木町 関内 石川町 山手 根岸 磯子 新杉田 洋光台 港南台 本郷台 大船', ' ')) WITH ORDINALITY AS t(n, o);
INSERT INTO svc_stop SELECT 3, o, n FROM unnest(string_to_array(
  '大宮 浦和 赤羽 尾久 上野 東京 新橋 品川 川崎 横浜 戸塚 大船', ' ')) WITH ORDINALITY AS t(n, o);

-- 停車駅を駅の場所に結ぶ（同じ事業者の、版にある駅）
CREATE TEMP TABLE stop_place AS
SELECT st.service_id, st.seq, st.station,
       (SELECT s.place_id FROM reach.station s JOIN reach.line l ON l.id = s.line_id
          JOIN reach.station_node sn ON sn.station_id = s.id AND sn.build_id = :b
         WHERE s.name = st.station AND l.operator = v.operator LIMIT 1) AS place_id
FROM svc_stop st JOIN svc v ON v.id = st.service_id;
SELECT 'missing stop', * FROM stop_place WHERE place_id IS NULL;

-- 区間の道のり：同じ事業者の N02 の乗車の辺（長さ）と、同じ駅の場所の路線どうし（0m）の上で、隣の停車駅まで最短
CREATE TEMP TABLE len_edge AS
SELECT e.id, e.source, e.target, e.length_m AS cost, e.length_m AS reverse_cost, e.line_id
  FROM reach.edge e JOIN reach.line l ON l.id = e.line_id
 WHERE e.build_id = :b AND e.kind = 'RIDE' AND l.operator = '東日本旅客鉄道' AND l.service_type = 'conventional';
INSERT INTO len_edge
SELECT -e.id, e.source, e.target, 0, 0, NULL FROM reach.edge e
 WHERE e.build_id = :b AND e.kind = 'TRANSFER'
   AND EXISTS (SELECT 1 FROM len_edge a WHERE a.source IN (e.source) OR a.target IN (e.source))
   AND EXISTS (SELECT 1 FROM len_edge a WHERE a.source IN (e.target) OR a.target IN (e.target));

CREATE TEMP TABLE place_nodes AS
SELECT s.place_id, array_agg(DISTINCT sn.node_id) AS nodes
  FROM reach.station s JOIN reach.station_node sn ON sn.station_id = s.id AND sn.build_id = :b
  JOIN reach.line l ON l.id = s.line_id AND l.operator = '東日本旅客鉄道' AND l.service_type = 'conventional'
 GROUP BY s.place_id;

CREATE TEMP TABLE hop AS
SELECT a.service_id, a.seq, a.place_id AS from_place, b.place_id AS to_place, p.length_m, p.edges
FROM stop_place a JOIN stop_place b ON b.service_id = a.service_id AND b.seq = a.seq + 1
CROSS JOIN LATERAL (
  WITH d AS (SELECT * FROM pgr_dijkstra('SELECT id, source, target, cost, reverse_cost FROM len_edge',
              (SELECT nodes FROM place_nodes WHERE place_id = a.place_id),
              (SELECT nodes FROM place_nodes WHERE place_id = b.place_id), directed := false)),
       best AS (SELECT start_vid, end_vid, agg_cost FROM d WHERE edge = -1 ORDER BY agg_cost LIMIT 1)
  SELECT best.agg_cost AS length_m, array_agg(d.edge) FILTER (WHERE d.edge > 0) AS edges
    FROM d JOIN best USING (start_vid, end_vid) GROUP BY best.agg_cost
) p;
SELECT 'hop not found', count(*) FROM stop_place a JOIN stop_place b ON b.service_id = a.service_id AND b.seq = a.seq + 1
 WHERE NOT EXISTS (SELECT 1 FROM hop h WHERE h.service_id = a.service_id AND h.seq = a.seq);

-- 置き換える N02 の乗車の辺：どれかの系統が走る区間
CREATE TEMP TABLE covered AS SELECT DISTINCT unnest(edges) AS edge_id FROM hop;
SELECT 'covered N02 edges', count(*), string_agg(DISTINCT l.name, ', ')
  FROM covered c JOIN reach.edge e ON e.id = c.edge_id JOIN reach.line l ON l.id = e.line_id;

-- 新しいグラフ：N02 の乗車の辺（置き換えた区間を除く）＋ 系統の点・乗車の辺、乗換は同じ駅の場所の点どうし（同じ系統を除く）
CREATE TEMP TABLE g_node (id bigserial PRIMARY KEY, place_id bigint, service_id int, n02_node bigint);
INSERT INTO g_node (place_id, service_id, n02_node)
SELECT NULL, NULL, n.id FROM reach.node n WHERE n.build_id = :b;
UPDATE g_node g SET place_id = s.place_id
  FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id
 WHERE sn.build_id = :b AND sn.node_id = g.n02_node;
INSERT INTO g_node (place_id, service_id) SELECT DISTINCT place_id, service_id FROM stop_place;
CREATE INDEX ON g_node (n02_node);

CREATE TEMP TABLE g_edge (id bigserial, source bigint, target bigint, cost float8, reverse_cost float8, kind text);
INSERT INTO g_edge (source, target, cost, reverse_cost, kind)
SELECT gs.id, gt.id, e.cost, e.reverse_cost, 'RIDE'
  FROM reach.edge e JOIN g_node gs ON gs.n02_node = e.source JOIN g_node gt ON gt.n02_node = e.target
 WHERE e.build_id = :b AND e.kind = 'RIDE' AND NOT EXISTS (SELECT 1 FROM covered c WHERE c.edge_id = e.id);
INSERT INTO g_edge (source, target, cost, reverse_cost, kind)
SELECT gs.id, gt.id, h.length_m / 1000.0 / v.speed_kmh * 60, h.length_m / 1000.0 / v.speed_kmh * 60, 'RIDE'
  FROM hop h JOIN svc v ON v.id = h.service_id
  JOIN g_node gs ON gs.service_id = h.service_id AND gs.place_id = h.from_place
  JOIN g_node gt ON gt.service_id = h.service_id AND gt.place_id = h.to_place;
INSERT INTO g_edge (source, target, cost, reverse_cost, kind)
SELECT a.id, b.id, 10, 10, 'TRANSFER'
  FROM g_node a JOIN g_node b ON a.place_id = b.place_id AND a.id < b.id
 WHERE a.place_id IS NOT NULL
   AND (a.service_id IS DISTINCT FROM b.service_id OR a.service_id IS NULL)
   AND NOT (a.service_id IS NULL AND b.service_id IS NULL
            AND NOT EXISTS (SELECT 1 FROM reach.edge e WHERE e.build_id = :b AND e.kind = 'TRANSFER'
                             AND ((e.source = a.n02_node AND e.target = b.n02_node) OR (e.source = b.n02_node AND e.target = a.n02_node))));

CREATE FUNCTION pg_temp.route(a text, z text, OUT mins numeric, OUT transfers bigint, OUT via text) LANGUAGE sql AS $$
  WITH sa AS (SELECT array_agg(g.id) ids FROM g_node g WHERE g.place_id = (SELECT s.place_id FROM reach.station s JOIN reach.station_node sn ON sn.station_id = s.id AND sn.build_id = current_setting('svc.b')::bigint WHERE s.name = a LIMIT 1)),
       sz AS (SELECT array_agg(g.id) ids FROM g_node g WHERE g.place_id = (SELECT s.place_id FROM reach.station s JOIN reach.station_node sn ON sn.station_id = s.id AND sn.build_id = current_setting('svc.b')::bigint WHERE s.name = z LIMIT 1)),
       d AS (SELECT * FROM pgr_dijkstra('SELECT id, source, target, cost, reverse_cost FROM g_edge',
                (SELECT ids FROM sa), (SELECT ids FROM sz), directed := false)),
       best AS (SELECT start_vid, end_vid FROM d WHERE edge = -1 ORDER BY agg_cost LIMIT 1),
       p AS (SELECT d.*, e.kind, g.service_id, g.place_id FROM d JOIN best USING (start_vid, end_vid)
               LEFT JOIN g_edge e ON e.id = d.edge JOIN g_node g ON g.id = d.node)
  SELECT round(max(agg_cost)::numeric, 1), count(*) FILTER (WHERE kind = 'TRANSFER'),
         string_agg(DISTINCT coalesce((SELECT name FROM svc WHERE id = p.service_id), 'N02'), '→')
    FROM p
$$;
SELECT a AS "から", z AS "まで", r.* FROM (VALUES ('渋谷','東京'),('上野','駒込'),('横浜','上野'),('横浜','大宮'),('品川','田端'),('横浜','桜木町'),('東京','赤羽'),('上野','蓮田'),('渋谷','新宿'),('新宿','東京'),('鶯谷','尾久')) v(a, z),
  LATERAL pg_temp.route(a, z) r;
ROLLBACK;
