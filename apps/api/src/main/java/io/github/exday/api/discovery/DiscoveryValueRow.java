package io.github.exday.api.discovery;

import java.util.List;

/**
 * DB から取得する Value 1件分。{@code seasonal}・{@code yearRound} は「仮の評価」の材料で、
 * クエリの {@code ORDER BY seasonal DESC, year_round DESC, id ASC} により、このリストの並び順が
 * そのまま推薦順（{@code recommendations} → {@code otherValues}）にも、関連 Discovery の
 * 「一番の Value」選択にも使える。
 */
public record DiscoveryValueRow(
    String valueId, String subjectLabel, String summary, String body,
    String pictureUrl, String pictureAlt, String message, String participationMessage,
    boolean seasonal, boolean yearRound, List<String> seasons) {}
