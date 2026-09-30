package io.github.exday.api.context;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 「今」を扱う {@link Clock} の Bean 設定。
 *
 * <p>{@code exday.dev.clock.now} が設定されていれば固定した日時を返す時計、なければシステム時計
 * （タイムゾーンは Asia/Tokyo）を返す。テストでは {@code exday.dev.clock.now} を渡すことで
 * 「今」を固定できる。
 */
@Configuration
public class ClockConfiguration {

    /** ex-day の日時のタイムゾーン（Issue の決定：Asia/Tokyo）。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    @Bean
    public Clock clock(ExdayDevProperties properties) {
        if (properties.clock() != null && properties.clock().isFixed()) {
            OffsetDateTime fixed = OffsetDateTime.parse(properties.clock().now());
            return Clock.fixed(fixed.toInstant(), ZONE);
        }
        return Clock.system(ZONE);
    }
}
