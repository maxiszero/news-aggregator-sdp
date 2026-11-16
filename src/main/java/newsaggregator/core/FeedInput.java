package newsaggregator.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class FeedInput {
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final String TOO_LARGE = "Feed exceeds the 2 MB size limit.";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private FeedInput() {
    }

    static byte[] read(URI location, String accept) throws IOException {
        if ("classpath".equals(location.getScheme())) {
            try (InputStream input = FeedInput.class.getResourceAsStream(location.getPath())) {
                if (input == null) {
                    throw new IOException("Demo feed file is missing.");
                }
                return readLimited(input);
            }
        }

        HttpRequest request = HttpRequest.newBuilder(location)
                .header("Accept", accept)
                .header("User-Agent", "NewsAggregator/1.0")
                .GET()
                .build();
        var responseTask = HTTP.sendAsync(request, response -> response.statusCode() == 200
                ? new LimitedBodySubscriber() : BodySubscribers.replacing(new byte[0]));
        try {
            // Wait for the complete body so a stream that sends bytes slowly still has a deadline.
            HttpResponse<byte[]> response = responseTask.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                throw new IOException("Feed returned HTTP " + response.statusCode() + ".");
            }
            return response.body();
        } catch (TimeoutException error) {
            responseTask.cancel(true);
            throw new IOException("Feed request timed out after " + TIMEOUT.toSeconds() + " seconds.", error);
        } catch (InterruptedException error) {
            responseTask.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Feed request was interrupted.", error);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof IOException ioError) {
                throw ioError;
            }
            throw new IOException("Feed request failed.", error.getCause());
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
            throw new IOException(TOO_LARGE);
        }
        return bytes;
    }

    private static final class LimitedBodySubscriber implements BodySubscriber<byte[]> {
        private final BodySubscriber<byte[]> downstream = BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long bytesReceived;

        @Override
        public CompletionStage<byte[]> getBody() {
            return downstream.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            downstream.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            bytesReceived += buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if (bytesReceived > MAX_BYTES) {
                subscription.cancel();
                downstream.onError(new IOException(TOO_LARGE));
                return;
            }
            downstream.onNext(buffers);
        }

        @Override
        public void onError(Throwable error) {
            downstream.onError(error);
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
        }
    }
}
