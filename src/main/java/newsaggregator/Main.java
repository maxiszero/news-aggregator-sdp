package newsaggregator;

import java.io.IOException;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        Options options;
        try {
            options = parseOptions(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("Usage: newsaggregator.Main [port] [--web] (port from 0 to 65535).");
            System.exit(2);
            return;
        }

        var server = new NewsServer(options.port(), options.webEnabled());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.printf("The Signal API is running at http://127.0.0.1:%d/%n", server.port());
    }

    static Options parseOptions(String[] args) {
        boolean webEnabled = args.length > 0 && "--web".equals(args[args.length - 1]);
        int portArguments = webEnabled ? args.length - 1 : args.length;
        if (portArguments > 1) {
            throw new IllegalArgumentException("Use at most one port and one --web flag.");
        }
        int port = portArguments == 0 ? 8080 : Integer.parseInt(args[0]);
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be from 0 to 65535.");
        }
        return new Options(port, webEnabled);
    }

    record Options(int port, boolean webEnabled) {}
}
