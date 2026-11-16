package newsaggregator.core;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CategorizedArticle implements Article {
    private static final Map<String, String> TOPICS = Map.ofEntries(
            Map.entry("world", "world"), Map.entry("politics", "world"),
            Map.entry("international", "world"), Map.entry("society", "world"),
            Map.entry("technology", "technology"), Map.entry("tech", "technology"),
            Map.entry("programming", "technology"), Map.entry("webdev", "technology"),
            Map.entry("javascript", "technology"), Map.entry("java", "technology"),
            Map.entry("ai", "technology"), Map.entry("cybersecurity", "technology"),
            Map.entry("science", "science"), Map.entry("space", "science"),
            Map.entry("climate", "science"), Map.entry("environment", "science"),
            Map.entry("health", "science"), Map.entry("research", "science"),
            Map.entry("business", "business"), Map.entry("economy", "business"),
            Map.entry("finance", "business"), Map.entry("markets", "business"),
            Map.entry("startups", "business"),
            Map.entry("culture", "culture"), Map.entry("arts", "culture"),
            Map.entry("music", "culture"), Map.entry("design", "culture"),
            Map.entry("books", "culture"), Map.entry("film", "culture"),
            Map.entry("other", "other"));

    private final Article article;
    private final String category;
    private final List<String> tags;

    CategorizedArticle(Article article, String defaultCategory) {
        this.article = article;
        List<String> sourceTags = article.tags().stream()
                .map(tag -> BaseArticle.clean(tag).toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .toList();
        category = sourceTags.stream().filter(TOPICS::containsKey)
                .map(TOPICS::get).findFirst().orElse(defaultCategory);
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        labels.add(category);
        labels.addAll(sourceTags);
        tags = List.copyOf(labels);
    }

    @Override public String id() { return article.id(); }
    @Override public String title() { return article.title(); }
    @Override public String summary() { return article.summary(); }
    @Override public String url() { return article.url(); }
    @Override public String source() { return article.source(); }
    @Override public String sourceId() { return article.sourceId(); }
    @Override public Instant publishedAt() { return article.publishedAt(); }
    @Override public String category() { return category; }
    @Override public List<String> tags() { return tags; }
    @Override public String image() { return article.image(); }
}
