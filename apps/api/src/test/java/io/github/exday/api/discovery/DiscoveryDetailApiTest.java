package io.github.exday.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** S03（GET /discoveries/{discoveryId}）のテスト。固定した「今」は 2026-04-02（春）。 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-04-02T15:00:00+09:00")
class DiscoveryDetailApiTest extends DiscoveryDetailTestSupport {

    @Test
    void 春は季節が合うValueと通年のValueをすべて推薦する() {
        var body = body("FMI7GE306Hqw");
        assertThat(body).containsEntry("id", "FMI7GE306Hqw")
            .containsEntry("title", "大岡川の桜と風景の変化")
            .containsEntry("place", Map.of("name", "大岡川（桜木町駅〜蒔田）"));
        assertThat((String) body.get("body")).contains("大岡川の桜並木");

        @SuppressWarnings("unchecked")
        var recommendations = (List<Map<String, Object>>) body.get("recommendations");
        assertThat(recommendations).extracting(r -> r.get("message"))
            .containsExactly(
                "桜の季節です。川沿いの桜並木を歩いてみませんか",
                "夜の大岡川では、川面に映る桜も楽しめます",
                "大岡川の川沿いを歩いて、昔の風景との違いを探してみませんか");
        var sakura = recommendations.get(0);
        assertThat(((Map<?, ?>) sakura.get("media")).get("url"))
            .isEqualTo("/images/sample/placeholder-sakura.svg");
        assertThat(recommendations.get(1)).doesNotContainKey("media");
        var walk = recommendations.get(2);
        assertThat(walk).containsEntry("participationMessage", "昔を知っている方、写真を持っている方、一緒に残しませんか？");

        assertThat((List<?>) body.get("otherValues")).isEmpty();

        @SuppressWarnings("unchecked")
        var findings = (List<Map<String, Object>>) body.get("findings");
        assertThat(findings).extracting(f -> f.get("text")).containsExactly(
            "夜は川面に映る桜も楽しめるらしい",
            "朝の人が少ない時間に歩くのもよいらしい",
            "昔は今より桜の本数が少なかったのでは",
            "春には川沿いに屋台が出ていたらしい",
            "昔の川沿いは、今より建物が低く空が広かった");
        assertThat(findings.get(0)).containsEntry("kind", "value")
            .containsEntry("reactions", Map.of("understand", 3, "wantToGo", 5));
        assertThat(findings.get(1)).containsEntry("reactions", Map.of("understand", 0, "wantToGo", 0));
        assertThat(findings.get(2)).containsEntry("kind", "theory")
            .containsEntry("reactions", Map.of("maybe", 2));
        var backedTheory = findings.get(4);
        assertThat(backedTheory).containsEntry("backedBy", "提供された昔の写真").doesNotContainKey("reactions");

        @SuppressWarnings("unchecked")
        var tags = (List<String>) body.get("tags");
        assertThat(tags).containsExactlyInAnyOrder("大岡川", "桜", "夜桜");
        assertThat(body).containsEntry("reactions", Map.of("like", 4, "surprised", 1, "love", 6));
        assertThat(body).containsEntry("relations", Map.of("derivedFrom", List.of(), "derivedTo", List.of(), "related", List.of()));

        var context = (Map<?, ?>) body.get("context");
        assertThat(context.get("season")).isEqualTo("spring");
    }

    @Test
    void 派生元と関連のカードを一番の価値で組み立てる() {
        var body = body("LCy5noRLHXj6");
        assertThat(body).containsEntry("id", "LCy5noRLHXj6");

        @SuppressWarnings("unchecked")
        var findings = (List<Map<String, Object>>) body.get("findings");
        assertThat(findings.get(0)).containsEntry("backedBy", "Wikipedia「山下臨港線プロムナード」")
            .doesNotContainKey("reactions");
        assertThat(findings.get(1)).containsEntry("reactions", Map.of("maybe", 3));

        @SuppressWarnings("unchecked")
        var tags2 = (List<String>) body.get("tags");
        assertThat(tags2).containsExactlyInAnyOrder("臨港線");
        assertThat(body).containsEntry("reactions", Map.of("like", 2, "surprised", 3, "love", 0));

        @SuppressWarnings("unchecked")
        var relations = (Map<String, Object>) body.get("relations");
        @SuppressWarnings("unchecked")
        var derivedFrom = (List<Map<String, Object>>) relations.get("derivedFrom");
        assertThat(derivedFrom).hasSize(1);
        var origin = derivedFrom.get(0);
        assertThat(origin).containsEntry("id", "pNhOuychaTbx")
            .containsEntry("title", "桜木町から山下公園への海沿い散歩")
            .containsEntry("subject", "海沿いの散歩")
            .containsEntry("value", "海を見ながら、桜木町から山下公園まで歩ける");
        assertThat(((Map<?, ?>) origin.get("picture")).get("url")).isEqualTo("/images/sample/placeholder-sea.svg");

        assertThat((List<?>) relations.get("derivedTo")).isEmpty();

        @SuppressWarnings("unchecked")
        var related = (List<Map<String, Object>>) relations.get("related");
        assertThat(related).hasSize(1);
        assertThat(related.get(0)).containsEntry("id", "OglBhQeYvpZO").containsEntry("subject", "夜景")
            .doesNotContainKey("picture");
    }

    @Test
    void 存在しないDiscoveryは404を返す() {
        var response = get("doesNotExist1");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void 非公開のDiscoveryは404を返す() {
        jdbc.sql("UPDATE discovery SET visibility = 'HIDDEN' WHERE public_id = :id")
            .param("id", "FMI7GE306Hqw").update();
        var response = get("FMI7GE306Hqw");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
