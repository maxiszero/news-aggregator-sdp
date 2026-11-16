package newsaggregator.core;

import java.net.URI;

final class JsonSourceFactory extends NewsSourceFactory {
    JsonSourceFactory() {
        super("dev", "DEV Community", "JSON", "technology",
                "classpath:/feeds/demo-json.json", "https://dev.to/api/articles?per_page=12");
    }

    @Override
    protected NewsSource create(URI location) {
        return new JsonNewsAdapter(new DevApiClient(location), id, name);
    }
}
