package io.github.exday.api.card.project;

import io.github.exday.api.generated.model.ProjectListCard;
import io.github.exday.api.generated.model.ProjectType;
import io.github.exday.api.projectconversation.TriggeredConversations;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 一覧の企画カード（C29。OpenAPI の ProjectListCard）を作る。S17 の企画の一覧と、S01 の TOP の企画の枠で同じ見せ方にする。
 *
 * <p>数の決まりもここが持つ：項目の数は、指す先が見えない項目も位置として数える（S15 の並びの位置の数と同じ）。
 * 企画をきっかけにした会話の数は {@link TriggeredConversations} の範囲。どの企画を集めるか（公開のものだけを選ぶこと、
 * 並び）は呼び出し側が決め、ここでは選んだ企画の数をまとめて読む（企画ごとに問い合わせない）。
 */
@Component
public class ProjectListCards {

    private static final String COUNT_ITEMS = """
        SELECT pi.project_id, count(*) AS item_count
          FROM project_item pi
         WHERE pi.project_id IN (:projectIds)
         GROUP BY pi.project_id
        """;

    /** カードの材料（呼び出し側が公開と確かめた企画）。{@code type}・{@code creatorKind} は DB の値。 */
    public record ProjectRow(long id, String publicId, String title, String callText, String type,
            String creatorKind, String creatorName) {}

    private final JdbcClient jdbcClient;
    private final TriggeredConversations triggeredConversations;

    public ProjectListCards(JdbcClient jdbcClient, TriggeredConversations triggeredConversations) {
        this.jdbcClient = jdbcClient;
        this.triggeredConversations = triggeredConversations;
    }

    /** 渡した順に企画カードを返す。 */
    public List<ProjectListCard> cards(List<ProjectRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        var ids = rows.stream().map(ProjectRow::id).toList();
        Map<Long, Integer> itemCounts = jdbcClient.sql(COUNT_ITEMS)
            .param("projectIds", ids)
            .query((rs, rowNum) -> Map.entry(rs.getLong("project_id"), rs.getInt("item_count")))
            .list().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var conversationCounts = triggeredConversations.countByProject(ids);
        return rows.stream().map(row -> {
            var card = new ProjectListCard()
                .id(row.publicId())
                .title(row.title())
                .type(ProjectType.fromValue(row.type().toLowerCase(Locale.ROOT)))
                .creatorKind(ProjectListCard.CreatorKindEnum.fromValue(row.creatorKind().toLowerCase(Locale.ROOT)))
                .creatorName(row.creatorName())
                .itemCount(itemCounts.getOrDefault(row.id(), 0))
                .triggeredConversationCount(conversationCounts.getOrDefault(row.id(), 0));
            if (!row.callText().isEmpty()) {
                card.callText(row.callText());
            }
            return card;
        }).toList();
    }
}
