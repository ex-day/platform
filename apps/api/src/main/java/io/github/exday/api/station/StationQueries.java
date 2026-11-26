package io.github.exday.api.station;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 駅を選ぶ（F06「駅の選び方」）ための SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする（apps/api/AGENTS.md）。
 *
 * <p>駅は、ACTIVE で種類が「駅」の場所（1つの駅の場所は乗換できる駅のまとまり1つ）のうち、使っている到達圏のデータ
 * （ACTIVE の版）のグラフに点を持つもの（取り込んだ範囲の外の駅を候補に出さない。版がなければ候補は空。#163）。路線は計算の中の駅
 * （{@code reach.station}・{@code reach.line}。DB V3）から読む。場所は公開状態を持たないので表から読む。
 */
@Repository
public class StationQueries {

    /** まわりの駅を探す範囲（直線、メートル）。 */
    static final int NEARBY_RADIUS_METERS = 5000;

    // 当てはまり方：「駅」を除いた名前が同じ → で始まる → 含む。同じ順位の中は短い順、名前（文字コード）、識別子
    private static final String SEARCH_BY_NAME = """
        SELECT p.id, p.public_id, p.name,
               CASE WHEN regexp_replace(p.name, '駅$', '') = :exact THEN 0
                    WHEN regexp_replace(p.name, '駅$', '') LIKE :prefix ESCAPE '\\' THEN 1
                    ELSE 2 END AS tier
          FROM place p
         WHERE p.status = 'ACTIVE'
           AND EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = p.id AND k.kind = '駅')
           AND EXISTS (SELECT 1 FROM reach.station st
                         JOIN reach.station_node sn ON sn.station_id = st.id
                         JOIN reach.build b ON b.id = sn.build_id AND b.status = 'ACTIVE'
                        WHERE st.place_id = p.id)
           AND p.name LIKE :contains ESCAPE '\\'
         ORDER BY tier, char_length(regexp_replace(p.name, '駅$', '')), p.name COLLATE "C", p.public_id COLLATE "C"
         LIMIT :limit
        """;

    // 場所の形と駅の形の最短の直線の距離。場所が駅ならその駅を先頭に
    private static final String NEAR_PLACE = """
        SELECT s.id, s.public_id, s.name, round(ST_Distance(t.geom, s.geom))::integer AS distance_meters
          FROM place t
          JOIN place s ON ST_DWithin(t.geom, s.geom, :radius)
         WHERE t.id = :placeId
           AND s.status = 'ACTIVE'
           AND EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = s.id AND k.kind = '駅')
           AND EXISTS (SELECT 1 FROM reach.station st
                         JOIN reach.station_node sn ON sn.station_id = st.id
                         JOIN reach.build b ON b.id = sn.build_id AND b.status = 'ACTIVE'
                        WHERE st.place_id = s.id)
         ORDER BY (s.id = t.id) DESC, distance_meters, s.public_id COLLATE "C"
         LIMIT :limit
        """;

    // 現在地は保存せず、この問い合わせの中だけで使う
    private static final String NEAR_LOCATION = """
        WITH here AS (
            SELECT ST_SetSRID(ST_MakePoint(CAST(:longitude AS double precision), CAST(:latitude AS double precision)),
                              4326)::geography AS geom
        )
        SELECT s.id, s.public_id, s.name, round(ST_Distance(h.geom, s.geom))::integer AS distance_meters
          FROM here h
          JOIN place s ON ST_DWithin(h.geom, s.geom, :radius)
         WHERE s.status = 'ACTIVE'
           AND EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = s.id AND k.kind = '駅')
           AND EXISTS (SELECT 1 FROM reach.station st
                         JOIN reach.station_node sn ON sn.station_id = st.id
                         JOIN reach.build b ON b.id = sn.build_id AND b.status = 'ACTIVE'
                        WHERE st.place_id = s.id)
         ORDER BY distance_meters, s.public_id COLLATE "C"
         LIMIT :limit
        """;

    // 事業者・路線は一般的な呼び名で見せる（DB V5 の呼び名の表。無ければ N02 の名前）。見せる名前の順。
    // 同じ呼び名になる路線（横浜市の1号線と3号線 → ブルーライン 等）は1つにまとめる
    private static final String LINES = """
        SELECT x.place_id, x.operator, x.name
          FROM (
            SELECT DISTINCT s.place_id,
                   coalesce(od.display_name, l.operator) AS operator,
                   coalesce(ld.display_name, l.name) AS name
              FROM reach.station s
              JOIN reach.line l ON l.id = s.line_id
              LEFT JOIN reach.operator_display od ON od.operator = l.operator
              LEFT JOIN reach.line_display ld ON ld.operator = l.operator AND ld.name = l.name
             WHERE s.place_id IN (:placeIds)
          ) x
         ORDER BY x.place_id, x.operator COLLATE "C", x.name COLLATE "C"
        """;

    private static final String PLACE_ID = """
        SELECT p.id FROM place p WHERE p.public_id = :publicId
        """;

    /** 駅の場所。{@code distanceMeters} は名前で探したときは使わない。 */
    record StationRow(long id, String publicId, String name, int distanceMeters) {}

    /** 駅を通る路線（見せる名前）。 */
    record LineRow(long placeId, String operator, String name) {}

    private final JdbcClient jdbcClient;

    public StationQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** {@code name} は「駅」を除き、LIKE の記号を逃がす前の、探す文字。 */
    List<StationRow> searchByName(String name, int limit) {
        String escaped = escapeLike(name);
        return jdbcClient.sql(SEARCH_BY_NAME)
            .param("exact", name)
            .param("prefix", escaped + "%")
            .param("contains", "%" + escaped + "%")
            .param("limit", limit)
            .query((rs, rowNum) -> new StationRow(rs.getLong("id"), rs.getString("public_id"), rs.getString("name"), 0))
            .list();
    }

    List<StationRow> nearPlace(long placeId, int limit) {
        return jdbcClient.sql(NEAR_PLACE)
            .param("placeId", placeId)
            .param("radius", NEARBY_RADIUS_METERS)
            .param("limit", limit)
            .query((rs, rowNum) -> new StationRow(rs.getLong("id"), rs.getString("public_id"), rs.getString("name"),
                rs.getInt("distance_meters")))
            .list();
    }

    List<StationRow> nearLocation(double latitude, double longitude, int limit) {
        return jdbcClient.sql(NEAR_LOCATION)
            .param("latitude", latitude)
            .param("longitude", longitude)
            .param("radius", NEARBY_RADIUS_METERS)
            .param("limit", limit)
            .query((rs, rowNum) -> new StationRow(rs.getLong("id"), rs.getString("public_id"), rs.getString("name"),
                rs.getInt("distance_meters")))
            .list();
    }

    List<LineRow> lines(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(LINES)
            .param("placeIds", List.copyOf(placeIds))
            .query((rs, rowNum) -> new LineRow(rs.getLong("place_id"), rs.getString("operator"), rs.getString("name")))
            .list();
    }

    Optional<Long> placeId(String publicId) {
        return jdbcClient.sql(PLACE_ID).param("publicId", publicId).query(Long.class).optional();
    }

    /** LIKE の記号（{@code \}・{@code %}・{@code _}）を文字として扱う。 */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
