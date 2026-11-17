package io.github.exday.api.place;

import io.github.exday.api.card.project.ProjectCards;
import java.sql.Array;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * S13（場所のページ）が読む SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする（apps/api/AGENTS.md）。
 *
 * <p>場所は公開状態を持たないので表から読む。場所の中に出すもの（発見・会話・企画・探している話）は、
 * 公開のビュー（{@code visible_*}）を通す（DB 設計 2.5節）。
 */
@Repository
public class PlaceQueries {

    private static final String FIND_PLACE = """
        SELECT p.id, p.public_id, p.name, p.spatial_type, p.status, p.existed_from, p.existed_to,
               ST_AsGeoJSON(p.geom::geometry, :decimals) AS geojson,
               (SELECT array_agg(k.kind ORDER BY k.kind COLLATE "C") FROM place_kind k WHERE k.place_id = p.id) AS kinds,
               s.public_id AS station_public_id, s.name AS station_name
          FROM place p
          LEFT JOIN place s ON s.id = p.access_station_place_id
         WHERE p.public_id = :publicId
        """;

    // 統合先をたどる。最後の場所からさらに統合先が出ている（循環・深さの上限）なら has_next
    private static final String FIND_FINAL_MERGE_TARGET = """
        WITH RECURSIVE chain (id, depth, path) AS (
            SELECT CAST(:placeId AS bigint), 0, ARRAY[CAST(:placeId AS bigint)]
            UNION ALL
            SELECT p.merged_into_place_id, c.depth + 1, c.path || p.merged_into_place_id
              FROM chain c
              JOIN place p ON p.id = c.id
             WHERE p.merged_into_place_id IS NOT NULL
               AND NOT p.merged_into_place_id = ANY (c.path)
               AND c.depth < :maxDepth
        )
        SELECT c.depth, p.public_id, p.name, p.merged_into_place_id IS NOT NULL AS has_next
          FROM chain c
          JOIN place p ON p.id = c.id
         ORDER BY c.depth DESC
         LIMIT 1
        """;

    private static final String FIND_ALIASES = """
        SELECT n.name, n.kind, n.valid_from, n.valid_to
          FROM place_name n
         WHERE n.place_id = :placeId AND n.kind IN (:kinds)
         ORDER BY n.valid_from NULLS LAST, n.name COLLATE "C", n.id
        """;

    // 階層：行政の階層（ADMINISTRATIVE）を先に、なければ包含（CONTAINS）。同じ種類なら上位の識別子の順で最初。
    // この場所自身と通った場所は含めない（循環したらそこでやめる）。上限の段まで
    private static final String FIND_BREADCRUMB = """
        WITH RECURSIVE up (id, depth, path) AS (
            SELECT CAST(:placeId AS bigint), 0, ARRAY[CAST(:placeId AS bigint)]
            UNION ALL
            SELECT parent.from_place_id, up.depth + 1, up.path || parent.from_place_id
              FROM up
              JOIN LATERAL (
                  SELECT r.from_place_id
                    FROM place_relation r
                    JOIN place pp ON pp.id = r.from_place_id
                   WHERE r.to_place_id = up.id AND r.kind IN (:kinds)
                   ORDER BY (r.kind <> :administrative), pp.public_id
                   LIMIT 1
              ) parent ON true
             WHERE NOT parent.from_place_id = ANY (up.path)
               AND up.depth < :maxDepth
        )
        SELECT p.public_id, p.name
          FROM up
          JOIN place p ON p.id = up.id
         WHERE up.depth > 0
         ORDER BY up.depth DESC
        """;

    // 推定の値：値の種類と計算の出どころごとに、いちばん新しく計算したもの
    private static final String FIND_ESTIMATES = """
        SELECT DISTINCT ON (e.metric, e.method) e.metric, e.value, e.unit, e.method, e.method_version, e.computed_at
          FROM place_estimate e
         WHERE e.place_id = :placeId
         ORDER BY e.metric, e.method, e.computed_at DESC, e.id DESC
        """;

    // ここでの発見：この場所が関わる、公開で成立を取り消しておらず、統合された側でない Discovery。
    // 成立の時点の新しい順に上限まで選んでから、観点（代表表記の文字コードの順で最初のもの）を付ける
    private static final String FIND_DISCOVERIES = """
        WITH matched AS (
            SELECT d.id, d.public_id, d.title, d.summary, d.established_at,
                   array_agg(DISTINCT dp.role) AS roles
              FROM discovery_place dp
              JOIN visible_discovery d ON d.id = dp.discovery_id
             WHERE dp.place_id = :placeId AND dp.status = :active
               AND NOT EXISTS (SELECT 1 FROM discovery_relation m
                                WHERE m.from_discovery_id = d.id AND m.kind = :merged AND m.status = :active)
             GROUP BY d.id, d.public_id, d.title, d.summary, d.established_at
        )
        SELECT m.id, m.public_id, m.title, m.summary, m.roles,
               (SELECT s.label
                  FROM subject_assignment sa
                  JOIN subject s ON s.id = sa.subject_id
                 WHERE sa.discovery_id = m.id AND s.kind = :aspect
                 ORDER BY s.label COLLATE "C"
                 LIMIT 1) AS aspect,
               count(*) OVER () AS total
          FROM matched m
         ORDER BY m.established_at DESC, m.public_id
         LIMIT :limit
        """;

    // このあたりで探している人がいます：この場所に当てはまる検討中の候補で、対象の会話に場所が決まっていない、
    // または対象の問いに回答がないもの。エリアの場所なら、範囲に入る新しい場所の案も当てはめる。公開の会話のものだけ。
    // 会話・問い（対象）ごとにまとめ、並びと件数と上位の対象を SQL で決めてから、その対象の候補だけを返す
    // （候補の行に LIMIT をかけると、対象の件数の約束を守れない）。total は対象の全体の数
    private static final String FIND_SEEKING = """
        WITH matched AS (
            SELECT pc.public_id, pc.created_at, pc.proposed_name,
                   pc.open_question_id IS NOT NULL AS is_question,
                   coalesce(pc.open_question_id, pc.conversation_id) AS target_id,
                   coalesce(q.public_id, c.public_id) AS target_public_id,
                   coalesce(pc.conversation_id, q.conversation_id) AS conversation_id,
                   q.public_id AS question_public_id, q.content AS question_content
              FROM visible_place_candidate pc
              LEFT JOIN conversation c ON c.id = pc.conversation_id
              LEFT JOIN open_question q ON q.id = pc.open_question_id
             WHERE pc.status = :considering
               AND (pc.place_id = :placeId
                    OR (:isArea AND pc.place_id IS NULL AND pc.proposed_geom IS NOT NULL
                        AND ST_Covers((SELECT geom FROM place WHERE id = :placeId), pc.proposed_geom)))
               AND (
                    (pc.conversation_id IS NOT NULL
                     AND NOT EXISTS (SELECT 1 FROM conversation_place cp WHERE cp.conversation_id = pc.conversation_id))
                 OR (pc.open_question_id IS NOT NULL AND q.status = :active
                     AND NOT EXISTS (SELECT 1 FROM place_adoption a
                                      WHERE a.open_question_id = pc.open_question_id AND a.status IN (:adoptedStatuses)))
               )
        ), targets AS (
            SELECT is_question, target_id, min(target_public_id) AS target_public_id,
                   max(created_at) AS last_created_at, count(*) OVER () AS total
              FROM matched
             GROUP BY is_question, target_id
             ORDER BY last_created_at DESC, target_public_id
             LIMIT :limit
        )
        SELECT m.public_id, m.proposed_name, m.is_question, m.target_id, m.conversation_id,
               m.question_public_id, m.question_content, t.total
          FROM targets t
          JOIN matched m ON m.is_question = t.is_question AND m.target_id = t.target_id
         ORDER BY t.last_created_at DESC, t.target_public_id, m.created_at, m.public_id
        """;

    private static final String FIND_CONVERSATION_IDS = """
        SELECT c.id
          FROM visible_conversation c
         WHERE EXISTS (SELECT 1 FROM conversation_place cp WHERE cp.conversation_id = c.id AND cp.place_id = :placeId)
         ORDER BY c.created_at DESC, c.public_id
         LIMIT :limit
        """;

    private static final String COUNT_CONVERSATIONS = """
        SELECT count(*)
          FROM visible_conversation c
         WHERE EXISTS (SELECT 1 FROM conversation_place cp WHERE cp.conversation_id = c.id AND cp.place_id = :placeId)
        """;

    // この場所が入っている企画：この場所を指す項目、またはこの場所が関わる Discovery（公開）を指す項目を持つ、公開の企画
    private static final String PLACE_PROJECT_MATCHES = """
        WITH place_discoveries AS (
            SELECT dp.discovery_id FROM discovery_place dp WHERE dp.place_id = :placeId AND dp.status = :active
        ),
        matched AS (
            SELECT pi.project_id,
                   min(pi.position) FILTER (WHERE pi.discovery_id IN (SELECT discovery_id FROM place_discoveries)) AS discovery_position,
                   min(pi.position) FILTER (WHERE pi.place_id = :placeId) AS place_position
              FROM visible_project_item pi
             WHERE pi.place_id = :placeId
                OR pi.discovery_id IN (SELECT discovery_id FROM place_discoveries)
             GROUP BY pi.project_id
        )
        """;

    private static final String FIND_PROJECTS = PLACE_PROJECT_MATCHES + """
        SELECT pr.public_id, pr.title, pr.type, pr.creator_kind, up.nickname AS creator_name,
               m.discovery_position, m.place_position
          FROM matched m
          JOIN visible_project pr ON pr.id = m.project_id
          JOIN user_profile up ON up.user_id = pr.creator_user_id
         ORDER BY pr.created_at DESC, pr.public_id
         LIMIT :limit
        """;

    private static final String COUNT_PROJECTS = PLACE_PROJECT_MATCHES + """
        SELECT count(*) FROM matched m JOIN visible_project pr ON pr.id = m.project_id
        """;

    // このあたりの場所：保存した関係（1段）でこの場所の中に入る場所のうち、公開の発見か公開の会話があるもの
    private static final String FIND_NEARBY = """
        WITH children AS (
            SELECT DISTINCT r.to_place_id AS id
              FROM place_relation r
             WHERE r.from_place_id = :placeId AND r.kind IN (:kinds) AND r.to_place_id <> :placeId
        )
        SELECT p.public_id, p.name,
               (SELECT array_agg(k.kind ORDER BY k.kind COLLATE "C") FROM place_kind k WHERE k.place_id = p.id) AS kinds,
               count(*) OVER () AS total
          FROM children ch
          JOIN place p ON p.id = ch.id
         WHERE EXISTS (SELECT 1 FROM discovery_place dp
                         JOIN visible_discovery d ON d.id = dp.discovery_id
                        WHERE dp.place_id = p.id AND dp.status = :active
                          AND NOT EXISTS (SELECT 1 FROM discovery_relation m
                                           WHERE m.from_discovery_id = d.id AND m.kind = :merged AND m.status = :active))
            OR EXISTS (SELECT 1 FROM conversation_place cp
                         JOIN visible_conversation c ON c.id = cp.conversation_id
                        WHERE cp.place_id = p.id)
         ORDER BY p.name COLLATE "C", p.public_id
         LIMIT :limit
        """;

    public record PlaceRow(long id, String publicId, String name, String spatialType, String status,
            LocalDate existedFrom, LocalDate existedTo, String geoJson, List<String> kinds,
            String stationPublicId, String stationName) {}

    public record MergeRow(int depth, String targetPublicId, String targetName, boolean hasNext) {}

    public record AliasRow(String name, String kind, LocalDate validFrom, LocalDate validTo) {}

    public record PlaceRefRow(String publicId, String name) {}

    public record EstimateRow(String metric, double value, String unit, String method, String methodVersion,
            OffsetDateTime computedAt) {}

    public record DiscoveryRow(long id, String publicId, String title, String summary, List<String> roles,
            String aspect, int total) {}

    /**
     * 探している話の候補の1行。対象（isQuestion と targetId）ごとに、対象の並びの順・候補の作成の順に並ぶ。
     * conversationId はカードに出す会話（問いなら問いが属する会話）。total は対象の全体の数。
     */
    public record SeekingCandidateRow(String publicId, String proposedName, boolean isQuestion, long targetId,
            long conversationId, String questionPublicId, String questionContent, int total) {}

    public record NearbyRow(String publicId, String name, List<String> kinds, int total) {}

    /** ページの一覧の上限（OpenAPI の maxItems。固定）。 */
    static final int DISCOVERY_LIMIT = 30;
    static final int LIST_LIMIT = 10;
    static final int NEARBY_LIMIT = 20;

    /** 階層・統合をたどる深さの上限。 */
    static final int BREADCRUMB_MAX_DEPTH = 10;
    private static final int MAX_MERGE_DEPTH = 20;

    private static final int GEOJSON_DECIMALS = 7;
    private static final String ACTIVE = "ACTIVE";
    private static final String MERGED = "MERGED";

    private final JdbcClient jdbcClient;

    public PlaceQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<PlaceRow> findPlace(String publicId) {
        return jdbcClient.sql(FIND_PLACE)
            .param("publicId", publicId)
            .param("decimals", GEOJSON_DECIMALS)
            .query((rs, rowNum) -> new PlaceRow(
                rs.getLong("id"), rs.getString("public_id"), rs.getString("name"), rs.getString("spatial_type"),
                rs.getString("status"), rs.getObject("existed_from", LocalDate.class),
                rs.getObject("existed_to", LocalDate.class), rs.getString("geojson"), toStringList(rs.getArray("kinds")),
                rs.getString("station_public_id"), rs.getString("station_name")))
            .optional();
    }

    public MergeRow findFinalMergeTarget(long placeId) {
        return jdbcClient.sql(FIND_FINAL_MERGE_TARGET)
            .param("placeId", placeId)
            .param("maxDepth", MAX_MERGE_DEPTH)
            .query((rs, rowNum) -> new MergeRow(
                rs.getInt("depth"), rs.getString("public_id"), rs.getString("name"), rs.getBoolean("has_next")))
            .single();
    }

    public List<AliasRow> findAliases(long placeId) {
        return jdbcClient.sql(FIND_ALIASES)
            .param("placeId", placeId)
            .param("kinds", List.of("ALIAS", "FORMER"))
            .query((rs, rowNum) -> new AliasRow(
                rs.getString("name"), rs.getString("kind"),
                rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_to", LocalDate.class)))
            .list();
    }

    public List<PlaceRefRow> findBreadcrumb(long placeId) {
        return jdbcClient.sql(FIND_BREADCRUMB)
            .param("placeId", placeId)
            .param("kinds", List.of("ADMINISTRATIVE", "CONTAINS"))
            .param("administrative", "ADMINISTRATIVE")
            .param("maxDepth", BREADCRUMB_MAX_DEPTH)
            .query((rs, rowNum) -> new PlaceRefRow(rs.getString("public_id"), rs.getString("name")))
            .list();
    }

    public List<EstimateRow> findEstimates(long placeId) {
        return jdbcClient.sql(FIND_ESTIMATES)
            .param("placeId", placeId)
            .query((rs, rowNum) -> new EstimateRow(
                rs.getString("metric"), rs.getDouble("value"), rs.getString("unit"), rs.getString("method"),
                rs.getString("method_version"), rs.getObject("computed_at", OffsetDateTime.class)))
            .list();
    }

    public List<DiscoveryRow> findDiscoveries(long placeId) {
        return jdbcClient.sql(FIND_DISCOVERIES)
            .param("placeId", placeId)
            .param("active", ACTIVE)
            .param("merged", MERGED)
            .param("aspect", "ASPECT")
            .param("limit", DISCOVERY_LIMIT)
            .query((rs, rowNum) -> new DiscoveryRow(
                rs.getLong("id"), rs.getString("public_id"), rs.getString("title"), rs.getString("summary"),
                toStringList(rs.getArray("roles")), rs.getString("aspect"), rs.getInt("total")))
            .list();
    }

    /** 探している話の、上位 {@link #LIST_LIMIT} 件の対象の候補（並びは {@code FIND_SEEKING}）。 */
    public List<SeekingCandidateRow> findSeekingCandidates(long placeId, boolean isArea) {
        return jdbcClient.sql(FIND_SEEKING)
            .param("placeId", placeId)
            .param("isArea", isArea)
            .param("considering", "CONSIDERING")
            .param("active", ACTIVE)
            .param("adoptedStatuses", List.of("ADOPTED", "RECONSIDERING"))
            .param("limit", LIST_LIMIT)
            .query((rs, rowNum) -> new SeekingCandidateRow(
                rs.getString("public_id"), rs.getString("proposed_name"), rs.getBoolean("is_question"),
                rs.getLong("target_id"), rs.getLong("conversation_id"), rs.getString("question_public_id"),
                rs.getString("question_content"), rs.getInt("total")))
            .list();
    }

    /** この場所の会話の識別子（会話の開始の時点の新しい順、最大 {@link #LIST_LIMIT} 件）。 */
    public List<Long> findConversationIds(long placeId) {
        return jdbcClient.sql(FIND_CONVERSATION_IDS)
            .param("placeId", placeId)
            .param("limit", LIST_LIMIT)
            .query((rs, rowNum) -> rs.getLong("id"))
            .list();
    }

    public int countConversations(long placeId) {
        return jdbcClient.sql(COUNT_CONVERSATIONS)
            .param("placeId", placeId)
            .query(Integer.class)
            .single();
    }

    public List<ProjectCards.ProjectRow> findProjects(long placeId) {
        return jdbcClient.sql(FIND_PROJECTS)
            .param("placeId", placeId)
            .param("active", ACTIVE)
            .param("limit", LIST_LIMIT)
            .query((rs, rowNum) -> new ProjectCards.ProjectRow(
                rs.getString("public_id"), rs.getString("title"), rs.getString("type"),
                rs.getString("creator_kind"), rs.getString("creator_name"),
                (Integer) rs.getObject("discovery_position"), (Integer) rs.getObject("place_position")))
            .list();
    }

    public int countProjects(long placeId) {
        return jdbcClient.sql(COUNT_PROJECTS)
            .param("placeId", placeId)
            .param("active", ACTIVE)
            .query(Integer.class)
            .single();
    }

    public List<NearbyRow> findNearby(long placeId) {
        return jdbcClient.sql(FIND_NEARBY)
            .param("placeId", placeId)
            .param("kinds", List.of("ADMINISTRATIVE", "CONTAINS", "AREA"))
            .param("active", ACTIVE)
            .param("merged", MERGED)
            .param("limit", NEARBY_LIMIT)
            .query((rs, rowNum) -> new NearbyRow(
                rs.getString("public_id"), rs.getString("name"), toStringList(rs.getArray("kinds")), rs.getInt("total")))
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
