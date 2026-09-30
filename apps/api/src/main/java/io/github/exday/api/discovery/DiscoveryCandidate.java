package io.github.exday.api.discovery;

/** DB から取得する推薦の材料。識別子は公開用のものだけを持つ。 */
public record DiscoveryCandidate(
    String discoveryId, String valueId, String title, String subject, String summary,
    String placeName, String pictureUrl, String pictureAlt,
    boolean seasonal, double distanceMeters) {}
