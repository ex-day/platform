package io.github.exday.api.discovery;

import io.github.exday.api.context.EvaluationContextProvider;
import io.github.exday.api.discovery.DiscoveryDetailQueries.ConversationCardRow;
import io.github.exday.api.discovery.DiscoveryDetailQueries.DiscoveryRow;
import io.github.exday.api.discovery.DiscoveryDetailQueries.FindingRow;
import io.github.exday.api.discovery.DiscoveryDetailQueries.PlaceRefRow;
import io.github.exday.api.discovery.DiscoveryDetailQueries.PlaceRow;
import io.github.exday.api.discovery.DiscoveryDetailQueries.ProjectRow;
import io.github.exday.api.discovery.DiscoveryDetailQueries.ValueRow;
import io.github.exday.api.generated.model.BackedTheoryFinding;
import io.github.exday.api.generated.model.ConditionDescription;
import io.github.exday.api.generated.model.ConversationCard;
import io.github.exday.api.generated.model.ConversationCardPage;
import io.github.exday.api.generated.model.ConversationCardWithFirstPost;
import io.github.exday.api.generated.model.ConversationCardWithoutFirstPost;
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
import io.github.exday.api.generated.model.ProjectCard;
import io.github.exday.api.generated.model.ProjectCardPage;
import io.github.exday.api.generated.model.Season;
import io.github.exday.api.generated.model.UnbackedTheoryFinding;
import io.github.exday.api.generated.model.UnbackedTheoryFindingReactions;
import io.github.exday.api.generated.model.ValueFinding;
import io.github.exday.api.generated.model.ValueFindingReactions;
import io.github.exday.api.generated.model.ValueItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * DB から読んだ行を、S03 の {@link DiscoveryDetail} に組み立てる。
 *
 * <p>Value の振り分け（推薦する／その他の魅力）はここで行い、画面では評価しない（OpenAPI の getDiscovery）。
 * 今は季節だけで振り分けるので、評価に要る情報が足りない（評価不能）ことは起きない。
 */
@Component
public class DiscoveryDetailAssembler {

    /** 会話カードの本文の冒頭の文字数。 */
    static final int EXCERPT_LENGTH = 100;

    private static final String ALL_YEAR = "ALL_YEAR";

    private final DiscoveryDetailQueries queries;
    private final EvaluationContextProvider contextProvider;

    public DiscoveryDetailAssembler(DiscoveryDetailQueries queries, EvaluationContextProvider contextProvider) {
        this.queries = queries;
        this.contextProvider = contextProvider;
    }

    public DiscoveryDetail assemble(DiscoveryRow discovery) {
        var context = contextProvider.current();
        String currentSeason = context.getSeason().getValue().toUpperCase(Locale.ROOT);

        var merge = toMergeState(queries.findFinalMergeTarget(discovery.id()));
        boolean merged = merge instanceof Merged;

        List<PlaceRow> placeRows = queries.findPlaces(List.of(discovery.id())).getOrDefault(discovery.id(), List.of());
        List<PlaceRef> displayPlaces = displayPlaces(placeRows);
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
        var cardRows = queries.findConversationCards(cardIds);
        var cardPlaces = queries.findConversationPlaces(cardIds);

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
            .findings(queries.findFindings(discovery.id()).stream().map(DiscoveryDetailAssembler::toFinding).toList())
            .originConversations(toCards(originConversationIds, cardRows, cardPlaces))
            .startedConversations(new ConversationCardPage()
                .items(toCards(startedConversationIds, cardRows, cardPlaces))
                .totalCount(queries.countStartedConversations(discovery.id())))
            .projects(new ProjectCardPage()
                .items(queries.findProjects(discovery.id()).stream().map(DiscoveryDetailAssembler::toProject).toList())
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
        if (row.targetPublicId() != null) {
            merged.setTarget(new DiscoveryRef().id(row.targetPublicId()).title(row.targetTitle()));
        }
        return merged;
    }

    /** 出す位置の場所（役割が体験できる場所・始まり）。なければ関わる場所の最初のもの。 */
    static List<PlaceRef> displayPlaces(List<PlaceRow> rows) {
        var display = rows.stream()
            .filter(row -> DiscoveryDetailQueries.DISPLAY_ROLES.contains(row.role()))
            .map(DiscoveryDetailAssembler::toPlaceRef)
            .distinct()
            .toList();
        if (!display.isEmpty() || rows.isEmpty()) {
            return display;
        }
        return List.of(toPlaceRef(rows.get(0)));
    }

    private static PlaceRef toPlaceRef(PlaceRow row) {
        return new PlaceRef().id(row.publicId()).name(row.name());
    }

    private static DiscoveryPlaceItem toPlaceItem(PlaceRow row, boolean isDisplayPlace) {
        var item = new DiscoveryPlaceItem()
            .place(toPlaceRef(row))
            .spatialType(DiscoveryPlaceItem.SpatialTypeEnum.fromValue(row.spatialType().toLowerCase(Locale.ROOT)))
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

    /** わかってきたことの3つの形（価値・裏付けのない説・裏付けのある説）。 */
    static FindingItem toFinding(FindingRow row) {
        if ("VALUE".equals(row.kind())) {
            return new ValueFinding()
                .id(row.publicId())
                .kind(ValueFinding.KindEnum.VALUE)
                .text(row.content())
                .backed(false)
                .unconfirmed(row.unconfirmed())
                .reactions(new ValueFindingReactions().agree(row.agree()).wantToGo(row.wantToGo()));
        }
        if (row.backed()) {
            return new BackedTheoryFinding()
                .id(row.publicId())
                .kind(BackedTheoryFinding.KindEnum.THEORY)
                .text(row.content())
                .backed(true)
                .unconfirmed(row.unconfirmed());
        }
        return new UnbackedTheoryFinding()
            .id(row.publicId())
            .kind(UnbackedTheoryFinding.KindEnum.THEORY)
            .text(row.content())
            .backed(false)
            .unconfirmed(row.unconfirmed())
            .reactions(new UnbackedTheoryFindingReactions().maybe(row.maybe()));
    }

    private static List<ConversationCard> toCards(List<Long> ids, Map<Long, ConversationCardRow> rows,
            Map<Long, List<PlaceRefRow>> places) {
        return ids.stream()
            .map(rows::get)
            .filter(Objects::nonNull)
            .map(row -> toCard(row, places.getOrDefault(row.id(), List.of())))
            .toList();
    }

    /** 会話カード。最初の Post が非公開なら、本文の冒頭と始めた人を返さない。 */
    static ConversationCard toCard(ConversationCardRow row, List<PlaceRefRow> placeRows) {
        List<PlaceRef> places = placeRows.stream()
            .map(place -> new PlaceRef().id(place.publicId()).name(place.name()))
            .toList();
        if (row.firstPostVisible()) {
            var card = new ConversationCardWithFirstPost()
                .id(row.publicId())
                .firstPostVisible(true)
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
            .firstPostVisible(false)
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

    /**
     * 企画カード。この Discovery を指す項目があれば discovery、なければ place。
     * 順番・順位は、その種類のいちばん前の項目のもので、コース・番付のときだけ返す。
     */
    static ProjectCard toProject(ProjectRow row) {
        boolean byDiscovery = row.discoveryPosition() != null;
        var card = new ProjectCard()
            .id(row.publicId())
            .title(row.title())
            .type(ProjectCard.TypeEnum.fromValue(row.type().toLowerCase(Locale.ROOT)))
            .creatorKind(ProjectCard.CreatorKindEnum.fromValue(row.creatorKind().toLowerCase(Locale.ROOT)))
            .creatorName(row.creatorName())
            .matchedBy(byDiscovery ? ProjectCard.MatchedByEnum.DISCOVERY : ProjectCard.MatchedByEnum.PLACE);
        if ("COURSE".equals(row.type()) || "RANKING".equals(row.type())) {
            card.setPosition(byDiscovery ? row.discoveryPosition() : row.placePosition());
        }
        return card;
    }

    private DiscoveryRelations toRelations(long discoveryId) {
        var rows = queries.findRelations(discoveryId);
        var places = queries.findPlaces(rows.stream().map(DiscoveryDetailQueries.RelationRow::otherId).distinct().toList());
        List<DiscoverySummary> derivedFrom = new ArrayList<>();
        List<DiscoverySummary> derivedTo = new ArrayList<>();
        List<DiscoverySummary> related = new ArrayList<>();
        for (var row : rows) {
            var otherPlaces = displayPlaces(places.getOrDefault(row.otherId(), List.of()));
            if (otherPlaces.isEmpty()) {
                continue; // 場所のない Discovery は作らないが、念のため要約の約束（場所1件以上）を守る
            }
            var summary = new DiscoverySummary()
                .id(row.otherPublicId())
                .title(row.otherTitle())
                .summary(row.otherSummary())
                .displayPlaces(otherPlaces);
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
