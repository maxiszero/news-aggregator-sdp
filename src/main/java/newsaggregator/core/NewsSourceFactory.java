package newsaggregator.core;

import java.net.URI;

abstract class NewsSourceFactory {
    final String id;
    final String name;
    final String format;
    final String defaultCategory;
    private final URI demo;
    private final URI live;

    NewsSourceFactory(String id, String name, String format, String defaultCategory, String demo, String live) {
        this.id = id;
        this.name = name;
        this.format = format;
        this.defaultCategory = defaultCategory;
        this.demo = URI.create(demo);
        this.live = URI.create(live);
    }

    final NewsSource forMode(String mode) {
        return create("live".equals(mode) ? live : demo);
    }

    protected abstract NewsSource create(URI location);
}
