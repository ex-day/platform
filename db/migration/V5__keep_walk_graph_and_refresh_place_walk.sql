-- ex-day：DB V5。寄り道の到達圏（F06）で、登録した場所をすぐに出す・駅と路線を一般的な呼び名で見せる（Issue #168、#163 の 2026-11-26 の人の判断）
--
-- 設計：docs/ui/functions/F06-detour-reach.md「データの形」6・8、DEC-0014 決定1
--
-- - 版ごとに OSM の歩きのグラフ（reach.walk_node・reach.walk_edge）と駅の点（reach.walk_station）を残す。
--   これまでは版を作る処理の一時の表だけで使っていた（V4）。登録した場所を、版の作り直し（半年に1回）を待たずに出すため
-- - 場所・種類・到達点（徒歩）・徒歩の行き方が変わったら、待ち行列（reach.place_walk_job）に場所を積み、API の処理が
--   その場所の分だけ「駅から場所への歩き」（reach.place_walk）と版で使う行き方（reach.build_access）を作り直す
--   （reach.refresh_place_walk）。駅・路線（N02）の変更は、これまでどおり版の作り直しで反映する
-- - 事業者・路線を一般的な呼び名で見せる対応表（reach.operator_display・reach.line_display）。表に無ければ N02 の名前

-- ---------------------------------------------------------
-- 版の歩きのグラフ（F06「データの形」6）
-- ---------------------------------------------------------

CREATE TABLE reach.walk_graph (
    build_id                bigint           PRIMARY KEY REFERENCES reach.build (id) ON DELETE CASCADE,
    walk_cap_minutes        double precision NOT NULL CHECK (walk_cap_minutes > 0),
    walk_meters_per_minute  double precision NOT NULL CHECK (walk_meters_per_minute > 0),
    snap_meters             double precision NOT NULL CHECK (snap_meters > 0),
    min_island_nodes        integer          NOT NULL CHECK (min_island_nodes >= 0),
    created_at              timestamptz      NOT NULL DEFAULT now()
);
COMMENT ON TABLE reach.walk_graph IS '版の歩きのグラフ（OSM の歩ける道）と、それを作った決まりの値。行のある版だけ、場所ごとの作り直しができる（サンプルの手で作った版には無い）';

CREATE TABLE reach.walk_node (
    build_id  bigint                NOT NULL REFERENCES reach.walk_graph (build_id) ON DELETE CASCADE,
    node_id   bigint                NOT NULL,
    geom      geometry(Point, 4326) NOT NULL,
    PRIMARY KEY (build_id, node_id)
);
COMMENT ON TABLE reach.walk_node IS '歩きのグラフの点（道の交差点・端。node_id は OSM の点の番号）。小さな島は除いてある。場所・到達点は、いちばん近い点に結ぶ';
CREATE INDEX reach_walk_node_geom_idx ON reach.walk_node USING gist (geom);

CREATE TABLE reach.walk_edge (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    build_id      bigint                     NOT NULL REFERENCES reach.walk_graph (build_id) ON DELETE CASCADE,
    source        bigint                     NOT NULL,
    target        bigint                     NOT NULL,
    cost          double precision           NOT NULL CHECK (cost >= 0),
    reverse_cost  double precision           NOT NULL CHECK (reverse_cost >= 0),
    geom          geometry(LineString, 4326) NOT NULL
);
COMMENT ON TABLE reach.walk_edge IS '歩きのグラフの辺（交差点で区切った道）。cost は分。geom は両端を結んだ線（探す範囲で絞るためだけに使う。大きさを抑えるため道の形は持たない）';
COMMENT ON COLUMN reach.walk_edge.geom IS '両端の点を結んだ線。片道の上限までの道は両端が起点から上限の距離の中にあるので、両端で絞っても結果は道の形で絞ったときと同じ';
CREATE INDEX reach_walk_edge_build_idx ON reach.walk_edge (build_id);
CREATE INDEX reach_walk_edge_geom_idx ON reach.walk_edge USING gist (geom);

CREATE TABLE reach.walk_station (
    build_id          bigint           NOT NULL REFERENCES reach.walk_graph (build_id) ON DELETE CASCADE,
    station_place_id  bigint           NOT NULL REFERENCES place (id),
    node_id           bigint           NOT NULL,
    access_m          double precision NOT NULL CHECK (access_m >= 0),
    PRIMARY KEY (build_id, station_place_id)
);
COMMENT ON TABLE reach.walk_station IS '版のグラフにある駅の場所を結んだ歩きの点（ホームの重心からいちばん近い点。DEC-0014 決定1）と、そこまでの直線の距離（m）';
CREATE INDEX reach_walk_station_node_idx ON reach.walk_station (build_id, node_id);

COMMENT ON TABLE reach.build_access IS '版で使う徒歩の行き方（ACTIVE・WALK）。版を作るときに読んだものを記録し、あとで登録した行き方は場所ごとの作り直し（reach.refresh_place_walk）で、比べる道のりと一緒に足す。API は、ここにある行き方だけを使う（比べる道のりが版にある行き方だけを使い、徒歩の上限を守るため。PR #165 のレビュー）。あとで無効にした行き方は、place_access.status ですぐに除く';

-- 到達点を消したら、その到達点への歩きも消す（場所ごとの作り直しで消すが、到達点を先に消せるようにする）
ALTER TABLE reach.place_walk DROP CONSTRAINT place_walk_arrival_point_id_fkey;
ALTER TABLE reach.place_walk ADD CONSTRAINT place_walk_arrival_point_id_fkey
    FOREIGN KEY (arrival_point_id) REFERENCES place_arrival_point (id) ON DELETE CASCADE;

-- ---------------------------------------------------------
-- 場所ごとの作り直しの待ち行列
-- ---------------------------------------------------------

CREATE TABLE reach.place_walk_job (
    build_id      bigint      NOT NULL REFERENCES reach.build (id) ON DELETE CASCADE,
    place_id      bigint      NOT NULL REFERENCES place (id) ON DELETE CASCADE,
    requested_at  timestamptz NOT NULL DEFAULT now(),
    attempts      integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error    text,
    PRIMARY KEY (build_id, place_id)
);
COMMENT ON TABLE reach.place_walk_job IS '駅から場所への歩きを作り直す場所（版ごと）。場所・種類・到達点・行き方の変更のトリガーが積み、API の処理（PlaceWalkRefresher）が取り出す。作っている途中（BUILDING）の版の分は READY になってから行う。切り戻しに備え、前に使っていた版（RETIRED）も作り直す。失敗したら attempts を増やして後ろに回し、上限に達したら残す（監視で見る）';

-- 版を作り始める処理（tools/reach）は、版の行を足すトランザクションでこの鍵を排他で取る。場所の変更は共有で取る。
-- これで、変更が版の行より前に確定していれば版を作る処理がその変更を読み、後なら変更のトリガーが版の行を見て待ち行列に積む
-- （どちらにも入らない変更がない。時刻には頼らない）。変更を書く処理は READ COMMITTED で動かす（既定）
CREATE FUNCTION reach.build_lock_key() RETURNS bigint LANGUAGE sql IMMUTABLE AS $$ SELECT 2026112601::bigint $$;

CREATE FUNCTION reach.enqueue_place_walk() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    ids bigint[];
BEGIN
    IF TG_TABLE_NAME = 'place' THEN
        ids := ARRAY[NEW.id];
    ELSIF TG_OP = 'INSERT' THEN
        ids := ARRAY[NEW.place_id];
    ELSIF TG_OP = 'DELETE' THEN
        ids := ARRAY[OLD.place_id];
    ELSE
        ids := ARRAY[NEW.place_id, OLD.place_id];
    END IF;
    PERFORM pg_advisory_xact_lock_shared(reach.build_lock_key());
    INSERT INTO reach.place_walk_job (build_id, place_id)
    SELECT b.id, i.place_id
      FROM reach.build b
     CROSS JOIN (SELECT DISTINCT unnest(ids) AS place_id) i
     WHERE b.status IN ('BUILDING', 'READY', 'ACTIVE', 'RETIRED')
       AND EXISTS (SELECT 1 FROM place p WHERE p.id = i.place_id)
    ON CONFLICT (build_id, place_id) DO UPDATE SET requested_at = now(), attempts = 0, last_error = NULL;
    RETURN NULL;
END $$;

CREATE TRIGGER place_enqueue_place_walk
    AFTER INSERT OR UPDATE OF geom, status, spatial_type ON place
    FOR EACH ROW EXECUTE FUNCTION reach.enqueue_place_walk();
CREATE TRIGGER place_kind_enqueue_place_walk
    AFTER INSERT OR UPDATE OR DELETE ON place_kind
    FOR EACH ROW EXECUTE FUNCTION reach.enqueue_place_walk();
CREATE TRIGGER place_arrival_point_enqueue_place_walk
    AFTER INSERT OR UPDATE OR DELETE ON place_arrival_point
    FOR EACH ROW EXECUTE FUNCTION reach.enqueue_place_walk();
CREATE TRIGGER place_access_enqueue_place_walk
    AFTER INSERT OR UPDATE OR DELETE ON place_access
    FOR EACH ROW EXECUTE FUNCTION reach.enqueue_place_walk();

-- ---------------------------------------------------------
-- 1つの場所の「駅から場所への歩き」を作り直す
-- ---------------------------------------------------------
-- 版を作るときの一括の計算（tools/reach/sql/build_walk.sql）と同じ決まりで、向きを逆にたどる（歩きは向きを区別しない）。
-- 着く先（到達点（徒歩）と場所の代表点）を道の点に結び、そこから片道の上限までたどって、版の駅の点に着いた組を持つ。
-- 徒歩の行き方がある組は、上限を超えても上限なしの道のりを持つ。版で使う行き方（build_access）も、この場所の分を入れ直す。
-- 歩きのグラフの無い版（サンプルの手で作った版）では何もしない（NULL を返す）。返すのは作った行の数
CREATE FUNCTION reach.refresh_place_walk(p_build_id bigint, p_place_id bigint) RETURNS integer LANGUAGE plpgsql AS $$
DECLARE
    g reach.walk_graph%ROWTYPE;
    n1 integer;
    n2 integer;
BEGIN
    SELECT * INTO g FROM reach.walk_graph WHERE build_id = p_build_id;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    DELETE FROM reach.place_walk WHERE build_id = p_build_id AND place_id = p_place_id;
    DELETE FROM reach.build_access ba USING place_access pa
     WHERE ba.build_id = p_build_id AND pa.id = ba.place_access_id AND pa.place_id = p_place_id;

    -- 寄り道先にならない場所（ACTIVE でない、都道府県・市区町村・町丁・字・駅）は、行を消すだけ
    IF NOT EXISTS (SELECT 1 FROM place p WHERE p.id = p_place_id AND p.status = 'ACTIVE'
                    AND NOT EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = p.id
                                     AND k.kind IN ('都道府県', '市区町村', '町丁・字', '駅'))) THEN
        RETURN 0;
    END IF;

    INSERT INTO reach.build_access (build_id, place_access_id)
    SELECT p_build_id, pa.id FROM place_access pa
     WHERE pa.place_id = p_place_id AND pa.status = 'ACTIVE' AND pa.mode = 'WALK';

    IF to_regclass('pg_temp.refresh_target') IS NOT NULL THEN
        DROP TABLE pg_temp.refresh_target;
    END IF;
    CREATE TEMP TABLE refresh_target AS
    WITH target AS (
      SELECT p.id AS place_id, NULL::bigint AS arrival_point_id,
             CASE p.spatial_type WHEN 'ROUTE' THEN ST_StartPoint(ST_GeometryN(p.geom::geometry, 1))
                                 ELSE ST_PointOnSurface(p.geom::geometry) END AS p
        FROM place p WHERE p.id = p_place_id
      UNION ALL
      SELECT a.place_id, a.id, a.geom::geometry
        FROM place_arrival_point a WHERE a.place_id = p_place_id AND a.mode = 'WALK'
    )
    SELECT t.place_id, t.arrival_point_id, n.node_id, n.geom, n.d AS access_m
      FROM target t
     CROSS JOIN LATERAL (
       SELECT w.node_id, w.geom, ST_Distance(w.geom::geography, t.p::geography) AS d
         FROM reach.walk_node w WHERE w.build_id = p_build_id ORDER BY w.geom <-> t.p LIMIT 1
     ) n
     WHERE n.d <= g.snap_meters;

    INSERT INTO reach.place_walk (build_id, station_place_id, place_id, arrival_point_id, walk_min, method)
    SELECT p_build_id, s.station_place_id, t.place_id, t.arrival_point_id,
           min(d.agg_cost + s.access_m / g.walk_meters_per_minute + t.access_m / g.walk_meters_per_minute), 'OSM_WALK'
      FROM refresh_target t
     CROSS JOIN LATERAL pgr_drivingDistance(
       format('SELECT id, source, target, cost, reverse_cost FROM reach.walk_edge WHERE build_id = %s AND geom && ST_Expand(%L::geometry, %s)',
              p_build_id, t.geom, g.walk_cap_minutes * g.walk_meters_per_minute / 80000.0),
       t.node_id, greatest(g.walk_cap_minutes - t.access_m / g.walk_meters_per_minute, 0), directed := false) d
      JOIN reach.walk_station s ON s.build_id = p_build_id AND s.node_id = d.node
     WHERE s.station_place_id <> t.place_id
     GROUP BY s.station_place_id, t.place_id, t.arrival_point_id
    HAVING min(d.agg_cost + s.access_m / g.walk_meters_per_minute + t.access_m / g.walk_meters_per_minute)
           <= g.walk_cap_minutes;
    GET DIAGNOSTICS n1 = ROW_COUNT;

    -- 徒歩の行き方がある組で、上の探索に残らなかったもの（上限を超える）は、上限なしの道のりを持つ（build_walk.sql と同じ）
    INSERT INTO reach.place_walk (build_id, station_place_id, place_id, arrival_point_id, walk_min, method)
    SELECT p_build_id, a.station_place_id, a.place_id, a.arrival_point_id,
           d.agg_cost + a.station_access_m / g.walk_meters_per_minute + a.target_access_m / g.walk_meters_per_minute,
           'OSM_WALK'
      FROM (
        SELECT DISTINCT s.station_place_id, s.node_id AS station_node, s.access_m AS station_access_m,
               t.place_id, t.arrival_point_id, t.node_id AS target_node, t.geom AS target_geom, t.access_m AS target_access_m
          FROM place_access pa
          JOIN reach.walk_station s ON s.build_id = p_build_id AND s.station_place_id = pa.station_place_id
          JOIN refresh_target t ON pa.arrival_point_id IS NULL OR t.arrival_point_id = pa.arrival_point_id
         WHERE pa.place_id = p_place_id AND pa.status = 'ACTIVE' AND pa.mode = 'WALK'
           AND s.station_place_id <> t.place_id
           AND NOT EXISTS (SELECT 1 FROM reach.place_walk w
                            WHERE w.build_id = p_build_id AND w.station_place_id = s.station_place_id
                              AND w.place_id = t.place_id AND w.arrival_point_id IS NOT DISTINCT FROM t.arrival_point_id)
      ) a
     CROSS JOIN LATERAL (
       SELECT coalesce(
         (SELECT r.agg_cost FROM pgr_dijkstra(
            format('SELECT id, source, target, cost, reverse_cost FROM reach.walk_edge WHERE build_id = %s AND geom && ST_Expand(%L::geometry, 0.05)',
                   p_build_id,
                   ST_MakeLine((SELECT w.geom FROM reach.walk_node w WHERE w.build_id = p_build_id AND w.node_id = a.station_node),
                               a.target_geom)),
            a.station_node, a.target_node, directed := false) r
          WHERE r.node = a.target_node),
         (SELECT r.agg_cost FROM pgr_dijkstra(
            format('SELECT id, source, target, cost, reverse_cost FROM reach.walk_edge WHERE build_id = %s', p_build_id),
            a.station_node, a.target_node, directed := false) r
          WHERE r.node = a.target_node)) AS agg_cost
     ) d
     WHERE d.agg_cost IS NOT NULL;
    GET DIAGNOSTICS n2 = ROW_COUNT;

    DROP TABLE refresh_target;
    RETURN n1 + n2;
END $$;
COMMENT ON FUNCTION reach.refresh_place_walk(bigint, bigint) IS '1つの場所の、版の「駅から場所への歩き」と版で使う行き方を作り直す。歩きのグラフの無い版では何もしない（NULL）';

-- ---------------------------------------------------------
-- 事業者・路線の一般的な呼び名（駅の候補で見せる）
-- ---------------------------------------------------------

CREATE TABLE reach.operator_display (
    operator      text PRIMARY KEY CHECK (operator <> ''),
    display_name  text NOT NULL CHECK (display_name <> '')
);
COMMENT ON TABLE reach.operator_display IS '事業者の一般的な呼び名（N02 の事業者名 → 見せる名前。例：東日本旅客鉄道 → JR東日本）。無ければ N02 の名前のまま。人が直す';

CREATE TABLE reach.line_display (
    operator      text NOT NULL CHECK (operator <> ''),
    name          text NOT NULL CHECK (name <> ''),
    display_name  text NOT NULL CHECK (display_name <> ''),
    PRIMARY KEY (operator, name)
);
COMMENT ON TABLE reach.line_display IS '路線の一般的な呼び名（N02 の事業者名・路線名 → 見せる名前。例：横浜市の1号線 → ブルーライン）。無ければ N02 の名前のまま。同じ呼び名になる路線（1号線と3号線など）は、駅の候補で1つにまとめる。人が直す';

-- 始める地域（N02-25 の2次メッシュ 5339）の事業者・路線から、正式な名前と一般的な呼び名が違うもの。
-- 事業者の名前のままでよい路線の愛称（つくばエクスプレス・りんかい線等）は、路線の側だけで直す（事業者と路線が同じ名前に並ばないように）
INSERT INTO reach.operator_display (operator, display_name) VALUES
    ('東日本旅客鉄道', 'JR東日本'),
    ('東海旅客鉄道', 'JR東海'),
    ('東京地下鉄', '東京メトロ'),
    ('東京都', '都営'),
    ('横浜市', '横浜市営地下鉄'),
    ('東急電鉄', '東急'),
    ('京浜急行電鉄', '京急'),
    ('相模鉄道', '相鉄');

INSERT INTO reach.line_display (operator, name, display_name) VALUES
    ('東京地下鉄', '2号線日比谷線', '日比谷線'),
    ('東京地下鉄', '3号線銀座線', '銀座線'),
    ('東京地下鉄', '4号線丸ノ内線', '丸ノ内線'),
    ('東京地下鉄', '4号線丸ノ内線分岐線', '丸ノ内線'),
    ('東京地下鉄', '5号線東西線', '東西線'),
    ('東京地下鉄', '7号線南北線', '南北線'),
    ('東京地下鉄', '8号線有楽町線', '有楽町線'),
    ('東京地下鉄', '9号線千代田線', '千代田線'),
    ('東京地下鉄', '11号線半蔵門線', '半蔵門線'),
    ('東京地下鉄', '13号線副都心線', '副都心線'),
    ('東京都', '1号線浅草線', '浅草線'),
    ('東京都', '6号線三田線', '三田線'),
    ('東京都', '10号線新宿線', '新宿線'),
    ('東京都', '12号線大江戸線', '大江戸線'),
    ('東京都', '荒川線', '都電荒川線'),
    ('横浜市', '1号線', 'ブルーライン'),
    ('横浜市', '3号線', 'ブルーライン'),
    ('横浜市', '4号線', 'グリーンライン'),
    ('首都圏新都市鉄道', '常磐新線', 'つくばエクスプレス'),
    ('東京臨海高速鉄道', '臨海副都心線', 'りんかい線'),
    ('ゆりかもめ', '東京臨海新交通臨海線', 'ゆりかもめ'),
    ('横浜高速鉄道', 'みなとみらい21線', 'みなとみらい線'),
    ('横浜シーサイドライン', '金沢シーサイドライン', 'シーサイドライン');
