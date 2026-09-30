package io.github.exday.api;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * サンプルデータの読み取りに使う SQL。
 *
 * <p>値は必ずパラメータで渡し、SQL の文字列に埋め込まない（AGENTS ルール、Issue #89）。
 */
final class SampleQueries {

    private SampleQueries() {}

    /** 公開中の Discovery の件数。 */
    static final String COUNT_PUBLIC_DISCOVERIES = """
            SELECT count(*)
              FROM discovery
             WHERE visibility = :visibility
            """;

    /** PostGIS の関数（ST_Distance）で、大岡川と山下公園の代表点との距離（メートル）を求める。 */
    static final String DISTANCE_BETWEEN_PLACES = """
            SELECT ST_Distance(
                       (SELECT geom FROM place WHERE name = :fromName),
                       (SELECT geom FROM place WHERE name = :toName)
                   )
            """;

    static long countPublicDiscoveries(JdbcClient jdbcClient) {
        return jdbcClient.sql(COUNT_PUBLIC_DISCOVERIES)
                .param("visibility", "PUBLIC")
                .query(Long.class)
                .single();
    }

    static double distanceBetweenPlaces(JdbcClient jdbcClient, String fromName, String toName) {
        return jdbcClient.sql(DISTANCE_BETWEEN_PLACES)
                .param("fromName", fromName)
                .param("toName", toName)
                .query(Double.class)
                .single();
    }
}
