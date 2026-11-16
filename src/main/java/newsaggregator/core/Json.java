package newsaggregator.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private static final int MAX_DEPTH = 32;
    private final String input;
    private int position;

    private Json(String input) {
        this.input = input;
    }

    static Object read(String input) throws IOException {
        Json parser = new Json(input);
        Object value = parser.value(0);
        if (parser.nextToken() != '\0' || parser.position != input.length()) {
            throw parser.malformed();
        }
        return value;
    }

    private Object value(int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("JSON feed exceeds the nesting limit.");
        }
        return switch (nextToken()) {
            case '{' -> object(depth + 1);
            case '[' -> array(depth + 1);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object(int depth) throws IOException {
        expect('{');
        Map<String, Object> fields = new LinkedHashMap<>();
        if (take('}')) {
            return fields;
        }
        do {
            String key = string();
            expect(':');
            fields.put(key, value(depth));
        } while (take(','));
        expect('}');
        return fields;
    }

    private List<Object> array(int depth) throws IOException {
        expect('[');
        List<Object> items = new ArrayList<>();
        if (take(']')) {
            return items;
        }
        do {
            items.add(value(depth));
        } while (take(','));
        expect(']');
        return items;
    }

    private String string() throws IOException {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (position < input.length()) {
            char character = input.charAt(position++);
            if (character == '"') {
                return value.toString();
            }
            if (character < 0x20) {
                throw malformed();
            }
            value.append(character == '\\' ? escapedCharacter() : character);
        }
        throw malformed();
    }

    private char escapedCharacter() throws IOException {
        if (position == input.length()) {
            throw malformed();
        }
        return switch (input.charAt(position++)) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> unicodeCharacter();
            default -> throw malformed();
        };
    }

    private char unicodeCharacter() throws IOException {
        if (position + 4 > input.length()) {
            throw malformed();
        }
        try {
            char character = (char) HexFormat.fromHexDigits(input, position, position + 4);
            position += 4;
            return character;
        } catch (NumberFormatException error) {
            throw malformed();
        }
    }

    private Object literal(String token, Object value) throws IOException {
        if (!input.startsWith(token, position)) {
            throw malformed();
        }
        position += token.length();
        return value;
    }

    private BigDecimal number() throws IOException {
        int start = position;
        takeRaw('-');
        if (!takeRaw('0')) {
            digits();
        }
        if (takeRaw('.')) {
            digits();
        }
        if (takeRaw('e') || takeRaw('E')) {
            if (!takeRaw('+')) {
                takeRaw('-');
            }
            digits();
        }
        try {
            return new BigDecimal(input.substring(start, position));
        } catch (NumberFormatException error) {
            throw malformed();
        }
    }

    private void digits() throws IOException {
        int start = position;
        while (position < input.length() && input.charAt(position) >= '0' && input.charAt(position) <= '9') {
            position++;
        }
        if (position == start) {
            throw malformed();
        }
    }

    private void expect(char token) throws IOException {
        if (!take(token)) {
            throw malformed();
        }
    }

    private boolean take(char token) {
        nextToken();
        return takeRaw(token);
    }

    private boolean takeRaw(char token) {
        if (position < input.length() && input.charAt(position) == token) {
            position++;
            return true;
        }
        return false;
    }

    private char nextToken() {
        while (position < input.length()) {
            char character = input.charAt(position);
            if (character != ' ' && character != '\n' && character != '\r' && character != '\t') {
                return character;
            }
            position++;
        }
        return '\0';
    }

    private IOException malformed() {
        return new IOException("Malformed JSON feed at character " + (position + 1) + ".");
    }
}
