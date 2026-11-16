package newsaggregator.core;

import java.time.Instant;
import java.util.List;

public interface Article {
    String id();
    String title();
    String summary();
    String url();
    String source();
    String sourceId();
    Instant publishedAt();
    String category();
    List<String> tags();
    String image();
}
