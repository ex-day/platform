package io.github.exday.api.detour;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 寄り道の到達圏（F06）の計算の決まりの値（F06「設定で持つ値」）。データ（行き方）に書き込まず、設定で持つ。
 *
 * @param waitMinutes 乗るたびの待ち（分）。行き・帰りの最初の乗車に足す（乗換の待ちは乗換の10分に含める）
 * @param walkCapMinutes 駅と場所の間の歩きの上限（片道・分）。超える組は候補から外す
 * @param minStayMinutes 寄れる時間の下限（分）。これより短い場所は出さない
 * @param bands 地図の帯の、過ごせる時間の下限（分）。小さい順
 * @param walkMetersPerMinute 歩きの速さ（m/分）。地図の帯の半径に使う
 * @param bandDetourFactor 地図の帯を駅のまわりの円で描くときの遠回りの係数（直線の距離 × 係数 ＝ 道のり。帯は目安の形）
 */
@ConfigurationProperties(prefix = "exday.detour")
public record DetourProperties(
    int waitMinutes,
    int walkCapMinutes,
    int minStayMinutes,
    List<Integer> bands,
    double walkMetersPerMinute,
    double bandDetourFactor) {

    /** カーソルに入れる、値の要約。値を変えると古いカーソルは使えなくなる。 */
    String digest() {
        String text = waitMinutes + "|" + walkCapMinutes + "|" + minStayMinutes + "|" + bands + "|"
            + walkMetersPerMinute + "|" + bandDetourFactor;
        return Integer.toHexString(text.hashCode());
    }
}
