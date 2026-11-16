package newsaggregator.core;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class NewsFacade {
    private static final SortStrategy LATEST = new SortStrategy.LatestFirst();
    private static final Map<String, SortStrategy> SORTS = Map.of(
            "latest", LATEST,
            "oldest", new SortStrategy.OldestFirst(),
            "title", new SortStrategy.TitleOrder());

    private final List<NewsSourceFactory> sources;
    private final FeedPublisher publisher = new FeedPublisher();
    private volatile FeedSnapshot snapshot = new FeedSnapshot(0, "demo", Instant.EPOCH, List.of(), List.of());
    private boolean refreshing;

    public NewsFacade() {
        this(List.of(new RssSourceFactory(), new JsonSourceFactory()));
    }

    NewsFacade(List<NewsSourceFactory> sources) {
        this.sources = List.copyOf(sources);
        refresh("demo");
    }

    public FeedSnapshot current() {
        return snapshot;
    }

    public synchronized FeedSnapshot refresh(String mode) {
        if (refreshing) {
            throw new IllegalStateException("An observer cannot refresh the feed from its update callback.");
        }
        if (!"demo".equals(mode) && !"live".equals(mode)) {
            throw new IllegalArgumentException("Mode must be demo or live.");
        }
        refreshing = true;
        try {
            return refreshSources(mode);
        } finally {
            refreshing = false;
        }
    }

    private FeedSnapshot refreshSources(String mode) {
        Map<String, Article> unique = new LinkedHashMap<>();
        List<SourceStatus> statuses = new ArrayList<>();
        for (NewsSourceFactory factory : sources) {
            try {
                List<Article> fetched = factory.forMode(mode).fetch();
                for (Article article : fetched) {
                    unique.putIfAbsent(article.id(), new CategorizedArticle(article, factory.defaultCategory));
                }
                statuses.add(new SourceStatus(factory.id, factory.name, factory.format, fetched.size(),
                        "ok", "Source loaded successfully."));
            } catch (IOException error) {
                String detail = error.getMessage() == null ? "Feed could not be loaded." : error.getMessage();
                statuses.add(new SourceStatus(factory.id, factory.name, factory.format, 0, "error", detail));
            }
        }

        List<Article> ordered = unique.values().stream().sorted(LATEST.comparator()).toList();
        FeedSnapshot updated = new FeedSnapshot(snapshot.revision() + 1, mode, Instant.now(), ordered, statuses);
        snapshot = updated;
        publisher.publish(updated);
        return updated;
    }

    public void subscribe(Consumer<FeedSnapshot> observer) {
        publisher.subscribe(observer);
    }

    public void unsubscribe(Consumer<FeedSnapshot> observer) {
        publisher.unsubscribe(observer);
    }

    public List<Article> search(String query, String category, String source, String sort) {
        return query(query, category, source, sort).articles();
    }

    public FeedSnapshot query(String query, String category, String source, String sort) {
        FeedSnapshot current = snapshot;
        String needle = normalize(query);
        String categoryFilter = normalize(category);
        String sourceFilter = normalize(source);
        SortStrategy strategy = SORTS.getOrDefault(normalize(sort), LATEST);
        List<Article> articles = current.articles().stream()
                .filter(article -> matchesFilter(categoryFilter, article.category()))
                .filter(article -> matchesFilter(sourceFilter, article.sourceId()))
                .filter(article -> matchesQuery(article, needle))
                .sorted(strategy.comparator())
                .toList();
        return new FeedSnapshot(current.revision(), current.mode(), current.updatedAt(), articles, current.sources());
    }

    private static String normalize(String value) {
        return BaseArticle.clean(value).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesFilter(String filter, String value) {
        return filter.isEmpty() || "all".equals(filter) || filter.equals(value);
    }

    private static boolean matchesQuery(Article article, String query) {
        return query.isEmpty() || normalize(article.title()).contains(query)
                || normalize(article.summary()).contains(query)
                || normalize(article.source()).contains(query)
                || article.tags().stream().anyMatch(tag -> tag.contains(query));
    }
}
