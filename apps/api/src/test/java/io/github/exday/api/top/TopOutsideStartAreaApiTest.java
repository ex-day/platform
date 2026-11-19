package io.github.exday.api.top;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * S01（GET /top）の始める地域の外のお知らせのテスト。始める地域を横浜市中区（サンプルの place:naka）にして確かめる。
 */
@TestPropertySource(properties = {
    "exday.dev.clock.now=2026-04-11T10:00:00+09:00",
    "exday.top.start-area-place-id=69c0f38b475b"})
@SuppressWarnings("unchecked")
class TopOutsideStartAreaApiTest extends TopTestSupport {

    @Test
    void 始める地域の外ならお知らせを先頭に出しいちばん近い発見を重ねずに出す() {
        assertThat(sid("place:naka")).isEqualTo("69c0f38b475b");
        var body = okJson("/top?from=" + sid("place:kozukue-castle"));
        assertThat((Map<String, Object>) body.get("origin")).containsEntry("outsideStartArea", true);
        assertThat(kinds(body).get(0)).isEqualTo("outside_start_area");
        var notice = (Map<String, Object>) section(body, "outside_start_area").get("nearestDiscovery");
        assertThat(discoveryId(notice)).isEqualTo(sid("disc:kozukue"));
        assertThat(notice).containsEntry("distanceMeters", 0).containsEntry("isExample", true);
        // お知らせに出した発見は、いま・この時期に重ねない（近く・港北区になく、全国から出す）
        var seasonal = section(body, "seasonal");
        assertThat(discoveryIds(seasonal)).doesNotContain(sid("disc:kozukue"));
        assertThat(items(seasonal)).allSatisfy(item -> assertThat(item).containsEntry("scope", "nationwide"));
    }

    @Test
    void 始める地域の中ならお知らせを出さない() {
        // 桜木町駅は中区の面に入る
        var body = okJson("/top");
        assertThat((Map<String, Object>) body.get("origin")).containsEntry("outsideStartArea", false);
        assertThat(kinds(body)).doesNotContain("outside_start_area");
    }
}
