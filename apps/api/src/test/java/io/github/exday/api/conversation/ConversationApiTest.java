package io.github.exday.api.conversation;

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
 * S14（GET /conversations/{conversationId} と、Post・わかってきたことの続き）のテスト。
 * データはサンプルデータ（db/sample/R__sample_data.sql）。固定した「今」は 2026-04-02（春）。
 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-04-02T15:00:00+09:00")
@SuppressWarnings("unchecked")
class ConversationApiTest extends DbApiTestSupport {

    private Map<String, Object> conversation(String key) {
        return okJson("/conversations/" + sid(key));
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

    // ---- 全体 ----

    @Test
    void 散歩の会話の区画を返す() {
        var body = conversation("conv:walk");
        assertThat(body).containsEntry("id", sid("conv:walk")).containsEntry("publicPostCount", 3)
            .doesNotContainKey("entry");
        assertSameInstant(body.get("startedAt"), "2026-04-05T10:00:00+09:00");

        assertThat(body).containsEntry("places", List.of(
            Map.of("place", ref("place:sakuragicho", "桜木町駅"), "role", "START", "order", 1, "status", "adopted",
                "source", "conversation"),
            Map.of("place", ref("place:kishamichi", "汽車道"), "role", "VIA", "order", 2, "status", "adopted",
                "source", "conversation"),
            Map.of("place", ref("place:yamashita", "山下公園"), "role", "END", "order", 3, "status", "adopted",
                "source", "conversation")));

        // 採用中の候補は採用の順番に全部。ほかの候補はない
        var candidates = map(body.get("placeCandidates"));
        assertThat(list(candidates.get("adopted"))).extracting(c -> (Object) c.get("id"))
            .containsExactly(sid("cand:walk-sakuragicho"), sid("cand:walk-kishamichi"), sid("cand:walk-yamashita"));
        assertThat(list(candidates.get("adopted")).get(1))
            .containsEntry("target", Map.of("kind", "conversation"))
            .containsEntry("place", Map.of("kind", "place", "place", ref("place:kishamichi", "汽車道")))
            .containsEntry("status", "considering")
            .containsEntry("adoption", Map.of("status", "adopted", "adoptedBy", "conversationStarter"))
            .containsEntry("basisPostIds", List.of(sid("post:walk-2"), sid("post:walk-3")))
            .containsEntry("voices", Map.of("support", 1, "oppose", 0));
        assertThat(candidates).containsEntry("others", List.of()).containsEntry("othersTotalCount", 0);

        assertThat(body).containsEntry("questions",
            Map.of("answered", List.of(), "others", List.of(), "othersTotalCount", 0, "moved", List.of()));

        var findings = map(body.get("findings"));
        assertThat(findings).containsEntry("totalCount", 1).containsEntry("moved", List.of()).doesNotContainKey("nextCursor");
        assertThat(list(findings.get("items"))).singleElement().satisfies(f -> assertThat(f)
            .containsEntry("id", sid("finding:walk-value")).containsEntry("version", 1)
            .containsEntry("reactions", Map.of("agree", 1, "wantToGo", 0)));

        var discoveries = map(body.get("discoveries"));
        assertThat(discoveries).containsEntry("totalCount", 1);
        assertThat(list(discoveries.get("items"))).singleElement().satisfies(d -> assertThat(d)
            .containsEntry("id", sid("disc:walk"))
            .containsEntry("displayPlaces", List.of(ref("place:sakuragicho", "桜木町駅"))));

        // 場所が決まっていて問いもないので、届くところはない
        assertThat(body).containsEntry("reach", Map.of("items", List.of(), "totalCount", 0));
    }

    @Test
    void 非公開のPostは識別子と時点と返信先だけを返し本文と書いた人を返さない() {
        var posts = map(conversation("conv:walk").get("posts"));
        assertThat(map(posts.get("first"))).containsEntry("id", sid("post:walk-1"))
            .containsEntry("visibility", "visible").containsEntry("authorName", "港の散歩人")
            .containsEntry("replyTo", List.of()).containsEntry("reactions", Map.of("empathy", 1));
        var recent = list(posts.get("recent"));
        assertThat(recent).extracting(p -> (Object) p.get("id"))
            .containsExactly(sid("post:walk-2"), sid("post:walk-3"), sid("post:walk-4"));
        assertThat(recent.get(0)).containsEntry("replyTo", List.of(sid("post:walk-1")));
        var hidden = recent.get(2);
        assertThat(hidden.keySet()).containsExactlyInAnyOrder("id", "visibility", "postedAt", "replyTo");
        assertThat(hidden).containsEntry("visibility", "hidden");
        assertThat(posts).doesNotContainKey("olderCursor");
    }

    @Test
    void 入口を種類ごとに返し見えない入口は返さない() {
        assertThat(conversation("conv:kozukue"))
            .containsEntry("entry", Map.of("kind", "place", "place", ref("place:kozukue-castle", "小机城址")));
        assertThat(conversation("conv:slopes-own")).containsEntry("entry", Map.of("kind", "project",
            "project", Map.of("id", sid("proj:slopes"), "title", "鶴見の急坂番付（推定）"), "relation", "own"));
        assertThat(conversation("conv:slopes-snow")).containsEntry("entry", Map.of("kind", "project",
            "project", Map.of("id", sid("proj:slopes"), "title", "鶴見の急坂番付（推定）"), "relation", "triggered"));

        // 企画が非公開なら入口を返さない（会話は残す）
        jdbc.sql("UPDATE project SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("proj:slopes")).update();
        assertThat(conversation("conv:slopes-snow")).doesNotContainKey("entry");

        // Discovery の入口：統合されていれば最後の統合先、成立を取り消していれば返さない
        jdbc.sql("UPDATE conversation SET entry_discovery_id = (SELECT id FROM discovery WHERE public_id = :d) WHERE public_id = :c")
            .param("d", sid("disc:roses")).param("c", sid("conv:soba")).update();
        assertThat(conversation("conv:soba")).containsEntry("entry", Map.of("kind", "discovery",
            "discovery", Map.of("id", sid("disc:roses"), "title", "山下公園のバラを、海と一緒に見る")));
        merge("disc:roses", "disc:walk");
        assertThat(map(conversation("conv:soba").get("entry"))).containsEntry("discovery",
            Map.of("id", sid("disc:walk"), "title", "汽車道を通って、桜木町から山下公園へ海沿いの散歩"));
        jdbc.sql("UPDATE discovery SET withdrawn_at = now() WHERE public_id = :id").param("id", sid("disc:walk")).update();
        assertThat(conversation("conv:soba")).doesNotContainKey("entry");
    }

    @Test
    void 非公開の会話と形の合わない識別子は全部のAPIで404と400を返す() {
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("conv:walk")).update();
        for (var path : List.of("/conversations/" + sid("conv:walk"),
                "/conversations/" + sid("conv:walk") + "/posts?before=" + sid("post:walk-3"),
                "/conversations/" + sid("conv:walk") + "/findings?after=" + sid("finding:walk-value"),
                "/conversations/doesNotExist")) {
            var response = getJson(path);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(404);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
        for (var path : List.of("/conversations/short", "/conversations/" + sid("conv:soba") + "/posts?before=x",
                "/conversations/" + sid("conv:soba") + "/posts", "/conversations/" + sid("conv:soba") + "?postLimit=51")) {
            assertThat(getJson(path).getStatusCode().value()).as(path).isEqualTo(400);
        }
        // カーソルが別の会話のものなら 404
        assertThat(getJson("/conversations/" + sid("conv:soba") + "/posts?before=" + sid("post:walk-3"))
            .getStatusCode().value()).isEqualTo(404);
        assertThat(getJson("/conversations/" + sid("conv:soba") + "/findings?after=" + sid("finding:walk-value"))
            .getStatusCode().value()).isEqualTo(404);
    }

    // ---- Post の続き ----

    /** そば屋の会話に Post を足す（最初の Post より後）。i が大きいほど新しい。 */
    private void addSobaPosts(int count, String atBase) {
        jdbc.sql("""
                INSERT INTO post (public_id, conversation_id, user_id, body, posted_at)
                SELECT 'sobaPost' || lpad(i::text, 4, '0'), c.id, c.starter_user_id, 'そば屋の話 ' || i,
                       CAST(:base AS timestamptz) + make_interval(mins => i)
                  FROM conversation c, generate_series(1, :count) AS i
                 WHERE c.public_id = :c
                """)
            .param("c", sid("conv:soba")).param("count", count).param("base", atBase).update();
    }

    @Test
    void Postは最初と新しい一定数を返し続きはカーソルで重複なく読める() {
        // 最初の Post（soba-1）、soba-2（22:00）、足した25件（23:01〜）。2件は同じ時点にして識別子で並べる
        addSobaPosts(25, "2026-04-15 23:00+09");
        jdbc.sql("UPDATE post SET posted_at = (SELECT posted_at FROM post WHERE public_id = 'sobaPost0010') WHERE public_id = 'sobaPost0011'")
            .update();

        var posts = map(conversation("conv:soba").get("posts"));
        assertThat(map(posts.get("first"))).containsEntry("id", sid("post:soba-1"));
        var recent = list(posts.get("recent"));
        assertThat(recent).hasSize(20);
        assertThat(recent.get(0)).containsEntry("id", "sobaPost0006");
        assertThat(recent.get(19)).containsEntry("id", "sobaPost0025");
        assertThat(posts).containsEntry("olderCursor", "sobaPost0006");

        // 読んでいる間に新しい Post が足されても、続きの範囲はずれない
        jdbc.sql("""
                INSERT INTO post (public_id, conversation_id, user_id, body, posted_at)
                SELECT 'sobaPostNew1', id, starter_user_id, '新しい Post', '2026-04-20 10:00+09' FROM conversation WHERE public_id = :c
                """).param("c", sid("conv:soba")).update();

        // カーソルより前を新しい側から4件、古い順に
        var older = okJson("/conversations/" + sid("conv:soba") + "/posts?before=sobaPost0006&limit=4");
        assertThat(list(older.get("items"))).extracting(p -> (Object) p.get("id"))
            .containsExactly("sobaPost0002", "sobaPost0003", "sobaPost0004", "sobaPost0005");
        assertThat(older).containsEntry("olderCursor", "sobaPost0002");
        // その前は soba-2 と 0001 だけ。最初の Post は含めず、続きはない
        var oldest = okJson("/conversations/" + sid("conv:soba") + "/posts?before=sobaPost0002&limit=4");
        assertThat(list(oldest.get("items"))).extracting(p -> (Object) p.get("id"))
            .containsExactly(sid("post:soba-2"), "sobaPost0001");
        assertThat(oldest).doesNotContainKey("olderCursor");
        var last = okJson("/conversations/" + sid("conv:soba") + "/posts?before=" + sid("post:soba-2"));
        assertThat(last).containsEntry("items", List.of()).doesNotContainKey("olderCursor");

        // 同じ時点の2件は識別子の順
        var tie = okJson("/conversations/" + sid("conv:soba") + "/posts?before=sobaPost0012&limit=2");
        assertThat(list(tie.get("items"))).extracting(p -> (Object) p.get("id"))
            .containsExactly("sobaPost0010", "sobaPost0011");

        // postLimit で数を変えられる
        var small = map(okJson("/conversations/" + sid("conv:soba") + "?postLimit=3").get("posts"));
        assertThat(list(small.get("recent"))).extracting(p -> (Object) p.get("id"))
            .containsExactly("sobaPost0024", "sobaPost0025", "sobaPostNew1");
    }

    // ---- わかってきたこと ----

    /** 散歩の会話に、わかってきたことを足す（i が大きいほど新しい）。 */
    private void addWalkFindings(int count) {
        jdbc.sql("""
                WITH f AS (
                    INSERT INTO finding (public_id, conversation_id, kind, analysis_version, created_at)
                    SELECT 'walkFind' || lpad(i::text, 4, '0'), c.id, 'THEORY', 'test',
                           timestamptz '2026-04-07 00:00+09' + make_interval(hours => i)
                      FROM conversation c, generate_series(1, :count) AS i
                     WHERE c.public_id = :c
                    RETURNING id, public_id, created_at
                )
                INSERT INTO finding_version (finding_id, version, content, analysis_version, created_at)
                SELECT id, 1, '散歩の説 ' || public_id, 'test', created_at FROM f
                """)
            .param("c", sid("conv:walk")).param("count", count).update();
    }

    @Test
    void わかってきたことは最初の一定数と全体の数を返し続きをカーソルで読める() {
        addWalkFindings(25);
        var findings = map(conversation("conv:walk").get("findings"));
        assertThat(findings).containsEntry("totalCount", 26).containsEntry("nextCursor", "walkFind0019");
        var items = list(findings.get("items"));
        assertThat(items).hasSize(20);
        assertThat(items.get(0)).containsEntry("id", sid("finding:walk-value"));
        assertThat(items.get(19)).containsEntry("id", "walkFind0019");

        var next = okJson("/conversations/" + sid("conv:walk") + "/findings?after=walkFind0019");
        assertThat(list(next.get("items"))).extracting(f -> (Object) f.get("id"))
            .containsExactly("walkFind0020", "walkFind0021", "walkFind0022", "walkFind0023", "walkFind0024", "walkFind0025");
        assertThat(next).containsEntry("totalCount", 26).containsEntry("moved", List.of()).doesNotContainKey("nextCursor");

        // findingLimit で数を変えられる
        var small = map(okJson("/conversations/" + sid("conv:walk") + "?findingLimit=2").get("findings"));
        assertThat(small).containsEntry("nextCursor", "walkFind0001");
    }

    @Test
    void カーソルのわかってきたことが統合や失効しても続きを読める() {
        addWalkFindings(5);
        jdbc.sql("UPDATE finding SET status = 'MERGED', merged_into_finding_id = (SELECT id FROM finding WHERE public_id = 'walkFind0005') WHERE public_id = 'walkFind0002'")
            .update();
        jdbc.sql("UPDATE finding SET status = 'EXPIRED' WHERE public_id = 'walkFind0003'").update();
        var afterMerged = okJson("/conversations/" + sid("conv:walk") + "/findings?after=walkFind0002");
        assertThat(list(afterMerged.get("items"))).extracting(f -> (Object) f.get("id"))
            .containsExactly("walkFind0004", "walkFind0005");
        var afterExpired = okJson("/conversations/" + sid("conv:walk") + "/findings?after=walkFind0003");
        assertThat(list(afterExpired.get("items"))).extracting(f -> (Object) f.get("id"))
            .containsExactly("walkFind0004", "walkFind0005");
        assertThat(afterExpired).containsEntry("totalCount", 4);
    }

    @Test
    void 版が新しくなっても並びの位置は変わらず更新の時点と版で見分けられる() {
        addWalkFindings(2);
        jdbc.sql("""
                INSERT INTO finding_version (finding_id, version, content, analysis_version, created_at)
                SELECT id, 2, '散歩の説（言い換え）', 'test', timestamptz '2026-04-10 00:00+09' FROM finding WHERE public_id = 'walkFind0001'
                """).update();
        jdbc.sql("UPDATE finding SET current_version = 2 WHERE public_id = 'walkFind0001'").update();
        var items = list(map(conversation("conv:walk").get("findings")).get("items"));
        assertThat(items).extracting(f -> (Object) f.get("id"))
            .containsExactly(sid("finding:walk-value"), "walkFind0001", "walkFind0002");
        var updated = items.get(1);
        assertThat(updated).containsEntry("version", 2).containsEntry("text", "散歩の説（言い換え）");
        assertSameInstant(updated.get("createdAt"), "2026-04-07T01:00:00+09:00");
        assertSameInstant(updated.get("updatedAt"), "2026-04-10T00:00:00+09:00");
    }

    @Test
    void 統合されて別の公開の会話へ移ったわかってきたことだけを案内する() {
        addWalkFindings(2);
        // そば屋の会話にわかってきたことを作り、散歩の会話の1件をそこへ統合する。もう1件は同じ会話の中で統合する
        jdbc.sql("""
                WITH f AS (
                    INSERT INTO finding (public_id, conversation_id, kind, analysis_version)
                    SELECT 'sobaFinding1', id, 'THEORY', 'test' FROM conversation WHERE public_id = :c RETURNING id
                )
                INSERT INTO finding_version (finding_id, version, content, analysis_version)
                SELECT id, 1, 'そば屋の説', 'test' FROM f
                """).param("c", sid("conv:soba")).update();
        jdbc.sql("UPDATE finding SET status = 'MERGED', merged_into_finding_id = (SELECT id FROM finding WHERE public_id = 'sobaFinding1') WHERE public_id = 'walkFind0001'")
            .update();
        jdbc.sql("UPDATE finding SET status = 'MERGED', merged_into_finding_id = (SELECT id FROM finding WHERE public_id = :id) WHERE public_id = 'walkFind0002'")
            .param("id", sid("finding:walk-value")).update();

        var findings = map(conversation("conv:walk").get("findings"));
        assertThat(findings).containsEntry("totalCount", 1)
            .containsEntry("moved", List.of(Map.of("id", "walkFind0001", "conversationId", sid("conv:soba"))));

        // 統合先の会話が非公開なら案内しない
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("conv:soba")).update();
        assertThat(map(conversation("conv:walk").get("findings"))).containsEntry("moved", List.of());
    }

    // ---- 問いと回答 ----

    @Test
    void 回答のない問いはほかの問いに入り回答が採用されると回答のある問いに入る() {
        var questions = map(conversation("conv:soba").get("questions"));
        assertThat(questions).containsEntry("answered", List.of()).containsEntry("othersTotalCount", 1);
        assertThat(list(questions.get("others"))).singleElement().satisfies(q -> assertThat(q)
            .containsEntry("id", sid("q:soba")).containsEntry("kind", "where").containsEntry("status", "active")
            .containsEntry("askedByPerson", true).containsEntry("keepOpen", true)
            .containsEntry("reactions", Map.of("curious", 1)).doesNotContainKey("answer"));

        adoptSobaQuestionAt("place:yokohama-st", "QUESTION_OWNER");
        var body = conversation("conv:soba");
        var answered = list(map(body.get("questions")).get("answered"));
        assertThat(answered).singleElement().satisfies(q -> assertThat(q).containsEntry("answer", Map.of(
            "kind", "place", "status", "adopted", "adoptedBy", "questionOwner", "place", ref("place:yokohama-st", "横浜駅"))));
        assertThat(map(body.get("questions"))).containsEntry("others", List.of()).containsEntry("othersTotalCount", 0);
        // 問いの回答として採用した場所も、会話の場所の状態に出る
        assertThat(body).containsEntry("places", List.of(Map.of("place", ref("place:yokohama-st", "横浜駅"),
            "status", "adopted", "source", "question")));
    }

    /** そば屋の問いの回答として、場所を採用する。 */
    private void adoptSobaQuestionAt(String placeKey, String actorKind) {
        long decision = operatorDecision("PLACE_ADOPT");
        jdbc.sql("""
                INSERT INTO place_candidate (public_id, open_question_id, place_id, origin)
                SELECT 'candSobaAns1', q.id, p.id, 'HUMAN' FROM open_question q, place p
                 WHERE q.public_id = :q AND p.public_id = :place
                """).param("q", sid("q:soba")).param("place", sid(placeKey)).update();
        jdbc.sql("""
                INSERT INTO place_adoption (candidate_id, open_question_id, place_id, actor_kind, actor_user_id, created_decision_id)
                SELECT pc.id, pc.open_question_id, pc.place_id, :actor,
                       (SELECT user_id FROM user_profile WHERE nickname = 'そば好き'), :decision
                  FROM place_candidate pc WHERE pc.public_id = 'candSobaAns1'
                """).param("actor", actorKind).param("decision", decision).update();
    }

    /** 散歩の会話に「なぜ」の問いを作り、回答を採用する。 */
    private void addWalkQuestionAnswer(String answerColumns, String answerValues) {
        long decision = operatorDecision("ANSWER_ADOPT");
        jdbc.sql("""
                INSERT INTO open_question (public_id, conversation_id, content, kind, analysis_version)
                SELECT 'walkQuestion', id, '汽車道はなぜ線路の跡なのか', 'WHY', 'test' FROM conversation WHERE public_id = :c
                """).param("c", sid("conv:walk")).update();
        jdbc.sql("INSERT INTO question_answer (question_id, " + answerColumns + ", actor_kind, created_decision_id) "
                + "SELECT q.id, " + answerValues + ", 'RULE_OR_OPERATOR', :decision FROM open_question q WHERE q.public_id = 'walkQuestion'")
            .param("decision", decision).update();
    }

    @Test
    void 回答のPostは属する会話が公開なら非公開のPostでも識別子だけを返す() {
        addWalkQuestionAnswer("answer_post_id", "(SELECT id FROM post WHERE public_id = '" + sid("post:walk-4") + "')");
        var answered = list(map(conversation("conv:walk").get("questions")).get("answered"));
        assertThat(answered).singleElement().satisfies(q -> assertThat(q)
            .containsEntry("askedByPerson", false).doesNotContainKey("keepOpen")
            .containsEntry("answer", Map.of("kind", "post", "status", "adopted", "adoptedBy", "ruleOrOperator",
                "post", Map.of("state", "available", "id", sid("post:walk-4"), "conversationId", sid("conv:walk")))));
    }

    @Test
    void 回答のわかってきたことは採用した版と案内する先の両方が公開できるときだけ中身を返す() {
        // そば屋の会話のわかってきたこと（版1で採用、いまは版2）を、散歩の会話の問いの回答にする
        jdbc.sql("""
                WITH f AS (
                    INSERT INTO finding (public_id, conversation_id, kind, analysis_version)
                    SELECT 'sobaFinding1', id, 'THEORY', 'test' FROM conversation WHERE public_id = :c RETURNING id
                )
                INSERT INTO finding_version (finding_id, version, content, analysis_version)
                SELECT id, v, CASE v WHEN 1 THEN '採用した版の内容' ELSE '今の版の内容' END, 'test'
                  FROM f, generate_series(1, 2) AS v
                """).param("c", sid("conv:soba")).update();
        jdbc.sql("UPDATE finding SET current_version = 2 WHERE public_id = 'sobaFinding1'").update();
        addWalkQuestionAnswer("answer_finding_id, answer_finding_version",
            "(SELECT id FROM finding WHERE public_id = 'sobaFinding1'), 1");

        assertThat(walkAnswer()).containsEntry("finding", Map.of("state", "available", "id", "sobaFinding1",
            "conversationId", sid("conv:soba"), "text", "採用した版の内容"));

        // 統合されていれば、最後の統合先へ案内する（内容は採用した版のまま）
        jdbc.sql("UPDATE finding SET status = 'MERGED', merged_into_finding_id = (SELECT id FROM finding WHERE public_id = :id) WHERE public_id = 'sobaFinding1'")
            .param("id", sid("finding:walk-value")).update();
        assertThat(walkAnswer()).containsEntry("finding", Map.of("state", "available", "id", sid("finding:walk-value"),
            "conversationId", sid("conv:walk"), "text", "採用した版の内容"));

        // 採用した版の側（そば屋の会話）が非公開なら、案内する先が公開でも参照できないとだけ返す。採用の状態はそのまま
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("conv:soba")).update();
        assertThat(walkAnswer()).containsEntry("status", "adopted")
            .containsEntry("finding", Map.of("state", "unavailable"));
    }

    @Test
    void 回答のわかってきたことの統合先が非公開なら参照できないとだけ返す() {
        jdbc.sql("""
                WITH f AS (
                    INSERT INTO finding (public_id, conversation_id, kind, analysis_version)
                    SELECT 'sobaFinding1', id, 'THEORY', 'test' FROM conversation WHERE public_id = :c RETURNING id
                )
                INSERT INTO finding_version (finding_id, version, content, analysis_version) SELECT id, 1, 'そば屋の説', 'test' FROM f
                """).param("c", sid("conv:soba")).update();
        addWalkQuestionAnswer("answer_finding_id, answer_finding_version",
            "(SELECT id FROM finding WHERE public_id = :answer), 1".replace(":answer", "'" + sid("finding:walk-value") + "'"));
        jdbc.sql("UPDATE finding SET status = 'MERGED', merged_into_finding_id = (SELECT id FROM finding WHERE public_id = 'sobaFinding1') WHERE public_id = :id")
            .param("id", sid("finding:walk-value")).update();
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("conv:soba")).update();
        assertThat(walkAnswer()).containsEntry("finding", Map.of("state", "unavailable"));
    }

    @Test
    void 同じわかってきたことの違う版を問いごとに採用していればそれぞれ採用した版の内容を返す() {
        jdbc.sql("""
                INSERT INTO finding_version (finding_id, version, content, analysis_version)
                SELECT id, 2, '散歩の価値（版2）', 'test' FROM finding WHERE public_id = :id
                """).param("id", sid("finding:walk-value")).update();
        jdbc.sql("UPDATE finding SET current_version = 2 WHERE public_id = :id").param("id", sid("finding:walk-value")).update();
        long decision = operatorDecision("ANSWER_ADOPT");
        jdbc.sql("""
                INSERT INTO open_question (public_id, conversation_id, content, kind, analysis_version, created_at)
                SELECT 'walkQuestio' || v, c.id, '問い ' || v, 'WHY', 'test', timestamptz '2026-04-08 00:00+09' + make_interval(hours => v)
                  FROM conversation c, generate_series(1, 2) AS v WHERE c.public_id = :c
                """).param("c", sid("conv:walk")).update();
        jdbc.sql("""
                INSERT INTO question_answer (question_id, answer_finding_id, answer_finding_version, actor_kind, created_decision_id)
                SELECT q.id, f.id, CAST(right(q.public_id, 1) AS integer), 'RULE_OR_OPERATOR', :decision
                  FROM open_question q, finding f WHERE q.public_id LIKE 'walkQuestio_' AND f.public_id = :f
                """).param("decision", decision).param("f", sid("finding:walk-value")).update();
        var answered = list(map(conversation("conv:walk").get("questions")).get("answered"));
        assertThat(answered).extracting(q -> (Object) q.get("id")).containsExactly("walkQuestio1", "walkQuestio2");
        assertThat(map(map(answered.get(0).get("answer")).get("finding")))
            .containsEntry("text", "桜木町から汽車道を通って山下公園まで、海を見ながら散歩できる");
        assertThat(map(map(answered.get(1).get("answer")).get("finding"))).containsEntry("text", "散歩の価値（版2）");
    }

    @Test
    void 生まれた発見は場所のあるものを成立の新しい順に10件と全体の数を返す() {
        long decision = operatorDecision("OPERATOR_REGISTER");
        jdbc.sql("""
                WITH d AS (
                    INSERT INTO discovery (public_id, title, established_at)
                    SELECT 'walkDisc' || lpad(i::text, 4, '0'), '散歩の発見 ' || i,
                           timestamptz '2026-05-01 00:00+09' + make_interval(days => i)
                      FROM generate_series(1, 12) AS i
                    RETURNING id, public_id
                ), o AS (
                    INSERT INTO discovery_origin (discovery_id, kind, conversation_id, decision_id)
                    SELECT d.id, 'CONVERSATION', c.id, :decision FROM d, conversation c WHERE c.public_id = :c
                )
                INSERT INTO discovery_place (discovery_id, place_id, role, created_decision_id)
                SELECT d.id, p.id, 'EXPERIENCE', :decision FROM d, place p
                 WHERE p.public_id = :p AND d.public_id <> 'walkDisc0012'
                """).param("decision", decision).param("c", sid("conv:walk")).param("p", sid("place:yamashita")).update();
        var discoveries = map(conversation("conv:walk").get("discoveries"));
        // 関わる場所のない1件（カードにできない）は数えない。散歩の Discovery と11件で12件
        assertThat(discoveries).containsEntry("totalCount", 12);
        assertThat(list(discoveries.get("items"))).extracting(d -> (Object) d.get("id"))
            .containsExactly("walkDisc0011", "walkDisc0010", "walkDisc0009", "walkDisc0008", "walkDisc0007",
                "walkDisc0006", "walkDisc0005", "walkDisc0004", "walkDisc0003", "walkDisc0002");
    }

    private Map<String, Object> walkAnswer() {
        var answered = list(map(conversation("conv:walk").get("questions")).get("answered"));
        return map(answered.get(0).get("answer"));
    }

    @Test
    void 統合されて別の会話へ移った問いを案内する() {
        jdbc.sql("""
                INSERT INTO open_question (public_id, conversation_id, content, kind, status, merged_into_question_id, analysis_version)
                SELECT 'walkQuestion', c.id, 'そば屋はどこ', 'WHERE', 'MERGED', q.id, 'test'
                  FROM conversation c, open_question q WHERE c.public_id = :c AND q.public_id = :q
                """).param("c", sid("conv:walk")).param("q", sid("q:soba")).update();
        assertThat(map(conversation("conv:walk").get("questions")))
            .containsEntry("othersTotalCount", 0)
            .containsEntry("moved", List.of(Map.of("id", "walkQuestion", "conversationId", sid("conv:soba"))));
    }

    // ---- 場所の候補 ----

    @Test
    void 採用中の候補はほかの候補の上限によらず全部返しほかは検討中を先に並べる() {
        // そば屋の会話に、会話の場所の候補を55件足し、1件を採用する（ほかは検討中・不採用）
        jdbc.sql("""
                INSERT INTO place_candidate (public_id, conversation_id, proposed_name, origin, status, created_at)
                SELECT 'sobaCand' || lpad(i::text, 4, '0'), c.id, 'そば屋の候補 ' || i, 'HUMAN',
                       CASE WHEN i <= 5 THEN 'REJECTED' ELSE 'CONSIDERING' END,
                       timestamptz '2026-04-16 00:00+09' + make_interval(mins => i)
                  FROM conversation c, generate_series(1, 55) AS i WHERE c.public_id = :c
                """).param("c", sid("conv:soba")).update();
        long decision = operatorDecision("PLACE_ADOPT");
        jdbc.sql("""
                INSERT INTO place_candidate (public_id, conversation_id, place_id, origin, created_at)
                SELECT 'sobaCandYoko', c.id, p.id, 'HUMAN', timestamptz '2026-04-30 00:00+09'
                  FROM conversation c, place p WHERE c.public_id = :c AND p.public_id = :p
                """).param("c", sid("conv:soba")).param("p", sid("place:yokohama-st")).update();
        jdbc.sql("""
                INSERT INTO place_adoption (candidate_id, conversation_id, place_id, role, actor_kind, actor_user_id, created_decision_id)
                SELECT pc.id, pc.conversation_id, pc.place_id, 'TOPIC', 'CONVERSATION_STARTER',
                       (SELECT user_id FROM user_profile WHERE nickname = 'そば好き'), :decision
                  FROM place_candidate pc WHERE pc.public_id = 'sobaCandYoko'
                """).param("decision", decision).update();

        var candidates = map(conversation("conv:soba").get("placeCandidates"));
        assertThat(list(candidates.get("adopted"))).extracting(c -> (Object) c.get("id")).containsExactly("sobaCandYoko");
        // ほか：問いの候補（soba-west）と会話の候補55件。検討中（採用されたことのないもの）を先に、作成の古い順
        assertThat(candidates).containsEntry("othersTotalCount", 56);
        var others = list(candidates.get("others"));
        assertThat(others).hasSize(50);
        assertThat(others.get(0)).containsEntry("id", sid("cand:soba-west"))
            .containsEntry("target", Map.of("kind", "question", "questionId", sid("q:soba")));
        assertThat(map(others.get(0).get("place"))).containsEntry("kind", "proposal")
            .containsEntry("name", "水車のあるそば屋（横浜駅西口のあたり）").containsEntry("spatialType", "point")
            .containsEntry("kinds", List.of("店"))
            .containsEntry("geometry", Map.of("type", "Point", "coordinates", List.of(139.6195, 35.4662)));
        assertThat(others.get(1)).containsEntry("id", "sobaCand0006");
        assertThat(others.stream().map(c -> c.get("status")).toList()).doesNotContain("rejected");

        // 採用し直されて取り消された候補は、取り消しの理由とともに、ほかの候補の後ろの側に入る
        jdbc.sql("""
                UPDATE place_adoption SET status = 'REVOKED', revoke_reason = 'READOPTED', revoked_at = now(),
                       revoked_decision_id = :decision
                 WHERE candidate_id = (SELECT id FROM place_candidate WHERE public_id = 'sobaCandYoko')
                """).param("decision", decision).update();
        var after = map(conversation("conv:soba").get("placeCandidates"));
        assertThat(after).containsEntry("adopted", List.of()).containsEntry("othersTotalCount", 57);
        var all = list(after.get("others"));
        assertThat(all).hasSize(50).extracting(c -> (Object) c.get("id")).doesNotContain("sobaCandYoko");
    }

    // ---- 届くところと S13 の整合 ----

    @Test
    void 届くところはS13の探している人がいますと同じ場所を返す() {
        // 新しい場所の案（横浜駅西口のあたり）は、位置を含むエリア（西区・横浜市・神奈川県）に、小さい順に届く
        var reach = map(conversation("conv:soba").get("reach"));
        assertThat(reach).containsEntry("totalCount", 3);
        assertThat(list(reach.get("items"))).extracting(r -> (Object) r.get("place"))
            .containsExactly(ref("place:nishi", "横浜市西区"), ref("place:yokohama", "横浜市"), ref("place:kanagawa", "神奈川県"));
        assertThat(list(reach.get("items"))).allSatisfy(r -> assertThat(r).containsEntry("matchedBy", "area"));
        assertConsistentWithS13("conv:soba", List.of("place:nishi", "place:yokohama", "place:kanagawa", "place:yokohama-st"));

        // 会話の場所を採用しても、回答のない場所を探す問いが残っていれば届く
        long decision = operatorDecision("PLACE_ADOPT");
        jdbc.sql("""
                INSERT INTO place_candidate (public_id, conversation_id, place_id, origin)
                SELECT 'sobaCandYoko', c.id, p.id, 'HUMAN' FROM conversation c, place p WHERE c.public_id = :c AND p.public_id = :p
                """).param("c", sid("conv:soba")).param("p", sid("place:yokohama-st")).update();
        assertThat(list(map(conversation("conv:soba").get("reach")).get("items"))).extracting(r -> (Object) r.get("place"))
            .startsWith(ref("place:yokohama-st", "横浜駅"));
        assertConsistentWithS13("conv:soba", List.of("place:nishi", "place:yokohama", "place:kanagawa", "place:yokohama-st"));
        jdbc.sql("""
                INSERT INTO place_adoption (candidate_id, conversation_id, place_id, role, actor_kind, actor_user_id, created_decision_id)
                SELECT pc.id, pc.conversation_id, pc.place_id, 'TOPIC', 'CONVERSATION_STARTER',
                       (SELECT user_id FROM user_profile WHERE nickname = 'そば好き'), :decision
                  FROM place_candidate pc WHERE pc.public_id = 'sobaCandYoko'
                """).param("decision", decision).update();
        assertThat(map(conversation("conv:soba").get("reach"))).containsEntry("totalCount", 3);
        assertConsistentWithS13("conv:soba", List.of("place:nishi", "place:yokohama", "place:kanagawa", "place:yokohama-st"));

        // 問いの回答を採用すると、どこにも届かない
        adoptSobaQuestionAt("place:nishi", "QUESTION_OWNER");
        assertThat(map(conversation("conv:soba").get("reach"))).containsEntry("totalCount", 0);
        assertConsistentWithS13("conv:soba", List.of("place:nishi", "place:yokohama", "place:kanagawa", "place:yokohama-st"));
    }

    /** S14 の届くところにある場所だけが、S13 の探している人がいますにこの会話を出す。 */
    private void assertConsistentWithS13(String conversationKey, List<String> placeKeys) {
        var reached = list(map(conversation(conversationKey).get("reach")).get("items")).stream()
            .map(r -> map(r.get("place")).get("id")).toList();
        for (var placeKey : placeKeys) {
            var seeking = list(map(okJson("/places/" + sid(placeKey)).get("seeking")).get("items"));
            boolean shown = seeking.stream().anyMatch(card -> sid(conversationKey).equals(map(card.get("conversation")).get("id")));
            assertThat(shown).as(placeKey).isEqualTo(reached.contains(sid(placeKey)));
        }
    }

    // ---- この会話から生まれた発見 ----

    @Test
    void 生まれた発見は統合されていれば最後の統合先を1件にまとめて出す() {
        // 散歩の Discovery をバラに統合する。バラの由来にもこの会話を足し、同じものが1件になることを確かめる
        merge("disc:walk", "disc:roses");
        long decision = operatorDecision("OPERATOR_REGISTER");
        jdbc.sql("""
                INSERT INTO discovery_origin (discovery_id, kind, conversation_id, decision_id)
                SELECT d.id, 'CONVERSATION', c.id, :decision FROM discovery d, conversation c WHERE d.public_id = :d AND c.public_id = :c
                """).param("decision", decision).param("d", sid("disc:roses")).param("c", sid("conv:walk")).update();
        var discoveries = map(conversation("conv:walk").get("discoveries"));
        assertThat(discoveries).containsEntry("totalCount", 1);
        assertThat(list(discoveries.get("items"))).extracting(d -> (Object) d.get("id")).containsExactly(sid("disc:roses"));

        // 統合が循環して最後の統合先を決められなければ出さない
        merge("disc:roses", "disc:walk");
        jdbc.sql("DELETE FROM discovery_origin WHERE discovery_id = (SELECT id FROM discovery WHERE public_id = :d)")
            .param("d", sid("disc:roses")).update();
        assertThat(conversation("conv:walk")).containsEntry("discoveries", Map.of("items", List.of(), "totalCount", 0));
    }

    // ---- 読み取りの一貫性 ----

    @Test
    void S14の応答はREPEATABLE_READの読み取り専用トランザクションで作る() throws Exception {
        for (var method : List.of(
                ConversationService.class.getMethod("find", String.class, int.class, int.class),
                ConversationService.class.getMethod("findOlderPosts", String.class, String.class, int.class),
                ConversationService.class.getMethod("findFindings", String.class, String.class, int.class))) {
            var transactional = method.getAnnotation(Transactional.class);
            assertThat(transactional.readOnly()).as(method.getName()).isTrue();
            assertThat(transactional.isolation()).as(method.getName()).isEqualTo(Isolation.REPEATABLE_READ);
        }
    }
}
