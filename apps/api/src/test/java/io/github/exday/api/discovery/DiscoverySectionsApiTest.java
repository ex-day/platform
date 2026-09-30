package io.github.exday.api.discovery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.exday.api.context.ClockConfiguration;
import io.github.exday.api.context.EvaluationContextProvider;
import io.github.exday.api.context.ExdayDevProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code GET /discovery-sections} の API テスト。
 *
 * <p>{@link MockMvcBuilders#standaloneSetup} でコントローラーだけを立ち上げ、Spring Boot の
 * 全部を立ち上げずに JSON のレスポンスを確かめる。DB や AutoConfiguration を巻き込まないため、
 * 時計と設定はテスト側で組み立てる。
 */
class DiscoverySectionsApiTest {

    private MockMvc mockMvc() {
        ExdayDevProperties properties = new ExdayDevProperties(
            new ExdayDevProperties.ClockProperties("2026-04-02T15:00:00+09:00"),
            new ExdayDevProperties.LocationProperties("桜木町駅", 35.4509, 139.6309),
            null);
        // ClockConfiguration は @Configuration だが、単体テストでは直接 new して Bean の
        // 組み立てだけ再現する（実際の Bean と同じ設定を通す）。
        Clock configured = new ClockConfiguration().clock(properties);
        EvaluationContextProvider provider = new EvaluationContextProvider(configured, properties);
        DiscoverySectionsController controller = new DiscoverySectionsController(provider);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void セクションの一覧を設定どおりのcontextとともに返す() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(get("/discovery-sections"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            // context：設定で固定した「今」と「いる場所」。
            .andExpect(jsonPath("$.context.now").value("2026-04-02T15:00:00+09:00"))
            .andExpect(jsonPath("$.context.season").value("spring"))
            .andExpect(jsonPath("$.context.location.name").value("桜木町駅"))
            .andExpect(jsonPath("$.context.location.lat").value(35.4509))
            .andExpect(jsonPath("$.context.location.lng").value(139.6309))
            .andExpect(jsonPath("$.context.clockSource").value("fixed"))
            .andExpect(jsonPath("$.context.locationSource").value("fixed"))
            // sections：nearby → season の順、表示名は「この近く」「今の時期」。
            .andExpect(jsonPath("$.sections.length()").value(2))
            .andExpect(jsonPath("$.sections[0].key").value("nearby"))
            .andExpect(jsonPath("$.sections[0].title").value("この近く"))
            .andExpect(jsonPath("$.sections[1].key").value("season"))
            .andExpect(jsonPath("$.sections[1].title").value("今の時期"));
    }
}
