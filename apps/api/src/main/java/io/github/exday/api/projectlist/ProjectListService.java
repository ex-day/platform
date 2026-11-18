package io.github.exday.api.projectlist;

import io.github.exday.api.generated.model.ProjectCreatorKind;
import io.github.exday.api.generated.model.ProjectListCard;
import io.github.exday.api.generated.model.ProjectListPage;
import io.github.exday.api.generated.model.ProjectListSort;
import io.github.exday.api.generated.model.ProjectType;
import io.github.exday.api.projectconversation.TriggeredConversations;
import io.github.exday.api.projectlist.ProjectListQueries.ProjectRow;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S17 の応答を、1つの読み取りの時点（スナップショット）でそろえて作る（apps/api/AGENTS.md「読み取りの一貫性と公開制御」）。
 */
@Service
public class ProjectListService {

    private final ProjectListQueries queries;
    private final TriggeredConversations triggeredConversations;

    public ProjectListService(ProjectListQueries queries, TriggeredConversations triggeredConversations) {
        this.queries = queries;
        this.triggeredConversations = triggeredConversations;
    }

    /** カーソルが条件に合わない、または並びのキーの企画がなければ {@link InvalidProjectListCursorException}。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectListPage find(ProjectType type, ProjectCreatorKind creator, ProjectListSort sort, String after,
            int limit) {
        ProjectListQueries.Key key = null;
        if (after != null) {
            key = queries.findKey(ProjectListCursor.keyFor(after, sort, type, creator))
                .orElseThrow(InvalidProjectListCursorException::new);
        }
        // 1件多く読み、続きがあるかを知る
        var rows = new ArrayList<>(queries.findProjects(
            type == null ? null : type.getValue().toUpperCase(Locale.ROOT),
            creator == null ? null : creator.getValue().toUpperCase(Locale.ROOT), key, limit + 1));
        boolean hasNext = rows.size() > limit;
        if (hasNext) {
            rows.remove(rows.size() - 1);
        }
        var counts = triggeredConversations.countByProject(rows.stream().map(ProjectRow::id).toList());
        var page = new ProjectListPage().items(rows.stream().map(row -> {
            var card = new ProjectListCard()
                .id(row.publicId())
                .title(row.title())
                .type(ProjectType.fromValue(row.type().toLowerCase(Locale.ROOT)))
                .creatorKind(ProjectListCard.CreatorKindEnum.fromValue(row.creatorKind().toLowerCase(Locale.ROOT)))
                .creatorName(row.creatorName())
                .itemCount(row.itemCount())
                .triggeredConversationCount(counts.getOrDefault(row.id(), 0));
            if (!row.callText().isEmpty()) {
                card.callText(row.callText());
            }
            return card;
        }).toList());
        if (hasNext) {
            page.nextCursor(new ProjectListCursor(sort, type, creator, rows.get(rows.size() - 1).publicId()).encode());
        }
        return page;
    }
}
