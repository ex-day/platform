package io.github.exday.api.discovery;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * S03 のみんなの声（C26）が読む SQL。値は {@code JdbcClient} の {@code .param(...)} で全てバインドする
 * （apps/api/AGENTS.md）。
 */
@Repository
public class DiscoveryPostsQueries {

    /** 返信先の引用（{@code excerpt}）に使う、本文の冒頭の文字数。 */
    static final int EXCERPT_LENGTH = 40;

    private static final String FIND_DISCOVERY_ID = """
        SELECT d.id
        FROM discovery d
        WHERE d.public_id = :publicId AND d.visibility = :visibility
        """;

    // 返信先が複数ある場合は、投稿日時の最も古い（公開中の）返信先を1件だけ引用する。
    private static final String FIND_POSTS = """
        SELECT p.id, p.public_id, up.nickname AS author_name, p.posted_at, p.body,
               rp.public_id AS reply_post_id, rp.author_name AS reply_author_name, rp.excerpt AS reply_excerpt,
               (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = p.id) AS reaction_count,
               cd.public_id AS continued_in_id, cd.title AS continued_in_title
        FROM post p
        JOIN user_profile up ON up.user_id = p.user_id
        LEFT JOIN LATERAL (
            SELECT tp.public_id, tup.nickname AS author_name, left(tp.body, :excerptLength) AS excerpt
            FROM post_reference pr
            JOIN post tp ON tp.id = pr.to_post_id
            JOIN user_profile tup ON tup.user_id = tp.user_id
            WHERE pr.from_post_id = p.id AND pr.kind = :reply AND tp.visibility = :visibility
            ORDER BY tp.posted_at, tp.id
            LIMIT 1
        ) rp ON true
        LEFT JOIN post_continuation pc ON pc.post_id = p.id
        LEFT JOIN discovery cd ON cd.id = pc.discovery_id AND cd.visibility = :visibility
        WHERE p.discovery_id = :discoveryId AND p.visibility = :visibility
        ORDER BY p.posted_at, p.id
        """;

    private static final String FIND_POST_MEDIA = """
        SELECT m.post_id, m.public_id, m.kind, m.name, m.url, m.alt,
               m.source_status, m.source_type, m.source_name
        FROM media m
        WHERE m.discovery_id = :discoveryId AND m.post_id IS NOT NULL
        ORDER BY m.post_id, m.id
        """;

    private static final String FIND_DERIVATION_MARKERS = """
        SELECT op.public_id AS after_post_id, d.public_id, d.title
        FROM discovery_relation dr
        JOIN post op ON op.id = dr.origin_post_id
        JOIN discovery d ON d.id = dr.to_discovery_id
        WHERE dr.from_discovery_id = :discoveryId AND dr.relation_type = :derived
          AND op.visibility = :visibility AND d.visibility = :visibility
        ORDER BY op.posted_at, op.id, dr.id
        """;

    // 派生元が複数ある場合は、最初に登録された関係の派生元を返す。
    private static final String FIND_DERIVED_ORIGIN = """
        SELECT d.public_id, d.title
        FROM discovery_relation dr
        JOIN discovery d ON d.id = dr.from_discovery_id
        WHERE dr.to_discovery_id = :discoveryId AND dr.relation_type = :derived
          AND d.visibility = :visibility
        ORDER BY dr.id
        LIMIT 1
        """;

    private final JdbcClient jdbcClient;

    public DiscoveryPostsQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Long> findDiscoveryId(String publicId) {
        return jdbcClient.sql(FIND_DISCOVERY_ID)
            .param("publicId", publicId)
            .param("visibility", "PUBLIC")
            .query(Long.class)
            .optional();
    }

    public List<DiscoveryPostRow> findPosts(long discoveryId) {
        return jdbcClient.sql(FIND_POSTS)
            .param("discoveryId", discoveryId)
            .param("visibility", "PUBLIC")
            .param("reply", "REPLY")
            .param("excerptLength", EXCERPT_LENGTH)
            .query((rs, rowNum) -> new DiscoveryPostRow(
                rs.getLong("id"), rs.getString("public_id"), rs.getString("author_name"),
                rs.getObject("posted_at", OffsetDateTime.class), rs.getString("body"),
                rs.getString("reply_post_id"), rs.getString("reply_author_name"), rs.getString("reply_excerpt"),
                rs.getInt("reaction_count"),
                rs.getString("continued_in_id"), rs.getString("continued_in_title")))
            .list();
    }

    public List<DiscoveryPostMediaRow> findPostMedia(long discoveryId) {
        return jdbcClient.sql(FIND_POST_MEDIA)
            .param("discoveryId", discoveryId)
            .query((rs, rowNum) -> new DiscoveryPostMediaRow(
                rs.getLong("post_id"), rs.getString("public_id"), rs.getString("kind"), rs.getString("name"),
                rs.getString("url"), rs.getString("alt"),
                rs.getString("source_status"), rs.getString("source_type"), rs.getString("source_name")))
            .list();
    }

    public List<DerivationMarkerRow> findDerivationMarkers(long discoveryId) {
        return jdbcClient.sql(FIND_DERIVATION_MARKERS)
            .param("discoveryId", discoveryId)
            .param("visibility", "PUBLIC")
            .param("derived", "DERIVED")
            .query((rs, rowNum) -> new DerivationMarkerRow(
                rs.getString("after_post_id"), rs.getString("public_id"), rs.getString("title")))
            .list();
    }

    public Optional<DiscoveryRefRow> findDerivedOrigin(long discoveryId) {
        return jdbcClient.sql(FIND_DERIVED_ORIGIN)
            .param("discoveryId", discoveryId)
            .param("visibility", "PUBLIC")
            .param("derived", "DERIVED")
            .query((rs, rowNum) -> new DiscoveryRefRow(rs.getString("public_id"), rs.getString("title")))
            .optional();
    }
}
