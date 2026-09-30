package io.github.exday.api.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code GET /discovery-sections} の API テスト。
 *
 * <p>Spring Boot を実サーバー（ランダムポート）で起動し、
 * {@link org.springframework.boot.context.properties.ConfigurationProperties} のバインドと
 * {@code server.servlet.context-path=/api/v1}（{@code application.properties}）を含む
 * 実際のルーティングを通した HTTP 応答を確かめる。DB は使わないため {@code DataSourceAutoConfiguration}
 * を除外する。「今」と「いる場所」は {@link TestPropertySource} で固定して、応答の {@code context} を検証する。
 */
@SpringBootTest(
    classes = io.github.exday.api.ApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "exday.dev.clock.now=2026-04-02T15:00:00+09:00",
    "exday.dev.location.name=桜木町駅",
    "exday.dev.location.lat=35.4509",
    "exday.dev.location.lng=139.6309"
})
class DiscoverySectionsApiTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    DiscoveryCandidateQueries queries;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    DiscoveryDetailQueries discoveryDetailQueries;

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
        new ParameterizedTypeReference<>() {};

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @Test
    void セクションの一覧を設定どおりのcontextとともに返す() {
        // TestRestTemplate は server.servlet.context-path=/api/v1 を rootUri に含めるため、
        // 相対パスとして /discovery-sections を渡す。実際のリクエスト先は /api/v1/discovery-sections。
        assertThat(restTemplate.getRootUri()).endsWith("/api/v1");

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            "/discovery-sections", org.springframework.http.HttpMethod.GET, null, JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
            .isNotNull()
            .satisfies(ct -> assertThat(ct.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        // context：設定で固定した「今」と「いる場所」。
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.get("context");
        assertThat(context.get("now")).isEqualTo("2026-04-02T15:00:00+09:00");
        assertThat(context.get("season")).isEqualTo("spring");
        @SuppressWarnings("unchecked")
        Map<String, Object> location = (Map<String, Object>) context.get("location");
        assertThat(location.get("name")).isEqualTo("桜木町駅");
        assertThat(((Number) location.get("lat")).doubleValue()).isEqualTo(35.4509);
        assertThat(((Number) location.get("lng")).doubleValue()).isEqualTo(139.6309);
        assertThat(context.get("clockSource")).isEqualTo("fixed");
        assertThat(context.get("locationSource")).isEqualTo("fixed");

        // sections：nearby → season の順、表示名は「この近く」「今の時期」。
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) body.get("sections");
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).get("key")).isEqualTo("nearby");
        assertThat(sections.get(0).get("title")).isEqualTo("この近く");
        assertThat(sections.get(1).get("key")).isEqualTo("season");
        assertThat(sections.get(1).get("title")).isEqualTo("今の時期");
    }

    @Test
    void contextPath外のパスは404を返す() {
        // 実サーバーのルーティングで /api/v1 の外側にある同名パスにはハンドラが登録されない
        // ことを、rootUri を回避した絶対 URL で確認する（context-path が効いている確認）。
        String absoluteUrl = "http://localhost:" + port + "/discovery-sections";
        ResponseEntity<String> response = restTemplate.getForEntity(absoluteUrl, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
