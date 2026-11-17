package io.github.exday.api.place;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exday.api.DbApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * S13（GET /places/{placeId}）のテスト。データはサンプルデータ（db/sample/R__sample_data.sql）。
 * 固定した「今」は 2026-04-02（春）。
 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-04-02T15:00:00+09:00")
class PlaceApiTest extends DbApiTestSupport {

    private Map<String, Object> place(String key) {
        return okJson("/places/" + sid(key));
    }

    private static Map<String, Object> ref(String key, String name) {
        return Map.of("id", sid(key), "name", name);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 山下公園の区画を返す() {
        var body = place("place:yamashita");
        assertThat(body).containsEntry("id", sid("place:yamashita"))
            .containsEntry("name", "山下公園")
            .containsEntry("spatialType", "point")
            .containsEntry("kinds", List.of("公園"))
            .containsEntry("aliases", List.of())
            .containsEntry("state", Map.of("status", "active"))
            .containsEntry("capabilities", Map.of("canStartConversation", true))
            .containsEntry("geometry", Map.of("type", "Point", "coordinates", List.of(139.6505, 35.4462)))
            .containsEntry("accessStation", ref("place:ishikawacho", "石川町駅"))
            .containsEntry("breadcrumb", List.of(
                ref("place:kanagawa", "神奈川県"), ref("place:yokohama", "横浜市"), ref("place:naka", "横浜市中区")))
            .containsEntry("estimates", List.of())
            .containsEntry("seeking", Map.of("items", List.of(), "totalCount", 0))
            .containsEntry("nearbyPlaces", Map.of("items", List.of(), "totalCount", 0))
            .doesNotContainKey("existedPeriod");

        // 観点ごと：季節の楽しみ（バラ）と散歩（汽車道の散歩）。1つの Discovery は1つの観点にだけ入る
        var discoveries = (Map<String, Object>) body.get("discoveries");
        assertThat(discoveries).containsEntry("totalCount", 2);
        var groups = (List<Map<String, Object>>) discoveries.get("groups");
        assertThat(groups).extracting(g -> g.get("aspect")).containsExactly("季節の楽しみ", "散歩");
        var roses = ((List<Map<String, Object>>) groups.get(0).get("items")).get(0);
        assertThat(roses).containsEntry("roles", List.of("EXPERIENCE"));
        assertThat((Map<String, Object>) roses.get("discovery")).containsEntry("id", sid("disc:roses"))
            .containsEntry("displayPlaces", List.of(ref("place:yamashita", "山下公園")));
        var walk = ((List<Map<String, Object>>) groups.get(1).get("items")).get(0);
        assertThat(walk).containsEntry("roles", List.of("END"));
        assertThat((Map<String, Object>) walk.get("discovery"))
            .containsEntry("displayPlaces", List.of(ref("place:sakuragicho", "桜木町駅")));

        var conversations = (Map<String, Object>) body.get("conversations");
        assertThat(conversations).containsEntry("totalCount", 1);
        assertThat((List<Map<String, Object>>) conversations.get("items")).singleElement()
            .satisfies(c -> assertThat(c).containsEntry("id", sid("conv:walk")).containsEntry("replyCount", 2));

        assertThat(body).containsEntry("projects", Map.of("items", List.of(Map.of(
                "id", sid("proj:harbor-course"), "title", "港の見える散歩コース", "type", "course",
                "creatorKind", "user", "creatorName", "はまっこ", "matchedBy", "discovery", "position", 1)),
            "totalCount", 1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 小机城址は別名と階層と入口の会話を返し成立を取り消した発見は出さない() {
        var body = place("place:kozukue-castle");
        assertThat(body).containsEntry("kinds", List.of("公園", "城跡"))
            .containsEntry("aliases", List.of(Map.of("name", "小机城", "kind", "alias")))
            .containsEntry("breadcrumb", List.of(ref("place:kanagawa", "神奈川県"), ref("place:yokohama", "横浜市"),
                ref("place:kohoku", "横浜市港北区"), ref("place:kozukue-cho", "小机町")));
        var discoveries = (Map<String, Object>) body.get("discoveries");
        assertThat(discoveries).containsEntry("totalCount", 1);
        var groups = (List<Map<String, Object>>) discoveries.get("groups");
        assertThat(groups).singleElement().satisfies(g -> assertThat(g).containsEntry("aspect", "歴史"));
        assertThat((Map<String, Object>) body.get("conversations")).containsEntry("totalCount", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 坂は推定の値を返し番付の企画にplaceで入る() {
        var body = place("place:slope-c");
        assertThat((List<Map<String, Object>>) body.get("estimates")).singleElement().satisfies(e -> assertThat(e)
            .containsEntry("metric", "SLOPE_GRADE").containsEntry("value", 10.5).containsEntry("unit", "percent")
            .containsEntry("method", "elevation-x-road").containsEntry("methodVersion", "sample-1"));
        var projects = (Map<String, Object>) body.get("projects");
        assertThat((List<Map<String, Object>>) projects.get("items")).singleElement().satisfies(p -> assertThat(p)
            .containsEntry("title", "鶴見の急坂番付（推定）").containsEntry("type", "ranking")
            .containsEntry("creatorKind", "operator").containsEntry("matchedBy", "place").containsEntry("position", 4));
        // 発見も会話もない場所でも開ける（最初の声のお誘いは画面が出す）
        assertThat(body).containsEntry("discoveries", Map.of("groups", List.of(), "totalCount", 0))
            .containsEntry("conversations", Map.of("items", List.of(), "totalCount", 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void エリアの場所には範囲に入る新しい場所の案の問いを出す() {
        var body = place("place:nishi");
        var seeking = (Map<String, Object>) body.get("seeking");
        assertThat(seeking).containsEntry("totalCount", 1);
        var card = ((List<Map<String, Object>>) seeking.get("items")).get(0);
        assertThat(card).containsEntry("kind", "question")
            .containsEntry("question", Map.of("id", sid("q:soba"), "content", "水車のあるそば屋はどこにあったか"))
            .containsEntry("candidates", List.of(Map.of(
                "id", sid("cand:soba-west"), "kind", "proposal", "name", "水車のあるそば屋（横浜駅西口のあたり）")));
        assertThat((Map<String, Object>) card.get("conversation")).containsEntry("id", sid("conv:soba"));

        // 地点の場所（横浜駅）には、範囲による当てはめをしない
        assertThat(place("place:yokohama-st")).containsEntry("seeking", Map.of("items", List.of(), "totalCount", 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 場所が決まると探している話から外れる() {
        // そば屋の会話に、横浜駅を話の場所の候補として足す（会話は場所未定）
        jdbc.sql("""
                INSERT INTO place_candidate (public_id, conversation_id, place_id, origin)
                SELECT 'candYokoSt01', c.id, p.id, 'HUMAN' FROM conversation c, place p
                 WHERE c.public_id = :conv AND p.public_id = :place
                """)
            .param("conv", sid("conv:soba")).param("place", sid("place:yokohama-st")).update();
        var seeking = (Map<String, Object>) place("place:yokohama-st").get("seeking");
        assertThat(seeking).containsEntry("totalCount", 1);
        var card = ((List<Map<String, Object>>) seeking.get("items")).get(0);
        assertThat(card).containsEntry("kind", "conversation").containsEntry("candidates", List.of(Map.of(
            "id", "candYokoSt01", "kind", "place", "place", ref("place:yokohama-st", "横浜駅"))));
        assertThat((Map<String, Object>) card.get("conversation")).containsEntry("id", sid("conv:soba"));

        // 問いの回答として横浜駅を採用すると、会話にも場所が付くので、どちらも探している話から外れる
        long decision = operatorDecision("PLACE_ADOPT");
        jdbc.sql("""
                INSERT INTO place_candidate (public_id, open_question_id, place_id, origin)
                SELECT 'candYokoQ001', q.id, p.id, 'HUMAN' FROM open_question q, place p
                 WHERE q.public_id = :q AND p.public_id = :place
                """)
            .param("q", sid("q:soba")).param("place", sid("place:yokohama-st")).update();
        jdbc.sql("""
                INSERT INTO place_adoption (candidate_id, open_question_id, place_id, actor_kind, created_decision_id)
                SELECT id, open_question_id, place_id, 'RULE_OR_OPERATOR', :decision FROM place_candidate WHERE public_id = 'candYokoQ001'
                """)
            .param("decision", decision).update();
        assertThat(place("place:yokohama-st")).containsEntry("seeking", Map.of("items", List.of(), "totalCount", 0));
        assertThat(place("place:nishi")).containsEntry("seeking", Map.of("items", List.of(), "totalCount", 0));
        // 採用した場所の会話として出る
        assertThat((Map<String, Object>) place("place:yokohama-st").get("conversations")).containsEntry("totalCount", 1);
    }

    @Test
    void 非公開の会話は会話と探している話と件数に出さない() {
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id IN (:ids)")
            .param("ids", List.of(sid("conv:walk"), sid("conv:soba"))).update();
        assertThat(place("place:yamashita")).containsEntry("conversations", Map.of("items", List.of(), "totalCount", 0));
        assertThat(place("place:nishi")).containsEntry("seeking", Map.of("items", List.of(), "totalCount", 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void このあたりの場所は発見か会話のある場所だけを出す() {
        var nearby = (Map<String, Object>) place("place:naka").get("nearbyPlaces");
        // 中区の中には山下公園（発見・会話あり）と氷川丸（なし）
        assertThat(nearby).containsEntry("totalCount", 1);
        assertThat((List<Map<String, Object>>) nearby.get("items")).singleElement()
            .satisfies(n -> assertThat(n).containsEntry("place", ref("place:yamashita", "山下公園"))
                .containsEntry("kinds", List.of("公園")));
    }

    @Test
    void 統合済みの場所は最後の統合先を案内し会話を受け付けない() {
        jdbc.sql("UPDATE place SET status = 'MERGED', merged_into_place_id = (SELECT id FROM place WHERE public_id = :to) WHERE public_id = :from")
            .param("from", sid("place:slope-c")).param("to", sid("place:slope-d")).update();
        jdbc.sql("UPDATE place SET status = 'MERGED', merged_into_place_id = (SELECT id FROM place WHERE public_id = :to) WHERE public_id = :from")
            .param("from", sid("place:slope-d")).param("to", sid("place:slope-a")).update();
        assertThat(place("place:slope-c"))
            .containsEntry("state", Map.of("status", "merged", "target", ref("place:slope-a", "鶴見の坂A")))
            .containsEntry("capabilities", Map.of("canStartConversation", false));
    }

    @Test
    void 統合が循環しているときは統合されたことだけを返す() {
        jdbc.sql("UPDATE place SET status = 'MERGED', merged_into_place_id = (SELECT id FROM place WHERE public_id = :to) WHERE public_id = :from")
            .param("from", sid("place:slope-c")).param("to", sid("place:slope-d")).update();
        jdbc.sql("UPDATE place SET status = 'MERGED', merged_into_place_id = (SELECT id FROM place WHERE public_id = :to) WHERE public_id = :from")
            .param("from", sid("place:slope-d")).param("to", sid("place:slope-c")).update();
        assertThat(place("place:slope-c")).containsEntry("state", Map.of("status", "merged"));
    }

    @Test
    void 廃止の場所は存在した期間を返し会話を受け付ける() {
        jdbc.sql("UPDATE place SET status = 'ABOLISHED', existed_from = '1950-04-01', existed_to = '1990-03-31' WHERE public_id = :id")
            .param("id", sid("place:slope-c")).update();
        assertThat(place("place:slope-c")).containsEntry("state", Map.of("status", "abolished"))
            .containsEntry("existedPeriod", Map.of("from", "1950-04-01", "to", "1990-03-31"))
            .containsEntry("capabilities", Map.of("canStartConversation", true));
    }

    @Test
    void 階層が循環していても自分と通った場所を含めない() {
        // 神奈川県 ＞ 横浜市 ＞ 中区 に、中区 ＞ 神奈川県 を足して循環させる
        jdbc.sql("""
                INSERT INTO place_relation (from_place_id, to_place_id, kind, origin)
                SELECT f.id, t.id, 'ADMINISTRATIVE', 'OPERATOR' FROM place f, place t WHERE f.public_id = :f AND t.public_id = :t
                """)
            .param("f", sid("place:naka")).param("t", sid("place:kanagawa")).update();
        assertThat(place("place:yamashita")).containsEntry("breadcrumb", List.of(
            ref("place:kanagawa", "神奈川県"), ref("place:yokohama", "横浜市"), ref("place:naka", "横浜市中区")));
        assertThat(place("place:naka")).containsEntry("breadcrumb", List.of(
            ref("place:kanagawa", "神奈川県"), ref("place:yokohama", "横浜市")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 発見は成立の新しい順に30件を選んでから観点ごとにまとめる() {
        // 氷川丸に、観点の違う発見を31件足す（古いものほど観点の名前が前）
        long decision = operatorDecision("OPERATOR_REGISTER");
        jdbc.sql("""
                WITH d AS (
                    INSERT INTO discovery (public_id, title, established_at)
                    SELECT 'hikawaDisc' || lpad(i::text, 2, '0'), '氷川丸の発見 ' || i,
                           timestamptz '2026-01-01 00:00+09' + make_interval(days => i)
                      FROM generate_series(1, 31) AS i
                    RETURNING id, public_id
                )
                INSERT INTO discovery_place (discovery_id, place_id, role, created_decision_id)
                SELECT d.id, p.id, 'EXPERIENCE', :decision FROM d, place p WHERE p.public_id = :place
                """)
            .param("decision", decision).param("place", sid("place:hikawamaru")).update();
        jdbc.sql("""
                INSERT INTO subject (label, kind, status) VALUES ('Aの観点', 'ASPECT', 'ADOPTED'), ('Bの観点', 'ASPECT', 'ADOPTED')
                """).update();
        // いちばん古い1件だけ「Aの観点」、ほかは「Bの観点」
        jdbc.sql("""
                INSERT INTO subject_assignment (subject_id, discovery_id, assigned_by)
                SELECT s.id, d.id, 'OPERATOR' FROM discovery d, subject s
                 WHERE d.public_id LIKE 'hikawaDisc%'
                   AND s.label = CASE WHEN d.public_id = 'hikawaDisc01' THEN 'Aの観点' ELSE 'Bの観点' END
                """).update();

        var discoveries = (Map<String, Object>) place("place:hikawamaru").get("discoveries");
        assertThat(discoveries).containsEntry("totalCount", 31);
        var groups = (List<Map<String, Object>>) discoveries.get("groups");
        // いちばん古い（Aの観点の）1件は30件に入らないので、観点の名前の順で前に来ても選ばれない
        assertThat(groups).singleElement().satisfies(g -> assertThat(g).containsEntry("aspect", "Bの観点"));
        var items = (List<Map<String, Object>>) groups.get(0).get("items");
        assertThat(items).hasSize(30);
        assertThat(((Map<String, Object>) items.get(0).get("discovery")).get("id")).isEqualTo("hikawaDisc31");
        assertThat(((Map<String, Object>) items.get(29).get("discovery")).get("id")).isEqualTo("hikawaDisc02");
    }

    @Test
    void 同じ発見がこの場所に別の役割で関わっていても1件にまとめる() {
        long decision = operatorDecision("OPERATOR_REGISTER");
        jdbc.sql("""
                INSERT INTO discovery_place (discovery_id, place_id, role, created_decision_id)
                SELECT d.id, p.id, 'MEMORY', :decision FROM discovery d, place p WHERE d.public_id = :d AND p.public_id = :p
                """)
            .param("decision", decision).param("d", sid("disc:roses")).param("p", sid("place:yamashita")).update();
        var body = place("place:yamashita");
        @SuppressWarnings("unchecked")
        var groups = (List<Map<String, Object>>) ((Map<String, Object>) body.get("discoveries")).get("groups");
        assertThat(((Map<?, ?>) body.get("discoveries")).get("totalCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        var roses = ((List<Map<String, Object>>) groups.get(0).get("items")).get(0);
        assertThat(roses).containsEntry("roles", List.of("EXPERIENCE", "MEMORY"));
    }

    @Test
    void ない場所は404で形の合わない識別子は400を返す() {
        var missing = getJson("/places/doesNotExist");
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        var invalid = getJson("/places/short");
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void S13の応答はREPEATABLE_READの読み取り専用トランザクションで作る() throws Exception {
        var transactional = PlaceService.class.getMethod("find", String.class)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation())
            .isEqualTo(org.springframework.transaction.annotation.Isolation.REPEATABLE_READ);
    }
}
