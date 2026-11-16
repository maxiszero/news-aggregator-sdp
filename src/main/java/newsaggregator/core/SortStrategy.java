package newsaggregator.core;

import java.util.Comparator;

interface SortStrategy {
    Comparator<Article> BY_TIME = Comparator.comparing(Article::publishedAt);
    Comparator<Article> BY_TITLE = Comparator.comparing(Article::title, String.CASE_INSENSITIVE_ORDER);

    Comparator<Article> comparator();

    final class LatestFirst implements SortStrategy {
        @Override
        public Comparator<Article> comparator() {
            return BY_TIME.reversed().thenComparing(BY_TITLE);
        }
    }

    final class OldestFirst implements SortStrategy {
        @Override
        public Comparator<Article> comparator() {
            return BY_TIME.thenComparing(BY_TITLE);
        }
    }

    final class TitleOrder implements SortStrategy {
        @Override
        public Comparator<Article> comparator() {
            return BY_TITLE.thenComparing(BY_TIME.reversed());
        }
    }
}
