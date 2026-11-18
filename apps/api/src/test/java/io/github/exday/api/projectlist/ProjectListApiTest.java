package io.github.exday.api.projectlist;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exday.api.DbApiTestSupport;
import io.github.exday.api.generated.model.ProjectCreatorKind;
import io.github.exday.api.generated.model.ProjectListSort;
import io.github.exday.api.generated.model.ProjectType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S17（GET /projects）のテスト。データはサンプルデータ（db/sample/R__sample_data.sql）。
 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-04-02T15:00:00+09:00")
@SuppressWarnings("unchecked")
class ProjectListApiTest extends DbApiTestSupport {

    private static List<Map<String, Object>> items(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("items");
    }

    private static List<Object> ids(Map<String, Object> body) {
        return items(body).stream().map(item -> item.get("id")).toList();
    }

    @Test
    void 公開の企画を新しい順に企画カードで返す() {
        var body = okJson("/projects");
        assertThat(items(body)).containsExactly(
            Map.of("id", sid("proj:harbor-course"), "title", "港の見える散歩コース",
                "callText", "おすすめの寄り道があれば教えてください", "type", "course", "creatorKind", "user",
                "creatorName", "はまっこ", "itemCount", 2, "triggeredConversationCount", 0),
            Map.of("id", sid("proj:slopes"), "title", "鶴見の急坂番付（推定）", "callText", "みんなの知ってる急坂はどこ？",
                "type", "ranking", "creatorKind", "operator", "creatorName", "ex-day 運営", "itemCount", 4,
                "triggeredConversationCount", 1));
        assertThat(body).doesNotContainKeys("nextCursor", "totalCount");
    }

    @Test
    void 型と作った人で絞り込み0件なら空の配列を返す() {
        assertThat(ids(okJson("/projects?type=ranking"))).containsExactly(sid("proj:slopes"));
        assertThat(ids(okJson("/projects?creator=user"))).containsExactly(sid("proj:harbor-course"));
        assertThat(ids(okJson("/projects?type=course&creator=user&sort=new"))).containsExactly(sid("proj:harbor-course"));
        assertThat(okJson("/projects?type=memory")).isEqualTo(Map.of("items", List.of()));
        assertThat(okJson("/projects?type=ranking&creator=user")).isEqualTo(Map.of("items", List.of()));
    }

    @Test
    void 非公開の企画は出さず項目の数は見えない項目も数え会話の数は公開の会話だけを数える() {
        jdbc.sql("UPDATE discovery SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("disc:walk")).update();
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("conv:slopes-snow")).update();
        var body = okJson("/projects");
        assertThat(items(body).get(0)).containsEntry("itemCount", 2);
        assertThat(items(body).get(1)).containsEntry("triggeredConversationCount", 0);

        jdbc.sql("UPDATE project SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("proj:slopes")).update();
        assertThat(ids(okJson("/projects"))).containsExactly(sid("proj:harbor-course"));
    }

    /** ユーザーの企画（まとめ）を足す。i が大きいほど新しい。 */
    private void addProjects(int count) {
        jdbc.sql("""
                INSERT INTO project (public_id, creator_kind, creator_user_id, type, title, created_at)
                SELECT 'listProj' || lpad(i::text, 4, '0'), 'USER', (SELECT user_id FROM user_profile WHERE nickname = 'はまっこ'),
                       'COLLECTION', 'まとめ ' || i, timestamptz '2026-05-01 00:00+09' + make_interval(hours => i)
                  FROM generate_series(1, :count) AS i
                """).param("count", count).update();
    }

    @Test
    void 続きはカーソルで重複なく読め新しい企画が足されても範囲がずれない() {
        addProjects(25);
        // 同じ時点の2件は識別子の降順
        jdbc.sql("UPDATE project SET created_at = (SELECT created_at FROM project WHERE public_id = 'listProj0010') WHERE public_id = 'listProj0011'")
            .update();
        var first = okJson("/projects");
        assertThat(items(first)).hasSize(20);
        assertThat(ids(first).get(0)).isEqualTo("listProj0025");
        assertThat(ids(first)).containsSequence("listProj0011", "listProj0010");
        assertThat(ids(first).get(19)).isEqualTo("listProj0006");
        assertThat(first).containsEntry("nextCursor", "naalistProj0006");

        // 読んでいる間に新しい企画が足されても、続きの範囲は変わらない（新しいものは先頭の側）
        jdbc.sql("""
                INSERT INTO project (public_id, creator_kind, creator_user_id, type, title, created_at)
                SELECT 'listProjNew1', 'USER', (SELECT user_id FROM user_profile WHERE nickname = 'はまっこ'), 'COLLECTION', '新しい',
                       timestamptz '2026-06-01 00:00+09'
                """).update();
        var next = okJson("/projects?after=naalistProj0006");
        assertThat(ids(next)).containsExactly("listProj0005", "listProj0004", "listProj0003", "listProj0002",
            "listProj0001", sid("proj:harbor-course"), sid("proj:slopes"));
        assertThat(next).doesNotContainKey("nextCursor");

        // 絞り込みのカーソルには、絞り込みが入る。limit で数を変えられる
        var filtered = okJson("/projects?type=collection&creator=user&limit=3");
        assertThat(ids(filtered)).containsExactly("listProjNew1", "listProj0025", "listProj0024");
        assertThat(filtered).containsEntry("nextCursor", "nlulistProj0024");
        assertThat(ids(okJson("/projects?type=collection&creator=user&limit=2&after=nlulistProj0024")))
            .containsExactly("listProj0023", "listProj0022");
    }

    @Test
    void 違う条件のカーソルと形の合わない値は400を返す() {
        addProjects(3);
        for (var path : List.of(
                "/projects?type=ranking&after=naalistProj0002",
                "/projects?after=nralistProj0002",
                "/projects?after=naadoesNotExist",
                "/projects?after=xaalistProj0002",
                "/projects?type=unknown",
                "/projects?creator=admin",
                "/projects?sort=popular",
                "/projects?limit=0",
                "/projects?limit=51")) {
            var response = getJson(path);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(400);
            assertThat(response.getHeaders().getContentType()).as(path).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
    }

    @Test
    void S17の応答はREPEATABLE_READの読み取り専用トランザクションで作る() throws Exception {
        var transactional = ProjectListService.class.getMethod("find", ProjectType.class, ProjectCreatorKind.class,
                ProjectListSort.class, String.class, int.class)
            .getAnnotation(Transactional.class);
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
