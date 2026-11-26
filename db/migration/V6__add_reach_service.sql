-- ex-day：DB V6。寄り道の到達圏（F06）の鉄道のグラフに、探索用の路線（運行系統）を入れる（Issue #170）
--
-- 設計：docs/ui/functions/F06-detour-reach.md「データの形」9
--
-- N02 は正式な路線で入っていて、山手線・京浜東北線・上野東京ラインなどの運行系統で通り抜けるところ（東京・品川・田端 など）に
-- 余計な乗換が入る（PR #169 のレビュー）。運行系統の定義（停車駅の並び・速さ・通る N02 の路線）は tools/reach/services/ の
-- ファイルに持ち（2026-11-26 の人の判断）、版を作るときに、ここの表へ写す。切り戻した版は、その版を作ったときの系統のまま。
--
-- - 版の系統（reach.build_service）と停車駅（reach.build_service_stop）
-- - グラフの点・辺は、N02 の路線（line_id）か、版の系統（service_id）のどちらか1つに属する。系統の点は「系統 × 駅の場所」に1つ
-- - 系統の走る区間の N02 の乗車の辺を外すのは、人が「置き換えてよい」とした路線だけ（reach.build_replaced_line。
--   2026-11-26 の人の判断。快速だけを登録した段階で外すと、各駅停車だけの駅へ行けなくなるため。PR #169 の再レビュー）

CREATE TABLE reach.build_service (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    build_id     bigint           NOT NULL REFERENCES reach.build (id) ON DELETE CASCADE,
    service_key  text             NOT NULL CHECK (service_key ~ '^[a-z0-9][a-z0-9-]*$'),
    operator     text             NOT NULL CHECK (operator <> ''),
    name         text             NOT NULL CHECK (name <> ''),
    kind         text             NOT NULL CHECK (kind <> ''),
    speed_kmh    double precision NOT NULL CHECK (speed_kmh > 0),
    is_loop      boolean          NOT NULL DEFAULT false,
    UNIQUE (build_id, service_key)
);
COMMENT ON TABLE reach.build_service IS '版の探索用の路線（運行系統）。tools/reach/services/ の定義を、版を作るときに写したもの。operator は主な事業者の N02 の事業者名（駅の候補では、停車駅の路線の事業者を出す。事業者をまたぐ直通のため）、name は見せる系統の名前（山手線・上野東京ライン 等）';
COMMENT ON COLUMN reach.build_service.kind IS '種別（普通・快速・特急 等）。停車駅や速さが違えば別の系統にする';
COMMENT ON COLUMN reach.build_service.is_loop IS '環状（最後の停車駅から最初の停車駅へもつながる）。停車駅は1回ずつ並べ、最初の駅を最後に繰り返さない';

CREATE TABLE reach.build_service_stop (
    build_service_id  bigint  NOT NULL REFERENCES reach.build_service (id) ON DELETE CASCADE,
    seq               integer NOT NULL CHECK (seq >= 1),
    station_id        bigint  NOT NULL REFERENCES reach.station (id),
    display_name      text    CHECK (display_name <> ''),
    PRIMARY KEY (build_service_id, seq)
);
COMMENT ON TABLE reach.build_service_stop IS '版の系統の停車駅の並び（seq の順）。通過地点（経路を決めるだけの駅）は入れない。station_id は N02 の正式な路線の駅（reach.station。年度が変わっても同じ）。同じ駅が並びに2回出てもよい（一意にするのは順番だけ）';
COMMENT ON COLUMN reach.build_service_stop.display_name IS 'この駅で見せる系統の名前（例：上野東京ラインを戸塚では「東海道線」）。無ければ reach.build_service.name';
CREATE INDEX reach_build_service_stop_station_idx ON reach.build_service_stop (station_id);

CREATE TABLE reach.build_replaced_line (
    build_id  bigint NOT NULL REFERENCES reach.build (id) ON DELETE CASCADE,
    line_id   bigint NOT NULL REFERENCES reach.line (id),
    PRIMARY KEY (build_id, line_id)
);
COMMENT ON TABLE reach.build_replaced_line IS 'この版で、系統の走る区間の乗車の辺を外した N02 の路線（人が「置き換えてよい」とした路線）。外した区間の駅に、どれかの系統の点か残った乗車の辺があることを、版を作るときに確かめる';

-- グラフの点・辺は、N02 の路線か版の系統のどちらか1つに属する（乗換の辺はどちらにも属さない）
ALTER TABLE reach.node ALTER COLUMN line_id DROP NOT NULL;
ALTER TABLE reach.node ADD COLUMN service_id bigint REFERENCES reach.build_service (id) ON DELETE CASCADE;
ALTER TABLE reach.node ADD CONSTRAINT node_line_or_service_check CHECK (num_nonnulls(line_id, service_id) = 1);
COMMENT ON COLUMN reach.node.service_id IS '版の系統の点（系統 × 駅の場所に1つ）。N02 の路線の点は line_id';

ALTER TABLE reach.edge ADD COLUMN service_id bigint REFERENCES reach.build_service (id) ON DELETE CASCADE;
ALTER TABLE reach.edge DROP CONSTRAINT edge_check;
ALTER TABLE reach.edge ADD CONSTRAINT edge_ride_owner_check
    CHECK ((kind = 'RIDE' AND num_nonnulls(line_id, service_id) = 1)
        OR (kind = 'TRANSFER' AND num_nonnulls(line_id, service_id) = 0));
COMMENT ON COLUMN reach.edge.service_id IS '版の系統の乗車の辺（隣の停車駅の間。N02 の線路でたどった道のり ÷ 系統の表定速度）。N02 の路線の乗車の辺は line_id';
