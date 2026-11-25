package io.github.exday.api.detour;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exday.api.DbApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.data.Offset;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 登録・変更した場所の歩きを作り直す処理（{@link PlaceWalkRefresher}、DB V5）のテスト。
 *
 * <p>サンプルの版（sample.1）は歩きのグラフを持たないので、テストの中で歩きのグラフを持つ READY の版（test.walk）を作る：
 * 東西に 80m ごとの点 1〜4（辺は1分ずつ）、桜木町駅を点1に結ぶ。計算そのものの確かめは {@code db/check/v5_checks.sql}。
 */
class PlaceWalkRefresherTest extends DbApiTestSupport {

    @Autowired PlaceWalkRefresher refresher;

    private long buildId;

    @BeforeEach
    void walkGraph() {
        buildId = jdbc.sql("INSERT INTO reach.build (version, status) VALUES ('test.walk', 'READY') RETURNING id")
            .query(Long.class).single();
        jdbc.sql("""
                INSERT INTO reach.walk_graph (build_id, walk_cap_minutes, walk_meters_per_minute, snap_meters, min_island_nodes)
                VALUES (:build, 15, 80, 300, 100)
                """).param("build", buildId).update();
        jdbc.sql("""
                INSERT INTO reach.walk_node (build_id, node_id, geom)
                SELECT :build, n, ST_SetSRID(ST_MakePoint(139.6400 + 0.000882 * (n - 1), 35.4400), 4326)
                  FROM generate_series(1, 4) AS n
                """).param("build", buildId).update();
        jdbc.sql("""
                INSERT INTO reach.walk_edge (build_id, source, target, cost, reverse_cost, geom)
                SELECT :build, a.node_id, b.node_id, 1, 1, ST_MakeLine(a.geom, b.geom)
                  FROM reach.walk_node a JOIN reach.walk_node b ON b.build_id = a.build_id AND b.node_id = a.node_id + 1
                 WHERE a.build_id = :build
                """).param("build", buildId).update();
        jdbc.sql("""
                INSERT INTO reach.walk_station (build_id, station_place_id, node_id, access_m)
                SELECT :build, id, 1, 0 FROM place WHERE public_id = :station
                """).param("build", buildId).param("station", sid("place:sakuragicho")).update();
    }

    private long addPark(String publicId, int node) {
        long id = jdbc.sql("""
                INSERT INTO place (public_id, name, spatial_type, geom, origin)
                VALUES (:publicId, 'テストの公園', 'POINT',
                        ST_SetSRID(ST_MakePoint(139.6400 + 0.000882 * (:node - 1), 35.4400), 4326)::geography, 'OPERATOR')
                RETURNING id
                """).param("publicId", publicId).param("node", node).query(Long.class).single();
        jdbc.sql("INSERT INTO place_kind (place_id, kind) VALUES (:id, '公園')").param("id", id).update();
        return id;
    }

    private Double walkMinutes(long placeId) {
        return jdbc.sql("""
                SELECT walk_min FROM reach.place_walk
                 WHERE build_id = :build AND place_id = :place AND arrival_point_id IS NULL
                """).param("build", buildId).param("place", placeId).query(Double.class).optional().orElse(null);
    }

    private long jobs() {
        return jdbc.sql("SELECT count(*) FROM reach.place_walk_job").query(Long.class).single();
    }

    @Test
    void 足した場所は作り直すと駅からの歩きを持ち_待ち行列は空になる() {
        long park = addPark("tstpark00001", 3);
        assertThat(walkMinutes(park)).isNull();
        assertThat(jobs()).isPositive();

        int done = refresher.refreshPending();

        assertThat(done).isPositive();
        assertThat(walkMinutes(park)).isCloseTo(2.0, Offset.offset(1e-6));
        assertThat(jobs()).isZero();
    }

    @Test
    void 場所を動かすと作り直して歩きが変わる() {
        long park = addPark("tstpark00002", 2);
        refresher.refreshPending();
        assertThat(walkMinutes(park)).isCloseTo(1.0, Offset.offset(1e-6));

        jdbc.sql("""
                UPDATE place SET geom = ST_SetSRID(ST_MakePoint(139.6400 + 0.000882 * 3, 35.4400), 4326)::geography
                 WHERE id = :id
                """).param("id", park).update();
        refresher.refreshPending();

        assertThat(walkMinutes(park)).isCloseTo(3.0, Offset.offset(1e-6));
    }

    @Test
    void 作っている途中の版の分は_READY_になるまで取り出さない() {
        jdbc.sql("UPDATE reach.build SET status = 'BUILDING' WHERE id = :build").param("build", buildId).update();
        long park = addPark("tstpark00003", 3);
        refresher.refreshPending();
        assertThat(walkMinutes(park)).isNull();
        assertThat(jdbc.sql("SELECT count(*) FROM reach.place_walk_job WHERE build_id = :build")
            .param("build", buildId).query(Long.class).single()).isPositive();

        jdbc.sql("UPDATE reach.build SET status = 'READY' WHERE id = :build").param("build", buildId).update();
        refresher.refreshPending();
        assertThat(walkMinutes(park)).isCloseTo(2.0, Offset.offset(1e-6));
    }

    @Test
    void 試した回数が上限に達した場所は取り出さず残す() {
        long park = addPark("tstpark00004", 3);
        jdbc.sql("UPDATE reach.place_walk_job SET attempts = 5, last_error = 'テスト' WHERE place_id = :place")
            .param("place", park).update();

        refresher.refreshPending();

        assertThat(walkMinutes(park)).isNull();
        assertThat(jdbc.sql("SELECT count(*) FROM reach.place_walk_job WHERE place_id = :place")
            .param("place", park).query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void 失敗した版の分は捨てる() {
        addPark("tstpark00005", 3);
        jdbc.sql("UPDATE reach.build SET status = 'FAILED' WHERE id = :build").param("build", buildId).update();
        assertThat(jdbc.sql("SELECT count(*) FROM reach.place_walk_job WHERE build_id = :build")
            .param("build", buildId).query(Long.class).single()).isPositive();

        refresher.refreshPending();

        assertThat(jdbc.sql("SELECT count(*) FROM reach.place_walk_job WHERE build_id = :build")
            .param("build", buildId).query(Long.class).single()).isZero();
    }
}
