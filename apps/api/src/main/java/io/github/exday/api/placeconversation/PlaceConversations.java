package io.github.exday.api.placeconversation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 場所の会話：その場所が採用されている（会話の採用、または会話の問いの回答の採用。conversation_place）公開の会話。
 * どの画面でも同じ範囲で数える（S13 の「この場所の会話」の全体の数、S15 の場所の項目の会話の数）。
 */
@Component
public class PlaceConversations {

    private static final String COUNT_BY_PLACE = """
        SELECT cp.place_id, count(DISTINCT c.id) AS conversations
          FROM conversation_place cp
          JOIN visible_conversation c ON c.id = cp.conversation_id
         WHERE cp.place_id IN (:placeIds)
         GROUP BY cp.place_id
        """;

    private final JdbcClient jdbcClient;

    public PlaceConversations(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 場所（DB の id）ごとの公開の会話の数。会話のない場所は入らない（0 として扱う）。 */
    public Map<Long, Integer> countByPlace(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(COUNT_BY_PLACE)
            .param("placeIds", List.copyOf(placeIds))
            .query((rs, rowNum) -> Map.entry(rs.getLong("place_id"), rs.getInt("conversations")))
            .list().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
