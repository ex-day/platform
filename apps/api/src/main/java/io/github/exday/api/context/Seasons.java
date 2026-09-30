package io.github.exday.api.context;

import io.github.exday.api.generated.model.CurrentSeason;
import java.time.Month;

/**
 * 「今」の月から季節を決めるためのユーティリティ。
 *
 * <p>Issue の決定：
 * <ul>
 *   <li>3〜5 月：spring</li>
 *   <li>6〜8 月：summer</li>
 *   <li>9〜11 月：autumn</li>
 *   <li>12〜2 月：winter</li>
 * </ul>
 * タイムゾーンは Asia/Tokyo。月の判定は呼び出し側で Asia/Tokyo に変換した日時から取得する。
 */
public final class Seasons {

    private Seasons() {}

    /**
     * 月から季節を返す。
     *
     * @param month 月（{@link Month}。Asia/Tokyo で判定した月）。
     */
    public static CurrentSeason of(Month month) {
        return switch (month) {
            case MARCH, APRIL, MAY -> CurrentSeason.SPRING;
            case JUNE, JULY, AUGUST -> CurrentSeason.SUMMER;
            case SEPTEMBER, OCTOBER, NOVEMBER -> CurrentSeason.AUTUMN;
            case DECEMBER, JANUARY, FEBRUARY -> CurrentSeason.WINTER;
        };
    }
}
