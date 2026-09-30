package io.github.exday.api.discovery;

/**
 * 声とともに提供された資料の1件分。{@code kind}・{@code source*} は DB の値（大文字）のまま持つ。
 *
 * @param postId ともに提供された声の内部の連番
 */
public record DiscoveryPostMediaRow(
    long postId, String publicId, String kind, String name, String url, String alt,
    String sourceStatus, String sourceType, String sourceName) {}
