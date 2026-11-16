package newsaggregator.core;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

record BaseArticle(String id, String title, String summary, String url, String source,
                   String sourceId, Instant publishedAt, List<String> tags, String image) implements Article {
    static Optional<Article> from(String title, String summary, String url, String source, String sourceId,
                                  Instant publishedAt, List<String> tags, String image) {
        String cleanTitle = clean(title);
        String candidateUrl = url == null ? "" : url.strip();
        Optional<String> cleanUrl = candidateUrl.matches("/demo/[a-z0-9-]+")
                ? Optional.of(candidateUrl) : externalUrl(candidateUrl);
        if (cleanTitle.isEmpty() || cleanUrl.isEmpty() || publishedAt == null) {
            return Optional.empty();
        }
        String articleUrl = cleanUrl.orElseThrow();
        String id = UUID.nameUUIDFromBytes(canonicalUrl(articleUrl).getBytes(StandardCharsets.UTF_8)).toString();
        return Optional.of(new BaseArticle(id, cleanTitle, clean(summary), articleUrl, source, sourceId,
                publishedAt, List.copyOf(tags), externalUrl(image).orElse("")));
    }

    @Override
    public String category() {
        return "other";
    }

    static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{Z}]+", " ").strip();
    }

    private static Optional<String> externalUrl(String value) {
        String candidate = value == null ? "" : value.strip();
        try {
            URI uri = URI.create(candidate);
            if (uri.getHost() != null && uri.getUserInfo() == null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                return Optional.of(uri.toASCIIString());
            }
        } catch (IllegalArgumentException ignored) {
            // A malformed external URL is not a usable article or image.
        }
        return Optional.empty();
    }

    private static String canonicalUrl(String url) {
        URI uri = URI.create(url).normalize();
        if (!uri.isAbsolute()) {
            return uri.toString();
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1 || scheme.equals("https") && port == 443
                || scheme.equals("http") && port == 80;
        String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : Arrays.stream(uri.getRawQuery().split("&"))
                .filter(parameter -> !parameter.toLowerCase(Locale.ROOT).startsWith("utm_"))
                .collect(Collectors.joining("&"));
        return scheme + "://" + host + (defaultPort ? "" : ":" + port) + path
                + (query.isEmpty() ? "" : "?" + query);
    }
}
