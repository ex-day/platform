package io.github.exday.api.discovery;

import io.github.exday.api.generated.model.EvaluationContext;
import io.github.exday.api.generated.model.SectionKey;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DiscoveryCandidateQueries {
    private static final String FIND_CANDIDATES = """
        WITH candidates AS (
            SELECT d.public_id AS discovery_id, v.public_id AS value_id,
                   d.title, v.subject_label, v.summary, p.name AS place_name,
                   m.url AS picture_url, m.alt AS picture_alt,
                   EXISTS (
                       SELECT 1 FROM value_condition c
                       WHERE c.value_id = v.id AND c.axis = :axis AND c.season = :season
                   ) AS seasonal,
                   EXISTS (
                       SELECT 1 FROM value_condition c
                       WHERE c.value_id = v.id AND c.axis = :axis AND c.season = :allYear
                   ) OR NOT EXISTS (
                       SELECT 1 FROM value_condition c
                       WHERE c.value_id = v.id AND c.axis = :axis
                   ) AS year_round,
                   ST_Distance(p.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
                       AS distance_meters
            FROM discovery d
            JOIN discovery_value v ON v.discovery_id = d.id
            JOIN discovery_place dp ON dp.discovery_id = d.id AND dp.is_primary
            JOIN place p ON p.id = dp.place_id
            LEFT JOIN media m ON m.id = v.picture_media_id
            WHERE d.establishment_status = :established AND d.visibility = :visibility
              AND (:seasonOnly OR ST_DWithin(
                  p.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius))
        )
        SELECT * FROM candidates
        WHERE seasonal OR (NOT :seasonOnly AND year_round)
        """;

    private final JdbcClient jdbcClient;

    public DiscoveryCandidateQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<DiscoveryCandidate> find(SectionKey key, EvaluationContext context, double radius) {
        return jdbcClient.sql(FIND_CANDIDATES)
            .param("axis", "SEASON")
            .param("season", context.getSeason().getValue().toUpperCase(Locale.ROOT))
            .param("allYear", "ALL_YEAR")
            .param("established", "ESTABLISHED")
            .param("visibility", "PUBLIC")
            .param("seasonOnly", key == SectionKey.SEASON)
            .param("lat", context.getLocation().getLat())
            .param("lng", context.getLocation().getLng())
            .param("radius", radius)
            .query((rs, rowNum) -> new DiscoveryCandidate(
                rs.getString("discovery_id"), rs.getString("value_id"), rs.getString("title"),
                rs.getString("subject_label"), rs.getString("summary"), rs.getString("place_name"),
                rs.getString("picture_url"), rs.getString("picture_alt"),
                rs.getBoolean("seasonal"), rs.getDouble("distance_meters")))
            .list();
    }
}
