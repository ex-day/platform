package io.github.exday.api.project;

import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * S15（企画のページ）が読む SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする（apps/api/AGENTS.md）。
 *
 * <p>企画は公開のビュー（{@code visible_project}）で見えると確かめてから中身を読む。並びは {@code visible_project_item} を使わず
 * {@code project_item} を全部読み、指す先（Discovery）だけを公開の判定にかける（見えない項目も位置を残すため。DB 設計 2.5節）。
 * 同じ時点のものは識別子（public_id）の文字コードの順に並べる。
 */
@Repository
public class ProjectQueries {

    private static final String FIND_PROJECT = """
        SELECT pr.id, pr.public_id, pr.type, pr.title, pr.call_text, pr.description, pr.creator_kind, up.nickname
          FROM visible_project pr
          JOIN user_profile up ON up.user_id = pr.creator_user_id
         WHERE pr.public_id = :publicId
        """;

    private static final String FIND_ITEMS = """
        SELECT pi.public_id, pi.position, pi.note, pi.place_id, pi.discovery_id
          FROM project_item pi
         WHERE pi.project_id = :projectId
         ORDER BY pi.position
        """;

    // 場所の形と代表点（点はその点、経路は始点、エリアは形の中の1点）
    private static final String PLACE_COLUMNS = """
        SELECT p.id, p.public_id, p.name, p.spatial_type,
               ST_AsGeoJSON(p.geom::geometry, :decimals) AS geojson,
               (SELECT array_agg(k.kind ORDER BY k.kind COLLATE "C") FROM place_kind k WHERE k.place_id = p.id) AS kinds,
               ST_X(a.anchor) AS anchor_lon, ST_Y(a.anchor) AS anchor_lat
          FROM place p
          CROSS JOIN LATERAL (
              -- 点は ST_PointOnSurface でその点そのもの
              SELECT CASE p.spatial_type
                         WHEN 'ROUTE' THEN ST_StartPoint(ST_GeometryN(p.geom::geometry, 1))
                         ELSE ST_PointOnSurface(p.geom::geometry)
                     END AS anchor
          ) a
        """;

    private static final String FIND_PLACES_BY_ID = PLACE_COLUMNS + """
         WHERE p.id IN (:ids)
        """;

    private static final String FIND_PLACES_BY_PUBLIC_ID = PLACE_COLUMNS + """
         WHERE p.public_id IN (:publicIds)
        """;

    private static final String FIND_VISIBLE_DISCOVERIES = """
        SELECT d.id, d.public_id, d.title, d.summary FROM visible_discovery d WHERE d.id IN (:ids)
        """;

    private static final String COUNT_ORIGIN_CONVERSATIONS = """
        SELECT o.discovery_id, count(DISTINCT c.id) AS conversations
          FROM discovery_origin o
          JOIN visible_conversation c ON c.id = o.conversation_id
         WHERE o.discovery_id IN (:ids)
         GROUP BY o.discovery_id
        """;

    private static final String COUNT_STARTED_CONVERSATIONS = """
        SELECT c.entry_discovery_id AS discovery_id, count(*) AS conversations
          FROM visible_conversation c
         WHERE c.entry_discovery_id IN (:ids)
         GROUP BY c.entry_discovery_id
        """;

    // ---- タイムライン ----

    private static final String FIND_OWN_CONVERSATION = """
        SELECT c.id, c.public_id
          FROM visible_conversation c
         WHERE c.entry_project_id = :projectId AND c.is_project_own
         ORDER BY c.created_at, c.public_id COLLATE "C"
         LIMIT 1
        """;

    // 変更の出来事（kind_order 0）と、企画そのものの会話の Post（kind_order 1）を混ぜる。
    // 全順序：時点 → 種類（変更の出来事が先）→ 識別子。新しい側から選ぶ
    private static final String FIND_TIMELINE = """
        WITH entries AS (
            SELECT 0 AS kind_order, ch.id, ch.public_id, ch.changed_at AS at
              FROM visible_project_change ch
             WHERE ch.project_id = :projectId
            UNION ALL
            SELECT 1, p.id, p.public_id, p.posted_at
              FROM post p
             WHERE p.conversation_id = :ownConversationId
        )
        SELECT e.kind_order, e.id, e.public_id, e.at
          FROM entries e
         WHERE true %s
         ORDER BY e.at DESC, e.kind_order DESC, e.public_id COLLATE "C" DESC
         LIMIT :limit
        """;

    private static final String TIMELINE_BEFORE_KEY = """
        AND (e.at < :beforeAt
             OR (e.at = :beforeAt AND (e.kind_order < :beforeKind
                                       OR (e.kind_order = :beforeKind AND e.public_id COLLATE "C" < :beforePublicId))))""";

    private static final String FIND_CHANGE_KEY = """
        SELECT ch.changed_at AS at FROM visible_project_change ch WHERE ch.public_id = :publicId AND ch.project_id = :projectId
        """;

    private static final String FIND_POST_KEY = """
        SELECT p.posted_at AS at FROM post p WHERE p.public_id = :publicId AND p.conversation_id = :conversationId
        """;

    private static final String FIND_CHANGES = """
        SELECT ch.id, ch.public_id, ch.kind, ch.changed_at, ch.fields_changed FROM project_change ch WHERE ch.id IN (:ids)
        """;

    private static final String FIND_CHANGE_ITEMS = """
        SELECT ci.change_id, ci.kind, ci.before_position, ci.after_position, ci.place_id, ci.discovery_id
          FROM project_change_item ci
         WHERE ci.change_id IN (:ids)
         ORDER BY ci.change_id, ci.id
        """;

    // ---- 企画をきっかけにした会話（C40） ----

    private static final String FIND_TRIGGERED_CONVERSATION_IDS = """
        SELECT c.id
          FROM visible_conversation c
         WHERE c.entry_project_id = :projectId AND NOT c.is_project_own
         ORDER BY c.created_at DESC, c.public_id COLLATE "C"
         LIMIT :limit
        """;

    /** 企画をきっかけにした会話の上限（OpenAPI の ConversationCardPage の maxItems）。 */
    static final int TRIGGERED_LIMIT = 10;

    private static final int GEOJSON_DECIMALS = 7;

    record ProjectRow(long id, String publicId, String type, String title, String callText, String description,
            String creatorKind, String creatorName) {}

    record ItemRow(String publicId, int position, String note, Long placeId, Long discoveryId) {}

    record PlaceRow(long id, String publicId, String name, String spatialType, String geoJson, List<String> kinds,
            double anchorLon, double anchorLat) {}

    record DiscoveryRow(long id, String publicId, String title, String summary) {}

    record IdRef(long id, String publicId) {}

    /** タイムラインの1つ（change は変更の出来事、ほかは Post）。 */
    record TimelineRow(boolean change, long id, String publicId, OffsetDateTime at) {}

    /** タイムラインの並びのキー。 */
    record TimelineKey(OffsetDateTime at, boolean change, String publicId) {}

    record ChangeRow(long id, String publicId, String kind, OffsetDateTime changedAt, List<String> fieldsChanged) {}

    record ChangeItemRow(long changeId, String kind, Integer beforePosition, Integer afterPosition, Long placeId,
            Long discoveryId) {}

    private final JdbcClient jdbcClient;

    public ProjectQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<ProjectRow> findProject(String publicId) {
        return jdbcClient.sql(FIND_PROJECT)
            .param("publicId", publicId)
            .query((rs, rowNum) -> new ProjectRow(rs.getLong("id"), rs.getString("public_id"), rs.getString("type"),
                rs.getString("title"), rs.getString("call_text"), rs.getString("description"),
                rs.getString("creator_kind"), rs.getString("nickname")))
            .optional();
    }

    public List<ItemRow> findItems(long projectId) {
        return jdbcClient.sql(FIND_ITEMS)
            .param("projectId", projectId)
            .query((rs, rowNum) -> new ItemRow(rs.getString("public_id"), rs.getInt("position"), rs.getString("note"),
                (Long) rs.getObject("place_id", Long.class), (Long) rs.getObject("discovery_id", Long.class)))
            .list();
    }

    public Map<Long, PlaceRow> findPlaces(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        return places(jdbcClient.sql(FIND_PLACES_BY_ID).param("ids", List.copyOf(placeIds)))
            .stream().collect(Collectors.toMap(PlaceRow::id, row -> row));
    }

    public Map<String, PlaceRow> findPlacesByPublicId(Collection<String> publicIds) {
        if (publicIds.isEmpty()) {
            return Map.of();
        }
        return places(jdbcClient.sql(FIND_PLACES_BY_PUBLIC_ID).param("publicIds", List.copyOf(publicIds)))
            .stream().collect(Collectors.toMap(PlaceRow::publicId, row -> row));
    }

    private List<PlaceRow> places(JdbcClient.StatementSpec spec) {
        return spec.param("decimals", GEOJSON_DECIMALS)
            .query((rs, rowNum) -> new PlaceRow(rs.getLong("id"), rs.getString("public_id"), rs.getString("name"),
                rs.getString("spatial_type"), rs.getString("geojson"), strings(rs.getArray("kinds")),
                rs.getDouble("anchor_lon"), rs.getDouble("anchor_lat")))
            .list();
    }

    public Map<Long, DiscoveryRow> findVisibleDiscoveries(Collection<Long> discoveryIds) {
        if (discoveryIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(FIND_VISIBLE_DISCOVERIES)
            .param("ids", List.copyOf(discoveryIds))
            .query((rs, rowNum) -> new DiscoveryRow(rs.getLong("id"), rs.getString("public_id"), rs.getString("title"),
                rs.getString("summary")))
            .list().stream().collect(Collectors.toMap(DiscoveryRow::id, row -> row));
    }

    public Map<Long, Integer> countOriginConversations(Collection<Long> discoveryIds) {
        return counts(COUNT_ORIGIN_CONVERSATIONS, discoveryIds);
    }

    public Map<Long, Integer> countStartedConversations(Collection<Long> discoveryIds) {
        return counts(COUNT_STARTED_CONVERSATIONS, discoveryIds);
    }

    private Map<Long, Integer> counts(String sql, Collection<Long> discoveryIds) {
        if (discoveryIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(sql)
            .param("ids", List.copyOf(discoveryIds))
            .query((rs, rowNum) -> Map.entry(rs.getLong("discovery_id"), rs.getInt("conversations")))
            .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Optional<IdRef> findOwnConversation(long projectId) {
        return jdbcClient.sql(FIND_OWN_CONVERSATION)
            .param("projectId", projectId)
            .query((rs, rowNum) -> new IdRef(rs.getLong("id"), rs.getString("public_id")))
            .optional();
    }

    /** キーより前のタイムラインを新しい側から最大 {@code limit} 件（キーが null なら最新から）。 */
    public List<TimelineRow> findTimeline(long projectId, Long ownConversationId, TimelineKey before, int limit) {
        var spec = jdbcClient.sql(FIND_TIMELINE.formatted(before == null ? "" : TIMELINE_BEFORE_KEY))
            .param("projectId", projectId)
            .param("ownConversationId", ownConversationId == null ? -1L : ownConversationId)
            .param("limit", limit);
        if (before != null) {
            spec = spec.param("beforeAt", before.at())
                .param("beforeKind", before.change() ? 0 : 1)
                .param("beforePublicId", before.publicId());
        }
        return spec.query((rs, rowNum) -> new TimelineRow(rs.getInt("kind_order") == 0, rs.getLong("id"),
                rs.getString("public_id"), rs.getObject("at", OffsetDateTime.class)))
            .list();
    }

    /** 変更の出来事のカーソル：この企画の（公開の）出来事なら、その時点。 */
    public Optional<OffsetDateTime> findChangeKey(long projectId, String publicId) {
        return jdbcClient.sql(FIND_CHANGE_KEY)
            .param("projectId", projectId)
            .param("publicId", publicId)
            .query((rs, rowNum) -> rs.getObject("at", OffsetDateTime.class))
            .optional();
    }

    /** Post のカーソル：企画そのものの会話の Post なら、その時点。 */
    public Optional<OffsetDateTime> findPostKey(long conversationId, String publicId) {
        return jdbcClient.sql(FIND_POST_KEY)
            .param("conversationId", conversationId)
            .param("publicId", publicId)
            .query((rs, rowNum) -> rs.getObject("at", OffsetDateTime.class))
            .optional();
    }

    public Map<Long, ChangeRow> findChanges(Collection<Long> changeIds) {
        if (changeIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(FIND_CHANGES)
            .param("ids", List.copyOf(changeIds))
            .query((rs, rowNum) -> new ChangeRow(rs.getLong("id"), rs.getString("public_id"), rs.getString("kind"),
                rs.getObject("changed_at", OffsetDateTime.class), strings(rs.getArray("fields_changed"))))
            .list().stream().collect(Collectors.toMap(ChangeRow::id, row -> row));
    }

    public List<ChangeItemRow> findChangeItems(Collection<Long> changeIds) {
        if (changeIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(FIND_CHANGE_ITEMS)
            .param("ids", List.copyOf(changeIds))
            .query((rs, rowNum) -> new ChangeItemRow(rs.getLong("change_id"), rs.getString("kind"),
                (Integer) rs.getObject("before_position", Integer.class),
                (Integer) rs.getObject("after_position", Integer.class),
                (Long) rs.getObject("place_id", Long.class), (Long) rs.getObject("discovery_id", Long.class)))
            .list();
    }

    public List<Long> findTriggeredConversationIds(long projectId) {
        return jdbcClient.sql(FIND_TRIGGERED_CONVERSATION_IDS)
            .param("projectId", projectId)
            .param("limit", TRIGGERED_LIMIT)
            .query(Long.class)
            .list();
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        try {
            return List.of((String[]) array.getArray());
        } finally {
            array.free();
        }
    }
}
