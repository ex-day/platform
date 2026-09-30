package io.github.exday.api.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exday.api.generated.model.CurrentSeason;
import java.time.Month;
import org.junit.jupiter.api.Test;

/**
 * {@link Seasons} の単体テスト。境目の月（3・6・9・12月）と、その前の月（2・5・8・11月）
 * を確かめる。Issue の決定：3〜5 月が spring、6〜8 月が summer、9〜11 月が autumn、
 * 12〜2 月が winter。
 */
class SeasonsTest {

    @Test
    void 春は3月から5月() {
        assertThat(Seasons.of(Month.MARCH)).isEqualTo(CurrentSeason.SPRING);
        assertThat(Seasons.of(Month.APRIL)).isEqualTo(CurrentSeason.SPRING);
        assertThat(Seasons.of(Month.MAY)).isEqualTo(CurrentSeason.SPRING);
    }

    @Test
    void 夏は6月から8月() {
        assertThat(Seasons.of(Month.JUNE)).isEqualTo(CurrentSeason.SUMMER);
        assertThat(Seasons.of(Month.JULY)).isEqualTo(CurrentSeason.SUMMER);
        assertThat(Seasons.of(Month.AUGUST)).isEqualTo(CurrentSeason.SUMMER);
    }

    @Test
    void 秋は9月から11月() {
        assertThat(Seasons.of(Month.SEPTEMBER)).isEqualTo(CurrentSeason.AUTUMN);
        assertThat(Seasons.of(Month.OCTOBER)).isEqualTo(CurrentSeason.AUTUMN);
        assertThat(Seasons.of(Month.NOVEMBER)).isEqualTo(CurrentSeason.AUTUMN);
    }

    @Test
    void 冬は12月から2月() {
        assertThat(Seasons.of(Month.DECEMBER)).isEqualTo(CurrentSeason.WINTER);
        assertThat(Seasons.of(Month.JANUARY)).isEqualTo(CurrentSeason.WINTER);
        assertThat(Seasons.of(Month.FEBRUARY)).isEqualTo(CurrentSeason.WINTER);
    }
}
