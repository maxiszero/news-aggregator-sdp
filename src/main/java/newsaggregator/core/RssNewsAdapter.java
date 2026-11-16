package newsaggregator.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class RssNewsAdapter implements NewsSource {
    private final RssFeedReader reader;
    private final String id;
    private final String name;

    RssNewsAdapter(RssFeedReader reader, String id, String name) {
        this.reader = reader;
        this.id = id;
        this.name = name;
    }

    @Override
    public List<Article> fetch() throws IOException {
        List<Article> articles = new ArrayList<>();
        for (RssFeedReader.Item item : reader.readItems()) {
            BaseArticle.from(item.title(), item.description(), item.link(), name, id, item.pubDate(),
                    item.categories(), item.image()).ifPresent(articles::add);
        }
        return articles;
    }
}
