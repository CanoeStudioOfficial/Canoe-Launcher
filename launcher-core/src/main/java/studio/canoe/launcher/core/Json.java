package studio.canoe.launcher.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("Unexpected trailing JSON at position " + parser.position);
        }
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder builder = new StringBuilder();
        write(builder, value);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String string) {
            writeString(builder, string);
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                writeString(builder, String.valueOf(entry.getKey()));
                builder.append(':');
                write(builder, entry.getValue());
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            Iterator<?> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                write(builder, iterator.next());
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append(']');
        } else if (value.getClass().isArray()) {
            builder.append('[');
            Object[] array = (Object[]) value;
            for (int index = 0; index < array.length; index++) {
                write(builder, array[index]);
                if (index + 1 < array.length) {
                    builder.append(',');
                }
            }
            builder.append(']');
        } else {
            writeString(builder, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static final class Parser {
        private final String source;
        private int position;

        private Parser(String source) {
            this.source = source;
        }

        private Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON.");
            }

            char ch = source.charAt(position);
            return switch (ch) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (ch == '-' || Character.isDigit(ch)) {
                        yield parseNumber();
                    }
                    throw new IllegalArgumentException("Unexpected JSON token at position " + position);
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                position++;
                return object;
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    position++;
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                position++;
                return array;
            }

            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    position++;
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (!isAtEnd()) {
                char ch = source.charAt(position++);
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch == '\\') {
                    if (isAtEnd()) {
                        throw new IllegalArgumentException("Unterminated escape sequence.");
                    }
                    char escaped = source.charAt(position++);
                    switch (escaped) {
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> {
                            if (position + 4 > source.length()) {
                                throw new IllegalArgumentException("Invalid unicode escape.");
                            }
                            String hex = source.substring(position, position + 4);
                            builder.append((char) Integer.parseInt(hex, 16));
                            position += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape character: " + escaped);
                    }
                } else {
                    builder.append(ch);
                }
            }
            throw new IllegalArgumentException("Unterminated string.");
        }

        private Object parseLiteral(String literal, Object value) {
            if (!source.startsWith(literal, position)) {
                throw new IllegalArgumentException("Invalid literal at position " + position);
            }
            position += literal.length();
            return value;
        }

        private Number parseNumber() {
            int start = position;
            if (peek('-')) {
                position++;
            }
            while (!isAtEnd() && Character.isDigit(source.charAt(position))) {
                position++;
            }
            if (!isAtEnd() && source.charAt(position) == '.') {
                position++;
                while (!isAtEnd() && Character.isDigit(source.charAt(position))) {
                    position++;
                }
            }
            if (!isAtEnd() && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
                position++;
                if (!isAtEnd() && (source.charAt(position) == '+' || source.charAt(position) == '-')) {
                    position++;
                }
                while (!isAtEnd() && Character.isDigit(source.charAt(position))) {
                    position++;
                }
            }
            String number = source.substring(start, position);
            return number.contains(".") || number.contains("e") || number.contains("E")
                    ? Double.parseDouble(number)
                    : Long.parseLong(number);
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private void expect(char expected) {
            if (isAtEnd() || source.charAt(position) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + position);
            }
            position++;
        }

        private boolean peek(char expected) {
            return !isAtEnd() && source.charAt(position) == expected;
        }

        private boolean isAtEnd() {
            return position >= source.length();
        }
    }
}
