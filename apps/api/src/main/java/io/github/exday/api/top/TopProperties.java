package io.github.exday.api.top;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TOP（S01）の設定。どちらも場所の識別子（public_id）で持つ。
 *
 * @param defaultOriginPlaceId 既定の起点（{@code from} を省いたとき）。始める地域の中の場所（例：桜木町駅）。
 *                             始める地域（市）そのものにすると、「近く」が市の全体になり広すぎる
 * @param startAreaPlaceId 始める地域の場所（例：横浜市）。起点がこの外かどうかで、始める地域の外のお知らせを出す。
 *                         設定がない、または場所がないときは、外のお知らせを出さない
 */
@ConfigurationProperties(prefix = "exday.top")
public record TopProperties(String defaultOriginPlaceId, String startAreaPlaceId) {}
