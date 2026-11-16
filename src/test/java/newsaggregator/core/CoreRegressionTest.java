package newsaggregator.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class CoreRegressionTest {
    private static final String RSS = """
            <rss version="2.0"><channel>
              <item>
                <title>A telescope tracks incoming meteors</title>
                <description><![CDATA[<p>Night skies &amp; ice.</p>]]></description>
                <link>https://EXAMPLE.com:443/meteors?utm_source=rss#lead</link>
                <pubDate>Mon, 16 Nov 2026 08:00:00 GMT</pubDate>
                <category>space</category>
              </item>
            </channel></rss>
            """;
    private static final String JSON = """
            [
              {
                "title": "Duplicate with a cleaner URL",
                "url": "https://example.com/meteors",
                "published_at": "2026-11-16T07:00:00Z"
              },
              {
                "title": "A calmer website",
                "description": "Escaped \\"quotes\\" and unicode \\u2603",
                "url": "https://example.com/site",
                "published_at": "2026-11-16T06:00:00Z",
                "tag_list": ["webdev"," accessibility ","WEBDEV"],
                "extra": {"metrics": [1e2, true, null]}
              },
              {
                "title": "Invalid article URL",
                "url": "javascript:alert(1)",
                "published_at": "2026-11-16T04:00:00Z"
              }
            ]
            """;

    private CoreRegressionTest() {
    }

    public static void run() throws IOException {
        verifyReentrantRefresh();
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String address = "http://127.0.0.1:" + provider.getAddress().getPort();
        var entityFetched = new AtomicBoolean();
        provider.createContext("/rss", exchange -> respond(exchange, RSS));
        provider.createContext("/json", exchange -> respond(exchange, JSON));
        provider.createContext("/malformed-json", exchange -> respond(exchange, "[{\"title\":}"));
        provider.createContext("/malformed-rss", exchange -> respond(exchange,
                "<!DOCTYPE rss [<!ENTITY data SYSTEM \"" + address + "/entity\">]>"
                        + "<rss><channel><title>&data;</title></channel></rss>"));
        provider.createContext("/entity", exchange -> {
            entityFetched.set(true);
            respond(exchange, "External entity");
        });
        provider.createContext("/large", exchange -> respond(exchange, new byte[2 * 1024 * 1024 + 1]));
        provider.createContext("/slow", CoreRegressionTest::sendSlowBody);
        provider.start();
        try {
            verifyProviderData(address);
            require(!entityFetched.get(), "RSS parser rejects a DOCTYPE without fetching the external entity");
            expectIo(() -> FeedInput.read(URI.create(address + "/large"), "application/json"), "size limit");
            expectIo(() -> FeedInput.read(URI.create(address + "/slow"), "application/json"), "timed out");
            require(FeedInput.read(URI.create(address + "/json"), "application/json").length > 0,
                    "A timed-out request does not prevent the next successful request");
        } finally {
            provider.stop(0);
        }
    }

    private static void verifyReentrantRefresh() {
        var facade = new NewsFacade();
        var rejected = new ArrayList<Long>();
        var delivered = new ArrayList<Long>();
        facade.subscribe(snapshot -> {
            try {
                facade.refresh("demo");
                throw new AssertionError("A refresh from an observer must fail");
            } catch (IllegalStateException expected) {
                rejected.add(snapshot.revision());
            }
        });
        facade.subscribe(snapshot -> {
            require(snapshot == facade.current(), "Observer receives an already committed snapshot");
            delivered.add(snapshot.revision());
        });
        require(facade.refresh("demo").revision() == 2, "An observer cannot create an extra revision");
        require(facade.refresh("demo").revision() == 3, "The refresh guard resets when delivery completes");
        require(rejected.equals(List.of(2L, 3L)) && delivered.equals(rejected),
                "Reentry is rejected and the remaining observer still sees revisions in order");
    }

    private static void verifyProviderData(String address) throws IOException {
        String validJson = address + "/json";
        var facade = new NewsFacade(List.of(
                factory("rss", "RSS", "world", address + "/rss", address + "/malformed-rss",
                        location -> new RssNewsAdapter(new RssFeedReader(location), "rss", "RSS")),
                factory("json", "JSON", "technology", validJson, validJson,
                        location -> new JsonNewsAdapter(new DevApiClient(location), "json", "JSON"))));
        var combined = facade.current();
        require(combined.articles().size() == 2, "Canonical duplicate and unsafe URL are discarded");
        require(combined.sources().stream().map(SourceStatus::count).toList().equals(List.of(1, 2)),
                "Sources report valid items before cross-source deduplication");
        Article rss = combined.articles().get(0);
        require(rss.sourceId().equals("rss") && rss.summary().equals("Night skies & ice.")
                        && rss.category().equals("science") && rss.tags().equals(List.of("science", "space")),
                "RSS markup is stripped and the decorator adds the science category to space");
        Article json = combined.articles().get(1);
        require(json.summary().equals("Escaped \"quotes\" and unicode \u2603")
                        && json.tags().equals(List.of("technology", "webdev", "accessibility")),
                "JSON escapes are decoded and tags are normalized and deduplicated");

        var partial = facade.refresh("live");
        require(partial.articles().size() == 2 && partial.articles().stream().allMatch(a -> a.sourceId().equals("json"))
                        && partial.sources().stream().map(SourceStatus::state).toList().equals(List.of("error", "ok")),
                "A failed RSS feed leaves the valid JSON feed available");
        require(partial.articles().stream().anyMatch(a -> a.id().equals(rss.id())),
                "Equivalent URLs produce a stable ID across adapters and refreshes");
        expectIo(() -> new DevApiClient(URI.create(address + "/malformed-json")).readPosts(), "Malformed JSON");
    }

    private static NewsSourceFactory factory(String id, String format, String category, String demo, String live,
                                             Function<URI, NewsSource> createSource) {
        return new NewsSourceFactory(id, format, format, category, demo, live) {
            @Override
            protected NewsSource create(URI location) {
                return createSource.apply(location);
            }
        };
    }

    private static void sendSlowBody(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(200, 0);
            var output = exchange.getResponseBody();
            for (int chunk = 0; chunk < 24; chunk++) {
                output.write(' ');
                output.flush();
                Thread.sleep(250);
            }
        } catch (InterruptedException shutdown) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static void expectIo(IoAction action, String message) throws IOException {
        try {
            action.run();
        } catch (IOException failure) {
            require(failure.getMessage().contains(message), "Expected " + message + ", received " + failure);
            return;
        }
        throw new AssertionError("Expected IOException with " + message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
