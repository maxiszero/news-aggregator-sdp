package newsaggregator.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class JsonNewsAdapter implements NewsSource {
    private final DevApiClient client;
    private final String id;
    private final String name;

    JsonNewsAdapter(DevApiClient client, String id, String name) {
        this.client = client;
        this.id = id;
        this.name = name;
    }

    @Override
    public List<Article> fetch() throws IOException {
        List<Article> articles = new ArrayList<>();
        for (DevApiClient.Post post : client.readPosts()) {
            BaseArticle.from(post.title(), post.description(), post.url(), name, id, post.publication(),
                    post.tags(), post.coverImage()).ifPresent(articles::add);
        }
        return articles;
    }
}
