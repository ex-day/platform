package io.github.exday.api.top;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * S01（GET /top）のテスト。データはサンプルデータ（db/sample/R__sample_data.sql）。「今」は春（2026年4月）。
 * 既定の起点は桜木町駅、始める地域は横浜市（application.properties）。
 */
@TestPropertySource(properties = "exday.dev.clock.now=2026-04-11T10:00:00+09:00")
@SuppressWarnings("unchecked")
class TopApiTest extends TopTestSupport {

    @Test
    void 既定の起点で中身のある枠だけを優先の順に返す() {
        var body = okJson("/top");
        assertThat(body.get("season")).isEqualTo("spring");
        // 桜木町駅は中区・西区の面のどちらにも入る（サンプルの面は粗い）。覆う市区町村のうち面の小さい西区が同じ行政の地域
        assertThat(body.get("origin")).isEqualTo(Map.of(
            "place", ref("place:sakuragicho", "桜木町駅"), "source", "default", "outsideStartArea", false,
            "adminArea", ref("place:nishi", "横浜市西区")));
        assertThat(body.get("smallMap")).isEqualTo(Map.of("minutes", 120, "trip", "round_trip"));
        // 近くの場所：起点自身と、起点を含む上位の場所（神奈川県・横浜市・中区・西区・みなとみらい）を除いた6か所
        // 声のある発見：公開の会話を由来に持つ汽車道の散歩だけ（運営の登録だけのものは数えない）
        assertThat(body.get("areaCounts")).isEqualTo(Map.of("placeCount", 6, "voicedDiscoveryCount", 1));
        // 番付は近くに推定の値がないので出さない。始める地域の中なのでお知らせも出さない
        assertThat(kinds(body)).containsExactly(
            "seasonal", "nearby_discoveries", "projects", "examples", "seeking", "awaiting_voices");
    }

    @Test
    void 運営の登録のDiscoveryも条件に合えばいまこの時期に出し見本の表示を付ける() {
        var seasonal = section(okJson("/top"), "seasonal");
        assertThat(seasonal.get("hasMore")).isEqualTo(false);
        assertThat(items(seasonal)).containsExactly(
            Map.of(
                "card", Map.of(
                    "discovery", Map.of("id", sid("disc:ooka-sakura"), "title", "大岡川の桜並木を、川沿いに歩く",
                        "summary", "春、大岡川の両岸に桜が続き、川沿いを歩いて楽しめる。",
                        "displayPlaces", List.of(ref("place:ooka", "大岡川"))),
                    "nearestPlace", ref("place:ooka", "大岡川"), "distanceMeters", 521, "isExample", true),
                "value", Map.of("id", sid("value:ooka-sakura"), "description", "川沿いに続く桜並木を歩ける"),
                "scope", "nearby"),
            Map.of(
                "card", Map.of(
                    "discovery", Map.of("id", sid("disc:roses"), "title", "山下公園のバラを、海と一緒に見る",
                        "summary", "春と秋に、海沿いのバラ園でたくさんの品種が咲く。",
                        "displayPlaces", List.of(ref("place:yamashita", "山下公園"))),
                    "nearestPlace", ref("place:yamashita", "山下公園"), "distanceMeters", 1863, "isExample", true),
                "value", Map.of("id", sid("value:roses"), "description", "海を背景に、たくさんの品種のバラが見られる"),
                "scope", "nearby"));
    }

    @Test
    void 先の枠に出たDiscoveryは重ねず使われなかった運営の登録のものを見本の枠に出す() {
        var body = okJson("/top");
        var nearby = section(body, "nearby_discoveries");
        assertThat(items(nearby)).containsExactly(Map.of(
            "discovery", Map.of("id", sid("disc:walk"), "title", "汽車道を通って、桜木町から山下公園へ海沿いの散歩",
                "summary", "桜木町駅から汽車道を渡り、海を見ながら山下公園まで歩ける。線路の跡を歩けるのも楽しい。",
                "displayPlaces", List.of(ref("place:sakuragicho", "桜木町駅"))),
            "nearestPlace", ref("place:sakuragicho", "桜木町駅"), "distanceMeters", 0, "isExample", false));
        var examples = section(body, "examples");
        assertThat(discoveryIds(examples)).containsExactly(sid("disc:kozukue"));
        assertThat(items(examples).get(0)).containsEntry("isExample", true).containsEntry("distanceMeters", 7579);
        assertThat(examples.get("hasMore")).isEqualTo(false);
    }

    @Test
    void 企画探しています声を待っているを返す() {
        var body = okJson("/top");
        var projects = section(body, "projects");
        assertThat(items(projects).stream().map(item -> item.get("id")).toList())
            .containsExactly(sid("proj:harbor-course"), sid("proj:slopes"));
        assertThat(items(projects).get(1)).containsEntry("itemCount", 4).containsEntry("triggeredConversationCount", 1);

        var seeking = items(section(body, "seeking"));
        assertThat(seeking).hasSize(1);
        assertThat(((Map<String, Object>) seeking.get(0).get("conversation")).get("id")).isEqualTo(sid("conv:soba"));
        assertThat(seeking.get(0).get("questions"))
            .isEqualTo(List.of(Map.of("id", sid("q:soba"), "content", "水車のあるそば屋はどこにあったか")));
        assertThat(seeking.get(0).get("candidates")).isEqualTo(List.of(
            Map.of("id", sid("cand:soba-west"), "kind", "proposal", "name", "水車のあるそば屋（横浜駅西口のあたり）")));
        assertThat(seeking.get(0)).containsEntry("nearestCandidateDistanceMeters", 1983);

        // 会話も Discovery もない近くの場所。起点自身と上位の場所は出さない
        var awaiting = section(body, "awaiting_voices");
        assertThat(placeIds(awaiting))
            .containsExactly(sid("place:ishikawacho"), sid("place:yokohama-st"), sid("place:hikawamaru"));
        assertThat(items(awaiting).get(0)).isEqualTo(Map.of("place", ref("place:ishikawacho", "石川町駅"),
            "kinds", List.of("駅")));
    }

    @Test
    void 地点の起点は3km以内を近くにし遠くの見本を後ろに出す() {
        var body = okJson("/top?from=" + sid("place:kozukue-castle"));
        assertThat(body.get("origin")).isEqualTo(Map.of(
            "place", ref("place:kozukue-castle", "小机城址"), "source", "requested", "outsideStartArea", false,
            "adminArea", ref("place:kohoku", "横浜市港北区")));
        // 小机町・港北区・横浜市・神奈川県は上位の場所なので除く
        assertThat(body.get("areaCounts")).isEqualTo(Map.of("placeCount", 1, "voicedDiscoveryCount", 0));
        // 小机城址の発見はいま・この時期に出たので、近くの発見の枠は中身がなく出さない
        assertThat(kinds(body)).containsExactly("seasonal", "projects", "examples", "seeking", "awaiting_voices");
        var seasonal = items(section(body, "seasonal"));
        assertThat(discoveryIds(section(body, "seasonal"))).containsExactly(sid("disc:kozukue"));
        assertThat(seasonal.get(0)).containsEntry("scope", "nearby");
        assertThat(discoveryIds(section(body, "examples"))).containsExactly(sid("disc:ooka-sakura"), sid("disc:roses"));
        assertThat(placeIds(section(body, "awaiting_voices"))).containsExactly(sid("place:kozukue-st"));
        // 近くに候補がない探していますには、いちばん近い候補までの距離を返さない
        assertThat(items(section(body, "seeking")).get(0)).doesNotContainKey("nearestCandidateDistanceMeters");
    }

    @Test
    void 区域の起点は区域と交わるものを近くにし市区町村ならそれ自身を同じ行政の地域にする() {
        var body = okJson("/top?from=" + sid("place:naka"));
        assertThat(body.get("origin")).isEqualTo(Map.of(
            "place", ref("place:naka", "横浜市中区"), "source", "requested", "outsideStartArea", false,
            "adminArea", ref("place:naka", "横浜市中区")));
        // 大岡川（区の境をまたぐ経路）も交わるので近く。中の場所は距離0（同じ距離は識別子の順）
        var seasonal = items(section(body, "seasonal"));
        assertThat(discoveryIds(section(body, "seasonal"))).containsExactly(sid("disc:ooka-sakura"), sid("disc:roses"));
        assertThat(seasonal).allSatisfy(item ->
            assertThat((Map<String, Object>) item.get("card")).containsEntry("distanceMeters", 0));
        // 起点自身（中区）と上位の場所（横浜市・神奈川県）は近くの場所に出さない。隣の西区は交わるので出る
        var awaiting = placeIds(section(body, "awaiting_voices"));
        assertThat(awaiting).containsExactly(sid("place:ishikawacho"), sid("place:nishi"), sid("place:hikawamaru"),
            sid("place:minatomirai"));
        assertThat(body.get("areaCounts")).isEqualTo(Map.of("placeCount", 8, "voicedDiscoveryCount", 1));
    }

    @Test
    void 経路の起点は経路から3km以内を近くにする() {
        var body = okJson("/top?from=" + sid("place:ooka"));
        // 大岡川を覆う市区町村は横浜市だけ（中区・西区の境をまたぐ）
        assertThat(((Map<String, Object>) body.get("origin")).get("adminArea")).isEqualTo(ref("place:yokohama", "横浜市"));
        // 近くで足りない分を、同じ行政の地域（横浜市）の中から補う（小机城址は 3km の外だが横浜市の中）
        var seasonal = items(section(body, "seasonal"));
        assertThat(discoveryIds(section(body, "seasonal")))
            .containsExactly(sid("disc:ooka-sakura"), sid("disc:roses"), sid("disc:kozukue"));
        assertThat(seasonal.stream().map(item -> item.get("scope")).toList())
            .containsExactly("nearby", "nearby", "admin_area");
        assertThat((Map<String, Object>) seasonal.get(0).get("card")).containsEntry("distanceMeters", 0);
        assertThat(placeIds(section(body, "awaiting_voices"))).doesNotContain(sid("place:ooka"), sid("place:yokohama"),
            sid("place:kanagawa"));
        assertThat(items(section(body, "seeking")).get(0)).containsEntry("nearestCandidateDistanceMeters", 2504);
    }

    @Test
    void 近くになければいまこの時期は全国から2件を出し番付は値の大きい順に出す() {
        var body = okJson("/top?from=" + sid("place:tsurumi-st"));
        var seasonal = section(body, "seasonal");
        assertThat(items(seasonal)).hasSize(2).allSatisfy(item -> assertThat(item).containsEntry("scope", "nationwide"));
        assertThat(seasonal.get("hasMore")).isEqualTo(true);

        var estimates = section(body, "estimates");
        assertThat(estimates.get("hasMore")).isEqualTo(true);
        var groups = (List<Map<String, Object>>) estimates.get("groups");
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).get("metric")).isEqualTo("SLOPE_GRADE");
        var ranked = (List<Map<String, Object>>) groups.get(0).get("items");
        assertThat(ranked.stream().map(item -> item.get("place")).toList()).containsExactly(
            ref("place:slope-a", "鶴見の坂A"), ref("place:slope-d", "鶴見の坂D"), ref("place:slope-b", "鶴見の坂B"));
        assertThat((Map<String, Object>) ranked.get(0).get("estimate")).containsEntry("value", 14.2)
            .containsEntry("unit", "percent").containsEntry("method", "elevation-x-road");
        // 番付に出た場所は、声を待っているに重ねない
        assertThat(placeIds(section(body, "awaiting_voices"))).contains(sid("place:slope-c"))
            .doesNotContain(sid("place:slope-a"), sid("place:slope-d"), sid("place:slope-b"));
    }

    @Test
    void 声のある発見の数は公開の会話Postの由来を持つものだけを数える() {
        // 運営の登録に加えて公開の会話の由来も持つ Discovery は数える
        long decision = operatorDecision("OPERATOR_REGISTER");
        jdbc.sql("""
                INSERT INTO discovery_origin (discovery_id, kind, conversation_id, decision_id)
                SELECT d.id, 'CONVERSATION', c.id, :decision FROM discovery d, conversation c
                 WHERE d.public_id = :discovery AND c.public_id = :conversation
                """)
            .param("decision", decision).param("discovery", sid("disc:ooka-sakura")).param("conversation", sid("conv:walk"))
            .update();
        assertThat(okJson("/top").get("areaCounts")).isEqualTo(Map.of("placeCount", 6, "voicedDiscoveryCount", 2));
        // 由来の会話が非公開なら数えない（Discovery が公開でも）
        jdbc.sql("UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("conv:walk")).update();
        var body = okJson("/top");
        assertThat(body.get("areaCounts")).isEqualTo(Map.of("placeCount", 6, "voicedDiscoveryCount", 0));
        // 数えないだけで、公開の Discovery なので枠には出る
        assertThat(discoveryIds(section(body, "nearby_discoveries"))).containsExactly(sid("disc:walk"));
    }

    @Test
    void 非公開と統合された側のDiscoveryは出さない() {
        jdbc.sql("UPDATE discovery SET visibility = 'HIDDEN' WHERE public_id = :id").param("id", sid("disc:roses")).update();
        merge("disc:kozukue", "disc:ooka-sakura");
        var body = okJson("/top");
        assertThat(discoveryIds(section(body, "seasonal"))).containsExactly(sid("disc:ooka-sakura"));
        assertThat(kinds(body)).doesNotContain("examples");
    }

    @Test
    void 統合された場所を起点にすると統合先をたどる() {
        jdbc.sql("""
                UPDATE place SET status = 'MERGED', merged_into_place_id = (SELECT id FROM place WHERE public_id = :to)
                 WHERE public_id = :from
                """)
            .param("from", sid("place:kozukue-st")).param("to", sid("place:sakuragicho")).update();
        var origin = (Map<String, Object>) okJson("/top?from=" + sid("place:kozukue-st")).get("origin");
        assertThat(origin).containsEntry("place", ref("place:sakuragicho", "桜木町駅")).containsEntry("source", "requested");
    }

    @Test
    void 起点の場所がなければ404で形が合わなければ400() {
        assertThat(getJson("/top?from=AAAAAAAAAAAA").getStatusCode().value()).isEqualTo(404);
        assertThat(getJson("/top?from=abc").getStatusCode().value()).isEqualTo(400);
    }
}
