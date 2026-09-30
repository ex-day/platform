package io.github.exday.api.discovery;

/** Discovery へのリアクション種類ごとの件数（0件を含む）。 */
public record DiscoveryReactionCounts(int like, int surprised, int love) {}
