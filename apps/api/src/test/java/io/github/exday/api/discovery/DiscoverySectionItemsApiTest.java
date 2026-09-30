package io.github.exday.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exday.api.generated.model.SectionKey;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "exday.dev.clock.now=2026-04-02T15:00:00+09:00")
class DiscoverySectionItemsApiTest extends DiscoveryItemsTestSupport {
    private static final String HIDE = """
        UPDATE discovery SET visibility = :visibility WHERE public_id = :id
        """;
    private static final String UNESTABLISH = """
        UPDATE discovery SET establishment_status = :status, established_at = NULL WHERE public_id = :id
        """;
    private static final String REMOVE_CONDITIONS = """
        DELETE FROM value_condition WHERE value_id IN (
            SELECT id FROM discovery_value WHERE discovery_id = (
                SELECT id FROM discovery WHERE public_id = :id))
        """;
    private static final String ADD_SEASON = """
        INSERT INTO value_condition (value_id, axis, season)
        SELECT id, :axis, :season FROM discovery_value WHERE subject_label = :subject
        """;

    @Test
    void 春の候補とカードの項目を公開IDで返す() {
        var cards = items("nearby/discoveries?limit=20");
        assertThat(cards).extracting(c -> c.get("subject"))
            .contains("桜並木", "夜桜", "川沿いの散歩").doesNotContain("秋のバラ");
        var cherry = cards.stream().filter(c -> c.get("subject").equals("桜並木")).findFirst().orElseThrow();
        assertThat(cherry).containsEntry("id", "FMI7GE306Hqw")
            .containsEntry("title", "大岡川の桜と風景の変化")
            .containsEntry("value", "川沿いに続く桜並木を、水面越しに眺められる")
            .containsEntry("place", Map.of("name", "大岡川（桜木町駅〜蒔田）"));
        assertThat(((Map<?, ?>) cherry.get("picture")).get("url")).isEqualTo("/images/sample/placeholder-sakura.svg");
        assertThat(cards).allSatisfy(c -> {
            assertThat((String) c.get("id")).matches("[0-9A-Za-z]{12}");
            assertThat((String) c.get("valueId")).matches("[0-9A-Za-z]{12}");
        });
        var night = cards.stream().filter(c -> c.get("subject").equals("夜桜")).findFirst().orElseThrow();
        assertThat(night.get("id")).isEqualTo(cherry.get("id"));
        assertThat(night.get("valueId")).isNotEqualTo(cherry.get("valueId"));
        assertThat(night).doesNotContainKey("picture");
        var response = get("nearby/discoveries");
        assertThat(response.getBody()).containsEntry("key", "nearby");
        var evaluation = (Map<?, ?>) response.getBody().get("context");
        assertThat(evaluation.get("season")).isEqualTo("spring");
        assertThat(evaluation.get("now")).isEqualTo("2026-04-02T15:00:00+09:00");
    }

    @Test
    void 春のseasonは一致するValueだけを返し距離順になる() {
        var cards = items("season/discoveries?limit=20");
        assertThat(cards).extracting(c -> c.get("subject"))
            .containsExactlyInAnyOrder("桜並木", "夜桜", "春のバラ");
        assertThat(cards.subList(0, 2)).allSatisfy(c -> assertThat(c.get("id")).isEqualTo("FMI7GE306Hqw"));
    }

    @Test
    void nearbyは季節一致を先にして距離順で並べた後にlimitを適用する() {
        var candidates = queries.find(SectionKey.NEARBY, context.current(), 3000);
        var byId = candidates.stream().collect(java.util.stream.Collectors.toMap(DiscoveryCandidate::valueId, c -> c));
        var cards = items("nearby/discoveries?limit=20");
        boolean yearRoundSeen = false;
        double previousDistance = -1;
        for (var card : cards) {
            var candidate = byId.get(card.get("valueId"));
            if (!candidate.seasonal() && !yearRoundSeen) {
                yearRoundSeen = true;
                previousDistance = -1;
            }
            if (yearRoundSeen) assertThat(candidate.seasonal()).isFalse();
            assertThat(candidate.distanceMeters()).isGreaterThanOrEqualTo(previousDistance);
            previousDistance = candidate.distanceMeters();
        }
        assertThat(yearRoundSeen).isTrue();
        assertThat(items("nearby/discoveries")).hasSize(8).isEqualTo(cards.subList(0, 8));
        assertThat(items("nearby/discoveries?limit=1")).isEqualTo(cards.subList(0, 1));
    }

    @Test
    void 非公開と未成立は候補に含めない() {
        jdbc.sql(HIDE).param("visibility", "HIDDEN").param("id", "FMI7GE306Hqw").update();
        jdbc.sql(UNESTABLISH).param("status", "UNESTABLISHED").param("id", "0cKuRVG95FPG").update();
        assertThat(items("season/discoveries")).isEmpty();
        assertThat(items("nearby/discoveries?limit=20")).extracting(c -> c.get("id"))
            .doesNotContain("FMI7GE306Hqw", "0cKuRVG95FPG");
    }

    @Test
    void 季節条件なしはnearbyだけに入り複数季節でもカードは重複しない() {
        jdbc.sql(REMOVE_CONDITIONS).param("id", "QWTFhGoAnUC5").update();
        jdbc.sql(ADD_SEASON).param("axis", "SEASON").param("season", "AUTUMN").param("subject", "桜並木").update();
        assertThat(items("nearby/discoveries?limit=20")).extracting(c -> c.get("subject")).contains("動物園");
        assertThat(items("season/discoveries?limit=20")).extracting(c -> c.get("subject"))
            .containsExactlyInAnyOrder("桜並木", "夜桜", "春のバラ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "21", "-1", "abc", "1.5"})
    void 不正なlimitはproblemJsonの400(String limit) {
        var response = get("nearby/discoveries?limit=" + limit);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("status", 400).containsKeys("title");
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "NEARBY"})
    void 一覧にないkeyはproblemJsonの404(String key) {
        var response = get(key + "/discoveries");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("status", 404).containsKeys("title");
    }
}
