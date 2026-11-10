package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Independent known-answer cases.
 *
 * <p>The expected strings were derived outside the implementation under test:
 * from the canonical specification's interoperability table, from the official
 * PHP fixture, and with a small independent Python Base36 encoder. Each case
 * asserts both representations and both conversion directions.</p>
 */
class KnownAnswerTest {

    private static final HexFormat HEX = HexFormat.of();

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void knownAnswerRoundTrips(String name, long timestamp, String entropyHex, String id, String binaryHex) {
        Pikaid fromFields = Pikaid.of(timestamp, HEX.parseHex(entropyHex));
        Pikaid fromText = Pikaid.parse(id);
        Pikaid fromBinary = Pikaid.fromBinary(HEX.parseHex(binaryHex));

        assertEquals(id, fromFields.toString(), name + " text");
        assertEquals(timestamp, fromFields.timestamp(), name + " timestamp");
        assertEquals(entropyHex, fromFields.randomnessHex(), name + " entropy");
        assertEquals(binaryHex, HEX.formatHex(fromFields.toBinary()), name + " binary");
        assertEquals(fromFields, fromText, name + " text equality");
        assertEquals(fromFields, fromBinary, name + " binary equality");
        assertEquals(id, fromText.toString(), name + " text round trip");
        assertEquals(binaryHex, HEX.formatHex(fromBinary.toBinary()), name + " binary round trip");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCases")
    void invalidKnownAnswersAreRejected(String name, String id) {
        assertEquals(false, Pikaid.isValid(id), name);
        assertThrows(PikaidFormatException.class, () -> Pikaid.parse(id), name);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                // Canonical specification section 7 / official PHP fixture.
                Arguments.of("spec-all-zero", 0L,
                        "000000000000000000000000", "00000000000000000000000000",
                        "0000000000000000000000000000000000"),
                Arguments.of("spec-one", 1L,
                        "000000000000000000000001", "00000010000000000000000001",
                        "0000000001000000000000000000000001"),
                Arguments.of("official-sample", 1_700_000_000L,
                        "00112233445566778899aabb", "0s44we8002loz3po6mcf6vsn23",
                        "006553f10000112233445566778899aabb"),
                Arguments.of("spec-max", 78_364_164_095L,
                        "ffffffffffffffffffffffff", "zzzzzzz7oiylpimjg5u2ca1ypr",
                        "123ede3fffffffffffffffffffffffffff"),

                // Independently derived boundaries.
                Arguments.of("timestamp-max-entropy-zero", 78_364_164_095L,
                        "000000000000000000000000", "zzzzzzz0000000000000000000",
                        "123ede3fff000000000000000000000000"),
                Arguments.of("timestamp-max-minus-one", 78_364_164_094L,
                        "000000000000000000000000", "zzzzzzy0000000000000000000",
                        "123ede3ffe000000000000000000000000"),
                Arguments.of("timestamp-36-squared-minus-one", 1_295L,
                        "000000000000000000000000", "00000zz0000000000000000000",
                        "000000050f000000000000000000000000"),
                Arguments.of("timestamp-36-squared", 1_296L,
                        "000000000000000000000000", "00001000000000000000000000",
                        "0000000510000000000000000000000000"),
                Arguments.of("timestamp-36-to-six-minus-one", 2_176_782_335L,
                        "000000000000000000000000", "0zzzzzz0000000000000000000",
                        "0081bf0fff000000000000000000000000"),

                // Independently derived entropy boundaries.
                Arguments.of("entropy-highest-bit", 1_700_000_000L,
                        "800000000000000000000000", "0s44we83u9haurb9q2x1650zcw",
                        "006553f100800000000000000000000000"),
                Arguments.of("entropy-one", 1_700_000_000L,
                        "000000000000000000000001", "0s44we80000000000000000001",
                        "006553f100000000000000000000000001"),
                Arguments.of("entropy-zero", 1_700_000_000L,
                        "000000000000000000000000", "0s44we80000000000000000000",
                        "006553f100000000000000000000000000"),
                Arguments.of("arbitrary-values", 1_234_567_890L,
                        "0123456789abcdef01234567", "0kf12oi0188skr4f4jv458xrhj",
                        "00499602d20123456789abcdef01234567"));
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of("spec-entropy-overflow", "0000000zzzzzzzzzzzzzzzzzzz"),
                Arguments.of("entropy-max-plus-one", "00000007oiylpimjg5u2ca1yps"),
                Arguments.of("all-z-suffix", "0000000zzzzzzzzzzzzzzzzzzz"));
    }
}
