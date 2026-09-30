package io.github.exday.api.discovery;

import io.github.exday.api.context.ClockConfiguration;
import io.github.exday.api.generated.model.DerivationMarker;
import io.github.exday.api.generated.model.DiscoveryPosts;
import io.github.exday.api.generated.model.DiscoveryRef;
import io.github.exday.api.generated.model.Image;
import io.github.exday.api.generated.model.MediaItem;
import io.github.exday.api.generated.model.MediaSource;
import io.github.exday.api.generated.model.Post;
import io.github.exday.api.generated.model.ReplyQuote;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** DB から取得した行を、S03 のみんなの声（{@link DiscoveryPosts}）に組み立てる。 */
@Component
public class DiscoveryPostsAssembler {

    private final DiscoveryPostsQueries queries;

    public DiscoveryPostsAssembler(DiscoveryPostsQueries queries) {
        this.queries = queries;
    }

    public DiscoveryPosts assemble(long discoveryId) {
        Map<Long, List<MediaItem>> mediaByPost = new LinkedHashMap<>();
        for (var row : queries.findPostMedia(discoveryId)) {
            mediaByPost.computeIfAbsent(row.postId(), key -> new ArrayList<>()).add(toMediaItem(row));
        }

        List<Post> posts = queries.findPosts(discoveryId).stream()
            .map(row -> toPost(row, mediaByPost.getOrDefault(row.id(), List.of())))
            .toList();

        List<DerivationMarker> markers = queries.findDerivationMarkers(discoveryId).stream()
            .map(row -> new DerivationMarker(
                row.afterPostId(), new DiscoveryRef(row.discoveryId(), row.discoveryTitle())))
            .toList();

        var result = new DiscoveryPosts(posts, markers);
        queries.findDerivedOrigin(discoveryId)
            .ifPresent(origin -> result.setDerivedOrigin(new DiscoveryRef(origin.id(), origin.title())));
        return result;
    }

    private static Post toPost(DiscoveryPostRow row, List<MediaItem> media) {
        var postedAt = row.postedAt().atZoneSameInstant(ClockConfiguration.ZONE).toOffsetDateTime();
        var post = new Post(row.publicId(), row.authorName(), postedAt, row.body(), media, row.reactionCount());
        if (row.replyToPostId() != null) {
            post.setReplyTo(new ReplyQuote(row.replyToPostId(), row.replyToAuthorName(), row.replyToExcerpt()));
        }
        if (row.continuedInId() != null) {
            post.setContinuedIn(new DiscoveryRef(row.continuedInId(), row.continuedInTitle()));
        }
        return post;
    }

    private static MediaItem toMediaItem(DiscoveryPostMediaRow row) {
        var kind = MediaItem.KindEnum.fromValue(lower(row.kind()));
        var source = new MediaSource(MediaSource.StatusEnum.fromValue(lower(row.sourceStatus())));
        if (row.sourceType() != null) {
            source.setType(MediaSource.TypeEnum.fromValue(lower(row.sourceType())));
        }
        source.setName(row.sourceName());
        var item = new MediaItem(row.publicId(), kind, row.name(), source);
        if (kind == MediaItem.KindEnum.IMAGE) {
            item.setImage(new Image(row.url(), row.alt()));
        }
        return item;
    }

    private static String lower(String dbValue) {
        return dbValue.toLowerCase(Locale.ROOT);
    }
}
