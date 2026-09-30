package io.github.exday.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "exday.dev.clock.now=2026-11-02T15:00:00+09:00",
    "exday.recommendation.nearby-radius-meters=1"
})
class AutumnDiscoverySectionItemsApiTest extends DiscoveryItemsTestSupport {
    @Test
    void いる場所を遠方に変えるとnearbyの候補がなくなる() {
        var evaluation = context.current();
        evaluation.setLocation(new io.github.exday.api.generated.model.Location("東京駅", 35.6812, 139.7671));
        assertThat(queries.find(io.github.exday.api.generated.model.SectionKey.NEARBY, evaluation, 3000)).isEmpty();
        assertThat(queries.find(io.github.exday.api.generated.model.SectionKey.SEASON, evaluation, 3000)).hasSize(2);
    }

    @Test
    void 秋のseasonは秋のバラと銀杏になりnearbyの半径に制限されない() {
        assertThat(items("season/discoveries?limit=20")).extracting(c -> c.get("subject"))
            .containsExactlyInAnyOrder("秋のバラ", "黄葉");
        // この散歩の ROUTE は桜木町駅を通るため、距離0で半径1mにも含まれる。
        assertThat(items("nearby/discoveries?limit=20")).extracting(c -> c.get("subject"))
            .containsExactlyInAnyOrder("海沿いの散歩", "線路跡の遊歩道");
    }
}
