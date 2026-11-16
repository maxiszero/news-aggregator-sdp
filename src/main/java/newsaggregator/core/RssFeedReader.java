package newsaggregator.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

final class RssFeedReader {
    private static final String MEDIA_RSS = "http://search.yahoo.com/mrss/";
    private final URI location;

    RssFeedReader(URI location) {
        this.location = location;
    }

    record Item(String title, String description, String link, Instant pubDate, List<String> categories,
                String image) {
    }

    List<Item> readItems() throws IOException {
        byte[] bytes = FeedInput.read(location, "application/rss+xml, application/xml, text/xml");
        Element root;
        try {
            root = parser().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
        } catch (ParserConfigurationException | SAXException error) {
            throw new IOException("RSS feed could not be parsed.", error);
        }
        if (!"rss".equalsIgnoreCase(root.getTagName())) {
            throw new IOException("Response does not contain an RSS feed.");
        }

        List<Item> articles = new ArrayList<>();
        NodeList items = root.getElementsByTagName("item");
        for (int index = 0; index < items.getLength(); index++) {
            Element item = (Element) items.item(index);
            articles.add(new Item(text(item, "title"), description(item), text(item, "link"),
                    date(text(item, "pubDate")), categories(item), image(item)));
        }
        return articles;
    }

    private static DocumentBuilder parser() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler());
        return builder;
    }

    private static String text(Element element, String tagName) {
        NodeList elements = element.getElementsByTagName(tagName);
        return elements.getLength() == 0 ? "" : elements.item(0).getTextContent();
    }

    private static String description(Element element) {
        return text(element, "description").replaceAll("<[^<>]*>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"");
    }

    private static Instant date(String value) {
        try {
            return ZonedDateTime.parse(value.strip(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(value.strip());
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }

    private static List<String> categories(Element item) {
        List<String> values = new ArrayList<>();
        NodeList categories = item.getElementsByTagName("category");
        for (int index = 0; index < categories.getLength(); index++) {
            values.add(categories.item(index).getTextContent());
        }
        return values;
    }

    private static String image(Element item) {
        NodeList thumbnails = item.getElementsByTagNameNS(MEDIA_RSS, "thumbnail");
        if (thumbnails.getLength() != 0) {
            return ((Element) thumbnails.item(0)).getAttribute("url");
        }
        NodeList enclosures = item.getElementsByTagName("enclosure");
        for (int index = 0; index < enclosures.getLength(); index++) {
            Element enclosure = (Element) enclosures.item(index);
            if (enclosure.getAttribute("type").startsWith("image/")) {
                return enclosure.getAttribute("url");
            }
        }
        return "";
    }
}
