package io.github.exday.api.conversation;

import io.github.exday.api.card.discovery.DiscoveryCards;
import io.github.exday.api.card.finding.FindingCards;
import io.github.exday.api.conversation.ConversationQueries.CandidateRow;
import io.github.exday.api.conversation.ConversationQueries.ConversationRow;
import io.github.exday.api.conversation.ConversationQueries.IdRef;
import io.github.exday.api.conversation.ConversationQueries.Key;
import io.github.exday.api.conversation.ConversationQueries.OwnedRef;
import io.github.exday.api.conversation.ConversationQueries.PostRow;
import io.github.exday.api.conversation.ConversationQueries.QuestionRow;
import io.github.exday.api.generated.model.ActiveCandidateAdoption;
import io.github.exday.api.generated.model.AdoptionActor;
import io.github.exday.api.generated.model.AdoptionStatus;
import io.github.exday.api.generated.model.AnswerFindingRef;
import io.github.exday.api.generated.model.AnswerPostRef;
import io.github.exday.api.generated.model.AvailableAnswerFindingRef;
import io.github.exday.api.generated.model.AvailableAnswerPostRef;
import io.github.exday.api.generated.model.CandidateAdoption;
import io.github.exday.api.generated.model.CandidateExistingPlace;
import io.github.exday.api.generated.model.CandidatePlace;
import io.github.exday.api.generated.model.CandidateProposedPlace;
import io.github.exday.api.generated.model.CandidateTarget;
import io.github.exday.api.generated.model.ConversationCandidateTarget;
import io.github.exday.api.generated.model.ConversationDetail;
import io.github.exday.api.generated.model.ConversationEntry;
import io.github.exday.api.generated.model.ConversationPlaceItem;
import io.github.exday.api.generated.model.DiscoveryEntry;
import io.github.exday.api.generated.model.DiscoveryRef;
import io.github.exday.api.generated.model.DiscoverySummary;
import io.github.exday.api.generated.model.DiscoverySummaryPage;
import io.github.exday.api.generated.model.FindingItem;
import io.github.exday.api.generated.model.FindingPage;
import io.github.exday.api.generated.model.FindingQuestionAnswer;
import io.github.exday.api.generated.model.HiddenPost;
import io.github.exday.api.generated.model.MovedItem;
import io.github.exday.api.generated.model.OlderPostPage;
import io.github.exday.api.generated.model.Period;
import io.github.exday.api.generated.model.PlaceCandidateItem;
import io.github.exday.api.generated.model.PlaceCandidateItemVoices;
import io.github.exday.api.generated.model.PlaceCandidateSection;
import io.github.exday.api.generated.model.PlaceEntry;
import io.github.exday.api.generated.model.PlaceQuestionAnswer;
import io.github.exday.api.generated.model.PlaceRef;
import io.github.exday.api.generated.model.PostItem;
import io.github.exday.api.generated.model.PostQuestionAnswer;
import io.github.exday.api.generated.model.PostThread;
import io.github.exday.api.generated.model.ProjectEntry;
import io.github.exday.api.generated.model.ProjectRef;
import io.github.exday.api.generated.model.QuestionAnswer;
import io.github.exday.api.generated.model.QuestionCandidateTarget;
import io.github.exday.api.generated.model.QuestionItem;
import io.github.exday.api.generated.model.QuestionItemReactions;
import io.github.exday.api.generated.model.QuestionSection;
import io.github.exday.api.generated.model.ReachPage;
import io.github.exday.api.generated.model.ReachPlace;
import io.github.exday.api.generated.model.ReconsideringCandidateAdoption;
import io.github.exday.api.generated.model.RevokedCandidateAdoption;
import io.github.exday.api.generated.model.SpatialType;
import io.github.exday.api.generated.model.UnavailableAnswerRef;
import io.github.exday.api.generated.model.VisiblePost;
import io.github.exday.api.generated.model.VisiblePostReactions;
import io.github.exday.api.geometry.GeoJsonGeometries;
import io.github.exday.api.merge.MergeChains;
import io.github.exday.api.merge.MergeChains.Resolution;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** DB から読んだ行を、S14 の {@link ConversationDetail} 等に組み立てる。 */
@Component
public class ConversationAssembler {

    private final ConversationQueries queries;
    private final MergeChains mergeChains;
    private final DiscoveryCards discoveryCards;
    private final FindingCards findingCards;

    public ConversationAssembler(ConversationQueries queries, MergeChains mergeChains, DiscoveryCards discoveryCards,
            FindingCards findingCards) {
        this.queries = queries;
        this.mergeChains = mergeChains;
        this.discoveryCards = discoveryCards;
        this.findingCards = findingCards;
    }

    public ConversationDetail assemble(ConversationRow conversation, int postLimit, int findingLimit) {
        var detail = new ConversationDetail()
            .id(conversation.publicId())
            .startedAt(conversation.createdAt())
            .publicPostCount(conversation.publicPostCount())
            .places(queries.findPlaces(conversation.id()).stream().map(ConversationAssembler::toPlace).toList())
            .posts(toThread(conversation.id(), postLimit))
            .placeCandidates(toCandidates(queries.findCandidates(conversation.id())))
            .questions(toQuestions(conversation.id()))
            .findings(findings(conversation.id(), null, findingLimit)
                .moved(moved(queries.findMergedFindings(conversation.id()), conversation.id(),
                    mergeChains::findings, queries::findVisibleFindings, ConversationQueries.MOVED_FINDINGS_LIMIT)))
            .discoveries(toDiscoveries(conversation.id()))
            .reach(toReach(conversation.id()));
        toEntry(conversation).ifPresent(detail::entry);
        return detail;
    }

    // ---- 入口 ----

    private Optional<ConversationEntry> toEntry(ConversationRow conversation) {
        if (conversation.entryPlaceId() != null) {
            return queries.findPlaceRef(conversation.entryPlaceId())
                .map(ref -> new PlaceEntry().kind(PlaceEntry.KindEnum.PLACE).place(placeRef(ref)));
        }
        if (conversation.entryProjectId() != null) {
            return queries.findProjectRef(conversation.entryProjectId())
                .map(ref -> new ProjectEntry()
                    .kind(ProjectEntry.KindEnum.PROJECT)
                    .project(new ProjectRef().id(ref.publicId()).title(ref.name()))
                    .relation(conversation.projectOwn() ? ProjectEntry.RelationEnum.OWN
                        : ProjectEntry.RelationEnum.TRIGGERED));
        }
        if (conversation.entryDiscoveryId() != null) {
            // 統合されていれば最後の統合先（S03 と同じ決まり）。決められない・見えないなら入口を返さない
            var resolution = mergeChains.discoveries(List.of(conversation.entryDiscoveryId()))
                .get(conversation.entryDiscoveryId());
            if (!resolution.resolved()) {
                return Optional.empty();
            }
            return queries.findDiscoveryRef(resolution.finalId())
                .map(ref -> new DiscoveryEntry()
                    .kind(DiscoveryEntry.KindEnum.DISCOVERY)
                    .discovery(new DiscoveryRef().id(ref.publicId()).title(ref.name())));
        }
        return Optional.empty();
    }

    // ---- 場所の状態 ----

    private static ConversationPlaceItem toPlace(ConversationQueries.PlaceRow row) {
        var item = new ConversationPlaceItem()
            .place(new PlaceRef().id(row.publicId()).name(row.name()))
            .status(adoptionStatus(row.status()))
            .source(row.fromQuestion() ? ConversationPlaceItem.SourceEnum.QUESTION
                : ConversationPlaceItem.SourceEnum.CONVERSATION);
        if (row.role() != null) {
            item.role(row.role());
        }
        if (row.seq() != null) {
            item.order(row.seq());
        }
        return item;
    }

    // ---- Post ----

    private PostThread toThread(long conversationId, int limit) {
        // 公開の会話には最初の Post がある（会話は最初の Post とともに作る）
        var first = queries.findFirstPost(conversationId)
            .orElseThrow(() -> new IllegalStateException("会話に Post がない: " + conversationId));
        var page = olderPage(conversationId, first.id(), null, limit);
        var thread = new PostThread().first(toPost(first)).recent(page.getItems());
        if (page.getOlderCursor() != null) {
            thread.olderCursor(page.getOlderCursor());
        }
        return thread;
    }

    /** カーソルより前の Post。見つからない（会話・カーソルが違う）なら空。 */
    public Optional<OlderPostPage> olderPosts(long conversationId, String before, int limit) {
        var first = queries.findFirstPost(conversationId);
        var key = queries.findPostKey(conversationId, before);
        if (first.isEmpty() || key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(olderPage(conversationId, first.get().id(), key.get(), limit));
    }

    private OlderPostPage olderPage(long conversationId, long firstPostId, Key before, int limit) {
        // 1件多く読み、続きがあるかを知る
        var rows = new ArrayList<>(queries.findPostsBefore(conversationId, firstPostId, before, limit + 1));
        boolean hasOlder = rows.size() > limit;
        if (hasOlder) {
            rows.remove(rows.size() - 1);
        }
        java.util.Collections.reverse(rows);
        var page = new OlderPostPage().items(rows.stream().map(ConversationAssembler::toPost).toList());
        if (hasOlder) {
            page.olderCursor(rows.get(0).publicId());
        }
        return page;
    }

    private static PostItem toPost(PostRow row) {
        if (!row.visible()) {
            return new HiddenPost()
                .id(row.publicId())
                .visibility(HiddenPost.VisibilityEnum.HIDDEN)
                .postedAt(row.postedAt())
                .replyTo(row.replyTo());
        }
        return new VisiblePost()
            .id(row.publicId())
            .visibility(VisiblePost.VisibilityEnum.VISIBLE)
            .body(row.body())
            .authorName(row.nickname())
            .postedAt(row.postedAt())
            .replyTo(row.replyTo())
            .reactions(new VisiblePostReactions().empathy(row.empathy()));
    }

    // ---- 場所の候補と採用（C33） ----

    private static PlaceCandidateSection toCandidates(List<CandidateRow> rows) {
        var adopted = rows.stream().filter(CandidateRow::adopted).map(ConversationAssembler::toCandidate).toList();
        var others = rows.stream().filter(row -> !row.adopted()).toList();
        return new PlaceCandidateSection()
            .adopted(adopted)
            .others(others.stream().map(ConversationAssembler::toCandidate).toList())
            .othersTotalCount(others.isEmpty() ? 0 : others.get(0).sectionTotal());
    }

    private static PlaceCandidateItem toCandidate(CandidateRow row) {
        CandidateTarget target = row.questionPublicId() == null
            ? new ConversationCandidateTarget().kind(ConversationCandidateTarget.KindEnum.CONVERSATION)
            : new QuestionCandidateTarget().kind(QuestionCandidateTarget.KindEnum.QUESTION)
                .questionId(row.questionPublicId());
        var item = new PlaceCandidateItem()
            .id(row.publicId())
            .target(target)
            .place(toCandidatePlace(row))
            .status(PlaceCandidateItem.StatusEnum.fromValue(row.status().toLowerCase(Locale.ROOT)))
            .basisPostIds(List.copyOf(new LinkedHashSet<>(row.basisPostIds())))
            .voices(new PlaceCandidateItemVoices().support(row.support()).oppose(row.oppose()));
        toAdoption(row).ifPresent(item::adoption);
        return item;
    }

    /** 既存の場所の候補と、採用されて場所ができた案は、場所を返す。ほかは新しい場所の案。 */
    private static CandidatePlace toCandidatePlace(CandidateRow row) {
        if (row.placePublicId() != null) {
            return new CandidateExistingPlace()
                .kind(CandidateExistingPlace.KindEnum.PLACE)
                .place(new PlaceRef().id(row.placePublicId()).name(row.placeName()));
        }
        var proposal = new CandidateProposedPlace()
            .kind(CandidateProposedPlace.KindEnum.PROPOSAL)
            .name(row.proposedName())
            .kinds(row.proposedKinds());
        if (row.proposedSpatialType() != null) {
            proposal.spatialType(SpatialType.fromValue(row.proposedSpatialType().toLowerCase(Locale.ROOT)));
            proposal.geometry(GeoJsonGeometries.parse(row.proposedGeoJson()));
        }
        if (row.proposedExistedFrom() != null || row.proposedExistedTo() != null) {
            var period = new Period();
            if (row.proposedExistedFrom() != null) {
                period.from(row.proposedExistedFrom());
            }
            if (row.proposedExistedTo() != null) {
                period.to(row.proposedExistedTo());
            }
            proposal.existedPeriod(period);
        }
        return proposal;
    }

    private static Optional<CandidateAdoption> toAdoption(CandidateRow row) {
        if (row.adoptionStatus() == null) {
            return Optional.empty();
        }
        return Optional.of(switch (row.adoptionStatus()) {
            case "ADOPTED" -> new ActiveCandidateAdoption()
                .status(ActiveCandidateAdoption.StatusEnum.ADOPTED).adoptedBy(actor(row.actorKind()));
            case "RECONSIDERING" -> new ReconsideringCandidateAdoption()
                .status(ReconsideringCandidateAdoption.StatusEnum.RECONSIDERING).adoptedBy(actor(row.actorKind()));
            default -> new RevokedCandidateAdoption()
                .status(RevokedCandidateAdoption.StatusEnum.REVOKED)
                .reason(switch (row.revokeReason()) {
                    case "READOPTED" -> RevokedCandidateAdoption.ReasonEnum.READOPTED;
                    case "INHERITED_BY_MERGE" -> RevokedCandidateAdoption.ReasonEnum.MERGED;
                    default -> RevokedCandidateAdoption.ReasonEnum.WITHDRAWN;
                });
        });
    }

    // ---- 問いと回答の採用（C34） ----

    private QuestionSection toQuestions(long conversationId) {
        var rows = queries.findQuestions(conversationId);
        var postRefs = answerPosts(rows);
        var findingRefs = answerFindings(rows);
        var answered = rows.stream().filter(QuestionRow::answered)
            .map(row -> toQuestion(row, postRefs, findingRefs)).toList();
        var others = rows.stream().filter(row -> !row.answered()).toList();
        return new QuestionSection()
            .answered(answered)
            .others(others.stream().map(row -> toQuestion(row, postRefs, findingRefs)).toList())
            .othersTotalCount(others.isEmpty() ? 0 : others.get(0).sectionTotal())
            .moved(moved(queries.findMergedQuestions(conversationId), conversationId, mergeChains::questions,
                queries::findVisibleQuestions, ConversationQueries.MOVED_QUESTIONS_LIMIT));
    }

    private static QuestionItem toQuestion(QuestionRow row, Map<Long, AnswerPostRef> postRefs,
            Map<Long, AnswerFindingRef> findingRefs) {
        var item = new QuestionItem()
            .id(row.publicId())
            .content(row.content())
            .kind(QuestionItem.KindEnum.fromValue(row.kind().toLowerCase(Locale.ROOT)))
            .status(QuestionItem.StatusEnum.fromValue(row.status().toLowerCase(Locale.ROOT)))
            .askedByPerson(row.askedByPerson())
            .reactions(new QuestionItemReactions().curious(row.curious()));
        if (row.keepOpen() != null) {
            item.keepOpen(row.keepOpen());
        }
        toAnswer(row, postRefs, findingRefs).ifPresent(item::answer);
        return item;
    }

    /** 場所の採用を先に見る。回答の参照先が見せられなくても、回答（採用）はそのまま返す。 */
    private static Optional<QuestionAnswer> toAnswer(QuestionRow row, Map<Long, AnswerPostRef> postRefs,
            Map<Long, AnswerFindingRef> findingRefs) {
        if (row.placeAnswerStatus() != null) {
            return Optional.of(new PlaceQuestionAnswer()
                .kind(PlaceQuestionAnswer.KindEnum.PLACE)
                .status(adoptionStatus(row.placeAnswerStatus()))
                .adoptedBy(actor(row.placeAnswerActor()))
                .place(new PlaceRef().id(row.placePublicId()).name(row.placeName())));
        }
        if (row.otherAnswerStatus() == null) {
            return Optional.empty();
        }
        if (row.answerPostId() != null) {
            return Optional.of(new PostQuestionAnswer()
                .kind(PostQuestionAnswer.KindEnum.POST)
                .status(adoptionStatus(row.otherAnswerStatus()))
                .adoptedBy(actor(row.otherAnswerActor()))
                .post(postRefs.getOrDefault(row.answerPostId(), unavailable())));
        }
        return Optional.of(new FindingQuestionAnswer()
            .kind(FindingQuestionAnswer.KindEnum.FINDING)
            .status(adoptionStatus(row.otherAnswerStatus()))
            .adoptedBy(actor(row.otherAnswerActor()))
            .finding(findingRefs.getOrDefault(row.answerFindingId(), unavailable())));
    }

    /** 回答の Post：属する会話が公開なら識別子（Post が非公開でも）。 */
    private Map<Long, AnswerPostRef> answerPosts(List<QuestionRow> rows) {
        var ids = rows.stream().map(QuestionRow::answerPostId).filter(Objects::nonNull).distinct().toList();
        return queries.findAnswerPosts(ids).stream().collect(Collectors.toMap(OwnedRef::id,
            ref -> new AvailableAnswerPostRef()
                .state(AvailableAnswerPostRef.StateEnum.AVAILABLE)
                .id(ref.publicId())
                .conversationId(ref.conversationPublicId())));
    }

    /**
     * 回答のわかってきたこと：採用した版（属するわかってきたことの公開）と、案内する先（統合先。決められなければ自身）の
     * 両方が公開できるときだけ、採用した版の内容と案内先を返す。キーは回答のわかってきたこと。
     */
    private Map<Long, AnswerFindingRef> answerFindings(List<QuestionRow> rows) {
        var answers = rows.stream().filter(row -> row.answerFindingId() != null && row.placeAnswerStatus() == null)
            .toList();
        if (answers.isEmpty()) {
            return Map.of();
        }
        var ids = answers.stream().map(QuestionRow::answerFindingId).distinct().toList();
        var resolutions = mergeChains.findings(ids);
        var targets = ids.stream().map(id -> guideTarget(resolutions.get(id), id)).toList();
        var lookup = new ArrayList<Long>(ids);
        lookup.addAll(targets);
        var visible = queries.findVisibleFindings(lookup.stream().distinct().toList()).stream()
            .collect(Collectors.toMap(OwnedRef::id, Function.identity()));
        var versions = queries.findFindingVersions(ids).stream()
            .collect(Collectors.toMap(v -> v.findingId() + ":" + v.version(), ConversationQueries.VersionRow::content));
        var refs = new java.util.HashMap<Long, AnswerFindingRef>();
        for (var row : answers) {
            long id = row.answerFindingId();
            long target = guideTarget(resolutions.get(id), id);
            var text = versions.get(id + ":" + row.answerFindingVersion());
            if (!visible.containsKey(id) || !visible.containsKey(target) || text == null) {
                continue;
            }
            var guide = visible.get(target);
            refs.put(id, new AvailableAnswerFindingRef()
                .state(AvailableAnswerFindingRef.StateEnum.AVAILABLE)
                .id(guide.publicId())
                .conversationId(guide.conversationPublicId())
                .text(text));
        }
        return refs;
    }

    private static long guideTarget(Resolution resolution, long self) {
        return resolution != null && resolution.resolved() ? resolution.finalId() : self;
    }

    private static UnavailableAnswerRef unavailable() {
        return new UnavailableAnswerRef().state(UnavailableAnswerRef.StateEnum.UNAVAILABLE);
    }

    // ---- 統合で移ったもの（問い・わかってきたこと） ----

    /** 最後の統合先が決まり、公開で、別の会話にあるものだけを案内する。 */
    private List<MovedItem> moved(List<IdRef> merged, long conversationId,
            Function<List<Long>, Map<Long, Resolution>> resolve,
            Function<List<Long>, List<OwnedRef>> visibleTargets, int limit) {
        if (merged.isEmpty()) {
            return List.of();
        }
        var resolutions = resolve.apply(merged.stream().map(IdRef::id).toList());
        var finals = resolutions.values().stream().filter(Resolution::resolved).map(Resolution::finalId)
            .distinct().toList();
        var targets = visibleTargets.apply(finals).stream().collect(Collectors.toMap(OwnedRef::id, Function.identity()));
        var items = new ArrayList<MovedItem>();
        for (var ref : merged) {
            var resolution = resolutions.get(ref.id());
            if (resolution == null || !resolution.resolved()) {
                continue;
            }
            var target = targets.get(resolution.finalId());
            if (target == null || target.conversationId() == conversationId) {
                continue;
            }
            items.add(new MovedItem().id(ref.publicId()).conversationId(target.conversationPublicId()));
            if (items.size() == limit) {
                break;
            }
        }
        return items;
    }

    // ---- わかってきたこと（C25） ----

    /** キーより後の有効なわかってきたこと（並びは作成の時点・識別子の順）。moved は空。 */
    public FindingPage findings(long conversationId, Key after, int limit) {
        var refs = new ArrayList<>(queries.findFindingIds(conversationId, after, limit + 1));
        boolean hasNext = refs.size() > limit;
        if (hasNext) {
            refs.remove(refs.size() - 1);
        }
        var cards = findingCards.cards(refs.stream().map(IdRef::id).toList());
        List<FindingItem> items = refs.stream().map(ref -> cards.get(ref.id())).filter(Objects::nonNull).toList();
        var page = new FindingPage().items(items).totalCount(queries.countFindings(conversationId)).moved(List.of());
        if (hasNext && !refs.isEmpty()) {
            page.nextCursor(refs.get(refs.size() - 1).publicId());
        }
        return page;
    }

    // ---- この会話から生まれた発見（C03） ----

    /** 統合された側は最後の統合先に置き換え（決められなければ出さない）、同じものは1件にまとめる。 */
    private DiscoverySummaryPage toDiscoveries(long conversationId) {
        var origins = queries.findOriginDiscoveryIds(conversationId);
        var finals = mergeChains.discoveries(origins).values().stream()
            .filter(Resolution::resolved).map(Resolution::finalId).distinct().toList();
        var rows = queries.findVisibleDiscoveries(finals);
        var cards = discoveryCards.cards(rows.stream()
            .map(row -> new DiscoveryCards.DiscoveryRow(row.id(), row.publicId(), row.title(), row.summary()))
            .toList());
        List<DiscoverySummary> items = rows.stream().map(row -> cards.get(row.id())).filter(Objects::nonNull).toList();
        return new DiscoverySummaryPage()
            .items(items.stream().limit(ConversationQueries.DISCOVERY_LIMIT).toList())
            .totalCount(items.size());
    }

    // ---- この会話が届くところ ----

    private ReachPage toReach(long conversationId) {
        var rows = queries.findReach(conversationId);
        return new ReachPage()
            .items(rows.stream().map(row -> new ReachPlace()
                .place(new PlaceRef().id(row.publicId()).name(row.name()))
                .matchedBy(row.candidate() ? ReachPlace.MatchedByEnum.CANDIDATE : ReachPlace.MatchedByEnum.AREA))
                .toList())
            .totalCount(rows.isEmpty() ? 0 : rows.get(0).total());
    }

    // ---- 共通 ----

    private static PlaceRef placeRef(ConversationQueries.Ref ref) {
        return new PlaceRef().id(ref.publicId()).name(ref.name());
    }

    private static AdoptionStatus adoptionStatus(String status) {
        return "RECONSIDERING".equals(status) ? AdoptionStatus.RECONSIDERING : AdoptionStatus.ADOPTED;
    }

    private static AdoptionActor actor(String actorKind) {
        return switch (actorKind) {
            case "QUESTION_OWNER" -> AdoptionActor.QUESTION_OWNER;
            case "CONVERSATION_STARTER" -> AdoptionActor.CONVERSATION_STARTER;
            default -> AdoptionActor.RULE_OR_OPERATOR;
        };
    }
}
