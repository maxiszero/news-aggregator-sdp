package newsaggregator;

import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;
import newsaggregator.core.Article;
import newsaggregator.core.FeedSnapshot;
import newsaggregator.core.SourceStatus;

final class FeedJson {
    private FeedJson() {}

    static String discovery() {
        return "{\"name\":\"The Signal\",\"api\":\"News Aggregator\","
                + "\"endpoints\":{\"feed\":\"GET /api/feed\",\"refresh\":\"POST /api/refresh\","
                + "\"events\":\"GET /api/events\",\"demoArticle\":\"GET /demo/{slug}\"}}";
    }

    static String snapshot(FeedSnapshot snapshot) {
        return "{\"revision\":" + snapshot.revision()
                + ",\"mode\":" + quote(snapshot.mode())
                + ",\"updatedAt\":" + quote(snapshot.updatedAt().toString())
                + ",\"articles\":" + array(snapshot.articles(), FeedJson::article)
                + ",\"sources\":" + array(snapshot.sources(), FeedJson::source) + "}";
    }

    static String event(FeedSnapshot snapshot) {
        return "{\"revision\":" + snapshot.revision() + ",\"mode\":" + quote(snapshot.mode()) + "}";
    }

    static String error(String message) {
        return "{\"error\":" + quote(message) + "}";
    }

    static String article(Article article) {
        return "{\"id\":" + quote(article.id())
                + ",\"title\":" + quote(article.title())
                + ",\"summary\":" + quote(article.summary())
                + ",\"url\":" + quote(article.url())
                + ",\"source\":" + quote(article.source())
                + ",\"sourceId\":" + quote(article.sourceId())
                + ",\"publishedAt\":" + quote(article.publishedAt().toString())
                + ",\"category\":" + quote(article.category())
                + ",\"tags\":" + array(article.tags(), FeedJson::quote)
                + ",\"image\":" + quote(article.image()) + "}";
    }

    private static String source(SourceStatus status) {
        return "{\"id\":" + quote(status.id())
                + ",\"name\":" + quote(status.name())
                + ",\"format\":" + quote(status.format())
                + ",\"count\":" + status.count()
                + ",\"state\":" + quote(status.state())
                + ",\"detail\":" + quote(status.detail()) + "}";
    }

    private static <T> String array(List<T> items, Function<T, String> encode) {
        var json = new StringJoiner(",", "[", "]");
        items.forEach(item -> json.add(encode.apply(item)));
        return json.toString();
    }

    private static String quote(String value) {
        var json = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}
