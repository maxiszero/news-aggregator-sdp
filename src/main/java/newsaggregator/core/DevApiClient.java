package newsaggregator.core;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DevApiClient {
    private final URI location;

    DevApiClient(URI location) {
        this.location = location;
    }

    record Post(String title, String description, String url, Instant publication,
                List<String> tags, String coverImage) {
    }

    List<Post> readPosts() throws IOException {
        byte[] bytes = FeedInput.read(location, "application/json");
        Object response = Json.read(new String(bytes, StandardCharsets.UTF_8));
        if (!(response instanceof List<?> entries)) {
            throw new IOException("JSON feed must contain an array of posts.");
        }

        List<Post> posts = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> post) {
                posts.add(new Post(text(post, "title"), text(post, "description"), text(post, "url"),
                        date(text(post, "published_at")), tags(post.get("tag_list")), text(post, "cover_image")));
            }
        }
        return posts;
    }

    private static String text(Map<?, ?> entry, String key) {
        return entry.get(key) instanceof String value ? value : "";
    }

    private static Instant date(String value) {
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static List<String> tags(Object value) {
        if (value instanceof List<?> tags) {
            return tags.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }
}
