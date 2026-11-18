package io.github.exday.api.discovery;

import io.github.exday.api.context.EvaluationContextProvider;
import io.github.exday.api.discovery.DiscoveryDetailQueries.DiscoveryRow;
import io.github.exday.api.discovery.DiscoveryDetailQueries.PlaceRow;
import io.github.exday.api.discovery.DiscoveryDetailQueries.ValueRow;
import io.github.exday.api.generated.model.ConditionDescription;
import io.github.exday.api.generated.model.ConversationCardPage;
import io.github.exday.api.generated.model.DiscoveryCapabilities;
import io.github.exday.api.generated.model.DiscoveryDetail;
import io.github.exday.api.generated.model.DiscoveryOrigin;
import io.github.exday.api.generated.model.DiscoveryPlaceItem;
import io.github.exday.api.generated.model.DiscoveryReactions;
import io.github.exday.api.generated.model.DiscoveryRef;
import io.github.exday.api.generated.model.DiscoveryRelations;
import io.github.exday.api.generated.model.DiscoverySummary;
import io.github.exday.api.generated.model.FindingItem;
import io.github.exday.api.generated.model.MergeState;
import io.github.exday.api.generated.model.Merged;
import io.github.exday.api.generated.model.NotMerged;
import io.github.exday.api.generated.model.PlaceRef;
import io.github.exday.api.generated.model.ProjectCardPage;
import io.github.exday.api.generated.model.Season;
import io.github.exday.api.generated.model.SpatialType;
import io.github.exday.api.card.conversation.ConversationCards;
import io.github.exday.api.card.discovery.DiscoveryCards;
import io.github.exday.api.card.finding.FindingCards;
import io.github.exday.api.geometry.GeoJsonGeometries;
import io.github.exday.api.card.project.ProjectCards;
import io.github.exday.api.generated.model.ValueItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * DB から読んだ行を、S03 の {@link DiscoveryDetail} に組み立てる。
 *
 * <p>Value の振り分け（推薦する／その他の魅力）はここで行い、画面では評価しない（OpenAPI の getDiscovery）。
 * 今は季節だけで振り分けるので、評価に要る情報が足りない（評価不能）ことは起きない。
 */
@Component
public class DiscoveryDetailAssembler {

    private static final String ALL_YEAR = "ALL_YEAR";

    private final DiscoveryDetailQueries queries;
    private final ConversationCards conversationCards;
    private final DiscoveryCards discoveryCards;
    private final FindingCards findingCards;
    private final EvaluationContextProvider contextProvider;

    public DiscoveryDetailAssembler(DiscoveryDetailQueries queries, ConversationCards conversationCards,
            DiscoveryCards discoveryCards, FindingCards findingCards, EvaluationContextProvider contextProvider) {
        this.queries = queries;
        this.conversationCards = conversationCards;
        this.discoveryCards = discoveryCards;
        this.findingCards = findingCards;
        this.contextProvider = contextProvider;
    }

    public DiscoveryDetail assemble(DiscoveryRow discovery) {
        var context = contextProvider.current();
        String currentSeason = context.getSeason().getValue().toUpperCase(Locale.ROOT);

        var merge = toMergeState(queries.findFinalMergeTarget(discovery.id()));
        boolean merged = merge instanceof Merged;

        List<PlaceRow> placeRows = queries.findPlaces(List.of(discovery.id())).getOrDefault(discovery.id(), List.of());
        List<PlaceRef> displayPlaces = DiscoveryCards.displayPlaces(placeRows.stream()
            .map(row -> new DiscoveryCards.RolePlace(row.publicId(), row.name(), row.role())).toList());
        var displayIds = displayPlaces.stream().map(PlaceRef::getId).toList();

        List<ValueItem> recommendations = new ArrayList<>();
        List<ValueItem> otherValues = new ArrayList<>();
        classifyValues(queries.findValues(discovery.id()), currentSeason, recommendations, otherValues);

        var reactionCounts = queries.findReactionCounts(discovery.id());

        var originConversationIds = queries.findOriginConversationIds(discovery.id());
        var startedConversationIds = queries.findStartedConversationIds(discovery.id());
        var cardIds = new LinkedHashSet<Long>();
        cardIds.addAll(originConversationIds);
        cardIds.addAll(startedConversationIds);
        var cards = conversationCards.cardsById(cardIds);

        var detail = new DiscoveryDetail()
            .id(discovery.publicId())
            .title(discovery.title())
            .summary(discovery.summary())
            .merge(merge)
            .capabilities(new DiscoveryCapabilities().canStartConversation(!merged).canReact(!merged))
            .context(context)
            .displayPlaces(displayPlaces)
            .aspects(queries.findAspects(discovery.id()))
            .recommendations(recommendations)
            .otherValues(otherValues)
            .origins(queries.findOrigins(discovery.id()).stream().map(DiscoveryDetailAssembler::toOrigin).toList())
            .reactions(new DiscoveryReactions()
                .agree(reactionCounts.getOrDefault("AGREE", 0))
                .wantToGo(reactionCounts.getOrDefault("WANT_TO_GO", 0)))
            .places(placeRows.stream().map(row -> toPlaceItem(row, displayIds.contains(row.publicId()))).toList())
            .findings(findings(discovery.id()))
            .originConversations(originConversationIds.stream().map(cards::get).filter(java.util.Objects::nonNull).toList())
            .startedConversations(new ConversationCardPage()
                .items(startedConversationIds.stream().map(cards::get).filter(java.util.Objects::nonNull).toList())
                .totalCount(queries.countStartedConversations(discovery.id())))
            .projects(new ProjectCardPage()
                .items(queries.findProjects(discovery.id()).stream()
                    .map(row -> ProjectCards.toCard(row, ProjectCards.Prefer.DISCOVERY)).toList())
                .totalCount(queries.countProjects(discovery.id())))
            .relations(toRelations(discovery.id()));
        if (!displayPlaces.isEmpty()) {
            detail.setDetourFrom(displayPlaces.get(0));
        }
        return detail;
    }

    private static MergeState toMergeState(DiscoveryDetailQueries.MergeRow row) {
        if (row.depth() == 0) {
            return new NotMerged().status(NotMerged.StatusEnum.NONE);
        }
        var merged = new Merged().status(Merged.StatusEnum.MERGED);
        if (row.targetPublicId() != null && !row.hasNext()) {
            merged.setTarget(new DiscoveryRef().id(row.targetPublicId()).title(row.targetTitle()));
        }
        return merged;
    }

    private static PlaceRef toPlaceRef(PlaceRow row) {
        return new PlaceRef().id(row.publicId()).name(row.name());
    }

    private static DiscoveryPlaceItem toPlaceItem(PlaceRow row, boolean isDisplayPlace) {
        var item = new DiscoveryPlaceItem()
            .place(toPlaceRef(row))
            .spatialType(SpatialType.fromValue(row.spatialType().toLowerCase(Locale.ROOT)))
            .kinds(row.kinds())
            .role(row.role())
            .isDisplayPlace(isDisplayPlace)
            .geometry(GeoJsonGeometries.parse(row.geoJson()));
        if (row.seq() != null) {
            item.setOrder(row.seq());
        }
        if (row.validFrom() != null || row.validTo() != null) {
            var period = new io.github.exday.api.generated.model.Period();
            period.setFrom(row.validFrom());
            period.setTo(row.validTo());
            item.setPeriod(period);
        }
        if (row.description() != null && !row.description().isEmpty()) {
            item.setDescription(row.description());
        }
        return item;
    }

    /**
     * 推薦する Value（季節が一致、または通年・季節の条件なし）と、その他の魅力（季節違い）に分ける。
     * 推薦は、季節が一致するものを先に、次に通年・条件なしのもの（同じなら DB の順）。
     */
    static void classifyValues(List<ValueRow> rows, String currentSeason,
            List<ValueItem> recommendations, List<ValueItem> otherValues) {
        List<ValueItem> seasonal = new ArrayList<>();
        List<ValueItem> yearRound = new ArrayList<>();
        for (var row : rows) {
            var item = toValueItem(row);
            if (row.seasons().contains(currentSeason)) {
                seasonal.add(item);
            } else if (row.seasons().isEmpty() || row.seasons().contains(ALL_YEAR)) {
                yearRound.add(item);
            } else {
                otherValues.add(item);
            }
        }
        recommendations.addAll(seasonal);
        recommendations.addAll(yearRound);
    }

    private static ValueItem toValueItem(ValueRow row) {
        var item = new ValueItem().id(row.publicId()).description(row.description()).conditions(new ArrayList<>());
        if (!row.seasons().isEmpty()) {
            List<Season> seasons = row.seasons().stream()
                .sorted(java.util.Comparator.comparingInt(DiscoveryDetailAssembler::seasonOrder))
                .map(value -> Season.fromValue(value.toLowerCase(Locale.ROOT)))
                .toList();
            item.addConditionsItem(new ConditionDescription()
                .axis(ConditionDescription.AxisEnum.SEASON)
                .label("季節")
                .text(String.join("・", seasons.stream().map(DiscoveryDetailAssembler::seasonText).toList()))
                .seasons(seasons));
        }
        return item;
    }

    private static int seasonOrder(String season) {
        return switch (season) {
            case "SPRING" -> 1;
            case "SUMMER" -> 2;
            case "AUTUMN" -> 3;
            case "WINTER" -> 4;
            default -> 5;
        };
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

    private static DiscoveryOrigin toOrigin(DiscoveryDetailQueries.OriginRow row) {
        return new DiscoveryOrigin()
            .kind(DiscoveryOrigin.KindEnum.fromValue(row.kind().toLowerCase(Locale.ROOT)))
            .establishedBy("AUTO".equals(row.method())
                ? DiscoveryOrigin.EstablishedByEnum.AUTO
                : DiscoveryOrigin.EstablishedByEnum.HUMAN);
    }

    private List<FindingItem> findings(long discoveryId) {
        var ids = queries.findFindingIds(discoveryId);
        var cards = findingCards.cards(ids);
        return ids.stream().map(cards::get).filter(Objects::nonNull).toList();
    }

    private DiscoveryRelations toRelations(long discoveryId) {
        var rows = queries.findRelations(discoveryId);
        var cards = discoveryCards.cards(rows.stream()
            .map(row -> new DiscoveryCards.DiscoveryRow(row.otherId(), row.otherPublicId(), row.otherTitle(), row.otherSummary()))
            .toList());
        List<DiscoverySummary> derivedFrom = new ArrayList<>();
        List<DiscoverySummary> derivedTo = new ArrayList<>();
        List<DiscoverySummary> related = new ArrayList<>();
        for (var row : rows) {
            var summary = cards.get(row.otherId());
            if (summary == null) {
                continue;
            }
            List<DiscoverySummary> target;
            if ("DERIVED".equals(row.kind())) {
                // 派生は from が派生元。この Discovery が to なら、相手は派生元
                target = row.outgoing() ? derivedTo : derivedFrom;
            } else {
                target = related;
            }
            if (target.size() < DiscoveryDetailQueries.LIST_LIMIT && !target.contains(summary)) {
                target.add(summary);
            }
        }
        return new DiscoveryRelations().derivedFrom(derivedFrom).derivedTo(derivedTo).related(related);
    }
}
