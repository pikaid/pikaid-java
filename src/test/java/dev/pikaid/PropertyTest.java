package dev.pikaid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Deterministic property tests over randomized valid and invalid values.
 *
 * <p>All randomness is seeded so failures are reproducible. Oracles come from
 * the format definition, not from the implementation under test.</p>
 */
class PropertyTest {

    private static final char[] BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    @Test
    void randomizedFieldsRoundTripLosslessly() {
        Random random = new Random(0x1A2B_3C4D_5E6FL);

        for (int i = 0; i < 2_000; i++) {
            long timestamp = random.nextLong(Pikaid.MAX_TIMESTAMP + 1);
            byte[] entropy = randomBytes(random);
            Pikaid id = Pikaid.of(timestamp, entropy);

            String text = id.toString();
            byte[] binary = id.toBinary();

            assertEquals(26, text.length(), "text length at iteration " + i);
            assertEquals(17, binary.length, "binary length at iteration " + i);
            assertEquals(timestamp, id.timestamp());
            assertArrayEquals(entropy, id.randomness());

            Pikaid fromText = Pikaid.parse(text);
            Pikaid fromBinary = Pikaid.fromBinary(binary);

            assertEquals(id, fromText, "text equality at iteration " + i);
            assertEquals(id, fromBinary, "binary equality at iteration " + i);
            assertEquals(text, fromText.toString());
            assertEquals(text, fromBinary.toString());
            assertArrayEquals(binary, fromText.toBinary());
            assertArrayEquals(binary, fromBinary.toBinary());
        }
    }

    @Test
    void arbitraryTwentySixCharacterStringsAreNeverSilentlyNormalized() {
        Random random = new Random(0x0FED_CBA9_8765_4321L);
        int validSeen = 0;

        for (int i = 0; i < 5_000; i++) {
            String candidate = randomCandidate(random);
            assertEquals(26, candidate.length());

            if (Pikaid.isValid(candidate)) {
                validSeen++;
                Pikaid parsed = Pikaid.parse(candidate);
                assertEquals(candidate, parsed.toString(), "valid text must be canonical");
                assertEquals(parsed, Pikaid.fromBinary(parsed.toBinary()));
                assertTrue(Pikaid.isWellFormed(candidate));
            } else {
                assertThrows(PikaidFormatException.class, () -> Pikaid.parse(candidate),
                        "invalid text must raise: " + candidate);
            }
        }

        assertTrue(validSeen > 0, "seeded sample should contain some valid pikaids");
    }

    @Test
    void everyTimestampBoundaryRoundTrips() {
        long[] timestamps = {
                0L, 1L, 2L, 35L, 36L, 1_295L, 1_296L, 46_655L, 46_656L,
                1_679_615L, 1_679_616L, 60_466_175L, 60_466_176L,
                2_176_782_335L, 2_176_782_336L,
                Pikaid.MAX_TIMESTAMP - 1, Pikaid.MAX_TIMESTAMP
        };
        byte[] entropy = new byte[12];
        Arrays.fill(entropy, (byte) 0xA5);

        for (long timestamp : timestamps) {
            Pikaid id = Pikaid.of(timestamp, entropy);
            assertEquals(id, Pikaid.parse(id.toString()), "timestamp " + timestamp);
            assertEquals(id, Pikaid.fromBinary(id.toBinary()), "timestamp " + timestamp);
        }
    }

    @Test
    void veryLargeInvalidEntropyNeverProducesAPartialValue() {
        String[] invalid = {
                "0000000zzzzzzzzzzzzzzzzzzz",
                "0000000" + "7oiylpimjg5u2ca1yps",
                "0000000" + "zzzzzzzzzzzzzzzzzzz",
                "0000000" + "zzzzzzzzzzzzzzzzzzy",
        };

        for (String candidate : invalid) {
            assertFalse(Pikaid.isValid(candidate), candidate);
            assertThrows(PikaidFormatException.class, () -> Pikaid.parse(candidate), candidate);
        }
    }

    @Test
    void emptyAndOversizedBinaryNeverProducesAPartialValue() {
        assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(new byte[0]));
        assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(new byte[16]));
        assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(new byte[18]));
        assertThrows(PikaidFormatException.class, () -> Pikaid.fromBinary(new byte[255]));
    }

    private static byte[] randomBytes(Random random) {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String randomCandidate(Random random) {
        char[] chars = new char[26];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = BASE36[random.nextInt(BASE36.length)];
        }
        return new String(chars);
    }
}
