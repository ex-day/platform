package io.github.exday.api.context;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 開発用の「今」と「いる場所」の設定。
 *
 * <p>本番では設定を空にして、実際の日時と既定エリア（桜木町駅の付近）を使う。ローカルで
 * 「4月2日の15時に桜木町にいる」ことにして画面を確かめたい場合など、開発中に固定したい
 * ときだけ設定する。使ってみる人の現在地は API で受け取らない（Issue #89 の決定）。
 *
 * <p>OpenAPI の {@code EvaluationContext.clockSource} / {@code locationSource} は、
 * 実際に固定を使ったかどうかで {@code fixed} / {@code system}・{@code default} を切り替える。
 *
 * @param clock 「今」の設定。{@code now} が空なら実時計を使う（{@code clockSource=system}）。
 * @param location 「いる場所」の設定。{@code name}・{@code lat}・{@code lng} のいずれかが
 *                 空なら既定エリアを使う（{@code locationSource=default}）。
 * @param defaultLocation 既定エリア。設定しない場合は桜木町駅の付近を使う。
 */
@ConfigurationProperties(prefix = "exday.dev")
public record ExdayDevProperties(
    ClockProperties clock,
    LocationProperties location,
    LocationProperties defaultLocation) {

    /**
     * 桜木町駅の付近。設定で {@code exday.dev.default-location} を指定しなかった場合の既定値。
     */
    public static final LocationProperties SAKURAGICHO =
        new LocationProperties("桜木町駅", 35.4509, 139.6309);

    public ExdayDevProperties {
        if (defaultLocation == null || !defaultLocation.isComplete()) {
            defaultLocation = SAKURAGICHO;
        }
    }

    /**
     * 「今」の設定。
     *
     * @param now ISO 8601 の日時。空なら実時計を使う。例：{@code 2026-04-02T15:00:00+09:00}。
     */
    public record ClockProperties(String now) {
        public boolean isFixed() {
            return now != null && !now.isBlank();
        }
    }

    /**
     * 「いる場所」の設定。
     *
     * @param name 表示用の名前。
     * @param lat 緯度。
     * @param lng 経度。
     */
    public record LocationProperties(String name, Double lat, Double lng) {
        public boolean isComplete() {
            return name != null && !name.isBlank() && lat != null && lng != null;
        }
    }
}
