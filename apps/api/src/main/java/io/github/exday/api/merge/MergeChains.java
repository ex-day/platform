package io.github.exday.api.merge;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 統合をたどって、最後の統合先を決める（Discovery・わかってきたこと・問い・場所）。どの画面でも同じ決まりにする。
 *
 * <p>決まり：統合の関係を {@link #MAX_DEPTH} 段までたどる。すでに通ったものには戻らない。
 * 最後のものからさらに統合の関係が出ている（循環している、または深さの上限で止まった）ときは、
 * 最後の統合先を決められない（{@link Resolution#resolved()} が false）。公開かどうかは呼び出し側が確かめる。
 * 複数をまとめて1つの SQL でたどる（1件ずつ問い合わせない）。
 */
@Component
public class MergeChains {

    /** 統合をたどる深さの上限。 */
    public static final int MAX_DEPTH = 20;

    /** 統合のたどりの結果。{@code depth} が 0 なら統合されていない（{@code finalId} は自身）。 */
    public record Resolution(long startId, long finalId, int depth, boolean resolved) {

        public boolean merged() {
            return depth > 0;
        }
    }

    private static final String DISCOVERY = """
        WITH RECURSIVE chain (start_id, id, depth, path) AS (
            SELECT d.id, d.id, 0, ARRAY[d.id] FROM discovery d WHERE d.id IN (:ids)
            UNION ALL
            SELECT c.start_id, r.to_discovery_id, c.depth + 1, c.path || r.to_discovery_id
              FROM chain c
              JOIN discovery_relation r
                ON r.from_discovery_id = c.id AND r.kind = 'MERGED' AND r.status = 'ACTIVE'
             WHERE NOT r.to_discovery_id = ANY (c.path)
               AND c.depth < :maxDepth
        )
        SELECT DISTINCT ON (c.start_id) c.start_id, c.id AS final_id, c.depth,
               EXISTS (SELECT 1 FROM discovery_relation n
                        WHERE n.from_discovery_id = c.id AND n.kind = 'MERGED' AND n.status = 'ACTIVE') AS has_next
          FROM chain c
         ORDER BY c.start_id, c.depth DESC, c.id
        """;

    private static final String FINDING = """
        WITH RECURSIVE chain (start_id, id, depth, path) AS (
            SELECT f.id, f.id, 0, ARRAY[f.id] FROM finding f WHERE f.id IN (:ids)
            UNION ALL
            SELECT c.start_id, f.merged_into_finding_id, c.depth + 1, c.path || f.merged_into_finding_id
              FROM chain c
              JOIN finding f ON f.id = c.id
             WHERE f.merged_into_finding_id IS NOT NULL
               AND NOT f.merged_into_finding_id = ANY (c.path)
               AND c.depth < :maxDepth
        )
        SELECT DISTINCT ON (c.start_id) c.start_id, c.id AS final_id, c.depth,
               f.merged_into_finding_id IS NOT NULL AS has_next
          FROM chain c
          JOIN finding f ON f.id = c.id
         ORDER BY c.start_id, c.depth DESC
        """;

    private static final String QUESTION = """
        WITH RECURSIVE chain (start_id, id, depth, path) AS (
            SELECT q.id, q.id, 0, ARRAY[q.id] FROM open_question q WHERE q.id IN (:ids)
            UNION ALL
            SELECT c.start_id, q.merged_into_question_id, c.depth + 1, c.path || q.merged_into_question_id
              FROM chain c
              JOIN open_question q ON q.id = c.id
             WHERE q.merged_into_question_id IS NOT NULL
               AND NOT q.merged_into_question_id = ANY (c.path)
               AND c.depth < :maxDepth
        )
        SELECT DISTINCT ON (c.start_id) c.start_id, c.id AS final_id, c.depth,
               q.merged_into_question_id IS NOT NULL AS has_next
          FROM chain c
          JOIN open_question q ON q.id = c.id
         ORDER BY c.start_id, c.depth DESC
        """;

    private static final String PLACE = """
        WITH RECURSIVE chain (start_id, id, depth, path) AS (
            SELECT p.id, p.id, 0, ARRAY[p.id] FROM place p WHERE p.id IN (:ids)
            UNION ALL
            SELECT c.start_id, p.merged_into_place_id, c.depth + 1, c.path || p.merged_into_place_id
              FROM chain c
              JOIN place p ON p.id = c.id
             WHERE p.merged_into_place_id IS NOT NULL
               AND NOT p.merged_into_place_id = ANY (c.path)
               AND c.depth < :maxDepth
        )
        SELECT DISTINCT ON (c.start_id) c.start_id, c.id AS final_id, c.depth,
               p.merged_into_place_id IS NOT NULL AS has_next
          FROM chain c
          JOIN place p ON p.id = c.id
         ORDER BY c.start_id, c.depth DESC
        """;

    private final JdbcClient jdbcClient;

    public MergeChains(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Discovery の統合（discovery_relation の MERGED）をたどる。 */
    public Map<Long, Resolution> discoveries(Collection<Long> ids) {
        return resolve(DISCOVERY, ids);
    }

    /** わかってきたことの統合（merged_into_finding_id）をたどる。 */
    public Map<Long, Resolution> findings(Collection<Long> ids) {
        return resolve(FINDING, ids);
    }

    /** 問いの統合（merged_into_question_id）をたどる。 */
    public Map<Long, Resolution> questions(Collection<Long> ids) {
        return resolve(QUESTION, ids);
    }

    /** 場所の統合（merged_into_place_id）をたどる。 */
    public Map<Long, Resolution> places(Collection<Long> ids) {
        return resolve(PLACE, ids);
    }

    private Map<Long, Resolution> resolve(String sql, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Resolution> rows = jdbcClient.sql(sql)
            .param("ids", List.copyOf(ids))
            .param("maxDepth", MAX_DEPTH)
            .query((rs, rowNum) -> new Resolution(rs.getLong("start_id"), rs.getLong("final_id"),
                rs.getInt("depth"), !rs.getBoolean("has_next")))
            .list();
        return rows.stream().collect(Collectors.toMap(Resolution::startId, row -> row));
    }
}
