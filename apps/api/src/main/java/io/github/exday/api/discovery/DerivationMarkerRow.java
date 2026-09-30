package io.github.exday.api.discovery;

/** 派生の目印の1件分。{@code afterPostId} の声の直後に、派生先の Discovery を挟む。 */
public record DerivationMarkerRow(String afterPostId, String discoveryId, String discoveryTitle) {}
