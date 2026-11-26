package io.github.exday.api.detour;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登録・変更した場所の歩きを作り直す処理（{@link PlaceWalkRefresher}）の設定。計算の決まりの値ではないので、
 * {@link DetourProperties}（カーソルに入れる値）とは分ける。
 *
 * @param enabled 決まった間隔で動かすか（テストでは止め、テストから直接呼ぶ）
 * @param interval 待ち行列を見る間隔（前の回が終わってから）
 * @param batchSize 1回に作り直す場所の数の上限
 * @param maxAttempts 失敗した場所を試す回数の上限。達したら待ち行列に残す（監視で見る）
 */
@ConfigurationProperties(prefix = "exday.detour.walk-refresh")
public record PlaceWalkRefreshProperties(
    boolean enabled,
    Duration interval,
    int batchSize,
    int maxAttempts) {
}
