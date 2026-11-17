package io.github.exday.api;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * サンプルデータの読み取りに使う SQL。
 *
 * <p>値は必ずパラメータで渡し、SQL の文字列に埋め込まない（AGENTS ルール、Issue #89）。
 */
final class SampleQueries {

    private SampleQueries() {}

    /** 一般のユーザーに見える Discovery の件数（公開、かつ成立を取り消していない。visible_discovery のビュー）。 */
    static final String COUNT_PUBLIC_DISCOVERIES = """
            SELECT count(*)
              FROM visible_discovery
            """;

    /** PostGIS の関数（ST_Distance）で、2つの場所の形の間の距離（メートル）を求める。 */
    static final String DISTANCE_BETWEEN_PLACES = """
            SELECT ST_Distance(
                       (SELECT geom FROM place WHERE name = :fromName),
                       (SELECT geom FROM place WHERE name = :toName)
                   )
            """;

    static long countPublicDiscoveries(JdbcClient jdbcClient) {
        return jdbcClient.sql(COUNT_PUBLIC_DISCOVERIES)
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
