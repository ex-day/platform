package io.github.exday.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * S03（GET /discoveries/{discoveryId}）のテスト。固定した「今」は 2026-04-02（春）。
 * データはサンプルデータ（db/sample/R__sample_data.sql）。
 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-04-02T15:00:00+09:00")
class DiscoveryDetailApiTest extends DiscoveryDetailTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void 会話から成立した散歩のDiscoveryを区画ごとに返す() {
        var body = body(sid("disc:walk"));
        assertThat(body).containsEntry("id", sid("disc:walk"))
            .containsEntry("title", "汽車道を通って、桜木町から山下公園へ海沿いの散歩")
            .containsEntry("merge", Map.of("status", "none"))
            .containsEntry("capabilities", Map.of("canStartConversation", true, "canReact", true));
        assertThat((String) body.get("summary")).contains("汽車道");

        var sakuragicho = Map.of("id", sid("place:sakuragicho"), "name", "桜木町駅");
        assertThat(body).containsEntry("displayPlaces", List.of(sakuragicho))
            .containsEntry("detourFrom", sakuragicho)
            .containsEntry("aspects", List.of("散歩"))
            .containsEntry("origins", List.of(Map.of("kind", "conversation", "establishedBy", "auto")))
            .containsEntry("reactions", Map.of("agree", 1, "wantToGo", 1))
            .containsEntry("otherValues", List.of());

        var recommendations = (List<Map<String, Object>>) body.get("recommendations");
        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0)).containsEntry("id", sid("value:walk"))
            .containsEntry("conditions", List.of(Map.of(
                "axis", "season", "label", "季節", "text", "通年", "seasons", List.of("all_year"))));

        // 関わる場所：順番つき（始まり・経由・終わり）。地図に出す形は GeoJSON
        var places = (List<Map<String, Object>>) body.get("places");
        assertThat(places).extracting(p -> ((Map<?, ?>) p.get("place")).get("name"))
            .containsExactly("桜木町駅", "汽車道", "山下公園");
        assertThat(places).extracting(p -> p.get("role")).containsExactly("START", "VIA", "END");
        assertThat(places).extracting(p -> p.get("order")).containsExactly(1, 2, 3);
        assertThat(places).extracting(p -> p.get("isDisplayPlace")).containsExactly(true, false, false);
        assertThat(places.get(0)).containsEntry("spatialType", "point").containsEntry("kinds", List.of("駅"))
            .containsEntry("geometry", Map.of("type", "Point", "coordinates", List.of(139.6308, 35.4509)));
        var route = (Map<String, Object>) places.get(1).get("geometry");
        assertThat(route).containsEntry("type", "LineString");
        assertThat((List<?>) route.get("coordinates")).hasSize(3)
            .first().isEqualTo(List.of(139.6318, 35.4518));
        assertThat(places.get(1)).containsEntry("spatialType", "route").doesNotContainKeys("period", "description");

        var findings = (List<Map<String, Object>>) body.get("findings");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0)).containsEntry("kind", "value").doesNotContainKey("backing")
            .containsEntry("unconfirmed", false)
            .containsEntry("reactions", Map.of("agree", 1, "wantToGo", 0));

        // もとになった会話：非公開の返信は数えない（公開の Post 3件 − 最初の Post）
        var origin = (List<Map<String, Object>>) body.get("originConversations");
        assertThat(origin).hasSize(1);
        assertThat(origin.get(0)).containsEntry("id", sid("conv:walk"))
            .containsEntry("firstPost", "visible")
            .containsEntry("excerpt", "桜木町から山下公園まで散歩したよ！海が見えて気持ちよかった")
            .containsEntry("starterName", "港の散歩人")
            .containsEntry("replyCount", 2);
        assertThat((List<Map<String, Object>>) origin.get(0).get("places")).extracting(p -> p.get("name"))
            .containsExactly("桜木町駅", "汽車道", "山下公園");

        assertThat(body).containsEntry("startedConversations", Map.of("items", List.of(), "totalCount", 0));
        assertThat(body).containsEntry("projects", Map.of("items", List.of(Map.of(
                "id", sid("proj:harbor-course"), "title", "港の見える散歩コース", "type", "course",
                "creatorKind", "user", "creatorName", "はまっこ", "matchedBy", "discovery", "position", 1)),
            "totalCount", 1));
        assertThat(body).containsEntry("relations",
            Map.of("derivedFrom", List.of(), "derivedTo", List.of(), "related", List.of()));
        assertThat(((Map<?, ?>) body.get("context")).get("season")).isEqualTo("spring");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 春は季節が合うValueを先に通年と条件なしのValueを後に推薦する() {
        var body = body(sid("disc:kozukue"));
        var recommendations = (List<Map<String, Object>>) body.get("recommendations");
        assertThat(recommendations).extracting(v -> v.get("id"))
            .containsExactly(sid("value:kozukue-sakura"), sid("value:kozukue-moat"));
        assertThat(recommendations.get(1)).containsEntry("conditions", List.of());
        assertThat(body).containsEntry("otherValues", List.of())
            .containsEntry("origins", List.of(Map.of("kind", "operator", "establishedBy", "human")))
            .containsEntry("displayPlaces", List.of(Map.of("id", sid("place:kozukue-castle"), "name", "小机城址")));
        assertThat((List<Map<String, Object>>) body.get("places")).first()
            .satisfies(p -> assertThat(p).containsEntry("role", "EXPERIENCE").containsEntry("isDisplayPlace", true)
                .doesNotContainKey("order").containsEntry("kinds", List.of("公園", "城跡")));
        // 1つの投稿から自動で成立したが取り消した Discovery の由来の会話は、この Discovery の会話には入らない
        assertThat(body).containsEntry("originConversations", List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void この発見から始まった会話とそのわかってきたことを新しい順に返す() {
        for (int i = 1; i <= 11; i++) {
            long conversation = jdbc.sql("""
                    INSERT INTO conversation (public_id, entry_discovery_id, starter_user_id, public_post_count,
                                              last_public_posted_at, created_at)
                    SELECT :publicId, d.id, u.user_id, 1, :at, :at
                      FROM discovery d, user_profile u
                     WHERE d.public_id = :discovery AND u.nickname = 'はまっこ'
                    RETURNING id
                    """)
                .param("publicId", String.format("startedCon%02d", i))
                .param("at", java.time.OffsetDateTime.parse("2026-04-20T10:00:00+09:00").plusHours(i))
                .param("discovery", sid("disc:walk"))
                .query(Long.class).single();
            jdbc.sql("""
                    INSERT INTO post (conversation_id, user_id, body, posted_at)
                    SELECT :conversation, user_id, :body, :at FROM user_profile WHERE nickname = 'はまっこ'
                    """)
                .param("conversation", conversation).param("body", "汽車道を歩いた " + i)
                .param("at", java.time.OffsetDateTime.parse("2026-04-20T10:00:00+09:00").plusHours(i))
                .update();
        }
        // いちばん新しい会話から取り出した説（裏付けなし）
        jdbc.sql("""
                WITH f AS (
                    INSERT INTO finding (conversation_id, kind, analysis_version)
                    SELECT id, 'THEORY', 'test' FROM conversation WHERE public_id = 'startedCon11'
                    RETURNING id
                )
                INSERT INTO finding_version (finding_id, version, content, analysis_version)
                SELECT id, 1, '昔は汽車道を貨物列車が走っていた', 'test' FROM f
                """).update();

        var body = body(sid("disc:walk"));
        var started = (Map<String, Object>) body.get("startedConversations");
        assertThat(started).containsEntry("totalCount", 11);
        var items = (List<Map<String, Object>>) started.get("items");
        assertThat(items).hasSize(10);
        assertThat(items.get(0)).containsEntry("excerpt", "汽車道を歩いた 11").containsEntry("replyCount", 0)
            .containsEntry("places", List.of());
        assertThat(items.get(9)).containsEntry("excerpt", "汽車道を歩いた 2");

        var findings = (List<Map<String, Object>>) body.get("findings");
        assertThat(findings).hasSize(2);
        assertThat(findings.get(1)).containsEntry("kind", "theory").containsEntry("backing", "unbacked")
            .containsEntry("text", "昔は汽車道を貨物列車が走っていた")
            .containsEntry("reactions", Map.of("maybe", 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 最初のPostが非公開の会話は本文の冒頭と始めた人を返さない() {
        jdbc.sql("UPDATE post SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("post:walk-1")).update();
        jdbc.sql("UPDATE conversation SET public_post_count = public_post_count - 1 WHERE public_id = :id")
            .param("id", sid("conv:walk")).update();

        var card = ((List<Map<String, Object>>) body(sid("disc:walk")).get("originConversations")).get(0);
        assertThat(card).containsEntry("firstPost", "hidden").containsEntry("replyCount", 2)
            .doesNotContainKeys("excerpt", "starterName");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 会話が非公開ならもとになった会話とわかってきたことに出さない() {
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id = :id")
            .param("id", sid("conv:walk")).update();
        var body = body(sid("disc:walk"));
        assertThat(body).containsEntry("originConversations", List.of()).containsEntry("findings", List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 場所を項目に持つ企画はplaceで返し企画ごとに1件にする() {
        // 港の見える散歩コースに、山下公園（散歩の Discovery の終わりの場所・バラの Discovery の場所）を3番目に足す
        jdbc.sql("""
                INSERT INTO project_item (project_id, place_id, position)
                SELECT pr.id, p.id, 3 FROM project pr, place p WHERE pr.public_id = :project AND p.public_id = :place
                """)
            .param("project", sid("proj:harbor-course")).param("place", sid("place:yamashita")).update();

        var roses = (Map<String, Object>) body(sid("disc:roses")).get("projects");
        assertThat(roses).containsEntry("totalCount", 1);
        assertThat((List<Map<String, Object>>) roses.get("items")).singleElement()
            .satisfies(p -> assertThat(p).containsEntry("matchedBy", "place").containsEntry("position", 3));

        // 散歩の Discovery は、Discovery を指す項目と場所を指す項目の両方が当てはまるが、企画は1件で discovery
        var walk = (Map<String, Object>) body(sid("disc:walk")).get("projects");
        assertThat(walk).containsEntry("totalCount", 1);
        assertThat((List<Map<String, Object>>) walk.get("items")).singleElement()
            .satisfies(p -> assertThat(p).containsEntry("matchedBy", "discovery").containsEntry("position", 1));
    }

    @Test
    void 統合されたDiscoveryは最後の統合先を案内し新しい会話とリアクションを受け付けない() {
        merge("disc:roses", "disc:kozukue");
        merge("disc:kozukue", "disc:ooka-sakura");
        var body = body(sid("disc:roses"));
        assertThat(body).containsEntry("merge", Map.of("status", "merged",
                "target", Map.of("id", sid("disc:ooka-sakura"), "title", "大岡川の桜並木を、川沿いに歩く")))
            .containsEntry("capabilities", Map.of("canStartConversation", false, "canReact", false));
    }

    @Test
    void 統合先が見えないときは統合されたことだけを返す() {
        merge("disc:roses", "disc:kozukue");
        jdbc.sql("UPDATE discovery SET visibility = 'HIDDEN' WHERE public_id = :id")
            .param("id", sid("disc:kozukue")).update();
        assertThat(body(sid("disc:roses"))).containsEntry("merge", Map.of("status", "merged"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 関係するDiscoveryは見えるものだけを派生元と派生したものと関連に分ける() {
        long decision = operatorDecision("DERIVE");
        jdbc.sql("""
                INSERT INTO discovery_relation (from_discovery_id, to_discovery_id, kind, created_decision_id)
                SELECT f.id, t.id, k.kind, :decision
                  FROM (VALUES (:walk, :roses, 'DERIVED'), (:kozukue, :walk, 'DERIVED'), (:walk, :ooka, 'RELATED'),
                               (:walk, :bamboo, 'RELATED')) AS k (f, t, kind)
                  JOIN discovery f ON f.public_id = k.f
                  JOIN discovery t ON t.public_id = k.t
                """)
            .param("decision", decision).param("walk", sid("disc:walk")).param("roses", sid("disc:roses"))
            .param("kozukue", sid("disc:kozukue")).param("ooka", sid("disc:ooka-sakura"))
            .param("bamboo", sid("disc:bamboo"))
            .update();

        var relations = (Map<String, Object>) body(sid("disc:walk")).get("relations");
        assertThat((List<Map<String, Object>>) relations.get("derivedFrom")).extracting(d -> d.get("id"))
            .containsExactly(sid("disc:kozukue"));
        assertThat((List<Map<String, Object>>) relations.get("derivedTo")).extracting(d -> d.get("id"))
            .containsExactly(sid("disc:roses"));
        // 成立を取り消した竹林は出さない
        var related = (List<Map<String, Object>>) relations.get("related");
        assertThat(related).extracting(d -> d.get("id")).containsExactly(sid("disc:ooka-sakura"));
        assertThat(related.get(0)).containsEntry("displayPlaces",
            List.of(Map.of("id", sid("place:ooka"), "name", "大岡川")));
    }

    @Test
    void 存在しないDiscoveryは404を返す() {
        var response = get("doesNotExist");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void 成立を取り消したDiscoveryは404を返す() {
        assertThat(get(sid("disc:bamboo")).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void 非公開のDiscoveryは404を返す() {
        jdbc.sql("UPDATE discovery SET visibility = 'HIDDEN' WHERE public_id = :id")
            .param("id", sid("disc:walk")).update();
        var response = get(sid("disc:walk"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void 識別子の形が合わなければproblemJsonの400を返す() {
        for (var id : List.of("short", "a".repeat(13), "abc-def_ghij")) {
            var response = get(id);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(response.getBody()).containsEntry("status", 400).containsKeys("title")
                .doesNotContainKeys("trace", "exception", "message");
        }
    }
}
