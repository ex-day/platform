package io.github.exday.api.top;

import io.github.exday.api.card.conversation.ConversationCards;
import io.github.exday.api.card.discovery.DiscoveryCards;
import io.github.exday.api.card.project.ProjectListCards;
import io.github.exday.api.context.Seasons;
import io.github.exday.api.generated.model.AwaitingVoicesSection;
import io.github.exday.api.generated.model.CurrentSeason;
import io.github.exday.api.generated.model.DiscoverySummary;
import io.github.exday.api.generated.model.EstimateGroup;
import io.github.exday.api.generated.model.EstimateSection;
import io.github.exday.api.generated.model.EstimatedPlace;
import io.github.exday.api.generated.model.ExampleSection;
import io.github.exday.api.generated.model.ExistingPlaceCandidate;
import io.github.exday.api.generated.model.NearbyDiscoverySection;
import io.github.exday.api.generated.model.NearbyPlace;
import io.github.exday.api.generated.model.OutsideStartAreaSection;
import io.github.exday.api.generated.model.PlaceEstimate;
import io.github.exday.api.generated.model.PlaceRef;
import io.github.exday.api.generated.model.ProposedPlaceCandidate;
import io.github.exday.api.generated.model.QuestionRef;
import io.github.exday.api.generated.model.SeasonalDiscoveryCard;
import io.github.exday.api.generated.model.SeasonalSection;
import io.github.exday.api.generated.model.SeekingCandidate;
import io.github.exday.api.generated.model.TopAreaCounts;
import io.github.exday.api.generated.model.TopDiscoveryCard;
import io.github.exday.api.generated.model.TopOrigin;
import io.github.exday.api.generated.model.TopPage;
import io.github.exday.api.generated.model.TopProjectSection;
import io.github.exday.api.generated.model.TopSeasonalValue;
import io.github.exday.api.generated.model.TopSection;
import io.github.exday.api.generated.model.TopSeekingCard;
import io.github.exday.api.generated.model.TopSeekingSection;
import io.github.exday.api.generated.model.TopSmallMap;
import io.github.exday.api.merge.MergeChains;
import io.github.exday.api.top.TopQueries.DiscoveryRow;
import io.github.exday.api.top.TopQueries.Origin;
import io.github.exday.api.top.TopQueries.SeekingRow;
import io.github.exday.api.top.TopQueries.Upper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S01（TOP）の応答を、1つの読み取りの時点（スナップショット）でそろえて作る（apps/api/AGENTS.md「読み取りの一貫性と公開制御」）。
 *
 * <p>枠は F01 の優先の順に作り、全体の予算（{@link #BUDGET}）を枠の順に配る。各枠は配られた数より1件多く読み、
 * 超えたら {@code hasMore}。1つの Discovery・場所は先に出た枠にだけ置く（画面定義 S01「取得用 API で返すもの（案）」）。
 */
@Service
public class TopService {

    /** 「近く」の距離（POINT・ROUTE の起点。暫定）。AREA の起点は 0（交わるもの）。 */
    static final double NEAR_METERS = 3000;
    /** TOP の全体の件数の予算。 */
    static final int BUDGET = 50;
    static final int OUTSIDE_LIMIT = 1;
    static final int SEASONAL_LIMIT = 8;
    static final int NATIONWIDE_LIMIT = 2;
    static final int NEARBY_LIMIT = 12;
    static final int PROJECT_LIMIT = 6;
    static final int ESTIMATE_LIMIT = 6;
    static final int METRIC_LIMIT = 2;
    static final int PER_METRIC = 3;
    static final int EXAMPLE_LIMIT = 6;
    static final int SEEKING_LIMIT = 6;
    static final int PER_SEEKING_CARD = 3;
    static final int AWAITING_LIMIT = 6;
    /** 小さな地図の使える時間（分。往復）。S01「小さな地図と S12 の条件」。 */
    static final int SMALL_MAP_MINUTES = 120;

    private final TopQueries queries;
    private final MergeChains mergeChains;
    private final DiscoveryCards discoveryCards;
    private final ProjectListCards projectCards;
    private final ConversationCards conversationCards;
    private final Clock clock;
    private final TopProperties properties;

    public TopService(TopQueries queries, MergeChains mergeChains, DiscoveryCards discoveryCards,
            ProjectListCards projectCards, ConversationCards conversationCards, Clock clock, TopProperties properties) {
        this.queries = queries;
        this.mergeChains = mergeChains;
        this.discoveryCards = discoveryCards;
        this.projectCards = projectCards;
        this.conversationCards = conversationCards;
        this.clock = clock;
        this.properties = properties;
    }

    /** 1つの応答を作る間の状態（予算の残り、使った Discovery・場所）。 */
    private static final class Budget {
        int remaining = BUDGET;
        final Set<Long> usedDiscoveries = new HashSet<>();
        final Set<Long> usedPlaces = new HashSet<>();

        int allocate(int limit) {
            return Math.min(limit, remaining);
        }

        void spend(int count) {
            remaining -= count;
        }
    }

    /** 起点がない（{@code from} の場所がない、統合の先を決められない）ときは {@link TopOriginNotFoundException}。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TopPage find(String from) {
        var now = OffsetDateTime.now(clock);
        CurrentSeason season = Seasons.of(now.getMonth());
        String seasonValue = season.getValue().toUpperCase(Locale.ROOT);

        boolean requested = from != null;
        String originPublicId = requested ? from : properties.defaultOriginPlaceId();
        long startId = queries.findPlaceId(originPublicId).orElseThrow(TopOriginNotFoundException::new);
        var resolution = mergeChains.places(List.of(startId)).get(startId);
        if (resolution == null || !resolution.resolved()) {
            throw new TopOriginNotFoundException();
        }
        Origin origin = queries.findOrigin(resolution.finalId());
        List<Upper> upper = queries.findUpper(origin.id());
        double radius = "AREA".equals(origin.spatialType()) ? 0 : NEAR_METERS;
        var excluded = new ArrayList<Long>();
        excluded.add(origin.id());
        upper.forEach(place -> excluded.add(place.id()));
        Optional<PlaceRef> adminArea = adminArea(origin, upper);
        Optional<Long> adminId = adminAreaId(origin, upper);
        boolean outside = isOutsideStartArea(origin, upper);

        var topOrigin = new TopOrigin()
            .place(ref(origin.publicId(), origin.name()))
            .source(requested ? TopOrigin.SourceEnum.REQUESTED : TopOrigin.SourceEnum.DEFAULT)
            .outsideStartArea(outside);
        adminArea.ifPresent(topOrigin::adminArea);

        var budget = new Budget();
        var sections = new ArrayList<TopSection>();
        if (outside) {
            outsideSection(origin, budget).ifPresent(sections::add);
        }
        seasonalSection(origin, radius, adminId, seasonValue, budget).ifPresent(sections::add);
        nearbySection(origin, radius, budget).ifPresent(sections::add);
        projectSection(budget).ifPresent(sections::add);
        estimateSection(origin, radius, excluded, budget).ifPresent(sections::add);
        exampleSection(origin, budget).ifPresent(sections::add);
        seekingSection(origin, radius, budget).ifPresent(sections::add);
        awaitingSection(origin, radius, excluded, budget).ifPresent(sections::add);

        return new TopPage()
            .now(now)
            .season(season)
            .origin(topOrigin)
            .smallMap(new TopSmallMap().minutes(SMALL_MAP_MINUTES).trip(TopSmallMap.TripEnum.ROUND_TRIP))
            .areaCounts(new TopAreaCounts()
                .placeCount(queries.countPlaces(origin.id(), radius, excluded))
                .voicedDiscoveryCount(queries.countVoiced(origin.id(), radius)))
            .sections(sections);
    }

    // ---- 起点 ----

    /**
     * 同じ行政の地域：起点が「市区町村」の AREA なら起点そのもの。ほかは関係で親へたどった「市区町村」のいちばん近い段、
     * なければ形が起点の形を覆う「市区町村」のうち面の小さいもの。
     */
    private static Optional<Upper> adminAreaPlace(Origin origin, List<Upper> upper) {
        var related = upper.stream()
            .filter(place -> place.municipality() && place.depth() != null)
            .min(Comparator.comparingInt(Upper::depth).thenComparing(Upper::publicId));
        if (related.isPresent()) {
            return related;
        }
        return upper.stream()
            .filter(place -> place.municipality() && place.covers())
            .min(Comparator.comparingDouble(Upper::area).thenComparing(Upper::publicId));
    }

    private static boolean isOriginMunicipality(Origin origin) {
        return "AREA".equals(origin.spatialType()) && origin.municipality();
    }

    private static Optional<PlaceRef> adminArea(Origin origin, List<Upper> upper) {
        if (isOriginMunicipality(origin)) {
            return Optional.of(ref(origin.publicId(), origin.name()));
        }
        return adminAreaPlace(origin, upper).map(place -> ref(place.publicId(), place.name()));
    }

    private static Optional<Long> adminAreaId(Origin origin, List<Upper> upper) {
        if (isOriginMunicipality(origin)) {
            return Optional.of(origin.id());
        }
        return adminAreaPlace(origin, upper).map(Upper::id);
    }

    /** 起点が始める地域そのもの、またはその上位の場所に始める地域があれば中。設定がない・場所がないときは中として扱う。 */
    private boolean isOutsideStartArea(Origin origin, List<Upper> upper) {
        var configured = properties.startAreaPlaceId();
        if (configured == null || configured.isBlank()) {
            return false;
        }
        var startArea = queries.findPlaceId(configured);
        if (startArea.isEmpty()) {
            return false;
        }
        long id = startArea.get();
        return origin.id() != id && upper.stream().noneMatch(place -> place.id() == id);
    }

    // ---- 枠 ----

    private Optional<TopSection> outsideSection(Origin origin, Budget budget) {
        int n = budget.allocate(OUTSIDE_LIMIT);
        if (n == 0) {
            return Optional.empty();
        }
        var rows = queries.findAny(origin.id(), budget.usedDiscoveries, 1);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var cards = discoveryCards(rows);
        if (cards.isEmpty()) {
            return Optional.empty();
        }
        use(budget, rows.subList(0, 1));
        return Optional.of(new OutsideStartAreaSection()
            .kind(OutsideStartAreaSection.KindEnum.OUTSIDE_START_AREA)
            .nearestDiscovery(cards.get(0)));
    }

    private Optional<TopSection> seasonalSection(Origin origin, double radius, Optional<Long> adminId,
            String season, Budget budget) {
        int n = budget.allocate(SEASONAL_LIMIT);
        if (n == 0) {
            return Optional.empty();
        }
        var picked = new ArrayList<DiscoveryRow>();
        var scopes = new ArrayList<SeasonalDiscoveryCard.ScopeEnum>();
        boolean hasMore;
        // 1. 近く
        var near = queries.findSeasonalNear(origin.id(), radius, season, budget.usedDiscoveries, n + 1);
        hasMore = near.size() > n;
        addAll(picked, scopes, near.subList(0, Math.min(n, near.size())), SeasonalDiscoveryCard.ScopeEnum.NEARBY);
        // 2. 同じ行政の地域（近くで足りなければ補う。ちょうど足りたときも、続きがあるかを1件で確かめる）
        if (!hasMore && adminId.isPresent()) {
            var used = new HashSet<>(budget.usedDiscoveries);
            picked.forEach(row -> used.add(row.id()));
            int rest = n - picked.size();
            var inArea = queries.findSeasonalInAdminArea(origin.id(), adminId.get(), season, used, rest + 1);
            hasMore = inArea.size() > rest;
            addAll(picked, scopes, inArea.subList(0, Math.min(rest, inArea.size())),
                SeasonalDiscoveryCard.ScopeEnum.ADMIN_AREA);
        }
        // 3. 全国（1・2 で1件もなければ）
        if (picked.isEmpty()) {
            int k = Math.min(NATIONWIDE_LIMIT, n);
            var any = queries.findSeasonalAny(origin.id(), season, budget.usedDiscoveries, k + 1);
            hasMore = any.size() > k;
            addAll(picked, scopes, any.subList(0, Math.min(k, any.size())),
                SeasonalDiscoveryCard.ScopeEnum.NATIONWIDE);
        }
        if (picked.isEmpty()) {
            return Optional.empty();
        }
        var summaries = summaries(picked);
        var values = queries.findSeasonalValues(picked.stream().map(DiscoveryRow::id).toList(), season);
        var items = new ArrayList<SeasonalDiscoveryCard>();
        for (int i = 0; i < picked.size(); i++) {
            var row = picked.get(i);
            var summary = summaries.get(row.id());
            var value = values.get(row.id());
            if (summary == null || value == null) {
                continue;
            }
            items.add(new SeasonalDiscoveryCard()
                .card(toCard(row, summary))
                .value(new TopSeasonalValue().id(value.publicId()).description(value.description()))
                .scope(scopes.get(i)));
        }
        if (items.isEmpty()) {
            return Optional.empty();
        }
        use(budget, picked);
        budget.spend(items.size() - picked.size());
        return Optional.of(new SeasonalSection()
            .kind(SeasonalSection.KindEnum.SEASONAL)
            .items(items)
            .hasMore(hasMore));
    }

    private Optional<TopSection> nearbySection(Origin origin, double radius, Budget budget) {
        int n = budget.allocate(NEARBY_LIMIT);
        if (n == 0) {
            return Optional.empty();
        }
        var rows = queries.findNear(origin.id(), radius, budget.usedDiscoveries, n + 1);
        boolean hasMore = rows.size() > n;
        var picked = rows.subList(0, Math.min(n, rows.size()));
        var cards = discoveryCards(picked);
        if (cards.isEmpty()) {
            return Optional.empty();
        }
        use(budget, picked);
        budget.spend(cards.size() - picked.size());
        return Optional.of(new NearbyDiscoverySection()
            .kind(NearbyDiscoverySection.KindEnum.NEARBY_DISCOVERIES)
            .items(cards)
            .hasMore(hasMore));
    }

    private Optional<TopSection> projectSection(Budget budget) {
        int n = budget.allocate(PROJECT_LIMIT);
        if (n == 0) {
            return Optional.empty();
        }
        var rows = queries.findProjects(n + 1);
        boolean hasMore = rows.size() > n;
        var cards = projectCards.cards(rows.subList(0, Math.min(n, rows.size())));
        if (cards.isEmpty()) {
            return Optional.empty();
        }
        budget.spend(cards.size());
        return Optional.of(new TopProjectSection()
            .kind(TopProjectSection.KindEnum.PROJECTS)
            .items(cards)
            .hasMore(hasMore));
    }

    private Optional<TopSection> estimateSection(Origin origin, double radius, List<Long> excluded, Budget budget) {
        int n = budget.allocate(ESTIMATE_LIMIT);
        if (n == 0) {
            return Optional.empty();
        }
        // 出す数より1つ多い指標・1件多い場所まで読み、出さないものがあれば hasMore
        var rows = queries.findEstimates(origin.id(), radius, excluded, METRIC_LIMIT + 1, PER_METRIC + 1);
        var groups = new LinkedHashMap<String, EstimateGroup>();
        int taken = 0;
        boolean hasMore = false;
        for (var row : rows) {
            if (row.metricRank() > METRIC_LIMIT || row.rank() > PER_METRIC || taken >= n) {
                hasMore = true;
                continue;
            }
            var estimate = new PlaceEstimate()
                .metric(row.metric())
                .value(row.value())
                .unit(row.unit())
                .method(row.method())
                .computedAt(row.computedAt());
            estimate.setMethodVersion(row.methodVersion());
            groups.computeIfAbsent(row.metric(), metric -> new EstimateGroup().metric(metric).items(new ArrayList<>()))
                .addItemsItem(new EstimatedPlace().place(ref(row.placePublicId(), row.placeName())).estimate(estimate));
            budget.usedPlaces.add(row.placeId());
            taken++;
        }
        if (groups.isEmpty()) {
            return Optional.empty();
        }
        budget.spend(taken);
        return Optional.of(new EstimateSection()
            .kind(EstimateSection.KindEnum.ESTIMATES)
            .groups(new ArrayList<>(groups.values()))
            .hasMore(hasMore));
    }

    private Optional<TopSection> exampleSection(Origin origin, Budget budget) {
        int n = budget.allocate(EXAMPLE_LIMIT);
        if (n == 0) {
            return Optional.empty();
        }
        var rows = queries.findExamples(origin.id(), budget.usedDiscoveries, n + 1);
        boolean hasMore = rows.size() > n;
        var picked = rows.subList(0, Math.min(n, rows.size()));
        var cards = discoveryCards(picked);
        if (cards.isEmpty()) {
            return Optional.empty();
        }
        use(budget, picked);
        budget.spend(cards.size() - picked.size());
        return Optional.of(new ExampleSection()
            .kind(ExampleSection.KindEnum.EXAMPLES)
            .items(cards)
            .hasMore(hasMore));
    }

    private Optional<TopSection> seekingSection(Origin origin, double radius, Budget budget) {
        int n = budget.allocate(SEEKING_LIMIT);
        if (n == 0) {
            return Optional.empty();
        }
        var rows = queries.findSeeking(origin.id(), radius, n + 1, PER_SEEKING_CARD);
        boolean hasMore = rows.stream().anyMatch(row -> row.rank() > n);
        var byConversation = new LinkedHashMap<Long, List<SeekingRow>>();
        rows.stream()
            .filter(row -> row.rank() <= n)
            .forEach(row -> byConversation.computeIfAbsent(row.conversationId(), id -> new ArrayList<>()).add(row));
        if (byConversation.isEmpty()) {
            return Optional.empty();
        }
        var conversations = conversationCards.cardsById(byConversation.keySet());
        var items = new ArrayList<TopSeekingCard>();
        for (var entry : byConversation.entrySet()) {
            var conversation = conversations.get(entry.getKey());
            if (conversation == null) {
                continue;
            }
            var questions = new ArrayList<QuestionRef>();
            var candidates = new ArrayList<SeekingCandidate>();
            Double nearDistance = null;
            for (var row : entry.getValue()) {
                nearDistance = row.nearDistance();
                if ("Q".equals(row.item())) {
                    questions.add(new QuestionRef().id(row.publicId()).content(row.content()));
                } else if ("C".equals(row.item())) {
                    candidates.add(toCandidate(row));
                }
            }
            var card = new TopSeekingCard()
                .conversation(conversation)
                .questions(questions)
                .candidates(candidates);
            if (nearDistance != null) {
                card.setNearestCandidateDistanceMeters((int) Math.round(nearDistance));
            }
            items.add(card);
        }
        if (items.isEmpty()) {
            return Optional.empty();
        }
        budget.spend(items.size());
        return Optional.of(new TopSeekingSection()
            .kind(TopSeekingSection.KindEnum.SEEKING)
            .items(items)
            .hasMore(hasMore));
    }

    private Optional<TopSection> awaitingSection(Origin origin, double radius, List<Long> excluded, Budget budget) {
        int n = budget.allocate(AWAITING_LIMIT);
        if (n == 0) {
            return Optional.empty();
        }
        var rows = queries.findAwaiting(origin.id(), radius, excluded, budget.usedPlaces, n + 1);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        boolean hasMore = rows.size() > n;
        var items = rows.subList(0, Math.min(n, rows.size())).stream()
            .map(row -> new NearbyPlace().place(ref(row.publicId(), row.name())).kinds(row.kinds()))
            .toList();
        budget.spend(items.size());
        return Optional.of(new AwaitingVoicesSection()
            .kind(AwaitingVoicesSection.KindEnum.AWAITING_VOICES)
            .items(items)
            .hasMore(hasMore));
    }

    // ---- 部品 ----

    private static void addAll(List<DiscoveryRow> picked, List<SeasonalDiscoveryCard.ScopeEnum> scopes,
            List<DiscoveryRow> rows, SeasonalDiscoveryCard.ScopeEnum scope) {
        for (var row : rows) {
            picked.add(row);
            scopes.add(scope);
        }
    }

    /** 選んだ Discovery を使ったものにし、その数を予算から引く。 */
    private static void use(Budget budget, List<DiscoveryRow> rows) {
        rows.forEach(row -> budget.usedDiscoveries.add(row.id()));
        budget.spend(rows.size());
    }

    private Map<Long, DiscoverySummary> summaries(List<DiscoveryRow> rows) {
        return discoveryCards.cards(rows.stream()
            .map(row -> new DiscoveryCards.DiscoveryRow(row.id(), row.publicId(), row.title(), row.summary()))
            .toList());
    }

    /** 渡した順の TOP の Discovery のカード（カードを作れない Discovery は除く）。 */
    private List<TopDiscoveryCard> discoveryCards(List<DiscoveryRow> rows) {
        var summaries = summaries(rows);
        var cards = new ArrayList<TopDiscoveryCard>();
        for (var row : rows) {
            var summary = summaries.get(row.id());
            if (summary != null) {
                cards.add(toCard(row, summary));
            }
        }
        return cards;
    }

    private static TopDiscoveryCard toCard(DiscoveryRow row, DiscoverySummary summary) {
        return new TopDiscoveryCard()
            .discovery(summary)
            .nearestPlace(ref(row.placePublicId(), row.placeName()))
            .distanceMeters((int) Math.round(row.distance()))
            .isExample(row.example());
    }

    private static SeekingCandidate toCandidate(SeekingRow row) {
        if (row.proposedName() != null) {
            return new ProposedPlaceCandidate()
                .id(row.publicId())
                .kind(ProposedPlaceCandidate.KindEnum.PROPOSAL)
                .name(row.proposedName());
        }
        return new ExistingPlaceCandidate()
            .id(row.publicId())
            .kind(ExistingPlaceCandidate.KindEnum.PLACE)
            .place(ref(row.placePublicId(), row.placeName()));
    }

    private static PlaceRef ref(String publicId, String name) {
        return new PlaceRef().id(publicId).name(name);
    }
}
