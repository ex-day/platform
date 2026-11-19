package io.github.exday.api.top;

import io.github.exday.api.card.project.ProjectListCards.ProjectRow;
import io.github.exday.api.openquestion.QuestionAnswers;
import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * S01（TOP）が読む SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする（apps/api/AGENTS.md）。
 *
 * <p>「近く」は起点の場所の形で分ける（画面定義 S01「取得用 API で返すもの（案）」）。POINT・ROUTE は起点の形から直線で
 * {@code radius}（3km）以内、AREA は起点の形と交わる（{@code radius} を 0 にして {@code ST_DWithin} で判定する）。
 * 距離は起点の形と対象の形の最短の距離。代表の1点には置き換えない。
 */
@Repository
public class TopQueries {

    // ---- 起点 ----

    private static final String FIND_PLACE_ID = """
        SELECT p.id FROM place p WHERE p.public_id = :publicId
        """;

    private static final String FIND_ORIGIN = """
        SELECT p.id, p.public_id, p.name, p.spatial_type,
               EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = p.id AND k.kind = :municipality) AS municipality
          FROM place p
         WHERE p.id = :originId
        """;

    // 起点を含む上位の場所：場所どうしの関係を親へたどった場所（depth はいちばん近い段）と、形が起点の形を覆う区域
    private static final String FIND_UPPER = """
        WITH RECURSIVE up (id, depth, path) AS (
            SELECT CAST(:originId AS bigint), 0, ARRAY[CAST(:originId AS bigint)]
            UNION ALL
            SELECT r.from_place_id, up.depth + 1, up.path || r.from_place_id
              FROM up
              JOIN place_relation r ON r.to_place_id = up.id AND r.kind IN (:upKinds)
             WHERE NOT r.from_place_id = ANY (up.path)
               AND up.depth < :maxDepth
        ), related AS (
            SELECT id, min(depth) AS depth FROM up WHERE depth > 0 GROUP BY id
        ), covering AS (
            SELECT p.id
              FROM place p, place o
             WHERE o.id = :originId AND p.id <> o.id AND p.spatial_type = :area AND ST_Covers(p.geom, o.geom)
        )
        SELECT p.id, p.public_id, p.name, r.depth, c.id IS NOT NULL AS covers,
               EXISTS (SELECT 1 FROM place_kind k WHERE k.place_id = p.id AND k.kind = :municipality) AS municipality,
               ST_Area(p.geom) AS area
          FROM place p
          LEFT JOIN related r ON r.id = p.id
          LEFT JOIN covering c ON c.id = p.id
         WHERE r.id IS NOT NULL OR c.id IS NOT NULL
        """;

    // ---- このあたりの数字 ----

    private static final String COUNT_PLACES = """
        SELECT count(*)
          FROM place p, (SELECT geom FROM place WHERE id = :originId) o
         WHERE p.status = :activePlace AND p.id NOT IN (:excluded) AND ST_DWithin(p.geom, o.geom, :radius)
        """;

    // 声のある発見：由来に公開の会話・公開の Post に届くものを持つ、公開の Discovery（統合された側でない）。1件ずつ数える
    private static final String COUNT_VOICED = """
        SELECT count(*)
          FROM visible_discovery d, (SELECT geom FROM place WHERE id = :originId) o
         WHERE NOT EXISTS (SELECT 1 FROM discovery_relation m
                            WHERE m.from_discovery_id = d.id AND m.kind = :merged AND m.status = :active)
           AND EXISTS (SELECT 1 FROM discovery_place dp JOIN place p ON p.id = dp.place_id
                        WHERE dp.discovery_id = d.id AND dp.status = :active AND ST_DWithin(p.geom, o.geom, :radius))
           AND EXISTS (SELECT 1 FROM discovery_origin og
                        WHERE og.discovery_id = d.id
                          AND (og.conversation_id IN (SELECT c.id FROM visible_conversation c)
                               OR og.post_id IN (SELECT vp.id FROM visible_post vp)))
        """;

    // ---- Discovery の枠 ----

    // 公開で統合された側でない Discovery を、見出しの場所（関わる場所のうち起点にいちばん近いもの）の距離の順に。
    // 1つ目の %s は WITH 句（同じ行政の地域の段だけ）、2つ目の %s は枠ごとの条件
    private static final String FIND_DISCOVERIES = """
        %s
        SELECT d.id, d.public_id, d.title, d.summary, n.place_public_id, n.place_name, n.distance,
               EXISTS (SELECT 1 FROM discovery_origin og WHERE og.discovery_id = d.id AND og.kind = :operator) AS is_example
          FROM visible_discovery d
         CROSS JOIN (SELECT geom FROM place WHERE id = :originId) o
         CROSS JOIN LATERAL (
              SELECT p.public_id AS place_public_id, p.name AS place_name, ST_Distance(p.geom, o.geom) AS distance
                FROM discovery_place dp
                JOIN place p ON p.id = dp.place_id
               WHERE dp.discovery_id = d.id AND dp.status = :active
               ORDER BY ST_Distance(p.geom, o.geom), p.public_id COLLATE "C"
               LIMIT 1
         ) n
         WHERE d.id NOT IN (:usedIds)
           AND NOT EXISTS (SELECT 1 FROM discovery_relation m
                            WHERE m.from_discovery_id = d.id AND m.kind = :merged AND m.status = :active)
           %s
         ORDER BY n.distance, d.public_id COLLATE "C"
         LIMIT :limit
        """;

    // 見出しの場所が近ければ、関わる場所のどれかが近い（いちばん近い場所で判定する）
    private static final String NEAR = " AND n.distance <= :radius";
    private static final String SEASONAL = """
         AND EXISTS (SELECT 1 FROM discovery_value v JOIN value_condition vc ON vc.value_id = v.id
                      WHERE v.discovery_id = d.id AND v.status = :active AND vc.axis = :seasonAxis AND vc.season = :season)""";
    private static final String EXAMPLE = """
         AND EXISTS (SELECT 1 FROM discovery_origin og WHERE og.discovery_id = d.id AND og.kind = :operator)""";
    // 同じ行政の地域：その場所の形と交わる、または関係で子孫にあたる場所を持つ
    private static final String ADMIN_AREA_WITH = """
        WITH RECURSIVE admin_down (id, depth, path) AS (
            SELECT CAST(:adminId AS bigint), 0, ARRAY[CAST(:adminId AS bigint)]
            UNION ALL
            SELECT r.to_place_id, a.depth + 1, a.path || r.to_place_id
              FROM admin_down a
              JOIN place_relation r ON r.from_place_id = a.id AND r.kind IN (:upKinds)
             WHERE NOT r.to_place_id = ANY (a.path)
               AND a.depth < :maxDepth
        )""";
    private static final String IN_ADMIN_AREA = """
         AND EXISTS (SELECT 1 FROM discovery_place dp JOIN place p ON p.id = dp.place_id
                      WHERE dp.discovery_id = d.id AND dp.status = :active
                        AND (p.id IN (SELECT id FROM admin_down)
                             OR ST_Intersects(p.geom, (SELECT geom FROM place WHERE id = :adminId))))""";

    private static final String FIND_ANY = FIND_DISCOVERIES.formatted("", "");
    private static final String FIND_NEAR = FIND_DISCOVERIES.formatted("", NEAR);
    private static final String FIND_SEASONAL_NEAR = FIND_DISCOVERIES.formatted("", SEASONAL + NEAR);
    private static final String FIND_SEASONAL_ADMIN = FIND_DISCOVERIES.formatted(ADMIN_AREA_WITH, SEASONAL + IN_ADMIN_AREA);
    private static final String FIND_SEASONAL_ANY = FIND_DISCOVERIES.formatted("", SEASONAL);
    private static final String FIND_EXAMPLES = FIND_DISCOVERIES.formatted("", EXAMPLE);

    // 今の季節に合う Value（複数あれば作った時点の古いもの）
    private static final String FIND_SEASONAL_VALUES = """
        SELECT DISTINCT ON (v.discovery_id) v.discovery_id, v.public_id, v.description
          FROM discovery_value v
          JOIN value_condition vc ON vc.value_id = v.id
         WHERE v.discovery_id IN (:ids) AND v.status = :active AND vc.axis = :seasonAxis AND vc.season = :season
         ORDER BY v.discovery_id, v.created_at, v.public_id COLLATE "C"
        """;

    // ---- 企画 ----

    private static final String FIND_PROJECTS = """
        SELECT pr.id, pr.public_id, pr.title, pr.call_text, pr.type, pr.creator_kind, up.nickname
          FROM visible_project pr
          JOIN user_profile up ON up.user_id = pr.creator_user_id
         ORDER BY pr.created_at DESC, pr.public_id COLLATE "C" DESC
         LIMIT :limit
        """;

    // ---- 番付 ----

    // 近くの場所（起点自身と上位の場所を除く）の推定の値。1つの場所・指標に出どころが複数あれば、出どころの名前の順で最初のもの
    // （その出どころの中は、いちばん新しく計算したもの）。指標は名前の順、指標の中は値の大きい順
    private static final String FIND_ESTIMATES = """
        WITH latest AS (
            SELECT DISTINCT ON (e.place_id, e.metric)
                   e.place_id, e.metric, e.value, e.unit, e.method, e.method_version, e.computed_at
              FROM place_estimate e
              JOIN place p ON p.id = e.place_id
             CROSS JOIN (SELECT geom FROM place WHERE id = :originId) o
             WHERE p.status = :activePlace AND p.id NOT IN (:excluded) AND ST_DWithin(p.geom, o.geom, :radius)
             ORDER BY e.place_id, e.metric, e.method COLLATE "C", e.computed_at DESC, e.id DESC
        ), ranked AS (
            SELECT l.*, p.public_id, p.name,
                   row_number() OVER (PARTITION BY l.metric ORDER BY l.value DESC, p.public_id COLLATE "C") AS rn,
                   dense_rank() OVER (ORDER BY l.metric COLLATE "C") AS metric_rank
              FROM latest l
              JOIN place p ON p.id = l.place_id
        )
        SELECT * FROM ranked
         WHERE metric_rank <= :metricLimit AND rn <= :perMetric
         ORDER BY metric_rank, rn
        """;

    // ---- 探しています ----

    // 答えの出ていない「どこ」の問い（QuestionAnswers）と、場所の決まっていない会話の検討中の候補。会話ごとに1件。
    // 近くに候補を持つ会話を先に（いちばん近い候補の距離の順）、ほかは会話の作った時点の新しい順。
    // 会話の行（T）と、その会話の問い（Q）・候補（C）の行を返す
    private static final String FIND_SEEKING = """
        WITH o AS (SELECT geom FROM place WHERE id = :originId),
        uq AS (
            SELECT q.id, q.conversation_id, q.public_id, q.content, q.created_at
              FROM visible_open_question q
             WHERE q.kind = :whereKind AND q.status = :activeQuestion
               AND NOT EXISTS (SELECT 1 FROM place_adoption a
                                WHERE a.open_question_id = q.id AND a.status IN (:activeAdoption))
               AND NOT EXISTS (SELECT 1 FROM question_answer qa
                                WHERE qa.question_id = q.id AND qa.status IN (:activeAdoption))
        ), cand AS (
            SELECT pc.public_id, coalesce(pc.conversation_id, uq.conversation_id) AS conversation_id, pc.proposed_name,
                   p.public_id AS place_public_id, p.name AS place_name,
                   ST_Distance(coalesce(p.geom, pc.proposed_geom), o.geom) AS distance
              FROM visible_place_candidate pc
             CROSS JOIN o
              LEFT JOIN uq ON uq.id = pc.open_question_id
              LEFT JOIN place p ON p.id = pc.place_id
             WHERE pc.status = :considering
               AND ((pc.conversation_id IS NOT NULL
                     AND NOT EXISTS (SELECT 1 FROM conversation_place cp WHERE cp.conversation_id = pc.conversation_id))
                    OR uq.id IS NOT NULL)
        ), targets AS (
            SELECT c.id, c.public_id, c.created_at,
                   (SELECT min(cand.distance) FROM cand WHERE cand.conversation_id = c.id) AS min_distance
              FROM visible_conversation c
             WHERE EXISTS (SELECT 1 FROM uq WHERE uq.conversation_id = c.id)
                OR EXISTS (SELECT 1 FROM cand WHERE cand.conversation_id = c.id)
        ), picked AS (
            SELECT t.id, CASE WHEN t.min_distance <= :radius THEN t.min_distance END AS near_distance,
                   row_number() OVER (ORDER BY CASE WHEN t.min_distance <= :radius THEN 0 ELSE 1 END,
                                               CASE WHEN t.min_distance <= :radius THEN t.min_distance END,
                                               t.created_at DESC, t.public_id COLLATE "C") AS rank
              FROM targets t
        )
        SELECT pk.rank, 'T' AS item, 0 AS ord, pk.id AS conversation_id, pk.near_distance,
               CAST(NULL AS text) AS public_id, CAST(NULL AS text) AS content,
               CAST(NULL AS text) AS place_public_id, CAST(NULL AS text) AS place_name, CAST(NULL AS text) AS proposed_name
          FROM picked pk
         WHERE pk.rank <= :limit
        UNION ALL
        SELECT pk.rank, 'Q', q.ord, pk.id, pk.near_distance, q.public_id, q.content, NULL, NULL, NULL
          FROM picked pk
         CROSS JOIN LATERAL (
              SELECT uq.public_id, uq.content,
                     row_number() OVER (ORDER BY uq.created_at, uq.public_id COLLATE "C") AS ord
                FROM uq
               WHERE uq.conversation_id = pk.id
               ORDER BY uq.created_at, uq.public_id COLLATE "C"
               LIMIT :perCard
         ) q
         WHERE pk.rank <= :limit
        UNION ALL
        SELECT pk.rank, 'C', ca.ord, pk.id, pk.near_distance, ca.public_id, NULL, ca.place_public_id, ca.place_name,
               ca.proposed_name
          FROM picked pk
         CROSS JOIN LATERAL (
              SELECT cand.public_id, cand.place_public_id, cand.place_name, cand.proposed_name,
                     row_number() OVER (ORDER BY cand.distance NULLS LAST, cand.public_id COLLATE "C") AS ord
                FROM cand
               WHERE cand.conversation_id = pk.id
               ORDER BY cand.distance NULLS LAST, cand.public_id COLLATE "C"
               LIMIT :perCard
         ) ca
         WHERE pk.rank <= :limit
         ORDER BY 1, 2 DESC, 3
        """;

    // ---- 声を待っている ----

    private static final String FIND_AWAITING = """
        SELECT p.public_id, p.name,
               (SELECT array_agg(k.kind ORDER BY k.kind COLLATE "C") FROM place_kind k WHERE k.place_id = p.id) AS kinds
          FROM place p
         CROSS JOIN (SELECT geom FROM place WHERE id = :originId) o
         WHERE p.status = :activePlace AND p.id NOT IN (:excluded) AND p.id NOT IN (:usedPlaces)
           AND ST_DWithin(p.geom, o.geom, :radius)
           AND NOT EXISTS (SELECT 1 FROM conversation_place cp JOIN visible_conversation c ON c.id = cp.conversation_id
                            WHERE cp.place_id = p.id)
           AND NOT EXISTS (SELECT 1 FROM discovery_place dp JOIN visible_discovery d ON d.id = dp.discovery_id
                            WHERE dp.place_id = p.id AND dp.status = :active)
         ORDER BY ST_Distance(p.geom, o.geom), p.public_id COLLATE "C"
         LIMIT :limit
        """;

    /** 場所の種類「市区町村」（行政の地域）。 */
    static final String MUNICIPALITY = "市区町村";
    /** 上位の場所をたどる関係の種類（行政の階層・地理的な包含・観光や生活のエリア）。 */
    static final List<String> UP_KINDS = List.of("ADMINISTRATIVE", "CONTAINS", "AREA");
    static final int MAX_DEPTH = 20;
    /** 使っていない Discovery・場所の識別子の一覧を空にしないための値（IN の一覧を空にできないため）。 */
    private static final long NONE = -1L;

    record Origin(long id, String publicId, String name, String spatialType, boolean municipality) {}

    record Upper(long id, String publicId, String name, Integer depth, boolean covers, boolean municipality,
            double area) {}

    record DiscoveryRow(long id, String publicId, String title, String summary, String placePublicId,
            String placeName, double distance, boolean example) {}

    record ValueRow(String publicId, String description) {}

    record EstimateRow(long placeId, String placePublicId, String placeName, String metric, double value, String unit,
            String method, String methodVersion, OffsetDateTime computedAt, int metricRank, int rank) {}

    record SeekingRow(int rank, String item, long conversationId, Double nearDistance, String publicId, String content,
            String placePublicId, String placeName, String proposedName) {}

    record AwaitingRow(String publicId, String name, List<String> kinds) {}

    private final JdbcClient jdbcClient;

    public TopQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Long> findPlaceId(String publicId) {
        return jdbcClient.sql(FIND_PLACE_ID).param("publicId", publicId).query(Long.class).optional();
    }

    public Origin findOrigin(long originId) {
        return jdbcClient.sql(FIND_ORIGIN)
            .param("originId", originId)
            .param("municipality", MUNICIPALITY)
            .query((rs, rowNum) -> new Origin(rs.getLong("id"), rs.getString("public_id"), rs.getString("name"),
                rs.getString("spatial_type"), rs.getBoolean("municipality")))
            .single();
    }

    public List<Upper> findUpper(long originId) {
        return jdbcClient.sql(FIND_UPPER)
            .param("originId", originId)
            .param("upKinds", UP_KINDS)
            .param("maxDepth", MAX_DEPTH)
            .param("area", "AREA")
            .param("municipality", MUNICIPALITY)
            .query((rs, rowNum) -> new Upper(rs.getLong("id"), rs.getString("public_id"), rs.getString("name"),
                (Integer) rs.getObject("depth", Integer.class), rs.getBoolean("covers"), rs.getBoolean("municipality"),
                rs.getDouble("area")))
            .list();
    }

    public int countPlaces(long originId, double radius, Collection<Long> excluded) {
        return jdbcClient.sql(COUNT_PLACES)
            .param("originId", originId)
            .param("radius", radius)
            .param("excluded", List.copyOf(excluded))
            .param("activePlace", "ACTIVE")
            .query(Integer.class).single();
    }

    public int countVoiced(long originId, double radius) {
        return jdbcClient.sql(COUNT_VOICED)
            .param("originId", originId)
            .param("radius", radius)
            .param("merged", "MERGED")
            .param("active", "ACTIVE")
            .query(Integer.class).single();
    }

    /** 公開の Discovery（使っていないもの）を、起点にいちばん近いものから {@code limit} 件。 */
    public List<DiscoveryRow> findAny(long originId, Collection<Long> used, int limit) {
        return discoveries(FIND_ANY, originId, used, limit, 0, null, null);
    }

    public List<DiscoveryRow> findNear(long originId, double radius, Collection<Long> used, int limit) {
        return discoveries(FIND_NEAR, originId, used, limit, radius, null, null);
    }

    public List<DiscoveryRow> findSeasonalNear(long originId, double radius, String season, Collection<Long> used,
            int limit) {
        return discoveries(FIND_SEASONAL_NEAR, originId, used, limit, radius, season, null);
    }

    public List<DiscoveryRow> findSeasonalInAdminArea(long originId, long adminId, String season,
            Collection<Long> used, int limit) {
        return discoveries(FIND_SEASONAL_ADMIN, originId, used, limit, 0, season, adminId);
    }

    public List<DiscoveryRow> findSeasonalAny(long originId, String season, Collection<Long> used, int limit) {
        return discoveries(FIND_SEASONAL_ANY, originId, used, limit, 0, season, null);
    }

    public List<DiscoveryRow> findExamples(long originId, Collection<Long> used, int limit) {
        return discoveries(FIND_EXAMPLES, originId, used, limit, 0, null, null);
    }

    private List<DiscoveryRow> discoveries(String sql, long originId, Collection<Long> used, int limit, double radius,
            String season, Long adminId) {
        return jdbcClient.sql(sql)
            .param("originId", originId)
            .param("usedIds", withNone(used))
            .param("limit", limit)
            .param("operator", "OPERATOR")
            .param("merged", "MERGED")
            .param("active", "ACTIVE")
            // 枠によって使わないパラメータもある（使わない名前は無視される）
            .param("radius", radius)
            .param("season", season)
            .param("seasonAxis", "SEASON")
            .param("adminId", adminId)
            .param("upKinds", UP_KINDS)
            .param("maxDepth", MAX_DEPTH)
            .query((rs, rowNum) -> new DiscoveryRow(rs.getLong("id"), rs.getString("public_id"),
                rs.getString("title"), rs.getString("summary"), rs.getString("place_public_id"),
                rs.getString("place_name"), rs.getDouble("distance"), rs.getBoolean("is_example")))
            .list();
    }

    /** Discovery ごとの、今の季節に合う Value。 */
    public Map<Long, ValueRow> findSeasonalValues(Collection<Long> discoveryIds, String season) {
        if (discoveryIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(FIND_SEASONAL_VALUES)
            .param("ids", List.copyOf(discoveryIds))
            .param("active", "ACTIVE")
            .param("seasonAxis", "SEASON")
            .param("season", season)
            .query((rs, rowNum) -> Map.entry(rs.getLong("discovery_id"),
                new ValueRow(rs.getString("public_id"), rs.getString("description"))))
            .list().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<ProjectRow> findProjects(int limit) {
        return jdbcClient.sql(FIND_PROJECTS)
            .param("limit", limit)
            .query((rs, rowNum) -> new ProjectRow(
                rs.getLong("id"), rs.getString("public_id"), rs.getString("title"), rs.getString("call_text"),
                rs.getString("type"), rs.getString("creator_kind"), rs.getString("nickname")))
            .list();
    }

    /** 指標は名前の順に {@code metricLimit} 個まで、指標の中は値の大きい順に {@code perMetric} 件まで。 */
    public List<EstimateRow> findEstimates(long originId, double radius, Collection<Long> excluded, int metricLimit,
            int perMetric) {
        return jdbcClient.sql(FIND_ESTIMATES)
            .param("originId", originId)
            .param("radius", radius)
            .param("excluded", List.copyOf(excluded))
            .param("activePlace", "ACTIVE")
            .param("metricLimit", metricLimit)
            .param("perMetric", perMetric)
            .query((rs, rowNum) -> new EstimateRow(rs.getLong("place_id"), rs.getString("public_id"),
                rs.getString("name"), rs.getString("metric"), rs.getDouble("value"), rs.getString("unit"),
                rs.getString("method"), rs.getString("method_version"),
                rs.getObject("computed_at", OffsetDateTime.class), rs.getInt("metric_rank"), rs.getInt("rn")))
            .list();
    }

    /** 探していますの会話を上位 {@code limit} 件。会話ごとに問い・候補を {@code perCard} 件まで。 */
    public List<SeekingRow> findSeeking(long originId, double radius, int limit, int perCard) {
        return jdbcClient.sql(FIND_SEEKING)
            .param("originId", originId)
            .param("radius", radius)
            .param("limit", limit)
            .param("perCard", perCard)
            .param("whereKind", "WHERE")
            .param("activeQuestion", "ACTIVE")
            .param("activeAdoption", QuestionAnswers.ACTIVE_ADOPTION)
            .param("considering", "CONSIDERING")
            .query((rs, rowNum) -> new SeekingRow(rs.getInt("rank"), rs.getString("item"), rs.getLong("conversation_id"),
                (Double) rs.getObject("near_distance", Double.class), rs.getString("public_id"), rs.getString("content"),
                rs.getString("place_public_id"), rs.getString("place_name"), rs.getString("proposed_name")))
            .list();
    }

    public List<AwaitingRow> findAwaiting(long originId, double radius, Collection<Long> excluded,
            Collection<Long> usedPlaces, int limit) {
        return jdbcClient.sql(FIND_AWAITING)
            .param("originId", originId)
            .param("radius", radius)
            .param("excluded", List.copyOf(excluded))
            .param("usedPlaces", withNone(usedPlaces))
            .param("activePlace", "ACTIVE")
            .param("active", "ACTIVE")
            .param("limit", limit)
            .query((rs, rowNum) -> new AwaitingRow(rs.getString("public_id"), rs.getString("name"),
                toStringList(rs.getArray("kinds"))))
            .list();
    }

    private static List<Long> withNone(Collection<Long> ids) {
        var list = new ArrayList<Long>(ids);
        list.add(NONE);
        return list;
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
