package io.github.exday.api.discovery;

import java.sql.Array;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import io.github.exday.api.card.project.ProjectCards;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * S03（Discovery のページ）が読む SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする
 * （apps/api/AGENTS.md）。
 *
 * <p>一般のユーザー向けの読み取りなので、公開の判定は DB の公開のビュー（{@code visible_*}）に任せる
 * （DB 設計 2.5節）。表を直接読むのは、公開のビューで見えると確かめた対象の中身（関わる場所・Value 等）だけにする。
 */
@Repository
public class DiscoveryDetailQueries {

    private static final String FIND_DISCOVERY = """
        SELECT d.id, d.public_id, d.title, d.summary
          FROM visible_discovery d
         WHERE d.public_id = :publicId
        """;

    // 統合先をたどる（循環は統合の処理で作らないが、念のため通った Discovery を2度通らない）。最後の統合先を返す。
    // 最後の Discovery からさらに統合の関係が出ている（循環している、または深さの上限で止まった）ときは、
    // 最後の統合先を決められないので has_next を true にし、統合先を返さない（F12 で循環を作らないようにする）。
    private static final String FIND_FINAL_MERGE_TARGET = """
        WITH RECURSIVE chain (id, depth, path) AS (
            SELECT CAST(:discoveryId AS bigint), 0, ARRAY[CAST(:discoveryId AS bigint)]
            UNION ALL
            SELECT r.to_discovery_id, c.depth + 1, c.path || r.to_discovery_id
              FROM chain c
              JOIN discovery_relation r
                ON r.from_discovery_id = c.id AND r.kind = :merged AND r.status = :active
             WHERE NOT r.to_discovery_id = ANY (c.path)
               AND c.depth < :maxDepth
        )
        SELECT c.depth, v.public_id, v.title,
               EXISTS (SELECT 1 FROM discovery_relation n
                        WHERE n.from_discovery_id = c.id AND n.kind = :merged AND n.status = :active) AS has_next
          FROM chain c
          LEFT JOIN visible_discovery v ON v.id = c.id
         ORDER BY c.depth DESC
         LIMIT 1
        """;

    private static final String FIND_PLACES = """
        SELECT dp.discovery_id, p.public_id, p.name, p.spatial_type, dp.role, dp.seq,
               dp.valid_from, dp.valid_to, dp.description,
               ST_AsGeoJSON(p.geom::geometry, :decimals) AS geojson,
               (SELECT array_agg(k.kind ORDER BY k.kind) FROM place_kind k WHERE k.place_id = p.id) AS kinds
          FROM discovery_place dp
          JOIN place p ON p.id = dp.place_id
         WHERE dp.discovery_id IN (:discoveryIds) AND dp.status = :active
         ORDER BY dp.discovery_id, dp.seq NULLS LAST, dp.id
        """;

    private static final String FIND_ASPECTS = """
        SELECT s.label
          FROM subject_assignment sa
          JOIN subject s ON s.id = sa.subject_id
         WHERE sa.discovery_id = :discoveryId AND s.kind = :aspect
         ORDER BY s.label
        """;

    private static final String FIND_VALUES = """
        SELECT v.public_id, v.description,
               (SELECT array_agg(c.season ORDER BY c.season)
                  FROM value_condition c
                 WHERE c.value_id = v.id AND c.axis = :seasonAxis) AS seasons
          FROM discovery_value v
         WHERE v.discovery_id = :discoveryId AND v.status = :active
         ORDER BY v.id
        """;

    private static final String FIND_ORIGINS = """
        SELECT o.kind, o.conversation_id, d.method
          FROM discovery_origin o
          JOIN decision d ON d.id = o.decision_id
         WHERE o.discovery_id = :discoveryId
         ORDER BY o.id
        """;

    private static final String FIND_REACTIONS = """
        SELECT r.kind, count(*) AS n
          FROM reaction r
         WHERE r.discovery_id = :discoveryId
         GROUP BY r.kind
        """;

    // わかってきたこと：もとになった会話と、この発見から始まった会話の、有効な Finding の今の版。公開の会話のものだけ
    private static final String FIND_FINDINGS = """
        SELECT f.public_id, f.kind, v.content, v.backed, v.unconfirmed,
               count(r.id) FILTER (WHERE r.kind = :agree) AS agree,
               count(r.id) FILTER (WHERE r.kind = :wantToGo) AS want_to_go,
               count(r.id) FILTER (WHERE r.kind = :maybe) AS maybe
          FROM visible_finding f
          JOIN finding_version v ON v.finding_id = f.id AND v.version = f.current_version
          LEFT JOIN reaction r ON r.finding_id = f.id
         WHERE f.status = :active
           AND (f.conversation_id IN (SELECT o.conversation_id FROM discovery_origin o
                                       WHERE o.discovery_id = :discoveryId AND o.conversation_id IS NOT NULL)
                OR f.conversation_id IN (SELECT c.id FROM conversation c WHERE c.entry_discovery_id = :discoveryId))
         GROUP BY f.id, f.public_id, f.kind, v.content, v.backed, v.unconfirmed, f.created_at
         ORDER BY f.created_at, f.id
        """;

    private static final String FIND_ORIGIN_CONVERSATION_IDS = """
        SELECT c.id
          FROM visible_conversation c
         WHERE c.id IN (SELECT o.conversation_id FROM discovery_origin o
                         WHERE o.discovery_id = :discoveryId AND o.conversation_id IS NOT NULL)
         ORDER BY c.created_at, c.public_id
        """;

    private static final String FIND_STARTED_CONVERSATION_IDS = """
        SELECT c.id
          FROM visible_conversation c
         WHERE c.entry_discovery_id = :discoveryId
         ORDER BY c.created_at DESC, c.public_id
         LIMIT :limit
        """;

    private static final String COUNT_STARTED_CONVERSATIONS = """
        SELECT count(*) FROM visible_conversation c WHERE c.entry_discovery_id = :discoveryId
        """;

    // 関わる企画：この Discovery を指す項目、またはこの Discovery の関わる場所を指す項目を持つ、公開の企画（企画ごとに1件）
    private static final String FIND_PROJECTS = """
        WITH discovery_places AS (
            SELECT dp.place_id FROM discovery_place dp
             WHERE dp.discovery_id = :discoveryId AND dp.status = :active
        ),
        matched AS (
            SELECT pi.project_id,
                   min(pi.position) FILTER (WHERE pi.discovery_id = :discoveryId) AS discovery_position,
                   min(pi.position) FILTER (WHERE pi.place_id IN (SELECT place_id FROM discovery_places)) AS place_position
              FROM visible_project_item pi
             WHERE pi.discovery_id = :discoveryId
                OR pi.place_id IN (SELECT place_id FROM discovery_places)
             GROUP BY pi.project_id
        )
        SELECT pr.public_id, pr.title, pr.type, pr.creator_kind, up.nickname AS creator_name,
               m.discovery_position, m.place_position
          FROM matched m
          JOIN visible_project pr ON pr.id = m.project_id
          JOIN user_profile up ON up.user_id = pr.creator_user_id
         ORDER BY pr.created_at DESC, pr.public_id
         LIMIT :limit
        """;

    private static final String COUNT_PROJECTS = """
        WITH discovery_places AS (
            SELECT dp.place_id FROM discovery_place dp
             WHERE dp.discovery_id = :discoveryId AND dp.status = :active
        )
        SELECT count(DISTINCT pi.project_id)
          FROM visible_project_item pi
         WHERE pi.discovery_id = :discoveryId
            OR pi.place_id IN (SELECT place_id FROM discovery_places)
        """;

    // 関係する Discovery（派生・関連）。相手は公開で成立を取り消しておらず、統合された側でないもの
    private static final String FIND_RELATIONS = """
        SELECT r.kind, r.from_discovery_id = :discoveryId AS outgoing,
               o.id AS other_id, o.public_id AS other_public_id, o.title AS other_title, o.summary AS other_summary
          FROM discovery_relation r
          JOIN visible_discovery o
            ON o.id = CASE WHEN r.from_discovery_id = :discoveryId THEN r.to_discovery_id ELSE r.from_discovery_id END
         WHERE (r.from_discovery_id = :discoveryId OR r.to_discovery_id = :discoveryId)
           AND r.kind IN (:relationKinds) AND r.status = :active
           AND NOT EXISTS (SELECT 1 FROM discovery_relation m
                            WHERE m.from_discovery_id = o.id AND m.kind = :merged AND m.status = :active)
         ORDER BY o.title, o.public_id
        """;

    /** 公開のビューで見えた Discovery。 */
    record DiscoveryRow(long id, String publicId, String title, String summary) {}

    /**
     * 統合をたどった結果。depth が 0 なら統合されていない。統合先が見えないとき、
     * または最後の統合先を決められないとき（hasNext）は、統合先を返さない。
     */
    record MergeRow(int depth, String targetPublicId, String targetTitle, boolean hasNext) {}

    record PlaceRow(long discoveryId, String publicId, String name, String spatialType, String role, Integer seq,
            LocalDate validFrom, LocalDate validTo, String description, String geoJson, List<String> kinds) {}

    record ValueRow(String publicId, String description, List<String> seasons) {}

    record OriginRow(String kind, Long conversationId, String method) {}

    record FindingRow(String publicId, String kind, String content, boolean backed, boolean unconfirmed,
            int agree, int wantToGo, int maybe) {}


    record RelationRow(String kind, boolean outgoing, long otherId, String otherPublicId, String otherTitle,
            String otherSummary) {}

    /** 一覧の区画の最大件数（OpenAPI の maxItems。固定）。 */
    static final int LIST_LIMIT = 10;

    /** 統合をたどる深さの上限。 */
    private static final int MAX_MERGE_DEPTH = 20;

    /** GeoJSON の座標の小数の桁数（7桁でおよそ 1cm）。 */
    private static final int GEOJSON_DECIMALS = 7;

    private static final String ACTIVE = "ACTIVE";

    private final JdbcClient jdbcClient;

    public DiscoveryDetailQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<DiscoveryRow> findDiscovery(String publicId) {
        return jdbcClient.sql(FIND_DISCOVERY)
            .param("publicId", publicId)
            .query((rs, rowNum) -> new DiscoveryRow(
                rs.getLong("id"), rs.getString("public_id"), rs.getString("title"), rs.getString("summary")))
            .optional();
    }

    public MergeRow findFinalMergeTarget(long discoveryId) {
        return jdbcClient.sql(FIND_FINAL_MERGE_TARGET)
            .param("discoveryId", discoveryId)
            .param("merged", "MERGED")
            .param("active", ACTIVE)
            .param("maxDepth", MAX_MERGE_DEPTH)
            .query((rs, rowNum) -> new MergeRow(
                rs.getInt("depth"), rs.getString("public_id"), rs.getString("title"), rs.getBoolean("has_next")))
            .single();
    }

    /** 関わる場所を Discovery ごとにまとめて返す（順番、識別子の順）。 */
    Map<Long, List<PlaceRow>> findPlaces(Collection<Long> discoveryIds) {
        if (discoveryIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(FIND_PLACES)
            .param("discoveryIds", discoveryIds)
            .param("active", ACTIVE)
            .param("decimals", GEOJSON_DECIMALS)
            .query((rs, rowNum) -> new PlaceRow(
                rs.getLong("discovery_id"), rs.getString("public_id"), rs.getString("name"),
                rs.getString("spatial_type"), rs.getString("role"), (Integer) rs.getObject("seq"),
                rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_to", LocalDate.class),
                rs.getString("description"), rs.getString("geojson"), toStringList(rs.getArray("kinds"))))
            .list().stream()
            .collect(Collectors.groupingBy(PlaceRow::discoveryId, java.util.LinkedHashMap::new, Collectors.toList()));
    }

    public List<String> findAspects(long discoveryId) {
        return jdbcClient.sql(FIND_ASPECTS)
            .param("discoveryId", discoveryId)
            .param("aspect", "ASPECT")
            .query(String.class)
            .list();
    }

    public List<ValueRow> findValues(long discoveryId) {
        return jdbcClient.sql(FIND_VALUES)
            .param("discoveryId", discoveryId)
            .param("seasonAxis", "SEASON")
            .param("active", ACTIVE)
            .query((rs, rowNum) -> new ValueRow(
                rs.getString("public_id"), rs.getString("description"), toStringList(rs.getArray("seasons"))))
            .list();
    }

    public List<OriginRow> findOrigins(long discoveryId) {
        return jdbcClient.sql(FIND_ORIGINS)
            .param("discoveryId", discoveryId)
            .query((rs, rowNum) -> new OriginRow(
                rs.getString("kind"), (Long) rs.getObject("conversation_id"), rs.getString("method")))
            .list();
    }

    /** Discovery へのリアクションの種類ごとの数。 */
    public Map<String, Integer> findReactionCounts(long discoveryId) {
        return jdbcClient.sql(FIND_REACTIONS)
            .param("discoveryId", discoveryId)
            .query((rs, rowNum) -> Map.entry(rs.getString("kind"), rs.getInt("n")))
            .list().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<FindingRow> findFindings(long discoveryId) {
        return jdbcClient.sql(FIND_FINDINGS)
            .param("discoveryId", discoveryId)
            .param("active", ACTIVE)
            .param("agree", "AGREE")
            .param("wantToGo", "WANT_TO_GO")
            .param("maybe", "MAYBE")
            .query((rs, rowNum) -> new FindingRow(
                rs.getString("public_id"), rs.getString("kind"), rs.getString("content"),
                rs.getBoolean("backed"), rs.getBoolean("unconfirmed"),
                rs.getInt("agree"), rs.getInt("want_to_go"), rs.getInt("maybe")))
            .list();
    }

    public List<Long> findOriginConversationIds(long discoveryId) {
        return jdbcClient.sql(FIND_ORIGIN_CONVERSATION_IDS)
            .param("discoveryId", discoveryId)
            .query(Long.class)
            .list();
    }

    /** この発見から始まった会話の識別子（新しい順、最大 {@link #LIST_LIMIT} 件）。 */
    public List<Long> findStartedConversationIds(long discoveryId) {
        return jdbcClient.sql(FIND_STARTED_CONVERSATION_IDS)
            .param("discoveryId", discoveryId)
            .param("limit", LIST_LIMIT)
            .query((rs, rowNum) -> rs.getLong("id"))
            .list();
    }

    public int countStartedConversations(long discoveryId) {
        return jdbcClient.sql(COUNT_STARTED_CONVERSATIONS)
            .param("discoveryId", discoveryId)
            .query(Integer.class)
            .single();
    }

    /** 関わる企画（企画の作成の時点の新しい順、最大 {@link #LIST_LIMIT} 件）。 */
    public List<ProjectCards.ProjectRow> findProjects(long discoveryId) {
        return jdbcClient.sql(FIND_PROJECTS)
            .param("discoveryId", discoveryId)
            .param("active", ACTIVE)
            .param("limit", LIST_LIMIT)
            .query((rs, rowNum) -> new ProjectCards.ProjectRow(
                rs.getString("public_id"), rs.getString("title"), rs.getString("type"),
                rs.getString("creator_kind"), rs.getString("creator_name"),
                (Integer) rs.getObject("discovery_position"), (Integer) rs.getObject("place_position")))
            .list();
    }

    public int countProjects(long discoveryId) {
        return jdbcClient.sql(COUNT_PROJECTS)
            .param("discoveryId", discoveryId)
            .param("active", ACTIVE)
            .query(Integer.class)
            .single();
    }

    public List<RelationRow> findRelations(long discoveryId) {
        return jdbcClient.sql(FIND_RELATIONS)
            .param("discoveryId", discoveryId)
            .param("relationKinds", List.of("DERIVED", "RELATED"))
            .param("merged", "MERGED")
            .param("active", ACTIVE)
            .query((rs, rowNum) -> new RelationRow(
                rs.getString("kind"), rs.getBoolean("outgoing"), rs.getLong("other_id"),
                rs.getString("other_public_id"), rs.getString("other_title"), rs.getString("other_summary")))
            .list();
    }

    private static List<String> toStringList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (Object value : (Object[]) array.getArray()) {
            result.add((String) value);
        }
        return result;
    }
}
