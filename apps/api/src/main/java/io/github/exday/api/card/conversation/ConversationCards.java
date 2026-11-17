package io.github.exday.api.card.conversation;

import io.github.exday.api.generated.model.ConversationCard;
import io.github.exday.api.generated.model.ConversationCardWithFirstPost;
import io.github.exday.api.generated.model.ConversationCardWithoutFirstPost;
import io.github.exday.api.generated.model.PlaceRef;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 会話カード（C40。OpenAPI の ConversationCard）を、会話の識別子の並びから作る。どの画面の一覧でも同じ見せ方にする（使う画面はコンポーネント一覧の C40）。
 *
 * <p>どの会話を集めるか（公開の会話だけを選ぶこと）は呼び出し側が決め、ここでは選んだ会話の中身を2つの SQL でまとめて読む
 * （会話ごとに問い合わせない）。最初の Post が非公開なら、本文の冒頭と始めた人を返さない。
 */
@Component
public class ConversationCards {

    /** 会話カードの本文の冒頭の文字数。 */
    public static final int EXCERPT_LENGTH = 100;

    // 会話カード：最初の Post（時点、識別子の順で最初）と、それが公開かどうか
    private static final String FIND_CONVERSATION_CARDS = """
        SELECT c.id, c.public_id, c.created_at, c.public_post_count, c.last_public_posted_at,
               fp.visibility AS first_post_visibility, fp.body AS first_post_body, up.nickname AS starter_name
          FROM conversation c
          JOIN LATERAL (
              SELECT p.visibility, p.body, p.user_id
                FROM post p
               WHERE p.conversation_id = c.id
               ORDER BY p.posted_at, p.id
               LIMIT 1
          ) fp ON true
          JOIN user_profile up ON up.user_id = fp.user_id
         WHERE c.id IN (:conversationIds)
        """;

    private static final String FIND_CONVERSATION_PLACES = """
        SELECT cp.conversation_id, p.public_id, p.name
          FROM conversation_place cp
          JOIN place p ON p.id = cp.place_id
         WHERE cp.conversation_id IN (:conversationIds)
         GROUP BY cp.conversation_id, p.public_id, p.name
         ORDER BY cp.conversation_id, min(coalesce(cp.seq, 2147483647)), p.name
        """;

    record CardRow(long id, String publicId, OffsetDateTime createdAt, int publicPostCount,
            OffsetDateTime lastPublicPostedAt, boolean firstPostVisible, String firstPostBody, String starterName) {}

    record PlaceRefRow(String publicId, String name) {}

    private final JdbcClient jdbcClient;

    public ConversationCards(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 会話の識別子の並びの順に、会話カードを返す（見つからない会話は飛ばす）。 */
    public List<ConversationCard> cards(List<Long> conversationIds) {
        var byId = cardsById(conversationIds);
        return conversationIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** 会話の識別子ごとの会話カード。並びを呼び出し側で決めるときに使う。 */
    public Map<Long, ConversationCard> cardsById(Collection<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        var rows = findCards(conversationIds);
        var places = findPlaces(conversationIds);
        var result = new HashMap<Long, ConversationCard>();
        rows.forEach((id, row) -> result.put(id, toCard(row, places.getOrDefault(id, List.of()))));
        return result;
    }

    private Map<Long, CardRow> findCards(Collection<Long> conversationIds) {
        return jdbcClient.sql(FIND_CONVERSATION_CARDS)
            .param("conversationIds", conversationIds)
            .query((rs, rowNum) -> new CardRow(
                rs.getLong("id"), rs.getString("public_id"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getInt("public_post_count"), rs.getObject("last_public_posted_at", OffsetDateTime.class),
                "PUBLIC".equals(rs.getString("first_post_visibility")),
                rs.getString("first_post_body"), rs.getString("starter_name")))
            .list().stream()
            .collect(Collectors.toMap(CardRow::id, row -> row));
    }

    /** 会話と場所の採用した紐付き（採用中・再検討中）を、会話の識別子ごとに返す。 */
    private Map<Long, List<PlaceRefRow>> findPlaces(Collection<Long> conversationIds) {
        var result = new HashMap<Long, List<PlaceRefRow>>();
        jdbcClient.sql(FIND_CONVERSATION_PLACES)
            .param("conversationIds", conversationIds)
            .query((ResultSet rs) -> {
                result.computeIfAbsent(rs.getLong("conversation_id"), key -> new ArrayList<>())
                    .add(new PlaceRefRow(rs.getString("public_id"), rs.getString("name")));
            });
        return result;
    }

    /** 会話カード。最初の Post が非公開なら、本文の冒頭と始めた人を返さない。 */
    static ConversationCard toCard(CardRow row, List<PlaceRefRow> placeRows) {
        List<PlaceRef> places = placeRows.stream()
            .map(place -> new PlaceRef().id(place.publicId()).name(place.name()))
            .toList();
        if (row.firstPostVisible()) {
            var card = new ConversationCardWithFirstPost()
                .id(row.publicId())
                .firstPost(ConversationCardWithFirstPost.FirstPostEnum.VISIBLE)
                .excerpt(excerpt(row.firstPostBody()))
                .starterName(row.starterName())
                .replyCount(Math.max(0, row.publicPostCount() - 1))
                .startedAt(row.createdAt())
                .places(places);
            card.setLastPostedAt(row.lastPublicPostedAt());
            return card;
        }
        var card = new ConversationCardWithoutFirstPost()
            .id(row.publicId())
            .firstPost(ConversationCardWithoutFirstPost.FirstPostEnum.HIDDEN)
            .replyCount(Math.max(0, row.publicPostCount()))
            .startedAt(row.createdAt())
            .places(places);
        card.setLastPostedAt(row.lastPublicPostedAt());
        return card;
    }

    static String excerpt(String body) {
        if (body.codePointCount(0, body.length()) <= EXCERPT_LENGTH) {
            return body;
        }
        return body.substring(0, body.offsetByCodePoints(0, EXCERPT_LENGTH)) + "…";
    }
}
