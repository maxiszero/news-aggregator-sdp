package newsaggregator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import newsaggregator.core.Article;
import newsaggregator.core.CoreRegressionTest;
import newsaggregator.core.NewsFacade;
import newsaggregator.core.SourceStatus;

public final class ProjectTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private ProjectTest() {}

    public static void main(String[] args) throws Exception {
        verifyDemo();
        CoreRegressionTest.run();
        verifyLocalAddresses();
        verifyOptions();
        verifyHttpAndObservers(false);
        verifyHttpAndObservers(true);
        System.out.println("All checks passed: adapters, deduplication, source failures, limits, sorting, JSON API, Observer and optional web frontend.");
    }

    private static void verifyDemo() {
        var facade = new NewsFacade();
        var snapshot = facade.current();
        require(snapshot.mode().equals("demo") && snapshot.articles().size() == 10, "Demo loads all 10 stories");
        require(snapshot.sources().stream().map(SourceStatus::format).toList().equals(List.of("RSS", "JSON")),
                "Both adapters ran");
        require(snapshot.sources().stream().allMatch(source -> source.state().equals("ok") && source.count() == 5),
                "Both adapters load their five fixture stories");
        require(snapshot.articles().stream().map(Article::id).distinct().count() == 10, "Story IDs are distinct");
        require(snapshot.articles().stream().allMatch(article -> article.tags().contains(article.category())),
                "Decorator adds the category to the tags");
        require(snapshot.articles().stream().allMatch(article -> article.image().isEmpty()),
                "Demo JSON keeps its empty image field");
        require(facade.search(" ACCESSIBILITY ", "technology", "dev", "latest").stream()
                .map(Article::url).toList().equals(List.of("/demo/a-calmer-web")), "Search combines tags and filters");
        require(facade.search("", "science", "bbc", "latest").stream()
                .map(Article::url).toList().equals(List.of("/demo/listening-to-glaciers")), "RSS categories are searchable");
        require(facade.search("no such article here", "all", "all", "latest").isEmpty(), "Search can return no matches");

        checkSorted(facade.search("", "all", "all", "latest"), Comparator.comparing(Article::publishedAt).reversed());
        checkSorted(facade.search("", "all", "all", "oldest"), Comparator.comparing(Article::publishedAt));
        checkSorted(facade.search("", "all", "all", "title"), Comparator.comparing(Article::title,
                String.CASE_INSENSITIVE_ORDER));
    }

    private static void checkSorted(List<Article> articles, Comparator<Article> order) {
        require(articles.size() == 10 && articles.equals(articles.stream().sorted(order).toList()), "Sort strategy order");
    }

    private static void verifyLocalAddresses() {
        require(NewsServer.allowedAuthority("127.0.0.1", 80) && NewsServer.allowedAuthority("localhost", 80)
                        && NewsServer.allowedAuthority("localhost:80", 80),
                "Loopback Host may omit the default HTTP port");
        require(NewsServer.allowedOrigin("http://127.0.0.1", 80) && NewsServer.allowedOrigin("http://localhost", 80)
                        && NewsServer.allowedOrigin("http://localhost:80", 80),
                "Loopback Origin may omit the default HTTP port");
        require(!NewsServer.allowedAuthority("localhost", 8080)
                        && !NewsServer.allowedAuthority("example.com", 80)
                        && NewsServer.allowedAuthority("localhost:8080", 8080),
                "Nondefault Host requires the exact port and loopback name");
        require(!NewsServer.allowedOrigin("http://localhost", 8080)
                        && !NewsServer.allowedOrigin("https://localhost", 80)
                        && !NewsServer.allowedOrigin("http://localhost.example.com", 80)
                        && NewsServer.allowedOrigin("http://localhost:8080", 8080),
                "Origin requires HTTP, loopback and the server's port");
    }

    private static void verifyOptions() {
        require(Main.parseOptions(new String[0]).equals(new Main.Options(8080, false)), "CLI defaults to API-only on port 8080");
        require(Main.parseOptions(new String[]{"8123"}).equals(new Main.Options(8123, false)), "CLI accepts an API-only port");
        require(Main.parseOptions(new String[]{"--web"}).equals(new Main.Options(8080, true)), "CLI accepts --web alone");
        require(Main.parseOptions(new String[]{"0", "--web"}).equals(new Main.Options(0, true)), "CLI accepts an ephemeral web port");
        for (String[] invalid : List.of(new String[]{"--unknown"}, new String[]{"-1"}, new String[]{"65536"},
                new String[]{"--web", "0"}, new String[]{"0", "--api"}, new String[]{"--web", "--web"})) {
            try {
                Main.parseOptions(invalid);
                throw new AssertionError("Unexpected CLI arguments were accepted: " + String.join(" ", invalid));
            } catch (IllegalArgumentException expected) {
                // Invalid options are rejected before the server is constructed.
            }
        }
    }

    private static void verifyHttpAndObservers(boolean webEnabled) throws Exception {
        try (var server = webEnabled ? new NewsServer(0, true) : new NewsServer(0)) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            var discovery = get(base, "/api");
            requireJson(discovery, 200);
            require(discovery.body().equals(FeedJson.discovery()), "Both modes describe API endpoints at /api");
            require(discovery.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'none'"),
                    "JSON discovery retains its content policy");
            if (webEnabled) {
                verifyWebRoutes(base);
            } else {
                var root = get(base, "/");
                requireJson(root, 200);
                require(root.body().equals(discovery.body()), "The API-only root keeps its discovery response");
                for (String path : List.of("/app.js", "/styles.css", "/favicon.svg", "/images/coastal-grid.jpg")) {
                    requireJson(get(base, path), 404);
                }
            }
            var demo = get(base, "/demo/coastal-grid");
            requireJson(demo, 200);
            require(demo.body().contains("\"title\":\"A coastal grid learns to run on the wind\"")
                    && demo.body().contains("\"url\":\"/demo/coastal-grid\"")
                    && demo.body().contains("\"image\":\"\""), "A demo link resolves to its JSON article");
            requireJson(get(base, "/demo/unknown"), 404);
            requireJson(get(base, "/index.html"), 404);
            var filtered = get(base, "/api/feed?q=calmer&category=technology&source=dev&sort=title");
            requireJson(filtered, 200);
            require(filtered.body().contains("/demo/a-calmer-web")
                    && !filtered.body().contains("/demo/coastal-grid"), "HTTP returns a filtered snapshot");
            var wrongMethod = get(base, "/api/refresh");
            requireJson(wrongMethod, 405);
            require(wrongMethod.headers().firstValue("Allow").orElse("").equals("POST"), "GET cannot refresh");
            requireJson(refresh(base, "bad-mode", null), 400);
            requireJson(refresh(base, "demo", "https://example.com"), 403);

            var readers = Executors.newFixedThreadPool(2);
            try (var firstStream = openEvents(base); var secondStream = openEvents(base)) {
                var first = new BufferedReader(new InputStreamReader(firstStream, StandardCharsets.UTF_8));
                var second = new BufferedReader(new InputStreamReader(secondStream, StandardCharsets.UTF_8));
                require(readEvent(readers, first).contains("\"revision\":1"), "First observer sees initial revision");
                require(readEvent(readers, second).contains("\"revision\":1"), "Second observer sees initial revision");
                var updated = refresh(base, "demo", null);
                requireJson(updated, 200);
                require(updated.body().contains("\"revision\":2"), "Refresh commits revision 2");
                require(readEvent(readers, first).contains("\"revision\":2"), "First observer sees the refresh");
                require(readEvent(readers, second).contains("\"revision\":2"), "Second observer sees the refresh");
                require(get(base, "/api/feed").body().contains("\"revision\":2"), "New revision is available to queries");
            } finally {
                readers.shutdownNow();
            }
        }
    }

    private static void verifyWebRoutes(String base) throws IOException, InterruptedException {
        var page = get(base, "/");
        requireContent(page, 200, "text/html; charset=utf-8");
        require(page.body().contains("The Signal"), "The optional web root serves the packaged dashboard");
        String csp = page.headers().firstValue("Content-Security-Policy").orElse("");
        require(csp.contains("default-src 'none'") && csp.contains("frame-ancestors 'none'")
                        && csp.contains("script-src 'self'") && csp.contains("style-src 'self'")
                        && csp.contains("connect-src 'self'") && csp.contains("img-src 'self'")
                        && !csp.contains("unsafe-"),
                "Web CSP permits same-origin assets and API requests");
        requireContent(get(base, "/app.js"), 200, "text/javascript; charset=utf-8");
        requireContent(get(base, "/styles.css"), 200, "text/css; charset=utf-8");
        requireContent(get(base, "/favicon.svg"), 200, "image/svg+xml; charset=utf-8");
        for (String photo : List.of("/images/coastal-grid.jpg", "/images/calmer-web.jpg", "/images/glacier.jpg")) {
            var image = HTTP.send(request(base, photo).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            requireContent(image, 200, "image/jpeg");
            require(image.body().length > 2 && image.body()[0] == (byte) 0xff && image.body()[1] == (byte) 0xd8,
                    photo + " is a JPEG file");
        }
        for (String path : List.of("/web/index.html", "/web/app.js", "/feeds/demo-rss.xml", "/images/missing.jpg",
                "/images/../app.js", "/images/%2e%2e/app.js", "/%61pp.js")) {
            requireJson(get(base, path), 404);
        }
        var post = request(base, "/app.js").POST(HttpRequest.BodyPublishers.noBody()).build();
        var rejected = HTTP.send(post, HttpResponse.BodyHandlers.ofString());
        requireJson(rejected, 405);
        require(rejected.headers().firstValue("Allow").orElse("").equals("GET"), "Static assets require GET");
    }

    private static HttpResponse<String> get(String base, String path) throws IOException, InterruptedException {
        return HTTP.send(request(base, path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> refresh(String base, String mode, String origin)
            throws IOException, InterruptedException {
        var request = request(base, "/api/refresh?mode=" + mode).POST(HttpRequest.BodyPublishers.noBody());
        if (origin != null) {
            request.header("Origin", origin);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static InputStream openEvents(String base) throws IOException, InterruptedException {
        var response = HTTP.send(request(base, "/api/events").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        require(response.statusCode() == 200, "SSE connection opens");
        require(response.headers().firstValue("Content-Type").orElse("").equals("text/event-stream; charset=utf-8"),
                "SSE uses the event-stream content type");
        return response.body();
    }

    private static String readEvent(ExecutorService executor, BufferedReader input) throws Exception {
        return executor.submit(() -> {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    return line;
                }
            }
            throw new IOException("Event stream closed.");
        }).get(5, TimeUnit.SECONDS);
    }

    private static HttpRequest.Builder request(String base, String path) {
        return HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5));
    }

    private static void requireJson(HttpResponse<String> response, int statusCode) {
        requireContent(response, statusCode, "application/json; charset=utf-8");
    }

    private static void requireContent(HttpResponse<?> response, int statusCode, String contentType) {
        require(response.statusCode() == statusCode, response.uri() + " returns status " + statusCode);
        require(response.headers().firstValue("Content-Type").orElse("").equals(contentType),
                response.uri() + " returns " + contentType);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
