-- ex-day：DB V4。寄り道の到達圏（F06）の鉄道のグラフ・版・駅から場所への歩き（Issue #161・#168、PR #165 の3段目）
--
-- 設計：docs/ui/functions/F06-detour-reach.md「データの形」5〜7、DEC-0014 決定1（pgRouting。歩きは OSM の歩ける道）
--
-- - 到達圏のデータは版（reach.build）ごとに持ち、使う版（ACTIVE）を1つにする。作り直しに失敗しても前の版で続けられる
-- - 鉄道のグラフ（reach.node・reach.edge）は pgRouting が読む。点は路線ごとに分ける（ex-day/poc reach/sql/01・02 と同じ）
-- - 駅から場所（到達点）への歩き（reach.place_walk）は、OSM の歩ける道で前もって計算した片道15分までの道のり
-- - 歩きのグラフそのもの（OSM の道）は、版を作る処理の中だけで使い、ここには持たない（ODbL の派生のデータベースを小さくする）

CREATE EXTENSION IF NOT EXISTS pgrouting;

-- ---------------------------------------------------------
-- 版（F06「データの形」5）
-- ---------------------------------------------------------

CREATE TABLE reach.build (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version       text        NOT NULL UNIQUE CHECK (version ~ '^[0-9A-Za-z._-]{1,40}$'),
    status        text        NOT NULL CHECK (status IN ('BUILDING', 'READY', 'ACTIVE', 'RETIRED', 'FAILED')),
    sources       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    note          text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    activated_at  timestamptz,
    CHECK ((status = 'ACTIVE') <= (activated_at IS NOT NULL))
);
COMMENT ON TABLE reach.build IS '到達圏のデータの版。グラフ・駅の点・駅から場所への歩きは版ごとに持つ。使う版は ACTIVE の1つ（作り直し・切り戻しの手順は #163）';
COMMENT ON COLUMN reach.build.version IS '版の名前。カーソルに入れる（例：2026-11-19.1）';
COMMENT ON COLUMN reach.build.sources IS '元のデータの版（例：{"n02": "N02-25", "osm": "kanto-261119"}）';
CREATE UNIQUE INDEX reach_build_one_active ON reach.build ((true)) WHERE status = 'ACTIVE';

-- ---------------------------------------------------------
-- 路線の表定速度（F06「データの形」5。PoC reach/sql/01・02）
-- ---------------------------------------------------------

ALTER TABLE reach.line
    ADD COLUMN service_type text NOT NULL DEFAULT 'conventional'
        CHECK (service_type IN ('shinkansen', 'conventional', 'subway', 'tram', 'monorail', 'agt', 'cable', 'other')),
    ADD COLUMN speed_kmh numeric NOT NULL DEFAULT 40 CHECK (speed_kmh > 0);
COMMENT ON COLUMN reach.line.speed_kmh IS '表定速度（km/h）。誤差を「行けない」側に寄せるため遅めに置く（PoC の既定：在来線40・地下鉄30・新幹線150 等）。直したら版を作り直す';

CREATE TABLE reach.speed_override (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    line_id       bigint      NOT NULL REFERENCES reach.line (id),
    from_station  bigint      NOT NULL REFERENCES reach.station (id),
    to_station    bigint      NOT NULL REFERENCES reach.station (id),
    speed_kmh     numeric     NOT NULL CHECK (speed_kmh > 0),
    source        text        NOT NULL CHECK (source <> ''),
    created_at    timestamptz NOT NULL DEFAULT now(),
    CHECK (from_station <> to_station)
);
COMMENT ON TABLE reach.speed_override IS '区間ごとの表定速度の上書き。同じ路線の2駅の間の辺に当てる（版を作るときに使う）';

-- ---------------------------------------------------------
-- 鉄道のグラフ（版ごと）
-- ---------------------------------------------------------

CREATE TABLE reach.node (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    build_id  bigint    NOT NULL REFERENCES reach.build (id) ON DELETE CASCADE,
    line_id   bigint    NOT NULL REFERENCES reach.line (id),
    geom      geography NOT NULL CHECK (GeometryType(geom) = 'POINT')
);
COMMENT ON TABLE reach.node IS 'グラフの点。路線ごとに分ける（別の路線の線路が触れている所で、乗換なしに乗り移れないようにする）。id は版をまたいで一意（pgRouting の点の番号）';
CREATE INDEX reach_node_build_idx ON reach.node (build_id);

CREATE TABLE reach.edge (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    build_id      bigint           NOT NULL REFERENCES reach.build (id) ON DELETE CASCADE,
    kind          text             NOT NULL CHECK (kind IN ('RIDE', 'TRANSFER')),
    line_id       bigint           REFERENCES reach.line (id),
    source        bigint           NOT NULL REFERENCES reach.node (id) ON DELETE CASCADE,
    target        bigint           NOT NULL REFERENCES reach.node (id) ON DELETE CASCADE,
    length_m      double precision CHECK (length_m >= 0),
    cost          double precision NOT NULL CHECK (cost >= 0),
    reverse_cost  double precision NOT NULL CHECK (reverse_cost >= 0),
    CHECK ((kind = 'RIDE') = (line_id IS NOT NULL))
);
COMMENT ON TABLE reach.edge IS 'グラフの辺。cost・reverse_cost は分（乗車は 距離÷表定速度、乗換は設定の10分）。向きは区別しない（cost = reverse_cost）';
CREATE INDEX reach_edge_build_idx ON reach.edge (build_id);

CREATE TABLE reach.station_node (
    build_id    bigint NOT NULL REFERENCES reach.build (id) ON DELETE CASCADE,
    station_id  bigint NOT NULL REFERENCES reach.station (id),
    node_id     bigint NOT NULL REFERENCES reach.node (id) ON DELETE CASCADE,
    PRIMARY KEY (build_id, station_id, node_id)
);
COMMENT ON TABLE reach.station_node IS '計算の中の駅（路線ごとの駅）のホームの両端の点。駅の場所（乗換できるまとまり）からの探索は、ここの点のどれかから始める';

-- ---------------------------------------------------------
-- 駅から場所（到達点）への歩き（版ごと。F06「データの形」6）
-- ---------------------------------------------------------

CREATE TABLE reach.place_walk (
    build_id          bigint           NOT NULL REFERENCES reach.build (id) ON DELETE CASCADE,
    station_place_id  bigint           NOT NULL REFERENCES place (id),
    place_id          bigint           NOT NULL REFERENCES place (id),
    arrival_point_id  bigint           REFERENCES place_arrival_point (id),
    walk_min          double precision NOT NULL CHECK (walk_min >= 0),
    method            text             NOT NULL CHECK (method IN ('OSM_WALK', 'SAMPLE')),
    UNIQUE NULLS NOT DISTINCT (build_id, station_place_id, place_id, arrival_point_id),
    CHECK (station_place_id <> place_id)
);
COMMENT ON TABLE reach.place_walk IS '駅（駅の場所）から場所への歩きの道のり（分）。到達点ごとに1行、到達点のない行は場所の代表点まで。ふつうは片道の上限（15分）までだが、徒歩の行き方がある組は、行き方と比べるために上限を超えても道のりを持つ（API は上限を超える組を外す）';
COMMENT ON COLUMN reach.place_walk.arrival_point_id IS '着く先の到達点。NULL は場所の代表点（到達点のない場所）';
COMMENT ON COLUMN reach.place_walk.method IS 'OSM_WALK：OSM の歩ける道（DEC-0014 決定1）。SAMPLE：サンプルデータの手で置いた値';
CREATE INDEX reach_place_walk_station_idx ON reach.place_walk (build_id, station_place_id);
