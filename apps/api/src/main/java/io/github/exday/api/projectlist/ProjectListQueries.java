package io.github.exday.api.projectlist;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * S17（企画の一覧）が読む SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする（apps/api/AGENTS.md）。
 * 公開の企画（visible_project）を、作った時点の降順、同じなら識別子（文字コード）の降順に並べる。
 */
@Repository
public class ProjectListQueries {

    // 項目の数は、指す先が見えない項目も位置として数える（S15 の並びと同じ）
    private static final String FIND_PROJECTS = """
        SELECT pr.id, pr.public_id, pr.title, pr.call_text, pr.type, pr.creator_kind, up.nickname,
               (SELECT count(*) FROM project_item pi WHERE pi.project_id = pr.id) AS item_count
          FROM visible_project pr
          JOIN user_profile up ON up.user_id = pr.creator_user_id
         WHERE true %s
         ORDER BY pr.created_at DESC, pr.public_id COLLATE "C" DESC
         LIMIT :limit
        """;

    // 条件はつなげて WHERE に足す。どれも先頭に空白を置き、前の条件の名前とくっつかないようにする
    private static final String BY_TYPE = " AND pr.type = :type";
    private static final String BY_CREATOR = " AND pr.creator_kind = :creatorKind";
    private static final String AFTER_KEY = """
         AND (pr.created_at < :afterAt OR (pr.created_at = :afterAt AND pr.public_id COLLATE "C" < :afterPublicId))""";

    // カーソルの企画は、公開かどうかを問わず並びのキーとして使う（読んでいる間に非公開になっても続きを読める）
    private static final String FIND_KEY = """
        SELECT pr.created_at FROM project pr WHERE pr.public_id = :publicId
        """;

    record ProjectRow(long id, String publicId, String title, String callText, String type, String creatorKind,
            String creatorName, int itemCount) {}

    record Key(OffsetDateTime at, String publicId) {}

    private final JdbcClient jdbcClient;

    public ProjectListQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 条件に合う公開の企画を、キーより後から最大 {@code limit} 件。{@code type}・{@code creatorKind} は DB の値か null。 */
    public List<ProjectRow> findProjects(String type, String creatorKind, Key after, int limit) {
        var conditions = (type == null ? "" : BY_TYPE) + (creatorKind == null ? "" : BY_CREATOR)
            + (after == null ? "" : " " + AFTER_KEY);
        var spec = jdbcClient.sql(FIND_PROJECTS.formatted(conditions)).param("limit", limit);
        if (type != null) {
            spec = spec.param("type", type);
        }
        if (creatorKind != null) {
            spec = spec.param("creatorKind", creatorKind);
        }
        if (after != null) {
            spec = spec.param("afterAt", after.at()).param("afterPublicId", after.publicId());
        }
        return spec.query((rs, rowNum) -> new ProjectRow(rs.getLong("id"), rs.getString("public_id"),
                rs.getString("title"), rs.getString("call_text"), rs.getString("type"), rs.getString("creator_kind"),
                rs.getString("nickname"), rs.getInt("item_count")))
            .list();
    }

    public Optional<Key> findKey(String publicId) {
        return jdbcClient.sql(FIND_KEY)
            .param("publicId", publicId)
            .query((rs, rowNum) -> new Key(rs.getObject("created_at", OffsetDateTime.class), publicId))
            .optional();
    }
}
