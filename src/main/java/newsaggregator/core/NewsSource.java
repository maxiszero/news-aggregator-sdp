package newsaggregator.core;

import java.io.IOException;
import java.util.List;

interface NewsSource {
    List<Article> fetch() throws IOException;
}
