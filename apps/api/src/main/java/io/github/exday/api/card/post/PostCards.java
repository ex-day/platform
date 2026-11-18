package io.github.exday.api.card.post;

import io.github.exday.api.generated.model.HiddenPost;
import io.github.exday.api.generated.model.PostItem;
import io.github.exday.api.generated.model.VisiblePost;
import io.github.exday.api.generated.model.VisiblePostReactions;
import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 会話の Post（C26。OpenAPI の PostItem）を作る。どの画面でも同じ見せ方にする（S14 の会話、S15 のタイムライン）。
 *
 * <p>どの Post を、どの順で出すか（属する会話が公開と確かめること、並び、件数）は呼び出し側が決める。
 * 非公開の Post は、識別子・時点・返信先だけを返し、本文と書いた人を返さない（HiddenPost。DB 設計 2.5節）。
 * 書いた人は今のニックネーム（Issue #74）、返信先は同じ会話の中の Post（時点・識別子の順）、共感の数を、まとめて1つの SQL で読む。
 */
@Component
public class PostCards {

    private static final String FIND_POSTS = """
        SELECT p.id, p.public_id, p.visibility, p.body, p.posted_at, u.nickname,
               (SELECT count(*) FROM reaction r WHERE r.post_id = p.id AND r.kind = 'EMPATHY') AS empathy,
               (SELECT array_agg(t.public_id ORDER BY t.posted_at, t.public_id COLLATE "C")
                  FROM post_reference pr JOIN post t ON t.id = pr.to_post_id
                 WHERE pr.from_post_id = p.id AND pr.kind = 'REPLY' AND t.conversation_id = p.conversation_id) AS reply_to
          FROM post p
          LEFT JOIN user_profile u ON u.user_id = p.user_id
         WHERE p.id IN (:ids)
        """;

    private final JdbcClient jdbcClient;

    public PostCards(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Post（DB の id）ごとの PostItem。並べるのは呼び出し側。 */
    public Map<Long, PostItem> cards(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(FIND_POSTS)
            .param("ids", List.copyOf(postIds))
            .query((rs, rowNum) -> Map.entry(rs.getLong("id"), toItem(
                rs.getString("public_id"), "PUBLIC".equals(rs.getString("visibility")), rs.getString("body"),
                rs.getObject("posted_at", OffsetDateTime.class), rs.getString("nickname"), rs.getInt("empathy"),
                strings(rs.getArray("reply_to")))))
            .list().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static PostItem toItem(String publicId, boolean visible, String body, OffsetDateTime postedAt,
            String nickname, int empathy, List<String> replyTo) {
        if (!visible) {
            return new HiddenPost()
                .id(publicId)
                .visibility(HiddenPost.VisibilityEnum.HIDDEN)
                .postedAt(postedAt)
                .replyTo(replyTo);
        }
        return new VisiblePost()
            .id(publicId)
            .visibility(VisiblePost.VisibilityEnum.VISIBLE)
            .body(body)
            .authorName(nickname)
            .postedAt(postedAt)
            .replyTo(replyTo)
            .reactions(new VisiblePostReactions().empathy(empathy));
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
