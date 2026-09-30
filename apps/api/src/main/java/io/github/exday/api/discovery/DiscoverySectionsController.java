package io.github.exday.api.discovery;

import io.github.exday.api.context.EvaluationContextProvider;
import io.github.exday.api.generated.api.S01Api;
import io.github.exday.api.generated.model.DiscoverySection;
import io.github.exday.api.generated.model.DiscoverySectionItems;
import io.github.exday.api.generated.model.RecommendedDiscoveryCard;
import io.github.exday.api.generated.model.PlaceSummary;
import io.github.exday.api.generated.model.Image;
import io.github.exday.api.generated.model.DiscoverySectionList;
import io.github.exday.api.generated.model.SectionKey;
import java.util.List;
import java.beans.PropertyEditorSupport;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * S01（Discovery提案（サービスTOP））のセクション関連のエンドポイントの実装。
 */
@RestController
public class DiscoverySectionsController implements S01Api {

    /** 「この近く」の表示名（PC の見出し、モバイルのタブ）。 */
    static final String TITLE_NEARBY = "この近く";

    /** 「今の時期」の表示名。 */
    static final String TITLE_SEASON = "今の時期";

    /** セクションの並び順：nearby → season（Issue の決定）。 */
    static final List<DiscoverySection> SECTIONS = List.of(
        new DiscoverySection(SectionKey.NEARBY, TITLE_NEARBY),
        new DiscoverySection(SectionKey.SEASON, TITLE_SEASON));

    private final EvaluationContextProvider contextProvider;

    private final DiscoveryCandidateQueries queries;
    private final DiscoveryRecommendation recommendation;
    private final DiscoveryRecommendationProperties properties;

    public DiscoverySectionsController(EvaluationContextProvider contextProvider,
            DiscoveryCandidateQueries queries, DiscoveryRecommendation recommendation,
            DiscoveryRecommendationProperties properties) {
        this.contextProvider = contextProvider;
        this.queries = queries;
        this.recommendation = recommendation;
        this.properties = properties;
    }

    @InitBinder
    void bindSectionKey(WebDataBinder binder) {
        // 通常の enum 変換は失敗時に Java の定数名へフォールバックするため、
        // 専用の editor で OpenAPI の値（nearby / season）だけを受け付ける。
        binder.registerCustomEditor(SectionKey.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(SectionKey.fromValue(text));
            }
        });
    }

    @Override
    public ResponseEntity<DiscoverySectionItems> getDiscoverySectionItems(SectionKey sectionKey, Integer limit) {
        var context = contextProvider.current();
        var candidates = queries.find(sectionKey, context, properties.nearbyRadiusMeters());
        var items = recommendation.select(candidates, limit).stream().map(candidate -> {
            var card = new RecommendedDiscoveryCard(candidate.discoveryId(), candidate.title(),
                candidate.subject(), candidate.summary(), new PlaceSummary(candidate.placeName()),
                candidate.valueId());
            if (candidate.pictureUrl() != null) {
                card.setPicture(new Image(candidate.pictureUrl(), candidate.pictureAlt()));
            }
            return card;
        }).toList();
        return ResponseEntity.ok(new DiscoverySectionItems(context, sectionKey, items));
    }

    @Override
    public ResponseEntity<DiscoverySectionList> getDiscoverySections() {
        DiscoverySectionList body = new DiscoverySectionList(contextProvider.current(), SECTIONS);
        return ResponseEntity.ok(body);
    }
}
