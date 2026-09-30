package io.github.exday.api.discovery;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * S03（Discovery 詳細）が読む SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする
 * （apps/api/AGENTS.md）。
 */
@Repository
public class DiscoveryDetailQueries {

    private static final String FIND_SUMMARY = """
        SELECT d.id, d.public_id, d.title, d.summary AS body, p.name AS place_name
        FROM discovery d
        JOIN discovery_place dp ON dp.discovery_id = d.id AND dp.is_primary
        JOIN place p ON p.id = dp.place_id
        WHERE d.public_id = :publicId AND d.visibility = :visibility
        """;

    private static final String FIND_VALUES = """
        SELECT v.id, v.public_id AS value_id, v.subject_label, v.summary, v.body,
               m.url AS picture_url, m.alt AS picture_alt,
               vp.message, vp.participation_message,
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
               (
                   SELECT array_agg(c.season ORDER BY c.season)
                   FROM value_condition c
                   WHERE c.value_id = v.id AND c.axis = :axis AND c.season <> :allYear
               ) AS seasons
        FROM discovery_value v
        LEFT JOIN media m ON m.id = v.picture_media_id
        LEFT JOIN value_presentation vp ON vp.value_id = v.id
        WHERE v.discovery_id = :discoveryId
        ORDER BY seasonal DESC, year_round DESC, v.id ASC
        """;

    private static final String FIND_FINDINGS = """
        SELECT f.public_id AS finding_id, f.kind, f.text, f.backed_by,
               COALESCE(SUM(CASE WHEN r.reaction_type = 'UNDERSTAND' THEN 1 ELSE 0 END), 0) AS understand,
               COALESCE(SUM(CASE WHEN r.reaction_type = 'WANT_TO_GO' THEN 1 ELSE 0 END), 0) AS want_to_go,
               COALESCE(SUM(CASE WHEN r.reaction_type = 'MAYBE' THEN 1 ELSE 0 END), 0) AS maybe
        FROM finding f
        LEFT JOIN finding_reaction r ON r.finding_id = f.id
        WHERE f.discovery_id = :discoveryId AND f.status = :status
        GROUP BY f.id, f.public_id, f.kind, f.text, f.backed_by, f.display_order
        ORDER BY f.display_order
        """;

    private static final String FIND_TAGS = """
        SELECT DISTINCT label FROM (
            SELECT pt.label
            FROM post_tag pt
            JOIN post p ON p.id = pt.post_id
            WHERE p.discovery_id = :discoveryId
            UNION
            SELECT ot.label
            FROM discovery_operator_tag ot
            WHERE ot.discovery_id = :discoveryId
        ) AS combined
        ORDER BY label
        """;

    private static final String FIND_REACTIONS = """
        SELECT reaction_type, COUNT(*) AS reaction_count
        FROM discovery_reaction
        WHERE discovery_id = :discoveryId
        GROUP BY reaction_type
        """;

    private static final String FIND_RELATIONS = """
        SELECT dr.relation_type = :derived AND dr.to_discovery_id = :discoveryId AS derived_from,
               dr.relation_type = :derived AND dr.from_discovery_id = :discoveryId AS derived_to,
               od.id AS other_id, od.public_id AS other_public_id, od.title AS other_title,
               op.name AS other_place_name
        FROM discovery_relation dr
        JOIN discovery od
            ON od.id = CASE WHEN dr.to_discovery_id = :discoveryId
                            THEN dr.from_discovery_id ELSE dr.to_discovery_id END
        JOIN discovery_place odp ON odp.discovery_id = od.id AND odp.is_primary
        JOIN place op ON op.id = odp.place_id
        WHERE (dr.from_discovery_id = :discoveryId OR dr.to_discovery_id = :discoveryId)
          AND od.visibility = :visibility
        ORDER BY dr.id
        """;

    private final JdbcClient jdbcClient;

    public DiscoveryDetailQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<DiscoverySummary> findSummary(String publicId) {
        return jdbcClient.sql(FIND_SUMMARY)
            .param("publicId", publicId)
            .param("visibility", "PUBLIC")
            .query((rs, rowNum) -> new DiscoverySummary(
                rs.getLong("id"), rs.getString("public_id"), rs.getString("title"),
                rs.getString("body"), rs.getString("place_name")))
            .optional();
    }

    public List<DiscoveryValueRow> findValues(long discoveryId, String season) {
        return jdbcClient.sql(FIND_VALUES)
            .param("discoveryId", discoveryId)
            .param("axis", "SEASON")
            .param("season", season)
            .param("allYear", "ALL_YEAR")
            .query((rs, rowNum) -> new DiscoveryValueRow(
                rs.getString("value_id"), rs.getString("subject_label"), rs.getString("summary"),
                rs.getString("body"), rs.getString("picture_url"), rs.getString("picture_alt"),
                rs.getString("message"), rs.getString("participation_message"),
                rs.getBoolean("seasonal"), rs.getBoolean("year_round"), toStringList(rs.getArray("seasons"))))
            .list();
    }

    public List<DiscoveryFindingRow> findFindings(long discoveryId) {
        return jdbcClient.sql(FIND_FINDINGS)
            .param("discoveryId", discoveryId)
            .param("status", "ACTIVE")
            .query((rs, rowNum) -> new DiscoveryFindingRow(
                rs.getString("finding_id"), rs.getString("kind"), rs.getString("text"),
                rs.getString("backed_by"), rs.getInt("understand"), rs.getInt("want_to_go"), rs.getInt("maybe")))
            .list();
    }

    public List<String> findTags(long discoveryId) {
        return jdbcClient.sql(FIND_TAGS)
            .param("discoveryId", discoveryId)
            .query(String.class)
            .list();
    }

    public DiscoveryReactionCounts findReactions(long discoveryId) {
        var counts = jdbcClient.sql(FIND_REACTIONS)
            .param("discoveryId", discoveryId)
            .query((rs, rowNum) -> Map.entry(rs.getString("reaction_type"), rs.getInt("reaction_count")))
            .list();
        int like = 0;
        int surprised = 0;
        int love = 0;
        for (var entry : counts) {
            switch (entry.getKey()) {
                case "LIKE" -> like = entry.getValue();
                case "SURPRISED" -> surprised = entry.getValue();
                case "LOVE" -> love = entry.getValue();
                default -> { }
            }
        }
        return new DiscoveryReactionCounts(like, surprised, love);
    }

    public List<DiscoveryRelationRow> findRelations(long discoveryId) {
        return jdbcClient.sql(FIND_RELATIONS)
            .param("discoveryId", discoveryId)
            .param("visibility", "PUBLIC")
            .param("derived", "DERIVED")
            .query((rs, rowNum) -> new DiscoveryRelationRow(
                rs.getBoolean("derived_from"), rs.getBoolean("derived_to"),
                rs.getLong("other_id"), rs.getString("other_public_id"), rs.getString("other_title"),
                rs.getString("other_place_name")))
            .list();
    }

    private static List<String> toStringList(Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            var result = new ArrayList<String>();
            for (Object value : (Object[]) array.getArray()) {
                result.add((String) value);
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read season array.", e);
        }
    }
}
