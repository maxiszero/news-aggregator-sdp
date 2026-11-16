package newsaggregator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import newsaggregator.core.Article;
import newsaggregator.core.FeedSnapshot;
import newsaggregator.core.NewsFacade;

final class NewsServer implements AutoCloseable {
    private static final String JSON = "application/json; charset=utf-8";
    private static final String NO_CONTENT_CSP = "default-src 'none'; frame-ancestors 'none'";
    private static final String WEB_CSP = NO_CONTENT_CSP + "; base-uri 'none'; form-action 'self'; "
            + "script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'";
    private static final Map<String, WebAsset> WEB_ASSETS = Map.of(
            "/", new WebAsset("index.html", "text/html; charset=utf-8"),
            "/app.js", new WebAsset("app.js", "text/javascript; charset=utf-8"),
            "/styles.css", new WebAsset("styles.css", "text/css; charset=utf-8"),
            "/favicon.svg", new WebAsset("favicon.svg", "image/svg+xml; charset=utf-8"),
            "/images/coastal-grid.jpg", new WebAsset("images/coastal-grid.jpg", "image/jpeg"),
            "/images/calmer-web.jpg", new WebAsset("images/calmer-web.jpg", "image/jpeg"),
            "/images/glacier.jpg", new WebAsset("images/glacier.jpg", "image/jpeg"));

    private final NewsFacade facade = new NewsFacade();
    private final Map<String, Article> demoStories = facade.current().articles().stream()
            .collect(Collectors.toUnmodifiableMap(Article::url, Function.identity()));
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Semaphore eventSlots = new Semaphore(24);
    private final Semaphore refreshSlot = new Semaphore(1);
    private final boolean webEnabled;
    private final HttpServer server;

    NewsServer(int port) throws IOException {
        this(port, false);
    }

    NewsServer(int port, boolean webEnabled) throws IOException {
        this.webEnabled = webEnabled;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    void start() {
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            setResponseHeaders(exchange);
            if (!allowedAuthority(exchange.getRequestHeaders().getFirst("Host"), port())) {
                error(exchange, 403, "Use the local application address.");
                return;
            }

            var uri = exchange.getRequestURI();
            String path = uri.getPath();
            if (path == null) {
                error(exchange, 404, "This endpoint could not be found.");
                return;
            }
            Article story = demoStories.get(path);
            WebAsset asset = webEnabled ? WEB_ASSETS.get(uri.getRawPath()) : null;
            String expectedMethod = switch (path) {
                case "/api/refresh" -> "POST";
                case "/", "/api", "/api/feed", "/api/events" -> "GET";
                default -> story != null || asset != null ? "GET" : null;
            };
            if (expectedMethod == null) {
                error(exchange, 404, "This endpoint could not be found.");
                return;
            }
            if (!exchange.getRequestMethod().equals(expectedMethod)) {
                exchange.getResponseHeaders().set("Allow", expectedMethod);
                error(exchange, 405, "Method not allowed.");
                return;
            }

            if (asset != null) {
                serveWebAsset(exchange, asset);
                return;
            }
            switch (path) {
                case "/", "/api" -> sendJson(exchange, 200, FeedJson.discovery());
                case "/api/feed" -> feed(exchange);
                case "/api/refresh" -> refresh(exchange);
                case "/api/events" -> events(exchange);
                default -> sendJson(exchange, 200, FeedJson.article(story));
            }
        }
    }

    private static void serveWebAsset(HttpExchange exchange, WebAsset asset) throws IOException {
        try (var content = NewsServer.class.getResourceAsStream("/web/" + asset.filename())) {
            if (content == null) {
                error(exchange, 404, "This web resource could not be found.");
                return;
            }
            exchange.getResponseHeaders().set("Content-Security-Policy", WEB_CSP);
            send(exchange, 200, asset.contentType(), content.readAllBytes());
        }
    }

    private void feed(HttpExchange exchange) throws IOException {
        Map<String, String> params;
        try {
            params = query(exchange);
        } catch (IllegalArgumentException exception) {
            error(exchange, 400, "Invalid query string.");
            return;
        }
        FeedSnapshot result = facade.query(params.get("q"), params.get("category"),
                params.get("source"), params.get("sort"));
        sendJson(exchange, 200, FeedJson.snapshot(result));
    }

    private void refresh(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (!allowedOrigin(origin, port())) {
            error(exchange, 403, "Refresh is available from the local API only.");
            return;
        }
        String mode;
        try {
            mode = query(exchange).getOrDefault("mode", facade.current().mode());
        } catch (IllegalArgumentException exception) {
            error(exchange, 400, "Invalid query string.");
            return;
        }
        if (!mode.equals("demo") && !mode.equals("live")) {
            error(exchange, 400, "Choose demo or live mode.");
            return;
        }
        if (!refreshSlot.tryAcquire()) {
            error(exchange, 409, "A feed refresh is already running.");
            return;
        }
        try {
            sendJson(exchange, 200, FeedJson.snapshot(facade.refresh(mode)));
        } finally {
            refreshSlot.release();
        }
    }

    private void events(HttpExchange exchange) throws IOException {
        if (!eventSlots.tryAcquire()) {
            error(exchange, 503, "Too many live connections.");
            return;
        }

        var pending = new ArrayBlockingQueue<FeedSnapshot>(1);
        Consumer<FeedSnapshot> listener = snapshot -> {
            pending.poll();
            pending.offer(snapshot);
        };
        facade.subscribe(listener);
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                FeedSnapshot initial = facade.current();
                writeEvent(output, initial);
                long revision = initial.revision();
                while (!Thread.currentThread().isInterrupted()) {
                    FeedSnapshot next = pending.poll(10, TimeUnit.SECONDS);
                    if (next == null) {
                        output.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
                        output.flush();
                    } else if (next.revision() > revision) {
                        writeEvent(output, next);
                        revision = next.revision();
                    }
                }
            }
        } catch (IOException disconnected) {
            // The client can disconnect while the server is writing to its stream.
        } catch (InterruptedException shutdown) {
            Thread.currentThread().interrupt();
        } finally {
            facade.unsubscribe(listener);
            eventSlots.release();
        }
    }

    static boolean allowedAuthority(String host, int port) {
        return ("127.0.0.1:" + port).equals(host)
                || ("localhost:" + port).equalsIgnoreCase(host)
                || port == 80 && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host));
    }

    static boolean allowedOrigin(String origin, int port) {
        String scheme = "http://";
        return origin == null || origin.startsWith(scheme) && allowedAuthority(origin.substring(scheme.length()), port);
    }

    private static void setResponseHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", NO_CONTENT_CSP);
    }

    private static Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return Map.of();
        }
        if (raw.length() > 4096) {
            throw new IllegalArgumentException("Query too long");
        }
        var params = new HashMap<String, String>();
        for (String field : raw.split("&")) {
            String[] pair = field.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private static void writeEvent(OutputStream output, FeedSnapshot snapshot) throws IOException {
        String event = "id: " + snapshot.revision() + "\nevent: feed\ndata: " + FeedJson.event(snapshot) + "\n\n";
        output.write(event.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, FeedJson.error(message));
    }

    private static void sendJson(HttpExchange exchange, int status, String content) throws IOException {
        send(exchange, status, JSON, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] content) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
    }

    private record WebAsset(String filename, String contentType) {}
}
