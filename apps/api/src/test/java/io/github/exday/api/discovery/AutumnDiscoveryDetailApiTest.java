package io.github.exday.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** S03 の季節の振り分けの秋のもの。固定した「今」は 2026-11-02（秋）。 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-11-02T15:00:00+09:00")
class AutumnDiscoveryDetailApiTest extends DiscoveryDetailTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void 秋は季節違いのValueをその他の魅力に振り分ける() {
        var kozukue = body(sid("disc:kozukue"));
        assertThat((List<Map<String, Object>>) kozukue.get("recommendations")).extracting(v -> v.get("id"))
            .containsExactly(sid("value:kozukue-moat"));
        var other = (List<Map<String, Object>>) kozukue.get("otherValues");
        assertThat(other).extracting(v -> v.get("id")).containsExactly(sid("value:kozukue-sakura"));
        assertThat(other.get(0)).containsEntry("conditions", List.of(Map.of(
            "axis", "season", "label", "季節", "text", "春（3〜5月）", "seasons", List.of("spring"))));
        assertThat(((Map<?, ?>) kozukue.get("context")).get("season")).isEqualTo("autumn");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 春と秋の条件を持つValueは秋に推薦する() {
        var roses = body(sid("disc:roses"));
        var recommendations = (List<Map<String, Object>>) roses.get("recommendations");
        assertThat(recommendations).singleElement().satisfies(v -> assertThat(v).containsEntry("conditions",
            List.of(Map.of("axis", "season", "label", "季節", "text", "春（3〜5月）・秋（9〜11月）",
                "seasons", List.of("spring", "autumn")))));
        assertThat(roses).containsEntry("otherValues", List.of());
    }
}
