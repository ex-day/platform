package io.github.exday.api.discovery;

/** DB から取得する finding（わかってきたこと）1件分。reaction 件数はリアクションが無ければ 0。 */
public record DiscoveryFindingRow(
    String findingId, String kind, String text, String backedBy,
    int understand, int wantToGo, int maybe) {}
