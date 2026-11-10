package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.Random;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A second, independent conformance oracle.
 *
 * <p>Expected values are derived from the format definition with JDK primitives
 * rather than the implementation under test: timestamp text comes from
 * {@link Long#toString(long, int)}, randomness text from a repeated
 * {@link BigInteger#divideAndRemainder(BigInteger)} encoder, and binary layout
 * from explicit shifts and copies. Because these paths share no code with
 * {@code Pikaid}'s encoder, a defect there cannot silently agree with itself.</p>
 */
class ConformanceOracleTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final BigInteger BASE = BigInteger.valueOf(36);
    private static final BigInteger TWO_POW_96 = BigInteger.ONE.shiftLeft(96);
    private static final String ZERO_ENTROPY = "000000000000000000000000";
    private static final String[] ENTROPY_PATTERNS = {
        "000000000000000000000000",
        "ffffffffffffffffffffffff",
        "800000000000000000000000",
        "000000000000000000000001",
        "0123456789abcdef01234567",
        "00ff00ff00ff00ff00ff00ff",
    };

    static LongStream boundaries() {
        return LongStream.of(0L, 1L, 35L, 36L, 1_295L, 1_296L, 2_176_782_335L, 2_176_782_336L,
                1_700_000_000L, 1_234_567_890L, Pikaid.MAX_TIMESTAMP - 1, Pikaid.MAX_TIMESTAMP);
    }

    @ParameterizedTest(name = "timestamp {0}")
    @MethodSource("boundaries")
    void timestampTextMatchesJdkBase36(long timestamp) {
        String expectedTimestamp = pad(Long.toString(timestamp, 36), 7);

        Pikaid id = Pikaid.of(timestamp, HEX.parseHex(ZERO_ENTROPY));
        assertEquals(expectedTimestamp + pad("", 19), id.toString());
        assertEquals(timestamp, id.timestamp());
        assertEquals(id, Pikaid.parse(id.toString()));
    }

    @Test
    void randomnessTextMatchesBigIntegerOracle() {
        for (String entropyHex : ENTROPY_PATTERNS) {
            byte[] entropy = HEX.parseHex(entropyHex);
            String expectedRandomness = encodeBase36(new BigInteger(1, entropy), 19);

            Pikaid id = Pikaid.of(1_700_000_000L, entropy);
            assertEquals(expectedRandomness, id.toString().substring(7), entropyHex);
            assertEquals(entropyHex, id.randomnessHex(), entropyHex);
            assertEquals(id, Pikaid.parse(id.toString()), entropyHex);
        }
    }

    @Test
    void binaryLayoutMatchesIndependentAssembly() {
        long[] timestamps = {0L, 1L, 1_295L, 1_700_000_000L, Pikaid.MAX_TIMESTAMP};
        for (long timestamp : timestamps) {
            for (String entropyHex : ENTROPY_PATTERNS) {
                byte[] entropy = HEX.parseHex(entropyHex);
                byte[] expected = new byte[17];
                expected[0] = (byte) (timestamp >>> 32);
                expected[1] = (byte) (timestamp >>> 24);
                expected[2] = (byte) (timestamp >>> 16);
                expected[3] = (byte) (timestamp >>> 8);
                expected[4] = (byte) timestamp;
                System.arraycopy(entropy, 0, expected, 5, 12);

                Pikaid id = Pikaid.of(timestamp, entropy);
                assertArrayEquals(expected, id.toBinary(), timestamp + "/" + entropyHex);
                assertEquals(id, Pikaid.fromBinary(expected), timestamp + "/" + entropyHex);
            }
        }
    }

    @Test
    void nineteenDigitsExceedNinetySixBits() {
        BigInteger maximum = BASE.pow(19).subtract(BigInteger.ONE);
        assertTrue(maximum.compareTo(TWO_POW_96) > 0, "36^19 - 1 must exceed 2^96");
        assertEquals("zzzzzzzzzzzzzzzzzzz", encodeBase36(maximum, 19));
        assertEquals("7oiylpimjg5u2ca1ypr", encodeBase36(TWO_POW_96.subtract(BigInteger.ONE), 19));
    }

    @Test
    void randomnessOfExactlyTwoToTheNinetySixthIsRejected() {
        String overflow = "0000000" + encodeBase36(TWO_POW_96, 19);
        assertEquals(26, overflow.length());
        assertTrue(Pikaid.isWellFormed(overflow), "26 base36 characters are well formed");
        assertThrows(PikaidFormatException.class, () -> Pikaid.parse(overflow));
    }

    @Test
    void randomizedValuesMatchTheOracle() {
        Random random = new Random(0xC0FFEE_1234_5678L);
        for (int i = 0; i < 5_000; i++) {
            long timestamp = random.nextLong(Pikaid.MAX_TIMESTAMP + 1);
            byte[] entropy = new byte[12];
            random.nextBytes(entropy);

            String expected = pad(Long.toString(timestamp, 36), 7)
                    + encodeBase36(new BigInteger(1, entropy), 19);
            Pikaid id = Pikaid.of(timestamp, entropy);

            assertEquals(expected, id.toString(), "iteration " + i);
            assertEquals(timestamp, Pikaid.parse(expected).timestamp(), "iteration " + i);
            assertEquals(id, Pikaid.fromBinary(id.toBinary()), "iteration " + i);
        }
    }

    private static String encodeBase36(BigInteger value, int width) {
        StringBuilder reversed = new StringBuilder(width);
        BigInteger remaining = value;
        for (int i = 0; i < width; i++) {
            BigInteger[] division = remaining.divideAndRemainder(BASE);
            reversed.append(Character.forDigit(division[1].intValue(), 36));
            remaining = division[0];
        }
        if (!remaining.equals(BigInteger.ZERO)) {
            throw new IllegalArgumentException("value does not fit in " + width + " base36 digits");
        }
        return reversed.reverse().toString();
    }

    private static String pad(String value, int width) {
        StringBuilder padded = new StringBuilder(width);
        for (int i = value.length(); i < width; i++) {
            padded.append('0');
        }
        return padded.append(value).toString();
    }
}
