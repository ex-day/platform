package io.github.exday.api.discovery;

/**
 * 対象 Discovery と関係する、もう一方の Discovery 1件分。
 *
 * @param derivedFrom 対象 Discovery が派生してできた（相手が派生元）
 * @param derivedTo 対象 Discovery から相手が派生してできた
 *
 * <p>{@code derivedFrom}・{@code derivedTo} がともに false の場合は {@code RELATED}（向きを問わない関連）。
 */
public record DiscoveryRelationRow(
    boolean derivedFrom, boolean derivedTo,
    long otherId, String otherPublicId, String otherTitle, String otherPlaceName) {}
