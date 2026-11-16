package newsaggregator.core;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class FeedPublisher {
    private static final System.Logger LOG = System.getLogger(FeedPublisher.class.getName());
    private final CopyOnWriteArrayList<Consumer<FeedSnapshot>> observers = new CopyOnWriteArrayList<>();

    void subscribe(Consumer<FeedSnapshot> observer) {
        observers.addIfAbsent(Objects.requireNonNull(observer));
    }

    void unsubscribe(Consumer<FeedSnapshot> observer) {
        observers.remove(observer);
    }

    void publish(FeedSnapshot snapshot) {
        for (Consumer<FeedSnapshot> observer : observers) {
            try {
                observer.accept(snapshot);
            } catch (RuntimeException error) {
                LOG.log(System.Logger.Level.WARNING, "Feed observer failed on revision " + snapshot.revision(), error);
            }
        }
    }
}
