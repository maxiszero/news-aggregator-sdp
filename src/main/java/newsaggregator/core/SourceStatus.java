package newsaggregator.core;

public record SourceStatus(String id, String name, String format, int count, String state, String detail) {
}
