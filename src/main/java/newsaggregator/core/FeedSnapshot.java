package newsaggregator.core;

import java.time.Instant;
import java.util.List;

public record FeedSnapshot(long revision, String mode, Instant updatedAt,
                           List<Article> articles, List<SourceStatus> sources) {
    public FeedSnapshot {
        articles = List.copyOf(articles);
        sources = List.copyOf(sources);
    }
}
