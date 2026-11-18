package io.github.exday.api.project;

import io.github.exday.api.card.conversation.ConversationCards;
import io.github.exday.api.card.discovery.DiscoveryCards;
import io.github.exday.api.card.post.PostCards;
import io.github.exday.api.estimate.PlaceEstimates;
import io.github.exday.api.generated.model.ChangeTarget;
import io.github.exday.api.generated.model.ConversationCard;
import io.github.exday.api.generated.model.ConversationCardPage;
import io.github.exday.api.generated.model.DiscoveryChangeTarget;
import io.github.exday.api.generated.model.DiscoveryProjectItem;
import io.github.exday.api.generated.model.DiscoveryRef;
import io.github.exday.api.generated.model.DiscoverySummary;
import io.github.exday.api.generated.model.OlderTimelinePage;
import io.github.exday.api.generated.model.PlaceChangeTarget;
import io.github.exday.api.generated.model.PlaceProjectItem;
import io.github.exday.api.generated.model.PlaceRef;
import io.github.exday.api.generated.model.ProjectChangeEvent;
import io.github.exday.api.generated.model.ProjectChangeItem;
import io.github.exday.api.generated.model.ProjectDetail;
import io.github.exday.api.generated.model.ProjectItem;
import io.github.exday.api.generated.model.ProjectMapPlace;
import io.github.exday.api.generated.model.ProjectTimeline;
import io.github.exday.api.generated.model.ProjectType;
import io.github.exday.api.generated.model.SpatialType;
import io.github.exday.api.generated.model.TimelineChange;
import io.github.exday.api.generated.model.TimelineEntry;
import io.github.exday.api.generated.model.TimelinePost;
import io.github.exday.api.generated.model.UnavailableChangeTarget;
import io.github.exday.api.generated.model.UnavailableProjectItem;
import io.github.exday.api.geometry.GeoJsonGeometries;
import io.github.exday.api.merge.MergeChains;
import io.github.exday.api.merge.MergeChains.Resolution;
import io.github.exday.api.placeconversation.PlaceConversations;
import io.github.exday.api.project.ProjectQueries.ChangeItemRow;
import io.github.exday.api.project.ProjectQueries.ItemRow;
import io.github.exday.api.project.ProjectQueries.PlaceRow;
import io.github.exday.api.project.ProjectQueries.ProjectRow;
import io.github.exday.api.project.ProjectQueries.TimelineKey;
import io.github.exday.api.project.ProjectQueries.TimelineRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** DB から読んだ行を、S15 の {@link ProjectDetail} 等に組み立てる。 */
@Component
public class ProjectAssembler {

    /** 変えた表示の項目の、DB の値と応答の値（この順で返す）。 */
    private static final Map<String, String> FIELDS = new java.util.LinkedHashMap<>();

    static {
        FIELDS.put("TITLE", "title");
        FIELDS.put("CALL_TEXT", "callText");
        FIELDS.put("DESCRIPTION", "description");
    }

    private final ProjectQueries queries;
    private final MergeChains mergeChains;
    private final DiscoveryCards discoveryCards;
    private final ConversationCards conversationCards;
    private final PostCards postCards;
    private final PlaceEstimates placeEstimates;
    private final PlaceConversations placeConversations;

    public ProjectAssembler(ProjectQueries queries, MergeChains mergeChains, DiscoveryCards discoveryCards,
            ConversationCards conversationCards, PostCards postCards, PlaceEstimates placeEstimates,
            PlaceConversations placeConversations) {
        this.queries = queries;
        this.mergeChains = mergeChains;
        this.discoveryCards = discoveryCards;
        this.conversationCards = conversationCards;
        this.postCards = postCards;
        this.placeEstimates = placeEstimates;
        this.placeConversations = placeConversations;
    }

    public ProjectDetail assemble(ProjectRow project, int timelineLimit) {
        boolean ordered = ordered(project);
        var own = queries.findOwnConversation(project.id());
        var detail = new ProjectDetail()
            .id(project.publicId())
            .type(ProjectType.fromValue(project.type().toLowerCase(Locale.ROOT)))
            .title(project.title())
            .creatorKind(ProjectDetail.CreatorKindEnum.fromValue(project.creatorKind().toLowerCase(Locale.ROOT)))
            .creatorName(project.creatorName())
            .items(toItems(queries.findItems(project.id()), ordered));
        if (!project.callText().isEmpty()) {
            detail.callText(project.callText());
        }
        if (!project.description().isEmpty()) {
            detail.description(project.description());
        }
        own.ifPresent(ref -> detail.ownConversationId(ref.publicId()));
        var page = timelinePage(project, own.map(ProjectQueries.IdRef::id).orElse(null), null, timelineLimit);
        var timeline = new ProjectTimeline().items(page.getItems());
        if (page.getOlderCursor() != null) {
            timeline.olderCursor(page.getOlderCursor());
        }
        return detail.timeline(timeline).triggeredConversations(toTriggered(project.id()));
    }

    private static boolean ordered(ProjectRow project) {
        return "COURSE".equals(project.type()) || "RANKING".equals(project.type());
    }

    // ---- 並び（C37） ----

    private List<ProjectItem> toItems(List<ItemRow> rows, boolean ordered) {
        var placeIds = rows.stream().map(ItemRow::placeId).filter(Objects::nonNull).distinct().toList();
        var places = queries.findPlaces(placeIds);
        var estimates = placeEstimates.byPlace(placeIds);
        var placeConversationCounts = placeConversations.countByPlace(placeIds);
        var discoveries = resolveDiscoveries(rows.stream().map(ItemRow::discoveryId).filter(Objects::nonNull).toList());
        var finals = discoveries.values().stream().distinct().toList();
        var summaries = summaries(finals);
        var mapPlaces = queries.findPlacesByPublicId(summaries.values().stream()
            .flatMap(summary -> summary.getDisplayPlaces().stream()).map(PlaceRef::getId).distinct().toList());
        var originCounts = queries.countOriginConversations(finals);
        var startedCounts = queries.countStartedConversations(finals);

        List<ProjectItem> items = new ArrayList<>();
        for (var row : rows) {
            Integer position = ordered ? row.position() : null;
            if (row.placeId() != null) {
                var place = places.get(row.placeId());
                var item = new PlaceProjectItem()
                    .kind(PlaceProjectItem.KindEnum.PLACE)
                    .id(row.publicId())
                    .place(new PlaceRef().id(place.publicId()).name(place.name()))
                    .placeKinds(place.kinds())
                    .spatialType(spatialType(place))
                    .geometry(GeoJsonGeometries.parse(place.geoJson()))
                    .anchor(anchor(place))
                    .estimates(estimates.getOrDefault(row.placeId(), List.of()))
                    .conversationCount(placeConversationCounts.getOrDefault(row.placeId(), 0));
                item.setPosition(position);
                if (!row.note().isEmpty()) {
                    item.note(row.note());
                }
                items.add(item);
                continue;
            }
            Long target = discoveries.get(row.discoveryId());
            var summary = target == null ? null : summaries.get(target);
            if (summary == null) {
                // 指していた Discovery（統合先を含む）が見えない・決められない：位置だけを残す
                var unavailable = new UnavailableProjectItem().kind(UnavailableProjectItem.KindEnum.UNAVAILABLE);
                unavailable.setPosition(position);
                items.add(unavailable);
                continue;
            }
            var shapes = summary.getDisplayPlaces().stream().map(ref -> mapPlaces.get(ref.getId())).toList();
            var item = new DiscoveryProjectItem()
                .kind(DiscoveryProjectItem.KindEnum.DISCOVERY)
                .id(row.publicId())
                .discovery(summary)
                .mapPlaces(shapes.stream().map(place -> new ProjectMapPlace()
                    .place(new PlaceRef().id(place.publicId()).name(place.name()))
                    .spatialType(spatialType(place))
                    .geometry(GeoJsonGeometries.parse(place.geoJson()))).toList())
                .anchor(anchor(shapes.get(0)))
                .originConversationCount(originCounts.getOrDefault(target, 0))
                .startedConversationCount(startedCounts.getOrDefault(target, 0));
            item.setPosition(position);
            if (!row.note().isEmpty()) {
                item.note(row.note());
            }
            items.add(item);
        }
        return items;
    }

    /** Discovery（DB の id）ごとの、案内する先（統合されていなければ自身、されていれば最後の統合先）。決められないものは入らない。 */
    private Map<Long, Long> resolveDiscoveries(Collection<Long> discoveryIds) {
        var resolutions = mergeChains.discoveries(List.copyOf(new LinkedHashSet<>(discoveryIds)));
        return resolutions.values().stream().filter(Resolution::resolved)
            .collect(Collectors.toMap(Resolution::startId, Resolution::finalId));
    }

    /** 公開で、カードにできる（関わる場所がある）Discovery のカード。 */
    private Map<Long, DiscoverySummary> summaries(Collection<Long> discoveryIds) {
        var visible = queries.findVisibleDiscoveries(discoveryIds);
        return discoveryCards.cards(visible.values().stream()
            .map(row -> new DiscoveryCards.DiscoveryRow(row.id(), row.publicId(), row.title(), row.summary()))
            .toList());
    }

    private static SpatialType spatialType(PlaceRow place) {
        return SpatialType.fromValue(place.spatialType().toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List anchor(PlaceRow place) {
        return List.of(place.anchorLon(), place.anchorLat());
    }

    // ---- タイムライン ----

    /** カーソルより前のタイムライン。見つからない（企画・カーソルが違う）なら空。 */
    public Optional<OlderTimelinePage> olderTimeline(ProjectRow project, String cursor, int limit) {
        var own = queries.findOwnConversation(project.id());
        boolean change = cursor.charAt(0) == 'c';
        String publicId = cursor.substring(1);
        Optional<java.time.OffsetDateTime> at = change
            ? queries.findChangeKey(project.id(), publicId)
            : own.flatMap(ref -> queries.findPostKey(ref.id(), publicId));
        return at.map(time -> timelinePage(project, own.map(ProjectQueries.IdRef::id).orElse(null),
            new TimelineKey(time, change, publicId), limit));
    }

    private OlderTimelinePage timelinePage(ProjectRow project, Long ownConversationId, TimelineKey before, int limit) {
        // 1件多く読み、続きがあるかを知る
        var rows = new ArrayList<>(queries.findTimeline(project.id(), ownConversationId, before, limit + 1));
        boolean hasOlder = rows.size() > limit;
        if (hasOlder) {
            rows.remove(rows.size() - 1);
        }
        Collections.reverse(rows);
        var posts = postCards.cards(rows.stream().filter(row -> !row.change()).map(TimelineRow::id).toList());
        var changes = toChanges(project, rows.stream().filter(TimelineRow::change).map(TimelineRow::id).toList());
        List<TimelineEntry> entries = rows.stream().map(row -> row.change()
                ? (TimelineEntry) new TimelineChange().kind(TimelineChange.KindEnum.CHANGE).change(changes.get(row.id()))
                : new TimelinePost().kind(TimelinePost.KindEnum.POST).post(posts.get(row.id())))
            .toList();
        var page = new OlderTimelinePage().items(entries);
        if (hasOlder) {
            var first = rows.get(0);
            page.olderCursor((first.change() ? "c" : "p") + first.publicId());
        }
        return page;
    }

    private Map<Long, ProjectChangeEvent> toChanges(ProjectRow project, List<Long> changeIds) {
        if (changeIds.isEmpty()) {
            return Map.of();
        }
        boolean ordered = ordered(project);
        var changes = queries.findChanges(changeIds);
        var items = queries.findChangeItems(changeIds);
        var places = queries.findPlaces(items.stream().map(ChangeItemRow::placeId).filter(Objects::nonNull).distinct().toList());
        var discoveries = resolveDiscoveries(items.stream().map(ChangeItemRow::discoveryId).filter(Objects::nonNull).toList());
        var visible = queries.findVisibleDiscoveries(discoveries.values().stream().distinct().toList());
        var itemsByChange = items.stream().collect(Collectors.groupingBy(ChangeItemRow::changeId));
        var events = new HashMap<Long, ProjectChangeEvent>();
        for (var change : changes.values()) {
            var event = new ProjectChangeEvent()
                .id(change.publicId())
                .kind(ProjectChangeEvent.KindEnum.fromValue(change.kind().toLowerCase(Locale.ROOT)))
                .changedAt(change.changedAt())
                .items(itemsByChange.getOrDefault(change.id(), List.of()).stream()
                    .map(item -> toChangeItem(item, ordered, places, discoveries, visible)).toList())
                .changedFields(FIELDS.entrySet().stream().filter(field -> change.fieldsChanged().contains(field.getKey()))
                    .map(field -> ProjectChangeEvent.ChangedFieldsEnum.fromValue(field.getValue())).toList());
            events.put(change.id(), event);
        }
        return events;
    }

    private static ProjectChangeItem toChangeItem(ChangeItemRow row, boolean ordered, Map<Long, PlaceRow> places,
            Map<Long, Long> discoveries, Map<Long, ProjectQueries.DiscoveryRow> visible) {
        var item = new ProjectChangeItem()
            .kind(ProjectChangeItem.KindEnum.fromValue(row.kind().toLowerCase(Locale.ROOT)))
            .target(toTarget(row, places, discoveries, visible));
        if (ordered) {
            item.setBeforePosition(row.beforePosition());
            item.setAfterPosition(row.afterPosition());
        }
        return item;
    }

    private static ChangeTarget toTarget(ChangeItemRow row, Map<Long, PlaceRow> places, Map<Long, Long> discoveries,
            Map<Long, ProjectQueries.DiscoveryRow> visible) {
        if (row.placeId() != null) {
            var place = places.get(row.placeId());
            return new PlaceChangeTarget().kind(PlaceChangeTarget.KindEnum.PLACE)
                .place(new PlaceRef().id(place.publicId()).name(place.name()));
        }
        Long target = discoveries.get(row.discoveryId());
        var discovery = target == null ? null : visible.get(target);
        if (discovery == null) {
            return new UnavailableChangeTarget().kind(UnavailableChangeTarget.KindEnum.UNAVAILABLE);
        }
        return new DiscoveryChangeTarget().kind(DiscoveryChangeTarget.KindEnum.DISCOVERY)
            .discovery(new DiscoveryRef().id(discovery.publicId()).title(discovery.title()));
    }

    // ---- 企画をきっかけにした会話（C40） ----

    private ConversationCardPage toTriggered(long projectId) {
        var ids = queries.findTriggeredConversationIds(projectId);
        var cards = conversationCards.cardsById(ids);
        List<ConversationCard> items = ids.stream().map(cards::get).filter(Objects::nonNull).toList();
        return new ConversationCardPage().items(items).totalCount(queries.countTriggeredConversations(projectId));
    }
}
