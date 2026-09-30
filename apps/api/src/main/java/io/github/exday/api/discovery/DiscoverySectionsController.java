package io.github.exday.api.discovery;

import io.github.exday.api.context.EvaluationContextProvider;
import io.github.exday.api.generated.api.S01Api;
import io.github.exday.api.generated.model.DiscoverySection;
import io.github.exday.api.generated.model.DiscoverySectionList;
import io.github.exday.api.generated.model.SectionKey;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * S01（Discovery提案（サービスTOP））のセクション関連のエンドポイントの実装。
 *
 * <p>この Issue では {@code GET /discovery-sections}（セクションの一覧）のみを実装する。
 * セクションごとの中身（{@code /discovery-sections/{sectionKey}/discoveries}）は別 Issue で
 * 生成 interface の default 実装（{@code 501 Not Implemented}）を使う。
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

    public DiscoverySectionsController(EvaluationContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public ResponseEntity<DiscoverySectionList> getDiscoverySections() {
        DiscoverySectionList body = new DiscoverySectionList(contextProvider.current(), SECTIONS);
        return ResponseEntity.ok(body);
    }
}
