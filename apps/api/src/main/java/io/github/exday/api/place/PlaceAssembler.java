package io.github.exday.api.place;

import io.github.exday.api.card.discovery.DiscoveryCards;
import io.github.exday.api.generated.model.AbolishedPlaceState;
import io.github.exday.api.generated.model.ActivePlaceState;
import io.github.exday.api.generated.model.ConversationCardPage;
import io.github.exday.api.generated.model.ExistingPlaceCandidate;
import io.github.exday.api.generated.model.MergedPlaceState;
import io.github.exday.api.generated.model.NearbyPlace;
import io.github.exday.api.generated.model.NearbyPlacePage;
import io.github.exday.api.generated.model.PlaceAlias;
import io.github.exday.api.generated.model.PlaceCapabilities;
import io.github.exday.api.generated.model.PlaceDetail;
import io.github.exday.api.generated.model.PlaceDiscoveryCard;
import io.github.exday.api.generated.model.PlaceDiscoveryGroup;
import io.github.exday.api.generated.model.PlaceDiscoveryGroups;
import io.github.exday.api.generated.model.PlaceEstimate;
import io.github.exday.api.generated.model.PlaceRef;
import io.github.exday.api.generated.model.PlaceState;
import io.github.exday.api.generated.model.ProjectCardPage;
import io.github.exday.api.generated.model.ProposedPlaceCandidate;
import io.github.exday.api.generated.model.QuestionRef;
import io.github.exday.api.generated.model.SeekingCandidate;
import io.github.exday.api.generated.model.SeekingCard;
import io.github.exday.api.generated.model.SeekingCardPage;
import io.github.exday.api.generated.model.SeekingConversationCard;
import io.github.exday.api.generated.model.SeekingQuestionCard;
import io.github.exday.api.generated.model.SpatialType;
import io.github.exday.api.place.PlaceQueries.PlaceRow;
import io.github.exday.api.place.PlaceQueries.SeekingCandidateRow;
import io.github.exday.api.card.conversation.ConversationCards;
import io.github.exday.api.geometry.GeoJsonGeometries;
import io.github.exday.api.card.project.ProjectCards;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** DB から読んだ行を、S13 の {@link PlaceDetail} に組み立てる。 */
@Component
public class PlaceAssembler {

    private final PlaceQueries queries;
    private final DiscoveryCards discoveryCards;
    private final ConversationCards conversationCards;

    public PlaceAssembler(PlaceQueries queries, DiscoveryCards discoveryCards, ConversationCards conversationCards) {
        this.queries = queries;
        this.discoveryCards = discoveryCards;
        this.conversationCards = conversationCards;
    }

    public PlaceDetail assemble(PlaceRow place) {
        var self = new PlaceRef().id(place.publicId()).name(place.name());
        boolean merged = "MERGED".equals(place.status());

        var detail = new PlaceDetail()
            .id(place.publicId())
            .name(place.name())
            .spatialType(SpatialType.fromValue(place.spatialType().toLowerCase(Locale.ROOT)))
            .kinds(place.kinds())
            .aliases(queries.findAliases(place.id()).stream().map(PlaceAssembler::toAlias).toList())
            .state(toState(place))
            .capabilities(new PlaceCapabilities().canStartConversation(!merged))
            .geometry(GeoJsonGeometries.parse(place.geoJson()))
            .breadcrumb(queries.findBreadcrumb(place.id()).stream()
                .map(row -> new PlaceRef().id(row.publicId()).name(row.name())).toList())
            .estimates(queries.findEstimates(place.id()).stream().map(PlaceAssembler::toEstimate).toList())
            .discoveries(toDiscoveryGroups(place.id()))
            .seeking(toSeeking(queries.findSeekingCandidates(place.id(), "AREA".equals(place.spatialType())), self))
            .conversations(new ConversationCardPage()
                .items(conversationCards.cards(queries.findConversationIds(place.id())))
                .totalCount(queries.countConversations(place.id())))
            .projects(new ProjectCardPage()
                .items(queries.findProjects(place.id()).stream()
                    .map(row -> ProjectCards.toCard(row, ProjectCards.Prefer.PLACE)).toList())
                .totalCount(queries.countProjects(place.id())))
            .nearbyPlaces(toNearby(queries.findNearby(place.id())));
        if (place.existedFrom() != null || place.existedTo() != null) {
            var period = new io.github.exday.api.generated.model.Period();
            period.setFrom(place.existedFrom());
            period.setTo(place.existedTo());
            detail.setExistedPeriod(period);
        }
        if (place.stationPublicId() != null) {
            detail.setAccessStation(new PlaceRef().id(place.stationPublicId()).name(place.stationName()));
        }
        return detail;
    }

    private PlaceState toState(PlaceRow place) {
        return switch (place.status()) {
            case "MERGED" -> {
                var state = new MergedPlaceState().status(MergedPlaceState.StatusEnum.MERGED);
                var merge = queries.findFinalMergeTarget(place.id());
                if (merge.depth() > 0 && !merge.hasNext() && merge.targetPublicId() != null) {
                    state.setTarget(new PlaceRef().id(merge.targetPublicId()).name(merge.targetName()));
                }
                yield state;
            }
            case "ABOLISHED" -> new AbolishedPlaceState().status(AbolishedPlaceState.StatusEnum.ABOLISHED);
            default -> new ActivePlaceState().status(ActivePlaceState.StatusEnum.ACTIVE);
        };
    }

    private static PlaceAlias toAlias(PlaceQueries.AliasRow row) {
        var alias = new PlaceAlias()
            .name(row.name())
            .kind("FORMER".equals(row.kind()) ? PlaceAlias.KindEnum.FORMER : PlaceAlias.KindEnum.ALIAS);
        if (row.validFrom() != null || row.validTo() != null) {
            var period = new io.github.exday.api.generated.model.Period();
            period.setFrom(row.validFrom());
            period.setTo(row.validTo());
            alias.setPeriod(period);
        }
        return alias;
    }

    private static PlaceEstimate toEstimate(PlaceQueries.EstimateRow row) {
        var estimate = new PlaceEstimate()
            .metric(row.metric())
            .value(row.value())
            .unit(row.unit())
            .method(row.method())
            .computedAt(row.computedAt());
        estimate.setMethodVersion(row.methodVersion());
        return estimate;
    }

    /**
     * 成立の新しい順に選んだ Discovery を、観点（代表表記の文字コードの順で最初のもの）ごとにまとめる。
     * グループは観点の文字コードの順、観点なしは最後。グループの中は選んだ順（成立の新しい順）のまま。
     */
    private PlaceDiscoveryGroups toDiscoveryGroups(long placeId) {
        var rows = queries.findDiscoveries(placeId);
        var cards = discoveryCards.cards(rows.stream()
            .map(row -> new DiscoveryCards.DiscoveryRow(row.id(), row.publicId(), row.title(), row.summary()))
            .toList());
        var groups = new LinkedHashMap<String, List<PlaceDiscoveryCard>>();
        List<PlaceDiscoveryCard> withoutAspect = new ArrayList<>();
        for (var row : rows) {
            var summary = cards.get(row.id());
            if (summary == null) {
                continue;
            }
            var card = new PlaceDiscoveryCard().discovery(summary).roles(row.roles().stream().sorted().toList());
            if (row.aspect() == null) {
                withoutAspect.add(card);
            } else {
                groups.computeIfAbsent(row.aspect(), key -> new ArrayList<>()).add(card);
            }
        }
        List<PlaceDiscoveryGroup> result = new ArrayList<>();
        groups.keySet().stream().sorted().forEach(aspect ->
            result.add(new PlaceDiscoveryGroup().aspect(aspect).items(groups.get(aspect))));
        if (!withoutAspect.isEmpty()) {
            result.add(new PlaceDiscoveryGroup().items(withoutAspect));
        }
        int total = rows.isEmpty() ? 0 : rows.get(0).total();
        return new PlaceDiscoveryGroups().groups(result).totalCount(total);
    }

    /** 探している話を、会話・問いごとにまとめる（並び・上限・全体の数は SQL で決めてある）。 */
    private SeekingCardPage toSeeking(List<SeekingCandidateRow> rows, PlaceRef self) {
        record Target(boolean question, long id) {}
        var byTarget = new LinkedHashMap<Target, List<SeekingCandidateRow>>();
        for (var row : rows) {
            byTarget.computeIfAbsent(new Target(row.isQuestion(), row.targetId()), key -> new ArrayList<>()).add(row);
        }
        var cards = conversationCards.cardsById(rows.stream().map(SeekingCandidateRow::conversationId).toList());
        List<SeekingCard> items = byTarget.values().stream()
            .map(list -> toSeekingCard(list, cards.get(list.get(0).conversationId()), self))
            .filter(Objects::nonNull)
            .toList();
        return new SeekingCardPage().items(items).totalCount(rows.isEmpty() ? 0 : rows.get(0).total());
    }

    private static SeekingCard toSeekingCard(List<SeekingCandidateRow> candidates,
            io.github.exday.api.generated.model.ConversationCard conversation, PlaceRef self) {
        if (conversation == null) {
            return null;
        }
        List<SeekingCandidate> list = candidates.stream().map(row -> toCandidate(row, self)).toList();
        var first = candidates.get(0);
        if (first.isQuestion()) {
            return new SeekingQuestionCard()
                .kind(SeekingQuestionCard.KindEnum.QUESTION)
                .conversation(conversation)
                .question(new QuestionRef().id(first.questionPublicId()).content(first.questionContent()))
                .candidates(list);
        }
        return new SeekingConversationCard()
            .kind(SeekingConversationCard.KindEnum.CONVERSATION)
            .conversation(conversation)
            .candidates(list);
    }

    private static SeekingCandidate toCandidate(SeekingCandidateRow row, PlaceRef self) {
        if (row.proposedName() != null) {
            return new ProposedPlaceCandidate()
                .id(row.publicId())
                .kind(ProposedPlaceCandidate.KindEnum.PROPOSAL)
                .name(row.proposedName());
        }
        return new ExistingPlaceCandidate()
            .id(row.publicId())
            .kind(ExistingPlaceCandidate.KindEnum.PLACE)
            .place(self);
    }

    private static NearbyPlacePage toNearby(List<PlaceQueries.NearbyRow> rows) {
        var items = rows.stream()
            .map(row -> new NearbyPlace().place(new PlaceRef().id(row.publicId()).name(row.name())).kinds(row.kinds()))
            .toList();
        return new NearbyPlacePage().items(items).totalCount(rows.isEmpty() ? 0 : rows.get(0).total());
    }
}
