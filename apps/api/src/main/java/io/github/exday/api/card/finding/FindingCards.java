package io.github.exday.api.card.finding;

import io.github.exday.api.generated.model.BackedTheoryFinding;
import io.github.exday.api.generated.model.FindingItem;
import io.github.exday.api.generated.model.UnbackedTheoryFinding;
import io.github.exday.api.generated.model.UnbackedTheoryFindingReactions;
import io.github.exday.api.generated.model.ValueFinding;
import io.github.exday.api.generated.model.ValueFindingReactions;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * わかってきたこと（C25。OpenAPI の FindingItem）を作る。どの画面の一覧でも同じ見せ方にする（使う画面はコンポーネント一覧の C25）。
 *
 * <p>どのわかってきたことを、どの順で出すか（公開のものだけを選ぶこと、並び、件数）は呼び出し側が決める。
 * ここでは選んだわかってきたことの今の版と、リアクションの数（版をまたいで引き継ぐ）を1つの SQL でまとめて読む。
 * 今の版になった時点は、版が1ならできた時点と同じにする（OpenAPI の updatedAt）。
 */
@Component
public class FindingCards {

    private static final String FIND_FINDINGS = """
        SELECT f.id, f.public_id, f.kind, f.created_at, f.current_version,
               v.content, v.backed, v.unconfirmed,
               CASE WHEN f.current_version = 1 THEN f.created_at ELSE v.created_at END AS version_created_at,
               count(r.id) FILTER (WHERE r.kind = 'AGREE') AS agree,
               count(r.id) FILTER (WHERE r.kind = 'WANT_TO_GO') AS want_to_go,
               count(r.id) FILTER (WHERE r.kind = 'MAYBE') AS maybe
          FROM finding f
          JOIN finding_version v ON v.finding_id = f.id AND v.version = f.current_version
          LEFT JOIN reaction r ON r.finding_id = f.id
         WHERE f.id IN (:ids)
         GROUP BY f.id, v.finding_id, v.version
        """;

    private record FindingRow(long id, String publicId, String kind, OffsetDateTime createdAt, int version,
            String content, boolean backed, boolean unconfirmed, OffsetDateTime updatedAt,
            int agree, int wantToGo, int maybe) {}

    private final JdbcClient jdbcClient;

    public FindingCards(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 識別子（DB の id）ごとのわかってきたこと。並べるのは呼び出し側。 */
    public Map<Long, FindingItem> cards(Collection<Long> findingIds) {
        if (findingIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(FIND_FINDINGS)
            .param("ids", List.copyOf(findingIds))
            .query((rs, rowNum) -> new FindingRow(rs.getLong("id"), rs.getString("public_id"), rs.getString("kind"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getInt("current_version"),
                rs.getString("content"), rs.getBoolean("backed"), rs.getBoolean("unconfirmed"),
                rs.getObject("version_created_at", OffsetDateTime.class),
                rs.getInt("agree"), rs.getInt("want_to_go"), rs.getInt("maybe")))
            .list().stream()
            .collect(Collectors.toMap(FindingRow::id, FindingCards::toCard));
    }

    /** わかってきたことの3つの形（価値・裏付けのない説・裏付けのある説）。 */
    private static FindingItem toCard(FindingRow row) {
        if ("VALUE".equals(row.kind())) {
            return new ValueFinding()
                .id(row.publicId())
                .kind(ValueFinding.KindEnum.VALUE)
                .text(row.content())
                .version(row.version())
                .createdAt(row.createdAt())
                .updatedAt(row.updatedAt())
                .unconfirmed(row.unconfirmed())
                .reactions(new ValueFindingReactions().agree(row.agree()).wantToGo(row.wantToGo()));
        }
        if (row.backed()) {
            return new BackedTheoryFinding()
                .id(row.publicId())
                .kind(BackedTheoryFinding.KindEnum.THEORY)
                .text(row.content())
                .version(row.version())
                .createdAt(row.createdAt())
                .updatedAt(row.updatedAt())
                .backing(BackedTheoryFinding.BackingEnum.BACKED)
                .unconfirmed(row.unconfirmed());
        }
        return new UnbackedTheoryFinding()
            .id(row.publicId())
            .kind(UnbackedTheoryFinding.KindEnum.THEORY)
            .text(row.content())
            .version(row.version())
            .createdAt(row.createdAt())
            .updatedAt(row.updatedAt())
            .backing(UnbackedTheoryFinding.BackingEnum.UNBACKED)
            .unconfirmed(row.unconfirmed())
            .reactions(new UnbackedTheoryFindingReactions().maybe(row.maybe()));
    }
}
