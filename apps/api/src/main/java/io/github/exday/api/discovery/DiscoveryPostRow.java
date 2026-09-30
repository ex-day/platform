package io.github.exday.api.discovery;

import java.time.OffsetDateTime;

/**
 * みんなの声の1件分。返信先（{@code replyTo*}）・派生先（{@code continuedIn*}）がない場合は null。
 *
 * @param id 内部の連番。資料との突き合わせにだけ使い、API には出さない
 */
public record DiscoveryPostRow(
    long id, String publicId, String authorName, OffsetDateTime postedAt, String body,
    String replyToPostId, String replyToAuthorName, String replyToExcerpt,
    int reactionCount,
    String continuedInId, String continuedInTitle) {}
