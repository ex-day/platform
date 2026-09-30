package io.github.exday.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** S03 のみんなの声（GET /discoveries/{discoveryId}/posts）のテスト。 */
class DiscoveryPostsApiTest extends DiscoveryDetailTestSupport {

    private static final String WALK = "pNhOuychaTbx";
    private static final String RINKO = "LCy5noRLHXj6";
    private static final String OOKA = "FMI7GE306Hqw";

    @Test
    void 海沿い散歩の声を古い順に返し派生の目印と続きの注記を付ける() {
        var body = postsBody(WALK);

        var posts = posts(body);
        assertThat(posts).extracting(p -> p.get("id")).containsExactlyElementsOf(
            IntStream.rangeClosed(1, 20).mapToObj(i -> postId("walk" + i)).toList());

        assertThat(body.get("derivationMarkers")).isEqualTo(List.of(Map.of(
            "afterPostId", postId("walk8"),
            "discovery", Map.of("id", RINKO, "title", "山下臨港線の跡をたどる"))));
        assertThat(body).doesNotContainKey("derivedOrigin");

        var continued = posts.stream().filter(p -> p.containsKey("continuedIn")).toList();
        assertThat(continued).extracting(p -> p.get("id")).containsExactly(postId("walk16"), postId("walk19"));
        assertThat(continued.get(0).get("continuedIn"))
            .isEqualTo(Map.of("id", RINKO, "title", "山下臨港線の跡をたどる"));

        var walk1 = posts.get(0);
        assertThat(walk1).containsEntry("authorName", "夕景さんぽ")
            .containsEntry("postedAt", "2026-03-10T17:20:00+09:00")
            .containsEntry("body", "桜木町から山下公園まで散歩しました。海沿いがずっと気持ちよかった #散歩")
            .containsEntry("media", List.of())
            .containsEntry("reactionCount", 2)
            .doesNotContainKey("replyTo");

        // walk5 は walk3・walk4 の両方への返信。投稿日時の古い walk3 を引用し、本文は冒頭の40文字にする。
        assertThat(posts.get(4).get("replyTo")).isEqualTo(Map.of(
            "postId", postId("walk3"), "authorName", "夕景さんぽ",
            "excerpt", "汽車道→赤レンガ→象の鼻の横→線路跡の高い遊歩道→山下公園、の順です。線路跡の遊"));

        assertThat(posts.get(5)).containsEntry("reactionCount", 4);

        @SuppressWarnings("unchecked")
        var walk8Media = (List<Map<String, Object>>) posts.get(7).get("media");
        assertThat(walk8Media).hasSize(1);
        assertThat(walk8Media.get(0)).containsEntry("kind", "image")
            .containsEntry("source", Map.of("status", "self"));
    }

    @Test
    void 派生して生まれたDiscoveryは元の会話を返す() {
        var body = postsBody(RINKO);
        assertThat(body).containsEntry("derivedOrigin",
            Map.of("id", WALK, "title", "桜木町から山下公園への海沿い散歩"));
        assertThat(body).containsEntry("derivationMarkers", List.of());
        assertThat(posts(body)).extracting(p -> p.get("id"))
            .containsExactly(postId("rinko1"), postId("rinko2"), postId("rinko3"));
    }

    @Test
    void 大岡川の声に資料と返信先の引用を付ける() {
        var body = postsBody(OOKA);
        var posts = posts(body);
        assertThat(posts).hasSize(7);
        assertThat(body).containsEntry("derivationMarkers", List.of()).doesNotContainKey("derivedOrigin");

        var ooka4 = posts.get(3);
        assertThat(ooka4).containsEntry("id", postId("ooka4"))
            .containsEntry("authorName", "資料室の人")
            .containsEntry("postedAt", "2026-03-21T15:30:00+09:00")
            .containsEntry("reactionCount", 5)
            .containsEntry("replyTo", Map.of(
                "postId", postId("ooka2"), "authorName", "大岡川の近所",
                "excerpt", "子どものころは、今ほど桜は多くなかった気がします。"));
        assertThat(ooka4.get("media")).isEqualTo(List.of(Map.of(
            "id", sampleId("media:ooka-old"),
            "kind", "image",
            "name", "ookagawa-old.svg",
            "image", Map.of("url", "/images/sample/placeholder-old-photo.svg", "alt", "仮の画像（昔の大岡川の川沿い）"),
            "source", Map.of("status", "unset"))));

        // Discovery に直接提供された資料（post_id なし）は、どの声にも付けない。
        assertThat(posts).filteredOn(p -> !p.get("id").equals(postId("ooka4")))
            .allSatisfy(p -> assertThat(p.get("media")).isEqualTo(List.of()));
    }

    @Test
    void ニックネームを変えると投稿者名と引用の投稿者名も変わる() {
        jdbc.sql("UPDATE user_profile SET nickname = :newName WHERE nickname = :oldName")
            .param("newName", "大岡川のそば").param("oldName", "大岡川の近所").update();

        var posts = posts(postsBody(OOKA));
        assertThat(posts.get(1)).containsEntry("id", postId("ooka2")).containsEntry("authorName", "大岡川のそば");
        assertThat(((Map<?, ?>) posts.get(3).get("replyTo")).get("authorName")).isEqualTo("大岡川のそば");
    }

    @Test
    void 非公開の声は返さず引用もしない() {
        jdbc.sql("UPDATE post SET visibility = 'HIDDEN' WHERE public_id = :id")
            .param("id", postId("ooka2")).update();

        var posts = posts(postsBody(OOKA));
        assertThat(posts).hasSize(6).extracting(p -> p.get("id")).doesNotContain(postId("ooka2"));
        var ooka4 = posts.stream().filter(p -> p.get("id").equals(postId("ooka4"))).findFirst().orElseThrow();
        assertThat(ooka4).doesNotContainKey("replyTo");
    }

    @Test
    void 存在しないDiscoveryは404を返す() {
        var response = getPosts("doesNotExist1");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void 長さ64のIDは入力チェックを通り404を返す() {
        var response = getPosts("a".repeat(64));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void 長さ65以上のIDはproblemJsonの400を返す() {
        for (var id : List.of("a".repeat(65), "a".repeat(70))) {
            var response = getPosts(id);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(response.getBody()).containsEntry("status", 400).containsKeys("title")
                .doesNotContainKeys("trace", "exception", "message");
        }
    }

    @Test
    void 非公開のDiscoveryは404を返す() {
        jdbc.sql("UPDATE discovery SET visibility = 'HIDDEN' WHERE public_id = :id")
            .param("id", OOKA).update();
        var response = getPosts(OOKA);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> posts(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("posts");
    }

    private static String postId(String key) {
        return sampleId("post:" + key);
    }

    /** サンプルデータの pg_temp.sid と同じ規則（md5('ex-day-sample:' || key) の先頭12文字）。 */
    private static String sampleId(String key) {
        try {
            var digest = MessageDigest.getInstance("MD5")
                .digest(("ex-day-sample:" + key).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
