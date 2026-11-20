-- ex-day：DB V3。寄り道の到達圏（F06）の駅・行き方・到達点（Issue #161、PR #165 の3段目）
--
-- 設計：docs/ui/functions/F06-detour-reach.md「データの形」1〜4、DEC-0013 決定2・4・5
--
-- - 利用者から見える駅は場所（place。種類が「駅」）。1つの駅の場所＝ N02 の駅グループ（乗換できるまとまり）1つ
-- - 計算の中の駅（路線ごとの駅）と路線は reach のスキーマに置く（運用・実装のデータ。論理設計 14節）
-- - 行き方（place_access）と到達点（place_arrival_point）はドメインの表として public に置く
--
-- 鉄道のグラフ（reach.node・reach.edge・表定速度）、到達圏の版（reach.build）、駅から場所への歩き（reach.place_walk）は、
-- 歩きのデータ・更新の手順を #163 で決めてから、次のマイグレーションで作る。

CREATE SCHEMA IF NOT EXISTS reach;
COMMENT ON SCHEMA reach IS '寄り道の到達圏（F06）の計算のデータ。ドメインの表と分ける';

-- ---------------------------------------------------------
-- 駅（F06「データの形」1、DEC-0013 決定4）
-- ---------------------------------------------------------

CREATE TABLE reach.line (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operator    text        NOT NULL CHECK (operator <> ''),
    name        text        NOT NULL CHECK (name <> ''),
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (operator, name)
);
COMMENT ON TABLE reach.line IS '路線（事業者・路線の名前）。駅の候補で同じ名前の駅を見分けるために出す。表定速度等は到達圏のグラフの段で足す';

CREATE TABLE reach.station (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id    bigint      NOT NULL REFERENCES place (id),
    line_id     bigint      NOT NULL REFERENCES reach.line (id),
    name        text        NOT NULL CHECK (name <> ''),
    geom        geography   NOT NULL CHECK (GeometryType(geom) IN ('POINT', 'LINESTRING', 'MULTILINESTRING')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (line_id, place_id)
);
COMMENT ON TABLE reach.station IS '計算の中の駅（路線ごとの駅1行。N02 の駅）。place_id は駅の場所（乗換できるまとまり）。駅の場所の種類が「駅」であることは取り込みの処理で守る';
COMMENT ON COLUMN reach.station.name IS 'N02 の駅名（駅の場所の表示名とは別に持つ）';
COMMENT ON COLUMN reach.station.geom IS 'ホームの位置（N02 は線。WGS84）';
CREATE INDEX reach_station_place_idx ON reach.station (place_id);

CREATE TABLE reach.station_source (
    station_id     bigint   NOT NULL REFERENCES reach.station (id),
    dataset_year   smallint NOT NULL CHECK (dataset_year BETWEEN 2000 AND 2100),
    station_code   text     NOT NULL CHECK (station_code <> ''),
    group_code     text     NOT NULL CHECK (group_code <> ''),
    PRIMARY KEY (station_id, dataset_year),
    UNIQUE (dataset_year, station_code)
);
COMMENT ON TABLE reach.station_source IS 'N02 の年度ごとの駅コード・グループコードとの対応。年度が変わって駅コードが変わっても、station_id と駅の場所は変えない（名寄せの運用は #163）';

-- ---------------------------------------------------------
-- 到達点（F06「データの形」3、DEC-0013 決定5）
-- ---------------------------------------------------------

CREATE TABLE place_arrival_point (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id    bigint      NOT NULL REFERENCES place (id),
    geom        geography   NOT NULL CHECK (GeometryType(geom) = 'POINT'),
    kind        text        NOT NULL CHECK (kind IN ('CONFIRMED_ENTRANCE', 'ESTIMATED')),
    mode        text        NOT NULL DEFAULT 'WALK' CHECK (mode IN ('WALK', 'CAR', 'BUS')),
    source      text        NOT NULL CHECK (source IN ('OSM_ENTRANCE', 'OSM_ESTIMATED', 'OFFICIAL', 'OPERATOR')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, place_id)
);
COMMENT ON TABLE place_arrival_point IS '場所の到達点（確かな入口／推定の到達点）。1つの場所に0以上。駅からの歩きはここまでを測る';
COMMENT ON COLUMN place_arrival_point.mode IS '着く手段。MVP は徒歩だけ。将来：車（駐車場）・バス（停留所）';
COMMENT ON COLUMN place_arrival_point.source IS 'OSM_ENTRANCE：OSM の入口の印。OSM_ESTIMATED：OSM の道と範囲の境界の交わり・範囲の端';
CREATE INDEX place_arrival_point_place_idx ON place_arrival_point (place_id);

-- ---------------------------------------------------------
-- 行き方（F06「データの形」2、DEC-0013 決定2・3）
-- ---------------------------------------------------------

CREATE TABLE place_access (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id             bigint      NOT NULL REFERENCES place (id),
    station_place_id     bigint      NOT NULL REFERENCES place (id),
    mode                 text        NOT NULL CHECK (mode IN ('WALK', 'BUS', 'TAXI', 'OTHER')),
    minutes              integer     NOT NULL CHECK (minutes > 0),
    arrival_point_id     bigint,
    original_text        text,
    note                 text,
    source               text        NOT NULL CHECK (source IN ('OPERATOR', 'OFFICIAL', 'USER', 'CONVERSATION', 'AUTO')),
    verification         text        NOT NULL CHECK (verification IN ('HUMAN_VERIFIED', 'UNVERIFIED')),
    source_url           text,
    retrieved_on         date,
    verified_at          timestamptz,
    status               text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INVALID', 'EXTRACTION_FAILED')),
    created_decision_id  bigint      NOT NULL REFERENCES decision (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    CHECK (station_place_id <> place_id),
    CHECK ((verification = 'HUMAN_VERIFIED') = (verified_at IS NOT NULL)),
    FOREIGN KEY (arrival_point_id, place_id) REFERENCES place_arrival_point (id, place_id)
);
COMMENT ON TABLE place_access IS '行き方（駅から場所まで）。1つの場所に何件でも。直さずに足していき、古い・誤りは status で無効にする。人が登録したものは AI の解釈で書き換えない（DEC-0013 決定2）';
COMMENT ON COLUMN place_access.station_place_id IS '駅の場所（種類が「駅」）';
COMMENT ON COLUMN place_access.mode IS '主な手段。MVP の F06 は徒歩（WALK）だけを参照する（バス等は MVP で扱わない）';
COMMENT ON COLUMN place_access.minutes IS '合計の分数（駅を出てから着く先まで）';
COMMENT ON COLUMN place_access.arrival_point_id IS '着く先の到達点。なければ場所そのもの。同じ場所の到達点だけを指せる（複合の外部キー）';
COMMENT ON COLUMN place_access.verification IS '人が確かめた／機械で取り出した・未確認。使える順番（DEC-0013 決定3）は出どころと確かめた状態から決め、列に持たない';
COMMENT ON COLUMN place_access.created_decision_id IS '登録した判断の記録（登録したのが人か取得の処理かは判断の記録の主体で分かる）';
CREATE INDEX place_access_place_idx ON place_access (place_id) WHERE status = 'ACTIVE';
CREATE INDEX place_access_station_idx ON place_access (station_place_id) WHERE status = 'ACTIVE';
