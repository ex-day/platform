package io.github.exday.api.discovery;

import io.github.exday.api.context.EvaluationContextProvider;
import io.github.exday.api.generated.model.ConditionDescription;
import io.github.exday.api.generated.model.DiscoveryCard;
import io.github.exday.api.generated.model.DiscoveryDetail;
import io.github.exday.api.generated.model.DiscoveryReactions;
import io.github.exday.api.generated.model.DiscoveryRelations;
import io.github.exday.api.generated.model.Finding;
import io.github.exday.api.generated.model.FindingReactions;
import io.github.exday.api.generated.model.Image;
import io.github.exday.api.generated.model.PlaceSummary;
import io.github.exday.api.generated.model.Recommendation;
import io.github.exday.api.generated.model.Season;
import io.github.exday.api.generated.model.ValueItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * DB から取得した行を、S03 の {@link DiscoveryDetail} レスポンスに組み立てる。
 *
 * <p>Value の振り分け・関係する Discovery の「一番の Value」選択は
 * {@link DiscoveryDetailQueries} が返す並び順（推薦度の高い順）にそのまま従う。
 */
@Component
public class DiscoveryDetailAssembler {

    private final DiscoveryDetailQueries queries;
    private final EvaluationContextProvider contextProvider;

    public DiscoveryDetailAssembler(DiscoveryDetailQueries queries, EvaluationContextProvider contextProvider) {
        this.queries = queries;
        this.contextProvider = contextProvider;
    }

    public DiscoveryDetail assemble(DiscoverySummary summary) {
        var context = contextProvider.current();
        String season = context.getSeason().getValue().toUpperCase(Locale.ROOT);

        var valueRows = queries.findValues(summary.id(), season);
        List<Recommendation> recommendations = new ArrayList<>();
        List<ValueItem> otherValues = new ArrayList<>();
        for (var row : valueRows) {
            if (row.seasonal() || row.yearRound()) {
                recommendations.add(toRecommendation(row));
            } else {
                otherValues.add(toValueItem(row));
            }
        }

        List<Finding> findings = queries.findFindings(summary.id()).stream()
            .map(this::toFinding)
            .toList();

        var reactionCounts = queries.findReactions(summary.id());
        var reactions = new DiscoveryReactions(
            reactionCounts.like(), reactionCounts.surprised(), reactionCounts.love());

        var relations = assembleRelations(summary.id(), season);

        var detail = new DiscoveryDetail(
            summary.publicId(), summary.title(), summary.body(),
            new PlaceSummary(summary.placeName()), context,
            recommendations, otherValues, findings,
            queries.findTags(summary.id()), reactions, relations);
        return detail;
    }

    private Recommendation toRecommendation(DiscoveryValueRow row) {
        var recommendation = new Recommendation(row.valueId(), row.message());
        recommendation.setParticipationMessage(row.participationMessage());
        recommendation.setMedia(toImage(row));
        return recommendation;
    }

    private ValueItem toValueItem(DiscoveryValueRow row) {
        var item = new ValueItem(
            row.valueId(), row.subjectLabel(), row.summary(), row.body(), toConditions(row));
        item.setPicture(toImage(row));
        return item;
    }

    private List<ConditionDescription> toConditions(DiscoveryValueRow row) {
        if (row.seasons() == null || row.seasons().isEmpty()) {
            return List.of();
        }
        List<Season> seasons = row.seasons().stream().map(DiscoveryDetailAssembler::toSeason).toList();
        var condition = new ConditionDescription("季節", seasonsToText(seasons));
        condition.setSeasons(seasons);
        return List.of(condition);
    }

    private static String seasonsToText(List<Season> seasons) {
        List<String> texts = seasons.stream().map(DiscoveryDetailAssembler::seasonText).toList();
        return String.join("・", texts);
    }

    private static String seasonText(Season season) {
        return switch (season) {
            case SPRING -> "春（3〜5月）";
            case SUMMER -> "夏（6〜8月）";
            case AUTUMN -> "秋（9〜11月）";
            case WINTER -> "冬（12〜2月）";
            case ALL_YEAR -> "通年";
        };
    }

    private static Season toSeason(String dbValue) {
        return Season.fromValue(dbValue.toLowerCase(Locale.ROOT));
    }

    private static Image toImage(DiscoveryValueRow row) {
        if (row.pictureUrl() == null) {
            return null;
        }
        return new Image(row.pictureUrl(), row.pictureAlt());
    }

    private Finding toFinding(DiscoveryFindingRow row) {
        var kind = "VALUE".equals(row.kind()) ? Finding.KindEnum.VALUE : Finding.KindEnum.THEORY;
        var finding = new Finding(row.findingId(), kind, row.text());
        finding.setBackedBy(row.backedBy());
        if (row.backedBy() == null) {
            var reactions = new FindingReactions();
            if (kind == Finding.KindEnum.VALUE) {
                reactions.setUnderstand(row.understand());
                reactions.setWantToGo(row.wantToGo());
            } else {
                reactions.setMaybe(row.maybe());
            }
            finding.setReactions(reactions);
        }
        return finding;
    }

    private DiscoveryRelations assembleRelations(long discoveryId, String season) {
        List<DiscoveryCard> derivedFrom = new ArrayList<>();
        List<DiscoveryCard> derivedTo = new ArrayList<>();
        List<DiscoveryCard> related = new ArrayList<>();
        for (var row : queries.findRelations(discoveryId)) {
            var card = toCard(row, season);
            if (row.derivedFrom()) {
                derivedFrom.add(card);
            } else if (row.derivedTo()) {
                derivedTo.add(card);
            } else {
                related.add(card);
            }
        }
        return new DiscoveryRelations(derivedFrom, derivedTo, related);
    }

    private DiscoveryCard toCard(DiscoveryRelationRow row, String season) {
        var values = queries.findValues(row.otherId(), season);
        String subject = "";
        String value = "";
        Image picture = null;
        if (!values.isEmpty()) {
            var top = values.get(0);
            subject = top.subjectLabel();
            value = top.summary();
            picture = toImage(top);
        }
        var card = new DiscoveryCard(
            row.otherPublicId(), row.otherTitle(), subject, value,
            new PlaceSummary(row.otherPlaceName()));
        card.setPicture(picture);
        return card;
    }
}

