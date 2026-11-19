package io.github.exday.api.projectconversation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 企画をきっかけにした会話：入口がその企画で、企画そのものの会話でない、公開の会話。
 * どの画面でも同じ範囲で数える（S15 の「企画をきっかけにした会話」の全体の数、S17 の企画カードの会話の数）。
 */
@Component
public class TriggeredConversations {

    private static final String COUNT_BY_PROJECT = """
        SELECT c.entry_project_id AS project_id, count(*) AS conversations
          FROM visible_conversation c
         WHERE c.entry_project_id IN (:projectIds) AND NOT c.is_project_own
         GROUP BY c.entry_project_id
        """;

    private final JdbcClient jdbcClient;

    public TriggeredConversations(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 企画（DB の id）ごとの数。会話のない企画は入らない（0 として扱う）。 */
    public Map<Long, Integer> countByProject(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(COUNT_BY_PROJECT)
            .param("projectIds", List.copyOf(projectIds))
            .query((rs, rowNum) -> Map.entry(rs.getLong("project_id"), rs.getInt("conversations")))
            .list().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
