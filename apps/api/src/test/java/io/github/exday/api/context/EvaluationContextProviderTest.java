package io.github.exday.api.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exday.api.context.ExdayDevProperties.ClockProperties;
import io.github.exday.api.context.ExdayDevProperties.LocationProperties;
import io.github.exday.api.generated.model.CurrentSeason;
import io.github.exday.api.generated.model.EvaluationContext;
import io.github.exday.api.generated.model.EvaluationContext.ClockSourceEnum;
import io.github.exday.api.generated.model.EvaluationContext.LocationSourceEnum;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * {@link EvaluationContextProvider} の単体テスト。
 *
 * <p>「今」と「いる場所」を設定で固定した場合と、していない場合の {@code context} の値を確かめる。
 */
class EvaluationContextProviderTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    @Test
    void 設定で今といる場所を固定した場合はfixedを返す() {
        // 2026-04-02T15:00:00+09:00 を「今」として固定した状態を作る。
        Clock clock = Clock.fixed(Instant.parse("2026-04-02T06:00:00Z"), JST);
        ExdayDevProperties properties = new ExdayDevProperties(
            new ClockProperties("2026-04-02T15:00:00+09:00"),
            new LocationProperties("桜木町駅", 35.4509, 139.6309),
            null);

        EvaluationContext context = new EvaluationContextProvider(clock, properties).current();

        assertThat(context.getNow().toInstant()).isEqualTo(Instant.parse("2026-04-02T06:00:00Z"));
        assertThat(context.getSeason()).isEqualTo(CurrentSeason.SPRING);
        assertThat(context.getLocation().getName()).isEqualTo("桜木町駅");
        assertThat(context.getLocation().getLat()).isEqualTo(35.4509);
        assertThat(context.getLocation().getLng()).isEqualTo(139.6309);
        assertThat(context.getClockSource()).isEqualTo(ClockSourceEnum.FIXED);
        assertThat(context.getLocationSource()).isEqualTo(LocationSourceEnum.FIXED);
    }

    @Test
    void 設定が空なら実時計と既定エリアを返す() {
        // clock は systemDefaultZone 相当のもの、properties は空。
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), JST);
        ExdayDevProperties properties = new ExdayDevProperties(
            new ClockProperties(null),
            new LocationProperties(null, null, null),
            null);

        EvaluationContext context = new EvaluationContextProvider(clock, properties).current();

        assertThat(context.getSeason()).isEqualTo(CurrentSeason.SUMMER);
        assertThat(context.getClockSource()).isEqualTo(ClockSourceEnum.SYSTEM);
        assertThat(context.getLocationSource()).isEqualTo(LocationSourceEnum.DEFAULT);
        // 既定エリアは桜木町駅の付近。
        assertThat(context.getLocation().getName()).isEqualTo("桜木町駅");
        assertThat(context.getLocation().getLat()).isEqualTo(35.4509);
        assertThat(context.getLocation().getLng()).isEqualTo(139.6309);
    }

    @Test
    void 場所だけ固定した場合はlocationのみfixed() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), JST);
        ExdayDevProperties properties = new ExdayDevProperties(
            new ClockProperties(null),
            new LocationProperties("元町・中華街", 35.4408, 139.6503),
            null);

        EvaluationContext context = new EvaluationContextProvider(clock, properties).current();

        assertThat(context.getClockSource()).isEqualTo(ClockSourceEnum.SYSTEM);
        assertThat(context.getLocationSource()).isEqualTo(LocationSourceEnum.FIXED);
        assertThat(context.getLocation().getName()).isEqualTo("元町・中華街");
        assertThat(context.getSeason()).isEqualTo(CurrentSeason.AUTUMN);
    }
}
