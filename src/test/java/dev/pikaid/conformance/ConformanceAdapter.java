package dev.pikaid.conformance;

import dev.pikaid.Pikaid;
import dev.pikaid.PikaidFormatException;
import dev.pikaid.PikaidGenerator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Test-only JSON-Lines bridge for the official pikaid conformance suite.
 *
 * <p>This class is not part of the published artifact. It parses one protocol
 * request per line, calls the real public {@link Pikaid} and
 * {@link PikaidGenerator} API, and writes exactly one response per request. It
 * never re-implements encoding, decoding, validation, or randomness.</p>
 *
 * <p>The bridge advertises every optional capability because the public Java API
 * genuinely provides each one. The {@code csprng_failure_injection} capability
 * is test-only: it wraps a real {@link SecureRandom} in a switchable delegate and
 * constructs the documented {@link PikaidGenerator} test constructor, so the
 * production failure-propagation path is exercised without adding any
 * production API.</p>
 *
 * <p>Run with:</p>
 *
 * <pre>
 * java -cp target/classes:target/test-classes \
 *     dev.pikaid.conformance.ConformanceAdapter
 * </pre>
 */
public final class ConformanceAdapter {

    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final ToggleSecureRandom randomSource = new ToggleSecureRandom();
    private final PikaidGenerator generator =
            new PikaidGenerator(randomSource, Clock.systemUTC());

    private ConformanceAdapter() {
    }

    /**
     * Starts the bridge on standard input and output.
     *
     * @param args ignored
     * @throws IOException if standard input cannot be read or output cannot be written
     */
    public static void main(String[] args) throws IOException {
        BufferedReader input = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Writer output = new BufferedWriter(
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        new ConformanceAdapter().serve(input, output);
    }

    private void serve(BufferedReader input, Writer output) throws IOException {
        String line;
        while ((line = input.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            Object parsed;
            try {
                parsed = Json.parse(line);
            } catch (IllegalArgumentException malformed) {
                System.err.println("malformed request JSON: " + malformed.getMessage());
                System.exit(2);
                return;
            }
            Map<String, Object> request = asObject(parsed, "request");
            Object idValue = request.get("request_id");
            if (!(idValue instanceof Long requestId)) {
                System.err.println("request_id must be an integer");
                System.exit(2);
                return;
            }
            Object operation = request.get("operation");
            if (!(operation instanceof String operationName)) {
                output.write(error(requestId, "invalid_input",
                        "operation must be a string"));
                output.write('\n');
                output.flush();
                continue;
            }
            Map<String, Object> arguments;
            Object rawArguments = request.get("arguments");
            if (rawArguments == null) {
                arguments = Map.of();
            } else {
                try {
                    arguments = asObject(rawArguments, "arguments");
                } catch (IllegalArgumentException wrongShape) {
                    output.write(error(requestId, "invalid_input",
                            wrongShape.getMessage()));
                    output.write('\n');
                    output.flush();
                    continue;
                }
            }
            output.write(dispatch(requestId, operationName, arguments));
            output.write('\n');
            output.flush();
        }
    }

    private String dispatch(long requestId, String operation, Map<String, Object> arguments) {
        try {
            return success(requestId, perform(operation, arguments));
        } catch (InvalidWireInputException invalid) {
            return error(requestId, "invalid_input", invalid.getMessage());
        } catch (PikaidFormatException invalid) {
            return error(requestId, "invalid_input", nonEmpty(invalid.getMessage()));
        } catch (EntropyUnavailableException entropy) {
            return error(requestId, "entropy_unavailable", entropy.getMessage());
        } catch (UnsupportedOperationException unsupported) {
            return error(requestId, "unsupported_operation", unsupported.getMessage());
        } catch (RuntimeException unexpected) {
            return error(requestId, "internal_error",
                    nonEmpty(unexpected.getMessage() == null
                            ? unexpected.getClass().getName()
                            : unexpected.getClass().getName() + ": " + unexpected.getMessage()));
        }
    }

    private Object perform(String operation, Map<String, Object> arguments) {
        switch (operation) {
            case "hello":
                return hello();
            case "generate_string":
                return generator.generate().toString();
            case "text_to_binary":
                return toHex(Pikaid.parse(requireString(arguments, "text")).toBinary());
            case "binary_to_text":
                return Pikaid.fromBinary(fromHex(requireString(arguments, "binary_hex"))).toString();
            case "boolean_validation":
                return Pikaid.isValid(requireString(arguments, "text"));
            case "format_only_validation":
                return Pikaid.isWellFormed(requireString(arguments, "text"));
            case "parse_components":
                return components(Pikaid.parse(requireString(arguments, "text")));
            case "generate_with_timestamp":
                return generator.generate(requireTimestamp(arguments)).toString();
            case "generate_binary":
                return toHex(generator.generateBinary());
            case "generate_binary_with_timestamp":
                return toHex(generator.generateBinary(requireTimestamp(arguments)));
            case "public_compare":
                return Pikaid.parse(requireString(arguments, "a"))
                        .compareTo(Pikaid.parse(requireString(arguments, "b")));
            case "inject_fault":
                return injectFault(arguments);
            default:
                throw new UnsupportedOperationException("unknown operation: " + operation);
        }
    }

    private Map<String, Object> hello() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("protocol_version", 1L);
        value.put("capabilities", List.of(
                "generate_string",
                "text_to_binary",
                "binary_to_text",
                "boolean_validation",
                "format_only_validation",
                "parse_components",
                "parse_datetime",
                "generate_with_timestamp",
                "generate_binary",
                "generate_binary_with_timestamp",
                "public_compare",
                "csprng_failure_injection"));
        return value;
    }

    private static Map<String, Object> components(Pikaid pikaid) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("timestamp_seconds", pikaid.timestamp());
        value.put("randomness_hex", pikaid.randomnessHex());
        value.put("datetime_utc", UTC_FORMAT.format(pikaid.timestampInstant()));
        return value;
    }

    private Map<String, Object> injectFault(Map<String, Object> arguments) {
        String fault = requireString(arguments, "fault");
        if (!"csprng_failure".equals(fault)) {
            throw new InvalidWireInputException("unsupported fault: " + fault);
        }
        Object active = arguments.get("active");
        if (!(active instanceof Boolean failing)) {
            throw new InvalidWireInputException("active must be a boolean");
        }
        randomSource.setFailing(failing);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("fault", fault);
        value.put("active", failing);
        return value;
    }

    private static String requireString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text)) {
            throw new InvalidWireInputException("argument " + name + " must be a string");
        }
        return text;
    }

    private static long requireTimestamp(Map<String, Object> arguments) {
        Object value = arguments.get("timestamp_seconds");
        if (!(value instanceof Long timestamp)) {
            throw new InvalidWireInputException("argument timestamp_seconds must be an integer");
        }
        return timestamp;
    }

    private static String nonEmpty(String message) {
        return message == null || message.isEmpty() ? "operation failed" : message;
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 0x0F];
        }
        return new String(out);
    }

    private static byte[] fromHex(String hex) {
        if (hex.length() != Pikaid.BINARY_LENGTH * 2) {
            throw new InvalidWireInputException(
                    "binary_hex must be exactly " + (Pikaid.BINARY_LENGTH * 2) + " characters");
        }
        byte[] out = new byte[Pikaid.BINARY_LENGTH];
        for (int i = 0; i < out.length; i++) {
            int high = lowerHexDigit(hex.charAt(i * 2));
            int low = lowerHexDigit(hex.charAt(i * 2 + 1));
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    private static int lowerHexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        throw new InvalidWireInputException("binary_hex must contain only lowercase hex digits");
    }

    private static String success(long requestId, Object value) {
        return "{\"request_id\":" + requestId + ",\"ok\":true,\"value\":" + writeValue(value) + '}';
    }

    private static String error(long requestId, String category, String message) {
        return "{\"request_id\":" + requestId + ",\"ok\":false,\"error\":{\"category\":"
                + writeValue(category) + ",\"message\":" + writeValue(message) + "}}";
    }


    private static String writeValue(Object value) {
        StringBuilder out = new StringBuilder();
        appendValue(out, value);
        return out.toString();
    }

    private static void appendValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            appendString(out, text);
        } else if (value instanceof Long || value instanceof Integer) {
            out.append(value);
        } else if (value instanceof Boolean flag) {
            out.append(flag.booleanValue());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                appendString(out, String.valueOf(entry.getKey()));
                out.append(':');
                appendValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                appendValue(out, list.get(i));
            }
            out.append(']');
        } else {
            throw new IllegalStateException("cannot serialize " + value.getClass().getName());
        }
    }

    private static void appendString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String what) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(what + " must be a JSON object");
        }
        return (Map<String, Object>) value;
    }

    /** Minimal strict JSON reader for the protocol's request shapes. */
    private static final class Json {

        private final String text;
        private int index;

        private Json(String text) {
            this.text = text;
        }

        static Object parse(String text) {
            Json parser = new Json(text);
            Object value = parser.readValue();
            parser.skipWhitespace();
            if (parser.index != text.length()) {
                throw parser.failure("unexpected trailing characters");
            }
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw failure("unexpected end of input");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw failure("object key must be a string");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                if (object.putIfAbsent(key, value) != null) {
                    throw failure("duplicate object key: " + key);
                }
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    index++;
                } else if (next == '}') {
                    index++;
                    return object;
                } else {
                    throw failure("expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return array;
            }
            while (true) {
                array.add(readValue());
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    index++;
                } else if (next == ']') {
                    index++;
                    return array;
                } else {
                    throw failure("expected ',' or ']'");
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (true) {
                if (index >= text.length()) {
                    throw failure("unterminated string");
                }
                char c = text.charAt(index++);
                if (c == '"') {
                    return value.toString();
                }
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                if (index >= text.length()) {
                    throw failure("unterminated escape");
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicodeEscape());
                    default -> throw failure("invalid escape: \\" + escape);
                }
            }
        }

        private char readUnicodeEscape() {
            if (index + 4 > text.length()) {
                throw failure("truncated unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(index++), 16);
                if (digit < 0) {
                    throw failure("invalid unicode escape digit");
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private Object readNumber() {
            int start = index;
            if (peek() == '-') {
                index++;
            }
            int digits = index;
            while (index < text.length() && isDigit(text.charAt(index))) {
                index++;
            }
            if (index == digits) {
                throw failure("invalid number");
            }
            boolean integral = true;
            if (index < text.length() && text.charAt(index) == '.') {
                integral = false;
                index++;
                int fraction = index;
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index++;
                }
                if (index == fraction) {
                    throw failure("invalid number fraction");
                }
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                integral = false;
                index++;
                if (index < text.length()
                        && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                int exponent = index;
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index++;
                }
                if (index == exponent) {
                    throw failure("invalid number exponent");
                }
            }
            String literal = text.substring(start, index);
            if (integral) {
                try {
                    return Long.parseLong(literal);
                } catch (NumberFormatException overflow) {
                    throw failure("integer out of range");
                }
            }
            return Double.parseDouble(literal);
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw failure("invalid literal");
            }
            index += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private char peek() {
            if (index >= text.length()) {
                throw failure("unexpected end of input");
            }
            return text.charAt(index);
        }

        private void expect(char expected) {
            if (index >= text.length() || text.charAt(index) != expected) {
                throw failure("expected '" + expected + "'");
            }
            index++;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private IllegalArgumentException failure(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }

    private static final class InvalidWireInputException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidWireInputException(String message) {
            super(message);
        }
    }

    private static final class EntropyUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        EntropyUnavailableException() {
            super("injected CSPRNG failure");
        }
    }

    private static final class ToggleSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;

        private final SecureRandom delegate = new SecureRandom();
        private volatile boolean failing;

        void setFailing(boolean failing) {
            this.failing = failing;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (failing) {
                throw new EntropyUnavailableException();
            }
            delegate.nextBytes(bytes);
        }
    }
}
