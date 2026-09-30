package io.github.exday.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** S03 の季節振り分けの秋バージョン。固定した「今」は 2026-11-02（秋）。 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-11-02T15:00:00+09:00")
class AutumnDiscoveryDetailApiTest extends DiscoveryDetailTestSupport {

    @Test
    void 秋は季節違いのValueをotherValuesへ振り分ける() {
        var body = body("FMI7GE306Hqw");

        @SuppressWarnings("unchecked")
        var recommendations = (List<Map<String, Object>>) body.get("recommendations");
        assertThat(recommendations).extracting(r -> r.get("message"))
            .containsExactly("大岡川の川沿いを歩いて、昔の風景との違いを探してみませんか");

        @SuppressWarnings("unchecked")
        var otherValues = (List<Map<String, Object>>) body.get("otherValues");
        assertThat(otherValues).extracting(v -> v.get("subject")).containsExactlyInAnyOrder("桜並木", "夜桜");
        var sakura = otherValues.stream().filter(v -> v.get("subject").equals("桜並木")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var conditions = (List<Map<String, Object>>) sakura.get("conditions");
        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0)).containsEntry("label", "季節").containsEntry("seasons", List.of("spring"));

        var context = (Map<?, ?>) body.get("context");
        assertThat(context.get("season")).isEqualTo("autumn");
    }
}
