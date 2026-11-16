package newsaggregator.core;

import java.net.URI;

final class RssSourceFactory extends NewsSourceFactory {
    RssSourceFactory() {
        super("bbc", "BBC News", "RSS", "world",
                "classpath:/feeds/demo-rss.xml", "https://feeds.bbci.co.uk/news/world/rss.xml");
    }

    @Override
    protected NewsSource create(URI location) {
        return new RssNewsAdapter(new RssFeedReader(location), id, name);
    }
}
