package io.github.exday.api.detour;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 寄り道の到達圏（F06）の SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする（apps/api/AGENTS.md）。
 *
 * <p>pgRouting に渡す辺の SQL は、版の番号と開始点の配列を、DB の側の {@code format()} で埋めて作る（値は文字列に連結しない）。
 * 1つの駅の場所はホームの点を複数持つので、仮の点 0 から開始点のそれぞれへ所要時間 0 の辺を足し、1回だけ探索する
 * （ex-day/poc reach/sql/03_reachable.sql と同じ）。
 */
@Repository
public class DetourQueries {

    private static final String ACTIVE_BUILD = """
        SELECT b.id, b.version FROM reach.build b WHERE b.status = 'ACTIVE'
        """;

    private static final String PLACE = """
        SELECT p.id, p.public_id, p.name, p.status,
               EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = p.id AND k.kind = '駅') AS station
          FROM place p
         WHERE p.public_id = :publicId
        """;

    // 駅の場所から、鉄道で maxMinutes 分までに着ける駅の場所と、そこまでの分（待ちを含まない）
    private static final String STATION_TIMES = """
        SELECT s.place_id, min(d.agg_cost) AS minutes
          FROM (SELECT array_agg(sn.node_id) AS nodes
                  FROM reach.station_node sn JOIN reach.station s ON s.id = sn.station_id
                 WHERE sn.build_id = :buildId AND s.place_id = :placeId) start
         CROSS JOIN LATERAL pgr_drivingDistance(
                 format('SELECT id, source, target, cost, reverse_cost FROM reach.edge WHERE build_id = %s '
                        'UNION ALL SELECT -n, 0, n, 0, 0 FROM unnest(%L::bigint[]) AS n', :buildId, start.nodes),
                 0, CAST(:maxMinutes AS double precision), directed := true) d
          JOIN reach.station_node sn ON sn.build_id = :buildId AND sn.node_id = d.node
          JOIN reach.station s ON s.id = sn.station_id
         WHERE start.nodes IS NOT NULL
         GROUP BY s.place_id
        """;

    // 寄れる場所。駅ごとの行き（t1）・帰り（t2）を配列で受け、場所ごとに過ごせる時間がいちばん長くなる駅を選ぶ（F06「計算の決まり」3・4）
    private static final String PLACES = """
        WITH st AS (
            SELECT x.station_place_id, x.t1, x.t2
              FROM unnest(CAST(:stationIds AS bigint[]), CAST(:t1 AS double precision[]), CAST(:t2 AS double precision[]))
                   AS x (station_place_id, t1, t2)
        ),
        auto AS (
            -- 自動の目安：確かな入口 → 推定の到達点 → 場所の代表点の順に、いちばん近いもの
            SELECT DISTINCT ON (w.station_place_id, w.place_id) w.station_place_id, w.place_id, w.walk_min
              FROM reach.place_walk w
              JOIN st USING (station_place_id)
              LEFT JOIN place_arrival_point a ON a.id = w.arrival_point_id
             WHERE w.build_id = :buildId
             ORDER BY w.station_place_id, w.place_id,
                      CASE a.kind WHEN 'CONFIRMED_ENTRANCE' THEN 0 WHEN 'ESTIMATED' THEN 1 ELSE 2 END, w.walk_min
        ),
        acc AS (
            -- 徒歩の行き方：使える順番（DEC-0013 決定3）でいちばん上のもの。同じ順番なら新しいもの
            SELECT DISTINCT ON (pa.station_place_id, pa.place_id)
                   pa.station_place_id, pa.place_id, pa.minutes, pa.arrival_point_id
              FROM place_access pa
              JOIN st ON st.station_place_id = pa.station_place_id
             WHERE pa.status = 'ACTIVE' AND pa.mode = 'WALK'
             ORDER BY pa.station_place_id, pa.place_id,
                      CASE WHEN pa.source = 'OPERATOR' OR (pa.source = 'OFFICIAL' AND pa.verification = 'HUMAN_VERIFIED') THEN 1
                           WHEN pa.source = 'OFFICIAL' THEN 2
                           WHEN pa.source = 'USER' THEN 3
                           WHEN pa.source = 'CONVERSATION' THEN 4
                           ELSE 5 END,
                      pa.created_at DESC, pa.id DESC
        ),
        walk AS (
            -- 行き方があれば、同じ駅から同じ着く先までの自動の目安と比べて長いほう。比べられなければ行き方の分数
            SELECT coalesce(acc.station_place_id, auto.station_place_id) AS station_place_id,
                   coalesce(acc.place_id, auto.place_id) AS place_id,
                   CASE WHEN acc.place_id IS NULL THEN auto.walk_min
                        ELSE greatest(acc.minutes, coalesce(cmp.walk_min, acc.minutes)) END AS walk_min,
                   (acc.place_id IS NULL OR coalesce(cmp.walk_min, 0) > acc.minutes) AS estimated
              FROM acc
              FULL JOIN auto ON auto.station_place_id = acc.station_place_id AND auto.place_id = acc.place_id
              LEFT JOIN LATERAL (
                  SELECT CASE WHEN acc.arrival_point_id IS NULL THEN auto.walk_min
                              ELSE (SELECT w.walk_min FROM reach.place_walk w
                                     WHERE w.build_id = :buildId AND w.station_place_id = acc.station_place_id
                                       AND w.place_id = acc.place_id AND w.arrival_point_id = acc.arrival_point_id) END
                         AS walk_min
              ) cmp ON acc.place_id IS NOT NULL
        ),
        cand AS (
            -- 分は切り上げる（誤差は「行けない」側。DEC-0013 決定1）。寄り道先で終わるときは帰りの歩きもない
            SELECT w.place_id, w.station_place_id, w.walk_min, w.estimated,
                   CAST(ceil(st.t1 + w.walk_min - 1e-9) AS integer) AS outbound,
                   CASE WHEN :stop THEN 0 ELSE CAST(ceil(w.walk_min + st.t2 - 1e-9) AS integer) END AS ret
              FROM walk w JOIN st USING (station_place_id)
             WHERE w.walk_min <= :walkCap
        ),
        best AS (
            SELECT DISTINCT ON (c.place_id) c.*, :minutes - c.outbound - c.ret AS stay
              FROM cand c
             ORDER BY c.place_id, :minutes - c.outbound - c.ret DESC, c.walk_min, c.station_place_id
        )
        SELECT p.public_id, p.name, b.stay, b.outbound, b.ret,
               sp.public_id AS station_public_id, sp.name AS station_name,
               CAST(ceil(b.walk_min - 1e-9) AS integer) AS walk_minutes, b.estimated,
               (SELECT array_agg(k.kind ORDER BY k.kind COLLATE "C") FROM place_kind k WHERE k.place_id = p.id) AS kinds,
               ST_AsGeoJSON(CASE p.spatial_type
                                WHEN 'ROUTE' THEN ST_StartPoint(ST_GeometryN(p.geom::geometry, 1))
                                ELSE ST_PointOnSurface(p.geom::geometry) END, 6) AS position
          FROM best b
          JOIN place p ON p.id = b.place_id
          JOIN place sp ON sp.id = b.station_place_id
         WHERE b.stay >= :minStay
           AND p.status = 'ACTIVE'
           AND NOT EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = p.id AND k.kind = '駅')
           AND (CAST(:afterStay AS integer) IS NULL
                OR b.stay < :afterStay
                OR (b.stay = :afterStay AND p.public_id COLLATE "C" > CAST(:afterPlaceId AS text) COLLATE "C"))
         ORDER BY b.stay DESC, p.public_id COLLATE "C"
         LIMIT :limit
        """;

    // 地図の帯：駅ごとに、帯の下限を引いた残りで歩ける半径の円を作り、帯ごとに合わせる（ex-day/poc reach/sql/12_detour.sql と同じ）
    private static final String BANDS = """
        WITH st AS (
            SELECT x.station_place_id, x.slack
              FROM unnest(CAST(:stationIds AS bigint[]), CAST(:slack AS double precision[])) AS x (station_place_id, slack)
        ),
        circle AS (
            SELECT b.min_stay,
                   ST_Buffer(p.geom,
                             least(CASE WHEN :stop THEN st.slack - b.min_stay ELSE (st.slack - b.min_stay) / 2 END, :walkCap)
                                 * :metersPerMinute / :detourFactor,
                             'quad_segs=8')::geometry AS geom
              FROM unnest(CAST(:bands AS integer[])) AS b (min_stay)
              JOIN st ON st.slack > b.min_stay
              JOIN place p ON p.id = st.station_place_id
        )
        SELECT c.min_stay,
               ST_AsGeoJSON(ST_Multi(ST_SimplifyPreserveTopology(ST_Union(c.geom), 0.00005)), 6) AS geometry
          FROM circle c
         GROUP BY c.min_stay
         ORDER BY c.min_stay
        """;

    record Build(long id, String version) {}

    record PlaceRow(long id, String publicId, String name, String status, boolean station) {}

    record ReachPlaceRow(String publicId, String name, int stay, int outbound, int ret, String stationPublicId,
                         String stationName, int walkMinutes, boolean estimated, List<String> kinds, String position) {}

    record BandRow(int minStay, String geometry) {}

    /** 寄れる場所の問い合わせの条件。駅ごとの t1・t2 は同じ並びの配列。 */
    record PlaceQuery(long buildId, long[] stationIds, double[] t1, double[] t2, boolean stop, int minutes,
                      int walkCap, int minStay, Integer afterStay, String afterPlaceId, int limit) {}

    private final JdbcClient jdbcClient;

    public DetourQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<Build> activeBuild() {
        return jdbcClient.sql(ACTIVE_BUILD)
            .query((rs, rowNum) -> new Build(rs.getLong("id"), rs.getString("version")))
            .optional();
    }

    Optional<PlaceRow> place(String publicId) {
        return jdbcClient.sql(PLACE)
            .param("publicId", publicId)
            .query((rs, rowNum) -> new PlaceRow(rs.getLong("id"), rs.getString("public_id"), rs.getString("name"),
                rs.getString("status"), rs.getBoolean("station")))
            .optional();
    }

    /** 駅の場所ごとの、鉄道だけの分（待ちを含まない）。着けない駅の場所は入らない。 */
    Map<Long, Double> stationTimes(long buildId, long placeId, double maxMinutes) {
        var times = new HashMap<Long, Double>();
        jdbcClient.sql(STATION_TIMES)
            .param("buildId", buildId)
            .param("placeId", placeId)
            .param("maxMinutes", maxMinutes)
            .query(rs -> {
                times.put(rs.getLong("place_id"), rs.getDouble("minutes"));
            });
        return times;
    }

    List<ReachPlaceRow> places(PlaceQuery q) {
        return jdbcClient.sql(PLACES)
            .param("buildId", q.buildId())
            .param("stationIds", q.stationIds())
            .param("t1", q.t1())
            .param("t2", q.t2())
            .param("stop", q.stop())
            .param("minutes", q.minutes())
            .param("walkCap", q.walkCap())
            .param("minStay", q.minStay())
            .param("afterStay", q.afterStay())
            .param("afterPlaceId", q.afterPlaceId())
            .param("limit", q.limit())
            .query((rs, rowNum) -> new ReachPlaceRow(rs.getString("public_id"), rs.getString("name"), rs.getInt("stay"),
                rs.getInt("outbound"), rs.getInt("ret"), rs.getString("station_public_id"), rs.getString("station_name"),
                rs.getInt("walk_minutes"), rs.getBoolean("estimated"), kinds(rs.getArray("kinds")),
                rs.getString("position")))
            .list();
    }

    List<BandRow> bands(long[] stationIds, double[] slack, boolean stop, DetourProperties settings) {
        return jdbcClient.sql(BANDS)
            .param("stationIds", stationIds)
            .param("slack", slack)
            .param("stop", stop)
            .param("walkCap", settings.walkCapMinutes())
            .param("metersPerMinute", settings.walkMetersPerMinute())
            .param("detourFactor", settings.bandDetourFactor())
            .param("bands", settings.bands().stream().mapToInt(Integer::intValue).toArray())
            .query((rs, rowNum) -> new BandRow(rs.getInt("min_stay"), rs.getString("geometry")))
            .list();
    }

    private static List<String> kinds(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }
}
