package io.github.exday.api.discovery;

/** S03 の対象 Discovery の概要（公開中のものだけを対象にする）。 */
public record DiscoverySummary(long id, String publicId, String title, String body, String placeName) {}
