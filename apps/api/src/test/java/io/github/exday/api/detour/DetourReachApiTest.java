package io.github.exday.api.detour;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exday.api.DbApiTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 寄り道の到達圏（F06。GET /detour/reach・GET /detour/reach/places）のテスト。
 *
 * <p>データはサンプルデータ（db/sample/R__sample_data.sql）の手で作った版（sample.1）：
 * 根岸線 横浜〜桜木町 3分・桜木町〜石川町 2.7分、東海道線 横浜〜鶴見 10.5分、横浜線 横浜〜小机 13.5分、ブルーライン 横浜〜桜木町 4分、
 * 乗換10分（待ちを含む）、行き・帰りそれぞれの最初の乗車の待ち5分。歩き：桜木町 → 汽車道2分・みなとみらい3分・大岡川5分、横浜 → みなとみらい8分、
 * 石川町 → 大岡川12分・山下公園14分・氷川丸15分、小机 → 小机城址（推定の到達点12分・代表点9分。公式の行き方は到達点まで15分）、
 * 鶴見 → 鶴見の坂A 8分・B 13分・C 12分。
 */
class DetourReachApiTest extends DbApiTestSupport {

    private static final String SAKURAGICHO = sid("place:sakuragicho");
    private static final String YOKOHAMA = sid("place:yokohama-st");
    private static final String TSURUMI = sid("place:tsurumi-st");
    private static final String KOZUKUE = sid("place:kozukue-st");

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> places(Map<String, Object> reach) {
        return (Map<String, Object>) reach.get("places");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> names(List<Map<String, Object>> items) {
        return items.stream().map(i -> ((Map<String, Object>) i.get("place")).get("name")).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> bandMinimums(Map<String, Object> reach) {
        return ((List<Map<String, Object>>) reach.get("bands")).stream().map(b -> b.get("minStayMinutes")).toList();
    }

    @Test
    void 戻るときは過ごせる時間の長い順に寄れる場所を返す() {
        var reach = okJson("/detour/reach?from=" + SAKURAGICHO + "&t=60");
        assertThat(reach).containsEntry("kind", "reachable").containsEntry("directMinutes", 0);
        @SuppressWarnings("unchecked")
        var condition = (Map<String, Object>) reach.get("condition");
        assertThat(condition).containsEntry("minutes", 60).containsEntry("end", Map.of("kind", "return"));
        assertThat(condition.get("from")).isEqualTo(Map.of(
            "place", Map.of("id", SAKURAGICHO, "name", "桜木町駅"),
            "lines", List.of(Map.of("operator", "JR東日本", "name", "根岸線"),
                Map.of("operator", "横浜市交通局", "name", "ブルーライン"))));

        var items = items(places(reach));
        // 汽車道：60 − 2 − 2。山下公園：石川町まで 2.7＋5、歩き14分 → 行き 22・帰り 22。氷川丸は 14分で下限の15分に届かない。
        // 市区町村・駅の場所は出さない
        assertThat(names(items)).containsExactly("汽車道", "みなとみらい", "大岡川", "山下公園");
        assertThat(items.get(0)).containsEntry("stayMinutes", 56).containsEntry("outboundMinutes", 2)
            .containsEntry("returnMinutes", 2).containsEntry("kinds", List.of("遊歩道"))
            .containsEntry("access", Map.of("station", Map.of("id", SAKURAGICHO, "name", "桜木町駅"),
                "mode", "walk", "minutes", 2, "estimated", true));
        assertThat(items.get(3)).containsEntry("stayMinutes", 16).containsEntry("outboundMinutes", 22)
            .containsEntry("returnMinutes", 22);
        @SuppressWarnings("unchecked")
        var access = (Map<String, Object>) items.get(3).get("access");
        assertThat(access).containsEntry("station", Map.of("id", sid("place:ishikawacho"), "name", "石川町駅"));
        assertThat(items.get(0)).containsEntry("position", Map.of("type", "Point", "coordinates", List.of(139.6318, 35.4518)));
        assertThat(places(reach)).doesNotContainKey("nextCursor");
        // 移動を始める駅で過ごせる時間は 60分。60分以上の帯は「より長い」ときだけなので、15・30 の2つ
        assertThat(bandMinimums(reach)).containsExactly(15, 30);
    }

    @Test
    void 続きはカーソルで最後まで読める() {
        var first = places(okJson("/detour/reach?from=" + SAKURAGICHO + "&t=60&limit=2"));
        assertThat(names(items(first))).containsExactly("汽車道", "みなとみらい");
        var cursor = (String) first.get("nextCursor");
        assertThat(cursor).matches("^[0-9A-Za-z_-]{16,256}$");

        var second = okJson("/detour/reach/places?from=" + SAKURAGICHO + "&t=60&after=" + cursor + "&limit=2");
        assertThat(names(items(second))).containsExactly("大岡川", "山下公園");
        assertThat(second).doesNotContainKey("nextCursor").doesNotContainKey("bands");
    }

    @Test
    void 条件が違う_読めない_版や設定が変わったカーソルは400() {
        var cursor = (String) places(okJson("/detour/reach?from=" + SAKURAGICHO + "&t=60&limit=1")).get("nextCursor");
        assertThat(getJson("/detour/reach/places?from=" + SAKURAGICHO + "&t=75&after=" + cursor).getStatusCode().value())
            .isEqualTo(400);
        assertThat(getJson("/detour/reach/places?from=" + SAKURAGICHO + "&t=60&to=none&after=" + cursor)
            .getStatusCode().value()).isEqualTo(400);
        assertThat(getJson("/detour/reach/places?from=" + YOKOHAMA + "&t=60&after=" + cursor).getStatusCode().value())
            .isEqualTo(400);
        assertThat(getJson("/detour/reach/places?from=" + SAKURAGICHO + "&t=60&after=AAAAAAAAAAAAAAAAAAAA")
            .getStatusCode().value()).isEqualTo(400);

        // 到達圏のデータの版が切り替わったら、前の版のカーソルは使えない（画面は最初から読み直す）
        jdbc.sql("UPDATE reach.build SET version = 'sample.2'").update();
        assertThat(getJson("/detour/reach/places?from=" + SAKURAGICHO + "&t=60&after=" + cursor).getStatusCode().value())
            .isEqualTo(400);
    }

    @Test
    void 寄り道先で終わるときは帰りを0にする() {
        var reach = okJson("/detour/reach?from=" + SAKURAGICHO + "&t=30&to=none");
        assertThat(reach).containsEntry("kind", "reachable").containsEntry("directMinutes", 0);
        @SuppressWarnings("unchecked")
        var condition = (Map<String, Object>) reach.get("condition");
        assertThat(condition).containsEntry("end", Map.of("kind", "stop"));
        var items = items(places(reach));
        // 汽車道：30 − 2。みなとみらい：30 − 3。大岡川：30 − 5。山下公園：30 − 22 は 8分で出ない
        assertThat(names(items)).containsExactly("汽車道", "みなとみらい", "大岡川");
        assertThat(items.get(0)).containsEntry("stayMinutes", 28).containsEntry("returnMinutes", 0);
        assertThat(bandMinimums(reach)).containsExactly(15);
    }

    @Test
    void 別の駅へ向かうときは到着の駅までの帰りを引く() {
        var reach = okJson("/detour/reach?from=" + SAKURAGICHO + "&t=60&to=" + TSURUMI);
        // 桜木町 → 鶴見：3（ブルーライン）＋10（乗換）＋10.5（東海道線）＋5（待ち）＝ 28.5 → 29分
        assertThat(reach).containsEntry("kind", "reachable").containsEntry("directMinutes", 29);
        @SuppressWarnings("unchecked")
        var end = (Map<String, Object>) ((Map<String, Object>) reach.get("condition")).get("end");
        assertThat(end).containsEntry("kind", "station");
        var items = items(places(reach));
        // 汽車道：行き 2、帰り 2＋28.5 → 31、過ごせる 27。鶴見の坂A：行き 28.5＋8 → 37、帰り 8、過ごせる 15（到着の駅のまわり）
        assertThat(names(items)).containsExactly("汽車道", "みなとみらい", "大岡川", "鶴見の坂A");
        assertThat(items.get(0)).containsEntry("stayMinutes", 27).containsEntry("returnMinutes", 31);
        assertThat(items.get(3)).containsEntry("stayMinutes", 15).containsEntry("outboundMinutes", 37)
            .containsEntry("returnMinutes", 8);
    }

    @Test
    void 使える時間が寄り道をしないで着く時間より短ければ時間が足りない() {
        // 桜木町 → 小机：3＋10＋13.5＋5 ＝ 31.5 → 32分
        var reach = okJson("/detour/reach?from=" + SAKURAGICHO + "&t=30&to=" + KOZUKUE);
        assertThat(reach).containsEntry("kind", "not_enough_time").containsEntry("directMinutes", 32)
            .doesNotContainKey("places").doesNotContainKey("bands");
    }

    @Test
    void 徒歩の行き方があれば同じ到達点までの自動の目安と比べて長いほうを使う() {
        var items = items(places(okJson("/detour/reach?from=" + YOKOHAMA + "&t=90")));
        var castle = items.stream()
            .filter(i -> names(List.of(i)).getFirst().equals("小机城址")).findFirst().orElseThrow();
        // 公式の行き方15分（到達点まで）と、同じ到達点までの自動の目安12分 → 15分（行き方の分数なので目安ではない）。
        // 小机まで 13.5＋5 ＝ 18.5、行き 34・帰り 34
        assertThat(castle).containsEntry("stayMinutes", 22).containsEntry("outboundMinutes", 34)
            .containsEntry("access", Map.of("station", Map.of("id", KOZUKUE, "name", "小机駅"),
                "mode", "walk", "minutes", 15, "estimated", false));

        // 自動の目安のほうが長ければ、自動の目安を使い、目安と出す
        jdbc.sql("UPDATE place_access SET minutes = 10").update();
        castle = items(places(okJson("/detour/reach?from=" + YOKOHAMA + "&t=90"))).stream()
            .filter(i -> names(List.of(i)).getFirst().equals("小机城址")).findFirst().orElseThrow();
        assertThat(castle).containsEntry("access", Map.of("station", Map.of("id", KOZUKUE, "name", "小机駅"),
            "mode", "walk", "minutes", 12, "estimated", true));
    }

    @Test
    void 行き方と同じ到達点までの自動の目安の長いほうが上限を超えれば外す() {
        // 公式の行き方10分、同じ到達点までの OSM の道のり18分 → 長いほうの18分が上限の15分を超えるので出さない
        jdbc.sql("UPDATE place_access SET minutes = 10").update();
        jdbc.sql("UPDATE reach.place_walk SET walk_min = 18 WHERE arrival_point_id IS NOT NULL").update();
        assertThat(names(items(places(okJson("/detour/reach?from=" + YOKOHAMA + "&t=90"))))).doesNotContain("小机城址");

        // 同じ到達点までの道のりを計算できない（行が無い）ときだけ、行き方の分数を使う（目安ではない）
        jdbc.sql("DELETE FROM reach.place_walk WHERE arrival_point_id IS NOT NULL").update();
        var castle = items(places(okJson("/detour/reach?from=" + YOKOHAMA + "&t=90"))).stream()
            .filter(i -> names(List.of(i)).getFirst().equals("小机城址")).findFirst().orElseThrow();
        assertThat(castle).containsEntry("access", Map.of("station", Map.of("id", KOZUKUE, "name", "小机駅"),
            "mode", "walk", "minutes", 10, "estimated", false));
    }

    @Test
    void 版を作り始めたあとに登録した行き方は次の版まで使わない() {
        // 桜木町駅 → 山下公園の徒歩10分の行き方を、版のあとに足す。比べる道のりが版に無いので、次の版まで使わない
        jdbc.sql("""
                INSERT INTO place_access (place_id, station_place_id, mode, minutes, source, verification, created_decision_id)
                SELECT (SELECT id FROM place WHERE public_id = :place), (SELECT id FROM place WHERE public_id = :station),
                       'WALK', 10, 'USER', 'UNVERIFIED', (SELECT created_decision_id FROM place_access LIMIT 1)
                """)
            .param("place", sid("place:yamashita")).param("station", SAKURAGICHO).update();
        jdbc.sql("UPDATE place_access SET created_at = (SELECT created_at FROM reach.build) + interval '1 minute'"
            + " WHERE place_id = (SELECT id FROM place WHERE public_id = :place)")
            .param("place", sid("place:yamashita")).update();
        var yamashita = items(places(okJson("/detour/reach?from=" + SAKURAGICHO + "&t=60"))).stream()
            .filter(i -> names(List.of(i)).getFirst().equals("山下公園")).findFirst().orElseThrow();
        // 山下公園は今までどおり石川町駅からの自動の目安（16分）。桜木町駅からの10分（過ごせる 40分）にはならない
        assertThat(yamashita).containsEntry("stayMinutes", 16);
        @SuppressWarnings("unchecked")
        var access = (Map<String, Object>) yamashita.get("access");
        assertThat(access).containsEntry("station", Map.of("id", sid("place:ishikawacho"), "name", "石川町駅"));

        // 次の版（版を作り始めたのが行き方より後）では使う。ここでは版の作り始めの時点を動かして確かめる
        jdbc.sql("UPDATE reach.build SET created_at = now() + interval '1 hour'").update();
        yamashita = items(places(okJson("/detour/reach?from=" + SAKURAGICHO + "&t=60"))).stream()
            .filter(i -> names(List.of(i)).getFirst().equals("山下公園")).findFirst().orElseThrow();
        assertThat(yamashita).containsEntry("stayMinutes", 40);
    }

    @Test
    void 版を作ったあとに廃止した駅は降りる駅にしない() {
        // 山下公園はサンプルでは石川町駅からだけ歩ける。石川町駅を廃止すると、グラフは通り抜けるが降りる駅にはしない
        jdbc.sql("UPDATE place SET status = 'ABOLISHED' WHERE public_id = :id").param("id", sid("place:ishikawacho")).update();
        var reach = okJson("/detour/reach?from=" + SAKURAGICHO + "&t=60");
        var items = items(places(reach));
        assertThat(names(items)).containsExactly("汽車道", "みなとみらい", "大岡川");
        assertThat(items).allSatisfy(i -> assertThat(i.get("access").toString()).doesNotContain(sid("place:ishikawacho")));
        // 地図の帯は残る（桜木町駅のまわり）
        assertThat(bandMinimums(reach)).containsExactly(15, 30);
    }

    @Test
    void ACTIVEでない場所は出さない() {
        jdbc.sql("UPDATE place SET status = 'ABOLISHED' WHERE public_id = :id").param("id", sid("place:kishamichi")).update();
        assertThat(names(items(places(okJson("/detour/reach?from=" + SAKURAGICHO + "&t=60")))))
            .containsExactly("みなとみらい", "大岡川", "山下公園");
    }

    @Test
    void 駅でない_ない場所_時間の形の誤りは400か404() {
        assertThat(getJson("/detour/reach?from=" + sid("place:yamashita") + "&t=60").getStatusCode().value())
            .isEqualTo(400);
        assertThat(getJson("/detour/reach?from=000000000000&t=60").getStatusCode().value()).isEqualTo(404);
        assertThat(getJson("/detour/reach?from=" + SAKURAGICHO + "&t=60&to=" + sid("place:yamashita"))
            .getStatusCode().value()).isEqualTo(400);
        assertThat(getJson("/detour/reach?from=" + SAKURAGICHO + "&t=50").getStatusCode().value()).isEqualTo(400);
        assertThat(getJson("/detour/reach?from=" + SAKURAGICHO + "&t=15").getStatusCode().value()).isEqualTo(400);
        assertThat(getJson("/detour/reach?from=" + SAKURAGICHO + "&t=60&limit=51").getStatusCode().value())
            .isEqualTo(400);
        assertThat(getJson("/detour/reach?from=" + SAKURAGICHO).getStatusCode().value()).isEqualTo(400);
    }
}
