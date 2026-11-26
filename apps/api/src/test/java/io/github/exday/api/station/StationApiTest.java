package io.github.exday.api.station;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.exday.api.DbApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 駅の選び方（F06。GET /stations・GET /places/{placeId}/nearby-stations・POST /stations/nearby）のテスト。
 * データはサンプルデータ（db/sample/R__sample_data.sql）。駅の場所は桜木町・横浜・鶴見・小机・石川町。
 */
class StationApiTest extends DbApiTestSupport {

    private static Map<String, Object> ref(String key, String name) {
        return Map.of("id", sid(key), "name", name);
    }

    private static Map<String, Object> line(String operator, String name) {
        return Map.of("operator", operator, "name", name);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("items");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> placesOf(List<Map<String, Object>> nearby) {
        return nearby.stream().map(n -> ((Map<String, Object>) n.get("station")).get("place")).toList();
    }

    /** 探す文字はテンプレートの値で渡す（日本語・空白・記号を符号化させる）。 */
    private ResponseEntity<Map<String, Object>> search(String q) {
        return rest.exchange("/stations?q={q}", HttpMethod.GET, null,
            new ParameterizedTypeReference<Map<String, Object>>() {}, Map.of("q", q));
    }

    private ResponseEntity<Map<String, Object>> search(String q, int limit) {
        return rest.exchange("/stations?q={q}&limit={limit}", HttpMethod.GET, null,
            new ParameterizedTypeReference<Map<String, Object>>() {}, Map.of("q", q, "limit", limit));
    }

    private Map<String, Object> searchOk(String q) {
        var response = search(q);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    private Map<String, Object> searchOk(String q, int limit) {
        var response = search(q, limit);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> postJson(String path, String json) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers),
            new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @Test
    void 名前が同じ駅を路線つきで返す() {
        var body = searchOk("横浜");
        // 横浜市・横浜市中区等は駅でないので出ない。路線は事業者・路線の名前の順（文字コード）
        assertThat(items(body)).containsExactly(Map.of(
            "place", ref("place:yokohama-st", "横浜駅"),
            "lines", List.of(
                line("JR東日本", "東海道線"), line("JR東日本", "根岸線"), line("JR東日本", "横浜線"),
                line("JR東日本", "横須賀線"),
                line("京急", "本線"), line("東急", "東横線"), line("横浜市営地下鉄", "ブルーライン"),
                line("横浜高速鉄道", "みなとみらい線"), line("相鉄", "相鉄本線"))));
    }

    @Test
    void 駅を付けても付けなくても同じ駅を返す() {
        var withSuffix = searchOk("桜木町駅");
        var withoutSuffix = searchOk("桜木町");
        assertThat(withSuffix).isEqualTo(withoutSuffix);
        assertThat(items(withSuffix)).containsExactly(Map.of(
            "place", ref("place:sakuragicho", "桜木町駅"),
            "lines", List.of(line("JR東日本", "根岸線"), line("横浜市営地下鉄", "ブルーライン"))));
    }

    @Test
    void 同じ名前_で始まる_含むの順に並べ件数で切る() {
        // 「町」は桜木町・石川町に含まれる（同じ順位。名前の長さが同じなので文字コードの順）。小机町は駅でないので出ない
        assertThat(items(searchOk("町"))).extracting(s -> s.get("place"))
            .containsExactly(ref("place:sakuragicho", "桜木町駅"), ref("place:ishikawacho", "石川町駅"));
        assertThat(items(searchOk("町", 1))).extracting(s -> s.get("place"))
            .containsExactly(ref("place:sakuragicho", "桜木町駅"));
        // 「小机」：小机駅は「駅」を除くと同じ名前（小机城址・小机町は駅でない）
        assertThat(items(searchOk("小机"))).extracting(s -> s.get("place"))
            .containsExactly(ref("place:kozukue-st", "小机駅"));
    }

    @Test
    void LIKEの記号は文字として扱い空の文字は400() {
        assertThat(items(searchOk("%"))).isEmpty();
        assertThat(items(searchOk("_"))).isEmpty();
        assertThat(search("  ").getStatusCode().value()).isEqualTo(400);
        assertThat(getJson("/stations").getStatusCode().value()).isEqualTo(400);
        assertThat(search("横浜", 21).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void 使っている版に入っていない駅は候補に出さない() {
        long id = jdbc.sql("""
                INSERT INTO place (public_id, name, spatial_type, geom, origin)
                VALUES ('tststation02', '桜木町東駅', 'POINT', ST_SetSRID(ST_MakePoint(139.632, 35.451), 4326)::geography, 'OPERATOR')
                RETURNING id
                """).query(Long.class).single();
        jdbc.sql("INSERT INTO place_kind (place_id, kind) VALUES (:id, '駅')").param("id", id).update();

        assertThat(items(searchOk("桜木町"))).extracting(s -> s.get("place"))
            .containsExactly(ref("place:sakuragicho", "桜木町駅"));
        assertThat(placesOf(items(okJson("/places/" + sid("place:kishamichi") + "/nearby-stations"))))
            .doesNotContain(Map.of("id", "tststation02", "name", "桜木町東駅"));

        // 使っている版がなければ、名前での検索・場所のまわり・現在地のまわりのどれも候補は空
        jdbc.sql("UPDATE reach.build SET status = 'RETIRED' WHERE status = 'ACTIVE'").update();
        assertThat(items(searchOk("桜木町"))).isEmpty();
        assertThat(items(okJson("/places/" + sid("place:kishamichi") + "/nearby-stations"))).isEmpty();
        var nearby = postJson("/stations/nearby", """
                {"latitude": 35.4509, "longitude": 139.6308}
                """);
        assertThat(nearby.getStatusCode().value()).isEqualTo(200);
        assertThat(items(nearby.getBody())).isEmpty();
    }

    @Test
    void 廃止した駅は出さない() {
        jdbc.sql("UPDATE place SET status = 'ABOLISHED' WHERE public_id = :id").param("id", sid("place:ishikawacho")).update();
        assertThat(items(searchOk("町"))).extracting(s -> s.get("place"))
            .containsExactly(ref("place:sakuragicho", "桜木町駅"));
        assertThat(placesOf(items(okJson("/places/" + sid("place:yamashita") + "/nearby-stations"))))
            .doesNotContain(ref("place:ishikawacho", "石川町駅"));
    }

    @Test
    void 場所のまわりの駅を距離の順に返す() {
        var nearby = items(okJson("/places/" + sid("place:yamashita") + "/nearby-stations"));
        // 5km 以内（鶴見・小机は外）。距離は直線（メートル、四捨五入）
        assertThat(placesOf(nearby)).containsExactly(
            ref("place:ishikawacho", "石川町駅"), ref("place:sakuragicho", "桜木町駅"), ref("place:yokohama-st", "横浜駅"));
        assertThat(((Number) nearby.get(0).get("distanceMeters")).doubleValue()).isCloseTo(1084, within(3.0));
        assertThat(((Number) nearby.get(2).get("distanceMeters")).doubleValue()).isCloseTo(3373, within(3.0));
        @SuppressWarnings("unchecked")
        var station = (Map<String, Object>) nearby.get(0).get("station");
        assertThat(station).containsEntry("lines", List.of(line("JR東日本", "根岸線")));

        assertThat(placesOf(items(okJson("/places/" + sid("place:yamashita") + "/nearby-stations?limit=1"))))
            .containsExactly(ref("place:ishikawacho", "石川町駅"));
    }

    @Test
    void 場所が駅ならその駅を距離0で先頭に返す() {
        var nearby = items(okJson("/places/" + sid("place:sakuragicho") + "/nearby-stations"));
        assertThat(placesOf(nearby)).containsExactly(
            ref("place:sakuragicho", "桜木町駅"), ref("place:ishikawacho", "石川町駅"), ref("place:yokohama-st", "横浜駅"));
        assertThat(nearby.get(0)).containsEntry("distanceMeters", 0);
    }

    @Test
    void 統合された場所は統合先のまわりの駅を返し_ない場所は404() {
        jdbc.sql("""
                UPDATE place SET status = 'MERGED', merged_into_place_id = (SELECT id FROM place WHERE public_id = :to)
                 WHERE public_id = :from
                """)
            .param("from", sid("place:kozukue-castle")).param("to", sid("place:yamashita")).update();
        assertThat(okJson("/places/" + sid("place:kozukue-castle") + "/nearby-stations"))
            .isEqualTo(okJson("/places/" + sid("place:yamashita") + "/nearby-stations"));

        assertThat(getJson("/places/000000000000/nearby-stations").getStatusCode().value()).isEqualTo(404);
        assertThat(getJson("/places/" + sid("place:yamashita") + "/nearby-stations?limit=11").getStatusCode().value())
            .isEqualTo(400);
    }

    @Test
    void 現在地のまわりの駅を本文の座標で返す() {
        var response = postJson("/stations/nearby", """
            {"latitude": 35.47, "longitude": 139.62}
            """);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var nearby = items(response.getBody());
        assertThat(placesOf(nearby)).containsExactly(
            ref("place:yokohama-st", "横浜駅"), ref("place:sakuragicho", "桜木町駅"),
            ref("place:ishikawacho", "石川町駅"), ref("place:kozukue-st", "小机駅"));
        assertThat(((Number) nearby.get(0).get("distanceMeters")).doubleValue()).isCloseTo(503, within(3.0));

        var limited = postJson("/stations/nearby", """
            {"latitude": 35.47, "longitude": 139.62, "limit": 2}
            """);
        assertThat(placesOf(items(limited.getBody())))
            .containsExactly(ref("place:yokohama-st", "横浜駅"), ref("place:sakuragicho", "桜木町駅"));
    }

    @Test
    void 現在地のまわりに駅がなければ空_座標が範囲の外なら400() {
        var tokyoBay = postJson("/stations/nearby", """
            {"latitude": 35.30, "longitude": 139.90}
            """);
        assertThat(tokyoBay.getStatusCode().value()).isEqualTo(200);
        assertThat(items(tokyoBay.getBody())).isEmpty();

        assertThat(postJson("/stations/nearby", """
            {"latitude": 91, "longitude": 139.62}
            """).getStatusCode().value()).isEqualTo(400);
        assertThat(postJson("/stations/nearby", """
            {"longitude": 139.62}
            """).getStatusCode().value()).isEqualTo(400);
        assertThat(postJson("/stations/nearby", """
            {"latitude": 35.47, "longitude": 139.62, "limit": 11}
            """).getStatusCode().value()).isEqualTo(400);
    }
}
