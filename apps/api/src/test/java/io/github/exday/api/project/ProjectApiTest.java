package io.github.exday.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exday.api.DbApiTestSupport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S15（GET /projects/{projectId} と、タイムラインの続き）のテスト。
 * データはサンプルデータ（db/sample/R__sample_data.sql）。固定した「今」は 2026-04-02（春）。
 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-04-02T15:00:00+09:00")
@SuppressWarnings("unchecked")
class ProjectApiTest extends DbApiTestSupport {

    private Map<String, Object> project(String key) {
        return okJson("/projects/" + sid(key));
    }

    private static Map<String, Object> ref(String key, String name) {
        return Map.of("id", sid(key), "name", name);
    }

    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static void assertSameInstant(Object actual, String expected) {
        assertThat(OffsetDateTime.parse((String) actual).toInstant()).isEqualTo(OffsetDateTime.parse(expected).toInstant());
    }

    /** タイムラインの1つを、種類と識別子だけにする（"change:..." / "post:..."）。 */
    private static String entry(Map<String, Object> entry) {
        var kind = (String) entry.get("kind");
        var body = map(entry.get(kind));
        return kind + ":" + body.get("id");
    }

    private static List<String> entries(Object timeline) {
        return list(map(timeline).get("items")).stream().map(ProjectApiTest::entry).toList();
    }

    // ---- 全体 ----

    @Test
    void 急坂番付の区画を返す() {
        var body = project("proj:slopes");
        assertThat(body).containsEntry("id", sid("proj:slopes")).containsEntry("type", "ranking")
            .containsEntry("title", "鶴見の急坂番付（推定）").containsEntry("callText", "みんなの知ってる急坂はどこ？")
            .containsEntry("description", "標高のデータと道路から計算した勾配の順に並べた、推定の番付です。")
            .containsEntry("creatorKind", "operator").containsEntry("creatorName", "ex-day 運営")
            .containsEntry("ownConversationId", sid("conv:slopes-own"));

        var items = list(body.get("items"));
        assertThat(items).extracting(i -> (Object) i.get("id"))
            .containsExactly(sid("item:slopes-a"), sid("item:slopes-b"), sid("item:slopes-d"), sid("item:slopes-c"));
        assertThat(items).extracting(i -> (Object) i.get("position")).containsExactly(1, 2, 3, 4);
        var slopeD = items.get(2);
        assertThat(slopeD).containsEntry("kind", "place").containsEntry("place", ref("place:slope-d", "鶴見の坂D"))
            .containsEntry("placeKinds", List.of("坂")).containsEntry("spatialType", "route")
            .containsEntry("anchor", List.of(139.66, 35.498)).containsEntry("conversationCount", 0)
            .doesNotContainKey("note");
        assertThat(map(slopeD.get("geometry"))).containsEntry("type", "LineString");
        assertThat(list(slopeD.get("estimates"))).singleElement().satisfies(e -> {
            assertThat(e).containsEntry("metric", "SLOPE_GRADE").containsEntry("value", 13.1)
                .containsEntry("method", "elevation-x-road").containsEntry("methodVersion", "sample-1");
            assertSameInstant(e.get("computedAt"), "2026-03-20T09:00:00+09:00");
        });

        // タイムライン：作った出来事、声2つ、変えた出来事（時点の順）
        assertThat(entries(body.get("timeline"))).containsExactly(
            "change:" + sid("change:slopes-created"), "post:" + sid("post:slopes-own-1"),
            "post:" + sid("post:slopes-own-2"), "change:" + sid("change:slopes-add-d"));
        assertThat(map(body.get("timeline"))).doesNotContainKey("olderCursor");
        var changes = list(map(body.get("timeline")).get("items"));
        assertThat(map(changes.get(3).get("change"))).containsEntry("kind", "updated")
            .containsEntry("changedFields", List.of())
            .containsEntry("items", List.of(
                Map.of("kind", "added", "afterPosition", 3,
                    "target", Map.of("kind", "place", "place", ref("place:slope-d", "鶴見の坂D"))),
                Map.of("kind", "moved", "beforePosition", 3, "afterPosition", 4,
                    "target", Map.of("kind", "place", "place", ref("place:slope-c", "鶴見の坂C")))));
        assertThat(map(changes.get(2).get("post"))).containsEntry("replyTo", List.of(sid("post:slopes-own-1")))
            .containsEntry("authorName", "昔の鶴見っ子");

        // 企画をきっかけにした会話（企画そのものの会話は入れない）
        var triggered = map(body.get("triggeredConversations"));
        assertThat(triggered).containsEntry("totalCount", 1);
        assertThat(list(triggered.get("items"))).extracting(c -> (Object) c.get("id")).containsExactly(sid("conv:slopes-snow"));
    }

    @Test
    void コースのDiscoveryの項目は地図の場所と代表点と2種類の会話の数を返す() {
        var body = project("proj:harbor-course");
        assertThat(body).containsEntry("type", "course").containsEntry("creatorKind", "user")
            .containsEntry("creatorName", "はまっこ").doesNotContainKey("ownConversationId");
        var items = list(body.get("items"));
        var walk = items.get(0);
        assertThat(walk).containsEntry("kind", "discovery").containsEntry("position", 1)
            .containsEntry("note", "汽車道を通るのがおすすめ")
            .containsEntry("originConversationCount", 1).containsEntry("startedConversationCount", 0);
        assertThat(map(walk.get("discovery"))).containsEntry("id", sid("disc:walk"))
            .containsEntry("displayPlaces", List.of(ref("place:sakuragicho", "桜木町駅")));
        assertThat(list(walk.get("mapPlaces"))).singleElement().satisfies(p -> assertThat(p)
            .containsEntry("place", ref("place:sakuragicho", "桜木町駅")).containsEntry("spatialType", "point"));
        var shape = map(list(walk.get("mapPlaces")).get(0).get("geometry"));
        // 点の代表点はその点
        assertThat(walk.get("anchor")).isEqualTo(shape.get("coordinates"));
        assertThat(walk).doesNotContainKeys("estimates", "conversationCount");

        var hikawa = items.get(1);
        assertThat(hikawa).containsEntry("kind", "place").containsEntry("place", ref("place:hikawamaru", "氷川丸"))
            .containsEntry("position", 2).doesNotContainKey("note").containsEntry("estimates", List.of());

        // 企画そのものの会話がなくても、出来事だけでタイムラインを返す
        assertThat(entries(body.get("timeline"))).containsExactly("change:" + sid("change:course-created"));
        var created = map(list(map(body.get("timeline")).get("items")).get(0).get("change"));
        assertThat(list(created.get("items")).get(0)).containsEntry("target", Map.of("kind", "discovery",
            "discovery", Map.of("id", sid("disc:walk"), "title", "汽車道を通って、桜木町から山下公園へ海沿いの散歩")));

        // この Discovery から始まった会話は、由来の会話と分けて数える
        jdbc.sql("UPDATE conversation SET entry_discovery_id = (SELECT id FROM discovery WHERE public_id = :d) WHERE public_id = :c")
            .param("d", sid("disc:walk")).param("c", sid("conv:soba")).update();
        assertThat(list(project("proj:harbor-course").get("items")).get(0))
            .containsEntry("originConversationCount", 1).containsEntry("startedConversationCount", 1);
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id IN (:ids)")
            .param("ids", List.of(sid("conv:soba"), sid("conv:walk"))).update();
        assertThat(list(project("proj:harbor-course").get("items")).get(0))
            .containsEntry("originConversationCount", 0).containsEntry("startedConversationCount", 0);
    }

    @Test
    void エリアの場所の代表点は形の中の1点() {
        jdbc.sql("""
                INSERT INTO project_item (public_id, project_id, place_id, position)
                SELECT 'courseNishi1', pr.id, p.id, 3 FROM project pr, place p WHERE pr.public_id = :pr AND p.public_id = :p
                """).param("pr", sid("proj:harbor-course")).param("p", sid("place:nishi")).update();
        var nishi = list(project("proj:harbor-course").get("items")).get(2);
        assertThat(nishi).containsEntry("spatialType", "area");
        var anchor = (List<Double>) nishi.get("anchor");
        // 西区のエリア（139.605〜139.635, 35.448〜35.472）の中
        assertThat(anchor.get(0)).isBetween(139.605, 139.635);
        assertThat(anchor.get(1)).isBetween(35.448, 35.472);
    }

    // ---- 表示できない項目 ----

    @Test
    void 見えないDiscoveryの項目は位置だけを残し統合は最後の統合先を出す() {
        // 統合：最後の統合先が見えれば、統合先のカード
        merge("disc:walk", "disc:roses");
        var merged = list(project("proj:harbor-course").get("items")).get(0);
        assertThat(merged).containsEntry("kind", "discovery").containsEntry("id", sid("item:course-1"));
        assertThat(map(merged.get("discovery"))).containsEntry("id", sid("disc:roses"));
        // 動いた項目の指す先も、今の状態で出す
        var created = map(list(map(project("proj:harbor-course").get("timeline")).get("items")).get(0).get("change"));
        assertThat(map(list(created.get("items")).get(0).get("target"))).containsEntry("discovery",
            Map.of("id", sid("disc:roses"), "title", "山下公園のバラを、海と一緒に見る"));

        // 統合先が非公開なら、表示できない項目（位置だけ）
        jdbc.sql("UPDATE discovery SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("disc:roses")).update();
        var body = project("proj:harbor-course");
        var items = list(body.get("items"));
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).isEqualTo(Map.of("kind", "unavailable", "position", 1));
        assertThat(items.get(1)).containsEntry("kind", "place").containsEntry("position", 2);
        var hidden = map(list(map(body.get("timeline")).get("items")).get(0).get("change"));
        assertThat(map(list(hidden.get("items")).get(0).get("target"))).isEqualTo(Map.of("kind", "unavailable"));
    }

    @Test
    void 成立を取り消したDiscoveryと統合が循環しているDiscoveryの項目は表示できない() {
        jdbc.sql("UPDATE discovery SET withdrawn_at = now() WHERE public_id = :id").param("id", sid("disc:walk")).update();
        assertThat(list(project("proj:harbor-course").get("items")).get(0)).isEqualTo(Map.of("kind", "unavailable", "position", 1));

        jdbc.sql("UPDATE discovery SET withdrawn_at = NULL WHERE public_id = :id").param("id", sid("disc:walk")).update();
        merge("disc:walk", "disc:roses");
        merge("disc:roses", "disc:walk");
        assertThat(list(project("proj:harbor-course").get("items")).get(0)).isEqualTo(Map.of("kind", "unavailable", "position", 1));
    }

    @Test
    void まとめ記憶の企画は順番を返さない() {
        jdbc.sql("UPDATE project SET type = 'COLLECTION' WHERE public_id = :id").param("id", sid("proj:slopes")).update();
        var body = project("proj:slopes");
        assertThat(list(body.get("items"))).allSatisfy(i -> assertThat(i).doesNotContainKey("position"));
        var changed = map(list(map(body.get("timeline")).get("items")).get(3).get("change"));
        assertThat(list(changed.get("items"))).allSatisfy(i -> assertThat(i).doesNotContainKeys("beforePosition", "afterPosition"));
    }

    // ---- タイムライン ----

    @Test
    void タイムラインは新しい側から一定数を返し続きはカーソルで重複なく読める() {
        // 企画そのものの会話に Post を25件足し、1件は変えた出来事と同じ時点にする（変更の出来事が先）
        jdbc.sql("""
                INSERT INTO post (public_id, conversation_id, user_id, body, posted_at)
                SELECT 'slopePost' || lpad(i::text, 3, '0'), c.id, (SELECT user_id FROM user_profile WHERE nickname = '坂好き'),
                       '坂の話 ' || i, timestamptz '2026-04-13 00:00+09' + make_interval(mins => i)
                  FROM conversation c, generate_series(1, 25) AS i WHERE c.public_id = :c
                """).param("c", sid("conv:slopes-own")).update();
        jdbc.sql("""
                INSERT INTO post (public_id, conversation_id, user_id, body, posted_at)
                SELECT 'slopePostTie', c.id, (SELECT user_id FROM user_profile WHERE nickname = '坂好き'), '同じ時点',
                       timestamptz '2026-04-12 20:00+09'
                  FROM conversation c WHERE c.public_id = :c
                """).param("c", sid("conv:slopes-own")).update();
        jdbc.sql("UPDATE post SET visibility = 'HIDDEN' WHERE public_id = 'slopePost010'").update();

        // 全部で 2 + 2 + 1 + 25 = 30。新しい側から20件
        var timeline = map(project("proj:slopes").get("timeline"));
        var first = entries(timeline);
        assertThat(first).hasSize(20).startsWith("post:slopePost006").endsWith("post:slopePost025");
        assertThat(first).contains("post:slopePost010");
        var hiddenPost = list(timeline.get("items")).stream()
            .filter(e -> "post:slopePost010".equals(entry(e))).findFirst().orElseThrow();
        assertThat(map(hiddenPost.get("post"))).containsEntry("visibility", "hidden").doesNotContainKeys("body", "authorName");
        assertThat(timeline).containsEntry("olderCursor", "pslopePost006");

        // 前の10件：同じ時点なら変更の出来事が先
        var older = okJson("/projects/" + sid("proj:slopes") + "/timeline?before=pslopePost006");
        assertThat(entries(older)).containsExactly(
            "change:" + sid("change:slopes-created"), "post:" + sid("post:slopes-own-1"),
            "post:" + sid("post:slopes-own-2"), "change:" + sid("change:slopes-add-d"), "post:slopePostTie",
            "post:slopePost001", "post:slopePost002", "post:slopePost003", "post:slopePost004", "post:slopePost005");
        assertThat(older).doesNotContainKey("olderCursor");

        // 変更の出来事のカーソルでも読める。limit で数を変えられる
        var beforeChange = okJson("/projects/" + sid("proj:slopes") + "/timeline?before=c" + sid("change:slopes-add-d") + "&limit=2");
        assertThat(entries(beforeChange)).containsExactly("post:" + sid("post:slopes-own-1"), "post:" + sid("post:slopes-own-2"));
        assertThat(beforeChange).containsEntry("olderCursor", "p" + sid("post:slopes-own-1"));
        var beforeTie = okJson("/projects/" + sid("proj:slopes") + "/timeline?before=pslopePostTie&limit=1");
        assertThat(entries(beforeTie)).containsExactly("change:" + sid("change:slopes-add-d"));

        // timelineLimit で数を変えられる
        assertThat(entries(okJson("/projects/" + sid("proj:slopes") + "?timelineLimit=2").get("timeline")))
            .containsExactly("post:slopePost024", "post:slopePost025");
    }

    @Test
    void 変えた表示の項目は決まった順で中身を写さずに返す() {
        jdbc.sql("UPDATE project_change SET fields_changed = ARRAY['DESCRIPTION', 'TITLE'] WHERE public_id = :id")
            .param("id", sid("change:slopes-add-d")).update();
        var changed = map(list(map(project("proj:slopes").get("timeline")).get("items")).get(3).get("change"));
        assertThat(changed).containsEntry("changedFields", List.of("title", "description"));
    }

    // ---- 公開 ----

    @Test
    void 非公開の企画と形の合わない識別子とカーソルは404と400を返す() {
        jdbc.sql("UPDATE project SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("proj:slopes")).update();
        for (var path : List.of("/projects/" + sid("proj:slopes"),
                "/projects/" + sid("proj:slopes") + "/timeline?before=p" + sid("post:slopes-own-2"),
                "/projects/doesNotExist")) {
            var response = getJson(path);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(404);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
        for (var path : List.of("/projects/short", "/projects/" + sid("proj:harbor-course") + "/timeline",
                "/projects/" + sid("proj:harbor-course") + "/timeline?before=x" + sid("post:slopes-own-2"),
                "/projects/" + sid("proj:harbor-course") + "?timelineLimit=0")) {
            assertThat(getJson(path).getStatusCode().value()).as(path).isEqualTo(400);
        }
        // カーソルが別の企画のものなら 404
        assertThat(getJson("/projects/" + sid("proj:harbor-course") + "/timeline?before=c" + sid("change:slopes-created"))
            .getStatusCode().value()).isEqualTo(404);
        assertThat(getJson("/projects/" + sid("proj:harbor-course") + "/timeline?before=p" + sid("post:slopes-own-2"))
            .getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void 非公開の会話は企画をきっかけにした会話と企画そのものの会話に出さない() {
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id IN (:ids)")
            .param("ids", List.of(sid("conv:slopes-snow"), sid("conv:slopes-own"))).update();
        var body = project("proj:slopes");
        assertThat(body).containsEntry("triggeredConversations", Map.of("items", List.of(), "totalCount", 0))
            .doesNotContainKey("ownConversationId");
        assertThat(entries(body.get("timeline"))).containsExactly(
            "change:" + sid("change:slopes-created"), "change:" + sid("change:slopes-add-d"));
    }

    // ---- 読み取りの一貫性 ----

    @Test
    void S15の応答はREPEATABLE_READの読み取り専用トランザクションで作る() throws Exception {
        for (var method : List.of(
                ProjectService.class.getMethod("find", String.class, int.class),
                ProjectService.class.getMethod("findOlderTimeline", String.class, String.class, int.class))) {
            var transactional = method.getAnnotation(Transactional.class);
            assertThat(transactional.readOnly()).as(method.getName()).isTrue();
            assertThat(transactional.isolation()).as(method.getName()).isEqualTo(Isolation.REPEATABLE_READ);
        }
    }
}
